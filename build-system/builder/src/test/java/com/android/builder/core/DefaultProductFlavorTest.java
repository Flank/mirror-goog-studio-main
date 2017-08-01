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

package com.android.builder.core;

import com.android.builder.internal.ClassFieldImpl;
import com.android.builder.model.ClassField;
import com.android.builder.model.ProductFlavor;
import com.android.testutils.internal.CopyOfTester;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Map;
import junit.framework.TestCase;

public class DefaultProductFlavorTest extends TestCase {

    private DefaultProductFlavor defaultFlavor;
    private DefaultProductFlavor defaultFlavor2;
    private DefaultProductFlavor custom;
    private DefaultProductFlavor custom2;

    @Override
    protected void setUp() throws Exception {
        defaultFlavor = new DefaultProductFlavor("default");
        defaultFlavor2 = new DefaultProductFlavor("default2");

        custom = new DefaultProductFlavor("custom");
        custom.setMinSdkVersion(new DefaultApiVersion(42));
        custom.setTargetSdkVersion(new DefaultApiVersion(43));
        custom.setRenderscriptTargetApi(17);
        custom.setVersionCode(44);
        custom.setVersionName("42.0");
        custom.setApplicationId("com.forty.two");
        custom.setTestApplicationId("com.forty.two.test");
        custom.setTestInstrumentationRunner("com.forty.two.test.Runner");
        custom.setTestHandleProfiling(true);
        custom.setTestFunctionalTest(true);
        custom.addResourceConfiguration("hdpi");
        custom.addManifestPlaceholders(ImmutableMap.of("one", "oneValue", "two", "twoValue"));

        custom.addResValue(new ClassFieldImpl("foo", "one", "oneValue"));
        custom.addResValue(new ClassFieldImpl("foo", "two", "twoValue"));
        custom.addBuildConfigField(new ClassFieldImpl("foo", "one", "oneValue"));
        custom.addBuildConfigField(new ClassFieldImpl("foo", "two", "twoValue"));
        custom.setVersionNameSuffix("custom");
        custom.setApplicationIdSuffix("custom");

        custom2 = new DefaultProductFlavor("custom2");
        custom2.addResourceConfigurations("ldpi", "hdpi");
        custom2.addManifestPlaceholders(
                ImmutableMap.of("two", "twoValueBis", "three", "threeValue"));
        custom2.addResValue(new ClassFieldImpl("foo", "two", "twoValueBis"));
        custom2.addResValue(new ClassFieldImpl("foo", "three", "threeValue"));
        custom2.addBuildConfigField(new ClassFieldImpl("foo", "two", "twoValueBis"));
        custom2.addBuildConfigField(new ClassFieldImpl("foo", "three", "threeValue"));
        custom2.setApplicationIdSuffix("custom2");
        custom2.setVersionNameSuffix("custom2");
        custom2.setApplicationId("com.custom2.app");
    }

    public void testClone() {
        ProductFlavor flavor = DefaultProductFlavor.clone(custom);
        assertEquals(custom.toString(), flavor.toString());

        CopyOfTester.assertAllGettersCalled(
                DefaultProductFlavor.class, custom, DefaultProductFlavor::clone);
    }

    public void testMergeOnDefault() {
        ProductFlavor flavor =
                DefaultProductFlavor.mergeFlavors(defaultFlavor, ImmutableList.of(custom));

        assertNotNull(flavor.getMinSdkVersion());
        assertEquals(42, flavor.getMinSdkVersion().getApiLevel());
        assertNotNull(flavor.getTargetSdkVersion());
        assertEquals(43, flavor.getTargetSdkVersion().getApiLevel());
        assertNotNull(flavor.getRenderscriptTargetApi());
        assertEquals(17, flavor.getRenderscriptTargetApi().intValue());
        assertNotNull(flavor.getVersionCode());
        assertEquals(44, flavor.getVersionCode().intValue());
        assertEquals("42.0", flavor.getVersionName());
        assertEquals("com.forty.two", flavor.getApplicationId());
        assertEquals("com.forty.two.test", flavor.getTestApplicationId());
        assertEquals("com.forty.two.test.Runner", flavor.getTestInstrumentationRunner());
        assertEquals(Boolean.TRUE, flavor.getTestHandleProfiling());
        assertEquals(Boolean.TRUE, flavor.getTestFunctionalTest());
    }

    public void testMergeOnCustom() {
        ProductFlavor flavor =
                DefaultProductFlavor.mergeFlavors(defaultFlavor, ImmutableList.of(custom));

        assertNotNull(flavor.getMinSdkVersion());
        assertEquals(42, flavor.getMinSdkVersion().getApiLevel());
        assertNotNull(flavor.getTargetSdkVersion());
        assertEquals(43, flavor.getTargetSdkVersion().getApiLevel());
        assertNotNull(flavor.getRenderscriptTargetApi());
        assertEquals(17, flavor.getRenderscriptTargetApi().intValue());
        assertNotNull(flavor.getVersionCode());
        assertEquals(44, flavor.getVersionCode().intValue());
        assertEquals("42.0", flavor.getVersionName());
        assertEquals("com.forty.two", flavor.getApplicationId());
        assertEquals("com.forty.two.test", flavor.getTestApplicationId());
        assertEquals("com.forty.two.test.Runner", flavor.getTestInstrumentationRunner());
        assertEquals(Boolean.TRUE, flavor.getTestHandleProfiling());
        assertEquals(Boolean.TRUE, flavor.getTestFunctionalTest());
    }

    public void testMergeDefaultOnDefault() {
        ProductFlavor flavor =
                DefaultProductFlavor.mergeFlavors(defaultFlavor2, ImmutableList.of(defaultFlavor));

        assertNull(flavor.getMinSdkVersion());
        assertNull(flavor.getTargetSdkVersion());
        assertNull(flavor.getRenderscriptTargetApi());
        assertNull(flavor.getVersionCode());
        assertNull(flavor.getVersionName());
        assertNull(flavor.getApplicationId());
        assertNull(flavor.getTestApplicationId());
        assertNull(flavor.getTestInstrumentationRunner());
        assertNull(flavor.getTestHandleProfiling());
        assertNull(flavor.getTestFunctionalTest());
    }

    public void testResourceConfigMerge() {
        ProductFlavor flavor = DefaultProductFlavor.mergeFlavors(custom2, ImmutableList.of(custom));

        Collection<String> configs = flavor.getResourceConfigurations();
        assertEquals(2, configs.size());
        assertTrue(configs.contains("hdpi"));
        assertTrue(configs.contains("ldpi"));
    }

    public void testManifestPlaceholdersMerge() {
        ProductFlavor flavor = DefaultProductFlavor.mergeFlavors(custom2, ImmutableList.of(custom));

        Map<String, Object> manifestPlaceholders = flavor.getManifestPlaceholders();
        assertEquals(3, manifestPlaceholders.size());
        assertEquals("oneValue", manifestPlaceholders.get("one"));
        assertEquals("twoValue", manifestPlaceholders.get("two"));
        assertEquals("threeValue", manifestPlaceholders.get("three"));

    }

    public void testResValuesMerge() {
        ProductFlavor flavor = DefaultProductFlavor.mergeFlavors(custom2, ImmutableList.of(custom));

        Map<String, ClassField> resValues = flavor.getResValues();
        assertEquals(3, resValues.size());
        assertEquals("oneValue", resValues.get("one").getValue());
        assertEquals("twoValue", resValues.get("two").getValue());
        assertEquals("threeValue", resValues.get("three").getValue());
    }

    public void testBuildConfigFieldMerge() {
        ProductFlavor flavor = DefaultProductFlavor.mergeFlavors(custom2, ImmutableList.of(custom));

        Map<String, ClassField> buildConfigFields = flavor.getBuildConfigFields();
        assertEquals(3, buildConfigFields.size());
        assertEquals("oneValue", buildConfigFields.get("one").getValue());
        assertEquals("twoValue", buildConfigFields.get("two").getValue());
        assertEquals("threeValue", buildConfigFields.get("three").getValue());
    }

    public void testMergeMultiple() {
        DefaultProductFlavor custom3 = new DefaultProductFlavor("custom3");
        custom3.setMinSdkVersion(new DefaultApiVersion(102));
        custom3.setApplicationIdSuffix("custom3");
        custom3.setVersionNameSuffix("custom3");

        ProductFlavor flavor =
                DefaultProductFlavor.mergeFlavors(custom, ImmutableList.of(custom3, custom2));

        assertEquals(flavor.getMinSdkVersion(), new DefaultApiVersion(102));
        assertEquals("customcustom3custom2", flavor.getVersionNameSuffix());
        assertEquals(flavor.getApplicationIdSuffix(), "custom.custom3.custom2");
    }

    public void testSecondDimensionOverwritesDefault() {
        DefaultProductFlavor custom3 = new DefaultProductFlavor("custom3");
        custom3.setMinSdkVersion(new DefaultApiVersion(102));

        ProductFlavor flavor =
                DefaultProductFlavor.mergeFlavors(custom, ImmutableList.of(custom3, custom2));

        assertEquals(flavor.getMinSdkVersion(), new DefaultApiVersion(102));
        assertEquals(flavor.getApplicationId(), "com.custom2.app");
        assertEquals("customcustom2", flavor.getVersionNameSuffix());
        assertEquals(flavor.getApplicationIdSuffix(), "custom.custom2");
    }
}
