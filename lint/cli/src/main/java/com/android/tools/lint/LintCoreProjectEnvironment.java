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

package com.android.tools.lint;

import com.android.annotations.NonNull;
import com.google.common.collect.Sets;
import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreJavaFileManager;
import com.intellij.core.JavaCoreApplicationEnvironment;
import com.intellij.core.JavaCoreProjectEnvironment;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiElementFinderImpl;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCliJavaFileManagerImpl;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;

public class LintCoreProjectEnvironment extends JavaCoreProjectEnvironment {
    @NonNull
    public static LintCoreProjectEnvironment create(
            @NonNull Disposable parentDisposable,
            @NonNull JavaCoreApplicationEnvironment applicationEnvironment) {
        return new LintCoreProjectEnvironment(parentDisposable, applicationEnvironment);
    }

    @Override
    protected void preregisterServices() {
        super.preregisterServices();
        KotlinCoreEnvironment.registerProjectExtensionPoints(Extensions.getArea(getProject()));
    }

    @Override
    protected void registerJavaPsiFacade() {
        MockProject project = getProject();
        ExtensionsArea area = Extensions.getArea(project);

        project.registerService(
                CoreJavaFileManager.class,
                (CoreJavaFileManager) ServiceManager.getService(project, JavaFileManager.class));

        area.getExtensionPoint(PsiElementFinder.EP_NAME)
                .registerExtension(
                        new PsiElementFinderImpl(
                                project,
                                ServiceManager.getService(project, JavaFileManager.class)));

        super.registerJavaPsiFacade();
    }

    public LintCoreProjectEnvironment(
            Disposable parentDisposable, CoreApplicationEnvironment applicationEnvironment) {
        super(parentDisposable, applicationEnvironment);

        MockProject project = getProject();
        ExtensionsArea area = Extensions.getArea(project);
        LintCoreApplicationEnvironment.registerProjectExtensionPoints(area);
        LintCoreApplicationEnvironment.registerProjectServices(this);
    }

    private List<File> myPaths = new ArrayList<>();

    public List<File> getPaths() {
        return myPaths;
    }

    public void registerPaths(@NonNull List<File> classpath) {
        myPaths.addAll(classpath);

        int expectedSize = classpath.size();
        Set<File> files = Sets.newHashSetWithExpectedSize(expectedSize);

        VirtualFileSystem local = StandardFileSystems.local();

        for (File path : classpath) {
            if (files.contains(path)) {
                continue;
            }
            files.add(path);

            if (path.exists()) {
                if (path.isFile()) {
                    // Make sure these paths are absolute - nested jar file systems
                    // do not work correctly with relative paths (for example
                    // JavaPsiFacade.findClass will not find classes in these jar
                    // file systems.)
                    if (!path.isAbsolute()) {
                        path = path.getAbsoluteFile();
                    }
                    addJarToClassPath(path);
                } else if (path.isDirectory()) {
                    VirtualFile virtualFile = local.findFileByPath(path.getPath());
                    if (virtualFile != null) {
                        addSourcesToClasspath(virtualFile);
                    }
                }
            }
        }
    }

    @Override
    protected JavaFileManager createCoreFileManager() {
        PsiManager psiManager = PsiManager.getInstance(getProject());
        return new KotlinCliJavaFileManagerImpl(psiManager);
    }
}
