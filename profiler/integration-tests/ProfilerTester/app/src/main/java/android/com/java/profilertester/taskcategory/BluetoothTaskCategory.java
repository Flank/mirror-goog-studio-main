package android.com.java.profilertester.taskcategory;

import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class BluetoothTaskCategory extends TaskCategory {
    private final List<? extends Task> mTasks =
            Arrays.asList(new ScanningTask(), new LeScanningTask());
    private final Activity mHostActivity;

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
    protected boolean shouldRunTask(@NonNull Task taskToRun) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            return false;
        }
        if (!bluetoothAdapter.isEnabled()) {
            mHostActivity.startActivityForResult(
                    new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                    ActivityRequestCodes.REQUEST_ENABLE_BT.ordinal());
            return false;
        }

        return true;
    }

    @NonNull
    @Override
    public RequestCodePermissions getPermissionsRequired(@NonNull Task taskToRun) {
        return new RequestCodePermissions(
                new String[] {
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                },
                ActivityRequestCodes.LOCATION);
    }

    private final class ScanningTask extends Task {
        private static final int INVALID_TIME = -1;
        private static final int BT_STARTUP_TIME_S = 5;
        private static final int BT_SCAN_TIME_S = 60;

        @NonNull
        @Override
        protected String execute() throws Exception {
            final CountDownLatch startedLatch = new CountDownLatch(1);
            final CountDownLatch endedLatch = new CountDownLatch(1);

            // According to the spec, it takes some time to start the scan (just give it about 5
            // seconds to start), and approximately another 11 seconds for BT to complete its scan.
            // However, during testing, the scan itself takes approximately 50s. So just give it a
            // minute to complete.
            final AtomicLong startTime = new AtomicLong(INVALID_TIME);
            final AtomicLong endTime = new AtomicLong(INVALID_TIME);
            final List<BluetoothDevice> devices = new ArrayList<>();

            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
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
            BroadcastReceiver deviceFoundBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    devices.add(device);
                }
            };
            BroadcastReceiver endBroadcastReceiver =
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            // Only set the time once.
                            endTime.compareAndSet(INVALID_TIME, System.currentTimeMillis());
                            endedLatch.countDown();
                        }
                    };
            mHostActivity.registerReceiver(
                    startBroadcastReceiver,
                    new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED));
            mHostActivity.registerReceiver(
                    deviceFoundBroadcastReceiver,
                    new IntentFilter(BluetoothDevice.ACTION_FOUND));
            mHostActivity.registerReceiver(
                    endBroadcastReceiver,
                    new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

            bluetoothAdapter.startDiscovery();
            startedLatch.await(BT_STARTUP_TIME_S, TimeUnit.SECONDS);
            endedLatch.await(BT_SCAN_TIME_S, TimeUnit.SECONDS);
            bluetoothAdapter.cancelDiscovery();

            mHostActivity.unregisterReceiver(endBroadcastReceiver);
            mHostActivity.unregisterReceiver(deviceFoundBroadcastReceiver);
            mHostActivity.unregisterReceiver(startBroadcastReceiver);

            if (startTime.get() == -1) {
                return "Could not start bluetooth scanning";
            } else if (endTime.get() == -1) {
                return "Bluetooth scanning did not stop in time";
            }
            return "Discovery took: "
                    + TimeUnit.MILLISECONDS.toSeconds(endTime.get() - startTime.get())
                    + "s and found "
                    + devices.size()
                    + " devices.";
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Bluetooth Scan";
        }
    }

    /** Bluetooth LE scanning task. */
    private final class LeScanningTask extends Task {
        private static final int BTLE_SCAN_TIME_S = 20;

        @NonNull
        @Override
        protected String execute() throws Exception {
            final CountDownLatch completedLatch = new CountDownLatch(1);

            final AtomicBoolean error = new AtomicBoolean(false);
            final List<ScanResult> scanResults = new ArrayList<>();

            if (!mHostActivity
                    .getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                return "Bluetooth LE not available!";
            }

            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                return "Bluetooth adapter disabled!";
            }

            BluetoothLeScanner leScanner = bluetoothAdapter.getBluetoothLeScanner();
            ScanCallback callback =
                    new ScanCallback() {
                        @Override
                        public void onScanResult(int callbackType, ScanResult result) {
                            scanResults.add(result);
                        }

                        @Override
                        public void onBatchScanResults(List<ScanResult> results) {
                            scanResults.addAll(results);
                        }

                        @Override
                        public void onScanFailed(int errorCode) {
                            completedLatch.countDown();
                            error.set(true);
                        }
                    };
            leScanner.startScan(
                    null,
                    new ScanSettings.Builder().setScanMode(SCAN_MODE_LOW_LATENCY).build(),
                    callback);
            completedLatch.await(BTLE_SCAN_TIME_S, TimeUnit.SECONDS);
            leScanner.stopScan(callback);

            return "There are " + scanResults.size() + " scan results.";
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Bluetooth LE Scan";
        }
    }
}
