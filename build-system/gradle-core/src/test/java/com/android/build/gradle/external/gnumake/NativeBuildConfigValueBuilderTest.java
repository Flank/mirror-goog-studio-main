/*
 * Copyright (C) 2015 The Android Open Source Project
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
/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.build.gradle.external.gnumake;

import static com.google.common.truth.Truth.assertAbout;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValue;
import com.android.build.gradle.truth.NativeBuildConfigValueSubject;
import com.google.gson.Gson;
import java.io.File;
import org.junit.Test;


public class NativeBuildConfigValueBuilderTest {

    private static void assertThatNativeBuildConfigEquals(
            @NonNull String commands, @NonNull String expected) {
        File projectPath = new File("/projects/MyProject/jni/Android.mk");

        NativeBuildConfigValue actualValue =
                new NativeBuildConfigValueBuilder(projectPath, projectPath.getParentFile())
                        .setCommands("echo build command", "echo clean command", "debug", commands)
                        .build();

        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            expected = expected.replace("/", "\\\\");
        }

        NativeBuildConfigValue expectedValue =
                new Gson().fromJson(expected, NativeBuildConfigValue.class);

        assertAbout(NativeBuildConfigValueSubject.nativebuildConfigValues())
                .that(actualValue)
                .isEqualTo(expectedValue);
    }

    @Test
    public void doubleTarget() {
        assertThatNativeBuildConfigEquals(
                "g++ -c a.c -o x86_64/a.o\n"
                        + "g++ x86_64/a.o -o x86_64/a.so\n"
                        + "g++ -c a.c -o x86/a.o\n"
                        + "g++ x86/a.o -o x86/a.so",
                "{\n"
                        + "  \"buildFiles\": [\n"
                        + "    {\n"
                        + "      \"path\": \"/projects/MyProject/jni/Android.mk\"\n"
                        + "    }\n"
                        + "  ],\n"
                        + "  \"cleanCommands\": [\n"
                        + "    \"echo clean command\"\n"
                        + "  ],"
                        + "  \"buildTargetsCommand\": \"echo build command {LIST_OF_TARGETS_TO_BUILD}\",\n"
                        + "  \"libraries\": {\n"
                        + "    \"a-debug-x86_64\": {\n"
                        + "      abi : \"x86_64\","
                        + "      artifactName : \"a\","
                        + "      \"buildCommand\": \"echo build command x86_64/a.so\",\n"
                        + "      \"toolchain\": \"toolchain-x86_64\",\n"
                        + "      \"files\": [\n"
                        + "        {\n"
                        + "          \"src\": {\n"
                        + "            \"path\": \"/projects/MyProject/jni/a.c\"\n"
                        + "          },\n"
                        + "          \"flags\": \"\"\n"
                        + "        }\n"
                        + "      ],\n"
                        + "      \"output\": {\n"
                        + "        \"path\": \"x86_64/a.so\"\n"
                        + "      }\n"
                        + "    },\n"
                        + "    \"a-debug-x86\": {\n"
                        + "      abi : \"x86\","
                        + "      artifactName : \"a\","
                        + "      \"buildCommand\": \"echo build command x86/a.so\",\n"
                        + "      \"toolchain\": \"toolchain-x86\",\n"
                        + "      \"files\": [\n"
                        + "        {\n"
                        + "          \"src\": {\n"
                        + "            \"path\": \"/projects/MyProject/jni/a.c\"\n"
                        + "          },\n"
                        + "          \"flags\": \"\"\n"
                        + "        }\n"
                        + "      ],\n"
                        + "      \"output\": {\n"
                        + "        \"path\": \"x86/a.so\"\n"
                        + "      }\n"
                        + "    }\n"
                        + "  },\n"
                        + "  \"toolchains\": {\n"
                        + "    \"toolchain-x86\": {\n"
                        + "      \"cCompilerExecutable\": {\n"
                        + "        \"path\": \"g++\"\n"
                        + "      }\n"
                        + "    },\n"
                        + "    \"toolchain-x86_64\": {\n"
                        + "      \"cCompilerExecutable\": {\n"
                        + "        \"path\": \"g++\"\n"
                        + "      }\n"
                        + "    }\n"
                        + "  },\n"
                        + "  \"cFileExtensions\": [\n"
                        + "    \"c\"\n"
                        + "  ],\n"
                        + "  \"cppFileExtensions\": []\n"
                        + "}");
    }

    @Test
    public void includeInSource() {
        assertThatNativeBuildConfigEquals(
                "g++ -c a.c -o x/aa.o -Isome-include-path\n",
                "{\n"
                        + "  \"buildFiles\": [\n"
                        + "    {\n"
                        + "      \"path\": \"/projects/MyProject/jni/Android.mk\"\n"
                        + "    }\n"
                        + "  ],\n"
                        + "  \"cleanCommands\": [\n"
                        + "    \"echo clean command\"\n"
                        + "  ],"
                        + "  \"buildTargetsCommand\": \"echo build command {LIST_OF_TARGETS_TO_BUILD}\",\n"
                        + "  \"libraries\": {\n"
                        + "    \"aa-debug-x\": {\n"
                        + "      \"buildCommand\": \"echo build command x/aa.o\",\n"
                        + "      \"toolchain\": \"toolchain-x\",\n"
                        + "      \"abi\": \"x\",\n"
                        + "      artifactName : \"aa\","
                        + "      \"files\": [\n"
                        + "        {\n"
                        + "          \"src\": {\n"
                        + "            \"path\": \"/projects/MyProject/jni/a.c\"\n"
                        + "          },\n"
                        + "          \"flags\": \"-Isome-include-path\"\n"
                        + "        }\n"
                        + "      ],\n"
                        + "      \"output\": {\n"
                        + "        \"path\": \"x/aa.o\"\n"
                        + "      }\n"
                        + "    }\n"
                        + "  },\n"
                        + "  \"toolchains\": {\n"
                        + "    \"toolchain-x\": {\n"
                        + "      \"cCompilerExecutable\": {\n"
                        + "        \"path\": \"g++\"\n"
                        + "      }\n"
                        + "    }\n"
                        + "  },\n"
                        + "  \"cFileExtensions\": [\n"
                        + "    \"c\"\n"
                        + "  ],\n"
                        + "  \"cppFileExtensions\": []\n"
                        + "}");
    }

    @Test
    public void weirdExtension1() {
        assertThatNativeBuildConfigEquals(
                "g++ -c a.c -o x86_64/aa.o\n"
                        + "g++ -c a.S -o x86_64/aS.so\n"
                        + "g++ x86_64/aa.o x86_64/aS.so -o x86/a.so",
                "{\n"
                        + "  \"buildFiles\": [\n"
                        + "    {\n"
                        + "      \"path\": \"/projects/MyProject/jni/Android.mk\"\n"
                        + "    }\n"
                        + "  ],\n"
                        + "  \"cleanCommands\": [\n"
                        + "    \"echo clean command\"\n"
                        + "  ],"
                        + "  \"buildTargetsCommand\": \"echo build command {LIST_OF_TARGETS_TO_BUILD}\",\n"
                        + "  \"libraries\": {\n"
                        + "    \"a-debug-x86\": {\n"
                        + "      abi : \"x86\","
                        + "      artifactName : \"a\","
                        + "      \"buildCommand\": \"echo build command x86/a.so\",\n"
                        + "      \"toolchain\": \"toolchain-x86\",\n"
                        + "      \"files\": [\n"
                        + "        {\n"
                        + "          \"src\": {\n"
                        + "            \"path\": \"/projects/MyProject/jni/a.S\"\n"
                        + "          },\n"
                        + "          \"flags\": \"\"\n"
                        + "        },\n"
                        + "        {\n"
                        + "          \"src\": {\n"
                        + "            \"path\": \"/projects/MyProject/jni/a.c\"\n"
                        + "          },\n"
                        + "          \"flags\": \"\"\n"
                        + "        }\n"
                        + "      ],\n"
                        + "      \"output\": {\n"
                        + "        \"path\": \"x86/a.so\"\n"
                        + "      }\n"
                        + "    }\n"
                        + "  },\n"
                        + "  \"toolchains\": {\n"
                        + "    \"toolchain-x86\": {\n"
                        + "      \"cCompilerExecutable\": {\n"
                        + "        \"path\": \"g++\"\n"
                        + "      }\n"
                        + "    }\n"
                        + "  },\n"
                        + "  \"cFileExtensions\": [\n"
                        + "    \"S\",\n"
                        + "    \"c\"\n"
                        + "  ],\n"
                        + "  \"cppFileExtensions\": []\n"
                        + "}");
    }

    @Test
    public void weirdExtension2() {
        assertThatNativeBuildConfigEquals(
                "g++ -c a.S -o x86_64/aS.so\n"
                        + "g++ -c a.c -o x86_64/aa.o\n"
                        + "g++ x86_64/aa.o x86_64/aS.so -o x86/a.so",
                "{\n"
                        + "  \"buildFiles\": [\n"
                        + "    {\n"
                        + "      \"path\": \"/projects/MyProject/jni/Android.mk\"\n"
                        + "    }\n"
                        + "  ],\n"
                        + "  \"cleanCommands\": [\n"
                        + "    \"echo clean command\"\n"
                        + "  ],"
                        + "  \"buildTargetsCommand\": \"echo build command {LIST_OF_TARGETS_TO_BUILD}\",\n"
                        + "  \"libraries\": {\n"
                        + "    \"a-debug-x86\": {\n"
                        + "      abi : \"x86\","
                        + "      artifactName : \"a\","
                        + "      \"buildCommand\": \"echo build command x86/a.so\",\n"
                        + "      \"toolchain\": \"toolchain-x86\",\n"
                        + "      \"files\": [\n"
                        + "        {\n"
                        + "          \"src\": {\n"
                        + "            \"path\": \"/projects/MyProject/jni/a.S\"\n"
                        + "          },\n"
                        + "          \"flags\": \"\"\n"
                        + "        },\n"
                        + "        {\n"
                        + "          \"src\": {\n"
                        + "            \"path\": \"/projects/MyProject/jni/a.c\"\n"
                        + "          },\n"
                        + "          \"flags\": \"\"\n"
                        + "        }\n"
                        + "      ],\n"
                        + "      \"output\": {\n"
                        + "        \"path\": \"x86/a.so\"\n"
                        + "      }\n"
                        + "    }\n"
                        + "  },\n"
                        + "  \"toolchains\": {\n"
                        + "    \"toolchain-x86\": {\n"
                        + "      \"cCompilerExecutable\": {\n"
                        + "        \"path\": \"g++\"\n"
                        + "      }\n"
                        + "    }\n"
                        + "  },\n"
                        + "  \"cFileExtensions\": [\n"
                        + "    \"c\",\n"
                        + "    \"S\"\n"
                        + "  ],\n"
                        + "  \"cppFileExtensions\": []\n"
                        + "}");
    }
}
