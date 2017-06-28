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

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.internal.dsl.Splits;
import com.google.common.collect.Sets;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.TypeToken;
import com.google.protobuf.Descriptors;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.wireless.android.sdk.stats.DeviceInfo;
import com.google.wireless.android.sdk.stats.GradleBuildSplits;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.gradle.api.Task;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;
import org.junit.Test;

public class AnalyticsUtilTest {

    @Test
    public void checkAllTasksHaveEnumValues() throws IOException {
        checkHaveAllEnumValues(
                Task.class,
                AnalyticsUtil::getTaskExecutionType,
                AnalyticsUtil::getPotentialTaskExecutionTypeName,
                "com.android.build.gradle.tasks.ZipMergingTask",
                "com.android.build.gradle.internal.tasks.PlatformAttrExtractorTask",
                "com.android.build.gradle.tasks.AndroidZip");
    }

    @Test
    public void checkAllTransformsHaveEnumValues() throws IOException {
        checkHaveAllEnumValues(
                Transform.class,
                AnalyticsUtil::getTransformType,
                AnalyticsUtil::getPotentialTransformTypeName,
                "com.android.build.gradle.internal.transforms.LibraryIntermediateJarsTransform",
                "com.android.build.gradle.internal.transforms.LibraryAarJarsTransform",
                "com.android.build.gradle.internal.pipeline.TestTransform",
                "com.android.build.gradle.internal.tasks.AppPreBuildTask");
    }

    @Test
    public void splitConverterTest() throws IOException {
        Splits splits =
                new Splits(
                        new Instantiator() {
                            @Override
                            public <T> T newInstance(Class<? extends T> aClass, Object... objects)
                                    throws ObjectInstantiationException {
                                try {
                                    return aClass.getConstructor().newInstance();
                                } catch (Exception e) {
                                    throw new ObjectInstantiationException(aClass, e);
                                }
                            }
                        });
        // Defaults
        {
            GradleBuildSplits proto = AnalyticsUtil.toProto(splits);
            assertThat(proto.getAbiEnabled()).isFalse();
            assertThat(proto.getAbiEnableUniversalApk()).isFalse();
            assertThat(proto.getAbiFiltersList()).isEmpty();
            assertThat(proto.getDensityEnabled()).isFalse();
            assertThat(proto.getLanguageEnabled()).isFalse();
        }

        splits.abi(
                it -> {
                    it.setEnable(true);
                    it.setUniversalApk(true);
                    it.reset();
                    it.include("x86", "armeabi");
                });
        {
            GradleBuildSplits proto = AnalyticsUtil.toProto(splits);
            assertThat(proto.getAbiEnabled()).isTrue();
            assertThat(proto.getAbiEnableUniversalApk()).isTrue();
            assertThat(proto.getAbiFiltersList())
                    .containsExactly(
                            DeviceInfo.ApplicationBinaryInterface.ARME_ABI,
                            DeviceInfo.ApplicationBinaryInterface.X86_ABI);
        }

        splits.density(
                it -> {
                    it.setEnable(true);
                    it.setAuto(true);
                    it.reset();
                    it.include("xxxhdpi", "xxhdpi");
                });
        {
            GradleBuildSplits proto = AnalyticsUtil.toProto(splits);
            assertThat(proto.getDensityEnabled()).isTrue();
            assertThat(proto.getDensityAuto()).isTrue();
            assertThat(proto.getDensityValuesList()).containsExactly(640, 480);
        }

        splits.language(
                it -> {
                    it.setEnable(true);
                    it.setAuto(true);
                    it.include("en");
                });
        {
            GradleBuildSplits proto = AnalyticsUtil.toProto(splits);
            assertThat(proto.getLanguageEnabled()).isTrue();
            assertThat(proto.getLanguageAuto()).isTrue();
            assertThat(proto.getLanguageIncludesList()).containsExactly("en");
        }

        // Check other field population is based on enable flag.
        splits.language(it -> it.setEnable(false));
        {
            GradleBuildSplits proto = AnalyticsUtil.toProto(splits);
            assertThat(proto.getLanguageEnabled()).isFalse();
            assertThat(proto.getLanguageAuto()).isFalse();
            assertThat(proto.getLanguageIncludesList()).isEmpty();
        }
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

        Descriptors.EnumDescriptor protoEnum =
                mappingFunction.apply(missingTasks.get(0)).getDescriptorForType();

        int maxNumber =
                protoEnum
                        .getValues()
                        .stream()
                        .mapToInt(Descriptors.EnumValueDescriptor::getNumber)
                        .max()
                        .orElseThrow(() -> new IllegalStateException("Empty enum?"));

        StringBuilder error =
                new StringBuilder()
                        .append("Some ")
                        .append(itemClass.getSimpleName())
                        .append(
                                "s do not have corresponding logging proto enum values.\n"
                                        + "See tools/analytics-library/protos/src/main/proto/"
                                        + "analytics_enums.proto")
                        .append(protoEnum.getFullName())
                        .append(".\n");
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
