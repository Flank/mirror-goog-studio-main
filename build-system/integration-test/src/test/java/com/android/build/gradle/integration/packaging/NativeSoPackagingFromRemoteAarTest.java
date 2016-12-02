/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.packaging;

import static com.android.build.gradle.integration.common.fixture.TemporaryProjectModification.doTest;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
/**
 * test for packaging of asset files.
 */
public class NativeSoPackagingFromRemoteAarTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    private GradleTestProject appProject;
    private GradleTestProject libProject;

    private void execute(String... tasks) {
        project.executor().run(tasks);
    }

    @Before
    public void setUp() throws IOException {
        appProject = project.getSubproject("app");
        libProject = project.getSubproject("library");

        // rewrite settings.gradle to remove un-needed modules
        Files.write("include 'app'\n"
                + "include 'library'\n",
                new File(project.getTestDir(), "settings.gradle"), Charsets.UTF_8);

        // setup dependencies.
        TestFileUtils.appendToFile(appProject.getBuildFile(),
                "repositories {\n"
                + "    maven { url '../testrepo' }\n"
                + "}\n"
                + "dependencies {\n"
                + "    compile 'com.example.android.nativepackaging:library:1.0-SNAPSHOT'\n"
                + "}\n");

        TestFileUtils.appendToFile(libProject.getBuildFile(),
                "apply plugin: 'maven'\n"
                + "\n"
                + "group = 'com.example.android.nativepackaging'\n"
                + "archivesBaseName = 'library'\n"
                + "version = '1.0-SNAPSHOT'\n"
                + "\n"
                + "uploadArchives {\n"
                + "    repositories {\n"
                + "        mavenDeployer {\n"
                + "            repository(url: uri(\"../testrepo\"))\n"
                + "        }\n"
                + "    }\n"
                + "}\n");

        // put some default files in the library project, to check non incremental packaging
        // as well, and to provide files to change to test incremental support.
        File libDir = libProject.getTestDir();
        createOriginalSoFile(libDir,  "main",        "liblibrary.so",      "library:abcd");
        createOriginalSoFile(libDir,  "main",        "liblibrary2.so",     "library2:abcdef");

        // build and deploy the library
        project.executor()
                .withArgument("--configure-on-demand")
                .run("library:clean", "library:uploadArchives");

        execute("app:clean", "app:assembleDebug");
    }

    private static void createOriginalSoFile(
            @NonNull File projectFolder,
            @NonNull String dimension,
            @NonNull String filename,
            @NonNull String content) throws IOException {
        File assetFolder = FileUtils.join(projectFolder, "src", dimension, "jniLibs", "x86");
        FileUtils.mkdirs(assetFolder);
        Files.write(content, new File(assetFolder, filename), Charsets.UTF_8);
    }

    @Test
    public void testNonIncrementalPackaging() throws IOException, InterruptedException {
        checkApk(appProject, "liblibrary.so",      "library:abcd");
        checkApk(appProject, "liblibrary2.so",     "library2:abcdef");
    }

    @Test
    public void testAppProjectWithNewSoInAar() throws Exception {
        doTest(libProject, project -> {
            project.addFile("src/main/jniLibs/x86/libnewapp.so", "newfile content");
            // must be two calls as it's a single project that includes both modules and
            // dependency is resolved at evaluation time, before the library published its new
            // versions.
            execute("library:uploadArchives");
            execute("app:assembleDebug");

            checkApk(appProject, "libnewapp.so", "newfile content");
        });
    }

    @Test
    public void testAppProjectWithRemovedSoInAar() throws Exception {
        doTest(libProject, project -> {
            project.removeFile("src/main/jniLibs/x86/liblibrary2.so");
            // must be two calls as it's a single project that includes both modules and
            // dependency is resolved at evaluation time, before the library published its new
            // versions.
            execute("library:uploadArchives");
            execute("app:assembleDebug");

            checkApk(appProject, "liblibrary2.so", null);
        });
    }

    @Test
    public void testAppProjectWithEditedSoInAar() throws Exception {
        doTest(libProject, project -> {
            project.replaceFile("src/main/jniLibs/x86/liblibrary2.so", "new content");
            // must be two calls as it's a single project that includes both modules and
            // dependency is resolved at evaluation time, before the library published its new
            // versions.
            execute("library:uploadArchives");
            execute("app:assembleDebug");

            checkApk(appProject, "liblibrary2.so", "new content");
        });
    }

    /**
     * check an apk has (or not) the given asset file name.
     *
     * If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param filename the filename
     * @param content the content
     */
    private static void checkApk(
            @NonNull GradleTestProject project,
            @NonNull String filename,
            @Nullable String content) throws IOException, InterruptedException {
        File apk = project.getApk("debug");
        check(assertThatApk(apk), "lib", filename, content);
        PackagingTests.checkZipAlign(apk);
    }

    /**
     * check an aat has (or not) the given asset file name.
     *
     * If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param filename the filename
     * @param content the content
     */
    private static void checkAar(
            @NonNull GradleTestProject project,
            @NonNull String filename,
            @Nullable String content) throws IOException {
        check(assertThatAar(project.getAar("debug")), "jni", filename, content);
    }

    private static void check(
            @NonNull AbstractAndroidSubject subject,
            @NonNull String folderName,
            @NonNull String filename,
            @Nullable String content) throws IOException {
        if (content != null) {
            subject.containsFileWithContent(folderName + "/x86/" + filename, content);
        } else {
            subject.doesNotContain(folderName + "/x86/" + filename);
        }
    }
}
