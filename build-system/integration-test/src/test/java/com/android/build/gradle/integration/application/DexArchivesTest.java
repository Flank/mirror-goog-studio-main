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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.annotations.NonNull;
import com.android.build.api.transform.Format;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TransformOutputContent;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.pipeline.SubStream;
import com.android.testutils.TestUtils;
import com.android.testutils.apk.Dex;
import com.android.testutils.truth.MoreTruth;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

/** Tests for incremental dexing using dex archives. */
@SuppressWarnings("OptionalGetWithoutIsPresent")
public class DexArchivesTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    public void testInitialBuild() throws Exception {
        assembleDebug();

        checkIntermediaryDexArchives(getInitialDexEntries());

        checkIntermediaryDexFiles(ImmutableList.of("classes.dex"));

        Dex mainDex = project.getApk("debug").getMainDexFile().get();
        MoreTruth.assertThat(mainDex).containsExactlyClassesIn(getInitialDexClasses());
    }

    @Test
    public void testChangingExistingFile() throws Exception {
        assembleDebug();

        long created = FileUtils.find(builderDir(), "BuildConfig.dex").get().lastModified();
        TestFileUtils.addMethod(
                FileUtils.join(project.getMainSrcDir(), "com/example/helloworld/HelloWorld.java"),
                "\npublic void addedMethod() {}");
        TestUtils.waitForFileSystemTick();

        assembleDebug();
        assertThat(FileUtils.find(builderDir(), "BuildConfig.dex").get()).wasModifiedAt(created);
        assertThat(FileUtils.find(builderDir(), "HelloWorld.dex").get().lastModified())
                .isGreaterThan(created);

        Dex mainDex = project.getApk("debug").getMainDexFile().get();
        MoreTruth.assertThat(mainDex).containsExactlyClassesIn(getInitialDexClasses());
        MoreTruth.assertThat(mainDex)
                .containsClass("Lcom/example/helloworld/HelloWorld;")
                .that()
                .hasMethod("addedMethod");
    }

    @Test
    public void testAddingFile() throws IOException, InterruptedException {
        assembleDebug();
        long created = FileUtils.find(builderDir(), "BuildConfig.dex").get().lastModified();

        String newClass = "package com.example.helloworld;\n" + "public class NewClass {}";
        Files.write(
                project.getMainSrcDir().toPath().resolve("com/example/helloworld/NewClass.java"),
                newClass.getBytes(Charsets.UTF_8));

        TestUtils.waitForFileSystemTick();
        assembleDebug();
        assertThat(FileUtils.find(builderDir(), "BuildConfig.dex").get()).wasModifiedAt(created);

        List<String> dexEntries = Lists.newArrayList("NewClass.dex");
        dexEntries.addAll(getInitialDexEntries());
        checkIntermediaryDexArchives(dexEntries);

        List<String> dexClasses = Lists.newArrayList("Lcom/example/helloworld/NewClass;");
        dexClasses.addAll(getInitialDexClasses());
        MoreTruth.assertThat(project.getApk("debug").getMainDexFile().get())
                .containsExactlyClassesIn(dexClasses);
    }

    @Test
    public void testRemovingFile() throws IOException, InterruptedException {
        String newClass = "package com.example.helloworld;\n" + "public class ToRemove {}";
        File srcToRemove =
                FileUtils.join(project.getMainSrcDir(), "com/example/helloworld/ToRemove.java");
        Files.write(srcToRemove.toPath(), newClass.getBytes(Charsets.UTF_8));
        assembleDebug();

        assertThat(FileUtils.find(builderDir(), "ToRemove.dex").get()).exists();

        srcToRemove.delete();
        assembleDebug();

        checkIntermediaryDexArchives(getInitialDexEntries());
        MoreTruth.assertThat(project.getApk("debug").getMainDexFile().get())
                .containsExactlyClassesIn(getInitialDexClasses());
    }

    @Test
    public void testForReleaseVariants() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(project.getBuildFile(), "android.dexOptions.dexInProcess false");
        GradleBuildResult result = project.executor().run("assembleRelease");
        assertThat(result.getNotUpToDateTasks()).contains(":transformClassesWithPreDexForRelease");
        assertThat(result.getNotUpToDateTasks()).contains(":transformDexWithDexForRelease");
    }

    private void checkIntermediaryDexArchives(@NonNull Collection<String> dexEntryNames) {
        TransformOutputContent content = new TransformOutputContent(builderDir());
        assertThat(content).hasSize(1);

        SubStream stream = content.getSingleStream();
        assertThat(stream).hasFormat(Format.DIRECTORY);
        // some checks on stream?

        ImmutableList<String> produced =
                FileUtils.getAllFiles(content.getLocation(stream))
                        .transform(File::getName)
                        .toList();

        assertThat(produced).containsExactlyElementsIn(dexEntryNames);
    }

    private void checkIntermediaryDexFiles(@NonNull Collection<String> expectedNames) {
        TransformOutputContent content = new TransformOutputContent(mergerDir());
        assertThat(content).hasSize(1);

        SubStream stream = content.getSingleStream();
        assertThat(stream).hasFormat(Format.DIRECTORY);

        List<String> dexFiles =
                FileUtils.find(content.getLocation(stream), Pattern.compile(".*\\.dex"))
                        .stream()
                        .map(File::getName)
                        .collect(Collectors.toList());

        assertThat(dexFiles).containsExactlyElementsIn(expectedNames);
    }

    @NonNull
    private List<String> getInitialDexEntries() {
        return Lists.newArrayList(
                "BuildConfig.dex",
                "HelloWorld.dex",
                "R.dex",
                "R$id.dex",
                "R$layout.dex",
                "R$string.dex");
    }

    @NonNull
    private List<String> getInitialDexClasses() {
        return Lists.newArrayList(
                "Lcom/example/helloworld/BuildConfig;",
                "Lcom/example/helloworld/HelloWorld;",
                "Lcom/example/helloworld/R;",
                "Lcom/example/helloworld/R$id;",
                "Lcom/example/helloworld/R$layout;",
                "Lcom/example/helloworld/R$string;");
    }

    private void assembleDebug() throws IOException, InterruptedException {
        project.executor().withUseDexArchive(true).withEnabledAapt2(true).run("assembleDebug");
    }

    @NonNull
    private File builderDir() {
        return project.getIntermediateFile("transforms", "dexBuilder", "debug");
    }

    @NonNull
    private File mergerDir() {
        return project.getIntermediateFile("transforms", "dexMerger", "debug");
    }
}
