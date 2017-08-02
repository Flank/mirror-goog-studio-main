/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.TestOptions.Execution;
import com.android.utils.HelpfulEnumConverter;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;
/** Options for running tests. */
@SuppressWarnings("unused") // Exposed in the DSL.
public class TestOptions {
    private static final HelpfulEnumConverter<Execution> EXECUTION_CONVERTER =
            new HelpfulEnumConverter<>(Execution.class);

    @Nullable private String resultsDir;

    @Nullable private String reportDir;

    private boolean animationsDisabled;

    @NonNull private Execution execution = Execution.HOST;

    /**
     * Options for controlling unit tests execution.
     *
     * @since 1.1
     */
    @NonNull private final UnitTestOptions unitTests;

    public TestOptions(Instantiator instantiator) {
        this.unitTests = instantiator.newInstance(UnitTestOptions.class);
    }

    /**
     * Configures unit test options.
     *
     * @since 1.2
     */
    public void unitTests(Closure closure) {
        ConfigureUtil.configure(closure, unitTests);
    }

    /**
     * Configures unit test options.
     *
     * @since 1.2
     */
    @NonNull
    public UnitTestOptions getUnitTests() {
        return unitTests;
    }

    /** Name of the results directory. */
    @Nullable
    public String getResultsDir() {
        return resultsDir;
    }

    public void setResultsDir(@Nullable String resultsDir) {
        this.resultsDir = resultsDir;
    }

    /** Name of the reports directory. */
    @Nullable
    public String getReportDir() {
        return reportDir;
    }

    public void setReportDir(@Nullable String reportDir) {
        this.reportDir = reportDir;
    }

    /** Disables animations during instrumented tests. */
    public boolean getAnimationsDisabled() {
        return animationsDisabled;
    }

    public void setAnimationsDisabled(boolean animationsDisabled) {
        this.animationsDisabled = animationsDisabled;
    }

    @NonNull
    public String getExecution() {
        return Verify.verifyNotNull(
                EXECUTION_CONVERTER.reverse().convert(execution),
                "No string representation for enum.");
    }

    @NonNull
    public Execution getExecutionEnum() {
        return execution;
    }

    public void setExecution(@NonNull String execution) {
        this.execution =
                Preconditions.checkNotNull(
                        EXECUTION_CONVERTER.convert(execution),
                        "The value of `execution` cannot be null.");
    }

    /** Options for controlling unit tests execution. */
    public static class UnitTestOptions {
        // Used by testTasks.all below, DSL docs generator can't handle diamond operator.
        @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection", "Convert2Diamond"})
        private DomainObjectSet<Test> testTasks = new DefaultDomainObjectSet<Test>(Test.class);

        private boolean returnDefaultValues;
        private boolean includeAndroidResources;

        /**
         * Whether unmocked methods from android.jar should throw exceptions or return default
         * values (i.e. zero or null).
         *
         * <p>See <a href="https://developer.android.com/studio/test/index.html">Test Your App</a>
         * for details.
         *
         * @since 1.1
         */
        public boolean isReturnDefaultValues() {
            return returnDefaultValues;
        }

        public void setReturnDefaultValues(boolean returnDefaultValues) {
            this.returnDefaultValues = returnDefaultValues;
        }

        /**
         * Whether Android resources, assets and manifests are used by unit tests.
         *
         * <p>If set, Android resources, assets and manifest merging will be done before unit tests
         * are run. When enabled, tests can look for a file called {@code
         * com/android/tools/test_config.properties} on the classpath. This will be a java
         * properties file, with the following keys:
         *
         * <ul>
         *   <li>{@code android_merged_assets} - absolute path to the merged assets directory.
         *   <li>{@code android_merged_manifest} - absolute path to the merged manifest file.
         *   <li>{@code android_merged_resources} - absolute path to the merged resources directory.
         *   <li>{@code android_sdk_resources} - absolute path to the resources of the compile SDK.
         * </ul>
         *
         * @since 2.4
         */
        public boolean isIncludeAndroidResources() {
            return includeAndroidResources;
        }

        public void setIncludeAndroidResources(boolean includeAndroidResources) {
            this.includeAndroidResources = includeAndroidResources;
        }

        /**
         * Configures all unit testing tasks.
         *
         * <p>See {@link Test} for available options.
         *
         * <p>Inside the closure you can check the name of the task to configure only some test
         * tasks, e.g.
         *
         * <pre>
         * android {
         *     testOptions {
         *         unitTests.all {
         *             if (it.name == 'testDebug') {
         *                 systemProperty 'debug', 'true'
         *             }
         *         }
         *     }
         * }
         * </pre>
         *
         * @since 1.2
         */
        public void all(final Closure<Test> configClosure) {
            //noinspection Convert2Lambda - DSL docs generator can't handle lambdas.
            testTasks.all(
                    new Action<Test>() {
                        @Override
                        public void execute(Test testTask) {
                            ConfigureUtil.configure(configClosure, testTask);
                        }
                    });
        }

        /**
         * Configures a given test task. The configuration closures that were passed to {@link
         * #all(Closure)} will be applied to it.
         *
         * <p>Not meant to be called from build scripts. The reason it exists is that tasks are
         * created after the build scripts are evaluated, so users have to "register" their
         * configuration closures first and we can only apply them later.
         *
         * @since 1.2
         */
        public void applyConfiguration(@NonNull Test task) {
            this.testTasks.add(task);
        }
    }
}
