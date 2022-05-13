/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.instrumentation.threading.agent.callback;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Contains a list of methods annotated with threading annotations that should not be checked for
 * threading violations.
 */
public class BaselineViolations {

    private static final Logger LOGGER = Logger.getLogger(BaselineViolations.class.getName());

    private final Set<String> violatingMethods;

    private BaselineViolations(Set<String> violatingMethods) {
        this.violatingMethods = violatingMethods;
    }

    /**
     * Checks if the method should not be checked for threading violations.
     *
     * @param method should be specified as FullClassName[$InternalClassName]#MethodName. For
     *     example: com.android.tools.SomeClass#doSomething,
     *     com.android.tools.SomeClass$InnerClass#doSomethingElse
     */
    public boolean isIgnored(String method) {
        return violatingMethods.contains(method);
    }

    static BaselineViolations fromResource() {
        try (InputStream stream =
                BaselineViolations.class.getResourceAsStream("/baseline_violations.txt")) {
            return fromStream(stream);
        } catch (IOException e) {
            LOGGER.severe("Couldn't read baseline violations file, " + e);
            LOGGER.info("Will skip all baseline violation checks");
            return new BaselineViolations(Collections.emptySet());
        }
    }

    static BaselineViolations fromStream(InputStream stream) {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
        return new BaselineViolations(
                bufferedReader
                        .lines()
                        .map(String::trim)
                        .filter(l -> !l.startsWith("#") && !l.isEmpty())
                        .collect(Collectors.toSet()));
    }
}
