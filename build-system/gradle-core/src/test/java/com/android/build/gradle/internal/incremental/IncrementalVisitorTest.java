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
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.incremental.annotated.NoInnerClassFor21;
import com.android.build.gradle.internal.incremental.annotated.NotAnnotatedClass;
import com.android.build.gradle.internal.incremental.annotated.OuterClassFor21;
import com.android.build.gradle.internal.incremental.annotated.SingleLevelOuterClassFor21;
import com.android.build.gradle.internal.incremental.annotated.TestTargetApi;
import com.android.utils.ILogger;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

/**
 * tests for the {@link IncrementalVisitor}
 */
public class IncrementalVisitorTest {

    @Mock
    ILogger logger;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testIsClassTargetingNewerPlatform() throws IOException {

        AsmUtils.ClassReaderProvider classReaderProvider = new AsmUtils.ClassReaderProvider() {
            @Override
            public ClassReader loadClassBytes(@NonNull String className, @NonNull ILogger logger)
                    throws IOException {
                try (InputStream is = this.getClass().getClassLoader().getResourceAsStream(
                        className + ".class")) {
                    if (is == null) {
                        fail("Cannot load class " + className);
                    }
                    return new ClassReader(is);
                }
            }
        };

        {
            ClassNode innerInnerClassNode = AsmUtils
                    .readClass(OuterClassFor21.class.getClassLoader(),
                            Type.getType(OuterClassFor21.InnerClass.InnerInnerClass.class)
                                    .getInternalName());

            Assert.assertFalse(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            24, Type.getType(TestTargetApi.class), classReaderProvider,
                            innerInnerClassNode, logger));
            Assert.assertTrue(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            19, Type.getType(TestTargetApi.class), classReaderProvider,
                            innerInnerClassNode, logger));
        }

        {
            ClassNode innerClassNode = AsmUtils.readClass(OuterClassFor21.class.getClassLoader(),
                    Type.getType(OuterClassFor21.InnerClass.class).getInternalName());

            Assert.assertFalse(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            24, Type.getType(TestTargetApi.class),
                            classReaderProvider, innerClassNode, logger));
            Assert.assertTrue(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            19, Type.getType(TestTargetApi.class),
                            classReaderProvider, innerClassNode, logger));
        }
        {
            ClassNode outerFor21 = AsmUtils.readClass(OuterClassFor21.class.getClassLoader(),
                    Type.getType(OuterClassFor21.class).getInternalName());

            Assert.assertFalse(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            24, Type.getType(TestTargetApi.class),
                            classReaderProvider, outerFor21, logger));
            Assert.assertTrue(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            19, Type.getType(TestTargetApi.class),
                            classReaderProvider, outerFor21, logger));
        }
        {
            ClassNode noInnerClassFor21 = AsmUtils.readClass(OuterClassFor21.class.getClassLoader(),
                    Type.getType(NoInnerClassFor21.class).getInternalName());

            Assert.assertFalse(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            24, Type.getType(TestTargetApi.class), classReaderProvider,
                            noInnerClassFor21, logger));
            Assert.assertTrue(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            19, Type.getType(TestTargetApi.class), classReaderProvider,
                            noInnerClassFor21, logger));
        }
        {
            ClassNode singleLevelOuterClassFor21 = AsmUtils.readClass(OuterClassFor21.class.getClassLoader(),
                    Type.getType(SingleLevelOuterClassFor21.class).getInternalName());

            Assert.assertFalse(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            24, Type.getType(TestTargetApi.class), classReaderProvider,
                            singleLevelOuterClassFor21, logger));
            Assert.assertTrue(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            19, Type.getType(TestTargetApi.class), classReaderProvider,
                            singleLevelOuterClassFor21, logger));
        }
        {
            ClassNode innerClass = AsmUtils.readClass(OuterClassFor21.class.getClassLoader(),
                    Type.getType(SingleLevelOuterClassFor21.InnerClass.class).getInternalName());

            Assert.assertFalse(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            24, Type.getType(TestTargetApi.class), classReaderProvider,
                            innerClass, logger));
            Assert.assertTrue(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            19, Type.getType(TestTargetApi.class), classReaderProvider,
                            innerClass, logger));
        }
        {
            ClassNode innerClass = AsmUtils.readClass(OuterClassFor21.class.getClassLoader(),
                    Type.getType(SingleLevelOuterClassFor21.InnerClass.class).getInternalName());

            Assert.assertFalse(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            24, Type.getType(TestTargetApi.class), classReaderProvider,
                            innerClass, logger));
            Assert.assertTrue(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            19, Type.getType(TestTargetApi.class), classReaderProvider,
                            innerClass, logger));
        }
        {
            ClassNode innerInnerClass = AsmUtils.readClass(OuterClassFor21.class.getClassLoader(),
                    Type.getType(NotAnnotatedClass.InnerClass.InnerInnerClass.class).getInternalName());

            Assert.assertFalse(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            24, Type.getType(TestTargetApi.class), classReaderProvider,
                            innerInnerClass, logger));
            Assert.assertFalse(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            19, Type.getType(TestTargetApi.class), classReaderProvider,
                            innerInnerClass, logger));
        }
        {
            ClassNode innerClass = AsmUtils.readClass(OuterClassFor21.class.getClassLoader(),
                    Type.getType(NotAnnotatedClass.InnerClass.class).getInternalName());

            Assert.assertFalse(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            24, Type.getType(TestTargetApi.class), classReaderProvider,
                            innerClass, logger));
            Assert.assertFalse(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            19, Type.getType(TestTargetApi.class), classReaderProvider,
                            innerClass, logger));
        }
        {
            ClassNode outerClass = AsmUtils.readClass(OuterClassFor21.class.getClassLoader(),
                    Type.getType(NotAnnotatedClass.class).getInternalName());

            Assert.assertFalse(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            24, Type.getType(TestTargetApi.class), classReaderProvider,
                            outerClass, logger));
            Assert.assertFalse(
                    IncrementalVisitor.isClassTargetingNewerPlatform(
                            19, Type.getType(TestTargetApi.class), classReaderProvider,
                            outerClass, logger));
        }
    }

    @Test
    public void testClassEligibility() throws IOException {
        File fooDotBar = temporaryFolder.newFile("foo.bar");
        assertThat(IncrementalVisitor.isClassEligibleForInstantRun(fooDotBar)).isFalse();

        File RDotClass = temporaryFolder.newFile("R.class");
        assertThat(IncrementalVisitor.isClassEligibleForInstantRun(RDotClass)).isFalse();

        File RdimenDotClass = temporaryFolder.newFile("R$dimen.class");
        assertThat(IncrementalVisitor.isClassEligibleForInstantRun(RdimenDotClass)).isFalse();

        File someClass = temporaryFolder.newFile("Some.class");
        assertThat(IncrementalVisitor.isClassEligibleForInstantRun(someClass)).isTrue();

        File RSomethingClass = temporaryFolder.newFile("Rsomething.class");
        assertThat(IncrementalVisitor.isClassEligibleForInstantRun(RSomethingClass)).isTrue();

        File RSomethingWithInner = temporaryFolder.newFile("Rsome$dimen.class");
        assertThat(IncrementalVisitor.isClassEligibleForInstantRun(RSomethingWithInner)).isTrue();
    }
}
