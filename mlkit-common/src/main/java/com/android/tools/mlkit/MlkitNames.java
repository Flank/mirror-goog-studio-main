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
import com.google.common.base.CaseFormat;
import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.io.MoreFiles;
import com.google.common.primitives.UnsignedBytes;
import java.io.File;
import javax.lang.model.SourceVersion;

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

    public static String computeModelClassName(File modelFile) {
        // TODO(b/150986465): in gradle or other place, handle the case that two models might
        // have same class name.
        String formattedName =
                CaseFormat.LOWER_UNDERSCORE.to(
                        CaseFormat.UPPER_CAMEL,
                        MoreFiles.getNameWithoutExtension(modelFile.toPath()).trim());
        CharMatcher classNameMatcher =
                CharMatcher.inRange('0', '9')
                        .or(CharMatcher.inRange('A', 'Z'))
                        .or(CharMatcher.inRange('a', 'z'));
        String className = classNameMatcher.retainFrom(formattedName);

        if (className.isEmpty()) {
            // If we can't interpret a valid class name from file name, then create name from
            // fileName hashcode(i.e. Model75)
            return "Model"
                    + UnsignedBytes.toString(
                            Hashing.murmur3_32()
                                    .hashString(modelFile.getName(), Charsets.UTF_8)
                                    .asBytes()[0]);
        } else if (SourceVersion.isIdentifier(className) && !SourceVersion.isKeyword(className)) {
            return className;
        } else {
            return "Model" + className;
        }
    }
}
