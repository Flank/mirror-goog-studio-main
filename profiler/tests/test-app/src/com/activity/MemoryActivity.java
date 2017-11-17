package com.activity;

import android.app.Activity;
import java.util.*;

public class MemoryActivity extends Activity {

    public static class MemTestEntity {};

    public MemoryActivity() {
        super("MemoryActivity");
        makeSureTestEntityClassLoaded(null);
    }

    // This functions is really doing nothing except making sure
    // MemTestEntity class is loaded by the time we start real testing.
    private MemTestEntity makeSureTestEntityClassLoaded(MemTestEntity o)
    {
        if (o != null) {
            return new MemTestEntity();
        }
        return o;
    }

    List<Object> entities = new ArrayList<Object>();

    public void makeAllocationNoise() {
        List<Object> objs = new ArrayList<Object>();
        final int DataBatchSize = 2000;
        for (int i = 0; i < DataBatchSize; i++) {
            objs.add(new Object());
        }
        System.out.println("MemoryActivity.makeAllocationNoise");
    }

    public void allocate() {
        entities.add(new MemTestEntity());
        System.out.println("MemoryActivity.allocate");
    }

    public void free() {
        entities.clear();
        System.out.println("MemoryActivity.free");
    }

    public void gc() {
        System.gc();
        System.out.println("MemoryActivity.gc");
    }

    public int size() {
        int result = entities.size();
        System.out.printf("MemoryActivity.size %d\n", result);
        return result;
    }
}
