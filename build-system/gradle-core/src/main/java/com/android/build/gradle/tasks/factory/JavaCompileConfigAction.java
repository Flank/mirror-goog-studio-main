package com.android.build.gradle.tasks.factory;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.ANNOTATION_PROCESSOR;
import static com.android.builder.core.VariantType.LIBRARY;
import static com.android.builder.core.VariantType.UNIT_TEST;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.dsl.CoreAnnotationProcessorOptions;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.dependency.level2.AndroidDependency;
import com.android.builder.model.SyncIssue;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
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
        final BaseVariantData testedVariantData = scope.getTestedVariantData();
        scope.getVariantData().javacTask = javacTask;
        javacTask.compileSdkVersion = scope.getGlobalScope().getExtension().getCompileSdkVersion();
        javacTask.mBuildContext = scope.getBuildContext();

        // We can't just pass the collection directly, as the instanceof check in the incremental
        // compile doesn't work recursively currently, so every ConfigurableFileTree needs to be
        // directly in the source array.
        for (ConfigurableFileTree fileTree: scope.getVariantData().getJavaSources()) {
            javacTask.source(fileTree);
        }

        // javac 1.8 may generate code that uses class not available in android.jar.  This is fine
        // if jack is used to compile code for the app and this compile task is created only for
        // unit test.  In which case, we want to keep the default bootstrap classpath.
        final boolean keepDefaultBootstrap =
                scope.getVariantConfiguration().isJackEnabled()
                        && JavaVersion.current().isJava8Compatible();

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
                () -> {
                    FileCollection classpath = scope.getJavaClasspath();
                    Project project = scope.getGlobalScope().getProject();
                    if (keepDefaultBootstrap) {
                        classpath =
                                classpath.plus(
                                        project.files(
                                                scope.getGlobalScope()
                                                        .getAndroidBuilder()
                                                        .getBootClasspath(false)));
                    }

                    if (testedVariantData != null) {
                        // For libraries, the classpath from androidBuilder includes the library
                        // output (bundle/classes.jar) as a normal dependency. In unit tests we
                        // don't want to package the jar at every run, so we use the *.class
                        // files instead.
                        if (!testedVariantData.getType().equals(LIBRARY)
                                || scope.getVariantData().getType().equals(UNIT_TEST)) {
                            classpath =
                                    classpath.plus(testedVariantData.getScope().getJavaClasspath());
                            classpath =
                                    classpath.plus(
                                            project.files(
                                                    testedVariantData
                                                            .getScope()
                                                            .getJavaOutputDir()));
                        }

                        if (scope.getVariantData().getType().equals(UNIT_TEST)
                                && testedVariantData.getType().equals(LIBRARY)) {
                            // The bundled classes.jar may exist, but it's probably old. Don't
                            // use it, we already have the *.class files in the classpath.
                            AndroidDependency testedLibrary =
                                    testedVariantData.getVariantConfiguration().getOutput();
                            if (testedLibrary != null) {
                                File jarFile = testedLibrary.getJarFile();
                                classpath = classpath.minus(project.files(jarFile));
                            }
                        }
                    }
                    return classpath;
                });

        javacTask.setDestinationDir(scope.getJavaOutputDir());

        CompileOptions compileOptions = scope.getGlobalScope().getExtension().getCompileOptions();

        AbstractCompilesUtil.configureLanguageLevel(
                javacTask,
                compileOptions,
                scope.getGlobalScope().getExtension().getCompileSdkVersion(),
                scope.getVariantConfiguration().isJackEnabled());

        javacTask.getOptions().setEncoding(compileOptions.getEncoding());

        Project project = scope.getGlobalScope().getProject();

        Configuration annotationProcessorConfiguration =
                scope.getVariantData().getVariantDependency().getAnnotationProcessorConfiguration();


        Boolean includeCompileClasspath =
                scope.getVariantConfiguration()
                        .getJavaCompileOptions()
                        .getAnnotationProcessorOptions()
                        .getIncludeCompileClasspath();
        Preconditions.checkNotNull(includeCompileClasspath);
        FileCollection processorPath = includeCompileClasspath
                ? javacTask.getClasspath()
                : project.files();
        processorPath =
                processorPath.plus(scope.getArtifactFileCollection(ANNOTATION_PROCESSOR, ALL, JAR));
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
                && project.getPlugins().hasPlugin("com.neenbedankt.android-apt")) {
            // warn user if using android-apt plugin, as it overwrites the annotation processor opts
            scope.getGlobalScope().getAndroidBuilder().getErrorReporter().handleSyncWarning(
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
}
