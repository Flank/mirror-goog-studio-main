package android.com.java.profilertester.asic;

import static android.app.Activity.RESULT_OK;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.com.java.profilertester.ActivityRequestCodes;
import android.com.java.profilertester.profiletask.TaskCategory;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.NonNull;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class BluetoothTaskCategory extends TaskCategory {
    private final List<? extends Task> mTasks = Arrays.asList(new ScanningTask());
    private final Activity mHostActivity;

    // Latch to wait for user to accept/reject Bluetooth access prompt.
    private CountDownLatch mPredicateCountdownLatch;
    // Volatile because this is crossing thread boundaries.
    private volatile boolean mBluetoothEnabled = false;

    public BluetoothTaskCategory(@NonNull Activity hostActivity) {
        mHostActivity = hostActivity;
    }

    @NonNull
    @Override
    public List<? extends Task> getTasks() {
        return mTasks;
    }

    @NonNull
    @Override
    protected String getCategoryName() {
        return "Bluetooth";
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ActivityRequestCodes.REQUEST_ENABLE_BT.ordinal()) {
            mBluetoothEnabled = (resultCode == RESULT_OK);
            mPredicateCountdownLatch.countDown();
        }
    }

    @Override
    protected boolean shouldRunTask(@NonNull Task taskToRun) {
        mPredicateCountdownLatch = new CountDownLatch(1);

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            return false;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mHostActivity.startActivityForResult(
                    enableBtIntent, ActivityRequestCodes.REQUEST_ENABLE_BT.ordinal());

            try {
                mPredicateCountdownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
                return false;
            }
        } else {
            mBluetoothEnabled = true;
        }

        // Presumably bluetooth is enabled by the time the Activity resumes?
        return mBluetoothEnabled;
    }

    private final class ScanningTask extends Task {
        @NonNull
        @Override
        protected String execute() throws Exception {
            final CountDownLatch startedLatch = new CountDownLatch(1);
            final CountDownLatch endedLatch = new CountDownLatch(1);

            final AtomicLong startTime = new AtomicLong(-1);
            final AtomicLong endTime = new AtomicLong(-1);

            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (!mBluetoothEnabled || bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                return "Bluetooth adapter disabled!";
            }

            BroadcastReceiver startBroadcastReceiver =
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            startTime.set(System.currentTimeMillis());
                            startedLatch.countDown();
                        }
                    };
            BroadcastReceiver endBroadcastReceiver =
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            endTime.set(System.currentTimeMillis());
                            endedLatch.countDown();
                        }
                    };
            mHostActivity.registerReceiver(
                    startBroadcastReceiver,
                    new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED));
            mHostActivity.registerReceiver(
                    endBroadcastReceiver,
                    new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

            bluetoothAdapter.startDiscovery();
            // According to the spec, it takes some time to start the scan (just give it about 5
            // seconds to start), and approximately another 11 seconds for BT to complete its scan
            // (so gave it a good 20 seconds to complete, just in case).
            startedLatch.await(5, TimeUnit.SECONDS);
            endedLatch.await(20, TimeUnit.SECONDS);
            bluetoothAdapter.cancelDiscovery();

            mHostActivity.unregisterReceiver(startBroadcastReceiver);
            mHostActivity.unregisterReceiver(endBroadcastReceiver);

            if (startTime.get() == -1) {
                return "Could not start bluetooth scanning";
            } else if (endTime.get() == -1) {
                return "Bluetooth scanning did not stop in time";
            }
            return "Discovery took: " + (endTime.get() - startTime.get() + "ms");
        }

        @NonNull
        @Override
        protected String getTaskName() {
            return "Bluetooth Scan";
        }
    }
}
