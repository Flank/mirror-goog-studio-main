/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.resources;

import com.android.annotations.NonNull;

/**
 * An enum representing accessibility of an android resource.
 *
 * <p>Public accessibility.
 *
 * <p>A resource can be marked as public by adding the {@code public} element to a values XML file:
 *
 * <p>{@code <public name="my_public_string" type="string"/>}.
 *
 * <p>These elements are usually defined in the {@code public.xml} file inside the {@code
 * res/values} directory.
 *
 * <p>Private accessibility.
 *
 * <p>Sometimes a libraries can have a great number of resources and it might be confusing or
 * tiresome to find the correct one when writing the Java or Kotlin code. In order to restrict the
 * resources visible from the source code, one can list the only resources they want to be visible
 * by using the {@code java-symbol} element:
 *
 * <p>{@code <java-symbol name="my_visible_string" type="string/>}.
 *
 * <p>These elements are usually defined in the {@code symbols.xml} file inside the {@code
 * res/values} directory. The name {@code private} comes from these resources being present in the
 * {@code private R.java} along with {@code public} resources.
 *
 * <p>Default accessibility.
 *
 * <p>All resources that were not marked as {@code public} or {@code private} have the {@code
 * default} accessibility.
 *
 * <p>Usage.
 *
 * <p>In order to generate a public R.java containing only the public resources and a private R.java
 * containing both public and private resources, specify the package for the private R.java in the
 * {@code build.gradle} file. For example, if the application's package (or the custom package) is
 * {@code com.foo.bar} you could use {@code com.foo.bar.symbols} for the private R.java:
 *
 * <pre>
 *     android {
 *         ...
 *         aaptOptions {
 *             privateRDotJavaPackage "com.foo.bar.symbols"
 *         }
 *     }
 * </pre>
 *
 * <p>Without the package for the private R.java specified, only the public R.java will be generated
 * and it will contain all resources (ones marked as public, java-symbol and those not marked as
 * either).
 */
public enum ResourceAccessibility {
    DEFAULT("default"),
    PRIVATE("private"),
    PUBLIC("public");

    private final String qualifier;

    ResourceAccessibility(String qualifier) {
        this.qualifier = qualifier;
    }

    public String getName() {
        return qualifier;
    }

    public static ResourceAccessibility getEnum(@NonNull String qualifier) {
        for (ResourceAccessibility accessibility : values()) {
            if (accessibility.qualifier.equals(qualifier)) {
                return accessibility;
            }
        }

        return null;
    }
}
