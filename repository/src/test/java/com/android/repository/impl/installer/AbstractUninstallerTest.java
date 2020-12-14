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

import static com.android.repository.testframework.FakePackage.FakeLocalPackage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.testframework.MockFileOp;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Tests for {@link AbstractUninstaller}
 */
public class AbstractUninstallerTest {
    @Test
    public void uninstallerProperties() {
        MockFileOp fop = new MockFileOp();
        RepoManager mgr = new RepoManagerImpl();
        mgr.setLocalPath(fop.toPath("/sdk"));
        LocalPackage local = new FakeLocalPackage("foo;bar", fop);
        AbstractUninstaller uninstaller = new TestUninstaller(local, mgr);
        assertSame(uninstaller.getPackage(), local);
        assertEquals(uninstaller.getName(), String.format("Uninstall %1$s (revision: %2$s)",
                local.getDisplayName(),
                local.getVersion().toString()));
    }

    private static class TestUninstaller extends AbstractUninstaller {

        public TestUninstaller(@NonNull LocalPackage p, @NonNull RepoManager manager) {
            super(p, manager);
        }

        @Override
        protected boolean doComplete(
                @Nullable Path installTemp, @NonNull ProgressIndicator progress) {
            return true;
        }

        @Override
        protected boolean doPrepare(
                @NonNull Path installTempPath, @NonNull ProgressIndicator progress) {
            return true;
        }
    }
}

