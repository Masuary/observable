package observable;

import observable.server.Profiler;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicReference;

public class Props {
    public static boolean notProcessing = true;

    public static AtomicReference<Profiler.TimingData> currentTarget = new AtomicReference<>(null);
    public static ThreadLocal<ArrayDeque<Profiler.TimingData>> currentTargetStack =
            ThreadLocal.withInitial(ArrayDeque::new);

    public static void pushCurrentTarget(Profiler.TimingData data) {
        if (data == null) {
            return;
        }
        ArrayDeque<Profiler.TimingData> stack = currentTargetStack.get();
        if (currentTarget.get() == null && !stack.isEmpty()) {
            stack.clear();
        }
        stack.push(data);
        currentTarget.set(data);
    }

    public static void popCurrentTarget(Profiler.TimingData data) {
        if (data == null) {
            return;
        }

        ArrayDeque<Profiler.TimingData> stack = currentTargetStack.get();
        if (!stack.isEmpty() && stack.peek() == data) {
            stack.pop();
        } else {
            stack.remove(data);
        }
        currentTarget.set(stack.peek());
    }

    public static void clearCurrentTargets() {
        currentTargetStack.get().clear();
        currentTarget.set(null);
    }

    public static int entityDepth = -1;
    public static int blockEntityDepth = -1;
    public static int blockDepth = -1;
    public static int fluidDepth = -1;
}
