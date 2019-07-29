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

import static com.android.ide.common.resources.configuration.FolderConfigurationSubject.assertThat;
import static com.android.ide.common.resources.configuration.VersionQualifier.DEFAULT_VERSION;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceMergerItem;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRound;
import com.android.resources.UiMode;
import com.google.common.base.Joiner;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class FolderConfigurationTest {
    /*
     * Test createDefault creates all the qualifiers.
     */
    @Test
    public void createDefault() {
        FolderConfiguration defaultConfig = FolderConfiguration.createDefault();

        // this is always valid and up to date.
        int count = FolderConfiguration.getQualifierCount();

        // make sure all the qualifiers were created.
        for (int i = 0; i < count; i++) {
            assertThat(defaultConfig.getQualifier(i))
                    .named("Qualifier with index " + i)
                    .isNotNull();
        }
    }

    @Test
    public void simpleResMatch() {
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

    @Test
    public void isMatchFor() {
        FolderConfiguration en = FolderConfiguration.getConfigForFolder("values-en");
        assertThat(en).isNotNull();
        FolderConfiguration enUs = FolderConfiguration.getConfigForFolder("values-en-rUS");
        assertThat(enUs).isNotNull();
        assertThat(enUs).isMatchFor(enUs);
        assertThat(en).isMatchFor(en);
        assertThat(enUs).isMatchFor(en);
        assertThat(en).isMatchFor(enUs);
    }

    @Test
    public void versionResMatch() {
        runConfigMatchTest(
                "en-rUS-w600dp-h1024dp-large-port-mdpi-finger-nokeys-v12",
                2,
                "",
                "large",
                "w540dp");
    }

    @Test
    public void versionResMatchWithBcp47() {
        runConfigMatchTest(
                "b+kok+Knda+419+VARIANT-w600dp",
                2,
                "",
                "large",
                "w540dp");
    }

    @Test
    public void addQualifier() {
        FolderConfiguration defaultConfig = FolderConfiguration.createDefault();

        int count = FolderConfiguration.getQualifierCount();
        for (int i = 0; i < count; i++) {
            FolderConfiguration empty = new FolderConfiguration();

            ResourceQualifier q = defaultConfig.getQualifier(i);

            empty.addQualifier(q);

            // check it was added
            assertThat(empty.getQualifier(i))
                    .named("addQualifier failed for " + q.getClass().getName())
                    .isNotNull();
        }
    }

    @Test
    public void getConfig1() {
        FolderConfiguration config =
                FolderConfiguration.getConfig(new String[] {"values", "en", "rUS"});
        assertThat(config).isNotNull();

        assertThat(config).hasLanguage("en");
        assertThat(config).hasRegion("US");
        assertThat(config).hasNoScreenDimension();
        assertThat(config).hasNoLayoutDirection();
    }

    @Test
    public void invalidRepeats() {
        assertThat(FolderConfiguration.getConfigForFolder("values-en-rUS-rES"))
                .named(("Folder Config for 'values-en-rUS-rES'"))
                .isNull();
    }

    @Test
    public void getConfig2() {
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-en-rUS");
        assertThat(config).named("Folder Config for 'values-en-rUS'").isNotNull();
        assertThat(config).hasLanguage("en");
        assertThat(config).hasRegion("US");
        assertThat(config).hasNoScreenDimension();
        assertThat(config).hasNoLayoutDirection();
    }

    @Test
    public void getConfigCaseInsensitive() {
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-EN-rus");
        assertThat(config).named("Folder Config for 'values-EN-rus'").isNotNull();
        assertThat(config).hasLanguage("en");
        assertThat(config).hasRegion("US");
        assertThat(config).hasNoScreenDimension();
        assertThat(config).hasNoLayoutDirection();
        assertThat(config.getFolderName(ResourceFolderType.LAYOUT))
                .named("Layout folder name for " + config.getQualifierString())
                .isEqualTo("layout-en-rUS");

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

    @Test
    public void toStrings() {
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-en-rUS");

        assertThat(config).named("Folder Config for 'values-en-rUS'").isNotNull();

        assertThat(config.toDisplayString())
                .named("Display String for " + config.getQualifierString())
                .isEqualTo("Locale en_US");
        assertThat(config.toShortDisplayString())
                .named("Display String for " + config.getQualifierString())
                .isEqualTo("en,US");

        assertThat(config.getFolderName(ResourceFolderType.LAYOUT))
                .named("Layout folder name for " + config.getQualifierString())
                .isEqualTo("layout-en-rUS");

        assertThat(config.getQualifierString())
                .named("Qualifier String for " + config.getQualifierString())
                .isEqualTo("en-rUS");

        assertThat(new FolderConfiguration().getQualifierString())
                .named("Qualifier string for empty FolderConfiguration")
                .isEqualTo("");
    }

    @Test
    public void normalizeByAddingImpliedVersionQualifier() {
        // test normal qualifiers that all have the same min SDK
        doTestNormalizeByAddingImpliedVersionQualifier(4, "large");
        doTestNormalizeByAddingImpliedVersionQualifier(8, "notnight");
        doTestNormalizeByAddingImpliedVersionQualifier(13, "sw42dp");
        doTestNormalizeByAddingImpliedVersionQualifier(17, "ldrtl");

        // test we take the highest qualifier
        doTestNormalizeByAddingImpliedVersionQualifier(13, "sw42dp", "large");

        // test where different values have different minSdk
        /* Ambiguous now that aapt accepts 3 letter language codes; get clarification.
        doTestNormalize(8, "car");
        */
        doTestNormalizeByAddingImpliedVersionQualifier(13, "television");
        doTestNormalizeByAddingImpliedVersionQualifier(16, "appliance");

        // test case where there's already a higher -v# qualifier
        doTestNormalizeByAddingImpliedVersionQualifier(18, "sw42dp", "v18");

        // finally test that in some cases it won't add a -v# value.
        doTestNormalizeByAddingImpliedVersionQualifier(DEFAULT_VERSION, "port");
    }

    @Test
    public void normalizeByRemovingRedundantVersionQualifier() {
        // The "v1" qualifier is removed because it is redundant.
        doTestNormalizeByRemovingRedundantVersionQualifier("", "v1");
        // The "v2" qualifier is preserved.
        doTestNormalizeByRemovingRedundantVersionQualifier("v2", "v2");
        // The "large" qualifier was introduced at API level 4.
        // Version qualifiers up to "v4" are removed in combination with "large".
        doTestNormalizeByRemovingRedundantVersionQualifier("large", "large-v3");
        doTestNormalizeByRemovingRedundantVersionQualifier("large", "large-v4");
        // Version qualifiers "v5" and above are preserved.
        doTestNormalizeByRemovingRedundantVersionQualifier("large-v5", "large-v5");
        // The "notnight" qualifier was introduced at API level 8.
        doTestNormalizeByRemovingRedundantVersionQualifier("notnight", "notnight-v8");
        doTestNormalizeByRemovingRedundantVersionQualifier("notnight-v9", "notnight-v9");
        // The "sw42dp" qualifier was introduced at API level 13.
        doTestNormalizeByRemovingRedundantVersionQualifier("sw42dp", "sw42dp-v13");
        doTestNormalizeByRemovingRedundantVersionQualifier("sw42dp-v14", "sw42dp-v14");
        // The "sw42dp" qualifier was introduced at API level 13.
        doTestNormalizeByRemovingRedundantVersionQualifier("ldrtl", "ldrtl-v17");
        doTestNormalizeByRemovingRedundantVersionQualifier("ldrtl-v18", "ldrtl-v18");
        // The "television" qualifier was introduced at API level 13.
        doTestNormalizeByRemovingRedundantVersionQualifier("television", "television-v13");
        doTestNormalizeByRemovingRedundantVersionQualifier("television-v14", "television-v14");
        // "The appliance" qualifier was introduced at API level 16.
        doTestNormalizeByRemovingRedundantVersionQualifier("appliance", "appliance-v16");
        doTestNormalizeByRemovingRedundantVersionQualifier("appliance-v17", "appliance-v17");

        // The highest API level takes effect.
        doTestNormalizeByRemovingRedundantVersionQualifier("sw42dp-large", "sw42dp-large-v13");
        doTestNormalizeByRemovingRedundantVersionQualifier("sw42dp-large-v14", "sw42dp-large-v14");
    }

    @Test
    public void configMatch() {
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
        assertThat(matchingConfigurables).containsExactly(configurables.get(3));

        // Now remove the version qualifier and check that we "xhdpi" and "xhdpi-v14"
        ref.setVersionQualifier(null);
        matchingConfigurables = ref.findMatchingConfigurables(configurables);
        assertThat(matchingConfigurables)
                .containsExactly(configurables.get(1), configurables.get(3));
    }

    @Test
    public void isRoundMatch() {
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-en-round");
        assertThat(config).named("FoldeR Config for 'values-en-round'").isNotNull();
        assertThat(config).hasScreenRound(ScreenRound.ROUND);

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

    @Test
    public void densityQualifier() {
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

    @Test
    public void nullQualifierValidity() {
        FolderConfiguration folderConfiguration = new FolderConfiguration();
        for (int i = 0; i < FolderConfiguration.getQualifierCount(); i++) {
            ResourceQualifier qualifier = folderConfiguration.getQualifier(i);
            if (qualifier != null) {
                assertThat(qualifier.isValid())
                        .named("isValid for " + i + "th qualifier")
                        .isFalse();
            }
        }
    }

    @Test
    public void layoutDirectionQualifier() {
        FolderConfiguration ltr = FolderConfiguration.getConfigForFolder("layout-ldltr");
        FolderConfiguration rtl = FolderConfiguration.getConfigForFolder("layout-ldrtl");
        FolderConfiguration base = FolderConfiguration.createDefault();
        assertThat(ltr).isNotMatchFor(rtl);
        assertThat(ltr).isMatchFor(base);
        assertThat(rtl).isMatchFor(base);
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
        assertThat(reference).isNotNull();

        List<Configurable> list = getConfigurable(configs);

        Configurable match = reference.findMatchingConfigurable(list);
        assertThat(list.indexOf(match)).isEqualTo(resultIndex);
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

    @Test
    public void sort1() {
        List<FolderConfiguration> configs = new ArrayList<>();
        FolderConfiguration f1 = FolderConfiguration.getConfigForFolder("values-hdpi");
        FolderConfiguration f2 = FolderConfiguration.getConfigForFolder("values-v11");
        FolderConfiguration f3 = FolderConfiguration.getConfigForFolder("values-sp");
        FolderConfiguration f4 = FolderConfiguration.getConfigForFolder("values-v4");
        configs.add(f1);
        configs.add(f2);
        configs.add(f3);
        configs.add(f4);
        assertThat(configs).containsExactly(f1, f2, f3, f4);
        Collections.sort(configs);
        assertThat(configs).containsExactly(f2, f4, f1, f3).inOrder();
    }

    @Test
    public void sort2() {
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
            assertThat(config).named(name).isNotNull();
            configs.add(config);
        }
        Collections.sort(configs);
        Collections.reverse(configs);
        //assertEquals("", configs.get(0).toDisplayString());

        List<String> strings = new ArrayList<>();
        for (FolderConfiguration config : configs) {
            strings.add(config.getQualifierString());
        }
        assertThat(Joiner.on(",").skipNulls().join(strings))
                .isEqualTo("fr-rCA,en-port,en-notouch-12key,en,port-ldpi,port-notouch-12key,");
    }

    private static void doTestNormalizeByAddingImpliedVersionQualifier(
            int expectedVersion, String... segments) {
        FolderConfiguration config =
                FolderConfiguration.getConfigFromQualifiers(Arrays.asList(segments));
        assertThat(config).isNotNull();

        config.normalizeByAddingImpliedVersionQualifier();
        if (expectedVersion == DEFAULT_VERSION) {
            assertThat(config).hasNoVersion();
        } else {
            assertThat(config).hasVersion(expectedVersion);
        }
    }

    private static void doTestNormalizeByRemovingRedundantVersionQualifier(
            @NonNull String expectedNormalizedQualifierString, @NonNull String qualifierString) {
        FolderConfiguration config =
                FolderConfiguration.getConfigForQualifierString(qualifierString);
        assertThat(config).isNotNull();

        config.normalizeByRemovingRedundantVersionQualifier();
        assertThat(config.getQualifierString()).isEqualTo(expectedNormalizedQualifierString);
    }

    @Test
    public void carModeAndLanguage() {
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-car");
        assertThat(config).isNotNull();

        assertThat(config).hasNoLocale();
        assertThat(config).hasUiMode(UiMode.CAR);

        config = FolderConfiguration.getConfigForFolder("values-b+car");
        assertThat(config).isNotNull();

        assertThat(config).hasLanguage("car");
        assertThat(config).hasNoUiMode();
    }

    @Test
    public void vrHeadset() {
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-vrheadset");
        assertThat(config).isNotNull();

        assertThat(config).hasUiMode(UiMode.VR_HEADSET);
    }

    @Test
    public void isMatchForBcp47() {
        FolderConfiguration blankFolder = FolderConfiguration.getConfigForFolder("values");
        FolderConfiguration enFolder = FolderConfiguration.getConfigForFolder("values-en");
        FolderConfiguration deFolder = FolderConfiguration.getConfigForFolder("values-de");
        FolderConfiguration deBcp47Folder = FolderConfiguration.getConfigForFolder("values-b+de");
        assertThat(blankFolder).isNotNull();
        assertThat(enFolder).isNotNull();
        assertThat(deFolder).isNotNull();
        assertThat(deBcp47Folder).isNotNull();

        assertThat(enFolder).isNotMatchFor(deFolder);
        assertThat(deFolder).isNotMatchFor(enFolder);
        assertThat(enFolder).isNotMatchFor(deBcp47Folder);
        assertThat(deBcp47Folder).isNotMatchFor(enFolder);

        assertThat(enFolder).isMatchFor(blankFolder);
        assertThat(deFolder).isMatchFor(blankFolder);
        assertThat(deBcp47Folder).isMatchFor(blankFolder);
    }

    @Test
    public void findMatchingConfigurables() {
        ResourceMergerItem itemBlank =
                new ResourceMergerItem("foo", null, ResourceType.STRING, null, null, null) {
                    @Override
                    public String toString() {
                        return "itemBlank";
                    }
                };
        ResourceFile sourceBlank = ResourceFile.createSingle(new File("sourceBlank"), itemBlank, "");
        itemBlank.setSourceFile(sourceBlank);
        FolderConfiguration configBlank = itemBlank.getConfiguration();

        ResourceMergerItem itemEn =
                new ResourceMergerItem("foo", null, ResourceType.STRING, null, null, null) {
                    @Override
                    public String toString() {
                        return "itemEn";
                    }
                };
        ResourceFile sourceEn = ResourceFile.createSingle(new File("sourceEn"), itemBlank, "en");
        itemEn.setSourceFile(sourceEn);
        FolderConfiguration configEn = itemEn.getConfiguration();

        ResourceMergerItem itemBcpEn =
                new ResourceMergerItem("foo", null, ResourceType.STRING, null, null, null) {
                    @Override
                    public String toString() {
                        return "itemBcpEn";
                    }
                };
        ResourceFile sourceBcpEn = ResourceFile.createSingle(new File("sourceBcpEn"), itemBlank, "b+en");
        itemBcpEn.setSourceFile(sourceBcpEn);
        FolderConfiguration configBcpEn = itemBcpEn.getConfiguration();

        ResourceMergerItem itemDe =
                new ResourceMergerItem("foo", null, ResourceType.STRING, null, null, null) {
                    @Override
                    public String toString() {
                        return "itemDe";
                    }
                };

        ResourceFile sourceDe = ResourceFile.createSingle(new File("sourceDe"), itemBlank, "de");
        itemDe.setSourceFile(sourceDe);
        FolderConfiguration configDe = itemDe.getConfiguration();

        // "" matches everything
        assertThat(
                        configBlank.findMatchingConfigurables(
                                Arrays.asList(itemBlank, itemBcpEn, itemEn, itemDe)))
                .containsExactly(itemBlank, itemBcpEn, itemEn, itemDe);

        // "de" matches only "" and "de"
        assertThat(
                        configDe.findMatchingConfigurables(
                                Arrays.asList(itemBlank, itemBcpEn, itemEn, itemDe)))
                .containsExactly(itemBlank, itemDe);

        // "en" matches "en" and "b+en"
        assertThat(configEn).isMatchFor(configBcpEn);
        assertThat(configBcpEn).isMatchFor(configEn);
        assertThat(
                        configEn.findMatchingConfigurables(
                                Arrays.asList(itemBlank, itemBcpEn, itemEn, itemDe)))
                .containsExactly(itemBcpEn, itemEn);

        // "b+en" matches "en and "b+en"
        assertThat(
                        configBcpEn.findMatchingConfigurables(
                                Arrays.asList(itemBlank, itemBcpEn, itemEn, itemDe)))
                .containsExactly(itemBcpEn, itemEn);
    }

    @Test
    public void fromQualifierString() {
        FolderConfiguration blankFolder = FolderConfiguration.getConfigForQualifierString("");
        FolderConfiguration enFolder = FolderConfiguration.getConfigForQualifierString("en");
        FolderConfiguration deFolder = FolderConfiguration.getConfigForQualifierString("de");
        FolderConfiguration deBcp47Folder = FolderConfiguration.getConfigForQualifierString("b+de");
        FolderConfiguration twoQualifiersFolder =
                FolderConfiguration.getConfigForQualifierString("de-hdpi");

        assertThat(blankFolder).isNotNull();
        assertThat(enFolder).isNotNull();
        assertThat(deFolder).isNotNull();
        assertThat(deBcp47Folder).isNotNull();
        assertThat(twoQualifiersFolder).isNotNull();

        assertThat(enFolder).isNotMatchFor(deFolder);
        assertThat(deFolder).isNotMatchFor(enFolder);
        assertThat(enFolder).isNotMatchFor(deBcp47Folder);
        assertThat(deBcp47Folder).isNotMatchFor(enFolder);

        assertThat(enFolder).isMatchFor(blankFolder);
        assertThat(deFolder).isMatchFor(blankFolder);
        assertThat(deBcp47Folder).isMatchFor(blankFolder);

        assertThat(twoQualifiersFolder).hasLanguage("de");
        assertThat(twoQualifiersFolder).hasDensity(Density.HIGH);
    }

    @Test
    public void copyOf() {
        FolderConfiguration deBcp47Folder = FolderConfiguration.getConfigForFolder("values-b+de");
        FolderConfiguration copy = FolderConfiguration.copyOf(deBcp47Folder);
        assertThat(deBcp47Folder).isNotNull();
        assertThat(copy).isNotNull();

        assertThat(copy).isMatchFor(deBcp47Folder);

        copy.setLocaleQualifier(new LocaleQualifier("en"));
        assertThat(copy).hasLanguage("en");
        assertThat(deBcp47Folder).hasLanguage("de");

        copy.setDensityQualifier(new DensityQualifier(Density.HIGH));
        assertThat(copy).hasDensity(Density.HIGH);
        assertThat(deBcp47Folder.getDensityQualifier())
                .isEqualTo(new FolderConfiguration().getDensityQualifier());

        FolderConfiguration blankFolder = FolderConfiguration.getConfigForFolder("values");
        assertThat(blankFolder).isNotNull();
        copy = FolderConfiguration.copyOf(blankFolder);
        assertThat(copy).isMatchFor(blankFolder);

        copy.setVersionQualifier(new VersionQualifier(21));
        assertThat(copy).hasVersion(21);
        assertThat(blankFolder).hasNoVersion();
    }

    @Test
    public void screenSizeMatching() {
        runConfigMatchTest("normal-v21", 2, "", "v21", "normal", "large");
        runConfigMatchTest("normal-v21", 1, "", "v21", "small", "large");
        runConfigMatchTest("normal-v21", 0, "", "v23", "small", "large");
        runConfigMatchTest("large-v21", 3, "", "v21", "small", "large");
        runConfigMatchTest("large-v21", 1, "", "v21", "small", "xlarge");
        runConfigMatchTest("small-v21", 2, "", "v21", "small", "xlarge");
        runConfigMatchTest("small-v21", 1, "", "v21", "normal", "xlarge");
    }

    @Test
    public void testForEach() {
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-en-vrheadset");
        assertThat(config).isNotNull();
        List<Object> list = new ArrayList<>();
        config.forEach(list::add);
        assertThat(list.stream().map(Object::toString).collect(Collectors.toList()))
                .containsExactly("en", "vrheadset");
    }

    @Test
    public void anyPredicate() {
        FolderConfiguration config = FolderConfiguration.getConfigForFolder("values-vrheadset");
        assertThat(config).isNotNull();
        assertThat(config).hasUiMode(UiMode.VR_HEADSET);

        assertThat(config.any(qualifier -> qualifier instanceof UiModeQualifier)).isTrue();
        assertThat(config.any(qualifier -> qualifier instanceof DensityQualifier)).isFalse();
    }

    @Test
    public void languageConfigFromQualifiers() {
        assertThat(FolderConfiguration.getLanguageConfigFromQualifiers("en-rUS"))
                .containsExactly("en");

        assertThat(FolderConfiguration.getLanguageConfigFromQualifiers("en-rUS,fr-rFR"))
                .containsExactly("en", "fr");

        // qualifiers before and after, wrong WideGamut/HDR order (bug on O and P)
        assertThat(
                        FolderConfiguration.getLanguageConfigFromQualifiers(
                                "mcc310-mnc410-en-rUS,fr-rFR-ldltr-sw411dp-w411dp-h746dp-normal-long-notround-lowdr-nowidecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27"))
                .containsExactly("en", "fr");

        // qualifiers after only
        assertThat(
                        FolderConfiguration.getLanguageConfigFromQualifiers(
                                "en-rUS,fr-rFR-ldltr-sw411dp-w411dp-h746dp-normal-long-notround-lowdr-nowidecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27"))
                .containsExactly("en", "fr");

        // qualifiers before only
        assertThat(
                        FolderConfiguration.getLanguageConfigFromQualifiers(
                                "mcc310-mnc410-en-rUS,fr-rFR"))
                .containsExactly("en", "fr");

        // Correct order for WideGamut/HDR
        assertThat(
                        FolderConfiguration.getLanguageConfigFromQualifiers(
                                "en-rUS,fr-rFR-ldltr-sw411dp-w411dp-h746dp-normal-long-notround-nowidecg-lowdr-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27"))
                .containsExactly("en", "fr");

    }
}
