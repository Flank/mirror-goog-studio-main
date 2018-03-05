package android.com.java.profilertester.taskcategory;

import android.Manifest;
import android.app.Activity;
import android.com.java.profilertester.R;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("ALL")
public final class CameraTaskCategory extends TaskCategory {
    private final Activity mHostActivity;
    private final List<? extends Task> mTasks =
            Arrays.asList(new RearCameraTask(), new FrontCameraTask(), new FlashlightTask());

    public CameraTaskCategory(@NonNull Activity activity) {
        mHostActivity = activity;
    }

    @NonNull
    @Override
    public List<? extends Task> getTasks() {
        return mTasks;
    }

    @NonNull
    @Override
    protected String getCategoryName() {
        return "Camera";
    }

    @Override
    protected boolean shouldRunTask(@NonNull Task taskToRun) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && (ActivityCompat.checkSelfPermission(
                                mHostActivity.getApplicationContext(), Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(
                    mHostActivity,
                    new String[] {Manifest.permission.CAMERA},
                    ActivityRequestCodes.CAMERA.ordinal());
            return false;
        }

        return true;
    }

    private abstract class CameraTask extends Task implements SurfaceHolder.Callback {
        @Nullable
        @Override
        protected final String execute() throws Exception {
            SurfaceView preview = (SurfaceView) mHostActivity.findViewById(R.id.preview_surface);
            SurfaceHolder holder = preview.getHolder();
            holder.addCallback(this);

            Camera camera = getCamera();
            if (camera == null) {
                return "No camera available for task!";
            }

            camera.setPreviewDisplay(holder);
            Camera.Parameters params = camera.getParameters();
            editParameters(params);
            camera.setParameters(params);
            camera.startPreview();
            try {
                Thread.sleep(DEFAULT_TASK_TIME_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            restoreParameters(params);
            camera.setParameters(params);
            camera.stopPreview();
            camera.release();
            return "Camera task finished.";
        }

        @Nullable
        protected abstract Camera getCamera();

        @NonNull
        protected void editParameters(@NonNull Camera.Parameters parameters) {}

        @NonNull
        protected void restoreParameters(@NonNull Camera.Parameters parameters) {}

        @Override
        public void surfaceCreated(SurfaceHolder holder) {}

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {}
    }

    private final class FrontCameraTask extends CameraTask {
        @Nullable
        @Override
        protected Camera getCamera() {
            int numCameras = Camera.getNumberOfCameras();
            int frontCameraId = -1;
            for (int i = 0; i < numCameras; i++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    frontCameraId = i;
                    break;
                }
            }

            if (frontCameraId == -1) {
                return null;
            }

            return Camera.open(frontCameraId);
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Enable Front Camera";
        }
    }

    private final class RearCameraTask extends CameraTask {
        @Nullable
        @Override
        protected Camera getCamera() {
            return Camera.open(); // open() with no params access the first rear-facing camera.
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Enable Rear Camera";
        }
    }

    private final class FlashlightTask extends CameraTask {
        @Nullable
        @Override
        protected Camera getCamera() {
            return Camera.open(); // open() with no params access the first rear-facing camera.
        }

        @NonNull
        @Override
        protected void editParameters(@NonNull Camera.Parameters parameters) {
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        }

        @NonNull
        @Override
        protected void restoreParameters(@NonNull Camera.Parameters parameters) {
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Enable Flashlight";
        }
    }
}
