/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License",;
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

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintClient.Companion.isStudio
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.google.common.annotations.VisibleForTesting
import java.util.Collections.unmodifiableList
import java.util.EnumSet

/**
 * Registry which provides a list of checks to be performed on an
 * Android project.
 */
open class BuiltinIssueRegistry : IssueRegistry() {

    override val vendor: Vendor = AOSP_VENDOR

    companion object {
        /**
         * Reset the registry such that it recomputes its available
         * issues.
         *
         * NOTE: This is only intended for testing purposes.
         */
        @VisibleForTesting
        @JvmStatic
        fun reset() {
            IssueRegistry.reset()
        }

        private val builtinIssues: List<Issue> = unmodifiableList(
            listOf(
                AccessibilityDetector.ISSUE,
                ActionsXmlDetector.ISSUE,
                AddJavascriptInterfaceDetector.ISSUE,
                AlarmDetector.ISSUE,
                AllCapsDetector.ISSUE,
                AllowAllHostnameVerifierDetector.ISSUE,
                AlwaysShowActionDetector.ISSUE,
                AndroidAutoDetector.INVALID_USES_TAG_ISSUE,
                AndroidAutoDetector.MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH,
                AndroidAutoDetector.MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE,
                AndroidAutoDetector.MISSING_ON_PLAY_FROM_SEARCH,
                AndroidTvDetector.IMPLIED_TOUCHSCREEN_HARDWARE,
                AndroidTvDetector.MISSING_BANNER,
                AndroidTvDetector.MISSING_LEANBACK_LAUNCHER,
                AndroidTvDetector.MISSING_LEANBACK_SUPPORT,
                AndroidTvDetector.PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                AndroidTvDetector.UNSUPPORTED_TV_HARDWARE,
                AnnotationDetector.ANNOTATION_USAGE,
                AnnotationDetector.FLAG_STYLE,
                AnnotationDetector.INSIDE_METHOD,
                AnnotationDetector.SWITCH_TYPE_DEF,
                AnnotationDetector.UNIQUE,
                ApiDetector.INLINED,
                ApiDetector.OBSOLETE_SDK,
                ApiDetector.OVERRIDE,
                ApiDetector.UNSUPPORTED,
                ApiDetector.UNUSED,
                AppCompatCallDetector.ISSUE,
                AppCompatCustomViewDetector.ISSUE,
                AppCompatResourceDetector.ISSUE,
                AppLinksAutoVerifyDetector.ISSUE,
                AppLinksValidDetector.INTENT_FILTER_UNIQUE_DATA_ATTRIBUTES,
                AppLinksValidDetector.TEST_URL,
                AppLinksValidDetector.VALIDATION,
                ArraySizeDetector.INCONSISTENT,
                AssertDetector.EXPENSIVE,
                AutofillDetector.ISSUE,
                BadHostnameVerifierDetector.ISSUE,
                BatteryDetector.ISSUE,
                BottomAppBarDetector.ISSUE,
                ButtonDetector.BACK_BUTTON,
                ButtonDetector.CASE,
                ButtonDetector.ORDER,
                ButtonDetector.STYLE,
                ByteOrderMarkDetector.BOM,
                C2dmDetector.ISSUE,
                CallSuperDetector.ISSUE,
                CanvasSizeDetector.ISSUE,
                CheckResultDetector.CHECK_PERMISSION,
                CheckResultDetector.CHECK_RESULT,
                ChildCountDetector.ADAPTER_VIEW_ISSUE,
                ChildCountDetector.SCROLLVIEW_ISSUE,
                ChromeOsDetector.NON_RESIZEABLE_ACTIVITY,
                ChromeOsDetector.PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                ChromeOsDetector.SETTING_ORIENTATION_ON_ACTIVITY,
                ChromeOsDetector.UNSUPPORTED_CHROME_OS_HARDWARE,
                ChromeOsSourceDetector.UNSUPPORTED_CAMERA_FEATURE,
                ChromeOsSourceDetector.UNSUPPORTED_LOCKED_ORIENTATION,
                CipherGetInstanceDetector.DEPRECATED_PROVIDER,
                CipherGetInstanceDetector.ISSUE,
                CleanupDetector.APPLY_SHARED_PREF,
                CleanupDetector.COMMIT_FRAGMENT,
                CleanupDetector.RECYCLE_RESOURCE,
                CleanupDetector.SHARED_PREF,
                ClickableViewAccessibilityDetector.ISSUE,
                CommentDetector.EASTER_EGG,
                CommentDetector.STOP_SHIP,
                ConstraintLayoutDetector.ISSUE,
                CordovaVersionDetector.ISSUE,
                CustomViewDetector.ISSUE,
                CutPasteDetector.ISSUE,
                DataBindingDetector.ESCAPE_XML,
                DateFormatDetector.DATE_FORMAT,
                DateFormatDetector.WEEK_YEAR,
                DeletedProviderDetector.ISSUE,
                DeprecationDetector.ISSUE,
                DiffUtilDetector.ISSUE,
                DosLineEndingDetector.ISSUE,
                DuplicateIdDetector.CROSS_LAYOUT,
                DuplicateIdDetector.WITHIN_LAYOUT,
                DuplicateResourceDetector.ISSUE,
                DuplicateResourceDetector.STRING_ESCAPING,
                DuplicateResourceDetector.TYPE_MISMATCH,
                EllipsizeMaxLinesDetector.ISSUE,
                ExifInterfaceDetector.ISSUE,
                ExportedReceiverDetector.ISSUE,
                ExtraTextDetector.ISSUE,
                FineLocationDetector.ISSUE,
                FirebaseAnalyticsDetector.INVALID_NAME,
                FirebaseMessagingDetector.MISSING_TOKEN_REFRESH,
                FontDetector.FONT_VALIDATION,
                FragmentDetector.ISSUE,
                FullBackupContentDetector.ISSUE,
                GetContentDescriptionOverrideDetector.ISSUE,
                GetSignaturesDetector.ISSUE,
                GradleDetector.ACCIDENTAL_OCTAL,
                GradleDetector.AGP_DEPENDENCY,
                GradleDetector.ANNOTATION_PROCESSOR_ON_COMPILE_PATH,
                GradleDetector.BUNDLED_GMS,
                GradleDetector.COMPATIBILITY,
                GradleDetector.DATA_BINDING_WITHOUT_KAPT,
                GradleDetector.DEPENDENCY,
                GradleDetector.DEPRECATED,
                GradleDetector.DEPRECATED_CONFIGURATION,
                GradleDetector.DEPRECATED_LIBRARY,
                GradleDetector.DEV_MODE_OBSOLETE,
                GradleDetector.DUPLICATE_CLASSES,
                GradleDetector.EXPIRED_TARGET_SDK_VERSION,
                GradleDetector.EXPIRING_TARGET_SDK_VERSION,
                GradleDetector.GRADLE_GETTER,
                GradleDetector.GRADLE_PLUGIN_COMPATIBILITY,
                GradleDetector.HIGH_APP_VERSION_CODE,
                GradleDetector.IDE_SUPPORT,
                GradleDetector.JAVA_PLUGIN_LANGUAGE_LEVEL,
                GradleDetector.JCENTER_REPOSITORY_OBSOLETE,
                GradleDetector.KTX_EXTENSION_AVAILABLE,
                GradleDetector.LIFECYCLE_ANNOTATION_PROCESSOR_WITH_JAVA8,
                GradleDetector.MIN_SDK_TOO_LOW,
                GradleDetector.NOT_INTERPOLATED,
                GradleDetector.PATH,
                GradleDetector.PLUS,
                GradleDetector.REMOTE_VERSION,
                GradleDetector.RISKY_LIBRARY,
                GradleDetector.STRING_INTEGER,
                GridLayoutDetector.ISSUE,
                HandlerDetector.ISSUE,
                HardcodedDebugModeDetector.ISSUE,
                HardcodedValuesDetector.ISSUE,
                HardwareIdDetector.ISSUE,
                HighSensorSamplingRateDetector.ISSUE,
                IconDetector.DUPLICATES_CONFIGURATIONS,
                IconDetector.DUPLICATES_NAMES,
                IconDetector.GIF_USAGE,
                IconDetector.ICON_COLORS,
                IconDetector.ICON_DENSITIES,
                IconDetector.ICON_DIP_SIZE,
                IconDetector.ICON_EXPECTED_SIZE,
                IconDetector.ICON_EXTENSION,
                IconDetector.ICON_LAUNCHER_SHAPE,
                IconDetector.ICON_LOCATION,
                IconDetector.ICON_MISSING_FOLDER,
                IconDetector.ICON_MIX_9PNG,
                IconDetector.ICON_NODPI,
                IconDetector.ICON_XML_AND_PNG,
                IconDetector.NOTIFICATION_ICON_COMPATIBILITY,
                IconDetector.WEBP_ELIGIBLE,
                IconDetector.WEBP_UNSUPPORTED,
                IgnoreWithoutReasonDetector.ISSUE,
                IncludeDetector.ISSUE,
                InefficientWeightDetector.BASELINE_WEIGHTS,
                InefficientWeightDetector.INEFFICIENT_WEIGHT,
                InefficientWeightDetector.NESTED_WEIGHTS,
                InefficientWeightDetector.ORIENTATION,
                InefficientWeightDetector.WRONG_0DP,
                InstantAppDetector.ISSUE,
                IntentDetector.ISSUE,
                InteroperabilityDetector.KOTLIN_PROPERTY,
                InteroperabilityDetector.LAMBDA_LAST,
                InteroperabilityDetector.NO_HARD_KOTLIN_KEYWORDS,
                InteroperabilityDetector.PLATFORM_NULLNESS,
                InvalidImeActionIdDetector.ISSUE,
                InvalidPackageDetector.ISSUE,
                ItemDecoratorDetector.ISSUE,
                IteratorDetector.ISSUE,
                JavaPerformanceDetector.PAINT_ALLOC,
                JavaPerformanceDetector.USE_SPARSE_ARRAY,
                JavaPerformanceDetector.USE_VALUE_OF,
                JavaScriptInterfaceDetector.ISSUE,
                JobSchedulerDetector.ISSUE,
                KeyboardNavigationDetector.ISSUE,
                LabelForDetector.ISSUE,
                LayoutConsistencyDetector.INCONSISTENT_IDS,
                LayoutInflationDetector.ISSUE,
                LeakDetector.ISSUE,
                LeanbackWifiUsageDetector.ISSUE,
                LintDetectorDetector.CHECK_URL,
                LintDetectorDetector.DOLLAR_STRINGS,
                LintDetectorDetector.ID,
                LintDetectorDetector.MISSING_DOC_EXAMPLE,
                LintDetectorDetector.PSI_COMPARE,
                LintDetectorDetector.TEXT_FORMAT,
                LintDetectorDetector.TRIM_INDENT,
                LintDetectorDetector.UNEXPECTED_DOMAIN,
                LintDetectorDetector.USE_KOTLIN,
                LintDetectorDetector.USE_UAST,
                LocaleDetector.FINAL_LOCALE,
                LocaleDetector.STRING_LOCALE,
                LocaleFolderDetector.DEPRECATED_CODE,
                LocaleFolderDetector.GET_LOCALES,
                LocaleFolderDetector.INVALID_FOLDER,
                LocaleFolderDetector.USE_ALPHA_2,
                LocaleFolderDetector.WRONG_REGION,
                LogDetector.CONDITIONAL,
                LogDetector.LONG_TAG,
                LogDetector.WRONG_TAG,
                ManifestDetector.ALLOW_BACKUP,
                ManifestDetector.APP_INDEXING_SERVICE,
                ManifestDetector.APPLICATION_ICON,
                ManifestDetector.DEVICE_ADMIN,
                ManifestDetector.DUPLICATE_ACTIVITY,
                ManifestDetector.DUPLICATE_USES_FEATURE,
                ManifestDetector.GRADLE_OVERRIDES,
                ManifestDetector.ILLEGAL_REFERENCE,
                ManifestDetector.MIPMAP,
                ManifestDetector.MOCK_LOCATION,
                ManifestDetector.MULTIPLE_USES_SDK,
                ManifestDetector.ORDER,
                ManifestDetector.SET_VERSION,
                ManifestDetector.TARGET_NEWER,
                ManifestDetector.UNIQUE_PERMISSION,
                ManifestDetector.USES_SDK,
                ManifestDetector.WEARABLE_BIND_LISTENER,
                ManifestDetector.WRONG_PARENT,
                ManifestPermissionAttributeDetector.ISSUE,
                ManifestResourceDetector.ISSUE,
                ManifestTypoDetector.ISSUE,
                MediaBrowserServiceCompatVersionDetector.ISSUE,
                MediaCapabilitiesDetector.ISSUE,
                MergeMarkerDetector.ISSUE,
                MergeRootFrameLayoutDetector.ISSUE,
                MissingClassDetector.INNERCLASS,
                MissingClassDetector.INSTANTIATABLE,
                MissingClassDetector.MISSING,
                MissingIdDetector.ISSUE,
                MissingPrefixDetector.MISSING_NAMESPACE,
                MotionLayoutDetector.INVALID_SCENE_FILE_REFERENCE,
                MotionSceneDetector.MOTION_SCENE_FILE_VALIDATION_ERROR,
                NamespaceDetector.CUSTOM_VIEW,
                NamespaceDetector.REDUNDANT,
                NamespaceDetector.RES_AUTO,
                NamespaceDetector.TYPO,
                NamespaceDetector.UNUSED,
                NegativeMarginDetector.ISSUE,
                NestedScrollingWidgetDetector.ISSUE,
                NetworkSecurityConfigDetector.ACCEPTS_USER_CERTIFICATES,
                NetworkSecurityConfigDetector.INSECURE_CONFIGURATION,
                NetworkSecurityConfigDetector.ISSUE,
                NetworkSecurityConfigDetector.MISSING_BACKUP_PIN,
                NetworkSecurityConfigDetector.PIN_SET_EXPIRY,
                NfcTechListDetector.ISSUE,
                NonConstantResourceIdDetector.NON_CONSTANT_RESOURCE_ID,
                NonInternationalizedSmsDetector.ISSUE,
                NotificationTrampolineDetector.ACTIVITY,
                NotificationTrampolineDetector.TRAMPOLINE,
                ObjectAnimatorDetector.BROKEN_PROPERTY,
                ObjectAnimatorDetector.MISSING_KEEP,
                ObsoleteLayoutParamsDetector.ISSUE,
                OnClickDetector.ISSUE,
                OverdrawDetector.ISSUE,
                OverrideConcreteDetector.ISSUE,
                OverrideDetector.ISSUE,
                PackageVisibilityDetector.QUERY_ALL_PACKAGES_PERMISSION,
                PackageVisibilityDetector.QUERY_PERMISSIONS_NEEDED,
                ParcelDetector.ISSUE,
                PendingIntentMutableFlagDetector.ISSUE,
                PermissionDetector.MISSING_PERMISSION,
                PluralsDetector.EXTRA,
                PluralsDetector.IMPLIED_QUANTITY,
                PluralsDetector.MISSING,
                PowerManagerDetector.INVALID_WAKE_LOCK_TAG,
                PreferenceActivityDetector.ISSUE,
                PrivateApiDetector.BLOCKED_PRIVATE_API,
                PrivateApiDetector.DISCOURAGED_PRIVATE_API,
                PrivateApiDetector.PRIVATE_API,
                PrivateApiDetector.SOON_BLOCKED_PRIVATE_API,
                PrivateKeyDetector.ISSUE,
                PrivateResourceDetector.ISSUE,
                ProguardDetector.SPLIT_CONFIG,
                ProguardDetector.WRONG_KEEP,
                PropertyFileDetector.ESCAPE,
                PropertyFileDetector.HTTP,
                PropertyFileDetector.PROXY_PASSWORD,
                PxUsageDetector.DP_ISSUE,
                PxUsageDetector.IN_MM_ISSUE,
                PxUsageDetector.PX_ISSUE,
                PxUsageDetector.SMALL_SP_ISSUE,
                RangeDetector.RANGE,
                ReadParcelableDetector.ISSUE,
                RecyclerViewDetector.CLEAR_ALL_DATA,
                RecyclerViewDetector.DATA_BINDER,
                RecyclerViewDetector.FIXED_POSITION,
                RegistrationDetector.ISSUE,
                RelativeOverlapDetector.ISSUE,
                RemoteViewDetector.ISSUE,
                RequiredAttributeDetector.ISSUE,
                RequiresFeatureDetector.REQUIRES_FEATURE,
                ResourceCycleDetector.CRASH,
                ResourceCycleDetector.CYCLE,
                ResourcePrefixDetector.ISSUE,
                ResourceTypeDetector.COLOR_USAGE,
                ResourceTypeDetector.HALF_FLOAT,
                ResourceTypeDetector.RESOURCE_TYPE,
                RestrictionsDetector.ISSUE,
                RestrictToDetector.RESTRICTED,
                RestrictToDetector.TEST_VISIBILITY,
                RtlDetector.COMPAT,
                RtlDetector.ENABLED,
                RtlDetector.SYMMETRY,
                RtlDetector.USE_START,
                SamDetector.ISSUE,
                ScopedStorageDetector.ISSUE,
                ScrollViewChildDetector.ISSUE,
                SdCardDetector.ISSUE,
                SdkIntDetector.ISSUE,
                SecureRandomDetector.ISSUE,
                SecureRandomGeneratorDetector.ISSUE,
                SecurityDetector.EXPORTED_PROVIDER,
                SecurityDetector.EXPORTED_RECEIVER,
                SecurityDetector.EXPORTED_SERVICE,
                SecurityDetector.OPEN_PROVIDER,
                SecurityDetector.SET_READABLE,
                SecurityDetector.SET_WRITABLE,
                SecurityDetector.WORLD_READABLE,
                SecurityDetector.WORLD_WRITEABLE,
                ServiceCastDetector.ISSUE,
                ServiceCastDetector.WIFI_MANAGER,
                ServiceCastDetector.WIFI_MANAGER_UNCERTAIN,
                SetJavaScriptEnabledDetector.ISSUE,
                SetTextDetector.SET_TEXT_I18N,
                SharedPrefsDetector.ISSUE,
                SignatureOrSystemDetector.ISSUE,
                SliceDetector.ISSUE,
                SplashScreenDetector.ISSUE,
                SQLiteDetector.ISSUE,
                SslCertificateSocketFactoryDetector.CREATE_SOCKET,
                SslCertificateSocketFactoryDetector.GET_INSECURE,
                StartDestinationDetector.ISSUE,
                StateListDetector.ISSUE,
                StorageDetector.ISSUE,
                StringAuthLeakDetector.AUTH_LEAK,
                StringCasingDetector.DUPLICATE_STRINGS,
                StringFormatDetector.ARG_COUNT,
                StringFormatDetector.ARG_TYPES,
                StringFormatDetector.INVALID,
                StringFormatDetector.POTENTIAL_PLURAL,
                SyntheticAccessorDetector.ISSUE,
                SystemPermissionsDetector.ISSUE,
                TextFieldDetector.ISSUE,
                TextViewDetector.ISSUE,
                TextViewDetector.SELECTABLE,
                ThreadDetector.THREAD,
                TileProviderDetector.ISSUE,
                TitleDetector.ISSUE,
                ToastDetector.ISSUE,
                TooManyViewsDetector.TOO_DEEP,
                TooManyViewsDetector.TOO_MANY,
                TranslationDetector.EXTRA,
                TranslationDetector.MISSING,
                TranslationDetector.MISSING_BASE,
                TranslationDetector.TRANSLATED_UNTRANSLATABLE,
                TranslucentViewDetector.ISSUE,
                TypedefDetector.TYPE_DEF,
                TypoDetector.ISSUE,
                TypographyDetector.DASHES,
                TypographyDetector.ELLIPSIS,
                TypographyDetector.FRACTIONS,
                TypographyDetector.OTHER,
                TypographyDetector.QUOTES,
                UnpackedNativeCodeDetector.ISSUE,
                UnsafeBroadcastReceiverDetector.ACTION_STRING,
                UnsafeBroadcastReceiverDetector.BROADCAST_SMS,
                UnsafeNativeCodeDetector.LOAD,
                UnsafeNativeCodeDetector.UNSAFE_NATIVE_CODE_LOCATION,
                UnusedResourceDetector.ISSUE,
                UnusedResourceDetector.ISSUE_IDS,
                UseCompoundDrawableDetector.ISSUE,
                UselessViewDetector.USELESS_LEAF,
                UselessViewDetector.USELESS_PARENT,
                Utf8Detector.ISSUE,
                VectorDetector.ISSUE,
                VectorDrawableCompatDetector.ISSUE,
                VectorPathDetector.PATH_LENGTH,
                VectorPathDetector.PATH_VALID,
                ViewConstructorDetector.ISSUE,
                ViewHolderDetector.ISSUE,
                ViewTypeDetector.ADD_CAST,
                ViewTypeDetector.WRONG_VIEW_CAST,
                WakelockDetector.ISSUE,
                WakelockDetector.TIMEOUT,
                WatchFaceEditorDetector.ISSUE,
                WearStandaloneAppDetector.INVALID_WEAR_FEATURE_ATTRIBUTE,
                WearStandaloneAppDetector.WEAR_STANDALONE_APP_ISSUE,
                WebViewApiAvailabilityDetector.ISSUE,
                WebViewClientDetector.PROCEEDS_ON_RECEIVED_SSL_ERROR,
                WebViewDetector.ISSUE,
                WorkManagerDetector.ISSUE,
                WrongCallDetector.ISSUE,
                WrongCaseDetector.WRONG_CASE,
                WrongIdDetector.INVALID,
                WrongIdDetector.NOT_SIBLING,
                WrongIdDetector.UNKNOWN_ID,
                WrongIdDetector.UNKNOWN_ID_LAYOUT,
                WrongImportDetector.ISSUE,
                WrongLocationDetector.ISSUE,
                WrongThreadInterproceduralDetector.ISSUE,
                X509TrustManagerDetector.IMPLEMENTS_CUSTOM,
                X509TrustManagerDetector.TRUSTS_ALL
            )
        )
    }

    init {
        for (issue in builtinIssues) {
            //noinspection LeakingThis
            issue.registry = this
        }
    }

    public override fun cacheable(): Boolean {
        // In the IDE, cache across incremental runs; here, lint is never run in parallel
        // Outside of the IDE, typically in Gradle, we don't want this caching since
        // lint can run in parallel and this caching can be incorrect;
        // see for example issue 77891711
        return isStudio
    }

    override val issues: List<Issue>
        get() = builtinIssues

    override val deletedIssues: List<String> = listOf(
        // Off by default for a while; unlikely to be turned on (and this is
        // just an awareness check which is unlikely to be enabled by those
        // who could benefit from it)
        "GoogleAppIndexingWarning",

        // Implementation not correct and would require rewrite to fix, not worth it
        "GoogleAppIndexingApiWarning",

        // Deleted a while back when restrictions were removed on launcher icons
        "IconLauncherFormat",

        // No longer relevant, only applied to minSdk < 14
        "ViewTag",

        // No longer relevant, only applied to minSdk < 9
        "FieldGetter",

        // Renamed to MissingClass
        "MissingRegistered",

        // Combined into FontValidation
        "FontValidationWarning",
        "FontValidationError",

        // Combined into AppLinksAutoVerify
        "AppLinksAutoVerifyError",
        "AppLinksAutoVerifyWarning",

        // Deleted; no longer needed thanks to d8
        "Assert"
    )

    override fun getIssueCapacity(scope: EnumSet<Scope>): Int {
        return if (scope == Scope.ALL) {
            issues.size
        } else {
            var initialSize = 12
            when {
                scope.contains(Scope.RESOURCE_FILE) -> initialSize += 115
                scope.contains(Scope.ALL_RESOURCE_FILES) -> initialSize += 12
            }
            when {
                scope.contains(Scope.JAVA_FILE) -> initialSize += 150
                scope.contains(Scope.CLASS_FILE) -> initialSize += 16
                scope.contains(Scope.MANIFEST) -> initialSize += 70
                scope.contains(Scope.GRADLE_FILE) -> initialSize += 24
            }
            initialSize
        }
    }

    override val api: Int
        get() = CURRENT_API
}
