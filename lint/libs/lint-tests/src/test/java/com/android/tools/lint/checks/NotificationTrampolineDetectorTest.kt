/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class NotificationTrampolineDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return NotificationTrampolineDetector()
    }

    // A broadcast receiver which launches another activity
    private val broadcastReceiver = java(
        """
        package test.pkg;

        import android.content.BroadcastReceiver;
        import android.content.Context;
        import android.content.Intent;

        public class BroadcastTrampoline extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                // The start below will be blocked
                Intent i = new Intent();
                i.setClassName("test.pkg", "test.pkg.SecondActivity");
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }
        }
        """
    ).indented()

    fun testBroadcastTrampolineJava() {
        lint().files(
            broadcastReceiver,
            java(
                """
                package test.pkg;

                import android.app.Notification;
                import android.app.NotificationManager;
                import android.app.PendingIntent;
                import android.content.Context;
                import android.content.Intent;
                import android.os.Build;

                import androidx.core.app.NotificationCompat;

                import static android.app.Notification.EXTRA_NOTIFICATION_ID;

                public class NotificationTest {
                    public static final String ACTION_LAUNCH =
                            "test.pkg.action.LAUNCH";

                    public void test(Context context, String channelId, int id, int requestCode, int flags) {
                        Intent notificationIntent = new Intent(context, test.pkg.BroadcastTrampoline.class);
                        PendingIntent notificationPendingIntent = PendingIntent.getBroadcast(context, requestCode, notificationIntent, flags);

                        Intent broadcastIntent = new Intent(context, BroadcastTrampoline.class);
                        broadcastIntent.setAction(ACTION_LAUNCH);
                        broadcastIntent.putExtra(EXTRA_NOTIFICATION_ID, id);
                        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                        PendingIntent broadcastPendingIntent =
                                PendingIntent.getBroadcast(context, 0, broadcastIntent, 0);

                        NotificationCompat.Builder builder =
                                new NotificationCompat.Builder(context, channelId)
                                        .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                                        .setContentTitle("Notification Trampoline Test")
                                        .setContentText("Tap this notification to launch a new receiver")
                                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                        .setContentIntent(notificationPendingIntent)
                                        .setAutoCancel(true)
                                        .addAction(android.R.drawable.ic_dialog_email, "Launch Receiver From Action",
                                                broadcastPendingIntent);
                        Notification notification = builder.build();
                        NotificationManager notificationManager =
                                context.getSystemService(NotificationManager.class);
                        notificationManager.notify(id, notification);
                    }
                }
                """
            ).indented(),
            *notificationStubs
        ).run().expect(
            """
            src/test/pkg/NotificationTest.java:36: Error: This intent launches a BroadcastReceiver (BroadcastTrampoline) which launches activities; this indirection is bad for performance, and activities should be launched directly from the notification [NotificationTrampoline]
                                    .setContentIntent(notificationPendingIntent)
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/BroadcastTrampoline.java:14: <No location-specific message>
                    context.startActivity(i);
                    ~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/NotificationTest.java:38: Error: This intent launches a BroadcastReceiver (BroadcastTrampoline) which launches activities; this indirection is bad for performance, and activities should be launched directly from the notification [NotificationTrampoline]
                                    .addAction(android.R.drawable.ic_dialog_email, "Launch Receiver From Action",
                                     ^
                src/test/pkg/BroadcastTrampoline.java:14: <No location-specific message>
                    context.startActivity(i);
                    ~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    fun testBroadcastTrampolineKotlin() {
        lint().files(
            broadcastReceiver,
            kotlin(
                """
                package test.pkg

                import android.R
                import android.app.Notification
                import android.app.NotificationManager
                import android.app.PendingIntent
                import android.content.Context
                import android.content.Intent
                import android.os.Build
                import androidx.core.app.NotificationCompat

                class NotificationTest {
                    fun test(context: Context, channelId: String?, id: Int, requestCode: Int, flags: Int) {
                        val notificationIntent = Intent(context, BroadcastTrampoline::class.java)
                        val notificationPendingIntent =
                            PendingIntent.getBroadcast(context, requestCode, notificationIntent, flags)
                        val broadcastIntent = Intent(context, BroadcastTrampoline::class.java)
                        broadcastIntent.action = ACTION_LAUNCH
                        broadcastIntent.putExtra(Notification.EXTRA_NOTIFICATION_ID, id)
                        notificationIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        val broadcastPendingIntent = PendingIntent.getBroadcast(context, 0, broadcastIntent, 0)
                        val builder = NotificationCompat.Builder(context, channelId!!)
                            .setSmallIcon(R.drawable.ic_menu_my_calendar)
                            .setContentTitle("Notification Trampoline Test")
                            .setContentText("Tap this notification to launch a new receiver")
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setContentIntent(notificationPendingIntent)
                            .setAutoCancel(true)
                            .addAction(
                                R.drawable.ic_dialog_email, "Launch Receiver From Action",
                                broadcastPendingIntent
                            )
                        val notification = builder.build()
                        val notificationManager = context.getSystemService(
                            NotificationManager::class.java
                        )
                        notificationManager.notify(id, notification)
                    }
                }
                """
            ).indented(),
            *notificationStubs
        ).run().expect(
            """
            src/test/pkg/NotificationTest.kt:27: Error: This intent launches a BroadcastReceiver (BroadcastTrampoline) which launches activities; this indirection is bad for performance, and activities should be launched directly from the notification [NotificationTrampoline]
                        .setContentIntent(notificationPendingIntent)
                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/BroadcastTrampoline.java:14: <No location-specific message>
                    context.startActivity(i);
                    ~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/NotificationTest.kt:29: Error: This intent launches a BroadcastReceiver (BroadcastTrampoline) which launches activities; this indirection is bad for performance, and activities should be launched directly from the notification [NotificationTrampoline]
                        .addAction(
                         ^
                src/test/pkg/BroadcastTrampoline.java:14: <No location-specific message>
                    context.startActivity(i);
                    ~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    fun testServiceTrampolineJava() {
        lint().files(
            java(
                """
                package test.pkg;
                import android.app.Notification;
                import android.app.NotificationManager;
                import android.app.PendingIntent;
                import android.content.Context;
                import android.content.Intent;
                import androidx.core.app.NotificationCompat;
                @SuppressWarnings("unused")
                public class NotificationTest {
                    public void testServices(Context context, String channelId, int id) {
                        NotificationManager notificationManager =
                                context.getSystemService(NotificationManager.class);
                        Intent notificationIntent = new Intent(context, ServiceTrampoline.class);
                        PendingIntent serviceIntent = PendingIntent.getService(context, 0, notificationIntent, 0);
                        NotificationCompat.Builder builder =
                                new NotificationCompat.Builder(context, channelId)
                                        .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                                        .setContentTitle("Notification Trampoline Test")
                                        .setContentText("Tap this notification to launch a new activity")
                                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                        .setContentIntent(serviceIntent)
                                        .setAutoCancel(true);
                        Notification notification = builder.build();
                        notificationManager.notify(id, notification);
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;
                import android.app.Service;
                import android.content.Intent;
                import android.os.IBinder;

                public class ServiceTrampoline extends Service {
                    private IBinder binder;
                    @Override
                    public int onStartCommand(Intent intent, int flags, int startId) {
                        intent.setAction(Intent.ACTION_VIEW);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                        return Service.START_STICKY;
                    }

                    @Override
                    public IBinder onBind(Intent intent) {
                        return binder;
                    }
                }
                """
            ).indented(),
            *notificationStubs
        ).run().expect(
            """
            src/test/pkg/NotificationTest.java:21: Error: This intent launches a Service (ServiceTrampoline) which launches activities; this indirection is bad for performance, and activities should be launched directly from the notification [NotificationTrampoline]
                                    .setContentIntent(serviceIntent)
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/ServiceTrampoline.java:13: <No location-specific message>
                    startActivity(intent);
                    ~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testLaunchUnknownService() {
        lint().files(
            broadcastReceiver,
            java(
                """
                package test.pkg;

                import android.app.Notification;
                import android.app.NotificationManager;
                import android.app.PendingIntent;
                import android.content.Context;
                import android.content.Intent;
                import androidx.core.app.NotificationCompat;
                @SuppressWarnings("unused")
                public class NotificationTest {
                    public void testServices(Context context, String channelId, int id) {
                        NotificationManager notificationManager =
                                context.getSystemService(NotificationManager.class);
                        Intent notificationIntent = new Intent(context, ServiceTrampoline.class);
                        PendingIntent serviceIntent = PendingIntent.getService(context, 0, notificationIntent, 0);
                        NotificationCompat.Builder builder =
                                new NotificationCompat.Builder(context, channelId)
                                        .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                                        .setContentTitle("Notification Trampoline Test")
                                        .setContentText("Tap this notification to launch a new service")
                                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                        .setContentIntent(serviceIntent)
                                        .setAutoCancel(true);
                        Notification notification = builder.build();
                        notificationManager.notify(id, notification);
                    }
                }
                """
            ).indented(),
            *notificationStubs
        ).run().expect(
            """
            src/test/pkg/NotificationTest.java:22: Warning: Notifications should only launch a Service from notification actions (addAction) [LaunchActivityFromNotification]
                                    .setContentIntent(serviceIntent)
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/NotificationTest.java:15: This Service intent is launched from a notification; this is discouraged except as notification actions
                    PendingIntent serviceIntent = PendingIntent.getService(context, 0, notificationIntent, 0);
                                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testTrampolineKotlinQualifiedNames() {
        // Similar to test1, but with a slightly different AST shape which requires
        // handling (such as a qualified name in a class literal, as well as
        // making sure we treat separated declarations and assignments the same
        // as a local variable with initializer).
        lint().files(
            broadcastReceiver,
            kotlin(
                """
                package test.pkg

                import android.R
                import android.app.Notification
                import android.app.NotificationManager
                import android.app.PendingIntent
                import android.content.Context
                import android.content.Intent
                import android.os.Build
                import androidx.core.app.NotificationCompat

                class NotificationTest {
                    fun test(context: Context, channelId: String?, id: Int, requestCode: Int, flags: Int) {
                        val notificationIntent = Intent(context, BroadcastTrampoline::class.java)
                        val notificationPendingIntent: PendingIntent
                        val broadcastIntent = Intent(context, test.pkg.BroadcastTrampoline::class.java)
                        notificationPendingIntent =
                            PendingIntent.getBroadcast(context, requestCode, notificationIntent, flags)
                        broadcastIntent.action = ACTION_LAUNCH
                        broadcastIntent.putExtra(Notification.EXTRA_NOTIFICATION_ID, id)
                        notificationIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        val broadcastPendingIntent = PendingIntent.getBroadcast(context, 0, broadcastIntent, 0)
                        val builder = NotificationCompat.Builder(context, channelId!!)
                            .setSmallIcon(R.drawable.ic_menu_my_calendar)
                            .setContentTitle("Notification Trampoline Test")
                            .setContentText("Tap this notification to launch a new activity")
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setContentIntent(notificationPendingIntent)
                            .setAutoCancel(true)
                            .addAction(
                                R.drawable.ic_dialog_email, "Launch From Action",
                                broadcastPendingIntent
                            )
                        val notification = builder.build()
                        val notificationManager = context.getSystemService(
                            NotificationManager::class.java
                        )
                        notificationManager.notify(id, notification)
                    }
                }
                """
            ).indented(),
            *notificationStubs
        ).run().expect(
            """
            src/test/pkg/NotificationTest.kt:28: Error: This intent launches a BroadcastReceiver (BroadcastTrampoline) which launches activities; this indirection is bad for performance, and activities should be launched directly from the notification [NotificationTrampoline]
                        .setContentIntent(notificationPendingIntent)
                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/BroadcastTrampoline.java:14: <No location-specific message>
                    context.startActivity(i);
                    ~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/NotificationTest.kt:30: Error: This intent launches a BroadcastReceiver (BroadcastTrampoline) which launches activities; this indirection is bad for performance, and activities should be launched directly from the notification [NotificationTrampoline]
                        .addAction(
                         ^
                src/test/pkg/BroadcastTrampoline.java:14: <No location-specific message>
                    context.startActivity(i);
                    ~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    fun testBroadcastUsage() {
        lint().files(
            broadcastReceiver,
            java(
                """
                package test.pkg;

                import android.app.Notification;
                import android.app.NotificationManager;
                import android.app.PendingIntent;
                import android.content.Context;
                import android.content.Intent;
                import android.os.Build;

                import androidx.core.app.NotificationCompat;

                import static android.app.Notification.EXTRA_NOTIFICATION_ID;

                public class NotificationTest {
                    public void test(Context context, String channelId, int id, int requestCode, int flags) {
                        Intent notificationIntent = new Intent(context, UnknownBroadcast.class);
                        PendingIntent notificationPendingIntent = PendingIntent.getBroadcast(context, requestCode, notificationIntent, flags);

                        NotificationCompat.Builder builder =
                                new NotificationCompat.Builder(context, channelId)
                                        .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                                        .setContentTitle("Notification Trampoline Test")
                                        .setContentText("Tap this notification to launch a new activity")
                                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                        .setContentIntent(notificationPendingIntent) // WARN
                                        .setAutoCancel(true)
                                        .addAction(android.R.drawable.ic_dialog_email, "Launch From Action",
                                                notificationPendingIntent); // OK
                    }
                }
                """
            ).indented(),
            *notificationStubs
        ).run().expect(
            """
            src/test/pkg/NotificationTest.java:25: Warning: Notifications should only launch a BroadcastReceiver from notification actions (addAction) [LaunchActivityFromNotification]
                                    .setContentIntent(notificationPendingIntent) // WARN
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/NotificationTest.java:17: This BroadcastReceiver intent is launched from a notification; this is discouraged except as notification actions
                    PendingIntent notificationPendingIntent = PendingIntent.getBroadcast(context, requestCode, notificationIntent, flags);
                                                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testPendingIntentFromMethod() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.Notification;
                import android.app.NotificationManager;
                import android.app.PendingIntent;
                import android.content.Context;
                import android.content.Intent;
                import androidx.core.app.NotificationCompat;
                @SuppressWarnings("unused")
                public class NotificationTest {
                    private PendingIntent createIntent() {
                        Intent notificationIntent = new Intent(context, ServiceTrampoline.class);
                        return PendingIntent.getService(context, 0, notificationIntent, 0);
                    }
                    public void testServices(Context context, String channelId, int id) {
                        NotificationManager notificationManager =
                                context.getSystemService(NotificationManager.class);
                        NotificationCompat.Builder builder =
                                new NotificationCompat.Builder(context, channelId)
                                        .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                                        .setContentTitle("Notification Trampoline Test")
                                        .setContentText("Tap this notification to launch a new service")
                                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                        .setContentIntent(createIntent())
                                        .setAutoCancel(true);
                        Notification notification = builder.build();
                        notificationManager.notify(id, notification);
                    }
                }
                """
            ).indented(),
            *notificationStubs
        ).run().expect(
            """
            src/test/pkg/NotificationTest.java:24: Warning: Notifications should only launch a Service from notification actions (addAction) [LaunchActivityFromNotification]
                                    .setContentIntent(createIntent())
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/NotificationTest.java:13: This Service intent is launched from a notification; this is discouraged except as notification actions
                    return PendingIntent.getService(context, 0, notificationIntent, 0);
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    private val notificationStubs = arrayOf(
        // NotificationCompat & Builder Stubs
        java(
            """
            package androidx.core.app;
            import android.app.Notification;
            import android.app.PendingIntent;
            import android.content.Context;
            public class NotificationCompat {
                public static final int PRIORITY_DEFAULT = 0;
                public static class Builder {
                    public Builder(Context context, String channel) { }
                    public NotificationCompat.Builder setSmallIcon(int icon) { return this; }
                    public NotificationCompat.Builder setContentTitle(String title) { return this; }
                    public NotificationCompat.Builder setContentText(String title) { return this; }
                    public NotificationCompat.Builder setPriority(int priority) { return this; }
                    public NotificationCompat.Builder setContentIntent(PendingIntent intent) { return this; }
                    public NotificationCompat.Builder setFullScreenIntent(PendingIntent intent, boolean highPriority) { return this; }
                    public NotificationCompat.Builder setAutoCancel(boolean auto) { return this; }
                    public NotificationCompat.Builder addAction(int icon, CharSequence title, PendingIntent intent) { return this; }
                    public Notification build() {
                        return null;
                    }
                }
            }
            """
        ).indented()
    )
}
