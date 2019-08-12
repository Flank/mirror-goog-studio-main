/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.ApiKt;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** Registry which provides a list of checks to be performed on an Android project */
public class BuiltinIssueRegistry extends IssueRegistry {
    private static final List<Issue> sIssues;

    public static final int INITIAL_CAPACITY = 365;

    static {
        List<Issue> issues = new ArrayList<>(INITIAL_CAPACITY);

        issues.add(AccessibilityDetector.ISSUE);
        issues.add(ActionsXmlDetector.ISSUE);
        issues.add(AddJavascriptInterfaceDetector.ISSUE);
        issues.add(AlarmDetector.ISSUE);
        issues.add(AllCapsDetector.ISSUE);
        issues.add(AllowAllHostnameVerifierDetector.ISSUE);
        issues.add(AlwaysShowActionDetector.ISSUE);
        issues.add(AndroidAutoDetector.INVALID_USES_TAG_ISSUE);
        issues.add(AndroidAutoDetector.MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH);
        issues.add(AndroidAutoDetector.MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE);
        issues.add(AndroidAutoDetector.MISSING_ON_PLAY_FROM_SEARCH);
        issues.add(AndroidTvDetector.IMPLIED_TOUCHSCREEN_HARDWARE);
        issues.add(AndroidTvDetector.MISSING_BANNER);
        issues.add(AndroidTvDetector.MISSING_LEANBACK_LAUNCHER);
        issues.add(AndroidTvDetector.MISSING_LEANBACK_SUPPORT);
        issues.add(AndroidTvDetector.PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE);
        issues.add(AndroidTvDetector.UNSUPPORTED_TV_HARDWARE);
        issues.add(AnnotationDetector.ANNOTATION_USAGE);
        issues.add(AnnotationDetector.FLAG_STYLE);
        issues.add(AnnotationDetector.INSIDE_METHOD);
        issues.add(AnnotationDetector.SWITCH_TYPE_DEF);
        issues.add(AnnotationDetector.UNIQUE);
        issues.add(ApiDetector.INLINED);
        issues.add(ApiDetector.OBSOLETE_SDK);
        issues.add(ApiDetector.OVERRIDE);
        issues.add(ApiDetector.UNSUPPORTED);
        issues.add(ApiDetector.UNUSED);
        issues.add(AppCompatCallDetector.ISSUE);
        issues.add(AppCompatCustomViewDetector.ISSUE);
        issues.add(AppCompatResourceDetector.ISSUE);
        issues.add(AppIndexingApiDetector.ISSUE_APP_INDEXING);
        issues.add(AppIndexingApiDetector.ISSUE_APP_INDEXING_API);
        issues.add(AppLinksAutoVerifyDetector.ISSUE_ERROR);
        issues.add(AppLinksAutoVerifyDetector.ISSUE_WARNING);
        issues.add(AppLinksValidDetector.TEST_URL);
        issues.add(AppLinksValidDetector.VALIDATION);
        issues.add(ArraySizeDetector.INCONSISTENT);
        issues.add(AssertDetector.ISSUE);
        issues.add(AutofillDetector.ISSUE);
        issues.add(BadHostnameVerifierDetector.ISSUE);
        issues.add(BatteryDetector.ISSUE);
        issues.add(BottomAppBarDetector.ISSUE);
        issues.add(ButtonDetector.BACK_BUTTON);
        issues.add(ButtonDetector.CASE);
        issues.add(ButtonDetector.ORDER);
        issues.add(ButtonDetector.STYLE);
        issues.add(ByteOrderMarkDetector.BOM);
        issues.add(CallSuperDetector.ISSUE);
        issues.add(C2dmDetector.ISSUE);
        issues.add(CanvasSizeDetector.ISSUE);
        issues.add(CheckResultDetector.CHECK_PERMISSION);
        issues.add(CheckResultDetector.CHECK_RESULT);
        issues.add(ChildCountDetector.ADAPTER_VIEW_ISSUE);
        issues.add(ChildCountDetector.SCROLLVIEW_ISSUE);
        issues.add(ChromeOsDetector.PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE);
        issues.add(ChromeOsDetector.UNSUPPORTED_CHROME_OS_HARDWARE);
        issues.add(ChromeOsDetector.NON_RESIZEABLE_ACTIVITY);
        issues.add(ChromeOsDetector.SETTING_ORIENTATION_ON_ACTIVITY);
        issues.add(ChromeOsSourceDetector.UNSUPPORTED_LOCKED_ORIENTATION);
        issues.add(ChromeOsSourceDetector.UNSUPPORTED_CAMERA_FEATURE);
        issues.add(CipherGetInstanceDetector.ISSUE);
        issues.add(CipherGetInstanceDetector.DEPRECATED_PROVIDER);
        issues.add(CleanupDetector.APPLY_SHARED_PREF);
        issues.add(CleanupDetector.COMMIT_FRAGMENT);
        issues.add(CleanupDetector.RECYCLE_RESOURCE);
        issues.add(CleanupDetector.SHARED_PREF);
        issues.add(ClickableViewAccessibilityDetector.ISSUE);
        issues.add(CommentDetector.EASTER_EGG);
        issues.add(CommentDetector.STOP_SHIP);
        issues.add(ConstraintLayoutDetector.ISSUE);
        issues.add(CordovaVersionDetector.ISSUE);
        issues.add(CustomViewDetector.ISSUE);
        issues.add(CutPasteDetector.ISSUE);
        issues.add(DataBindingDetector.ESCAPE_XML);
        issues.add(DateFormatDetector.DATE_FORMAT);
        issues.add(DeletedProviderDetector.ISSUE);
        issues.add(DeprecationDetector.ISSUE);
        issues.add(DetectMissingPrefix.MISSING_NAMESPACE);
        issues.add(DiffUtilDetector.ISSUE);
        issues.add(DosLineEndingDetector.ISSUE);
        issues.add(DuplicateIdDetector.CROSS_LAYOUT);
        issues.add(DuplicateIdDetector.WITHIN_LAYOUT);
        issues.add(DuplicateResourceDetector.ISSUE);
        issues.add(DuplicateResourceDetector.STRING_ESCAPING);
        issues.add(DuplicateResourceDetector.TYPE_MISMATCH);
        issues.add(EllipsizeMaxLinesDetector.ISSUE);
        issues.add(ExifInterfaceDetector.ISSUE);
        issues.add(ExtraTextDetector.ISSUE);
        issues.add(FieldGetterDetector.ISSUE);
        issues.add(FirebaseAnalyticsDetector.INVALID_NAME);
        issues.add(FirebaseMessagingDetector.MISSING_TOKEN_REFRESH);
        issues.add(FontDetector.FONT_VALIDATION_ERROR);
        issues.add(FontDetector.FONT_VALIDATION_WARNING);
        issues.add(FragmentDetector.ISSUE);
        issues.add(FullBackupContentDetector.ISSUE);
        issues.add(GetContentDescriptionOverrideDetector.ISSUE);
        issues.add(GetSignaturesDetector.ISSUE);
        issues.add(GradleDetector.ACCIDENTAL_OCTAL);
        issues.add(GradleDetector.ANNOTATION_PROCESSOR_ON_COMPILE_PATH);
        issues.add(GradleDetector.BUNDLED_GMS);
        issues.add(GradleDetector.COMPATIBILITY);
        issues.add(GradleDetector.DATA_BINDING_WITHOUT_KAPT);
        issues.add(GradleDetector.DEPENDENCY);
        issues.add(GradleDetector.DEPRECATED);
        issues.add(GradleDetector.DEPRECATED_CONFIGURATION);
        issues.add(GradleDetector.DEPRECATED_LIBRARY);
        issues.add(GradleDetector.DEV_MODE_OBSOLETE);
        issues.add(GradleDetector.DUPLICATE_CLASSES);
        issues.add(GradleDetector.EXPIRED_TARGET_SDK_VERSION);
        issues.add(GradleDetector.EXPIRING_TARGET_SDK_VERSION);
        issues.add(GradleDetector.GRADLE_GETTER);
        issues.add(GradleDetector.GRADLE_PLUGIN_COMPATIBILITY);
        issues.add(GradleDetector.HIGH_APP_VERSION_CODE);
        issues.add(GradleDetector.IDE_SUPPORT);
        issues.add(GradleDetector.LIFECYCLE_ANNOTATION_PROCESSOR_WITH_JAVA8);
        issues.add(GradleDetector.MIN_SDK_TOO_LOW);
        issues.add(GradleDetector.NOT_INTERPOLATED);
        issues.add(GradleDetector.PATH);
        issues.add(GradleDetector.PLUS);
        issues.add(GradleDetector.REMOTE_VERSION);
        issues.add(GradleDetector.RISKY_LIBRARY);
        issues.add(GradleDetector.STRING_INTEGER);
        issues.add(GridLayoutDetector.ISSUE);
        issues.add(HandlerDetector.ISSUE);
        issues.add(HardcodedDebugModeDetector.ISSUE);
        issues.add(HardcodedValuesDetector.ISSUE);
        issues.add(HardwareIdDetector.ISSUE);
        issues.add(IconDetector.DUPLICATES_CONFIGURATIONS);
        issues.add(IconDetector.DUPLICATES_NAMES);
        issues.add(IconDetector.GIF_USAGE);
        issues.add(IconDetector.ICON_COLORS);
        issues.add(IconDetector.ICON_DENSITIES);
        issues.add(IconDetector.ICON_DIP_SIZE);
        issues.add(IconDetector.ICON_EXPECTED_SIZE);
        issues.add(IconDetector.ICON_EXTENSION);
        issues.add(IconDetector.ICON_LAUNCHER_SHAPE);
        issues.add(IconDetector.ICON_LOCATION);
        issues.add(IconDetector.ICON_MISSING_FOLDER);
        issues.add(IconDetector.ICON_MIX_9PNG);
        issues.add(IconDetector.ICON_NODPI);
        issues.add(IconDetector.ICON_XML_AND_PNG);
        issues.add(IconDetector.NOTIFICATION_ICON_COMPATIBILITY);
        issues.add(IconDetector.WEBP_ELIGIBLE);
        issues.add(IconDetector.WEBP_UNSUPPORTED);
        issues.add(IgnoreWithoutReasonDetector.ISSUE);
        issues.add(IncludeDetector.ISSUE);
        issues.add(InefficientWeightDetector.BASELINE_WEIGHTS);
        issues.add(InefficientWeightDetector.INEFFICIENT_WEIGHT);
        issues.add(InefficientWeightDetector.NESTED_WEIGHTS);
        issues.add(InefficientWeightDetector.ORIENTATION);
        issues.add(InefficientWeightDetector.WRONG_0DP);
        issues.add(IntentDetector.ISSUE);
        issues.add(InteroperabilityDetector.KOTLIN_PROPERTY);
        issues.add(InteroperabilityDetector.LAMBDA_LAST);
        issues.add(InteroperabilityDetector.NO_HARD_KOTLIN_KEYWORDS);
        issues.add(InteroperabilityDetector.PLATFORM_NULLNESS);
        issues.add(InstantAppDetector.ISSUE);
        issues.add(InvalidImeActionIdDetector.ISSUE);
        issues.add(InvalidPackageDetector.ISSUE);
        issues.add(IteratorDetector.ISSUE);
        issues.add(ItemDecoratorDetector.ISSUE);
        issues.add(JavaPerformanceDetector.PAINT_ALLOC);
        issues.add(JavaPerformanceDetector.USE_SPARSE_ARRAY);
        issues.add(JavaPerformanceDetector.USE_VALUE_OF);
        issues.add(JavaScriptInterfaceDetector.ISSUE);
        issues.add(JobSchedulerDetector.ISSUE);
        issues.add(KeyboardNavigationDetector.ISSUE);
        issues.add(LabelForDetector.ISSUE);
        issues.add(LayoutConsistencyDetector.INCONSISTENT_IDS);
        issues.add(LayoutInflationDetector.ISSUE);
        issues.add(LeakDetector.ISSUE);
        issues.add(LocaleDetector.FINAL_LOCALE);
        issues.add(LocaleDetector.STRING_LOCALE);
        issues.add(LocaleFolderDetector.DEPRECATED_CODE);
        issues.add(LocaleFolderDetector.GET_LOCALES);
        issues.add(LocaleFolderDetector.INVALID_FOLDER);
        issues.add(LocaleFolderDetector.USE_ALPHA_2);
        issues.add(LocaleFolderDetector.WRONG_REGION);
        issues.add(LogDetector.CONDITIONAL);
        issues.add(LogDetector.LONG_TAG);
        issues.add(LogDetector.WRONG_TAG);
        issues.add(ManifestDetector.ALLOW_BACKUP);
        issues.add(ManifestDetector.APP_INDEXING_SERVICE);
        issues.add(ManifestDetector.APPLICATION_ICON);
        issues.add(ManifestDetector.DEVICE_ADMIN);
        issues.add(ManifestDetector.DUPLICATE_ACTIVITY);
        issues.add(ManifestDetector.DUPLICATE_USES_FEATURE);
        issues.add(ManifestDetector.GRADLE_OVERRIDES);
        issues.add(ManifestDetector.ILLEGAL_REFERENCE);
        issues.add(ManifestDetector.MIPMAP);
        issues.add(ManifestDetector.MOCK_LOCATION);
        issues.add(ManifestDetector.MULTIPLE_USES_SDK);
        issues.add(ManifestDetector.ORDER);
        issues.add(ManifestDetector.SET_VERSION);
        issues.add(ManifestDetector.TARGET_NEWER);
        issues.add(ManifestDetector.UNIQUE_PERMISSION);
        issues.add(ManifestDetector.USES_SDK);
        issues.add(ManifestDetector.WEARABLE_BIND_LISTENER);
        issues.add(ManifestDetector.WRONG_PARENT);
        issues.add(ManifestPermissionAttributeDetector.ISSUE);
        issues.add(ManifestResourceDetector.ISSUE);
        issues.add(ManifestTypoDetector.ISSUE);
        issues.add(MediaBrowserServiceCompatVersionDetector.ISSUE);
        issues.add(MergeMarkerDetector.ISSUE);
        issues.add(MergeRootFrameLayoutDetector.ISSUE);
        issues.add(MissingClassDetector.INNERCLASS);
        issues.add(MissingClassDetector.INSTANTIATABLE);
        issues.add(MissingClassDetector.MISSING);
        issues.add(MissingIdDetector.ISSUE);
        issues.add(NamespaceDetector.CUSTOM_VIEW);
        issues.add(NamespaceDetector.RES_AUTO);
        issues.add(NamespaceDetector.REDUNDANT);
        issues.add(NamespaceDetector.TYPO);
        issues.add(NamespaceDetector.UNUSED);
        issues.add(NegativeMarginDetector.ISSUE);
        issues.add(NestedScrollingWidgetDetector.ISSUE);
        issues.add(NetworkSecurityConfigDetector.ISSUE);
        issues.add(NetworkSecurityConfigDetector.ACCEPTS_USER_CERTIFICATES);
        issues.add(NetworkSecurityConfigDetector.INSECURE_CONFIGURATION);
        issues.add(NetworkSecurityConfigDetector.MISSING_BACKUP_PIN);
        issues.add(NetworkSecurityConfigDetector.PIN_SET_EXPIRY);
        issues.add(NfcTechListDetector.ISSUE);
        issues.add(NonInternationalizedSmsDetector.ISSUE);
        issues.add(ObjectAnimatorDetector.BROKEN_PROPERTY);
        issues.add(ObjectAnimatorDetector.MISSING_KEEP);
        issues.add(ObsoleteLayoutParamsDetector.ISSUE);
        issues.add(OnClickDetector.ISSUE);
        issues.add(OverdrawDetector.ISSUE);
        issues.add(OverrideConcreteDetector.ISSUE);
        issues.add(OverrideDetector.ISSUE);
        issues.add(ParcelDetector.ISSUE);
        issues.add(PermissionDetector.MISSING_PERMISSION);
        issues.add(PluralsDetector.EXTRA);
        issues.add(PluralsDetector.IMPLIED_QUANTITY);
        issues.add(PluralsDetector.MISSING);
        issues.add(PowerManagerDetector.INVALID_WAKE_LOCK_TAG);
        issues.add(PreferenceActivityDetector.ISSUE);
        issues.add(PrivateApiDetector.PRIVATE_API);
        issues.add(PrivateApiDetector.DISCOURAGED_PRIVATE_API);
        issues.add(PrivateApiDetector.SOON_BLOCKED_PRIVATE_API);
        issues.add(PrivateApiDetector.BLOCKED_PRIVATE_API);
        issues.add(PrivateKeyDetector.ISSUE);
        issues.add(PrivateResourceDetector.ISSUE);
        issues.add(ProguardDetector.SPLIT_CONFIG);
        issues.add(ProguardDetector.WRONG_KEEP);
        issues.add(PropertyFileDetector.ESCAPE);
        issues.add(PropertyFileDetector.HTTP);
        issues.add(PropertyFileDetector.PROXY_PASSWORD);
        issues.add(PxUsageDetector.DP_ISSUE);
        issues.add(PxUsageDetector.IN_MM_ISSUE);
        issues.add(PxUsageDetector.PX_ISSUE);
        issues.add(PxUsageDetector.SMALL_SP_ISSUE);
        issues.add(RangeDetector.RANGE);
        issues.add(ReadParcelableDetector.ISSUE);
        issues.add(RecyclerViewDetector.DATA_BINDER);
        issues.add(RecyclerViewDetector.FIXED_POSITION);
        issues.add(RegistrationDetector.ISSUE);
        issues.add(RelativeOverlapDetector.ISSUE);
        issues.add(RequiredAttributeDetector.ISSUE);
        issues.add(RequiresFeatureDetector.REQUIRES_FEATURE);
        issues.add(ResourceCycleDetector.CRASH);
        issues.add(ResourceCycleDetector.CYCLE);
        issues.add(ResourcePrefixDetector.ISSUE);
        issues.add(ResourceTypeDetector.COLOR_USAGE);
        issues.add(ResourceTypeDetector.HALF_FLOAT);
        issues.add(ResourceTypeDetector.RESOURCE_TYPE);
        issues.add(RestrictToDetector.RESTRICTED);
        issues.add(RestrictToDetector.TEST_VISIBILITY);
        issues.add(RestrictionsDetector.ISSUE);
        issues.add(RtlDetector.COMPAT);
        issues.add(RtlDetector.ENABLED);
        issues.add(RtlDetector.SYMMETRY);
        issues.add(RtlDetector.USE_START);
        issues.add(SamDetector.ISSUE);
        issues.add(SQLiteDetector.ISSUE);
        issues.add(ScrollViewChildDetector.ISSUE);
        issues.add(SdCardDetector.ISSUE);
        issues.add(SecureRandomDetector.ISSUE);
        issues.add(SecureRandomGeneratorDetector.ISSUE);
        issues.add(SecurityDetector.EXPORTED_PROVIDER);
        issues.add(SecurityDetector.EXPORTED_RECEIVER);
        issues.add(SecurityDetector.EXPORTED_SERVICE);
        issues.add(SecurityDetector.OPEN_PROVIDER);
        issues.add(SecurityDetector.SET_READABLE);
        issues.add(SecurityDetector.SET_WRITABLE);
        issues.add(SecurityDetector.WORLD_READABLE);
        issues.add(SecurityDetector.WORLD_WRITEABLE);
        issues.add(ServiceCastDetector.ISSUE);
        issues.add(ServiceCastDetector.WIFI_MANAGER);
        issues.add(ServiceCastDetector.WIFI_MANAGER_UNCERTAIN);
        issues.add(SetJavaScriptEnabledDetector.ISSUE);
        issues.add(SetTextDetector.SET_TEXT_I18N);
        issues.add(SignatureOrSystemDetector.ISSUE);
        issues.add(SliceDetector.ISSUE);
        issues.add(SslCertificateSocketFactoryDetector.CREATE_SOCKET);
        issues.add(SslCertificateSocketFactoryDetector.GET_INSECURE);
        issues.add(StartDestinationDetector.ISSUE);
        issues.add(StateListDetector.ISSUE);
        issues.add(StorageDetector.ISSUE);
        issues.add(StringAuthLeakDetector.AUTH_LEAK);
        issues.add(StringCasingDetector.DUPLICATE_STRINGS);
        issues.add(StringFormatDetector.ARG_COUNT);
        issues.add(StringFormatDetector.ARG_TYPES);
        issues.add(StringFormatDetector.INVALID);
        issues.add(StringFormatDetector.POTENTIAL_PLURAL);
        issues.add(SyntheticAccessorDetector.ISSUE);
        issues.add(SystemPermissionsDetector.ISSUE);
        issues.add(TextFieldDetector.ISSUE);
        issues.add(TextViewDetector.ISSUE);
        issues.add(TextViewDetector.SELECTABLE);
        issues.add(ThreadDetector.THREAD);
        issues.add(TitleDetector.ISSUE);
        issues.add(ToastDetector.ISSUE);
        issues.add(TooManyViewsDetector.TOO_DEEP);
        issues.add(TooManyViewsDetector.TOO_MANY);
        issues.add(TranslationDetector.EXTRA);
        issues.add(TranslationDetector.MISSING);
        issues.add(TranslationDetector.MISSING_BASE);
        issues.add(TranslationDetector.TRANSLATED_UNTRANSLATABLE);
        issues.add(TranslucentViewDetector.ISSUE);
        issues.add(TrustAllX509TrustManagerDetector.ISSUE);
        issues.add(TypedefDetector.TYPE_DEF);
        issues.add(TypoDetector.ISSUE);
        issues.add(TypographyDetector.DASHES);
        issues.add(TypographyDetector.ELLIPSIS);
        issues.add(TypographyDetector.FRACTIONS);
        issues.add(TypographyDetector.OTHER);
        issues.add(TypographyDetector.QUOTES);
        issues.add(UnpackedNativeCodeDetector.ISSUE);
        issues.add(UnsafeBroadcastReceiverDetector.ACTION_STRING);
        issues.add(UnsafeBroadcastReceiverDetector.BROADCAST_SMS);
        issues.add(UnsafeNativeCodeDetector.LOAD);
        issues.add(UnsafeNativeCodeDetector.UNSAFE_NATIVE_CODE_LOCATION);
        issues.add(UnusedNavigationDetector.ISSUE);
        issues.add(UnusedResourceDetector.ISSUE);
        issues.add(UnusedResourceDetector.ISSUE_IDS);
        issues.add(UseCompoundDrawableDetector.ISSUE);
        issues.add(UselessViewDetector.USELESS_LEAF);
        issues.add(UselessViewDetector.USELESS_PARENT);
        issues.add(Utf8Detector.ISSUE);
        issues.add(VectorDetector.ISSUE);
        issues.add(VectorDrawableCompatDetector.ISSUE);
        issues.add(VectorPathDetector.PATH_LENGTH);
        issues.add(VectorPathDetector.PATH_VALID);
        issues.add(ViewConstructorDetector.ISSUE);
        issues.add(ViewHolderDetector.ISSUE);
        issues.add(ViewTagDetector.ISSUE);
        issues.add(ViewTypeDetector.ADD_CAST);
        issues.add(ViewTypeDetector.WRONG_VIEW_CAST);
        issues.add(WakelockDetector.ISSUE);
        issues.add(WakelockDetector.TIMEOUT);
        issues.add(WearStandaloneAppDetector.INVALID_WEAR_FEATURE_ATTRIBUTE);
        issues.add(WearStandaloneAppDetector.WEAR_STANDALONE_APP_ISSUE);
        issues.add(WebViewApiAvailabilityDetector.ISSUE);
        issues.add(WebViewDetector.ISSUE);
        issues.add(WorkManagerDetector.ISSUE);
        issues.add(WrongCallDetector.ISSUE);
        issues.add(WrongCaseDetector.WRONG_CASE);
        issues.add(WrongIdDetector.INVALID);
        issues.add(WrongIdDetector.NOT_SIBLING);
        issues.add(WrongIdDetector.UNKNOWN_ID);
        issues.add(WrongIdDetector.UNKNOWN_ID_LAYOUT);
        issues.add(WrongImportDetector.ISSUE);
        issues.add(WrongLocationDetector.ISSUE);
        issues.add(WrongThreadInterproceduralDetector.ISSUE);

        sIssues = Collections.unmodifiableList(issues);
    }

    /** Constructs a new {@link BuiltinIssueRegistry} */
    public BuiltinIssueRegistry() {}

    @Override
    public boolean cacheable() {
        // In the IDE, cache across incremental runs; here, lint is never run in parallel
        // Outside of the IDE, typically in Gradle, we don't want this caching since
        // lint can run in parallel and this caching can be incorrect;
        // see for example issue 77891711
        return LintClient.Companion.isStudio();
    }

    @NonNull
    @Override
    public List<Issue> getIssues() {
        return sIssues;
    }

    @Override
    protected int getIssueCapacity(@NonNull EnumSet<Scope> scope) {
        if (scope.equals(Scope.ALL)) {
            return getIssues().size();
        } else {
            int initialSize = 12;
            if (scope.contains(Scope.RESOURCE_FILE)) {
                initialSize += 105;
            } else if (scope.contains(Scope.ALL_RESOURCE_FILES)) {
                initialSize += 12;
            }

            if (scope.contains(Scope.JAVA_FILE)) {
                initialSize += 127;
            } else if (scope.contains(Scope.CLASS_FILE)) {
                initialSize += 16;
            } else if (scope.contains(Scope.MANIFEST)) {
                initialSize += 54;
            } else if (scope.contains(Scope.GRADLE_FILE)) {
                initialSize += 15;
            }
            return initialSize;
        }
    }

    @Override
    public int getApi() {
        return ApiKt.CURRENT_API;
    }

    /**
     * Reset the registry such that it recomputes its available issues.
     *
     * <p>NOTE: This is only intended for testing purposes.
     */
    @VisibleForTesting
    public static void reset() {
        IssueRegistry.Companion.reset();
    }
}
