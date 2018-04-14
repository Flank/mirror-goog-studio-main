package android.com.java.profilertester.taskcategory;

import static android.content.Context.ALARM_SERVICE;
import static android.content.Context.POWER_SERVICE;
import static android.os.PowerManager.PARTIAL_WAKE_LOCK;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class BackgroundTaskCategory extends TaskCategory {
    private static final String NUM_OF_WAKEUPS = "Number of Wakeups";

    // Repeating alarms have system-enforced minimum interval so we need a longer task time.
    private static final long ALARM_TASK_TIME_MS = TimeUnit.MINUTES.toMillis(2);

    @NonNull
    private final List<? extends Task> mTasks = Arrays.asList(new WakeLockTask(), new AlarmTask());

    @NonNull private final Activity mHostActivity;

    public BackgroundTaskCategory(@NonNull Activity hostActivity) {
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
        return "Background Tasks";
    }

    private final class WakeLockTask extends Task {
        @NonNull
        @Override
        protected String execute() {
            PowerManager powerManager =
                    (PowerManager) mHostActivity.getSystemService(POWER_SERVICE);
            if (powerManager == null) {
                return "Could not acquire the PowerManager!";
            }
            PowerManager.WakeLock wakeLock =
                    powerManager.newWakeLock(
                            PARTIAL_WAKE_LOCK, mHostActivity.getPackageName() + ":WakeLockTaskTag");
            wakeLock.acquire(LONG_TASK_TIME_MS);
            try {
                Thread.sleep(LONG_TASK_TIME_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            return "Released wake lock.";
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Wake Lock";
        }
    }

    private final class AlarmTask extends Task {
        @NonNull
        @Override
        protected String execute() {
            Context context = mHostActivity.getApplicationContext();
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
            if (alarmManager == null) {
                return "Error setting alarm!";
            }
            Intent intent = new Intent(context, AlarmReceiver.class);
            PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(
                            mHostActivity.getApplicationContext(),
                            ActivityRequestCodes.ALARM.ordinal(),
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
            // As of API 22, system enforces a minimum of 1 minute interval and at least 5 seconds
            // before the initial trigger.
            alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5),
                    TimeUnit.MINUTES.toMillis(1),
                    pendingIntent);
            if (pendingIntent == null) {
                return "Error setting alarm!";
            }

            try {
                Thread.sleep(ALARM_TASK_TIME_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }

            alarmManager.cancel(pendingIntent);
            return "Alarms cancelled normally.";
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Alarm";
        }
    }

    public static final class AlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            intent.putExtra(NUM_OF_WAKEUPS, intent.getIntExtra(NUM_OF_WAKEUPS, 0) + 1);
        }
    }
}
