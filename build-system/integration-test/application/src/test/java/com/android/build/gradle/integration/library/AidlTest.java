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

package com.android.build.gradle.integration.library;

import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Assemble tests for aidl. */
@RunWith(FilterableParameterized.class)
public class AidlTest {

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<String> data() {
        return ImmutableList.of("com.android.application", "com.android.library");
    }

    @Rule public GradleTestProject project;

    private String plugin;
    private File iTestAidl;
    private File aidlDir;
    private File activity;
    private File javaDir;

    public AidlTest(String plugin) {
        this.plugin = plugin;
        this.project =
                GradleTestProject.builder().fromTestApp(HelloWorldApp.forPlugin(plugin)).create();
    }

    @Before
    public void setUp() throws IOException {
        aidlDir = project.file("src/main/aidl/com/example/helloworld");
        javaDir = project.file("src/main/java/com/example/helloworld");

        FileUtils.mkdirs(aidlDir);

        TestFileUtils.appendToFile(
                new File(aidlDir, "MyRect.aidl"),
                ""
                        + "package com.example.helloworld;\n"
                        + "\n"
                        + "// Declare MyRect so AIDL can find it and knows that it implements\n"
                        + "// the parcelable protocol.\n"
                        + "parcelable MyRect;\n");

        iTestAidl = new File(aidlDir, "ITest.aidl");

        TestFileUtils.appendToFile(
                iTestAidl,
                ""
                        + "package com.example.helloworld;\n"
                        + "\n"
                        + "import com.example.helloworld.MyRect;\n"
                        + "\n"
                        + "interface ITest {\n"
                        + "    MyRect getMyRect();\n"
                        + "    int getInt();\n"
                        + "}");

        TestFileUtils.appendToFile(
                new File(aidlDir, "Packaged.aidl"),
                ""
                        + "package com.example.helloworld;\n"
                        + "\n"
                        + "import com.example.helloworld.MyRect;\n"
                        + "\n"
                        + "interface Packaged {\n"
                        + "    MyRect getMyRect();\n"
                        + "    int getInt();\n"
                        + "}");

        activity = new File(javaDir, "HelloWorld.java");

        TestFileUtils.appendToFile(
                new File(javaDir, "MyRect.java"),
                ""
                        + "package com.example.helloworld;\n"
                        + "\n"
                        + "import android.os.Parcel;\n"
                        + "import android.os.Parcelable;\n"
                        + "\n"
                        + "public class MyRect implements Parcelable {\n"
                        + "    public int left;\n"
                        + "    public int top;\n"
                        + "    public int right;\n"
                        + "    public int bottom;\n"
                        + "\n"
                        + "    public static final Parcelable.Creator<MyRect> CREATOR = new Parcelable.Creator<MyRect>() {\n"
                        + "        public MyRect createFromParcel(Parcel in) {\n"
                        + "            return new MyRect(in);\n"
                        + "        }\n"
                        + "\n"
                        + "        public MyRect[] newArray(int size) {\n"
                        + "            return new MyRect[size];\n"
                        + "        }\n"
                        + "    };\n"
                        + "\n"
                        + "    public MyRect() {\n"
                        + "    }\n"
                        + "\n"
                        + "    private MyRect(Parcel in) {\n"
                        + "        readFromParcel(in);\n"
                        + "    }\n"
                        + "\n"
                        + "    public void writeToParcel(Parcel out) {\n"
                        + "        out.writeInt(left);\n"
                        + "        out.writeInt(top);\n"
                        + "        out.writeInt(right);\n"
                        + "        out.writeInt(bottom);\n"
                        + "    }\n"
                        + "\n"
                        + "    public void readFromParcel(Parcel in) {\n"
                        + "        left = in.readInt();\n"
                        + "        top = in.readInt();\n"
                        + "        right = in.readInt();\n"
                        + "        bottom = in.readInt();\n"
                        + "    }\n"
                        + "\n"
                        + "    public int describeContents() {\n"
                        + "        // TODO Auto-generated method stub\n"
                        + "        return 0;\n"
                        + "    }\n"
                        + "\n"
                        + "    public void writeToParcel(Parcel arg0, int arg1) {\n"
                        + "        // TODO Auto-generated method stub\n"
                        + "\n"
                        + "    }\n"
                        + "}\n");

        TestFileUtils.addMethod(
                activity,
                "\n"
                        + "                void useAidlClasses(ITest instance) throws Exception {\n"
                        + "                    MyRect r = instance.getMyRect();\n"
                        + "                    r.toString();\n"
                        + "                }\n"
                        + "                ");

        if (plugin.contains("library")) {
            TestFileUtils.appendToFile(
                    project.getBuildFile(),
                    "android.aidlPackagedList = [\"com/example/helloworld/Packaged.aidl\"]\n"
                            + "\n"
                            + "// Check that AIDL is published as intermediate artifact for library.\n"
                            + "afterEvaluate {\n"
                            + "    assert !configurations.debugApiElements.outgoing.variants.findAll { it.name == \""
                            + AndroidArtifacts.ArtifactType.AIDL.getType()
                            + "\" }.isEmpty()\n"
                            + "}\n");
        }
    }

    @Test
    public void lint() throws IOException, InterruptedException {
        project.executor().run("lint");
    }

    @Test
    public void testAidl() throws Exception {
        project.execute("assembleDebug");
        checkAar("ITest");

        // Check for original file comment in the generated file (bug: 121251997)
        File genSrcFile =
                project.getGeneratedSourceFile(
                        "aidl_source_output_dir",
                        "debug",
                        "out",
                        "com",
                        "example",
                        "helloworld",
                        "ITest.java");

        assertThat(genSrcFile).doesNotContain(" * Original file: ");

        TestFileUtils.searchAndReplace(iTestAidl, "int getInt();", "");
        project.execute("assembleDebug");
        checkAar("ITest");

        TestFileUtils.searchAndReplace(iTestAidl, "ITest", "IRenamed");
        TestFileUtils.searchAndReplace(activity, "ITest", "IRenamed");
        Files.move(iTestAidl, new File(aidlDir, "IRenamed.aidl"));

        project.execute("assembleDebug");
        checkAar("IRenamed");
        checkAar("ITest");
    }

    @Test
    public void testJapaneseCharacters() throws IOException, InterruptedException {
        // First, add japanese characters
        TestFileUtils.searchAndReplace(
                new File(aidlDir, "Packaged.aidl"),
                "interface Packaged {" ,
                "interface Packaged {\n" + "/**\n" + "     * テスト用コメント\n" + "     */");

        // Then, change encoding to Shift_JIS and compile
        // (should fail on mac/linux if encoding is not handled properly)
        String newEncoding = "Shift_JIS";
        TestFileUtils.appendToFile(
                project.getBuildFile(), "android.compileOptions.encoding = '" + newEncoding + "'");

        changeEncoding(
                ImmutableList.of(
                        new File(aidlDir, "Packaged.aidl"),
                        new File(aidlDir, "MyRect.aidl"),
                        new File(aidlDir, "ITest.aidl"),
                        new File(javaDir, "MyRect.java"),
                        new File(javaDir, "HelloWorld.java")),
                Charset.forName(newEncoding));

        project.execute("clean", "assembleDebug");
    }

    private void checkAar(String dontInclude) throws Exception {
        if (!this.plugin.contains("library")) {
            return;
        }

        project.testAar(
                "debug",
                it -> {
                    it.contains("aidl/com/example/helloworld/MyRect.aidl");
                    it.contains("aidl/com/example/helloworld/Packaged.aidl");
                    it.doesNotContain("aidl/com/example/helloworld/" + dontInclude + ".aidl");
                });
    }

    private static void changeEncoding(@NonNull List<File> files, @NonNull Charset newEncoding)
            throws IOException {
        Charset oldEncoding = StandardCharsets.UTF_8;
        for (File f : files) {
            // Read file
            List<String> lines = java.nio.file.Files.readAllLines(f.toPath(), oldEncoding);

            // Re-create file in new encoding
            java.nio.file.Files.write(f.toPath(), lines, newEncoding);
        }
    }
}
