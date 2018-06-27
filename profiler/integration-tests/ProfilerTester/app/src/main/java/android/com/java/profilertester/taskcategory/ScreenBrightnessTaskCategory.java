package android.com.java.profilertester.taskcategory;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Window;
import android.view.WindowManager;
import java.util.Arrays;
import java.util.List;

public class ScreenBrightnessTaskCategory extends TaskCategory {
    private final List<? extends Task> mTasks =
            Arrays.asList(
                    new ScreenBrightnessTask("Minimize Screen Brightness", 0),
                    new ScreenBrightnessTask("Maximize Screen Brightness", 255));

    @NonNull private final Activity mHostActivity;

    private int mOldBrightnessMode;
    private int mOldBrightness;

    public ScreenBrightnessTaskCategory(@NonNull Activity hostActivity) {
        mHostActivity = hostActivity;
    }

    @NonNull
    @Override
    public List<? extends Task> getTasks() {
        return mTasks;
    }

    @Override
    protected boolean shouldRunTask(@NonNull Task taskToRun) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.System.canWrite(mHostActivity)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + mHostActivity.getPackageName()));
            mHostActivity.startActivityForResult(
                    intent, ActivityRequestCodes.WRITE_SETTINGS.ordinal());
            return !Settings.System.canWrite(mHostActivity);
        }

        try {
            ContentResolver contentResolver = mHostActivity.getContentResolver();
            mOldBrightnessMode =
                    Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE);
            mOldBrightness =
                    Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS);
        } catch (Settings.SettingNotFoundException ignored) {
            return false;
        }

        return true;
    }

    @NonNull
    @Override
    public RequestCodePermissions getPermissionsRequired(@NonNull Task taskToRun) {
        return new RequestCodePermissions(
                new String[] {Manifest.permission.WRITE_SETTINGS},
                ActivityRequestCodes.WRITE_SETTINGS);
    }

    @NonNull
    @Override
    protected String getCategoryName() {
        return "Screen Brightness";
    }

    private class ScreenBrightnessTask extends Task {
        @NonNull private final String mTaskName;
        private final int
                mBrightness; // Brightness setting between [0, 255], where 0 is min and 255 is max.

        private ScreenBrightnessTask(@NonNull String taskName, int brightness) {
            mTaskName = taskName;
            mBrightness = brightness;
            if (mBrightness < 0 || mBrightness > 255) {
                throw new RuntimeException("Brightness needs to be [0, 255]!");
            }
        }

        /** This method needs to be executed on the main thread. */
        @Override
        public void preExecute() {
            ContentResolver contentResolver = mHostActivity.getContentResolver();
            Window window = mHostActivity.getWindow();

            // Set task brightness.
            Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, mBrightness);
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.screenBrightness = (float) mBrightness / 255.0f;
            window.setAttributes(layoutParams);
        }

        @Nullable
        @Override
        protected String execute() {
            try {
                Thread.sleep(DEFAULT_TASK_TIME_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return "Brightness returning to normal...";
        }

        /** This method needs to be executed on the main thread. */
        @Override
        public void postExecute() {
            ContentResolver contentResolver = mHostActivity.getContentResolver();
            Window window = mHostActivity.getWindow();

            // Reset brightness.
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.screenBrightness = (float) mOldBrightness / 255.0f;
            window.setAttributes(layoutParams);
            Settings.System.putInt(
                    contentResolver, Settings.System.SCREEN_BRIGHTNESS, mOldBrightness);
            Settings.System.putInt(
                    contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mOldBrightnessMode);
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return mTaskName;
        }
    }
}
