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

class SliceDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return SliceDetector()
    }

    fun testAtLeastOneRow() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.PendingIntent;
                import android.app.slice.Slice;
                import android.content.Context;
                import android.net.Uri;
                import androidx.slice.builders.GridRowBuilder;
                import androidx.slice.builders.GridRowBuilder.CellBuilder;
                import androidx.slice.builders.ListBuilder;
                import androidx.slice.builders.ListBuilder.HeaderBuilder;
                import androidx.slice.builders.ListBuilder.RowBuilder;
                import androidx.slice.builders.SliceAction;
                import androidx.core.graphics.drawable.IconCompat;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class SliceTest {
                    // ListBuilder should have at least one row
                    //
                    // // This is bad because this results in empty slice
                    // Slice slice = new ListBuilder(context, uri, ttl).build()
                    //
                    // Message:
                    // A slice should have at least one row added to it.
                    //
                    // Explanation:
                    // A slice should have at least one row added to it. If the content for
                    // the slice is unavailable or being loaded and will update eventually,
                    // you should still add all the rows that would represent your slice and
                    // indicate that content is being loaded on them, this allows the Slice
                    // to be presented correctly and update fluidly.
                    public void testMultipleRows(Context context, Uri uri, long ttl) {
                        // This is bad because this results in empty slice
                        Slice slice = new ListBuilder(context, uri, ttl).build();
                    }

                    public void testMultipleRows(Context context, Uri uri, long ttl,
                            SliceAction primary) {
                        ListBuilder lb1 = new ListBuilder(context, uri, ttl);
                        ListBuilder lb2 = new ListBuilder(context, uri, ttl); // missing on this one
                        RowBuilder rb = new RowBuilder(lb1);
                        rb.setPrimaryAction(primary);
                        rb.setTitle(null, true /* isLoading */);
                    }

                    public void testMultipleRowsOk1(Context context, Uri uri, long ttl,
                            SliceAction primary) {
                        ListBuilder lb = new ListBuilder(context, uri, ttl);
                        RowBuilder rb = new RowBuilder(lb);
                        rb.setPrimaryAction(primary);
                        rb.setTitle(null, true /* isLoading */);
                    }

                    @SuppressWarnings("UnnecessaryLocalVariable")
                    public void testMultipleRowsOk2(Context context, Uri uri, long ttl,
                            SliceAction primary) {
                        ListBuilder lb = new ListBuilder(context, uri, ttl);
                        ListBuilder lb2 = lb;
                        RowBuilder rb = new RowBuilder(lb2);
                        rb.setPrimaryAction(primary);
                        rb.setTitle(null, true /* isLoading */);
                    }
                }
                """
            ).indented(),
            *stubs
        ).run().expect(
            """
            src/test/pkg/SliceTest.java:33: Warning: A slice should have at least one row added to it [Slices]
                    Slice slice = new ListBuilder(context, uri, ttl).build();
                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SliceTest.java:39: Warning: A slice should have at least one row added to it [Slices]
                    ListBuilder lb2 = new ListBuilder(context, uri, ttl); // missing on this one
                                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun testAllRowsShouldHaveContent() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.PendingIntent;
                import android.app.slice.Slice;
                import android.content.Context;
                import android.net.Uri;
                import androidx.slice.builders.GridRowBuilder;
                import androidx.slice.builders.GridRowBuilder.CellBuilder;
                import androidx.slice.builders.ListBuilder;
                import androidx.slice.builders.ListBuilder.HeaderBuilder;
                import androidx.slice.builders.ListBuilder.RowBuilder;
                import androidx.slice.builders.SliceAction;
                import androidx.core.graphics.drawable.IconCompat;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class SliceTest {
                    // All rows should have at least one bit of content
                    //
                    // Message:
                    // <builder name> should have a piece of content added to it.
                    //
                    // Explanation:
                    // <builder name> should have a piece of content added to it, otherwise why
                    // add it to the list? If the content for the row is unavailable or being
                    // loaded and will update eventually, you should still add that content to
                    // the row and indicate that its being loaded on them, this allows the
                    // Slice to be presented correctly and update fluidly.

                    public void testAllRowsShouldHaveAtLeastOneBitOfContent(Context context,
                            Uri uri, long ttl) {
                        ListBuilder lb = new ListBuilder(context, uri, ttl);

                        // This is bad, rb should have something set on it before added
                        // to ListBuilder
                        RowBuilder rb = new RowBuilder(lb);
                        lb.addRow(rb);

                        // GridRowBuilder gets cells added to it, and those should also
                        // have at least one piece of content so this is bad:
                        GridRowBuilder grb = new GridRowBuilder(lb);
                        CellBuilder cb = new CellBuilder(grb);
                        grb.addCell(cb);

                        // This is bad because CellBuilder didn't have anything added to it
                        lb.addGridRow(grb);

                        // This is bad because HeaderBuilder didn't have anything added to it
                        HeaderBuilder hb = new HeaderBuilder(lb);
                        lb.setHeader(hb);
                    }

                    public void testAllRowsShouldHaveAtLeastOneBitOfContentOk(Context context,
                            Uri uri, long ttl, SliceAction primary) {
                        ListBuilder lb = new ListBuilder(context, uri, ttl);
                        RowBuilder rb = new RowBuilder(lb);
                        rb.setPrimaryAction(primary);
                        rb.setTitle(null, true /* isLoading */);
                    }

                    public void testAllRowsShouldHaveAtLeastOneBitOfContentOk2(Context context,
                            Uri uri, long ttl) {
                        ListBuilder lb = new ListBuilder(context, uri, ttl);
                        RowBuilder rb = new RowBuilder(lb).setTitle(null, true).setPrimaryAction(primary);
                    }
                }
                """
            ).indented(),
            *stubs
        ).run().expect(
            """
            src/test/pkg/SliceTest.java:31: Warning: A slice should have a primary action set on one of its rows [Slices]
                    ListBuilder lb = new ListBuilder(context, uri, ttl);
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SliceTest.java:35: Warning: RowBuilder should have a piece of content added to it [Slices]
                    RowBuilder rb = new RowBuilder(lb);
                                    ~~~~~~~~~~~~~~~~~~
            src/test/pkg/SliceTest.java:41: Warning: CellBuilder should have a piece of content added to it [Slices]
                    CellBuilder cb = new CellBuilder(grb);
                                     ~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SliceTest.java:48: Warning: HeaderBuilder should have a piece of content added to it [Slices]
                    HeaderBuilder hb = new HeaderBuilder(lb);
                                       ~~~~~~~~~~~~~~~~~~~~~
            0 errors, 4 warnings
            """
        )
    }

    fun testListBuilderShouldAlwaysHavePrimaryActionSet() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.PendingIntent;
                import android.app.slice.Slice;
                import android.content.Context;
                import android.net.Uri;
                import androidx.slice.builders.GridRowBuilder;
                import androidx.slice.builders.GridRowBuilder.CellBuilder;
                import androidx.slice.builders.ListBuilder;
                import androidx.slice.builders.ListBuilder.HeaderBuilder;
                import androidx.slice.builders.ListBuilder.RowBuilder;
                import androidx.slice.builders.SliceAction;
                import androidx.core.graphics.drawable.IconCompat;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class SliceTest {
                    // ListBuilder should always have a primary action set somewhere
                    //
                    //  Message:
                    //  A Slice should have a primary action set on one of its rows.
                    //
                    //  Explanation:
                    //  A slice is an actionable piece of content and should have a primary
                    // action linking the user to the relevant activity inside of the app
                    // for that slice. Additionally, a primary action is required to present
                    // the slice in a small “shortcut” format.

                    public void testListBuilderShouldHavePrimaryAction(Context context, Uri uri,
                            long ttl) {
                        // All rows accept a primary action via #setPrimaryAction
                        // (RowBuilder, HeaderBuilder, GridRowBuilder, InputRangeBuilder,
                        // RangeBuilder) there should be at least one primary action set
                        // somewhere in the Slice

                        ListBuilder lb = new ListBuilder(context, uri, ttl);
                        RowBuilder rb = new RowBuilder(lb);
                        rb.setTitle("some title text");

                        // This is bad because rb#setPrimaryAction has not been called
                        lb.addRow(rb);
                    }

                    public void testListBuilderShouldHavePrimaryActionOk(Context context, Uri uri,
                            long ttl, SliceAction primary) {
                        ListBuilder lb = new ListBuilder(context, uri, ttl);
                        RowBuilder rb1 = new RowBuilder(lb);
                        rb1.setTitle("some title text");
                        RowBuilder rb2 = new RowBuilder(lb);
                        rb1.setTitle("some title text");
                        rb2.setPrimaryAction(primary);
                        lb.addRow(rb1);
                        lb.addRow(rb2);
                    }
                }
                """
            ).indented(),
            *stubs
        ).run().expect(
            """
            src/test/pkg/SliceTest.java:35: Warning: A slice should have a primary action set on one of its rows [Slices]
                    ListBuilder lb = new ListBuilder(context, uri, ttl);
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testKotlin() {
        lint().files(
            kotlin(
                """
                @file:Suppress("unused")

                package test.pkg

                import android.annotation.SuppressLint
                import android.app.PendingIntent
                import android.content.Context
                import android.net.Uri

                import androidx.core.graphics.drawable.IconCompat
                import androidx.slice.builders.ListBuilder
                import androidx.slice.builders.ListBuilder.RowBuilder
                import androidx.slice.builders.SliceAction

                @SuppressLint("UnknownNullness")
                class SliceTest {
                    // RowBuilder shouldn't have a mixture of actions / icons in end items
                    fun testNoMixingActionsAndIcons1(context: Context, uri: Uri, ttl: Long,
                                                     sliceAction: SliceAction, sliceAction2: SliceAction, icon: IconCompat) {
                        val listBuilder = ListBuilder(context, uri, ttl)
                        val rowBuilder = RowBuilder(listBuilder)
                        rowBuilder.setTitle("some title text")
                        rowBuilder.setPrimaryAction(sliceAction)
                        rowBuilder.addEndItem(icon, 0)

                        // This is bad because an icon was already added to the end
                        rowBuilder.addEndItem(sliceAction2)
                    }


                    fun testListBuilderShouldHavePrimaryAction(context: Context, uri: Uri,
                                                               ttl: Long) {
                        // All rows accept a primary action via #setPrimaryAction
                        // there should be at least one primary action set
                        // somewhere in the Slice

                        val listBuilder = ListBuilder(context, uri, ttl)
                        val rowBuilder = RowBuilder(listBuilder)
                        rowBuilder.setTitle("some title text")

                        // This is bad because rowBuilder#setPrimaryAction has not been called
                        listBuilder.addRow(rowBuilder)
                    }

                    // A mixture of slice actions and icons are not supported on a row, add
                    // either actions or icons but not both.
                    fun testNoMixingDefaultAndCustom(context: Context, uri: Uri, ttl: Long,
                                                     pendingIntent: PendingIntent, sliceAction: SliceAction,
                                                     sliceAction2: SliceAction, icon: IconCompat, primary: SliceAction) {
                        val listBuilder = ListBuilder(context, uri, ttl)

                        val defaultToggle = SliceAction(pendingIntent,
                                "default toggle",
                                true /* isChecked */)

                        val customToggle = SliceAction(pendingIntent,
                                icon,
                                "default toggle",
                                true /* isChecked */)

                        val rowBuilder = RowBuilder(listBuilder)
                        rowBuilder.setPrimaryAction(primary)

                        // Bad because mixture of ‘default' toggle and custom toggle
                        rowBuilder.addEndItem(defaultToggle)
                        rowBuilder.addEndItem(customToggle)
                    }
                }
                """
            ).indented(),
            *stubs
        ).run().expect(
            """
            src/test/pkg/SliceTest.kt:27: Warning: RowBuilder cannot have a mixture of icons and slice actions added to the end items [Slices]
                    rowBuilder.addEndItem(sliceAction2)
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/SliceTest.kt:24: Earlier icon here
            src/test/pkg/SliceTest.kt:37: Warning: A slice should have a primary action set on one of its rows [Slices]
                    val listBuilder = ListBuilder(context, uri, ttl)
                                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SliceTest.kt:65: Warning: RowBuilder should not have a mixture of default and custom toggles [Slices]
                    rowBuilder.addEndItem(defaultToggle)
                                          ~~~~~~~~~~~~~
                src/test/pkg/SliceTest.kt:66: Conflicting action type here
            0 errors, 3 warnings
            """
        )
    }

    fun testRowBuilderCannotHaveMultipleTimeStamps() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.PendingIntent;
                import android.app.slice.Slice;
                import android.content.Context;
                import android.net.Uri;
                import androidx.slice.builders.GridRowBuilder;
                import androidx.slice.builders.GridRowBuilder.CellBuilder;
                import androidx.slice.builders.ListBuilder;
                import androidx.slice.builders.ListBuilder.HeaderBuilder;
                import androidx.slice.builders.ListBuilder.RowBuilder;
                import androidx.slice.builders.SliceAction;
                import androidx.core.graphics.drawable.IconCompat;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class SliceTest {
                    // RowBuilder can't have multiple timestamps added to it
                    // Message:
                    // RowBuilder can only have one timestamp added to it, remove one of your timestamps.
                    public void testSingleTimeStamp(Context context, Uri uri, long ttl,
                    long timeStamp, long timestamp2, SliceAction primary) {
                        ListBuilder lb = new ListBuilder(context, uri, ttl);
                        RowBuilder rb = new RowBuilder(lb);
                        rb.setTitleItem(timeStamp);
                        rb.setTitle("some title text");
                        // This is bad because a timestamp was already added
                        rb.addEndItem(timestamp2);
                        rb.setPrimaryAction(primary);
                    }
                }
                """
            ).indented(),
            *stubs
        ).run().expect(
            """
            src/test/pkg/SliceTest.java:27: Warning: RowBuilder can only have one timestamp added to it, remove one of your timestamps [Slices]
                    rb.addEndItem(timestamp2);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/SliceTest.java:24: Earlier timestamp here
            0 errors, 1 warnings
            """
        )
    }

    fun testRowBuilderShouldNotHaveMixtureOfActionsAndIcons() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.PendingIntent;
                import android.app.slice.Slice;
                import android.content.Context;
                import android.net.Uri;
                import androidx.slice.builders.GridRowBuilder;
                import androidx.slice.builders.GridRowBuilder.CellBuilder;
                import androidx.slice.builders.ListBuilder;
                import androidx.slice.builders.ListBuilder.HeaderBuilder;
                import androidx.slice.builders.ListBuilder.RowBuilder;
                import androidx.slice.builders.SliceAction;
                import androidx.core.graphics.drawable.IconCompat;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class SliceTest {
                    // RowBuilder shouldn't have a mixture of actions / icons in end items
                    // Message:
                    // RowBuilder cannot have a mixture of icons and slice actions added to the end items.
                    //
                    // Explanation:
                    // RowBuilder cannot have a mixture of icons and slice actions added to the end items.
                    public void testNoMixingActionsAndIcons1(Context context, Uri uri, long ttl,
                            SliceAction sliceAction, SliceAction sliceAction2, IconCompat icon) {
                        ListBuilder lb = new ListBuilder(context, uri, ttl);
                        RowBuilder rb = new RowBuilder(lb);
                        rb.setTitle("some title text");
                        rb.setPrimaryAction(sliceAction);
                        rb.addEndItem(icon, 0);

                        // This is bad because an icon was already added to the end
                        rb.addEndItem(sliceAction2);
                    }

                    public void testNoMixingActionsAndIcons2(Context context, Uri uri, long ttl,
                            SliceAction sliceAction, SliceAction sliceAction2, IconCompat icon) {
                        ListBuilder lb2 = new ListBuilder(context, uri, ttl);
                        RowBuilder rb2 = new RowBuilder(lb2);
                        rb2.setTitle("some title text");
                        rb2.setPrimaryAction(sliceAction);
                        rb2.addEndItem(sliceAction2);

                        // This is bad because a slice action was already added to the end
                        rb2.addEndItem(icon, 0);
                    }

                    public void testNoMixingActionsAndIconsOk(Context context, Uri uri, long ttl,
                            SliceAction sliceAction, SliceAction sliceAction2, IconCompat icon) {
                        ListBuilder lb2 = new ListBuilder(context, uri, ttl);
                        RowBuilder rb1 = new RowBuilder(lb2);
                        RowBuilder rb2 = new RowBuilder(lb2);
                        rb2.setTitle("some title text");
                        rb2.setPrimaryAction(sliceAction);
                        rb2.addEndItem(sliceAction2);
                        rb1.addEndItem(icon, 0); // ok - not on the same row builder
                    }
                }
                """
            ).indented(),
            *stubs
        ).run().expect(
            """
            src/test/pkg/SliceTest.java:32: Warning: RowBuilder cannot have a mixture of icons and slice actions added to the end items [Slices]
                    rb.addEndItem(sliceAction2);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/SliceTest.java:29: Earlier icon here
            src/test/pkg/SliceTest.java:44: Warning: RowBuilder cannot have a mixture of icons and slice actions added to the end items [Slices]
                    rb2.addEndItem(icon, 0);
                    ~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/SliceTest.java:41: Earlier slice action here
            0 errors, 2 warnings
            """
        )
    }

    fun testRowBuilderShouldNotMixDefaultAndCustom() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.PendingIntent;
                import android.app.slice.Slice;
                import android.content.Context;
                import android.net.Uri;
                import androidx.slice.builders.GridRowBuilder;
                import androidx.slice.builders.GridRowBuilder.CellBuilder;
                import androidx.slice.builders.ListBuilder;
                import androidx.slice.builders.ListBuilder.HeaderBuilder;
                import androidx.slice.builders.ListBuilder.RowBuilder;
                import androidx.slice.builders.SliceAction;
                import androidx.core.graphics.drawable.IconCompat;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class SliceTest {
                    // RowBuilder shouldn't have a mixture of ‘default' and custom toggles
                    //
                    // Message:
                    // A mixture of slice actions and icons are not supported on a row, add
                    // either actions or icons but not both.
                    public void testNoMixingDefaultAndCustom(Context context, Uri uri, long ttl,
                            PendingIntent pendingIntent, SliceAction sliceAction,
                            SliceAction sliceAction2, IconCompat icon, SliceAction primary) {
                        ListBuilder lb = new ListBuilder(context, uri, ttl);

                        SliceAction defaultToggle =
                                new SliceAction(pendingIntent,
                                        "default toggle",
                                        true /* isChecked */);

                        SliceAction customToggle =
                                new SliceAction(pendingIntent,
                                        icon,
                                        "default toggle",
                                        true /* isChecked */);

                        RowBuilder rb = new RowBuilder(lb);
                        rb.setPrimaryAction(primary);

                        // Bad because mixture of ‘default' toggle and custom toggle
                        rb.addEndItem(defaultToggle);
                        rb.addEndItem(customToggle);
                    }
                }
                """
            ).indented(),
            *stubs
        ).run().expect(
            """
            src/test/pkg/SliceTest.java:42: Warning: RowBuilder should not have a mixture of default and custom toggles [Slices]
                    rb.addEndItem(defaultToggle);
                                  ~~~~~~~~~~~~~
                src/test/pkg/SliceTest.java:43: Conflicting action type here
            0 errors, 1 warnings
            """
        )
    }

    fun testRowBuilderShouldNotMix2() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.PendingIntent;
                import android.app.slice.Slice;
                import android.content.Context;
                import android.net.Uri;
                import androidx.slice.builders.GridRowBuilder;
                import androidx.slice.builders.GridRowBuilder.CellBuilder;
                import androidx.slice.builders.ListBuilder;
                import androidx.slice.builders.ListBuilder.HeaderBuilder;
                import androidx.slice.builders.ListBuilder.RowBuilder;
                import androidx.slice.builders.SliceAction;
                import androidx.core.graphics.drawable.IconCompat;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class SliceTest {
                    // ListBuilder shouldn't have a mixture of ‘default' and custom toggles
                    // set in ListBuilder#addAction
                    // Message:
                    // A mixture of slice actions and icons are not supported on a list,
                    // add either actions or icons but not both.
                    public void testNoMixingDefaultAndCustomInAddAction(Context context, Uri uri,
                            long ttl, PendingIntent pendingIntent, SliceAction sliceAction,
                            SliceAction sliceAction2, IconCompat customToggleIcon) {

                        SliceAction defaultToggle =
                                new SliceAction(pendingIntent,
                                        "default toggle",
                                        true /* isChecked */);

                        SliceAction customToggle =
                                new SliceAction(pendingIntent,
                                        customToggleIcon,
                                        "default toggle",
                                        true /* isChecked */);

                        ListBuilder lb = new ListBuilder(context, uri, ttl);
                        lb.addAction(defaultToggle);
                        lb.addAction(customToggle);
                    }
                }
                """
            ).indented(),
            *stubs
        ).run().expect(
            """
            src/test/pkg/SliceTest.java:37: Warning: A slice should have at least one row added to it [Slices]
                    ListBuilder lb = new ListBuilder(context, uri, ttl);
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testSeeMoreAction() {
        @Suppress("ConstantConditionIf")
        if (!SliceDetector.WARN_ABOUT_TOO_MANY_ROWS) {
            return
        }

        lint().files(
            java(
                """
                package test.pkg;

                import android.app.PendingIntent;
                import android.app.slice.Slice;
                import android.content.Context;
                import android.net.Uri;
                import androidx.slice.builders.GridRowBuilder;
                import androidx.slice.builders.GridRowBuilder.CellBuilder;
                import androidx.slice.builders.ListBuilder;
                import androidx.slice.builders.ListBuilder.HeaderBuilder;
                import androidx.slice.builders.ListBuilder.RowBuilder;
                import androidx.slice.builders.SliceAction;
                import androidx.core.graphics.drawable.IconCompat;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class SliceTest {
                    // Consider setting a see more action if more than 4 rows added to ListBuilder
                    // Message:
                    // Depending on where the slice is being displayed, all rows of content may
                    // not be visible, consider adding an intent to an activity with the rest
                    // of the content.
                    //
                    // Explanation:
                    // Slices can be presented in various ways, and sometimes all content in
                    // a slice may not be shown if the slice is very large. If content is cut
                    // off the slice can display a “show more” tap affordance and link to
                    // the rest of the content if ListBuilder#setSeeMoreAction is used.
                    public void testMoreAction(Context context, Uri uri, long ttl,
                            SliceAction primary) {
                        ListBuilder lb = new ListBuilder(context, uri, ttl);

                        RowBuilder rb1 = new RowBuilder(lb);
                        rb1.setPrimaryAction(primary);
                        rb1.setTitle("title1");
                        lb.addRow(rb1);

                        RowBuilder rb2 = new RowBuilder(lb);
                        rb2.setPrimaryAction(primary);
                        rb2.setTitle("title1");
                        lb.addRow(rb2);

                        RowBuilder rb3 = new RowBuilder(lb);
                        rb3.setPrimaryAction(primary);
                        rb3.setTitle("title1");
                        lb.addRow(rb3);

                        RowBuilder rb4 = new RowBuilder(lb);
                        rb4.setPrimaryAction(primary);
                        rb4.setTitle("title1");
                        lb.addRow(rb4);

                        RowBuilder rb5 = new RowBuilder(lb);
                        rb5.setPrimaryAction(primary);
                        rb5.setTitle("title1");
                        lb.addRow(rb5);

                        RowBuilder rb6 = new RowBuilder(lb);
                        rb6.setPrimaryAction(primary);
                        rb6.setTitle("title1");
                        lb.addRow(rb6);
                    }
                }
                """
            ).indented(),
            *stubs
        ).run().expect(
            """
            src/test/pkg/SliceTest.java:55: Warning: Consider setting a see more action if more than 4 rows added to ListBuilder. Depending on where the slice is being displayed, all rows of content may not be visible, consider adding an intent to an activity with the rest of the content [Slices]
                    lb.addRow(rb5);
                    ~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testCorrectManifestDeclaration() {
        lint().files(
            manifest(
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <application>
                            <provider android:name=".MyProvider"
                                android:authority="com.example">
                                <intent-filter>
                                    <action android:name="com.example.some.INTENT" />
                                    <category android:name="android.app.slice.category.SLICE" />
                                </intent-filter>
                            </provider>
                        </application>
                    </manifest>
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.net.Uri;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class MyProvider extends DefaultSliceProvider {
                    @Override
                    public Uri onMapIntentToUri(Intent intent) {
                        return super.onMapIntentToUri(intent);
                    }
                }
                """
            ).indented(),
            *stubs
        ).run().expectClean()
    }

    fun testConsumer1() {
        // RowBuilders are constructed inside consumer methods on builder
        lint().files(
            java(
                """
                    package test.pkg;
                    import android.app.PendingIntent;
                    import android.content.Context;
                    import android.net.Uri;

                    import androidx.core.graphics.drawable.IconCompat;
                    import androidx.slice.Slice;
                    import androidx.slice.builders.ListBuilder;
                    import androidx.slice.builders.SliceAction;

                    import static androidx.slice.builders.ListBuilder.INFINITY;
                    import static androidx.slice.builders.ListBuilder.SMALL_IMAGE;
                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    public abstract class SimpleTest {
                        protected abstract Context getContext();
                        private Slice createWeather(Uri sliceUri, PendingIntent intent, int image1, int image2, int image3) {
                            SliceAction primaryAction = new SliceAction(intent,
                                    IconCompat.createWithResource(getContext(), image1), SMALL_IMAGE,
                                    "Weather is happening!");
                            return new ListBuilder(getContext(), sliceUri, INFINITY)
                                    .addGridRow(gb -> gb
                                            .setPrimaryAction(primaryAction)
                                            .addCell(cb -> cb
                                                    .addImage(IconCompat.createWithResource(getContext(),
                                                            image2),
                                                            SMALL_IMAGE)
                                                    .addText("MON")
                                                    .addTitleText("69\u00B0"))
                                            .addCell(cb -> cb
                                                    .addImage(IconCompat.createWithResource(getContext(),
                                                            image3),
                                                            SMALL_IMAGE)
                                                    .addText("FRI")
                                                    .addTitleText("68\u00B0")))
                                    .build();
                        }

                        private Slice createGallery(Uri sliceUri) {
                            SliceAction primaryAction = new SliceAction(
                                    getBroadcastIntent(ACTION_TOAST, "open photo album"),
                                    IconCompat.createWithResource(getContext(), R.drawable.slices_1),
                                    LARGE_IMAGE,
                                    "Open photo album");
                            return new ListBuilder(getContext(), sliceUri, INFINITY) // 2
                                    .setColor(0xff4285F4)
                                    .addRow(b -> b
                                            .setTitle("Family trip to Hawaii")
                                            .setSubtitle("Sep 30, 2017 - Oct 2, 2017")
                                            .setPrimaryAction(primaryAction))
                                    .addAction(new SliceAction(
                                            getBroadcastIntent(ACTION_TOAST, "cast photo album"),
                                            IconCompat.createWithResource(getContext(), R.drawable.ic_cast),
                                            "Cast photo album"))
                                    .addAction(new SliceAction(
                                            getBroadcastIntent(ACTION_TOAST, "share photo album"),
                                            IconCompat.createWithResource(getContext(), R.drawable.ic_share),
                                            "Share photo album"))
                                    .addGridRow(b -> b
                                            .addCell(cb -> cb
                                                    .addImage(IconCompat.createWithResource(getContext(),
                                                            R.drawable.slices_1),
                                                            LARGE_IMAGE))
                                            .addCell(cb -> cb
                                                    .addImage(IconCompat.createWithResource(getContext(),
                                                            R.drawable.slices_2),
                                                            LARGE_IMAGE))
                                            .addCell(cb -> cb
                                                    .addImage(IconCompat.createWithResource(getContext(),
                                                            R.drawable.slices_3),
                                                            LARGE_IMAGE))
                                            .addCell(cb -> cb
                                                    .addImage(IconCompat.createWithResource(getContext(),
                                                            R.drawable.slices_4),
                                                            LARGE_IMAGE))
                                            .addCell(cb -> cb
                                                    .addImage(IconCompat.createWithResource(getContext(),
                                                            R.drawable.slices_2),
                                                            LARGE_IMAGE))
                                            .addCell(cb -> cb
                                                    .addImage(IconCompat.createWithResource(getContext(),
                                                            R.drawable.slices_3),
                                                            LARGE_IMAGE))
                                            .addCell(cb -> cb
                                                    .addImage(IconCompat.createWithResource(getContext(),
                                                            R.drawable.slices_4),
                                                            LARGE_IMAGE))
                                            .setSeeMoreAction(getBroadcastIntent(ACTION_TOAST, "see your gallery"))
                                            .setContentDescription("Images from your trip to Hawaii"))
                                    .build();
                        }
                    }
                """
            ).indented(),
            *stubs
        ).run().expectClean()
    }

    fun testSliceProviderMissingCategory() {
        lint().files(
            manifest(
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <application>
                            <provider android:name=".MyProvider"
                                android:authority="com.example">
                                <intent-filter>
                                    <action android:name="com.example.some.INTENT" />
                                </intent-filter>
                            </provider>
                        </application>
                    </manifest>
                """
            ).indented(),
            java(
                """
                package test.pkg;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class MyProvider extends DefaultSliceProvider {
                }
                """
            ).indented(),
            *stubs
        ).run().expect(
            """
            AndroidManifest.xml:6: Warning: All SliceProvider filters require category slice to be set:  <category android:name="android.app.slice.category.SLICE" /> [Slices]
                        <intent-filter>
                        ^
                src/test/pkg/MyProvider.java:4: SliceProvider declaration
            0 errors, 1 warnings
            """
        )
    }

    fun testMissingOnMapDeclaration() {
        lint().files(
            manifest(
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <application>
                            <provider android:name=".MyProvider"
                                android:authority="com.example">
                                <intent-filter>
                                    <action android:name="com.example.some.INTENT" />
                                    <category android:name="android.app.slice.category.SLICE" />
                                </intent-filter>
                            </provider>
                        </application>
                    </manifest>
                """
            ).indented(),
            java(
                """
                package test.pkg;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class MyProvider extends DefaultSliceProvider {
                }
                """
            ).indented(),
            *stubs
        ).run().expect(
            """
            src/test/pkg/MyProvider.java:4: Warning: Implement SliceProvider#onMapIntentToUri to handle the intents defined on your slice <provider> in your manifest [Slices]
            public class MyProvider extends DefaultSliceProvider {
                         ~~~~~~~~~~
                AndroidManifest.xml:8: <No location-specific message
            0 errors, 1 warnings
            """
        )
    }

    fun testMissingOnIntentFilterDeclaration() {
        lint().files(
            manifest(
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <application>
                            <provider android:name=".MyProvider"
                                android:authority="com.example">
                            </provider>
                        </application>
                    </manifest>
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.content.Intent;
                import android.net.Uri;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class MyProvider extends DefaultSliceProvider {
                    @Override
                    public Uri onMapIntentToUri(Intent intent) {
                        return super.onMapIntentToUri(intent);
                    }
                }
                """
            ).indented(),
            *stubs
        ).run().expect(
            """
            src/test/pkg/MyProvider.java:9: Warning: Define intent filters in your manifest on your <provider android:name="test.pkg.MyProvider">; otherwise onMapIntentToUri will not be called [Slices]
                public Uri onMapIntentToUri(Intent intent) {
                           ~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun test79784005_part1() {
        // Regression test for
        // 79784005: "at least one item in row" Lint check false positive in Kotlin when using apply
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.Context
                import android.net.Uri
                import androidx.slice.Slice
                import androidx.slice.builders.ListBuilder
                import androidx.slice.builders.SliceAction

                internal fun createDemoSlice1(
                        context: Context,
                        sliceUri: Uri,
                        primary: SliceAction): Slice = ListBuilder(context, sliceUri, 5L)
                        .apply {
                            addRow(ListBuilder.RowBuilder(context, sliceUri)
                                    .setTitle("URI found.")
                                    .setPrimaryAction(primary))
                        }.build()

                internal fun createDemoSlice2(
                        context: Context,
                        sliceUri: Uri,
                        primary: SliceAction): Slice = ListBuilder(context, sliceUri, 5L)
                        .run {
                            addRow(ListBuilder.RowBuilder(context, sliceUri)
                                    .setTitle("URI found.")
                                    .setPrimaryAction(primary))
                        }.build()

                internal fun createDemoSlice3(
                        context: Context,
                        sliceUri: Uri,
                        primary: SliceAction): Slice = with(ListBuilder(context, sliceUri, 5L)) {
                    addRow(ListBuilder.RowBuilder(context, sliceUri)
                            .setTitle("URI found.")
                            .setPrimaryAction(primary))
                }.build()
                """
            ).indented(),
            *stubs
        ).run().expectClean()
    }

    fun test79784005_part2() {
        // Regression test for comment #2 of
        // 79784005: "at least one item in row" Lint check false positive in Kotlin when using apply
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Context;
                import android.net.Uri;

                import androidx.slice.Slice;
                import androidx.slice.builders.ListBuilder;
                import androidx.slice.builders.ListBuilder.RowBuilder;
                import androidx.slice.builders.SliceAction;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName"})
                public class SliceTest {
                    Slice createSlice1(Uri sliceUri, Context context, SliceAction primary) {
                        return new ListBuilder(context, sliceUri, ListBuilder.INFINITY)
                                .addRow(new RowBuilder(context, sliceUri).setTitle("URI found.").setSubtitle("Subtitle").setPrimaryAction(primary))
                                .build();
                    }

                    Slice createSlice2(Uri sliceUri, Context context, SliceAction primary) {
                        return new ListBuilder(context, sliceUri, ListBuilder.INFINITY)
                                .addRow(new ListBuilder.RowBuilder(context, sliceUri).setPrimaryAction(primary))
                                .build();
                    }

                    Slice createSlice3(Uri sliceUri, Context context, SliceAction primary) {
                        return new ListBuilder(context, sliceUri, ListBuilder.INFINITY)
                                .addRow(b -> b
                                        .setTitle("My title")
                                        .setPrimaryAction(primary))
                                .build();
                    }
                }
                """
            ).indented(),
            *stubs
        ).run().expectClean()
    }

    // Stubs:

    private val listBuilder: TestFile = java(
        """
            package androidx.slice.builders;
            import android.app.PendingIntent;
            import android.content.Context;
            import android.net.Uri;

            import java.util.List;

            import androidx.core.graphics.drawable.IconCompat;
            import androidx.core.util.Consumer;
            import androidx.slice.Slice;
            @SuppressWarnings("all") // stubs
            public class ListBuilder {
                public static final int ICON_IMAGE = SliceHints.ICON_IMAGE;
                public static final int SMALL_IMAGE = SliceHints.SMALL_IMAGE;
                public static final int LARGE_IMAGE = SliceHints.LARGE_IMAGE;
                public static final int UNKNOWN_IMAGE = SliceHints.UNKNOWN_IMAGE;
                public static final long INFINITY = SliceHints.INFINITY;
                public ListBuilder(Context context, Uri uri, long ttl) {
                }
                public Slice build() {
                    return null;
                }
                public ListBuilder addRow(RowBuilder builder) { return this; }
                public ListBuilder addRow(Consumer<RowBuilder> c) { return this; }
                public ListBuilder addGridRow(GridRowBuilder builder) { return this; }
                public ListBuilder addGridRow(Consumer<GridRowBuilder> c) { return this; }
                public ListBuilder setHeader(HeaderBuilder builder) { return this; }
                public ListBuilder setHeader(Consumer<HeaderBuilder> c) { return this; }
                public ListBuilder addAction(SliceAction action) { return this; }
                public ListBuilder setColor(int i) { return this; }
                public ListBuilder setKeywords(List<String> keywords) { return this; }
                public ListBuilder setSeeMoreRow(RowBuilder builder) { return this; }
                public ListBuilder setSeeMoreRow(Consumer<RowBuilder> c) { return this; }
                public ListBuilder setSeeMoreAction(PendingIntent intent) { return this; }
                public static class RowBuilder {
                    public RowBuilder(ListBuilder parent) { }
                    public RowBuilder(ListBuilder parent, Uri uri) { }
                    public RowBuilder(Context context, Uri uri) { }
                    public RowBuilder setTitleItem(long timeStamp) { return this; }
                    public RowBuilder setTitleItem(IconCompat icon, int imageMode) { return this; }
                    public RowBuilder setPrimaryAction(SliceAction action) { return this; }
                    public RowBuilder setTitle(CharSequence title) { return this; }
                    public RowBuilder setTitle(CharSequence title, boolean isLoading) { return this; }
                    public RowBuilder setSubtitle(CharSequence subtitle) {
                        return setSubtitle(subtitle, false /* isLoading */);
                    }
                    public RowBuilder setSubtitle(CharSequence subtitle, boolean isLoading) { return this; }
                    public RowBuilder addEndItem(long timeStamp) { return this; }
                    public RowBuilder addEndItem(IconCompat icon, int imageMode) {
                        return addEndItem(icon, imageMode, false /* isLoading */);
                    }
                    public RowBuilder addEndItem(IconCompat icon, int imageMode,
                                                 boolean isLoading) { return this; }
                    public RowBuilder addEndItem(SliceAction action) { return this; }
                    public RowBuilder setContentDescription(CharSequence description) { return this; }
                }
                public static class HeaderBuilder {
                    public HeaderBuilder(ListBuilder parent) { }
                    public HeaderBuilder setTitle(CharSequence title) { return this; }
                    public HeaderBuilder setTitle(CharSequence title, boolean isLoading) { return this; }
                    public HeaderBuilder setPrimaryAction(SliceAction action) { return this; }
                    public HeaderBuilder setSummary(CharSequence summary) {
                        return setSummary(summary, false /* isLoading */);
                    }
                    public HeaderBuilder setSummary(CharSequence summary, boolean isLoading) { return this; }
                    public HeaderBuilder setSubtitle(CharSequence s) { return this; }
                    public HeaderBuilder setSubtitle(CharSequence s, boolean isLoading) { return this; }
                    public HeaderBuilder setContentDescription(CharSequence description) { return this; }
                }
                public ListBuilder addInputRange(InputRangeBuilder b) { return this; }
                public ListBuilder addInputRange(Consumer<InputRangeBuilder> c) { return this; }
                public ListBuilder addRange(RangeBuilder rangeBuilder) { return this; }
                public ListBuilder addRange(Consumer<RangeBuilder> c) { return this; }
                public static class InputRangeBuilder {
                    public InputRangeBuilder(ListBuilder lb) { }
                    public InputRangeBuilder setMin(int min) { return this; }
                    public InputRangeBuilder setMax(int max) { return this; }
                    public InputRangeBuilder setValue(int value) { return this; }
                    public InputRangeBuilder setTitle(CharSequence title) { return this; }
                    public InputRangeBuilder setSubtitle(CharSequence title) { return this; }
                    public InputRangeBuilder setThumb(IconCompat thumb) { return this; }
                    public InputRangeBuilder setPrimaryAction(SliceAction action) { return this; }
                    public InputRangeBuilder setContentDescription(CharSequence description) { return this; }
                    public InputRangeBuilder setInputAction(PendingIntent action) { return this; }
                }
                public static class RangeBuilder {
                    public RangeBuilder(ListBuilder parent) { }
                    public RangeBuilder setMax(int max) { return this; }
                    public RangeBuilder setValue(int value) { return this; }
                    public RangeBuilder setTitle(CharSequence title) { return this; }
                    public RangeBuilder setSubtitle(CharSequence title) { return this; }
                    public RangeBuilder setPrimaryAction(SliceAction action) { return this; }
                    public RangeBuilder setContentDescription(CharSequence description) { return this; }
                }
            }
        """
    ).indented()

    private val gridRowBuilder: TestFile = java(
        """
            package androidx.slice.builders;
            import android.app.PendingIntent;
            import android.net.Uri;

            import androidx.core.graphics.drawable.IconCompat;
            import androidx.core.util.Consumer;
            @SuppressWarnings("all") // stubs
            public class GridRowBuilder {
                public GridRowBuilder(ListBuilder parent) {
                }
                public GridRowBuilder addCell(CellBuilder builder) { return this; }
                public GridRowBuilder addCell(Consumer<CellBuilder> c) { return this; }
                public GridRowBuilder setPrimaryAction(SliceAction action) { return this; }
                public GridRowBuilder setSeeMoreCell(CellBuilder builder) { return this; }
                public GridRowBuilder setSeeMoreCell(Consumer<CellBuilder> c) { return this; }
                public GridRowBuilder setSeeMoreAction(PendingIntent intent) { return this; }
                public GridRowBuilder setContentDescription(CharSequence description) { return this; }

                public static final class CellBuilder {
                    public CellBuilder(GridRowBuilder parent) { }
                    public CellBuilder(GridRowBuilder parent, Uri uri) { }
                    public CellBuilder addText(CharSequence text) { return this; }
                    public CellBuilder addText(CharSequence text, boolean isLoading) { return this; }
                    public CellBuilder addImage(IconCompat image, int imageMode) { return this; }
                    public CellBuilder addImage(IconCompat image, int imageMode, boolean isLoading) { return this; }
                    public CellBuilder addTitleText(CharSequence text) { return this; }
                    public CellBuilder addTitleText(CharSequence text, boolean isLoading) { return this; }
                    public CellBuilder setContentIntent(PendingIntent intent) { return this; }
                    public CellBuilder setContentDescription(String s) { return this; }
                }
            }
        """
    ).indented()

    private val sliceAction: TestFile = java(
        """
            package androidx.slice.builders;
            import android.app.PendingIntent;

            import androidx.core.graphics.drawable.IconCompat;
            @SuppressWarnings("all") // stubs
            public class SliceAction {
                public SliceAction(PendingIntent action, IconCompat actionIcon,
                                   CharSequence actionTitle) {
                    this(action, actionIcon, ListBuilder.ICON_IMAGE, actionTitle);
                }
                public SliceAction(PendingIntent action, IconCompat actionIcon,
                                   int imageMode, CharSequence actionTitle) {
                }
                public SliceAction(PendingIntent action, IconCompat actionIcon,
                                   CharSequence actionTitle, boolean isChecked) {
                }
                public SliceAction(PendingIntent action, CharSequence actionTitle,
                                   boolean isChecked) {
                }
                public PendingIntent getAction() {
                    return null;
                }
            }
        """
    ).indented()

    private val sliceHints: TestFile = java(
        """
        package androidx.slice.builders;

        @SuppressWarnings("all") // stubs
        public class SliceHints {
            public static final int ICON_IMAGE = 0;
            public static final int SMALL_IMAGE = 1;
            public static final int LARGE_IMAGE = 2;
            public static final int UNKNOWN_IMAGE = 3;
            public static final long INFINITY = -1;
        }
        """
    ).indented()

    private val iconCompat: TestFile = java(
        """
            package androidx.core.graphics.drawable;
            import android.content.Context;
            import android.graphics.Bitmap;
            @SuppressWarnings("all") // stubs
            public class IconCompat {
                public static IconCompat createFromIcon(Bitmap icon) {
                    return null;
                }
                public static IconCompat createWithResource(Context context, int resId) {
                    return null;
                }
            }
        """
    ).indented()

    private val sliceProvider: TestFile = java(
        """
            package androidx.slice;
            import android.content.ContentProvider;
            import android.content.Intent;
            import android.net.Uri;
            @SuppressWarnings("all") // stubs
            public abstract class SliceProvider extends ContentProvider {
                public abstract Slice onBindSlice(Uri sliceUri);
                public void onSlicePinned(Uri sliceUri) {
                }
                public void onSliceUnpinned(Uri sliceUri) {
                }
                public Uri onMapIntentToUri(Intent intent) {
                    return null;
                }
            }
        """
    ).indented()

    private val defaultSliceProvider: TestFile = java(
        """
            package test.pkg;
            import android.content.ContentValues;
            import android.database.Cursor;
            import android.net.Uri;

            import androidx.slice.Slice;
            import androidx.slice.SliceProvider;
            @SuppressWarnings("all") // stubs
            public class DefaultSliceProvider extends SliceProvider {
                @Override
                public Slice onBindSlice(Uri sliceUri) {
                    return null;
                }
                @Override
                public boolean onCreate() {
                    return false;
                }
                @Override
                public Cursor query(Uri uri, String[] strings, String s, String[] strings1, String s1) {
                    return null;
                }
                @Override
                public String getType(Uri uri) {
                    return null;
                }
                @Override
                public Uri insert(Uri uri, ContentValues contentValues) {
                    return null;
                }
                @Override
                public int delete(Uri uri, String s, String[] strings) {
                    return 0;
                }
                @Override
                public int update(Uri uri, ContentValues contentValues, String s, String[] strings) {
                    return 0;
                }
            }
        """
    ).indented()

    private val consumer: TestFile = java(
        """
            package androidx.core.util;
            @SuppressWarnings("all") // stubs
            public interface Consumer<T> {
                void accept(T t);
            }
        """
    ).indented()

    private val slice: TestFile = java(
        """
            package androidx.slice;
            @SuppressWarnings("all") // stubs
            public class Slice {
            }
        """
    ).indented()

    private val stubs = arrayOf(
        listBuilder,
        gridRowBuilder,
        sliceAction,
        sliceHints,
        sliceProvider,
        defaultSliceProvider,
        consumer,
        slice,
        iconCompat
    )
}
