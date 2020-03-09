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
package com.android.tools.mlkit;

import com.android.utils.StringHelper;

/** Store names that used by both light class and gradle task. */
public class MlkitNames {
    public static final String OUTPUTS = "Outputs";

    public static final String PACKAGE_SUFFIX = ".ml";

    /** Format getter method to getPropertyNameAsType(i.e. getImage1AsTensorImage()). */
    public static String formatGetterName(String propertyName, String type) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder
                .append("get")
                .append(StringHelper.usLocaleCapitalize(propertyName))
                .append("As")
                .append(StringHelper.usLocaleCapitalize(type));

        return stringBuilder.toString();
    }
}
