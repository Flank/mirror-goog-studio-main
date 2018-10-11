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

package com.android.testutils.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import com.android.annotations.NonNull;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.mockito.internal.util.reflection.LenientCopyTool;
import org.mockito.stubbing.Answer;

/** Utility to test "copyOf" and "initWith" methods. */
public class CopyOfTester {

    private static final Pattern GETTER_NAME = Pattern.compile("^(get|is)[A-Z].*");

    private static final Predicate<Method> IS_GETTER =
            m -> GETTER_NAME.matcher(m.getName()).matches();

    /**
     * Checks that all getters declared in the given class (and superclasses) are called when the
     * copying code is invoked.
     *
     * <p>This way it ensures that copyOf/initWith method is performed correctly. This is based on
     * the following assumptions:
     *
     * <ul>
     *   <li>In the initWith method class copying is supposed to be performed with getters ONLY
     *   <li>If you add a new field and a getter for it, you might still forget to add the
     *       corresponding copy operation to the initWith
     * </ul>
     *
     * <p>Thus, this test will fail and you will be notified of the getter that was not called
     */
    public static <T> void assertAllGettersCalled(
            @NonNull Class<T> klass, @NonNull T object, @NonNull Consumer<T> copyingCode) {
        // We're keeping track of names, and not Method instances to handle interface
        // implementations  that change the return type to be more specific. In such case
        // klass.getMethods() returns both methods, but the overridden one cannot be invoked.
        Set<String> allGetters =
                Arrays.stream(klass.getMethods())
                        .filter(IS_GETTER)
                        .filter(method -> method.getDeclaringClass() != Object.class)
                        .map(Method::getName)
                        .collect(Collectors.toSet());
        assertThat(allGetters).named("getters declared in " + klass.getName()).isNotEmpty();

        Set<String> gettersCalled = new HashSet<>();

        // The code below seems to be the only way to create a "spy" (object with all state copied
        // from the original) with a custom Answer.
        Answer recordGetters =
                invocation -> {
                    Method method = invocation.getMethod();
                    if (GETTER_NAME.matcher(method.getName()).matches()) {
                        gettersCalled.add(method.getName());
                    }
                    return invocation.callRealMethod();
                };
        T mock = mock(klass, recordGetters);
        new LenientCopyTool().copyToMock(object, mock);

        copyingCode.accept(mock);

        assertThat(gettersCalled).named("getters called").containsExactlyElementsIn(allGetters);
    }
}
