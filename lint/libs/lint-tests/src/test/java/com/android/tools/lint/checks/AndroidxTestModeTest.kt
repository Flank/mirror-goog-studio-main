/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import junit.framework.TestCase.assertEquals
import org.intellij.lang.annotations.Language
import org.junit.Test

class AndroidxTestModeTest {
    private fun migrate(testFile: TestFile): String {
        return AndroidxTestMode().migrateToAndroidX(testFile.contents)
    }

    @Test
    fun testBasic() {
        @Language("java")
        val java = java(
            """
            package test.pkg;

            import android.support.v4.app.DialogFragment;
            import android.support.v4.app.Fragment;
            import android.support.v4.app.FragmentManager;
            import androidx.fragment.app.FragmentTransaction;

            public class CommitTest2 {
                private void test() {
                    FragmentTransaction transaction = getFragmentManager().beginTransaction();
                    MyDialogFragment fragment = new MyDialogFragment();
                    fragment.show(transaction, "MyTag");
                }

                private FragmentManager getFragmentManager() {
                    return null;
                }
            }
          """
        ).indented()

        @Suppress("DanglingJavadoc", "PointlessBooleanExpression", "ConstantConditions")
        @Language("java")
        val expected = """
            package test.pkg;

            import androidx.fragment.app.DialogFragment;
            import androidx.fragment.app.Fragment;
            import androidx.fragment.app.FragmentManager;
            import androidx.fragment.app.FragmentTransaction;

            public class CommitTest2 {
                private void test() {
                    FragmentTransaction transaction = getFragmentManager().beginTransaction();
                    MyDialogFragment fragment = new MyDialogFragment();
                    fragment.show(transaction, "MyTag");
                }

                private FragmentManager getFragmentManager() {
                    return null;
                }
            }
        """.trimIndent().trim()
        val modified = migrate(java)
        assertEquals(expected, modified)
    }
}
