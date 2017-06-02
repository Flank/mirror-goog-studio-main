/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.xml;

import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.VALUE_MATCH_PARENT;
import static org.junit.Assert.assertEquals;

import com.android.SdkConstants;
import org.junit.Test;

public final class XmlBuilderTest {
    @Test
    public void toStringTabHost() {
        String expected =
                "<TabHost\n"
                        + "    android:layout_width=\"200dip\"\n"
                        + "    android:layout_height=\"300dip\">\n"
                        + "\n"
                        + "    <LinearLayout\n"
                        + "        android:layout_width=\"match_parent\"\n"
                        + "        android:layout_height=\"match_parent\"\n"
                        + "        android:orientation=\"vertical\">\n"
                        + "\n"
                        + "        <TabWidget\n"
                        + "            android:id=\"@android:id/tabs\"\n"
                        + "            android:layout_width=\"match_parent\"\n"
                        + "            android:layout_height=\"wrap_content\" />\n"
                        + "\n"
                        + "        <FrameLayout\n"
                        + "            android:id=\"@android:id/tab_content\"\n"
                        + "            android:layout_width=\"match_parent\"\n"
                        + "            android:layout_height=\"match_parent\">\n"
                        + "\n"
                        + "            <LinearLayout\n"
                        + "                android:id=\"@+id/tab_1\"\n"
                        + "                android:layout_width=\"match_parent\"\n"
                        + "                android:layout_height=\"match_parent\"\n"
                        + "                android:orientation=\"vertical\">\n"
                        + "\n"
                        + "            </LinearLayout>\n"
                        + "\n"
                        + "            <LinearLayout\n"
                        + "                android:id=\"@+id/tab_2\"\n"
                        + "                android:layout_width=\"match_parent\"\n"
                        + "                android:layout_height=\"match_parent\"\n"
                        + "                android:orientation=\"vertical\">\n"
                        + "\n"
                        + "            </LinearLayout>\n"
                        + "\n"
                        + "            <LinearLayout\n"
                        + "                android:id=\"@+id/tab_3\"\n"
                        + "                android:layout_width=\"match_parent\"\n"
                        + "                android:layout_height=\"match_parent\"\n"
                        + "                android:orientation=\"vertical\">\n"
                        + "\n"
                        + "            </LinearLayout>\n"
                        + "        </FrameLayout>\n"
                        + "    </LinearLayout>\n"
                        + "</TabHost>\n";

        XmlBuilder builder =
                new XmlBuilder()
                        .startTag("TabHost")
                        .androidAttribute(ATTR_LAYOUT_WIDTH, "200dip")
                        .androidAttribute(ATTR_LAYOUT_HEIGHT, "300dip")
                          .startTag("LinearLayout")
                          .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
                          .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_MATCH_PARENT)
                          .androidAttribute("orientation", "vertical")
                            .startTag("TabWidget")
                            .androidAttribute("id", "@android:id/tabs")
                            .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
                            .androidAttribute(ATTR_LAYOUT_HEIGHT, SdkConstants.VALUE_WRAP_CONTENT)
                            .endTag("TabWidget")
                            .startTag("FrameLayout")
                            .androidAttribute("id", "@android:id/tab_content")
                            .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
                            .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_MATCH_PARENT);

        for (int i = 0; i < 3; i++) {
            builder.startTag("LinearLayout")
                    .androidAttribute("id", "@+id/tab_" + (i + 1))
                    .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
                    .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_MATCH_PARENT)
                    .androidAttribute("orientation", "vertical")
                    .endTag("LinearLayout");
        }

        builder.endTag("FrameLayout").endTag("LinearLayout").endTag("TabHost");

        assertEquals(expected, builder.toString());
    }

    @Test
    public void toStringEmptyElementNotLayout() {
        String expected = "<TabWidget />\n";

        String actual = new XmlBuilder().startTag("TabWidget").endTag("TabWidget").toString();

        assertEquals(expected, actual);
    }

    @Test
    public void toStringEmptyElementLayout() {
        String expected = "<LinearLayout>\n" +
                          "\n" +
                          "</LinearLayout>\n";

        String actual = new XmlBuilder().startTag("LinearLayout").endTag("LinearLayout").toString();

        assertEquals(expected, actual);
    }

    @Test
    public void toStringNoClosePreviousTagWithoutAttributes() {
        String expected = "<Foo>\n\n" +
                          "    <Bar />\n" +
                          "</Foo>\n";

        String actual =
                new XmlBuilder()
                        .startTag("Foo")
                          .startTag("Bar")
                          .endTag("Bar")
                        .endTag("Foo")
                        .toString();

        assertEquals(expected, actual);
    }

    @Test
    public void toStringAttributeWithNoNamespace() {
        String expected = "<Foo\n" +
                          "    name=\"value\" />\n";

        String actual =
                new XmlBuilder()
                        .startTag("Foo")
                        .attribute("", "name", "value")
                        .endTag("Foo")
                        .toString();

        assertEquals(expected, actual);
    }

    @Test
    public void characterDataInElementWithNoAttributes() {
        String expected =
                "<resources>\n"
                        + "\n"
                        + "    <style\n"
                        + "        name=\"vertical_orientation\">\n"
                        + "\n"
                        + "        <item>\n"
                        + "            vertical\n"
                        + "        </item>\n"
                        + "    </style>\n"
                        + "</resources>\n";

        String actual =
                new XmlBuilder()
                        .startTag("resources")
                          .startTag("style")
                          .attribute("name", "vertical_orientation")
                            .startTag("item")
                            .characterData("vertical")
                            .endTag("item")
                          .endTag("style")
                        .endTag("resources")
                        .toString();

        assertEquals(expected, actual);
    }

    @Test
    public void characterDataInElementWithAttributes() {
        String expected =
                "<resources>\n"
                        + "\n"
                        + "    <style\n"
                        + "        name=\"vertical_orientation\">\n"
                        + "\n"
                        + "        <item\n"
                        + "            name=\"android:orientation\">\n"
                        + "            vertical\n"
                        + "        </item>\n"
                        + "    </style>\n"
                        + "</resources>\n";

        String actual =
                new XmlBuilder()
                        .startTag("resources")
                          .startTag("style")
                          .attribute("name", "vertical_orientation")
                            .startTag("item")
                            .attribute("name", "android:orientation")
                            .characterData("vertical")
                            .endTag("item")
                          .endTag("style")
                        .endTag("resources")
                        .toString();

        assertEquals(expected, actual);
    }
}
