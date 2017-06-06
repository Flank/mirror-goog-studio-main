/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.builder.symbols;

import com.android.utils.XmlUtils;
import com.google.common.truth.Truth;
import org.junit.Test;

/** Tests for the {@link ResourceExtraXmlParser}. */
public class ResourceExtraXmlParserTest {

    @Test
    public void parseExtra() throws Exception {
        String xml =
                ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<android.support.design.widget.CoordinatorLayout "
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    android:layout_width=\"match_parent\"\n"
                        + "    android:layout_height=\"match_parent\"\n"
                        + "    android:fitsSystemWindows=\"true\"\n"
                        + "    tools:context=\"com.example.user.foo.Main\">\n"
                        + "\n"
                        + "    <android.support.design.widget.AppBarLayout\n"
                        + "        android:layout_width=\"match_parent\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:theme=\"@style/AppTheme.AppBarOverlay\">\n"
                        + "\n"
                        + "        <android.support.v7.widget.Toolbar\n"
                        + "            android:id=\"@+id/toolbar\"\n"
                        + "            android:layout_width=\"match_parent\"\n"
                        + "            android:layout_height=\"?attr/actionBarSize\"\n"
                        + "            android:background=\"?attr/colorPrimary\"\n"
                        + "            app:popupTheme=\"@style/AppTheme.PopupOverlay\" />\n"
                        + "\n"
                        + "    </android.support.design.widget.AppBarLayout>\n"
                        + "\n"
                        + "    <include layout=\"@layout/content_main\" />\n"
                        + "\n"
                        + "    <android.support.design.widget.FloatingActionButton\n"
                        + "        android:id=\"@+id/fab\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:layout_gravity=\"bottom|end\"\n"
                        + "        android:layout_margin=\"dimen/fab_margin\"\n"
                        + "        app:srcCompat=\"@android:drawable/ic_dialog_email\" />\n"
                        + "\n"
                        + "</android.support.design.widget.CoordinatorLayout>";

        SymbolTable table =
                ResourceExtraXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        SymbolTable expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("id", "toolbar", "int", 0x7f_0c_0001))
                        .add(SymbolTestUtils.createSymbol("id", "fab", "int", 0x7f_0c_0002))
                        .build();

        Truth.assertThat(table).isEqualTo(expected);
    }
}
