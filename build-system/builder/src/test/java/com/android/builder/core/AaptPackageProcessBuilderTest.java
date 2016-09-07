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

package com.android.builder.core;

import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidLibrary;
import com.android.ide.common.process.ProcessInfo;
import com.android.repository.Revision;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.targets.AndroidTargetManager;
import com.android.testutils.TestUtils;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;

import org.junit.Ignore;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link AaptPackageProcessBuilder} class
 */
// TODO: This test is failing on the tools continuous integration bot, as it can't find the
// proper SDK components (e.g. "Test requires android-21"). Ignoring this test for now to restore
// green tests but this test should be restored ASAP.
@Ignore
public class AaptPackageProcessBuilderTest extends TestCase {

    @Mock
    AaptOptions mAaptOptions;

    BuildToolInfo mBuildToolInfo;
    IAndroidTarget mIAndroidTarget;

    ILogger mLogger = new StdLogger(StdLogger.Level.VERBOSE);

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        AndroidSdkHandler handler = AndroidSdkHandler.getInstance(TestUtils.getSdk());
        mBuildToolInfo = handler.getLatestBuildTool(progress, false);
        if (mBuildToolInfo == null) {
            throw new RuntimeException("Test requires build-tools 21");
        }
        AndroidTargetManager targetManager = handler.getAndroidTargetManager(progress);
        for (IAndroidTarget iAndroidTarget : targetManager.getTargets(progress)) {
            if (iAndroidTarget.getVersion().getApiLevel() == 21) {
                mIAndroidTarget = iAndroidTarget;
            }
        }
        if (mIAndroidTarget == null) {
            throw new RuntimeException("Test requires android-21");
        }
    }

    public void testAndroidManifestPackaging() {
        File virtualAndroidManifestFile = new File("/path/to/non/existent/file");

        AaptPackageProcessBuilder aaptPackageProcessBuilder =
                new AaptPackageProcessBuilder(virtualAndroidManifestFile, mAaptOptions);
        aaptPackageProcessBuilder.setResPackageOutput("/path/to/non/existent/dir");

        ProcessInfo processInfo = aaptPackageProcessBuilder
                .build(mBuildToolInfo, mIAndroidTarget, mLogger);

        List<String> command = processInfo.getArgs();

        assertTrue(virtualAndroidManifestFile.getAbsolutePath().equals(
                command.get(command.indexOf("-M") + 1)));
        assertTrue("/path/to/non/existent/dir".equals(command.get(command.indexOf("-F") + 1)));
        assertTrue(command.get(command.indexOf("-I") + 1).contains("android.jar"));
    }

    public void testResourcesPackaging() {
        File virtualAndroidManifestFile = new File("/path/to/non/existent/file");
        File assetsFolder = Mockito.mock(File.class);
        Mockito.when(assetsFolder.isDirectory()).thenReturn(true);
        Mockito.when(assetsFolder.getAbsolutePath()).thenReturn("/path/to/assets/folder");
        File resFolder = Mockito.mock(File.class);
        Mockito.when(resFolder.isDirectory()).thenReturn(true);
        Mockito.when(resFolder.getAbsolutePath()).thenReturn("/path/to/res/folder");

        AaptPackageProcessBuilder aaptPackageProcessBuilder =
                new AaptPackageProcessBuilder(virtualAndroidManifestFile, mAaptOptions);
        aaptPackageProcessBuilder.setResPackageOutput("/path/to/non/existent/dir")
                .setAssetsFolder(assetsFolder)
                .setResFolder(resFolder)
                .setPackageForR("com.example.package.forR")
                .setSourceOutputDir("path/to/source/output/dir")
                .setLibraries(ImmutableList.of(Mockito.mock(AndroidLibrary.class)))
                .setType(VariantType.DEFAULT);

        ProcessInfo processInfo = aaptPackageProcessBuilder
                .build(mBuildToolInfo, mIAndroidTarget, mLogger);

        List<String> command = processInfo.getArgs();

        assertTrue(virtualAndroidManifestFile.getAbsolutePath().equals(
                command.get(command.indexOf("-M") + 1)));
        assertTrue("/path/to/non/existent/dir".equals(command.get(command.indexOf("-F") + 1)));
        assertTrue(command.get(command.indexOf("-I") + 1).contains("android.jar"));
        assertTrue("/path/to/res/folder".equals(command.get(command.indexOf("-S") + 1)));
        assertTrue("/path/to/assets/folder".equals(command.get(command.indexOf("-A") + 1)));
        assertTrue("path/to/source/output/dir".equals(command.get(command.indexOf("-J") + 1)));
        assertTrue("com.example.package.forR".equals(command.get(command.indexOf("--custom-package") + 1)));

        assertTrue(command.indexOf("-f") != -1);
        assertTrue(command.indexOf("--no-crunch") != -1);
        assertTrue(command.indexOf("-0") != -1);
        assertTrue(command.indexOf("apk") != -1);
    }

    public void testResourcesPackagingForTest() {
        File virtualAndroidManifestFile = new File("/path/to/non/existent/file");
        File assetsFolder = Mockito.mock(File.class);
        Mockito.when(assetsFolder.isDirectory()).thenReturn(true);
        Mockito.when(assetsFolder.getAbsolutePath()).thenReturn("/path/to/assets/folder");
        File resFolder = Mockito.mock(File.class);
        Mockito.when(resFolder.isDirectory()).thenReturn(true);
        Mockito.when(resFolder.getAbsolutePath()).thenReturn("/path/to/res/folder");

        AaptPackageProcessBuilder aaptPackageProcessBuilder =
                new AaptPackageProcessBuilder(virtualAndroidManifestFile, mAaptOptions);
        aaptPackageProcessBuilder.setResPackageOutput("/path/to/non/existent/dir")
                .setAssetsFolder(assetsFolder)
                .setResFolder(resFolder)
                .setPackageForR("com.example.package.forR")
                .setSourceOutputDir("path/to/source/output/dir")
                .setLibraries(ImmutableList.of(Mockito.mock(AndroidLibrary.class)))
                .setType(VariantType.ANDROID_TEST);

        ProcessInfo processInfo = aaptPackageProcessBuilder
                .build(mBuildToolInfo, mIAndroidTarget, mLogger);

        List<String> command = processInfo.getArgs();

        assertTrue(virtualAndroidManifestFile.getAbsolutePath().equals(
                command.get(command.indexOf("-M") + 1)));
        assertTrue("/path/to/non/existent/dir".equals(command.get(command.indexOf("-F") + 1)));
        assertTrue(command.get(command.indexOf("-I") + 1).contains("android.jar"));
        assertTrue("/path/to/res/folder".equals(command.get(command.indexOf("-S") + 1)));
        assertTrue("/path/to/assets/folder".equals(command.get(command.indexOf("-A") + 1)));
        assertTrue("path/to/source/output/dir".equals(command.get(command.indexOf("-J") + 1)));
        assertTrue(command.indexOf("--custom-package") == -1);

        assertTrue(command.indexOf("-f") != -1);
        assertTrue(command.indexOf("--no-crunch") != -1);
        assertTrue(command.indexOf("-0") != -1);
        assertTrue(command.indexOf("apk") != -1);
    }

    public void testResourcesPackagingForLibrary() {
        File virtualAndroidManifestFile = new File("/path/to/non/existent/file");
        File assetsFolder = Mockito.mock(File.class);
        Mockito.when(assetsFolder.isDirectory()).thenReturn(true);
        Mockito.when(assetsFolder.getAbsolutePath()).thenReturn("/path/to/assets/folder");
        File resFolder = Mockito.mock(File.class);
        Mockito.when(resFolder.isDirectory()).thenReturn(true);
        Mockito.when(resFolder.getAbsolutePath()).thenReturn("/path/to/res/folder");

        AaptPackageProcessBuilder aaptPackageProcessBuilder =
                new AaptPackageProcessBuilder(virtualAndroidManifestFile, mAaptOptions);
        aaptPackageProcessBuilder.setResPackageOutput("/path/to/non/existent/dir")
                .setAssetsFolder(assetsFolder)
                .setResFolder(resFolder)
                .setPackageForR("com.example.package.forR")
                .setSourceOutputDir("path/to/source/output/dir")
                .setLibraries(ImmutableList.of(Mockito.mock(AndroidLibrary.class)))
                .setType(VariantType.LIBRARY);

        ProcessInfo processInfo = aaptPackageProcessBuilder
                .build(mBuildToolInfo, mIAndroidTarget, mLogger);

        List<String> command = processInfo.getArgs();

        assertTrue(virtualAndroidManifestFile.getAbsolutePath().equals(
                command.get(command.indexOf("-M") + 1)));
        assertTrue("/path/to/non/existent/dir".equals(command.get(command.indexOf("-F") + 1)));
        assertTrue(command.get(command.indexOf("-I") + 1).contains("android.jar"));
        assertTrue("/path/to/res/folder".equals(command.get(command.indexOf("-S") + 1)));
        assertTrue("/path/to/assets/folder".equals(command.get(command.indexOf("-A") + 1)));
        assertTrue("path/to/source/output/dir".equals(command.get(command.indexOf("-J") + 1)));

        assertTrue(command.indexOf("--non-constant-id") != -1);

        assertTrue(command.indexOf("-f") != -1);
        assertTrue(command.indexOf("--no-crunch") != -1);
        assertTrue(command.indexOf("-0") != -1);
        assertTrue(command.indexOf("apk") != -1);
    }


    public void testSplitResourcesPackaging() {
        File virtualAndroidManifestFile = new File("/path/to/non/existent/file");
        File assetsFolder = Mockito.mock(File.class);
        Mockito.when(assetsFolder.isDirectory()).thenReturn(true);
        Mockito.when(assetsFolder.getAbsolutePath()).thenReturn("/path/to/assets/folder");
        File resFolder = Mockito.mock(File.class);
        Mockito.when(resFolder.isDirectory()).thenReturn(true);
        Mockito.when(resFolder.getAbsolutePath()).thenReturn("/path/to/res/folder");

        AaptPackageProcessBuilder aaptPackageProcessBuilder =
                new AaptPackageProcessBuilder(virtualAndroidManifestFile, mAaptOptions);
        aaptPackageProcessBuilder.setResPackageOutput("/path/to/non/existent/dir")
                .setAssetsFolder(assetsFolder)
                .setResFolder(resFolder)
                .setPackageForR("com.example.package.forR")
                .setSourceOutputDir("path/to/source/output/dir")
                .setLibraries(ImmutableList.of(Mockito.mock(AndroidLibrary.class)))
                .setType(VariantType.DEFAULT)
                .setSplits(ImmutableList.of("mdpi", "hdpi"));

        ProcessInfo processInfo = aaptPackageProcessBuilder
                .build(mBuildToolInfo, mIAndroidTarget, mLogger);

        List<String> command = processInfo.getArgs();

        assertTrue(virtualAndroidManifestFile.getAbsolutePath().equals(
                command.get(command.indexOf("-M") + 1)));
        assertTrue("/path/to/non/existent/dir".equals(command.get(command.indexOf("-F") + 1)));
        assertTrue(command.get(command.indexOf("-I") + 1).contains("android.jar"));
        assertTrue("/path/to/res/folder".equals(command.get(command.indexOf("-S") + 1)));
        assertTrue("/path/to/assets/folder".equals(command.get(command.indexOf("-A") + 1)));
        assertTrue("path/to/source/output/dir".equals(command.get(command.indexOf("-J") + 1)));
        assertTrue("com.example.package.forR".equals(command.get(command.indexOf("--custom-package") + 1)));

        assertTrue(command.indexOf("-f") != -1);
        assertTrue(command.indexOf("--no-crunch") != -1);
        assertTrue(command.indexOf("-0") != -1);
        assertTrue(command.indexOf("apk") != -1);

        assertTrue("--split".equals(command.get(command.indexOf("mdpi") - 1)));
        assertTrue("--split".equals(command.get(command.indexOf("hdpi") - 1)));
        assertTrue(command.indexOf("--preferred-density") == -1);
    }

    public void testPre21ResourceConfigsAndPreferredDensity() {
        File virtualAndroidManifestFile = new File("/path/to/non/existent/file");
        File assetsFolder = Mockito.mock(File.class);
        Mockito.when(assetsFolder.isDirectory()).thenReturn(true);
        Mockito.when(assetsFolder.getAbsolutePath()).thenReturn("/path/to/assets/folder");
        File resFolder = Mockito.mock(File.class);
        Mockito.when(resFolder.isDirectory()).thenReturn(true);
        Mockito.when(resFolder.getAbsolutePath()).thenReturn("/path/to/res/folder");

        AaptPackageProcessBuilder aaptPackageProcessBuilder =
                new AaptPackageProcessBuilder(virtualAndroidManifestFile, mAaptOptions);
        aaptPackageProcessBuilder.setResPackageOutput("/path/to/non/existent/dir")
                .setAssetsFolder(assetsFolder)
                .setResFolder(resFolder)
                .setPackageForR("com.example.package.forR")
                .setSourceOutputDir("path/to/source/output/dir")
                .setLibraries(ImmutableList.of(Mockito.mock(AndroidLibrary.class)))
                .setType(VariantType.DEFAULT)
                .setResourceConfigs(ImmutableList.of("res1", "res2"))
                .setPreferredDensity("xhdpi");


        FakeProgressIndicator progress = new FakeProgressIndicator();
        BuildToolInfo buildToolInfo = getBuildToolInfo(new Revision(20, 0, 0));
        IAndroidTarget androidTarget = null;
        for (IAndroidTarget iAndroidTarget : getTargets()) {
            if (iAndroidTarget.getVersion().getApiLevel() < 20) {
                androidTarget = iAndroidTarget;
                break;
            }
        }
        if (androidTarget == null) {
            throw new RuntimeException("Test requires pre android-20");
        }

        ProcessInfo processInfo = aaptPackageProcessBuilder
                .build(buildToolInfo, androidTarget, mLogger);

        List<String> command = processInfo.getArgs();

        assertTrue("res1,res2,xhdpi,nodpi".equals(command.get(command.indexOf("-c") + 1)));
        assertTrue(command.indexOf("--preferred-density") == -1);
    }

    public void testPost21ResourceConfigsAndPreferredDensity() {
        File virtualAndroidManifestFile = new File("/path/to/non/existent/file");
        File assetsFolder = Mockito.mock(File.class);
        Mockito.when(assetsFolder.isDirectory()).thenReturn(true);
        Mockito.when(assetsFolder.getAbsolutePath()).thenReturn("/path/to/assets/folder");
        File resFolder = Mockito.mock(File.class);
        Mockito.when(resFolder.isDirectory()).thenReturn(true);
        Mockito.when(resFolder.getAbsolutePath()).thenReturn("/path/to/res/folder");

        AaptPackageProcessBuilder aaptPackageProcessBuilder =
                new AaptPackageProcessBuilder(virtualAndroidManifestFile, mAaptOptions);
        aaptPackageProcessBuilder.setResPackageOutput("/path/to/non/existent/dir")
                .setAssetsFolder(assetsFolder)
                .setResFolder(resFolder)
                .setPackageForR("com.example.package.forR")
                .setSourceOutputDir("path/to/source/output/dir")
                .setLibraries(ImmutableList.of(Mockito.mock(AndroidLibrary.class)))
                .setType(VariantType.DEFAULT)
                .setResourceConfigs(ImmutableList.of("res1", "res2"))
                .setPreferredDensity("xhdpi");


        BuildToolInfo buildToolInfo = getBuildToolInfo(new Revision(21, 0, 0));
        IAndroidTarget androidTarget = null;
        for (IAndroidTarget iAndroidTarget : getTargets()) {
            if (iAndroidTarget.getVersion().getApiLevel() < 21) {
                androidTarget = iAndroidTarget;
                break;
            }
        }
        if (androidTarget == null) {
            throw new RuntimeException("Test requires pre android-21");
        }

        ProcessInfo processInfo = aaptPackageProcessBuilder
                .build(buildToolInfo, androidTarget, mLogger);

        List<String> command = processInfo.getArgs();

        assertEquals("res1,res2", command.get(command.indexOf("-c") + 1));
        assertEquals("xhdpi", command.get(command.indexOf("--preferred-density") + 1));
    }

    public void testResConfigAndSplitConflict() {
        File virtualAndroidManifestFile = new File("/path/to/non/existent/file");
        File assetsFolder = Mockito.mock(File.class);
        Mockito.when(assetsFolder.isDirectory()).thenReturn(true);
        Mockito.when(assetsFolder.getAbsolutePath()).thenReturn("/path/to/assets/folder");
        File resFolder = Mockito.mock(File.class);
        Mockito.when(resFolder.isDirectory()).thenReturn(true);
        Mockito.when(resFolder.getAbsolutePath()).thenReturn("/path/to/res/folder");

        AaptPackageProcessBuilder aaptPackageProcessBuilder =
                new AaptPackageProcessBuilder(virtualAndroidManifestFile, mAaptOptions);
        aaptPackageProcessBuilder.setResPackageOutput("/path/to/non/existent/dir")
                .setAssetsFolder(assetsFolder)
                .setResFolder(resFolder)
                .setPackageForR("com.example.package.forR")
                .setSourceOutputDir("path/to/source/output/dir")
                .setLibraries(ImmutableList.of(Mockito.mock(AndroidLibrary.class)))
                .setType(VariantType.DEFAULT)
                .setResourceConfigs(
                        ImmutableList.of("nodpi", "en", "fr", "mdpi", "hdpi", "xxhdpi", "xxxhdpi"))
                .setSplits(ImmutableList.of("xhdpi"))
                .setPreferredDensity("xhdpi");


        BuildToolInfo buildToolInfo = getBuildToolInfo(new Revision(21, 0, 0));
        IAndroidTarget androidTarget = null;
        for (IAndroidTarget iAndroidTarget : getTargets()) {
            if (iAndroidTarget.getVersion().getApiLevel() < 21) {
                androidTarget = iAndroidTarget;
                break;
            }
        }
        if (androidTarget == null) {
            throw new RuntimeException("Test requires pre android-21");
        }

        try {
            aaptPackageProcessBuilder.build(buildToolInfo, androidTarget, mLogger);
        } catch(Exception expected) {
            assertEquals("Splits for densities \"xhdpi\" were configured, yet the resConfigs settings does not include such splits. The resulting split APKs would be empty.\n"
                    + "Suggestion : exclude those splits in your build.gradle : \n"
                    + "splits {\n"
                    + "     density {\n"
                    + "         enable true\n"
                    + "         exclude \"xhdpi\"\n"
                    + "     }\n"
                    + "}\n"
                    + "OR add them to the resConfigs list.", expected.getMessage());
        }
    }

    public void testResConfigAndSplitConflict2() {
        File virtualAndroidManifestFile = new File("/path/to/non/existent/file");
        File assetsFolder = Mockito.mock(File.class);
        Mockito.when(assetsFolder.isDirectory()).thenReturn(true);
        Mockito.when(assetsFolder.getAbsolutePath()).thenReturn("/path/to/assets/folder");
        File resFolder = Mockito.mock(File.class);
        Mockito.when(resFolder.isDirectory()).thenReturn(true);
        Mockito.when(resFolder.getAbsolutePath()).thenReturn("/path/to/res/folder");

        AaptPackageProcessBuilder aaptPackageProcessBuilder =
                new AaptPackageProcessBuilder(virtualAndroidManifestFile, mAaptOptions);
        aaptPackageProcessBuilder.setResPackageOutput("/path/to/non/existent/dir")
                .setAssetsFolder(assetsFolder)
                .setResFolder(resFolder)
                .setPackageForR("com.example.package.forR")
                .setSourceOutputDir("path/to/source/output/dir")
                .setLibraries(ImmutableList.of(Mockito.mock(AndroidLibrary.class)))
                .setType(VariantType.DEFAULT)
                .setResourceConfigs(ImmutableList.of("xxxhdpi"))
                .setSplits(ImmutableList.of("hdpi", "mdpi", "xxhdpi"))
                .setPreferredDensity("xhdpi");


        BuildToolInfo buildToolInfo = getBuildToolInfo(new Revision(21, 0, 0));
        IAndroidTarget androidTarget = null;
        for (IAndroidTarget iAndroidTarget : getTargets()) {
            if (iAndroidTarget.getVersion().getApiLevel() < 21) {
                androidTarget = iAndroidTarget;
                break;
            }
        }
        if (androidTarget == null) {
            throw new RuntimeException("Test requires pre android-21");
        }

        try {
            aaptPackageProcessBuilder.build(buildToolInfo, androidTarget, mLogger);
        } catch(Exception expected) {
            assertEquals("Splits for densities \"hdpi,mdpi,xxhdpi\" were configured, yet the "
                    + "resConfigs settings does not include such splits. The resulting split APKs "
                    + "would be empty.\n"
                    + "Suggestion : exclude those splits in your build.gradle : \n"
                    + "splits {\n"
                    + "     density {\n"
                    + "         enable true\n"
                    + "         exclude \"hdpi\",\"mdpi\",\"xxhdpi\"\n"
                    + "     }\n"
                    + "}\n"
                    + "OR add them to the resConfigs list.", expected.getMessage());
        }
    }

    public void testResConfigAndSplitNoConflict() {
        File virtualAndroidManifestFile = new File("/path/to/non/existent/file");
        File assetsFolder = Mockito.mock(File.class);
        Mockito.when(assetsFolder.isDirectory()).thenReturn(true);
        Mockito.when(assetsFolder.getAbsolutePath()).thenReturn("/path/to/assets/folder");
        File resFolder = Mockito.mock(File.class);
        Mockito.when(resFolder.isDirectory()).thenReturn(true);
        Mockito.when(resFolder.getAbsolutePath()).thenReturn("/path/to/res/folder");

        AaptPackageProcessBuilder aaptPackageProcessBuilder =
                new AaptPackageProcessBuilder(virtualAndroidManifestFile, mAaptOptions);
        aaptPackageProcessBuilder.setResPackageOutput("/path/to/non/existent/dir")
                .setAssetsFolder(assetsFolder)
                .setResFolder(resFolder)
                .setPackageForR("com.example.package.forR")
                .setSourceOutputDir("path/to/source/output/dir")
                .setLibraries(ImmutableList.of(Mockito.mock(AndroidLibrary.class)))
                .setType(VariantType.DEFAULT)
                .setResourceConfigs(ImmutableList
                        .of("en", "fr", "es", "de", "it", "mdpi", "hdpi", "xhdpi", "xxhdpi"))
                .setSplits(ImmutableList.of("mdpi", "hdpi", "xhdpi", "xxhdpi"));

        BuildToolInfo buildToolInfo = getBuildToolInfo(new Revision(20, 0, 0));
        IAndroidTarget androidTarget = null;
        for (IAndroidTarget iAndroidTarget : getTargets()) {
            if (iAndroidTarget.getVersion().getApiLevel() >= 20) {
                androidTarget = iAndroidTarget;
                break;
            }
        }
        if (androidTarget == null) {
            throw new RuntimeException("Test requires pre android 20");
        }

        ProcessInfo processInfo = aaptPackageProcessBuilder
                .build(buildToolInfo, androidTarget, mLogger);

        List<String> command = processInfo.getArgs();

        assertEquals("en,fr,es,de,it,mdpi,hdpi,xhdpi,xxhdpi",
                command.get(command.indexOf("-c") + 1));
        assertTrue("--split".equals(command.get(command.indexOf("mdpi") - 1)));
        assertTrue("--split".equals(command.get(command.indexOf("hdpi") - 1)));
        assertTrue("--split".equals(command.get(command.indexOf("xhdpi") - 1)));
        assertTrue("--split".equals(command.get(command.indexOf("xxhdpi") - 1)));
        assertEquals(-1, command.indexOf("xxxhdpi"));
    }

    public void testResConfigWithPreferredDensityFlags() {
        File virtualAndroidManifestFile = new File("/path/to/non/existent/file");
        File assetsFolder = Mockito.mock(File.class);
        Mockito.when(assetsFolder.isDirectory()).thenReturn(true);
        Mockito.when(assetsFolder.getAbsolutePath()).thenReturn("/path/to/assets/folder");
        File resFolder = Mockito.mock(File.class);
        Mockito.when(resFolder.isDirectory()).thenReturn(true);
        Mockito.when(resFolder.getAbsolutePath()).thenReturn("/path/to/res/folder");

        AaptPackageProcessBuilder aaptPackageProcessBuilder =
                new AaptPackageProcessBuilder(virtualAndroidManifestFile, mAaptOptions);
        aaptPackageProcessBuilder.setResPackageOutput("/path/to/non/existent/dir")
                .setAssetsFolder(assetsFolder)
                .setResFolder(resFolder)
                .setPackageForR("com.example.package.forR")
                .setSourceOutputDir("path/to/source/output/dir")
                .setLibraries(ImmutableList.of(Mockito.mock(AndroidLibrary.class)))
                .setType(VariantType.DEFAULT)
                .setResourceConfigs(ImmutableList
                        .of("en", "fr", "es", "de", "it", "hdpi"));

        BuildToolInfo buildToolInfo = getBuildToolInfo(new Revision(21, 0, 0));
        IAndroidTarget androidTarget = null;
        for (IAndroidTarget iAndroidTarget : getTargets()) {
            if (iAndroidTarget.getVersion().getApiLevel() >= 21) {
                androidTarget = iAndroidTarget;
                break;
            }
        }
        if (androidTarget == null) {
            throw new RuntimeException("Test requires pre android-21");
        }

        ProcessInfo processInfo = aaptPackageProcessBuilder
                .build(buildToolInfo, androidTarget, mLogger);

        List<String> command = processInfo.getArgs();

        assertEquals("en,fr,es,de,it",
                command.get(command.indexOf("-c") + 1));
        assertEquals(-1, command.indexOf("mdpi"));
        assertTrue("--preferred-density".equals(command.get(command.indexOf("hdpi") - 1)));
        assertEquals(-1, command.indexOf("xhdpi"));
        assertEquals(-1, command.indexOf("xxhdpi"));
        assertEquals(-1, command.indexOf("xxxhdpi"));
    }


    public void testResConfigAndPreferredDensityConflict() {
        File virtualAndroidManifestFile = new File("/path/to/non/existent/file");
        File assetsFolder = Mockito.mock(File.class);
        Mockito.when(assetsFolder.isDirectory()).thenReturn(true);
        Mockito.when(assetsFolder.getAbsolutePath()).thenReturn("/path/to/assets/folder");
        File resFolder = Mockito.mock(File.class);
        Mockito.when(resFolder.isDirectory()).thenReturn(true);
        Mockito.when(resFolder.getAbsolutePath()).thenReturn("/path/to/res/folder");

        AaptPackageProcessBuilder aaptPackageProcessBuilder =
                new AaptPackageProcessBuilder(virtualAndroidManifestFile, mAaptOptions);
        aaptPackageProcessBuilder.setResPackageOutput("/path/to/non/existent/dir")
                .setAssetsFolder(assetsFolder)
                .setResFolder(resFolder)
                .setPackageForR("com.example.package.forR")
                .setSourceOutputDir("path/to/source/output/dir")
                .setLibraries(ImmutableList.of(Mockito.mock(AndroidLibrary.class)))
                .setType(VariantType.DEFAULT)
                .setResourceConfigs(ImmutableList.of( "en", "fr", "es", "de", "it", "hdpi"))
                .setSplits(ImmutableList.of("hdpi"))
                .setPreferredDensity("hdpi");

        BuildToolInfo buildToolInfo = getBuildToolInfo(new Revision(21, 0, 0));
        IAndroidTarget androidTarget = null;
        for (IAndroidTarget iAndroidTarget : getTargets()) {
            if (iAndroidTarget.getVersion().getApiLevel() >= 21) {
                androidTarget = iAndroidTarget;
                break;
            }
        }
        if (androidTarget == null) {
            throw new RuntimeException("Test requires pre android-21");
        }

        try {
            aaptPackageProcessBuilder.build(buildToolInfo, androidTarget, mLogger);
        } catch (Exception expected) {
            assertEquals("When using splits in tools 21 and above, resConfigs should not contain "
                    + "any densities. Right now, it contains \"hdpi\"\n"
                    + "Suggestion: remove these from resConfigs from build.gradle", expected.getMessage());
        }
    }

    public void testResConfigAndPreferredDensityNoConflict() {
        File virtualAndroidManifestFile = new File("/path/to/non/existent/file");
        File assetsFolder = Mockito.mock(File.class);
        Mockito.when(assetsFolder.isDirectory()).thenReturn(true);
        Mockito.when(assetsFolder.getAbsolutePath()).thenReturn("/path/to/assets/folder");
        File resFolder = Mockito.mock(File.class);
        Mockito.when(resFolder.isDirectory()).thenReturn(true);
        Mockito.when(resFolder.getAbsolutePath()).thenReturn("/path/to/res/folder");

        AaptPackageProcessBuilder aaptPackageProcessBuilder =
                new AaptPackageProcessBuilder(virtualAndroidManifestFile, mAaptOptions);
        aaptPackageProcessBuilder.setResPackageOutput("/path/to/non/existent/dir")
                .setAssetsFolder(assetsFolder)
                .setResFolder(resFolder)
                .setPackageForR("com.example.package.forR")
                .setSourceOutputDir("path/to/source/output/dir")
                .setLibraries(ImmutableList.of(Mockito.mock(AndroidLibrary.class)))
                .setType(VariantType.DEFAULT)
                // only languages, no density...
                .setResourceConfigs(ImmutableList.of("en", "fr", "es", "de", "it"))
                .setPreferredDensity("hdpi");

        BuildToolInfo buildToolInfo = getBuildToolInfo(new Revision(21, 0, 0));
        if (buildToolInfo == null) {
            throw new RuntimeException("Test requires build-tools 21");
        }
        IAndroidTarget androidTarget = null;
        for (IAndroidTarget iAndroidTarget : getTargets()) {
            if (iAndroidTarget.getVersion().getApiLevel() >= 21) {
                androidTarget = iAndroidTarget;
                break;
            }
        }
        if (androidTarget == null) {
            throw new RuntimeException("Test requires pre android-21");
        }

        ProcessInfo processInfo = aaptPackageProcessBuilder
                .build(buildToolInfo, androidTarget, mLogger);

        List<String> command = processInfo.getArgs();

        assertEquals("en,fr,es,de,it", command.get(command.indexOf("-c") + 1));
    }

    public void testEnvironment() {
        File virtualAndroidManifestFile = new File("/path/to/non/existent/file");

        AaptPackageProcessBuilder aaptPackageProcessBuilder =
                new AaptPackageProcessBuilder(virtualAndroidManifestFile, mAaptOptions);
        aaptPackageProcessBuilder.setResPackageOutput("/path/to/non/existent/dir");

        // add an env to the builder
        aaptPackageProcessBuilder.addEnvironment("foo", "bar");

        ProcessInfo processInfo = aaptPackageProcessBuilder
                .build(mBuildToolInfo, mIAndroidTarget, mLogger);

        Map<String, Object> env = processInfo.getEnvironment();
        assertEquals(1, env.size());
        assertNotNull(env.get("foo"));
        assertEquals("bar", env.get("foo"));
    }

    private static BuildToolInfo getBuildToolInfo(Revision revision) {
        AndroidSdkHandler handler = AndroidSdkHandler.getInstance(TestUtils.getSdk());
        FakeProgressIndicator progress = new FakeProgressIndicator();
        BuildToolInfo buildToolInfo = handler.getBuildToolInfo(revision, progress);
        if (buildToolInfo == null) {
            throw new RuntimeException("Test requires build-tools " + revision);
        }
        return buildToolInfo;
    }

    private static Collection<IAndroidTarget> getTargets() {
        AndroidSdkHandler handler = AndroidSdkHandler.getInstance(TestUtils.getSdk());
        FakeProgressIndicator progress = new FakeProgressIndicator();
        Collection<IAndroidTarget> targets = handler.getAndroidTargetManager(progress)
                .getTargets(progress);
        return targets;
    }
}
