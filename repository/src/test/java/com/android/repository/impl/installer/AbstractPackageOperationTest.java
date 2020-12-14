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

import static com.android.repository.api.PackageOperation.InstallStatus;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Downloader;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.MockFileOp;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * Tests for AbstractPackageOperation.
 * Note that some functionality is covered by {@code PatchInstallerFactoryTest}.
 */

public class AbstractPackageOperationTest {

    /**
     * Run two installs simultaneously and verify that each operation only happens once.
     * Also verify that logs are appropriately added to both ProgressIndicators.
     */
    @Test
    public void resumeOperation() throws Exception {
        MockFileOp fop = new MockFileOp();
        RepositoryPackages packages = new RepositoryPackages();
        FakePackage.FakeRemotePackage remotePackage = new FakePackage.FakeRemotePackage("foo");
        RepoManager mgr = new FakeRepoManager(fop.toPath("/sdk"), packages);
        AtomicReference<PackageOperation.InstallStatus> status = new AtomicReference<>(
                InstallStatus.NOT_STARTED);
        TestOperation op = new TestOperation(remotePackage, mgr, new FakeDownloader(fop), status);
        op.registerStateChangeListener((operation, progress) -> {
            if (operation.getInstallStatus() == InstallStatus.PREPARED) {
                assertTrue(status.compareAndSet(InstallStatus.PREPARING,
                        InstallStatus.PREPARED));
            }
            if (operation.getInstallStatus() == InstallStatus.COMPLETE) {
                assertTrue(status.compareAndSet(InstallStatus.RUNNING,
                        InstallStatus.COMPLETE));
            }
        });

        CompletableFuture<Void> prepareFinished1 = new CompletableFuture<>();
        CompletableFuture<Void> prepareFinished2 = new CompletableFuture<>();
        FakeProgressIndicator progress1 = new FakeProgressIndicator();
        Thread t1 = new Thread(() -> {
            assertTrue(op.prepare(progress1));
            prepareFinished1.complete(null);
            assertTrue(op.complete(progress1));
        });
        FakeProgressIndicator progress2 = new FakeProgressIndicator();
        Thread t2 = new Thread(() -> {
            assertTrue(op.prepare(progress2));
            prepareFinished2.complete(null);
            assertTrue(op.complete(progress2));
        });
        t1.start();
        t2.start();
        prepareFinished1.get();
        prepareFinished2.get();

        // Verify exactly one of the messages is recorded in each ProgressIndicator
        assertTrue(progress1.getInfos().contains("prepare: " + t1.getName()) ^
                progress1.getInfos().contains("prepare: " + t2.getName()));
        assertTrue(progress2.getInfos().contains("prepare: " + t1.getName()) ^
                progress2.getInfos().contains("prepare: " + t2.getName()));

        t1.join();
        t2.join();
        assertTrue(progress1.getInfos().contains("complete: " + t1.getName()) ^
                progress1.getInfos().contains("complete: " + t2.getName()));
        assertTrue(progress2.getInfos().contains("complete: " + t1.getName()) ^
                progress2.getInfos().contains("complete: " + t2.getName()));

        assertEquals(InstallStatus.COMPLETE, status.get());

    }

    private static class TestOperation extends AbstractInstaller {

        private final AtomicReference<PackageOperation.InstallStatus> mStatus;

        public TestOperation(
                @NonNull RemotePackage p,
                @NonNull RepoManager manager,
                @NonNull Downloader downloader,
                @NonNull AtomicReference<InstallStatus> status) {
            super(p, manager, downloader);
            mStatus = status;
        }

        @Override
        protected boolean doPrepare(
                @NonNull Path installTempPath, @NonNull ProgressIndicator progress) {
            try {
                // Allow time for the other thread to invoke prepare()
                Thread.sleep(500);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            assertTrue(mStatus.compareAndSet(InstallStatus.NOT_STARTED, InstallStatus.PREPARING));
            progress.logInfo("prepare: " + Thread.currentThread().getName());
            return true;
        }

        @Override
        protected boolean doComplete(
                @Nullable Path installTemp, @NonNull ProgressIndicator progress) {
            try {
                // Allow time for the other thread to invoke complete()
                Thread.sleep(500);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            assertTrue(mStatus.compareAndSet(InstallStatus.PREPARED, InstallStatus.RUNNING));
            progress.logInfo("complete: " + Thread.currentThread().getName());
            return true;
        }
    }
}
