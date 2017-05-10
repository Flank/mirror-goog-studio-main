/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency;

import com.android.annotations.NonNull;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.gradle.api.attributes.Attribute;

/** Type for Build Type attributes in Gradle's configuration objects. */
public class AndroidTypeAttr implements org.gradle.api.Named {
    public static final Attribute<AndroidTypeAttr> ATTRIBUTE = Attribute.of(AndroidTypeAttr.class);

    public static final AndroidTypeAttr TYPE_APK = of("Apk");
    public static final AndroidTypeAttr TYPE_AAR = of("Aar");
    public static final AndroidTypeAttr TYPE_FEATURE = of("Feature");
    public static final AndroidTypeAttr TYPE_METADATA = of("Metadata");

    private static AndroidTypeAttr of(String name) {
        return new AndroidTypeAttr(name);
    }

    @NonNull private final String name;

    private AndroidTypeAttr(@NonNull String name) {
        this.name = name;
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("name", name).toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AndroidTypeAttr that = (AndroidTypeAttr) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
