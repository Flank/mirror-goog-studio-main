package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeFile;
import com.android.builder.model.NativeFolder;
import com.android.builder.model.NativeSettings;
import com.android.builder.model.NativeToolchain;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;

/** Test the ExternalNativeComponentModelPlugin. */
public class ExternalNativeComponentPluginTest {

    private final boolean isWindows =
            SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS;
    private String buildCommand = isWindows ? "cmd /c echo '' >" : "touch ";
    private String cleanCommand = isWindows ? "cmd /c del " : "rm ";

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(new HelloWorldJniApp())
                    .useExperimentalGradleVersion(true)
                    .create();

    @Test
    public void checkConfigurationUsingJsonDataFile() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.model.external'\n"
                        + "\n"
                        + "model {\n"
                        + "    nativeBuild {\n"
                        + "        create {\n"
                        + "            configs.add(file(\"config.json\"))\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        TestFileUtils.appendToFile(
                project.file("config.json"),
                "\n"
                        + "{\n"
                        + "    \"cleanCommands\" : [\""
                        + cleanCommand
                        + "output.txt\"],\n"
                        + "    \"buildFiles\" : [\"CMakeLists.txt\"],\n"
                        + "    \"libraries\" : {\n"
                        + "        \"foo\" : {\n"
                        + "            \"buildCommand\" : \""
                        + buildCommand
                        + "output.txt\",\n"
                        + "            \"artifactName\" : \"output\",\n"
                        + "            \"toolchain\" : \"toolchain1\",\n"
                        + "            \"output\" : \"build/libfoo.so\",\n"
                        + "            \"abi\" : \"x86\",\n"
                        + "            \"folders\" : [\n"
                        + "                {\n"
                        + "                    \"src\" : \"src/main/jni\",\n"
                        + "                    \"cFlags\" : \"folderCFlag1 folderCFlag2\",\n"
                        + "                    \"cppFlags\" : \"folderCppFlag1 folderCppFlag2\",\n"
                        + "                    \"workingDirectory\" : \"workingDir\"\n"
                        + "                }\n"
                        + "            ],\n"
                        + "            \"files\" : [\n"
                        + "                {\n"
                        + "                    \"src\" : \"src/main/jni/hello.c\",\n"
                        + "                    \"flags\" : \"fileFlag1 fileFlag2\",\n"
                        + "                    \"workingDirectory\" : \"workingDir\"\n"
                        + "                }\n"
                        + "            ]\n"
                        + "        }\n"
                        + "    },\n"
                        + "    \"toolchains\" : {\n"
                        + "        \"toolchain1\" : {\n"
                        + "            \"cCompilerExecutable\" : \"clang\",\n"
                        + "            \"cppCompilerExecutable\" : \"clang++\"\n"
                        + "        }\n"
                        + "    },\n"
                        + "    \"cFileExtensions\" : [\"c\"],\n"
                        + "    \"cppFileExtensions\" : [\"cpp\"]\n"
                        + "}\n");
        NativeAndroidProject model =
                project.executeAndReturnModel(NativeAndroidProject.class, "assemble");
        checkModel(model);
        project.execute("clean");
        assertThat(project.file("output.txt")).doesNotExist();
    }

    @Test
    public void checkConfigurationWithMultipleJsonDataFiles()
            throws IOException, InterruptedException {
        final File script =
                project.file(isWindows ? "generate_configs.cmd" : "generate_configs.sh");

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.model.external'\n"
                        + "\n"
                        + "model {\n"
                        + "    nativeBuild {\n"
                        + "        create {\n"
                        + "            configs.addAll([\n"
                        + "                file(\"config1.json\"),\n"
                        + "                file(\"config2.json\")\n"
                        + "            ])\n"
                        + "            command \""
                        + script.getAbsolutePath().replace("\\", "\\\\")
                        + "\"\n"
                        + "        }\n"
                        + "        create {\n"
                        + "            configs.addAll([\n"
                        + "                file(\"config3.json\"),\n"
                        + "                file(\"config4.json\")\n"
                        + "            ])\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        TestFileUtils.appendToFile(
                project.file("config1.json.template"),
                "{\n"
                        + "    \"buildFiles\" : [\"CMakeLists.txt\"],\n"
                        + "    \"libraries\" : {\n"
                        + "        \"foo-DEBUG\" : {\n"
                        + "            \"buildCommand\" : \""
                        + buildCommand
                        + "output.txt\",\n"
                        + "            \"buildType\" : \"debug\",\n"
                        + "            \"artifactName\" : \"foo\",\n"
                        + "            \"abi\" : \"x86\",\n"
                        + "            \"toolchain\" : \"toolchain1\",\n"
                        + "            \"output\" : \"build/debug/libfoo.so\"\n"
                        + "        }\n"
                        + "    },\n"
                        + "    \"toolchains\" : {\n"
                        + "        \"toolchain1\" : {\n"
                        + "            \"cCompilerExecutable\" : \"clang\",\n"
                        + "            \"cppCompilerExecutable\" : \"clang++\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}");
        TestFileUtils.appendToFile(
                project.file("config2.json.template"),
                "{\n"
                        + "    \"buildFiles\" : [\"CMakeLists.txt\"],\n"
                        + "    \"libraries\" : {\n"
                        + "        \"foo-RELEASE\" : {\n"
                        + "            \"buildCommand\" : \""
                        + buildCommand
                        + "output.txt\",\n"
                        + "            \"buildType\" : \"release\",\n"
                        + "            \"artifactName\" : \"foo\",\n"
                        + "            \"abi\" : \"x86\",\n"
                        + "            \"toolchain\" : \"toolchain1\",\n"
                        + "            \"output\" : \"build/release/libfoo.so\"\n"
                        + "        }\n"
                        + "    },\n"
                        + "    \"toolchains\" : {\n"
                        + "        \"toolchain1\" : {\n"
                        + "            \"cCompilerExecutable\" : \"clang\",\n"
                        + "            \"cppCompilerExecutable\" : \"clang++\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}");
        String copy =
                SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS ? "copy" : "cp";
        TestFileUtils.appendToFile(
                script,
                "\n"
                        + copy
                        + " "
                        + project.file("config1.json.template")
                        + " "
                        + project.file("config1.json")
                        + "\n"
                        + copy
                        + " "
                        + project.file("config2.json.template")
                        + " "
                        + project.file("config2.json")
                        + "\n");
        script.setExecutable(true);

        TestFileUtils.appendToFile(
                project.file("config3.json"),
                "\n"
                        + "{\n"
                        + "    \"buildFiles\" : [\"CMakeLists.txt\"],\n"
                        + "    \"libraries\" : {\n"
                        + "        \"bar-DEBUG\" : {\n"
                        + "            \"buildCommand\" : \""
                        + buildCommand
                        + "output.txt\",\n"
                        + "            \"buildType\" : \"debug\",\n"
                        + "            \"artifactName\" : \"bar\",\n"
                        + "            \"abi\" : \"x86\",\n"
                        + "            \"toolchain\" : \"toolchain2\",\n"
                        + "            \"output\" : \"build/debug/libbar.so\"\n"
                        + "        }\n"
                        + "    },\n"
                        + "    \"toolchains\" : {\n"
                        + "        \"toolchain2\" : {\n"
                        + "            \"cCompilerExecutable\" : \"gcc\",\n"
                        + "            \"cppCompilerExecutable\" : \"g++\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        TestFileUtils.appendToFile(
                project.file("config4.json"),
                "\n"
                        + "{\n"
                        + "    \"buildFiles\" : [\"CMakeLists.txt\"],\n"
                        + "    \"libraries\" : {\n"
                        + "        \"bar-RELEASE\" : {\n"
                        + "            \"buildCommand\" : \""
                        + buildCommand
                        + "bar.txt\",\n"
                        + "            \"buildType\" : \"release\",\n"
                        + "            \"artifactName\" : \"bar\",\n"
                        + "            \"abi\" : \"x86\",\n"
                        + "            \"toolchain\" : \"toolchain2\",\n"
                        + "            \"output\" : \"build/release/libbar.so\"\n"
                        + "        }\n"
                        + "    },\n"
                        + "    \"toolchains\" : {\n"
                        + "        \"toolchain2\" : {\n"
                        + "            \"cCompilerExecutable\" : \"gcc\",\n"
                        + "            \"cppCompilerExecutable\" : \"g++\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        assertThat(project.file("config1.json")).doesNotExist();
        assertThat(project.file("config2.json")).doesNotExist();
        project.execute("generateConfigFiles");
        assertThat(project.file("config1.json")).exists();
        assertThat(project.file("config2.json")).exists();

        NativeAndroidProject model =
                project.executeAndReturnModel(NativeAndroidProject.class, "assemble");
        assertThat(model.getFileExtensions()).containsEntry("c", "c");
        assertThat(model.getFileExtensions()).containsEntry("C", "c++");
        assertThat(model.getFileExtensions()).containsEntry("CPP", "c++");
        assertThat(model.getFileExtensions()).containsEntry("c++", "c++");
        assertThat(model.getFileExtensions()).containsEntry("cc", "c++");
        assertThat(model.getFileExtensions()).containsEntry("cp", "c++");
        assertThat(model.getFileExtensions()).containsEntry("cpp", "c++");
        assertThat(model.getFileExtensions()).containsEntry("cxx", "c++");

        assertThat(model.getArtifacts()).hasSize(4);
        for (NativeArtifact artifact : model.getArtifacts()) {
            if (artifact.getName().startsWith("foo")) {
                if (artifact.getName().endsWith("DEBUG")) {
                    assertThat(artifact.getName()).isEqualTo("foo-DEBUG");
                    assertThat(artifact.getAssembleTaskName()).isEqualTo("createFoo-DEBUG");
                } else {
                    assertThat(artifact.getName()).isEqualTo("foo-RELEASE");
                    assertThat(artifact.getAssembleTaskName()).isEqualTo("createFoo-RELEASE");
                }

                assertThat(artifact.getToolChain()).isEqualTo("toolchain1");
                assertThat(artifact.getOutputFile()).hasName("libfoo.so");
            } else {
                if (artifact.getName().endsWith("DEBUG")) {
                    assertThat(artifact.getName()).isEqualTo("bar-DEBUG");
                    assertThat(artifact.getAssembleTaskName()).isEqualTo("createBar-DEBUG");
                } else {
                    assertThat(artifact.getName()).isEqualTo("bar-RELEASE");
                    assertThat(artifact.getAssembleTaskName()).isEqualTo("createBar-RELEASE");
                }

                assertThat(artifact.getToolChain()).isEqualTo("toolchain2");
                assertThat(artifact.getOutputFile()).hasName("libbar.so");
            }
        }

        assertThat(model.getToolChains()).hasSize(2);
        for (NativeToolchain toolchain : model.getToolChains()) {
            if (toolchain.getName().equals("toolchain1")) {

                assertThat(toolchain.getName()).isEqualTo("toolchain1");
                assertThat(toolchain.getCCompilerExecutable().getName()).isEqualTo("clang");
                assertThat(toolchain.getCppCompilerExecutable().getName()).isEqualTo("clang++");
            } else {
                assertThat(toolchain.getName()).isEqualTo("toolchain2");
                assertThat(toolchain.getCCompilerExecutable().getName()).isEqualTo("gcc");
                assertThat(toolchain.getCppCompilerExecutable().getName()).isEqualTo("g++");
            }
        }
    }

    @Test
    public void checkConfigrationsUsingPluginDSL() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.model.external'\n"
                        + "\n"
                        + "model {\n"
                        + "    nativeBuildConfig {\n"
                        + "        cleanCommands.add(\""
                        + cleanCommand
                        + "output.txt\")\n"
                        + "        buildFiles.addAll([file(\"CMakeLists.txt\")])\n"
                        + "        cFileExtensions.add(\"c\")\n"
                        + "        cppFileExtensions.add(\"cpp\")\n"
                        + "\n"
                        + "        libraries {\n"
                        + "            create(\"foo\") {\n"
                        + "                buildCommand \""
                        + buildCommand
                        + "output.txt\"\n"
                        + "                abi \"x86\"\n"
                        + "                artifactName \"output\"\n"
                        + "                toolchain \"toolchain1\"\n"
                        + "                output file(\"build/libfoo.so\")\n"
                        + "                folders {\n"
                        + "                    create() {\n"
                        + "                        src \"src/main/jni\"\n"
                        + "                        cFlags \"folderCFlag1 folderCFlag2\"\n"
                        + "                        cppFlags \"folderCppFlag1 folderCppFlag2\"\n"
                        + "                        workingDirectory \"workingDir\"\n"
                        + "                    }\n"
                        + "                }\n"
                        + "                files {\n"
                        + "                    create() {\n"
                        + "                        src \"src/main/jni/hello.c\"\n"
                        + "                        flags \"fileFlag1 fileFlag2\"\n"
                        + "                        workingDirectory \"workingDir\"\n"
                        + "                    }\n"
                        + "                }\n"
                        + "\n"
                        + "            }\n"
                        + "        }\n"
                        + "        toolchains {\n"
                        + "            create(\"toolchain1\") {\n"
                        + "                // Needs to be CCompilerExecutable instead of the more correct cCompilerExecutable because,\n"
                        + "                // of a stupid bug with Gradle.\n"
                        + "                CCompilerExecutable = \"clang\"\n"
                        + "                cppCompilerExecutable \"clang++\"\n"
                        + "\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        NativeAndroidProject model =
                project.executeAndReturnModel(NativeAndroidProject.class, "assemble");
        checkModel(model);
        project.execute("clean");
        assertThat(project.file("output.txt")).doesNotExist();
    }

    private void checkModel(NativeAndroidProject model) {
        Collection<NativeSettings> settingsMap = model.getSettings();
        assertThat(project.file("output.txt")).exists();

        assertThat(model.getArtifacts()).hasSize(1);

        assertThat(model.getFileExtensions()).containsEntry("c", "c");
        assertThat(model.getFileExtensions()).containsEntry("cpp", "c++");

        final NativeArtifact artifact = Iterables.getOnlyElement(model.getArtifacts());
        assertThat(artifact.getToolChain()).isEqualTo("toolchain1");

        // Source Folders
        assertThat(artifact.getSourceFolders()).hasSize(1);
        final NativeFolder folder = Iterables.getOnlyElement(artifact.getSourceFolders());
        assertThat(folder.getFolderPath()).isEqualTo(project.file("src/main/jni"));
        assertThat(folder.getWorkingDirectory()).isEqualTo(project.file("workingDir"));
        NativeSettings setting =
                settingsMap
                        .stream()
                        .filter(i -> i.getName().equals(folder.getPerLanguageSettings().get("c")))
                        .findAny()
                        .orElseThrow(AssertionError::new);
        assertThat(setting.getCompilerFlags()).containsAllOf("folderCFlag1", "folderCFlag2");
        String cPlusPlusSettings =
                Iterables.getOnlyElement(artifact.getSourceFolders())
                        .getPerLanguageSettings()
                        .get("c++");
        setting =
                settingsMap
                        .stream()
                        .filter(i -> i.getName().equals(cPlusPlusSettings))
                        .findAny()
                        .orElseThrow(AssertionError::new);
        assertThat(setting.getCompilerFlags()).containsAllOf("folderCppFlag1", "folderCppFlag2");

        assertThat(artifact.getSourceFiles()).hasSize(1);
        final NativeFile file = Iterables.getOnlyElement(artifact.getSourceFiles());
        assertThat(file.getFilePath()).isEqualTo(project.file("src/main/jni/hello.c"));
        assertThat(file.getWorkingDirectory()).isEqualTo(project.file("workingDir"));
        setting =
                settingsMap
                        .stream()
                        .filter(i -> i.getName().equals(file.getSettingsName()))
                        .findAny()
                        .orElseThrow(AssertionError::new);
        assertThat(setting.getCompilerFlags()).containsAllOf("fileFlag1", "fileFlag2");

        assertThat(model.getToolChains()).hasSize(1);
        NativeToolchain toolchain = Iterables.getOnlyElement(model.getToolChains());
        assertThat(toolchain.getName()).isEqualTo("toolchain1");
        assertThat(toolchain.getCCompilerExecutable().getName()).isEqualTo("clang");
        assertThat(toolchain.getCppCompilerExecutable().getName()).isEqualTo("clang++");
    }
}
