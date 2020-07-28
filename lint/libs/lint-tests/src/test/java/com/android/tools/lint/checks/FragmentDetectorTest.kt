/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class FragmentDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return FragmentDetector()
    }

    fun testBasic() {
        val expected =
            """
            src/test/pkg/FragmentTest.java:10: Error: This fragment class should be public (test.pkg.FragmentTest.Fragment1) [ValidFragment]
                private static class Fragment1 extends Fragment {
                                     ~~~~~~~~~
            src/test/pkg/FragmentTest.java:15: Error: This fragment inner class should be static (test.pkg.FragmentTest.Fragment2) [ValidFragment]
                public class Fragment2 extends Fragment {
                             ~~~~~~~~~
            src/test/pkg/FragmentTest.java:21: Error: The default constructor must be public [ValidFragment]
                    private Fragment3() {
                            ~~~~~~~~~
            src/test/pkg/FragmentTest.java:26: Error: This fragment should provide a default constructor (a public constructor with no arguments) (test.pkg.FragmentTest.Fragment4) [ValidFragment]
                public static class Fragment4 extends Fragment {
                                    ~~~~~~~~~
            src/test/pkg/FragmentTest.java:27: Error: Avoid non-default constructors in fragments: use a default constructor plus Fragment#setArguments(Bundle) instead [ValidFragment]
                    private Fragment4(int sample) {
                            ~~~~~~~~~
            src/test/pkg/FragmentTest.java:36: Error: Avoid non-default constructors in fragments: use a default constructor plus Fragment#setArguments(Bundle) instead [ValidFragment]
                    public Fragment5(int sample) {
                           ~~~~~~~~~
            6 errors, 0 warnings
            """

        lint().files(
            java(
                "src/test/pkg/FragmentTest.java",
                """
                package test.pkg;

                import android.annotation.SuppressLint;
                import android.app.Fragment;

                @SuppressWarnings("unused")
                public class FragmentTest {

                    // Should be public
                    private static class Fragment1 extends Fragment {

                    }

                    // Should be static
                    public class Fragment2 extends Fragment {

                    }

                    // Should have a public constructor
                    public static class Fragment3 extends Fragment {
                        private Fragment3() {
                        }
                    }

                    // Should have a public constructor with no arguments
                    public static class Fragment4 extends Fragment {
                        private Fragment4(int sample) {
                        }
                    }

                    // Should *only* have the default constructor, not the
                    // multi-argument one
                    public static class Fragment5 extends Fragment {
                        public Fragment5() {
                        }
                        public Fragment5(int sample) {
                        }
                    }

                    // Suppressed
                    @SuppressLint("ValidFragment")
                    public static class Fragment6 extends Fragment {
                        private Fragment6() {
                        }
                    }

                    public static class ValidFragment1 extends Fragment {
                        public ValidFragment1() {
                        }
                    }

                    // (Not a fragment)
                    private class NotAFragment {
                    }

                    // Ok: Has implicit constructor
                    public static class Fragment7 extends Fragment {
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testAnonymousInnerClass() {
        val expected =
            """
            src/test/pkg/Parent.java:7: Error: Fragments should be static such that they can be re-instantiated by the system, and anonymous classes are not static [ValidFragment]
                    return new Fragment() {
                               ~~~~~~~~
            1 errors, 0 warnings"""

        lint().files(
            java(
                "src/test/pkg/Parent.java",
                """
                    package test.pkg;

                    import android.app.Fragment;

                    public class Parent {
                        public Fragment method() {
                            return new Fragment() {
                            };
                        }
                    }
                    """
            ).indented()
        ).run().expect(expected)
    }

    fun testAndroidXFragment() {
        // Regression test for
        // 119675579: Remove the Fragments must have a no-arg constructor warning when using 1.1.0+
        val expected =
            """
            src/test/pkg/Parent.java:5: Error: This fragment should provide a default constructor (a public constructor with no arguments) (test.pkg.FragmentTest.Fragment1) [ValidFragment]
                public static class Fragment1 extends android.support.v4.app.Fragment {
                                    ~~~~~~~~~
            src/test/pkg/Parent.java:6: Error: Avoid non-default constructors in fragments: use a default constructor plus Fragment#setArguments(Bundle) instead [ValidFragment]
                    private Fragment1(int sample) { // ERROR
                            ~~~~~~~~~
            2 errors, 0 warnings
        """

        lint().files(
            java(
                "src/test/pkg/Parent.java",
                """
                    package test.pkg;

                    public class FragmentTest {
                        // Should have a public constructor with no arguments
                        public static class Fragment1 extends android.support.v4.app.Fragment {
                            private Fragment1(int sample) { // ERROR
                            }
                        }
                        // androidx is okay
                        public static class Fragment2 extends androidx.fragment.app.Fragment {
                            private Fragment2(int sample) { // OK
                            }
                        }
                    }
                    """
            ).indented(),
            java(
                """
                package android.support.v4.app;
                // Stub
                public class Fragment {
                }
                """
            ),
            java(
                """
                package androidx.fragment.app;
                // Stub
                public class Fragment {
                }
                """
            )
        ).run().expect(expected)
    }
}
