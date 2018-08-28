/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static com.android.SdkConstants.DOT_JAVA;
import static com.android.SdkConstants.INT_DEF_ANNOTATION;
import static com.android.SdkConstants.LONG_DEF_ANNOTATION;
import static com.android.SdkConstants.STRING_DEF_ANNOTATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.AnchorOutputType;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AbstractAndroidCompile;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.utils.AndroidXDependency;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.packaging.TypedefRemover;
import com.android.tools.lint.gradle.api.ExtractAnnotationRequest;
import com.android.tools.lint.gradle.api.ReflectiveLintRunner;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Task which extracts annotations from the source files, and writes them to one of two possible
 * destinations:
 *
 * <ul>
 *   <li>A "external annotations" file (pointed to by {@link ExtractAnnotations#output}) which
 *       records the annotations in a zipped XML format for use by the IDE and by lint to associate
 *       the (source retention) annotations back with the compiled code
 * </ul>
 *
 * We typically only extract external annotations when building libraries; ProGuard annotations are
 * extracted when building libraries (to record in the AAR), <b>or</b> when building an app module
 * where ProGuarding is enabled.
 */
@CacheableTask
public class ExtractAnnotations extends AbstractAndroidCompile {

    @NonNull
    private static final AndroidXDependency ANDROIDX_ANNOTATIONS =
            AndroidXDependency.fromPreAndroidXDependency(
                    "com.android.support", "support-annotations");

    private Supplier<List<String>> bootClasspath;

    private File typedefFile;

    private File output;

    private String encoding;

    private BuildableArtifact classDir;

    private ArtifactCollection libraries;

    @Nullable FileCollection lintClassPath;

    private String variantName;

    @Internal
    @NotNull
    @Override
    public String getVariantName() {
        return variantName;
    }

    @Override
    public void setVariantName(@NotNull String name) {
        variantName = name;
    }


    /** Lint classpath */
    @InputFiles
    @Nullable
    public FileCollection getLintClassPath() {
        return lintClassPath;
    }

    @NonNull
    @Override
    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputFiles
    @SkipWhenEmpty
    public FileTree getSource() {
        return super.getSource();
    }

    /** Boot classpath: typically android.jar */
    @CompileClasspath
    public List<String> getBootClasspath() {
        return bootClasspath.get();
    }

    public void setBootClasspath(Supplier<List<String>> bootClasspath) {
        this.bootClasspath = bootClasspath;
    }

    @CompileClasspath
    public FileCollection getLibraries() {
        return libraries.getArtifactFiles();
    }

    /** The output .zip file to write the annotations database to, if any */
    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setOutput(File output) {
        this.output = output;
    }

    /**
     * The output .txt file to write the typedef recipe file to. A "recipe" file is a file which
     * describes typedef classes, typically ones that should be deleted. It is generated by this
     * {@link ExtractAnnotations} task and consumed by the {@link TypedefRemover}.
     */
    @OutputFile
    public File getTypedefFile() {
        return typedefFile;
    }

    /**
     * The encoding to use when reading source files. The output file will ignore this and will
     * always be a UTF-8 encoded .xml file inside the annotations zip file.
     */
    @NonNull
    @Input
    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Location of class files. If set, any non-public typedef source retention annotations will be
     * removed prior to .jar packaging.
     */
    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public BuildableArtifact getClassDir() {
        return classDir;
    }

    public void setClassDir(BuildableArtifact classDir) {
        this.classDir = classDir;
    }

    @Override
    @TaskAction
    protected void compile() {
        SourceFileVisitor fileVisitor = new SourceFileVisitor();
        getSource().visit(fileVisitor);
        List<File> sourceFiles = fileVisitor.sourceUnits;

        if (!containsTypeDefs(sourceFiles)) {
            writeEmptyTypeDefFile(getTypedefFile());
            return;
        }

        List<File> roots = fileVisitor.getSourceRoots();
        FileCollection classpath = getClasspath();
        if (classpath != null) {
            for (File jar : classpath) {
                roots.add(jar);
            }
        }
        for (String path : getBootClasspath()) {
            roots.add(new File(path));
        }

        ExtractAnnotationRequest request =
                new ExtractAnnotationRequest(
                        getTypedefFile(),
                        getLogger(),
                        getClassDir().get(),
                        getOutput(),
                        sourceFiles,
                        roots);
        FileCollection lintClassPath = getLintClassPath();
        if (lintClassPath != null) {
            new ReflectiveLintRunner().extractAnnotations(getProject().getGradle(),
                    request, lintClassPath.getFiles());
        }
    }

    private static void writeEmptyTypeDefFile(@Nullable File file) {
        if (file == null) {
            return;
        }

        try {
            FileUtils.deleteIfExists(file);
            Files.createParentDirs(file);
            Files.asCharSink(file, Charsets.UTF_8).write("");
        } catch (IOException ignore) {
        }
    }

    /**
     * Returns true if the given set of source files contain any typedef references.
     */
    private static boolean containsTypeDefs(@NonNull List<File> sourceFiles) {
        for (File file : sourceFiles) {
            if (containsTypeDefs(file)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the given source file contains any typedef references.
     */
    private static boolean containsTypeDefs(@NonNull File file) {
        try {
            // TODO: Perform faster checks, for example converting the target annotations
            // to byte arrays and then scanning through the files on disk (without reading
            // them into memory) looking for those byte sequences. Possibly using memory
            // mapped buffers.
            try (Stream<String> lines = Files.asCharSource(file, UTF_8).lines()) {
                return lines.anyMatch(
                        line ->
                                line.contains("Def")
                                        && (line.contains(INT_DEF_ANNOTATION.oldName())
                                                || line.contains(INT_DEF_ANNOTATION.newName())
                                                || line.contains(LONG_DEF_ANNOTATION.oldName())
                                                || line.contains(LONG_DEF_ANNOTATION.newName())
                                                || line.contains(STRING_DEF_ANNOTATION.oldName())
                                                || line.contains(STRING_DEF_ANNOTATION.newName())));
            }
        } catch (IOException e) {
            return false;
        }
    }

    @Input
    public boolean hasAndroidAnnotations() {
        for (ResolvedArtifactResult artifact : libraries.getArtifacts()) {
            ComponentIdentifier id = artifact.getId()
                    .getComponentIdentifier();
            // because we only ask for external dependencies, we should be able to cast
            // this always
            if (id instanceof ModuleComponentIdentifier) {
                ModuleComponentIdentifier moduleId = (ModuleComponentIdentifier) id;

                // Search in both AndroidX and pre-AndroidX libraries
                if (moduleId.getGroup().equals(ANDROIDX_ANNOTATIONS.getGroup())
                                && moduleId.getModule().equals(ANDROIDX_ANNOTATIONS.getModule())
                        || moduleId.getGroup().equals(ANDROIDX_ANNOTATIONS.getOldGroup())
                                && moduleId.getModule()
                                        .equals(ANDROIDX_ANNOTATIONS.getOldModule())) {
                    return true;
                }
            }
        }

        return false;
    }

    public static class CreationAction extends VariantTaskCreationAction<ExtractAnnotations> {

        @NonNull private final AndroidConfig extension;
        private File output;
        private File typedefFile;

        public CreationAction(
                @NonNull AndroidConfig extension, @NonNull VariantScope variantScope) {
            super(variantScope);
            this.extension = extension;
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("extract", "Annotations");
        }

        @NonNull
        @Override
        public Class<ExtractAnnotations> getType() {
            return ExtractAnnotations.class;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);

            output =
                    getVariantScope()
                            .getArtifacts()
                            .appendArtifact(
                                    InternalArtifactType.ANNOTATIONS_ZIP,
                                    taskName,
                                    SdkConstants.FN_ANNOTATIONS_ZIP);

            typedefFile =
                    getVariantScope()
                            .getArtifacts()
                            .appendArtifact(
                                    InternalArtifactType.ANNOTATIONS_TYPEDEF_FILE,
                                    taskName,
                                    "typedefs.txt");
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends ExtractAnnotations> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setGenerateAnnotationsTask(taskProvider);
        }

        @Override
        public void configure(@NonNull ExtractAnnotations task) {
            super.configure(task);
            VariantScope variantScope = getVariantScope();

            final GradleVariantConfiguration variantConfig = variantScope.getVariantConfiguration();
            final AndroidBuilder androidBuilder = variantScope.getGlobalScope().getAndroidBuilder();

            task.setDescription(
                    "Extracts Android annotations for the "
                            + variantConfig.getFullName()
                            + " variant into the archive file");
            task.setGroup(BasePlugin.BUILD_GROUP);

            // publish intermediate annotation data
            task.setOutput(output);
            task.setDestinationDir(output.getParentFile());

            task.typedefFile = typedefFile;

            task.setClassDir(
                    variantScope
                            .getArtifacts()
                            .getFinalArtifactFiles(AnchorOutputType.ALL_CLASSES));

            task.setSource(variantScope.getVariantData().getJavaSources());
            task.setEncoding(extension.getCompileOptions().getEncoding());
            task.setSourceCompatibility(
                    extension.getCompileOptions().getSourceCompatibility().toString());
            task.setClasspath(variantScope.getJavaClasspath(COMPILE_CLASSPATH, CLASSES));

            task.libraries = variantScope.getArtifactCollection(
                    COMPILE_CLASSPATH, EXTERNAL, CLASSES);

            // Setup the boot classpath just before the task actually runs since this will
            // force the sdk to be parsed. (Same as in compileTask)
            task.setBootClasspath(() -> androidBuilder.getBootClasspathAsStrings(false));

            task.lintClassPath = variantScope.getGlobalScope().getProject().getConfigurations()
                    .getByName(LintBaseTask.LINT_CLASS_PATH);
        }
    }

    /**
     * Visitor which gathers a series of individual source files as well as inferring the set of
     * source roots
     */
    private static class SourceFileVisitor extends EmptyFileVisitor {
        private final List<File> sourceUnits = Lists.newArrayListWithExpectedSize(100);
        private final List<File> sourceRoots = Lists.newArrayList();

        private String mostRecentRoot = "\000";

        public SourceFileVisitor() {
        }

        public List<File> getSourceFiles() {
            return sourceUnits;
        }

        public List<File> getSourceRoots() {
            return sourceRoots;
        }

        private static final String BUILD_GENERATED = File.separator + "build" + File.separator
                + "generated" + File.separator;

        @Override
        public void visitFile(FileVisitDetails details) {
            File file = details.getFile();
            String path = file.getPath();
            if (path.endsWith(DOT_JAVA) && !path.contains(BUILD_GENERATED)) {
                // Infer the source roots. These are available as relative paths
                // on the file visit details.
                if (!path.startsWith(mostRecentRoot)) {
                    RelativePath relativePath = details.getRelativePath();
                    String pathString = relativePath.getPathString();
                    // The above method always uses / as a file separator but for
                    // comparisons with the path we need to use the native separator:
                    pathString = pathString.replace('/', File.separatorChar);

                    if (path.endsWith(pathString)) {
                        String root = path.substring(0, path.length() - pathString.length());
                        File rootFile = new File(root);
                        if (!sourceRoots.contains(rootFile)) {
                            mostRecentRoot = rootFile.getPath();
                            sourceRoots.add(rootFile);
                        }
                    }
                }

                sourceUnits.add(file);
            }
        }
    }
}
