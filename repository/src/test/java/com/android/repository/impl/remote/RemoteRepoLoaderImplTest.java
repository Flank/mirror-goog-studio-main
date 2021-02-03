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

package com.android.repository.impl.remote;

import static com.android.repository.testframework.FakePackage.FakeRemotePackage;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.Channel;
import com.android.repository.api.Checksum;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.api.SimpleRepositorySource;
import com.android.repository.impl.manager.RemoteRepoLoader;
import com.android.repository.impl.manager.RemoteRepoLoaderImpl;
import com.android.repository.impl.meta.Archive;
import com.android.repository.impl.meta.RemotePackageImpl;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepositorySourceProvider;
import com.android.repository.testframework.FakeSettingsController;
import com.android.repository.testframework.MockFileOp;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import junit.framework.TestCase;
import org.mockito.Mockito;

/** Tests for {@link RemoteRepoLoaderImpl} */
public class RemoteRepoLoaderImplTest extends TestCase {

    public void testRemoteRepo() throws Exception {
        RepositorySource source = new SimpleRepositorySource("http://www.example.com",
                "Source UI Name", true,
                ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(
                new URL("http://www.example.com"),
                getClass().getResourceAsStream("/testRepo2.xml"));
        FakeProgressIndicator progress = new FakeProgressIndicator(true);
        RemoteRepoLoader loader =
                new RemoteRepoLoaderImpl(
                        ImmutableList.of(
                                new FakeRepositorySourceProvider(ImmutableList.of(source))),
                        null);
        Map<String, RemotePackage> pkgs = loader
                .fetchPackages(progress, downloader, new FakeSettingsController(false));
        progress.assertNoErrorsOrWarnings();
        assertEquals(2, pkgs.size());
        RemotePackage p1 = pkgs.get("mypackage;foo");
        assertEquals(new Revision(1, 2, 3), p1.getVersion());
        assertEquals("the license text", p1.getLicense().getValue().trim());
        Collection<Archive> archives = ((RemotePackageImpl) p1).getAllArchives();
        assertEquals(4, archives.size());
        assertTrue(p1.getTypeDetails() instanceof TypeDetails.GenericType);
        Iterator<Archive> archiveIter = ((RemotePackageImpl) p1).getAllArchives().iterator();
        Archive a1 = archiveIter.next();
        assertEquals(1234, a1.getComplete().getSize());
        assertEquals("x64", a1.getHostArch());
        Archive a2 = archiveIter.next();
        assertEquals(1234, a2.getComplete().getSize());
        assertEquals("aarch64", a2.getHostArch());
        Archive a3 = archiveIter.next();
        Iterator<Archive.PatchType> patchIter = a3.getAllPatches().iterator();
        Archive.PatchType patch = patchIter.next();
        assertEquals(new Revision(1, 3, 2), patch.getBasedOn().toRevision());
        patch = patchIter.next();
        assertEquals(new Revision(2), patch.getBasedOn().toRevision());
    }

    public void testChannels() throws Exception {
        RepositorySource source = new SimpleRepositorySource("http://www.example.com",
                                                             "Source UI Name", true,
                                                             ImmutableSet.of(RepoManager.getCommonModule(),
                                                                     RepoManager.getGenericModule()),
                                                             null);
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(new URL("http://www.example.com"),
                getClass().getResourceAsStream("/testRepoWithChannels.xml"));
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RemoteRepoLoader loader =
                new RemoteRepoLoaderImpl(
                        ImmutableList.of(
                                new FakeRepositorySourceProvider(ImmutableList.of(source))),
                        null);
        FakeSettingsController settings = new FakeSettingsController(false);
        Map<String, RemotePackage> pkgs = loader
          .fetchPackages(progress, downloader, settings);
        progress.assertNoErrorsOrWarnings();

        assertEquals(2, pkgs.size());
        assertEquals(new Revision(1, 2, 3), pkgs.get("mypackage;foo").getVersion());
        assertEquals(new Revision(4, 5, 6), pkgs.get("mypackage;bar").getVersion());

        settings.setChannel(Channel.create(1));
        pkgs = loader.fetchPackages(progress, downloader, settings);
        assertEquals(2, pkgs.size());
        assertEquals(new Revision(1, 2, 4), pkgs.get("mypackage;foo").getVersion());
        assertEquals(new Revision(4, 5, 6), pkgs.get("mypackage;bar").getVersion());

        settings.setChannel(Channel.create(2));
        pkgs = loader.fetchPackages(progress, downloader, settings);
        assertEquals(2, pkgs.size());
        assertEquals(new Revision(1, 2, 5), pkgs.get("mypackage;foo").getVersion());
        assertEquals(new Revision(4, 5, 6), pkgs.get("mypackage;bar").getVersion());

        settings.setChannel(Channel.create(3));
        pkgs = loader.fetchPackages(progress, downloader, settings);
        assertEquals(2, pkgs.size());
        assertEquals(new Revision(1, 2, 5), pkgs.get("mypackage;foo").getVersion());
        assertEquals(new Revision(4, 5, 7), pkgs.get("mypackage;bar").getVersion());
    }

    public void testFallback() throws Exception {
        RepositorySource source =
                new SimpleRepositorySource(
                        "http://www.example.com",
                        "Source UI Name",
                        true,
                        ImmutableSet.of(
                                RepoManager.getCommonModule(), RepoManager.getGenericModule()),
                        null);
        final String legacyUrl = "http://www.example.com/legacy";
        RepositorySource legacySource =
                new SimpleRepositorySource(
                        legacyUrl,
                        "Legacy UI Name",
                        true,
                        ImmutableSet.of(
                                RepoManager.getCommonModule(), RepoManager.getGenericModule()),
                        null);
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(new URL("http://www.example.com"),
                getClass().getResourceAsStream("/testRepo.xml"));
        downloader.registerUrl(new URL(legacyUrl),
                "foo".getBytes());
        FakeProgressIndicator progress = new FakeProgressIndicator(true);
        RemoteRepoLoader loader =
                new RemoteRepoLoaderImpl(
                        ImmutableList.of(
                                new FakeRepositorySourceProvider(
                                        ImmutableList.of(source, legacySource))),
                        (source1, downloader1, settings, progress1) -> {
                            assertEquals(legacyUrl, source1.getUrl());
                            FakeRemotePackage legacy = new FakeRemotePackage("legacy");
                            legacy.setRevision(new Revision(1, 2, 9));
                            legacy.setCompleteUrl("http://www.example.com/legacy.zip");
                            return ImmutableSet.of(legacy);
                        });
        Map<String, RemotePackage> pkgs = loader
                .fetchPackages(progress, downloader, new FakeSettingsController(false));
        progress.assertNoErrorsOrWarnings();
        assertEquals(3, pkgs.size());
        assertEquals(new Revision(1, 2, 9), pkgs.get("legacy").getVersion());
    }

    public void testNonFallbackPreferred() throws Exception {
        RepositorySource source =
                new SimpleRepositorySource(
                        "http://www.example.com",
                        "Source UI Name",
                        true,
                        ImmutableSet.of(
                                RepoManager.getCommonModule(), RepoManager.getGenericModule()),
                        null);
        final String legacyUrl = "http://www.example.com/legacy";
        RepositorySource legacySource =
                new SimpleRepositorySource(
                        legacyUrl,
                        "Legacy UI Name",
                        true,
                        ImmutableSet.of(
                                RepoManager.getCommonModule(), RepoManager.getGenericModule()),
                        null);
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(
                new URL("http://www.example.com"),
                getClass().getResourceAsStream("/testRepo2.xml"));
        downloader.registerUrl(new URL(legacyUrl),
                "foo".getBytes());
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RemoteRepoLoader loader =
                new RemoteRepoLoaderImpl(
                        ImmutableList.of(
                                new FakeRepositorySourceProvider(
                                        ImmutableList.of(source, legacySource))),
                        (source1, downloader1, settings, progress1) -> {
                            assertEquals(legacyUrl, source1.getUrl());
                            FakeRemotePackage legacy = new FakeRemotePackage("mypackage;foo");
                            legacy.setRevision(new Revision(1, 2, 3));
                            legacy.setCompleteUrl("http://www.example.com/legacy.zip");
                            return ImmutableSet.of(legacy);
                        });
        Map<String, RemotePackage> pkgs = loader
                .fetchPackages(progress, downloader, new FakeSettingsController(false));
        progress.assertNoErrorsOrWarnings();
        assertEquals(2, pkgs.size());
        assertFalse(pkgs.get("mypackage;foo") instanceof FakePackage);
    }

    private static final String TEST_LOCAL_PREFERRED_REPO =
            "\n"
                    + "<repo:repository\n"
                    + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                    + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                    + "    <remotePackage path=\"mypackage;foo\">\n"
                    + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                    + "        <revision><major>%1$d</major></revision>\n"
                    + "        <display-name>%2$s</display-name>\n"
                    + "        <archives>\n"
                    + "            <archive>\n"
                    + "                <complete>\n"
                    + "                    <size>2345</size>\n"
                    + "                    <checksum>e1b7e62ecc3925054900b70e6eb9038bba8f702a</checksum>\n"
                    + "                    <url>%3$s</url>\n"
                    + "                </complete>\n"
                    + "            </archive>\n"
                    + "        </archives>\n"
                    + "    </remotePackage>\n"
                    + "</repo:repository>\n"
                    + "\n";

    public void testLocalPreferred() throws Exception {
        RepositorySource httpSource =
                new SimpleRepositorySource(
                        "http://www.example.com",
                        "HTTP Source UI Name",
                        true,
                        ImmutableSet.of(RepoManager.getGenericModule()),
                        new FakeRepositorySourceProvider(ImmutableList.of()));
        RepositorySource fileSource =
                new SimpleRepositorySource(
                        "file:///foo/bar",
                        "File Source UI Name",
                        true,
                        ImmutableSet.of(RepoManager.getGenericModule()),
                        new FakeRepositorySourceProvider(ImmutableList.of()));
        RepositorySource fileSource2 =
                new SimpleRepositorySource(
                        "file:///foo/bar2",
                        "File Source UI Name 2",
                        true,
                        ImmutableSet.of(RepoManager.getGenericModule()),
                        new FakeRepositorySourceProvider(ImmutableList.of()));
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RemoteRepoLoader loader =
                new RemoteRepoLoaderImpl(
                        ImmutableList.of(
                                new FakeRepositorySourceProvider(
                                        ImmutableList.of(httpSource, fileSource, fileSource2))),
                        null);

        // file preferred over url: relative paths
        downloader.registerUrl(new URL("http://www.example.com"),
                String.format(TEST_LOCAL_PREFERRED_REPO, 1, "http", "foo").getBytes());
        downloader.registerUrl(new URL("file:///foo/bar"),
                String.format(TEST_LOCAL_PREFERRED_REPO, 1, "file", "bar").getBytes());
        Map<String, RemotePackage> pkgs =
                loader.fetchPackages(progress, downloader, new FakeSettingsController(false));
        assertEquals("file", pkgs.get("mypackage;foo").getDisplayName());

        // file preferred over url: absolute paths
        downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(new URL("file:///foo/bar"),
                String.format(TEST_LOCAL_PREFERRED_REPO, 1, "http", "http://example.com").getBytes());
        downloader.registerUrl(new URL("file:///foo/bar2"),
                String.format(TEST_LOCAL_PREFERRED_REPO, 1, "file", "file:///foo/bar2").getBytes());
        pkgs = loader.fetchPackages(progress, downloader, new FakeSettingsController(false));
        assertEquals("file", pkgs.get("mypackage;foo").getDisplayName());

        // newer http preferred over file
        downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(new URL("http://www.example.com"),
                String.format(TEST_LOCAL_PREFERRED_REPO, 2, "http", "foo").getBytes());
        downloader.registerUrl(new URL("file:///foo/bar"),
                String.format(TEST_LOCAL_PREFERRED_REPO, 1, "file", "bar").getBytes());
        pkgs = loader.fetchPackages(progress, downloader, new FakeSettingsController(false));
        assertEquals("http", pkgs.get("mypackage;foo").getDisplayName());
    }

    public void testNewerFallbackPreferred() throws Exception {
        RepositorySource source =
                new SimpleRepositorySource(
                        "http://www.example.com",
                        "Source UI Name",
                        true,
                        ImmutableSet.of(
                                RepoManager.getCommonModule(), RepoManager.getGenericModule()),
                        null);
        final String legacyUrl = "http://www.example.com/legacy";
        RepositorySource legacySource =
                new SimpleRepositorySource(
                        legacyUrl,
                        "Legacy UI Name",
                        true,
                        ImmutableSet.of(
                                RepoManager.getCommonModule(), RepoManager.getGenericModule()),
                        null);
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(
                new URL("http://www.example.com"),
                getClass().getResourceAsStream("/testRepo2.xml"));
        downloader.registerUrl(new URL(legacyUrl),
                "foo".getBytes());
        FakeProgressIndicator progress = new FakeProgressIndicator(true);
        RemoteRepoLoader loader =
                new RemoteRepoLoaderImpl(
                        ImmutableList.of(
                                new FakeRepositorySourceProvider(
                                        ImmutableList.of(source, legacySource))),
                        (source1, downloader1, settings, progress1) -> {
                            assertEquals(legacyUrl, source1.getUrl());
                            FakeRemotePackage legacy = new FakeRemotePackage("mypackage;foo");
                            legacy.setRevision(new Revision(1, 2, 4));
                            legacy.setCompleteUrl("http://www.example.com/legacy.zip");
                            return ImmutableSet.of(legacy);
                        });
        Map<String, RemotePackage> pkgs = loader
                .fetchPackages(progress, downloader, new FakeSettingsController(false));
        progress.assertNoErrorsOrWarnings();
        assertEquals(2, pkgs.size());
        assertTrue(pkgs.get("mypackage;foo") instanceof FakePackage);
    }

    /** Create five sources and verify that they're processed in the right order */
    public void testRepoOrdering() throws Exception {
        List<RepositorySourceProvider> providers = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            SimpleRepositorySource source =
                    new SimpleRepositorySource(
                            "http://www.example.com/f" + i,
                            "Source UI Name",
                            true,
                            ImmutableSet.of(
                                    RepoManager.getCommonModule(), RepoManager.getGenericModule()),
                            Mockito.mock(RepositorySourceProvider.class));
            providers.add(new FakeRepositorySourceProvider(ImmutableList.of(source)));
        }
        String template =
                "<repo:repository\n"
                        + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "%s"
                        + "</repo:repository>\n";
        String packageTemplate =
                "    <remotePackage path=\"mypackage;foo%d\">\n"
                        + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                        + "        <revision><major>1</major></revision>\n"
                        + "        <display-name>%s</display-name>\n"
                        + "        <archives><archive><complete>\n"
                        + "                    <size>1234</size>\n"
                        + "                    <checksum type='sha-1'>123</checksum>\n"
                        + "                    <url>foo</url>\n"
                        + "        </complete></archive></archives>\n"
                        + "    </remotePackage>\n";

        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        for (int i = 1; i <= 5; i++) {
            String package1 = String.format(packageTemplate, i, "bad");
            String package2 = String.format(packageTemplate, i + 1, "good");
            downloader.registerUrl(
                    new URL("http://www.example.com/f" + i),
                    String.format(template, package1 + package2).getBytes());
        }
        FakeProgressIndicator progress = new FakeProgressIndicator(true);
        RemoteRepoLoader loader = new RemoteRepoLoaderImpl(providers, null);
        Map<String, RemotePackage> pkgs =
                loader.fetchPackages(progress, downloader, new FakeSettingsController(false));
        progress.assertNoErrorsOrWarnings();
        assertEquals(6, pkgs.size());

        for (int i = 2; i <= 6; i++) {
            assertEquals("good", pkgs.get("mypackage;foo" + i).getDisplayName());
        }
    }

    public void testDownloadsAreParallel() throws Exception {
        RepositorySource httpSource =
                new SimpleRepositorySource(
                        "http://www.example.com",
                        "HTTP Source UI Name",
                        true,
                        ImmutableSet.of(RepoManager.getGenericModule()),
                        null);
        RepositorySource httpSource2 =
                new SimpleRepositorySource(
                        "http://www.example2.com",
                        "HTTP Source2 UI Name",
                        true,
                        ImmutableSet.of(RepoManager.getGenericModule()),
                        null);
        RepositorySource fileSource =
                new SimpleRepositorySource(
                        "file:///foo/bar",
                        "File Source UI Name",
                        true,
                        ImmutableSet.of(RepoManager.getGenericModule()),
                        null);
        List<RepositorySource> sourceList = ImmutableList.of(httpSource, httpSource2, fileSource);
        FakeProgressIndicator progress = new FakeProgressIndicator(true);
        RemoteRepoLoader loader =
                new RemoteRepoLoaderImpl(
                        ImmutableList.of(new FakeRepositorySourceProvider(sourceList)), null);
        CyclicBarrier barrier = new CyclicBarrier(sourceList.size());
        FakeDownloader downloader =
                new FakeDownloader(new MockFileOp()) {
                    private void awaitBarrier() {
                        try {
                            barrier.await();
                        } catch (Exception e) {
                            fail(
                                    "Unexpected exception while waiting in download thread: "
                                            + e.getMessage());
                        }
                    }

                    @NonNull
                    @Override
                    public InputStream downloadAndStream(
                            @NonNull URL url, @NonNull ProgressIndicator indicator)
                            throws IOException {
                        InputStream stream = super.downloadAndStream(url, indicator);
                        awaitBarrier();
                        return stream;
                    }

                    @Override
                    public Path downloadFully(
                            @NonNull URL url, @NonNull ProgressIndicator indicator)
                            throws IOException {
                        Path path = super.downloadFully(url, indicator);
                        awaitBarrier();
                        return path;
                    }

                    @Override
                    public void downloadFully(
                            @NonNull URL url,
                            @NonNull Path target,
                            @Nullable Checksum checksum,
                            @NonNull ProgressIndicator indicator)
                            throws IOException {
                        super.downloadFully(url, target, checksum, indicator);
                        awaitBarrier();
                    }
                };

        downloader.registerUrl(
                new URL("http://www.example.com"), getClass().getResourceAsStream("/testRepo.xml"));
        downloader.registerUrl(
                new URL("http://www.example2.com"),
                getClass().getResourceAsStream("/testRepo.xml"));
        downloader.registerUrl(
                new URL("file:///foo/bar"), getClass().getResourceAsStream("/testRepo.xml"));

        Thread mainFetchThread =
                new Thread(
                        () -> {
                            try {
                                Map<String, RemotePackage> pkgs =
                                        loader.fetchPackages(
                                                progress,
                                                downloader,
                                                new FakeSettingsController(false));
                                // The same repo manifest comes from all sources, which just contains 2 packages and
                                // we sanity check it here. This test just verifies that the downloads are indeed parallel -
                                // the rest of the tests will therefore serve to verify the correctness of the concurrent
                                // data processing (i.e., assert other package details in various cases), in addition to
                                // the specific aspect of the implementation they are testing.
                                assertEquals(2, pkgs.size());
                            } catch (Throwable t) {
                                StringWriter stringWriter = new StringWriter();
                                PrintWriter printWriter = new PrintWriter(stringWriter);
                                t.printStackTrace(printWriter);
                                fail(
                                        "Exception in fetchPackages() thread: "
                                                + stringWriter.toString());
                            }
                        });
        mainFetchThread.start();
        mainFetchThread.join(60000);
        assertFalse(mainFetchThread.isAlive());
        assertFalse(mainFetchThread.isInterrupted());
    }
}
