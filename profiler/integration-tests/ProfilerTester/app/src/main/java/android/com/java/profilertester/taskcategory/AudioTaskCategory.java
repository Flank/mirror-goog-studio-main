package android.com.java.profilertester.taskcategory;

import android.Manifest;
import android.app.Activity;
import android.com.java.profilertester.R;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public final class AudioTaskCategory extends TaskCategory {
    private final List<? extends Task> mTasks =
            Arrays.asList(new PlaybackTask(), new RecordingTask());

    @NonNull private final Activity mHostActivity;

    public AudioTaskCategory(@NonNull Activity hostActivity) {
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
        return "Audio";
    }

    @Override
    protected boolean shouldRunTask(@NonNull Task taskToRun) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && (ActivityCompat.checkSelfPermission(
                                mHostActivity.getApplicationContext(),
                                Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(
                    mHostActivity,
                    new String[] {Manifest.permission.RECORD_AUDIO},
                    ActivityRequestCodes.MICROPHONE.ordinal());
            return false;
        }

        return true;
    }

    private final class PlaybackTask extends Task {
        @NonNull
        @Override
        protected String execute() {
            MediaPlayer mediaPlayer =
                    MediaPlayer.create(
                            mHostActivity.getApplicationContext(), R.raw.sample_ringtone);
            mediaPlayer.start();
            try {
                Thread.sleep(DEFAULT_TASK_TIME_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            mediaPlayer.stop();
            mediaPlayer.release();
            return "Audio finished playing";
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Audio Sample Playback";
        }
    }

    private final class RecordingTask extends Task {
        @NonNull
        @Override
        protected String execute() {
            String outputFileLocation =
                    mHostActivity.getExternalCacheDir().getAbsolutePath() + "/sample.mp4";
            MediaRecorder recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(1); // Mono channel, since we only have one mic.
            recorder.setAudioEncodingBitRate(160 * 1000); // 160 Kbps, CD-quality.
            recorder.setAudioSamplingRate(44100); // 44.1kHz, CD-quality.
            recorder.setOutputFile(outputFileLocation);
            try {
                recorder.prepare();
            } catch (IOException e) {
                e.printStackTrace();
                return "Could not prepare the recorder!";
            }
            recorder.start();
            try {
                Thread.sleep(DEFAULT_TASK_TIME_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            recorder.stop();
            return "Recording finished. File located at: " + outputFileLocation;
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Record Mic Audio";
        }
    }
}
