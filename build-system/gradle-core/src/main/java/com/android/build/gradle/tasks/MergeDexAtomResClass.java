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
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ATOM_DEX;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;

import com.android.annotations.NonNull;
import com.android.build.api.transform.TransformException;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.DexOptions;
import com.android.dex.Dex;
import com.android.dx.command.dexer.DxContext;
import com.android.dx.merge.CollisionPolicy;
import com.android.dx.merge.DexMerger;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.Collection;
import java.util.regex.Pattern;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.TaskAction;

/** Task to merge all the atoms dex with their final R classes. */
public class MergeDexAtomResClass extends BaseTask {

    @TaskAction
    public void TaskAction() throws InterruptedException {
        DexOptions dexOptions =
                DefaultDexOptions.copyOf(scope.getGlobalScope().getExtension().getDexOptions());
        ProcessOutputHandler outputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(new DexParser(), Message.Kind.ERROR, getILogger()),
                        new ToolOutputParser(new DexParser(), getILogger()),
                        getBuilder().getErrorReporter());
        WaitableExecutor<Void> executor = WaitableExecutor.useGlobalSharedThreadPool();

        for (AtomConfig.AtomInfo atomInfo : atomConfigTask.getAtomInfoCollection()) {
            executor.execute(
                    () -> {
                        // Base atom or atom with no resources. Copy the dex over to the final location.
                        if (FileUtils.find(atomInfo.getJavaClassDir(), Pattern.compile("\\.class"))
                                .isEmpty()) {
                            File atomDex = new File(atomInfo.getAtomDexDir(), FN_APK_CLASSES_DEX);
                            File outDex = new File(atomInfo.getFinalDexDir(), FN_APK_CLASSES_DEX);
                            FileUtils.copyFile(atomDex, outDex);
                            return null;
                        }

                        // First dex the R.class file to the temporary directory.
                        try {
                            getBuilder()
                                    .getDexByteCodeConverter()
                                    .convertByteCode(
                                            ImmutableSet.of(atomInfo.getJavaClassDir()),
                                            atomInfo.getDexTempDir(),
                                            false,
                                            null,
                                            dexOptions,
                                            outputHandler,
                                            getMinSdkVersionForDx());
                        } catch (Exception e) {
                            throw new TransformException(e);
                        }

                        // Then merge the two dex files in the final location.
                        Dex[] input = new Dex[2];
                        input[0] = new Dex(new File(atomInfo.getDexTempDir(), FN_APK_CLASSES_DEX));
                        input[1] = new Dex(new File(atomInfo.getAtomDexDir(), FN_APK_CLASSES_DEX));

                        try (ProcessOutput output = outputHandler.createOutput()) {
                            DxContext dxContext =
                                    new DxContext(
                                            output.getStandardOutput(), output.getErrorOutput());
                            Dex merged =
                                    new DexMerger(input, CollisionPolicy.KEEP_FIRST, dxContext)
                                            .merge();
                            merged.writeTo(new File(atomInfo.getFinalDexDir(), FN_APK_CLASSES_DEX));
                        }

                        return null;
                    });
        }
        executor.waitForTasksWithQuickFail(true);
    }

    @InputFiles
    @NonNull
    public Collection<File> getJavaClassDirsCollection() {
        return atomConfigTask.getJavaClassDirsCollection();
    }

    @InputFiles
    @NonNull
    public FileCollection getAtomDexDirCollection() {
        return atomDexDirs.getArtifactFiles();
    }

    @OutputDirectories
    @NonNull
    public Collection<File> getTempDirsCollection() {
        return atomConfigTask.getDexTempDirsCollection();
    }

    @OutputDirectories
    @NonNull
    public Collection<File> getOutputDirsCollection() {
        return atomConfigTask.getFinalDexDirsCollection();
    }

    @Input
    @Optional
    public Integer getMinSdkVersionForDx() {
        return scope.getMinSdkForDx();
    }

    private VariantScope scope;
    private ArtifactCollection atomDexDirs;
    private AtomConfig atomConfigTask;

    public static class ConfigAction implements TaskConfigAction<MergeDexAtomResClass> {

        private final VariantScope scope;

        public ConfigAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("dexAll", "Atoms");
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
            task.scope = scope;

            task.atomConfigTask = scope.getVariantData().atomConfigTask;
            task.atomDexDirs = scope.getArtifactCollection(COMPILE_CLASSPATH, MODULE, ATOM_DEX);
        }
    }

}
