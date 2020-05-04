/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.deployer.rules;

import com.android.tools.deployer.devices.DeviceId;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Suite;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

public class ApiLevel extends Suite {

    /**
     * Annotation that tells the {@link ApiLevel} test runner to only run the test method with API
     * levels in the specified range, inclusive.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    public @interface InRange {
        int min() default Integer.MIN_VALUE;
        int max() default Integer.MAX_VALUE;
    }

    /**
     * Annotation that indicates that the annotated field should be initialized before each test
     * method runs. The field must be of a type with a single-argument constructor, which will be
     * passed the current API level in the form of a{@link DeviceId}.
     *
     * Intended for use with {@link DashboardBenchmark} and {@link FakeDeviceConnection}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public @interface Init {}

    private final List<Runner> runners;

    public ApiLevel(Class<?> klass) throws Throwable {
        super(klass, Collections.emptyList());
        runners = Collections.unmodifiableList(createRunnersForApiLevels());
    }

    @Override
    protected List<Runner> getChildren() {
        return runners;
    }

    private List<Runner> createRunnersForApiLevels()
            throws InitializationError, NoTestsRemainException {
        List<Runner> runners = new ArrayList<>();
        for (DeviceId deviceId : DeviceId.values()) {
            runners.add(createFilteredRunner(deviceId));
        }
        return runners;
    }

    private Runner createFilteredRunner(DeviceId deviceId)
            throws InitializationError, NoTestsRemainException {
        ApiLevelRunner runner = new ApiLevelRunner(deviceId, getTestClass().getJavaClass());
        runner.filter(new ApiLevelFilter(deviceId));
        return runner;
    }

    /** Test runner that initializes all test class fields annotated with {@link Init}. */
    static class ApiLevelRunner extends BlockJUnit4ClassRunner {
        private DeviceId deviceId;

        ApiLevelRunner(DeviceId deviceId, Class<?> testClass) throws InitializationError {
            super(testClass);
            this.deviceId = deviceId;
        }

        @Override
        public Object createTest() throws Exception {
            Object testClassInstance = super.createTest();
            List<FrameworkField> annotatedFieldsByParameter =
                    getTestClass().getAnnotatedFields(Init.class);
            for (FrameworkField field : annotatedFieldsByParameter) {
                initializeField(testClassInstance, field);
            }
            return testClassInstance;
        }

        @Override
        protected String getName() {
            return String.format("[%s]", deviceId);
        }

        @Override
        protected void validateFields(List<Throwable> errors) {
            super.validateFields(errors);
            List<FrameworkField> annotatedFields =
                    getTestClass().getAnnotatedFields(Init.class);
            for (FrameworkField field : annotatedFields) {
                if (field.getType() == DeviceId.class) {
                    continue;
                }
                try {
                    field.getType().getConstructor(DeviceId.class);
                } catch (NoSuchMethodException ex) {
                    errors.add(
                            new Exception(
                                    "Fields annotated with @ApiLevel.Initialize must either have a "
                                    + "constructor taking a single DeviceId parameter or be of "
                                    + "type DeviceId"));
                }
            }
        }

        @Override
        protected Annotation[] getRunnerAnnotations() {
            return new Annotation[0];
        }

        @Override
        protected Statement classBlock(RunNotifier notifier) {
            return childrenInvoker(notifier);
        }

        private void initializeField(Object testClassInstance, FrameworkField field) throws Exception {
            Object value;
            if (field.getType() == DeviceId.class) {
                value = deviceId;
            } else {
                Constructor<?> constructor = field.getType().getConstructor(DeviceId.class);
                value = constructor.newInstance(deviceId);
            }
            field.getField().set(testClassInstance, value);
        }
    }

    /** Test filter that filters out test methods based on the current device API. */
    static class ApiLevelFilter extends Filter {
        private final DeviceId deviceId;

        ApiLevelFilter(DeviceId deviceId) {
            this.deviceId = deviceId;
        }

        @Override
        public boolean shouldRun(Description description) {
            int minApi = Integer.MIN_VALUE;
            int maxApi = Integer.MAX_VALUE;
            InRange inRange = description.getAnnotation(InRange.class);
            if (inRange != null) {
                minApi = Math.max(inRange.min(), minApi);
                maxApi = Math.min(inRange.max(), maxApi);
            }
            return deviceId.api() >= minApi && deviceId.api() <= maxApi;
        }

        @Override
        public String describe() {
            return deviceId.name();
        }
    }
}
