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

package com.android.build.gradle.integration.application

import com.android.apksig.ApkVerifier
import com.android.apksig.ApkVerifier.IssueWithParams
import com.android.apksig.zip.ZipFormatException
import com.android.apkzlib.sign.DigestAlgorithm
import com.android.apkzlib.sign.SignatureAlgorithm
import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.Adb
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.AndroidVersionMatcher
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.build.gradle.integration.common.utils.SigningConfigHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.SigningConfig
import com.android.builder.model.Variant
import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.testutils.TestUtils
import com.google.common.collect.ImmutableList
import com.google.common.collect.Range
import com.google.common.io.Resources
import groovy.transform.CompileStatic
import java.security.NoSuchAlgorithmException
import org.hamcrest.Matcher
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import static com.android.builder.core.BuilderConstants.DEBUG
import static com.android.builder.core.BuilderConstants.RELEASE
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_KEY_ALIAS
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_KEY_PASSWORD
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_STORE_FILE
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_STORE_PASSWORD
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_V1_ENABLED
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_V2_ENABLED
import static com.android.testutils.truth.MoreTruth.assertThatZip
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

/**
 * Integration test for all signing-related features.
 */
@CompileStatic
@RunWith(FilterableParameterized)
class SigningTest {

    public static final String STORE_PASSWORD = "store_password"

    public static final String ALIAS_NAME = "alias_name"

    public static final String KEY_PASSWORD = "key_password"

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        List<Object[]> parameters = []

        parameters.add([
                "rsa_keystore.jks",
                "CERT.RSA",
                SignatureAlgorithm.RSA.minSdkVersion] as Object[]);
        parameters.add([
                "dsa_keystore.jks",
                "CERT.DSA",
                SignatureAlgorithm.DSA.minSdkVersion] as Object[]);
        parameters.add([
                "ec_keystore.jks",
                "CERT.EC",
                SignatureAlgorithm.ECDSA.minSdkVersion] as Object[]);

        return parameters;
    }

    @Parameterized.Parameter(0)
    public String keystoreName

    @Parameterized.Parameter(1)
    public String certEntryName

    @Parameterized.Parameter(2)
    public int minSdkVersion

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.noBuildFile())
            .create()

    @Rule
    public Adb adb = new Adb();

    private File keystore

    @Before
    public void setUp() throws Exception {
        keystore = project.file("the.keystore")


        createKeystoreFile(keystoreName, keystore)

        project.buildFile << """
                apply plugin: 'com.android.application'

                android {
                    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"

                    defaultConfig {
                        minSdkVersion ${minSdkVersion}
                    }

                    signingConfigs {
                        customDebug {
                            storeFile file("${keystore.name}")
                            storePassword "$STORE_PASSWORD"
                            keyAlias "$ALIAS_NAME"
                            keyPassword "$KEY_PASSWORD"
                        }
                    }

                    buildTypes {
                        debug {
                            signingConfig signingConfigs.customDebug
                        }

                        customSigning {
                            initWith release
                        }
                    }

                    applicationVariants.all { variant ->
                        if (variant.buildType.name == "customSigning") {
                            variant.outputsAreSigned = true
                            // This usually means there is a task that generates the final outputs
                            // and variant.outputs*.outputFile is set to point to these files.
                        }
                    }
                }
"""

    }

    private static void createKeystoreFile(String resourceName, File keystore) {
        def keystoreBytes =
                Resources.toByteArray(
                        Resources.getResource(SigningTest, "SigningTest/" + resourceName))
        keystore << keystoreBytes
    }

    private void execute(String... tasks) {
        project.executor().run(tasks)
    }

    @Test
    void "signing DSL"() throws Exception {
        execute("assembleDebug")
        File apk = project.getApk("debug")
        assertThatZip(apk).contains("META-INF/$certEntryName")
        assertThatZip(apk).contains("META-INF/CERT.SF")
        ApkVerifier.Result verificationResult = assertApkSignaturesVerify(apk, minSdkVersion)
        assertTrue(verificationResult.isVerifiedUsingV1Scheme())
    }

    @Test
    void "assemble with injected signing config"() {
        // add prop args for signing override.
        List<String> args = ImmutableList.of(
                "-P" + PROPERTY_SIGNING_STORE_FILE + "=" + keystore.getPath(),
                "-P" + PROPERTY_SIGNING_STORE_PASSWORD + "=" + STORE_PASSWORD,
                "-P" + PROPERTY_SIGNING_KEY_ALIAS + "=" + ALIAS_NAME,
                "-P" + PROPERTY_SIGNING_KEY_PASSWORD + "=" + KEY_PASSWORD)

        project.executor().withArguments(args).run("assembleRelease")
        File apk = project.getApk("release")

        // Check for signing file inside the archive.
        assertThatZip(apk).contains("META-INF/$certEntryName")
        assertThatZip(apk).contains("META-INF/CERT.SF")
        ApkVerifier.Result verificationResult = assertApkSignaturesVerify(apk, minSdkVersion)
        assertTrue(verificationResult.isVerifiedUsingV1Scheme())
    }

    @Test
    void "check custom signing"() throws Exception {
        Collection<Variant> variants = project.model().getSingle().getOnlyModel().getVariants();

        for (Variant variant : variants) {
            // Release variant doesn't specify the signing config, so it should not be considered
            // signed.
            if (variant.getName().equals("release")) {
                assertThat(variant.mainArtifact.signed).named(variant.name).isFalse()
            }

            // customSigning is identical to release, but overrides the signing check.
            if (variant.getName().equals("customSigning")) {
                assertThat(variant.mainArtifact.signed).named(variant.name).isTrue()
            }
        }
    }

    @Test
    public void "signing configs model"() {
        def model = project.model().getSingle().getOnlyModel()

        Collection<SigningConfig> signingConfigs = model.signingConfigs
        assertThat(signingConfigs.collect {it.name}).containsExactly("debug", "customDebug")

        SigningConfig debugSigningConfig = ModelHelper.getSigningConfig(signingConfigs, DEBUG)
        new SigningConfigHelper(debugSigningConfig, DEBUG, true).test()

        SigningConfig mySigningConfig = ModelHelper.getSigningConfig(signingConfigs, "customDebug")
        new SigningConfigHelper(mySigningConfig, "customDebug", true)
                .setStoreFile(keystore)
                .setStorePassword(STORE_PASSWORD)
                .setKeyAlias(ALIAS_NAME)
                .setKeyPassword(KEY_PASSWORD)
                .test()

        Variant debugVariant = ModelHelper.getVariant(model.variants, DEBUG)
        assertThat(debugVariant.mainArtifact.signingConfigName).isEqualTo("customDebug")
        Collection<AndroidArtifact> debugExtraAndroidArtifacts = debugVariant.getExtraAndroidArtifacts()
        AndroidArtifact androidTestArtifact = ModelHelper.getAndroidArtifact(
                debugExtraAndroidArtifacts,
                AndroidProject.ARTIFACT_ANDROID_TEST)

        assertThat(androidTestArtifact.signingConfigName).isEqualTo("customDebug")

        Variant releaseVariant = ModelHelper.getVariant(model.variants, RELEASE)
        assertThat(releaseVariant.mainArtifact.signingConfigName).isNull()
    }

    @Test
    public void 'signingReport task'() throws Exception {
        execute("signingReport")
    }

    @Test
    public void 'SHA algorithm change'() throws Exception {
        File apk = project.getApk("debug")

        if (minSdkVersion < DigestAlgorithm.API_SHA_256_RSA_AND_ECDSA) {
            execute("assembleDebug")

            assertThatApk(apk).containsFileWithMatch("META-INF/CERT.SF", "SHA1-Digest");
            assertThatApk(apk).containsFileWithoutContent("META-INF/CERT.SF", "SHA-1-Digest");
            assertThatApk(apk).containsFileWithoutContent("META-INF/CERT.SF", "SHA-256-Digest");
            assertThatApk(apk).containsFileWithMatch("META-INF/MANIFEST.MF", "SHA1-Digest");
            assertThatApk(apk).containsFileWithoutContent("META-INF/MANIFEST.MF", "SHA-1-Digest");
            assertThatApk(apk).containsFileWithoutContent("META-INF/MANIFEST.MF", "SHA-256-Digest");

            TestFileUtils.searchAndReplace(
                    project.buildFile,
                    "minSdkVersion \\d+",
                    "minSdkVersion $DigestAlgorithm.API_SHA_256_RSA_AND_ECDSA")
        }

        TestUtils.waitForFileSystemTick()
        execute("assembleDebug")

        if ((certEntryName.endsWith(SignatureAlgorithm.RSA.keyAlgorithm))
                || (certEntryName.endsWith(SignatureAlgorithm.ECDSA.keyAlgorithm))) {
            assertThatApk(apk).containsFileWithMatch("META-INF/CERT.SF", "SHA-256-Digest");
            assertThatApk(apk).containsFileWithoutContent("META-INF/CERT.SF", "SHA1-Digest");
            assertThatApk(apk).containsFileWithoutContent("META-INF/CERT.SF", "SHA-1-Digest");
            assertThatApk(apk).containsFileWithMatch("META-INF/MANIFEST.MF", "SHA-256-Digest");
            assertThatApk(apk).containsFileWithoutContent("META-INF/MANIFEST.MF", "SHA-1-Digest");
        } else {
            assertThatApk(apk).containsFileWithMatch("META-INF/CERT.SF", "SHA1-Digest");
            assertThatApk(apk).containsFileWithoutContent("META-INF/CERT.SF", "SHA-1-Digest");
            assertThatApk(apk).containsFileWithoutContent("META-INF/CERT.SF", "SHA-256-Digest");
            assertThatApk(apk).containsFileWithMatch("META-INF/MANIFEST.MF", "SHA1-Digest");
            assertThatApk(apk).containsFileWithoutContent("META-INF/MANIFEST.MF", "SHA-1-Digest");
            assertThatApk(apk).containsFileWithoutContent("META-INF/MANIFEST.MF", "SHA-256-Digest");
        }

        TestFileUtils.searchAndReplace(
                project.buildFile,
                "minSdkVersion \\d+",
                "minSdkVersion $DigestAlgorithm.API_SHA_256_ALL_ALGORITHMS")

        TestUtils.waitForFileSystemTick()
        execute("assembleDebug")

        assertThatApk(apk).containsFileWithMatch("META-INF/CERT.SF", "SHA-256-Digest");
        assertThatApk(apk).containsFileWithoutContent("META-INF/CERT.SF", "SHA1-Digest");
        assertThatApk(apk).containsFileWithoutContent("META-INF/CERT.SF", "SHA-1-Digest");
        assertThatApk(apk).containsFileWithMatch("META-INF/MANIFEST.MF", "SHA-256-Digest");
        assertThatApk(apk).containsFileWithoutContent("META-INF/MANIFEST.MF", "SHA-1-Digest");
    }

    /**
     * Runs the connected tests to make sure the APK can be successfully installed. To cover
     * different scenarios, for every signature algorithm we need to build APKs with three different
     * minimum SDK versions and run each one against three different phones (for ECDSA there are
     * only two APKs and two phones).
     */
    @Test
    @Category(DeviceTests)
    public void 'SHA algorithm change - on device'() throws Exception {
        List<Matcher<AndroidVersion>> matchers = [
                AndroidVersionMatcher.forRange(
                        Range.lessThan(DigestAlgorithm.API_SHA_256_RSA_AND_ECDSA)),
                AndroidVersionMatcher.forRange(
                        Range.closedOpen(
                                DigestAlgorithm.API_SHA_256_RSA_AND_ECDSA,
                                DigestAlgorithm.API_SHA_256_ALL_ALGORITHMS)),
                AndroidVersionMatcher.forRange(
                        Range.atLeast(DigestAlgorithm.API_SHA_256_ALL_ALGORITHMS))
        ]

        List<IDevice> devices = matchers.collect{ m -> adb.getDevice(m)}

        // Check APK with minimum SDK 1. Skip this for ECDSA.
        if (minSdkVersion < DigestAlgorithm.API_SHA_256_RSA_AND_ECDSA) {
            for (IDevice device : devices) {
                checkOnDevice(device)
            }

            TestFileUtils.searchAndReplace(
                    project.buildFile,
                    "minSdkVersion \\d+",
                    "minSdkVersion $DigestAlgorithm.API_SHA_256_RSA_AND_ECDSA")
        }

        // Check APK with minimum SDK 18. Build script was set to 18 from the start or was just
        // changed above. Don't run on the oldest device, it's not compatible with the APK.
        for (IDevice device : devices.drop(1)) {
            checkOnDevice(device)
        }

        // Check APK with minimum SDK 21.
        TestFileUtils.searchAndReplace(
                project.buildFile,
                "minSdkVersion \\d+",
                "minSdkVersion $DigestAlgorithm.API_SHA_256_ALL_ALGORITHMS")

        checkOnDevice(devices.last())
    }

    private void checkOnDevice(IDevice device) {
        device.uninstallPackage("com.example.helloworld")
        device.uninstallPackage("com.example.helloworld.test")
        project.executor()
                .withArgument(Adb.getInjectToDeviceProviderProperty(device))
                .run(GradleTestProject.DEVICE_TEST_TASK)
    }

    @Test
    public void 'signing scheme toggle'() throws Exception {
        File apk = project.getApk("debug")

        // Toggles not specified -- testing their default values
        execute("clean", "assembleDebug")
        assertThatApk(apk).contains("META-INF/$certEntryName")
        assertThatApk(apk).contains("META-INF/CERT.SF")
        assertThatApk(apk).containsApkSigningBlock()
        ApkVerifier.Result verificationResult = assertApkSignaturesVerify(apk, minSdkVersion)
        assertTrue(verificationResult.isVerifiedUsingV1Scheme())
        assertTrue(verificationResult.isVerifiedUsingV2Scheme())

        // Specified: v1SigningEnabled false, v2SigningEnabled false
        TestFileUtils.searchAndReplace(
                project.buildFile,
                "customDebug \\{",
                "customDebug {\nv1SigningEnabled false\nv2SigningEnabled false")
        TestUtils.waitForFileSystemTick()
        execute("clean", "assembleDebug")
        assertThatApk(apk).doesNotContain("META-INF/$certEntryName")
        assertThatApk(apk).doesNotContain("META-INF/CERT.SF")
        assertThatApk(apk).doesNotContainApkSigningBlock()
        assertApkSignaturesDoNotVerify(apk, minSdkVersion)

        // Specified: v1SigningEnabled true, v2SigningEnabled false
        TestFileUtils.searchAndReplace(
                project.buildFile,
                "v1SigningEnabled false",
                "v1SigningEnabled true")
        TestUtils.waitForFileSystemTick()
        execute("clean", "assembleDebug")
        assertThatApk(apk).contains("META-INF/$certEntryName")
        assertThatApk(apk).contains("META-INF/CERT.SF")
        assertThatApk(apk).doesNotContainApkSigningBlock()
        verificationResult = assertApkSignaturesVerify(apk, minSdkVersion)
        assertTrue(verificationResult.isVerifiedUsingV1Scheme())
        assertFalse(verificationResult.isVerifiedUsingV2Scheme())

        // Specified: v1SigningEnabled false, v2SigningEnabled true
        TestFileUtils.searchAndReplace(
                project.buildFile,
                "v1SigningEnabled true",
                "v1SigningEnabled false")
        TestFileUtils.searchAndReplace(
                project.buildFile,
                "v2SigningEnabled false",
                "v2SigningEnabled true")
        TestUtils.waitForFileSystemTick()
        execute("clean", "assembleDebug")
        assertThatApk(apk).doesNotContain("META-INF/$certEntryName")
        assertThatApk(apk).doesNotContain("META-INF/CERT.SF")
        assertThatApk(apk).containsApkSigningBlock()
        // API Level 24 is the lowest level at which APKs don't have to be signed with v1 scheme
        assertApkSignaturesDoNotVerify(apk, Math.min(23, minSdkVersion))
        verificationResult = assertApkSignaturesVerify(apk, Math.max(24, minSdkVersion))
        assertFalse(verificationResult.isVerifiedUsingV1Scheme())
        assertTrue(verificationResult.isVerifiedUsingV2Scheme())

        // Specified: v1SigningEnabled true, v2SigningEnabled true
        TestFileUtils.searchAndReplace(
                project.buildFile,
                "v1SigningEnabled false",
                "v1SigningEnabled true")
        TestUtils.waitForFileSystemTick()
        execute("clean", "assembleDebug")
        assertThatApk(apk).contains("META-INF/$certEntryName")
        assertThatApk(apk).contains("META-INF/CERT.SF")
        assertThatApk(apk).containsApkSigningBlock()
        verificationResult = assertApkSignaturesVerify(apk, minSdkVersion)
        assertTrue(verificationResult.isVerifiedUsingV1Scheme())
        assertTrue(verificationResult.isVerifiedUsingV2Scheme())
    }

    @Test
    void "assemble with injected v1 config only"() {
        // add prop args for signing override.
        List<String> args = ImmutableList.of(
                "-P" + PROPERTY_SIGNING_STORE_FILE + "=" + keystore.getPath(),
                "-P" + PROPERTY_SIGNING_STORE_PASSWORD + "=" + STORE_PASSWORD,
                "-P" + PROPERTY_SIGNING_KEY_ALIAS + "=" + ALIAS_NAME,
                "-P" + PROPERTY_SIGNING_KEY_PASSWORD + "=" + KEY_PASSWORD,
                "-P" + PROPERTY_SIGNING_V1_ENABLED + "=true",
                "-P" + PROPERTY_SIGNING_V2_ENABLED + "=false");

        project.executor().withArguments(args).run("assembleRelease")
        File apk = project.getApk("release")

        assertThatApk(apk).contains("META-INF/$certEntryName")
        assertThatApk(apk).contains("META-INF/CERT.SF")
        assertThatApk(apk).doesNotContainApkSigningBlock()
        ApkVerifier.Result verificationResult = assertApkSignaturesVerify(apk, minSdkVersion)
        assertTrue(verificationResult.isVerifiedUsingV1Scheme())
        assertFalse(verificationResult.isVerifiedUsingV2Scheme())
    }

    @Test
    void "assemble with injected v2 config only"() {
        // add prop args for signing override.
        List<String> args = ImmutableList.of(
                "-P" + PROPERTY_SIGNING_STORE_FILE + "=" + keystore.getPath(),
                "-P" + PROPERTY_SIGNING_STORE_PASSWORD + "=" + STORE_PASSWORD,
                "-P" + PROPERTY_SIGNING_KEY_ALIAS + "=" + ALIAS_NAME,
                "-P" + PROPERTY_SIGNING_KEY_PASSWORD + "=" + KEY_PASSWORD,
                "-P" + PROPERTY_SIGNING_V1_ENABLED + "=false",
                "-P" + PROPERTY_SIGNING_V2_ENABLED + "=true");

        project.executor().withArguments(args).run("assembleRelease")
        File apk = project.getApk("release")

        assertThatApk(apk).doesNotContain("META-INF/$certEntryName")
        assertThatApk(apk).doesNotContain("META-INF/CERT.SF")
        assertThatApk(apk).containsApkSigningBlock()
        // API Level 24 is the lowest level at which APKs don't have to be signed with v1 scheme
        assertApkSignaturesDoNotVerify(apk, 23)
        ApkVerifier.Result verificationResult =
                assertApkSignaturesVerify(apk, Math.max(minSdkVersion, 24))
        assertFalse(verificationResult.isVerifiedUsingV1Scheme())
        assertTrue(verificationResult.isVerifiedUsingV2Scheme())
    }

    private static ApkVerifier.Result assertApkSignaturesVerify(File apk, int minSdkVersion)
            throws IOException, NoSuchAlgorithmException, ZipFormatException {
        ApkVerifier.Result result =
                new ApkVerifier.Builder(apk)
                        .setMinCheckedPlatformVersion(minSdkVersion)
                        .build()
                        .verify()
        if (result.isVerified()) {
            return result
        }

        List<IssueWithParams> errors = new ArrayList<>()
        errors.addAll(result.getErrors())
        for (ApkVerifier.Result.V1SchemeSignerInfo signer : result.getV1SchemeSigners()) {
            errors.addAll(signer.getErrors())
        }
        for (ApkVerifier.Result.V2SchemeSignerInfo signer : result.getV2SchemeSigners()) {
            errors.addAll(signer.getErrors())
        }
        fail("APK signatures failed to verify. " + errors.size() + " error(s): " + errors)
    }

    private static ApkVerifier.Result assertApkSignaturesDoNotVerify(File apk, int minSdkVersion)
            throws IOException, NoSuchAlgorithmException, ZipFormatException {
        ApkVerifier.Result result =
                new ApkVerifier.Builder(apk)
                        .setMinCheckedPlatformVersion(minSdkVersion)
                        .build()
                        .verify()
        if (result.isVerified()) {
            fail("APK signatures unexpectedly verified")
            return null
        }
        return result
    }
}
