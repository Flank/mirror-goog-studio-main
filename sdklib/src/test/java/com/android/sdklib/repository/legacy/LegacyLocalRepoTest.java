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

import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.Repository;
import com.android.repository.api.SchemaModule;
import com.android.repository.impl.manager.LocalRepoLoader;
import com.android.repository.impl.manager.LocalRepoLoaderImpl;
import com.android.repository.impl.meta.SchemaModuleUtil;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.OptionalLibrary;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.legacy.local.LocalSdk;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.meta.SdkCommonFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import junit.framework.TestCase;

/**
 * Tests parsing and rewriting legacy local packages.
 */
public class LegacyLocalRepoTest extends TestCase {

    public void testParseLegacy() throws URISyntaxException, FileNotFoundException {
        MockFileOp mockFop = new MockFileOp();
        mockFop.recordExistingFolder("/sdk/tools");
        mockFop.recordExistingFile("/sdk/tools/source.properties",
                "Pkg.License=Terms and Conditions\n" +
                        "Archive.Os=WINDOWS\n" +
                        "Pkg.Revision=22.3.4\n" +
                        "Platform.MinPlatformToolsRev=18\n" +
                        "Pkg.LicenseRef=android-sdk-license\n" +
                        "Archive.Arch=ANY\n" +
                        "Pkg.SourceUrl=https\\://example.com/repository-8.xml");
        mockFop.recordExistingFile("/sdk/tools/" + LocalSdk.androidCmdName(), "placeholder");
        mockFop.recordExistingFile("/sdk/tools/" + SdkConstants.FN_EMULATOR, "placeholder");

        File root = new File("/sdk");
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RepoManager mgr =
                new AndroidSdkHandler(root, null, mockFop).getSdkManager(progress);
        progress.assertNoErrorsOrWarnings();

        LocalRepoLoader sdk = new LocalRepoLoaderImpl(root, mgr,
                                                      new LegacyLocalRepoLoader(root, mockFop), mockFop);
        Map<String, LocalPackage> packages = sdk.getPackages(progress);
        progress.assertNoErrorsOrWarnings();
        assertEquals(1, packages.size());
        LocalPackage local = packages.get("tools");
        assertTrue(local.getPath().startsWith(SdkConstants.FD_TOOLS));
        assertEquals("Terms and Conditions", local.getLicense().getValue());
        assertEquals(new Revision(22, 3, 4), local.getVersion());
    }

    public void testRewriteLegacyTools() throws Exception {
        MockFileOp mockFop = new MockFileOp();
        mockFop.recordExistingFolder("/sdk/tools");
        mockFop.recordExistingFile("/sdk/tools/source.properties",
                "Pkg.License=Terms and Conditions\n" +
                        "Archive.Os=WINDOWS\n" +
                        "Pkg.Revision=22.3\n" +
                        "Platform.MinPlatformToolsRev=18\n" +
                        "Pkg.LicenseRef=android-sdk-license\n" +
                        "Archive.Arch=ANY\n" +
                        "Pkg.SourceUrl=https\\://example.com/repository-8.xml");

        LocalPackage local = loadLocalPackage(mockFop, "/sdk", "tools/package.xml");

        assertTrue(local.getPath().startsWith(SdkConstants.FD_TOOLS));
        assertEquals("Terms and Conditions", local.getLicense().getValue());
        int[] revision = local.getVersion().toIntArray(false);
        assertEquals(3, revision.length);
        assertEquals(22, revision[0]);
        assertEquals(3, revision[1]);
        assertEquals(0, revision[2]);
    }

    public void testRewriteLegacyAddon() throws Exception {
        MockFileOp mockFop = new MockFileOp();
        recordLegacyGoogleApis23(mockFop);

        LocalPackage local = loadLocalPackage(mockFop, "/sdk", "add-ons/addon-google_apis-google-23/package.xml");
        SdkCommonFactory factory = AndroidSdkHandler.getCommonModule().createLatestFactory();

        assertTrue(local.getPath().startsWith(SdkConstants.FD_ADDONS));
        assertEquals(new Revision(1, 0, 0), local.getVersion());
        TypeDetails typeDetails = local.getTypeDetails();
        assertTrue(typeDetails instanceof DetailsTypes.AddonDetailsType);
        DetailsTypes.AddonDetailsType details = (DetailsTypes.AddonDetailsType) typeDetails;
        Set<OptionalLibrary> desired =
                Sets.newHashSet(
                        factory.createLibraryType(
                                "com.google.android.maps",
                                "maps.jar",
                                "API for Google Maps",
                                new File("/sdk/add-ons/addon-google_apis-google-23/"),
                                false),
                        factory.createLibraryType(
                                "com.android.future.usb.accessory",
                                "usb.jar",
                                "API for USB Accessories",
                                new File("/sdk/add-ons/addon-google_apis-google-23/"),
                                false),
                        factory.createLibraryType(
                                "com.google.android.media.effects",
                                "effects.jar",
                                "Collection of video effects",
                                new File("/sdk/add-ons/addon-google_apis-google-23/"),
                                false));

        Set<OptionalLibrary> libraries = Sets.newHashSet(details.getLibraries().getLibrary());
        assertEquals(desired, libraries);

    }

    public void testRewriteLegacyAddonWithMinimalSourceProperties() throws Exception {
        MockFileOp mockFop = new MockFileOp();
        mockFop.recordExistingFile("/sdk/add-ons/addon-google_apis-google-23/source.properties",
                                   "AndroidVersion.ApiLevel=23\n"
                                   + "Pkg.Desc=Android + Google APIs\n"
                                   + "Pkg.Revision=1\n");
        mockFop.recordExistingFile("/sdk/add-ons/addon-google_apis-google-23/manifest.ini",
                                   "name=Google APIs Test\n"
                                   + "name-id=google_apis\n"
                                   + "vendor=Google Inc.\n"
                                   + "vendor-id=google\n"
                                   + "description=Android + Google APIs\n");

        LocalPackage local = loadLocalPackage(mockFop, "/sdk", "add-ons/addon-google_apis-google-23/package.xml");

        assertTrue(local.getPath().startsWith(SdkConstants.FD_ADDONS));
        assertThat(local.getDisplayName()).startsWith("Google APIs Test,");
    }

    public void testRewriteLegacyAddonGetNameFromManifest() throws Exception {
        MockFileOp mockFop = new MockFileOp();
        mockFop.recordExistingFile("/sdk/add-ons/addon-google_apis-google-23/source.properties",
                                   "AndroidVersion.ApiLevel=23\n"
                                   + "Pkg.Desc=Android + Google APIs\n"
                                   + "Pkg.Revision=1\n");
        mockFop.recordExistingFile("/sdk/add-ons/addon-google_apis-google-23/manifest.ini",
                                   "name=Google Apis\n"
                                   + "vendor=Google Inc.\n"
                                   + "vendor-id=google\n"
                                   + "description=Android + Google APIs\n");

        LocalPackage local = loadLocalPackage(mockFop, "/sdk", "add-ons/addon-google_apis-google-23/package.xml");

        assertTrue(local.getPath().startsWith(SdkConstants.FD_ADDONS));
        assertThat(local.getDisplayName()).startsWith("Google Apis,");
    }

    public void testRewriteLegacyAddonGetNameIdFromManifest() throws Exception {
        MockFileOp mockFop = new MockFileOp();
        mockFop.recordExistingFile("/sdk/add-ons/addon-google_apis-google-23/source.properties",
                                   "AndroidVersion.ApiLevel=23\n"
                                   + "Pkg.Desc=Android + Google APIs\n"
                                   + "Pkg.Revision=1\n");
        mockFop.recordExistingFile("/sdk/add-ons/addon-google_apis-google-23/manifest.ini",
                                   "name-id=google_apis\n"
                                   + "vendor=Google Inc.\n"
                                   + "vendor-id=google\n"
                                   + "description=Android + Google APIs\n");

        LocalPackage local = loadLocalPackage(mockFop, "/sdk", "add-ons/addon-google_apis-google-23/package.xml");

        assertTrue(local.getPath().startsWith(SdkConstants.FD_ADDONS));
        assertThat(local.getDisplayName()).startsWith("Google Apis,");
    }

    private static void recordLegacyGoogleApis23(MockFileOp fop) {
        fop.recordExistingFile("/sdk/add-ons/addon-google_apis-google-23/source.properties",
                "Addon.NameDisplay=Google APIs\n"
                        + "Addon.NameId=google_apis\n"
                        + "Addon.VendorDisplay=Google Inc.\n"
                        + "Addon.VendorId=google\n"
                        + "AndroidVersion.ApiLevel=23\n"
                        + "Pkg.Desc=Android + Google APIs\n"
                        + "Pkg.Revision=1\n"
                        + "Pkg.SourceUrl=https\\://dl.google.com/android/repository/addon.xml\n");
        fop.recordExistingFile("/sdk/add-ons/addon-google_apis-google-23/manifest.ini",
                "name=Google APIs\n"
                        + "name-id=google_apis\n"
                        + "vendor=Google Inc.\n"
                        + "vendor-id=google\n"
                        + "description=Android + Google APIs\n"
                        + "\n"
                        + "# version of the Android platform on which this add-on is built.\n"
                        + "api=23\n"
                        + "\n"
                        + "# revision of the add-on\n"
                        + "revision=1\n"
                        + "\n"
                        + "# list of libraries, separated by a semi-colon.\n"
                        + "libraries=com.google.android.maps;com.android.future.usb.accessory;com.google.android.media.effects\n"
                        + "\n"
                        + "# details for each library\n"
                        + "com.google.android.maps=maps.jar;API for Google Maps\n"
                        + "com.android.future.usb.accessory=usb.jar;API for USB Accessories\n"
                        + "com.google.android.media.effects=effects.jar;Collection of video effects\n"
                        + "\n"
                        + "SystemImage.GpuSupport=true\n");
        fop.recordExistingFile("/sdk/add-ons/addon-google_apis-google-23/libs/effects.jar");
        fop.recordExistingFile("/sdk/add-ons/addon-google_apis-google-23/libs/maps.jar");
        fop.recordExistingFile("/sdk/add-ons/addon-google_apis-google-23/libs/usb.jar");
    }

    private static LocalPackage loadLocalPackage(MockFileOp mockFop, String rootPath, String packagePath) throws Exception {
        FakeProgressIndicator progress = new FakeProgressIndicator();
        File root = new File(rootPath);
        RepoManager mgr = new AndroidSdkHandler(root, null, mockFop).getSdkManager(progress);

        progress.assertNoErrorsOrWarnings();

        Collection<SchemaModule<?>> extensions = ImmutableList
          .of(RepoManager.getCommonModule(), RepoManager.getGenericModule(), AndroidSdkHandler.getAddonModule());

        // Now read the new package
        Repository repo =
                (Repository)
                        SchemaModuleUtil.unmarshal(
                                mockFop.newFileInputStream(new File(rootPath, packagePath)),
                                extensions,
                                true,
                                progress);
        progress.assertNoErrorsOrWarnings();
        LocalPackage local = repo.getLocalPackage();
        local.setInstalledPath(mgr.getLocalPath());

        return local;
    }
}
