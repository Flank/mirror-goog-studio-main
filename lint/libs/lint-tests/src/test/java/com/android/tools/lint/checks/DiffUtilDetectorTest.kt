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

    private val diffUtilStubs = arrayOf(
        java(
            """
            package android.support.v7.util;
            public class DiffUtil {
                public abstract static class ItemCallback<T> {
                    public abstract boolean areItemsTheSame(T oldItem, T newItem);
                    public abstract boolean areContentsTheSame(T oldItem, T newItem);
                }
            }
            """
        ).indented(),
        java(
            """
            package androidx.recyclerview.widget;
            public class DiffUtil {
                public abstract static class ItemCallback<T> {
                    public abstract boolean areItemsTheSame(T oldItem, T newItem);
                    public abstract boolean areContentsTheSame(T oldItem, T newItem);
                }
            }
            """
        ).indented()
    )

    fun testIdentityEqualsOkay() {
        // Regression test for b/132234925
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
            *diffUtilStubs
        ).run().expectClean()
    }

    fun testKotlinDataClasses() {
        // Regression test for https://issuetracker.google.com/122928037
        lint().files(
            kotlin(
                """
                package com.squareup.cash.diffutil

                import android.support.v7.util.DiffUtil
                import com.squareup.cash.lib.Foo

                class FooAdapter {
                  private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Foo>() {
                    override fun areItemsTheSame(
                      oldItem: Foo,
                      newItem: Foo
                    ): Boolean {
                      return oldItem.id == newItem.id
                    }

                    override fun areContentsTheSame(
                      oldItem: Foo,
                      newItem: Foo
                    ): Boolean {
                      return oldItem == newItem
                    }
                  }
                }
                """
            ),
            kotlin(
                """
                package com.squareup.cash.lib

                interface Foo {
                    val id: String
                    override fun equals(other: Any?): Boolean

                    data class Impl(
                        override val id: String
                    ) : Foo
                }
                """
            ).indented(),
            *diffUtilStubs
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
            *diffUtilStubs
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
            *diffUtilStubs
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
            *diffUtilStubs
        ).run().expect(
            """
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
            *diffUtilStubs
        ).run().expect(
            """
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

    fun testSealedClasses() {
        // Regression test for issue 132234925
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.support.v7.util.DiffUtil

                sealed class SealedClass {
                    data class ClassOne(val data: Int) : SealedClass()
                    data class ClassTwo(val value: String) : SealedClass()

                    object DiffCallback : DiffUtil.ItemCallback<SealedClass>() {
                        override fun areItemsTheSame(oldItem: SealedClass, newItem: SealedClass): Boolean {
                            return oldItem === newItem
                        }

                        override fun areContentsTheSame(oldItem: SealedClass, newItem: SealedClass): Boolean {
                            // Wrong: Error: Suspicious equality check: equals() is not implemented in SealedClass [DiffUtilEquals]
                            return oldItem == newItem // OK
                        }
                    }
                }
                """
            ).indented(),
            *diffUtilStubs
        ).run().expectClean()
    }

    fun testKnownInstance() {
        // Regression test for https://issuetracker.google.com/161584622
        // [lint] DiffUtilEquals false positive when type is exactly known
        lint().files(
            kotlin(
                """
                package com.example

                data class Model(
                    val id: String,
                    val content: String
                ) : IModel
                """
            ).indented(),
            kotlin(
                """
                package com.example

                interface IModel {
                    // Workaround from https://issuetracker.google.com/issues/122928037
                    //override fun equals(other: Any?): Boolean
                }
                """
            ).indented(),
            java(
                """
                package com.example;

                import androidx.annotation.NonNull;
                import androidx.recyclerview.widget.DiffUtil;

                public class MyDiffUtilCallbackJava extends DiffUtil.ItemCallback<IModel> {

                        @Override public boolean areItemsTheSame(@NonNull IModel oldItem, @NonNull IModel newItem) {
                                if (oldItem instanceof Model && newItem instanceof Model) {
                                        Model model1 = (Model)oldItem;
                                        Model model2 = (Model)newItem;
                                        return model1.getId().equals(model2.getId());
                                }
                                return false;
                        }
                        @Override public boolean areContentsTheSame(@NonNull IModel oldItem, @NonNull IModel newItem) {
                                if (oldItem instanceof Model && newItem instanceof Model) {
                                        return oldItem.equals(newItem);
                                }
                                return false;
                        }
                }
                """
            ).indented(),
            *diffUtilStubs
        ).run().expectClean()
    }

    override fun getDetector(): Detector {
        return DiffUtilDetector()
    }
}
