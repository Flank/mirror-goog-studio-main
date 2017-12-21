/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.desugar;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.builder.core.DesugarProcessArgs.MIN_SUPPORTED_API_TRY_WITH_RESOURCES;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.instant.InstantRunTestUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.model.SyncIssue;
import com.android.ide.common.process.ProcessException;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.android.testutils.apk.SplitApks;
import com.android.tools.ir.client.InstantRunArtifactType;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests use of Java 8 language in the application module. */
@RunWith(Parameterized.class)
public class DesugarAppTest {

    private static final ImmutableList<String> TRY_WITH_RESOURCES_RUNTIME =
            ImmutableList.of(
                    "Lcom/google/devtools/build/android/desugar/runtime/ThrowableExtension;",
                    "Lcom/google/devtools/build/android/desugar/runtime/ThrowableExtension$AbstractDesugaringStrategy;",
                    "Lcom/google/devtools/build/android/desugar/runtime/ThrowableExtension$MimicDesugaringStrategy;",
                    "Lcom/google/devtools/build/android/desugar/runtime/ThrowableExtension$NullDesugaringStrategy;",
                    "Lcom/google/devtools/build/android/desugar/runtime/ThrowableExtension$ReuseDesugaringStrategy;");


    @NonNull private final Boolean enableGradleWorkers;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Parameterized.Parameters(name = "enableGradleWorkers={0}")
    public static Boolean[] getParameters() {
        return new Boolean[] {Boolean.TRUE, Boolean.FALSE};
    }

    public DesugarAppTest(@NonNull Boolean enableGradleWorkers) {
        this.enableGradleWorkers = enableGradleWorkers;
    }

    @Test
    public void noTaskIfNoJava8Set() throws IOException, InterruptedException {
        GradleBuildResult result = getProjectExecutor().run("assembleDebug");
        assertThat(result.getNotUpToDateTasks())
                .doesNotContain(":transformClassesWithDesugarForDebug");
    }

    @Test
    public void taskRunsIfJava8Set() throws IOException, InterruptedException {
        enableDesugar();
        GradleBuildResult result = getProjectExecutor().run("assembleDebug");
        assertThat(result.getNotUpToDateTasks()).contains(":transformClassesWithDesugarForDebug");
    }

    @Test
    public void syncIssueIfJava8AndDesugarDisabled() throws IOException, InterruptedException {
        enableDesugar();
        AndroidProject result =
                project.model()
                        .with(BooleanOption.ENABLE_DESUGAR, false)
                        .ignoreSyncIssues()
                        .getSingle()
                        .getOnlyModel();
        boolean found =
                result.getSyncIssues()
                        .stream()
                        .filter(i -> i.getSeverity() == SyncIssue.SEVERITY_ERROR)
                        .anyMatch(
                                i ->
                                        i.getMessage()
                                                .equals(
                                                        "Please add 'android.enableDesugar=true' "
                                                                + "to your gradle.properties file "
                                                                + "to enable Java 8 language "
                                                                + "support."));
        assertThat(found).named("Sync issue to enable Desugar found").isTrue();
    }

    @Test
    public void supportsJava8() throws IOException, InterruptedException, ProcessException {
        enableDesugar();
        Files.write(
                project.getMainSrcDir().toPath().resolve("com/example/helloworld/Data.java"),
                ImmutableList.of(
                        "package com.example.helloworld;",
                        "class Data {",
                        "    public static void doLambda() {",
                        "        Runnable r = () -> { };",
                        "    }",
                        "}"));

        GradleBuildResult result = getProjectExecutor().run("assembleDebug");
        assertThat(result.getNotUpToDateTasks()).contains(":transformClassesWithDesugarForDebug");
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .containsClass("Lcom/example/helloworld/Data;");
    }

    @Test
    public void desugarsLibraryDependency()
            throws IOException, InterruptedException, ProcessException {
        enableDesugar();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "dependencies {\n"
                        + "    compile fileTree(dir: 'libs', include: ['*.jar'])\n"
                        + "}");
        List<String> classes = createLibToDesugarAndGetClasses();
        getProjectExecutor().run("assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        for (String klass : Iterables.concat(classes, TRY_WITH_RESOURCES_RUNTIME)) {
            assertThat(apk).containsClass(klass);
        }
    }

    @Test
    public void testNonDesugaredLibraryDependency()
            throws IOException, InterruptedException, ProcessException {
        // see b/65543679 for details
        Assume.assumeFalse(enableGradleWorkers);
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "dependencies {\n"
                        + "    compile fileTree(dir: 'libs', include: ['*.jar'])\n"
                        + "}");
        List<String> classes = createLibToDesugarAndGetClasses();
        GradleBuildResult result = getProjectExecutor().expectFailure().run("assembleDebug");
        assertThat(result.getStderr())
                .contains(
                        "The dependency contains Java 8 bytecode. Please enable desugaring by "
                                + "adding the following to build.gradle\n"
                                + "android {\n"
                                + "    compileOptions {\n"
                                + "        sourceCompatibility 1.8\n"
                                + "        targetCompatibility 1.8\n"
                                + "    }\n"
                                + "}\n");
    }

    @Test
    public void runsAfterJacoco() throws IOException, InterruptedException {
        enableDesugar();
        TestFileUtils.appendToFile(
                project.getBuildFile(), "\nandroid.buildTypes.debug.testCoverageEnabled true");
        TestFileUtils.addMethod(
                FileUtils.join(project.getMainSrcDir(), "com/example/helloworld/HelloWorld.java"),
                "Runnable r = () -> { };");

        getProjectExecutor().run("assembleDebug");
    }

    @Test
    public void testBuildCacheIntegration()
            throws IOException, InterruptedException, ProcessException {
        // regression test for - http://b.android.com/292762
        enableDesugar();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                String.format(
                        "\n"
                                + "android.buildTypes.debug.testCoverageEnabled true\n"
                                + "android.defaultConfig.minSdkVersion %d\n"
                                + "dependencies {\n"
                                + "    compile 'com.android.support:support-v4:%s'\n"
                                + "}",
                        GradleTestProject.SUPPORT_LIB_MIN_SDK,
                        GradleTestProject.SUPPORT_LIB_VERSION));

        getProjectExecutor().run("assembleDebug");
        getProjectExecutor().run("clean", "assembleDebug");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                .containsClass("Landroid/support/v4/app/ActivityCompat;");
    }

    @Test
    public void testWithoutDexArchives()
            throws IOException, InterruptedException, ProcessException {
        enableDesugar();
        TestFileUtils.appendToFile(
                project.getBuildFile(), "\nandroid.defaultConfig.minSdkVersion 24");

        Files.write(
                project.getMainSrcDir().toPath().resolve("com/example/helloworld/Data.java"),
                ImmutableList.of(
                        "package com.example.helloworld;",
                        "interface Data {",
                        "    static void staticMethod() {",
                        "    }",
                        "    default void defaultMethod() {",
                        "    }",
                        "}"));

        getProjectExecutor().withUseDexArchive(false).run("assembleDebug");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                .containsClass("Lcom/example/helloworld/Data;");
    }

    @Test
    public void testWithoutDexArchivesNoPredexing()
            throws IOException, InterruptedException, ProcessException {
        enableDesugar();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\nandroid.defaultConfig.minSdkVersion 24\n"
                        + "android.dexOptions.preDexLibraries false\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.support:support-v4:"
                        + GradleTestProject.SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}");

        Files.write(
                project.getMainSrcDir().toPath().resolve("com/example/helloworld/Data.java"),
                ImmutableList.of(
                        "package com.example.helloworld;",
                        "interface Data {",
                        "    static void staticMethod() {",
                        "    }",
                        "    default void defaultMethod() {",
                        "    }",
                        "}"));

        getProjectExecutor().withUseDexArchive(false).run("assembleDebug");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                .containsClass("Lcom/example/helloworld/Data;");
    }

    @Test
    public void testTryWithResourcesPlatformUnsupported()
            throws IOException, InterruptedException, ProcessException {
        enableDesugar();
        writeClassWithTryWithResources();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                String.format(
                        "\n" + "android.defaultConfig.minSdkVersion %d\n",
                        MIN_SUPPORTED_API_TRY_WITH_RESOURCES - 1));
        getProjectExecutor().run("assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        for (String klass : TRY_WITH_RESOURCES_RUNTIME) {
            assertThat(apk).containsClass(klass);
        }
    }

    @Test
    public void testTryWithResourcesPlatformUnsupportedInstantRun() throws Exception {
        enableDesugar();
        writeClassWithTryWithResources();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                String.format(
                        "\n" + "android.defaultConfig.minSdkVersion %d\n",
                        MIN_SUPPORTED_API_TRY_WITH_RESOURCES - 1));
        InstantRun instantRunModel =
                InstantRunTestUtils.getInstantRunModel(
                        Iterables.getOnlyElement(
                                project.model().getSingle().getModelMap().values()));
        getProjectExecutor()
                .withInstantRun(new AndroidVersion(24, null), OptionalCompilationStep.FULL_APK)
                .run("assembleDebug");
        InstantRunBuildInfo initialContext = InstantRunTestUtils.loadContext(instantRunModel);

        List<Apk> splits =
                initialContext
                        .getArtifacts()
                        .stream()
                        .filter(artifact -> artifact.type == InstantRunArtifactType.SPLIT)
                        .map(
                                a -> {
                                    try {
                                        return new Apk(a.file);
                                    } catch (IOException e) {
                                        throw new UncheckedIOException(e);
                                    }
                                })
                        .collect(Collectors.toList());
        for (String klass : TRY_WITH_RESOURCES_RUNTIME) {
            assertThat(new SplitApks(splits)).hasClass(klass);
        }
    }

    @Test
    public void testTryWithResourcesPlatformSupported()
            throws IOException, InterruptedException, ProcessException {
        enableDesugar();
        writeClassWithTryWithResources();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                String.format(
                        "\n" + "android.defaultConfig.minSdkVersion %d\n",
                        MIN_SUPPORTED_API_TRY_WITH_RESOURCES));
        getProjectExecutor().run("assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        for (String klass : TRY_WITH_RESOURCES_RUNTIME) {
            assertThat(apk).doesNotContainClass(klass);
        }

        // also make sure we do not reference ThrowableExtension classes
        Dex mainDex = apk.getMainDexFile().orElseThrow(AssertionError::new);
        DexBackedClassDef classDef = mainDex.getClasses().get("Lcom/example/helloworld/Data;");
        List<String> allTypesInDex =
                classDef.dexFile
                        .getTypes()
                        .stream()
                        .map(d -> d.getType())
                        .collect(Collectors.toList());

        for (String klass : TRY_WITH_RESOURCES_RUNTIME) {
            assertThat(allTypesInDex).doesNotContain(klass);
        }
    }

    @Ignore("TODO(imorlowska): delete?")
    @Test
    public void testFailureIfOldDxAndMinSdk24Plus() throws IOException, InterruptedException {
        enableDesugar();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    buildToolsVersion \""
                        + AndroidBuilder.MIN_BUILD_TOOLS_REV.toString()
                        + "\"\n"
                        + "}\n"
                        + "\n"
                        + "android.dexOptions.dexInProcess false\n"
                        + "android.defaultConfig.minSdkVersion 24");
        GradleBuildResult result =
                getProjectExecutor().expectFailure().withUseDexArchive(false).run("assembleDebug");
        assertThat(result.getStderr())
                .contains("Execution failed for task ':transformClassesWithPreDexForDebug'");
    }

    @Test
    public void testUpToDateForIncCompileTasks() throws IOException, InterruptedException {
        enableDesugar();
        getProjectExecutor().run("assembleDebug");

        Path newSource = project.getMainSrcDir().toPath().resolve("test").resolve("Data.java");
        Files.createDirectories(newSource.getParent());
        Files.write(newSource, ImmutableList.of("package test;", "public class Data {}"));
        GradleBuildResult result = getProjectExecutor().run("assembleDebug");

        assertThat(result.getUpToDateTasks())
                .containsAllIn(ImmutableList.of(":extractTryWithResourcesSupportJarDebug"));
    }

    @Test
    public void testAndroidMethodInvocationsNotRewritten()
            throws IOException, InterruptedException, ProcessException {
        enableDesugar();
        // using android-26 as ServiceConnection has a default method
        TestFileUtils.appendToFile(project.getBuildFile(), "\nandroid.compileSdkVersion 26");

        Path newSource = project.getMainSrcDir().toPath().resolve("test").resolve("MyService.java");
        Files.createDirectories(newSource.getParent());
        Files.write(
                newSource,
                ImmutableList.of(
                        "package test;",
                        "import android.content.ServiceConnection;",
                        "import android.content.ComponentName;",
                        "import android.os.IBinder;",
                        "public class MyService implements ServiceConnection {",
                        "    public void onServiceConnected(ComponentName var1, IBinder var2) {}",
                        "    public void onServiceDisconnected(ComponentName var1) {}",
                        "}"));
        getProjectExecutor().run("assembleDebug");
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .hasClass("Ltest/MyService;")
                .that()
                .doesNotHaveMethod("onBindingDied");
    }

    @Test
    public void testWithBuildCacheDisabled() throws IOException, InterruptedException {
        enableDesugar();
        GradleBuildResult result =
                getProjectExecutor()
                        .with(BooleanOption.ENABLE_BUILD_CACHE, false)
                        .run("assembleDebug");
        assertThat(result.getNotUpToDateTasks()).contains(":transformClassesWithDesugarForDebug");
    }

    @Test
    public void testLegacyMultidexForDesugaredTypes() throws IOException, InterruptedException {
        enableDesugar();
        TestFileUtils.addMethod(
                FileUtils.join(project.getMainSrcDir(), "com/example/helloworld/HelloWorld.java"),
                "Runnable r = () -> { };");
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android.buildTypes.debug.multiDexKeepProguard file('rules')\n"
                        + "android.defaultConfig.multiDexEnabled true\n"
                        + "android.defaultConfig.minSdkVersion 20");
        // just keep the HelloWorld, the lambda one should be inferred
        Files.write(
                project.getBuildFile().toPath().resolveSibling("rules"),
                "-keep class **HelloWorld".getBytes());

        getProjectExecutor().run("assembleDebug");

        ImmutableMap<String, DexBackedClassDef> classes =
                project.getApk(GradleTestProject.ApkType.DEBUG)
                        .getMainDexFile()
                        .orElseThrow(AssertionError::new)
                        .getClasses();

        long helloWorldClasses =
                classes.keySet()
                        .stream()
                        .filter(t -> t.startsWith("Lcom/example/helloworld/HelloWorld"))
                        .count();
        assertThat(helloWorldClasses)
                .named("original and synthesized classes count in the main dex ")
                .isEqualTo(2);
    }

    private void enableDesugar() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.compileOptions.sourceCompatibility 1.8\n"
                        + "android.compileOptions.targetCompatibility 1.8");
    }

    @NonNull
    private List<String> createLibToDesugarAndGetClasses() throws IOException {
        class Utility {
            public void lambdaMethod() {
                Runnable r = () -> {};
                try (java.io.StringReader reader = new java.io.StringReader("")) {
                    System.out.println("In try-with-resources with reader " + reader.hashCode());
                }
            }
        }
        Path lib = project.getTestDir().toPath().resolve("libs/my-lib.jar");
        Files.createDirectories(lib.getParent());

        String path = Utility.class.getName().replace('.', '/') + SdkConstants.DOT_CLASS;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path);
                ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(lib))) {
            ZipEntry entry = new ZipEntry(path);
            out.putNextEntry(entry);
            out.write(ByteStreams.toByteArray(in));
            out.closeEntry();
        }
        return ImmutableList.of("L" + Utility.class.getName().replaceAll("\\.", "/") + ";");
    }

    private void writeClassWithTryWithResources() throws IOException {
        Files.write(
                project.getMainSrcDir().toPath().resolve("com/example/helloworld/Data.java"),
                ImmutableList.of(
                        "package com.example.helloworld;",
                        "import java.io.StringReader;",
                        "public class Data {",
                        "    public void foo() {",
                        "        try(StringReader r = new StringReader(\"\")) {",
                        "        }",
                        "    }",
                        "}"));
    }

    private GradleTaskExecutor getProjectExecutor() {
        return project.executor().with(BooleanOption.ENABLE_GRADLE_WORKERS, enableGradleWorkers);
    }
}
