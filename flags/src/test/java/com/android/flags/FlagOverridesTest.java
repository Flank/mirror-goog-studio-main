package com.android.flags;

import static com.google.common.truth.Truth.assertThat;

import com.android.flags.overrides.DefaultFlagOverrides;
import org.junit.Test;

public class FlagOverridesTest {
    @Test
    public void testAddingAndRemovingOverrides() throws Exception {
        FlagOverrides flagOverrides = new DefaultFlagOverrides();

        Flags flags = new Flags(flagOverrides);
        FlagGroup group = new FlagGroup(flags, "test", "Dummy");
        Flag<String> flagA = Flag.create(group, "a", "Dummy", "Dummy", "A");
        Flag<String> flagB = Flag.create(group, "b", "Dummy", "Dummy", "B");
        Flag<String> flagC = Flag.create(group, "c", "Dummy", "Dummy", "C");
        Flag<String> flagD = Flag.create(group, "d", "Dummy", "Dummy", "D");

        flagOverrides.put(flagA, "a");
        flagOverrides.put(flagB, "b");
        flagOverrides.put(flagC, "d");
        flagOverrides.put(flagC, "c");

        assertThat(flagOverrides.get(flagA)).isEqualTo("a");
        assertThat(flagOverrides.get(flagB)).isEqualTo("b");
        assertThat(flagOverrides.get(flagC)).isEqualTo("c");
        assertThat(flagOverrides.get(flagD)).isNull();

        flagOverrides.remove(flagB);
        assertThat(flagOverrides.get(flagB)).isNull();
    }
}
