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

import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.api.transform.Status;
import com.android.build.gradle.shrinker.TestClasses.InnerClasses;
import com.android.build.gradle.shrinker.TestClasses.Interfaces;
import com.android.build.gradle.shrinker.TestClasses.Reflection;
import com.android.testutils.TestClassesGenerator;
import com.android.utils.FileUtils;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/** Tests for {@link FullRunShrinker}. */
@SuppressWarnings("SpellCheckingInspection") // Lots of type descriptors below.
public class FullRunShrinkerTest extends AbstractShrinkerTest {

    @Test
    public void simple_oneClass() throws Exception {
        // Given:
        Files.write(TestClasses.SimpleScenario.aaa(), new File(mTestPackageDir, "Aaa.class"));

        // When:
        fullRun("Aaa", "aaa:()V");

        // Then:
        assertMembersLeft("Aaa", "<init>:()V", "aaa:()V", "bbb:()V");
    }

    @Test
    public void simple_threeClasses() throws Exception {
        // Given:
        Files.write(TestClasses.SimpleScenario.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(TestClasses.SimpleScenario.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(TestClasses.SimpleScenario.ccc(), new File(mTestPackageDir, "Ccc.class"));

        // When:
        fullRun("Bbb", "bbb:(Ltest/Aaa;)V");

        // Then:
        assertMembersLeft("Aaa", "<init>:()V", "aaa:()V", "bbb:()V");
        assertMembersLeft("Bbb", "<init>:()V", "bbb:(Ltest/Aaa;)V");
        assertClassSkipped("Ccc");
    }

    @Test
    public void virtualCalls_keepEntryPointsSuperclass() throws Exception {
        // Given:
        Files.write(
                TestClasses.VirtualCalls.abstractClass(),
                new File(mTestPackageDir, "AbstractClass.class"));
        Files.write(TestClasses.VirtualCalls.impl(1), new File(mTestPackageDir, "Impl1.class"));

        // When:
        fullRun("Impl1", "abstractMethod:()V");

        // Then:
        assertMembersLeft("Impl1", "<init>:()V", "abstractMethod:()V");
        assertMembersLeft("AbstractClass", "<init>:()V");
    }

    @Test
    public void virtualCalls_abstractType() throws Exception {
        // Given:
        Files.write(
                TestClasses.VirtualCalls.main_abstractType(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(
                TestClasses.VirtualCalls.abstractClass(),
                new File(mTestPackageDir, "AbstractClass.class"));
        Files.write(TestClasses.VirtualCalls.impl(1), new File(mTestPackageDir, "Impl1.class"));
        Files.write(TestClasses.VirtualCalls.impl(2), new File(mTestPackageDir, "Impl2.class"));
        Files.write(TestClasses.VirtualCalls.impl(3), new File(mTestPackageDir, "Impl3.class"));

        // When:
        fullRun("Main", "main:([Ljava/lang/String;)V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:([Ljava/lang/String;)V");
        assertClassSkipped("Impl3");
        assertMembersLeft("AbstractClass", "abstractMethod:()V", "<init>:()V");
        assertMembersLeft("Impl1", "abstractMethod:()V", "<init>:()V");
        assertMembersLeft("Impl2", "abstractMethod:()V", "<init>:()V");
    }

    @Test
    public void virtualCalls_concreteType() throws Exception {
        // Given:
        Files.write(
                TestClasses.VirtualCalls.main_concreteType(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(
                TestClasses.VirtualCalls.abstractClass(),
                new File(mTestPackageDir, "AbstractClass.class"));
        Files.write(TestClasses.VirtualCalls.impl(1), new File(mTestPackageDir, "Impl1.class"));
        Files.write(TestClasses.VirtualCalls.impl(2), new File(mTestPackageDir, "Impl2.class"));
        Files.write(TestClasses.VirtualCalls.impl(3), new File(mTestPackageDir, "Impl3.class"));

        // When:
        fullRun("Main", "main:([Ljava/lang/String;)V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:([Ljava/lang/String;)V");
        assertClassSkipped("Impl3");
        assertMembersLeft("AbstractClass", "<init>:()V");
        assertMembersLeft("Impl1", "abstractMethod:()V", "<init>:()V");
        assertMembersLeft("Impl2", "<init>:()V");
    }

    @Test
    public void virtualCalls_methodFromParent() throws Exception {
        // Given:
        Files.write(
                TestClasses.VirtualCalls.main_parentChild(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.VirtualCalls.parent(), new File(mTestPackageDir, "Parent.class"));
        Files.write(TestClasses.VirtualCalls.child(), new File(mTestPackageDir, "Child.class"));

        // When:
        fullRun("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        assertMembersLeft("Parent", "<init>:()V", "onlyInParent:()V");
        assertMembersLeft("Child", "<init>:()V");
    }

    @Test
    public void sdkTypes_methodsFromJavaClasses() throws Exception {
        // Given:
        Files.write(TestClasses.SdkTypes.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(
                TestClasses.SdkTypes.myException(), new File(mTestPackageDir, "MyException.class"));

        // When:
        fullRun("Main", "main:([Ljava/lang/String;)V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:([Ljava/lang/String;)V");
        assertMembersLeft(
                "MyException", "<init>:()V", "hashCode:()I", "getMessage:()Ljava/lang/String;");
    }

    @Test
    public void interfaces_sdkInterface_classUsed_abstractType() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(
                Interfaces.namedRunnableImpl(),
                new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        fullRun(
                "Main",
                "buildMyCharSequence:()Ltest/MyCharSequence;",
                "callCharSequence:(Ljava/lang/CharSequence;)V");

        // Then:
        assertMembersLeft(
                "Main",
                "<init>:()V",
                "buildMyCharSequence:()Ltest/MyCharSequence;",
                "callCharSequence:(Ljava/lang/CharSequence;)V");
        assertMembersLeft(
                "MyCharSequence",
                "subSequence:(II)Ljava/lang/CharSequence;",
                "charAt:(I)C",
                "length:()I",
                "<init>:()V");
        assertClassSkipped("MyInterface");
        assertClassSkipped("MyImpl");

        assertImplements("MyCharSequence", "java/lang/CharSequence");
    }

    @Test
    public void interfaces_sdkInterface_implementedIndirectly() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(
                Interfaces.namedRunnableImpl(),
                new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        fullRun(
                "Main",
                "buildNamedRunnableImpl:()Ltest/NamedRunnableImpl;",
                "callRunnable:(Ljava/lang/Runnable;)V");

        // Then:
        assertMembersLeft(
                "Main",
                "<init>:()V",
                "buildNamedRunnableImpl:()Ltest/NamedRunnableImpl;",
                "callRunnable:(Ljava/lang/Runnable;)V");
        assertMembersLeft("NamedRunnableImpl", "run:()V", "<init>:()V");

        assertMembersLeft("NamedRunnable");
        assertImplements("NamedRunnableImpl", "test/NamedRunnable");

        // TODO: Write proper fixture code that will make sure all cases stay the same after
        // an empty incremental run for all test cases.
        forceEmptyIncrementalRun();
        assertMembersLeft("NamedRunnable");
    }

    @Test
    public void interfaces_sdkInterface_classUsed_concreteType() throws Exception {
        // Given:
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(
                Interfaces.namedRunnableImpl(),
                new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        fullRun(
                "Main",
                "buildMyCharSequence:()Ltest/MyCharSequence;",
                "callMyCharSequence:(Ltest/MyCharSequence;)V");

        // Then:
        assertMembersLeft(
                "Main",
                "<init>:()V",
                "buildMyCharSequence:()Ltest/MyCharSequence;",
                "callMyCharSequence:(Ltest/MyCharSequence;)V");
        assertMembersLeft(
                "MyCharSequence",
                "subSequence:(II)Ljava/lang/CharSequence;",
                "charAt:(I)C",
                "length:()I",
                "<init>:()V");
        assertClassSkipped("MyInterface");
        assertClassSkipped("MyImpl");

        assertImplements("MyCharSequence", "java/lang/CharSequence");
    }

    @Test
    public void interfaces_implementationFromSuperclass() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(
                Interfaces.namedRunnableImpl(),
                new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        fullRun(
                "Main",
                "useImplementationFromSuperclass:(Ltest/ImplementationFromSuperclass;)V",
                "useMyInterface:(Ltest/MyInterface;)V");

        // Then:
        assertMembersLeft(
                "Main",
                "<init>:()V",
                "useImplementationFromSuperclass:(Ltest/ImplementationFromSuperclass;)V",
                "useMyInterface:(Ltest/MyInterface;)V");
        assertMembersLeft("ImplementationFromSuperclass", "<init>:()V");
        assertMembersLeft("MyInterface", "doSomething:(Ljava/lang/Object;)V");
        assertClassSkipped("MyImpl");
        assertClassSkipped("MyCharSequence");

        // This is the tricky part: this method should be kept, because a subclass is using it to
        // implement an interface.
        assertMembersLeft("DoesSomething", "<init>:()V", "doSomething:(Ljava/lang/Object;)V");

        assertImplements("ImplementationFromSuperclass", "test/MyInterface");

        // Make sure we don't crash. TODO: do this for every test case here.
        incrementalRun(ImmutableMap.of("ImplementationFromSuperclass", Status.CHANGED));
    }

    @Test
    public void interfaces_implementationFromSuperclass_interfaceInheritance() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.mySubInterface(), new File(mTestPackageDir, "MySubInterface.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(
                Interfaces.namedRunnableImpl(),
                new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass_subInterface(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        ShrinkerGraph<String> graph =
                fullRun(
                                "Main",
                                "useImplementationFromSuperclass:(Ltest/ImplementationFromSuperclass;)V",
                                "useMyInterface:(Ltest/MyInterface;)V")
                        .graph;

        // Then:
        assertMembersLeft(
                "Main",
                "<init>:()V",
                "useImplementationFromSuperclass:(Ltest/ImplementationFromSuperclass;)V",
                "useMyInterface:(Ltest/MyInterface;)V");
        assertMembersLeft("ImplementationFromSuperclass", "<init>:()V");
        assertMembersLeft("MyInterface", "doSomething:(Ljava/lang/Object;)V");

        // This is the tricky part: this method should be kept, because a subclass is using it to
        // implement an interface.
        assertMembersLeft("DoesSomething", "<init>:()V", "doSomething:(Ljava/lang/Object;)V");

        assertMembersLeft("MySubInterface");
        assertClassSkipped("MyImpl");
        assertClassSkipped("MyCharSequence");

        assertImplements("ImplementationFromSuperclass", "test/MySubInterface");

        // Check the graph for unexepcted edges:
        assertThat(graph.getDependencies("test/MySubInterface"))
                .containsExactly(
                        new Dependency<>("test/MyInterface", DependencyType.INTERFACE_IMPLEMENTED));
        assertThat(graph.getDependencies("test/MyInterface"))
                .containsExactly(
                        new Dependency<>(
                                "test/MySubInterface", DependencyType.SUPERINTERFACE_KEPT));
        assertThat(graph.getDependencies("test/MyImpl"))
                .containsExactly(
                        new Dependency<>(
                                "test/MyImpl.doSomething:(Ljava/lang/Object;)V",
                                DependencyType.CLASS_IS_KEPT),
                        new Dependency<>(
                                "test/MyImpl.<init>:()V", DependencyType.REQUIRED_CLASS_STRUCTURE),
                        new Dependency<>("test/MyInterface", DependencyType.INTERFACE_IMPLEMENTED));
        assertThat(graph.getDependencies("test/ImplementationFromSuperclass"))
                .containsExactly(
                        new Dependency<>(
                                "test/DoesSomething", DependencyType.REQUIRED_CLASS_STRUCTURE),
                        new Dependency<>(
                                "test/ImplementationFromSuperclass.anotherMethod:()V",
                                DependencyType.CLASS_IS_KEPT),
                        new Dependency<>(
                                "test/ImplementationFromSuperclass.<init>:()V",
                                DependencyType.REQUIRED_CLASS_STRUCTURE),
                        new Dependency<>(
                                "test/MySubInterface", DependencyType.INTERFACE_IMPLEMENTED),
                        new Dependency<>(
                                "test/ImplementationFromSuperclass.doSomething$shrinker_fake:(Ljava/lang/Object;)V",
                                DependencyType.CLASS_IS_KEPT),
                        new Dependency<>("test/MyInterface", DependencyType.INTERFACE_IMPLEMENTED));
    }

    @Test
    public void interfaces_sdkInterface_classNotUsed() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(
                Interfaces.namedRunnableImpl(),
                new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        fullRun("Main", "callCharSequence:(Ljava/lang/CharSequence;)V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "callCharSequence:(Ljava/lang/CharSequence;)V");
        assertClassSkipped("MyCharSequence");
        assertClassSkipped("MyInterface");
        assertClassSkipped("MyImpl");
    }

    @Test
    public void interfaces_appInterface_abstractType() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(
                Interfaces.namedRunnableImpl(),
                new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        fullRun("Main", "buildMyImpl:()Ltest/MyImpl;", "useMyInterface:(Ltest/MyInterface;)V");

        // Then:
        assertMembersLeft(
                "Main",
                "<init>:()V",
                "buildMyImpl:()Ltest/MyImpl;",
                "useMyInterface:(Ltest/MyInterface;)V");
        assertMembersLeft("MyInterface", "doSomething:(Ljava/lang/Object;)V");
        assertMembersLeft("MyImpl", "<init>:()V", "doSomething:(Ljava/lang/Object;)V");
        assertClassSkipped("MyCharSequence");

        assertImplements("MyImpl", "test/MyInterface");
    }

    @Test
    public void interfaces_appInterface_concreteType() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(
                Interfaces.namedRunnableImpl(),
                new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        fullRun("Main", "useMyImpl_interfaceMethod:(Ltest/MyImpl;)V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "useMyImpl_interfaceMethod:(Ltest/MyImpl;)V");
        assertClassSkipped("MyInterface");
        assertMembersLeft("MyImpl", "<init>:()V", "doSomething:(Ljava/lang/Object;)V");
        assertClassSkipped("MyCharSequence");

        File classFile = getOutputClassFile("MyImpl");
        assertThat(getInterfaceNames(classFile)).doesNotContain("test/MyInterface");
    }

    @Test
    public void interfaces_appInterface_interfaceNotUsed() throws Exception {
        // Given:
        Files.write(Interfaces.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(Interfaces.myCharSequence(), new File(mTestPackageDir, "MyCharSequence.class"));
        Files.write(Interfaces.myInterface(), new File(mTestPackageDir, "MyInterface.class"));
        Files.write(Interfaces.myImpl(), new File(mTestPackageDir, "MyImpl.class"));
        Files.write(Interfaces.namedRunnable(), new File(mTestPackageDir, "NamedRunnable.class"));
        Files.write(
                Interfaces.namedRunnableImpl(),
                new File(mTestPackageDir, "NamedRunnableImpl.class"));
        Files.write(Interfaces.doesSomething(), new File(mTestPackageDir, "DoesSomething.class"));
        Files.write(
                Interfaces.implementationFromSuperclass(),
                new File(mTestPackageDir, "ImplementationFromSuperclass.class"));

        // When:
        fullRun("Main", "useMyImpl_otherMethod:(Ltest/MyImpl;)V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "useMyImpl_otherMethod:(Ltest/MyImpl;)V");
        assertClassSkipped("MyInterface");
        assertMembersLeft("MyImpl", "<init>:()V", "someOtherMethod:()V");
        assertClassSkipped("MyCharSequence");

        File classFile = getOutputClassFile("MyImpl");
        assertThat(getInterfaceNames(classFile)).doesNotContain("test/MyInterface");
    }

    @Test
    public void fields() throws Exception {
        // Given:
        Files.write(TestClasses.Fields.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Fields.myFields(), new File(mTestPackageDir, "MyFields.class"));
        Files.write(
                TestClasses.Fields.myFieldsSubclass(),
                new File(mTestPackageDir, "MyFieldsSubclass.class"));
        Files.write(
                TestClasses.emptyClass("MyFieldType"),
                new File(mTestPackageDir, "MyFieldType.class"));

        // When:
        fullRun("Main", "main:()I");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()I");
        assertMembersLeft(
                "MyFields",
                "<init>:()V",
                "<clinit>:()V",
                "readField:()I",
                "f1:I",
                "f2:I",
                "f4:Ltest/MyFieldType;",
                "sString:Ljava/lang/String;");
        assertMembersLeft("MyFieldType", "<init>:()V");
    }

    @Test
    public void fields_fromSuperclass() throws Exception {
        // Given:
        Files.write(TestClasses.Fields.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Fields.myFields(), new File(mTestPackageDir, "MyFields.class"));
        Files.write(
                TestClasses.Fields.myFieldsSubclass(),
                new File(mTestPackageDir, "MyFieldsSubclass.class"));
        Files.write(
                TestClasses.emptyClass("MyFieldType"),
                new File(mTestPackageDir, "MyFieldType.class"));

        // When:
        fullRun("Main", "main_subclass:()I");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main_subclass:()I");
        assertMembersLeft(
                "MyFields",
                "<init>:()V",
                "<clinit>:()V",
                "f1:I",
                "f2:I",
                "sString:Ljava/lang/String;");
        assertMembersLeft("MyFieldsSubclass", "<init>:()V");
        assertClassSkipped("MyFieldType");
    }

    @Test
    public void overrides_methodNotUsed() throws Exception {
        // Given:
        Files.write(
                TestClasses.MultipleOverriddenMethods.main(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(
                TestClasses.MultipleOverriddenMethods.interfaceOne(),
                new File(mTestPackageDir, "InterfaceOne.class"));
        Files.write(
                TestClasses.MultipleOverriddenMethods.interfaceTwo(),
                new File(mTestPackageDir, "InterfaceTwo.class"));
        Files.write(
                TestClasses.MultipleOverriddenMethods.implementation(),
                new File(mTestPackageDir, "Implementation.class"));

        // When:
        fullRun("Main", "buildImplementation:()V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "buildImplementation:()V");
        assertClassSkipped("InterfaceOne");
        assertClassSkipped("InterfaceTwo");
        assertMembersLeft("Implementation", "<init>:()V");
    }

    @Test
    public void overrides_classNotUsed() throws Exception {
        // Given:
        Files.write(
                TestClasses.MultipleOverriddenMethods.main(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(
                TestClasses.MultipleOverriddenMethods.interfaceOne(),
                new File(mTestPackageDir, "InterfaceOne.class"));
        Files.write(
                TestClasses.MultipleOverriddenMethods.interfaceTwo(),
                new File(mTestPackageDir, "InterfaceTwo.class"));
        Files.write(
                TestClasses.MultipleOverriddenMethods.implementation(),
                new File(mTestPackageDir, "Implementation.class"));

        // When:
        fullRun(
                "Main",
                "useInterfaceOne:(Ltest/InterfaceOne;)V",
                "useInterfaceTwo:(Ltest/InterfaceTwo;)V");

        // Then:
        assertMembersLeft(
                "Main",
                "<init>:()V",
                "useInterfaceOne:(Ltest/InterfaceOne;)V",
                "useInterfaceTwo:(Ltest/InterfaceTwo;)V");
        assertMembersLeft("InterfaceOne", "m:()V");
        assertMembersLeft("InterfaceTwo", "m:()V");
        assertClassSkipped("MyImplementation");
    }

    @Test
    public void overrides_interfaceOneUsed_classUsed() throws Exception {
        // Given:
        Files.write(
                TestClasses.MultipleOverriddenMethods.main(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(
                TestClasses.MultipleOverriddenMethods.interfaceOne(),
                new File(mTestPackageDir, "InterfaceOne.class"));
        Files.write(
                TestClasses.MultipleOverriddenMethods.interfaceTwo(),
                new File(mTestPackageDir, "InterfaceTwo.class"));
        Files.write(
                TestClasses.MultipleOverriddenMethods.implementation(),
                new File(mTestPackageDir, "Implementation.class"));

        // When:
        fullRun("Main", "useInterfaceOne:(Ltest/InterfaceOne;)V", "buildImplementation:()V");

        // Then:
        assertMembersLeft(
                "Main",
                "<init>:()V",
                "useInterfaceOne:(Ltest/InterfaceOne;)V",
                "buildImplementation:()V");
        assertMembersLeft("InterfaceOne", "m:()V");
        assertClassSkipped("InterfaceTwo");
        assertMembersLeft("Implementation", "<init>:()V", "m:()V");
    }

    @Test
    public void overrides_interfaceTwoUsed_classUsed() throws Exception {
        // Given:
        Files.write(
                TestClasses.MultipleOverriddenMethods.main(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(
                TestClasses.MultipleOverriddenMethods.interfaceOne(),
                new File(mTestPackageDir, "InterfaceOne.class"));
        Files.write(
                TestClasses.MultipleOverriddenMethods.interfaceTwo(),
                new File(mTestPackageDir, "InterfaceTwo.class"));
        Files.write(
                TestClasses.MultipleOverriddenMethods.implementation(),
                new File(mTestPackageDir, "Implementation.class"));

        // When:
        fullRun("Main", "useInterfaceTwo:(Ltest/InterfaceTwo;)V", "buildImplementation:()V");

        // Then:
        assertMembersLeft(
                "Main",
                "<init>:()V",
                "useInterfaceTwo:(Ltest/InterfaceTwo;)V",
                "buildImplementation:()V");
        assertMembersLeft("InterfaceTwo", "m:()V");
        assertClassSkipped("InterfaceOne");
        assertMembersLeft("Implementation", "<init>:()V", "m:()V");
    }

    @Test
    public void overrides_twoInterfacesUsed_classUsed() throws Exception {
        // Given:
        Files.write(
                TestClasses.MultipleOverriddenMethods.main(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(
                TestClasses.MultipleOverriddenMethods.interfaceOne(),
                new File(mTestPackageDir, "InterfaceOne.class"));
        Files.write(
                TestClasses.MultipleOverriddenMethods.interfaceTwo(),
                new File(mTestPackageDir, "InterfaceTwo.class"));
        Files.write(
                TestClasses.MultipleOverriddenMethods.implementation(),
                new File(mTestPackageDir, "Implementation.class"));

        // When:
        fullRun(
                "Main",
                "useInterfaceOne:(Ltest/InterfaceOne;)V",
                "useInterfaceTwo:(Ltest/InterfaceTwo;)V",
                "buildImplementation:()V");

        // Then:
        assertMembersLeft(
                "Main",
                "<init>:()V",
                "useInterfaceOne:(Ltest/InterfaceOne;)V",
                "useInterfaceTwo:(Ltest/InterfaceTwo;)V",
                "buildImplementation:()V");
        assertMembersLeft("InterfaceOne", "m:()V");
        assertMembersLeft("InterfaceTwo", "m:()V");
        assertMembersLeft("Implementation", "<init>:()V", "m:()V");
    }

    @Test
    public void overrides_noInterfacesUsed_classUsed() throws Exception {
        // Given:
        Files.write(
                TestClasses.MultipleOverriddenMethods.main(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(
                TestClasses.MultipleOverriddenMethods.interfaceOne(),
                new File(mTestPackageDir, "InterfaceOne.class"));
        Files.write(
                TestClasses.MultipleOverriddenMethods.interfaceTwo(),
                new File(mTestPackageDir, "InterfaceTwo.class"));
        Files.write(
                TestClasses.MultipleOverriddenMethods.implementation(),
                new File(mTestPackageDir, "Implementation.class"));

        // When:
        fullRun("Main", "useImplementation:(Ltest/Implementation;)V", "buildImplementation:()V");

        // Then:
        assertMembersLeft(
                "Main",
                "<init>:()V",
                "useImplementation:(Ltest/Implementation;)V",
                "buildImplementation:()V");
        assertClassSkipped("InterfaceOne");
        assertClassSkipped("InterfaceTwo");
        assertMembersLeft("Implementation", "<init>:()V", "m:()V");
    }

    @Test
    public void annotations_annotatedClass() throws Exception {
        // Given:
        Files.write(
                TestClasses.Annotations.main_annotatedClass(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(
                TestClasses.Annotations.myAnnotation(),
                new File(mTestPackageDir, "MyAnnotation.class"));
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));
        Files.write(TestClasses.Annotations.nested(), new File(mTestPackageDir, "Nested.class"));
        Files.write(
                TestClasses.emptyClass("SomeClass"), new File(mTestPackageDir, "SomeClass.class"));
        Files.write(
                TestClasses.emptyClass("SomeOtherClass"),
                new File(mTestPackageDir, "SomeOtherClass.class"));

        // When:
        fullRun("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        assertMembersLeft("SomeClass", "<init>:()V");
        assertMembersLeft("SomeOtherClass", "<init>:()V");
        assertMembersLeft(
                "MyAnnotation",
                "nested:()[Ltest/Nested;",
                "f:()I",
                "klass:()Ljava/lang/Class;",
                "myEnum:()Ltest/MyEnum;");
        assertMembersLeft("Nested", "name:()Ljava/lang/String;");
    }

    @Test
    public void annotations_annotatedMethod() throws Exception {
        // Given:
        Files.write(
                TestClasses.Annotations.main_annotatedMethod(),
                new File(mTestPackageDir, "Main.class"));
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

        // When:
        fullRun("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        assertMembersLeft("SomeClass", "<init>:()V");
        assertMembersLeft("SomeOtherClass", "<init>:()V");
        assertMembersLeft(
                "MyAnnotation",
                "nested:()[Ltest/Nested;",
                "f:()I",
                "klass:()Ljava/lang/Class;",
                "myEnum:()Ltest/MyEnum;");
        assertMembersLeft("Nested", "name:()Ljava/lang/String;");
    }

    @Test
    public void annotations_annotationsStripped() throws Exception {
        // Given:
        Files.write(
                TestClasses.Annotations.main_annotatedMethod(),
                new File(mTestPackageDir, "Main.class"));
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

        // When:
        fullRun("Main", "notAnnotated:()V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "notAnnotated:()V");
        assertClassSkipped("SomeClass");
        assertClassSkipped("SomeOtherClass");
        assertClassSkipped("MyAnnotation");
        assertClassSkipped("Nested");
    }

    @Test
    public void annotations_keepRules_class() throws Exception {
        Files.write(
                TestClasses.Annotations.main_annotatedClass(),
                new File(mTestPackageDir, "Main.class"));
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

        fullRun(parseKeepRules("-keep @test.MyAnnotation class **"));

        assertMembersLeft("Main", "<init>:()V");
    }

    @Test
    public void annotations_keepRules_atInterface() throws Exception {
        Files.write(
                TestClasses.Annotations.main_annotatedClass(),
                new File(mTestPackageDir, "Main.class"));
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

        fullRun(parseKeepRules("-keep @interface test.MyAnnotation"));

        assertMembersLeft(
                "MyAnnotation",
                "nested:()[Ltest/Nested;",
                "f:()I",
                "klass:()Ljava/lang/Class;",
                "myEnum:()Ltest/MyEnum;");
    }

    @Test
    public void annotations_keepRules_interface() throws Exception {
        Files.write(
                TestClasses.Annotations.main_annotatedClass(),
                new File(mTestPackageDir, "Main.class"));
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

        fullRun(parseKeepRules("-keep interface test.MyAnnotation"));

        assertMembersLeft(
                "MyAnnotation",
                "nested:()[Ltest/Nested;",
                "f:()I",
                "klass:()Ljava/lang/Class;",
                "myEnum:()Ltest/MyEnum;");
    }

    @Test
    public void annotations_keepRules_atClass() throws Exception {
        Files.write(
                TestClasses.Annotations.main_annotatedClass(),
                new File(mTestPackageDir, "Main.class"));
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

        fullRun(parseKeepRules("-keep @class test.MyAnnotation"));

        assertMembersLeft(
                "MyAnnotation",
                "nested:()[Ltest/Nested;",
                "f:()I",
                "klass:()Ljava/lang/Class;",
                "myEnum:()Ltest/MyEnum;");
    }

    @Test
    public void annotations_keepRules_atEnum() throws Exception {
        Files.write(
                TestClasses.Annotations.main_annotatedClass(),
                new File(mTestPackageDir, "Main.class"));
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

        fullRun(parseKeepRules("-keep @enum test.MyAnnotation"));

        assertClassSkipped("MyAnnotation");
    }

    @Test
    public void annotations_keepRules_classRule() throws Exception {
        Files.write(
                TestClasses.Annotations.main_annotatedClass(),
                new File(mTestPackageDir, "Main.class"));
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

        fullRun(parseKeepRules("-keep class test.MyAnnotation"));

        assertMembersLeft(
                "MyAnnotation",
                "nested:()[Ltest/Nested;",
                "f:()I",
                "klass:()Ljava/lang/Class;",
                "myEnum:()Ltest/MyEnum;");
    }

    @Test
    public void annotations_keepRules_wrongAtClass() throws Exception {
        Files.write(
                TestClasses.Annotations.main_annotatedClass(),
                new File(mTestPackageDir, "Main.class"));
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

        fullRun(parseKeepRules("-keep @class test.SomeClass"));

        assertClassSkipped("SomeClass");
    }

    @Test
    public void annotations_keepRules_method() throws Exception {
        Files.write(
                TestClasses.Annotations.main_annotatedMethod(),
                new File(mTestPackageDir, "Main.class"));
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

        fullRun(parseKeepRules("-keep class ** { @test/MyAnnotation *(...);}"));

        assertMembersLeft("Main", "<init>:()V", "main:()V");
    }

    /**
     * Tests that being referenced by a generic class signature is not enough to keep a class. In
     * this case we rewrite the signature instead.
     */
    @Test
    public void signatures_classSignature_onlyUsage() throws Exception {
        // Given:
        Files.write(TestClasses.Signatures.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Signatures.named(), new File(mTestPackageDir, "Named.class"));
        Files.write(TestClasses.Signatures.namedMap(), new File(mTestPackageDir, "NamedMap.class"));
        Files.write(TestClasses.Signatures.hasAge(), new File(mTestPackageDir, "HasAge.class"));

        // When:
        fullRun("Main", "main:(Ltest/NamedMap;)V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:(Ltest/NamedMap;)V");
        assertMembersLeft("NamedMap", "<init>:()V");
        assertClassSkipped("HasAge");

        // Named is skipped, because it's mentioned only in the signature.
        assertClassSkipped("Named");

        ClassNode namedMap = getClassNode(getOutputClassFile("NamedMap"));
        assertThat(namedMap.signature)
                .isEqualTo("<T::Ljava/io/Serializable;:Ljava/lang/Object;>Ljava/lang/Object;");
    }

    /** Tests that class signatures are left alone if the target class is kept. */
    @Test
    public void signatures_classSignature_otherUsages() throws Exception {
        // Given:
        Files.write(TestClasses.Signatures.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Signatures.named(), new File(mTestPackageDir, "Named.class"));
        Files.write(TestClasses.Signatures.namedMap(), new File(mTestPackageDir, "NamedMap.class"));
        Files.write(TestClasses.Signatures.hasAge(), new File(mTestPackageDir, "HasAge.class"));

        // When:
        fullRun("Main", "main:(Ltest/NamedMap;)V", "useNamed:(Ltest/Named;)V");

        // Then:
        assertMembersLeft(
                "Main", "<init>:()V", "main:(Ltest/NamedMap;)V", "useNamed:(Ltest/Named;)V");
        assertMembersLeft("NamedMap", "<init>:()V");
        assertMembersLeft("Named");
        assertClassSkipped("HasAge");

        ClassNode namedMap = getClassNode(getOutputClassFile("NamedMap"));
        assertThat(namedMap.signature)
                .isEqualTo("<T::Ljava/io/Serializable;:Ltest/Named;>Ljava/lang/Object;");
    }

    /**
     * Tests that being referenced by a generic class signature is not enough to keep a class. In
     * this case we rewrite the signature instead.
     */
    @Test
    public void signatures_methodSignature_onlyUsage() throws Exception {
        // Given:
        Files.write(TestClasses.Signatures.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Signatures.named(), new File(mTestPackageDir, "Named.class"));
        Files.write(TestClasses.Signatures.namedMap(), new File(mTestPackageDir, "NamedMap.class"));
        Files.write(TestClasses.Signatures.hasAge(), new File(mTestPackageDir, "HasAge.class"));

        // When:
        fullRun("Main", "callMethod:(Ltest/NamedMap;)V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "callMethod:(Ltest/NamedMap;)V");
        assertMembersLeft("NamedMap", "<init>:()V", "method:(Ljava/util/Collection;)V");
        assertClassSkipped("Named");
        assertClassSkipped("HasAge");

        ClassNode namedMap = getClassNode(getOutputClassFile("NamedMap"));
        MethodNode method = (MethodNode) namedMap.methods.get(1);
        assertThat(method.signature)
                .isEqualTo("<I::Ljava/lang/Object;>(Ljava/util/Collection<TI;>;)V");
    }

    /** Tests that method signatures are left alone if the target class is kept. */
    @Test
    public void signatures_methodSignature_otherUsages() throws Exception {
        // Given:
        Files.write(TestClasses.Signatures.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.Signatures.named(), new File(mTestPackageDir, "Named.class"));
        Files.write(TestClasses.Signatures.namedMap(), new File(mTestPackageDir, "NamedMap.class"));
        Files.write(TestClasses.Signatures.hasAge(), new File(mTestPackageDir, "HasAge.class"));

        // When:
        fullRun("Main", "callMethod:(Ltest/NamedMap;)V", "useHasAge:(Ltest/HasAge;)V");

        // Then:
        assertMembersLeft(
                "Main",
                "<init>:()V",
                "callMethod:(Ltest/NamedMap;)V",
                "useHasAge:(Ltest/HasAge;)V");
        assertMembersLeft("NamedMap", "<init>:()V", "method:(Ljava/util/Collection;)V");
        assertClassSkipped("Named");
        assertMembersLeft("HasAge");

        ClassNode namedMap = getClassNode(getOutputClassFile("NamedMap"));
        MethodNode method = (MethodNode) namedMap.methods.get(1);
        assertThat(method.signature).isEqualTo("<I::Ltest/HasAge;>(Ljava/util/Collection<TI;>;)V");
    }

    @Test
    public void superCalls_directSuperclass() throws Exception {
        // Given:
        Files.write(TestClasses.SuperCalls.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(TestClasses.SuperCalls.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(TestClasses.SuperCalls.ccc(), new File(mTestPackageDir, "Ccc.class"));

        // When:
        fullRun("Ccc", "callBbbMethod:()V");

        // Then:
        assertMembersLeft("Aaa", "<init>:()V");
        assertMembersLeft("Bbb", "<init>:()V", "onlyInBbb:()V");
        assertMembersLeft("Ccc", "<init>:()V", "callBbbMethod:()V");
    }

    @Test
    public void superCalls_indirectSuperclass() throws Exception {
        // Given:
        Files.write(TestClasses.SuperCalls.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(TestClasses.SuperCalls.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(TestClasses.SuperCalls.ccc(), new File(mTestPackageDir, "Ccc.class"));

        // When:
        fullRun("Ccc", "callAaaMethod:()V");

        // Then:
        assertMembersLeft("Aaa", "<init>:()V", "onlyInAaa:()V");
        assertMembersLeft("Bbb", "<init>:()V");
        assertMembersLeft("Ccc", "<init>:()V", "callAaaMethod:()V");
    }

    @Test
    public void superCalls_both() throws Exception {
        // Given:
        Files.write(TestClasses.SuperCalls.aaa(), new File(mTestPackageDir, "Aaa.class"));
        Files.write(TestClasses.SuperCalls.bbb(), new File(mTestPackageDir, "Bbb.class"));
        Files.write(TestClasses.SuperCalls.ccc(), new File(mTestPackageDir, "Ccc.class"));

        // When:
        fullRun("Ccc", "callOverriddenMethod:()V");

        // Then:
        assertMembersLeft("Aaa", "<init>:()V");
        assertMembersLeft("Bbb", "<init>:()V", "overridden:()V");
        assertMembersLeft("Ccc", "<init>:()V", "callOverriddenMethod:()V");
    }

    @Test
    public void innerClasses_useOuter() throws Exception {
        // Given:
        Files.write(InnerClasses.main_useOuterClass(), new File(mTestPackageDir, "Main.class"));
        Files.write(InnerClasses.outer(), new File(mTestPackageDir, "Outer.class"));
        Files.write(InnerClasses.inner(), new File(mTestPackageDir, "Outer$Inner.class"));
        Files.write(
                InnerClasses.staticInner(), new File(mTestPackageDir, "Outer$StaticInner.class"));
        Files.write(InnerClasses.anonymous(), new File(mTestPackageDir, "Outer$1.class"));

        // When:
        fullRun("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        assertMembersLeft("Outer", "outerMethod:()V", "<init>:()V");
        assertClassSkipped("Outer$Inner");
        assertClassSkipped("Outer$StaticInner");
        assertClassSkipped("Outer$1");

        assertNoInnerClasses("Outer");
        assertNoInnerClasses("Main");
    }

    @Test
    public void innerClasses_useOuter_anonymous() throws Exception {
        // Given:
        Files.write(
                InnerClasses.main_useOuterClass_makeAnonymousClass(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(InnerClasses.outer(), new File(mTestPackageDir, "Outer.class"));
        Files.write(InnerClasses.inner(), new File(mTestPackageDir, "Outer$Inner.class"));
        Files.write(
                InnerClasses.staticInner(), new File(mTestPackageDir, "Outer$StaticInner.class"));
        Files.write(InnerClasses.anonymous(), new File(mTestPackageDir, "Outer$1.class"));

        // When:
        fullRun("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        assertMembersLeft("Outer", "makeRunnable:()V", "<init>:()V");
        assertMembersLeft("Outer$1", "run:()V", "<init>:(Ltest/Outer;)V", "this$0:Ltest/Outer;");
        assertClassSkipped("Outer$Inner");
        assertClassSkipped("Outer$StaticInner");

        assertSingleInnerClassesEntry("Outer", null, "test/Outer$1");
        assertNoInnerClasses("Main");
    }

    @Test
    public void innerClasses_useInner() throws Exception {
        // Given:
        Files.write(InnerClasses.main_useInnerClass(), new File(mTestPackageDir, "Main.class"));
        Files.write(InnerClasses.outer(), new File(mTestPackageDir, "Outer.class"));
        Files.write(InnerClasses.inner(), new File(mTestPackageDir, "Outer$Inner.class"));
        Files.write(
                InnerClasses.staticInner(), new File(mTestPackageDir, "Outer$StaticInner.class"));
        Files.write(InnerClasses.anonymous(), new File(mTestPackageDir, "Outer$1.class"));

        // When:
        fullRun("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        assertMembersLeft("Outer", "<init>:()V");
        assertMembersLeft(
                "Outer$Inner", "innerMethod:()V", "<init>:(Ltest/Outer;)V", "this$0:Ltest/Outer;");
        assertClassSkipped("Outer$StaticInner");
        assertClassSkipped("Outer$1");

        assertSingleInnerClassesEntry("Outer", "test/Outer", "test/Outer$Inner");
        assertSingleInnerClassesEntry("Outer$Inner", "test/Outer", "test/Outer$Inner");
        assertSingleInnerClassesEntry("Main", "test/Outer", "test/Outer$Inner");
    }

    @Test
    public void innerClasses_useStaticInner() throws Exception {
        // Given:
        Files.write(
                InnerClasses.main_useStaticInnerClass(), new File(mTestPackageDir, "Main.class"));
        Files.write(InnerClasses.outer(), new File(mTestPackageDir, "Outer.class"));
        Files.write(InnerClasses.inner(), new File(mTestPackageDir, "Outer$Inner.class"));
        Files.write(
                InnerClasses.staticInner(), new File(mTestPackageDir, "Outer$StaticInner.class"));
        Files.write(InnerClasses.anonymous(), new File(mTestPackageDir, "Outer$1.class"));

        // When:
        fullRun("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        assertMembersLeft("Outer", "<init>:()V");
        assertClassSkipped("Outer$Inner");
        assertClassSkipped("Outer$1");
        assertMembersLeft("Outer$StaticInner", "<init>:()V", "staticInnerMethod:()V");

        assertSingleInnerClassesEntry("Outer", "test/Outer", "test/Outer$StaticInner");
        assertSingleInnerClassesEntry("Main", "test/Outer", "test/Outer$StaticInner");
        assertSingleInnerClassesEntry("Outer$StaticInner", "test/Outer", "test/Outer$StaticInner");
    }

    @Test
    public void innerClasses_notUsed() throws Exception {
        // Given:
        Files.write(InnerClasses.main_empty(), new File(mTestPackageDir, "Main.class"));
        Files.write(InnerClasses.outer(), new File(mTestPackageDir, "Outer.class"));
        Files.write(InnerClasses.inner(), new File(mTestPackageDir, "Outer$Inner.class"));
        Files.write(
                InnerClasses.staticInner(), new File(mTestPackageDir, "Outer$StaticInner.class"));
        Files.write(InnerClasses.anonymous(), new File(mTestPackageDir, "Outer$1.class"));

        // When:
        fullRun("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        assertClassSkipped("Outer");
        assertClassSkipped("Outer$Inner");
        assertClassSkipped("Outer$StaticInner");
        assertClassSkipped("Outer$1");

        assertNoInnerClasses("Main");
    }

    @Test
    public void staticMethods() throws Exception {
        // Given:
        Files.write(TestClasses.StaticMembers.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.StaticMembers.utils(), new File(mTestPackageDir, "Utils.class"));

        // When:
        fullRun("Main", "callStaticMethod:()Ljava/lang/Object;");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "callStaticMethod:()Ljava/lang/Object;");
        assertMembersLeft("Utils", "<init>:()V", "staticMethod:()Ljava/lang/Object;");
    }

    @Test
    public void staticFields_uninitialized() throws Exception {
        // Given:
        Files.write(TestClasses.StaticMembers.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.StaticMembers.utils(), new File(mTestPackageDir, "Utils.class"));

        // When:
        fullRun("Main", "getStaticField:()Ljava/lang/Object;");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "getStaticField:()Ljava/lang/Object;");
        assertMembersLeft("Utils", "<init>:()V", "staticField:Ljava/lang/Object;");
    }

    @Test
    public void reflection_instanceOf() throws Exception {
        // Given:
        Files.write(Reflection.main_instanceOf(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.emptyClass("Foo"), new File(mTestPackageDir, "Foo.class"));

        // When:
        fullRun("Main", "main:(Ljava/lang/Object;)Z");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:(Ljava/lang/Object;)Z");
        assertMembersLeft("Foo", "<init>:()V");
    }

    @Test
    public void reflection_classForName() throws Exception {
        // Given:
        Files.write(Reflection.main_classForName(), new File(mTestPackageDir, "Main.class"));
        Files.write(
                Reflection.classWithFields(), new File(mTestPackageDir, "ClassWithFields.class"));

        // When:
        fullRun("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        assertMembersLeft("ClassWithFields", "<init>:()V");
    }

    @Test
    public void reflection_classForName_dynamic() throws Exception {
        // Given:
        Files.write(
                Reflection.main_classForName_dynamic(), new File(mTestPackageDir, "Main.class"));
        Files.write(
                Reflection.classWithFields(), new File(mTestPackageDir, "ClassWithFields.class"));

        // When:
        fullRun("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        assertClassSkipped("ClassWithFields");
    }

    @Test
    public void reflection_atomicIntegerFieldUpdater() throws Exception {
        // Given:
        Files.write(
                Reflection.main_atomicIntegerFieldUpdater(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(
                Reflection.classWithFields(), new File(mTestPackageDir, "ClassWithFields.class"));

        // When:
        fullRun("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        assertMembersLeft("ClassWithFields", "<init>:()V", "intField:I");
    }

    @Test
    public void reflection_atomicLongFieldUpdater() throws Exception {
        // Given:
        Files.write(
                Reflection.main_atomicLongFieldUpdater(), new File(mTestPackageDir, "Main.class"));
        Files.write(
                Reflection.classWithFields(), new File(mTestPackageDir, "ClassWithFields.class"));

        // When:
        fullRun("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        assertMembersLeft("ClassWithFields", "<init>:()V", "longField:J");
    }

    @Test
    public void reflection_atomicReferenceFieldUpdater() throws Exception {
        // Given:
        Files.write(
                Reflection.main_atomicReferenceFieldUpdater(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(
                Reflection.classWithFields(), new File(mTestPackageDir, "ClassWithFields.class"));

        // When:
        fullRun("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        assertMembersLeft("ClassWithFields", "<init>:()V", "stringField:Ljava/lang/String;");
    }

    @Test
    public void reflection_classLiteral() throws Exception {
        // Given:
        Files.write(Reflection.main_classLiteral(), new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.emptyClass("Foo"), new File(mTestPackageDir, "Foo.class"));

        // When:
        fullRun("Main", "main:()Ljava/lang/Object;");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()Ljava/lang/Object;");
        assertMembersLeft("Foo", "<init>:()V");
    }

    @Test
    public void testTryCatch() throws Exception {
        // Given:
        Files.write(TestClasses.TryCatch.main(), new File(mTestPackageDir, "Main.class"));
        Files.write(
                TestClasses.TryCatch.customException(),
                new File(mTestPackageDir, "CustomException.class"));

        // When:
        fullRun("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V", "helper:()V");
        assertMembersLeft("CustomException", "<init>:()V");
    }

    @Test
    public void testTryFinally() throws Exception {
        // Given:
        Files.write(
                TestClasses.TryCatch.main_tryFinally(), new File(mTestPackageDir, "Main.class"));

        // When:
        fullRun("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V", "helper:()V");
    }

    @Test
    public void abstractClasses_callToInterfaceMethodInAbstractClass() throws Exception {
        // Given:
        Files.write(
                TestClasses.AbstractClasses.myInterface(),
                new File(mTestPackageDir, "MyInterface.class"));
        Files.write(
                TestClasses.AbstractClasses.abstractImpl(),
                new File(mTestPackageDir, "AbstractImpl.class"));
        Files.write(
                TestClasses.AbstractClasses.realImpl(),
                new File(mTestPackageDir, "RealImpl.class"));

        // When:
        fullRun("RealImpl", "main:()V");

        // Then:
        assertMembersLeft("MyInterface", "m:()V");
        assertMembersLeft("RealImpl", "<init>:()V", "main:()V", "m:()V");
        assertMembersLeft("AbstractImpl", "<init>:()V", "helper:()V");
    }

    @Test
    public void primitives() throws Exception {
        // Given:
        Files.write(TestClasses.Primitives.main(), new File(mTestPackageDir, "Main.class"));

        // When:
        fullRun("Main", "ldc:()Ljava/lang/Object;", "checkcast:(Ljava/lang/Object;)[I");

        // Then:
        assertMembersLeft(
                "Main",
                "<init>:()V",
                "ldc:()Ljava/lang/Object;",
                "checkcast:(Ljava/lang/Object;)[I");
    }

    @Test
    public void invalidReferences_sunMiscUnsafe() throws Exception {
        // Given:
        Files.write(
                TestClasses.InvalidReferences.main_sunMiscUnsafe(),
                new File(mTestPackageDir, "Main.class"));

        // When:
        fullRun("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        mExpectedWarnings = 1;
    }

    @Test
    public void invalidReferences_Instrumentation() throws Exception {
        // Given:
        Files.write(
                TestClasses.InvalidReferences.main_javaInstrumentation(),
                new File(mTestPackageDir, "Main.class"));

        // When:
        fullRun("Main", "main:()V");

        // Make sure we kept the method, even though we encountered unrecognized classes.
        assertMembersLeft(
                "Main",
                "<init>:()V",
                "main:()V",
                "transform:(Ljava/lang/ClassLoader;Ljava/lang/String;Ljava/lang/Class;Ljava/security/ProtectionDomain;[B)[B");
        assertImplements("Main", "java/lang/instrument/ClassFileTransformer");
        mExpectedWarnings = 1;
    }

    @Test
    public void invalidReferences_suprerclasses() throws Exception {
        // Given:
        Files.write(
                TestClasses.InvalidReferences.main_madeUpSuperclass(),
                new File(mTestPackageDir, "Main.class"));

        // When:
        fullRun("Main", "main:()V");

        // Then:
        assertMembersLeft("Main", "<init>:()V", "main:()V");
        mExpectedWarnings = 1;

        Pair<String, String> warning =
                Iterables.getOnlyElement(mShrinkerLogger.getWarningsEmitted());
        assertThat(warning.getFirst()).isEqualTo("test/Main");
        assertThat(warning.getSecond()).isEqualTo("com/madeup/Superclass");
    }

    @Test
    public void duplicateClasses() throws Exception {
        Files.write(
                TestClasses.classWithEmptyMethods("Foo", "a:()V"),
                new File(mTestPackageDir, "Foo1.class"));

        Files.write(
                TestClasses.classWithEmptyMethods("Foo", "b:()V"),
                new File(mTestPackageDir, "Foo2.class"));

        fullRun("Foo", "a:()V", "b:()V");

        // Either 'a' or 'b', plus default constructor.
        Set<String> members = getMembers("Foo");
        assertThat(members).hasSize(2);
    }

    @Test
    public void commasInKeepRules() throws Exception {
        Files.write(
                TestClasses.classWithEmptyMethods("Aaa", "a:()V"),
                new File(mTestPackageDir, "Aaa.class"));
        Files.write(
                TestClasses.classWithEmptyMethods("Bbb", "b:()V"),
                new File(mTestPackageDir, "Bbb.class"));
        Files.write(
                TestClasses.classWithEmptyMethods("Ccc", "c:()V"),
                new File(mTestPackageDir, "Ccc.class"));

        fullRun(parseKeepRules("-keep class test.Aaa,**.Ccc"));

        assertMembersLeft("Aaa", "<init>:()V");
        assertMembersLeft("Ccc", "<init>:()V");
        assertClassSkipped("Bbb");
    }

    @Test
    public void modifiers_volatile() throws Exception {
        Files.write(TestClasses.Modifiers.main(), new File(mTestPackageDir, "Main.class"));

        // Volatile and bridge use the same bitmask.
        fullRun(parseKeepRules("-keep class test.Main { volatile *; }"));

        assertMembersLeft("Main", "<init>:()V", "volatileField:I");
    }

    @Test
    public void modifiers_bridge() throws Exception {
        Files.write(TestClasses.Modifiers.main(), new File(mTestPackageDir, "Main.class"));

        // Volatile and bridge use the same bitmask.
        fullRun(parseKeepRules("-keep class test.Main { bridge *; }"));

        assertMembersLeft("Main", "<init>:()V", "bridgeMethod:()V");
    }

    @Test
    public void enums() throws Exception {
        Files.write(TestClasses.Annotations.myEnum(), new File(mTestPackageDir, "MyEnum.class"));

        fullRun(
                parseKeepRules(
                        "-keep enum * { public static **[] values(); public static ** valueOf(java.lang.String); }"));

        assertMembersLeft(
                "MyEnum",
                "<clinit>:()V",
                "<init>:(Ljava/lang/String;I)V",
                "values:()[Ltest/MyEnum;",
                "valueOf:(Ljava/lang/String;)Ltest/MyEnum;",
                "ONE:Ltest/MyEnum;",
                "TWO:Ltest/MyEnum;",
                "$VALUES:[Ltest/MyEnum;");
    }

    @Test
    public void target() throws Exception {
        Files.write(TestClasses.SimpleScenario.aaa(), new File(mTestPackageDir, "Aaa.class"));
        fullRun("Aaa", "aaa:()V");
        File outputClassFile = getOutputClassFile("Aaa");
        checkBytecodeVersion(outputClassFile, Opcodes.V1_6);
        FileUtils.delete(outputClassFile);

        ProguardConfig config = new ProguardConfig();
        config.parse("-target 1.8");
        mFullRunShrinker = createFullRunShrinker(config.getFlags().getBytecodeVersion());
        fullRun("Aaa", "aaa:()V");
        checkBytecodeVersion(outputClassFile, Opcodes.V1_8);
    }

    @Test
    public void whyAreYouKeeping() throws Exception {
        // Given:
        Files.write(
                TestClasses.VirtualCalls.main_parentChild(),
                new File(mTestPackageDir, "Main.class"));
        Files.write(TestClasses.VirtualCalls.parent(), new File(mTestPackageDir, "Parent.class"));
        Files.write(TestClasses.VirtualCalls.child(), new File(mTestPackageDir, "Child.class"));

        // When:
        FullRunShrinker<String>.Result result =
                fullRun(
                        parseKeepRules(
                                "-keep class test.Main { *; }\n -whyareyoukeeping class test.Parent { void onlyInParent(); }"));

        // Then:
        assertThat(result.traces.get("test/Parent.onlyInParent:()V").toList())
                .containsExactly(
                        Pair.of(
                                "test/Parent.onlyInParent:()V",
                                DependencyType.REQUIRED_CODE_REFERENCE),
                        Pair.of("test/Main.main:()V", DependencyType.REQUIRED_KEEP_RULES))
                .inOrder();
    }

    @Test
    public void lambdas_staticMethod() throws Exception {
        Files.write(TestClasses.Lambdas.samType(), new File(mTestPackageDir, "SamType.class"));
        Files.write(TestClasses.Lambdas.lambdas(), new File(mTestPackageDir, "Lambdas.class"));

        fullRun("Lambdas", "makeSamObjectStatic:()V");

        assertMembersLeft(
                "Lambdas",
                "<init>:()V",
                "makeSamObjectStatic:()V",
                "lambda$makeSamObjectStatic$1:(I)V");
        assertMembersLeft("SamType");
    }

    @Test
    public void lambdas_instanceMethod() throws Exception {
        Files.write(TestClasses.Lambdas.samType(), new File(mTestPackageDir, "SamType.class"));
        Files.write(TestClasses.Lambdas.lambdas(), new File(mTestPackageDir, "Lambdas.class"));

        fullRun("Lambdas", "makeSamObject:()V");

        assertMembersLeft(
                "Lambdas", "<init>:()V", "makeSamObject:()V", "lambda$makeSamObject$0:(I)V");
        assertMembersLeft("SamType");
    }

    @Test
    public void room_databaseSubclass() throws Exception {
        File roomDatabaseClassFile =
                FileUtils.join(
                        mAppClassesDir,
                        "android",
                        "arch",
                        "persistence",
                        "room",
                        "RoomDatabase.class");
        Files.createParentDirs(roomDatabaseClassFile);
        Files.write(
                TestClassesGenerator.emptyClass("android/arch/persistence/room", "RoomDatabase"),
                roomDatabaseClassFile);
        Files.write(
                TestClassesGenerator.emptyClass(
                        "test", "AppDatabase", "android/arch/persistence/room/RoomDatabase"),
                new File(mTestPackageDir, "SamType.class"));

        fullRun("AppDatabase", "<init>:()V");

        assertMembersLeft("AppDatabase", "<init>:()V");
        assertThat(getOutputClassFile("android/arch/persistence/room", "RoomDatabase")).isFile();
    }

    protected static void checkBytecodeVersion(File classFile, int version) throws IOException {
        try (DataInputStream input = new DataInputStream(new FileInputStream(classFile))) {
            assertThat(input.readInt()).named("magic bytes").isEqualTo(0xcafebabe);
            input.readUnsignedShort(); // Ignore minor version.
            assertThat(input.readUnsignedShort()).named("major version").isEqualTo(version);
        }
    }

    private void forceEmptyIncrementalRun() throws Exception {
        incrementalRun(Collections.emptyMap());
    }
}
