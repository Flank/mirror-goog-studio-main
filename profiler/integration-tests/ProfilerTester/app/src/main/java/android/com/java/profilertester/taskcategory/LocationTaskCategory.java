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
import android.support.v4.app.ActivityCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class LocationTaskCategory extends TaskCategory {
    @NonNull private final Activity mHostActivity;
    @NonNull private final Looper mLooper;

    public LocationTaskCategory(@NonNull Activity hostActivity, @NonNull Looper looper) {
        mHostActivity = hostActivity;
        mLooper = looper;
    }

    @NonNull
    @Override
    public List<? extends Task> getTasks() {
        return Arrays.asList(
                new LocationUpdateTask(
                        "Coarse location",
                        "Update coarse location",
                        LocationManager.NETWORK_PROVIDER),
                new LocationUpdateTask(
                        "Fine location", "Update fine location", LocationManager.GPS_PROVIDER));
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
                    new String[] {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION},
                    ActivityRequestCodes.LOCATION.ordinal());
            return false;
        }

        return true;
    }

    private final class LocationUpdateTask extends Task {
        @NonNull private final String mTaskName;
        @NonNull private final String mTaskDescription;
        @NonNull private final String mLocationServiceProvider;

        private LocationUpdateTask(
                @NonNull String taskName,
                @NonNull String taskDescription,
                @NonNull String locationServiceProvider) {
            mTaskName = taskName;
            mTaskDescription = taskDescription;
            mLocationServiceProvider = locationServiceProvider;
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
                        @Override
                        public void onLocationChanged(Location location) {
                            times.add(System.currentTimeMillis());
                            locations.add(location);
                        }

                        @Override
                        public void onStatusChanged(String provider, int status, Bundle extras) {}

                        @Override
                        public void onProviderEnabled(String provider) {}

                        @Override
                        public void onProviderDisabled(String provider) {}
                    };
            manager.requestLocationUpdates(getProvider(), 0, 0, locationListener, mLooper);

            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(30));
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
}
