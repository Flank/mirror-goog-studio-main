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
            src/test/pkg/MyProvider.java:4: Warning: Implement SliceProvider#onMapIntentToUri to handle the intents defined on your slice <provider> in your manifest. [Slices]
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
            src/test/pkg/MyProvider.java:9: Warning: Define intent filters in your manifest on your <provider android:name="test.pkg.MyProvider">; otherwise onMapIntentToUri will not be called. [Slices]
                public Uri onMapIntentToUri(Intent intent) {
                           ~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    private val listBuilder: TestFile = java(
        """
        package androidx.slice.builders;

        import android.app.slice.Slice;
        import android.content.Context;
        import android.net.Uri;
        import android.support.annotation.NonNull;
        import android.support.annotation.Nullable;
        import androidx.core.graphics.drawable.IconCompat;

        import java.util.function.Consumer;

        @SuppressWarnings("all") // stubs
        public class ListBuilder {
            public static final int ICON_IMAGE = SliceHints.ICON_IMAGE;
            public static final int SMALL_IMAGE = SliceHints.SMALL_IMAGE;
            public static final int LARGE_IMAGE = SliceHints.LARGE_IMAGE;
            public static final int UNKNOWN_IMAGE = SliceHints.UNKNOWN_IMAGE;
            public static final long INFINITY = SliceHints.INFINITY;


            public ListBuilder(@NonNull Context context, @NonNull Uri uri, long ttl) {
            }

            public Slice build() {
                return null;
            }

            @NonNull
            public ListBuilder addRow(@NonNull RowBuilder builder) {
                return this;
            }

            @NonNull
            public ListBuilder addRow(@NonNull Consumer<RowBuilder> c) {
                return this;
            }

            @NonNull
            public ListBuilder addGridRow(@NonNull GridRowBuilder builder) {
                return this;
            }
            @NonNull
            public ListBuilder setHeader(@NonNull HeaderBuilder builder) {
                return this;
            }

            @NonNull
            public ListBuilder addAction(@NonNull SliceAction action) {
                return this;
            }

            public static class RowBuilder {
                public RowBuilder(@NonNull ListBuilder parent) {
                }

                public RowBuilder(@NonNull ListBuilder parent, @NonNull Uri uri) {
                }

                public RowBuilder(@NonNull Context context, @NonNull Uri uri) {
                }

                @NonNull
                public RowBuilder setTitleItem(long timeStamp) {
                    return this;
                }

                @NonNull
                public RowBuilder setPrimaryAction(@NonNull SliceAction action) {
                    return this;
                }

                @NonNull
                public RowBuilder setTitle(@NonNull CharSequence title) {
                    return this;
                }

                @NonNull
                public RowBuilder setTitle(@Nullable CharSequence title, boolean isLoading) {
                    return this;
                }

                @NonNull
                public RowBuilder setSubtitle(@NonNull CharSequence subtitle) {
                    return setSubtitle(subtitle, false /* isLoading */);
                }

                @NonNull
                public RowBuilder setSubtitle(@Nullable CharSequence subtitle, boolean isLoading) {
                    return this;
                }

                @NonNull
                public RowBuilder addEndItem(long timeStamp) {
                    return this;
                }

                @NonNull
                public RowBuilder addEndItem(@NonNull IconCompat icon, int imageMode) {
                    return addEndItem(icon, imageMode, false /* isLoading */);
                }

                @NonNull
                public RowBuilder addEndItem(@Nullable IconCompat icon,int imageMode,
                                             boolean isLoading) {
                    return this;
                }
                @NonNull
                public RowBuilder addEndItem(@NonNull SliceAction action) {
                    return this;
                }
            }

            public static class HeaderBuilder {
                public HeaderBuilder(@NonNull ListBuilder parent) {
                }

                @NonNull
                public HeaderBuilder setTitle(@NonNull CharSequence title) {
                    return this;
                }

                @NonNull
                public HeaderBuilder setPrimaryAction(@NonNull SliceAction action) {
                    return this;
                }
            }


            @NonNull
            public ListBuilder addInputRange(@NonNull InputRangeBuilder b) {
                return this;
            }

            @NonNull
            public ListBuilder addInputRange(@NonNull Consumer<InputRangeBuilder> c) {
                return this;
            }

            @NonNull
            public ListBuilder addRange(@NonNull RangeBuilder rangeBuilder) {
                return this;
            }

            @NonNull
            public ListBuilder addRange(@NonNull Consumer<RangeBuilder> c) {
                return this;
            }


            public static class InputRangeBuilder {

                public InputRangeBuilder(ListBuilder lb) {
                }

                @NonNull
                public InputRangeBuilder setMin(int min) {
                    return this;
                }

                @NonNull
                public InputRangeBuilder setMax(int max) {
                    return this;
                }

                @NonNull
                public InputRangeBuilder setValue(int value) {
                    return this;
                }

                @NonNull
                public InputRangeBuilder setTitle(@NonNull CharSequence title) {
                    return this;
                }

                @NonNull
                public InputRangeBuilder setSubtitle(@NonNull CharSequence title) {
                    return this;
                }

                @NonNull
                public InputRangeBuilder setThumb(@NonNull IconCompat thumb) {
                    return this;
                }

                @NonNull
                public InputRangeBuilder setPrimaryAction(@NonNull SliceAction action) {
                    return this;
                }

                @NonNull
                public InputRangeBuilder setContentDescription(@NonNull CharSequence description) {
                    return this;
                }
            }

            public static class RangeBuilder {

                public RangeBuilder(@NonNull ListBuilder parent) {
                }

                @NonNull
                public RangeBuilder setMax(int max) {
                    return this;

                }

                @NonNull
                public RangeBuilder setValue(int value) {
                    return this;
                }

                @NonNull
                public RangeBuilder setTitle(@NonNull CharSequence title) {
                    return this;
                }

                @NonNull
                public RangeBuilder setSubtitle(@NonNull CharSequence title) {
                    return this;
                }

                @NonNull
                public RangeBuilder setPrimaryAction(@NonNull SliceAction action) {
                    return this;
                }

                @NonNull
                public RangeBuilder setContentDescription(@NonNull CharSequence description) {
                    return this;
                }
            }

        }
        """
    ).indented()

    private val gridRowBuilder: TestFile = java(
        """
        package androidx.slice.builders;

        import android.net.Uri;
        import android.support.annotation.NonNull;
        import android.support.annotation.Nullable;

        @SuppressWarnings("all") // stubs
        public class GridRowBuilder {
            public GridRowBuilder(ListBuilder parent) {
            }

            @NonNull
            public GridRowBuilder addCell(@NonNull CellBuilder builder) {
                return this;
            }

            @NonNull
            public GridRowBuilder setPrimaryAction(@NonNull SliceAction action) {
                return this;
            }

            public static final class CellBuilder {
                public CellBuilder(@NonNull GridRowBuilder parent) {
                }

                public CellBuilder(@NonNull GridRowBuilder parent, @NonNull Uri uri) {
                }

                @NonNull
                public CellBuilder addText(@NonNull CharSequence text) {
                    return addText(text, false /* isLoading */);
                }

                @NonNull
                public CellBuilder addText(@Nullable CharSequence text, boolean isLoading) {
                    return this;
                }

            }
        }
        """
    ).indented()

    private val sliceAction: TestFile = java(
        """
        package androidx.slice.builders;

        import android.app.PendingIntent;
        import android.support.annotation.NonNull;
        import androidx.core.graphics.drawable.IconCompat;

        @SuppressWarnings("all") // stubs
        public class SliceAction {
            public SliceAction(@NonNull PendingIntent action, @NonNull IconCompat actionIcon,
                               @NonNull CharSequence actionTitle) {
                this(action, actionIcon, ListBuilder.ICON_IMAGE, actionTitle);
            }

            public SliceAction(@NonNull PendingIntent action, @NonNull IconCompat actionIcon,
                               int imageMode, @NonNull CharSequence actionTitle) {
            }

            public SliceAction(@NonNull PendingIntent action, @NonNull IconCompat actionIcon,
                               @NonNull CharSequence actionTitle, boolean isChecked) {
            }

            public SliceAction(@NonNull PendingIntent action, @NonNull CharSequence actionTitle,
                               boolean isChecked) {
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
            import android.graphics.Bitmap;

            @SuppressWarnings("all") // stubs
            public class IconCompat {
                public static IconCompat createFromIcon(Bitmap icon) {
                    return null;
                }
            }
        """
    ).indented()

    private val sliceProvider: TestFile = java(
        """
            package androidx.slice;

            import android.app.slice.Slice;
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

            import android.app.slice.Slice;
            import android.content.ContentValues;
            import android.database.Cursor;
            import android.net.Uri;
            import android.support.annotation.NonNull;
            import android.support.annotation.Nullable;

            import androidx.slice.SliceProvider;

            @SuppressWarnings("all") // stubs
            public abstract class DefaultSliceProvider extends SliceProvider {
                @Override
                public Slice onBindSlice(Uri sliceUri) {
                    return null;
                }

                @Override
                public boolean onCreate() {
                    return false;
                }

                @Nullable
                @Override
                public Cursor query(@NonNull Uri uri, @Nullable String[] strings, @Nullable String s, @Nullable String[] strings1, @Nullable String s1) {
                    return null;
                }

                @Nullable
                @Override
                public String getType(@NonNull Uri uri) {
                    return null;
                }

                @Nullable
                @Override
                public Uri insert(@NonNull Uri uri, @Nullable ContentValues contentValues) {
                    return null;
                }

                @Override
                public int delete(@NonNull Uri uri, @Nullable String s, @Nullable String[] strings) {
                    return 0;
                }

                @Override
                public int update(@NonNull Uri uri, @Nullable ContentValues contentValues, @Nullable String s, @Nullable String[] strings) {
                    return 0;
                }
            }
        """
    ).indented()

    private val stubs = arrayOf(
        listBuilder,
        gridRowBuilder,
        sliceAction,
        sliceHints,
        iconCompat,
        sliceProvider,
        defaultSliceProvider
    )
}
