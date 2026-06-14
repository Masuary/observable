package observable.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import observable.Observable;
import observable.Props;
import observable.server.Profiler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class RefinedStorageProfilingHooks {
    private static final String NODE_SUFFIX = "NetworkNode";
    private static final String EXTRASTORAGE_PACKAGE = "edivad.extrastorage.";
    private static final ClassValue<Accessors> ACCESSORS = new ClassValue<Accessors>() {
        @Override
        protected Accessors computeValue(Class<?> type) {
            return new Accessors(
                    findMethod(type, "getLevel"),
                    findMethod(type, "getPos", "getPosition"),
                    findField(type, "level"),
                    findField(type, "pos")
            );
        }
    };

    private RefinedStorageProfilingHooks() {
    }

    public static Profiler.TimingData startNodeUpdate(Object node) {
        Accessors accessors = ACCESSORS.get(node.getClass());
        return start(
                accessors.getLevel(node),
                accessors.getPos(node),
                label(node),
                node.getClass().getName(),
                "update"
        );
    }

    public static Profiler.TimingData startNetworkUpdate(Object network) {
        Accessors accessors = ACCESSORS.get(network.getClass());
        return start(accessors.getLevel(network), accessors.getPos(network), "refinedstorage:network", network.getClass().getName(), "update");
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

    public static void finishNodeUpdate(Profiler.TimingData data, long startNanos) {
        if (data == null) {
            return;
        }

        data.setTime(System.nanoTime() - startNanos + data.getTime());
        data.setTicks(data.getTicks() + 1);
        Props.popCurrentTarget(data);
    }

    private static String label(Object node) {
        String className = node.getClass().getName();
        String simpleName = node.getClass().getSimpleName();
        if (simpleName.endsWith(NODE_SUFFIX)) {
            simpleName = simpleName.substring(0, simpleName.length() - NODE_SUFFIX.length());
        }
        String namespace = className.startsWith(EXTRASTORAGE_PACKAGE) ? "extrastorage" : "refinedstorage";
        return namespace + ":" + toSnakeCase(simpleName);
    }

    private static String toSnakeCase(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c)) {
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

    private static Method findMethod(Class<?> type, String... names) {
        for (String name : names) {
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

    private static final class Accessors {
        private final Method levelMethod;
        private final Method posMethod;
        private final Field levelField;
        private final Field posField;

        private Accessors(Method levelMethod, Method posMethod, Field levelField, Field posField) {
            this.levelMethod = levelMethod;
            this.posMethod = posMethod;
            this.levelField = levelField;
            this.posField = posField;
        }

        private Level getLevel(Object target) {
            Object value = get(levelMethod, levelField, target);
            return value instanceof Level ? (Level) value : null;
        }

        private BlockPos getPos(Object target) {
            Object value = get(posMethod, posField, target);
            return value instanceof BlockPos ? (BlockPos) value : null;
        }

        private Object get(Method method, Field field, Object target) {
            try {
                if (method != null) {
                    return method.invoke(target);
                }
                if (field != null) {
                    return field.get(target);
                }
            } catch (ReflectiveOperationException ignored) {
            }
            return null;
        }
    }
}
