/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Severity;
import com.android.utils.Pair;
import java.io.File;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.util.Collection;

@SuppressWarnings({"javadoc", "ConstantConditions"})
public class ApiLookupTest extends AbstractCheckTest {
    private final ApiLookup mDb = ApiLookup.get(createClient());

    public void testBasic() {
        assertEquals(
                5, mDb.getFieldVersion("android.Manifest$permission", "AUTHENTICATE_ACCOUNTS"));
        assertEquals(
                5, mDb.getFieldVersion("android.Manifest.permission", "AUTHENTICATE_ACCOUNTS"));
        assertEquals(
                5, mDb.getFieldVersion("android/Manifest$permission", "AUTHENTICATE_ACCOUNTS"));
        assertTrue(mDb.getFieldVersion("android/R$attr", "absListViewStyle") <= 1);
        assertEquals(11, mDb.getFieldVersion("android/R$attr", "actionMenuTextAppearance"));
        assertEquals(
                5,
                mDb.getMethodVersion(
                        "android.graphics.drawable.BitmapDrawable",
                        "<init>",
                        "(Landroid.content.res.Resources;Ljava.lang.String;)V"));
        assertEquals(
                5,
                mDb.getMethodVersion(
                        "android/graphics/drawable/BitmapDrawable",
                        "<init>",
                        "(Landroid/content/res/Resources;Ljava/lang/String;)V"));
        assertEquals(
                4,
                mDb.getMethodVersion(
                        "android/graphics/drawable/BitmapDrawable",
                        "setTargetDensity",
                        "(Landroid/util/DisplayMetrics;)V"));
        assertEquals(7, mDb.getClassVersion("android/app/WallpaperInfo"));
        assertEquals(11, mDb.getClassVersion("android/widget/StackView"));
        assertTrue(mDb.getClassVersion("ava/text/ChoiceFormat") <= 1);

        // Class lookup: Unknown class
        assertEquals(-1, mDb.getClassVersion("foo/Bar"));
        // Field lookup: Unknown class
        assertEquals(-1, mDb.getFieldVersion("foo/Bar", "FOOBAR"));
        // Field lookup: Unknown field
        assertEquals(-1, mDb.getFieldVersion("android/Manifest$permission", "FOOBAR"));
        // Method lookup: Unknown class
        assertEquals(
                -1,
                mDb.getMethodVersion(
                        "foo/Bar",
                        "<init>",
                        "(Landroid/content/res/Resources;Ljava/lang/String;)V"));
        // Method lookup: Unknown name
        assertEquals(
                -1,
                mDb.getMethodVersion(
                        "android/graphics/drawable/BitmapDrawable",
                        "foo",
                        "(Landroid/content/res/Resources;Ljava/lang/String;)V"));
        // Method lookup: Unknown argument list
        assertEquals(
                -1,
                mDb.getMethodVersion("android/graphics/drawable/BitmapDrawable", "<init>", "(I)V"));
    }

    public void testWildcardSyntax() {
        // Regression test:
        // This used to return 11 because of some wildcard syntax in the signature
        assertTrue(mDb.getMethodVersion("java/lang/Object", "getClass", "()") <= 1);
    }

    public void testIssue26467() {
        assertTrue(mDb.getMethodVersion("java/nio/ByteBuffer", "array", "()") <= 1);
        assertEquals(9, mDb.getMethodVersion("java/nio/Buffer", "array", "()"));
    }

    public void testNoInheritedConstructors() {
        assertTrue(mDb.getMethodVersion("java/util/zip/ZipOutputStream", "<init>", "()") <= 1);
        assertTrue(
                mDb.getMethodVersion(
                                "android/app/AliasActivity",
                                "<init>",
                                "(Landroid/content/Context;I)")
                        <= 1);
    }

    public void testIssue35190() {
        assertEquals(
                9,
                mDb.getMethodVersion("java/io/IOException", "<init>", "(Ljava/lang/Throwable;)V"));
    }

    public void testDeprecatedFields() {
        // Not deprecated:
        assertEquals(
                -1, mDb.getFieldDeprecatedIn("android/Manifest$permission", "GET_PACKAGE_SIZE"));
        // Field only has since > 1, no deprecation
        assertEquals(9, mDb.getFieldVersion("android/Manifest$permission", "NFC"));

        // Deprecated
        assertEquals(21, mDb.getFieldDeprecatedIn("android/Manifest$permission", "GET_TASKS"));
        // Field both deprecated and since > 1
        assertEquals(
                21, mDb.getFieldDeprecatedIn("android/Manifest$permission", "READ_SOCIAL_STREAM"));
        assertEquals(15, mDb.getFieldVersion("android/Manifest$permission", "READ_SOCIAL_STREAM"));
    }

    public void testDeprecatedMethods() {
        // Not deprecated:
        // assertEquals(12, mDb.getMethodVersion("android/app/Fragment", "onInflate",
        //        "(Landroid/app/Activity;Landroid/util/AttributeSet;Landroid/os/Bundle;)V"));
        assertEquals(
                24,
                mDb.getMethodDeprecatedIn(
                        "android/app/Activity", "setProgressBarIndeterminate", "(Z)V"));
        assertEquals(
                -1,
                mDb.getMethodDeprecatedIn(
                        "android/app/Activity", "getParent", "()Landroid/app/Activity;"));
        // Deprecated
        assertEquals(
                17,
                mDb.getMethodDeprecatedIn(
                        "android/content/IntentSender",
                        "getTargetPackage",
                        "()Ljava/lang/String;"));
        assertEquals(
                23,
                mDb.getMethodDeprecatedIn(
                        "android/app/Fragment",
                        "onInflate",
                        "(Landroid/app/Activity;Landroid/util/AttributeSet;Landroid/os/Bundle;)V"));
    }

    public void testDeprecatedClasses() {
        // Not deprecated:
        assertEquals(-1, mDb.getClassDeprecatedIn("android/app/Activity"));
        // Deprecated
        assertEquals(9, mDb.getClassDeprecatedIn("org/xml/sax/Parser"));
    }

    public void testRemovedFields() {
        // Not removed
        assertEquals(-1, mDb.getFieldRemovedIn("android/Manifest$permission", "GET_PACKAGE_SIZE"));
        // Field only has since > 1, no removal
        assertEquals(9, mDb.getFieldVersion("android/Manifest$permission", "NFC"));

        // Removed
        assertEquals(
                23, mDb.getFieldRemovedIn("android/Manifest$permission", "ACCESS_MOCK_LOCATION"));
        // Field both removed and since > 1
        assertEquals(
                23, mDb.getFieldRemovedIn("android/Manifest$permission", "AUTHENTICATE_ACCOUNTS"));
    }

    public void testRemovedMethods() {
        // Not removed
        assertEquals(
                -1,
                mDb.getMethodRemovedIn(
                        "android/app/Activity",
                        "enterPictureInPictureMode",
                        "(Landroid/app/PictureInPictureArgs;)Z"));
        // Moved to an interface
        assertEquals(
                -1,
                mDb.getMethodRemovedIn("android/database/sqlite/SQLiteDatabase", "close", "()V"));
        // Removed
        assertEquals(11, mDb.getMethodRemovedIn("android/app/Activity", "setPersistent", "(Z)V"));
    }

    public void testGetRemovedFields() {
        Collection<ApiMember> removedFields = mDb.getRemovedFields("android/Manifest$permission");
        assertTrue(removedFields.contains(new ApiMember("ACCESS_MOCK_LOCATION", 1, 0, 23)));
        assertTrue(removedFields.contains(new ApiMember("FLASHLIGHT", 1, 0, 24)));
        assertTrue(removedFields.contains(new ApiMember("READ_SOCIAL_STREAM", 15, 21, 23)));
        assertTrue(removedFields.stream().noneMatch(member -> member.getSignature().equals("NFC")));
    }

    public void testGetRemovedMethods() {
        Collection<ApiMember> removedMethods = mDb.getRemovedMethods("android/app/Activity");
        assertTrue(removedMethods.contains(new ApiMember("getInstanceCount()", 1, 0, 11)));
        assertTrue(removedMethods.contains(new ApiMember("setPersistent(Z)", 1, 0, 11)));
        assertTrue(
                removedMethods.stream().noneMatch(member -> member.getSignature().equals("NFC")));

        removedMethods = mDb.getRemovedMethods("android/database/sqlite/SQLiteProgram");
        assertTrue(
                removedMethods.contains(
                        new ApiMember("native_bind_string(ILjava/lang/String;)", 1, 0, 16)));
        // Method moved to a super class
        assertTrue(
                removedMethods.stream()
                        .noneMatch(member -> member.getSignature().equals("close()")));
    }

    public void testRemovedClasses() {
        // Not removed
        assertEquals(-1, mDb.getClassRemovedIn("android/app/Fragment"));
        // Removed
        assertEquals(24, mDb.getClassRemovedIn("android/graphics/AvoidXfermode"));
    }

    public void testInheritInterfaces() {
        // The onPreferenceStartFragment is inherited via the
        // android/preference/PreferenceFragment$OnPreferenceStartFragmentCallback
        // interface
        assertEquals(
                11,
                mDb.getMethodVersion(
                        "android/preference/PreferenceActivity",
                        "onPreferenceStartFragment",
                        "(Landroid/preference/PreferenceFragment;Landroid/preference/Preference;)"));
    }

    public void testInterfaceApi() {
        assertEquals(21, mDb.getClassVersion("android/animation/StateListAnimator"));
        assertEquals(
                11,
                mDb.getValidCastVersion(
                        "android/animation/AnimatorListenerAdapter",
                        "android/animation/Animator$AnimatorListener"));
        assertEquals(
                19,
                mDb.getValidCastVersion(
                        "android/animation/AnimatorListenerAdapter",
                        "android/animation/Animator$AnimatorPauseListener"));

        assertEquals(
                11, mDb.getValidCastVersion("android/animation/Animator", "java/lang/Cloneable"));
        assertEquals(
                22,
                mDb.getValidCastVersion(
                        "android/animation/StateListAnimator", "java/lang/Cloneable"));
    }

    public void testSuperClassCast() {
        assertEquals(
                22,
                mDb.getValidCastVersion(
                        "android/view/animation/AccelerateDecelerateInterpolator",
                        "android/view/animation/BaseInterpolator"));
    }

    public void testIsValidPackage() {
        assertTrue(isValidJavaPackage("java/lang/Integer"));
        assertTrue(isValidJavaPackage("java/util/Map$Entry"));
        assertTrue(isValidJavaPackage("javax/crypto/Cipher"));
        assertTrue(isValidJavaPackage("java/awt/font/NumericShaper"));

        assertFalse(isValidJavaPackage("javax/swing/JButton"));
        assertFalse(isValidJavaPackage("java/rmi/Naming"));
        assertFalse(isValidJavaPackage("java/lang/instrument/Instrumentation"));
    }

    private boolean isValidJavaPackage(String className) {
        return mDb.isValidJavaPackage(className, className.lastIndexOf('/'));
    }

    @Override
    protected Detector getDetector() {
        fail("This is not used in the ApiDatabase test");
        return null;
    }

    private File mCacheDir;

    @SuppressWarnings("StringBufferField")
    private final StringBuilder mLogBuffer = new StringBuilder();

    @SuppressWarnings({
        "ConstantConditions",
        "IOResourceOpenedButNotSafelyClosed",
        "ResultOfMethodCallIgnored"
    })
    @Override
    protected TestLintClient createClient() {
        mCacheDir = new File(getTempDir(), "lint-test-cache");
        mCacheDir.mkdirs();

        return new LookupTestClient();
    }

    @SuppressWarnings({
        "ConstantConditions",
        "IOResourceOpenedButNotSafelyClosed",
        "ResultOfMethodCallIgnored"
    })
    public void testCorruptedCacheHandling() throws Exception {
        if (ApiLookup.DEBUG_FORCE_REGENERATE_BINARY) {
            System.err.println("Skipping " + getName() + ": not valid while regenerating indices");
            return;
        }

        ApiLookup lookup;

        // Real cache:
        mCacheDir = createClient().getCacheDir(null, true);
        mLogBuffer.setLength(0);
        lookup = ApiLookup.get(new LookupTestClient());
        assertNotNull(lookup);
        assertEquals(11, lookup.getFieldVersion("android/R$attr", "actionMenuTextAppearance"));
        assertEquals("", mLogBuffer.toString()); // No warnings
        ApiLookup.dispose();

        // Custom cache dir: should also work
        mCacheDir = new File(getTempDir(), "test-cache");
        mCacheDir.mkdirs();
        mLogBuffer.setLength(0);
        lookup = ApiLookup.get(new LookupTestClient());
        assertNotNull(lookup);
        assertEquals(11, lookup.getFieldVersion("android/R$attr", "actionMenuTextAppearance"));
        assertEquals("", mLogBuffer.toString()); // No warnings
        ApiLookup.dispose();

        // Now truncate cache file
        File cacheFile =
                new File(
                        mCacheDir,
                        ApiLookup.getCacheFileName(
                                "api-versions.xml",
                                ApiLookup.getPlatformVersion(new LookupTestClient())));
        mLogBuffer.setLength(0);
        assertTrue(cacheFile.exists());
        RandomAccessFile raf = new RandomAccessFile(cacheFile, "rw");
        // Truncate file in half
        raf.setLength(100); // Broken header
        raf.close();
        ApiLookup.get(new LookupTestClient());
        String message = mLogBuffer.toString();
        // NOTE: This test is incompatible with the DEBUG_FORCE_REGENERATE_BINARY and WRITE_STATS
        // flags in the ApiLookup class, so if the test fails during development and those are
        // set, clear them.
        assertTrue(message.contains("Please delete the file and restart the IDE/lint:"));
        assertTrue(message.contains(mCacheDir.getPath()));
        ApiLookup.dispose();

        mLogBuffer.setLength(0);
        assertTrue(cacheFile.exists());
        raf = new RandomAccessFile(cacheFile, "rw");
        // Truncate file in half in the data portion
        raf.setLength(raf.length() / 2);
        raf.close();
        lookup = ApiLookup.get(new LookupTestClient());
        // This data is now truncated: lookup returns the wrong size.
        assertNotNull(lookup);
        lookup.getFieldVersion("android/R$attr", "actionMenuTextAppearance");
        assertTrue(message.contains("Please delete the file and restart the IDE/lint:"));
        assertTrue(message.contains(mCacheDir.getPath()));
        ApiLookup.dispose();

        mLogBuffer.setLength(0);
        assertTrue(cacheFile.exists());
        raf = new RandomAccessFile(cacheFile, "rw");
        // Truncate file to 0 bytes
        raf.setLength(0);
        raf.close();
        lookup = ApiLookup.get(new LookupTestClient());
        assertNotNull(lookup);
        assertEquals(11, lookup.getFieldVersion("android/R$attr", "actionMenuTextAppearance"));
        assertEquals("", mLogBuffer.toString()); // No warnings
        ApiLookup.dispose();
    }

    private static final boolean CHECK_DEPRECATED = true;

    private static void assertSameApi(String desc, int expected, int actual) {
        assertEquals(desc, expected, actual);
    }

    public void testDeprecatedIn() {
        assertEquals(9, mDb.getClassDeprecatedIn("org/xml/sax/Parser"));
        assertEquals(
                26,
                mDb.getFieldDeprecatedIn(
                        "android/accounts/AccountManager", "LOGIN_ACCOUNTS_CHANGED_ACTION"));

        assertEquals(
                20,
                mDb.getMethodDeprecatedIn(
                        "android/view/View", "fitSystemWindows", "(Landroid/graphics/Rect;)"));
        assertEquals(
                16,
                mDb.getMethodVersion("android/widget/CalendarView", "getWeekNumberColor", "()"));
        assertEquals(
                23,
                mDb.getMethodDeprecatedIn(
                        "android/widget/CalendarView", "getWeekNumberColor", "()"));
        assertEquals(
                19,
                mDb.getMethodVersion("android/webkit/WebView", "createPrintDocumentAdapter", "()"));
        // Regression test for 65376457: CreatePrintDocumentAdapter() was deprecated in api 21,
        // not api 3 as lint reports.
        // (The root bug was that for deprecation we also lowered it if superclasses were
        // deprecated (such as AbsoluteLayout, a superclass of WebView) - this is necessary when
        // computing version-requirements but not deprecation versions.
        assertEquals(
                21,
                mDb.getMethodDeprecatedIn(
                        "android/webkit/WebView", "createPrintDocumentAdapter", "()"));
    }

    public void testClassLookupInnerClasses() {
        assertEquals(24, mDb.getClassVersion("java/util/Locale$Category"));
        assertEquals(24, mDb.getClassVersion("java.util.Locale.Category"));
        assertEquals(1, mDb.getClassVersion("android/view/WindowManager$BadTokenException"));
        assertEquals(1, mDb.getClassVersion("android.view.WindowManager.BadTokenException"));
    }

    public void testClassDeprecation() {
        assertEquals(5, mDb.getClassDeprecatedIn("android/webkit/PluginData"));
        assertEquals(1, mDb.getClassVersion("java/io/LineNumberInputStream"));
        assertEquals(1, mDb.getClassDeprecatedIn("java/io/LineNumberInputStream"));
    }

    // Flaky test - http://b.android.com/225879
    @SuppressWarnings("unused")
    public void testFindEverything() {
        // Load the API versions file and look up every single method/field/class in there
        // (provided since != 1) and also check the deprecated calls.

        File file = createClient().findResource(ApiLookup.XML_FILE_PATH);
        if (file == null || !file.exists()) {
            return;
        }

        Api<ApiClass> info = Api.parseApi(file);
        for (ApiClass cls : info.getClasses().values()) {
            int classSince = cls.getSince();
            String className = cls.getName();
            if (className.startsWith("android/support/")) {
                continue;
            }
            assertSameApi(className, classSince, mDb.getClassVersion(className));

            for (String method : cls.getAllMethods(info)) {
                int since = cls.getMethod(method, info);
                int index = method.indexOf('(');
                String name = method.substring(0, index);
                String desc = method.substring(index);
                assertSameApi(method, since, mDb.getMethodVersion(className, name, desc));
            }
            for (String method : cls.getAllFields(info)) {
                int since = cls.getField(method, info);
                assertSameApi(method, since, mDb.getFieldVersion(className, method));
            }

            for (Pair<String, Integer> pair : cls.getInterfaces()) {
                String interfaceName = pair.getFirst();
                int api = pair.getSecond();
                assertSameApi(
                        interfaceName, api, mDb.getValidCastVersion(className, interfaceName));
            }
        }

        if (CHECK_DEPRECATED) {
            for (ApiClass cls : info.getClasses().values()) {
                int classDeprecatedIn = cls.getDeprecatedIn();
                String className = cls.getName();
                if (className.startsWith("android/support/")) {
                    continue;
                }
                if (classDeprecatedIn >= 1) {
                    assertSameApi(
                            className, classDeprecatedIn, mDb.getClassDeprecatedIn(className));
                } else {
                    assertSameApi(className, -1, mDb.getClassDeprecatedIn(className));
                }

                for (String method : cls.getAllMethods(info)) {
                    int deprecatedIn = cls.getMemberDeprecatedIn(method, info);
                    if (deprecatedIn == 0) {
                        deprecatedIn = -1;
                    }
                    int index = method.indexOf('(');
                    String name = method.substring(0, index);
                    String desc = method.substring(index);
                    assertSameApi(
                            method + " in " + className,
                            deprecatedIn,
                            mDb.getMethodDeprecatedIn(className, name, desc));
                }
                for (String method : cls.getAllFields(info)) {
                    int deprecatedIn = cls.getMemberDeprecatedIn(method, info);
                    if (deprecatedIn == 0) {
                        deprecatedIn = -1;
                    }
                    assertSameApi(
                            method, deprecatedIn, mDb.getFieldDeprecatedIn(className, method));
                }
            }
        }
    }

    public void testApi27() {
        // Regression test for 73514594; the following two attributes were added
        // *after* the prebuilt android.jar was checked into the source tree, which
        // is how the api-since data is computed. We're manually correcting for this
        // in the XML-to-binary database computation instead (and I plan to fix
        // metalava to also correct for this in the XML generation code.)
        assertEquals(27, mDb.getFieldVersion("android.R$attr", "navigationBarDividerColor"));
        assertEquals(27, mDb.getFieldVersion("android.R$attr", "windowLightNavigationBar"));
    }

    public void testLookUpContractSettings() {
        assertEquals(
                14, mDb.getFieldVersion("android/provider/ContactsContract$Settings", "DATA_SET"));
    }

    private final class LookupTestClient extends ToolsBaseTestLintClient {
        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Nullable
        @Override
        public File getCacheDir(@Nullable String name, boolean create) {
            assertNotNull(mCacheDir);
            if (create && !mCacheDir.exists()) {
                mCacheDir.mkdirs();
            }
            return mCacheDir;
        }

        @Override
        public void log(
                @NonNull Severity severity,
                @Nullable Throwable exception,
                @Nullable String format,
                @Nullable Object... args) {
            if (format != null) {
                mLogBuffer.append(String.format(format, args));
                mLogBuffer.append('\n');
            }
            if (exception != null) {
                StringWriter writer = new StringWriter();
                exception.printStackTrace(new PrintWriter(writer));
                mLogBuffer.append(writer.toString());
                mLogBuffer.append('\n');
            }
        }

        @Override
        public void log(Throwable exception, String format, Object... args) {
            log(Severity.WARNING, exception, format, args);
        }
    }
}
