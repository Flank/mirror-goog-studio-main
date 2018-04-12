/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.profiler.support.energy;

import android.app.Activity;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.PersistableBundle;
import com.android.tools.profiler.support.energy.gms.FusedLocationProviderClientWrapper;
import com.android.tools.profiler.support.util.StudioLog;

/**
 * A set of helpers for Android {@link PendingIntent} instrumentation, used by the Energy Profiler.
 */
@SuppressWarnings("unused") // Used by native instrumentation code.
public final class PendingIntentWrapper {
    /** Bundle extra key referring to a LocationResult parcelable. */
    private static final String EXTRA_LOCATION_RESULT =
            "com.google.android.gms.location.EXTRA_LOCATION_RESULT";

    private static final String LOCATION_RESULT_CLASS_NAME =
            "com.google.android.gms.location.LocationResult";

    private static final ThreadLocal<Intent> intentData = new ThreadLocal<Intent>();
    private static final PendingIntentMap intentMap = new PendingIntentMap();

    /**
     * EntryHook for {@link PendingIntent#getActivity(Context, int, Intent, int, Bundle)} to capture
     * the {@link Intent} used to create a {@link PendingIntent} that starts an {@link
     * android.app.Activity}.
     *
     * @param context the context parameter passed to the original method.
     * @param requestCode the requestCode parameter passed to the original method.
     * @param intent the intent parameter passed to the original method.
     * @param flags the flags parameter passed to the original method.
     * @param options the options parameter passed to the original method.
     */
    public static void onGetActivityEntry(
            Context context, int requestCode, Intent intent, int flags, Bundle options) {
        intentData.set(intent);
    }

    /**
     * ExistHook for {@link PendingIntent#getActivity(Context, int, Intent, int, Bundle)} to map the
     * {@link Intent} to the created {@link PendingIntent}.
     *
     * @param pendingIntent the pendingIntent returned by the original method.
     * @return the same {@link PendingIntent} as the original method.
     */
    public static PendingIntent onGetActivityExit(PendingIntent pendingIntent) {
        intentMap.putIntent(intentData.get(), pendingIntent);
        return pendingIntent;
    }

    /**
     * EntryHook for {@link PendingIntent#getService(Context, int, Intent, int)} to capture the
     * {@link Intent} used to create a {@link PendingIntent} that starts an {@link
     * android.app.Service}.
     *
     * @param context the context parameter passed to the original method.
     * @param requestCode the requestCode parameter passed to the original method.
     * @param intent the intent parameter passed to the original method.
     * @param flags the flags parameter passed to the original method.
     */
    public static void onGetServiceEntry(
            Context context, int requestCode, Intent intent, int flags) {
        intentData.set(intent);
    }

    /**
     * ExistHook for {@link PendingIntent#getService(Context, int, Intent, int)} to map the {@link
     * Intent} to the created {@link PendingIntent}.
     *
     * @param pendingIntent the pendingIntent returned by the original method.
     * @return the same {@link PendingIntent} as the original method.
     */
    public static PendingIntent onGetServiceExit(PendingIntent pendingIntent) {
        intentMap.putIntent(intentData.get(), pendingIntent);
        return pendingIntent;
    }

    /**
     * EntryHook for {@link PendingIntent#getBroadcast(Context, int, Intent, int)} to capture the
     * {@link Intent} used to create a {@link PendingIntent} that starts an {@link
     * android.content.BroadcastReceiver}.
     *
     * @param context the context parameter passed to the original method.
     * @param requestCode the requestCode parameter passed to the original method.
     * @param intent the intent parameter passed to the original method.
     * @param flags the flags parameter passed to the original method.
     */
    public static void onGetBroadcastEntry(
            Context context, int requestCode, Intent intent, int flags) {
        intentData.set(intent);
    }

    /**
     * ExistHook for {@link PendingIntent#getBroadcast(Context, int, Intent, int)} to map the {@link
     * Intent} to the created {@link PendingIntent}.
     *
     * @param pendingIntent the pendingIntent returned by the original method.
     * @return the same {@link PendingIntent} as the original method.
     */
    public static PendingIntent onGetBroadcastExit(PendingIntent pendingIntent) {
        intentMap.putIntent(intentData.get(), pendingIntent);
        return pendingIntent;
    }

    /**
     * EntryHook for {@link Activity#onCreate(Bundle, PersistableBundle)} to capture Activity
     * Intent.
     *
     * @param activity the wrapped {@link Activity} instance, i.e. "this".
     * @param savedInstance the savedInstance parameter passed to the original method.
     * @param persistableState the persistableState parameter passed to the original method.
     */
    public static void wrapActivityCreate(
            Activity activity, Bundle savedInstance, PersistableBundle persistableState) {
        handleIntent(activity.getIntent());
    }

    /**
     * EntryHook for {@link IntentService#onStartCommand(Intent, int, int)} to capture Service
     * Intent.
     *
     * @param intentService the wrapped {@link IntentService} instance, i.e. "this".
     * @param intent the intent parameter passed to the original method.
     * @param flags the flags parameter passed to the original method.
     * @param startId the startId parameter passed to the original method.
     */
    public static void wrapServiceStart(
            IntentService intentService, Intent intent, int flags, int startId) {
        handleIntent(intent);
    }
    /**
     * Detour hook for {@link BroadcastReceiver#onReceive(Context, Intent)} to capture
     * BroadcastReceiver Intent.
     *
     * @param receiver the wrapped {@link BroadcastReceiver} instance, i.e. "this".
     * @param context the context parameter passed to the original method.
     * @param intent the intent parameter passed to the original method.
     */
    public static void wrapBroadcastReceive(
            BroadcastReceiver receiver, Context context, Intent intent) {
        handleIntent(intent);
        receiver.onReceive(context, intent);
    }

    private static void handleIntent(Intent intent) {
        PendingIntent pendingIntent = intentMap.getPendingIntent(intent);
        if (intent.getIntExtra(Intent.EXTRA_ALARM_COUNT, 0) != 0) {
            // Alarm-fired event.
            AlarmManagerWrapper.sendIntentAlarmFiredIfExists(pendingIntent);
        } else if (intent.hasExtra(LocationManager.KEY_LOCATION_CHANGED)) {
            // Location-changed event.
            Location location = intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED);
            LocationManagerWrapper.sendIntentLocationChangedIfExists(pendingIntent, location);
        } else if (intent.hasExtra(EXTRA_LOCATION_RESULT)) {
            // GMS location-changed event.
            Object locationResult = intent.getParcelableExtra(EXTRA_LOCATION_RESULT);
            if (locationResult != null) {
                try {
                    Class<?> locationResultClass =
                            locationResult
                                    .getClass()
                                    .getClassLoader()
                                    .loadClass(LOCATION_RESULT_CLASS_NAME);
                    Location location =
                            (Location)
                                    locationResultClass
                                            .getMethod("getLastLocation")
                                            .invoke(locationResult);
                    FusedLocationProviderClientWrapper.sendIntentLocationChangedIfExists(
                            pendingIntent, location);
                } catch (Exception e) {
                    StudioLog.e("Could not send GMS LocationChanged event", e);
                }
            }
        }
    }
}
