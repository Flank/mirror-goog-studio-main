package com.android.build.gradle.internal.dsl;

import com.android.testutils.internal.CopyOfTester;
import java.util.Collections;
import org.gradle.api.Project;
import org.junit.Test;
import org.mockito.Mockito;

public class PostprocessingOptionsTest {

    @Test
    public void testInitWith() {
        Project mockProject = Mockito.mock(Project.class);

        CopyOfTester.assertAllGettersCalled(
                PostprocessingOptions.class,
                new PostprocessingOptions(mockProject, Collections.emptyList()),
                original -> {
                    PostprocessingOptions copy =
                            new PostprocessingOptions(mockProject, Collections.emptyList());
                    copy.initWith(original);

                    // Explicitly copy the String getter.
                    original.getCodeShrinker();
                });
    }
}
