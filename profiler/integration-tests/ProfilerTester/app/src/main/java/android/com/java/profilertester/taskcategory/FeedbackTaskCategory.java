package android.com.java.profilertester.taskcategory;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import java.util.Arrays;
import java.util.List;

public class FeedbackTaskCategory extends TaskCategory {
    @NonNull private final Activity mHostActivity;

    @NonNull
    private final List<? extends Task> mTasks =
            Arrays.asList(new HapticsTask(), new MotionSensorsTask());

    public FeedbackTaskCategory(@NonNull Activity hostActivity) {
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
        return "User Feedback";
    }

    @Override
    protected boolean shouldRunTask(@NonNull Task taskToRun) {
        Vibrator vibrator = (Vibrator) mHostActivity.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && (ActivityCompat.checkSelfPermission(
                                mHostActivity.getApplicationContext(),
                                Manifest.permission.BODY_SENSORS)
                        != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(
                    mHostActivity,
                    new String[] {Manifest.permission.BODY_SENSORS},
                    ActivityRequestCodes.BODY_SENSORS.ordinal());
            return false;
        }

        return true;
    }

    private final class HapticsTask extends Task {
        @NonNull
        @Override
        protected String execute() {
            Vibrator vibrator = (Vibrator) mHostActivity.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null) {
                return "Somehow could not get Vibrator service!";
            }

            vibrator.vibrate(DEFAULT_TASK_TIME_MS);
            try {
                Thread.sleep(DEFAULT_TASK_TIME_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            vibrator.cancel();
            return "Vibration stopped.";
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Shake Phone";
        }
    }

    private final class MotionSensorsTask extends Task implements SensorEventListener {
        private final float[] mAcceleration = new float[3];
        private volatile int mNumUpdates = 0;

        @NonNull
        @Override
        protected String execute() {
            mNumUpdates = 0;
            SensorManager manager =
                    (SensorManager) mHostActivity.getSystemService(Context.SENSOR_SERVICE);
            if (manager == null) {
                return "Could not get SensorManager!";
            }
            manager.registerListener(
                    this,
                    manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_GAME);
            try {
                Thread.sleep(DEFAULT_TASK_TIME_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            return "Number of accelerometer updates: " + mNumUpdates;
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Motion Detection";
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                //noinspection NonAtomicOperationOnVolatileField
                mNumUpdates++;
                mAcceleration[0] = event.values[0];
                mAcceleration[1] = event.values[1];
                mAcceleration[2] = event.values[2];
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    }
}
