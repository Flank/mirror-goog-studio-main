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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.builder.internal.packaging.ApkCreatorType.APK_FLINGER;
import static com.android.builder.internal.packaging.ApkCreatorType.APK_Z_FILE_CREATOR;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject;
import com.android.build.gradle.integration.common.truth.ScannerSubject;
import com.android.build.gradle.integration.common.utils.GradleTestProjectUtils;
import com.android.builder.internal.packaging.ApkCreatorType;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** test for packaging of java resources. */
@RunWith(FilterableParameterized.class)
public class JavaResPackagingTest {

    @FilterableParameterized.Parameters(name = "apkCreatorType_{0}")
    public static ApkCreatorType[] params() {
        return new ApkCreatorType[] {APK_Z_FILE_CREATOR, APK_FLINGER};
    }

    @FilterableParameterized.Parameter public ApkCreatorType apkCreatorType;

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    private GradleTestProject appProject;
    private GradleTestProject libProject;
    private GradleTestProject libProject2;
    private GradleTestProject testProject;
    private GradleTestProject jarProject;

    @Before
    public void setUp() throws Exception {
        appProject = project.getSubproject("app");
        libProject = project.getSubproject("library");
        libProject2 = project.getSubproject("library2");
        testProject = project.getSubproject("test");
        jarProject = project.getSubproject("jar");

        // Rewrite settings.gradle to remove un-needed modules. We include library3 so that
        // testAppProjectTestWithRemovedResFile() also serves as a regression test for
        // https://issuetracker.google.com/128858509
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write(
                        "include 'app'\n"
                                + "include 'library'\n"
                                + "include 'library2'\n"
                                + "include 'library3'\n"
                                + "include 'test'\n"
                                + "include 'jar'\n");

        // setup dependencies.
        appendToFile(
                appProject.getBuildFile(),
                "android {\n"
                        + "    publishNonDefault true\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    api project(':library')\n"
                        + "    api project(':library3')\n"
                        + "    api project(':jar')\n"
                        + "}\n");

        appendToFile(
                libProject.getBuildFile(),
                "dependencies {\n"
                        + "    api project(':library2')\n"
                        + "    api files('libs/local.jar')\n"
                        + "}\n");

        appendToFile(testProject.getBuildFile(), "android { targetProjectPath ':app' }\n");

        GradleTestProjectUtils.setApkCreatorType(project, apkCreatorType);

        // put some default files in the 4 projects, to check non incremental packaging as well,
        // and to provide files to change to test incremental support.
        File appDir = appProject.getProjectDir();
        createOriginalResFile(appDir,  "main",        "app.txt",         "app:abcd");
        createOriginalResFile(appDir,  "androidTest", "apptest.txt",     "appTest:abcd");
        // add some .kotlin_module files to ensure they're excluded by default.
        createOriginalResFile(appDir, "main", "META-INF", "foo.kotlin_module", "app:abcd");
        createOriginalResFile(
                appDir, "androidTest", "META-INF", "foo.kotlin_module", "appTest:abcd");

        File testDir = testProject.getProjectDir();
        createOriginalResFile(testDir, "main",        "test.txt",        "test:abcd");

        File libDir = libProject.getProjectDir();
        createOriginalResFile(libDir,  "main",        "library.txt",      "library:abcd");
        createOriginalResFile(libDir,  "androidTest", "librarytest.txt",  "libraryTest:abcd");
        // add some .kotlin_module files to ensure they're included for the AAR but excluded for the
        // android test APK.
        createOriginalResFile(libDir, "main", "META-INF", "foo.kotlin_module", "library:abcd");
        createOriginalResFile(
                libDir, "androidTest", "META-INF", "foo.kotlin_module", "libraryTest:abcd");

        File lib2Dir = libProject2.getProjectDir();
        createOriginalResFile(lib2Dir, "main",        "library2.txt",     "library2:abcd");
        createOriginalResFile(lib2Dir, "androidTest", "library2test.txt", "library2Test:abcd");

        File jarDir = jarProject.getProjectDir();
        File resFolder = FileUtils.join(jarDir, "src", "main", "resources", "com", "foo");
        FileUtils.mkdirs(resFolder);
        Files.asCharSink(new File(resFolder, "jar.txt"), Charsets.UTF_8).write("jar:abcd");
    }

    @After
    public void cleanUp() {
        project = null;
        appProject = null;
        testProject = null;
        libProject = null;
        libProject2 = null;
        jarProject = null;
    }

    private static void createOriginalResFile(
            @NonNull File projectFolder,
            @NonNull String dimension,
            @NonNull String filename,
            @NonNull String content)
            throws Exception {
        createOriginalResFile(projectFolder, dimension, "com/foo", filename, content);
    }

    private static void createOriginalResFile(
            @NonNull File projectFolder,
            @NonNull String dimension,
            @NonNull String parentDirRelativePath,
            @NonNull String filename,
            @NonNull String content)
            throws Exception {
        File resourcesFolder = FileUtils.join(projectFolder, "src", dimension, "resources");
        File parentFolder = new File(resourcesFolder, parentDirRelativePath);
        FileUtils.mkdirs(parentFolder);
        Files.asCharSink(new File(parentFolder, filename), Charsets.UTF_8).write(content);
    }

    private GradleBuildResult execute(String... tasks) throws IOException, InterruptedException {
        return project.executor().run(tasks);
    }

    @Test
    public void testNonIncrementalPackaging() throws Exception {
        project.executor().run("clean", "assembleDebug", "assembleAndroidTest");

        // check the files are there. Start from the bottom of the dependency graph
        checkAar(    libProject2, "library2.txt",     "library2:abcd");
        checkTestApk(libProject2, "library2.txt",     "library2:abcd");
        checkTestApk(libProject2, "library2test.txt", "library2Test:abcd");

        checkAar(    libProject,  "library.txt",     "library:abcd");
        checkAar(    libProject,  "localjar.txt",    "localjar:abcd");
        // aar does not contain dependency's assets
        checkAar(    libProject, "library2.txt",     null);
        // test apk contains both test-only assets, lib assets, and dependency assets.
        checkTestApk(libProject, "library.txt",      "library:abcd");
        checkTestApk(libProject, "library2.txt",     "library2:abcd");
        checkTestApk(libProject, "localjar.txt",     "localjar:abcd");
        checkTestApk(libProject, "librarytest.txt",  "libraryTest:abcd");
        // but not the assets of the dependency's own test
        checkTestApk(libProject, "library2test.txt", null);

        // app contain own assets + all dependencies' assets.
        checkApk(    appProject, "app.txt",          "app:abcd");
        checkApk(    appProject, "library.txt",      "library:abcd");
        checkApk(    appProject, "library2.txt",     "library2:abcd");
        checkApk(    appProject, "jar.txt",          "jar:abcd");
        checkApk(    appProject, "localjar.txt",     "localjar:abcd");
        // app test contains test-ony assets (not app, dependency, or dependency test assets).
        checkTestApk(appProject, "apptest.txt",      "appTest:abcd");
        checkTestApk(appProject, "app.txt",          null);
        checkTestApk(appProject, "library.txt",      null);
        checkTestApk(appProject, "library2.txt",     null);
        checkTestApk(appProject, "localjar.txt",     null);
        checkTestApk(appProject, "librarytest.txt",  null);
        checkTestApk(appProject, "library2test.txt", null);

        // All APKs should exclude .kotlin_module files, but the AAR should include it.
        checkApk(appProject, "META-INF", "foo.kotlin_module", null);
        checkTestApk(appProject, "META-INF", "foo.kotlin_module", null);
        checkTestApk(libProject, "META-INF", "foo.kotlin_module", null);
        checkAar(libProject, "META-INF", "foo.kotlin_module", "library:abcd");
    }

    // ---- APP DEFAULT ---

    @Test
    public void testAppProjectWithNewResFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.addFile("src/main/resources/com/foo/newapp.txt", "newfile content");
            execute("app:assembleDebug");

            checkApk(appProject, "newapp.txt", "newfile content");
        });
    }

    @Test
    public void testAppProjectWithRemovedResFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.removeFile("src/main/resources/com/foo/app.txt");
            execute("app:assembleDebug");

            checkApk(appProject, "app.txt", null);
        });
    }

    @Test
    public void testAppProjectWithRenamedResFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(
                appProject,
                project -> {
                    project.removeFile("src/main/resources/com/foo/app.txt");
                    project.addFile("src/main/resources/com/foo/moved_app.txt", "app:abcd");
                    execute("app:assembleDebug");

                    checkApk(appProject, "app.txt", null);
                    checkApk(appProject, "moved_app.txt", "app:abcd");
                });
    }

    @Test
    public void testAppProjectWithMovedResFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(
                appProject,
                project -> {
                    project.removeFile("src/main/resources/com/foo/app.txt");
                    project.addFile("src/main/resources/com/bar/app.txt", "app:abcd");
                    execute("app:assembleDebug");

                    checkApk(appProject, "app.txt", null);
                    checkApk(appProject, "com/bar", "app.txt", "app:abcd");
                });
    }

    @Test
    public void testAppProjectWithModifiedResFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.replaceFile("src/main/resources/com/foo/app.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "app.txt", "new content");
        });
    }

    @Test
    public void testAppProjectWithNewDebugResFileOverridingMain() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.addFile("src/debug/resources/com/foo/app.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "app.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug");
        checkApk(appProject, "app.txt", "app:abcd");
    }

    @Test
    public void testAppProjectWithNewResFileOverridingDependency() throws Exception {
        String resourcePath = "src/main/resources/com/foo/library.txt";

        execute("app:clean", "app:assembleDebug");
        checkApk(appProject, "library.txt", "library:abcd");


        doTest(
                appProject,
                project -> {
                    project.addFile(resourcePath, "new content");
                    assertThat(appProject.file(resourcePath)).exists();
                    GradleBuildResult result = execute("app:assembleDebug");
                    try (Scanner stdout = result.getStdout()) {
                        ScannerSubject.assertThat(stdout)
                                .contains(
                                        "More than one file was found with OS independent path 'com/foo/library.txt'.");
                    }
                    checkApk(appProject, "library.txt", "new content");
                });

        // Trying to figure out why the test is flaky?
        assertThat(appProject.file(resourcePath)).doesNotExist();

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug");
        checkApk(appProject, "library.txt", "library:abcd");
    }

    @Test
    public void testAppProjectWithNewResFileInDebugSourceSet() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.addFile("src/debug/resources/com/foo/app.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "app.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug");
        checkApk(appProject, "app.txt", "app:abcd");
    }

    /**
     * Check for correct behavior when the order of pre-merged java resource jar files changes. This
     * must be supported in order to use @Classpath annotations on the MergeJavaResourceTask inputs.
     */
    @Test
    public void testAppProjectWithReorderedDeps() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(
                appProject,
                project -> {
                    // change order of dependencies in app from (library, library3, jar) to
                    // (library3, jar, library).
                    project.replaceInFile("build.gradle", ":library3", ":tempLibrary3");
                    project.replaceInFile("build.gradle", ":library", ":tempLibrary");
                    project.replaceInFile("build.gradle", ":jar", ":tempJar");
                    project.replaceInFile("build.gradle", ":tempLibrary3", ":jar");
                    project.replaceInFile("build.gradle", ":tempLibrary", ":library3");
                    project.replaceInFile("build.gradle", ":tempJar", ":library");
                    execute("app:assembleDebug");

                    checkApk(appProject, "library.txt", "library:abcd");
                    checkApk(appProject, "library2.txt", "library2:abcd");
                    checkApk(appProject, "jar.txt", "jar:abcd");
                });
    }

    @Test
    public void testAppProjectWithModifiedResInDependency() throws Exception {
        execute("app:clean", "library:clean", "app:assembleDebug");

        doTest(libProject, project -> {
            project.replaceFile("src/main/resources/com/foo/library.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "library.txt", "new content");
        });
    }

    /**
     * Check for correct behavior when a java res source file get removed.
     *
     * Also, with app's dependency on library3, this serves as a regression test for
     * https://issuetracker.google.com/128858509
     */
    @Test
    public void testAppProjectWithAddedResInDependency() throws Exception {
        execute("app:clean", "library:clean", "app:assembleDebug");

        doTest(libProject, project -> {
            project.addFile("src/main/resources/com/foo/newlibrary.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "newlibrary.txt", "new content");
        });
    }

    @Test
    public void testAppProjectWithRemovedResInDependency() throws Exception {
        execute("app:clean", "library:clean", "app:assembleDebug");

        doTest(libProject, project -> {
            project.removeFile("src/main/resources/com/foo/library.txt");
            project.replaceInFile("build.gradle", "api files(.*)", "");
            execute("app:assembleDebug");

            checkApk(appProject, "library.txt", null);
            checkApk(appProject, "localjar.txt", null);
        });
    }

    // ---- APP TEST ---

    @Test
    public void testAppProjectTestWithNewResFile() throws Exception {
        execute("app:clean", "app:assembleAT");

        doTest(appProject, project -> {
            project.addFile("src/androidTest/resources/com/foo/newapp.txt", "new file content");
            execute("app:assembleAT");

            checkTestApk(appProject, "newapp.txt", "new file content");
        });
    }

    @Test
    public void testAppProjectTestWithRemovedResFile() throws Exception {
        execute("app:clean", "app:assembleAT");

        doTest(appProject, project -> {
            project.removeFile("src/androidTest/resources/com/foo/apptest.txt");
            execute("app:assembleAT");

            checkTestApk(appProject, "apptest.txt", null);
        });
    }

    @Test
    public void testAppProjectTestWithModifiedResFile() throws Exception {
        execute("app:clean", "app:assembleAT");

        doTest(appProject, project -> {
            project.replaceFile("src/androidTest/resources/com/foo/apptest.txt", "new content");
            execute("app:assembleAT");

            checkTestApk(appProject, "apptest.txt", "new content");
        });
    }

    // ---- LIB DEFAULT ---

    @Test
    public void testLibProjectWithNewResFile() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.addFile("src/main/resources/com/foo/newlibrary.txt", "newfile content");
            execute("library:assembleDebug");

            checkAar(libProject, "newlibrary.txt", "newfile content");
        });
    }

    @Test
    public void testLibProjectWithRemovedFile() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.removeFile("src/main/resources/com/foo/library.txt");
            project.replaceInFile("build.gradle", "api files(.*)", "");
            execute("library:assembleDebug");

            checkAar(libProject, "library.txt", null);
            checkAar(libProject, "localjar.txt", null);
        });
    }

    @Test
    public void testLibProjectWithModifiedResFile() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.replaceFile("src/main/resources/com/foo/library.txt", "new content");
            execute("library:assembleDebug");

            checkAar(libProject, "library.txt", "new content");
        });
    }

    @Test
    public void testLibProjectWithNewResFileInDebugSourceSet() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.addFile("src/debug/resources/com/foo/library.txt", "new content");
            execute("library:assembleDebug");

            checkAar(libProject, "library.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("library:assembleDebug");
        checkAar(libProject, "library.txt", "library:abcd");

    }

    // ---- LIB TEST ---

    @Test
    public void testLibProjectTestWithNewResFile() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.addFile("src/androidTest/resources/com/foo/newlibrary.txt", "new file content");
            execute("library:assembleAT");

            checkTestApk(libProject, "newlibrary.txt", "new file content");
        });
    }

    @Test
    public void testLibProjectTestWithRemovedResFile() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.removeFile("src/androidTest/resources/com/foo/librarytest.txt");
            project.replaceInFile("build.gradle", "api files(.*)", "");
            execute("library:assembleAT");

            checkTestApk(libProject, "librarytest.txt", null);
            checkTestApk(libProject, "localjar.txt", null);
        });
    }

    @Test
    public void testLibProjectTestWithModifiedResFile() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.replaceFile("src/androidTest/resources/com/foo/librarytest.txt", "new content");
            execute("library:assembleAT");

            checkTestApk(libProject, "librarytest.txt", "new content");
        });
    }

    @Test
    public void testLibProjectTestWithNewResFileOverridingTestedLib() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(
                libProject,
                project -> {
                    project.addFile("src/androidTest/resources/com/foo/library.txt", "new content");
                    GradleBuildResult result = execute("library:assembleAT");
                    try (Scanner stdout = result.getStdout()) {
                        ScannerSubject.assertThat(stdout)
                                .contains(
                                        "More than one file was found with OS independent path 'com/foo/library.txt'.");
                    }

                    checkTestApk(libProject, "library.txt", "new content");
                });

        // file's been removed, checking in the other direction.
        execute("library:assembleAT");
        checkTestApk(libProject, "library.txt", "library:abcd");
    }

    @Test
    public void testLibProjectTestWithNewResFileOverridingDependency() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(
                libProject,
                project -> {
                    project.addFile(
                            "src/androidTest/resources/com/foo/library2.txt", "new content");
                    GradleBuildResult result = execute("library:assembleAT");
                    try (Scanner stdout = result.getStdout()) {
                        ScannerSubject.assertThat(stdout)
                                .contains(
                                        "More than one file was found with OS independent path 'com/foo/library2.txt'.");
                    }

                    checkTestApk(libProject, "library2.txt", "new content");
                });

        // file's been removed, checking in the other direction.
        execute("library:assembleAT");
        checkTestApk(libProject, "library2.txt", "library2:abcd");
    }

    // ---- TEST DEFAULT ---

    @Test
    public void testTestProjectWithNewResFile() throws Exception {
        project.executor().run("test:clean", "test:assembleDebug");

        doTest(
                testProject,
                project -> {
                    project.addFile("src/main/resources/com/foo/newtest.txt", "newfile content");
                    this.project.executor().run("test:assembleDebug");

                    checkApk(testProject, "newtest.txt", "newfile content");
                });
    }

    @Test
    public void testTestProjectWithRemovedResFile() throws Exception {
        project.executor().run("test:clean", "test:assembleDebug");

        doTest(
                testProject,
                project -> {
                    project.removeFile("src/main/resources/com/foo/test.txt");
                    this.project.executor().run("test:assembleDebug");

                    checkApk(testProject, "test.txt", null);
                });
    }

    @Test
    public void testTestProjectWithModifiedResFile() throws Exception {
        project.executor().run("test:clean", "test:assembleDebug");

        doTest(
                testProject,
                project -> {
                    project.replaceFile("src/main/resources/com/foo/test.txt", "new content");
                    this.project.executor().run("test:assembleDebug");

                    checkApk(testProject, "test.txt", "new content");
                });
    }

    // --------------------------------

    /**
     * check an apk has (or not) the given res file name.
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
        checkApk(project, "com/foo", filename, content);
    }

    /**
     * check an apk has (or not) the given res file name.
     *
     * <p>If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param parentDirRelativePath the relative path of the file's parent directory
     * @param filename the filename
     * @param content the content
     */
    private static void checkApk(
            @NonNull GradleTestProject project,
            @NonNull String parentDirRelativePath,
            @NonNull String filename,
            @Nullable String content)
            throws Exception {
        check(assertThat(project.getApk("debug")), parentDirRelativePath, filename, content);
    }

    /**
     * check a test apk has (or not) the given res file name.
     *
     * <p>If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param filename the filename
     * @param content the content
     */
    private void checkTestApk(
            @NonNull GradleTestProject project, @NonNull String filename, @Nullable String content)
            throws Exception {
        checkTestApk(project, "com/foo", filename, content);
    }

    /**
     * check a test apk has (or not) the given res file name.
     *
     * <p>If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param parentDirRelativePath the relative path of the file's parent directory
     * @param filename the filename
     * @param content the content
     */
    private void checkTestApk(
            @NonNull GradleTestProject project,
            @NonNull String parentDirRelativePath,
            @NonNull String filename,
            @Nullable String content)
            throws Exception {
        check(assertThat(project.getTestApk()), parentDirRelativePath, filename, content);
    }

    /**
     * check an aar has (or not) the given res file name.
     *
     * <p>If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param filename the filename
     * @param content the content
     */
    private static void checkAar(
            @NonNull GradleTestProject project, @NonNull String filename, @Nullable String content)
            throws Exception {
        checkAar(project, "com/foo", filename, content);
    }

    /**
     * check an aar has (or not) the given res file name.
     *
     * <p>If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param parentDirRelativePath the relative path of the file's parent directory
     * @param filename the filename
     * @param content the content
     */
    private static void checkAar(
            @NonNull GradleTestProject project,
            @NonNull String parentDirRelativePath,
            @NonNull String filename,
            @Nullable String content)
            throws Exception {
        project.testAar("debug", it -> check(it, parentDirRelativePath, filename, content));
    }

    /**
     * check an AbstractAndroidSubject has (or not) the given res file name.
     *
     * <p>If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param subject the AbstractAndroidSubject
     * @param parentDirRelativePath the relative path of the file's parent directory
     * @param filename the filename
     * @param content the content
     */
    private static void check(
            @NonNull AbstractAndroidSubject subject,
            @NonNull String parentDirRelativePath,
            @NonNull String filename,
            @Nullable String content) {
        if (content != null) {
            subject.containsJavaResourceWithContent(
                    parentDirRelativePath + "/" + filename, content);
        } else {
            subject.doesNotContainJavaResource(parentDirRelativePath + "/" + filename);
        }
    }
}
