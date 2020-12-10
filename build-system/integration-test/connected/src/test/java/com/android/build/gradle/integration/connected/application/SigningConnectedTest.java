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

package com.android.build.gradle.integration.connected.application;

import static com.android.builder.internal.packaging.ApkCreatorType.APK_FLINGER;
import static com.android.builder.internal.packaging.ApkCreatorType.APK_Z_FILE_CREATOR;
import static com.android.tools.build.apkzlib.sign.SignatureAlgorithm.DSA;
import static com.android.tools.build.apkzlib.sign.SignatureAlgorithm.ECDSA;
import static com.android.tools.build.apkzlib.sign.SignatureAlgorithm.RSA;
import static java.lang.Math.max;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.AbiMatcher;
import com.android.build.gradle.integration.common.utils.AndroidVersionMatcher;
import com.android.build.gradle.integration.common.utils.GradleTestProjectUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.connected.utils.EmulatorUtils;
import com.android.build.gradle.options.StringOption;
import com.android.builder.internal.packaging.ApkCreatorType;
import com.android.ddmlib.IDevice;
import com.android.tools.build.apkzlib.sign.DigestAlgorithm;
import com.google.common.io.Resources;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class SigningConnectedTest {
    public static final String STORE_PASSWORD = "store_password";
    public static final String ALIAS_NAME = "alias_name";
    public static final String KEY_PASSWORD = "key_password";

    @Parameterized.Parameter() public String keystoreName;

    @Parameterized.Parameter(1)
    public String certEntryName;

    @Parameterized.Parameter(2)
    public int minSdkVersion;

    @Parameterized.Parameter(3)
    public ApkCreatorType apkCreatorType;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.noBuildFile())
                    .create();

    @Rule public Adb adb = new Adb();
    @ClassRule public static final ExternalResource EMULATOR = EmulatorUtils.getEmulator();

    @Parameterized.Parameters(name = "{0}, {3}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[] {
                    "rsa_keystore.jks", "CERT.RSA", max(RSA.minSdkVersion, 9), APK_FLINGER
                },
                new Object[] {
                    "rsa_keystore.jks", "CERT.RSA", max(RSA.minSdkVersion, 9), APK_Z_FILE_CREATOR
                },
                new Object[] {
                    "dsa_keystore.jks", "CERT.DSA", max(DSA.minSdkVersion, 9), APK_FLINGER
                },
                new Object[] {
                    "dsa_keystore.jks", "CERT.DSA", max(DSA.minSdkVersion, 9), APK_Z_FILE_CREATOR
                },
                new Object[] {
                    "ec_keystore.jks", "CERT.EC", max(ECDSA.minSdkVersion, 9), APK_FLINGER
                },
                new Object[] {
                    "ec_keystore.jks", "CERT.EC", max(ECDSA.minSdkVersion, 9), APK_Z_FILE_CREATOR
                });
    }

    @Before
    public void setUp() throws Exception {
        createKeystoreFile(keystoreName, project.file("the.keystore"));

        GradleTestProjectUtils.setApkCreatorType(project, apkCreatorType);

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                ""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion "
                        + minSdkVersion
                        + "\n"
                        + "        testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'\n"
                        + "    }\n"
                        + "\n"
                        + "    signingConfigs {\n"
                        + "        customDebug {\n"
                        + "            storeFile file('the.keystore')\n"
                        + "            storePassword '"
                        + STORE_PASSWORD
                        + "'\n"
                        + "            keyAlias '"
                        + ALIAS_NAME
                        + "'\n"
                        + "            keyPassword '"
                        + KEY_PASSWORD
                        + "'\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    buildTypes {\n"
                        + "        debug {\n"
                        + "            signingConfig signingConfigs.customDebug\n"
                        + "        }\n"
                        + "\n"
                        + "        customSigning {\n"
                        + "            initWith release\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    applicationVariants.all { variant ->\n"
                        + "        if (variant.buildType.name == \"customSigning\") {\n"
                        + "            variant.outputsAreSigned = true\n"
                        + "            // This usually means there is a task that generates the final outputs\n"
                        + "            // and variant.outputs*.outputFile is set to point to these files.\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "  androidTestImplementation \"com.android.support.test:runner:${project.testSupportLibVersion}\"\n"
                        + "  androidTestImplementation \"com.android.support.test:rules:${project.testSupportLibVersion}\"\n"
                        + "}\n"
                        + "");

        // fail fast if no response
        project.addAdbTimeout();
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll");
    }

    private static void createKeystoreFile(@NonNull String resourceName, @NonNull File keystore)
            throws Exception {
        byte[] keystoreBytes =
                Resources.toByteArray(
                        Resources.getResource(SigningConnectedTest.class, resourceName));
        Files.write(keystore.toPath(), keystoreBytes);
    }

    /**
     * Runs the connected tests to make sure the APK can be successfully installed.
     *
     * <p>To cover different scenarios, for every signature algorithm we need to build APKs with
     * three different minimum SDK versions and run each one against three different system images.
     *
     * <p>This method covers 24 and 19 devices. The test for a 17 device is a separate test method
     * that will report as skipped if the device is not available.
     */
    @Test
    @Ignore("b/165341811, b/175333004")
    public void shaAlgorithmChange_OnDevice() throws Exception {

        // Check APK with minimum SDK 21.
        TestFileUtils.searchRegexAndReplace(
                project.getBuildFile(),
                "minSdkVersion \\d+",
                "minSdkVersion " + DigestAlgorithm.API_SHA_256_ALL_ALGORITHMS);

        IDevice device24Plus =
                adb.getDevice(AndroidVersionMatcher.atLeast(24), AbiMatcher.anyAbi());
        checkOnDevice(device24Plus);

        // Check APK with minimum SDK 18.
        // Don't run on the oldest device, it's not compatible with the APK.
        TestFileUtils.searchRegexAndReplace(
                project.getBuildFile(),
                "minSdkVersion \\d+",
                "minSdkVersion " + DigestAlgorithm.API_SHA_256_RSA_AND_ECDSA);
        checkOnDevice(device24Plus);
        IDevice device19 = adb.getDevice(19);
        checkOnDevice(device19);

        // Check APK with minimum SDK 1. Skip this for ECDSA.
        if (minSdkVersion < DigestAlgorithm.API_SHA_256_RSA_AND_ECDSA) {
            TestFileUtils.searchRegexAndReplace(
                    project.getBuildFile(), "minSdkVersion \\d+", "minSdkVersion " + minSdkVersion);

            checkOnDevice(device19);
            checkOnDevice(device24Plus);
        }
    }

    /**
     * Run the connected tests against an api 17 device.
     *
     * <p>This will be ignored, rather than fail, if no api 17 device is connected.
     */
    @Test
    @Ignore("b/165341811, b/175333004")
    public void deployOnApi17() throws Exception {
        if (minSdkVersion >= DigestAlgorithm.API_SHA_256_RSA_AND_ECDSA) {
            // if min SDK is higher than the device, we cannot deploy.
            return;
        }
        IDevice device17 =
                adb.getDevice(
                        AndroidVersionMatcher.exactly(17),
                        AbiMatcher.anyAbi(),
                        error -> {
                            throw new AssumptionViolatedException(error);
                        });
        assert device17 != null;
        checkOnDevice(device17);
    }

    @Test
    public void connectedCheck() throws Exception {
        project.executor().run("connectedCheck");
    }

    private void checkOnDevice(@NonNull IDevice device) throws Exception {
        device.uninstallPackage("com.example.helloworld");
        device.uninstallPackage("com.example.helloworld.test");
        project.executor()
                .with(StringOption.DEVICE_POOL_SERIAL, device.getSerialNumber())
                .run(GradleTestProject.DEVICE_TEST_TASK);
    }
}
