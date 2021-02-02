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
package com.android.sdklib.repository.legacy;

import com.android.repository.Revision;
import com.android.repository.api.Channel;
import com.android.repository.api.ConstantSourceProvider;
import com.android.repository.api.RepoManager;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeProgressRunner;
import com.android.repository.testframework.FakeSettingsController;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.google.common.collect.ImmutableList;
import java.net.URL;
import java.util.Map;
import junit.framework.TestCase;

/**
 * Tests for {@link LegacyRemoteRepoLoader}.
 */
public class LegacyRemoteTest extends TestCase {

    public static final String ANDROID_FOLDER = "/android-home";

    public void testLegacyRemoteSdk() throws Exception {
        MockFileOp fop = new MockFileOp();
        final AndroidSdkHandler handler =
                new AndroidSdkHandler(null, fop.toPath(ANDROID_FOLDER), fop);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RepoManager mgr = handler.getSdkManager(progress);
        progress.assertNoErrorsOrWarnings();
        mgr.getSourceProviders().clear();
        progress.assertNoErrorsOrWarnings();

        mgr.registerSourceProvider(
                new ConstantSourceProvider("http://www.example.com/testRepo", "Repo",
                        ImmutableList.of(AndroidSdkHandler.getRepositoryModule(),
                                RepoManager.getGenericModule())));
        mgr.registerSourceProvider(
                new ConstantSourceProvider("http://www.example.com/testRepo2", "Repo2",
                        ImmutableList.of(AndroidSdkHandler.getRepositoryModule(),
                                RepoManager.getGenericModule())));
        progress.assertNoErrorsOrWarnings();

        FakeSettingsController settings = new FakeSettingsController(false);
        LegacyRemoteRepoLoader sdk = new LegacyRemoteRepoLoader();
        mgr.setFallbackRemoteRepoLoader(sdk);
        FakeDownloader downloader = new FakeDownloader(fop);
        downloader.registerUrl(
                new URL("http://www.example.com/testRepo2"),
                getClass().getResourceAsStream("/repository2-1_sample.xml"));
        downloader.registerUrl(new URL("http://www.example.com/testRepo"),
                getClass().getResourceAsStream("/repository_sample_10.xml"));
        FakeProgressRunner runner = new FakeProgressRunner();

        mgr.loadSynchronously(
                0,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                runner,
                downloader,
                settings);
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        RepositoryPackages packages = mgr.getPackages();

        Map<String, UpdatablePackage> consolidatedPkgs = packages.getConsolidatedPkgs();
        assertEquals(12, consolidatedPkgs.size());
        assertEquals(12, packages.getNewPkgs().size());

        settings.setChannel(Channel.create(1));
        mgr.markInvalid();
        mgr.loadSynchronously(
                0,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                runner,
                downloader,
                settings);
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        packages = mgr.getPackages();

        consolidatedPkgs = packages.getConsolidatedPkgs();
        assertEquals(14, consolidatedPkgs.size());
        UpdatablePackage doc = consolidatedPkgs.get("docs");
        assertEquals(new Revision(43), doc.getRemote().getVersion());
        UpdatablePackage pastry = consolidatedPkgs.get("platforms;android-Pastry");
        TypeDetails pastryDetails = pastry.getRepresentative().getTypeDetails();
        assertTrue(pastryDetails instanceof DetailsTypes.PlatformDetailsType);
        DetailsTypes.PlatformDetailsType platformDetails
                = (DetailsTypes.PlatformDetailsType) pastryDetails;
        assertEquals(5, platformDetails.getApiLevel());
        assertEquals("Pastry", platformDetails.getCodename());
        assertEquals(1, platformDetails.getLayoutlib().getApi());

        // TODO: more specific checks
    }
}
