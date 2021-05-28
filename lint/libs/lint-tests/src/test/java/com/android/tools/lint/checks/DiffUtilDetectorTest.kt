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
        compiled(
            "libs/diffutil.jar",
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
            ).indented(),
            0x675b248b,
            """
            androidx/recyclerview/widget/DiffUtil＄ItemCallback.class:
            H4sIAAAAAAAAAI1RzU7CQBCepYXaCoL4e/FgohE82MSLiRAuNUYTggeqB2/b
            dsDFsk22C+hjeTLx4AP4UMZZjNHAxc1mvp/MzM5kPz7f3gHgDHY9sKDuwIYD
            2wxKbSGF7jCwGs07BnaQJcig2hUSe5NxhCrkUWocrvBa4zgPH7DPx+QcNboj
            PuV+yuXQv4lGGOvWstO8Z+D2xVByPVFU5TbCsEXX+HXqGWRSo9S/bRvt8Hy5
            TWfZYuD1s4mK8VKYASsXYjC41SI9MZkMVnqY66ss1w7slMGGIoNTLhOVieTJ
            Vxg/xymqqcCZPxPJELX/U39g1gx4mkY8fmRQXpRSogpSnueYM6gtjsXg8F+v
            2PtQoH8wxwJm5qNYIrVHyAiLx6/AXogwcCiW5qZl02LgEvdIFWCVdPmvpkbf
            rAJrc6xCbY7rsEXoUabhm7b7BThTKZsQAgAA
            """,
            """
            androidx/recyclerview/widget/DiffUtil.class:
            H4sIAAAAAAAAAI1PTUsDQQx9abddu93a6t2DoKAeXPDiRXpZEQStBz/us7tp
            mTqdwuy01Z/lSfDgD/BHiZkFwaMJJHkveSH5+v74BHCO3QRtbMcYxhjF2CGk
            154XuTKmUOVzgNayy42qa64J3QtttR8T2kfHT4QoX1ZMGN5oy5PVomD3oAoj
            THK/XLmSr3QAg0s9nT56bU7naq0I/QnX/pbDeJ0ixhbhUNnKLXX1kjkuX0vD
            bq15k210NWOf/eoJo7AhM8rOsrtizqUnnP1LevD3LeyjJW8HI3G5QGJP0F6D
            gc7JO+itaScSuw0ZJP0mpxhIjoSNpAeZCVULnaj3A2+AtRtZAQAA
            """
        )
    )

    fun testIdentityEqualsOkay() {
        // Regression test for b/132234925
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.recyclerview.widget.DiffUtil

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

                import androidx.recyclerview.widget.DiffUtil
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

                import androidx.recyclerview.widget.DiffUtil

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

                import androidx.recyclerview.widget.DiffUtil

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

                import androidx.recyclerview.widget.DiffUtil

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

                import androidx.recyclerview.widget.DiffUtil;

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

                import androidx.recyclerview.widget.DiffUtil

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

                import androidx.recyclerview.widget.DiffUtil;

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

                import androidx.recyclerview.widget.DiffUtil

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
            *diffUtilStubs,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun test161584622() {
        // Regression test for https://issuetracker.google.com/issues/161584622
        lint().files(
            kotlin(
                """
                package com.example

                interface IModel {
                    // Workaround from https://issuetracker.google.com/issues/122928037
                    //override fun equals(other: Any?): Boolean
                }
                """
            ).indented(),
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

                import androidx.recyclerview.widget.DiffUtil

                class MyDiffUtilCallbackKotlin : DiffUtil.ItemCallback<IModel>() {

                    override fun areItemsTheSame(
                        oldItem: IModel,
                        newItem: IModel
                    ): Boolean =
                        oldItem is Model && newItem is Model && oldItem.id == newItem.id

                    override fun areContentsTheSame(
                        oldItem: IModel,
                        newItem: IModel
                    ): Boolean =
                        oldItem is Model && newItem is Model && oldItem == newItem
                }
                """
            ).indented(),
            java(
                """
                package com.example;

                import androidx.recyclerview.widget.DiffUtil;

                public class MyDiffUtilCallbackJava extends DiffUtil.ItemCallback<IModel> {

                    @Override public boolean areItemsTheSame(IModel oldItem, IModel newItem) {
                        if (oldItem instanceof Model && newItem instanceof Model) {
                            Model model1 = (Model)oldItem;
                            Model model2 = (Model)newItem;
                            return model1.getId().equals(model2.getId());
                        }
                        return false;
                    }
                    @Override public boolean areContentsTheSame(IModel oldItem, IModel newItem) {
                        return oldItem instanceof Model && newItem instanceof Model && oldItem.equals(newItem);
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
