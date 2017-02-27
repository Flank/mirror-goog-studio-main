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

package com.android.ide.common.build;

import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.builder.testing.api.DeviceConfigProvider;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.resources.Density;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;
import org.mockito.Mockito;

public class SplitOutputMatcherTest extends TestCase {

    /** Helper to run InstallHelper.computeMatchingOutput with variable ABI list. */
    private static List<File> computeBestOutput(
            @NonNull List<OutputFile> outputs, int deviceDensity, @NonNull String... deviceAbis)
            throws ProcessException {
        DeviceConfigProvider deviceConfigProvider = Mockito.mock(DeviceConfigProvider.class);
        when(deviceConfigProvider.getDensity()).thenReturn(deviceDensity);
        when(deviceConfigProvider.getAbis()).thenReturn(Arrays.asList(deviceAbis));
        return SplitOutputMatcher.computeBestOutput(
                Mockito.mock(ProcessExecutor.class),
                null /* splitSelectExe */,
                deviceConfigProvider,
                outputs,
                null /* variantAbiFilters */);
    }

    private static List<File> computeBestOutput(
            @NonNull List<OutputFile> outputs,
            @NonNull Set<String> deviceAbis,
            int deviceDensity,
            @NonNull String... variantAbiFilters)
            throws ProcessException {
        DeviceConfigProvider deviceConfigProvider = Mockito.mock(DeviceConfigProvider.class);
        when(deviceConfigProvider.getDensity()).thenReturn(deviceDensity);
        when(deviceConfigProvider.getAbis()).thenReturn(new ArrayList<String>(deviceAbis));
        return SplitOutputMatcher.computeBestOutput(
                Mockito.mock(ProcessExecutor.class),
                null /* splitSelectExec */,
                deviceConfigProvider,
                outputs,
                Arrays.asList(variantAbiFilters));
    }

    /**
     * Fake implementation of FilteredOutput
     */

    private static final class FakeSplitOutput implements OutputFile {

        private final String densityFilter;
        private final String abiFilter;
        private final File file;
        private final int versionCode;

        FakeSplitOutput(String densityFilter, String abiFilter, int versionCode) {
            this.densityFilter = densityFilter;
            this.abiFilter = abiFilter;
            file = new File(densityFilter + abiFilter);
            this.versionCode = versionCode;
        }

        FakeSplitOutput(String densityFilter, String abiFilter, File file, int versionCode) {
            this.densityFilter = densityFilter;
            this.abiFilter = abiFilter;
            this.file = file;
            this.versionCode = versionCode;
        }

        @NonNull
        @Override
        public OutputFile getMainOutputFile() {
            return this;
        }

        @NonNull
        @Override
        public Collection<? extends OutputFile> getOutputs() {
            return ImmutableList.of(this);
        }

        @Override
        public int getVersionCode() {
            return versionCode;
        }

        @NonNull
        @Override
        public String getOutputType() {
            return OutputFile.FULL_SPLIT;
        }

        @NonNull
        @Override
        public Collection<String> getFilterTypes() {
            ImmutableList.Builder<String> splitTypeBuilder = ImmutableList.builder();
            if (densityFilter != null) {
                splitTypeBuilder.add(OutputFile.DENSITY);
            }
            if (abiFilter != null) {
                splitTypeBuilder.add(OutputFile.ABI);
            }
            return splitTypeBuilder.build();
        }

        @NonNull
        @Override
        public Collection<FilterData> getFilters() {
            ImmutableList.Builder<FilterData> filters = ImmutableList.builder();
            if (densityFilter != null) {
                filters.add(FakeFilterData.Builder.build(OutputFile.DENSITY, densityFilter));
            }
            if (abiFilter != null) {
                filters.add(FakeFilterData.Builder.build(OutputFile.ABI, abiFilter));
            }
            return filters.build();
        }

        @NonNull
        @Override
        public File getOutputFile() {
            return file;
        }

        @Override
        public String toString() {
            return "FilteredOutput{" + densityFilter + ':' + abiFilter + '}';
        }
    }

    private static final class FakeFilterData implements FilterData {
        private final String filterType;
        private final String identifier;

        FakeFilterData(String filterType, String identifier) {
            this.filterType = filterType;
            this.identifier = identifier;
        }

        @NonNull
        @Override
        public String getIdentifier() {
            return identifier;
        }

        @NonNull
        @Override
        public String getFilterType() {
            return filterType;
        }

        public static class Builder {
            public static FilterData build(final String filterType, final String identifier) {
                return new FakeFilterData(filterType, identifier);
            }
        }
    }

    public void testSingleOutput() throws ProcessException {
        OutputFile match;
        List<OutputFile> list = Lists.newArrayList();

        list.add(match = getUniversalOutput(1));

        List<File> result = computeBestOutput(list, 160, "foo");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testDensityOnlyWithMatch() throws ProcessException {
        OutputFile match;
        List<OutputFile> list = Lists.newArrayList();

        list.add(getUniversalOutput(1));
        list.add(match = getDensityOutput(160, 2));
        list.add(getDensityOutput(320, 3));

        List<File> result =  computeBestOutput(list, 160, "foo");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testDensityOnlyWithUniversalMatch() throws ProcessException {
        OutputFile match;
        List<OutputFile> list = Lists.newArrayList();

        list.add(match = getUniversalOutput(3));
        list.add(getDensityOutput(320, 2));
        list.add(getDensityOutput(480, 1));

        List<File> result = computeBestOutput(list, 160, "foo");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testDensityOnlyWithNoMatch() throws ProcessException {
        List<OutputFile> list = Lists.newArrayList();

        list.add(getDensityOutput(320, 1));
        list.add(getDensityOutput(480, 2));

        List<File> result = computeBestOutput(list, 160, "foo");

        assertEquals(0, result.size());
    }

    public void testDensityOnlyWithCustomDeviceDensity() throws ProcessException {
        OutputFile match;
        List<OutputFile> list = Lists.newArrayList();

        list.add(match = getUniversalOutput(1));
        list.add(getDensityOutput(320, 2));
        list.add(getDensityOutput(480, 3));

        List<File> result = computeBestOutput(list, 1, "foo");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }


    public void testAbiOnlyWithMatch() throws ProcessException {
        OutputFile match;
        List<OutputFile> list = Lists.newArrayList();

        list.add(getUniversalOutput(1));
        list.add(match = getAbiOutput("foo", 2));
        list.add(getAbiOutput("bar", 3));

        List<File> result = computeBestOutput(list, 160, "foo");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testAbiOnlyWithMultiMatch() throws ProcessException {
        OutputFile match;
        List<OutputFile> list = Lists.newArrayList();

        // test where the versionCode match the abi order
        list.add(getUniversalOutput(1));
        list.add(getAbiOutput("foo", 2));
        list.add(match = getAbiOutput("bar", 3));

        // bar is preferred over foo
        List<File> result = computeBestOutput(list, 160, "bar", "foo");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testAbiPreference() throws ProcessException {
        OutputFile match;
        List<OutputFile> list = Lists.newArrayList();

        // test where the versionCode match the abi order
        list.add(getUniversalOutput(1));
        list.add(getAbiOutput("foo", 1));
        list.add(match = getAbiOutput("bar", 1, "bar1"));
        list.add(getAbiOutput("bar", 1, "bar2"));

        // bar is preferred over foo
        List<File> result = computeBestOutput(list, 160, "bar", "foo");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testAbiPreferenceForUniveralApk() throws ProcessException {
        OutputFile match;
        List<OutputFile> list = Lists.newArrayList();

        // test where the versionCode match the abi order
        list.add(match = getUniversalOutput(1));
        list.add(getAbiOutput("foo", 1));
        list.add(getAbiOutput("foo", 1));
        list.add(getAbiOutput("foo", 1));

        // bar is preferred over foo
        List<File> result = computeBestOutput(list, 160, "bar", "foo");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testAbiOnlyWithMultiMatch2() throws ProcessException {
        OutputFile match;
        List<OutputFile> list = Lists.newArrayList();

        // test where the versionCode does not match the abi order
        list.add(getUniversalOutput(1));
        list.add(getAbiOutput("foo", 2));
        list.add(match = getAbiOutput("bar", 3));

        // bar is preferred over foo
        List<File> result = computeBestOutput(list, 160, "foo", "bar");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testAbiOnlyWithUniversalMatch() throws ProcessException {
        OutputFile match;
        List<OutputFile> list = Lists.newArrayList();

        list.add(match = getUniversalOutput(1));
        list.add(getAbiOutput("foo", 2));
        list.add(getAbiOutput("bar", 3));

        List<File> result = computeBestOutput(list, 160, "zzz");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testAbiOnlyWithNoMatch() throws ProcessException {
        List<OutputFile> list = Lists.newArrayList();

        list.add(getAbiOutput("foo", 1));
        list.add(getAbiOutput("bar", 2));

        List<File> result = computeBestOutput(list, 160, "zzz");

        assertEquals(0, result.size());
    }

    public void testMultiFilterWithMatch() throws ProcessException {
        OutputFile match;
        List<OutputFile> list = Lists.newArrayList();

        list.add(getUniversalOutput(1));
        list.add(getOutput(160, "zzz",2));
        list.add(match = getOutput(160, "foo", 4));
        list.add(getOutput(320, "foo", 3));

        List<File> result = computeBestOutput(list, 160, "foo");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testMultiFilterWithUniversalMatch() throws ProcessException {
        OutputFile match;
        List<OutputFile> list = Lists.newArrayList();

        list.add(match = getUniversalOutput(4));
        list.add(getOutput(320, "zzz", 3));
        list.add(getOutput(160, "bar", 2));
        list.add(getOutput(320, "foo", 1));

        List<File> result = computeBestOutput(list, 160, "zzz");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testMultiFilterWithNoMatch() throws ProcessException {
        List<OutputFile> list = Lists.newArrayList();

        list.add(getOutput(320, "zzz", 1));
        list.add(getOutput(160, "bar", 2));
        list.add(getOutput(320, "foo", 3));

        List<File> result = computeBestOutput(list, 160, "zzz");

        assertEquals(0, result.size());
    }

    public void testVariantLevelAbiFilter() throws ProcessException {
        OutputFile match;
        List<OutputFile> list = Lists.newArrayList();

        list.add(match = getUniversalOutput(1));
        List<File> result = computeBestOutput(list, Sets.newHashSet("bar", "foo"), 160, "foo",
                "zzz");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testWrongVariantLevelAbiFilter() throws ProcessException {
        List<OutputFile> list = Lists.newArrayList();

        list.add(getUniversalOutput(1));

        List<File> result = computeBestOutput(list, Sets.newHashSet("bar", "foo"), 160, "zzz");

        assertEquals(0, result.size());
    }

    public void testDensitySplitPlugVariantLevelAbiFilter() throws ProcessException {
        OutputFile match;
        List<OutputFile> list = Lists.newArrayList();

        list.add(getUniversalOutput(1));
        list.add(getDensityOutput(240, 2));
        list.add(match = getDensityOutput(320, 3));
        list.add(getDensityOutput(480, 4));

        List<File> result = computeBestOutput(list, Sets.newHashSet("bar", "foo"), 320, "foo", "zzz");

        assertEquals(1, result.size());
    }

    public void testConfigFormatFixed() {
        String mccMnc =
                "310mcc-260mnc-en-rUS-ldltr-sw320dp-w320dp-h508dp-normal-long-port-"
                        + "notnight-hdpi-finger-keysexposed-nokeys-navexposed-trackball-v21:x86";
        String expectedMccMnc = "mcc310-mnc260-en-rUS-ldltr-sw320dp-w320dp-h508dp-normal-long-port-"
                + "notnight-hdpi-finger-keysexposed-nokeys-navexposed-trackball-v21:x86";
        assertEquals(expectedMccMnc, SplitOutputMatcher.prepareConfigFormatMccMnc(mccMnc));

        String mnc =
                "260mnc-en-rUS-ldltr-sw320dp-w320dp-h508dp-normal-long-port-"
                        + "notnight-hdpi-finger-keysexposed-nokeys-navexposed-trackball-v21:x86";
        String expectedMnc = "mnc260-en-rUS-ldltr-sw320dp-w320dp-h508dp-normal-long-port-"
                + "notnight-hdpi-finger-keysexposed-nokeys-navexposed-trackball-v21:x86";
        assertEquals(expectedMnc, SplitOutputMatcher.prepareConfigFormatMccMnc(mnc));

        String mcc =
                "310mcc-en-rUS-ldltr-sw320dp-w320dp-h508dp-normal-long-port-"
                        + "notnight-hdpi-finger-keysexposed-nokeys-navexposed-trackball-v21:x86";
        String expectedMcc = "mcc310-en-rUS-ldltr-sw320dp-w320dp-h508dp-normal-long-port-"
                + "notnight-hdpi-finger-keysexposed-nokeys-navexposed-trackball-v21:x86";
        assertEquals(expectedMcc, SplitOutputMatcher.prepareConfigFormatMccMnc(mcc));

        String noMccMnc =
                "en-rUS-ldltr-sw320dp-w320dp-h508dp-normal-long-port-"
                        + "notnight-hdpi-finger-keysexposed-nokeys-navexposed-trackball-v21:x86";
        String expectedNoMccMnc = "en-rUS-ldltr-sw320dp-w320dp-h508dp-normal-long-port-"
                + "notnight-hdpi-finger-keysexposed-nokeys-navexposed-trackball-v21:x86";
        assertEquals(expectedNoMccMnc, SplitOutputMatcher.prepareConfigFormatMccMnc(noMccMnc));
    }

    private static OutputFile getUniversalOutput(int versionCode) {
        return new FakeSplitOutput(null, null, versionCode);
    }

    private static OutputFile getDensityOutput(int densityFilter, int versionCode) {
        Density densityEnum = Density.getEnum(densityFilter);
        return new FakeSplitOutput(densityEnum.getResourceValue(), null, versionCode);
    }

    private static OutputFile getAbiOutput(String filter, int versionCode) {
        return new FakeSplitOutput(null, filter, versionCode);
    }

    private static OutputFile getAbiOutput(String filter, int versionCode, String file) {
        return new FakeSplitOutput(null, filter, new File(file), versionCode);
    }

    private static OutputFile getOutput(int densityFilter, String abiFilter, int versionCode) {
        Density densityEnum = Density.getEnum(densityFilter);
        return new FakeSplitOutput(densityEnum.getResourceValue(), abiFilter, versionCode);
    }
}
