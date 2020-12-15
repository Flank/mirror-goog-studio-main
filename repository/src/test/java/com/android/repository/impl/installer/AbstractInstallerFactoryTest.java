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

package com.android.repository.impl.installer;

import static com.android.testutils.file.InMemoryFileSystems.createInMemoryFileSystem;
import static com.android.testutils.file.InMemoryFileSystems.getPlatformSpecificPath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Downloader;
import com.android.repository.api.Installer;
import com.android.repository.api.InstallerFactory;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.Uninstaller;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Tests for {@link AbstractInstallerFactory}
 */
public class AbstractInstallerFactoryTest {

    @Test
    public void fallbackFactory() {
        InstallerFactory testFactory =
                new TestInstallerFactory() {
                    @Override
                    protected boolean canHandlePackage(
                            @NonNull RepoPackage pack, @NonNull RepoManager manager) {
                        return false;
                    }
                };
        testFactory.setFallbackFactory(
                new TestInstallerFactory() {
                    @NonNull
                    @Override
                    protected Installer doCreateInstaller(
                            @NonNull RemotePackage p,
                            @NonNull RepoManager mgr,
                            @NonNull Downloader downloader) {
                        Installer installer = Mockito.mock(Installer.class);
                        Mockito.when(installer.getName()).thenReturn("mock installer");
                        return installer;
                    }
                });

        assertEquals(
                "mock installer",
                testFactory
                        .createInstaller(
                                Mockito.mock(RemotePackage.class),
                                Mockito.mock(RepoManager.class),
                                Mockito.mock(Downloader.class))
                        .getName());
    }

    @Test
    public void installerFallback() {
        InstallerFactory testFactory =
                new TestInstallerFactory() {
                    @NonNull
                    @Override
                    protected Installer doCreateInstaller(
                            @NonNull RemotePackage p,
                            @NonNull RepoManager mgr,
                            @NonNull Downloader downloader) {
                        return new AbstractInstaller(p, mgr, downloader) {
                            @Override
                            protected boolean doPrepare(
                                    @NonNull Path installTempPath,
                                    @NonNull ProgressIndicator progress) {
                                return false;
                            }

                            @Override
                            protected boolean doComplete(
                                    @Nullable Path installTemp,
                                    @NonNull ProgressIndicator progress) {
                                return false;
                            }
                        };
                    }
                };

        InstallerFactory fallbackFactory =
                new TestInstallerFactory() {
                    @NonNull
                    @Override
                    protected Installer doCreateInstaller(
                            @NonNull RemotePackage p,
                            @NonNull RepoManager mgr,
                            @NonNull Downloader downloader) {
                        Installer installer = Mockito.mock(Installer.class);
                        Mockito.when(installer.getName()).thenReturn("fallback installer");
                        return installer;
                    }
                };

        testFactory.setFallbackFactory(fallbackFactory);

        Installer installer =
                testFactory.createInstaller(
                        Mockito.mock(RemotePackage.class),
                        Mockito.mock(RepoManager.class),
                        Mockito.mock(Downloader.class));
        assertEquals("fallback installer", installer.getFallbackOperation().getName());
    }

    @Test
    public void installerListeners() {
        InstallerFactory factory =
                new TestInstallerFactory() {
                    @NonNull
                    @Override
                    protected Installer doCreateInstaller(
                            @NonNull RemotePackage p,
                            @NonNull RepoManager mgr,
                            @NonNull Downloader downloader) {
                        return new AbstractInstaller(p, mgr, downloader) {
                            @Override
                            protected boolean doPrepare(
                                    @NonNull Path installTempPath,
                                    @NonNull ProgressIndicator progress) {
                                return true;
                            }

                            @Override
                            protected boolean doComplete(
                                    @Nullable Path installTemp,
                                    @NonNull ProgressIndicator progress) {
                                return true;
                            }
                        };
                    }
                };
        AtomicBoolean didPrepare = new AtomicBoolean(false);
        AtomicBoolean didComplete = new AtomicBoolean(false);
        AtomicReference<Installer> installer = new AtomicReference<>();

        final List<PackageOperation.StatusChangeListener> listeners = ImmutableList.of(
                (op, progress) -> {
                    assertEquals(installer.get(), op);
                    if (op.getInstallStatus() == PackageOperation.InstallStatus.COMPLETE) {
                        didComplete.compareAndSet(false, true);
                    }
                    if (op.getInstallStatus() == PackageOperation.InstallStatus.PREPARED) {
                        didPrepare.compareAndSet(false, true);
                    }
                }
        );
        factory.setListenerFactory(p -> listeners);
        installer.set(
                factory.createInstaller(
                        new FakePackage.FakeRemotePackage("foo"),
                        new FakeRepoManager(
                                createInMemoryFileSystem().getPath(getPlatformSpecificPath("/sdk")),
                                new RepositoryPackages()),
                        Mockito.mock(Downloader.class)));

        FakeProgressIndicator progress = new FakeProgressIndicator();
        installer.get().prepare(progress);
        assertTrue(didPrepare.get());
        assertFalse(didComplete.get());

        installer.get().complete(progress);
        assertTrue(didComplete.get());
    }

    @Test
    public void addInstallerListenersToFallback() {

        InstallerFactory testFactory = new TestInstallerFactory();

        InstallerFactory mockFallbackFactory = Mockito.mock(InstallerFactory.class);
        InstallerFactory.StatusChangeListenerFactory mockListenerFactory = Mockito.mock(InstallerFactory.StatusChangeListenerFactory.class);

        testFactory.setFallbackFactory(mockFallbackFactory);
        testFactory.setListenerFactory(mockListenerFactory);

        Mockito.verify(mockFallbackFactory).setListenerFactory(mockListenerFactory);
    }

    @Test
    public void addInstallerListenersToFallbackWhenSettingFallback() {

        InstallerFactory testFactory = new TestInstallerFactory();

        InstallerFactory mockFallbackFactory = Mockito.mock(InstallerFactory.class);
        InstallerFactory.StatusChangeListenerFactory mockListenerFactory = Mockito.mock(InstallerFactory.StatusChangeListenerFactory.class);

        testFactory.setListenerFactory(mockListenerFactory);
        testFactory.setFallbackFactory(mockFallbackFactory);

        Mockito.verify(mockFallbackFactory).setListenerFactory(mockListenerFactory);
    }

    private static class TestInstallerFactory extends AbstractInstallerFactory {

        @NonNull
        @Override
        protected Installer doCreateInstaller(
                @NonNull RemotePackage p,
                @NonNull RepoManager mgr,
                @NonNull Downloader downloader) {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        protected Uninstaller doCreateUninstaller(
                @NonNull LocalPackage p, @NonNull RepoManager mgr) {
            throw new UnsupportedOperationException();
        }
    }
}
