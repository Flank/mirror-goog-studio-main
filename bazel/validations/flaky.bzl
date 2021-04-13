"""Aspect to validate that only the specified targets are tagged as flaky.

When bazel is invoked with this aspect attached, it validates that only
the allowlisted targets below can set "flaky" to true.
"""

APPROVED_FLAKY_TESTS = [
    "//tools/base/build-system/integration-test/application:tests__GradlePluginMemoryLeakTest",  # b/153972155
    "//tools/base/build-system/integration-test/connected:UtpConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:FailureRetentionConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:TestWithSameDepAsAppConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:TestingSupportLibraryConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:SigningConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:ShardingConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:SeparateTestWithMinificationButNoObfuscationConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:SeparateTestWithAarDependencyConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:SeparateTestModuleWithMinifiedAppConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:SeparateTestModuleConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:SameNamedLibsConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:ResValueTypeConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:RenderscriptNdkConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:PkgOverrideConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:ParentLibsConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:Overlay3ConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:Overlay2ConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:Overlay1ConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:NoSplitNdkVariantsConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:NdkSanAngelesConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:NdkLibPrebuiltsConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:NdkJniLibConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:NdkConnectedCheckTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:MultiresConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:MultiProjectConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:MultiDexWithLibConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:MultiDexConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:MlModelBindingConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:MinifyLibConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:MinifyConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:MigratedConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:LibTestDepConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:LibsTestConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:LibMinifyLibDepConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:LibMinifyJarDepConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:KotlinAppConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:JarsInLibrariesConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:JacocoConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:FlavorsConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:FlavorlibConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:FlavoredlibConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:FlavoredConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:DynamicFeatureConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:DependenciesConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:DensitySplitConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:DataBindingIntegrationTestAppsConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:DataBindingExternalArtifactDependencyConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:D8DesugaringConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:CoreLibraryDesugarConversionConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:ComposeHelloWorldConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:CustomTestedApksTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:CmakeJniLibConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:BasicConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:ApplibtestConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:AttrOrderConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:ApiConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:AnnotationProcessorConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:AndroidTestResourcesConnectedTest",  # b/148626301
    "//tools/base/build-system/integration-test/connected:AdditionalTestOutputConnectedTest",  # b/148626301
    "//tools/adt/idea/adt-ui:intellij.android.adt.ui_tests",  # b/172521726
    "//tools/base/ddmlib:studio.android.sdktools.ddmlib.integration.tests_tests",  # b/175217297
    "//tools/adt/idea/android-uitests:RunOnEmulatorTest",  # b/181567595
    "//tools/adt/idea/android-uitests:CreateCppKotlinProjectTest",  # b/185998794
    "//tools/adt/idea/android-uitests:BasicLayoutEditTest",  # b/186225187
    "//tools/adt/idea/android-uitests:NewComposeProjectTest",  # b/186225189
]

FAILURE_MESSAGE = """Test target {} has flaky set to true.
Only approved targets can set flaky attribute to true (b/159928949).
If this is intentional, contact android-devtools-infra@ to approve the target."""

IGNORE_TAG = []

def _has_intersect(this, other):
    for item in this:
        if item in other:
            return True
    return False

def _limit_flaky_tests_impl(target, ctx):
    if ctx.rule.kind.endswith("_test"):
        if ctx.rule.attr.flaky and str(ctx.label) not in APPROVED_FLAKY_TESTS:
            if not _has_intersect(IGNORE_TAG, ctx.rule.attr.tags):
                fail(FAILURE_MESSAGE.format(str(ctx.label)))
    return []

limit_flaky_tests = aspect(
    implementation = _limit_flaky_tests_impl,
    attr_aspects = [],
)
