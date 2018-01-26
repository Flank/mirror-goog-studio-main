package android.com.java.profilertester.memory;

import android.com.java.profilertester.profiletask.TaskCategory;
import android.support.annotation.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class MemoryTaskCategory extends TaskCategory {
    private static final int PERIOD_TIME = 2;
    private static final int ITERATION_COUNT = 5;
    private static final int DELTA_SIZE = (1 << 22);
    private static final int DELTA_OBJECT_COUNT = 10000;

    private final List<? extends Task> mTasks = Arrays.asList(
            new AllocateJavaMemoryTask(),
            new AllocateNativeMemoryTask(),
            new AllocateObjectsTask());

    static {
        System.loadLibrary("native_memory");
    }

    public int getValue() {
        return ITERATION_COUNT;
    }

    private static class AllocateJavaMemoryTask extends MemoryTask {
        @Override
        protected String memoryExecute() throws Exception {
            char[][] table = new char[ITERATION_COUNT][];
            for (int i = 0; i < ITERATION_COUNT; ++i) {
                long start = System.currentTimeMillis();
                table[i] = new char[DELTA_SIZE];
                Arrays.fill(table[i], 'x');
                TimeUnit.MILLISECONDS.sleep(PERIOD_TIME * 1000 - (int) (System.currentTimeMillis() - start));
            }
            return null;
        }

        @NonNull
        @Override
        protected String getTaskName() {
            return "Java Memory Allocation";
        }
    }

    private static class AllocateNativeMemoryTask extends MemoryTask {
        public native void allocateNativeMemory();

        @Override
        protected String memoryExecute() throws Exception {
            allocateNativeMemory();
            return null;
        }

        @NonNull
        @Override
        protected String getTaskName() {
            return "Native Memory Allocation";
        }
    }

    private static class AllocateObjectsTask extends MemoryTask {
        @Override
        protected String memoryExecute() throws Exception {
            Integer[][] objects = new Integer[ITERATION_COUNT][];
            for (int k = 0; k < ITERATION_COUNT; ++k) {
                long start = System.currentTimeMillis();
                objects[k] = new Integer[DELTA_OBJECT_COUNT];
                for (int i = 0; i < objects[k].length; ++i) {
                    objects[k][i] = k * DELTA_OBJECT_COUNT + i;
                }
                TimeUnit.MILLISECONDS.sleep(PERIOD_TIME * 1000 - (int) (System.currentTimeMillis() - start));
            }
            return null;
        }

        @NonNull
        @Override
        protected String getTaskName() {
            return "Object Allocation";
        }
    }

    @NonNull
    @Override
    public List<? extends Task> getTasks() {
        return mTasks;
    }

    @NonNull
    @Override
    protected String getCategoryName() {
        return "Memory";
    }

    private static abstract class MemoryTask extends Task {
        @Override
        protected final String execute() throws Exception {
            Runtime.getRuntime().gc();
            String result = memoryExecute();
            Runtime.getRuntime().gc();
            return result;
        }

        protected abstract String memoryExecute() throws Exception;
    }
}
