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
import static com.android.testutils.truth.FileSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TransformOutputContent;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.pipeline.SubStream;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexerTool;
import com.android.testutils.TestUtils;
import com.android.testutils.apk.Dex;
import com.android.testutils.apk.Zip;
import com.android.testutils.truth.MoreTruth;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests for incremental dexing using dex archives. */
@RunWith(FilterableParameterized.class)
@SuppressWarnings("OptionalGetWithoutIsPresent")
public class DexArchivesTest {

    @Parameterized.Parameter() public DexerTool dexerTool;

    @Parameterized.Parameter(1)
    public DexMergerTool mergerTool;

    @Parameterized.Parameters(name = "{0}_{1}")
    public static Iterable<Object[]> getSetups() {
        return ImmutableList.of(
                new Object[] {DexerTool.DX, DexMergerTool.DX},
                new Object[] {DexerTool.D8, DexMergerTool.D8});
    }

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    public void testInitialBuild() throws Exception {
        runTask("assembleDebug");

        checkIntermediaryDexArchives(getInitialFolderDexEntries(), getInitialJarDexClasses());

        File merged = project.getIntermediateFile("dex/debug/out/");
        assertThat(merged).isDirectory();
        assertThat(merged.list()).hasLength(1);

        Dex mainDex = project.getApk(GradleTestProject.ApkType.DEBUG).getMainDexFile().get();
        MoreTruth.assertThat(mainDex).containsExactlyClassesIn(getApkDexClasses());
    }

    @Test
    public void testChangingExistingFile() throws Exception {
        runTask("assembleDebug");

        long created = FileUtils.find(builderDir(), "BuildConfig.dex").get().lastModified();
        TestFileUtils.addMethod(
                FileUtils.join(project.getMainSrcDir(), "com/example/helloworld/HelloWorld.java"),
                "\npublic void addedMethod() {}");
        TestUtils.waitForFileSystemTick();

        runTask("assembleDebug");
        assertThat(FileUtils.find(builderDir(), "BuildConfig.dex").get()).wasModifiedAt(created);
        assertThat(FileUtils.find(builderDir(), "HelloWorld.dex").get().lastModified())
                .isGreaterThan(created);

        Dex mainDex = project.getApk(GradleTestProject.ApkType.DEBUG).getMainDexFile().get();
        MoreTruth.assertThat(mainDex).containsExactlyClassesIn(getApkDexClasses());
        MoreTruth.assertThat(mainDex)
                .containsClass("Lcom/example/helloworld/HelloWorld;")
                .that()
                .hasMethod("addedMethod");
    }

    @Test
    public void testAddingFile() throws Exception {
        runTask("assembleDebug");
        long created = FileUtils.find(builderDir(), "BuildConfig.dex").get().lastModified();

        String newClass = "package com.example.helloworld;\n" + "public class NewClass {}";
        Files.write(
                project.getMainSrcDir().toPath().resolve("com/example/helloworld/NewClass.java"),
                newClass.getBytes(Charsets.UTF_8));

        TestUtils.waitForFileSystemTick();
        runTask("assembleDebug");
        assertThat(FileUtils.find(builderDir(), "BuildConfig.dex").get()).wasModifiedAt(created);

        List<String> dexEntries = Lists.newArrayList("NewClass.dex");
        dexEntries.addAll(getInitialFolderDexEntries());
        checkIntermediaryDexArchives(dexEntries, getInitialJarDexClasses());

        List<String> dexClasses = Lists.newArrayList("Lcom/example/helloworld/NewClass;");
        dexClasses.addAll(getApkDexClasses());
        MoreTruth.assertThat(project.getApk(GradleTestProject.ApkType.DEBUG).getMainDexFile().get())
                .containsExactlyClassesIn(dexClasses);
    }

    @Test
    public void testRemovingFile() throws Exception {
        String newClass = "package com.example.helloworld;\n" + "public class ToRemove {}";
        File srcToRemove =
                FileUtils.join(project.getMainSrcDir(), "com/example/helloworld/ToRemove.java");
        Files.write(srcToRemove.toPath(), newClass.getBytes(Charsets.UTF_8));
        runTask("assembleDebug");

        assertThat(FileUtils.find(builderDir(), "ToRemove.dex").get()).exists();

        srcToRemove.delete();
        runTask("assembleDebug");

        checkIntermediaryDexArchives(getInitialFolderDexEntries(), getInitialJarDexClasses());
        MoreTruth.assertThat(project.getApk(GradleTestProject.ApkType.DEBUG).getMainDexFile().get())
                .containsExactlyClassesIn(getApkDexClasses());
    }

    @Test
    public void testForReleaseVariants() throws IOException, InterruptedException {
        GradleBuildResult result = runTask("assembleRelease");

        assertThat(result.getTask(":transformClassesWithDexBuilderForRelease")).didWork();
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
        TransformOutputContent content = new TransformOutputContent(builderDir());

        ArrayList<String> producedJarsDexClasses = new ArrayList<>(4);
        for (SubStream stream : content) {
            switch (stream.getFormat()) {
                case DIRECTORY:
                    ImmutableList<String> produced =
                            FileUtils.getAllFiles(content.getLocation(stream))
                                    .transform(File::getName)
                                    .toList();
                    assertThat(produced).containsExactlyElementsIn(folderDexEntryNames);
                    break;
                case JAR:
                    try (Zip zip = new Zip(content.getLocation(stream))) {
                        producedJarsDexClasses.addAll(
                                zip.getEntries()
                                        .stream()
                                        .flatMap(DexArchivesTest::getDexClasses)
                                        .collect(Collectors.toList()));
                    }
                    break;
            }
        }
        assertThat(producedJarsDexClasses).containsExactlyElementsIn(jarsDexClasses);
    }

    @NonNull
    private List<String> getInitialFolderDexEntries() {
        return Lists.newArrayList("BuildConfig.dex", "HelloWorld.dex");
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
        return Lists.newArrayList(
                "Lcom/example/helloworld/BuildConfig;",
                "Lcom/example/helloworld/HelloWorld;",
                "Lcom/example/helloworld/R;",
                "Lcom/example/helloworld/R$id;",
                "Lcom/example/helloworld/R$layout;",
                "Lcom/example/helloworld/R$string;");
    }

    private GradleBuildResult runTask(@NonNull String taskName)
            throws IOException, InterruptedException {
        return project.executor()
                .with(BooleanOption.ENABLE_D8, dexerTool == DexerTool.D8)
                .run(taskName);
    }

    @NonNull
    private File builderDir() {
        return project.getIntermediateFile("transforms", "dexBuilder", "debug");
    }
}
