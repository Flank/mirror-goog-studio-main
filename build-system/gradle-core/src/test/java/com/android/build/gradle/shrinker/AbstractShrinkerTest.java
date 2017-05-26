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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.shrinker.parser.BytecodeVersion;
import com.android.build.gradle.shrinker.parser.Flags;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.sdklib.SdkVersionInfo;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.LoggerFactory;

/** Common code for testing shrinker runs. */
public abstract class AbstractShrinkerTest {
    @Rule public TemporaryFolder tmpDir = new TemporaryFolder();
    protected File mTestPackageDir;
    protected File mAppClassesDir;
    protected File mOutDir;
    protected Collection<TransformInput> mInputs;
    protected File mIncrementalDir;
    protected TransformOutputProvider mOutput;
    protected DirectoryInput mDirectoryInput;
    protected FullRunShrinker<String> mFullRunShrinker;
    protected ShrinkerLogger mShrinkerLogger;
    protected int mExpectedWarnings;

    @Before
    public void setUp() throws Exception {
        mShrinkerLogger =
                new ShrinkerLogger(Collections.emptyList(), LoggerFactory.getLogger(getClass()));
        mTestPackageDir = tmpDir.newFolder("app-classes", "test");
        mAppClassesDir = mTestPackageDir.getParentFile();
        File classDir = new File(tmpDir.getRoot(), "app-classes");
        mOutDir = tmpDir.newFolder("out");
        mIncrementalDir = tmpDir.newFolder("incremental");

        mDirectoryInput = mock(DirectoryInput.class);
        when(mDirectoryInput.getFile()).thenReturn(classDir);
        when(mDirectoryInput.getName()).thenReturn("randomName");
        TransformInput transformInput = mock(TransformInput.class);
        when(transformInput.getDirectoryInputs()).thenReturn(ImmutableList.of(mDirectoryInput));
        mOutput = mock(TransformOutputProvider.class);
        // we probably want a better mock that than so that we can return different dir depending
        // on inputs.
        when(mOutput.getContentLocation(
                        Mockito.anyString(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(Format.class)))
                .thenReturn(mOutDir);

        mInputs = ImmutableList.of(transformInput);
        mFullRunShrinker = createFullRunShrinker(null);
    }

    @NonNull
    protected final FullRunShrinker<String> createFullRunShrinker(
            @Nullable BytecodeVersion bytecodeVersion) {
        return new FullRunShrinker<>(
                WaitableExecutor.useGlobalSharedThreadPool(),
                JavaSerializationShrinkerGraph.empty(mIncrementalDir),
                getPlatformJars(),
                mShrinkerLogger,
                bytecodeVersion);
    }

    @Before
    public void resetWarningsCounter() throws Exception {
        mExpectedWarnings = 0;
    }

    @After
    public void checkLogger() throws Exception {
        assertEquals(mExpectedWarnings, mShrinkerLogger.getWarningsCount());
    }

    protected void assertClassSkipped(String className) {
        assertFalse(
                "Class " + className + " exists in output.",
                getOutputClassFile(className).exists());
    }

    protected void assertImplements(String className, String interfaceName) throws IOException {
        File classFile = getOutputClassFile(className);
        assertThat(getInterfaceNames(classFile)).contains(interfaceName);
    }

    protected static Set<String> getInterfaceNames(File classFile) throws IOException {
        ClassNode classNode = getClassNode(classFile);

        if (classNode.interfaces == null) {
            return ImmutableSet.of();
        } else {
            //noinspection unchecked
            return ImmutableSet.copyOf(classNode.interfaces);
        }
    }

    private static List<InnerClassNode> getInnerClasses(File classFile) throws IOException {
        ClassNode classNode = getClassNode(classFile);
        if (classNode.innerClasses == null) {
            return ImmutableList.of();
        } else {
            //noinspection unchecked
            return classNode.innerClasses;
        }
    }

    protected void assertSingleInnerClassesEntry(String className, String outer, String inner)
            throws IOException {
        List<InnerClassNode> innerClasses = getInnerClasses(getOutputClassFile(className));
        assertThat(innerClasses).hasSize(1);
        assertThat(innerClasses.get(0).outerName).isEqualTo(outer);
        assertThat(innerClasses.get(0).name).isEqualTo(inner);
    }

    protected void assertNoInnerClasses(String className) throws IOException {
        List<InnerClassNode> innerClasses = getInnerClasses(getOutputClassFile(className));
        assertThat(innerClasses).isEmpty();
    }

    protected void assertMembersLeft(String className, String... expectedMembers)
            throws IOException {
        assertThat(getMembers(className))
                .named(className + " members")
                .containsExactlyElementsIn(Arrays.asList(expectedMembers));
    }

    protected Set<String> getMembers(String className) throws IOException {
        File outFile = getOutputClassFile(className);

        assertTrue(
                String.format("Class %s does not exist in output.", className), outFile.exists());

        return getMembers(outFile);
    }

    @NonNull
    protected static Set<File> getPlatformJars() {
        File androidHome = TestUtils.getSdk();
        File androidJar =
                FileUtils.join(
                        androidHome,
                        SdkConstants.FD_PLATFORMS,
                        "android-" + SdkVersionInfo.HIGHEST_KNOWN_STABLE_API,
                        "android.jar");

        assertTrue(androidJar.getAbsolutePath() + " does not exist.", androidJar.exists());

        return ImmutableSet.of(androidJar);
    }

    private static Set<String> getMembers(File classFile) throws IOException {
        ClassNode classNode = getClassNode(classFile);

        //noinspection unchecked - ASM API
        return Stream.concat(
                        ((List<MethodNode>) classNode.methods)
                                .stream()
                                .map(methodNode -> methodNode.name + ":" + methodNode.desc),
                        ((List<FieldNode>) classNode.fields)
                                .stream()
                                .map(fieldNode -> fieldNode.name + ":" + fieldNode.desc))
                .collect(Collectors.toSet());
    }

    @NonNull
    protected static ClassNode getClassNode(File classFile) throws IOException {
        ClassReader classReader = new ClassReader(Files.toByteArray(classFile));
        ClassNode classNode = new ClassNode(Opcodes.ASM5);
        classReader.accept(
                classNode,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return classNode;
    }

    @NonNull
    protected File getOutputClassFile(@NonNull String className) {
        return getOutputClassFile("test", className);
    }

    @NonNull
    protected File getOutputClassFile(@NonNull String packageName, @NonNull String className) {
        return FileUtils.join(mOutDir, packageName, className + ".class");
    }

    @NonNull
    protected static Flags parseKeepRules(String rules) {
        ProguardConfig config = new ProguardConfig();
        config.parse(rules);
        return config.getFlags();
    }

    protected FullRunShrinker<String>.Result fullRun(Flags flags) throws IOException {
        return fullRun(
                ProguardParserKeepRules.keepRules(flags, mShrinkerLogger),
                ProguardParserKeepRules.whyAreYouKeepingRules(flags, mShrinkerLogger));
    }

    @NonNull
    private FullRunShrinker<String>.Result fullRun(
            @NonNull KeepRules keepRules, @Nullable KeepRules whyAreYouKeepingRules)
            throws IOException {
        return mFullRunShrinker.run(
                mInputs,
                Collections.emptyList(),
                mOutput,
                ImmutableMap.of(AbstractShrinker.CounterSet.SHRINK, keepRules),
                whyAreYouKeepingRules,
                true);
    }

    protected FullRunShrinker<String>.Result fullRun(String className, String... methods)
            throws IOException {
        return fullRun(new TestKeepRules(className, methods), null);
    }

    protected void incrementalRun(Map<String, Status> changes) throws Exception {
        IncrementalShrinker<String> incrementalShrinker =
                new IncrementalShrinker<>(
                        WaitableExecutor.useGlobalSharedThreadPool(),
                        JavaSerializationShrinkerGraph.readFromDir(
                                mIncrementalDir, this.getClass().getClassLoader()),
                        mShrinkerLogger,
                        null);

        Map<File, Status> files = Maps.newHashMap();
        for (Map.Entry<String, Status> entry : changes.entrySet()) {
            files.put(new File(mTestPackageDir, entry.getKey() + ".class"), entry.getValue());
        }

        when(mDirectoryInput.getChangedFiles()).thenReturn(files);

        incrementalShrinker.incrementalRun(mInputs, mOutput);
    }
}
