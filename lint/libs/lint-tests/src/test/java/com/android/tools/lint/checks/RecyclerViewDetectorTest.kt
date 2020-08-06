/*
 * Copyright (C) 2015 The Android Open Source Project
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

class RecyclerViewDetectorTest : AbstractCheckTest() {

    private val recyclerViewStub = java(
        "src/android/support/v7/widget/RecyclerView.java",
        """
            package android.support.v7.widget;

            import android.content.Context;
            import android.util.AttributeSet;
            import android.view.View;
            import java.util.List;

            // Just a stub for lint unit tests
            public class RecyclerView extends View {
                public RecyclerView(Context context, AttributeSet attrs) {
                    super(context, attrs);
                }

                public abstract static class ViewHolder {
                    public ViewHolder(View itemView) {
                    }
                }

                public abstract static class Adapter<VH extends ViewHolder> {
                    public abstract void onBindViewHolder(VH holder, int position);
                    public void onBindViewHolder(VH holder, int position, List<Object> payloads) {
                    }
                }
            }
            """
    ).indented()

    fun testFixedPosition() {
        val expected =
            """
            src/test/pkg/RecyclerViewTest.java:69: Error: Do not treat position as fixed; only use immediately and call holder.getAdapterPosition() to look it up later [RecyclerView]
                    public void onBindViewHolder(ViewHolder holder, int position) {
                                                                    ~~~~~~~~~~~~
            src/test/pkg/RecyclerViewTest.java:82: Error: Do not treat position as fixed; only use immediately and call holder.getAdapterPosition() to look it up later [RecyclerView]
                    public void onBindViewHolder(ViewHolder holder, final int position) {
                                                                    ~~~~~~~~~~~~~~~~~~
            src/test/pkg/RecyclerViewTest.java:102: Error: Do not treat position as fixed; only use immediately and call holder.getAdapterPosition() to look it up later [RecyclerView]
                    public void onBindViewHolder(ViewHolder holder, final int position) {
                                                                    ~~~~~~~~~~~~~~~~~~
            src/test/pkg/RecyclerViewTest.java:111: Error: Do not treat position as fixed; only use immediately and call holder.getAdapterPosition() to look it up later [RecyclerView]
                    public void onBindViewHolder(ViewHolder holder, final int position, List<Object> payloads) {
                                                                    ~~~~~~~~~~~~~~~~~~
            4 errors, 0 warnings
            """
        lint().files(
            java(
                """
                package test.pkg;

                import android.support.v7.widget.RecyclerView;
                import android.view.View;
                import android.widget.TextView;

                import java.util.List;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "unused"})
                public class RecyclerViewTest {
                    // From https://developer.android.com/training/material/lists-cards.html
                    public abstract static class Test1 extends RecyclerView.Adapter<Test1.ViewHolder> {
                        private String[] mDataset;
                        public static class ViewHolder extends RecyclerView.ViewHolder {
                            public TextView mTextView;
                            public ViewHolder(TextView v) {
                                super(v);
                                mTextView = v;
                            }
                        }

                        public Test1(String[] myDataset) {
                            mDataset = myDataset;
                        }

                        @Override
                        public void onBindViewHolder(ViewHolder holder, int position) {
                            holder.mTextView.setText(mDataset[position]); // OK
                        }
                    }

                    public abstract static class Test2 extends RecyclerView.Adapter<Test2.ViewHolder> {
                        public static class ViewHolder extends RecyclerView.ViewHolder {
                            public ViewHolder(View v) {
                                super(v);
                            }
                        }

                        @Override
                        public void onBindViewHolder(ViewHolder holder, int position) {
                            // OK
                        }
                    }

                    public abstract static class Test3 extends RecyclerView.Adapter<Test3.ViewHolder> {
                        public static class ViewHolder extends RecyclerView.ViewHolder {
                            public ViewHolder(View v) {
                                super(v);
                            }
                        }

                        @Override
                        public void onBindViewHolder(ViewHolder holder, final int position) {
                            // OK - final, but not referenced

                        }
                    }

                    public abstract static class Test4 extends RecyclerView.Adapter<Test4.ViewHolder> {
                        private int myCachedPosition;

                        public static class ViewHolder extends RecyclerView.ViewHolder {
                            public ViewHolder(View v) {
                                super(v);
                            }
                        }

                        @Override
                        public void onBindViewHolder(ViewHolder holder, int position) {
                            myCachedPosition = position; // ERROR: escapes
                        }
                    }

                    public abstract static class Test5 extends RecyclerView.Adapter<Test5.ViewHolder> {
                        public static class ViewHolder extends RecyclerView.ViewHolder {
                            public ViewHolder(View v) {
                                super(v);
                            }
                        }

                        @Override
                        public void onBindViewHolder(ViewHolder holder, final int position) {
                            new Runnable() {
                                @Override public void run() {
                                    System.out.println(position); // ERROR: escapes
                                }
                            }.run();
                        }
                    }

                    // https://code.google.com/p/android/issues/detail?id=172335
                    public abstract static class Test6 extends RecyclerView.Adapter<Test6.ViewHolder> {
                        List<String> myData;
                        public static class ViewHolder extends RecyclerView.ViewHolder {
                            private View itemView;
                            public ViewHolder(View v) {
                                super(v);
                            }
                        }

                        @Override
                        public void onBindViewHolder(ViewHolder holder, final int position) {
                            holder.itemView.setOnClickListener(new View.OnClickListener() {
                                public void onClick(View view) {
                                    myData.get(position); // ERROR
                                }
                            });
                        }

                        @Override
                        public void onBindViewHolder(ViewHolder holder, final int position, List<Object> payloads) {
                            holder.itemView.setOnClickListener(new View.OnClickListener() {
                                public void onClick(View view) {
                                    myData.get(position); // ERROR
                                }
                            });
                        }
                    }
                }
                """
            ).indented(),
            recyclerViewStub
        ).run().expect(expected)
    }

    fun testExecuteBindings() {
        val expected =
            """
            src/test/pkg/RecyclerViewTest2.java:32: Error: You must call holder.dataBinder.executePendingBindings() before the onBind method exits, otherwise, the DataBinding library will update the UI in the next animation frame causing a delayed update & potential jumps if the item resizes. [PendingBindings]
                        holder.dataBinder.someMethod(); // ERROR - no pending call
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/RecyclerViewTest2.java:40: Error: You must call holder.dataBinder.executePendingBindings() before the onBind method exits, otherwise, the DataBinding library will update the UI in the next animation frame causing a delayed update & potential jumps if the item resizes. [PendingBindings]
                        holder.dataBinder.someMethod(); // ERROR: After call
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/RecyclerViewTest2.java:48: Error: You must call holder.dataBinder.executePendingBindings() before the onBind method exits, otherwise, the DataBinding library will update the UI in the next animation frame causing a delayed update & potential jumps if the item resizes. [PendingBindings]
                            holder.dataBinder.someMethod(); // ERROR: can't reach pending
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/RecyclerViewTest2.java:116: Error: You must call holder.dataBinder.executePendingBindings() before the onBind method exits, otherwise, the DataBinding library will update the UI in the next animation frame causing a delayed update & potential jumps if the item resizes. [PendingBindings]
                            holder.dataBinder.someMethod(); // ERROR
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/RecyclerViewTest2.java:139: Error: You must call holder.dataBinder.executePendingBindings() before the onBind method exits, otherwise, the DataBinding library will update the UI in the next animation frame causing a delayed update & potential jumps if the item resizes. [PendingBindings]
                                holder.dataBinder.someMethod(); // ERROR: no fallthrough
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            5 errors, 0 warnings
            """
        lint().files(
            java(
                """
                package test.pkg;

                import android.support.v7.widget.RecyclerView;
                import android.view.View;
                import android.widget.TextView;

                @SuppressWarnings({"unused", "ConstantIfStatement", "ConstantConditions", "StatementWithEmptyBody"})
                public class RecyclerViewTest2 {
                    // From https://developer.android.com/training/material/lists-cards.html
                    public abstract static class AbstractTest extends RecyclerView.Adapter<AbstractTest.ViewHolder> {
                        public static class ViewHolder extends RecyclerView.ViewHolder {
                            public TextView mTextView;
                            public ViewDataBinding dataBinder;
                            public ViewHolder(TextView v) {
                                super(v);
                                mTextView = v;
                            }
                        }
                    }

                    public abstract static class Test1 extends AbstractTest {
                        @Override
                        public void onBindViewHolder(ViewHolder holder, int position) {
                            holder.dataBinder.someMethod(); // OK
                            holder.dataBinder.executePendingBindings();
                        }
                    }

                    public abstract static class Test2 extends AbstractTest {
                        @Override
                        public void onBindViewHolder(ViewHolder holder, int position) {
                            holder.dataBinder.someMethod(); // ERROR - no pending call
                        }
                    }

                    public abstract static class Test3 extends AbstractTest {
                        @Override
                        public void onBindViewHolder(ViewHolder holder, int position) {
                            holder.dataBinder.executePendingBindings();
                            holder.dataBinder.someMethod(); // ERROR: After call
                        }
                    }

                    public abstract static class Test4 extends AbstractTest {
                        @Override
                        public void onBindViewHolder(ViewHolder holder, int position) {
                            if (true) {
                                holder.dataBinder.someMethod(); // ERROR: can't reach pending
                            } else {
                                holder.dataBinder.executePendingBindings();
                            }
                        }
                    }

                    public abstract static class Test5 extends AbstractTest {
                        @Override
                        public void onBindViewHolder(ViewHolder holder, int position) {
                            holder.dataBinder.someMethod(); // OK
                            if (true) {
                                if (true) {
                                    if (false) {

                                    } else {
                                        holder.dataBinder.executePendingBindings();
                                    }
                                }
                            }
                        }
                    }

                    /* We don't yet track variable reassignment to compute equivalent data binders
                    public abstract static class Test6 extends AbstractTest {
                        @Override
                        public void onBindViewHolder(ViewHolder holder, int position) {
                            holder.dataBinder.someMethod(); // OK
                            ViewDataBinding dataBinder = holder.dataBinder;
                            dataBinder.executePendingBindings();
                        }
                    }
                    */

                    public abstract static class Test7 extends AbstractTest {
                        @Override
                        public void onBindViewHolder(ViewHolder holder, int position) {
                            if (true) {
                                holder.dataBinder.someMethod(); // OK
                            }
                            holder.dataBinder.executePendingBindings();
                        }
                    }

                    public abstract static class Test8 extends AbstractTest {
                        @Override
                        public void onBindViewHolder(ViewHolder holder, int position) {
                            holder.dataBinder.someMethod(); // OK
                            synchronized (this) {
                                holder.dataBinder.executePendingBindings();
                            }
                        }
                    }

                    public abstract static class Test9 extends AbstractTest {
                        @SuppressWarnings("UnusedLabel")
                        @Override
                        public void onBindViewHolder(ViewHolder holder, int position) {
                            holder.dataBinder.someMethod(); // OK
                        myLabel:
                            holder.dataBinder.executePendingBindings();
                        }
                    }

                    public abstract static class Test10 extends AbstractTest {
                        @Override
                        public void onBindViewHolder(ViewHolder holder, int position) {
                            if (true) {
                                holder.dataBinder.someMethod(); // ERROR
                                return;
                            }
                            holder.dataBinder.executePendingBindings();
                        }
                    }

                    public abstract static class Test11 extends AbstractTest {
                        @Override
                        public void onBindViewHolder(ViewHolder holder, int position) {
                            switch (position) {
                                case 1: holder.dataBinder.someMethod(); // OK: fallthrough
                                case 2: holder.dataBinder.executePendingBindings();
                            }
                        }
                    }

                    public abstract static class Test12 extends AbstractTest {
                        @Override
                        public void onBindViewHolder(ViewHolder holder, int position) {
                            switch (position) {
                                case 1:
                                    holder.dataBinder.someMethod(); // Not last: don't flag
                                    holder.dataBinder.someMethod(); // ERROR: no fallthrough
                                    break;
                                case 2:
                                    holder.dataBinder.executePendingBindings();
                            }
                        }
                    }

                    public abstract static class Test13 extends AbstractTest {
                        @Override
                        public void onBindViewHolder(ViewHolder holder, int position) {
                            do {
                                holder.dataBinder.someMethod(); // OK
                                holder.dataBinder.executePendingBindings();
                            } while (position-- >= 0);
                        }
                    }

                    public static class ViewDataBinding {
                        private View root;

                        public void someMethod() {
                        }
                        public void executePendingBindings() {
                        }

                        public View getRoot() {
                            return root;
                        }
                    }
                }
                """
            ).indented(),
            recyclerViewStub
        ).run().expect(expected)
    }

    override fun getDetector(): Detector {
        return RecyclerViewDetector()
    }
}
