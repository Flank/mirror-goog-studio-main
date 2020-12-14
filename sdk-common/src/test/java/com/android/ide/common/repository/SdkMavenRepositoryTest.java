/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.ide.common.repository;

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import junit.framework.TestCase;

public class SdkMavenRepositoryTest extends TestCase {
    private final MockFileOp mFileOp = new MockFileOp();
    private final Path SDK_HOME = mFileOp.toPath("/sdk");

    private AndroidSdkHandler mSdkHandler;
    private final RepositoryPackages mRepositoryPackages = new RepositoryPackages();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mSdkHandler =
                new AndroidSdkHandler(
                        SDK_HOME,
                        null,
                        mFileOp,
                        new FakeRepoManager(SDK_HOME, mRepositoryPackages));
    }

    private void registerRepo(@NonNull String vendor) throws IOException {
        String path = String.format("extras;%s;m2repository", vendor);
        // Create and add the package
        Map<String, LocalPackage> existing = new HashMap<>(mRepositoryPackages.getLocalPackages());
        LocalPackage pkg = new FakePackage.FakeLocalPackage(path, mFileOp);
        existing.put(path, pkg);
        mRepositoryPackages.setLocalPkgInfos(existing.values());
        // SdkMavenRepo requires that the path exists.
        ProgressIndicator progress = new FakeProgressIndicator();
        Files.createDirectories(
                new FakePackage.FakeRemotePackage(path)
                        .getInstallDir(mSdkHandler.getSdkManager(progress), progress));
    }

    private void registerAndroidRepo() throws IOException {
        registerRepo("android");
    }

    private void registerGoogleRepo() throws IOException {
        registerRepo("google");
    }

    public void testGetLocation() throws IOException {
        registerGoogleRepo();
        registerAndroidRepo();
        assertNull(SdkMavenRepository.ANDROID.getRepositoryLocation(null, false));

        Path android = SdkMavenRepository.ANDROID.getRepositoryLocation(SDK_HOME, true);
        assertNotNull(android);

        Path google = SdkMavenRepository.GOOGLE.getRepositoryLocation(SDK_HOME, true);
        assertNotNull(google);
    }

    public void testIsInstalled() throws IOException {
        assertFalse(SdkMavenRepository.ANDROID.isInstalled((Path) null));
        assertFalse(SdkMavenRepository.ANDROID.isInstalled((AndroidSdkHandler) null));
        assertFalse(SdkMavenRepository.ANDROID.isInstalled(mSdkHandler));
        assertFalse(SdkMavenRepository.GOOGLE.isInstalled(mSdkHandler));

        registerAndroidRepo();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mSdkHandler.getSdkManager(progress).loadSynchronously(0, progress, null, null);
        assertFalse(SdkMavenRepository.GOOGLE.isInstalled(mSdkHandler));
        assertTrue(SdkMavenRepository.ANDROID.isInstalled(mSdkHandler));

        registerGoogleRepo();
        mSdkHandler.getSdkManager(progress).loadSynchronously(0, progress, null, null);
        assertTrue(SdkMavenRepository.GOOGLE.isInstalled(mSdkHandler));
    }

    public void testGetDirName() {
        assertEquals("android", SdkMavenRepository.ANDROID.getDirName());
        assertEquals("google", SdkMavenRepository.GOOGLE.getDirName());
    }

    public void testGetByGroupId() {
        mFileOp.recordExistingFolder(
                "/sdk/extras/android/m2repository/com/android/support/appcompat-v7/19.0.0");
        mFileOp.recordExistingFolder(
                "/sdk/extras/google/m2repository/com/google/android/gms/play-services/5.2.08");

        assertSame(
                SdkMavenRepository.ANDROID,
                SdkMavenRepository.find(SDK_HOME, "com.android.support", "appcompat-v7"));
        assertSame(
                SdkMavenRepository.GOOGLE,
                SdkMavenRepository.find(SDK_HOME, "com.google.android.gms", "play-services"));
        assertNull(SdkMavenRepository.find(SDK_HOME, "com.google.guava", "guava"));
    }

    public void testGetSdkPath() {
        GradleCoordinate coord = new GradleCoordinate("foo.bar.baz", "artifact1",
                GradleCoordinate.parseRevisionNumber("1.2.3-alpha1"), null);
        String result = DetailsTypes.MavenType.getRepositoryPath(
                coord.getGroupId(), coord.getArtifactId(), coord.getRevision());
        assertEquals("extras;m2repository;foo;bar;baz;artifact1;1.2.3-alpha1", result);

        coord = new GradleCoordinate("foo.bar.baz", "artifact1", 1);
        result = DetailsTypes.MavenType.getRepositoryPath(
                coord.getGroupId(), coord.getArtifactId(), coord.getRevision());
        assertEquals("extras;m2repository;foo;bar;baz;artifact1;1", result);
    }

    public void testGetCoordinateFromSdkPath() {
        GradleCoordinate result = SdkMavenRepository
                .getCoordinateFromSdkPath("extras;m2repository;foo;bar;baz;artifact1;1.2.3-alpha1");
        assertEquals(new GradleCoordinate("foo.bar.baz", "artifact1",
                GradleCoordinate.parseRevisionNumber("1.2.3-alpha1"), null), result);

        result = SdkMavenRepository
                .getCoordinateFromSdkPath("extras;m2repository;foo;bar;baz;artifact1;1");
        assertEquals(new GradleCoordinate("foo.bar.baz", "artifact1", 1), result);

        result = SdkMavenRepository.getCoordinateFromSdkPath("bogus;foo;bar;baz;artifact1;1");
        assertNull(result);
    }

    public void testFindBestPackage() {
        RepoPackage r1 = new FakePackage.FakeRemotePackage("extras;m2repository;group;artifact;1");
        RepoPackage r123 = new FakePackage.FakeRemotePackage("extras;m2repository;group;artifact;1.2.3");
        RepoPackage r2 = new FakePackage.FakeRemotePackage("extras;m2repository;group;artifact;2");
        RepoPackage r211 = new FakePackage.FakeRemotePackage("extras;m2repository;group;artifact;2.1.1");
        RepoPackage bogus = new FakePackage.FakeRemotePackage("foo;group;artifact;2.1.2");
        RepoPackage other = new FakePackage.FakeRemotePackage("extras;m2repository;group2;artifact;2.1.3");
        List<RepoPackage> packages = ImmutableList.of(r1, r123, r2, r211, bogus, other);

        GradleCoordinate pattern = new GradleCoordinate("group", "artifact", 1);
        assertEquals(r1, SdkMavenRepository.findBestPackageMatching(pattern, packages));

        pattern = new GradleCoordinate("group", "artifact", 1, 2, 3);
        assertEquals(r123, SdkMavenRepository.findBestPackageMatching(pattern, packages));

        pattern = new GradleCoordinate("group", "artifact", 1, GradleCoordinate.PLUS_REV_VALUE);
        assertEquals(r123, SdkMavenRepository.findBestPackageMatching(pattern, packages));

        pattern = new GradleCoordinate("group", "artifact", 1, 0);
        assertEquals(r1, SdkMavenRepository.findBestPackageMatching(pattern, packages));

        pattern = new GradleCoordinate("group", "artifact", 2, 1, 2);
        assertNull(SdkMavenRepository.findBestPackageMatching(pattern, packages));

        pattern = new GradleCoordinate("group", "artifact", 2, 1, 3);
        assertNull(SdkMavenRepository.findBestPackageMatching(pattern, packages));
    }

    private void addLocalVersion(@NonNull HashMap<String, LocalPackage> existing, String revision) {
        String basePath = "extras;m2repository;com;android;tools;build;gradle;";
        FakePackage.FakeLocalPackage fakePackage =
                new FakePackage.FakeLocalPackage(basePath + revision, mFileOp);
        fakePackage.setRevision(Revision.parseRevision(revision));
        existing.put(basePath + revision, fakePackage);
    }

    private void setUpLocalVersions() {
        HashMap<String, LocalPackage> existing = new HashMap<>(mRepositoryPackages.getLocalPackages());
        addLocalVersion(existing, "4.0.0-rc01");
        addLocalVersion(existing, "4.0.0");
        addLocalVersion(existing, "4.1.0-alpha10");
        mRepositoryPackages.setLocalPkgInfos(existing.values());
    }

    public void testFindLatestLocalVersion() {
        setUpLocalVersions();

        ProgressIndicator progress = new FakeProgressIndicator();
        GradleCoordinate pattern = new GradleCoordinate("com.android.tools.build", "gradle", "1.0.0");
        GradleCoordinate previewPattern = new GradleCoordinate("com.android.tools.build", "gradle", "1.0.0-alpha01");
        String basePath = "extras;m2repository;com;android;tools;build;gradle;";

        RepoPackage result = SdkMavenRepository.findLatestLocalVersion(pattern, mSdkHandler, null, progress);
        assertEquals(mRepositoryPackages.getLocalPackages().get(basePath + "4.0.0"), result);

        result = SdkMavenRepository.findLatestLocalVersion(previewPattern, mSdkHandler, null, progress);
        assertEquals(mRepositoryPackages.getLocalPackages().get(basePath + "4.1.0-alpha10"), result);

        result = SdkMavenRepository.findLatestLocalVersion(previewPattern, mSdkHandler, (revision) -> !revision.isPreview(), progress);
        assertEquals(mRepositoryPackages.getLocalPackages().get(basePath + "4.0.0"), result);

        result = SdkMavenRepository.findLatestLocalVersion(previewPattern, mSdkHandler, (revision) -> revision.getMajor() != 4, progress);
        assertNull(result);
    }

    private static void addRemoteVersion(
            @NonNull HashMap<String, RemotePackage> existing, String revision) {
        String basePath = "extras;m2repository;com;android;tools;build;gradle;";
        FakePackage.FakeRemotePackage fakePackage = new FakePackage.FakeRemotePackage(basePath + revision);
        fakePackage.setRevision(Revision.parseRevision(revision));
        existing.put(basePath + revision, fakePackage);
    }

    private void setUpRemoteVersions() {
        HashMap<String, RemotePackage> existing = new HashMap<>(mRepositoryPackages.getRemotePackages());
        addRemoteVersion(existing, "5.0.0-rc01");
        addRemoteVersion(existing, "5.0.0");
        addRemoteVersion(existing, "5.1.0-alpha10");
        mRepositoryPackages.setRemotePkgInfos(existing.values());
    }

    public void testFindLatestRemoteVersion() {
        setUpRemoteVersions();

        ProgressIndicator progress = new FakeProgressIndicator();
        GradleCoordinate pattern = new GradleCoordinate("com.android.tools.build", "gradle", "1.0.0");
        GradleCoordinate previewPattern = new GradleCoordinate("com.android.tools.build", "gradle", "1.0.0-alpha01");
        String basePath = "extras;m2repository;com;android;tools;build;gradle;";

        RepoPackage result = SdkMavenRepository.findLatestRemoteVersion(pattern, mSdkHandler, null, progress);
        assertEquals(mRepositoryPackages.getRemotePackages().get(basePath + "5.0.0"), result);

        result = SdkMavenRepository.findLatestRemoteVersion(previewPattern, mSdkHandler, null, progress);
        assertEquals(mRepositoryPackages.getRemotePackages().get(basePath + "5.1.0-alpha10"), result);

        result = SdkMavenRepository.findLatestRemoteVersion(previewPattern, mSdkHandler, (revision) -> !revision.isPreview(), progress);
        assertEquals(mRepositoryPackages.getRemotePackages().get(basePath + "5.0.0"), result);

        result = SdkMavenRepository.findLatestRemoteVersion(previewPattern, mSdkHandler, (revision) -> revision.getMajor() != 5, progress);
        assertNull(result);
    }

    public void testFindLatestVersion() {
        setUpLocalVersions();
        setUpRemoteVersions();

        ProgressIndicator progress = new FakeProgressIndicator();
        GradleCoordinate pattern = new GradleCoordinate("com.android.tools.build", "gradle", "1.0.0");
        GradleCoordinate previewPattern = new GradleCoordinate("com.android.tools.build", "gradle", "1.0.0-alpha01");
        String basePath = "extras;m2repository;com;android;tools;build;gradle;";

        RepoPackage result = SdkMavenRepository.findLatestVersion(pattern, mSdkHandler, null, progress);
        assertEquals(mRepositoryPackages.getRemotePackages().get(basePath + "5.0.0"), result);

        result = SdkMavenRepository.findLatestVersion(previewPattern, mSdkHandler, null, progress);
        assertEquals(mRepositoryPackages.getRemotePackages().get(basePath + "5.1.0-alpha10"), result);

        result = SdkMavenRepository.findLatestVersion(pattern, mSdkHandler, (revision) -> revision.getMajor() != 4, progress);
        assertEquals(mRepositoryPackages.getRemotePackages().get(basePath + "5.0.0"), result);

        result = SdkMavenRepository.findLatestVersion(previewPattern, mSdkHandler, (revision) -> revision.getMajor() != 4, progress);
        assertEquals(mRepositoryPackages.getRemotePackages().get(basePath + "5.1.0-alpha10"), result);

        result = SdkMavenRepository.findLatestVersion(pattern, mSdkHandler, (revision) -> revision.getMajor() != 5, progress);
        assertEquals(mRepositoryPackages.getLocalPackages().get(basePath + "4.0.0"), result);

        result = SdkMavenRepository.findLatestVersion(previewPattern, mSdkHandler, (revision) -> revision.getMajor() != 5, progress);
        assertEquals(mRepositoryPackages.getLocalPackages().get(basePath + "4.1.0-alpha10"), result);
    }

}
