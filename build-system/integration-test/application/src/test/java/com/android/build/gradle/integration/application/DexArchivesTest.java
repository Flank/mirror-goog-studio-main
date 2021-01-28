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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat;
import static com.android.testutils.truth.DexSubject.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.scope.ArtifactTypeUtil;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.testutils.TestUtils;
import com.android.testutils.apk.Dex;
import com.android.testutils.apk.Zip;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;

/** Tests for incremental dexing using dex archives. */
@SuppressWarnings("OptionalGetWithoutIsPresent")
public class DexArchivesTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .withGradleBuildCacheDirectory(new File("local-build-cache"))
                    .create();

    @Test
    public void testInitialBuild() throws Exception {
        project.executor().run("assembleDebug");

        checkIntermediaryDexArchives(getInitialFolderDexEntries(), getInitialJarDexClasses());

        File merged = project.getIntermediateFile("dex/debug/mergeDexDebug/");
        assertThat(merged).isDirectory();
        assertThat(merged.list()).hasLength(1);

        Dex mainDex = project.getApk(GradleTestProject.ApkType.DEBUG).getMainDexFile().get();
        assertThat(mainDex).containsExactlyClassesIn(getApkDexClasses());
    }

    @Test
    public void testChangingExistingFile() throws Exception {
        project.executor().run("assembleDebug");
        TestFileUtils.addMethod(
                FileUtils.join(project.getMainSrcDir(), "com/example/helloworld/HelloWorld.java"),
                "\npublic void addedMethod() {}");

        if (!project.getIntermediateFile(
                        InternalArtifactType.COMPILE_BUILD_CONFIG_JAR.INSTANCE.getFolderName())
                .exists()) {
            long created = FileUtils.find(builderDir(), "BuildConfig.dex").get().lastModified();

            TestUtils.waitForFileSystemTick();
            project.executor().run("assembleDebug");

            assertThat(FileUtils.find(builderDir(), "BuildConfig.dex").get())
                    .wasModifiedAt(created);
            assertThat(FileUtils.find(builderDir(), "HelloWorld.dex").get().lastModified())
                    .isGreaterThan(created);
        } else {
            project.executor().run("assembleDebug");
        }

        Dex mainDex = project.getApk(GradleTestProject.ApkType.DEBUG).getMainDexFile().get();
        assertThat(mainDex).containsExactlyClassesIn(getApkDexClasses());
        assertThat(mainDex)
                .containsClass("Lcom/example/helloworld/HelloWorld;")
                .that()
                .hasMethod("addedMethod");
    }

    @Test
    public void testAddingFile() throws Exception {
        project.executor().run("assembleDebug");
        if (!project.getIntermediateFile(
                        InternalArtifactType.COMPILE_BUILD_CONFIG_JAR.INSTANCE.getFolderName())
                .exists()) {
            long created = FileUtils.find(builderDir(), "BuildConfig.dex").get().lastModified();

            addNewClass();
            TestUtils.waitForFileSystemTick();
            project.executor().run("assembleDebug");
            assertThat(FileUtils.find(builderDir(), "BuildConfig.dex").get())
                    .wasModifiedAt(created);
        } else {
            addNewClass();
            TestUtils.waitForFileSystemTick();
            project.executor().run("assembleDebug");
        }
        List<String> dexEntries = Lists.newArrayList("NewClass.dex");
        dexEntries.addAll(getInitialFolderDexEntries());
        checkIntermediaryDexArchives(dexEntries, getInitialJarDexClasses());

        List<String> dexClasses = Lists.newArrayList("Lcom/example/helloworld/NewClass;");
        dexClasses.addAll(getApkDexClasses());
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG).getMainDexFile().get())
                .containsExactlyClassesIn(dexClasses);
    }

    @Test
    public void testRemovingFile() throws Exception {
        String newClass = "package com.example.helloworld;\n" + "public class ToRemove {}";
        File srcToRemove =
                FileUtils.join(project.getMainSrcDir(), "com/example/helloworld/ToRemove.java");
        Files.write(srcToRemove.toPath(), newClass.getBytes(Charsets.UTF_8));
        project.executor().run("assembleDebug");

        assertThat(FileUtils.find(builderDir(), "ToRemove.dex").get()).exists();

        srcToRemove.delete();
        project.executor().run("assembleDebug");

        checkIntermediaryDexArchives(getInitialFolderDexEntries(), getInitialJarDexClasses());
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG).getMainDexFile().get())
                .containsExactlyClassesIn(getApkDexClasses());
    }

    @Test
    public void testForReleaseVariants() throws IOException, InterruptedException {
        GradleBuildResult result =
                project.executor()
                        // http://b/162074215
                        .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
                        .run("assembleRelease");

        assertThat(result.getTask(":dexBuilderRelease")).didWork();
        assertThat(result.getTask(":mergeDexRelease")).didWork();
        assertThat(result.getTask(":mergeExtDexRelease")).didWork();
    }

    /** Regression test for http://b/68144982. */
    @Test
    public void testIncrementalDexingRemoteDependency() throws IOException, InterruptedException {
        // Add an arbitrary external *AAR* dependency
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android.defaultConfig.minSdkVersion=10\n"
                        + "dependencies { implementation ('org.jdeferred:jdeferred-android-aar:1.2.2') {   transitive = false } }");

        project.executor().run("assembleDebug");

        // Minor version update
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\ndependencies { implementation ('org.jdeferred:jdeferred-android-aar:1.2.3') {   transitive = false } }");
        project.executor().run("assembleDebug");
    }

    @Test
    public void testWithCacheDoesNotLoadLocalState() throws IOException, InterruptedException {
        GradleTaskExecutor executor = project.executor().withArgument("--build-cache");

        executor.run("assembleDebug");
        File inputJarHashes =
                new File(
                        ArtifactTypeUtil.getOutputDir(
                                InternalArtifactType.DEX_ARCHIVE_INPUT_JAR_HASHES.INSTANCE,
                                project.getBuildDir()),
                        "debug/out");
        assertThat(inputJarHashes).exists();
        executor.run("clean");
        GradleBuildResult result = executor.run("assembleDebug");

        assertThat(result.getTask(":dexBuilderDebug")).wasFromCache();
        assertThat(inputJarHashes).doesNotExist();
    }

    @Test
    public void testDexingBucketsImpactOnCaching() throws IOException, InterruptedException {
        // 1st build to cache
        GradleTaskExecutor executor = project.executor().withArgument("--build-cache");

        executor.with(IntegerOption.DEXING_NUMBER_OF_BUCKETS, 1).run("assembleDebug");
        File previousRunDexBuckets =
                new File(
                        ArtifactTypeUtil.getOutputDir(
                                InternalArtifactType.DEX_NUMBER_OF_BUCKETS_FILE.INSTANCE,
                                project.getBuildDir()),
                        "debug/out");
        assertThat(previousRunDexBuckets).exists();
        executor.run("clean");

        // 2nd build should be a cache hit
        GradleBuildResult result =
                executor.with(IntegerOption.DEXING_NUMBER_OF_BUCKETS, 2).run("assembleDebug");
        assertThat(previousRunDexBuckets).doesNotExist();
        assertThat(result.getTask(":dexBuilderDebug")).wasFromCache();

        // 3rd build adds a source file
        if (!project.getIntermediateFile(
                        InternalArtifactType.COMPILE_BUILD_CONFIG_JAR.INSTANCE.getFolderName())
                .exists()) {
            long initialBuildTimestamp =
                    FileUtils.find(builderDir(), "BuildConfig.dex").get().lastModified();
            addNewClass();
            executor.with(IntegerOption.DEXING_NUMBER_OF_BUCKETS, 2).run("assembleDebug");
            assertThat(FileUtils.find(builderDir(), "BuildConfig.dex").get().lastModified())
                    .isGreaterThan(initialBuildTimestamp);
            assertThat(FileUtils.find(builderDir(), "NewClass.dex").get().lastModified())
                    .isGreaterThan(initialBuildTimestamp);
        }
    }

    @Test
    public void testIncrementalRunWithChangedBuckets() throws IOException, InterruptedException {
        project.executor().with(IntegerOption.DEXING_NUMBER_OF_BUCKETS, 1).run("assembleDebug");

        if (!project.getIntermediateFile(
                        InternalArtifactType.COMPILE_BUILD_CONFIG_JAR.INSTANCE.getFolderName())
                .exists()) {
            addNewClass();
            long initialBuildTimestamp =
                    FileUtils.find(builderDir(), "BuildConfig.dex").get().lastModified();

            project.executor().with(IntegerOption.DEXING_NUMBER_OF_BUCKETS, 2).run("assembleDebug");
            assertThat(FileUtils.find(builderDir(), "BuildConfig.dex").get().lastModified())
                    .isGreaterThan(initialBuildTimestamp);
            assertThat(FileUtils.find(builderDir(), "NewClass.dex").get().lastModified())
                    .isGreaterThan(initialBuildTimestamp);
        }
    }

    private static Stream<String> getDexClasses(Path path) {
        try {
            return new Dex(path).getClasses().keySet().stream();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void checkIntermediaryDexArchives(
            @NonNull Collection<String> folderDexEntryNames,
            @NonNull Collection<String> jarsDexClasses)
            throws Exception {
        File projectDexOut =
                new File(
                        ArtifactTypeUtil.getOutputDir(
                                InternalArtifactType.PROJECT_DEX_ARCHIVE.INSTANCE,
                                project.getBuildDir()),
                        "debug/out");

        List<String> dexInDirs = new ArrayList<>();
        List<String> dexInJars = new ArrayList<>();
        try (Stream<Path> files = Files.walk(projectDexOut.toPath())) {
            files.forEach(
                    f -> {
                        if (f.toString().endsWith(".dex")) {
                            dexInDirs.add(f.getFileName().toString());
                        } else if (f.toString().endsWith(".jar")) {
                            try (Zip zip = new Zip(f)) {
                                dexInJars.addAll(
                                        zip.getEntries().stream()
                                                .flatMap(DexArchivesTest::getDexClasses)
                                                .collect(Collectors.toList()));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            assertThat(f)
                                    .named("dexing output that is not dex nor jar")
                                    .isDirectory();
                        }
                    });
        }
        assertThat(dexInDirs).containsExactlyElementsIn(folderDexEntryNames);
        assertThat(dexInJars).containsExactlyElementsIn(jarsDexClasses);
    }

    @NonNull
    private List<String> getInitialFolderDexEntries() {
        if (project.getIntermediateFile(
                        InternalArtifactType.COMPILE_BUILD_CONFIG_JAR.INSTANCE.getFolderName())
                .exists()) {
            return Lists.newArrayList("HelloWorld.dex");
        } else {
            return Lists.newArrayList("BuildConfig.dex", "HelloWorld.dex");
        }
    }

    @NonNull
    private List<String> getInitialJarDexClasses() {
        return Lists.newArrayList(
                "Lcom/example/helloworld/R;",
                "Lcom/example/helloworld/R$id;",
                "Lcom/example/helloworld/R$layout;",
                "Lcom/example/helloworld/R$string;");
    }

    @NonNull
    private List<String> getApkDexClasses() {
        if (project.getIntermediateFile(
                        InternalArtifactType.COMPILE_BUILD_CONFIG_JAR.INSTANCE.getFolderName())
                .exists()) {
            return Lists.newArrayList(
                    "Lcom/example/helloworld/HelloWorld;",
                    "Lcom/example/helloworld/R;",
                    "Lcom/example/helloworld/R$id;",
                    "Lcom/example/helloworld/R$layout;",
                    "Lcom/example/helloworld/R$string;");
        } else {
            return Lists.newArrayList(
                    "Lcom/example/helloworld/BuildConfig;",
                    "Lcom/example/helloworld/HelloWorld;",
                    "Lcom/example/helloworld/R;",
                    "Lcom/example/helloworld/R$id;",
                    "Lcom/example/helloworld/R$layout;",
                    "Lcom/example/helloworld/R$string;");
        }
    }

    @NonNull
    private File builderDir() {
        return new File(
                ArtifactTypeUtil.getOutputDir(
                        InternalArtifactType.PROJECT_DEX_ARCHIVE.INSTANCE, project.getBuildDir()),
                "debug/out");
    }

    private void addNewClass() throws IOException {
        String newClass = "package com.example.helloworld;\n" + "public class NewClass {}";
        Files.write(
                project.getMainSrcDir().toPath().resolve("com/example/helloworld/NewClass.java"),
                newClass.getBytes(Charsets.UTF_8));
    }
}
