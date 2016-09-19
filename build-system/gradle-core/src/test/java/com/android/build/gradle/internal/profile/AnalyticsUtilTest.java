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

package com.android.build.gradle.internal.profile;

import com.android.annotations.NonNull;
import com.android.build.api.transform.Transform;
import com.google.common.collect.Sets;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.TypeToken;
import com.google.protobuf.ProtocolMessageEnum;

import org.gradle.api.Task;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AnalyticsUtilTest {

    @Test
    public void checkAllTasksHaveEnumValues() throws IOException {
        checkHaveAllEnumValues(
                Task.class,
                AnalyticsUtil::getTaskExecutionType,
                AnalyticsUtil::getPotentialTaskExecutionTypeName);
    }

    @Test
    public void checkAllTransformsHaveEnumValues() throws IOException {
        checkHaveAllEnumValues(
                Transform.class,
                AnalyticsUtil::getTransformType,
                AnalyticsUtil::getPotentialTransformTypeName,
                "com.android.build.gradle.internal.pipeline.TestTransform");
    }

    private <T, U extends ProtocolMessageEnum> void checkHaveAllEnumValues(
            @NonNull Class<T> itemClass,
            @NonNull Function<Class<T>, U> mappingFunction,
            @NonNull Function<Class<T>, String> calculateExpectedEnumName,
            @NonNull String... blackListClasses)
            throws IOException {
        ClassPath classPath = ClassPath.from(this.getClass().getClassLoader());

        Set<String> blackList = Sets.newHashSet(blackListClasses);

        TypeToken<T> taskInterface = TypeToken.of(itemClass);
        List<Class<T>> missingTasks =
                classPath
                        .getTopLevelClassesRecursive("com.android.build")
                        .stream()
                        .filter(
                                info -> !blackList.contains(info.getName()))
                        .map(classInfo -> (Class<T>) classInfo.load())
                        .filter(
                                clazz ->
                                        TypeToken.of(clazz).getTypes().contains(taskInterface)
                                                && !Modifier.isAbstract(clazz.getModifiers())
                                                && mapsToUnknownProtoValue(clazz, mappingFunction))
                        .collect(Collectors.toList());

        if (missingTasks.isEmpty()) {
            return;
        }

        // Now generate a descriptive error message.

        Class<? extends ProtocolMessageEnum> protoEnum =
                mappingFunction.apply(missingTasks.get(0)).getClass();

        int maxNumber =
                Arrays.stream(protoEnum.getEnumConstants())
                        .map(ProtocolMessageEnum::getNumber)
                        .mapToInt(Integer::intValue)
                        .max()
                        .orElseThrow(() -> new IllegalStateException("Empty enum?"));

        StringBuilder error =
                new StringBuilder(
                        "Some "
                                + itemClass.getSimpleName()
                                + "s do not have corresponding logging proto "
                                + "enum values.\n"
                                + "See logs/proto/wireless/android/sdk/stats/studio_stats.proto "
                                + protoEnum.getEnumConstants()[0]
                                        .getDescriptorForType()
                                        .getFullName()
                                + ".\n"
                                + "Add the following and re-run the import script.\n");
        List<String> suggestions =
                missingTasks
                        .stream()
                        .map(calculateExpectedEnumName)
                        .sorted()
                        .collect(Collectors.toList());

        for (String className : suggestions) {
            maxNumber++;
            error.append("    ").append(className).append(" = ").append(maxNumber).append(";\n");
        }
        throw new AssertionError(error.toString());
    }


    private static <T, U extends ProtocolMessageEnum> boolean mapsToUnknownProtoValue(
            @NonNull Class<T> clazz,
            @NonNull Function<Class<T>, U> mappingFunction) {
        // This assumes that the proto with value 0 means 'unknown'.
        return mappingFunction.apply(clazz).getNumber() == 0;
    }
}
