/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.performance;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder;
import com.android.build.gradle.integration.common.utils.SdkHelper;
import com.android.testutils.TestUtils;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import kotlin.KotlinVersion;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

/**
 * When we run the Gradle performance benchmarks, there's a lot of state we need around us on local
 * disk in order for the benchmarks to complete successfully: the SDK, various Maven repos, the
 * Gradle binary itself, etc.
 *
 * <p>When we're running the benchmarks locally, we have access to our entire set of repositories
 * and as a result all of the state we need. When we run in a virtual machine, however, we don't
 * have a full repo checkout and instead rely on bundling all of the required state in to the jar
 * and unzipping it at runtime.
 *
 * <p>This class represents each environment that benchmarks can run in. If there's anything that a
 * benchmark needs locally to complete successfully, it should go in to this class.
 */
public class BenchmarkEnvironment {
    /**
     * The local environment with a full repo checked out.
     *
     * <p>Note that this method doesn't actually do any repo checkouts, it relies on the repo
     * already being present on disk.
     */
    public static BenchmarkEnvironment fromRepo() {
        return new BenchmarkEnvironment(
                Paths.get(
                        GradleTestProject.BUILD_DIR.getAbsolutePath(), "BenchmarkTest", "profiles"),
                GradleTestProject.getLocalRepositories(),
                TestUtils.getWorkspaceFile("external").toPath(),
                SdkHelper.findSdkDir().toPath(),
                TestUtils.getWorkspaceFile("tools/external/gradle").toPath(),
                Paths.get(GradleTestProject.BUILD_DIR.getAbsolutePath(), "BenchmarkTest"));
    }

    /**
     * The environment as created from a standalone jar.
     *
     * <p>We have a build target called "performanceTestJar" that packages up the performance tests
     * along with all of the dependencies into a single jar. This method locates the dependencies
     * inside the jar and unzips them to a location on disk specified by the single argument to this
     * method. It assumes that it is being called from a java -jar invocation on the
     * performanceTestJar.
     *
     * <p>You'll need to clean up the content of dest before the process exits. If you don't, you'll
     * leak files on to local disk and run the risk of filling up the disk.
     *
     * @param dest the directory to unzip files to locally.
     * @throws IOException if something goes wrong while unzipping.
     */
    public static BenchmarkEnvironment fromJar(Path dest) throws IOException, ArchiveException {
        unpack(
                dest.resolve("offline_repo"),
                new BufferedInputStream(ClassLoader.getSystemResourceAsStream("offline_repo.zip")));
        unpack(
                dest.resolve("prebuilts_repo"),
                new BufferedInputStream(
                        ClassLoader.getSystemResourceAsStream("prebuilts_repo.tar")));
        unpack(
                dest.resolve("projects"),
                new BufferedInputStream(ClassLoader.getSystemResourceAsStream("projects.tar")));
        unpack(
                dest.resolve("android_sdk"),
                new BufferedInputStream(ClassLoader.getSystemResourceAsStream("android_sdk.tar")));
        unpack(
                dest.resolve("gradle"),
                new BufferedInputStream(ClassLoader.getSystemResourceAsStream("gradle.tar")));

        return new BenchmarkEnvironment(
                dest.resolve("profiles"),
                ImmutableList.of(dest.resolve("offline_repo"), dest.resolve("prebuilts_repo")),
                dest.resolve("projects"),
                dest.resolve("android_sdk"),
                dest.resolve("gradle"),
                dest.resolve("scratch"));
    }

    private static void unpack(Path dest, InputStream in) throws IOException, ArchiveException {
        byte[] buffer = new byte[4096];
        try (ArchiveInputStream ais = new ArchiveStreamFactory().createArchiveInputStream(in)) {
            ArchiveEntry entry;
            while ((entry = ais.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                File entryFile = dest.resolve(entry.getName()).toFile();
                Files.createParentDirs(entryFile);

                try (FileOutputStream fos = new FileOutputStream(entryFile)) {
                    int len;
                    while ((len = ais.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }

                if (entry instanceof TarArchiveEntry) {
                    TarArchiveEntry tarEntry = (TarArchiveEntry) entry;

                    boolean read = (tarEntry.getMode() & 0b100000000) != 0;
                    boolean write = (tarEntry.getMode() & 0b010000000) != 0;
                    boolean execute = (tarEntry.getMode() & 0b001000000) != 0;

                    if (!entryFile.setReadable(read)) {
                        throw new IOException(
                                "unable to make " + entryFile.getAbsolutePath() + " readable");
                    }
                    if (!entryFile.setWritable(write)) {
                        throw new IOException(
                                "unable to make " + entryFile.getAbsolutePath() + " writeable");
                    }
                    if (!entryFile.setExecutable(execute)) {
                        throw new IOException(
                                "unable to make " + entryFile.getAbsolutePath() + " executable");
                    }
                }
            }
        }
    }

    /**
     * Directory to store GradleBuildProfile output protos in. Passed to the Android Gradle Plugin
     * at runtime.
     */
    @NonNull private final Path profileDir;

    /**
     * A collection of Maven repos to use for GradleTestProject. Usually used to point at the
     * prebuilts and offline repos.
     */
    @NonNull private final List<Path> mavenRepos;

    /** Directory in which to find external projects, e.g. AntennaPod. */
    @NonNull private final Path projectDir;

    /** Directory in which to find the Android SDK. */
    @NonNull private final Path sdkDir;

    /** Directory in which to find versions of the Gradle tool. */
    @NonNull private final Path gradleDir;

    /** Directory to store copies of projects for individual benchmark tests. */
    @NonNull private final Path scratchDir;

    @NonNull
    public Path getProjectDir() {
        return projectDir;
    }

    @NonNull
    public Path getScratchDir() {
        return scratchDir;
    }

    private BenchmarkEnvironment(
            @NonNull Path profileDir,
            @NonNull List<Path> mavenRepos,
            @NonNull Path projectDir,
            @NonNull Path sdkDir,
            @NonNull Path gradleDir,
            @NonNull Path testDir) {
        this.profileDir = profileDir;
        this.mavenRepos = mavenRepos;
        this.projectDir = projectDir;
        this.sdkDir = sdkDir;
        this.gradleDir = gradleDir;
        this.scratchDir = testDir;
    }

    /**
     * Returns a GradleTestProjectBuilder that has a number of its fields pre-populated from the
     * given environment. It's advised that all projects created within a benchmarking run is done
     * by first calling this method and then setting anything else they need to set before calling
     * {@code build()}.
     */
    public GradleTestProjectBuilder getProjectBuilder() {
        return GradleTestProject.builder()
                .enableProfileOutputInDirectory(profileDir)
                .withTestDir(scratchDir.resolve("testDir").toFile())
                .withRepoDirectories(mavenRepos)
                .withAndroidHome(sdkDir.toFile())
                .withGradleDistributionDirectory(gradleDir.toFile())
                .withKotlinVersion(KotlinVersion.CURRENT.toString());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("profileDir", profileDir)
                .add("mavenRepos", mavenRepos)
                .add("projectDir", projectDir)
                .add("sdkDir", sdkDir)
                .add("gradleDir", gradleDir)
                .add("scratchDir", scratchDir)
                .toString();
    }
}
