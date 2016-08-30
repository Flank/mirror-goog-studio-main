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

package com.android.build.gradle.tasks.factory;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.dsl.CoreAnnotationProcessorOptions;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.model.AndroidAtom;
import com.android.utils.ILogger;
import com.android.utils.StringHelper;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;

import java.io.File;
import java.util.Collection;

/**
 * ConfigAction to compile the final R class for atoms.
 */
public class AtomResClassJavaCompileConfigAction implements TaskConfigAction<AndroidJavaCompile> {
    private static final ILogger LOG =
            LoggerWrapper.getLogger(AtomResClassJavaCompileConfigAction.class);

    private VariantScope scope;
    private AndroidAtom androidAtom;

    public AtomResClassJavaCompileConfigAction(
            @NonNull VariantScope scope,
            @NonNull AndroidAtom androidAtom) {
        this.scope = scope;
        this.androidAtom = androidAtom;
    }

    @NonNull
    @Override
    public String getName() {
        return scope.getTaskName("compile",
                StringHelper.capitalize(androidAtom.getAtomName()) + "ResClassWithJavac");
    }

    @NonNull
    @Override
    public Class<AndroidJavaCompile> getType() {
        return AndroidJavaCompile.class;
    }

    @Override
    public void execute(@NonNull AndroidJavaCompile javacTask) {
        javacTask.compileSdkVersion = scope.getGlobalScope().getExtension().getCompileSdkVersion();
        javacTask.mBuildContext = scope.getInstantRunBuildContext();
        javacTask.source(scope.getRClassSourceOutputDir(androidAtom));
        javacTask.setDestinationDir(scope.getJavaOutputDir(androidAtom));

        CompileOptions compileOptions = scope.getGlobalScope().getExtension().getCompileOptions();
        AbstractCompilesUtil.configureLanguageLevel(
                javacTask,
                compileOptions,
                scope.getGlobalScope().getExtension().getCompileSdkVersion(),
                scope.getVariantConfiguration().getJackOptions().isEnabled());

        javacTask.getOptions().setEncoding(compileOptions.getEncoding());

        Project project = scope.getGlobalScope().getProject();

        CoreAnnotationProcessorOptions annotationProcessorOptions =
                scope.getVariantConfiguration().getJavaCompileOptions()
                        .getAnnotationProcessorOptions();

        checkNotNull(annotationProcessorOptions.getIncludeCompileClasspath());
        Collection<File> processorPath =
                Lists.newArrayList(
                        scope.getVariantData().getVariantDependency()
                                .resolveAndGetAnnotationProcessorClassPath(
                                        annotationProcessorOptions.getIncludeCompileClasspath(),
                                        scope.getGlobalScope().getAndroidBuilder().getErrorReporter()));

        // javac 1.8 may generate code that uses class not available in android.jar.  This is fine
        // if jack is used to compile code for the app and this compile task is created only for
        // unit test.  In which case, we want to keep the default bootstrap classpath.
        final boolean keepDefaultBootstrap =
                scope.getVariantConfiguration().getJackOptions().isEnabled()
                        && JavaVersion.current().isJava8Compatible();

        if (!keepDefaultBootstrap) {
            // Set boot classpath if we don't need to keep the default.  Otherwise, this is added as
            // normal classpath.
            javacTask.getOptions().setBootClasspath(
                    Joiner.on(File.pathSeparator).join(
                            scope.getGlobalScope().getAndroidBuilder()
                                    .getBootClasspathAsStrings(false)));
        }

        ConventionMappingHelper.map(javacTask, "classpath",
                scope.getGlobalScope().getProject()::files);

        boolean incremental = AbstractCompilesUtil.isIncremental(project, scope, compileOptions,
                processorPath, LOG);
        if (incremental) {
            LOG.info("Using incremental javac compilation.");
            javacTask.getOptions().setIncremental(true);
        } else {
            LOG.info("Not using incremental javac compilation.");
        }
    }
}
