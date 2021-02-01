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

import static com.android.repository.testframework.FakePackage.FakeLocalPackage;
import static com.android.repository.testframework.FakePackage.FakeRemotePackage;

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.api.SimpleRepositorySource;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakeLoader;
import com.android.repository.testframework.FakeProgressRunner;
import com.android.repository.testframework.FakeRepositorySourceProvider;
import com.android.repository.testframework.MockFileOp;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.TestCase;
import org.junit.Assert;

/**
 * Tests for {@link RepoManagerImpl}.
 */
public class RepoManagerImplTest extends TestCase {

    // test load with local and remote, fake loaders, callbacks called in order
    public void testLoadOperationsInOrder() {
        MockFileOp fop = new MockFileOp();
        final AtomicInteger counter = new AtomicInteger(0);
        RepoManagerImpl.LocalRepoLoaderFactory localFactory =
                new TestLoaderFactory<>(new OrderTestLoader<>(1, counter, false));
        RepoManager.RepoLoadedListener localCallback =
                packages -> assertEquals(2, counter.addAndGet(1));
        RepoManagerImpl.RemoteRepoLoaderFactory remoteFactory =
                new TestLoaderFactory<>(new OrderTestLoader<>(3, counter, false));
        RepoManager.RepoLoadedListener remoteCallback =
                packages -> assertEquals(4, counter.addAndGet(1));
        Runnable errorCallback = Assert::fail;

        RepoManagerImpl mgr = new RepoManagerImpl(localFactory, remoteFactory);
        mgr.setLocalPath(fop.toPath("/repo"));
        mgr.registerSourceProvider(new FakeRepositorySourceProvider(
                ImmutableList.of()));
        FakeProgressRunner runner = new FakeProgressRunner();
        mgr.loadSynchronously(
                0,
                ImmutableList.of(localCallback),
                ImmutableList.of(remoteCallback),
                ImmutableList.of(errorCallback),
                runner,
                new FakeDownloader(fop),
                null);

        assertEquals(4, counter.get());
    }

    // test error causes error callbacks to be called
    public void testErrorCallbacks1() {
        MockFileOp fop = new MockFileOp();
        final AtomicInteger counter = new AtomicInteger(0);
        RepoManagerImpl.LocalRepoLoaderFactory localFactory =
                new TestLoaderFactory<>(new OrderTestLoader<>(1, counter, false));
        RepoManager.RepoLoadedListener localCallback =
                packages -> assertEquals(2, counter.addAndGet(1));
        RepoManagerImpl.RemoteRepoLoaderFactory remoteFactory =
                new TestLoaderFactory<>(new OrderTestLoader<>(3, counter, true));
        RepoManager.RepoLoadedListener remoteCallback = packages -> fail();
        Runnable errorCallback = () -> assertEquals(4, counter.addAndGet(1));

        RepoManagerImpl mgr = new RepoManagerImpl(localFactory, remoteFactory);
        mgr.setLocalPath(fop.toPath("/repo"));
        mgr.registerSourceProvider(new FakeRepositorySourceProvider(
                ImmutableList.of()));
        FakeProgressRunner runner = new FakeProgressRunner();
        try {
            mgr.loadSynchronously(
                    0,
                    ImmutableList.of(localCallback),
                    ImmutableList.of(remoteCallback),
                    ImmutableList.of(errorCallback),
                    runner,
                    new FakeDownloader(fop),
                    null);
        } catch (Exception e) {
            // expected
        }
        assertEquals(4, counter.get());
    }

    // test error causes error callbacks to be called
    public void testErrorCallbacks2() {
        MockFileOp fop = new MockFileOp();
        final AtomicInteger counter = new AtomicInteger(0);
        RepoManagerImpl.LocalRepoLoaderFactory localFactory =
                new TestLoaderFactory<>(new OrderTestLoader<>(1, counter, true));
        RepoManager.RepoLoadedListener localCallback = packages -> fail();
        RepoManagerImpl.RemoteRepoLoaderFactory remoteFactory =
                new TestLoaderFactory<>(new OrderTestLoader<>(3, counter, false));
        RepoManager.RepoLoadedListener remoteCallback = packages -> fail();
        Runnable errorCallback = () -> assertEquals(2, counter.addAndGet(1));

        RepoManagerImpl mgr = new RepoManagerImpl(localFactory, remoteFactory);
        mgr.setLocalPath(fop.toPath("/repo"));
        mgr.registerSourceProvider(new FakeRepositorySourceProvider(
                ImmutableList.of()));
        FakeProgressRunner runner = new FakeProgressRunner();
        try {
            mgr.loadSynchronously(
                    0,
                    ImmutableList.of(localCallback),
                    ImmutableList.of(remoteCallback),
                    ImmutableList.of(errorCallback),
                    runner,
                    new FakeDownloader(fop),
                    null);
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
                new TestLoaderFactory<>(new FakeLoader<LocalPackage>() {
                    @Override
                    protected Map<String, LocalPackage> run() {
                        assertTrue(localStarted.compareAndSet(false, true));
                        try {
                            runLocal.acquire();
                        } catch (InterruptedException e) {
                            fail();
                        }
                        return new HashMap<>();
                    }
                });
        RepoManager.RepoLoadedListener localCallback1 = new RunningCallback(localCallback1Run);
        RepoManager.RepoLoadedListener localCallback2 = new RunningCallback(localCallback2Run);
        RepoManager.RepoLoadedListener remoteCallback1 = new RunningCallback(remoteCallback1Run) {
            @Override
            public void loaded(@NonNull RepositoryPackages packages) {
                super.loaded(packages);
                completeDone.release();
            }
        };
        RepoManager.RepoLoadedListener remoteCallback2 = new RunningCallback(remoteCallback2Run) {
            @Override
            public void loaded(@NonNull RepositoryPackages packages) {
                super.loaded(packages);
                completeDone.release();
            }
        };

        Runnable errorCallback = Assert::fail;

        RepoManagerImpl mgr = new RepoManagerImpl(localFactory, new TestLoaderFactory());
        mgr.setLocalPath(fop.toPath("/repo"));
        mgr.registerSourceProvider(new FakeRepositorySourceProvider(
                ImmutableList.of()));
        FakeProgressRunner runner = new FakeProgressRunner();
        mgr.load(
                0,
                ImmutableList.of(localCallback1),
                ImmutableList.of(remoteCallback1),
                ImmutableList.of(errorCallback),
                runner,
                new FakeDownloader(fop),
                null);
        mgr.load(
                0,
                ImmutableList.of(localCallback2),
                ImmutableList.of(remoteCallback2),
                ImmutableList.of(errorCallback),
                runner,
                new FakeDownloader(fop),
                null);
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
    public void testTimeout() {
        MockFileOp fop = new MockFileOp();
        final AtomicBoolean localDidRun = new AtomicBoolean(false);
        final AtomicBoolean remoteDidRun = new AtomicBoolean(false);

        TestLoaderFactory<LocalPackage> localRunningFactory = new TestLoaderFactory<>(
                new RunningLoader<LocalPackage>(localDidRun) {
                    @Override
                    public boolean needsUpdate(long lastLocalRefreshMs, boolean deepCheck) {
                        return false;
                    }
                });
        TestLoaderFactory<RemotePackage> remoteRunningFactory = new TestLoaderFactory<>(
                new RunningLoader<>(remoteDidRun));

        RepoManagerImpl mgr = new RepoManagerImpl(localRunningFactory, remoteRunningFactory);
        mgr.setLocalPath(fop.toPath("/repo"));
        mgr.registerSourceProvider(new FakeRepositorySourceProvider(
                ImmutableList.of()));
        FakeProgressRunner runner = new FakeProgressRunner();
        mgr.loadSynchronously(0, null, null, null, runner, null, null);
        assertTrue(localDidRun.compareAndSet(true, false));
        assertFalse(remoteDidRun.get());

        // we shouldn't run because of timeout
        mgr.loadSynchronously(
                RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner, null, null);

        assertFalse(localDidRun.get());
        assertFalse(remoteDidRun.get());

        // remote should run since we've specified a downloader
        mgr.loadSynchronously(
                RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
                null,
                null,
                null,
                runner,
                new FakeDownloader(fop),
                null);
        assertFalse(localDidRun.compareAndSet(true, false));
        assertTrue(remoteDidRun.compareAndSet(true, false));

        // now neither should run because of caching
        mgr.loadSynchronously(
                RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
                null,
                null,
                null,
                runner,
                new FakeDownloader(fop),
                null);
        assertFalse(localDidRun.get());
        assertFalse(remoteDidRun.get());

        // now we will timeout, so they should run again
        mgr.loadSynchronously(-1, null, null, null, runner, new FakeDownloader(fop), null);
        assertTrue(localDidRun.compareAndSet(true, false));
        assertTrue(remoteDidRun.compareAndSet(true, false));
    }

    // test that we do the local repo needsUpdate check correctly
    public void testCheckForNewPackages() {
        MockFileOp fop = new MockFileOp();
        AtomicBoolean didRun = new AtomicBoolean(false);
        final AtomicBoolean shallowResult = new AtomicBoolean(false);
        final AtomicBoolean deepResult = new AtomicBoolean(false);
        RunningLoader<LocalPackage> loader = new RunningLoader<LocalPackage>(didRun) {
            @Override
            public boolean needsUpdate(long lastLocalRefreshMs, boolean deepCheck) {
                return shallowResult.get() || (deepCheck && deepResult.get());
            }
        };

        RepoManager mgr = new RepoManagerImpl(new TestLoaderFactory<>(loader), null);
        mgr.setLocalPath(fop.toPath("/repo"));
        FakeProgressRunner runner = new FakeProgressRunner();

        // First time we should load, despite not being out of date
        mgr.loadSynchronously(
                RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner, null, null);
        assertTrue(didRun.compareAndSet(true, false));

        // With default timeout, we shouldn't run again
        mgr.loadSynchronously(
                RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner, null, null);
        assertFalse(didRun.get());

        // Now with shallow check, we should run
        shallowResult.set(true);
        mgr.loadSynchronously(
                RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner, null, null);
        assertTrue(didRun.compareAndSet(true, false));

        // With deep check only we shouldn't run
        shallowResult.set(false);
        deepResult.set(true);
        mgr.loadSynchronously(
                RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner, null, null);
        assertFalse(didRun.get());

        // now we do the deep check and should run.
        mgr.reloadLocalIfNeeded(runner.getProgressIndicator());
        assertTrue(didRun.compareAndSet(true, false));

        // check again that we won't reload because of caching
        shallowResult.set(false);
        deepResult.set(false);
        mgr.loadSynchronously(
                RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner, null, null);
        assertFalse(didRun.get());
    }

    // test local/remote change listeners
    public void testChangeListeners() {
        MockFileOp fop = new MockFileOp();
        final Map<String, LocalPackage> localPackages = new HashMap<>();
        FakeLoader<LocalPackage> localLoader = new FakeLoader<>(localPackages);
        localPackages.put("foo", new FakeLocalPackage("foo", fop));

        final Map<String, RemotePackage> remotePackages = Maps.newHashMap();
        FakeLoader<RemotePackage> remoteLoader = new FakeLoader<>(remotePackages);
        FakeRemotePackage remote = new FakeRemotePackage("foo");
        remote.setRevision(new Revision(2));
        remotePackages.put("foo", remote);

        TestLoaderFactory localFactory = new TestLoaderFactory<>(localLoader);
        TestLoaderFactory remoteFactory = new TestLoaderFactory<>(remoteLoader);
        RepoManager mgr = new RepoManagerImpl(localFactory, remoteFactory);
        mgr.setLocalPath(fop.toPath("/repo"));

        FakeProgressRunner runner = new FakeProgressRunner();
        FakeDownloader downloader = new FakeDownloader(fop);

        @SuppressWarnings("ConstantConditions")
        RepositorySourceProvider provider = new FakeRepositorySourceProvider(
                ImmutableList.of(
                        new SimpleRepositorySource("foo", "source", true,
                                ImmutableList.of(), null)));
        mgr.registerSourceProvider(provider);
        // Initial load to set current state
        mgr.loadSynchronously(-1, null, null, null, runner, downloader, null);
        AtomicBoolean localRan = new AtomicBoolean(false);
        AtomicBoolean remoteRan = new AtomicBoolean(false);
        mgr.addLocalChangeListener(new RunningCallback(localRan));
        mgr.addRemoteChangeListener(new RunningCallback(remoteRan));

        // load again with no changes
        mgr.loadSynchronously(-1, null, null, null, runner, downloader, null);
        assertFalse(localRan.get());
        assertFalse(remoteRan.get());

        // update local and ensure the local listener fired
        localPackages.put("bar", new FakeLocalPackage("bar", fop));
        mgr.loadSynchronously(-1, null, null, null, runner, downloader, null);
        assertTrue(localRan.compareAndSet(true, false));
        assertFalse(remoteRan.get());

        // update remote and ensure the remote listener fired
        remotePackages.put("baz", new FakeRemotePackage("baz"));
        mgr.loadSynchronously(-1, null, null, null, runner, downloader, null);
        assertFalse(localRan.get());
        assertTrue(remoteRan.compareAndSet(true, false));
    }

    private static class RunningLoader<T extends RepoPackage> extends FakeLoader<T> {

        private final AtomicBoolean mDidRun;

        public RunningLoader(AtomicBoolean didRun) {
            mDidRun = didRun;
        }

        @Override
        protected Map<String, T> run() {
            assertTrue(mDidRun.compareAndSet(false, true));
            return super.run();
        }
    }

    private static class RunningCallback implements RepoManager.RepoLoadedListener {

        private final AtomicBoolean mDidRun;

        private RunningCallback(AtomicBoolean didRun) {
            mDidRun = didRun;
        }

        @Override
        public void loaded(@NonNull RepositoryPackages packages) {
            assertTrue(mDidRun.compareAndSet(false, true));
        }
    }

    private static class TestLoaderFactory<T extends RepoPackage>
            implements RepoManagerImpl.RemoteRepoLoaderFactory,
            RepoManagerImpl.LocalRepoLoaderFactory {

        private final FakeLoader<T> mLoader;

        public TestLoaderFactory(FakeLoader<T> loader) {
            mLoader = loader;
        }

        public TestLoaderFactory() {
            mLoader = new FakeLoader<>();
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

    private static class OrderTestLoader<T extends RepoPackage> extends FakeLoader<T> {

        private final int mTarget;

        private final AtomicInteger mCounter;

        private final boolean mFail;

        private OrderTestLoader(int target, AtomicInteger counter, boolean fail) {
            mTarget = target;
            mCounter = counter;
            mFail = fail;
        }

        @Override
        protected Map<String, T> run() {
            assertEquals(mTarget, mCounter.addAndGet(1));
            if (mFail) {
                throw new RuntimeException("expected");
            }
            return new HashMap<>();
        }
    }
}
