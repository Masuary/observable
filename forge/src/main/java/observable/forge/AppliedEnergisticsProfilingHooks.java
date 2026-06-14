package observable.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import observable.Observable;
import observable.Props;
import observable.server.Profiler;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class AppliedEnergisticsProfilingHooks {
    private static final String AE2_PACKAGE = "appeng.";
    private static final String CRAFTING_SERVICE = "appeng.me.service.CraftingService";
    private static final String STORAGE_SERVICE = "appeng.me.service.StorageService";
    private static final String PATHING_SERVICE = "appeng.me.service.PathingService";
    private static final String ENERGY_SERVICE = "appeng.me.service.EnergyService";
    private static final String ON_SERVER_END_TICK = "onServerEndTick";
    private static final String BLOCK_ENTITY_SUFFIX = "BlockEntity";
    private static final String PART_SUFFIX = "Part";
    private static final String LOGIC_SUFFIX = "Logic";
    private static final String TICKER_SUFFIX = "$Ticker";

    private static final ClassValue<TrackerAccessors> TRACKER_ACCESSORS = new ClassValue<TrackerAccessors>() {
        @Override
        protected TrackerAccessors computeValue(Class<?> type) {
            return new TrackerAccessors(
                    findMethod(type, "getNode"),
                    findMethod(type, "getGridTickable")
            );
        }
    };

    private static final ClassValue<GridAccessors> GRID_ACCESSORS = new ClassValue<GridAccessors>() {
        @Override
        protected GridAccessors computeValue(Class<?> type) {
            return new GridAccessors(findMethod(type, "getPivot"));
        }
    };

    private static final ClassValue<NodeAccessors> NODE_ACCESSORS = new ClassValue<NodeAccessors>() {
        @Override
        protected NodeAccessors computeValue(Class<?> type) {
            return new NodeAccessors(
                    findMethod(type, "getLevel"),
                    findMethod(type, "getOwner"),
                    findMethod(type, "getLocation")
            );
        }
    };

    private static final ClassValue<OwnerAccessors> OWNER_ACCESSORS = new ClassValue<OwnerAccessors>() {
        @Override
        protected OwnerAccessors computeValue(Class<?> type) {
            return new OwnerAccessors(
                    findMethod(type, "getLevel"),
                    findMethod(type, "getBlockEntity"),
                    findMethod(type, "getBlockPos", "getPos", "getLocation"),
                    findField(type, "this$0")
            );
        }
    };

    private AppliedEnergisticsProfilingHooks() {
    }

    public static Profiler.TimingData startTickingRequest(Object tracker) {
        if (tracker == null) {
            return null;
        }

        TrackerAccessors trackerAccessors = TRACKER_ACCESSORS.get(tracker.getClass());
        Object node = trackerAccessors.getNode(tracker);
        if (node == null) {
            return null;
        }

        Object tickable = trackerAccessors.getGridTickable(tracker);
        NodeAccessors nodeAccessors = NODE_ACCESSORS.get(node.getClass());
        Object owner = nodeAccessors.getOwner(node);
        Object labelSource = owner != null ? owner : unwrapTicker(tickable);
        if (labelSource == null) {
            labelSource = tickable != null ? tickable : node;
        }

        Level level = resolveLevel(node, nodeAccessors, owner);
        BlockPos pos = resolvePos(node, nodeAccessors, owner);
        Object traceSource = tickable != null ? tickable : labelSource;

        return start(
                level,
                pos,
                label(labelSource),
                traceSource.getClass().getName(),
                "tickingRequest"
        );
    }

    private static Profiler.TimingData start(Level level, BlockPos pos, String label, String traceClassName, String traceMethodName) {
        if (Props.notProcessing || !(level instanceof ServerLevel) || pos == null) {
            return null;
        }

        Profiler.TimingData data = Observable.INSTANCE.getPROFILER().processSyntheticBlock(
                level,
                pos,
                label,
                traceClassName,
                traceMethodName
        );
        Props.pushCurrentTarget(data);
        return data;
    }

    public static void runGridServiceEndTick(Object grid, Object service) {
        Profiler.TimingData data = startGridServiceTick(grid, service, ON_SERVER_END_TICK);
        long startNanos = data != null ? System.nanoTime() : 0L;
        try {
            invokeServiceEndTick(service);
        } finally {
            finishTiming(data, startNanos);
        }
    }

    public static Profiler.TimingData startGridServiceTick(Object grid, Object service, String traceMethodName) {
        if (grid == null || service == null || !isTrackedGridService(service)) {
            return null;
        }

        Object pivot = GRID_ACCESSORS.get(grid.getClass()).getPivot(grid);
        if (pivot == null) {
            return null;
        }

        NodeAccessors nodeAccessors = NODE_ACCESSORS.get(pivot.getClass());
        Object owner = nodeAccessors.getOwner(pivot);
        return start(
                resolveLevel(pivot, nodeAccessors, owner),
                resolvePos(pivot, nodeAccessors, owner),
                serviceLabel(service),
                service.getClass().getName(),
                traceMethodName
        );
    }

    public static void finishTickingRequest(Profiler.TimingData data, long startNanos) {
        finishTiming(data, startNanos);
    }

    public static void finishTiming(Profiler.TimingData data, long startNanos) {
        if (data == null) {
            return;
        }

        data.setTime(System.nanoTime() - startNanos + data.getTime());
        data.setTicks(data.getTicks() + 1);
        Props.popCurrentTarget(data);
    }

    private static void invokeServiceEndTick(Object service) {
        Method method = findMethod(service, ON_SERVER_END_TICK);
        if (method == null) {
            throw new IllegalStateException("Could not find AE2 grid service tick method on " + service.getClass().getName());
        }

        try {
            method.invoke(service);
        } catch (InvocationTargetException e) {
            rethrow(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not invoke AE2 grid service tick method on " + service.getClass().getName(), e);
        }
    }

    private static void rethrow(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        throw new RuntimeException(throwable);
    }

    private static Level resolveLevel(Object node, NodeAccessors nodeAccessors, Object owner) {
        Object nodeLevel = nodeAccessors.getLevel(node);
        if (nodeLevel instanceof Level) {
            return (Level) nodeLevel;
        }

        if (owner != null) {
            OwnerAccessors ownerAccessors = OWNER_ACCESSORS.get(owner.getClass());
            Object ownerLevel = ownerAccessors.getLevel(owner);
            if (ownerLevel instanceof Level) {
                return (Level) ownerLevel;
            }

            BlockEntity blockEntity = ownerAccessors.getBlockEntity(owner);
            if (blockEntity != null) {
                return blockEntity.getLevel();
            }

            Object ownerPos = ownerAccessors.getPos(owner);
            Object positionalLevel = getLevelFromPosLike(ownerPos);
            if (positionalLevel instanceof Level) {
                return (Level) positionalLevel;
            }
        }

        return null;
    }

    private static BlockPos resolvePos(Object node, NodeAccessors nodeAccessors, Object owner) {
        if (owner instanceof BlockEntity) {
            return ((BlockEntity) owner).getBlockPos();
        }

        if (owner != null) {
            OwnerAccessors ownerAccessors = OWNER_ACCESSORS.get(owner.getClass());
            BlockEntity blockEntity = ownerAccessors.getBlockEntity(owner);
            if (blockEntity != null) {
                return blockEntity.getBlockPos();
            }

            Object ownerPos = ownerAccessors.getPos(owner);
            BlockPos pos = getBlockPos(ownerPos);
            if (pos != null) {
                return pos;
            }
        }

        return getBlockPos(nodeAccessors.getLocation(node));
    }

    private static Object unwrapTicker(Object tickable) {
        if (tickable == null || !tickable.getClass().getName().endsWith(TICKER_SUFFIX)) {
            return tickable;
        }
        return OWNER_ACCESSORS.get(tickable.getClass()).getOuter(tickable);
    }

    private static boolean isTrackedGridService(Object service) {
        String className = service.getClass().getName();
        return CRAFTING_SERVICE.equals(className) ||
                STORAGE_SERVICE.equals(className) ||
                PATHING_SERVICE.equals(className) ||
                ENERGY_SERVICE.equals(className);
    }

    private static String serviceLabel(Object service) {
        String className = service.getClass().getName();
        String simpleName = service.getClass().getSimpleName();
        String namespace = className.startsWith(AE2_PACKAGE) ? "ae2" : "ae2_compat";
        return namespace + ":" + toSnakeCase(simpleName);
    }

    private static String label(Object source) {
        String className = source.getClass().getName();
        String simpleName = source.getClass().getSimpleName();
        if (simpleName.isEmpty()) {
            simpleName = className.substring(className.lastIndexOf('.') + 1);
        }

        if (simpleName.endsWith(BLOCK_ENTITY_SUFFIX)) {
            simpleName = simpleName.substring(0, simpleName.length() - BLOCK_ENTITY_SUFFIX.length());
        }
        if (simpleName.endsWith(PART_SUFFIX)) {
            simpleName = simpleName.substring(0, simpleName.length() - PART_SUFFIX.length());
        }
        if (simpleName.endsWith(LOGIC_SUFFIX)) {
            simpleName = simpleName.substring(0, simpleName.length() - LOGIC_SUFFIX.length());
        }

        String namespace = className.startsWith(AE2_PACKAGE) ? "ae2" : "ae2_compat";
        return namespace + ":" + toSnakeCase(simpleName);
    }

    private static BlockPos getBlockPos(Object value) {
        if (value instanceof BlockPos) {
            return (BlockPos) value;
        }
        Object pos = invoke(findMethod(value, "getPos"), value);
        return pos instanceof BlockPos ? (BlockPos) pos : null;
    }

    private static Object getLevelFromPosLike(Object value) {
        return invoke(findMethod(value, "getLevel"), value);
    }

    private static Method findMethod(Object target, String... names) {
        return target == null ? null : findMethod(target.getClass(), names);
    }

    private static Method findMethod(Class<?> type, String... names) {
        for (String name : names) {
            Class<?> current = type;
            while (current != null) {
                try {
                    Method method = current.getDeclaredMethod(name);
                    method.setAccessible(true);
                    return method;
                } catch (ReflectiveOperationException ignored) {
                    current = current.getSuperclass();
                }
            }

            try {
                Method method = type.getMethod(name);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Object invoke(Method method, Object target) {
        if (method == null || target == null) {
            return null;
        }
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object get(Field field, Object target) {
        if (field == null || target == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String toSnakeCase(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '$') {
                builder.append('_');
            } else if (Character.isUpperCase(c)) {
                if (i > 0) {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(c));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static final class TrackerAccessors {
        private final Method nodeMethod;
        private final Method gridTickableMethod;

        private TrackerAccessors(Method nodeMethod, Method gridTickableMethod) {
            this.nodeMethod = nodeMethod;
            this.gridTickableMethod = gridTickableMethod;
        }

        private Object getNode(Object target) {
            return invoke(nodeMethod, target);
        }

        private Object getGridTickable(Object target) {
            return invoke(gridTickableMethod, target);
        }
    }

    private static final class GridAccessors {
        private final Method pivotMethod;

        private GridAccessors(Method pivotMethod) {
            this.pivotMethod = pivotMethod;
        }

        private Object getPivot(Object target) {
            return invoke(pivotMethod, target);
        }
    }

    private static final class NodeAccessors {
        private final Method levelMethod;
        private final Method ownerMethod;
        private final Method locationMethod;

        private NodeAccessors(Method levelMethod, Method ownerMethod, Method locationMethod) {
            this.levelMethod = levelMethod;
            this.ownerMethod = ownerMethod;
            this.locationMethod = locationMethod;
        }

        private Object getLevel(Object target) {
            return invoke(levelMethod, target);
        }

        private Object getOwner(Object target) {
            return invoke(ownerMethod, target);
        }

        private Object getLocation(Object target) {
            return invoke(locationMethod, target);
        }
    }

    private static final class OwnerAccessors {
        private final Method levelMethod;
        private final Method blockEntityMethod;
        private final Method posMethod;
        private final Field outerField;

        private OwnerAccessors(Method levelMethod, Method blockEntityMethod, Method posMethod, Field outerField) {
            this.levelMethod = levelMethod;
            this.blockEntityMethod = blockEntityMethod;
            this.posMethod = posMethod;
            this.outerField = outerField;
        }

        private Object getLevel(Object target) {
            return invoke(levelMethod, target);
        }

        private BlockEntity getBlockEntity(Object target) {
            Object value = invoke(blockEntityMethod, target);
            return value instanceof BlockEntity ? (BlockEntity) value : null;
        }

        private Object getPos(Object target) {
            return invoke(posMethod, target);
        }

        private Object getOuter(Object target) {
            return get(outerField, target);
        }
    }
}
