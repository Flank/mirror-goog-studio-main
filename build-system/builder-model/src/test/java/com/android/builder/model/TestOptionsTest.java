package com.android.builder.model;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;

public class TestOptionsTest {

    @Test
    public void executionEnumNames() throws Exception {
        Set<String> valuesNames =
                Arrays.stream(TestOptions.Execution.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet());

        // These values are already used in the IDE, so cannot be renamed.
        Assert.assertTrue(valuesNames.contains("ANDROID_TEST_ORCHESTRATOR"));
    }
}
