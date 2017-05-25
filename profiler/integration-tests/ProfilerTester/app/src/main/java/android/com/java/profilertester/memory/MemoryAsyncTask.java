package android.com.java.profilertester.memory;

import android.os.AsyncTask;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;


public class MemoryAsyncTask extends AsyncTask<Integer, Void, Void> {
    public static final int LOCAL_MEMORY_ALLOCATION_NUMBER = 0;
    public static final int NATIVE_MEMORY_ALLOCATION_NUMBER = 1;
    public static final int OBJECT_ALLOCATION_NUMBER = 2;

    public final int PERIOD_TIME = 2;
    public final int ITERATION_COUNT = 5;
    public final int DELTA_SIZE = (1 << 22);
    public final int DELTA_OBJECT_COUNT = 10000;

    static {
        System.loadLibrary("native_memory");
    }

    public int getValue() {
        return ITERATION_COUNT;
    }

    private void allocateJavaMemory() throws InterruptedException {
        char[][] table = new char[ITERATION_COUNT][];
        for (int i = 0; i < ITERATION_COUNT; ++i) {
            long start = System.currentTimeMillis();
            table[i] = new char[DELTA_SIZE];
            Arrays.fill(table[i], 'x');
            TimeUnit.MILLISECONDS.sleep(PERIOD_TIME * 1000 - (int)(System.currentTimeMillis() - start));
        }
    }

    public native void allocateNativeMemory();

    private void allocateObjects() throws InterruptedException {
        Integer[][] objects = new Integer[ITERATION_COUNT][];
        for (int k = 0; k < ITERATION_COUNT; ++k) {
            long start = System.currentTimeMillis();
            objects[k] = new Integer[DELTA_OBJECT_COUNT];
            for (int i = 0; i < objects[k].length; ++i) {
                objects[k][i] = k * DELTA_OBJECT_COUNT + i;
            }
            TimeUnit.MILLISECONDS.sleep(PERIOD_TIME * 1000 - (int)(System.currentTimeMillis() - start));
        }
    }



    @Override
    protected Void doInBackground(Integer... parameters) {
        // "parameters" should only have one element as actionNumber
        assertEquals(parameters.length, 1);
        int actionNumber = parameters[0];

        Runtime.getRuntime().gc();
        try {
            if (actionNumber == LOCAL_MEMORY_ALLOCATION_NUMBER) {
                allocateJavaMemory();
            }

            if (actionNumber == NATIVE_MEMORY_ALLOCATION_NUMBER) {
                allocateNativeMemory();
            }

            if (actionNumber == OBJECT_ALLOCATION_NUMBER) {
                allocateObjects();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Runtime.getRuntime().gc();
        return null;
    }
}
