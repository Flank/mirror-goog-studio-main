/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.testutils;

import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

public class ClassSuiteRunner extends Suite {

    public ClassSuiteRunner(Class<?> suiteClass, RunnerBuilder builder)
            throws InitializationError, ClassNotFoundException {
        super(builder, suiteClass, getTestClasses(suiteClass));
    }

    private static Class<?>[] getTestClasses(Class<?> suiteClass) throws ClassNotFoundException {
        String name = System.getProperty("test.suite.class");
        Class<?> testClass = suiteClass.getClassLoader().loadClass(name);
        return new Class[] {testClass};
    }
}
