/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks;

import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.dependency.MavenCoordinatesImpl;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.utils.FileCache;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Throwables;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Unit test for {@link PrepareLibraryTask}. */
public class PrepareLibraryTaskTest {

    @Rule public TemporaryFolder testDir = new TemporaryFolder();

    @NonNull private File libraryDir;
    @NonNull private File projectDir;
    @NonNull private File buildCacheDir;

    @NonNull private MavenCoordinates mavenCoordinates;
    @NonNull private File aarFile;

    @Before
    public void setUp() throws IOException {
        libraryDir = testDir.newFolder("library");
        projectDir = testDir.newFolder("project");
        buildCacheDir = testDir.newFolder("build-cache");

        // Create an aar as input
        mavenCoordinates =
                new MavenCoordinatesImpl("testGroupId", "testArtifact", "1.0");
        File jarFile = new File(libraryDir, "classes.jar");
        Files.write("Library content", jarFile, StandardCharsets.UTF_8);
        aarFile = new File(libraryDir, "library.aar");
        createAar(jarFile, aarFile);
    }

    @Test
    public void testBuildCacheEnabled() throws Exception {
        FileCache buildCache = FileCache.getInstanceWithMultiProcessLocking(buildCacheDir);

        // Run PrepareLibraryTask, expect that the exploded aar is created in the build cache
        // directory
        File explodedDir = buildCache.getFileInCache(PrepareLibraryTask.getCacheInputs(aarFile));
        PrepareLibraryTask task =
                createPrepareLibraryTask(
                        projectDir, aarFile, explodedDir, buildCache, mavenCoordinates);
        task.execute();

        assertThat(buildCacheDir.list()).hasLength(2); // Including 1 lock file
        assertThat(FileUtils.join(explodedDir, "jars", "classes.jar"))
                .hasContents("Library content");
        long explodedDirTimestamp = explodedDir.lastModified();

        // Also expect that the exploded directory is not registered as an output directory for
        // Gradle incremental build
        assertThat(task.getOutputs().getFiles()).isEmpty();

        // Rerun PrepareLibraryTask, expect that the exploded aar is reused and not recreated
        task = createPrepareLibraryTask(
                projectDir, aarFile, explodedDir, buildCache, mavenCoordinates);
        task.execute();
        assertThat(buildCacheDir.list()).hasLength(2); // Including 1 lock file
        assertThat(explodedDir).wasModifiedAt(explodedDirTimestamp);

        // Change the contents of the aar
        MavenCoordinates mavenCoordinates2 = mavenCoordinates;
        File jarFile2 = new File(libraryDir, "classes.jar");
        Files.write("New library content", jarFile2, StandardCharsets.UTF_8);
        File aarFile2 = aarFile;
        createAar(jarFile2, aarFile2);

        // Run PrepareLibraryTask for the new aar, expect that a new exploded aar is created in the
        // build cache directory
        File explodedDir2 = buildCache.getFileInCache(PrepareLibraryTask.getCacheInputs(aarFile2));
        assertThat(explodedDir2).isNotEqualTo(explodedDir);

        task = createPrepareLibraryTask(
                projectDir, aarFile2, explodedDir2, buildCache, mavenCoordinates2);
        task.execute();
        assertThat(buildCacheDir.list()).hasLength(4); // Including 2 lock files
        assertThat(FileUtils.join(explodedDir2, "jars", "classes.jar"))
                .hasContents("New library content");

        // Create a new aar with the same contents but a different timestamp
        MavenCoordinates mavenCoordinates3 = mavenCoordinates2;
        File aarFile3 = aarFile2;
        TestUtils.waitForFileSystemTick();
        aarFile3.setLastModified(System.currentTimeMillis());

        // Run PrepareLibraryTask for the new aar, expect that a new exploded aar is created in the
        // build cache directory
        File explodedDir3 = buildCache.getFileInCache(PrepareLibraryTask.getCacheInputs(aarFile3));
        assertThat(explodedDir3).isNotEqualTo(explodedDir);
        assertThat(explodedDir3).isNotEqualTo(explodedDir2);

        task = createPrepareLibraryTask(
                projectDir, aarFile3, explodedDir3, buildCache, mavenCoordinates3);
        task.execute();
        assertThat(buildCacheDir.list()).hasLength(6); // Including 3 lock files
        assertThat(FileUtils.join(explodedDir3, "jars", "classes.jar"))
                .hasContents("New library content");
    }

    @Test
    public void testBuildCacheDisabled() throws IOException {
        // Run PrepareLibraryTask, expect that the exploded aar is created in the exploded
        // directory outside the build cache directory
        File explodedDir = testDir.newFolder("exploded-aar");
        PrepareLibraryTask task = createPrepareLibraryTask(
                projectDir, aarFile, explodedDir, null, mavenCoordinates);
        task.execute();
        assertThat(FileUtils.join(explodedDir, "jars", "classes.jar"))
                .hasContents("Library content");

        // Also expect that the exploded directory is registered as an output directory for Gradle
        // incremental build
        assertThat(task.getOutputs().getFiles()).containsExactly(explodedDir);
    }

    // http://b.android.com/228623
    @Test
    public void testBuildCacheEnabledWithSnapshotArtifact() throws IOException {
        FileCache buildCache = FileCache.getInstanceWithMultiProcessLocking(buildCacheDir);
        mavenCoordinates = new MavenCoordinatesImpl("testGroupId", "testArtifact", "1.0-SNAPSHOT");

        // Run PrepareLibraryTask, expect that the exploded aar is created in the exploded
        // directory outside the build cache directory (as if the build cache was disabled)
        File explodedDir = testDir.newFolder("exploded-aar");
        PrepareLibraryTask task = createPrepareLibraryTask(
                projectDir, aarFile, explodedDir, buildCache, mavenCoordinates);
        task.execute();
        assertThat(FileUtils.join(explodedDir, "jars", "classes.jar"))
                .hasContents("Library content");

        // Also expect that the exploded directory is registered as an output directory for Gradle
        // incremental build
        assertThat(task.getOutputs().getFiles()).containsExactly(explodedDir);
    }

    @Test
    public void testClassesDotJarDoesNotExist() throws IOException {
        // Create an aar as input, not containing classes.jar
        MavenCoordinates mavenCoordinates =
                new MavenCoordinatesImpl("testGroupId", "testArtifact", "1.0");
        File jarFile = new File(libraryDir, "no-classes.jar");
        Files.write("Library content", jarFile, StandardCharsets.UTF_8);
        File aarFile = new File(libraryDir, "library.aar");
        createAar(jarFile, aarFile);

        // Run PrepareLibraryTask, expect that the aar is unzipped and an empty jar/classes.jar is
        // created in the exploded directory
        File explodedDir = testDir.newFolder("exploded-aar");
        PrepareLibraryTask task = createPrepareLibraryTask(
                projectDir, aarFile, explodedDir, null, mavenCoordinates);
        task.execute();
        assertThat(FileUtils.join(explodedDir, "no-classes.jar")).hasContents("Library content");
        assertThat(FileUtils.join(explodedDir, "jars", "classes.jar"))
                .doesNotContain("Library content");
    }

    @Test
    public void testBuildCacheFailure() throws Exception {
        // Let the build cache throw an exception when called
        FileCache buildCache = mock(FileCache.class);
        when(buildCache.getFileInCache(any())).thenReturn(new File(buildCacheDir, "foo"));
        when(buildCache.createFileInCacheIfAbsent(any(), any())).thenThrow(
                new RuntimeException("Build cache error"));
        when(buildCache.getCacheDirectory()).thenReturn(buildCacheDir);

        // Run PrepareLibraryTask, expect it to fail
        try {
            File explodedDir =
                    buildCache.getFileInCache(PrepareLibraryTask.getCacheInputs(aarFile));
            PrepareLibraryTask task = createPrepareLibraryTask(
                    projectDir, aarFile, explodedDir, buildCache, mavenCoordinates);
            task.execute();
            fail("Expected TaskExecutionException");
        } catch (TaskExecutionException exception) {
            assertThat(exception.getCause().getMessage()).contains("Unable to unzip");
            assertThat(Throwables.getRootCause(exception).getMessage())
                    .contains("Build cache error");
        }
    }

    private void createAar(@NonNull File jarFile, @NonNull File aarFile) throws IOException {
        ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(aarFile));
        outputStream.putNextEntry(new ZipEntry(jarFile.getName()));

        byte[] data = Files.toString(jarFile, StandardCharsets.UTF_8).getBytes();
        outputStream.write(data, 0, data.length);

        outputStream.closeEntry();
        outputStream.close();
    }

    @NonNull
    private PrepareLibraryTask createPrepareLibraryTask(
            @NonNull File projectDir,
            @NonNull File bundle,
            @NonNull File explodedDir,
            @Nullable FileCache buildCache,
            @NonNull MavenCoordinates mavenCoordinates) {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build();
        PrepareLibraryTask task =
                project.getTasks().create("prepareLibrary", PrepareLibraryTask.class);
        task.init(bundle, explodedDir, buildCache, mavenCoordinates);
        return task;
    }
}
