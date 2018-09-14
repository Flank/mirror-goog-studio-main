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

import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FD_RES_DRAWABLE;
import static com.android.SdkConstants.FD_RES_LAYOUT;
import static com.android.SdkConstants.FD_RES_VALUES;
import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier;
import com.android.resources.ResourceType;
import com.android.resources.ScreenOrientation;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class TestResourceRepositoryTest2 extends TestCase {
    private final ResourceRepositoryFixture resourceFixture = new ResourceRepositoryFixture();
    private File mTempDir;
    private File mRes;
    private ResourceMerger mResourceMerger;
    private TestResourceRepository mRepository;
    private ILogger mLogger;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        resourceFixture.setUp();
        mTempDir = TestUtils.createTempDirDeletedOnExit();
        mRes = new File(mTempDir, FD_RES);
        mRes.mkdirs();
        File layout = new File(mRes, FD_RES_LAYOUT);
        File layoutLand = new File(mRes, FD_RES_LAYOUT + "-land");
        File values = new File(mRes, FD_RES_VALUES);
        File valuesEs = new File(mRes, FD_RES_VALUES + "-es");
        File valuesEsUs = new File(mRes, FD_RES_VALUES + "-es-rUS");
        File valuesKok = new File(mRes, FD_RES_VALUES + "-b+kok");
        File valuesKokIn = new File(mRes, FD_RES_VALUES + "-b+kok+IN");
        File drawable = new File(mRes, FD_RES_DRAWABLE);
        layout.mkdirs();
        layoutLand.mkdirs();
        values.mkdirs();
        valuesEs.mkdirs();
        valuesEsUs.mkdirs();
        valuesKok.mkdirs();
        valuesKokIn.mkdirs();
        drawable.mkdirs();
        new File(layout, "layout1.xml").createNewFile();
        new File(layoutLand, "layout1.xml").createNewFile();
        new File(layoutLand, "only_land.xml").createNewFile();
        new File(layout, "layout2.xml").createNewFile();
        new File(drawable, "graphic.9.png").createNewFile();
        File strings = new File(values, "strings.xml");
        Files.write(""
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
                + "</resources>\n", strings, Charsets.UTF_8);

        Files.write(""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<resources>\n"
                + "    <string name=\"show_all_apps\">Todo</string>\n"
                + "</resources>\n", new File(valuesEs, "strings.xml"), Charsets.UTF_8);
        Files.write(""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<resources>\n"
                + "    <string name=\"show_all_apps\">Todo</string>\n"
                + "</resources>\n", new File(valuesEsUs, "strings.xml"), Charsets.UTF_8);
        Files.write(""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<resources>\n"
                + "    <string name=\"show_all_apps\">Todo</string>\n"
                + "</resources>\n", new File(valuesKok, "strings.xml"), Charsets.UTF_8);
        Files.write(""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<resources>\n"
                + "    <string name=\"show_all_apps\">Todo</string>\n"
                + "</resources>\n", new File(valuesKokIn, "strings.xml"), Charsets.UTF_8);

        if ("testGetMatchingFileAliases".equals(getName())) {
            Files.write(""
                    + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<resources>\n"
                    + "    <item name=\"layout2\" type=\"layout\">@layout/indirect3</item>\n"
                    + "    <item name=\"indirect3\" type=\"layout\">@layout/indirect2</item>\n"
                    + "    <item name=\"indirect2\" type=\"layout\">@layout/indirect1</item>\n"
                    + "    <item name=\"indirect1\" type=\"layout\">@layout/layout1</item>\n"
                    + "</resources>", new File(valuesEsUs, "refs.xml"), Charsets.UTF_8);
        }

        mResourceMerger = new ResourceMerger(0);
        ResourceSet resourceSet = new ResourceSet("main", RES_AUTO, null, true);
        resourceSet.addSource(mRes);
        resourceSet.loadFromFiles(mLogger = new RecordingLogger());
        mResourceMerger.addDataSet(resourceSet);

        mRepository = new TestResourceRepository(RES_AUTO);
        mRepository.update(mResourceMerger);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            FileUtils.deletePath(mTempDir);
            resourceFixture.tearDown();
        } finally {
            super.tearDown();
        }
    }

    public void testBasic() throws Exception {
        assertFalse(mRepository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout0"));
        assertTrue(mRepository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));
        assertFalse(mRepository.hasResources(RES_AUTO, ResourceType.STRING, "layout1"));
        assertTrue(mRepository.hasResources(RES_AUTO, ResourceType.STRING, "home_title"));
        assertFalse(mRepository.hasResources(RES_AUTO, ResourceType.STRING, "home_title2"));
        assertFalse(mRepository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "graph"));
        assertTrue(mRepository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "graphic"));
        assertTrue(mRepository.hasResources(RES_AUTO, ResourceType.ID, "action_bar_refresh"));
        assertTrue(mRepository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "graphic"));
        assertTrue(mRepository.hasResources(RES_AUTO, ResourceType.DRAWABLE));
        assertFalse(mRepository.hasResources(RES_AUTO, ResourceType.ANIM));

        Collection<ResourceType> availableResourceTypes = mRepository.getResourceTypes(RES_AUTO);
        assertEquals(5, availableResourceTypes.size()); // layout, string, drawable, id, dimen

        Collection<String> allStrings =
                mRepository.getResources(RES_AUTO, ResourceType.STRING).keySet();
        assertEquals(7, allStrings.size());

        List<ResourceItem> itemList =
                mRepository.getResources(RES_AUTO, ResourceType.STRING, "menu_settings");
        assertNotNull(itemList);
        assertEquals(1, itemList.size());
        for (ResourceItem item : itemList) {
            assertEquals("menu_settings", item.getName());
            assertEquals(
                    "@string/menu_settings",
                    ((ResourceMergerItem) item).getXmlString(ResourceType.STRING, false));
        }
        //assertTrue(item.hasDefault());

        itemList = mRepository.getResources(RES_AUTO, ResourceType.STRING, "show_all_apps");
        assertNotNull(itemList);
        assertTrue(itemList.size() > 1);
        for (ResourceItem item : itemList) {
            assertEquals("show_all_apps", item.getName());
            assertEquals(
                    "@string/show_all_apps",
                    ((ResourceMergerItem) item).getXmlString(ResourceType.STRING, false));
        }
        //assertTrue(item.hasDefault());
        FolderConfiguration folderConfig = new FolderConfiguration();
        folderConfig.setLocaleQualifier(LocaleQualifier.getQualifier("en"));
        Map<ResourceType, ResourceValueMap> configuredItems =
                ResourceRepositoryUtil.getConfiguredResources(mRepository, folderConfig)
                        .row(RES_AUTO);
        ResourceValue value = configuredItems.get(ResourceType.STRING).get("show_all_apps");
        assertNotNull(value);
        assertEquals("All", value.getValue());
        assertSame(ResourceType.STRING, value.getResourceType());

        folderConfig = new FolderConfiguration();
        folderConfig.setLocaleQualifier(LocaleQualifier.getQualifier("es"));
        configuredItems =
                ResourceRepositoryUtil.getConfiguredResources(mRepository, folderConfig)
                        .row(RES_AUTO);
        value = configuredItems.get(ResourceType.STRING).get("show_all_apps");
        assertNotNull(value);
        assertEquals("Todo", value.getValue());
        assertSame(ResourceType.STRING, value.getResourceType());

        itemList = mRepository.getResources(RES_AUTO, ResourceType.LAYOUT, "only_land");
        assertNotNull(itemList);
        //assertFalse(item.hasDefault());
        assertEquals(1, itemList.size());
        ResourceFile resourceFile = ((ResourceMergerItem) itemList.get(0)).getSourceFile();
        assertEquals("only_land.xml", resourceFile.getFile().getName());
        assertEquals(ScreenOrientation.LANDSCAPE.getResourceValue(), resourceFile.getQualifiers());

        itemList = mRepository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1");
        assertNotNull(itemList);
        //assertTrue(item.hasDefault());
        assertEquals(2, itemList.size());
    }

    public void testGetConfiguredResources() throws Exception {
        FolderConfiguration folderConfig = new FolderConfiguration();
        folderConfig.setLocaleQualifier(LocaleQualifier.getQualifier("es"));
        folderConfig.setScreenOrientationQualifier(
                new ScreenOrientationQualifier(ScreenOrientation.LANDSCAPE));

        Map<ResourceType, ResourceValueMap> configuredResources =
                ResourceRepositoryUtil.getConfiguredResources(mRepository, folderConfig)
                        .row(RES_AUTO);
        ResourceValueMap strings = configuredResources.get(ResourceType.STRING);
        ResourceValueMap layouts = configuredResources.get(ResourceType.LAYOUT);
        ResourceValueMap ids = configuredResources.get(ResourceType.ID);
        ResourceValueMap dimens = configuredResources.get(ResourceType.DIMEN);
        assertEquals(1, ids.size());
        assertEquals(1, dimens.size());
        assertEquals("dialog_min_width_major", dimens.get("dialog_min_width_major").getName());
        assertEquals("45%", dimens.get("dialog_min_width_major").getValue());
        assertEquals("Todo", strings.get("show_all_apps").getValue());
        assertEquals(3, layouts.size());
        assertNotNull(layouts.get("layout1"));
    }

    public void testUpdates() throws Exception {
        assertFalse(mRepository.hasResources(RES_AUTO, ResourceType.ANIM));
        assertFalse(mRepository.hasResources(RES_AUTO, ResourceType.MENU));
        assertFalse(mRepository.hasResources(RES_AUTO, ResourceType.BOOL));

        assertTrue(mRepository.hasResources(RES_AUTO, ResourceType.DRAWABLE));
        assertTrue(mRepository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "graphic"));

        // Delete the drawable graphic
        ResourceSet resourceSet = mResourceMerger.getDataSets().get(0);

        File drawableFolder = new File(mRes, FD_RES_DRAWABLE);
        File graphicFile = new File(drawableFolder, "graphic.9.png");

        resourceSet.updateWith(mRes, graphicFile, FileStatus.REMOVED, mLogger);
        mRepository.update(mResourceMerger);

        assertFalse(mRepository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "graphic"));
        assertFalse(mRepository.hasResources(RES_AUTO, ResourceType.DRAWABLE));

        // Delete one of the overridden layouts
        List<ResourceItem> itemList =
                mRepository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1");
        assertNotNull(itemList);
        assertTrue(itemList.size() > 1);
        assertTrue(mRepository.hasResources(RES_AUTO, ResourceType.LAYOUT));
        assertTrue(mRepository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));

        File layoutFolder = new File(mRes, FD_RES_LAYOUT + "-land");
        File layoutFile = new File(layoutFolder, "layout1.xml");

        resourceSet.updateWith(mRes, layoutFile, FileStatus.REMOVED, mLogger);
        mRepository.update(mResourceMerger);

        // We still have a layout1: only default now
        assertTrue(mRepository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));
        itemList = mRepository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1");
        assertNotNull(itemList);
        assertEquals(1, itemList.size());

        // change strings
        assertTrue(mRepository.hasResources(RES_AUTO, ResourceType.STRING, "dummy"));
        assertFalse(mRepository.hasResources(RES_AUTO, ResourceType.STRING, "myDummy"));
        itemList = mRepository.getResources(RES_AUTO, ResourceType.STRING, "dummy");
        assertNotNull(itemList);
        assertNotNull(itemList.get(0));
        ResourceFile stringResFile = ((ResourceMergerItem) itemList.get(0)).getSourceFile();
        File stringFile = stringResFile.getFile();
        assertTrue(stringFile.exists());
        String strings = Files.toString(stringFile, Charsets.UTF_8);
        assertNotNull(strings);
        strings = strings.replace("name=\"dummy\"", "name=\"myDummy\"");
        Files.write(strings, stringFile, Charsets.UTF_8);

        resourceSet.updateWith(mRes, stringFile, FileStatus.CHANGED, mLogger);
        mRepository.update(mResourceMerger);

        assertTrue(mRepository.hasResources(RES_AUTO, ResourceType.STRING, "myDummy"));
        assertFalse(mRepository.hasResources(RES_AUTO, ResourceType.STRING, "dummy"));

        // add files
        assertFalse(mRepository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout5"));
        File layout = new File(mRes, FD_RES_LAYOUT);
        File newFile = new File(layout, "layout5.xml");
        boolean created = newFile.createNewFile();
        assertTrue(created);
        resourceSet.updateWith(mRes, newFile, FileStatus.NEW, mLogger);
        mRepository.update(mResourceMerger);
        assertTrue(mRepository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout5"));
    }

    @SuppressWarnings("ConstantConditions")
    public void testXliff() throws Exception {
        String content =
                ""
                        + "<resources xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\" >\n"
                        + "    <string name=\"share_with_application\">\n"
                        + "        Share your score of <xliff:g id=\"score\" example=\"1337\">%1$s</xliff:g>\n"
                        + "        with <xliff:g id=\"application_name\" example=\"Bluetooth\">%2$s</xliff:g>!\n"
                        + "    </string>\n"
                        + "    <string name=\"callDetailsDurationFormat\"><xliff:g id=\"minutes\" example=\"42\">%s</xliff:g> mins <xliff:g id=\"seconds\" example=\"28\">%s</xliff:g> secs</string>\n"
                        + "    <string name=\"description_call\">Call <xliff:g id=\"name\">%1$s</xliff:g></string>\n"
                        + "    <string name=\"other\"><xliff:g id=\"number_of_sessions\">%1$s</xliff:g> sessions removed from your schedule</string>\n"
                        + "    <!-- Format string used to add a suffix like \"KB\" or \"MB\" to a number\n"
                        + "         to display a size in kilobytes, megabytes, or other size units.\n"
                        + "         Some languages (like French) will want to add a space between\n"
                        + "         the placeholders. -->\n"
                        + "    <string name=\"fileSizeSuffix\"><xliff:g id=\"number\" example=\"123\">%1$s</xliff:g><xliff:g id=\"unit\" example=\"KB\">%2$s</xliff:g></string>"
                        + "</resources>\n";
        TestResourceRepository resources =
                resourceFixture.createTestResources(
                        RES_AUTO, new Object[] {"values/strings.xml", content});

        assertEquals(Collections.singleton(RES_AUTO), resources.getNamespaces());
        assertNotNull(resources);

        assertNotNull(resources);
        assertEquals("Share your score of (1337) with (Bluetooth)!",
                resources.getResources(RES_AUTO, ResourceType.STRING, "share_with_application")
                        .get(0).getResourceValue().getValue());
        assertEquals("Call ${name}",
                resources.getResources(RES_AUTO, ResourceType.STRING, "description_call")
                        .get(0).getResourceValue().getValue());
        assertEquals("(42) mins (28) secs",
                resources.getResources(RES_AUTO, ResourceType.STRING, "callDetailsDurationFormat")
                        .get(0).getResourceValue().getValue());
        assertEquals("${number_of_sessions} sessions removed from your schedule",
                resources.getResources(RES_AUTO, ResourceType.STRING, "other")
                        .get(0).getResourceValue().getValue());
        assertEquals("(123)(KB)",
                resources.getResources(RES_AUTO, ResourceType.STRING, "fileSizeSuffix")
                        .get(0).getResourceValue().getValue());
    }
}
