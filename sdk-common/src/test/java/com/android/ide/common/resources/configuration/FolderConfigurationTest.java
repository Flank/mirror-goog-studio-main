/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.ide.common.resources.configuration;

import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceMergerItem;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRound;
import com.android.resources.UiMode;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import junit.framework.TestCase;

public class FolderConfigurationTest extends TestCase {
    /*
     * Test createDefault creates all the qualifiers.
     */
    public void testCreateDefault() {
        FolderConfiguration defaultConfig = FolderConfiguration.createDefault();

        // this is always valid and up to date.
        final int count = FolderConfiguration.getQualifierCount();

        // make sure all the qualifiers were created.
        for (int i = 0 ; i < count ; i++) {
            assertThat(defaultConfig.getQualifier(i))
                    .named("Qualifier with index " + i)
                    .isNotNull();
        }
    }

    public void testSimpleResMatch() {
        runConfigMatchTest(
                "en-rGB-port-hdpi-notouch-12key",
                3,
                "",
                "en",
                "fr-rCA",
                "en-port",
                "en-notouch-12key",
                "port-ldpi",
                "port-notouch-12key");
    }

    public void testIsMatchFor() {
        FolderConfiguration en = FolderConfiguration.getConfigForFolder("values-en");
        FolderConfiguration enUs = FolderConfiguration.getConfigForFolder("values-en-rUS");
        assertNotNull(en);
        assertNotNull(enUs);
        assertTrue(enUs.isMatchFor(enUs));
        assertTrue(en.isMatchFor(en));
        assertTrue(enUs.isMatchFor(en));
        assertTrue(en.isMatchFor(enUs));
    }

    public void testVersionResMatch() {
        runConfigMatchTest(
                "en-rUS-w600dp-h1024dp-large-port-mdpi-finger-nokeys-v12",
                2,
                "",
                "large",
                "w540dp");
    }

    public void testVersionResMatchWithBcp47() {
        runConfigMatchTest(
                "b+kok+Knda+419+VARIANT-w600dp",
                2,
                "",
                "large",
                "w540dp");
    }

    @SuppressWarnings("ConstantConditions")
    public void testAddQualifier() {
        FolderConfiguration defaultConfig = FolderConfiguration.createDefault();

        final int count = FolderConfiguration.getQualifierCount();
        for (int i = 0 ; i < count ; i++) {
            FolderConfiguration empty = new FolderConfiguration();

            ResourceQualifier q = defaultConfig.getQualifier(i);

            empty.addQualifier(q);

            // check it was added
            assertNotNull(
                    "addQualifier failed for " + q.getClass().getName(), empty.getQualifier(i));
        }
    }

    @SuppressWarnings("ConstantConditions")
    public void testGetConfig1() {
        FolderConfiguration configForFolder =
                FolderConfiguration.getConfig(new String[] { "values", "en", "rUS" });
        assertNotNull(configForFolder);
        assertEquals("en", configForFolder.getLocaleQualifier().getLanguage());
        assertEquals("US", configForFolder.getLocaleQualifier().getRegion());
        assertNull(configForFolder.getScreenDimensionQualifier());
        assertNull(configForFolder.getLayoutDirectionQualifier());
    }

    @SuppressWarnings("ConstantConditions")
    public void testInvalidRepeats() {
        assertNull(FolderConfiguration.getConfigForFolder("values-en-rUS-rES"));
    }

    @SuppressWarnings("ConstantConditions")
    public void testGetConfig2() {
        FolderConfiguration configForFolder =
                FolderConfiguration.getConfigForFolder("values-en-rUS");
        assertNotNull(configForFolder);
        assertEquals("en", configForFolder.getLocaleQualifier().getLanguage());
        assertEquals("US", configForFolder.getLocaleQualifier().getRegion());
        assertNull(configForFolder.getScreenDimensionQualifier());
        assertNull(configForFolder.getLayoutDirectionQualifier());
    }

    @SuppressWarnings("ConstantConditions")
    public void testGetConfigCaseInsensitive() {
        FolderConfiguration configForFolder =
                FolderConfiguration.getConfigForFolder("values-EN-rus");
        assertNotNull(configForFolder);
        assertEquals("en", configForFolder.getLocaleQualifier().getLanguage());
        assertEquals("US", configForFolder.getLocaleQualifier().getRegion());
        assertNull(configForFolder.getScreenDimensionQualifier());
        assertNull(configForFolder.getLayoutDirectionQualifier());
        assertEquals("layout-en-rUS", configForFolder.getFolderName(ResourceFolderType.LAYOUT));

        runConfigMatchTest(
                "en-rgb-Port-HDPI-notouch-12key",
                3,
                "",
                "en",
                "fr-rCA",
                "en-port",
                "en-notouch-12key",
                "port-ldpi",
                "port-notouch-12key");
    }

    public void testToStrings() {
        FolderConfiguration configForFolder = FolderConfiguration.getConfigForFolder("values-en-rUS");
        assertNotNull(configForFolder);
        assertEquals("Locale en_US", configForFolder.toDisplayString());
        assertEquals("en,US", configForFolder.toShortDisplayString());
        assertEquals("layout-en-rUS", configForFolder.getFolderName(ResourceFolderType.LAYOUT));
        assertEquals("en-rUS", configForFolder.getQualifierString());
        assertEquals("", new FolderConfiguration().getQualifierString());
    }

    public void testNormalize() {
        // test normal qualifiers that all have the same min SDK
        doTestNormalize(4, "large");
        doTestNormalize(8, "notnight");
        doTestNormalize(13, "sw42dp");
        doTestNormalize(17, "ldrtl");

        // test we take the highest qualifier
        doTestNormalize(13, "sw42dp", "large");

        // test where different values have different minSdk
        /* Ambiguous now that aapt accepts 3 letter language codes; get clarification.
        doTestNormalize(8, "car");
        */
        doTestNormalize(13, "television");
        doTestNormalize(16, "appliance");

        // test case where there's already a higher -v# qualifier
        doTestNormalize(18, "sw42dp", "v18");

        // finally test that in some cases it won't add a -v# value.
        FolderConfiguration configForFolder = FolderConfiguration.getConfigFromQualifiers(
                Collections.singletonList("port"));

        assertNotNull(configForFolder);

        configForFolder.normalize();
        VersionQualifier versionQualifier = configForFolder.getVersionQualifier();
        assertNull(versionQualifier);
    }

    @SuppressWarnings("ConstantConditions")
    public void testConfigMatch() {
        FolderConfiguration ref = FolderConfiguration.createDefault();
        ref.setDensityQualifier(new DensityQualifier(Density.XHIGH));
        ref.addQualifier(new ScreenOrientationQualifier(ScreenOrientation.PORTRAIT));
        List<Configurable> configurables = getConfigurable(
                "",                // No qualifier
                "xhdpi",           // A matching qualifier
                "land",            // A non matching qualifier
                "xhdpi-v14",       // Matching qualifier with ignored qualifier
                "v14"              // Ignored qualifier
        );
        // First check that when all qualifiers are present, we match only one resource.
        List<Configurable> matchingConfigurables = ref.findMatchingConfigurables(configurables);
        assertEquals(ImmutableList.of(configurables.get(3)), matchingConfigurables);

        // Now remove the version qualifier and check that we "xhdpi" and "xhdpi-v14"
        ref.setVersionQualifier(null);
        matchingConfigurables = ref.findMatchingConfigurables(configurables);
        assertEquals(ImmutableSet.of(configurables.get(1), configurables.get(3)),
                ImmutableSet.copyOf(matchingConfigurables));

    }

    public void testIsRoundMatch() {
        FolderConfiguration configForFolder = FolderConfiguration
                .getConfigForFolder("values-en-round");
        assertNotNull(configForFolder);
        assertNotNull(configForFolder.getScreenRoundQualifier());
        assertEquals(ScreenRound.ROUND, configForFolder.getScreenRoundQualifier().getValue());
        runConfigMatchTest("en-rgb-Round-Port-HDPI-notouch-12key", 4,
                "",
                "en",
                "fr-rCa",
                "en-notround-hdpi",
                "en-notouch");

        runConfigMatchTest("en-rgb-Round-Port-HDPI-notouch-12key", 2,
                "",
                "en",
                "en-round-hdpi",
                "port-12key");
    }

    public void testDensityQualifier() {
        // test find correct density
        runConfigMatchTest("hdpi", 2,
                "ldpi",
                "mdpi",
                "hdpi",
                "xhdpi");

        // test mdpi matches no-density
        runConfigMatchTest("mdpi", 0,
                "",
                "ldpi",
                "hdpi");

        // test, if there is no no-density, that we match the higher dpi
        runConfigMatchTest("mdpi", 1,
                "ldpi",
                "hdpi");

        // mdpi is better than no-density
        runConfigMatchTest("mdpi", 1,
                "",
                "mdpi",
                "hdpi");
        runConfigMatchTest("xhdpi", 2,
                "ldpi",
                "",
                "mdpi");

        // scale down better than scale up
        runConfigMatchTest("xhdpi", 4,
                "",
                "ldpi",
                "mdpi",
                "hdpi",
                "xxxhdpi");
        runConfigMatchTest("hdpi", 3,
                "",
                "ldpi",
                "mdpi",
                "xhdpi",
                "xxhdpi");
        runConfigMatchTest("mdpi", 0,
                "ldpi",
                "400dpi",
                "xxhdpi",
                "xxxhdpi");
    }

    public void testNullQualifierValidity() {
        FolderConfiguration folderConfiguration = new FolderConfiguration();
        for (int i = 0; i < FolderConfiguration.getQualifierCount(); i++) {
            ResourceQualifier qualifier = folderConfiguration.getQualifier(i);
            if (qualifier != null) {
                assertFalse(qualifier.isValid());
            }
        }
    }

    // --- helper methods

    private static final class MockConfigurable implements Configurable {
        private final FolderConfiguration mConfig;

        MockConfigurable(String config) {
            mConfig = FolderConfiguration.getConfig(getFolderSegments(config));
        }

        @Override
        public FolderConfiguration getConfiguration() {
            return mConfig;
        }

        @Override
        public String toString() {
            return mConfig.toString();
        }
    }

    private static void runConfigMatchTest(String refConfig, int resultIndex, String... configs) {
        FolderConfiguration reference = FolderConfiguration.getConfig(getFolderSegments(refConfig));
        assertNotNull(reference);

        List<Configurable> list = getConfigurable(configs);

        Configurable match = reference.findMatchingConfigurable(list);
        assertEquals(resultIndex, list.indexOf(match));
    }

    private static List<Configurable> getConfigurable(String... configs) {
        ArrayList<Configurable> list = new ArrayList<>();

        for (String config : configs) {
            list.add(new MockConfigurable(config));
        }

        return list;
    }

    private static String[] getFolderSegments(String config) {
        return (!config.isEmpty() ? "foo-" + config : "foo").split("-");
    }

    public void testSort1() {
        List<FolderConfiguration> configs = new ArrayList<>();
        FolderConfiguration f1 = FolderConfiguration.getConfigForFolder("values-hdpi");
        FolderConfiguration f2 = FolderConfiguration.getConfigForFolder("values-v11");
        FolderConfiguration f3 = FolderConfiguration.getConfigForFolder("values-sp");
        FolderConfiguration f4 = FolderConfiguration.getConfigForFolder("values-v4");
        configs.add(f1);
        configs.add(f2);
        configs.add(f3);
        configs.add(f4);
        assertEquals(Arrays.asList(f1, f2, f3, f4), configs);
        Collections.sort(configs);
        assertEquals(Arrays.asList(f2, f4, f1, f3), configs);
    }

    public void testSort2() {
        // Test case from
        // http://developer.android.com/guide/topics/resources/providing-resources.html#BestMatch
        List<FolderConfiguration> configs = new ArrayList<>();
        for (String name : new String[] {
                "drawable",
                "drawable-en",
                "drawable-fr-rCA",
                "drawable-en-port",
                "drawable-en-notouch-12key",
                "drawable-port-ldpi",
                "drawable-port-notouch-12key"
         }) {
            FolderConfiguration config = FolderConfiguration.getConfigForFolder(name);
            assertNotNull(name, config);
            configs.add(config);
        }
        Collections.sort(configs);
        Collections.reverse(configs);
        //assertEquals("", configs.get(0).toDisplayString());

        List<String> strings = new ArrayList<>();
        for (FolderConfiguration config : configs) {
            strings.add(config.getQualifierString());
        }
        assertEquals("fr-rCA,en-port,en-notouch-12key,en,port-ldpi,port-notouch-12key,",
                Joiner.on(",").skipNulls().join(strings));
    }

    private static void doTestNormalize(int expectedVersion, String... segments) {
        FolderConfiguration configForFolder =
                FolderConfiguration.getConfigFromQualifiers(Arrays.asList(segments));

        assertNotNull(configForFolder);

        configForFolder.normalize();
        VersionQualifier versionQualifier = configForFolder.getVersionQualifier();
        assertNotNull(versionQualifier);
        assertEquals(expectedVersion, versionQualifier.getVersion());
    }

    public void testCarModeAndLanguage() {
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-car");
        assertNotNull(config);
        assertNull(config.getLocaleQualifier());
        assertNotNull(config.getUiModeQualifier());
        assertEquals(UiMode.CAR, config.getUiModeQualifier().getValue());

        config = FolderConfiguration.getConfigForFolder("values-b+car");
        assertNotNull(config);
        assertNotNull(config.getLocaleQualifier());
        assertNull(config.getUiModeQualifier());
        assertEquals("car", config.getLocaleQualifier().getLanguage());
    }

    public void testVrHeadset() {
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-vrheadset");
        assertNotNull(config);
        assertNotNull(config.getUiModeQualifier());
        assertEquals(UiMode.VR_HEADSET, config.getUiModeQualifier().getValue());
    }

    public void testIsMatchForBcp47() {
        FolderConfiguration blankFolder = FolderConfiguration.getConfigForFolder("values");
        FolderConfiguration enFolder = FolderConfiguration.getConfigForFolder("values-en");
        FolderConfiguration deFolder = FolderConfiguration.getConfigForFolder("values-de");
        FolderConfiguration deBcp47Folder = FolderConfiguration.getConfigForFolder("values-b+de");
        assertNotNull(enFolder);
        assertNotNull(deFolder);
        assertNotNull(deBcp47Folder);
        assertFalse(enFolder.isMatchFor(deFolder));
        assertFalse(deFolder.isMatchFor(enFolder));
        assertFalse(enFolder.isMatchFor(deBcp47Folder));
        assertFalse(deBcp47Folder.isMatchFor(enFolder));

        assertTrue(enFolder.isMatchFor(blankFolder));
        assertTrue(deFolder.isMatchFor(blankFolder));
        assertTrue(deBcp47Folder.isMatchFor(blankFolder));
    }

    public void testFindMatchingConfigurables() {
        ResourceMergerItem itemBlank =
                new ResourceMergerItem("foo", null, ResourceType.STRING, null, null) {
                    @Override
                    public String toString() {
                        return "itemBlank";
                    }
                };
        ResourceFile sourceBlank = ResourceFile.createSingle(new File("sourceBlank"), itemBlank, "");
        itemBlank.setSourceFile(sourceBlank);
        FolderConfiguration configBlank = itemBlank.getConfiguration();

        ResourceMergerItem itemEn =
                new ResourceMergerItem("foo", null, ResourceType.STRING, null, null) {
                    @Override
                    public String toString() {
                        return "itemEn";
                    }
                };
        ResourceFile sourceEn = ResourceFile.createSingle(new File("sourceEn"), itemBlank, "en");
        itemEn.setSourceFile(sourceEn);
        FolderConfiguration configEn = itemEn.getConfiguration();

        ResourceMergerItem itemBcpEn =
                new ResourceMergerItem("foo", null, ResourceType.STRING, null, null) {
                    @Override
                    public String toString() {
                        return "itemBcpEn";
                    }
                };
        ResourceFile sourceBcpEn = ResourceFile.createSingle(new File("sourceBcpEn"), itemBlank, "b+en");
        itemBcpEn.setSourceFile(sourceBcpEn);
        FolderConfiguration configBcpEn = itemBcpEn.getConfiguration();

        ResourceMergerItem itemDe =
                new ResourceMergerItem("foo", null, ResourceType.STRING, null, null) {
                    @Override
                    public String toString() {
                        return "itemDe";
                    }
                };

        ResourceFile sourceDe = ResourceFile.createSingle(new File("sourceDe"), itemBlank, "de");
        itemDe.setSourceFile(sourceDe);
        FolderConfiguration configDe = itemDe.getConfiguration();

        // "" matches everything
        assertEquals(Arrays.asList(itemBlank, itemBcpEn, itemEn, itemDe),
                configBlank.findMatchingConfigurables(
                        Arrays.asList(itemBlank, itemBcpEn, itemEn, itemDe)));

        // "de" matches only "" and "de"
        assertEquals(Arrays.asList(itemBlank, itemDe),
                configDe.findMatchingConfigurables(
                        Arrays.asList(itemBlank, itemBcpEn, itemEn, itemDe)));

        // "en" matches "en" and "b+en"
        assertTrue(configEn.isMatchFor(configBcpEn));
        assertTrue(configBcpEn.isMatchFor(configEn));
        assertEquals(Arrays.asList(itemBcpEn, itemEn),
                configEn.findMatchingConfigurables(
                        Arrays.asList(itemBlank, itemBcpEn, itemEn, itemDe)));

        // "b+en" matches "en and "b+en"
        assertEquals(Arrays.asList(itemBcpEn, itemEn),
                configBcpEn.findMatchingConfigurables(
                        Arrays.asList(itemBlank, itemBcpEn, itemEn, itemDe)));
    }

    public void testFromQualifierString() {
        FolderConfiguration blankFolder = FolderConfiguration.getConfigForQualifierString("");
        FolderConfiguration enFolder = FolderConfiguration.getConfigForQualifierString("en");
        FolderConfiguration deFolder = FolderConfiguration.getConfigForQualifierString("de");
        FolderConfiguration deBcp47Folder = FolderConfiguration.getConfigForQualifierString("b+de");
        FolderConfiguration twoQualifiersFolder =
                FolderConfiguration.getConfigForQualifierString("de-hdpi");

        assertNotNull(enFolder);
        assertNotNull(deFolder);
        assertNotNull(deBcp47Folder);
        assertFalse(enFolder.isMatchFor(deFolder));
        assertFalse(deFolder.isMatchFor(enFolder));
        assertFalse(enFolder.isMatchFor(deBcp47Folder));
        assertFalse(deBcp47Folder.isMatchFor(enFolder));

        assertTrue(enFolder.isMatchFor(blankFolder));
        assertTrue(deFolder.isMatchFor(blankFolder));
        assertTrue(deBcp47Folder.isMatchFor(blankFolder));

        assertEquals("de", twoQualifiersFolder.getLocaleQualifier().getLanguage());
        assertEquals(Density.HIGH, twoQualifiersFolder.getDensityQualifier().getValue());
    }

    public void testCopyOf() {
        FolderConfiguration deBcp47Folder = FolderConfiguration.getConfigForFolder("values-b+de");
        FolderConfiguration copy = FolderConfiguration.copyOf(deBcp47Folder);
        assertTrue(copy.isMatchFor(deBcp47Folder));

        copy.setLocaleQualifier(new LocaleQualifier("en"));
        assertEquals("en", copy.getLocaleQualifier().getLanguage());
        assertEquals("de", deBcp47Folder.getLocaleQualifier().getLanguage());

        copy.setDensityQualifier(new DensityQualifier(Density.HIGH));
        assertEquals(Density.HIGH, copy.getDensityQualifier().getValue());
        assertEquals(new FolderConfiguration().getDensityQualifier(),
                deBcp47Folder.getDensityQualifier());

        FolderConfiguration blankFolder = FolderConfiguration.getConfigForFolder("values");
        copy = FolderConfiguration.copyOf(blankFolder);
        assertTrue(copy.isMatchFor(blankFolder));

        copy.setVersionQualifier(new VersionQualifier(21));
        assertEquals(21, copy.getVersionQualifier().getVersion());
        assertNull(blankFolder.getVersionQualifier());
    }

    public void testScreenSizeMatching() {
        runConfigMatchTest("normal-v21", 2, "", "v21", "normal", "large");
        runConfigMatchTest("normal-v21", 1, "", "v21", "small", "large");
        runConfigMatchTest("normal-v21", 0, "", "v23", "small", "large");
        runConfigMatchTest("large-v21", 3, "", "v21", "small", "large");
        runConfigMatchTest("large-v21", 1, "", "v21", "small", "xlarge");
        runConfigMatchTest("small-v21", 2, "", "v21", "small", "xlarge");
        runConfigMatchTest("small-v21", 1, "", "v21", "normal", "xlarge");
    }

    public void testForEach() {
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-en-vrheadset");
        assertNotNull(config);
        List<Object> list = new ArrayList<>();
        config.forEach(list::add);
        assertEquals("[en, vrheadset]", list.toString());
    }

    public void testAnyPredicate() {
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-vrheadset");
        assertNotNull(config);
        assertNotNull(config.getUiModeQualifier());
        assertTrue(config.any(qualifier -> qualifier instanceof UiModeQualifier));
        assertFalse(config.any(qualifier -> qualifier instanceof DensityQualifier));
    }
}
