package com.android.build.gradle.tasks.factory;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.PROCESSED_JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.ANNOTATION_PROCESSOR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.scope.InternalArtifactType.ANNOTATION_PROCESSOR_LIST;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.AnnotationProcessorOptions;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.LazyTaskCreationAction;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

/** Configuration Action for a JavaCompile task. */
public class JavaCompileCreationAction extends LazyTaskCreationAction<AndroidJavaCompile> {
    private static final ILogger LOG = LoggerWrapper.getLogger(JavaCompileCreationAction.class);

    @NonNull private final VariantScope scope;
    private File destinationDir;

    public JavaCompileCreationAction(@NonNull VariantScope scope) {
        this.scope = scope;
    }

    @NonNull
    @Override
    public String getName() {
        return scope.getTaskName("compile", "JavaWithJavac");
    }

    @NonNull
    @Override
    public Class<AndroidJavaCompile> getType() {
        return AndroidJavaCompile.class;
    }

    @Override
    public void preConfigure(@NotNull String taskName) {
        super.preConfigure(taskName);

        BuildArtifactsHolder artifacts = scope.getArtifacts();
        destinationDir = artifacts.appendArtifact(InternalArtifactType.JAVAC, taskName, "classes");

        if (scope.getGlobalScope().getExtension().getDataBinding().isEnabled()) {
            // The data binding artifact is created through annotation processing, which is invoked
            // by the JavaCompile task. Therefore, we register JavaCompile as the generating task.
            artifacts.appendArtifact(
                    InternalArtifactType.DATA_BINDING_ARTIFACT,
                    ImmutableList.of(scope.getBundleArtifactFolderForDataBinding()),
                    taskName);
        }
    }

    @Override
    public void handleProvider(@NotNull TaskProvider<? extends AndroidJavaCompile> taskProvider) {
        super.handleProvider(taskProvider);
        scope.getTaskContainer().setJavacTask(taskProvider);
    }

    @Override
    public void configure(@NonNull final AndroidJavaCompile javacTask) {
        final GlobalScope globalScope = scope.getGlobalScope();
        final Project project = globalScope.getProject();
        BuildArtifactsHolder artifacts = scope.getArtifacts();

        javacTask.compileSdkVersion = globalScope.getExtension().getCompileSdkVersion();
        javacTask.mInstantRunBuildContext = scope.getInstantRunBuildContext();

        // We can't just pass the collection directly, as the instanceof check in the incremental
        // compile doesn't work recursively currently, so every ConfigurableFileTree needs to be
        // directly in the source array.
        for (ConfigurableFileTree fileTree: scope.getVariantData().getJavaSources()) {
            javacTask.source(fileTree);
        }

        javacTask.getOptions().setBootstrapClasspath(scope.getBootClasspath());
        javacTask.setClasspath(scope.getJavaClasspath(COMPILE_CLASSPATH, CLASSES));

        javacTask.setDestinationDir(destinationDir);

        CompileOptions compileOptions = globalScope.getExtension().getCompileOptions();
        javacTask.setSourceCompatibility(compileOptions.getSourceCompatibility().toString());
        javacTask.setTargetCompatibility(compileOptions.getTargetCompatibility().toString());
        javacTask.getOptions().setEncoding(compileOptions.getEncoding());

        Boolean includeCompileClasspath =
                scope.getVariantConfiguration()
                        .getJavaCompileOptions()
                        .getAnnotationProcessorOptions()
                        .getIncludeCompileClasspath();

        FileCollection processorPath =
                scope.getArtifactFileCollection(ANNOTATION_PROCESSOR, ALL, PROCESSED_JAR);
        if (Boolean.TRUE.equals(includeCompileClasspath)) {
            // We need the jar files because annotation processors require the resources.
            processorPath =
                    processorPath.plus(scope.getJavaClasspath(COMPILE_CLASSPATH, PROCESSED_JAR));
        }

        javacTask.getOptions().setAnnotationProcessorPath(processorPath);

        boolean incremental = AbstractCompilesUtil.isIncremental(
                project,
                scope,
                compileOptions,
                null, /* processorConfiguration, JavaCompile handles annotation processor now */
                LOG);

        if (incremental) {
            LOG.verbose("Using incremental javac compilation for %1$s %2$s.",
                    project.getPath(), scope.getFullVariantName());
            javacTask.getOptions().setIncremental(true);
        } else {
            LOG.verbose("Not using incremental javac compilation for %1$s %2$s.",
                    project.getPath(), scope.getFullVariantName());
        }

        AnnotationProcessorOptions annotationProcessorOptions =
                scope.getVariantConfiguration()
                        .getJavaCompileOptions()
                        .getAnnotationProcessorOptions();

        if (!annotationProcessorOptions.getClassNames().isEmpty()) {
            javacTask.getOptions().getCompilerArgs().add("-processor");
            javacTask.getOptions().getCompilerArgs().add(
                    Joiner.on(',').join(annotationProcessorOptions.getClassNames()));
        }
        if (!annotationProcessorOptions.getArguments().isEmpty()) {
            for (Map.Entry<String, String> arg :
                    annotationProcessorOptions.getArguments().entrySet()) {
                javacTask.getOptions().getCompilerArgs().add(
                        "-A" + arg.getKey() + "=" + arg.getValue());
            }
        }
        javacTask
                .getOptions()
                .getCompilerArgumentProviders()
                .addAll(annotationProcessorOptions.getCompilerArgumentProviders());

        javacTask
                .getOptions()
                .setAnnotationProcessorGeneratedSourcesDirectory(
                        scope.getAnnotationProcessorOutputDir());
        javacTask.annotationProcessorOutputFolder = scope.getAnnotationProcessorOutputDir();

        javacTask.processorListFile =
                artifacts.getFinalArtifactFiles(ANNOTATION_PROCESSOR_LIST);
        javacTask.variantName = scope.getFullVariantName();

        javacTask.dependsOn(scope.getTaskContainer().getSourceGenTask());
    }
}
