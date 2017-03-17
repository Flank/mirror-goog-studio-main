package com.android.build.gradle.tasks.factory;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.ANNOTATION_PROCESSOR;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.dsl.CoreAnnotationProcessorOptions;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.model.SyncIssue;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import java.io.File;
import java.util.Map;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;

/**
 * Configuration Action for a JavaCompile task.
 */
public class JavaCompileConfigAction implements TaskConfigAction<AndroidJavaCompile> {
    private static final ILogger LOG = LoggerWrapper.getLogger(JavaCompileConfigAction.class);

    @NonNull private VariantScope scope;

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


        final boolean keepDefaultBootstrap = keepBootclasspath();

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
        }

        FileCollection classpath = scope.getJavaClasspath(CLASSES);
        if (keepDefaultBootstrap) {
            classpath =
                    classpath.plus(
                            project.files(globalScope.getAndroidBuilder().getBootClasspath(false)));
        }
        javacTask.setClasspath(classpath);

        javacTask.setDestinationDir(scope.getJavaOutputDir());

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
            processorPath = processorPath.plus(scope.getJavaClasspath(JAR));
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

        CoreAnnotationProcessorOptions annotationProcessorOptions =
                scope.getVariantConfiguration().getJavaCompileOptions()
                        .getAnnotationProcessorOptions();

        if (!annotationProcessorOptions.getClassNames().isEmpty()) {
            javacTask.getOptions().getCompilerArgs().add("-processor");
            javacTask.getOptions().getCompilerArgs().add(
                    Joiner.on(',').join(annotationProcessorOptions.getClassNames()));
        }
        if ((!annotationProcessorConfiguration.getAllDependencies().isEmpty()
                        || !annotationProcessorOptions.getClassNames().isEmpty())
                && project.getPlugins().hasPlugin(AbstractCompilesUtil.ANDROID_APT_PLUGIN_NAME)) {
            // warn user if using android-apt plugin, as it overwrites the annotation processor opts
            globalScope
                    .getAndroidBuilder()
                    .getErrorReporter()
                    .handleSyncWarning(
                            null,
                            SyncIssue.TYPE_GENERIC,
                            "Using incompatible plugins for the annotation processing: "
                                    + "android-apt. This may result in an unexpected behavior.");
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

    }


    private boolean keepBootclasspath() {
        // javac 1.8 may generate code that uses class not available in android.jar.  This is fine
        // if jack or desugar are used to compile code for the app or compile task is created only
        // for unit test. In those cases, we want to keep the default bootstrap classpath.
        if (!JavaVersion.current().isJava8Compatible()) {
            return false;
        }

        VariantScope.Java8LangSupport java8LangSupport = scope.getJava8LangSupportType();
        if (java8LangSupport == VariantScope.Java8LangSupport.JACK) {
            return true;
        }

        // only if target and source is explicitly specified to 1.8 (and above), we keep the
        // default bootclasspath with Desugar. Otherwise, we use android.jar.
        CompileOptions compileOptions = scope.getGlobalScope().getExtension().getCompileOptions();
        return java8LangSupport == VariantScope.Java8LangSupport.DESUGAR
                && compileOptions.getTargetCompatibility().isJava8Compatible()
                && compileOptions.getSourceCompatibility().isJava8Compatible();
    }
}
