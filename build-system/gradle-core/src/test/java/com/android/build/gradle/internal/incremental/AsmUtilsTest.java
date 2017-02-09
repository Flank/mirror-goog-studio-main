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
import java.io.IOException;
import org.junit.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

/**
 * Tests for the {@link AsmUtils} class
 */
public class AsmUtilsTest {

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
}
