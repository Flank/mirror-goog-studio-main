package android.com.java.profilertester;

import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import java.util.concurrent.CountDownLatch;

public final class MainLooperThread extends Thread {
    private final CountDownLatch mReady = new CountDownLatch(1);

    @Nullable private volatile Looper mLooper = null;

    @Override
    public void run() {
        Looper.prepare();
        mLooper = Looper.myLooper();
        mReady.countDown();
        Looper.loop();
    }

    @Override
    public synchronized void start() {
        super.start();
        try {
            mReady.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @NonNull
    public Looper getLooper() {
        Looper looper = mLooper;
        if (looper == null) {
            throw new RuntimeException("Thread has not complete start() yet!");
        }
        return looper;
    }

    public void quit() {
        Looper looper = mLooper;
        if (looper == null) {
            throw new RuntimeException("Thread has not complete start() yet!");
        }
        looper.quitSafely();
    }
}
