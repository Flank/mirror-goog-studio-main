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

import com.android.annotations.NonNull;
import com.android.build.api.transform.TransformException;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.DexOptions;
import com.android.builder.dependency.level2.AtomDependency;
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
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.TaskAction;

/** Task to merge all the atoms dex with their final R classes. */
public class MergeDexAtomResClass extends BaseTask {

    @TaskAction
    public void TaskAction() throws TransformException, IOException {
        for (String atomName : getFlatAtomList()) {
            // Base atom or atom with no resources. Copy the dex over to the final location.
            if (FileUtils.find(getResClassDirs().get(atomName), Pattern.compile("\\.class"))
                    .isEmpty()) {
                File atomDex = new File(getAtomDexDirs().get(atomName), FN_APK_CLASSES_DEX);
                File outDex = new File(getOutputDirs().get(atomName), FN_APK_CLASSES_DEX);
                FileUtils.copyFile(atomDex, outDex);
                continue;
            }

            ProcessOutputHandler outputHandler =
                    new ParsingProcessOutputHandler(
                            new ToolOutputParser(new DexParser(), Message.Kind.ERROR, getILogger()),
                            new ToolOutputParser(new DexParser(), getILogger()),
                            getBuilder().getErrorReporter());
            DexOptions dexOptions =
                    DefaultDexOptions.copyOf(scope.getGlobalScope().getExtension().getDexOptions());

            // First dex the R.class file to the temporary directory.
            try {
                getBuilder()
                        .getDexByteCodeConverter()
                        .convertByteCode(
                                ImmutableSet.of(getResClassDirs().get(atomName)),
                                getTempDirs().get(atomName),
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
            input[0] = new Dex(new File(getTempDirs().get(atomName), FN_APK_CLASSES_DEX));
            input[1] = new Dex(new File(getAtomDexDirs().get(atomName), FN_APK_CLASSES_DEX));

            try (ProcessOutput output = outputHandler.createOutput()) {
                DxContext dxContext =
                        new DxContext(output.getStandardOutput(), output.getErrorOutput());
                Dex merged = new DexMerger(input, CollisionPolicy.KEEP_FIRST, dxContext).merge();
                merged.writeTo(new File(getOutputDirs().get(atomName), FN_APK_CLASSES_DEX));
            }
        }
    }

    @InputFiles
    @NonNull
    public Collection<File> getResClassDirsCollection() {
        return getResClassDirs().values();
    }

    @Input
    @NonNull
    public Map<String, File> getResClassDirs() {
        return resClassDirs;
    }

    public void setResClassDirs(@NonNull Map<String, File> resClassDirs) {
        this.resClassDirs = resClassDirs;
    }

    @InputFiles
    @NonNull
    public Collection<File> getAtomDexDirCollection() {
        return getAtomDexDirs().values();
    }

    @Input
    @NonNull
    public Map<String, File> getAtomDexDirs() {
        return atomDexDirs;
    }

    public void setAtomDexDirs(@NonNull Map<String, File> atomDexDirs) {
        this.atomDexDirs = atomDexDirs;
    }

    @OutputDirectories
    @NonNull
    public Collection<File> getTempDirCollection() {
        return getTempDirs().values();
    }

    @Input
    @NonNull
    public Map<String, File> getTempDirs() {
        return tempDirs;
    }

    public void setTempDirs(@NonNull Map<String, File> tempDirs) {
        this.tempDirs = tempDirs;
    }

    @OutputDirectories
    @NonNull
    public Collection<File> getOutputDirCollection() {
        return getOutputDirs().values();
    }

    @Input
    @NonNull
    public Map<String, File> getOutputDirs() {
        return outputDirs;
    }

    public void setOutputDirs(@NonNull Map<String, File> outputDirs) {
        this.outputDirs = outputDirs;
    }

    @Input
    @NonNull
    public List<String> getFlatAtomList() {
        return flatAtomList.get();
    }

    public void setFlatAtomList(@NonNull Supplier<List<String>> flatAtomList) {
        this.flatAtomList = flatAtomList;
    }

    @Input
    @Optional
    public Integer getMinSdkVersionForDx() {
        return scope.getMinSdkForDx();
    }

    private VariantScope scope;
    private Map<String, File> atomDexDirs;
    private Map<String, File> resClassDirs;
    private Map<String, File> tempDirs;
    private Map<String, File> outputDirs;
    private Supplier<List<String>> flatAtomList;

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
            final BaseVariantData<? extends BaseVariantOutputData> variantData =
                    scope.getVariantData();
            final GradleVariantConfiguration config = variantData.getVariantConfiguration();

            task.setVariantName(scope.getFullVariantName());
            task.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            task.scope = scope;

            task.setFlatAtomList(
                    () ->
                            config.getFlatAndroidAtomsDependencies()
                                    .stream()
                                    .map(AtomDependency::getAtomName)
                                    .collect(Collectors.toList()));

            ConventionMappingHelper.map(
                    task,
                    "atomDexDirs",
                    () -> {
                        Map<String, File> atomDexDirs = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            atomDexDirs.put(atom.getAtomName(), atom.getDexFolder());
                        }
                        return atomDexDirs;
                    });

            ConventionMappingHelper.map(
                    task,
                    "resClassDirs",
                    () -> {
                        Map<String, File> resClassDirs = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            resClassDirs.put(atom.getAtomName(), scope.getJavaOutputDir(atom));
                        }
                        return resClassDirs;
                    });

            ConventionMappingHelper.map(
                    task,
                    "tempDirs",
                    () -> {
                        Map<String, File> tempDirs = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            tempDirs.put(
                                    atom.getAtomName(),
                                    scope.getIncrementalDir(
                                            atom.getAtomName() + "-" + scope.getFullVariantName()));
                        }
                        return tempDirs;
                    });

            ConventionMappingHelper.map(
                    task,
                    "outputDirs",
                    () -> {
                        Map<String, File> outputDirs = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            outputDirs.put(atom.getAtomName(), scope.getDexOutputFolder(atom));
                        }
                        return outputDirs;
                    });
        }
    }

}
