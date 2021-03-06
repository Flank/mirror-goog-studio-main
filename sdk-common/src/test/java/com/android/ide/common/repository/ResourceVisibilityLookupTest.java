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
package com.android.ide.common.repository;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.testutils.TestUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;

public class ResourceVisibilityLookupTest extends TestCase {

    public void test() throws IOException {
        TestIdeAndroidLibrary library =
                createTestLibrary(
                        "com.android.tools:test-library:1.0.0",
                        ""
                                + "int dimen activity_horizontal_margin 0x7f030000\n"
                                + "int dimen activity_vertical_margin 0x7f030001\n"
                                + "int id action_settings 0x7f060000\n"
                                + "int layout activity_main 0x7f020000\n"
                                + "int menu menu_main 0x7f050000\n"
                                + "int string action_settings 0x7f040000\n"
                                + "int string app_name 0x7f040001\n"
                                + "int string hello_world 0x7f040002",
                        ""
                                + ""
                                + "dimen activity_vertical\n"
                                + "id action_settings\n"
                                + "layout activity_main\n");

        ResourceVisibilityLookup visibility =
                ResourceVisibilityLookup.create(
                        library.artifactAddress,
                        library.allResources,
                        library.publicResources);
        assertTrue(visibility.isPrivate(ResourceType.DIMEN, "activity_horizontal_margin"));
        assertFalse(visibility.isPrivate(ResourceType.ID, "action_settings"));
        assertFalse(visibility.isPrivate(ResourceType.LAYOUT, "activity_main"));
        //noinspection ConstantConditions
        assertTrue(visibility.isPrivate(ResourceUrl.parse("@dimen/activity_horizontal_margin")));

        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "activity_vertical")); // public
        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "unknown")); // not in this library
    }

    public void testAllPrivate() throws IOException {
        TestIdeAndroidLibrary library =
                createTestLibrary(
                        "com.android.tools:test-library:1.0.0",
                        ""
                                + "int dimen activity_horizontal_margin 0x7f030000\n"
                                + "int dimen activity_vertical_margin 0x7f030001\n"
                                + "int id action_settings 0x7f060000\n"
                                + "int layout activity_main 0x7f020000\n"
                                + "int menu menu_main 0x7f050000\n"
                                + "int string action_settings 0x7f040000\n"
                                + "int string app_name 0x7f040001\n"
                                + "int string hello_world 0x7f040002",
                        "");

        ResourceVisibilityLookup visibility =
                ResourceVisibilityLookup.create(
                        library.artifactAddress,
                        library.allResources,
                        library.publicResources);
        assertTrue(visibility.isPrivate(ResourceType.DIMEN, "activity_horizontal_margin"));
        assertTrue(visibility.isPrivate(ResourceType.ID, "action_settings"));
        assertTrue(visibility.isPrivate(ResourceType.LAYOUT, "activity_main"));
        assertTrue(visibility.isPrivate(ResourceType.DIMEN, "activity_vertical_margin"));

        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "unknown")); // not in this library
    }

    public void testNotDeclared() throws IOException {
        TestIdeAndroidLibrary library =
                createTestLibrary("com.android.tools:test-library:1.0.0", "", null);

        ResourceVisibilityLookup visibility =
                ResourceVisibilityLookup.create(
                        library.artifactAddress,
                        library.allResources,
                        library.publicResources);
        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "activity_horizontal_margin"));
        assertFalse(visibility.isPrivate(ResourceType.ID, "action_settings"));
        assertFalse(visibility.isPrivate(ResourceType.LAYOUT, "activity_main"));
        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "activity_vertical"));

        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "unknown")); // not in this library
    }

    public void testCombined() throws IOException {
        TestIdeAndroidLibrary library1 =
                createTestLibrary(
                        "com.android.tools:test-library:1.0.0",
                        ""
                                + "int dimen activity_horizontal_margin 0x7f030000\n"
                                + "int dimen activity_vertical_margin 0x7f030001\n"
                                + "int id action_settings 0x7f060000\n"
                                + "int layout activity_main 0x7f020000\n"
                                + "int menu menu_main 0x7f050000\n"
                                + "int string action_settings 0x7f040000\n"
                                + "int string app_name 0x7f040001\n"
                                + "int string hello_world 0x7f040002",
                        "string hello_world");
        TestIdeAndroidLibrary library2 =
                createTestLibrary(
                        "com.android.tools:test-library2:1.0.0",
                        ""
                                + "int layout foo 0x7f030001\n"
                                + "int layout bar 0x7f060000\n"
                                // Used public, but not explicitly declared: should remain public
                                // even though from the perspective of this library it looks private
                                // since this is a usage/override, not a declaration
                                + "int string hello_world 0x7f040003",
                        "" + "layout foo\n");

        List<TestIdeAndroidLibrary> androidLibraries = Arrays.asList(library1, library2);
        ResourceVisibilityLookup visibility = createFrom(androidLibraries);
        assertTrue(visibility.isPrivate(ResourceType.DIMEN, "activity_horizontal_margin"));
        assertTrue(visibility.isPrivate(ResourceType.ID, "action_settings"));
        assertTrue(visibility.isPrivate(ResourceType.LAYOUT, "activity_main"));
        assertTrue(visibility.isPrivate(ResourceType.DIMEN, "activity_vertical_margin"));
        assertFalse(visibility.isPrivate(ResourceType.LAYOUT, "foo"));
        assertTrue(visibility.isPrivate(ResourceType.LAYOUT, "bar"));
        assertFalse(visibility.isPrivate(ResourceType.STRING, "hello_world"));

        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "unknown")); // not in this library
    }

    public void testDependency() throws IOException {
        TestIdeAndroidLibrary library1 =
                createTestLibrary(
                        "com.android.tools:test-library:1.0.0",
                        ""
                                + "int dimen activity_horizontal_margin 0x7f030000\n"
                                + "int dimen activity_vertical_margin 0x7f030001\n"
                                + "int id action_settings 0x7f060000\n"
                                + "int layout activity_main 0x7f020000\n"
                                + "int menu menu_main 0x7f050000\n"
                                + "int string action_settings 0x7f040000\n"
                                + "int string app_name 0x7f040001\n"
                                + "int string hello_world 0x7f040002",
                        "");
        TestIdeAndroidLibrary library2 =
                createTestLibrary(
                        "com.android.tools:test-library2:1.0.0",
                        "" + "int layout foo 0x7f030001\n" + "int layout bar 0x7f060000\n",
                        ""
                        + "layout foo\n" /*,
                                                 Collections.singletonList(library1)*/); // TODO(b/158836360): Review when the dependency hierarchy is available.

        List<TestIdeAndroidLibrary> androidLibraries = Arrays.asList(library1, library2);
        ResourceVisibilityLookup visibility = createFrom(androidLibraries);
        assertTrue(visibility.isPrivate(ResourceType.DIMEN, "activity_horizontal_margin"));
        assertTrue(visibility.isPrivate(ResourceType.ID, "action_settings"));
        assertTrue(visibility.isPrivate(ResourceType.LAYOUT, "activity_main"));
        assertTrue(visibility.isPrivate(ResourceType.DIMEN, "activity_vertical_margin"));
        assertFalse(visibility.isPrivate(ResourceType.LAYOUT, "foo"));
        assertTrue(visibility.isPrivate(ResourceType.LAYOUT, "bar"));

        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "unknown")); // not in this library
    }

    public void testManager() throws IOException {
        TestIdeAndroidLibrary library =
                createTestLibrary(
                        "com.android.tools:test-library:1.0.0",
                        ""
                                + "int dimen activity_horizontal_margin 0x7f030000\n"
                                + "int dimen activity_vertical_margin 0x7f030001\n"
                                + "int id action_settings 0x7f060000\n"
                                + "int layout activity_main 0x7f020000\n"
                                + "int menu menu_main 0x7f050000\n"
                                + "int string action_settings 0x7f040000\n"
                                + "int string app_name 0x7f040001\n"
                                + "int string hello_world 0x7f040002",
                        "");
        assertTrue(
                createFrom(library)
                        .isPrivate(ResourceType.DIMEN, "activity_horizontal_margin"));

        assertTrue(createFrom(library)
                           .isPrivate(ResourceType.DIMEN, "activity_horizontal_margin"));
    }

    public void testImportedResources() throws IOException {
        // Regression test for https://code.google.com/p/android/issues/detail?id=183120 :
        // When a library depends on another library, all the resources from the dependency
        // are imported and exposed in R.txt from the downstream library too. When both
        // libraries expose public resources, we have to be careful such that we don't
        // take the presence of a resource (imported) and the absence of a public.txt declaration
        // (for the imported symbol in the dependent library) as evidence that this is a private
        // resource.
        TestIdeAndroidLibrary library1 =
                createTestLibrary(
                        "com.android.tools:test-library:1.0.0",
                        ""
                                + "int dimen public_library1_resource1 0x7f030000\n"
                                + "int dimen public_library1_resource2 0x7f030001\n"
                                + "int dimen private_library1_resource 0x7f030002\n",
                        ""
                                + "dimen public_library1_resource1\n"
                                + "dimen public_library1_resource2\n");

        TestIdeAndroidLibrary library2 =
                createTestLibrary(
                        "com.android.tools:test-library2:1.0.0",
                        ""
                                + "int dimen public_library2_resource1 0x7f030000\n"
                                + "int dimen public_library2_resource2 0x7f030001\n",
                        null // nothing marked as private: everything exposed
                );
        TestIdeAndroidLibrary library3 =
                createTestLibrary(
                        "com.android.tools:test-library3:1.0.0",
                        ""
                                + "int dimen public_library1_resource1 0x7f030000\n" // merged from
                                // library1
                                + "int dimen public_library1_resource2 0x7f030001\n"
                                + "int dimen private_library1_resource 0x7f030002\n"
                                + "int dimen public_library2_resource1 0x7f030003\n" // merged from
                                // library2
                                + "int dimen public_library2_resource2 0x7f030004\n"
                                + "int dimen public_library3_resource1 0x7f030005\n" // unique to
                                // library3
                                + "int dimen private_library3_resource 0x7f030006\n",
                        ""
                                + "dimen public_library2_resource1\n" /*,
                                                                      Arrays.asList(library1, library2)*/); // TODO(b/158836360): Review when the dependency hierarchy is available.

        List<TestIdeAndroidLibrary> androidLibraries = Arrays.asList(library1, library2, library3);
        ResourceVisibilityLookup visibility = createFrom(androidLibraries);
        assertTrue(visibility.isPrivate(ResourceType.DIMEN, "private_library1_resource"));
        assertTrue(visibility.isPrivate(ResourceType.DIMEN, "private_library3_resource"));
        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "public_library1_resource1"));
        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "public_library1_resource2"));
        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "public_library2_resource1"));
        assertTrue(
                visibility.isPrivate(
                        ResourceType.DIMEN, "public_library2_resource2")); // private in one library
        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "public_library3_resource"));
    }

    // TODO(b/158836360): Review when the dependency hierarchy is available.
    // public void testSymbolProvider() throws Exception {
    //    IdeLibrary library1 = createMockLibrary(
    //      "com.android.tools:test-library:1.0.0",
    //      ""
    //      + "int dimen public_library1_resource1 0x7f030000\n"
    //      + "int dimen public_library1_resource2 0x7f030001\n"
    //      + "int dimen private_library1_resource 0x7f030002\n",
    //      ""
    //      + "dimen public_library1_resource1\n"
    //      + "dimen public_library1_resource2\n"
    //    );
    //
    //    IdeLibrary library3 = createMockLibrary(
    //      "com.android.tools:test-library3:1.0.0",
    //      ""
    //      + "int dimen public_library1_resource1 0x7f030000\n" // merged from library1
    //      + "int dimen public_library1_resource2 0x7f030001\n"
    //      + "int dimen private_library1_resource 0x7f030002\n"
    //      + "int dimen public_library2_resource1 0x7f030003\n" // merged from library2
    //      + "int dimen public_library2_resource2 0x7f030004\n"
    //      + "int dimen public_library3_resource1 0x7f030005\n" // unique to library3
    //      + "int dimen private_library3_resource 0x7f030006\n",
    //      ""
    //        + "dimen public_library2_resource1\n",
    //      Collections.singletonList(library1)
    //    );
    //
    //    SymbolProvider provider = new SymbolProvider();
    //    Multimap<String, ResourceType> symbols = provider.getSymbols(library3);
    //
    //    // Exclude imported symbols
    //    assertFalse(symbols.get("public_library1_resource1").iterator().hasNext());
    //
    //    // Make sure non-imported symbols are there
    //    assertSame(ResourceType.DIMEN,
    // symbols.get("public_library3_resource1").iterator().next());
    //
    //    // Make sure we're actually caching results
    //    Multimap<String, ResourceType> symbols2 = provider.getSymbols(library3);
    //    assertSame(symbols, symbols2);
    // }

    static class TestIdeAndroidLibrary {

        TestIdeAndroidLibrary(@NonNull String artifactAddress,
                @NonNull File allResources,
                @NonNull File publicResources) {
            this.artifactAddress = artifactAddress;
            this.allResources = allResources;
            this.publicResources = publicResources;
        }

        public @NonNull String artifactAddress;

        public @NonNull File allResources;

        public @NonNull File publicResources;
    }

    public static TestIdeAndroidLibrary createTestLibrary(
            String name, String allResources, String publicResources) throws IOException {
        // Identical to PrivateResourceDetectorTest, but these are in test modules that
        // can't access each other
        final File tempDir = TestUtils.createTempDirDeletedOnExit().toFile();

        File rFile = new File(tempDir, FN_RESOURCE_TEXT);
        Files.asCharSink(rFile, Charsets.UTF_8).write(allResources);
        File publicTxtFile = new File(tempDir, FN_PUBLIC_TXT);
        if (publicResources != null) {
            Files.asCharSink(publicTxtFile, Charsets.UTF_8).write(publicResources);
        }
        GradleCoordinate c = GradleCoordinate.parseCoordinateString(name);
        assertNotNull(c);

        return new TestIdeAndroidLibrary(
                c.getGroupId() + ":" + c.getArtifactId() + ":" + c.getRevision() + "@aar",
                rFile,
                publicTxtFile
        );
    }

    @NonNull
    private static ResourceVisibilityLookup createFrom(
            @NonNull TestIdeAndroidLibrary library) {
        ResourceVisibilityLookup visibility =
                ResourceVisibilityLookup.create(
                        library.artifactAddress,
                        library.allResources,
                        library.publicResources);
        if (visibility.isEmpty()) {
            visibility = ResourceVisibilityLookup.NONE;
        }
        return visibility;
    }

    @NonNull
    private static ResourceVisibilityLookup createFrom(@NonNull List<TestIdeAndroidLibrary> libraries) {
        List<ResourceVisibilityLookup> list = new ArrayList<>();
        for (TestIdeAndroidLibrary d : libraries) {
            ResourceVisibilityLookup v = createFrom(d);
            if (!v.isEmpty()) {
                list.add(v);
            }
        }
        int size = list.size();
        return size == 0
               ? ResourceVisibilityLookup.NONE
               : size == 1
                 ? list.get(0)
                 : new ResourceVisibilityLookup.MultipleLibraryResourceVisibility(list);
    }
}
