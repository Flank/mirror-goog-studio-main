package android.com.java.profilertester.taskcategory;

import android.support.annotation.NonNull;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class MemoryTaskCategory extends TaskCategory {
    private final int PERIOD_TIME = 2;
    private final int ITERATION_COUNT = 5;
    private final int DELTA_SIZE = (1 << 22);
    private final int DELTA_OBJECT_COUNT = 10000;

    private final List<? extends Task> mTasks =
            Arrays.asList(
                    new AllocateJavaMemoryTask(),
                    new AllocateNativeMemoryTask(),
                    new AllocateObjectsTask(),
                    new JniRefsTask());

    static {
        System.loadLibrary("native_memory");
    }
    public native void allocateNativeMemory();

    public native long allocateJniRef(Object o);

    public native void freeJniRef(long refValue);

    public int getValue() {
        return ITERATION_COUNT;
    }

    private class AllocateJavaMemoryTask extends MemoryTask {
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
        protected String getTaskDescription() {
            return "Java Memory Allocation";
        }
    }

    private class AllocateNativeMemoryTask extends MemoryTask {

        @Override
        protected String memoryExecute() throws Exception {
            MemoryTaskCategory.this.allocateNativeMemory();
            return null;
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Native Memory Allocation";
        }
    }

    private class AllocationTestObject {
        int value = 0;

        AllocationTestObject(int v) {
            value = v;
        }
    }

    private class AllocateObjectsTask extends MemoryTask {
        @Override
        protected String memoryExecute() throws Exception {
            Object[][] objects = new Object[ITERATION_COUNT][];
            for (int k = 0; k < ITERATION_COUNT; ++k) {
                long start = System.currentTimeMillis();
                objects[k] = new Object[DELTA_OBJECT_COUNT];
                for (int i = 0; i < objects[k].length; ++i) {
                    objects[k][i] = new AllocationTestObject(k * DELTA_OBJECT_COUNT + i);
                }

                TimeUnit.MILLISECONDS.sleep(PERIOD_TIME * 1000 - (int) (System.currentTimeMillis() - start));
            }

            return null;
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Object Allocation";
        }
    }

    private class JniRefsTask extends MemoryTask {
        @Override
        protected String memoryExecute() throws Exception {
            long[] refs = new long[DELTA_OBJECT_COUNT / 10];
            for (int k = 0; k < ITERATION_COUNT; ++k) {
                long start = System.currentTimeMillis();
                for (int i = 0; i < refs.length; ++i) {
                    refs[i] = allocateJniRef(new AllocationTestObject(k * DELTA_OBJECT_COUNT + i));
                }
                Runtime.getRuntime().gc();
                for (long ref : refs) {
                    freeJniRef(ref);
                }
                TimeUnit.MILLISECONDS.sleep(
                        PERIOD_TIME * 1000 - (int) (System.currentTimeMillis() - start));
            }

            return null;
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "JNI References Allocation";
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
