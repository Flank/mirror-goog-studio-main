/*
 * Copyright (C) 2016 The Android Open Source Project
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

public class ObjectAnimatorDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ObjectAnimatorDetector();
    }

    public void test() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "src/test/pkg/AnimatorTest.java:21: Error: The setter for this property does not match the expected signature (public void setProp2(int arg) [ObjectAnimatorBinding]\n"
                + "        ObjectAnimator.ofInt(myObject, \"prop2\", 0, 1, 2, 5).start();\n"
                + "                                       ~~~~~~~\n"
                + "    src/test/pkg/AnimatorTest.java:58: Property setter here\n"
                + "src/test/pkg/AnimatorTest.java:24: Error: Could not find property setter method setUnknown on test.pkg.AnimatorTest.MyObject [ObjectAnimatorBinding]\n"
                + "        ObjectAnimator.ofInt(myObject, \"unknown\", 0, 1, 2, 5).start();\n"
                + "                                       ~~~~~~~~~\n"
                + "src/test/pkg/AnimatorTest.java:27: Error: The setter for this property (test.pkg.AnimatorTest.MyObject.setProp3) should not be static [ObjectAnimatorBinding]\n"
                + "        ObjectAnimator.ofInt(myObject, \"prop3\", 0, 1, 2, 5).start();\n"
                + "                                       ~~~~~~~\n"
                + "    src/test/pkg/AnimatorTest.java:61: Property setter here\n"
                + "src/test/pkg/AnimatorTest.java:40: Error: Could not find property setter method setAlpha2 on android.widget.Button [ObjectAnimatorBinding]\n"
                + "        ObjectAnimator.ofArgb(button, \"alpha2\", 1, 5); // Missing\n"
                + "                                      ~~~~~~~~\n"
                + "src/test/pkg/AnimatorTest.java:55: Warning: This method is accessed from an ObjectAnimator so it should be annotated with @Keep to ensure that it is discarded or renamed in release builds [AnimatorKeep]\n"
                + "        public void setProp1(int x) {\n"
                + "                    ~~~~~~~~~~~~~~\n"
                + "    src/test/pkg/AnimatorTest.java:15: ObjectAnimator usage here\n"
                + "src/test/pkg/AnimatorTest.java:58: Warning: This method is accessed from an ObjectAnimator so it should be annotated with @Keep to ensure that it is discarded or renamed in release builds [AnimatorKeep]\n"
                + "        private void setProp2(float x) {\n"
                + "                     ~~~~~~~~~~~~~~~~\n"
                + "    src/test/pkg/AnimatorTest.java:47: ObjectAnimator usage here\n"
                + "4 errors, 2 warnings\n",
            lintProject(
                    java(""
                            + "package test.pkg;\n"
                            + "\n"
                            + "\n"
                            + "import android.animation.ObjectAnimator;\n"
                            + "import android.animation.PropertyValuesHolder;\n"
                            + "import android.support.annotation.Keep;\n"
                            + "import android.view.View;\n"
                            + "import android.widget.Button;\n"
                            + "\n"
                            + "@SuppressWarnings(\"unused\")\n"
                            + "public class AnimatorTest {\n"
                            + "\n"
                            + "    public void testObjectAnimator(Button button) {\n"
                            + "        Object myObject = new MyObject();\n"
                            + "        ObjectAnimator animator1 = ObjectAnimator.ofInt(myObject, \"prop1\", 0, 1, 2, 5);\n"
                            + "        animator1.setDuration(10);\n"
                            + "        animator1.start();\n"
                            + "\n"
                            + "\n"
                            + "        // Incorrect type (float parameter) warning\n"
                            + "        ObjectAnimator.ofInt(myObject, \"prop2\", 0, 1, 2, 5).start();\n"
                            + "\n"
                            + "        // Missing method warning\n"
                            + "        ObjectAnimator.ofInt(myObject, \"unknown\", 0, 1, 2, 5).start();\n"
                            + "\n"
                            + "        // Static method warning\n"
                            + "        ObjectAnimator.ofInt(myObject, \"prop3\", 0, 1, 2, 5).start();\n"
                            + "\n"
                            + "        // OK: Already marked @Keep\n"
                            + "        ObjectAnimator.ofInt(myObject, \"prop4\", 0, 1, 2, 5).start();\n"
                            + "\n"
                            + "        // OK: multi int\n"
                            + "        ObjectAnimator.ofMultiInt(myObject, \"prop4\", new int[0][]).start();\n"
                            + "\n"
                            + "        // OK: multi int\n"
                            + "        ObjectAnimator.ofMultiFloat(myObject, \"prop5\", new float[0][]).start();\n"
                            + "\n"
                            + "        // View stuff\n"
                            + "        ObjectAnimator.ofFloat(button, \"alpha\", 1, 5); // TODO: Warn about better method?, e.g. button.animate().alpha(...)\n"
                            + "        ObjectAnimator.ofArgb(button, \"alpha2\", 1, 5); // Missing\n"
                            + "    }\n"
                            + "\n"
                            + "    public void testPropertyHolder() {\n"
                            + "        Object myObject = new MyObject();\n"
                            + "\n"
                            + "        PropertyValuesHolder p1 = PropertyValuesHolder.ofInt(\"prop1\", 50);\n"
                            + "        PropertyValuesHolder p2 = PropertyValuesHolder.ofFloat(\"prop2\", 100f);\n"
                            + "        ObjectAnimator.ofPropertyValuesHolder(myObject, p1, p2).start();\n"
                            + "        ObjectAnimator.ofPropertyValuesHolder(myObject,\n"
                            + "                PropertyValuesHolder.ofInt(\"prop1\", 50),\n"
                            + "                PropertyValuesHolder.ofFloat(\"prop2\", 100f)).start();\n"
                            + "    }\n"
                            + "\n"
                            + "    private static class MyObject {\n"
                            + "        public void setProp1(int x) {\n"
                            + "        }\n"
                            + "\n"
                            + "        private void setProp2(float x) {\n"
                            + "        }\n"
                            + "\n"
                            + "        public static void setProp3(int x) {\n"
                            + "        }\n"
                            + "\n"
                            + "        @Keep\n"
                            + "        public void setProp4(int[] x) {\n"
                            + "        }\n"
                            + "\n"
                            + "        @Keep\n"
                            + "        public void setProp5(float[] x) {\n"
                            + "        }\n"
                            + "\n"
                            + "        @Keep\n"
                            + "        public void setProp4(int x) {\n"
                            + "        }\n"
                            + "    }\n"
                            + "}"),
                    java("src/android/support/annotation/Keep.java", ""
                            + "package android.support.annotation;\n"
                            + "import java.lang.annotation.Retention;\n"
                            + "import java.lang.annotation.Target;\n"
                            + "import static java.lang.annotation.ElementType.*;\n"
                            + "import static java.lang.annotation.RetentionPolicy.CLASS;\n"
                            + "@Retention(CLASS)\n"
                            + "@Target({PACKAGE,TYPE,ANNOTATION_TYPE,CONSTRUCTOR,METHOD,FIELD})\n"
                            + "public @interface Keep {\n"
                            + "}")
            ));
    }

    public void testFlow() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "src/test/pkg/AnimatorFlowTest.java:10: Error: The setter for this property does not match the expected signature (public void setProp1(int arg) [ObjectAnimatorBinding]\n"
                + "        PropertyValuesHolder p1 = PropertyValuesHolder.ofInt(\"prop1\", 50); // ERROR\n"
                + "                                                             ~~~~~~~\n"
                + "    src/test/pkg/AnimatorFlowTest.java:37: Property setter here\n"
                + "src/test/pkg/AnimatorFlowTest.java:14: Error: The setter for this property does not match the expected signature (public void setProp1(int arg) [ObjectAnimatorBinding]\n"
                + "    private PropertyValuesHolder field = PropertyValuesHolder.ofInt(\"prop1\", 50); // ERROR\n"
                + "                                                                    ~~~~~~~\n"
                + "    src/test/pkg/AnimatorFlowTest.java:37: Property setter here\n"
                + "src/test/pkg/AnimatorFlowTest.java:21: Error: The setter for this property does not match the expected signature (public void setProp1(int arg) [ObjectAnimatorBinding]\n"
                + "        p1 = PropertyValuesHolder.ofInt(\"prop1\", 50); // ERROR\n"
                + "                                        ~~~~~~~\n"
                + "    src/test/pkg/AnimatorFlowTest.java:37: Property setter here\n"
                + "src/test/pkg/AnimatorFlowTest.java:26: Error: The setter for this property does not match the expected signature (public void setProp1(int arg) [ObjectAnimatorBinding]\n"
                + "        PropertyValuesHolder p1 = PropertyValuesHolder.ofInt(\"prop1\", 50); // ERROR\n"
                + "                                                             ~~~~~~~\n"
                + "    src/test/pkg/AnimatorFlowTest.java:37: Property setter here\n"
                + "src/test/pkg/AnimatorFlowTest.java:33: Error: The setter for this property does not match the expected signature (public void setProp1(int arg) [ObjectAnimatorBinding]\n"
                + "        ObjectAnimator.ofPropertyValuesHolder(new MyObject(), PropertyValuesHolder.ofInt(\"prop1\", 50)).start(); // ERROR\n"
                + "                                                                                         ~~~~~~~\n"
                + "    src/test/pkg/AnimatorFlowTest.java:37: Property setter here\n"
                + "5 errors, 0 warnings\n",
                lintProject(
                        java(""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.animation.ObjectAnimator;\n"
                                + "import android.animation.PropertyValuesHolder;\n"
                                + "\n"
                                + "@SuppressWarnings(\"unused\")\n"
                                + "public class AnimatorFlowTest {\n"
                                + "\n"
                                + "    public void testVariableInitializer() {\n"
                                + "        PropertyValuesHolder p1 = PropertyValuesHolder.ofInt(\"prop1\", 50); // ERROR\n"
                                + "        ObjectAnimator.ofPropertyValuesHolder(new MyObject(), p1).start();\n"
                                + "    }\n"
                                + "\n"
                                + "    private PropertyValuesHolder field = PropertyValuesHolder.ofInt(\"prop1\", 50); // ERROR\n"
                                + "    public void testFieldInitializer() {\n"
                                + "        ObjectAnimator.ofPropertyValuesHolder(new MyObject(), field).start();\n"
                                + "    }\n"
                                + "\n"
                                + "    public void testAssignment() {\n"
                                + "        PropertyValuesHolder p1;\n"
                                + "        p1 = PropertyValuesHolder.ofInt(\"prop1\", 50); // ERROR\n"
                                + "        ObjectAnimator.ofPropertyValuesHolder(new MyObject(), p1).start();\n"
                                + "    }\n"
                                + "\n"
                                + "    public void testReassignment() {\n"
                                + "        PropertyValuesHolder p1 = PropertyValuesHolder.ofInt(\"prop1\", 50); // ERROR\n"
                                + "        PropertyValuesHolder p2 = p1;\n"
                                + "        p1 = null;\n"
                                + "        ObjectAnimator.ofPropertyValuesHolder(new MyObject(), p2).start();\n"
                                + "    }\n"
                                + "\n"
                                + "    public void testInline() {\n"
                                + "        ObjectAnimator.ofPropertyValuesHolder(new MyObject(), PropertyValuesHolder.ofInt(\"prop1\", 50)).start(); // ERROR\n"
                                + "    }\n"
                                + "\n"
                                + "    private static class MyObject {\n"
                                + "        public void setProp1(double z) { // ERROR\n"
                                + "        }\n"
                                + "    }\n"
                                + "}")
                ));
    }
}
