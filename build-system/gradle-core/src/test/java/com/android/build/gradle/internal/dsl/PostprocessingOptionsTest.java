package com.android.build.gradle.internal.dsl;

import com.android.testutils.internal.CopyOfTester;
import org.junit.Test;

public class PostprocessingOptionsTest {

    @Test
    public void testInitWith() {
        CopyOfTester.assertAllGettersCalled(
                PostprocessingOptions.class,
                new PostprocessingOptions(),
                original -> {
                    PostprocessingOptions copy = new PostprocessingOptions();
                    copy.initWith(original);

                    // Explicitly copy the String getter.
                    original.getCodeShrinker();
                });
    }
}
