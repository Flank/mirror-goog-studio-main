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

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.dependency.level2.AtomDependency;
import com.android.utils.FileUtils;
import com.google.common.collect.Maps;
import com.google.common.collect.ObjectArrays;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.TaskAction;

/** Task to compile the atoms final R classes. */
public class JavaCompileAtomResClass extends DefaultAndroidTask {

    @InputFiles
    public Collection<File> getSrcDirsCollection() {
        return getSrcDirs().values();
    }

    @Input
    public Map<String, File> getSrcDirs() {
        return srcDirs;
    }

    public void setSrcDirs(Map<String, File> srcDirs) {
        this.srcDirs = srcDirs;
    }

    @OutputDirectories
    public Collection<File> getOutDirsCollection() {
        return getOutDirs().values();
    }

    @Input
    public Map<String, File> getOutDirs() {
        return outDirs;
    }

    public void setOutDirs(Map<String, File> outDirs) {
        this.outDirs = outDirs;
    }

    @Input
    public List<String> getFlatAtomList() {
        return flatAtomList.get();
    }

    public void setFlatAtomList(Supplier<List<String>> flatAtomList) {
        this.flatAtomList = flatAtomList;
    }

    private Map<String, File> srcDirs;
    private Map<String, File> outDirs;
    private Supplier<List<String>> flatAtomList;

    @TaskAction
    public void compile() {
        for (String atomName : getFlatAtomList()) {
            List<String> srcFiles =
                    FileUtils.find(getSrcDirs().get(atomName), Pattern.compile("\\.java$"))
                            .stream()
                            .map(File::getAbsolutePath)
                            .collect(Collectors.toList());
            if (srcFiles.isEmpty()) continue;
            String[] firstArgs = {
                "-source",
                "1.7",
                "-target",
                "1.7",
                "-d",
                getOutDirs().get(atomName).getAbsolutePath()
            };
            String[] arguments =
                    ObjectArrays.concat(firstArgs, srcFiles.toArray(new String[0]), String.class);

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            int result =
                    compiler.run(
                            null,
                            ByteStreams.nullOutputStream(),
                            ByteStreams.nullOutputStream(),
                            arguments);

            if (result != 0) {
                throw new GradleException(
                        String.format("Failed to compile atom %1$s R.java classes.", atomName));
            }
        }
    }

    public static class ConfigAction implements TaskConfigAction<JavaCompileAtomResClass> {
        public ConfigAction(@NonNull VariantOutputScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("compileAll", "AtomsResClassesWithJavac");
        }

        @NonNull
        @Override
        public Class<JavaCompileAtomResClass> getType() {
            return JavaCompileAtomResClass.class;
        }

        @Override
        public void execute(@NonNull JavaCompileAtomResClass javaCompileAtomResClass) {
            VariantScope variantScope = scope.getVariantScope();
            final BaseVariantData<? extends BaseVariantOutputData> variantData =
                    variantScope.getVariantData();
            final GradleVariantConfiguration config = variantData.getVariantConfiguration();

            javaCompileAtomResClass.setFlatAtomList(
                    () ->
                            config.getFlatAndroidAtomsDependencies()
                                    .stream()
                                    .map(AtomDependency::getAtomName)
                                    .collect(Collectors.toList()));
            ConventionMappingHelper.map(
                    javaCompileAtomResClass,
                    "srcDirs",
                    () -> {
                        Map<String, File> srcDirs = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            srcDirs.put(
                                    atom.getAtomName(),
                                    variantScope.getRClassSourceOutputDir(atom));
                        }
                        return srcDirs;
                    });
            ConventionMappingHelper.map(
                    javaCompileAtomResClass,
                    "outDirs",
                    () -> {
                        Map<String, File> outDirs = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            outDirs.put(atom.getAtomName(), variantScope.getJavaOutputDir(atom));
                        }
                        return outDirs;
                    });
            javaCompileAtomResClass.setVariantName(scope.getVariantOutputData().getFullName());
        }

        @NonNull private VariantOutputScope scope;
    }
}
