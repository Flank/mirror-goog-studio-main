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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class ViewHolderDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return ViewHolderDetector()
    }

    fun testBasic() {
        //noinspection all // Sample code
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Context;
                import android.view.LayoutInflater;
                import android.view.View;
                import android.view.ViewGroup;
                import android.widget.BaseAdapter;
                import android.widget.LinearLayout;
                import android.widget.TextView;

                import java.util.ArrayList;

                @SuppressWarnings({"ConstantConditions", "UnusedDeclaration"})
                public abstract class ViewHolderTest extends BaseAdapter {
                    @Override
                    public int getCount() {
                        return 0;
                    }

                    @Override
                    public Object getItem(int position) {
                        return null;
                    }

                    @Override
                    public long getItemId(int position) {
                        return 0;
                    }

                    public static class Adapter1 extends ViewHolderTest {
                        @Override
                        public View getView(int position, View convertView, ViewGroup parent) {
                            return null;
                        }
                    }

                    public static class Adapter2 extends ViewHolderTest {
                        LayoutInflater mInflater;

                        public View getView(int position, View convertView, ViewGroup parent) {
                            // Should use View Holder pattern here
                            convertView = mInflater.inflate(R.layout.your_layout, null);

                            TextView text = (TextView) convertView.findViewById(R.id.text);
                            text.setText("Position " + position);

                            return convertView;
                        }
                    }

                    public static class Adapter3 extends ViewHolderTest {
                        LayoutInflater mInflater;

                        public View getView(int position, View convertView, ViewGroup parent) {
                            // Already using View Holder pattern
                            if (convertView == null) {
                                convertView = mInflater.inflate(R.layout.your_layout, null);
                            }

                            TextView text = (TextView) convertView.findViewById(R.id.text);
                            text.setText("Position " + position);

                            return convertView;
                        }
                    }

                    public static class Adapter4 extends ViewHolderTest {
                        LayoutInflater mInflater;

                        public View getView(int position, View convertView, ViewGroup parent) {
                            // Already using View Holder pattern
                            //noinspection StatementWithEmptyBody
                            if (convertView != null) {
                            } else {
                                convertView = mInflater.inflate(R.layout.your_layout, null);
                            }

                            TextView text = (TextView) convertView.findViewById(R.id.text);
                            text.setText("Position " + position);

                            return convertView;
                        }
                    }

                    public static class Adapter5 extends ViewHolderTest {
                        LayoutInflater mInflater;

                        public View getView(int position, View convertView, ViewGroup parent) {
                            // Already using View Holder pattern
                            convertView = convertView == null ? mInflater.inflate(R.layout.your_layout, null) : convertView;

                            TextView text = (TextView) convertView.findViewById(R.id.text);
                            text.setText("Position " + position);

                            return convertView;
                        }
                    }

                    public static class Adapter6 extends ViewHolderTest {
                        private Context mContext;
                        private LayoutInflater mLayoutInflator;
                        private ArrayList<Double> mLapTimes;

                        @Override
                        public View getView(int position, View convertView, ViewGroup parent) {
                            if (mLayoutInflator == null)
                                mLayoutInflator = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                            View v = convertView;
                            if (v == null) v = mLayoutInflator.inflate(R.layout.your_layout, null);

                            LinearLayout listItemHolder = (LinearLayout) v.findViewById(R.id.laptimes_list_item_holder);
                            listItemHolder.removeAllViews();

                            for (int i = 0; i < mLapTimes.size(); i++) {
                                View lapItemView = mLayoutInflator.inflate(R.layout.laptime_item, null);
                                if (i == 0) {
                                    TextView t = (TextView) lapItemView.findViewById(R.id.laptime_text);
                                    //t.setText(TimeUtils.createStyledSpannableString(mContext, mLapTimes.get(i), true));
                                }

                                TextView t2 = (TextView) lapItemView.findViewById(R.id.laptime_text2);
                                if (i < mLapTimes.size() - 1 && mLapTimes.size() > 1) {
                                    double laptime = mLapTimes.get(i) - mLapTimes.get(i + 1);
                                    if (laptime < 0) laptime = mLapTimes.get(i);
                                    //t2.setText(TimeUtils.createStyledSpannableString(mContext, laptime, true));
                                } else {
                                    //t2.setText(TimeUtils.createStyledSpannableString(mContext, mLapTimes.get(i), true));
                                }

                                listItemHolder.addView(lapItemView);

                            }
                            return v;
                        }
                    }

                    public static class Adapter7 extends ViewHolderTest {
                        LayoutInflater inflater;

                        @Override
                        public View getView(final int position, final View convertView, final ViewGroup parent) {
                            View rootView = convertView;
                            final int itemViewType = getItemViewType(position);
                            switch (itemViewType) {
                                case 0:
                                    if (rootView != null)
                                        return rootView;
                                    rootView = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
                                    break;
                            }
                            return rootView;
                        }
                    }

                    public static final class R {
                        public static final class layout {
                            public static final int your_layout = 0x7f0a0000;
                            public static final int laptime_item = 0x7f0a0001;
                        }
                        public static final class id {
                            public static final int text = 0x7f0a0000;
                            public static final int laptimes_list_item_holder = 0x7f0a0001;
                            public static final int laptime_text = 0x7f0a0002;
                            public static final int laptime_text2 = 0x7f0a0003;
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/ViewHolderTest.java:42: Warning: Unconditional layout inflation from view adapter: Should use View Holder pattern (use recycled view passed into this method as the second parameter) for smoother scrolling [ViewHolder]
                        convertView = mInflater.inflate(R.layout.your_layout, null);
                                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }
}
