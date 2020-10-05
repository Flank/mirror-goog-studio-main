#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

if [[ $build_number =~ ^[0-9]+$ ]];
then
  IS_POST_SUBMIT=true
fi

readonly script_dir="$(dirname "$0")"
readonly script_name="$(basename "$0")"

# Invocation ID must be lower case in Upsalite URL
readonly invocation_id=$(uuidgen | tr A-F a-f)

readonly config_options="--config=local --config=release --config=cloud_resultstore"

"${script_dir}/bazel" \
        --max_idle_secs=60 \
        test \
        --keep_going \
        ${config_options} \
        --invocation_id=${invocation_id} \
        --build_tag_filters=-no_mac \
        --build_event_binary_file="${dist_dir}/bazel-${build_number}.bes" \
        --test_tag_filters=-no_mac,-no_test_mac,-qa_sanity,-qa_fast,-qa_unreliable,-perfgate \
        --tool_tag=${script_name} \
        --define=meta_android_build_number=${build_number} \
        --profile=${dist_dir}/profile-${build_number}.json.gz \
        -- \
        //tools/... \
        //tools/base/profiler/native/trace_processor_daemon \
        //tools/vendor/adt_infra_internal/rbe/logscollector:logs-collector_deploy.jar \
        -//tools/base/build-system/integration-test/... \
        -//tools/adt/idea/android-lang:intellij.android.lang.tests_tests \
        -//tools/adt/idea/profilers-ui:intellij.android.profilers.ui_tests \
        -//tools/base/build-system/builder:tests.test \
        -//tools/adt/idea/android-lang-databinding:intellij.android.lang-databinding.tests \
        -//tools/adt/idea/android-lang-databinding:intellij.android.lang-databinding.tests_runtime \
        -//tools/adt/idea/android-lang-databinding:intellij.android.lang-databinding.tests_testlib \
        -//tools/adt/idea/android-lang-databinding:intellij.android.lang-databinding.tests_tests \
        -//tools/adt/idea/android-uitests:AbiSplitApksTest \
        -//tools/adt/idea/android-uitests:AdaptiveIconsTest \
        -//tools/adt/idea/android-uitests:AddAppCompatLibTest \
        -//tools/adt/idea/android-uitests:AddCppToModuleActionTest \
        -//tools/adt/idea/android-uitests:AddKotlinTest \
        -//tools/adt/idea/android-uitests:AddLocaleTest \
        -//tools/adt/idea/android-uitests:AddNewBuildTypeTest \
        -//tools/adt/idea/android-uitests:AddRemoveCppDependencyTest \
        -//tools/adt/idea/android-uitests:AndroidDepTest \
        -//tools/adt/idea/android-uitests:AndroidLibsDepTest \
        -//tools/adt/idea/android-uitests:AndroidProfilerTest \
        -//tools/adt/idea/android-uitests:AndroidVectorDrawableToolTest \
        -//tools/adt/idea/android-uitests:ApkViewerTest \
        -//tools/adt/idea/android-uitests:AutoDebuggerInNdkProjectTest \
        -//tools/adt/idea/android-uitests:BaselineConstraintHandlingTest \
        -//tools/adt/idea/android-uitests:BasicLayoutEditTest \
        -//tools/adt/idea/android-uitests:BuildAndRunCMakeProjectTest \
        -//tools/adt/idea/android-uitests:BuildCppKotlinTest \
        -//tools/adt/idea/android-uitests:BuildTypesTest \
        -//tools/adt/idea/android-uitests:CLionIntegrationTest \
        -//tools/adt/idea/android-uitests:CMakeListsTest \
        -//tools/adt/idea/android-uitests:ChangeLibModSettingsTest \
        -//tools/adt/idea/android-uitests:ClearConstraintTest \
        -//tools/adt/idea/android-uitests:CodeConversionFromJavaToKotlinTest \
        -//tools/adt/idea/android-uitests:CompileWithJava8Test \
        -//tools/adt/idea/android-uitests:ConstraintLayoutAnchorExemptionTest \
        -//tools/adt/idea/android-uitests:ConstraintLayoutResizeHandleTest \
        -//tools/adt/idea/android-uitests:ConstraintLayoutTest \
        -//tools/adt/idea/android-uitests:ConvertFrom9PatchTest \
        -//tools/adt/idea/android-uitests:ConvertFromWebpToPngTest \
        -//tools/adt/idea/android-uitests:ConvertToWebpActionTest \
        -//tools/adt/idea/android-uitests:CreateAPKProjectTest \
        -//tools/adt/idea/android-uitests:CreateAndRunInstantAppTest \
        -//tools/adt/idea/android-uitests:CreateBaselineConnectionTest \
        -//tools/adt/idea/android-uitests:CreateBasicKotlinProjectTest \
        -//tools/adt/idea/android-uitests:CreateCppKotlinProjectTest \
        -//tools/adt/idea/android-uitests:CreateDefaultActivityTest \
        -//tools/adt/idea/android-uitests:CreateFromPreexistingApkTest \
        -//tools/adt/idea/android-uitests:CreateLoginActivityTest \
        -//tools/adt/idea/android-uitests:CreateNavGraphTest \
        -//tools/adt/idea/android-uitests:CreateNewAppModuleWithDefaultsTest \
        -//tools/adt/idea/android-uitests:CreateNewFlavorsTest \
        -//tools/adt/idea/android-uitests:CreateNewLibraryModuleWithDefaultsTest \
        -//tools/adt/idea/android-uitests:CreateNewMobileProjectTest \
        -//tools/adt/idea/android-uitests:CreateNewProjectWithCpp1Test \
        -//tools/adt/idea/android-uitests:CreateNewProjectWithCpp2Test \
        -//tools/adt/idea/android-uitests:CreateNewProjectWithCpp3Test \
        -//tools/adt/idea/android-uitests:CreateSettingsActivityTest \
        -//tools/adt/idea/android-uitests:DebugOnEmulatorTest \
        -//tools/adt/idea/android-uitests:DeploymentTest \
        -//tools/adt/idea/android-uitests:DualDebuggerBreakpointsTest \
        -//tools/adt/idea/android-uitests:DualDebuggerInNdkProjectTest \
        -//tools/adt/idea/android-uitests:EmbeddedEmulatorTest \
        -//tools/adt/idea/android-uitests:EspressoRecorderTest \
        -//tools/adt/idea/android-uitests:FilterIconTest \
        -//tools/adt/idea/android-uitests:FlavorsEditingTest \
        -//tools/adt/idea/android-uitests:FlavorsExecutionTest \
        -//tools/adt/idea/android-uitests:ForeachLiveTemplateTest \
        -//tools/adt/idea/android-uitests:ForiLiveTemplateTest \
        -//tools/adt/idea/android-uitests:GenerateApkWithReleaseVariantTest \
        -//tools/adt/idea/android-uitests:GradleSyncTest \
        -//tools/adt/idea/android-uitests:GuiTestRuleTest \
        -//tools/adt/idea/android-uitests:IdePermissionTest \
        -//tools/adt/idea/android-uitests:ImageAssetErrorCheckTest \
        -//tools/adt/idea/android-uitests:ImageAssetGradleTest \
        -//tools/adt/idea/android-uitests:ImportAndRunInstantAppTest \
        -//tools/adt/idea/android-uitests:ImportSampleProjectTest \
        -//tools/adt/idea/android-uitests:InferNullityTest \
        -//tools/adt/idea/android-uitests:InstallPackageTest \
        -//tools/adt/idea/android-uitests:InstantAppRunFromCmdLineTest \
        -//tools/adt/idea/android-uitests:InstrumentationTest \
        -//tools/adt/idea/android-uitests:JavaDebuggerTest \
        -//tools/adt/idea/android-uitests:JavaDepTest \
        -//tools/adt/idea/android-uitests:JavaLibsDepTest \
        -//tools/adt/idea/android-uitests:JavaToKotlinConversionTest \
        -//tools/adt/idea/android-uitests:LaunchApkViewerTest \
        -//tools/adt/idea/android-uitests:LayoutInspectorTest \
        -//tools/adt/idea/android-uitests:LintCheckWithKotlinTest \
        -//tools/adt/idea/android-uitests:LintTest \
        -//tools/adt/idea/android-uitests:LocalApkProjTest \
        -//tools/adt/idea/android-uitests:ModifyMinSdkAndSyncTest \
        -//tools/adt/idea/android-uitests:NameWithSpaceAndDollarTest \
        -//tools/adt/idea/android-uitests:NativeDebuggerBreakpointsTest \
        -//tools/adt/idea/android-uitests:NativeDebuggerInNdkProjectTest \
        -//tools/adt/idea/android-uitests:NdkSxsTest \
        -//tools/adt/idea/android-uitests:NewActivityTest \
        -//tools/adt/idea/android-uitests:NewComposeProjectTest \
        -//tools/adt/idea/android-uitests:NewModuleTest \
        -//tools/adt/idea/android-uitests:NewProjectTest \
        -//tools/adt/idea/android-uitests:NlEditorTest \
        -//tools/adt/idea/android-uitests:NoGradleSyncForProjectReimportTest \
        -//tools/adt/idea/android-uitests:OpenCloseVisualizationToolTest \
        -//tools/adt/idea/android-uitests:OpenExistingProjectTest \
        -//tools/adt/idea/android-uitests:PrivateResourceTest \
        -//tools/adt/idea/android-uitests:QuickFixForJniTest \
        -//tools/adt/idea/android-uitests:ResolveXmlReferencesTest \
        -//tools/adt/idea/android-uitests:RunInstrumentationTest \
        -//tools/adt/idea/android-uitests:RunOnEmulatorTest \
        -//tools/adt/idea/android-uitests:SessionRestartTest \
        -//tools/adt/idea/android-uitests:ShortcutNavigationTest \
        -//tools/adt/idea/android-uitests:SideConstraintHandlingTest \
        -//tools/adt/idea/android-uitests:SmartStepIntoTest \
        -//tools/adt/idea/android-uitests:SurroundWithShortcutTest \
        -//tools/adt/idea/android-uitests:TerminateAdbIfNotUsedTest \
        -//tools/adt/idea/android-uitests:ThemeEditorTest \
        -//tools/adt/idea/android-uitests:ToastLiveTemplateTest \
        -//tools/adt/idea/android-uitests:UnusedResourceEvaluationTest \
        -//tools/adt/idea/android-uitests:WatchpointTest \
        -//tools/adt/idea/android-uitests:X86AbiSplitApksTest \
        -//tools/adt/idea/android:intellij.android.core.tests \
        -//tools/adt/idea/android:intellij.android.core.tests_runtime \
        -//tools/adt/idea/android:intellij.android.core.tests_testlib \
        -//tools/adt/idea/android:intellij.android.core.tests_tests \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__AndroidKotlinCompletionContributorTest \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__AndroidLintCustomCheckTest \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__all \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__avd \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__editors.manifest \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__gradle \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__gradle.project.sync \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__gradle.project.sync.snapshots \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__gradle.structure \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__gradle.structure.configurables.dependencies.treeview \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__gradle.structure.configurables.variables.VariablesTableTest \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__gradle.structure.model.DependencyManagementTest \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__gradle.structure.model.android \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__gradle.variant.view.BuildVariantUpdaterIntegTest \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__imports \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__instantapp \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__jetbrains \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__jetbrains.android.refactoring.MigrateToAndroidxGradleGroovyTest \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__jetbrains.android.refactoring.MigrateToAndroidxGradleKtsTest \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__navigator \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__navigator.AndroidGradleProjectViewSnapshotComparisonTest \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__navigator.SourceProvidersSnapshotComparisonTest \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__npw.assetstudio \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__other \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__rendering \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__run \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__templates \
        -//tools/adt/idea/android:intellij.android.core.tests_tests__testartifacts \
        -//tools/adt/idea/androidx-integration-tests:intellij.android.androidx-integration-tests \
        -//tools/adt/idea/androidx-integration-tests:intellij.android.androidx-integration-tests_runtime \
        -//tools/adt/idea/androidx-integration-tests:intellij.android.androidx-integration-tests_testlib \
        -//tools/adt/idea/androidx-integration-tests:intellij.android.androidx-integration-tests_tests \
        -//tools/adt/idea/compose-designer:intellij.android.compose-designer.tests \
        -//tools/adt/idea/compose-designer:intellij.android.compose-designer.tests_runtime \
        -//tools/adt/idea/compose-designer:intellij.android.compose-designer.tests_testlib \
        -//tools/adt/idea/compose-designer:intellij.android.compose-designer.tests_tests \
        -//tools/adt/idea/compose-designer:intellij.android.compose-designer.tests_tests__all \
        -//tools/adt/idea/compose-designer:intellij.android.compose-designer.tests_tests__gradle \
        -//tools/adt/idea/compose-designer:intellij.android.compose-designer.tests_tests__non-gradle \
        -//tools/adt/idea/databinding:intellij.android.databinding.tests \
        -//tools/adt/idea/databinding:intellij.android.databinding.tests_runtime \
        -//tools/adt/idea/databinding:intellij.android.databinding.tests_testlib \
        -//tools/adt/idea/databinding:intellij.android.databinding.tests_tests \
        -//tools/adt/idea/databinding:intellij.android.databinding.tests_tests__all \
        -//tools/adt/idea/databinding:intellij.android.databinding.tests_tests__gradle \
        -//tools/adt/idea/databinding:intellij.android.databinding.tests_tests__non-gradle \
        -//tools/adt/idea/old-agp-tests:intellij.android.old-agp-tests \
        -//tools/adt/idea/old-agp-tests:intellij.android.old-agp-tests_runtime \
        -//tools/adt/idea/old-agp-tests:intellij.android.old-agp-tests_testlib \
        -//tools/adt/idea/old-agp-tests:intellij.android.old-agp-tests_tests \
        -//tools/adt/idea/resources-base:intellij.android.resources-base.tests \
        -//tools/adt/idea/resources-base:intellij.android.resources-base.tests_runtime \
        -//tools/adt/idea/resources-base:intellij.android.resources-base.tests_testlib \
        -//tools/adt/idea/resources-base:intellij.android.resources-base.tests_tests \
        -//tools/adt/idea/sync-perf-tests:intellij.android.sync-perf-tests \
        -//tools/adt/idea/sync-perf-tests:intellij.android.sync-perf-tests_runtime \
        -//tools/adt/idea/sync-perf-tests:intellij.android.sync-perf-tests_testlib \
        -//tools/adt/idea/sync-perf-tests:intellij.android.sync-perf-tests_tests \
        -//tools/adt/idea/sync-perf-tests:intellij.android.sync-perf-tests_tests__Base100 \
        -//tools/adt/idea/sync-perf-tests:intellij.android.sync-perf-tests_tests__Dolphin \
        -//tools/adt/idea/sync-perf-tests:intellij.android.sync-perf-tests_tests__all \
        -//tools/adt/idea/sync-perf-tests:intellij.android.sync-perf-tests_tests__other \
        -//tools/base/bazel/test/gradle:lib \
        -//tools/base/build-system/integration-test/databinding/incremental:tests \
        -//tools/base/build-system/integration-test/databinding:tests \
        -//tools/data-binding:data_binding_runtime.zip \
        -//tools/data-binding:data_binding_runtime_androidx.zip \
        -//tools/data-binding:data_binding_runtime_support.zip \
        -//tools/data-binding:runtimeLibraries \
        -//tools/data-binding:runtimeLibrariesAndroidX \
        -//tools/data-binding:runtimeLibrariesSupport \
        -//tools/idea/community-main-tests:intellij.idea.community.main.tests \
        -//tools/idea/community-main-tests:intellij.idea.community.main.tests_runtime \
        -//tools/idea/community-main-tests:intellij.idea.community.main.tests_testlib \
        -//tools/vendor/google/android-apk:android-apk.tests \
        -//tools/vendor/google/android-apk:android-apk.tests_runtime \
        -//tools/vendor/google/android-apk:android-apk.tests_testlib \
        -//tools/vendor/google/android-apk:android-apk.tests_tests \
        -//tools/vendor/google/android-ndk:android-ndk.tests \
        -//tools/vendor/google/android-ndk:android-ndk.tests_runtime \
        -//tools/vendor/google/android-ndk:android-ndk.tests_testlib \
        -//tools/vendor/google/android-ndk:android-ndk.tests_tests \
        -//tools/vendor/google/android-ndk:android-ndk.tests_tests__NativeGradleSyncProjectComparisonTest \
        -//tools/vendor/google/android-ndk:android-ndk.tests_tests__NativeProjectViewSnapshotComparisonTest \
        -//tools/vendor/google/android-ndk:android-ndk.tests_tests__NativeSourceProvidersSnapshotComparisonTest \
        -//tools/vendor/google/android-ndk:android-ndk.tests_tests__all \
        -//tools/vendor/google/android-ndk:android-ndk.tests_tests__integration.model \
        -//tools/vendor/google/android-ndk:android-ndk.tests_tests__integration.navigator \
        -//tools/vendor/google/android-ndk:android-ndk.tests_tests__integration.sync \
        -//tools/vendor/google/android-ndk:android-ndk.tests_tests__other \
        -//tools/vendor/google/android-ndk:android-ndk.tests_tests__sync \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_runtime \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_testlib \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__AttachBenchmarkTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__BasicDebuggerTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__BasicDualDebuggerTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__DualAttachTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__ExpressionEvaluationTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__FlagsWarningTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__FramesBenchmarkTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__InjectorSessionStarterTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__InlinedFunctionFramesTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__JavaDebuggerTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__JstringPrettyPrinterTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__LldbSettingsTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__LocalVariablesBenchmarkTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__NativeAttachTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__NativeAttachToGlobalProcessTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__NativeAttachToLocalProcessTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__PullLogsAfterFailureTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__ResumeBenchmarkTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__SegfaultTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__SmartStepInTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__StaticInitializerBreakpointTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__StepBenchmarkTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__StepIntoNativeTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__StlDebuggerTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__WatchpointTest \
        -//tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests__all

readonly bazel_status=$?

if [[ -d "${dist_dir}" ]]; then
  # info breaks if we pass --config=local or --config=cloud_resultstore because they don't
  # affect info, so we need to pass only --config=release here in order to fetch the proper
  # binaries
  readonly bin_dir="$("${script_dir}"/bazel info --config=release bazel-bin)"
  cp -a ${bin_dir}/tools/base/dynamic-layout-inspector/skiaparser.zip ${dist_dir}
  cp -a ${bin_dir}/tools/base/profiler/native/trace_processor_daemon/trace_processor_daemon ${dist_dir}
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${dist_dir}"/upsalite_test_results.html

  readonly java="prebuilts/studio/jdk/mac/Contents/Home/bin/java"
  ${java} -jar "${bin_dir}/tools/vendor/adt_infra_internal/rbe/logscollector/logs-collector_deploy.jar" \
    -bes "${dist_dir}/bazel-${build_number}.bes" \
    -testlogs "${dist_dir}/logs/junit"
fi

BAZEL_EXITCODE_TEST_FAILURES=3

# For post-submit builds, if the tests fail we still want to report success
# otherwise ATP will think the build failed and there are no tests. b/152755167
if [[ $IS_POST_SUBMIT && $bazel_status == $BAZEL_EXITCODE_TEST_FAILURES ]]; then
  exit 0
else
  exit $bazel_status
fi
