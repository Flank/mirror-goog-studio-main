package com.android.build.gradle.tasks.factory;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.ANNOTATION_PROCESSOR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.ANNOTATION_PROCESSOR_LIST;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.DATA_BINDING_DEPENDENCY_ARTIFACTS;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.AnnotationProcessorOptions;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.PathSensitivity;

/**
 * Configuration Action for a JavaCompile task.
 */
public class JavaCompileConfigAction implements TaskConfigAction<AndroidJavaCompile> {
    private static final ILogger LOG = LoggerWrapper.getLogger(JavaCompileConfigAction.class);

    @NonNull private final VariantScope scope;
    @NonNull private final File outputFolder;

    public JavaCompileConfigAction(@NonNull VariantScope scope, @NonNull File outputFolder) {
        this.scope = scope;
        this.outputFolder = outputFolder;
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
        javacTask.compileSdkVersion = globalScope.getExtension().getCompileSdkVersion();
        javacTask.mInstantRunBuildContext = scope.getInstantRunBuildContext();

        // We can't just pass the collection directly, as the instanceof check in the incremental
        // compile doesn't work recursively currently, so every ConfigurableFileTree needs to be
        // directly in the source array.
        for (ConfigurableFileTree fileTree: scope.getVariantData().getJavaSources()) {
            javacTask.source(fileTree);
        }

        final boolean keepDefaultBootstrap = scope.keepDefaultBootstrap();

        if (!keepDefaultBootstrap) {
            // Set boot classpath if we don't need to keep the default.  Otherwise, this is added as
            // normal classpath.
            javacTask
                    .getOptions()
                    .setBootClasspath(
                            Joiner.on(File.pathSeparator)
                                    .join(
                                            globalScope
                                                    .getAndroidBuilder()
                                                    .getBootClasspathAsStrings(false)));

            // Workaround to avoid absolute paths making it into the configuration of javacTask
            // This won't be needed with Gradle 4.3, as bootClasspath is replaced with bootstrapClasspath
            javacTask.getInputs().property("options.bootClasspath", null);
            javacTask
                    .getInputs()
                    .files(globalScope.getAndroidBuilder().getBootClasspath(false))
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                    .withPropertyName("options.bootClasspath.override");

        }

        FileCollection classpath = scope.getJavaClasspath(COMPILE_CLASSPATH, CLASSES);
        if (keepDefaultBootstrap) {
            classpath =
                    classpath.plus(
                            project.files(globalScope.getAndroidBuilder().getBootClasspath(false)));
        }
        javacTask.setClasspath(classpath);

        javacTask.setDestinationDir(outputFolder);

        CompileOptions compileOptions = globalScope.getExtension().getCompileOptions();

        AbstractCompilesUtil.configureLanguageLevel(
                javacTask,
                compileOptions,
                globalScope.getExtension().getCompileSdkVersion(),
                scope.getJava8LangSupportType());

        javacTask.getOptions().setEncoding(compileOptions.getEncoding());

        Configuration annotationProcessorConfiguration =
                scope.getVariantDependencies().getAnnotationProcessorConfiguration();

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

        javacTask.getOptions().getCompilerArgs().add("-s");
        javacTask.getOptions().getCompilerArgs().add(
                scope.getAnnotationProcessorOutputDir().getAbsolutePath());
        javacTask.annotationProcessorOutputFolder = scope.getAnnotationProcessorOutputDir();

        // Filter the annotation processor output folder from compiler arguments to avoid absolute path.
        // The output folder is already represented as an output via AndroidJavaCompile.getAnnotationProcessorOutputFolder()
        // so we only need to prevent it being tracked as a string in the compiler argument list.
        javacTask.getInputs().property("options.compilerArgs", null);
        javacTask
                .getInputs()
                .property(
                        "options.compilerArgs.filtered",
                        (Callable<Object>)
                                () -> {
                                    List<String> compilerArgs =
                                            javacTask.getOptions().getCompilerArgs();
                                    List<String> filteredArgs = Lists.newArrayList();
                                    Iterator<String> iCompilerArgs = compilerArgs.iterator();
                                    while (iCompilerArgs.hasNext()) {
                                        String compilerArg = iCompilerArgs.next();
                                        if ("-s".equals(compilerArg)) {
                                            if (iCompilerArgs.hasNext()) {
                                                iCompilerArgs.next();
                                            }
                                        } else {
                                            filteredArgs.add(compilerArg);
                                        }
                                    }
                                    return filteredArgs;
                                });

        // if data binding is enabled and this variant has merged dependency artifacts, then
        // make the javac task depend on them. (test variants don't do the merge so they
        // could not have the artifacts)
        if (scope.getGlobalScope().getExtension().getDataBinding().isEnabled()
                && scope.hasOutput(DATA_BINDING_DEPENDENCY_ARTIFACTS)) {
            javacTask.dataBindingDependencyArtifacts =
                    scope.getOutput(DATA_BINDING_DEPENDENCY_ARTIFACTS);
        }

        javacTask.processorListFile = scope.getOutput(ANNOTATION_PROCESSOR_LIST);
        javacTask.variantName = scope.getFullVariantName();
    }
}
