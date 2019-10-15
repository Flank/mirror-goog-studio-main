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

import org.gradle.api.attributes.Attribute;

/**
 * Type for the attribute holding custom Usage information.
 *
 * <p>This gives us the ability to disambiguate between building and publishing. This is because for
 * library modules, we have 2 sets of compile/runtime configurations (one for building and one for
 * publishing) because the artifacts are different (content of AAR vs AAR itself)
 *
 * <p>So we need to disambiguate them so that the right one is used. We cannot just change their
 * normal Usage attribute because they both need to be compatible with potential transitive
 * dependencies that are java libraries.
 */
public interface AndroidUsageAttr extends org.gradle.api.Named {
    Attribute<AndroidUsageAttr> ATTRIBUTE = Attribute.of(AndroidUsageAttr.class);

    String BUILD = "android-build";
    String PUBLICATION = "android-publication";
}
