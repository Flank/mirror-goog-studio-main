/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

public class ParcelDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ParcelDetector();
    }

    public void testParcelables() {
        String expected =
                ""
                        + "src/test/bytecode/MyParcelable1.java:6: Error: This class implements Parcelable but does not provide a CREATOR field [ParcelCreator]\n"
                        + "public class MyParcelable1 implements Parcelable {\n"
                        + "             ~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        classpath(),
                        manifest().minSdk(10),
                        java(
                                ""
                                        + "package test.bytecode;\n"
                                        + "\n"
                                        + "import android.os.Parcel;\n"
                                        + "import android.os.Parcelable;\n"
                                        + "\n"
                                        + "public class MyParcelable1 implements Parcelable {\n"
                                        + "\t@Override\n"
                                        + "\tpublic int describeContents() {\n"
                                        + "\t\treturn 0;\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\t@Override\n"
                                        + "\tpublic void writeToParcel(Parcel arg0, int arg1) {\n"
                                        + "\t}\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package test.bytecode;\n"
                                        + "\n"
                                        + "import android.os.Parcel;\n"
                                        + "import android.os.Parcelable;\n"
                                        + "\n"
                                        + "public class MyParcelable2 implements Parcelable {\n"
                                        + "\tpublic static final Parcelable.Creator<MyParcelable2> CREATOR = new Parcelable.Creator<MyParcelable2>() {\n"
                                        + "\t\tpublic MyParcelable2 createFromParcel(Parcel in) {\n"
                                        + "\t\t\treturn new MyParcelable2();\n"
                                        + "\t\t}\n"
                                        + "\n"
                                        + "\t\tpublic MyParcelable2[] newArray(int size) {\n"
                                        + "\t\t\treturn new MyParcelable2[size];\n"
                                        + "\t\t}\n"
                                        + "\t};\n"
                                        + "\n"
                                        + "\t@Override\n"
                                        + "\tpublic int describeContents() {\n"
                                        + "\t\treturn 0;\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\t@Override\n"
                                        + "\tpublic void writeToParcel(Parcel arg0, int arg1) {\n"
                                        + "\t}\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package test.bytecode;\n"
                                        + "\n"
                                        + "import android.os.Parcel;\n"
                                        + "import android.os.Parcelable;\n"
                                        + "\n"
                                        + "public class MyParcelable3 implements Parcelable {\n"
                                        + "\tpublic static final int CREATOR = 0; // Wrong type\n"
                                        + "\n"
                                        + "\t@Override\n"
                                        + "\tpublic int describeContents() {\n"
                                        + "\t\treturn 0;\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\t@Override\n"
                                        + "\tpublic void writeToParcel(Parcel arg0, int arg1) {\n"
                                        + "\t}\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package test.bytecode;\n"
                                        + "\n"
                                        + "import android.os.Parcel;\n"
                                        + "import android.os.Parcelable;\n"
                                        + "\n"
                                        + "public abstract class MyParcelable4 implements Parcelable {\n"
                                        + "    @Override\n"
                                        + "    public int describeContents() {\n"
                                        + "        return 0;\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    public void writeToParcel(Parcel arg0, int arg1) {\n"
                                        + "    }\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package test.bytecode;\n"
                                        + "\n"
                                        + "import android.os.Parcelable;\n"
                                        + "\n"
                                        + "public interface MyParcelable5 extends Parcelable {\n"
                                        + "    @Override\n"
                                        + "    public int describeContents();\n"
                                        + "}\n"))
                .run()
                .expect(expected);
    }

    public void testInterfaceOnSuperClass() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=171522
        String expected =
                ""
                        + "src/test/pkg/ParcelableDemo.java:14: Error: This class implements Parcelable but does not provide a CREATOR field [ParcelCreator]\n"
                        + "    private static class JustParcelable implements Parcelable {\n"
                        + "                         ~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ParcelableDemo.java:19: Error: This class implements Parcelable but does not provide a CREATOR field [ParcelCreator]\n"
                        + "    private static class JustParcelableSubclass extends JustParcelable {\n"
                        + "                         ~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ParcelableDemo.java:22: Error: This class implements Parcelable but does not provide a CREATOR field [ParcelCreator]\n"
                        + "    private static class ParcelableThroughAbstractSuper extends AbstractParcelable {\n"
                        + "                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ParcelableDemo.java:27: Error: This class implements Parcelable but does not provide a CREATOR field [ParcelCreator]\n"
                        + "    private static class ParcelableThroughInterface implements MoreThanParcelable {\n"
                        + "                         ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "4 errors, 0 warnings\n";
        //noinspection all // sample code
        lint().files(
                        java(
                                "src/test/pkg/ParcelableDemo.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.os.Parcel;\n"
                                        + "import android.os.Parcelable;\n"
                                        + "\n"
                                        + "public class ParcelableDemo {\n"
                                        + "    private interface MoreThanParcelable extends Parcelable {\n"
                                        + "        void somethingMore();\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private abstract static class AbstractParcelable implements Parcelable {\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private static class JustParcelable implements Parcelable {\n"
                                        + "        public int describeContents() {return 0;}\n"
                                        + "        public void writeToParcel(Parcel dest, int flags) {}\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private static class JustParcelableSubclass extends JustParcelable {\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private static class ParcelableThroughAbstractSuper extends AbstractParcelable {\n"
                                        + "        public int describeContents() {return 0;}\n"
                                        + "        public void writeToParcel(Parcel dest, int flags) {}\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private static class ParcelableThroughInterface implements MoreThanParcelable {\n"
                                        + "        public int describeContents() {return 0;}\n"
                                        + "        public void writeToParcel(Parcel dest, int flags) {}\n"
                                        + "        public void somethingMore() {}\n"
                                        + "    }\n"
                                        + "}"))
                .run()
                .expect(expected);
    }

    public void testSpans() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=192841
        //noinspection all // sample code
        lint().files(
                        java(
                                "src/test/pkg/TestSpan.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.text.TextPaint;\n"
                                        + "import android.text.style.URLSpan;\n"
                                        + "\n"
                                        + "public class TestSpan extends URLSpan {\n"
                                        + "    public TestSpan(String url) {\n"
                                        + "        super(url);\n"
                                        + "    }\n"
                                        + "}"))
                .run()
                .expectClean();
    }

    @SuppressWarnings("all") // sample code
    public void testTypeParameters() {
        lint().files(
                        java(
                                "src/test/pkg/ParcelTest.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.os.Bundle;\n"
                                        + "import android.os.Parcelable;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"unused\")\n"
                                        + "public class ParcelTest {\n"
                                        + "    public static <T extends Parcelable> T getParcelable(Bundle args, String key) {\n"
                                        + "        return args == null ? null : args.<T>getParcelable(key);\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expectClean();
    }

    @SuppressWarnings("all") // sample code
    public void testInheritedCreatorField() {
        lint().files(
                        java(
                                "src/test/pkg/ParcelableTest.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.os.Parcel;\n"
                                        + "import android.os.Parcelable;\n"
                                        + "\n"
                                        + "public class ParcelableTest {\n"
                                        + "    public static abstract class AbstractParcelable implements Parcelable {\n"
                                        + "        public static Creator CREATOR;\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public class MyParcelable extends AbstractParcelable {\n"
                                        + "        @Override\n"
                                        + "        public int describeContents() {\n"
                                        + "            return 0;\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        @Override\n"
                                        + "        public void writeToParcel(Parcel parcel, int i) {\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expectClean();
    }

    public void testKotlin() {
        lint().files(
                        kotlin(
                                "package test.pkg\n"
                                        + "\n"
                                        + "import android.os.Parcel\n"
                                        + "import android.os.Parcelable\n"
                                        + "\n"
                                        + "class MissingParcelable1a : Parcelable {\n"
                                        + "    override fun describeContents(): Int { return 0 }\n"
                                        + "    override fun writeToParcel(dest: Parcel, flags: Int) {}\n"
                                        + "}"))
                .incremental()
                .run()
                .expect(
                        ""
                                + "src/test/pkg/MissingParcelable1a.kt:6: Error: This class implements Parcelable but does not provide a CREATOR field [ParcelCreator]\n"
                                + "class MissingParcelable1a : Parcelable {\n"
                                + "      ~~~~~~~~~~~~~~~~~~~\n"
                                + "1 errors, 0 warnings\n");
    }

    public void testKotlinMissingJvmField() {
        lint().files(
                        // Error: CREATOR field is missing @JvmField
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "import android.os.Parcel\n"
                                        + "import android.os.Parcelable\n"
                                        + "\n"
                                        + "class MyClass(val something: String) : Parcelable {\n"
                                        + "\n"
                                        + "    private constructor(p: Parcel) : this(\n"
                                        + "            something = p.readString()\n"
                                        + "    )\n"
                                        + "\n"
                                        + "    override fun writeToParcel(dest: Parcel, flags: Int) {\n"
                                        + "        dest.writeString(something)\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    override fun describeContents() = 0\n"
                                        + "\n"
                                        + "    companion object {\n"
                                        + "        val CREATOR = object : Parcelable.Creator<MyClass> { // ERROR\n"
                                        + "            override fun createFromParcel(parcel: Parcel) = MyClass(parcel)\n"
                                        + "            override fun newArray(size: Int) = arrayOfNulls<MyClass>(size)\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}"),

                        // OK: It's there
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "import android.os.Parcel\n"
                                        + "import android.os.Parcelable\n"
                                        + "\n"
                                        + "class MyClass2(val something: String) : Parcelable {\n"
                                        + "\n"
                                        + "    private constructor(p: Parcel) : this(\n"
                                        + "            something = p.readString()\n"
                                        + "    )\n"
                                        + "\n"
                                        + "    override fun writeToParcel(dest: Parcel, flags: Int) {\n"
                                        + "        dest.writeString(something)\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    override fun describeContents() = 0\n"
                                        + "\n"
                                        + "    companion object {\n"
                                        + "        @JvmField val CREATOR = object : Parcelable.Creator<MyClass2> { // OK\n"
                                        + "            override fun createFromParcel(parcel: Parcel) = MyClass2(parcel)\n"
                                        + "            override fun newArray(size: Int) = arrayOfNulls<MyClass2>(size)\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}"),
                        // OK: It's not using a field but a companion object
                        kotlin(
                                ""
                                        + "package tet.pkg\n"
                                        + "\n"
                                        + "import android.os.Parcel\n"
                                        + "import android.os.Parcelable\n"
                                        + "\n"
                                        + "class MyClass3() : Parcelable {\n"
                                        + "    constructor(parcel: Parcel) : this() {\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    override fun writeToParcel(parcel: Parcel, flags: Int) {\n"
                                        + "\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    override fun describeContents(): Int {\n"
                                        + "        return 0\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    companion object CREATOR : Parcelable.Creator<KotlinParc> {\n"
                                        + "        override fun createFromParcel(parcel: Parcel): KotlinParc {\n"
                                        + "            return KotlinParc(parcel)\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        override fun newArray(size: Int): Array<KotlinParc?> {\n"
                                        + "            return arrayOfNulls(size)\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "}"))
                .run()
                .expect(
                        ""
                                + "src/test/pkg/MyClass.kt:19: Error: Field should be annotated with @JvmField [ParcelCreator]\n"
                                + "        val CREATOR = object : Parcelable.Creator<MyClass> { // ERROR\n"
                                + "            ~~~~~~~\n"
                                + "1 errors, 0 warnings")
                .expectFixDiffs(
                        ""
                                + "Fix for src/test/pkg/MyClass.kt line 18: Add @JvmField:\n"
                                + "@@ -19 +19\n"
                                + "-         val CREATOR = object : Parcelable.Creator<MyClass> { // ERROR\n"
                                + "+         @JvmField val CREATOR = object : Parcelable.Creator<MyClass> { // ERROR");
    }

    public void testParcelizeSuggestions() {
        lint().files(
                        kotlin(
                                ""
                                        + "@file:JvmName(\"TestKt\")\n"
                                        + "package test\n"
                                        + "\n"
                                        + "import kotlinx.android.parcel.*\n"
                                        + "import android.os.Parcel\n"
                                        + "import android.os.Parcelable\n"
                                        + "\n"
                                        + "@Parcelize\n"
                                        + "data class Test(val a: List<String>) : Parcelable // OK: Has parcelable\n"
                                        + "\n"
                                        + "class Test2(val a: List<String>) : Parcelable // Missing field but don't suggest @Parcelize\n"
                                        + "\n"
                                        + "data class Test3(val a: List<String>) : Parcelable // Warn: Missing @Parcelize\n"
                                        + "\n"),
                        kotlin(
                                ""
                                        + "package kotlinx.android.parcel\n"
                                        + "@Target(AnnotationTarget.CLASS)\n"
                                        + "@Retention(AnnotationRetention.BINARY)\n"
                                        + "annotation class Parcelize"))
                .run()
                .expect(
                        ""
                                + "src/test/Test.kt:11: Error: This class implements Parcelable but does not provide a CREATOR field [ParcelCreator]\n"
                                + "class Test2(val a: List<String>) : Parcelable // Missing field but don't suggest @Parcelize\n"
                                + "      ~~~~~\n"
                                + "src/test/Test.kt:13: Error: This class implements Parcelable but does not provide a CREATOR field [ParcelCreator]\n"
                                + "data class Test3(val a: List<String>) : Parcelable // Warn: Missing @Parcelize\n"
                                + "           ~~~~~\n"
                                + "2 errors, 0 warnings");
    }

    public void testNoJvmFieldWarning() {
        // Regression test for
        // 78197859: Misleading "Missing Parcelable CREATOR field"
        lint().files(
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "class ExtendedParcelable(\n"
                                        + "        value: String,\n"
                                        + "        private val value2: Int\n"
                                        + ") : BaseParcelable(value)\n"),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.os.Parcel;\n"
                                        + "import android.os.Parcelable;\n"
                                        + "import android.support.annotation.NonNull;\n"
                                        + "\n"
                                        + "/** @noinspection ClassNameDiffersFromFileName, MethodMayBeStatic */ "
                                        + "public class BaseParcelable implements Parcelable {\n"
                                        + "    @NonNull\n"
                                        + "    protected final String mValue;\n"
                                        + "\n"
                                        + "    public BaseParcelable(@NonNull String value) {\n"
                                        + "        mValue = value;\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    protected BaseParcelable(@NonNull Parcel in) {\n"
                                        + "        mValue = in.readString();\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public static final Creator<BaseParcelable> CREATOR = new Creator<BaseParcelable>() {\n"
                                        + "        @NonNull\n"
                                        + "        @Override\n"
                                        + "        public BaseParcelable createFromParcel(@NonNull Parcel in) {\n"
                                        + "            return new BaseParcelable(in);\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        @NonNull\n"
                                        + "        @Override\n"
                                        + "        public BaseParcelable[] newArray(int size) {\n"
                                        + "            return new BaseParcelable[size];\n"
                                        + "        }\n"
                                        + "    };\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    public int describeContents() {\n"
                                        + "        return 0;\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    public void writeToParcel(@NonNull Parcel dest, int flags) {\n"
                                        + "        dest.writeString(mValue);\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expectClean();
    }
}
