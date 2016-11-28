package com.android.build.gradle;

import com.android.build.gradle.internal.BadPluginException;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.DefaultBuildType;
import com.android.builder.model.SigningConfig;
import com.android.ide.common.signing.KeystoreHelper;
import com.google.common.collect.ImmutableMap;
import groovy.util.Eval;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import junit.framework.TestCase;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;

/** Tests for the internal workings of the app plugin ("android") */
public class AppPluginInternalTest extends BaseDslTest {

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        SdkHandler.setTestSdkFolder(new File(System.getenv("ANDROID_HOME")));
        project =
                ProjectBuilder.builder()
                        .withProjectDir(new File(getTestDir(), FOLDER_TEST_PROJECTS + "/basic"))
                        .build();

        project.apply(ImmutableMap.of("plugin", "com.android.application"));

        AppExtension android = project.getExtensions().getByType(AppExtension.class);
        android.setCompileSdkVersion(COMPILE_SDK_VERSION);
        android.setBuildToolsVersion(BUILD_TOOL_VERSION);
    }

    public void testBasic() {
        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks(true);

        TestCase.assertEquals(2, plugin.getVariantManager().getBuildTypes().size());
        TestCase.assertNotNull(
                plugin.getVariantManager().getBuildTypes().get(BuilderConstants.DEBUG));
        TestCase.assertNotNull(
                plugin.getVariantManager().getBuildTypes().get(BuilderConstants.RELEASE));
        TestCase.assertEquals(0, plugin.getVariantManager().getProductFlavors().size());

        List<BaseVariantData<?>> variants = plugin.getVariantManager().getVariantDataList();
        TestCase.assertEquals(
                DEFAULT_VARIANTS.size(), variants.size()); // includes the test variant(s)

        BaseDslTest.findVariantData(variants, "debug");
        BaseDslTest.findVariantData(variants, "release");
        BaseDslTest.findVariantData(variants, "debugAndroidTest");
    }

    public void testDefaultConfig() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    signingConfigs {\n"
                        + "        fakeConfig {\n"
                        + "            storeFile project.file('aa')\n"
                        + "            storePassword 'bb'\n"
                        + "            keyAlias 'cc'\n"
                        + "            keyPassword 'dd'\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    defaultConfig {\n"
                        + "        versionCode 1\n"
                        + "        versionName '2.0'\n"
                        + "        minSdkVersion 2\n"
                        + "        targetSdkVersion 3\n"
                        + "\n"
                        + "        signingConfig signingConfigs.fakeConfig\n"
                        + "    }\n"
                        + "}\n");

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks(true);

        TestCase.assertEquals(
                Integer.valueOf(1), plugin.getExtension().getDefaultConfig().getVersionCode());
        TestCase.assertNotNull(plugin.getExtension().getDefaultConfig().getMinSdkVersion());
        TestCase.assertEquals(
                2, plugin.getExtension().getDefaultConfig().getMinSdkVersion().getApiLevel());
        TestCase.assertNotNull(plugin.getExtension().getDefaultConfig().getTargetSdkVersion());
        TestCase.assertEquals(
                3, plugin.getExtension().getDefaultConfig().getTargetSdkVersion().getApiLevel());
        TestCase.assertEquals("2.0", plugin.getExtension().getDefaultConfig().getVersionName());

        TestCase.assertEquals(
                new File(project.getProjectDir(), "aa"),
                plugin.getExtension().getDefaultConfig().getSigningConfig().getStoreFile());
        TestCase.assertEquals(
                "bb",
                plugin.getExtension().getDefaultConfig().getSigningConfig().getStorePassword());
        TestCase.assertEquals(
                "cc", plugin.getExtension().getDefaultConfig().getSigningConfig().getKeyAlias());
        TestCase.assertEquals(
                "dd", plugin.getExtension().getDefaultConfig().getSigningConfig().getKeyPassword());
    }

    public void testBuildTypes() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    testBuildType 'staging'\n"
                        + "\n"
                        + "    buildTypes {\n"
                        + "        staging {\n"
                        + "            signingConfig signingConfigs.debug\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks(true);

        TestCase.assertEquals(3, plugin.getVariantManager().getBuildTypes().size());

        List<BaseVariantData<?>> variants = plugin.getVariantManager().getVariantDataList();
        LinkedHashMap<String, Integer> map = new LinkedHashMap<String, Integer>(3);
        map.put("appVariants", 3);
        map.put("unitTests", 3);
        map.put("androidTests", 1);
        TestCase.assertEquals(BaseDslTest.countVariants(map), variants.size());

        String[] variantNames = new String[] {"debug", "release", "staging"};

        for (String variantName : variantNames) {
            BaseDslTest.findVariantData(variants, variantName);
        }

        BaseVariantData testVariant = BaseDslTest.findVariantData(variants, "stagingAndroidTest");
        TestCase.assertEquals(
                "staging", testVariant.getVariantConfiguration().getBuildType().getName());
    }

    public void testFlavors() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    productFlavors {\n"
                        + "        flavor1 {\n"
                        + "\n"
                        + "        }\n"
                        + "        flavor2 {\n"
                        + "\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks(true);

        TestCase.assertEquals(2, plugin.getVariantManager().getProductFlavors().size());

        List<BaseVariantData<?>> variants = plugin.getVariantManager().getVariantDataList();
        LinkedHashMap<String, Integer> map = new LinkedHashMap<String, Integer>(3);
        map.put("appVariants", 4);
        map.put("unitTests", 4);
        map.put("androidTests", 2);
        TestCase.assertEquals(BaseDslTest.countVariants(map), variants.size());

        String[] variantNames =
                new String[] {
                    "flavor1Debug",
                    "flavor1Release",
                    "flavor1DebugAndroidTest",
                    "flavor2Debug",
                    "flavor2Release",
                    "flavor2DebugAndroidTest"
                };

        for (String variantName : variantNames) {
            BaseDslTest.findVariantData(variants, variantName);
        }
    }

    public void testMultiFlavors() {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    flavorDimensions   'dimension1', 'dimension2'\n"
                        + "\n"
                        + "    productFlavors {\n"
                        + "        f1 {\n"
                        + "            dimension 'dimension1'\n"
                        + "        }\n"
                        + "        f2 {\n"
                        + "            dimension 'dimension1'\n"
                        + "        }\n"
                        + "\n"
                        + "        fa {\n"
                        + "            dimension 'dimension2'\n"
                        + "        }\n"
                        + "        fb {\n"
                        + "            dimension 'dimension2'\n"
                        + "        }\n"
                        + "        fc {\n"
                        + "            dimension 'dimension2'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n");

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks(true);

        TestCase.assertEquals(5, plugin.getVariantManager().getProductFlavors().size());

        List<BaseVariantData<?>> variants = plugin.getVariantManager().getVariantDataList();
        LinkedHashMap<String, Integer> map = new LinkedHashMap<String, Integer>(3);
        map.put("appVariants", 12);
        map.put("unitTests", 12);
        map.put("androidTests", 6);
        TestCase.assertEquals(BaseDslTest.countVariants(map), variants.size());

        String[] variantNames =
                new String[] {
                    "f1FaDebug",
                    "f1FbDebug",
                    "f1FcDebug",
                    "f2FaDebug",
                    "f2FbDebug",
                    "f2FcDebug",
                    "f1FaRelease",
                    "f1FbRelease",
                    "f1FcRelease",
                    "f2FaRelease",
                    "f2FbRelease",
                    "f2FcRelease",
                    "f1FaDebugAndroidTest",
                    "f1FbDebugAndroidTest",
                    "f1FcDebugAndroidTest",
                    "f2FaDebugAndroidTest",
                    "f2FbDebugAndroidTest",
                    "f2FcDebugAndroidTest"
                };

        for (String variantName : variantNames) {
            BaseDslTest.findVariantData(variants, variantName);
        }
    }

    public void testSigningConfigs() throws Exception {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "\n"
                        + "    signingConfigs {\n"
                        + "        one {\n"
                        + "            storeFile project.file('a1')\n"
                        + "            storePassword 'b1'\n"
                        + "            keyAlias 'c1'\n"
                        + "            keyPassword 'd1'\n"
                        + "        }\n"
                        + "        two {\n"
                        + "            storeFile project.file('a2')\n"
                        + "            storePassword 'b2'\n"
                        + "            keyAlias 'c2'\n"
                        + "            keyPassword 'd2'\n"
                        + "        }\n"
                        + "        three {\n"
                        + "            storeFile project.file('a3')\n"
                        + "            storePassword 'b3'\n"
                        + "            keyAlias 'c3'\n"
                        + "            keyPassword 'd3'\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    defaultConfig {\n"
                        + "        versionCode 1\n"
                        + "        versionName '2.0'\n"
                        + "        minSdkVersion 2\n"
                        + "        targetSdkVersion 3\n"
                        + "    }\n"
                        + "\n"
                        + "    buildTypes {\n"
                        + "        debug {\n"
                        + "        }\n"
                        + "        staging {\n"
                        + "        }\n"
                        + "        release {\n"
                        + "            signingConfig owner.signingConfigs.three\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    productFlavors {\n"
                        + "        flavor1 {\n"
                        + "        }\n"
                        + "        flavor2 {\n"
                        + "            signingConfig owner.signingConfigs.one\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "}\n");

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        plugin.createAndroidTasks(true);

        List<BaseVariantData<?>> variants = plugin.getVariantManager().getVariantDataList();
        LinkedHashMap<String, Integer> map = new LinkedHashMap<String, Integer>(3);
        map.put("appVariants", 6);
        map.put("unitTests", 6);
        map.put("androidTests", 2);
        TestCase.assertEquals(BaseDslTest.countVariants(map), variants.size());

        BaseVariantData variant;
        SigningConfig signingConfig;

        variant = BaseDslTest.findVariantData(variants, "flavor1Debug");
        signingConfig = variant.getVariantConfiguration().getSigningConfig();
        TestCase.assertNotNull(signingConfig);
        final File file = ((CoreSigningConfig) signingConfig).getStoreFile();
        TestCase.assertEquals(
                KeystoreHelper.defaultDebugKeystoreLocation(),
                (file == null ? null : file.getAbsolutePath()));

        variant = BaseDslTest.findVariantData(variants, "flavor1Staging");
        signingConfig = variant.getVariantConfiguration().getSigningConfig();
        TestCase.assertNull(signingConfig);

        variant = BaseDslTest.findVariantData(variants, "flavor1Release");
        signingConfig = variant.getVariantConfiguration().getSigningConfig();
        TestCase.assertNotNull(signingConfig);
        TestCase.assertEquals(
                new File(project.getProjectDir(), "a3"),
                ((CoreSigningConfig) signingConfig).getStoreFile());

        variant = BaseDslTest.findVariantData(variants, "flavor2Debug");
        signingConfig = variant.getVariantConfiguration().getSigningConfig();
        TestCase.assertNotNull(signingConfig);
        final File file1 = ((CoreSigningConfig) signingConfig).getStoreFile();
        TestCase.assertEquals(
                KeystoreHelper.defaultDebugKeystoreLocation(),
                (file1 == null ? null : file1.getAbsolutePath()));

        variant = BaseDslTest.findVariantData(variants, "flavor2Staging");
        signingConfig = variant.getVariantConfiguration().getSigningConfig();
        TestCase.assertNotNull(signingConfig);
        TestCase.assertEquals(
                new File(project.getProjectDir(), "a1"),
                ((CoreSigningConfig) signingConfig).getStoreFile());

        variant = BaseDslTest.findVariantData(variants, "flavor2Release");
        signingConfig = variant.getVariantConfiguration().getSigningConfig();
        TestCase.assertNotNull(signingConfig);
        TestCase.assertEquals(
                new File(project.getProjectDir(), "a3"),
                ((CoreSigningConfig) signingConfig).getStoreFile());
    }

    /**
     * test that debug build type maps to the SigningConfig object as the signingConfig container
     */
    public void testDebugSigningConfig() throws Exception {
        Eval.me(
                "project",
                project,
                "\n"
                        + "\n"
                        + "project.android {\n"
                        + "    signingConfigs {\n"
                        + "        debug {\n"
                        + "            storePassword = 'foo'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);

        // check that the debug buildType has the updated debug signing config.
        DefaultBuildType buildType =
                (DefaultBuildType)
                        plugin.getVariantManager()
                                .getBuildTypes()
                                .get(BuilderConstants.DEBUG)
                                .getBuildType();
        SigningConfig signingConfig = buildType.getSigningConfig();
        TestCase.assertEquals(
                plugin.getVariantManager().getSigningConfigs().get(BuilderConstants.DEBUG),
                signingConfig);
        TestCase.assertEquals("foo", signingConfig.getStorePassword());
    }

    public void testSigningConfigInitWith() throws Exception {
        Project project =
                ProjectBuilder.builder()
                        .withProjectDir(new File(getTestDir(), FOLDER_TEST_PROJECTS + "/basic"))
                        .build();

        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(1);
        map.put("plugin", "com.android.application");
        project.apply(map);
        AppExtension android = project.getExtensions().getByType(AppExtension.class);
        android.setCompileSdkVersion(COMPILE_SDK_VERSION);
        android.setBuildToolsVersion(BUILD_TOOL_VERSION);

        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    signingConfigs {\n"
                        + "        foo.initWith(owner.signingConfigs.debug)\n"
                        + "    }\n"
                        + "}\n");

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);

        SigningConfig debugSC =
                plugin.getVariantManager().getSigningConfigs().get(BuilderConstants.DEBUG);
        SigningConfig fooSC = plugin.getVariantManager().getSigningConfigs().get("foo");

        TestCase.assertNotNull(fooSC);

        TestCase.assertEquals(debugSC.getStoreFile(), fooSC.getStoreFile());
        TestCase.assertEquals(debugSC.getStorePassword(), fooSC.getStorePassword());
        TestCase.assertEquals(debugSC.getKeyAlias(), fooSC.getKeyAlias());
        TestCase.assertEquals(debugSC.getKeyPassword(), fooSC.getKeyPassword());
    }

    public void testPluginDetection() {
        project.apply(ImmutableMap.of("plugin", "java"));

        AppExtension android = project.getExtensions().getByType(AppExtension.class);
        android.setCompileSdkVersion(COMPILE_SDK_VERSION);
        android.setBuildToolsVersion(BUILD_TOOL_VERSION);

        AppPlugin plugin = project.getPlugins().getPlugin(AppPlugin.class);
        Exception recordedException = null;
        try {
            plugin.createAndroidTasks(true);
        } catch (Exception e) {
            recordedException = e;
        }

        TestCase.assertNotNull(recordedException);
        TestCase.assertEquals(BadPluginException.class, recordedException.getClass());
    }

    private Project project;
}
