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

package com.android.build.gradle.tasks;

import static com.android.SdkConstants.FN_APK_CLASSES_DEX;
import static com.android.build.gradle.internal.TaskManager.ATOM_SUFFIX;

import com.android.annotations.NonNull;
import com.android.build.api.transform.TransformException;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.DexOptions;
import com.android.builder.model.AndroidAtom;
import com.android.dex.Dex;
import com.android.dx.command.dexer.DxContext;
import com.android.dx.merge.CollisionPolicy;
import com.android.dx.merge.DexMerger;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableSet;

import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

/**
 * Task to merge the atom dex with the final R class.
 */
public class MergeDexAtomResClass extends BaseTask {

    @TaskAction
    public void TaskAction() throws TransformException, IOException {
        ProcessOutputHandler outputHandler = new ParsingProcessOutputHandler(
                new ToolOutputParser(new DexParser(), Message.Kind.ERROR, getILogger()),
                new ToolOutputParser(new DexParser(), getILogger()),
                getBuilder().getErrorReporter());
        DexOptions dexOptions = DefaultDexOptions.copyOf(
                scope.getGlobalScope().getExtension().getDexOptions());

        // First dex the R.class file to the temporary directory.
        try {
            getBuilder().convertByteCode(
                    ImmutableSet.of(getResClassDir()),
                    getTempDir(),
                    false,
                    null,
                    dexOptions,
                    outputHandler);
        } catch (Exception e) {
            throw new TransformException(e);
        }

        // Then merge the two dexes in the final location.
        Dex[] input = new Dex[2];
        input[0] = new Dex(new File(getTempDir(), FN_APK_CLASSES_DEX));
        input[1] = new Dex(new File(getAtomDexDir(), FN_APK_CLASSES_DEX));

        try (ProcessOutput output = outputHandler.createOutput()) {
            DxContext dxContext = new DxContext(
                    output.getStandardOutput(), output.getErrorOutput());
            Dex merged = new DexMerger(input, CollisionPolicy.KEEP_FIRST, dxContext).merge();
            merged.writeTo(new File(getOutputDir(), FN_APK_CLASSES_DEX));
        }
    }

    @InputDirectory
    @NonNull
    public File getResClassDir() {
        return resClassDir;
    }

    public void setResClassDir(File resClassDir) {
        this.resClassDir = resClassDir;
    }

    @InputDirectory
    @NonNull
    public File getAtomDexDir() {
        return atomDexDir;
    }

    public void setAtomDexDir(File atomDexDir) {
        this.atomDexDir = atomDexDir;
    }

    @OutputDirectory
    @NonNull
    public File getTempDir() {
        return tempDir;
    }

    public void setTempDir(File tempDir) {
        this.tempDir = tempDir;
    }

    @OutputDirectory
    @NonNull
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(@NonNull File outputDir) {
        this.outputDir = outputDir;
    }

    private VariantScope scope;
    private File atomDexDir;
    private File resClassDir;
    private File tempDir;
    private File outputDir;

    public static class ConfigAction implements TaskConfigAction<MergeDexAtomResClass> {

        private VariantScope scope;
        private AndroidAtom androidAtom;

        public ConfigAction(
                @NonNull VariantScope scope,
                @NonNull AndroidAtom androidAtom) {
            this.scope = scope;
            this.androidAtom = androidAtom;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("dex",
                    StringHelper.capitalize(androidAtom.getAtomName()) + ATOM_SUFFIX);
        }

        @NonNull
        @Override
        public Class<MergeDexAtomResClass> getType() {
            return MergeDexAtomResClass.class;
        }

        @Override
        public void execute(@NonNull MergeDexAtomResClass task) {
            task.setVariantName(scope.getFullVariantName());
            task.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());

            task.setAtomDexDir(androidAtom.getDexFolder());
            task.setResClassDir(scope.getJavaOutputDir(androidAtom));

            task.setTempDir(scope.getIncrementalDir(
                    androidAtom.getAtomName() + "-" + scope.getFullVariantName()));
            task.setOutputDir(scope.getDexOutputFolder(androidAtom));
            task.scope = scope;
        }
    }

}
