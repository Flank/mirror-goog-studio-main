package com.android.build.gradle.integration.component;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.ide.common.signing.KeystoreHelper;
import com.android.ide.common.signing.KeytoolException;
import com.android.testutils.truth.MoreTruth;
import com.android.utils.StdLogger;
import java.io.File;
import java.io.IOException;
import java.security.KeyStore;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Integration test with signing config. */
public class ComponentSigningConfigTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.noBuildFile())
                    .useExperimentalGradleVersion(true)
                    .create();

    @Before
    public void setUp() throws IOException, KeytoolException {
        File debugKeyStore = project.file("debug.keystore");
        KeystoreHelper.createDebugStore(
                KeyStore.getDefaultType(),
                debugKeyStore,
                "android",
                "android",
                "androiddebugkey",
                new StdLogger(StdLogger.Level.INFO));
        // TODO: Let gradle resolve file when it is able to do that with File in a Managed type.
        String storeFile = "storeFile = new File(\'" + debugKeyStore.toString() + "\')";

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: \"com.android.model.application\"\n"
                        + "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "        buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "        buildTypes {\n"
                        + "            debug {\n"
                        + "                buildConfigFields.with {\n"
                        + "                    create() {\n"
                        + "                        type \"int\"\n"
                        + "                        name \"VALUE\"\n"
                        + "                        value \"1\"\n"
                        + "                    }\n"
                        + "                }\n"
                        + "            }\n"
                        + "            release {\n"
                        + "                signingConfig = $(\"android.signingConfigs.myConfig\")\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "    android.signingConfigs {\n"
                        + "        create(\"myConfig\") {\n"
                        + storeFile
                        + "\n"
                        + "            storePassword \"android\"\n"
                        + "            keyAlias \"androiddebugkey\"\n"
                        + "            keyPassword \"android\"\n"
                        + "            storeType \"jks\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void assembleRelease() throws IOException, InterruptedException {
        project.execute("clean", "assembleRelease");
        MoreTruth.assertThat(project.getApk(GradleTestProject.ApkType.of("release", true)))
                .contains("META-INF/CERT.RSA");
    }
}
