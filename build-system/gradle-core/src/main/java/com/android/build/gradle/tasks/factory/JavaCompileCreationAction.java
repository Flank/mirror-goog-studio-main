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
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskProvider;

/** Configuration Action for a JavaCompile task. */
public class JavaCompileCreationAction extends VariantTaskCreationAction<AndroidJavaCompile> {
    private static final ILogger LOG = LoggerWrapper.getLogger(JavaCompileCreationAction.class);

    private File destinationDir;

    public JavaCompileCreationAction(@NonNull VariantScope scope) {
        super(scope);
    }

    @NonNull
    @Override
    public String getName() {
        return getVariantScope().getTaskName("compile", "JavaWithJavac");
    }

    @NonNull
    @Override
    public Class<AndroidJavaCompile> getType() {
        return AndroidJavaCompile.class;
    }

    @Override
    public void preConfigure(@NonNull String taskName) {
        super.preConfigure(taskName);
        VariantScope scope = getVariantScope();

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
    public void handleProvider(@NonNull TaskProvider<? extends AndroidJavaCompile> taskProvider) {
        super.handleProvider(taskProvider);
        getVariantScope().getTaskContainer().setJavacTask(taskProvider);
    }

    @Override
    public void configure(@NonNull final AndroidJavaCompile task) {
        super.configure(task);
        VariantScope scope = getVariantScope();


        final GlobalScope globalScope = scope.getGlobalScope();
        final Project project = globalScope.getProject();
        BuildArtifactsHolder artifacts = scope.getArtifacts();

        task.compileSdkVersion = globalScope.getExtension().getCompileSdkVersion();
        task.mInstantRunBuildContext = scope.getInstantRunBuildContext();

        // We can't just pass the collection directly, as the instanceof check in the incremental
        // compile doesn't work recursively currently, so every ConfigurableFileTree needs to be
        // directly in the source array.
        for (ConfigurableFileTree fileTree: scope.getVariantData().getJavaSources()) {
            task.source(fileTree);
        }

        task.getOptions().setBootstrapClasspath(scope.getBootClasspath());
        task.setClasspath(scope.getJavaClasspath(COMPILE_CLASSPATH, CLASSES));

        task.setDestinationDir(destinationDir);

        CompileOptions compileOptions = globalScope.getExtension().getCompileOptions();
        task.setSourceCompatibility(compileOptions.getSourceCompatibility().toString());
        task.setTargetCompatibility(compileOptions.getTargetCompatibility().toString());
        task.getOptions().setEncoding(compileOptions.getEncoding());

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

        task.getOptions().setAnnotationProcessorPath(processorPath);

        boolean incremental = AbstractCompilesUtil.isIncremental(
                project,
                scope,
                compileOptions,
                null, /* processorConfiguration, JavaCompile handles annotation processor now */
                LOG);

        if (incremental) {
            LOG.verbose("Using incremental javac compilation for %1$s %2$s.",
                    project.getPath(), scope.getFullVariantName());
            task.getOptions().setIncremental(true);
        } else {
            LOG.verbose("Not using incremental javac compilation for %1$s %2$s.",
                    project.getPath(), scope.getFullVariantName());
        }

        AnnotationProcessorOptions annotationProcessorOptions =
                scope.getVariantConfiguration()
                        .getJavaCompileOptions()
                        .getAnnotationProcessorOptions();

        if (!annotationProcessorOptions.getClassNames().isEmpty()) {
            task.getOptions().getCompilerArgs().add("-processor");
            task.getOptions()
                    .getCompilerArgs()
                    .add(Joiner.on(',').join(annotationProcessorOptions.getClassNames()));
        }
        if (!annotationProcessorOptions.getArguments().isEmpty()) {
            for (Map.Entry<String, String> arg :
                    annotationProcessorOptions.getArguments().entrySet()) {
                task.getOptions().getCompilerArgs().add("-A" + arg.getKey() + "=" + arg.getValue());
            }
        }
        task.getOptions()
                .getCompilerArgumentProviders()
                .addAll(annotationProcessorOptions.getCompilerArgumentProviders());

        task.getOptions()
                .setAnnotationProcessorGeneratedSourcesDirectory(
                        scope.getAnnotationProcessorOutputDir());
        task.annotationProcessorOutputFolder = scope.getAnnotationProcessorOutputDir();

        task.processorListFile = artifacts.getFinalArtifactFiles(ANNOTATION_PROCESSOR_LIST);

        task.dependsOn(scope.getTaskContainer().getSourceGenTask());
    }
}
