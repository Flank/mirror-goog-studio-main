"""Aspect to validate that only the specified targets use eternal timeout.

When bazel is invoked with this aspect attached, it validates that only
the allowlisted targets below can use the eternal timeout.
"""
APPROVED_ETERNAL_TESTS = [
    "//tools/vendor/google/android-apk:android-apk.tests_tests",
    "//tools/adt/idea/sync-perf-tests:intellij.android.sync-perf-tests_tests__all",
    "//tools/adt/idea/studio:searchable_options_test",
    "//tools/adt/idea/old-agp-tests:intellij.android.old-agp-tests_tests",
    "//tools/adt/idea/ide-perf-tests:intellij.android.ide-perf-tests_tests__all",
    "//tools/adt/idea/designer-perf-tests:intellij.android.designer-perf-tests_tests",
    "//tools/adt/idea/android-uitests:X86AbiSplitApksTest",
    "//tools/adt/idea/android-uitests:WatchpointTest",
    "//tools/adt/idea/android-uitests:SmartStepIntoTest",
    "//tools/adt/idea/android-uitests:SessionRestartTest",
    "//tools/adt/idea/android-uitests:RunInstrumentationTest",
    "//tools/adt/idea/android-uitests:NativeDebuggerInNdkProjectTest",
    "//tools/adt/idea/android-uitests:NativeDebuggerBreakpointsTest",
    "//tools/adt/idea/android-uitests:JavaDebuggerTest",
    "//tools/adt/idea/android-uitests:InstrumentationTest",
    "//tools/adt/idea/android-uitests:InstantAppRunFromCmdLineTest",
    "//tools/adt/idea/android-uitests:ImportAndRunInstantAppTest",
    "//tools/adt/idea/android-uitests:FlavorsExecutionTest",
    "//tools/adt/idea/android-uitests:EspressoRecorderTest",
    "//tools/adt/idea/android-uitests:DualDebuggerInNdkProjectTest",
    "//tools/adt/idea/android-uitests:DualDebuggerBreakpointsTest",
    "//tools/adt/idea/android-uitests:DebugOnEmulatorTest",
    "//tools/adt/idea/android-uitests:CreateAndRunInstantAppTest",
    "//tools/adt/idea/android-uitests:CreateAPKProjectTest",
    "//tools/adt/idea/android-uitests:BuildAndRunCMakeProjectTest",
    "//tools/adt/idea/android-uitests:AutoDebuggerInNdkProjectTest",
    "//tools/adt/idea/android-uitests:AbiSplitApksTest",
    "//tools/adt/idea/android-templates:intellij.android.templates.tests_tests",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__testartifacts",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__other",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__navigator",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__jetbrains",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__gradle.structure.model.android",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__gradle.structure",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__gradle.project.sync.snapshots",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__gradle.project.sync",
    "//tools/adt/idea/android:intellij.android.core.tests_tests__gradle",
]

FAILURE_MESSAGE = """Test target {} has timeout set to eternal.
We do not want any new target with eternal timeout (b/162943254).
If this is intentional, contact android-devtools-infra@ to relax the restriction on the target."""

IGNORE_TAG = ["perfgate"]

def _has_intersect(this, other):
    for item in this:
        if item in other:
            return True
    return False

def _no_eternal_tests_impl(target, ctx):
    if ctx.rule.kind.endswith("_test"):
        if ctx.rule.attr.timeout == "eternal" and str(ctx.label) not in APPROVED_ETERNAL_TESTS:
            if not _has_intersect(IGNORE_TAG, ctx.rule.attr.tags):
                fail(FAILURE_MESSAGE.format(str(ctx.label)))
    return []

no_eternal_tests = aspect(
    implementation = _no_eternal_tests_impl,
    attr_aspects = [],
)
