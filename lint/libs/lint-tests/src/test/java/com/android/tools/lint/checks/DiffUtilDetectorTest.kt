/*
 * Copyright (C) 2018 The Android Open Source Project
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

class DiffUtilDetectorTest : AbstractCheckTest() {

    private val diffUtilStub = java(
        """
        package android.support.v7.util;
        public class DiffUtil {
            public abstract static class ItemCallback<T> {
                public abstract boolean areItemsTheSame(T oldItem, T newItem);
                public abstract boolean areContentsTheSame(T oldItem, T newItem);
            }
        }
        """
    ).indented()

    fun testIdentityEqualsOkay() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.support.v7.util.DiffUtil

                private val diffCallback = object : DiffUtil.ItemCallback<Cheese>() {
                    override fun areItemsTheSame(oldItem: Cheese, newItem: Cheese): Boolean =
                            oldItem.id === newItem.id

                    override fun areContentsTheSame(oldItem: Cheese, newItem: Cheese): Boolean =
                            oldItem.id === newItem.id
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                public class Cheese {
                    public int id;
                }
                """
            ).indented(),
            diffUtilStub
        ).run().expectClean()
    }

    fun testEqualsOkay() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.support.v7.util.DiffUtil

                private val diffCallback = object : DiffUtil.ItemCallback<Cheese>() {
                    override fun areItemsTheSame(oldItem: Cheese, newItem: Cheese): Boolean =
                            oldItem.id == newItem.id

                    override fun areContentsTheSame(oldItem: Cheese, newItem: Cheese): Boolean =
                            oldItem == newItem
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                public class Cheese {
                    public String id;

                    @Override
                    public boolean equals(Object o) {
                        if (this == o) return true;
                        if (o == null || getClass() != o.getClass()) return false;
                        Cheese cheese = (Cheese) o;
                        return id != null ? id.equals(cheese.id) : cheese.id == null;
                    }

                    @Override
                    public int hashCode() {
                        return id != null ? id.hashCode() : 0;
                    }
                }
                """
            ).indented(),
            diffUtilStub
        ).run().expectClean()
    }

    fun testDataClassEqualsOkay() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.support.v7.util.DiffUtil

                private val diffCallback = object : DiffUtil.ItemCallback<Cheese>() {
                    override fun areItemsTheSame(oldItem: Cheese, newItem: Cheese): Boolean =
                            oldItem.id == newItem.id

                    override fun areContentsTheSame(oldItem: Cheese, newItem: Cheese): Boolean =
                            oldItem == newItem
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg
                data class Cheese(var id: String? = null)
                """
            ).indented(),
            diffUtilStub
        ).run().expectClean()
    }

    fun testIdentityOperator() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.support.v7.util.DiffUtil

                private val diffCallback = object : DiffUtil.ItemCallback<Cheese>() {
                    override fun areItemsTheSame(oldItem: Cheese, newItem: Cheese): Boolean =
                            oldItem.id === newItem.id

                    override fun areContentsTheSame(oldItem: Cheese, newItem: Cheese): Boolean =
                            oldItem === newItem // ERROR
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.support.v7.util.DiffUtil;

                public class MyCallback extends DiffUtil.ItemCallback<Cheese> {
                    @Override
                    public boolean areItemsTheSame(Cheese oldItem, Cheese newItem) {
                        return oldItem.getId() == newItem.getId();
                    }

                    @Override
                    public boolean areContentsTheSame(Cheese oldItem, Cheese newItem) {
                        return oldItem == newItem;
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                public class Cheese {
                    public String id;
                }
                """
            ).indented(),
            diffUtilStub
        ).run().expect("""
            src/test/pkg/MyCallback.java:13: Error: Suspicious equality check: Did you mean .equals() instead of == ? [DiffUtilEquals]
                    return oldItem == newItem;
                                   ~~
            src/test/pkg/test.kt:10: Error: Suspicious equality check: Did you mean == instead of === ? [DiffUtilEquals]
                        oldItem === newItem // ERROR
                                ~~~
            2 errors, 0 warnings
            """
        )
    }

    fun testEqualsOperator() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.support.v7.util.DiffUtil

                private val diffCallback = object : DiffUtil.ItemCallback<Cheese>() {
                    override fun areItemsTheSame(oldItem: Cheese, newItem: Cheese): Boolean =
                            oldItem.id == newItem.id

                    override fun areContentsTheSame(oldItem: Cheese, newItem: Cheese): Boolean =
                            oldItem == newItem // ERROR
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.support.v7.util.DiffUtil;

                public class MyCallback extends DiffUtil.ItemCallback<Cheese> {
                    @Override
                    public boolean areItemsTheSame(Cheese oldItem, Cheese newItem) {
                        return oldItem.getId().getEquals(newItem.getId());
                    }

                    @Override
                    public boolean areContentsTheSame(Cheese oldItem, Cheese newItem) {
                        return oldItem.equals(newItem); // ERROR
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                public class Cheese {
                    public String id;
                }
                """
            ).indented(),
            diffUtilStub
        ).run().expect("""
            src/test/pkg/MyCallback.java:13: Error: Suspicious equality check: equals() is not implemented in test.pkg.Cheese [DiffUtilEquals]
                    return oldItem.equals(newItem); // ERROR
                                   ~~~~~~~~~~~~~~~
            src/test/pkg/test.kt:10: Error: Suspicious equality check: equals() is not implemented in Cheese [DiffUtilEquals]
                        oldItem == newItem // ERROR
                                ~~
            2 errors, 0 warnings
            """
        )
    }

    override fun getDetector(): Detector {
        return DiffUtilDetector()
    }
}
