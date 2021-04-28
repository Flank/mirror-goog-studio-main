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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class NativeSoPackagingFromRemoteAarTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("projectWithModules").create();

    private GradleTestProject appProject;
    private GradleTestProject libProject;

    private void execute(String... tasks) throws IOException, InterruptedException {
        project.executor().withFailOnWarning(false).run(tasks);
    }

    @Before
    public void setUp() throws Exception {
        appProject = project.getSubproject("app");
        libProject = project.getSubproject("library");

        // rewrite settings.gradle to remove un-needed modules
        Files.asCharSink(new File(project.getProjectDir(), "settings.gradle"), Charsets.UTF_8)
                .write("include 'app'\n" + "include 'library'\n");

        // setup dependencies.
        TestFileUtils.appendToFile(
                appProject.getBuildFile(),
                "repositories {\n"
                        + "    maven { url '../testrepo' }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "    api 'com.example.android.nativepackaging:library:1.0-SNAPSHOT@aar'\n"
                        + "}\n");

        TestFileUtils.appendToFile(
                libProject.getBuildFile(),
                "apply plugin: 'maven-publish'\n"
                        + "\n"
                        + "group = 'com.example.android.nativepackaging'\n"
                        + "version = '1.0-SNAPSHOT'\n"
                        + "\n"
                        + "android.publishing.singleVariant('release')\n"
                        + "\n"
                        + "afterEvaluate {\n"
                        + "    publishing {\n"
                        + "        repositories {\n"
                        + "            maven {\n"
                        + "                url = uri(\"../testrepo\")\n"
                        + "            }\n"
                        + "        }\n"
                        + "        publications {\n"
                        + "            release(MavenPublication) {\n"
                        + "                from components.release\n"
                        + "                artifactId = 'library'\n"
                        + "            }\n"
                        + "       }\n"
                        + "    }\n"
                        + "}\n");

        // put some default files in the library project, to check non incremental packaging
        // as well, and to provide files to change to test incremental support.
        File libDir = libProject.getProjectDir();
        createOriginalSoFile(libDir,  "main",        "liblibrary.so",      "library:abcd");
        createOriginalSoFile(libDir,  "main",        "liblibrary2.so",     "library2:abcdef");

        // build and deploy the library
        project.executor()
                .withFailOnWarning(false)
                .withArgument("--configure-on-demand")
                .run("library:clean", "library:publish");

        execute("app:clean", "app:assembleDebug");

        // build and deploy version 2.0-SNAPSHOT of the library (with an extra .so file)
        createOriginalSoFile(libDir, "main", "liblibrary3.so", "library3:abcdefg");
        TestFileUtils.searchAndReplace(libProject.getBuildFile(), "1.0-SNAPSHOT", "2.0-SNAPSHOT");
        project.executor()
                .withFailOnWarning(false)
                .withArgument("--configure-on-demand")
                .run("library:clean", "library:publish");
        // remove the added .so file and revert the version to 1.0-SNAPSHOT
        FileUtils.delete(FileUtils.join(libDir, "src", "main", "jniLibs", "x86", "liblibrary3.so"));
        TestFileUtils.searchAndReplace(libProject.getBuildFile(), "2.0-SNAPSHOT", "1.0-SNAPSHOT");
    }

    private static void createOriginalSoFile(
            @NonNull File projectFolder,
            @NonNull String dimension,
            @NonNull String filename,
            @NonNull String content)
            throws Exception {
        File assetFolder = FileUtils.join(projectFolder, "src", dimension, "jniLibs", "x86");
        FileUtils.mkdirs(assetFolder);
        Files.asCharSink(new File(assetFolder, filename), Charsets.UTF_8).write(content);
    }

    @Test
    public void testNonIncrementalPackaging() throws Exception {
        checkApk(appProject, "liblibrary.so",      "library:abcd");
        checkApk(appProject, "liblibrary2.so",     "library2:abcdef");
    }

    @Test
    public void testAppProjectWithNewSoInAar() throws Exception {
        doTest(
                libProject,
                project -> {
                    project.addFile("src/main/jniLibs/x86/libnewapp.so", "newfile content");
                    // must be two calls as it's a single project that includes both modules and
                    // dependency is resolved at evaluation time, before the library published its
                    // new
                    // versions.
                    execute("library:publish");
                    execute("app:assembleDebug");

                    checkApk(appProject, "libnewapp.so", "newfile content");
                });
    }

    @Test
    public void testAppProjectWithRemovedSoInAar() throws Exception {
        doTest(
                libProject,
                project -> {
                    project.removeFile("src/main/jniLibs/x86/liblibrary2.so");
                    // must be two calls as it's a single project that includes both modules and
                    // dependency is resolved at evaluation time, before the library published its
                    // new
                    // versions.
                    execute("library:publish");
                    execute("app:assembleDebug");

                    checkApk(appProject, "liblibrary2.so", null);
                });
    }

    @Test
    public void testAppProjectWithEditedSoInAar() throws Exception {
        doTest(
                libProject,
                project -> {
                    project.replaceFile("src/main/jniLibs/x86/liblibrary2.so", "new content");
                    // must be two calls as it's a single project that includes both modules and
                    // dependency is resolved at evaluation time, before the library published its
                    // new
                    // versions.
                    execute("library:publish");
                    execute("app:assembleDebug");

                    checkApk(appProject, "liblibrary2.so", "new content");
                });
    }

    /**
     * Regression test for Issue 141536986. This test ensures that updating the version of an
     * external dependency doesn't cause some of the native libraries to be removed from the APK.
     */
    @Test
    public void testChangingAarVersionInApp() throws Exception {
        doTest(
                appProject,
                project -> {
                    project.replaceInFile("build.gradle", "1.0-SNAPSHOT", "2.0-SNAPSHOT");
                    execute("app:assembleDebug");
                    checkApk(appProject, "liblibrary.so", "library:abcd");
                    checkApk(appProject, "liblibrary2.so", "library2:abcdef");
                    checkApk(appProject, "liblibrary3.so", "library3:abcdefg");
                });
    }

    /**
     * check an apk has (or not) the given asset file name.
     *
     * <p>If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param filename the filename
     * @param content the content
     */
    private static void checkApk(
            @NonNull GradleTestProject project, @NonNull String filename, @Nullable String content)
            throws Exception {
        Apk apk = project.getApk("debug");
        if (content != null) {
            TruthHelper.assertThat(apk).containsFileWithContent("lib/x86/" + filename, content);
        } else {
            TruthHelper.assertThat(apk).doesNotContain("lib/x86/" + filename);
        }
        PackagingTests.checkZipAlign(apk.getFile().toFile());
    }

}
