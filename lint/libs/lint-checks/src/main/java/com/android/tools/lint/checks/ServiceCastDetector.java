/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.CLASS_ACTIVITY;
import static com.android.SdkConstants.CLASS_APPLICATION;
import static com.android.SdkConstants.CLASS_CONTEXT;
import static com.android.SdkConstants.CLASS_VIEW;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaPsiScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.collect.Maps;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Detector looking for casts on th result of context.getSystemService which are suspect
 */
public class ServiceCastDetector extends Detector implements JavaPsiScanner {
    public static final Implementation IMPLEMENTATION = new Implementation(
        ServiceCastDetector.class,
        Scope.JAVA_FILE_SCOPE);

    /** Invalid cast to a type from the service constant */
    public static final Issue ISSUE = Issue.create(
            "ServiceCast", //$NON-NLS-1$
            "Wrong system service casts",

            "When you call `Context#getSystemService()`, the result is typically cast to " +
            "a specific interface. This lint check ensures that the cast is compatible with " +
            "the expected type of the return value.",

            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            IMPLEMENTATION);

    /** Using wifi manager from the wrong context */
    public static final Issue WIFI_MANAGER = Issue.create(
            "WifiManagerLeak", //$NON-NLS-1$
            "WifiManager Leak",

            "On versions prior to Android N (24), initializing the `WifiManager` via " +
            "`Context#getSystemService` can cause a memory leak if the context is not " +
            "the application context. Change `context.getSystemService(...)` to " +
            "`context.getApplicationContext().getSystemService(...)`.",

            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            IMPLEMENTATION);

    /** Using wifi manager from the wrong context: unknown Context origin */
    public static final Issue WIFI_MANAGER_UNCERTAIN = Issue.create(
            "WifiManagerPotentialLeak", //$NON-NLS-1$
            "WifiManager Potential Leak",

            "On versions prior to Android N (24), initializing the `WifiManager` via " +
            "`Context#getSystemService` can cause a memory leak if the context is not " +
            "the application context.\n" +
            "\n" +
            "In many cases, it's not obvious from the code where the `Context` is " +
            "coming from (e.g. it might be a parameter to a method, or a field initialized " +
            "from various method calls.)  It's possible that the context being passed in " +
            "is the application context, but to be on the safe side, you should consider " +
            "changing `context.getSystemService(...)` to " +
            "`context.getApplicationContext().getSystemService(...)`.",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Constructs a new {@link ServiceCastDetector} check */
    public ServiceCastDetector() {
    }

    // ---- Implements JavaScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("getSystemService"); //$NON-NLS-1$
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable JavaElementVisitor visitor,
            @NonNull PsiMethodCallExpression call, @NonNull PsiMethod method) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length == 1 && args[0] instanceof PsiReferenceExpression) {
            String name = ((PsiReferenceExpression)args[0]).getReferenceName();

            // Check WIFI_SERVICE context origin
            if ("WIFI_SERVICE".equals(name)) {
                checkWifiService(context, call);
            }

            // Check cast
            PsiElement parent = LintUtils.skipParentheses(call.getParent());
            if (parent instanceof PsiTypeCastExpression) {
                PsiTypeCastExpression cast = (PsiTypeCastExpression) parent;
                String expectedClass = getExpectedType(name);
                if (expectedClass != null && cast.getCastType() != null) {
                    String castType = cast.getCastType().getType().getCanonicalText();
                    if (castType.indexOf('.') == -1) {
                        expectedClass = stripPackage(expectedClass);
                    }
                    if (!castType.equals(expectedClass)) {
                        // It's okay to mix and match
                        // android.content.ClipboardManager and android.text.ClipboardManager
                        if (isClipboard(castType) && isClipboard(expectedClass)) {
                            return;
                        }

                        String message = String.format(
                                "Suspicious cast to `%1$s` for a `%2$s`: expected `%3$s`",
                                stripPackage(castType), name, stripPackage(expectedClass));
                        context.report(ISSUE, call, context.getLocation(cast), message);
                    }
                }
            }
        }
    }

    /**
     * Checks that the given call to {@code Context#getSystemService(WIFI_SERVICE)} is
     * using the application context
     */
    private static void checkWifiService(@NonNull JavaContext context,
            @NonNull PsiMethodCallExpression call) {
        JavaEvaluator evaluator = context.getEvaluator();
        PsiReferenceExpression methodExpression = call.getMethodExpression();
        PsiExpression qualifier = methodExpression.getQualifierExpression();

        PsiMethod resolvedMethod = call.resolveMethod();
        if (resolvedMethod != null &&
                (evaluator.isMemberInSubClassOf(resolvedMethod, CLASS_ACTIVITY, false) ||
                        (evaluator.isMemberInSubClassOf(resolvedMethod, CLASS_VIEW, false)))) {
            reportWifiServiceLeak(WIFI_MANAGER, context, call);
            return;
        }
        if (qualifier == null) {
            // Implicit: check surrounding class
            PsiMethod currentMethod = PsiTreeUtil.getParentOfType(call, PsiMethod.class, true);
            if (currentMethod != null
                    && !evaluator.isMemberInSubClassOf(currentMethod, CLASS_APPLICATION, true)) {
                reportWifiServiceLeak(WIFI_MANAGER, context, call);
            }
        } else {
            checkContextReference(context, qualifier, call);
        }
    }

    /**
     * Given a reference to a context, check to see if the context is an application
     * context (in which case, return quietly), or known to not be an application context
     * (in which case, report an error), or is of an unknown context type (in which case,
     * report a warning).
     *
     * @param context the lint analysis context
     * @param element the reference to be checked
     * @param call    the original getSystemService call to report an error against
     */
    private static boolean checkContextReference(
            @NonNull JavaContext context,
            @Nullable PsiElement element,
            @NonNull PsiMethodCallExpression call) {
        if (element == null) {
            return false;
        }
        if (element instanceof PsiMethodCallExpression) {
            PsiMethod resolvedMethod = ((PsiMethodCallExpression) element).resolveMethod();
            if (resolvedMethod != null && !"getApplicationContext".equals(resolvedMethod.getName())) {
                reportWifiServiceLeak(WIFI_MANAGER, context, call);
                return true;
            }
        } else if (element instanceof PsiReferenceExpression) {
            // Check variable references backwards
            PsiElement resolved = ((PsiReferenceExpression) element).resolve();
            if (resolved instanceof PsiField) {
                PsiType type = ((PsiField) resolved).getType();
                return checkWifiContextType(context, call, type, true);
            } else if (resolved instanceof PsiParameter) {
                // Parameter: is the parameter type something other than just "Context"
                // or some subclass of Application?
                PsiType type = ((PsiParameter) resolved).getType();
                return checkWifiContextType(context, call, type, true);
            } else if (resolved instanceof PsiLocalVariable) {
                PsiLocalVariable variable = (PsiLocalVariable) resolved;
                PsiType type = variable.getType();
                if (checkWifiContextType(context, call, type, false)) {
                    return true;
                }

                // Walk backwards through assignments to find the most recent initialization
                // of this variable
                PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class,
                        false);
                if (statement != null) {
                    PsiStatement prev = PsiTreeUtil.getPrevSiblingOfType(statement,
                            PsiStatement.class);
                    String targetName = variable.getName();
                    if (targetName == null) {
                        return false;
                    }
                    while (prev != null) {
                        if (prev instanceof PsiDeclarationStatement) {
                            for (PsiElement st :
                                    ((PsiDeclarationStatement) prev).getDeclaredElements()) {
                                if (variable.equals(st)) {
                                    return checkContextReference(context,
                                            variable.getInitializer(), call);
                                }
                            }
                        } else if (prev instanceof PsiExpressionStatement) {
                            PsiExpression expression = ((PsiExpressionStatement) prev)
                                    .getExpression();
                            if (expression instanceof PsiAssignmentExpression) {
                                PsiAssignmentExpression assign
                                        = (PsiAssignmentExpression) expression;
                                PsiExpression lhs = assign.getLExpression();
                                if (lhs instanceof PsiReferenceExpression) {
                                    PsiReferenceExpression reference = (PsiReferenceExpression) lhs;
                                    if (targetName.equals(reference.getReferenceName()) &&
                                            reference.getQualifier() == null) {
                                        return checkContextReference(context,
                                                assign.getRExpression(), call);
                                    }
                                }
                            }
                        }
                        prev = PsiTreeUtil.getPrevSiblingOfType(prev,
                                PsiStatement.class);
                    }
                }
            }
        }

        return false;
    }

    /**
     * Given a context type (of a parameter or field), check to see if that type implies
     * that the context is not the application context (for example because it's an Activity
     * rather than a plain context).
     * <p>
     * Returns true if it finds and reports a problem.
     */
    private static boolean checkWifiContextType(@NonNull JavaContext context,
            @NonNull PsiMethodCallExpression call, @NonNull PsiType type,
            boolean flagPlainContext) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (type instanceof PsiClassType) {
            PsiClass psiClass = ((PsiClassType) type).resolve();
            if (evaluator.extendsClass(psiClass, CLASS_APPLICATION, false)) {
                return false;
            }
        }
        if (evaluator.typeMatches(type, CLASS_CONTEXT)) {
            if (flagPlainContext) {
                reportWifiServiceLeak(WIFI_MANAGER_UNCERTAIN, context, call);
                return true;
            }
            return false;
        }

        reportWifiServiceLeak(WIFI_MANAGER, context, call);
        return true;
    }

    private static void reportWifiServiceLeak(@NonNull Issue issue, @NonNull JavaContext context,
            @NonNull PsiMethodCallExpression call) {
        if (context.getMainProject().getMinSdk() >= 24) {
            // Bug is fixed in Nougat
            return;
        }

        String qualifier = "";
        if (call.getMethodExpression().getQualifierExpression() != null) {
            qualifier = call.getMethodExpression().getText() + ".";
        }
        String message = String.format("The WIFI_SERVICE must be looked up on the "
                + "Application context or memory will leak on devices < Android N. "
                + "Try adding `%1$sgetApplicationContext()`. ", qualifier);
        context.report(issue, call, context.getLocation(call), message);
    }

    private static boolean isClipboard(@NonNull String cls) {
        return cls.equals("android.content.ClipboardManager")      //$NON-NLS-1$
                || cls.equals("android.text.ClipboardManager");    //$NON-NLS-1$
    }

    private static String stripPackage(@NonNull String fqcn) {
        int index = fqcn.lastIndexOf('.');
        if (index != -1) {
            fqcn = fqcn.substring(index + 1);
        }

        return fqcn;
    }

    @Nullable
    private static String getExpectedType(@Nullable String value) {
        return value != null ? getServiceMap().get(value) : null;
    }

    @NonNull
    private static Map<String, String> getServiceMap() {
        if (sServiceMap == null) {
            final int EXPECTED_SIZE = 56;
            sServiceMap = Maps.newHashMapWithExpectedSize(EXPECTED_SIZE);
            sServiceMap.put("ACCESSIBILITY_SERVICE", "android.view.accessibility.AccessibilityManager");
            sServiceMap.put("ACCOUNT_SERVICE", "android.accounts.AccountManager");
            sServiceMap.put("ACTIVITY_SERVICE", "android.app.ActivityManager");
            sServiceMap.put("ALARM_SERVICE", "android.app.AlarmManager");
            sServiceMap.put("APPWIDGET_SERVICE", "android.appwidget.AppWidgetManager");
            sServiceMap.put("APP_OPS_SERVICE", "android.app.AppOpsManager");
            sServiceMap.put("AUDIO_SERVICE", "android.media.AudioManager");
            sServiceMap.put("BATTERY_SERVICE", "android.os.BatteryManager");
            sServiceMap.put("BLUETOOTH_SERVICE", "android.bluetooth.BluetoothManager");
            sServiceMap.put("CAMERA_SERVICE", "android.hardware.camera2.CameraManager");
            sServiceMap.put("CAPTIONING_SERVICE", "android.view.accessibility.CaptioningManager");
            sServiceMap.put("CARRIER_CONFIG_SERVICE", "android.telephony.CarrierConfigManager");
            sServiceMap.put("CLIPBOARD_SERVICE", "android.text.ClipboardManager"); // also allow @Deprecated android.content.ClipboardManager
            sServiceMap.put("CONNECTIVITY_SERVICE", "android.net.ConnectivityManager");
            sServiceMap.put("CONSUMER_IR_SERVICE", "android.hardware.ConsumerIrManager");
            sServiceMap.put("DEVICE_POLICY_SERVICE", "android.app.admin.DevicePolicyManager");
            sServiceMap.put("DISPLAY_SERVICE", "android.hardware.display.DisplayManager");
            sServiceMap.put("DOWNLOAD_SERVICE", "android.app.DownloadManager");
            sServiceMap.put("DROPBOX_SERVICE", "android.os.DropBoxManager");
            sServiceMap.put("FINGERPRINT_SERVICE", "android.hardware.fingerprint.FingerprintManager");
            sServiceMap.put("HARDWARE_PROPERTIES_SERVICE", "android.os.HardwarePropertiesManager");
            sServiceMap.put("INPUT_METHOD_SERVICE", "android.view.inputmethod.InputMethodManager");
            sServiceMap.put("INPUT_SERVICE", "android.hardware.input.InputManager");
            sServiceMap.put("JOB_SCHEDULER_SERVICE", "android.app.job.JobScheduler");
            sServiceMap.put("KEYGUARD_SERVICE", "android.app.KeyguardManager");
            sServiceMap.put("LAUNCHER_APPS_SERVICE", "android.content.pm.LauncherApps");
            sServiceMap.put("LAYOUT_INFLATER_SERVICE", "android.view.LayoutInflater");
            sServiceMap.put("LOCATION_SERVICE", "android.location.LocationManager");
            sServiceMap.put("MEDIA_PROJECTION_SERVICE", "android.media.projection.MediaProjectionManager");
            sServiceMap.put("MEDIA_ROUTER_SERVICE", "android.media.MediaRouter");
            sServiceMap.put("MEDIA_SESSION_SERVICE", "android.media.session.MediaSessionManager");
            sServiceMap.put("MIDI_SERVICE", "android.media.midi.MidiManager");
            sServiceMap.put("NETWORK_STATS_SERVICE", "android.app.usage.NetworkStatsManager");
            sServiceMap.put("NFC_SERVICE", "android.nfc.NfcManager");
            sServiceMap.put("NOTIFICATION_SERVICE", "android.app.NotificationManager");
            sServiceMap.put("NSD_SERVICE", "android.net.nsd.NsdManager");
            sServiceMap.put("POWER_SERVICE", "android.os.PowerManager");
            sServiceMap.put("PRINT_SERVICE", "android.print.PrintManager");
            sServiceMap.put("RESTRICTIONS_SERVICE", "android.content.RestrictionsManager");
            sServiceMap.put("SEARCH_SERVICE", "android.app.SearchManager");
            sServiceMap.put("SENSOR_SERVICE", "android.hardware.SensorManager");
            sServiceMap.put("STORAGE_SERVICE", "android.os.storage.StorageManager");
            sServiceMap.put("STORAGE_SERVICE", "android.os.storage.StorageManager");
            sServiceMap.put("SYSTEM_HEALTH_SERVICE", "android.os.health.SystemHealthManager");
            sServiceMap.put("TELEPHONY_SERVICE", "android.telephony.TelephonyManager");
            sServiceMap.put("TELEPHONY_SUBSCRIPTION_SERVICE", "android.telephony.SubscriptionManager");
            sServiceMap.put("TEXT_SERVICES_MANAGER_SERVICE", "android.view.textservice.TextServicesManager");
            sServiceMap.put("TV_INPUT_SERVICE", "android.media.tv.TvInputManager");
            sServiceMap.put("UI_MODE_SERVICE", "android.app.UiModeManager");
            sServiceMap.put("USAGE_STATS_SERVICE", "android.app.usage.UsageStatsManager");
            sServiceMap.put("USB_SERVICE", "android.hardware.usb.UsbManager");
            sServiceMap.put("USER_SERVICE", "android.os.UserManager");
            sServiceMap.put("VIBRATOR_SERVICE", "android.os.Vibrator");
            sServiceMap.put("WALLPAPER_SERVICE", "android.service.wallpaper.WallpaperService");
            sServiceMap.put("WIFI_P2P_SERVICE", "android.net.wifi.p2p.WifiP2pManager");
            sServiceMap.put("WIFI_SERVICE", "android.net.wifi.WifiManager");
            sServiceMap.put("WINDOW_SERVICE", "android.view.WindowManager");

            assert sServiceMap.size() == EXPECTED_SIZE : sServiceMap.size();
        }

        return sServiceMap;
    }

    private static Map<String, String> sServiceMap;
}
