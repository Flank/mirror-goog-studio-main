package android.com.java.profilertester.taskcategory;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
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
import android.support.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class BackgroundTaskCategory extends TaskCategory {
    private static final String NUM_OF_ALARMS = "Number of Alarms";
    private static final String NUM_OF_WAKEUPS = "Number of Wakeups";

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
            Intent intent = new Intent(mHostActivity.getApplicationContext(), AlarmReceiver.class);
            intent.putExtra(NUM_OF_ALARMS, 20); // Enough alarms for once a second until we cancel.
            PendingIntent pendingIntent =
                    AlarmReceiver.setAlarm(mHostActivity.getApplicationContext(), intent);
            if (pendingIntent == null) {
                return "Error setting alarm!";
            }

            try {
                Thread.sleep(LONG_TASK_TIME_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }

            AlarmReceiver.cancelAlarm(mHostActivity.getApplicationContext(), pendingIntent);
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
            int numRemainingAlarms = intent.getIntExtra(NUM_OF_ALARMS, 1) - 1;
            intent.putExtra(NUM_OF_ALARMS, numRemainingAlarms);
            if (numRemainingAlarms > 0) {
                setAlarm(context, intent);
            }
        }

        /** @return the created {@link PendingIntent}. */
        @Nullable
        private static PendingIntent setAlarm(@NonNull Context context, @NonNull Intent intent) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
            if (alarmManager == null) {
                return null;
            }
            PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(
                            context,
                            ActivityRequestCodes.ALARM.ordinal(),
                            intent,
                            FLAG_UPDATE_CURRENT);
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(1),
                    pendingIntent);
            return pendingIntent;
        }

        private static void cancelAlarm(
                @NonNull Context context, @NonNull PendingIntent pendingIntent) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
            if (alarmManager == null) {
                return;
            }
            alarmManager.cancel(pendingIntent);
        }
    }
}
