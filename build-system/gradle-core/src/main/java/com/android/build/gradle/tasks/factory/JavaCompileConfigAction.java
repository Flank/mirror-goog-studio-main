package com.android.build.gradle.tasks.factory;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.dsl.CoreAnnotationProcessorOptions;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.model.SyncIssue;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import java.io.File;
import java.util.Map;
import java.util.concurrent.Callable;
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
        javacTask.compileSdkVersion = scope.getGlobalScope().getExtension().getCompileSdkVersion();
        javacTask.mBuildContext = scope.getInstantRunBuildContext();

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
            javacTask.getOptions().setBootClasspath(
                    Joiner.on(File.pathSeparator).join(
                            scope.getGlobalScope().getAndroidBuilder()
                                    .getBootClasspathAsStrings(false)));
        }

        ConventionMappingHelper.map(
                javacTask,
                "classpath",
                () ->
                        scope.getPreJavacClasspath()
                                .plus(scope.getVariantData().getAllGeneratedBytecode()));

        javacTask.setDestinationDir(scope.getJavaOutputDir());

        CompileOptions compileOptions = scope.getGlobalScope().getExtension().getCompileOptions();

        AbstractCompilesUtil.configureLanguageLevel(
                javacTask,
                compileOptions,
                scope.getGlobalScope().getExtension().getCompileSdkVersion(),
                scope.getJava8LangSupportType());

        javacTask.getOptions().setEncoding(compileOptions.getEncoding());

        Project project = scope.getGlobalScope().getProject();

        Boolean includeCompileClasspath =
                scope.getVariantConfiguration()
                        .getJavaCompileOptions()
                        .getAnnotationProcessorOptions()
                        .getIncludeCompileClasspath();

        Configuration annotationProcessorConfiguration =
                scope.getVariantData().getVariantDependency().getAnnotationProcessorConfiguration();

        if (!project.getPlugins().hasPlugin(AbstractCompilesUtil.ANDROID_APT_PLUGIN_NAME)) {
            FileCollection processorPath =
                    Boolean.TRUE.equals(includeCompileClasspath)
                            ? project.files((Callable) javacTask::getClasspath)
                            : project.files();
            processorPath = processorPath.plus(annotationProcessorConfiguration);
            javacTask.getOptions().setAnnotationProcessorPath(processorPath);
        }

        boolean incremental =
                AbstractCompilesUtil.isIncremental(
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
                scope.getVariantConfiguration()
                        .getJavaCompileOptions()
                        .getAnnotationProcessorOptions();

        if (!annotationProcessorOptions.getClassNames().isEmpty()) {
            javacTask.getOptions().getCompilerArgs().add("-processor");
            javacTask
                    .getOptions()
                    .getCompilerArgs()
                    .add(Joiner.on(',').join(annotationProcessorOptions.getClassNames()));
        }
        if ((!annotationProcessorConfiguration.getAllDependencies().isEmpty()
                        || !annotationProcessorOptions.getClassNames().isEmpty())
                && project.getPlugins().hasPlugin(AbstractCompilesUtil.ANDROID_APT_PLUGIN_NAME)) {
            // warn user if using android-apt plugin, as it overwrites the annotation processor opts
            scope.getGlobalScope()
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
                javacTask
                        .getOptions()
                        .getCompilerArgs()
                        .add("-A" + arg.getKey() + "=" + arg.getValue());
            }
        }

        javacTask.getOptions().getCompilerArgs().add("-s");
        javacTask.getOptions().getCompilerArgs().add(
                scope.getAnnotationProcessorOutputDir().getAbsolutePath());

    }
}
