package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeLibrary;
import com.android.builder.model.NativeToolchain;
import com.android.builder.model.Variant;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Assemble tests for ndkSanAngeles2. */
public class NdkSanAngeles2Test {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .useExperimentalGradleVersion(true)
                    .fromTestProject("ndkSanAngeles2")
                    .create();

    private static AndroidProject model;

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        model = project.executeAndReturnModel("clean", "assembleDebug").getOnlyModel();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void lint() throws IOException, InterruptedException {
        project.execute("lint");
    }

    @Test
    public void checkModel() {
        Collection<Variant> variants = model.getVariants();
        assertThat(variants).hasSize(8);

        Variant debugVariant = ModelHelper.getVariant(variants, "x86Debug");
        AndroidArtifact debugMainArtifact = debugVariant.getMainArtifact();
        assertThat(debugMainArtifact.getNativeLibraries()).hasSize(1);

        NativeLibrary nativeLibrary =
                Iterables.getOnlyElement(debugMainArtifact.getNativeLibraries());
        assertThat(nativeLibrary.getName()).isEqualTo("sanangeles");
        assertThat(nativeLibrary.getToolchainName()).isEqualTo("clang-x86");
        assertThat(nativeLibrary.getCCompilerFlags()).contains("-DDISABLE_IMPORTGL");
        assertThat(nativeLibrary.getCSystemIncludeDirs()).isEmpty();
        assertThat(nativeLibrary.getCppSystemIncludeDirs()).isNotEmpty();
        File solibSearchPath =
                Iterables.getOnlyElement(nativeLibrary.getDebuggableLibraryFolders());
        assertThat(new File(solibSearchPath, "libsanangeles.so")).exists();

        Collection<String> toolchainNames =
                model.getNativeToolchains()
                        .stream()
                        .map(NativeToolchain::getName)
                        .collect(Collectors.toList());
        Collection<String> expectedToolchains =
                ImmutableList.of(
                                SdkConstants.ABI_INTEL_ATOM,
                                SdkConstants.ABI_ARMEABI_V7A,
                                SdkConstants.ABI_ARMEABI,
                                SdkConstants.ABI_MIPS)
                        .stream()
                        .map(i -> "clang-" + i)
                        .collect(Collectors.toList());
        assertThat(toolchainNames).containsAllIn(expectedToolchains);
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws IOException, InterruptedException {
        project.executeConnectedCheck();
    }
}
