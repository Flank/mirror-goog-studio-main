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

package com.android.tools.lint;

import com.android.tools.lint.checks.AbstractCheckTest;
import com.android.tools.lint.checks.HardcodedValuesDetector;
import com.android.tools.lint.checks.SdCardDetector;
import com.android.tools.lint.checks.infrastructure.ProjectDescription;
import com.android.tools.lint.detector.api.Detector;
import com.intellij.codeInsight.CustomExceptionHandler;
import com.intellij.openapi.extensions.Extensions;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class LintCliClientTest extends AbstractCheckTest {
    public void testUnknownId() {
        lint().files(
                        gradle(
                                ""
                                        + "\n"
                                        + "android {\n"
                                        + "    lintOptions {\n"
                                        + "        // Let's disable UnknownLintId\n"
                                        + "        /* Let's disable UnknownLintId */\n"
                                        + "        disable 'HardcodedText', 'UnknownLintId'\n"
                                        + "        enable 'HardcodedTxt', 'sdcardpath'\n"
                                        + "    }\n"
                                        + "}\n"))
                .issues(HardcodedValuesDetector.ISSUE, SdCardDetector.ISSUE)
                .allowSystemErrors(true)
                .run()
                .expect(
                        ""
                                + "build.gradle:6: Warning: Unknown issue id \"UnknownLintId\" [UnknownIssueId]\n"
                                + "        disable 'HardcodedText', 'UnknownLintId'\n"
                                + "                                  ~~~~~~~~~~~~~\n"
                                + "build.gradle:7: Warning: Unknown issue id \"HardcodedTxt\". Did you mean 'HardcodedText' (Hardcoded text) ? [UnknownIssueId]\n"
                                + "        enable 'HardcodedTxt', 'sdcardpath'\n"
                                + "                ~~~~~~~~~~~~\n"
                                + "build.gradle:7: Warning: Unknown issue id \"sdcardpath\". Did you mean 'SdCardPath' (Hardcoded reference to /sdcard) ? [UnknownIssueId]\n"
                                + "        enable 'HardcodedTxt', 'sdcardpath'\n"
                                + "                                ~~~~~~~~~~\n"
                                + "0 errors, 3 warnings");
    }

    public void testUnknownIdSuppressed() {
        lint().files(
                        gradle(
                                ""
                                        + "\n"
                                        + "android {\n"
                                        + "    lintOptions {\n"
                                        + "        // Let's disable UnknownLintId\n"
                                        + "        /* Let's disable UnknownLintId */\n"
                                        + "        check 'HardcodedText', 'UnknownLintId'\n"
                                        + "        disable 'UnknownIssueId'\n"
                                        + "    }\n"
                                        + "}\n"))
                .issues(HardcodedValuesDetector.ISSUE)
                .allowSystemErrors(true)
                .run()
                .expectClean();
    }

    public void testMissingExtensionPoints() {
        // Regression test for 37817771
        UastEnvironment env = UastEnvironment.create(UastEnvironment.Configuration.create());
        Extensions.getExtensions(CustomExceptionHandler.KEY);
        env.dispose();
    }

    /** Test to ensure that LintCliClient throws an exception when it encounters a relative path. */
    public void testRelativeOverrides() {
        File tempDir = getTempDir();
        File binFile = new File(tempDir, mGetterTest2.targetRelativePath);
        try {
            binFile = mGetterTest2.createFile(tempDir);
        } catch (IOException ignore) {

        }

        File projectDir = new File(tempDir, "test_project");
        if (!projectDir.exists()) {
            assert (projectDir.mkdirs());
        }

        LintCliFlags flags = new LintCliFlags();
        flags.setClassesOverride(Arrays.asList(new File(mGetterTest2.targetRelativePath)));

        com.android.tools.lint.checks.infrastructure.TestLintClient client =
                new com.android.tools.lint.checks.infrastructure.TestLintClient(flags);

        ProjectDescription project = new ProjectDescription(mGetterTest);
        project.setType(ProjectDescription.Type.LIBRARY);

        lint().projects(project)
                .rootDirectory(projectDir)
                .client(client)
                .run()
                .expect(
                        "Relative Path found: bin/classes.jar. All paths should be absolute.",
                        IllegalArgumentException.class);

        projectDir.delete();
        binFile.delete();
    }

    @Override
    protected Detector getDetector() {
        return new HardcodedValuesDetector();
    }

    @SuppressWarnings("all") // Sample code
    private TestFile mGetterTest =
            java(
                    ""
                            + "package test.bytecode;\n"
                            + "\n"
                            + "public class GetterTest {\n"
                            + "\tprivate int mFoo1;\n"
                            + "\tprivate String mFoo2;\n"
                            + "\tprivate int mBar1;\n"
                            + "\tprivate static int sFoo4;\n"
                            + "\n"
                            + "\tpublic int getFoo1() {\n"
                            + "\t\treturn mFoo1;\n"
                            + "\t}\n"
                            + "\n"
                            + "\tpublic String getFoo2() {\n"
                            + "\t\treturn mFoo2;\n"
                            + "\t}\n"
                            + "\n"
                            + "\tpublic int isBar1() {\n"
                            + "\t\treturn mBar1;\n"
                            + "\t}\n"
                            + "\n"
                            + "\t// Not \"plain\" getters:\n"
                            + "\n"
                            + "\tpublic String getFoo3() {\n"
                            + "\t\t// NOT a plain getter\n"
                            + "\t\tif (mFoo2 == null) {\n"
                            + "\t\t\tmFoo2 = \"\";\n"
                            + "\t\t}\n"
                            + "\t\treturn mFoo2;\n"
                            + "\t}\n"
                            + "\n"
                            + "\tpublic int getFoo4() {\n"
                            + "\t\t// NOT a plain getter (using static)\n"
                            + "\t\treturn sFoo4;\n"
                            + "\t}\n"
                            + "\n"
                            + "\tpublic int getFoo5(int x) {\n"
                            + "\t\t// NOT a plain getter (has extra argument)\n"
                            + "\t\treturn sFoo4;\n"
                            + "\t}\n"
                            + "\n"
                            + "\tpublic int isBar2(String s) {\n"
                            + "\t\t// NOT a plain getter (has extra argument)\n"
                            + "\t\treturn mFoo1;\n"
                            + "\t}\n"
                            + "\n"
                            + "\tpublic void test() {\n"
                            + "\t\tgetFoo1();\n"
                            + "\t\tgetFoo2();\n"
                            + "\t\tgetFoo3();\n"
                            + "\t\tgetFoo4();\n"
                            + "\t\tgetFoo5(42);\n"
                            + "\t\tisBar1();\n"
                            + "\t\tisBar2(\"foo\");\n"
                            + "\t\tthis.getFoo1();\n"
                            + "\t\tthis.getFoo2();\n"
                            + "\t\tthis.getFoo3();\n"
                            + "\t\tthis.getFoo4();\n"
                            + "\t}\n"
                            + "}\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mGetterTest2 =
            base64gzip(
                    "bin/classes.jar",
                    ""
                            + "H4sIAAAAAAAAAAvwZmYRYeAAQoFoOwcGJMDJwMLg6xriqOvp56b/7xQDAzND"
                            + "gDc7B0iKCaokAKdmESCGa/Z19PN0cw0O0fN1++x75rSPt67eRV5vXa1zZ85v"
                            + "DjK4YvzgaZGel6+Op+/F0lUsnDNeSh6RjtTKsBATebJEq+KZ6uvMT0UfixjB"
                            + "tk83XmlkAzTbBmo7F8Q6NNtZgbgktbhEH7cSPpiSpMqS1OT8lFR9hGfQ1cph"
                            + "qHVPLSlJLQoBiukl5yQWF5cGxeYLOYrYMuf8LODq2LJty62krYdWLV16wak7"
                            + "d5EnL+dVdp/KuIKja3WzE7K/5P+wrglYbPrxYLhw/ZSP9xJ3q26onbmz+L3t"
                            + "83mWxvX///7iXdDx14CJqbjPsoDrbX/fzY3xM1vTlz2e8Xf6FG5llQk2Zvek"
                            + "W4UXX9fdkyE/W9bdwdp2w1texsDyx4scVhXevF7yK2z97tNH1d3mS21lNJ3K"
                            + "siwr7HzRN5amnX8mOrzQPNut2NFyxNSj0eXwq5nnz/vdNrmfMX+GT3Z5z2Tl"
                            + "xfkfb/q2zTG/5qBweYeXRS9fuW/6iklpVxcL7NBcmHhq9YRnJXr2K2dFi6sc"
                            + "6pgQl31A/MGV3M4XHFXGTWsYni6f3XexsjpjT/HWnV+Fkt95HnEzSA2at/r5"
                            + "SZOPD5tmh5x5oua6Yhnj/Sl5wsqrTDtN0iyips84bOPu2rk0MWRShGTYdpWw"
                            + "wvmLu44opSndUGSPu222PEuo8gXTxmW1197PYBfj9ou5te2Y1YSl5xRq+wWY"
                            + "ciRcGcuc3waW9n3cmvHc+tLujdwlWhf8pjlcrlf6F7pVPXNu0EmFdZe12nk9"
                            + "HrLdsNl1ieWHdZp9f2PyvoSig+xzfhqx9f1uEq9Vvy81f84nVv3Kyfwro79+"
                            + "fGLf8WrlU/kTMSc4tJbtKCqeZ3NGIK2wxfCp0b3AvUmzJmnPW2caHv5C+l3f"
                            + "6VN9E1psIr980NvmVP2A682qQ+f4XutNWzxnFfc/RT3vq6kfayezK5vMcl8c"
                            + "aLcoQ67q/6PJrwN97Y8vFtNljTOruJnz0vPWKZn87V9Cvsrs1t2/7fT7EJW4"
                            + "OhPe11/0zSYs8JGaHeHAeVpjMmu0SfVsLdGuVTeOnuuIND2/5nhX4Xt7UEY4"
                            + "ZPg5Pw+YD7lZQRmBkUmEATUjwrIoKBejApQ8ja4VOX+JoGizxZGjQSZwMeDO"
                            + "hwiwG5ErcWvhQ9FyD0suRTgYpBc5HORQ9HIxEsq1Ad6sbBBnsjJYAFUfYQbx"
                            + "AFJZ3LASBQAA");
}
