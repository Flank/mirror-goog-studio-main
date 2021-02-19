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
package com.android.repository.impl.sources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import com.android.annotations.NonNull;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.api.SchemaModule;
import com.android.repository.api.SettingsController;
import com.android.repository.api.SimpleRepositorySource;
import com.android.repository.impl.manager.RemoteRepoLoader;
import com.android.repository.impl.manager.RemoteRepoLoaderImpl;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.FakeRepositorySourceProvider;
import com.android.repository.testframework.FakeSettingsController;
import com.android.repository.testframework.MockFileOp;
import com.android.testutils.file.InMemoryFileSystems;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/** Tests for {@link LocalSourceProvider} */
public class LocalSourceProviderTest {
    @Test
    public void loadSources() throws Exception {
        FileSystem fs = InMemoryFileSystems.createInMemoryFileSystem();
        InMemoryFileSystems.recordExistingFile(
                fs.getPath(InMemoryFileSystems.getPlatformSpecificPath("/sources")),
                "#A comment"
                        + "enabled00=true\n"
                        + "enabled01=false\n"
                        + "disp00=the display name\n"
                        + "src00=http\\://example.com/foo\n"
                        + "src01=http\\://example.com/foo2\n"
                        + "count=2");
        LocalSourceProvider provider =
                new LocalSourceProvider(
                        fs.getPath(InMemoryFileSystems.getPlatformSpecificPath("/sources")),
                        ImmutableList.of(RepoManager.getGenericModule()));
        provider.setRepoManager(new FakeRepoManager(new RepositoryPackages()));
        Iterator<RepositorySource> sources =
                provider.getSources(null, new FakeProgressIndicator(), false).iterator();
        RepositorySource source = sources.next();
        assertTrue(source.isEnabled());
        assertEquals("the display name", source.getDisplayName());
        assertEquals("http://example.com/foo", source.getUrl());

        source = sources.next();
        assertFalse(source.isEnabled());
        assertNull(source.getDisplayName());
        assertEquals("http://example.com/foo2", source.getUrl());
        assertFalse(sources.hasNext());
    }

    @Test
    public void configFileDoesntExistDoesntWarn() {
        FileSystem fs = InMemoryFileSystems.createInMemoryFileSystem();
        LocalSourceProvider provider =
                new LocalSourceProvider(
                        fs.getPath(
                                InMemoryFileSystems.getPlatformSpecificPath("/doesntExist")),
                        ImmutableList.of(RepoManager.getGenericModule()));
        provider.setRepoManager(new FakeRepoManager(new RepositoryPackages()));
        FakeProgressIndicator logger = new FakeProgressIndicator();
        provider.getSources(null, logger, false).iterator();
        logger.assertNoErrorsOrWarnings();
    }

    @Test
    public void forceRefresh() {
        FileSystem fs = InMemoryFileSystems.createInMemoryFileSystem();
        Path sourcesPath =
                fs.getPath(InMemoryFileSystems.getPlatformSpecificPath("/sources"));
        InMemoryFileSystems.recordExistingFile(
                sourcesPath, "enabled00=true\n" + "src00=http\\://example.com/foo\n" + "count=1");
        LocalSourceProvider provider =
                new LocalSourceProvider(
                        sourcesPath, ImmutableList.of(RepoManager.getGenericModule()));
        provider.setRepoManager(new FakeRepoManager(new RepositoryPackages()));
        List<RepositorySource> sources =
                provider.getSources(null, new FakeProgressIndicator(), false);
        assertEquals(1, sources.size());

        InMemoryFileSystems.recordExistingFile(
                sourcesPath,
                "enabled00=true\n"
                        + "enabled01=false\n"
                        + "src00=http\\://example.com/foo\n"
                        + "src01=http\\://example.com/foo2\n"
                        + "count=2");
        sources = provider.getSources(null, new FakeProgressIndicator(), false);
        assertEquals(1, sources.size());
        sources = provider.getSources(null, new FakeProgressIndicator(), true);
        assertEquals(2, sources.size());
    }

    @Test
    public void modifySources() throws Exception {
        FileSystem fs = InMemoryFileSystems.createInMemoryFileSystem();
        Path file = fs.getPath(InMemoryFileSystems.getPlatformSpecificPath("/sources"));
        LocalSourceProvider provider =
                new LocalSourceProvider(file, ImmutableList.of(RepoManager.getGenericModule()));
        provider.setRepoManager(new FakeRepoManager(new RepositoryPackages()));
        FakeProgressIndicator progress = new FakeProgressIndicator();
        provider.getSources(null, progress, false);
        provider.addSource(
                new SimpleRepositorySource(
                        "http://example.com/foo",
                        "my display name",
                        true,
                        ImmutableList.of(RepoManager.getGenericModule()),
                        provider));
        assertFalse(Files.exists(file));
        provider.save(progress);
        Properties expected = new Properties();
        expected.setProperty("enabled00", "true");
        expected.setProperty("disp00", "my display name");
        expected.setProperty("src00", "http://example.com/foo");
        expected.setProperty("count", "1");

        Properties actual = new Properties();
        actual.load(Files.newInputStream(file));
        assertEquals(expected, actual);
        SimpleRepositorySource source2 =
                new SimpleRepositorySource(
                        "http://example.com/foo2",
                        "disp 2",
                        false,
                        ImmutableList.of(RepoManager.getGenericModule()),
                        provider);
        provider.addSource(source2);
        actual.clear();
        actual.load(Files.newInputStream(file));
        assertEquals(expected, actual);
        provider.save(progress);
        Properties expected2 = new Properties();
        expected2.putAll(expected);
        expected2.setProperty("enabled01", "false");
        expected2.setProperty("disp01", "disp 2");
        expected2.setProperty("src01", "http://example.com/foo2");
        expected2.setProperty("count", "2");
        actual.clear();
        actual.load(Files.newInputStream(file));
        assertEquals(expected2, actual);

        assertTrue(provider.removeSource(source2));
        assertFalse(provider.removeSource(source2));
        provider.save(progress);
        actual.clear();
        actual.load(Files.newInputStream(file));
        assertEquals(expected, actual);
    }

    @Test
    public void allowedModules() {
        @SuppressWarnings("unchecked")
        SchemaModule<Object> fakeSchema = mock(SchemaModule.class);

        FileSystem fs = InMemoryFileSystems.createInMemoryFileSystem();
        Path sourcesPath =
                fs.getPath(InMemoryFileSystems.getPlatformSpecificPath("/sources"));
        InMemoryFileSystems.recordExistingFile(
                sourcesPath,
                "#A comment"
                        + "enabled00=true\n"
                        + "enabled01=false\n"
                        + "disp00=the display name\n"
                        + "src00=http\\://example.com/foo\n"
                        + "src01=http\\://example.com/foo2\n"
                        + "count=2");
        ImmutableList<SchemaModule<?>> modules = ImmutableList.of(fakeSchema);
        LocalSourceProvider provider = new LocalSourceProvider(sourcesPath, modules);
        provider.setRepoManager(new FakeRepoManager(new RepositoryPackages()));
        List<RepositorySource> sources =
                provider.getSources(null, new FakeProgressIndicator(), false);
        assertEquals(modules, sources.iterator().next().getPermittedModules());
    }

    @Test
    public void addSourceDuringLoad() throws Exception {
        CompletableFuture<Boolean> loadStarted = new CompletableFuture<>();
        CompletableFuture<Boolean> sourceAdded = new CompletableFuture<>();
        RepositorySource source =
                new SimpleRepositorySource(
                        "http://www.example.com",
                        "Source UI Name",
                        true,
                        ImmutableSet.of(RepoManager.getGenericModule()),
                        mock(RepositorySourceProvider.class)) {
                    @NonNull
                    @Override
                    public String getUrl() {
                        loadStarted.complete(true);
                        try {
                            sourceAdded.get(5, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            fail();
                        }
                        return super.getUrl();
                    }
                };
        RepositorySource source2 = mock(RepositorySource.class);
        FileSystem fs = InMemoryFileSystems.createInMemoryFileSystem();
        LocalSourceProvider provider =
                new LocalSourceProvider(
                        fs.getPath(InMemoryFileSystems.getPlatformSpecificPath("/sources")),
                        ImmutableList.of());
        provider.setRepoManager(mock(RepoManager.class));
        provider.getSources(null, new FakeProgressIndicator(), false);
        provider.addSource(source);
        provider.addSource(source2);
        // We don't actually need the urls to be registered for this test
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        RemoteRepoLoader loader =
                new RemoteRepoLoaderImpl(
                        ImmutableList.of(
                                new FakeRepositorySourceProvider(
                                        ImmutableList.of(source, source2))),
                        null);

        SettingsController settings = new FakeSettingsController(false);
        Runnable runnable =
                () -> loader.fetchPackages(new FakeProgressIndicator(), downloader, settings);
        Thread t = new Thread(runnable);
        t.start();
        loadStarted.get(5, TimeUnit.SECONDS);
        provider.addSource(mock(RepositorySource.class));
        sourceAdded.complete(true);
        t.join();
    }
}
