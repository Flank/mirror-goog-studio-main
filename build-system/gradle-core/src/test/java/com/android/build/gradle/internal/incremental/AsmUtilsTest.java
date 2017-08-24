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

package com.android.build.gradle.internal.incremental;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.internal.incremental.annotated.OuterClassFor21;
import com.android.build.gradle.internal.incremental.annotated.SomeClassImplementingInterfaces;
import com.android.build.gradle.internal.incremental.annotated.SomeInterface;
import com.android.build.gradle.internal.incremental.annotated.SomeInterfaceWithDefaultMethods;
import com.android.utils.ILogger;
import com.android.utils.NullLogger;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

/**
 * Tests for the {@link AsmUtils} class
 */
public class AsmUtilsTest {

    static final AsmUtils.ClassNodeProvider classReaderProvider = new ClassNodeProviderForTests();

    ILogger iLogger = new NullLogger();

    @Test
    public void testGetOuterClassName() throws IOException {

        {
            ClassNode classNode = AsmUtils
                    .readClass(OuterClassFor21.class.getClassLoader(),
                            Type.getType(OuterClassFor21.InnerClass.InnerInnerClass.class)
                                    .getInternalName());

            assertThat(AsmUtils.getOuterClassName(classNode)).isEqualTo(
                    Type.getType(OuterClassFor21.InnerClass.class).getInternalName());
        }
        {
            ClassNode classNode = AsmUtils
                    .readClass(OuterClassFor21.class.getClassLoader(),
                            Type.getType(OuterClassFor21.InnerClass.class).getInternalName());

            assertThat(AsmUtils.getOuterClassName(classNode)).isEqualTo(
                    Type.getType(OuterClassFor21.class).getInternalName());
        }
        {
            ClassNode classNode = AsmUtils
                    .readClass(OuterClassFor21.class.getClassLoader(),
                            Type.getType(OuterClassFor21.class)
                                    .getInternalName());

            assertThat(AsmUtils.getOuterClassName(classNode)).isNull();
        }
    }

    @Test
    public void testReadInterfaces() throws IOException {
        ClassNode classNode =
                AsmUtils.loadClass(
                        classReaderProvider,
                        Type.getType(SomeClassImplementingInterfaces.class).getInternalName(),
                        iLogger);

        ImmutableList.Builder<ClassNode> listBuilder = ImmutableList.builder();
        assertThat(AsmUtils.readInterfaces(classNode, classReaderProvider, listBuilder, iLogger))
                .isTrue();

        ImmutableList<ClassNode> interfaceNodes = listBuilder.build();
        assertThat(interfaceNodes).hasSize(2);
        for (ClassNode interfaceNode : interfaceNodes) {
            assertThat(interfaceNode.name)
                    .isAnyOf(
                            Type.getType(SomeInterface.class).getInternalName(),
                            Type.getType(SomeInterfaceWithDefaultMethods.class).getInternalName());
        }
    }

    @Test
    public void testReadClassAndInterfaces() throws IOException {

        IncrementalVisitor.ClassAndInterfacesNode classNodeAndInterfaces =
                AsmUtils.readParentClassAndInterfaces(
                        classReaderProvider,
                        Type.getType(SomeClassImplementingInterfaces.class).getInternalName(),
                        "Object",
                        21,
                        iLogger);

        assertThat(classNodeAndInterfaces).isNotNull();
        assertThat(classNodeAndInterfaces.classNode.name)
                .isEqualTo(Type.getType(SomeClassImplementingInterfaces.class).getInternalName());
        assertThat(classNodeAndInterfaces.implementedInterfaces).hasSize(2);
        for (ClassNode implementedInterface : classNodeAndInterfaces.implementedInterfaces) {
            assertThat(implementedInterface.name)
                    .isAnyOf(
                            Type.getType(SomeInterface.class).getInternalName(),
                            Type.getType(SomeInterfaceWithDefaultMethods.class).getInternalName());
        }
    }
}
