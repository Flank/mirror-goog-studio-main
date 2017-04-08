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

package com.android.tools.lint.checks;

import static com.android.utils.XmlUtils.toXmlAttributeValue;

import com.android.tools.lint.detector.api.Detector;

public class VectorPathDetectorTest extends AbstractCheckTest {
    private static final String SHORT_PATH = ""
            + "M 37.8337860107,-40.3974914551 c 0,0 -35.8077850342,31.5523681641 -35.8077850342,31.5523681641 "
            + "c 0,0 40.9884796143,40.9278411865 40.9884796143,40.9278411865 c 0,0 -2.61700439453,2.0938873291 -2.61700439453,2.0938873291 "
            + "c 0,0 -41.1884460449,-40.9392852783 -41.1884460449,-40.9392852783 c 0,0 -34.6200408936,25.4699249268 -34.6200408936,25.4699249268 "
            + "c 0,0 55.9664764404,69.742401123 55.9664764404,69.742401123 c 0,0 73.2448120117,-59.1047973633 73.2448120117,-59.1047973633 "
            + "c 0,0 -55.9664916992,-69.7423400879 -55.9664916992,-69.7423400879 Z ";

    private static final String LONG_PATH = SHORT_PATH + SHORT_PATH + SHORT_PATH;

    @Override
    protected Detector getDetector() {
        return new VectorPathDetector();
    }

    public void test() {
        lint().files(
                xml("res/drawable/my_vector.xml", ""
                        + "<vector\n"
                        + "  xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "  android:name=\"root\"\n"
                        + "  android:width=\"48dp\"\n"
                        + "  android:height=\"48dp\"\n"
                        + "  android:tint=\"?attr/colorControlNormal\"\n"
                        + "  android:viewportHeight=\"48\"\n"
                        + "  android:viewportWidth=\"48\">\n"
                        + "\n"
                        + "  <group\n"
                        + "    android:translateX=\"-1.21595\"\n"
                        + "    android:translateY=\"6.86752\">\n"
                        + "\n"
                        + "    <clip-path\n"
                        + "      android:name=\"maskClipPath\"\n"
                        + "      android:pathData=\"@string/airplane_mask_clip_path_enabled\"/>\n"
                        + "\n"
                        + "    <path\n"
                        + "      android:name=\"crossPath\"\n"
                        + "      android:pathData=\"@string/airplane_cross_path\"\n"
                        + "      android:strokeColor=\"@android:color/white\"\n"
                        + "      android:strokeWidth=\"3.5\"\n"
                        + "      android:trimPathEnd=\"0\"/>\n"
                        + "\n"
                        + "    <group\n"
                        + "      android:translateX=\"23.481\"\n"
                        + "      android:translateY=\"18.71151\">\n"
                        + "      <path\n"
                        + "        android:fillColor=\"@android:color/white\"\n"
                        + "        android:pathData=\"@string/airplane_path\"/>\n"
                        + "    </group>\n"
                        + "    <group\n"
                        + "      android:translateX=\"23.481\"\n"
                        + "      android:translateY=\"18.71151\">\n"
                        + "      <path\n"
                        + "        android:fillColor=\"@android:color/white\"\n"
                        + "        android:pathData=\"" + toXmlAttributeValue(LONG_PATH) + "\"/>\n"
                        + "    </group>\n"
                        + "  </group>\n"
                        + "</vector>"),
                xml("res/values/paths.xml", ""
                        + "<resources>\n"
                        + "\n"
                        + "  <string name=\"airplane_path\">\n"
                        + SHORT_PATH
                        + "  </string>\n"
                        + "  <string name=\"airplane_cross_path\">" + SHORT_PATH + "</string>\n"
                        + "  <string name=\"airplane_mask_clip_path_disabled\">\n"
                        + LONG_PATH
                        + "  </string>\n"
                        + "  <string name=\"airplane_mask_clip_path_enabled\">\n"
                        + LONG_PATH
                        + "  </string>\n"
                        + "\n"
                        + "</resources>"),
                // Interpolator: don't flag long paths here
                xml("res/interpolator/my_interpolator.xml", ""
                        + "<pathInterpolator\n"
                        + "  xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "  android:pathData=\"" + toXmlAttributeValue(LONG_PATH) + "\"/>"))
                .incremental("res/drawable/my_vector.xml")
                .run()
                .maxLineLength(100)
                .expect(""
                        + "res/drawable/my_vector.xml:16: Warning: Very long vector path (1626 characters), which is bad for p…\n"
                        + "      android:pathData=\"@string/airplane_mask_clip_path_enabled\"/>\n"
                        + "                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/drawable/my_vector.xml:37: Warning: Very long vector path (1623 characters), which is bad for p…\n"
                        + "        android:pathData=\"M 37.8337860107,-40.3974914551 c 0,0 -35.8077850342,31.5523681641 -35.807…\n"
                        + "                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~…\n"
                        + "0 errors, 2 warnings\n");
    }

    public void testNoWarningWhenGradlePluginGeneratedImage() {
        lint().files(
                xml("res/drawable/my_vector.xml", ""
                        + "<vector\n"
                        + "  xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "  android:name=\"root\" android:width=\"48dp\" android:height=\"48dp\"\n"
                        + "  android:viewportHeight=\"48\" android:viewportWidth=\"48\">\n"
                        + "  <path\n"
                        + "    android:fillColor=\"@android:color/white\"\n"
                        + "    android:pathData=\"" + toXmlAttributeValue(LONG_PATH) + "\"/>\n"
                        + "</vector>"),
                gradle(""
                        + "buildscript {\n"
                        + "    dependencies {\n"
                        + "        classpath 'com.android.tools.build:gradle:2.2.0'\n"
                        + "    }\n"
                        + "}\n"))
                .run()
                .expectClean()
                .expectWarningCount(0) // redundant - just testing this infrastructure method
                .expectErrorCount(0);
    }

    public void testWarningWhenUsingSupportVectors() {
        lint().files(
                xml("res/drawable/my_vector.xml", ""
                        + "<vector\n"
                        + "  xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "  android:name=\"root\" android:width=\"48dp\" android:height=\"48dp\"\n"
                        + "  android:viewportHeight=\"48\" android:viewportWidth=\"48\">\n"
                        + "  <path\n"
                        + "    android:fillColor=\"@android:color/white\"\n"
                        + "    android:pathData=\"" + toXmlAttributeValue(LONG_PATH) + "\"/>\n"
                        + "</vector>"),
                gradle(""
                        + "buildscript {\n"
                        + "    dependencies {\n"
                        + "        classpath 'com.android.tools.build:gradle:2.0.0'\n"
                        + "    }\n"
                        + "}\n"
                        + "android.defaultConfig.vectorDrawables.useSupportLibrary = true\n"))
                .run()
                .maxLineLength(100)
                .expectWarningCount(1)
                .expectErrorCount(0)
                .expect(""
                        + "res/drawable/my_vector.xml:7: Warning: Very long vector path (1623 characters), which is bad for pe…\n"
                        + "    android:pathData=\"M 37.8337860107,-40.3974914551 c 0,0 -35.8077850342,31.5523681641 -35.8077850…\n"
                        + "                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~…\n"
                        + "0 errors, 1 warnings\n");
    }

    public void testInvalidScientificNotation() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=254147
        lint().files(
                xml("res/drawable/my_vector.xml", ""
                        + "<vector\n"
                        + "  xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "  android:name=\"root\" android:width=\"48dp\" android:height=\"48dp\"\n"
                        + "  android:viewportHeight=\"48\" android:viewportWidth=\"48\">\n"
                        + "  <path\n"
                        + "    android:fillColor=\"@android:color/white\"\n"
                        + "    android:pathData=\"m 1.05e-4,2.75448\" />\n"
                        + "</vector>"))
                .incremental("res/drawable/my_vector.xml")
                .run()
                .expect(""
                        + "res/drawable/my_vector.xml:7: Error: Avoid scientific notation (1.05e-4) in vector paths because it can lead to crashes on some devices [InvalidVectorPath]\n"
                        + "    android:pathData=\"m 1.05e-4,2.75448\" />\n"
                        + "                        ~~~~~~~\n"
                        + "1 errors, 0 warnings\n");
    }

    public void testInvalidScientificNotationWithResources() {
        lint().files(
                xml("res/drawable/my_vector.xml", ""
                        + "<vector\n"
                        + "  xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "  android:name=\"root\" android:width=\"48dp\" android:height=\"48dp\"\n"
                        + "  android:viewportHeight=\"48\" android:viewportWidth=\"48\">\n"
                        + "  <path\n"
                        + "    android:fillColor=\"@android:color/white\"\n"
                        + "    android:pathData=\"@string/my_vector_path\" />\n"
                        + "</vector>"),
                xml("res/values/strings.xml", ""
                        + "<resources>\n"
                        + "  <string name=\"my_vector_path\">m1.05e-4,2.75448</string>\n"
                        + "</resources>"))
                .incremental("res/drawable/my_vector.xml")
                .run()
                .expect(""
                        + "res/drawable/my_vector.xml:7: Error: Avoid scientific notation (1.05e-4) in vector paths because it can lead to crashes on some devices [InvalidVectorPath]\n"
                        + "    android:pathData=\"@string/my_vector_path\" />\n"
                        + "                      ~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n");
    }
}