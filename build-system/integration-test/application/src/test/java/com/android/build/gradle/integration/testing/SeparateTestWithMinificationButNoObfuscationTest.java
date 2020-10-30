package com.android.build.gradle.integration.testing;

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.options.OptionalBooleanOption;
import com.android.builder.model.CodeShrinker;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test for a separate test module that has minification turned on but no obfuscation (no
 * mapping.txt file produced)
 */
@RunWith(FilterableParameterized.class)
public class SeparateTestWithMinificationButNoObfuscationTest {

    @Parameterized.Parameters(name = "codeShrinker = {0}")
    public static CodeShrinker[] getShrinkers() {
        return new CodeShrinker[] {CodeShrinker.PROGUARD, CodeShrinker.R8};
    }

    @Parameterized.Parameter public CodeShrinker codeShrinker;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    // http://b/149978740
                    .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
                    .fromTestProject("separateTestWithMinificationButNoObfuscation")
                    .create();

    @Test
    public void testBuilding() throws IOException, InterruptedException {
        // just building fine is enough to test the regression.
        project.execute("clean");
        project.executor()
                .with(
                        OptionalBooleanOption.INTERNAL_ONLY_ENABLE_R8,
                        codeShrinker == CodeShrinker.R8)
                .run("assemble");
    }
}
