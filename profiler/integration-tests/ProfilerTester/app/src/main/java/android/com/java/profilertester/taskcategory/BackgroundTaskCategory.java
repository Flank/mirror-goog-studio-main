package android.com.java.profilertester.taskcategory;

import static android.content.Context.ALARM_SERVICE;
import static android.content.Context.JOB_SCHEDULER_SERVICE;
import static android.content.Context.POWER_SERVICE;
import static android.os.PowerManager.PARTIAL_WAKE_LOCK;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.util.Log;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class BackgroundTaskCategory extends TaskCategory {
    private static final String NUM_OF_WAKEUPS = "Number of Wakeups";

    // Repeating alarms have system-enforced minimum interval so we need a longer task time.
    private static final long ALARM_TASK_TIME_MS = TimeUnit.MINUTES.toMillis(2);

    @NonNull
    private final List<? extends Task> mTasks =
            Arrays.asList(
                    new WakeLockTask(),
                    new AlarmTask(),
                    new SingleJobTask(),
                    new PeriodicJobTask());

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

    public static class MyJobService extends JobService {
        private final String TAG = MyJobService.class.getSimpleName();

        @SuppressLint("UseSparseArrays")
        @NonNull
        private Map<Integer, MyServiceTask> mServiceTaskMap = new HashMap<>();

        @NonNull private ThreadPoolExecutor mThreadPoolExecutor = getDefaultThreadPoolExecutor();

        @Override
        public boolean onStartJob(JobParameters jobParameters) {
            Log.d(TAG, "Job started with ID: " + jobParameters.getJobId());
            MyServiceTask task = new MyServiceTask();
            mServiceTaskMap.put(jobParameters.getJobId(), task);
            task.executeOnExecutor(mThreadPoolExecutor, jobParameters);
            return true;
        }

        @Override
        public boolean onStopJob(JobParameters jobParameters) {
            Log.d(TAG, "Job stopped with ID: " + jobParameters.getJobId());
            MyServiceTask task = mServiceTaskMap.get(jobParameters.getJobId());
            task.cancel(true);
            return false;
        }

        @SuppressLint("StaticFieldLeak")
        private class MyServiceTask extends AsyncTask<JobParameters, Void, Void> {
            @Override
            public Void doInBackground(JobParameters... parameters) {
                Log.d(TAG, "Job running with ID: " + parameters[0].getJobId());
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(2L));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                jobFinished(parameters[0], false);
                return null;
            }
        }

        private static ThreadPoolExecutor getDefaultThreadPoolExecutor() {
            ThreadPoolExecutor threadPoolExecutor =
                    new ThreadPoolExecutor(
                            4,
                            128,
                            30,
                            TimeUnit.SECONDS,
                            new LinkedBlockingQueue<Runnable>(128),
                            new ThreadFactory() {
                                private final AtomicInteger mCount = new AtomicInteger(1);

                                public Thread newThread(@NonNull Runnable r) {
                                    return new Thread(
                                            r, "JobAsyncTask #" + mCount.getAndIncrement());
                                }
                            });
            threadPoolExecutor.allowCoreThreadTimeOut(true);
            return threadPoolExecutor;
        }
    }

    private abstract class JobTask extends Task {

        @NonNull
        @Override
        protected String execute() {
            Context context = mHostActivity.getApplicationContext();
            ComponentName componentName = new ComponentName(context, MyJobService.class);
            JobScheduler scheduler = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);
            if (scheduler == null) {
                return "Error setting job scheduler!";
            }

            scheduler.schedule(createJob(componentName));

            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(6L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return getTaskDescription() + " interrupted!";
            } finally {
                scheduler.cancelAll();
            }
            return "Job finished successfully";
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Job";
        }

        protected abstract JobInfo createJob(ComponentName componentName);
    }

    private final class SingleJobTask extends JobTask {

        @Override
        protected JobInfo createJob(ComponentName componentName) {
            JobInfo.Builder builder = new JobInfo.Builder(0, componentName);
            builder.setOverrideDeadline(TimeUnit.SECONDS.toMillis(2));
            builder.setMinimumLatency(TimeUnit.SECONDS.toMillis(1));
            return builder.build();
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Single Job";
        }
    }

    private final class PeriodicJobTask extends JobTask {

        @Override
        protected JobInfo createJob(ComponentName componentName) {
            JobInfo.Builder builder = new JobInfo.Builder(1, componentName);
            // JobInfo has a 15 minute minimum period interval and we can not see
            // the next job scheduled.
            builder.setPeriodic(TimeUnit.MINUTES.toMillis(15));
            return builder.build();
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Periodic Job";
        }
    }
}
