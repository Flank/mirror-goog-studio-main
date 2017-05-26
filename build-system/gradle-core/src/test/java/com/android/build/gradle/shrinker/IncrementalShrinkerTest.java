/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.shrinker;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.build.api.transform.Status;
import com.android.build.gradle.shrinker.IncrementalShrinker.IncrementalRunImpossibleException;
import com.android.build.gradle.shrinker.TestClasses.Annotations;
import com.android.build.gradle.shrinker.TestClassesForIncremental.Cycle;
import com.android.build.gradle.shrinker.TestClassesForIncremental.Interfaces;
import com.android.build.gradle.shrinker.TestClassesForIncremental.Simple;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.objectweb.asm.tree.ClassNode;

/** Tests for {@link IncrementalShrinker}. */
@SuppressWarnings("SpellCheckingInspection") // Lots of type descriptors below.
public class IncrementalShrinkerTest extends AbstractShrinkerTest {

    @Rule public ExpectedException mException = ExpectedException.none();

    @Test
    public void simple_testIncrementalUpdate() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.emptyClass("NotUsed"), new File(mTestPackageDir, "NotUsed.class"));

        fullRun("Main", "main:()V");

        assertTrue(new File(mIncrementalDir, "shrinker.bin").exists());
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        assertMembersLeft("Aaa", "<init>:()V", "m1:()V");
        assertMembersLeft("Bbb", "<init>:()V");
        assertClassSkipped("NotUsed");

        long timestampBbb = getOutputClassFile("Bbb").lastModified();
        long timestampMain = getOutputClassFile("Main").lastModified();
        TestUtils.waitForFileSystemTick(timestampMain);

        Files.write(Simple.main2(), new File(mTestPackageDir, "Main.class"));
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        assertMembersLeft("Aaa", "<init>:()V", "m2:()V");
        assertMembersLeft("Bbb", "<init>:()V");
        assertClassSkipped("NotUsed");

        assertTrue(timestampMain < getOutputClassFile("Main").lastModified());
        assertEquals(timestampBbb, getOutputClassFile("Bbb").lastModified());
    }

    @Test
    public void simple_unusedClassModified() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));
        Files.write(
                TestClassesForIncremental.classWhichReturnsInt("NotUsed", 1),
                new File(mTestPackageDir, "NotUsed.class"));

        fullRun("Main", "main:()V");
        long timestampBbb = getOutputClassFile("Bbb").lastModified();
        TestUtils.waitForFileSystemTick(timestampBbb);

        Files.write(
                TestClassesForIncremental.classWhichReturnsInt("NotUsed", 2),
                new File(mTestPackageDir, "NotUsed.class"));
        incrementalRun(ImmutableMap.of("NotUsed", Status.CHANGED));

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        assertMembersLeft("Aaa", "<init>:()V", "m1:()V");
        assertMembersLeft("Bbb", "<init>:()V");
        assertClassSkipped("NotUsed");

        assertEquals(timestampBbb, getOutputClassFile("Bbb").lastModified());
    }

    @Test
    public void simple_testIncrementalUpdate_methodAdded() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.emptyClass("NotUsed"), new File(mTestPackageDir, "NotUsed.class"));

        fullRun("Main", "main:()V");

        Files.write(Simple.main_extraMethod(), new File(mTestPackageDir, "Main.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("test/Main.extraMain:()V added");
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_methodRemoved() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main_extraMethod(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.emptyClass("NotUsed"), new File(mTestPackageDir, "NotUsed.class"));

        fullRun("Main", "main:()V");

        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("test/Main.extraMain:()V removed");
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_fieldAdded() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.emptyClass("NotUsed"), new File(mTestPackageDir, "NotUsed.class"));

        fullRun("Main", "main:()V");

        Files.write(Simple.main_extraField(), new File(mTestPackageDir, "Main.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("test/Main.sString:Ljava/lang/String; added");
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_fieldRemoved() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main_extraField(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.emptyClass("NotUsed"), new File(mTestPackageDir, "NotUsed.class"));

        fullRun("Main", "main:()V");

        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("test/Main.sString:Ljava/lang/String; removed");
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_classAdded() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));

        fullRun("Main", "main:()V");

        Files.write(TestClasses.emptyClass("NotUsed"), new File(mTestPackageDir, "NotUsed.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage(FileUtils.toSystemDependentPath("test/NotUsed.class") + " added");
        incrementalRun(
                ImmutableMap.of(
                        "Main", Status.CHANGED,
                        "NotUsed", Status.ADDED));
    }

    @Test
    public void simple_testIncrementalUpdate_classRemoved() throws Exception {
        // Given:
        File notUsedClass = new File(mTestPackageDir, "NotUsed.class");

        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.emptyClass("NotUsed"), notUsedClass);

        fullRun("Main", "main:()V");

        FileUtils.delete(notUsedClass);

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage(
                FileUtils.toSystemDependentPath("test/NotUsed.class") + " removed");
        incrementalRun(
                ImmutableMap.of(
                        "Main", Status.CHANGED,
                        "NotUsed", Status.REMOVED));
    }

    @Test
    public void simple_testIncrementalUpdate_superclassChanged() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));

        fullRun("Main", "main:()V");

        Files.write(Simple.bbb_extendsAaa(), new File(mTestPackageDir, "Bbb.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("test/Bbb superclass changed");
        incrementalRun(ImmutableMap.of("Bbb", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_interfacesChanged() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));

        fullRun("Main", "main:()V");

        Files.write(Simple.bbb_serializable(), new File(mTestPackageDir, "Bbb.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("test/Bbb interfaces changed");
        incrementalRun(ImmutableMap.of("Bbb", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_methodModifiersChanged() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));

        fullRun("Main", "main:()V");

        Files.write(Simple.bbb_packagePrivateConstructor(), new File(mTestPackageDir, "Bbb.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("test/Bbb.<init>:()V modifiers changed");
        incrementalRun(ImmutableMap.of("Bbb", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_fieldModifiersChanged() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main_extraField(), new File(mTestPackageDir, "Main.class"));

        fullRun("Main", "main:()V");

        Files.write(Simple.main_extraField_private(), new File(mTestPackageDir, "Main.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("test/Main.sString:Ljava/lang/String; modifiers changed");
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_classModifiersChanged() throws Exception {
        // Given:
        Files.write(Simple.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(Simple.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(Simple.main1(), new File(mTestPackageDir, "Main.class"));

        fullRun("Main", "main:()V");

        Files.write(Simple.bbb_packagePrivate(), new File(mTestPackageDir, "Bbb.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("test/Bbb modifiers changed");
        incrementalRun(ImmutableMap.of("Bbb", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_classAnnotationAdded() throws Exception {
        // Given:
        Files.write(Annotations.main_annotatedClass(), new File(mTestPackageDir, "Main.class"));
        Files.write(
                TestClasses.Annotations.myAnnotation(),
                new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(
                TestClasses.emptyClass("SomeClass"), new File(mTestPackageDir, "SomeClass.class"));
        Files.write(
                TestClasses.emptyClass("SomeOtherClass"),
                new File(mTestPackageDir, "SomeOtherClass.class"));

        fullRun("Main", "main:()V");

        Files.write(Annotations.main_noAnnotations(), new File(mTestPackageDir, "Main.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("Annotation test/MyAnnotation on test/Main removed");
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_classAnnotationRemoved() throws Exception {
        // Given:
        Files.write(Annotations.main_noAnnotations(), new File(mTestPackageDir, "Main.class"));
        Files.write(
                TestClasses.Annotations.myAnnotation(),
                new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(
                TestClasses.emptyClass("SomeClass"), new File(mTestPackageDir, "SomeClass.class"));
        Files.write(
                TestClasses.emptyClass("SomeOtherClass"),
                new File(mTestPackageDir, "SomeOtherClass.class"));

        fullRun("Main", "main:()V");

        Files.write(Annotations.main_annotatedClass(), new File(mTestPackageDir, "Main.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("Annotation test/MyAnnotation on test/Main added");
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_methodAnnotationAdded() throws Exception {
        // Given:
        Files.write(Annotations.main_noAnnotations(), new File(mTestPackageDir, "Main.class"));
        Files.write(
                TestClasses.Annotations.myAnnotation(),
                new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(
                TestClasses.emptyClass("SomeClass"), new File(mTestPackageDir, "SomeClass.class"));
        Files.write(
                TestClasses.emptyClass("SomeOtherClass"),
                new File(mTestPackageDir, "SomeOtherClass.class"));

        fullRun("Main", "main:()V");

        Files.write(Annotations.main_annotatedMethod(), new File(mTestPackageDir, "Main.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("Annotation test/MyAnnotation on test/Main.main added");
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
    }

    @Test
    public void simple_testIncrementalUpdate_methodAnnotationRemoved() throws Exception {
        // Given:
        Files.write(Annotations.main_annotatedMethod(), new File(mTestPackageDir, "Main.class"));
        Files.write(
                TestClasses.Annotations.myAnnotation(),
                new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(
                TestClasses.emptyClass("SomeClass"), new File(mTestPackageDir, "SomeClass.class"));
        Files.write(
                TestClasses.emptyClass("SomeOtherClass"),
                new File(mTestPackageDir, "SomeOtherClass.class"));

        fullRun("Main", "main:()V");

        Files.write(Annotations.main_noAnnotations(), new File(mTestPackageDir, "Main.class"));

        mException.expect(IncrementalRunImpossibleException.class);
        mException.expectMessage("Annotation test/MyAnnotation on test/Main.main removed");
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
    }

    @Test
    public void cycle() throws Exception {
        // Given:
        Files.write(Cycle.main1(), new File(mTestPackageDir, "Main.class"));
        Files.write(Cycle.cycleOne(), new File(mTestPackageDir, "CycleOne.class"));
        Files.write(Cycle.cycleTwo(), new File(mTestPackageDir, "CycleTwo.class"));
        Files.write(TestClasses.emptyClass("NotUsed"), new File(mTestPackageDir, "NotUsed.class"));

        fullRun("Main", "main:()V");

        assertTrue(new File(mIncrementalDir, "shrinker.bin").exists());
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        assertMembersLeft("CycleOne", "<init>:()V");
        assertMembersLeft("CycleTwo", "<init>:()V");
        assertClassSkipped("NotUsed");

        byte[] mainBytes = Files.toByteArray(getOutputClassFile("Main"));

        Files.write(Cycle.main2(), new File(mTestPackageDir, "Main.class"));
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        assertClassSkipped("CycleOne");
        assertClassSkipped("CycleTwo");
        assertClassSkipped("NotUsed");

        assertNotEquals(mainBytes, Files.toByteArray(getOutputClassFile("Main")));

        Files.write(Cycle.main1(), new File(mTestPackageDir, "Main.class"));
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        assertMembersLeft("CycleOne", "<init>:()V");
        assertMembersLeft("CycleTwo", "<init>:()V");
        assertClassSkipped("NotUsed");
    }

    @Test
    public void interfaces_stopUsing() throws Exception {
        Files.write(Interfaces.main(true), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));

        fullRun("Main", "buildMyImpl:()Ltest/MyImpl;", "main:(Ltest/MyImpl;)V");

        assertMembersLeft(
                "Main", "<init>:()V", "buildMyImpl:()Ltest/MyImpl;", "main:(Ltest/MyImpl;)V");
        assertMembersLeft("MyInterface", "doSomething:(Ljava/lang/Object;)V");
        assertMembersLeft("MyImpl", "<init>:()V", "doSomething:(Ljava/lang/Object;)V");
        assertImplements("MyImpl", "test/MyInterface");

        Files.write(Interfaces.main(false), new File(mTestPackageDir, "Main.class"));
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));

        assertClassSkipped("MyInterface");
        assertMembersLeft(
                "Main", "<init>:()V", "buildMyImpl:()Ltest/MyImpl;", "main:(Ltest/MyImpl;)V");
        assertMembersLeft("MyImpl", "<init>:()V", "doSomething:(Ljava/lang/Object;)V");
        File classFile = getOutputClassFile("MyImpl");
        assertThat(getInterfaceNames(classFile)).doesNotContain("test/MyInterface");
    }

    @Test
    public void interfaces_startUsing() throws Exception {
        Files.write(Interfaces.main(false), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));

        fullRun("Main", "buildMyImpl:()Ltest/MyImpl;", "main:(Ltest/MyImpl;)V");

        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));
        assertMembersLeft(
                "Main", "<init>:()V", "buildMyImpl:()Ltest/MyImpl;", "main:(Ltest/MyImpl;)V");
        assertMembersLeft("MyImpl", "<init>:()V", "doSomething:(Ljava/lang/Object;)V");
        File classFile = getOutputClassFile("MyImpl");
        assertThat(getInterfaceNames(classFile)).doesNotContain("test/MyInterface");
        assertClassSkipped("MyInterface");

        Files.write(Interfaces.main(true), new File(mTestPackageDir, "Main.class"));
        incrementalRun(ImmutableMap.of("Main", Status.CHANGED));

        assertMembersLeft(
                "Main", "<init>:()V", "buildMyImpl:()Ltest/MyImpl;", "main:(Ltest/MyImpl;)V");
        assertMembersLeft("MyImpl", "<init>:()V", "doSomething:(Ljava/lang/Object;)V");
        assertMembersLeft("MyInterface", "doSomething:(Ljava/lang/Object;)V");
        assertImplements("MyImpl", "test/MyInterface");
    }

    @Test
    public void signatures_classSignature_startUsing() throws Exception {
        Files.write(TestClasses.Signatures.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Signatures.named(), new File(mTestPackageDir, "Named.class"));
        Files.write(TestClasses.Signatures.namedMap(), new File(mTestPackageDir, "NamedMap.class"));
        Files.write(TestClasses.Signatures.hasAge(), new File(mTestPackageDir, "HasAge.class"));
        Files.write(
                TestClassesForIncremental.classWithCasts("Casts"),
                new File(mTestPackageDir, "Casts.class"));

        fullRun(
                parseKeepRules(
                        "-keep class test.Main { void main(...); }\n -keep class test.Casts { *; }"));

        assertMembersLeft("Main", "<init>:()V", "main:(Ltest/NamedMap;)V");
        assertMembersLeft("NamedMap", "<init>:()V");
        assertClassSkipped("HasAge");
        assertClassSkipped("Named");

        ClassNode namedMap = getClassNode(getOutputClassFile("NamedMap"));
        assertThat(namedMap.signature)
                .isEqualTo("<T::Ljava/io/Serializable;:Ljava/lang/Object;>Ljava/lang/Object;");

        // Start using Named.
        Files.write(
                TestClassesForIncremental.classWithCasts("Casts", "test/Named"),
                new File(mTestPackageDir, "Casts.class"));
        incrementalRun(ImmutableMap.of("Casts", Status.CHANGED));

        assertMembersLeft("Main", "<init>:()V", "main:(Ltest/NamedMap;)V");
        assertMembersLeft("NamedMap", "<init>:()V");
        assertClassSkipped("HasAge");
        assertMembersLeft("Named");

        namedMap = getClassNode(getOutputClassFile("NamedMap"));
        assertThat(namedMap.signature)
                .isEqualTo("<T::Ljava/io/Serializable;:Ltest/Named;>Ljava/lang/Object;");
    }

    @Test
    public void signatures_classSignature_stopUsing() throws Exception {
        Files.write(TestClasses.Signatures.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Signatures.named(), new File(mTestPackageDir, "Named.class"));
        Files.write(TestClasses.Signatures.namedMap(), new File(mTestPackageDir, "NamedMap.class"));
        Files.write(TestClasses.Signatures.hasAge(), new File(mTestPackageDir, "HasAge.class"));
        Files.write(
                TestClassesForIncremental.classWithCasts("Casts", "test/Named"),
                new File(mTestPackageDir, "Casts.class"));

        fullRun(
                parseKeepRules(
                        "-keep class test.Main { void main(...); }\n -keep class test.Casts { *; }"));

        assertMembersLeft("Main", "<init>:()V", "main:(Ltest/NamedMap;)V");
        assertMembersLeft("NamedMap", "<init>:()V");
        assertClassSkipped("HasAge");
        assertMembersLeft("Named");

        ClassNode namedMap = getClassNode(getOutputClassFile("NamedMap"));
        assertThat(namedMap.signature)
                .isEqualTo("<T::Ljava/io/Serializable;:Ltest/Named;>Ljava/lang/Object;");

        // Stop using Named.
        Files.write(
                TestClassesForIncremental.classWithCasts("Casts"),
                new File(mTestPackageDir, "Casts.class"));
        incrementalRun(ImmutableMap.of("Casts", Status.CHANGED));

        assertMembersLeft("Main", "<init>:()V", "main:(Ltest/NamedMap;)V");
        assertMembersLeft("NamedMap", "<init>:()V");
        assertClassSkipped("HasAge");
        assertClassSkipped("Named");

        namedMap = getClassNode(getOutputClassFile("NamedMap"));
        assertThat(namedMap.signature)
                .isEqualTo("<T::Ljava/io/Serializable;:Ljava/lang/Object;>Ljava/lang/Object;");
    }
}
