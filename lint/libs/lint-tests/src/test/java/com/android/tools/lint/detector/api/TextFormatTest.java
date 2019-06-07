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

package com.android.tools.lint.detector.api;

import static com.android.SdkConstants.AUTO_URI;
import static com.android.tools.lint.detector.api.TextFormat.HTML;
import static com.android.tools.lint.detector.api.TextFormat.HTML_WITH_UNICODE;
import static com.android.tools.lint.detector.api.TextFormat.RAW;
import static com.android.tools.lint.detector.api.TextFormat.TEXT;

import com.android.tools.lint.checks.PrivateApiDetector;
import com.android.tools.lint.checks.infrastructure.TestIssueRegistry;
import junit.framework.TestCase;

public class TextFormatTest extends TestCase {
    private static String convertMarkup(String raw, TextFormat to) {
        return RAW.convertTo(raw, to);
    }

    public void testConvertMarkup() {
        assertEquals("", convertMarkup("", HTML));

        // Normal escapes
        assertEquals("foo bar", convertMarkup("foo bar", HTML));
        assertEquals("foo<br/>\nbar", convertMarkup("foo\nbar", HTML));
        assertEquals("foo<br/>\nbar", convertMarkup("foo\nbar", HTML));
        assertEquals("&lt;&amp;>'\"", convertMarkup("<&>'\"", HTML));
        assertEquals("&lt;&amp;>'\"", convertMarkup("<&>'\"", HTML_WITH_UNICODE));
        assertEquals("a\u1234\u2345bc", HTML.convertTo("a&#4660;&#9029;bc", HTML_WITH_UNICODE));

        // Unicode
        assertEquals("abc&#4660;&#9029;def&lt;", convertMarkup("abc\u1234\u2345def<", HTML));
        assertEquals(
                "abc\u1234\u2345def&lt;", convertMarkup("abc\u1234\u2345def<", HTML_WITH_UNICODE));

        // HTML Formatting
        assertEquals("<code>@TargetApi(11)</code>, ", convertMarkup("`@TargetApi(11)`, ", HTML));
        assertEquals(
                "with <code>getArguments()</code>.", convertMarkup("with `getArguments()`.", HTML));
        assertEquals("(<code>dip</code>)", convertMarkup("(`dip`)", HTML));
        assertEquals(" <code>0dp</code> ", convertMarkup(" `0dp` ", HTML));
        assertEquals(
                "resources under <code>$ANDROID_SK/platforms/android-$VERSION/data/res/.</code>",
                convertMarkup(
                        "resources under `$ANDROID_SK/platforms/android-$VERSION/data/res/.`",
                        HTML));
        assertEquals(
                "wrong format. Instead of <code>-keepclasseswithmembernames</code> use ",
                convertMarkup("wrong format. Instead of `-keepclasseswithmembernames` use ", HTML));
        assertEquals("<code>exported=false</code>)", convertMarkup("`exported=false`)", HTML));
        assertEquals(
                "by setting <code>inputType=\"text\"</code>.",
                convertMarkup("by setting `inputType=\"text\"`.", HTML));
        assertEquals(
                "* <code>View(Context context)</code><br/>\n",
                convertMarkup("* `View(Context context)`\n", HTML));
        assertEquals("The <code>@+id/</code> syntax", convertMarkup("The `@+id/` syntax", HTML));
        assertEquals("", convertMarkup("", HTML));
        assertEquals("", convertMarkup("", HTML));
        assertEquals("This is <i>italic</i>", convertMarkup("This is *italic*", HTML));
        assertEquals("This is <b>bold</b>", convertMarkup("This is **bold**", HTML));
        assertEquals(
                "This is <b><i>bold-italics</i></b>",
                convertMarkup("This is ***bold-italics***", HTML));
        assertEquals("This is italics", convertMarkup("This is *italics*", TEXT));
        assertEquals("This is bold", convertMarkup("This is **bold**", TEXT));
        assertEquals("This is bold-italics", convertMarkup("This is ***bold-italics***", TEXT));
        assertEquals("values-v<i>NN</i>", convertMarkup("values-v\u200b*NN*", HTML));
        assertEquals("<code>values-v</code><i>NN</i>", convertMarkup("`values-v`*NN*", HTML));
        assertEquals(
                "Visit <a href=\"http://google.com\">http://google.com</a>.",
                convertMarkup("Visit http://google.com.", HTML));
        assertEquals(
                "This is <code>monospace</code>!", convertMarkup("This is `monospace`!", HTML));
        assertEquals(
                "See <a href=\"http://developer.android.com/reference/android/view/"
                        + "WindowManager.LayoutParams.html#FLAG_KEEP_SCREEN_ON\">http://developer."
                        + "android.com/reference/android/view/WindowManager.LayoutParams.html#"
                        + "FLAG_KEEP_SCREEN_ON</a>.",
                convertMarkup(
                        "See http://developer.android.com/reference/android/view/WindowManager.Layout"
                                + "Params.html#FLAG_KEEP_SCREEN_ON.",
                        HTML));

        // Text formatting
        assertEquals("@TargetApi(11), ", convertMarkup("`@TargetApi(11)`, ", TEXT));
        assertEquals("with getArguments().", convertMarkup("with `getArguments()`.", TEXT));
        assertEquals("bold", convertMarkup("*bold*", TEXT));
        assertEquals("Visit http://google.com.", convertMarkup("Visit http://google.com.", TEXT));

        // Corners (match at the beginning and end)
        assertEquals("<i>italic</i>", convertMarkup("*italic*", HTML));
        assertEquals("<b>bold</b>", convertMarkup("**bold**", HTML));
        assertEquals("<b><i>bold-italics</i></b>", convertMarkup("***bold-italics***", HTML));
        assertEquals("<code>monospace</code>!", convertMarkup("`monospace`!", HTML));

        // Not formatting
        assertEquals("a*b", convertMarkup("a*b", HTML));
        assertEquals("a* b*", convertMarkup("a* b*", HTML));
        assertEquals("*a *b", convertMarkup("*a *b", HTML));
        assertEquals("Prefix is http:// ", convertMarkup("Prefix is http:// ", HTML));
        assertEquals("", convertMarkup("", HTML));
        assertEquals("", convertMarkup("", HTML));
        assertEquals("", convertMarkup("", HTML));
        assertEquals("", convertMarkup("", HTML));
        assertEquals("This is * not * bold", convertMarkup("This is * not * bold", HTML));
        assertEquals(
                "* List item 1<br/>\n* List Item 2",
                convertMarkup("* List item 1\n* List Item 2", HTML));
        assertEquals("myhttp://foo.bar", convertMarkup("myhttp://foo.bar", HTML));

        assertEquals(
                "Incorrect line ending: found carriage return (<code>\\r</code>) without "
                        + "corresponding newline (<code>\\n</code>)",
                convertMarkup(
                        "Incorrect line ending: found carriage return (`\\r`) without "
                                + "corresponding newline (`\\n`)",
                        HTML));
        assertEquals("This is a backslash: \\.", convertMarkup("This is a backslash: \\\\.", HTML));

        assertEquals("*literal asterisks*", convertMarkup("\\*literal asterisks\\*", TEXT));
        assertEquals("*literal asterisks*", convertMarkup("\\*literal asterisks\\*", HTML));
        assertEquals(
                "&lt;ignore path=\"*/test/*\" />n\"",
                convertMarkup("<ignore path=\\\"\\*/test/\\*\\\" />\\n\"", HTML));
    }

    public void testEndUrl() {
        // Regression test for 65713835: Invalid link when click tooltip
        assertEquals(
                "If you are using Firebase API's ("
                        + "<a href=\"http://firebase.google.com/docs/android/setup\">"
                        + "http://firebase.google.com/docs/android/setup</a>), Android Studio ...",
                convertMarkup(
                        "If you are using Firebase API's "
                                + "(http://firebase.google.com/docs/android/setup), "
                                + "Android Studio ...",
                        HTML));
    }

    public void testConvertMarkup2() {
        // http at the end:
        // Explanation from ManifestDetector#TARGET_NEWER
        String explanation =
                "When your application runs on a version of Android that is more recent than your "
                        + "targetSdkVersion specifies that it has been tested with, various compatibility "
                        + "modes kick in. This ensures that your application continues to work, but it may "
                        + "look out of place. For example, if the targetSdkVersion is less than 14, your "
                        + "app may get an option button in the UI.\n"
                        + "\n"
                        + "To fix this issue, set the targetSdkVersion to the highest available value. Then "
                        + "test your app to make sure everything works correctly. You may want to consult "
                        + "the compatibility notes to see what changes apply to each version you are adding "
                        + "support for: "
                        + "http://developer.android.com/reference/android/os/Build.VERSION_CODES.html";

        assertEquals(
                "When your application runs on a version of Android that is more recent than your "
                        + "targetSdkVersion specifies that it has been tested with, various compatibility "
                        + "modes kick in. This ensures that your application continues to work, but it may "
                        + "look out of place. For example, if the targetSdkVersion is less than 14, your "
                        + "app may get an option button in the UI.<br/>\n"
                        + "<br/>\n"
                        + "To fix this issue, set the targetSdkVersion to the highest available value. Then "
                        + "test your app to make sure everything works correctly. You may want to consult "
                        + "the compatibility notes to see what changes apply to each version you are adding "
                        + "support for: "
                        + "<a href=\"http://developer.android.com/reference/android/os/Build.VERSION_CODES."
                        + "html\">http://developer.android.com/reference/android/os/Build.VERSION_CODES.html"
                        + "</a>",
                convertMarkup(explanation, HTML));
    }

    public void testConvertMarkup3() {
        // embedded http markup test
        // Explanation from NamespaceDetector#CUSTOMVIEW
        String explanation =
                "When using a custom view with custom attributes in a library project, the layout "
                        + "must use the special namespace "
                        + AUTO_URI
                        + " instead of a URI which includes "
                        + "the library project's own package. This will be used to automatically adjust the "
                        + "namespace of the attributes when the library resources are merged into the "
                        + "application project.";
        assertEquals(
                "When using a custom view with custom attributes in a library project, the layout "
                        + "must use the special namespace "
                        + "<a href=\"http://schemas.android.com/apk/res-auto\">"
                        + "http://schemas.android.com/apk/res-auto</a> "
                        + "instead of a URI which includes the library project's own package. "
                        + "This will be used to automatically adjust the namespace of the attributes when "
                        + "the library resources are merged into the application project.",
                convertMarkup(explanation, HTML));
    }

    public void testConvertMarkup4() {
        // monospace test
        String explanation =
                "The manifest should contain a `<uses-sdk>` element which defines the "
                        + "minimum minimum API Level required for the application to run, "
                        + "as well as the target version (the highest API level you have tested "
                        + "the version for).";

        assertEquals(
                "The manifest should contain a <code>&lt;uses-sdk></code> element which defines the "
                        + "minimum minimum API Level required for the application to run, "
                        + "as well as the target version (the highest API level you have tested "
                        + "the version for).",
                convertMarkup(explanation, HTML));
    }

    public void testConvertMarkup5() {
        // monospace and bold test
        // From ManifestDetector#MULTIPLE_USES_SDK
        String explanation =
                "The `<uses-sdk>` element should appear just once; the tools will **not** merge the "
                        + "contents of all the elements so if you split up the attributes across multiple "
                        + "elements, only one of them will take effect. To fix this, just merge all the "
                        + "attributes from the various elements into a single <uses-sdk> element.";

        assertEquals(
                "The <code>&lt;uses-sdk></code> element should appear just once; the tools "
                        + "will <b>not</b> merge the "
                        + "contents of all the elements so if you split up the attributes across multiple "
                        + "elements, only one of them will take effect. To fix this, just merge all the "
                        + "attributes from the various elements into a single &lt;uses-sdk> element.",
                convertMarkup(explanation, HTML));
    }

    public void testConvertMarkup6() {
        // Embedded code next to attributes
        // From AlwaysShowActionDetector#ISSUE
        String explanation =
                "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in "
                        + "Java code is usually a deviation from the user interface style guide."
                        + "Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n"
                        + "\n"
                        + "If `always` is used sparingly there are usually no problems and behavior is "
                        + "roughly equivalent to `ifRoom` but with preference over other `ifRoom` "
                        + "items. Using it more than twice in the same menu is a bad idea.\n"
                        + "\n"
                        + "This check looks for menu XML files that contain more than two `always` "
                        + "actions, or some `always` actions and no `ifRoom` actions. In Java code, "
                        + "it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` "
                        + "and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.";

        assertEquals(
                "Using <code>showAsAction=\"always\"</code> in menu XML, or "
                        + "<code>MenuItem.SHOW_AS_ACTION_ALWAYS</code> in Java code is usually a deviation "
                        + "from the user interface style guide.Use <code>ifRoom</code> or the "
                        + "corresponding <code>MenuItem.SHOW_AS_ACTION_IF_ROOM</code> instead.<br/>\n"
                        + "<br/>\n"
                        + "If <code>always</code> is used sparingly there are usually no problems and "
                        + "behavior is roughly equivalent to <code>ifRoom</code> but with preference over "
                        + "other <code>ifRoom</code> items. Using it more than twice in the same menu "
                        + "is a bad idea.<br/>\n"
                        + "<br/>\n"
                        + "This check looks for menu XML files that contain more than two <code>always</code> "
                        + "actions, or some <code>always</code> actions and no <code>ifRoom</code> actions. "
                        + "In Java code, it looks for projects that contain references to "
                        + "<code>MenuItem.SHOW_AS_ACTION_ALWAYS</code> and no references to "
                        + "<code>MenuItem.SHOW_AS_ACTION_IF_ROOM</code>.",
                convertMarkup(explanation, HTML));
    }

    public void testFencedBlockHtml() {
        String html =
                RAW.convertTo(
                        ""
                                + "This is plain text.\n"
                                + "```\n"
                                + "   This is preformatted   text!\n"
                                + "\n"
                                + "\n"
                                + "Bye!\n"
                                + "```\n"
                                + "This is after.",
                        HTML);
        assertEquals(
                ""
                        + "This is plain text.\n"
                        + "<pre>\n"
                        + "   This is preformatted   text!\n"
                        + "\n"
                        + "\n"
                        + "Bye!\n"
                        + "</pre>\n"
                        + "This is after.",
                html);
    }

    public void testFencedBlockText() {
        String html =
                RAW.convertTo(
                        ""
                                + "This is plain text.\n"
                                + "```\n"
                                + "   This is preformatted   text!\n"
                                + "\n"
                                + "\n"
                                + "Bye!\n"
                                + "```\n"
                                + "This is after.",
                        TEXT);
        assertEquals(
                ""
                        + "This is plain text.\n"
                        + "   This is preformatted   text!\n"
                        + "\n"
                        + "\n"
                        + "Bye!\n"
                        + "This is after.",
                html);
    }

    public void testFencedBlockBegeinning() {
        String html =
                RAW.convertTo(
                        "" + "```\n" + "This is preformatted   text!\n" + "```\n" + "Bye.", HTML);
        assertEquals(
                "" + "\n" + "<pre>\n" + "This is preformatted   text!\n" + "</pre>\n" + "Bye.",
                html);
    }

    public void testFencedCodeBlock() {
        String html =
                RAW.convertTo(
                        ""
                                + "This is plain text.\n"
                                + "```unknown\n"
                                + "   val foo = \"This is a string\"!\n"
                                + "   /* this is a comment */\n"
                                + "```\n"
                                + "This is after.",
                        HTML);
        assertEquals(
                ""
                        + "This is plain text.\n"
                        + "<pre>\n"
                        + "   val foo = \"This is a string\"!\n"
                        + "   /* this is a comment */\n"
                        + "</pre>\n"
                        + "This is after.",
                html);
    }

    public void testConvertSelf() {
        // No changes
        assertEquals("`foo`<b>", RAW.convertTo("`foo`<b>", RAW));
        assertEquals("`foo`<b>", TEXT.convertTo("`foo`<b>", TEXT));
        assertEquals("`foo`<b>", HTML.convertTo("`foo`<b>", HTML));
    }

    public void testConvertFromHtml() {
        assertEquals(
                "Line 1\nLine 2 <div>\n",
                HTML.convertTo("<html>Line 1<br>Line 2\n<!-- comment -->&lt;div&gt;</html>", TEXT));
    }

    public void testEscapedNewlines() {
        assertEquals(
                "Using reflection to access hidden/private Android APIs is not safe; it will often not work on devices from other vendors, and it may suddenly stop working (if the API is removed) or crash spectacularly (if the API behavior changes, since there are no guarantees for compatibility).",
                PrivateApiDetector.PRIVATE_API.getExplanation(TextFormat.HTML));
        // Ignore newlines if they are escaped; used for line wrapping
        assertEquals("abcd<br/>\nef", RAW.convertTo("ab\\\ncd\nef", HTML));
    }

    public void testConvertFromHtml2() {
        assertEquals(
                ""
                        + "Using showAsAction=\"always\" in menu XML, or\n"
                        + "MenuItem.SHOW_AS_ACTION_ALWAYS in Java code is usually a\n"
                        + "deviation from the user interface style guide.Use ifRoom or\n"
                        + "the corresponding MenuItem.SHOW_AS_ACTION_IF_ROOM instead.\n"
                        + "\n"
                        + "If always is used sparingly there are usually no problems\n"
                        + "and behavior is roughly equivalent to ifRoom but with\n"
                        + "preference over other ifRoom items. Using it more than twice\n"
                        + "in the same menu is a bad idea.\n"
                        + "\n"
                        + "This check looks for menu XML files that contain more than\n"
                        + "two always actions, or some always actions and no ifRoom\n"
                        + "actions. In Java code, it looks for projects that contain\n"
                        + "references to MenuItem.SHOW_AS_ACTION_ALWAYS and no\n"
                        + "references to MenuItem.SHOW_AS_ACTION_IF_ROOM.\n",
                HTML.convertTo(
                        "Using <code>showAsAction=\"always\"</code> in       menu XML, or "
                                + "<code>MenuItem.SHOW_AS_ACTION_ALWAYS</code> in Java code is usually a deviation "
                                + "from the user interface style guide.Use <code>ifRoom</code> or the "
                                + "corresponding <code>MenuItem.SHOW_AS_ACTION_IF_ROOM</code> instead.<br/>\n"
                                + "<br/>\n"
                                + "If <code>always</code> is used sparingly there are usually no problems and "
                                + "behavior is roughly equivalent to <code>ifRoom</code> but with preference over "
                                + "other <code>ifRoom</code> items.    Using it more than twice in the same menu "
                                + "is a bad idea.<br/>\n"
                                + "<br/>\n"
                                + "This check looks for menu XML files that contain more than two <code>always</code> "
                                + "actions, or some <code>always</code> actions and no <code>ifRoom</code> actions. "
                                + "In Java code, it looks for projects that contain references to "
                                + "<code>MenuItem.SHOW_AS_ACTION_ALWAYS</code> and no references to "
                                + "<code>MenuItem.SHOW_AS_ACTION_IF_ROOM</code>.",
                        TEXT));
    }

    public void testConvertFromHtml3() {
        assertEquals(
                ""
                        + "Paragraph 1\n"
                        + "Paragraph 2 \n"
                        + "Span 1Span 2\n"
                        + "* List item 1\n"
                        + "* List Item 2\n"
                        + "End paragraph\n"
                        + "This is unformatted text.\n"
                        + "  This is\n"
                        + " formatted text.\n"
                        + "Back to unformatted text.\n",
                HTML.convertTo(
                        "<html><p>Paragraph 1</p><p>Paragraph 2 </p><b>Span 1</b><span>"
                                + "Span 2</span><br/><ul><li>List item 1<li>List Item 2</ul><p>"
                                + "End paragraph</p>"
                                + "  This is   unformatted text."
                                + "<pre>"
                                + "  This is\n"
                                + " formatted text."
                                + "</pre>Back to   unformatted text.</html>",
                        TEXT));
    }

    public void testNbsp() {
        assertEquals("&nbsp;&nbsp;text", RAW.convertTo("\u00a0\u00A0text", HTML));
    }

    public void test181820() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=181820
        // Make sure we handle formatting characters at the end
        assertEquals("foo bar *", convertMarkup("foo bar *", HTML));
    }

    public void testHttps() {
        assertEquals(
                "Visit <a href=\"https://google.com\">https://google.com</a>.",
                convertMarkup("Visit https://google.com.", HTML));
    }

    public void testConvertAll() {
        for (Issue issue : new TestIssueRegistry().getIssues()) {
            // Make sure there are no exceptions during conversion for any of the builtin strings
            issue.getExplanation(TextFormat.RAW);
            issue.getBriefDescription(TextFormat.RAW);
            issue.getExplanation(TextFormat.HTML);
            issue.getBriefDescription(TextFormat.HTML);
            issue.getExplanation(TextFormat.TEXT);
            issue.getBriefDescription(TextFormat.TEXT);
        }
    }

    public void testTextToRaw() {
        assertEquals(
                "foo\\\\bar\\*baz\\*", TextFormat.TEXT.convertTo("foo\\bar*baz*", TextFormat.RAW));
        assertEquals(
                "\\*\\*\\*foo\\*\\*\\*", TextFormat.TEXT.convertTo("***foo***", TextFormat.RAW));
        assertEquals("\\`symbol\\`", TextFormat.TEXT.convertTo("`symbol`", TextFormat.RAW));
        assertEquals(
                "foo\\bar*baz*", TextFormat.RAW.convertTo("foo\\\\bar\\*baz\\*", TextFormat.TEXT));
        assertEquals("`symbol`", TextFormat.RAW.convertTo("\\`symbol\\`", TextFormat.TEXT));
    }
}
