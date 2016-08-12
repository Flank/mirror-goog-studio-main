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

package com.android.repository.impl.manager;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.Downloader;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.api.SettingsController;
import com.android.repository.api.SimpleRepositorySource;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressRunner;
import com.android.repository.testframework.FakeRepositorySourceProvider;
import com.android.repository.testframework.MockFileOp;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * Tests for {@link RepoManagerImpl}.
 */
public class RepoManagerImplTest extends TestCase {

    // test load with local and remote, dummy loaders, callbacks called in order
    public void testLoadOperationsInOrder() throws Exception {
        MockFileOp fop = new MockFileOp();
        final AtomicInteger counter = new AtomicInteger(0);
        RepoManagerImpl.LocalRepoLoaderFactory localFactory =
                new TestLoaderFactory(new OrderTestLoader(1, counter, false));
        RepoManager.RepoLoadedCallback localCallback =
                packages -> assertEquals(2, counter.addAndGet(1));
        RepoManagerImpl.RemoteRepoLoaderFactory remoteFactory =
                new TestLoaderFactory(new OrderTestLoader(3, counter, false));
        RepoManager.RepoLoadedCallback remoteCallback =
                packages -> assertEquals(4, counter.addAndGet(1));
        Runnable errorCallback = Assert::fail;

        RepoManagerImpl mgr = new RepoManagerImpl(fop, localFactory, remoteFactory);
        mgr.setLocalPath(new File("/repo"));
        mgr.registerSourceProvider(new FakeRepositorySourceProvider(
                ImmutableList.of()));
        FakeProgressRunner runner = new FakeProgressRunner();
        mgr.load(0, ImmutableList.of(localCallback), ImmutableList.of(remoteCallback),
                ImmutableList.of(errorCallback), runner, new FakeDownloader(fop), null, true);

        assertEquals(4, counter.get());
    }

    // test error causes error callbacks to be called
    public void testErrorCallbacks1() throws Exception {
        MockFileOp fop = new MockFileOp();
        final AtomicInteger counter = new AtomicInteger(0);
        RepoManagerImpl.LocalRepoLoaderFactory localFactory =
                new TestLoaderFactory(new OrderTestLoader(1, counter, false));
        RepoManager.RepoLoadedCallback localCallback =
                packages -> assertEquals(2, counter.addAndGet(1));
        RepoManagerImpl.RemoteRepoLoaderFactory remoteFactory =
                new TestLoaderFactory(new OrderTestLoader(3, counter, true));
        RepoManager.RepoLoadedCallback remoteCallback = packages -> fail();
        Runnable errorCallback = () -> assertEquals(4, counter.addAndGet(1));

        RepoManagerImpl mgr = new RepoManagerImpl(fop, localFactory, remoteFactory);
        mgr.setLocalPath(new File("/repo"));
        mgr.registerSourceProvider(new FakeRepositorySourceProvider(
                ImmutableList.of()));
        FakeProgressRunner runner = new FakeProgressRunner();
        try {
            mgr.load(0, ImmutableList.of(localCallback), ImmutableList.of(remoteCallback),
                    ImmutableList.of(errorCallback), runner, new FakeDownloader(fop), null, true);
        } catch (Exception e) {
            // expected
        }
        assertEquals(4, counter.get());

    }

    // test error causes error callbacks to be called
    public void testErrorCallbacks2() throws Exception {
        MockFileOp fop = new MockFileOp();
        final AtomicInteger counter = new AtomicInteger(0);
        RepoManagerImpl.LocalRepoLoaderFactory localFactory =
                new TestLoaderFactory(new OrderTestLoader(1, counter, true));
        RepoManager.RepoLoadedCallback localCallback = packages -> fail();
        RepoManagerImpl.RemoteRepoLoaderFactory remoteFactory =
                new TestLoaderFactory(new OrderTestLoader(3, counter, false));
        RepoManager.RepoLoadedCallback remoteCallback = packages -> fail();
        Runnable errorCallback = () -> assertEquals(2, counter.addAndGet(1));

        RepoManagerImpl mgr = new RepoManagerImpl(fop, localFactory, remoteFactory);
        mgr.setLocalPath(new File("/repo"));
        mgr.registerSourceProvider(new FakeRepositorySourceProvider(
                ImmutableList.of()));
        FakeProgressRunner runner = new FakeProgressRunner();
        try {
            mgr.load(0, ImmutableList.of(localCallback), ImmutableList.of(remoteCallback),
                    ImmutableList.of(errorCallback), runner, new FakeDownloader(fop), null, true);
        } catch (Exception e) {
            // expected
        }
        assertEquals(2, counter.get());
    }

    // test multiple loads at same time only kick off one load, and callbacks are invoked
    public void testMultiLoad() throws Exception {
        MockFileOp fop = new MockFileOp();
        final AtomicBoolean localStarted = new AtomicBoolean(false);
        AtomicBoolean localCallback1Run = new AtomicBoolean(false);
        AtomicBoolean localCallback2Run = new AtomicBoolean(false);
        AtomicBoolean remoteCallback1Run = new AtomicBoolean(false);
        AtomicBoolean remoteCallback2Run = new AtomicBoolean(false);
        final Semaphore runLocal = new Semaphore(1);
        runLocal.acquire();
        final Semaphore completeDone = new Semaphore(2);
        completeDone.acquire(2);

        RepoManagerImpl.LocalRepoLoaderFactory localFactory =
                new TestLoaderFactory(new DummyLoader() {
                    @Override
                    protected Map run() {
                        assertTrue(localStarted.compareAndSet(false, true));
                        try {
                            runLocal.acquire();
                        } catch (InterruptedException e) {
                            fail();
                        }
                        return Maps.newHashMap();
                    }
                });
        RepoManager.RepoLoadedCallback localCallback1 = new RunningCallback(localCallback1Run);
        RepoManager.RepoLoadedCallback localCallback2 = new RunningCallback(localCallback2Run);
        RepoManager.RepoLoadedCallback remoteCallback1 = new RunningCallback(remoteCallback1Run) {
            @Override
            public void doRun(@NonNull RepositoryPackages packages) {
                super.doRun(packages);
                completeDone.release();
            }
        };
        RepoManager.RepoLoadedCallback remoteCallback2 = new RunningCallback(remoteCallback2Run) {
            @Override
            public void doRun(@NonNull RepositoryPackages packages) {
                super.doRun(packages);
                completeDone.release();
            }
        };

        Runnable errorCallback = Assert::fail;

        RepoManagerImpl mgr = new RepoManagerImpl(fop, localFactory, new TestLoaderFactory());
        mgr.setLocalPath(new File("/repo"));
        mgr.registerSourceProvider(new FakeRepositorySourceProvider(
                ImmutableList.of()));
        FakeProgressRunner runner = new FakeProgressRunner();
        mgr.load(0, ImmutableList.of(localCallback1), ImmutableList.of(remoteCallback1),
                ImmutableList.of(errorCallback), runner, new FakeDownloader(fop), null, false);
        mgr.load(0, ImmutableList.of(localCallback2), ImmutableList.of(remoteCallback2),
                ImmutableList.of(errorCallback), runner, new FakeDownloader(fop), null, false);
        runLocal.release();

        if (!completeDone.tryAcquire(2, 10, TimeUnit.SECONDS)) {
            fail();
        }
        assertTrue(localCallback1Run.get());
        assertTrue(localCallback2Run.get());
        assertTrue(remoteCallback1Run.get());
        assertTrue(remoteCallback2Run.get());
    }

    // test timeout makes/doesn't make load happen
    public void testTimeout() throws Exception {
        MockFileOp fop = new MockFileOp();
        final AtomicBoolean localDidRun = new AtomicBoolean(false);
        final AtomicBoolean remoteDidRun = new AtomicBoolean(false);

        TestLoaderFactory localRunningFactory = new TestLoaderFactory(
                new RunningLoader(localDidRun) {
                    @Override
                    public boolean needsUpdate(long lastLocalRefreshMs, boolean deepCheck) {
                        return false;
                    }
                });
        TestLoaderFactory remoteRunningFactory = new TestLoaderFactory(
                new RunningLoader(remoteDidRun));

        RepoManagerImpl mgr = new RepoManagerImpl(fop, localRunningFactory, remoteRunningFactory);
        mgr.setLocalPath(new File("/repo"));
        mgr.registerSourceProvider(new FakeRepositorySourceProvider(
                ImmutableList.of()));
        FakeProgressRunner runner = new FakeProgressRunner();
        mgr.load(0, null, null, null, runner, null, null, true);
        assertTrue(localDidRun.compareAndSet(true, false));
        assertFalse(remoteDidRun.get());

        // we shouldn't run because of timeout
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner, null, null,
                true);

        assertFalse(localDidRun.get());
        assertFalse(remoteDidRun.get());

        // remote should run since we've specified a downloader
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner,
                new FakeDownloader(fop), null, true);
        assertFalse(localDidRun.compareAndSet(true, false));
        assertTrue(remoteDidRun.compareAndSet(true, false));

        // now neither should run because of caching
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner,
                new FakeDownloader(fop), null, true);
        assertFalse(localDidRun.get());
        assertFalse(remoteDidRun.get());

        // now we will timeout, so they should run again
        mgr.load(-1, null, null, null, runner, new FakeDownloader(fop), null, true);
        assertTrue(localDidRun.compareAndSet(true, false));
        assertTrue(remoteDidRun.compareAndSet(true, false));
    }

    // test that we do the local repo needsUpdate check correctly
    public void testCheckForNewPackages() throws Exception {
        MockFileOp fop = new MockFileOp();
        AtomicBoolean didRun = new AtomicBoolean(false);
        final AtomicBoolean shallowResult = new AtomicBoolean(false);
        final AtomicBoolean deepResult = new AtomicBoolean(false);
        RunningLoader loader = new RunningLoader(didRun) {
            @Override
            public boolean needsUpdate(long lastLocalRefreshMs, boolean deepCheck) {
                return shallowResult.get() || (deepCheck && deepResult.get());
            }
        };

        RepoManager mgr = new RepoManagerImpl(fop, new TestLoaderFactory(loader), null);
        mgr.setLocalPath(new File("/repo"));
        FakeProgressRunner runner = new FakeProgressRunner();

        // First time we should load, despite not being out of date
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner, null, null,
                true);
        assertTrue(didRun.compareAndSet(true, false));

        // With default timeout, we shouldn't run again
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner, null, null,
                true);
        assertFalse(didRun.get());

        // Now with shallow check, we should run
        shallowResult.set(true);
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner, null, null,
                true);
        assertTrue(didRun.compareAndSet(true, false));

        // With deep check only we shouldn't run
        shallowResult.set(false);
        deepResult.set(true);
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner, null, null,
                true);
        assertFalse(didRun.get());

        // now we do the deep check and should run.
        mgr.reloadLocalIfNeeded(runner.getProgressIndicator());
        assertTrue(didRun.compareAndSet(true, false));

        // check again that we won't reload because of caching
        shallowResult.set(false);
        deepResult.set(false);
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner, null,
                null, true);
        assertFalse(didRun.get());
    }

    // test local/remote change listeners
    public void testChangeListeners() throws Exception {
        MockFileOp fop = new MockFileOp();
        final Map<String, LocalPackage> localPackages = Maps.newHashMap();
        DummyLoader localLoader = new DummyLoader(localPackages);
        localPackages.put("foo", new FakePackage("foo", new Revision(1), null));

        final Map<String, RemotePackage> remotePackages = Maps.newHashMap();
        DummyLoader remoteLoader = new DummyLoader(remotePackages);
        remotePackages.put("foo", new FakePackage("foo", new Revision(2), null));

        TestLoaderFactory localFactory = new TestLoaderFactory(localLoader);
        TestLoaderFactory remoteFactory = new TestLoaderFactory(remoteLoader);
        RepoManager mgr = new RepoManagerImpl(fop, localFactory, remoteFactory);
        mgr.setLocalPath(new File("/repo"));

        FakeProgressRunner runner = new FakeProgressRunner();
        FakeDownloader downloader = new FakeDownloader(fop);

        @SuppressWarnings("ConstantConditions")
        RepositorySourceProvider provider = new FakeRepositorySourceProvider(
                ImmutableList.of(
                        new SimpleRepositorySource("foo", "source", true,
                                ImmutableList.of(), null)));
        mgr.registerSourceProvider(provider);
        // Initial load to set current state
        mgr.load(-1, null, null, null, runner, downloader, null, true);
        AtomicBoolean localRan = new AtomicBoolean(false);
        AtomicBoolean remoteRan = new AtomicBoolean(false);
        mgr.registerLocalChangeListener(new RunningCallback(localRan));
        mgr.registerRemoteChangeListener(new RunningCallback(remoteRan));

        // load again with no changes
        mgr.load(-1, null, null, null, runner, downloader, null, true);
        assertFalse(localRan.get());
        assertFalse(remoteRan.get());

        // update local and ensure the local listener fired
        localPackages.put("bar", new FakePackage("bar", new Revision(1), null));
        mgr.load(-1, null, null, null, runner, downloader, null, true);
        assertTrue(localRan.compareAndSet(true, false));
        assertFalse(remoteRan.get());

        // update remote and ensure the remote listener fired
        remotePackages.put("baz", new FakePackage("baz", new Revision(1), null));
        mgr.load(-1, null, null, null, runner, downloader, null, true);
        assertFalse(localRan.get());
        assertTrue(remoteRan.compareAndSet(true, false));
    }

    private static class RunningLoader extends DummyLoader {

        private final AtomicBoolean mDidRun;

        public RunningLoader(AtomicBoolean didRun) {
            mDidRun = didRun;
        }

        @Override
        protected Map run() {
            assertTrue(mDidRun.compareAndSet(false, true));
            return super.run();
        }
    }

    private static class RunningCallback implements RepoManager.RepoLoadedCallback {

        private final AtomicBoolean mDidRun;

        private RunningCallback(AtomicBoolean didRun) {
            mDidRun = didRun;
        }

        @Override
        public void doRun(@NonNull RepositoryPackages packages) {
            assertTrue(mDidRun.compareAndSet(false, true));
        }
    }

    private static class DummyLoader implements LocalRepoLoader, RemoteRepoLoader {

        private final Map<String, ? extends RepoPackage> mPackages;

        public DummyLoader() {
            mPackages = new HashMap<>();
        }

        public DummyLoader(@NonNull Map<String, ? extends RepoPackage> packages) {
            mPackages = packages;
        }

        @NonNull
        @Override
        public Map<String, LocalPackage> getPackages(@NonNull ProgressIndicator progress) {
            //noinspection unchecked
            return run();
        }

        @Override
        public boolean needsUpdate(long lastLocalRefreshMs, boolean deepCheck) {
            return true;
        }

        @NonNull
        @Override
        public Map<String, RemotePackage> fetchPackages(@NonNull ProgressIndicator progress,
                @NonNull Downloader downloader, @Nullable SettingsController settings) {
            //noinspection unchecked
            return run();
        }

        protected Map run() {
            return mPackages;
        }
    }

    private static class TestLoaderFactory implements RepoManagerImpl.RemoteRepoLoaderFactory,
            RepoManagerImpl.LocalRepoLoaderFactory {

        private final DummyLoader mLoader;

        public TestLoaderFactory(DummyLoader loader) {
            mLoader = loader;
        }

        public TestLoaderFactory() {
            mLoader = new DummyLoader();
        }

        @Override
        @NonNull
        public RemoteRepoLoader createRemoteRepoLoader(@NonNull ProgressIndicator progress) {
            return mLoader;
        }

        @Override
        @NonNull
        public LocalRepoLoader createLocalRepoLoader() {
            return mLoader;
        }
    }

    private static class OrderTestLoader extends DummyLoader {

        private final int mTarget;

        private final AtomicInteger mCounter;

        private final boolean mFail;

        private OrderTestLoader(int target, AtomicInteger counter, boolean fail) {
            mTarget = target;
            mCounter = counter;
            mFail = fail;
        }

        @Override
        protected Map run() {
            assertEquals(mTarget, mCounter.addAndGet(1));
            if (mFail) {
                throw new RuntimeException("expected");
            }
            return Maps.newHashMap();
        }
    }
}
