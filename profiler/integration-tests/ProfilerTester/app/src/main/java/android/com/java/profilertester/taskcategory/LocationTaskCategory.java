package android.com.java.profilertester.taskcategory;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.widget.Toast;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class LocationTaskCategory extends TaskCategory {
    @NonNull
    private final Activity mHostActivity;
    @NonNull
    private final Looper mLooper;

    @NonNull private final List<? extends Task> mTasks;

    public LocationTaskCategory(@NonNull Activity hostActivity, @NonNull Looper looper) {
        mHostActivity = hostActivity;
        mLooper = looper;
        mTasks =
                Arrays.asList(
                        new LocationUpdateTask(
                                "Coarse location",
                                "Update coarse location",
                                LocationManager.NETWORK_PROVIDER,
                                0),
                        new LocationUpdateTask(
                                "Fine location slow update",
                                "Update fine location with long interval",
                                LocationManager.GPS_PROVIDER,
                                TimeUnit.SECONDS.toMillis(30)),
                        new LocationUpdateTask(
                                "Fine location fast update",
                                "Update fine location with minimal interval",
                                LocationManager.GPS_PROVIDER,
                                0),
                        new GooglePlayServiceGetLastLocationTask(
                                "Location from Google API",
                                "Get location with Google API",
                                mHostActivity),
                        new GooglePlayServicePeriodicallyUpdateLocationTask(
                                "Periodic updates with Google API",
                                "Request location periodically with Google API",
                                mHostActivity,
                                mLooper));
    }

    @NonNull
    @Override
    public List<? extends Task> getTasks() {
        return mTasks;
    }

    @NonNull
    @Override
    protected String getCategoryName() {
        return "Location";
    }

    @Override
    protected boolean shouldRunTask(@NonNull Task taskToRun) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && (ActivityCompat.checkSelfPermission(
                mHostActivity.getApplicationContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(
                mHostActivity.getApplicationContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(
                    mHostActivity,
                    new String[]{ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION},
                    ActivityRequestCodes.LOCATION.ordinal());
            return false;
        }

        return true;
    }

    private final class LocationUpdateTask extends Task {
        private static final int MIN_SAMPLES = 2;

        @NonNull
        private final String mTaskName;
        @NonNull
        private final String mTaskDescription;
        @NonNull
        private final String mLocationServiceProvider;
        private final long mMinTimeMs;

        private LocationUpdateTask(
                @NonNull String taskName,
                @NonNull String taskDescription,
                @NonNull String locationServiceProvider,
                long minTimeMs) {
            mTaskName = taskName;
            mTaskDescription = taskDescription;
            mLocationServiceProvider = locationServiceProvider;
            mMinTimeMs = minTimeMs;
        }

        @NonNull
        @Override
        protected final String execute() {
            LocationManager manager =
                    (LocationManager)
                            mHostActivity
                                    .getApplicationContext()
                                    .getSystemService(Context.LOCATION_SERVICE);
            if (manager == null) {
                return "Could not acquire LocationManager!";
            }

            if (ActivityCompat.checkSelfPermission(
                    mHostActivity.getApplicationContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(
                    mHostActivity.getApplicationContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                return "Could not acquire both coarse and fine location permission!";
            }

            final List<Location> locations = new ArrayList<>();
            final List<Long> times = new ArrayList<>();
            long startTime = System.currentTimeMillis();
            LocationListener locationListener =
                    new LocationListener() {
                        int mTimes = 0;

                        @Override
                        public void onLocationChanged(Location location) {
                            times.add(System.currentTimeMillis());
                            locations.add(location);
                            mTimes++;
                            Toast.makeText(
                                    mHostActivity,
                                    String.format("Location updated (%d times)", mTimes),
                                    Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onStatusChanged(String provider, int status, Bundle extras) {
                        }

                        @Override
                        public void onProviderEnabled(String provider) {
                        }

                        @Override
                        public void onProviderDisabled(String provider) {
                        }
                    };
            manager.requestLocationUpdates(getProvider(), mMinTimeMs, 0, locationListener, mLooper);

            try {
                Thread.sleep(Math.max(TimeUnit.SECONDS.toMillis(30), mMinTimeMs * MIN_SAMPLES));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return getTaskName() + " update interrupted!";
            } finally {
                manager.removeUpdates(locationListener);
            }
            return getTaskName()
                    + " update completed, changed "
                    + locations.size()
                    + " times"
                    + (times.size() > 0
                    ? ", last one at " + (times.get(times.size() - 1) - startTime)
                    : "")
                    + ".";
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return mTaskDescription;
        }

        @NonNull
        private String getProvider() {
            return mLocationServiceProvider;
        }

        @NonNull
        private String getTaskName() {
            return mTaskName;
        }
    }

    private final class GooglePlayServiceGetLastLocationTask extends Task {
        @NonNull private final String mTaskName;
        @NonNull private final String mTaskDescription;
        @NonNull private final Activity mHostActivity;

        private GooglePlayServiceGetLastLocationTask(
                @NonNull String taskName,
                @NonNull String taskDescription,
                @NonNull Activity activity) {
            mTaskName = taskName;
            mTaskDescription = taskDescription;
            mHostActivity = activity;
        }

        @Nullable
        @Override
        protected String execute() throws Exception {
            if (ActivityCompat.checkSelfPermission(
                                    mHostActivity.getApplicationContext(),
                                    Manifest.permission.ACCESS_FINE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(
                                    mHostActivity.getApplicationContext(),
                                    Manifest.permission.ACCESS_COARSE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {
                return "Could not acquire both coarse and fine location permission!";
            }

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            FusedLocationProviderClient locationProviderClient =
                    LocationServices.getFusedLocationProviderClient(mHostActivity);
            com.google.android.gms.tasks.Task<Location> locationTask =
                    locationProviderClient
                            .getLastLocation()
                            .addOnCompleteListener(
                                    new OnCompleteListener<Location>() {
                                        @Override
                                        public void onComplete(
                                                @NonNull
                                                        com.google.android.gms.tasks.Task<Location>
                                                                task) {
                                            countDownLatch.countDown();
                                        }
                                    });
            countDownLatch.await();
            Location location = locationTask.getResult();
            return mTaskName + " update completed " + location.toString();
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return mTaskDescription;
        }
    }

    private final class GooglePlayServicePeriodicallyUpdateLocationTask extends Task {
        static final int UPDATE_NUMBER = 3;
        static final int UPDATE_INTERVAL_MS = 5000;

        @NonNull private final String mTaskName;
        @NonNull private final String mTaskDescription;
        @NonNull private final Activity mHostActivity;
        @NonNull private final Looper mLooper;

        private GooglePlayServicePeriodicallyUpdateLocationTask(
                @NonNull String taskName,
                @NonNull String taskDescription,
                @NonNull Activity activity,
                @NonNull Looper looper) {
            mTaskName = taskName;
            mTaskDescription = taskDescription;
            mHostActivity = activity;
            mLooper = looper;
        }

        @Nullable
        @Override
        protected String execute() throws Exception {
            if (ActivityCompat.checkSelfPermission(
                                    mHostActivity.getApplicationContext(),
                                    Manifest.permission.ACCESS_FINE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(
                                    mHostActivity.getApplicationContext(),
                                    Manifest.permission.ACCESS_COARSE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {
                return "Could not acquire both coarse and fine location permission!";
            }

            FusedLocationProviderClient locationProviderClient =
                    LocationServices.getFusedLocationProviderClient(mHostActivity);

            final List<Location> locations = new ArrayList<>();
            final CountDownLatch countDownLatch = new CountDownLatch(UPDATE_NUMBER);
            LocationCallback locationCallback =
                    new LocationCallback() {
                        @Override
                        public void onLocationResult(LocationResult result) {
                            locations.add(result.getLastLocation());
                            countDownLatch.countDown();
                        }
                    };

            LocationRequest locationRequest =
                    LocationRequest.create().setInterval(UPDATE_INTERVAL_MS);

            locationProviderClient.requestLocationUpdates(
                    locationRequest, locationCallback, mLooper);
            countDownLatch.await(UPDATE_INTERVAL_MS * (UPDATE_NUMBER + 1), TimeUnit.SECONDS);

            locationProviderClient.removeLocationUpdates(new LocationCallback());

            return mTaskName
                    + " update completed, changed "
                    + locations.size()
                    + " times"
                    + (locations.size() > 0
                            ? ", last one at " + (locations.get(locations.size() - 1))
                            : "")
                    + ".";
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return mTaskDescription;
        }
    }
}
