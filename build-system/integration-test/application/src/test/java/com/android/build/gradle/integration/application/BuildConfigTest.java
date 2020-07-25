package com.android.build.gradle.integration.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.BuildType;
import com.android.builder.model.ClassField;
import com.android.builder.model.ProductFlavor;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Test for BuildConfig field declared in build type, flavors, and variant and how they override
 * each other
 */
public class BuildConfigTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestApp(HelloWorldApp.noBuildFile()).create();

    private static AndroidProject model;

    @BeforeClass
    public static void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "  compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "  buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "\n"
                        + "  defaultConfig {\n"
                        + "    buildConfigField \"int\", \"VALUE_DEFAULT\", \"1\"\n"
                        + "    buildConfigField \"int\", \"VALUE_DEBUG\",   \"1\"\n"
                        + "    buildConfigField \"java.util.OptionalInt\", \"VALUE_EXPRESSION\", \"java.util.OptionalInt.empty()\"\n"
                        + "    buildConfigField \"String[]\", \"VALUE_STRING_ARRAY\", 'new String[]{\"hello\", \"world\"}'\n"
                        + "    buildConfigField \"String[]\", \"CALCULATED_STRING\", 'String.format(\"VALUE_DEFAULT=%1$d\", VALUE_DEFAULT)'\n"
                        + "    buildConfigField \"long\", \"VALUE_LONG\", \"50L\"\n"
                        + "    buildConfigField \"int\", \"VALUE_FLAVOR\",  \"1\"\n"
                        + "    buildConfigField \"float\", \"VALUE_FLOAT\", \"5f\"\n"
                        + "    buildConfigField \"int\", \"VALUE_VARIANT\", \"1\"\n"
                        + "  }\n"
                        + "\n"
                        + "  buildTypes {\n"
                        + "    debug {\n"
                        + "      buildConfigField \"int\", \"VALUE_DEBUG\",   \"100\"\n"
                        + "      buildConfigField \"int\", \"VALUE_VARIANT\", \"100\"\n"
                        + "    }\n"
                        + "  }\n"
                        + "\n"
                        + "  flavorDimensions 'foo'\n"
                        + "  productFlavors {\n"
                        + "    flavor1 {\n"
                        + "      buildConfigField \"int\", \"VALUE_DEBUG\",   \"10\"\n"
                        + "      buildConfigField \"int\", \"VALUE_FLAVOR\",  \"10\"\n"
                        + "      buildConfigField \"int\", \"VALUE_VARIANT\", \"10\"\n"
                        + "    }\n"
                        + "    flavor2 {\n"
                        + "      buildConfigField \"int\", \"VALUE_DEBUG\",   \"20\"\n"
                        + "      buildConfigField \"int\", \"VALUE_FLAVOR\",  \"20\"\n"
                        + "      buildConfigField \"int\", \"VALUE_VARIANT\", \"20\"\n"
                        + "    }\n"
                        + "  }\n"
                        + "\n"
                        + "  applicationVariants.all { variant ->\n"
                        + "    if (variant.buildType.name == \"debug\") {\n"
                        + "      variant.buildConfigField \"int\", \"VALUE_VARIANT\", \"1000\"\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n");

        model =
                project.executeAndReturnModel(
                                "clean",
                                "generateFlavor1DebugBuildConfig",
                                "generateFlavor1ReleaseBuildConfig",
                                "generateFlavor2DebugBuildConfig",
                                "generateFlavor2ReleaseBuildConfig")
                        .getOnlyModel();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void modelDefaultConfig() {
        Map<String, String> map = Maps.newHashMap();
        map.put("VALUE_DEBUG", "1");
        map.put("VALUE_DEFAULT", "1");
        map.put("VALUE_EXPRESSION", "java.util.OptionalInt.empty()");
        map.put("CALCULATED_STRING", "String.format(\"VALUE_DEFAULT=%1$d\", VALUE_DEFAULT)");
        map.put("VALUE_STRING_ARRAY", "new String[]{\"hello\", \"world\"}");
        map.put("VALUE_FLAVOR", "1");
        map.put("VALUE_FLOAT", "5f");
        map.put("VALUE_LONG", "50L");
        map.put("VALUE_VARIANT", "1");
        checkMaps(
                map,
                model.getDefaultConfig().getProductFlavor().getBuildConfigFields(),
                "defaultConfig");
    }

    @Test
    public void buildFlavor1Debug() throws IOException {
        String expected =
                "/**\n"
                        + " * Automatically generated file. DO NOT MODIFY\n"
                        + " */\n"
                        + "package com.example.helloworld;\n"
                        + "\n"
                        + "public final class BuildConfig {\n"
                        + "  public static final boolean DEBUG = Boolean.parseBoolean(\"true\");\n"
                        + "  public static final String APPLICATION_ID = \"com.example.helloworld\";\n"
                        + "  public static final String BUILD_TYPE = \"debug\";\n"
                        + "  public static final String FLAVOR = \"flavor1\";\n"
                        + "  public static final int VERSION_CODE = 1;\n"
                        + "  public static final String VERSION_NAME = \"1.0\";\n"
                        + "  // Field from default config.\n"
                        + "  public static final String[] CALCULATED_STRING = String.format(\"VALUE_DEFAULT=%1$d\", VALUE_DEFAULT);\n"
                        + "  // Field from build type: debug\n"
                        + "  public static final int VALUE_DEBUG = 100;\n"
                        + "  // Field from default config.\n"
                        + "  public static final int VALUE_DEFAULT = 1;\n"
                        + "  // Field from default config.\n"
                        + "  public static final java.util.OptionalInt VALUE_EXPRESSION = java.util.OptionalInt.empty();\n"
                        + "  // Field from product flavor: flavor1\n"
                        + "  public static final int VALUE_FLAVOR = 10;\n"
                        + "  // Field from default config.\n"
                        + "  public static final float VALUE_FLOAT = 5f;\n"
                        + "  // Field from default config.\n"
                        + "  public static final long VALUE_LONG = 50L;\n"
                        + "  // Field from default config.\n"
                        + "  public static final String[] VALUE_STRING_ARRAY = new String[]{\"hello\", \"world\"};\n"
                        + "  // Field from the variant API\n"
                        + "  public static final int VALUE_VARIANT = 1000;\n"
                        + "}\n";
        doCheckBuildConfig(expected, "flavor1/debug");
    }

    @Test
    public void modelFlavor1() {
        Map<String, String> map = Maps.newHashMap();
        map.put("VALUE_FLAVOR", "10");
        map.put("VALUE_DEBUG", "10");
        map.put("VALUE_VARIANT", "10");
        checkFlavor(model, "flavor1", map);
    }

    @Test
    public void buildFlavor2Debug() throws IOException {
        String expected =
                "/**\n"
                        + " * Automatically generated file. DO NOT MODIFY\n"
                        + " */\n"
                        + "package com.example.helloworld;\n"
                        + "\n"
                        + "public final class BuildConfig {\n"
                        + "  public static final boolean DEBUG = Boolean.parseBoolean(\"true\");\n"
                        + "  public static final String APPLICATION_ID = \"com.example.helloworld\";\n"
                        + "  public static final String BUILD_TYPE = \"debug\";\n"
                        + "  public static final String FLAVOR = \"flavor2\";\n"
                        + "  public static final int VERSION_CODE = 1;\n"
                        + "  public static final String VERSION_NAME = \"1.0\";\n"
                        + "  // Field from default config.\n"
                        + "  public static final String[] CALCULATED_STRING = String.format(\"VALUE_DEFAULT=%1$d\", VALUE_DEFAULT);\n"
                        + "  // Field from build type: debug\n"
                        + "  public static final int VALUE_DEBUG = 100;\n"
                        + "  // Field from default config.\n"
                        + "  public static final int VALUE_DEFAULT = 1;\n"
                        + "  // Field from default config.\n"
                        + "  public static final java.util.OptionalInt VALUE_EXPRESSION = java.util.OptionalInt.empty();\n"
                        + "  // Field from product flavor: flavor2\n"
                        + "  public static final int VALUE_FLAVOR = 20;\n"
                        + "  // Field from default config.\n"
                        + "  public static final float VALUE_FLOAT = 5f;\n"
                        + "  // Field from default config.\n"
                        + "  public static final long VALUE_LONG = 50L;\n"
                        + "  // Field from default config.\n"
                        + "  public static final String[] VALUE_STRING_ARRAY = new String[]{\"hello\", \"world\"};\n"
                        + "  // Field from the variant API\n"
                        + "  public static final int VALUE_VARIANT = 1000;\n"
                        + "}\n";
        doCheckBuildConfig(expected, "flavor2/debug");
    }

    @Test
    public void modelDebug() {
        Map<String, String> map = Maps.newHashMap();
        map.put("VALUE_DEBUG", "100");
        map.put("VALUE_VARIANT", "100");
        checkBuildType(model, "debug", map);
    }

    @Test
    public void buildFlavor1Release() throws IOException {
        String expected =
                "/**\n"
                        + " * Automatically generated file. DO NOT MODIFY\n"
                        + " */\n"
                        + "package com.example.helloworld;\n"
                        + "\n"
                        + "public final class BuildConfig {\n"
                        + "  public static final boolean DEBUG = false;\n"
                        + "  public static final String APPLICATION_ID = \"com.example.helloworld\";\n"
                        + "  public static final String BUILD_TYPE = \"release\";\n"
                        + "  public static final String FLAVOR = \"flavor1\";\n"
                        + "  public static final int VERSION_CODE = 1;\n"
                        + "  public static final String VERSION_NAME = \"1.0\";\n"
                        + "  // Field from default config.\n"
                        + "  public static final String[] CALCULATED_STRING = String.format(\"VALUE_DEFAULT=%1$d\", VALUE_DEFAULT);\n"
                        + "  // Field from product flavor: flavor1\n"
                        + "  public static final int VALUE_DEBUG = 10;\n"
                        + "  // Field from default config.\n"
                        + "  public static final int VALUE_DEFAULT = 1;\n"
                        + "  // Field from default config.\n"
                        + "  public static final java.util.OptionalInt VALUE_EXPRESSION = java.util.OptionalInt.empty();\n"
                        + "  // Field from product flavor: flavor1\n"
                        + "  public static final int VALUE_FLAVOR = 10;\n"
                        + "  // Field from default config.\n"
                        + "  public static final float VALUE_FLOAT = 5f;\n"
                        + "  // Field from default config.\n"
                        + "  public static final long VALUE_LONG = 50L;\n"
                        + "  // Field from default config.\n"
                        + "  public static final String[] VALUE_STRING_ARRAY = new String[]{\"hello\", \"world\"};\n"
                        + "  // Field from product flavor: flavor1\n"
                        + "  public static final int VALUE_VARIANT = 10;\n"
                        + "}\n";
        doCheckBuildConfig(expected, "flavor1/release");
    }

    @Test
    public void modelFlavor2() {
        Map<String, String> map = Maps.newHashMap();
        map.put("VALUE_FLAVOR", "10");
        map.put("VALUE_DEBUG", "10");
        map.put("VALUE_VARIANT", "10");
        checkFlavor(model, "flavor1", map);
    }

    @Test
    public void buildFlavor2Release() throws IOException {
        String expected =
                "/**\n"
                        + " * Automatically generated file. DO NOT MODIFY\n"
                        + " */\n"
                        + "package com.example.helloworld;\n"
                        + "\n"
                        + "public final class BuildConfig {\n"
                        + "  public static final boolean DEBUG = false;\n"
                        + "  public static final String APPLICATION_ID = \"com.example.helloworld\";\n"
                        + "  public static final String BUILD_TYPE = \"release\";\n"
                        + "  public static final String FLAVOR = \"flavor2\";\n"
                        + "  public static final int VERSION_CODE = 1;\n"
                        + "  public static final String VERSION_NAME = \"1.0\";\n"
                        + "  // Field from default config.\n"
                        + "  public static final String[] CALCULATED_STRING = String.format(\"VALUE_DEFAULT=%1$d\", VALUE_DEFAULT);\n"
                        + "  // Field from product flavor: flavor2\n"
                        + "  public static final int VALUE_DEBUG = 20;\n"
                        + "  // Field from default config.\n"
                        + "  public static final int VALUE_DEFAULT = 1;\n"
                        + "  // Field from default config.\n"
                        + "  public static final java.util.OptionalInt VALUE_EXPRESSION = java.util.OptionalInt.empty();\n"
                        + "  // Field from product flavor: flavor2\n"
                        + "  public static final int VALUE_FLAVOR = 20;\n"
                        + "  // Field from default config.\n"
                        + "  public static final float VALUE_FLOAT = 5f;\n"
                        + "  // Field from default config.\n"
                        + "  public static final long VALUE_LONG = 50L;\n"
                        + "  // Field from default config.\n"
                        + "  public static final String[] VALUE_STRING_ARRAY = new String[]{\"hello\", \"world\"};\n"
                        + "  // Field from product flavor: flavor2\n"
                        + "  public static final int VALUE_VARIANT = 20;\n"
                        + "}\n";
        doCheckBuildConfig(expected, "flavor2/release");
    }

    @Test
    public void modelRelease() {
        Map<String, String> map = Maps.newHashMap();
        checkBuildType(model, "release", map);
    }

    private static void doCheckBuildConfig(@NonNull String expected, @NonNull String variantDir)
            throws IOException {
        checkBuildConfig(project, expected, variantDir);
    }

    public static void checkBuildConfig(
            @NonNull GradleTestProject project,
            @NonNull String expected,
            @NonNull String variantDir)
            throws IOException {
        File buildConfigJar =
                new File(
                        project.getProjectDir(),
                        "build/intermediates/generated_build_config_bytecode/"
                                + variantDir
                                + "BuildConfig.jar");
        // If the compiled BuildConfig exists, the Java class BuildConfig should not check it's
        // contents as it shouldn't exist.
        if (buildConfigJar.exists()) {
            return;
        }
        File outputFile =
                new File(
                        project.getProjectDir(),
                        "build/generated/source/buildConfig/"
                                + variantDir
                                + "/com/example/helloworld/BuildConfig.java");
        Assert.assertTrue("Missing file: " + outputFile, outputFile.isFile());
        assertEquals(expected, Files.asByteSource(outputFile).asCharSource(Charsets.UTF_8).read());
    }

    private static void checkFlavor(
            @NonNull AndroidProject androidProject,
            @NonNull final String flavorName,
            @Nullable Map<String, String> valueMap) {
        ProductFlavor productFlavor =
                AndroidProjectUtils.getProductFlavor(androidProject, flavorName).getProductFlavor();
        assertNotNull(flavorName + " flavor null-check", productFlavor);

        checkMaps(valueMap, productFlavor.getBuildConfigFields(), flavorName);
    }

    private static void checkBuildType(
            @NonNull AndroidProject androidProject,
            @NonNull final String buildTypeName,
            @Nullable Map<String, String> valueMap) {
        BuildType buildType =
                AndroidProjectUtils.getBuildType(androidProject, buildTypeName).getBuildType();
        assertNotNull(buildTypeName + " flavor null-check", buildType);

        checkMaps(valueMap, buildType.getBuildConfigFields(), buildTypeName);
    }

    private static void checkMaps(
            @Nullable Map<String, String> valueMap,
            @Nullable Map<String, ClassField> value,
            @NonNull String name) {
        assertNotNull(value);

        // check the map against the expected one.
        assertEquals(valueMap.keySet(), value.keySet());
        for (String key : valueMap.keySet()) {
            ClassField field = value.get(key);
            assertNotNull(name + ": expected field " + key, field);
            assertEquals(name + ": check Value of " + key, valueMap.get(key), field.getValue());
        }
    }
}
