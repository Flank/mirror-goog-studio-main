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
package com.android.ide.common.resources;

import static com.android.ide.common.rendering.api.ResourceNamespace.ANDROID;
import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.android.ide.common.rendering.api.ResourceNamespace.Resolver.EMPTY_RESOLVER;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ArrayResourceValue;
import com.android.ide.common.rendering.api.DensityBasedResourceValue;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import junit.framework.TestCase;

public class ResourceResolverNoNamespacesTest extends TestCase {
    private final ResourceRepositoryFixture resourceFixture = new ResourceRepositoryFixture();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        resourceFixture.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        try {
            resourceFixture.tearDown();
        } finally {
            super.tearDown();
        }
    }

    static ResourceResolver nonNamespacedResolver(
            @NonNull Map<ResourceType, ResourceValueMap> projectResources,
            @NonNull Map<ResourceType, ResourceValueMap> frameworkResources,
            @Nullable String themeName) {
        ResourceReference theme = null;
        if (themeName != null) {
            theme =
                    ResourceUrl.parseStyleParentReference(themeName)
                            .resolve(RES_AUTO, EMPTY_RESOLVER);
        }

        return ResourceResolver.create(
                ImmutableMap.of(
                        RES_AUTO, projectResources, ResourceNamespace.ANDROID, frameworkResources),
                theme);
    }

    /**
     * Returns true if the given {@code themeStyle} extends the theme given by {@code parentStyle}
     */
    public static boolean themeExtends(
            @NonNull ResourceResolver resolver,
            @NonNull String parentStyle,
            @NonNull String themeStyle) {
        return resolver.styleExtends(
                resolver.getStyle(ResourceUrl.parse(themeStyle).resolve(RES_AUTO, EMPTY_RESOLVER)),
                resolver.getStyle(
                        ResourceUrl.parse(parentStyle).resolve(RES_AUTO, EMPTY_RESOLVER)));
    }

    public void testBasicFunctionality() throws Exception {
        MergerResourceRepository frameworkRepository =
                resourceFixture.createTestResources(
                        ResourceNamespace.ANDROID,
                        new Object[] {
                            "values/strings.xml",
                                    ""
                                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                            + "<resources>\n"
                                            + "    <string name=\"ok\">Ok</string>\n"
                                            + "</resources>\n",
                            "values/themes.xml",
                                    ""
                                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                            + "<resources>\n"
                                            + "    <style name=\"Theme\">\n"
                                            + "        <item name=\"colorForeground\">@android:color/bright_foreground_dark</item>\n"
                                            + "        <item name=\"colorBackground\">@android:color/background_dark</item>\n"
                                            + "    </style>\n"
                                            + "    <style name=\"Theme.Light\">\n"
                                            + "        <item name=\"colorBackground\">@android:color/background_light</item>\n"
                                            + "        <item name=\"colorForeground\">@color/bright_foreground_light</item>\n"
                                            + "    </style>\n"
                                            + "</resources>\n",
                            "values/colors.xml",
                                    ""
                                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                            + "<resources>\n"
                                            + "    <color name=\"background_dark\">#ff000000</color>\n"
                                            + "    <color name=\"background_light\">#ffffffff</color>\n"
                                            + "    <color name=\"bright_foreground_dark\">@android:color/background_light</color>\n"
                                            + "    <color name=\"bright_foreground_light\">@android:color/background_dark</color>\n"
                                            + "</resources>\n",
                            "values/ids.xml",
                                    ""
                                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                            + "<resources>\n"
                                            + "    <item name=\"some_framework_id\" type=\"id\" />\n"
                                            + "</resources>\n",
                        });

        MergerResourceRepository projectRepository =
                resourceFixture.createTestResources(
                        ResourceNamespace.TODO,
                        new Object[] {
                            "layout/layout1.xml",
                            "<!--contents doesn't matter-->",
                            "layout/layout2.xml",
                            "<!--contents doesn't matter-->",
                            "layout-land/layout1.xml",
                            "<!--contents doesn't matter-->",
                            "layout-land/onlyland.xml",
                            "<!--contents doesn't matter-->",
                            "drawable/graphic.9.png",
                            new byte[0],
                            "mipmap-xhdpi/ic_launcher.png",
                            new byte[0],
                            "values/styles.xml",
                            ""
                                    + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                    + "<resources>\n"
                                    + "    <style name=\"MyTheme\" parent=\"android:Theme.Light\">\n"
                                    + "        <item name=\"android:textColor\">#999999</item>\n"
                                    + "        <item name=\"foo\">?android:colorForeground</item>\n"
                                    + "    </style>\n"
                                    + "    <style name=\"MyTheme.Dotted1\" parent=\"\">\n"
                                    + "    </style>"
                                    + "    <style name=\"MyTheme.Dotted2\">\n"
                                    + "    </style>"
                                    + "    <style name=\"RandomStyle\">\n"
                                    + "        <item name=\"android:text\">&#169; Copyright</item>\n"
                                    + "    </style>"
                                    + "    <style name=\"RandomStyle2\" parent=\"RandomStyle\">\n"
                                    + "    </style>"
                                    + "    <style name=\"Theme.FakeTheme\" parent=\"\">\n"
                                    + "    </style>"
                                    + "    <style name=\"Theme\" parent=\"RandomStyle\">\n"
                                    + "    </style>"
                                    + "</resources>\n",
                            "values/strings.xml",
                            ""
                                    + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                    + "<resources>\n"
                                    + "    <item type=\"id\" name=\"action_bar_refresh\" />\n"
                                    + "    <item type=\"dimen\" name=\"dialog_min_width_major\">45%</item>\n"
                                    + "    <string name=\"home_title\">Home Sample</string>\n"
                                    + "    <string name=\"show_all_apps\">All</string>\n"
                                    + "    <string name=\"menu_wallpaper\">Wallpaper</string>\n"
                                    + "    <string name=\"menu_search\">Search</string>\n"
                                    + "    <string name=\"menu_settings\">Settings</string>\n"
                                    + "    <string name=\"dummy\" translatable=\"false\">Ignore Me</string>\n"
                                    + "    <string name=\"wallpaper_instructions\">Tap picture to set portrait wallpaper</string>\n"
                                    + "</resources>\n",
                            "values-es/strings.xml",
                            ""
                                    + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                    + "<resources>\n"
                                    + "    <string name=\"show_all_apps\">Todo</string>\n"
                                    + "</resources>\n",
                            "values/arrays.xml",
                            ""
                                    + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                    + "<resources>\n"
                                    + "    <string name=\"first\">Item1</string>\n"
                                    + "    <string-array name=\"my_array\">\n"
                                    + "        <item>@string/first</item>\n"
                                    + "        <item>Item2</item>\n"
                                    + "        <item>Item3</item>\n"
                                    + "    </string-array>\n"
                                    + "</resources>\n",
                        });

        assertEquals(
                Collections.singleton(ResourceNamespace.TODO), projectRepository.getNamespaces());
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-es-land");
        assertNotNull(config);
        Map<ResourceType, ResourceValueMap> projectResources =
                projectRepository.getConfiguredResources(config).row(ResourceNamespace.TODO);
        Map<ResourceType, ResourceValueMap> frameworkResources =
                frameworkRepository.getConfiguredResources(config).row(ResourceNamespace.ANDROID);
        assertNotNull(projectResources);
        ResourceResolver resolver =
                nonNamespacedResolver(projectResources, frameworkResources, "MyTheme");
        assertNotNull(resolver);

        LayoutLog logger =
                new LayoutLog() {
                    @Override
                    public void warning(
                            String tag, String message, Object viewCookie, Object data) {
                        fail(message);
                    }

                    @Override
                    public void fidelityWarning(
                            String tag,
                            String message,
                            Throwable throwable,
                            Object viewCookie,
                            Object data) {
                        fail(message);
                    }

                    @Override
                    public void error(String tag, String message, Object viewCookie, Object data) {
                        fail(message);
                    }

                    @Override
                    public void error(
                            String tag,
                            String message,
                            Throwable throwable,
                            Object viewCookie,
                            Object data) {
                        fail(message);
                    }
                };
        resolver.setLogger(logger);

        assertEquals("MyTheme", resolver.getTheme().getName());
        assertEquals(RES_AUTO, resolver.getTheme().getNamespace());

        // findResValue
        assertNotNull(resolver.findResValue("@string/show_all_apps", false));
        assertNotNull(resolver.findResValue("@android:string/ok", false));
        assertNotNull(resolver.findResValue("@android:string/ok", true));
        assertEquals("Todo", resolver.findResValue("@string/show_all_apps", false).getValue());
        assertEquals("Home Sample", resolver.findResValue("@string/home_title", false).getValue());
        assertEquals("45%", resolver.findResValue("@dimen/dialog_min_width_major",
                false).getValue());
        assertNotNull(resolver.findResValue("@android:color/bright_foreground_dark", true));
        assertEquals("@android:color/background_light",
                resolver.findResValue("@android:color/bright_foreground_dark", true).getValue());
        assertEquals("#ffffffff",
                resolver.findResValue("@android:color/background_light", true).getValue());
        assertNull(resolver.findResValue("?attr/non_existent_style", false)); // shouldn't log an error.
        assertEquals(Density.XHIGH,
                ((DensityBasedResourceValue) resolver.findResValue("@mipmap/ic_launcher", false))
                        .getResourceDensity());  // also ensures that returned value is instance of DensityBasedResourceValue

        // getTheme
        StyleResourceValue myTheme = resolver.getTheme("MyTheme", false);
        assertNotNull(myTheme);
        assertSame(resolver.findResValue("@style/MyTheme", false), myTheme);
        assertNull(resolver.getTheme("MyTheme", true));
        assertNull(resolver.getTheme("MyNonexistentTheme", true));
        StyleResourceValue themeLight = resolver.getTheme("Theme.Light", true);
        assertNotNull(themeLight);
        StyleResourceValue theme = resolver.getTheme("Theme", true);
        assertNotNull(theme);

        // getParent
        StyleResourceValue parent = resolver.getParent(myTheme);
        assertNotNull(parent);
        assertEquals("Theme.Light", parent.getName());

        // getChildren
        StyleResourceValue randomStyle =
                (StyleResourceValue) resolver.findResValue("@style/RandomStyle", false);
        assertNotNull(randomStyle);
        Collection<StyleResourceValue> children = resolver.getChildren(randomStyle);
        List<String> childNames =
                children.stream().map(StyleResourceValue::getName).collect(Collectors.toList());
        assertThat(childNames).containsExactly("Theme", "RandomStyle2");

        StyleResourceValue randomStyle2 =
                (StyleResourceValue) resolver.findResValue("@style/RandomStyle2", false);
        assertNotNull(randomStyle2);
        assertThat(resolver.getChildren(randomStyle2)).isEmpty();
        // themeIsParentOf
        assertTrue(resolver.themeIsParentOf(themeLight, myTheme));
        assertFalse(resolver.themeIsParentOf(myTheme, themeLight));
        assertTrue(resolver.themeIsParentOf(theme, themeLight));
        assertFalse(resolver.themeIsParentOf(themeLight, theme));
        assertTrue(resolver.themeIsParentOf(theme, myTheme));
        assertFalse(resolver.themeIsParentOf(myTheme, theme));
        StyleResourceValue dotted1 = resolver.getTheme("MyTheme.Dotted1", false);
        assertNotNull(dotted1);
        StyleResourceValue dotted2 = resolver.getTheme("MyTheme.Dotted2", false);
        assertNotNull(dotted2);
        assertTrue(resolver.themeIsParentOf(myTheme, dotted2));
        assertFalse(resolver.themeIsParentOf(myTheme, dotted1)); // because parent=""

        // isTheme
        assertFalse(resolver.isTheme(resolver.findResValue("@style/RandomStyle", false), null));
        assertFalse(resolver.isTheme(resolver.findResValue("@style/RandomStyle2", false), null));
        assertFalse(resolver.isTheme(resolver.findResValue("@style/Theme.FakeTheme", false), null));
        assertFalse(resolver.isTheme(resolver.findResValue("@style/Theme", false), null));
        //    check XML escaping in value resources
        assertEquals("\u00a9 Copyright", randomStyle.getItem(ANDROID, "text").getValue());
        assertTrue(resolver.isTheme(resolver.findResValue("@style/MyTheme.Dotted2", false), null));
        assertFalse(resolver.isTheme(resolver.findResValue("@style/MyTheme.Dotted1", false),
                null));
        assertTrue(resolver.isTheme(resolver.findResValue("@style/MyTheme", false), null));
        assertTrue(resolver.isTheme(resolver.findResValue("@android:style/Theme.Light", false),
                null));
        assertTrue(resolver.isTheme(resolver.findResValue("@android:style/Theme", false), null));

        // findItemInStyle
        assertNotNull(resolver.findItemInStyle(myTheme, "colorForeground", true));
        assertEquals("@color/bright_foreground_light",
                resolver.findItemInStyle(myTheme, "colorForeground", true).getValue());
        assertNotNull(resolver.findItemInStyle(dotted2, "colorForeground", true));
        assertNull(resolver.findItemInStyle(dotted1, "colorForeground", true));

        // findItemInTheme
        assertNotNull(resolver.findItemInTheme("colorForeground", true));
        assertEquals("@color/bright_foreground_light",
                resolver.findItemInTheme("colorForeground", true).getValue());
        assertEquals("@color/bright_foreground_light",
                resolver.findResValue("?colorForeground", true).getValue());
        ResourceValue target = new ResourceValue(RES_AUTO, ResourceType.STRING, "dummy", "?foo");
        assertEquals("#ff000000", resolver.resolveResValue(target).getValue());

        // getFrameworkResource
        assertNull(resolver.getFrameworkResource(ResourceType.STRING, "show_all_apps"));
        assertNotNull(resolver.getFrameworkResource(ResourceType.STRING, "ok"));
        assertEquals("Ok", resolver.getFrameworkResource(ResourceType.STRING, "ok").getValue());

        // getProjectResource
        assertNull(resolver.getProjectResource(ResourceType.STRING, "ok"));
        assertNotNull(resolver.getProjectResource(ResourceType.STRING, "show_all_apps"));
        assertEquals("Todo", resolver.getProjectResource(ResourceType.STRING,
                "show_all_apps").getValue());


        // resolveResValue
        //    android:color/bright_foreground_dark ⇒ @android:color/background_light ⇒ white
        assertEquals("Todo", resolver.resolveResValue(
                resolver.findResValue("@string/show_all_apps", false)).getValue());
        assertEquals("#ffffffff", resolver.resolveResValue(
                resolver.findResValue("@android:color/bright_foreground_dark", false)).getValue());

        assertEquals(
                "#ffffffff",
                resolver.resolveResValue(
                                new ResourceValue(
                                        ResourceNamespace.ANDROID,
                                        ResourceType.STRING,
                                        "bright_foreground_dark",
                                        "@android:color/background_light"))
                        .getValue());

        assertFalse(
                resolver.resolveResValue(
                                new ResourceValue(
                                        RES_AUTO, ResourceType.ID, "my_id", "@+id/some_new_id"))
                        .isFramework());
        // error expected.
        boolean failed = false;
        ResourceValue val = null;
        try {
            val =
                    resolver.resolveResValue(
                            new ResourceValue(
                                    RES_AUTO,
                                    ResourceType.STRING,
                                    "bright_foreground_dark",
                                    "@color/background_light"));
        } catch (AssertionError expected) {
            failed = true;
        }
        assertTrue("incorrect resource returned: " + val, failed);
        ResourceValue array = resolver
                .resolveResValue(resolver.getProjectResource(ResourceType.ARRAY, "my_array"));
        assertTrue("array" + "my_array" + "resolved incorrectly as " + array.getResourceType()
                .getName(), array instanceof ArrayResourceValue);

        // themeExtends
        assertTrue(themeExtends(resolver, "@android:style/Theme", "@android:style/Theme"));
        assertTrue(themeExtends(resolver, "@android:style/Theme", "@android:style/Theme.Light"));
        assertFalse(themeExtends(resolver, "@android:style/Theme.Light", "@android:style/Theme"));
        assertTrue(themeExtends(resolver, "@style/MyTheme.Dotted2", "@style/MyTheme.Dotted2"));
        assertTrue(themeExtends(resolver, "@style/MyTheme", "@style/MyTheme.Dotted2"));
        assertTrue(themeExtends(resolver, "@android:style/Theme.Light", "@style/MyTheme.Dotted2"));
        assertTrue(themeExtends(resolver, "@android:style/Theme", "@style/MyTheme.Dotted2"));
        assertFalse(themeExtends(resolver, "@style/MyTheme.Dotted1", "@style/MyTheme.Dotted2"));

        // Switch to MyTheme.Dotted1 (to make sure the parent="" inheritance works properly.)
        // To do that we need to create a new resource resolver.
        resolver = nonNamespacedResolver(projectResources, frameworkResources, "MyTheme.Dotted1");
        resolver.setLogger(logger);
        assertNotNull(resolver);
        assertEquals("MyTheme.Dotted1", resolver.getTheme().getName());
        assertEquals(RES_AUTO, resolver.getTheme().getNamespace());
        assertNull(resolver.findItemInTheme("colorForeground", true));

        resolver = nonNamespacedResolver(projectResources, frameworkResources, "MyTheme.Dotted2");
        resolver.setLogger(logger);
        assertNotNull(resolver);
        assertEquals("MyTheme.Dotted2", resolver.getTheme().getName());
        assertEquals(RES_AUTO, resolver.getTheme().getNamespace());
        assertNotNull(resolver.findItemInTheme("colorForeground", true));

        // Test recording resolver
        List<ResourceValue> chain = Lists.newArrayList();
        resolver = nonNamespacedResolver(projectResources, frameworkResources, "MyTheme");
        resolver = resolver.createRecorder(chain);
        assertNotNull(resolver.findResValue("@android:color/bright_foreground_dark", true));
        ResourceValue v = resolver.findResValue("@android:color/bright_foreground_dark", false);
        chain.clear();
        assertEquals("#ffffffff", resolver.resolveResValue(v).getValue());
        assertEquals("@android:color/bright_foreground_dark => "
                + "@android:color/background_light => #ffffffff",
                ResourceItemResolver.getDisplayString("@android:color/bright_foreground_dark",
                        chain));
    }

    public void testMissingMessage() throws Exception {
        MergerResourceRepository projectRepository =
                resourceFixture.createTestResources(
                        ResourceNamespace.TODO,
                        new Object[] {
                            "values/colors.xml",
                            ""
                                    + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                    + "<resources>\n"
                                    + "    <color name=\"loop1\">@color/loop1</color>\n"
                                    + "    <color name=\"loop2a\">@color/loop2b</color>\n"
                                    + "    <color name=\"loop2b\">@color/loop2a</color>\n"
                                    + "</resources>\n",
                        });

        assertEquals(
                Collections.singleton(ResourceNamespace.TODO), projectRepository.getNamespaces());
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-es-land");
        assertNotNull(config);
        Map<ResourceType, ResourceValueMap> projectResources =
                projectRepository.getConfiguredResources(config).row(ResourceNamespace.TODO);
        assertNotNull(projectResources);
        ResourceResolver resolver =
                nonNamespacedResolver(projectResources, projectResources, "MyTheme");
        final AtomicBoolean wasWarned = new AtomicBoolean(false);
        LayoutLog logger =
                new LayoutLog() {
                    @Override
                    public void warning(
                            String tag, String message, Object viewCookie, Object data) {
                        if ("Couldn't resolve resource @android:string/show_all_apps"
                                .equals(message)) {
                            wasWarned.set(true);
                        } else {
                            fail(message);
                        }
                    }
                };
        resolver.setLogger(logger);
        assertNull(resolver.findResValue("@string/show_all_apps", true));
        assertTrue(wasWarned.get());
    }

    public void testLoop() throws Exception {
        MergerResourceRepository projectRepository =
                resourceFixture.createTestResources(
                        ResourceNamespace.TODO,
                        new Object[] {
                            "values/colors.xml",
                            ""
                                    + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                    + "<resources>\n"
                                    + "    <color name=\"loop1\">@color/loop1</color>\n"
                                    + "    <color name=\"loop2a\">@color/loop2b</color>\n"
                                    + "    <color name=\"loop2b\">@color/loop2a</color>\n"
                                    + "</resources>\n",
                        });

        assertEquals(
                Collections.singleton(ResourceNamespace.TODO), projectRepository.getNamespaces());
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-es-land");
        assertNotNull(config);
        Map<ResourceType, ResourceValueMap> projectResources =
                projectRepository.getConfiguredResources(config).row(ResourceNamespace.TODO);
        assertNotNull(projectResources);
        ResourceResolver resolver =
                nonNamespacedResolver(projectResources, projectResources, "MyTheme");
        assertNotNull(resolver);

        final AtomicBoolean wasWarned = new AtomicBoolean(false);
        LayoutLog logger =
                new LayoutLog() {
                    @Override
                    public void error(
                            @Nullable String tag,
                            @NonNull String message,
                            @Nullable Throwable throwable,
                            @Nullable Object viewCookie,
                            @Nullable Object data) {
                        if (("Potential stack overflow trying to resolve "
                                        + "'@color/loop1': cyclic resource definitions?"
                                        + " Render may not be accurate.")
                                .equals(message)) {
                            wasWarned.set(true);
                        } else if (("Potential stack overflow trying to resolve "
                                        + "'@color/loop2b': cyclic resource definitions? "
                                        + "Render may not be accurate.")
                                .equals(message)) {
                            wasWarned.set(true);
                        } else {
                            fail(message);
                        }
                    }
                };
        resolver.setLogger(logger);

        assertNotNull(resolver.findResValue("@color/loop1", false));
        resolver.resolveResValue(resolver.findResValue("@color/loop1", false));
        assertTrue(wasWarned.get());

        wasWarned.set(false);
        assertNotNull(resolver.findResValue("@color/loop2a", false));
        resolver.resolveResValue(resolver.findResValue("@color/loop2a", false));
        assertTrue(wasWarned.get());
    }

    public void testParentCycle() throws Exception {
        MergerResourceRepository projectRepository =
                resourceFixture.createTestResources(
                        ResourceNamespace.TODO,
                        new Object[] {
                            "values/styles.xml",
                                    ""
                                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                            + "<resources>\n"
                                            + "    <style name=\"ButtonStyle.Base\">\n"
                                            + "        <item name=\"android:textColor\">#ff0000</item>\n"
                                            + "    </style>\n"
                                            + "    <style name=\"ButtonStyle\" parent=\"ButtonStyle.Base\">\n"
                                            + "        <item name=\"android:layout_height\">40dp</item>\n"
                                            + "    </style>\n"
                                            + "</resources>\n",
                            "layouts/layout.xml",
                                    ""
                                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                            + "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                            + "    android:layout_width=\"match_parent\"\n"
                                            + "    android:layout_height=\"match_parent\">\n"
                                            + "\n"
                                            + "    <TextView\n"
                                            + "        style=\"@style/ButtonStyle\"\n"
                                            + "        android:layout_width=\"wrap_content\"\n"
                                            + "        android:layout_height=\"wrap_content\" />\n"
                                            + "\n"
                                            + "</RelativeLayout>\n",
                        });
        assertEquals(
                Collections.singleton(ResourceNamespace.TODO), projectRepository.getNamespaces());
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-es-land");
        assertNotNull(config);
        Map<ResourceType, ResourceValueMap> projectResources =
                projectRepository.getConfiguredResources(config).row(ResourceNamespace.TODO);
        assertNotNull(projectResources);
        ResourceResolver resolver =
                nonNamespacedResolver(projectResources, projectResources, "ButtonStyle");
        assertNotNull(resolver);

        final AtomicBoolean wasWarned = new AtomicBoolean(false);
        LayoutLog logger =
                new LayoutLog() {
                    @Override
                    public void error(
                            @Nullable String tag,
                            @NonNull String message,
                            @Nullable Throwable throwable,
                            @Nullable Object viewCookie,
                            @Nullable Object data) {
                        assertEquals(
                                "Cyclic style parent definitions: \"ButtonStyle\" specifies "
                                        + "parent \"ButtonStyle.Base\" implies parent \"ButtonStyle\"",
                                message);
                        assertEquals(LayoutLog.TAG_BROKEN, tag);
                        wasWarned.set(true);
                    }
                };
        resolver.setLogger(logger);

        StyleResourceValue buttonStyle = (StyleResourceValue) resolver.findResValue(
                "@style/ButtonStyle", false);
        ResourceValue textColor = resolver.findItemInStyle(buttonStyle, "textColor", true);
        assertNotNull(textColor);
        assertEquals("#ff0000", textColor.getValue());
        assertFalse(wasWarned.get());
        ResourceValue missing = resolver.findItemInStyle(buttonStyle, "missing", true);
        assertNull(missing);
        assertTrue(wasWarned.get());
    }

    public void testSetDeviceDefaults() throws Exception {
        MergerResourceRepository frameworkRepository =
                resourceFixture.createTestResources(
                        ResourceNamespace.ANDROID,
                        new Object[] {
                            "values/themes.xml",
                                    ""
                                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                            + "<resources>\n"
                                            + "    <style name=\"Theme.Light\" parent=\"\">\n"
                                            + "         <item name=\"android:textColor\">#ff0000</item>\n"
                                            + "    </style>\n"
                                            + "    <style name=\"Theme.Holo.Light\" parent=\"Theme.Light\">\n"
                                            + "         <item name=\"android:textColor\">#00ff00</item>\n"
                                            + "    </style>\n"
                                            + "    <style name=\"Theme.DeviceDefault.Light\" parent=\"Theme.Holo.Light\"/>\n"
                                            + "    <style name=\"Theme\" parent=\"\">\n"
                                            + "         <item name=\"android:textColor\">#000000</item>\n"
                                            + "    </style>\n"
                                            + "    <style name=\"Theme.Holo\" parent=\"Theme\">\n"
                                            + "         <item name=\"android:textColor\">#0000ff</item>\n"
                                            + "    </style>\n"
                                            + "    <style name=\"Theme.DeviceDefault\" parent=\"Theme.Holo\"/>\n"
                                            + "</resources>\n",
                            "values/styles.xml",
                                    ""
                                            + "<resources>\n"
                                            + "    <style name=\"Widget.Button.Small\">\n"
                                            + "         <item name=\"android:textColor\">#000000</item>\n"
                                            + "    </style>\n"
                                            + "    <style name=\"Widget.Holo.Button.Small\">\n"
                                            + "         <item name=\"android:textColor\">#ffffff</item>\n"
                                            + "    </style>\n"
                                            + "    <style name=\"Widget.DeviceDefault.Button.Small\" parent=\"Widget.Holo.Button.Small\" />\n"
                                            + "    <style name=\"ButtonBar\">\n"
                                            + "         <item name=\"android:textColor\">#000000</item>\n"
                                            + "    </style>\n"
                                            + "    <style name=\"Holo.ButtonBar\">\n"
                                            + "         <item name=\"android:textColor\">#ffffff</item>\n"
                                            + "    </style>\n"
                                            + "    <style name=\"DeviceDefault.ButtonBar\" parent=\"Holo.ButtonBar\" />\n"
                                            + "</resources>\n",
                        });

        MergerResourceRepository projectRepository =
                resourceFixture.createTestResources(
                        ResourceNamespace.TODO,
                        new Object[] {
                            "values/themes.xml",
                            ""
                                    + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                    + "<resources>\n"
                                    + "    <style name=\"AppTheme\" parent=\"android:Theme.DeviceDefault.Light\"/>\n"
                                    + "    <style name=\"AppTheme.Dark\" parent=\"android:Theme.DeviceDefault\"/>\n"
                                    + "</resources>\n"
                        });

        assertEquals(
                Collections.singleton(ResourceNamespace.TODO), projectRepository.getNamespaces());
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-es-land");
        assertNotNull(config);
        Map<ResourceType, ResourceValueMap> projectResources =
                projectRepository.getConfiguredResources(config).row(ResourceNamespace.TODO);
        Map<ResourceType, ResourceValueMap> frameworkResources =
                frameworkRepository.getConfiguredResources(config).row(ResourceNamespace.ANDROID);
        assertNotNull(projectResources);
        ResourceResolver lightResolver =
                nonNamespacedResolver(projectResources, frameworkResources, "AppTheme");
        assertNotNull(lightResolver);
        ResourceValue textColor = lightResolver.findItemInTheme("textColor", true);
        assertNotNull(textColor);
        assertEquals("#00ff00", textColor.getValue());

        lightResolver.setDeviceDefaults(ResourceResolver.LEGACY_THEME);
        textColor = lightResolver.findItemInTheme("textColor", true);
        assertNotNull(textColor);
        assertEquals("#ff0000", textColor.getValue());

        ResourceResolver darkResolver =
                nonNamespacedResolver(projectResources, frameworkResources, "AppTheme.Dark");
        assertNotNull(darkResolver);
        textColor = darkResolver.findItemInTheme("textColor", true);
        assertNotNull(textColor);
        assertEquals("#0000ff", textColor.getValue());

        darkResolver.setDeviceDefaults(ResourceResolver.LEGACY_THEME);
        textColor = darkResolver.findItemInTheme("textColor", true);
        assertNotNull(textColor);
        assertEquals("#000000", textColor.getValue());

        // Check styles are correctly patched. We could use either resolver for that
        textColor = darkResolver
                .findItemInStyle(lightResolver.getStyle("Widget.DeviceDefault.Button.Small", true),
                        "textColor", true);
        assertEquals("#000000", textColor.getValue());
        textColor = darkResolver
                .findItemInStyle(lightResolver.getStyle("DeviceDefault.ButtonBar", true),
                        "textColor", true);
        assertEquals("#000000", textColor.getValue());

        darkResolver.setDeviceDefaults("Holo");
        textColor = darkResolver
                .findItemInStyle(lightResolver.getStyle("Widget.DeviceDefault.Button.Small", true),
                        "textColor", true);
        assertEquals("#ffffff", textColor.getValue());
        textColor = darkResolver
                .findItemInStyle(lightResolver.getStyle("DeviceDefault.ButtonBar", true),
                        "textColor", true);
        assertEquals("#ffffff", textColor.getValue());
    }

    public void testCycle() throws Exception {
        MergerResourceRepository frameworkRepository =
                resourceFixture.createTestResources(
                        ResourceNamespace.ANDROID,
                        new Object[] {
                            "values/themes.xml",
                            ""
                                    + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                    + "<resources>\n"
                                    + "    <style name=\"Theme.DeviceDefault.Light\"/>\n"
                                    + "</resources>\n",
                        });

        MergerResourceRepository projectRepository =
                resourceFixture.createTestResources(
                        ResourceNamespace.TODO,
                        new Object[] {
                            "values/themes.xml",
                            ""
                                    + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                    + "<resources>\n"
                                    + "    <style name=\"AppTheme\" parent=\"android:Theme.DeviceDefault.Light\"/>\n"
                                    + "    <style name=\"AppTheme.Dark\" parent=\"android:Theme.DeviceDefault\"/>\n"
                                    + "    <style name=\"foo\" parent=\"bar\"/>\n"
                                    + "    <style name=\"bar\" parent=\"foo\"/>\n"
                                    + "</resources>\n"
                        });

        assertEquals(
                Collections.singleton(ResourceNamespace.TODO), projectRepository.getNamespaces());
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values");
        assertNotNull(config);
        Map<ResourceType, ResourceValueMap> projectResources =
                projectRepository.getConfiguredResources(config).row(ResourceNamespace.TODO);
        Map<ResourceType, ResourceValueMap> frameworkResources =
                frameworkRepository.getConfiguredResources(config).row(ResourceNamespace.ANDROID);
        assertNotNull(projectResources);
        ResourceResolver resolver =
                nonNamespacedResolver(projectResources, frameworkResources, "AppTheme");

        final AtomicBoolean wasWarned = new AtomicBoolean(false);
        LayoutLog logger =
                new LayoutLog() {
                    @Override
                    public void error(
                            @Nullable String tag,
                            @NonNull String message,
                            @Nullable Throwable throwable,
                            @Nullable Object viewCookie,
                            @Nullable Object data) {
                        if ("Cyclic style parent definitions: \"foo\" specifies parent \"bar\" specifies parent \"foo\""
                                .equals(message)) {
                            wasWarned.set(true);
                        } else {
                            fail(message);
                        }
                    }
                };
        resolver.setLogger(logger);
        assertFalse(resolver.isTheme(resolver.findResValue("@style/foo", false), null));
        assertTrue(wasWarned.get());
    }

    public void testCopy() throws Exception {
        MergerResourceRepository frameworkRepository =
                resourceFixture.createTestResources(
                        ResourceNamespace.ANDROID,
                        new Object[] {
                            "values/themes.xml",
                            ""
                                    + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                    + "<resources>\n"
                                    + "    <style name=\"Theme.Material\"/>\n"
                                    + "</resources>\n",
                        });
        MergerResourceRepository projectRepository =
                resourceFixture.createTestResources(
                        ResourceNamespace.TODO,
                        new Object[] {
                            "values/colors.xml",
                                    ""
                                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                            + "<resources>\n"
                                            + "    <color name=\"loop1\">@color/loop1</color>\n"
                                            + "    <color name=\"loop2a\">@color/loop2b</color>\n"
                                            + "    <color name=\"loop2b\">@color/loop2a</color>\n"
                                            + "    <style name=\"MyStyle\"/>\n"
                                            + "</resources>\n",
                            "values/styles.xml",
                                    ""
                                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                            + "<resources>\n"
                                            + "    <style name=\"MyStyle\"/>\n"
                                            + "</resources>\n",
                        });

        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values");
        assertNotNull(config);
        Map<ResourceType, ResourceValueMap> projectResources =
                projectRepository.getConfiguredResources(config).row(ResourceNamespace.TODO);
        assertNotNull(projectResources);

        Map<ResourceType, ResourceValueMap> frameworkResources =
                frameworkRepository.getConfiguredResources(config).row(ResourceNamespace.ANDROID);
        assertNotNull(frameworkResources);

        ResourceResolver resolver =
                nonNamespacedResolver(
                        projectResources, frameworkResources, "android:Theme.Material");
        assertEquals(1, resolver.getAllThemes().size());
        assertNotNull(resolver.getTheme());

        assertNull(ResourceResolver.copy(null));
        ResourceResolver copyResolver = ResourceResolver.copy(resolver);
        assertNotNull(copyResolver);

        StyleResourceValue myTheme = resolver.getStyle("MyStyle", false);
        resolver.applyStyle(myTheme, true);
        assertEquals(2, resolver.getAllThemes().size());
        assertEquals(1, copyResolver.getAllThemes().size());
        resolver.clearStyles();
        assertEquals(1, resolver.getAllThemes().size());
        assertEquals(1, copyResolver.getAllThemes().size());
    }

    public void testEmptyRepository() throws Exception {
        // If the LocalResourceRespository fails to be loaded, the resolver will be created with empty maps. Make sure
        // empty maps are valid inputs
        ResourceResolver resolver =
                nonNamespacedResolver(Collections.emptyMap(), Collections.emptyMap(), null);
        assertNotNull(ResourceResolver.copy(resolver));

        assertNull(resolver.findResValue("@color/doesnt_exist", false));
        assertNull(resolver.findResValue("@android:color/doesnt_exist", false));
        assertNull(resolver.getTheme("NoTheme", false));
        assertNull(resolver.getTheme("NoTheme", true));
    }

    public void testResolverIds() throws Exception {
        MergerResourceRepository projectRepository =
                resourceFixture.createTestResources(
                        ResourceNamespace.TODO,
                        new Object[] {
                            "layout/layout1.xml",
                            "<!--contents doesn't matter-->",
                            "layout/layout2.xml",
                            "<!--contents doesn't matter-->",
                            "layout-land/layout1.xml",
                            "<!--contents doesn't matter-->",
                            "layout-land/onlyLand.xml",
                            "<!--contents doesn't matter-->",
                            "layouts/layout.xml",
                            ""
                                    + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                    + "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                    + "    android:layout_width=\"match_parent\"\n"
                                    + "    android:layout_height=\"match_parent\">\n"
                                    + "\n"
                                    + "    <TextView\n"
                                    + "        android:id=\"@+id/new_id\"\n"
                                    + "        style=\"@style/ButtonStyle\"\n"
                                    + "        android:layout_width=\"wrap_content\"\n"
                                    + "        android:layout_height=\"wrap_content\" />\n"
                                    + "\n"
                                    + "</RelativeLayout>\n",
                        });
        assertEquals(
                Collections.singleton(ResourceNamespace.TODO), projectRepository.getNamespaces());
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-es-land");
        assertNotNull(config);
        Map<ResourceType, ResourceValueMap> projectResources =
                projectRepository.getConfiguredResources(config).row(ResourceNamespace.TODO);
        assertNotNull(projectResources);

        ResourceResolver resolver =
                nonNamespacedResolver(projectResources, projectResources, "ButtonStyle");
        assertNotNull(resolver);

        ImmutableSet<String> libraryIds = ImmutableSet.of("lib_id1", "lib_id2");
        resolver.setProjectIdChecker(ref -> libraryIds.contains(ref.getName()));

        assertNull(resolver.findResValue("@id/not_found", false));
        assertNull(resolver.findResValue("@id/new_id", false));
        assertNull(resolver.findResValue("@id/framework_id1", false));
        assertNotNull(resolver.findResValue("@android:id/framework_id1", false));
        assertNotNull(resolver.findResValue("@id/lib_id1", false));

        // See the comment in ResourceResolver#dereference. We need to fix this.
        assertNotNull(resolver.findResValue("@id/lib_id1", true));
        assertNotNull(resolver.findResValue("@android:id/lib_id1", false));
    }
}
