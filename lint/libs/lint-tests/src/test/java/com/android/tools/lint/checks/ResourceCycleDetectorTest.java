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

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("javadoc")
public class ResourceCycleDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ResourceCycleDetector();
    }

    public void testStyles() {
        String expected =
                ""
                        + "res/values/styles.xml:9: Error: Style DetailsPage_EditorialBuyButton should not extend itself [ResourceCycle]\n"
                        + "<style name=\"DetailsPage_EditorialBuyButton\" parent=\"@style/DetailsPage_EditorialBuyButton\" />\n"
                        + "                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "res/values/styles.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<resources>\n"
                                        + "\n"
                                        + "<style name=\"DetailsPage_BuyButton\" parent=\"@style/DetailsPage_Button\">\n"
                                        + "       <item name=\"android:textColor\">@color/buy_button</item>\n"
                                        + "       <item name=\"android:background\">@drawable/details_page_buy_button</item>\n"
                                        + "</style>\n"
                                        + "\n"
                                        + "<style name=\"DetailsPage_EditorialBuyButton\" parent=\"@style/DetailsPage_EditorialBuyButton\" />\n"
                                        + "<!-- Should have been:\n"
                                        + "<style name=\"DetailsPage_EditorialBuyButton\" parent=\"@style/DetailsPage_BuyButton\" />\n"
                                        + "-->\n"
                                        + "\n"
                                        + "</resources>\n"
                                        + "\n"))
                .run()
                .expect(expected);
    }

    public void testStyleImpliedParent() {
        String expected =
                ""
                        + "res/values/stylecycle.xml:3: Error: Potential cycle: PropertyToggle is the implied parent of PropertyToggle.Base and this defines the opposite [ResourceCycle]\n"
                        + "  <style name=\"PropertyToggle\" parent=\"@style/PropertyToggle.Base\"></style>\n"
                        + "                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "res/values/stylecycle.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "  <style name=\"PropertyToggle\" parent=\"@style/PropertyToggle.Base\"></style>\n"
                                        + "  <style name=\"PropertyToggle.Base\"></style>\n"
                                        + "</resources>\n"))
                .run()
                .expect(expected);
    }

    public void testStylesInvalidParent() {
        // Regression test for 115722815
        lint().files(
                        xml(
                                "res/values/styles.xml",
                                ""
                                        + "<resources>\n"
                                        + "    <style name=\"AppTheme\" parent=\"@string/app_name\">\n"
                                        + "        <item name=\"colorPrimary\">@color/colorPrimary</item>\n"
                                        + "    </style>\n"
                                        + "</resources>\n"
                                        + "\n"))
                .run()
                .expect(
                        ""
                                + "res/values/styles.xml:2: Error: Invalid parent reference: expected a @style [ResourceCycle]\n"
                                + "    <style name=\"AppTheme\" parent=\"@string/app_name\">\n"
                                + "                           ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "1 errors, 0 warnings");
    }

    public void testLayouts() {
        String expected =
                ""
                        + "res/layout/layoutcycle1.xml:10: Error: Layout layoutcycle1 should not include itself [ResourceCycle]\n"
                        + "        layout=\"@layout/layoutcycle1\" />\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "res/layout/layoutcycle1.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\" >\n"
                                        + "\n"
                                        + "    <include\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        layout=\"@layout/layoutcycle1\" />\n"
                                        + "\n"
                                        + "</LinearLayout>\n"))
                .run()
                .expect(expected);
    }

    public void testNoCycleThroughFramework() {
        lint().files(
                        xml(
                                "res/values/theme.xml",
                                ""
                                        + "<resources>\n"
                                        +
                                        // "    <style name=\"InterstitialDialogLayout\" />\n" +
                                        // "\n" +
                                        "    <style name=\"ButtonBar\" parent=\"@android:style/ButtonBar\" />\n"
                                        +
                                        // "    <style name=\"ButtonBarButton\"
                                        // parent=\"@android:style/Widget.Button\" />\n" +
                                        "\n"
                                        + "</resources>\n"))
                .run()
                .expectClean();
    }

    public void testColors() {
        String expected =
                ""
                        + "res/values/colorcycle1.xml:2: Error: Color test should not reference itself [ResourceCycle]\n"
                        + "    <color name=\"test\">@color/test</color>\n"
                        + "                       ^\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "res/values/colorcycle1.xml",
                                ""
                                        + "<resources>\n"
                                        + "    <color name=\"test\">@color/test</color>\n"
                                        + "</resources>\n"))
                .run()
                .expect(expected);
    }

    public void testAaptCrash() {
        String expected =
                ""
                        + "res/values/aaptcrash.xml:5: Error: This construct can potentially crash aapt during a build. Change @+id/titlebar to @id/titlebar and define the id explicitly using <item type=\"id\" name=\"titlebar\"/> instead. [AaptCrash]\n"
                        + "        <item name=\"android:id\">@+id/titlebar</item>\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "res/values/aaptcrash.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <style name=\"TitleBar\">\n"
                                        + "        <item name=\"android:orientation\">horizontal</item>\n"
                                        + "        <item name=\"android:id\">@+id/titlebar</item>\n"
                                        + "        <item name=\"android:background\">@drawable/bg_titlebar</item>\n"
                                        + "        <item name=\"android:layout_width\">fill_parent</item>\n"
                                        + "        <item name=\"android:layout_height\">@dimen/titlebar_height</item>\n"
                                        + "    </style>\n"
                                        + "</resources>\n"))
                .run()
                .expect(expected);
    }

    public void testDeepColorCycle1() {
        String expected =
                ""
                        + "res/values/colorcycle2.xml:2: Error: Color Resource definition cycle: test1 => test2 => test3 => test1 [ResourceCycle]\n"
                        + "    <color name=\"test1\">@color/test2</color>\n"
                        + "                        ^\n"
                        + "    res/values/colorcycle4.xml:2: Reference from @color/test3 to color/test1 here\n"
                        + "    res/values/colorcycle3.xml:2: Reference from @color/test2 to color/test3 here\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        colorcycle2,
                        xml(
                                "res/values/colorcycle3.xml",
                                ""
                                        + "<resources>\n"
                                        + "    <color name=\"test2\">@color/test3</color>\n"
                                        + "    <color name=\"test2b\">#ff00ff00</color>\n"
                                        + "</resources>\n"),
                        xml(
                                "res/values/colorcycle4.xml",
                                ""
                                        + "<resources>\n"
                                        + "    <color name=\"test3\">@color/test1</color>\n"
                                        + "</resources>\n"))
                .run()
                .expect(expected);
    }

    public void testDeepColorCycle2() {
        String expected =
                ""
                        + "res/values/colorcycle5.xml:2: Error: Color Resource definition cycle: test1 => test2 => test1 [ResourceCycle]\n"
                        + "    <color name=\"test1\">@color/test2</color>\n"
                        + "                        ^\n"
                        + "    res/values/colorcycle5.xml:3: Reference from @color/test2 to color/test1 here\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "res/values/colorcycle5.xml",
                                ""
                                        + "<resources>\n"
                                        + "    <color name=\"test1\">@color/test2</color>\n"
                                        + "    <color name=\"test2\">@color/test1</color>\n"
                                        + "</resources>\n"))
                .run()
                .expect(expected);
    }

    public void testDeepStyleCycle1() {
        String expected =
                ""
                        + "res/values/stylecycle1.xml:6: Error: Style Resource definition cycle: ButtonStyle => ButtonStyle.Base => ButtonStyle [ResourceCycle]\n"
                        + "    <style name=\"ButtonStyle\" parent=\"ButtonStyle.Base\">\n"
                        + "                              ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "    res/values/stylecycle1.xml:3: Reference from @style/ButtonStyle.Base to style/ButtonStyle here\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "res/values/stylecycle1.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<resources>\n"
                                        + "    <style name=\"ButtonStyle.Base\">\n"
                                        + "        <item name=\"android:textColor\">#ff0000</item>\n"
                                        + "    </style>\n"
                                        + "    <style name=\"ButtonStyle\" parent=\"ButtonStyle.Base\">\n"
                                        + "        <item name=\"android:layout_height\">40dp</item>\n"
                                        + "    </style>\n"
                                        + "</resources>\n"))
                .run()
                .expect(expected);
    }

    public void testDeepStyleCycle2() {
        String expected =
                ""
                        + "res/values/stylecycle2.xml:3: Error: Style Resource definition cycle: mystyle1 => mystyle2 => mystyle3 => mystyle1 [ResourceCycle]\n"
                        + "    <style name=\"mystyle1\" parent=\"@style/mystyle2\">\n"
                        + "                           ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "    res/values/stylecycle2.xml:9: Reference from @style/mystyle3 to style/mystyle1 here\n"
                        + "    res/values/stylecycle2.xml:6: Reference from @style/mystyle2 to style/mystyle3 here\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "res/values/stylecycle2.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<resources>\n"
                                        + "    <style name=\"mystyle1\" parent=\"@style/mystyle2\">\n"
                                        + "        <item name=\"android:textColor\">#ff0000</item>\n"
                                        + "    </style>\n"
                                        + "    <style name=\"mystyle2\" parent=\"@style/mystyle3\">\n"
                                        + "        <item name=\"android:textColor\">#ff0ff</item>\n"
                                        + "    </style>\n"
                                        + "    <style name=\"mystyle3\" parent=\"@style/mystyle1\">\n"
                                        + "        <item name=\"android:textColor\">#ffff00</item>\n"
                                        + "    </style>\n"
                                        + "</resources>\n"))
                .run()
                .expect(expected);
    }

    public void testDeepIncludeOk() {
        lint().files(
                        layout1,
                        layout2,
                        layout3,
                        xml(
                                "res/layout/layout4.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\" >\n"
                                        + "\n"
                                        + "    <Button\n"
                                        + "        android:id=\"@+id/button1\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:text=\"Button\" />\n"
                                        + "\n"
                                        + "    <Button\n"
                                        + "        android:id=\"@+id/button2\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:text=\"Button\" />\n"
                                        + "\n"
                                        + "</LinearLayout>\n"))
                .run()
                .expectClean();
    }

    public void testDeepIncludeCycle() {
        String expected =
                ""
                        + "res/layout/layout1.xml:10: Error: Layout Resource definition cycle: layout1 => layout2 => layout4 => layout1 [ResourceCycle]\n"
                        + "        layout=\"@layout/layout2\" />\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "    res/layout/layout4.xml:16: Reference from @layout/layout4 to layout/layout1 here\n"
                        + "    res/layout/layout2.xml:16: Reference from @layout/layout2 to layout/layout4 here\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        layout1,
                        layout2,
                        layout3,
                        xml(
                                "res/layout/layout4.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\" >\n"
                                        + "\n"
                                        + "    <RadioButton\n"
                                        + "        android:id=\"@+id/radioButton1\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:text=\"RadioButton\" />\n"
                                        + "\n"
                                        + "    <include\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        layout=\"@layout/layout1\" />\n"
                                        + "\n"
                                        + "</LinearLayout>\n"))
                .run()
                .expect(expected);
    }

    public void testDeepAliasCycle() {
        String expected =
                ""
                        + "res/values/aliases.xml:2: Error: Layout Resource definition cycle: layout10 => layout20 => layout30 => layout10 [ResourceCycle]\n"
                        + "    <item name=\"layout10\" type=\"layout\">@layout/layout20</item>\n"
                        + "                                        ^\n"
                        + "    res/values/aliases.xml:4: Reference from @layout/layout30 to layout/layout10 here\n"
                        + "    res/values/aliases.xml:3: Reference from @layout/layout20 to layout/layout30 here\n"
                        + "res/values/colorcycle2.xml:2: Error: Color Resource definition cycle: test1 => test2 => test1 [ResourceCycle]\n"
                        + "    <color name=\"test1\">@color/test2</color>\n"
                        + "                        ^\n"
                        + "    res/values/aliases.xml:5: Reference from @color/test2 to color/test1 here\n"
                        + "res/layout/layout1.xml:10: Error: Layout Resource definition cycle: layout1 => layout2 => layout4 => layout1 [ResourceCycle]\n"
                        + "        layout=\"@layout/layout2\" />\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "    res/values/aliases.xml:6: Reference from @layout/layout4 to layout/layout1 here\n"
                        + "    res/layout/layout2.xml:16: Reference from @layout/layout2 to layout/layout4 here\n"
                        + "3 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "res/values/aliases.xml",
                                ""
                                        + "<resources>\n"
                                        + "    <item name=\"layout10\" type=\"layout\">@layout/layout20</item>\n"
                                        + "    <item name=\"layout20\" type=\"layout\">@layout/layout30</item>\n"
                                        + "    <item name=\"layout30\" type=\"layout\">@layout/layout10</item>\n"
                                        + "    <item name=\"test2\" type=\"color\">@color/test1</item>\n"
                                        + "    <item name=\"layout4\" type=\"layout\">@layout/layout1</item>\n"
                                        + "</resources>\n"
                                        + "\n"),
                        layout1,
                        layout2,
                        layout3,
                        colorcycle2)
                .run()
                .expect(expected);
    }

    public void testColorStateListCycle() {
        String expected =
                ""
                        + "res/values/aliases2.xml:2: Error: Color Resource definition cycle: bright_foreground_dark => color1 => bright_foreground_dark [ResourceCycle]\n"
                        + "    <item name=\"bright_foreground_dark\" type=\"color\">@color/color1</item>\n"
                        + "                                                     ^\n"
                        + "    res/color/color1.xml:3: Reference from @color/color1 to color/bright_foreground_dark here\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "res/color/color1.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<selector xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <item android:state_enabled=\"false\" android:color=\"@color/bright_foreground_dark_disabled\"/>\n"
                                        + "    <item android:color=\"@color/bright_foreground_dark\"/>\n"
                                        + "</selector>\n"),
                        aliases2)
                .run()
                .expect(expected);
    }

    public void testDrawableStateListCycle() {
        String expected =
                ""
                        + "res/drawable/drawable1.xml:4: Error: Drawable Resource definition cycle: drawable1 => textfield_search_pressed => drawable2 => drawable1 [ResourceCycle]\n"
                        + "    <item android:state_window_focused=\"false\" android:state_enabled=\"true\"\n"
                        + "    ^\n"
                        + "    res/values/aliases2.xml:4: Reference from @drawable/drawable2 to drawable/drawable1 here\n"
                        + "    res/values/aliases2.xml:3: Reference from @drawable/textfield_search_pressed to drawable/drawable2 here\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "res/drawable/drawable1.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<selector xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "\n"
                                        + "    <item android:state_window_focused=\"false\" android:state_enabled=\"true\"\n"
                                        + "        android:drawable=\"@drawable/textfield_search_default\" />\n"
                                        + "\n"
                                        + "    <item android:state_pressed=\"true\"\n"
                                        + "        android:drawable=\"@drawable/textfield_search_pressed\" />\n"
                                        + "\n"
                                        + "    <item android:state_enabled=\"true\" android:state_focused=\"true\"\n"
                                        + "        android:drawable=\"@drawable/textfield_search_selected\" />\n"
                                        + "\n"
                                        + "    <item android:drawable=\"@drawable/textfield_search_default\" />\n"
                                        + "</selector>\n"
                                        + "\n"),
                        aliases2)
                .run()
                .expect(expected);
    }

    public void testFontCycle() {
        String expected =
                ""
                        + "res/font/font1.xml:6: Error: Font Resource definition cycle: font1 => font2 => font1 [ResourceCycle]\n"
                        + "        android:font=\"@font/font2\" />\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "    res/font/font2.xml:6: Reference from @font/font2 to font/font1 here\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "res/font/font1.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <font\n"
                                        + "        android:fontStyle=\"normal\"\n"
                                        + "        android:fontWeight=\"400\"\n"
                                        + "        android:font=\"@font/font2\" />\n"
                                        + "</font-family>"
                                        + "\n"),
                        xml(
                                "res/font/font2.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <font\n"
                                        + "        android:fontStyle=\"italic\"\n"
                                        + "        android:fontWeight=\"400\"\n"
                                        + "        android:font=\"@font/font1\" />\n"
                                        + "</font-family>"
                                        + "\n"))
                .run()
                .expect(expected);
    }

    public void testFontCycleWithLocation() {
        String expected =
                ""
                        + "res/font/font1.xml:6: Error: Font Resource definition cycle: font1 => font2 => font3 => font1 [ResourceCycle]\n"
                        + "        android:font=\"@font/font2\" />\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "    res/font/font3.xml:6: Reference from @font/font3 to font/font1 here\n"
                        + "    res/font/font2.xml:6: Reference from @font/font2 to font/font3 here\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        xml(
                                "res/font/font1.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <font\n"
                                        + "        android:fontStyle=\"normal\"\n"
                                        + "        android:fontWeight=\"400\"\n"
                                        + "        android:font=\"@font/font2\" />\n"
                                        + "</font-family>"
                                        + "\n"),
                        xml(
                                "res/font/font2.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <font\n"
                                        + "        android:fontStyle=\"italic\"\n"
                                        + "        android:fontWeight=\"400\"\n"
                                        + "        android:font=\"@font/font3\" />\n"
                                        + "</font-family>"
                                        + "\n"),
                        xml(
                                "res/font/font3.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <font\n"
                                        + "        android:fontStyle=\"normal\"\n"
                                        + "        android:fontWeight=\"700\"\n"
                                        + "        android:font=\"@font/font1\" />\n"
                                        + "</font-family>"
                                        + "\n"))
                .run()
                .expect(expected);
    }

    public void testAdaptiveIconCycle() {
        // Regression test for issue 67462465: Flag cycles in adaptive icons
        lint().files(
                        xml(
                                "res/drawable/drawable_recursive.xml",
                                ""
                                        + "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <background>\n"
                                        + "        <solid android:color=\"#ffffff\" />\n"
                                        + "    </background>\n"
                                        + "    <foreground android:drawable=\"@drawable/drawable_recursive\" />\n"
                                        + "</adaptive-icon>"))
                .run()
                .expect(
                        "res/drawable/drawable_recursive.xml:5: Error: Drawable drawable_recursive should not reference itself [ResourceCycle]\n"
                                + "    <foreground android:drawable=\"@drawable/drawable_recursive\" />\n"
                                + "                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "1 errors, 0 warnings");
    }

    public void testNoCycleWithDifferentTypes() {
        // Ensure that we don't think we have a cycle when the folder+resource types don't match
        lint().files(
                        // Sample from support/v17/leanback
                        xml(
                                "res/layout/lb_search_orb.xml",
                                ""
                                        + "<merge xmlns:android=\"http://schemas.android.com/apk/res/android\" >\n"
                                        + "\n"
                                        + "    <View\n"
                                        + "        android:id=\"@+id/search_orb\"\n"
                                        + "        android:layout_width=\"@dimen/lb_search_orb_size\"\n"
                                        + "        android:layout_height=\"@dimen/lb_search_orb_size\"\n"
                                        + "        android:background=\"@drawable/lb_search_orb\" />\n"
                                        + "\n"
                                        + "    <ImageView\n"
                                        + "        android:id=\"@+id/icon\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:layout_gravity=\"center\"\n"
                                        + "        android:src=\"@drawable/lb_ic_in_app_search\"\n"
                                        + "        android:contentDescription=\"@string/orb_search_action\" />\n"
                                        + "\n"
                                        + "</merge>"))
                .run()
                .expectClean();
    }

    public void testNoCycleWithToolsAttributes() {
        // Ensure that we don't consider tools:showIn as a real resource since it's not
        // a regular resource reference and is used to clue in the layout editor for what
        // layout to surround a layout with
        lint().files(
                        xml(
                                "res/layout/a.xml",
                                ""
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" >\n"
                                        + "\n"
                                        + "    <include\n"
                                        + "        layout=\"@layout/b\"\n"
                                        + "        android:layout_width=\"@dimen/lb_search_orb_size\"\n"
                                        + "        android:layout_height=\"@dimen/lb_search_orb_size\" />\n"
                                        + "\n"
                                        + "</LinearLayout>"),
                        xml(
                                "res/layout/b.xml",
                                ""
                                        + "<merge xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    tools:showIn=\"@layout/a\">\n"
                                        + "\n"
                                        + "</merge>"))
                .run()
                .expectClean();
    }

    public void testStringCycle() {
        String expected =
                ""
                        + "res/values/aliases3.xml:3: Error: String string2 should not reference itself [ResourceCycle]\n"
                        + "    <string name=\"string2\">@string/string2</string> <!-- ERROR: self reference -->\n"
                        + "                           ^\n"
                        + "res/values/aliases3.xml:4: Error: String Resource definition cycle: string3 => string4 => string5 => string3 [ResourceCycle]\n"
                        + "    <string name=\"string3\">@string/string4</string> <!-- ERROR: cycle 3-4-5-3 -->\n"
                        + "                           ^\n"
                        + "    res/values/aliases3.xml:6: Reference from @string/string5 to string/string3 here\n"
                        + "    res/values/aliases3.xml:5: Reference from @string/string4 to string/string5 here\n"
                        + "res/values/aliases3.xml:8: Error: Dimension Resource definition cycle: dimen1 => dimen2 => dimen3 => dimen1 [ResourceCycle]\n"
                        + "    <dimen name=\"dimen1\">@dimen/dimen2</dimen> <!-- ERROR: Cycle 1-2-3-1 -->\n"
                        + "                         ^\n"
                        + "    res/values/aliases3.xml:10: Reference from @dimen/dimen3 to dimen/dimen1 here\n"
                        + "    res/values/aliases3.xml:9: Reference from @dimen/dimen2 to dimen/dimen3 here\n"
                        + "3 errors, 0 warnings";
        lint().files(
                        xml(
                                "res/values/aliases3.xml",
                                ""
                                        + "<resources>\n"
                                        + "    <string name=\"string1\">String 1</string> <!-- OK -->\n"
                                        + "    <string name=\"string2\">@string/string2</string> <!-- ERROR: self reference -->\n"
                                        + "    <string name=\"string3\">@string/string4</string> <!-- ERROR: cycle 3-4-5-3 -->\n"
                                        + "    <string name=\"string4\">@string/string5</string>\n"
                                        + "    <string name=\"string5\">@string/string3</string>\n"
                                        + "\n"
                                        + "    <dimen name=\"dimen1\">@dimen/dimen2</dimen> <!-- ERROR: Cycle 1-2-3-1 -->\n"
                                        + "    <item name=\"dimen2\" type=\"dimen\">@dimen/dimen3</item>\n"
                                        + "    <dimen name=\"dimen3\">@dimen/dimen1</dimen>\n"
                                        + "\n"
                                        + "    <!-- not cycles: unrelated resource types -->\n"
                                        + "    <dimen name=\"abc\">@dimen/def</dimen>\n"
                                        + "    <string name=\"def\">@string/abc</string>\n"
                                        + "    <string name=\"ghi\">@dimen/ghi</string>\n"
                                        + "</resources>"))
                .run()
                .expect(expected);
    }

    private TestFile aliases2 =
            xml(
                    "res/values/aliases2.xml",
                    ""
                            + "<resources>\n"
                            + "    <item name=\"bright_foreground_dark\" type=\"color\">@color/color1</item>\n"
                            + "    <item name=\"textfield_search_pressed\" type=\"drawable\">@drawable/drawable2</item>\n"
                            + "    <item name=\"drawable2\" type=\"drawable\">@drawable/drawable1</item>\n"
                            + "</resources>\n"
                            + "\n");

    private TestFile colorcycle2 =
            xml(
                    "res/values/colorcycle2.xml",
                    ""
                            + "<resources>\n"
                            + "    <color name=\"test1\">@color/test2</color>\n"
                            + "    <color name=\"unrelated1\">@color/test2b</color>\n"
                            + "    <color name=\"unrelated2\">#ff0000</color>\n"
                            + "</resources>\n");

    private TestFile layout1 =
            xml(
                    "res/layout/layout1.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:layout_width=\"match_parent\"\n"
                            + "    android:layout_height=\"match_parent\"\n"
                            + "    android:orientation=\"vertical\" >\n"
                            + "\n"
                            + "    <include\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        layout=\"@layout/layout2\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:id=\"@+id/button1\"\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:id=\"@+id/button2\"\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "</LinearLayout>\n");

    private TestFile layout2 =
            xml(
                    "res/layout/layout2.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:layout_width=\"match_parent\"\n"
                            + "    android:layout_height=\"match_parent\"\n"
                            + "    android:orientation=\"vertical\" >\n"
                            + "\n"
                            + "    <RadioButton\n"
                            + "        android:id=\"@+id/radioButton1\"\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:text=\"RadioButton\" />\n"
                            + "\n"
                            + "    <include\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        layout=\"@layout/layout3\" />\n"
                            + "\n"
                            + "    <include\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        layout=\"@layout/layout4\" />\n"
                            + "\n"
                            + "</LinearLayout>\n");

    private TestFile layout3 =
            xml(
                    "res/layout/layout3.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:layout_width=\"match_parent\"\n"
                            + "    android:layout_height=\"match_parent\"\n"
                            + "    android:orientation=\"vertical\" >\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:id=\"@+id/button1\"\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <CheckBox\n"
                            + "        android:id=\"@+id/checkBox1\"\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:text=\"CheckBox\" />\n"
                            + "\n"
                            + "</LinearLayout>\n");
}
