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
import com.intellij.codeInsight.ContainerProvider;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.InferredAnnotationsManager;
import com.intellij.codeInsight.runner.JavaMainMethodProvider;
import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreJavaFileManager;
import com.intellij.core.JavaCoreApplicationEnvironment;
import com.intellij.core.JavaCoreProjectEnvironment;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.fileTypes.FileTypeExtensionPoint;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.FileContextProvider;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.augment.TypeAnnotationModifier;
import com.intellij.psi.compiled.ClassFileDecompilers;
import com.intellij.psi.impl.JavaClassSupersImpl;
import com.intellij.psi.impl.PsiElementFinderImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessor;
import com.intellij.psi.impl.compiled.ClsCustomNavigationPolicy;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.meta.MetaDataContributor;
import com.intellij.psi.stubs.BinaryFileStubBuilders;
import com.intellij.psi.util.JavaClassSupers;

public class LintCoreApplicationEnvironment extends JavaCoreApplicationEnvironment {
    private static LintCoreApplicationEnvironment environment;

    @NonNull
    public static LintCoreApplicationEnvironment get() {
        if (environment == null) {
            Disposable parentDisposable = Disposer.newDisposable();
            environment = createApplicationEnvironment(parentDisposable);
        }

        return environment;
    }

    public LintCoreApplicationEnvironment(Disposable parentDisposable) {
        super(parentDisposable);
    }

    private static LintCoreApplicationEnvironment createApplicationEnvironment(Disposable parentDisposable/*, List<String> configFilePaths*/) {
        Extensions.cleanRootArea(parentDisposable);
        registerAppExtensionPoints();
        LintCoreApplicationEnvironment applicationEnvironment = new LintCoreApplicationEnvironment(parentDisposable);

        registerApplicationServicesForCLI(applicationEnvironment);
        registerApplicationServices(applicationEnvironment);

        return applicationEnvironment;
    }

    private static void registerAppExtensionPoints() {
        ExtensionsArea rootArea = Extensions.getRootArea();
        CoreApplicationEnvironment.registerExtensionPoint(rootArea, BinaryFileStubBuilders.EP_NAME, FileTypeExtensionPoint.class);
        CoreApplicationEnvironment.registerExtensionPoint(rootArea, FileContextProvider.EP_NAME, FileContextProvider.class);
        CoreApplicationEnvironment.registerExtensionPoint(rootArea, MetaDataContributor.EP_NAME, MetaDataContributor.class);
        CoreApplicationEnvironment.registerExtensionPoint(rootArea, PsiAugmentProvider.EP_NAME, PsiAugmentProvider.class);
        CoreApplicationEnvironment.registerExtensionPoint(rootArea, JavaMainMethodProvider.EP_NAME, JavaMainMethodProvider.class);
        CoreApplicationEnvironment.registerExtensionPoint(rootArea, ContainerProvider.EP_NAME, ContainerProvider.class);
        CoreApplicationEnvironment.registerExtensionPoint(rootArea, ClsCustomNavigationPolicy.EP_NAME, ClsCustomNavigationPolicy.class);
        CoreApplicationEnvironment.registerExtensionPoint(rootArea, ClassFileDecompilers.EP_NAME, ClassFileDecompilers.Decompiler.class);
        CoreApplicationEnvironment.registerExtensionPoint(rootArea, TypeAnnotationModifier.EP_NAME, TypeAnnotationModifier.class);
    }

    private static void registerApplicationServicesForCLI(JavaCoreApplicationEnvironment applicationEnvironment) {
        // ability to get text from annotations xml files
        applicationEnvironment.registerFileType(PlainTextFileType.INSTANCE, "xml");
        applicationEnvironment.registerParserDefinition(new JavaParserDefinition());
    }

    private static void registerApplicationServices(JavaCoreApplicationEnvironment applicationEnvironment) {
        applicationEnvironment.getApplication().registerService(JavaClassSupers.class, JavaClassSupersImpl.class);
        applicationEnvironment.getApplication().registerService(TransactionGuard.class, TransactionGuardImpl.class);
    }

    static void registerProjectExtensionPoints(ExtensionsArea area) {
        CoreApplicationEnvironment.registerExtensionPoint(area, PsiTreeChangePreprocessor.EP_NAME, PsiTreeChangePreprocessor.class);
        CoreApplicationEnvironment.registerExtensionPoint(area, PsiElementFinder.EP_NAME, PsiElementFinder.class);
    }

    public static void registerProjectServices(JavaCoreProjectEnvironment projectEnvironment) {
        MockProject project = projectEnvironment.getProject();
        project.registerService(ExternalAnnotationsManager.class, LintExternalAnnotationsManager.class);
        project.registerService(InferredAnnotationsManager.class, LintInferredAnnotationsManager.class);
    }

    static void registerProjectServicesForCLI(
            JavaCoreProjectEnvironment projectEnvironment) {
        MockProject project = projectEnvironment.getProject();
        project.registerService(CoreJavaFileManager.class,
                (CoreJavaFileManager) ServiceManager.getService(project, JavaFileManager.class));

        ExtensionsArea area = Extensions.getArea(project);
        area.getExtensionPoint(PsiElementFinder.EP_NAME).registerExtension(
                new PsiElementFinderImpl(
                        project, ServiceManager.getService(
                        project, JavaFileManager.class)));
    }
}
