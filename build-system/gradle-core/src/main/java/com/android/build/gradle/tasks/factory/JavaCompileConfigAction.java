package com.android.build.gradle.tasks.factory;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.ANNOTATION_PROCESSOR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.scope.InternalArtifactType.ANNOTATION_PROCESSOR_LIST;
import static com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT;
import static com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_DEPENDENCY_ARTIFACTS;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.AnnotationProcessorOptions;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.core.VariantType;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;

/**
 * Configuration Action for a JavaCompile task.
 */
public class JavaCompileConfigAction implements TaskConfigAction<AndroidJavaCompile> {
    private static final ILogger LOG = LoggerWrapper.getLogger(JavaCompileConfigAction.class);

    @NonNull private final VariantScope scope;

    public JavaCompileConfigAction(@NonNull VariantScope scope) {
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
    public void execute(@NonNull final AndroidJavaCompile javacTask) {
        scope.getVariantData().javacTask = javacTask;
        scope.getVariantData().javaCompilerTask = javacTask;
        final GlobalScope globalScope = scope.getGlobalScope();
        final Project project = globalScope.getProject();
        BuildArtifactsHolder artifacts = scope.getArtifacts();
        boolean isDataBindingEnabled = globalScope.getExtension().getDataBinding().isEnabled();

        javacTask.compileSdkVersion = globalScope.getExtension().getCompileSdkVersion();
        javacTask.mInstantRunBuildContext = scope.getInstantRunBuildContext();

        // We can't just pass the collection directly, as the instanceof check in the incremental
        // compile doesn't work recursively currently, so every ConfigurableFileTree needs to be
        // directly in the source array.
        for (ConfigurableFileTree fileTree: scope.getVariantData().getJavaSources()) {
            javacTask.source(fileTree);
        }

        javacTask.getOptions().setBootstrapClasspath(scope.getBootClasspath());

        FileCollection classpath = scope.getJavaClasspath(COMPILE_CLASSPATH, CLASSES);
        if (!globalScope.getProjectOptions().get(BooleanOption.ENABLE_CORE_LAMBDA_STUBS)
                && scope.keepDefaultBootstrap()) {
            // adding android.jar to classpath, as it is not in the bootclasspath
            classpath =
                    classpath.plus(
                            project.files(globalScope.getAndroidBuilder().getBootClasspath(false)));
        }
        javacTask.setClasspath(classpath);

        javacTask.setDestinationDir(
                artifacts
                        .appendArtifact(InternalArtifactType.JAVAC, javacTask, "classes"));

        CompileOptions compileOptions = globalScope.getExtension().getCompileOptions();

        AbstractCompilesUtil.configureLanguageLevel(
                javacTask,
                compileOptions,
                globalScope.getExtension().getCompileSdkVersion(),
                scope.getJava8LangSupportType());

        javacTask.getOptions().setEncoding(compileOptions.getEncoding());

        Boolean includeCompileClasspath =
                scope.getVariantConfiguration()
                        .getJavaCompileOptions()
                        .getAnnotationProcessorOptions()
                        .getIncludeCompileClasspath();

        FileCollection processorPath =
                scope.getArtifactFileCollection(ANNOTATION_PROCESSOR, ALL, JAR);
        if (Boolean.TRUE.equals(includeCompileClasspath)) {
            // We need the jar files because annotation processors require the resources.
            processorPath = processorPath.plus(scope.getJavaClasspath(COMPILE_CLASSPATH, JAR));
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
                .setAnnotationProcessorGeneratedSourcesDirectory(
                        scope.getAnnotationProcessorOutputDir());
        javacTask.annotationProcessorOutputFolder = scope.getAnnotationProcessorOutputDir();

        if (isDataBindingEnabled) {

            if (artifacts.hasArtifact(DATA_BINDING_DEPENDENCY_ARTIFACTS)) {
                // if data binding is enabled and this variant has merged dependency artifacts, then
                // make the javac task depend on them. (test variants don't do the merge so they
                // could not have the artifacts)
                javacTask.dataBindingDependencyArtifacts =
                        artifacts.getFinalArtifactFiles(DATA_BINDING_DEPENDENCY_ARTIFACTS);
            }
            if (artifacts.hasArtifact(DATA_BINDING_BASE_CLASS_LOG_ARTIFACT)) {
                javacTask.dataBindingClassLogDir =
                        artifacts.getFinalArtifactFiles(DATA_BINDING_BASE_CLASS_LOG_ARTIFACT);
            }
            // the data binding artifact is created by the annotation processor, so we register this
            // task output (which also publishes it) with javac as the generating task.
            javacTask.dataBindingArtifactOutputDirectory =
                    scope.getBundleArtifactFolderForDataBinding();

            artifacts.appendArtifact(InternalArtifactType.DATA_BINDING_ARTIFACT,
                    ImmutableList.of(scope.getBundleArtifactFolderForDataBinding()),
                    javacTask);

            VariantType variantType = scope.getType();
            if (variantType.isBaseModule()) {
                javacTask
                        .getInputs()
                        .file(
                                artifacts
                                        .getFinalArtifactFiles(
                                                InternalArtifactType
                                                        .FEATURE_DATA_BINDING_BASE_FEATURE_INFO)
                                        .get());
            } else if (variantType.isFeatureSplit()) {
                javacTask
                        .getInputs()
                        .file(
                                artifacts
                                        .getFinalArtifactFiles(
                                                InternalArtifactType
                                                        .FEATURE_DATA_BINDING_FEATURE_INFO)
                                        .get());
            }
        }

        javacTask.processorListFile =
                artifacts.getFinalArtifactFiles(ANNOTATION_PROCESSOR_LIST);
        javacTask.variantName = scope.getFullVariantName();
    }
}
