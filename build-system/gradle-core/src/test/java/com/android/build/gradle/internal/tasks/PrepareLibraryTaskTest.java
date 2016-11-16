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

import com.android.annotations.NonNull;
import com.android.builder.dependency.MavenCoordinatesImpl;
import com.android.apkzlib.utils.FileCache;
import com.android.builder.model.MavenCoordinates;
import com.android.utils.FileUtils;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Project;
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
    public void testBuildCacheEnabled() throws IOException {
        FileCache buildCache = FileCache.getInstanceWithInterProcessLocking(buildCacheDir);

        // Run PrepareLibraryTask, expect that the exploded aar is created in the build cache
        // directory
        File explodedDir =
                buildCache.getFileInCache(PrepareLibraryTask.getBuildCacheInputs(mavenCoordinates));
        PrepareLibraryTask task = createPrepareLibraryTask(
                projectDir, aarFile, explodedDir, Optional.of(buildCache), mavenCoordinates);
        task.execute();

        assertThat(buildCacheDir.list().length).isEqualTo(2); // Including 1 lock file
        assertThat(FileUtils.join(explodedDir, "jars", "classes.jar"))
                .hasContents("Library content");
        long explodedDirTimestamp = explodedDir.lastModified();

        // Also expect that the exploded directory is not registered as an output directory for
        // Gradle incremental build
        assertThat(task.getOutputs().getFiles()).isEmpty();

        // Rerun PrepareLibraryTask, expect that the exploded aar is reused and not recreated
        task = createPrepareLibraryTask(
                projectDir, aarFile, explodedDir, Optional.of(buildCache), mavenCoordinates);
        task.execute();
        assertThat(buildCacheDir.list().length).isEqualTo(2); // Including 1 lock file
        assertThat(explodedDir).wasModifiedAt(explodedDirTimestamp);

        // Create a new aar of the same library but with a different version
        File libraryDir2 = testDir.newFolder("library2");
        MavenCoordinates mavenCoordinates2 =
                new MavenCoordinatesImpl("testGroupId", "testArtifact", "1.1");
        File jarFile2 = new File(libraryDir2, "classes.jar");
        Files.write("New library content", jarFile2, StandardCharsets.UTF_8);
        File aarFile2 = new File(libraryDir2, "library2.aar");
        createAar(jarFile2, aarFile2);

        // Run PrepareLibraryTask for the new aar, expect that a new exploded aar is created in the
        // build cache directory
        File explodedDir2 =
                buildCache.getFileInCache(
                        PrepareLibraryTask.getBuildCacheInputs(mavenCoordinates2));
        assertThat(explodedDir2).isNotEqualTo(explodedDir);

        task = createPrepareLibraryTask(
                projectDir, aarFile2, explodedDir2, Optional.of(buildCache), mavenCoordinates2);
        task.execute();
        assertThat(buildCacheDir.list().length).isEqualTo(4); // Including 2 lock files
        assertThat(FileUtils.join(explodedDir2, "jars", "classes.jar"))
                .hasContents("New library content");
    }

    @Test
    public void testBuildCacheDisabled() throws IOException {
        // Run PrepareLibraryTask, expect that the exploded aar is created in the exploded
        // directory
        File explodedDir = testDir.newFolder("exploded-aar");
        PrepareLibraryTask task = createPrepareLibraryTask(
                projectDir, aarFile, explodedDir, Optional.empty(), mavenCoordinates);
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
                projectDir, aarFile, explodedDir, Optional.empty(), mavenCoordinates);
        task.execute();
        assertThat(FileUtils.join(explodedDir, "no-classes.jar")).hasContents("Library content");
        assertThat(FileUtils.join(explodedDir, "jars", "classes.jar"))
                .doesNotContain("Library content");
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
            @NonNull Optional<FileCache> buildCache,
            @NonNull MavenCoordinates mavenCoordinates) {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build();
        PrepareLibraryTask task =
                project.getTasks().create("prepareLibrary", PrepareLibraryTask.class);
        task.init(bundle, explodedDir, buildCache, mavenCoordinates);
        return task;
    }
}
