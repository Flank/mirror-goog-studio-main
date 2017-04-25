/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.lint.helpers;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UastParser;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameIdentifierOwner;
import java.io.File;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UFile;
import org.jetbrains.uast.UIdentifier;
import org.jetbrains.uast.UastContext;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.psi.UElementWithLocation;

@SuppressWarnings("UnnecessaryFullyQualifiedName") // class traffics in Project from both lint and openapi: be explicit
public class DefaultUastParser extends UastParser {
    private final UastContext uastContext;
    private final JavaEvaluator javaEvaluator;

    public DefaultUastParser(@Nullable com.android.tools.lint.detector.api.Project project,
            @NonNull com.intellij.openapi.project.Project p) {
        javaEvaluator = createEvaluator(project, p);
        if (!p.isDisposed()) {
            uastContext = ServiceManager.getService(p, UastContext.class);
        } else {
            uastContext = null;
        }
    }

    @NonNull
    protected DefaultJavaEvaluator createEvaluator(@Nullable Project project,
            @NonNull com.intellij.openapi.project.Project p) {
        return new DefaultJavaEvaluator(p, project);
    }

    /**
     * Prepare to parse the given contexts. This method will be called before
     * a series of {@link #parse(JavaContext)} calls, which allows some
     * parsers to do up front global computation in case they want to more
     * efficiently process multiple files at the same time. This allows a single
     * type-attribution pass for example, which is a lot more efficient than
     * performing global type analysis over and over again for each individual
     * file
     *
     * @param contexts a list of contexts to be parsed
     * @return true if the preparation succeeded; false if there were errors
     */
    @Override
    public boolean prepare(@NonNull List<JavaContext> contexts) {
        return true;
    }

    /**
     * Returns an evaluator which can perform various resolution tasks,
     * evaluate inheritance lookup etc.
     *
     * @return an evaluator
     */
    @Override
    @NonNull
    public JavaEvaluator getEvaluator() {
        return javaEvaluator;
    }

    /**
     * Parse the file pointed to by the given context.
     *
     * @param context the context pointing to the file to be parsed, typically via {@link
     *                Context#getContents()} but the file handle ( {@link Context#file} can also be
     *                used to map to an existing editor buffer in the surrounding tool, etc)
     * @return the compilation unit node for the file
     */
    @Override
    @Nullable
    public UFile parse(@NonNull JavaContext context) {
        com.intellij.openapi.project.Project ideaProject = uastContext.getProject();

        VirtualFile virtualFile = StandardFileSystems.local()
                .findFileByPath(context.file.getAbsolutePath());
        if (virtualFile == null) {
            return null;
        }

        PsiFile psiFile = PsiManager.getInstance(ideaProject).findFile(virtualFile);
        if (psiFile == null) {
            return null;
        }

        UElement uElement = uastContext.convertElementWithParent(psiFile, UFile.class);
        if (!(uElement instanceof UFile)) {
            // No need to log this; the parser should be reporting
            // a full warning (such as IssueRegistry#PARSER_ERROR)
            // with details, location, etc.
            return null;
        }

        return (UFile) uElement;
    }

    /**
     * Returns a UastContext which can provide UAST representations for source files
     */
    @Override
    @Nullable
    public UastContext getUastContext() {
        return uastContext;
    }

    /**
     * Returns a {@link Location} for the given element
     *
     * @param context information about the file being parsed
     * @param element the element to create a location for
     * @return a location for the given node
     */
    @Override
    @SuppressWarnings("MethodMayBeStatic") // subclasses may want to override/optimize
    @NonNull
    public Location getLocation(@NonNull JavaContext context, @NonNull PsiElement element) {
        if (element instanceof PsiCompiledElement) {
            PsiFile containingFile = element.getContainingFile();
            if (containingFile != null) {
                VirtualFile virtualFile = containingFile.getVirtualFile();
                if (virtualFile != null) {
                    return Location.create(VfsUtilCore.virtualToIoFile(virtualFile));
                }
            }
            return Location.create(context.file);
        }

        TextRange range = element.getTextRange();
        PsiFile containingFile = element.getContainingFile();
        File file = context.file;
        CharSequence contents = context.getContents();
        if (!containingFile.equals(context.getPsiFile())) {
            // Reporting an error in a different file.
            if (context.getDriver().getScope().size() == 1) {
                // Don't bother with this error if it's in a different file during single-file analysis
                return Location.NONE;
            }
            File ioFile = getFile(containingFile);
            if (ioFile == null) {
                return Location.NONE;
            }
            file = ioFile;
            contents = getFileContents(containingFile);
        }
        return Location.create(file, contents, range.getStartOffset(), range.getEndOffset())
                .setSource(element);
    }

    @Override
    @SuppressWarnings("MethodMayBeStatic") // subclasses may want to override/optimize
    @NonNull
    public Location getLocation(@NonNull JavaContext context, @NonNull UElement element) {
        if (element instanceof UElementWithLocation) {
            UFile file = UastUtils.getContainingFile(element);
            if (file == null) {
                return Location.NONE;
            }
            File ioFile = UastUtils.getIoFile(file);
            if (ioFile == null) {
                return Location.NONE;
            }
            UElementWithLocation segment = (UElementWithLocation) element;
            return Location.create(ioFile, file.getPsi().getText(), segment.getStartOffset(),
                    segment.getEndOffset()).setSource(element);
        } else {
            PsiElement psiElement = element.getPsi();
            if (psiElement != null) {
                return getLocation(context, psiElement);
            }
            UElement parent = element.getUastParent();
            if (parent != null) {
                return getLocation(context, parent);
            }
        }

        return Location.NONE;
    }

    @Override
    @SuppressWarnings("MethodMayBeStatic") // subclasses may want to override/optimize
    @NonNull
    public Location getCallLocation(@NonNull JavaContext context, @NonNull UCallExpression call,
            boolean includeReceiver, boolean includeArguments) {
        UExpression receiver = call.getReceiver();
        if (!includeReceiver || receiver == null) {
            if (includeArguments) {
                // Method with arguments but no receiver is the default range for UCallExpressions
                return getLocation(context, call);
            }
            // Just the method name
            UIdentifier methodIdentifier = call.getMethodIdentifier();
            if (methodIdentifier != null) {
                return getLocation(context, methodIdentifier);
            }
        } else {
            if (!includeArguments) {
                UIdentifier methodIdentifier = call.getMethodIdentifier();
                if (methodIdentifier != null) {
                    return getRangeLocation(context, receiver, 0, methodIdentifier, 0);
                }
            }
            return getRangeLocation(context, receiver, 0, call, 0);
        }

        return getLocation(context, call);
    }

    @Override
    @Nullable
    public File getFile(@NonNull PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        return virtualFile != null ? VfsUtilCore.virtualToIoFile(virtualFile) : null;
    }

    @Override
    @NonNull
    public CharSequence getFileContents(@NonNull PsiFile file) {
        return file.getText();
    }

    @Override
    @NonNull
    public Location createLocation(@NonNull PsiElement element) {
        TextRange range = element.getTextRange();
        PsiFile containingFile = element.getContainingFile();
        CharSequence contents;
        File file = getFile(containingFile);
        if (file == null) {
            return Location.NONE;
        }
        contents = getFileContents(containingFile);
        return Location.create(file, contents, range.getStartOffset(), range.getEndOffset())
                .setSource(element);
    }

    @Override
    @NonNull
    public Location createLocation(@NonNull UElement element) {
        if (element instanceof UElementWithLocation) {
            UFile file = UastUtils.getContainingFile(element);
            if (file == null) {
                return Location.NONE;
            }
            File ioFile = UastUtils.getIoFile(file);
            if (ioFile == null) {
                return Location.NONE;
            }
            UElementWithLocation segment = (UElementWithLocation) element;
            return Location.create(ioFile, file.getPsi().getText(), segment.getStartOffset(),
                    segment.getEndOffset()).setSource(element);
        } else {
            PsiElement psiElement = element.getPsi();
            if (psiElement != null) {
                return createLocation(psiElement);
            }
            UElement parent = element.getUastParent();
            if (parent != null) {
                return createLocation(parent);
            }
        }

        return Location.NONE;
    }

    /**
     * Returns a {@link Location} for the given node range (from the starting offset of the first
     * node to the ending offset of the second node).
     *
     * @param context   information about the file being parsed
     * @param from      the AST node to get a starting location from
     * @param fromDelta Offset delta to apply to the starting offset
     * @param to        the AST node to get a ending location from
     * @param toDelta   Offset delta to apply to the ending offset
     * @return a location for the given node
     */
    @Override
    @SuppressWarnings("MethodMayBeStatic") // subclasses may want to override/optimize
    @NonNull
    public Location getRangeLocation(@NonNull JavaContext context, @NonNull PsiElement from,
            int fromDelta, @NonNull PsiElement to, int toDelta) {
        CharSequence contents = context.getContents();
        TextRange fromRange = from.getTextRange();
        int start = Math.max(0, fromRange.getStartOffset()+fromDelta);
        int end = Math.min(contents == null ? Integer.MAX_VALUE : contents.length(),
                to.getTextRange().getEndOffset()+toDelta);
        if (end <= start) {
            // Some AST nodes don't have proper bounds, such as empty parameter lists
            return Location.create(context.file, contents, start, fromRange.getEndOffset())
                    .setSource(from);
        }
        return Location.create(context.file, contents, start, end).setSource(from);
    }

    @Nullable
    private static TextRange getTextRange(@NonNull UElement element) {
        if (element instanceof UElementWithLocation) {
            UElementWithLocation segment = (UElementWithLocation) element;
            return new TextRange(segment.getStartOffset(), segment.getEndOffset());
        } else {
            PsiElement psiElement = element.getPsi();
            if (psiElement != null) {
                return psiElement.getTextRange();
            }
        }

        return null;
    }

    @Override
    @SuppressWarnings("MethodMayBeStatic") // subclasses may want to override/optimize
    @NonNull
    public Location getRangeLocation(@NonNull JavaContext context, @NonNull UElement from,
            int fromDelta, @NonNull UElement to, int toDelta) {
        CharSequence contents = context.getContents();
        TextRange fromRange = getTextRange(from);
        TextRange toRange = getTextRange(to);

        // Make sure this element is reported in the correct file
        File file = context.file;
        PsiElement psi = findPsi(from);
        if (psi != null) {
            PsiFile containingFile = psi.getContainingFile();
            contents = context.getContents();
            if (!containingFile.equals(context.getPsiFile())) {
                // Reporting an error in a different file.
                if (context.getDriver().getScope().size() == 1) {
                    // Don't bother with this error if it's in a different file during single-file analysis
                    return Location.NONE;
                }
                File ioFile = getFile(containingFile);
                if (ioFile == null) {
                    return Location.NONE;
                }
                file = ioFile;
                contents = getFileContents(containingFile);
            }
        }

        if (fromRange != null && toRange != null) {
            int start = Math.max(0, fromRange.getStartOffset()+fromDelta);
            int end = Math.min(contents == null ? Integer.MAX_VALUE : contents.length(),
                    toRange.getEndOffset()+toDelta);
            if (end <= start) {
                // Some AST nodes don't have proper bounds, such as empty parameter lists
                return Location.create(file, contents, start, fromRange.getEndOffset())
                        .setSource(from);
            }
            return Location.create(file, contents, start, end).setSource(from);
        }

        return Location.create(file).setSource(from);
    }

    @Nullable
    private static PsiElement findPsi(@Nullable UElement element) {
        while (element != null) {
            PsiElement psi = element.getPsi();
            if (psi != null) {
                return psi;
            }
            element = element.getUastParent();
        }
        return null;
    }

    /**
     * Like {@link #getRangeLocation(JavaContext, PsiElement, int, PsiElement, int)}
     * but both offsets are relative to the starting offset of the given node. This is
     * sometimes more convenient than operating relative to the ending offset when you
     * have a fixed range in mind.
     *
     * @param context   information about the file being parsed
     * @param from      the AST node to get a starting location from
     * @param fromDelta Offset delta to apply to the starting offset
     * @param toDelta   Offset delta to apply to the starting offset
     * @return a location for the given node
     */
    @Override
    @SuppressWarnings("MethodMayBeStatic") // subclasses may want to override/optimize
    @NonNull
    public Location getRangeLocation(@NonNull JavaContext context, @NonNull PsiElement from,
            int fromDelta, int toDelta) {
        return getRangeLocation(context, from, fromDelta, from,
                -(from.getTextRange().getLength()-toDelta));
    }

    @Override
    @SuppressWarnings("MethodMayBeStatic") // subclasses may want to override/optimize
    @NonNull
    public Location getRangeLocation(@NonNull JavaContext context, @NonNull UElement from,
            int fromDelta, int toDelta) {
        TextRange fromRange = getTextRange(from);
        if (fromRange != null) {
            return getRangeLocation(context, from, fromDelta, from,
                    -(fromRange.getLength()-toDelta));
        }
        return Location.create(context.file).setSource(from);
    }

    /**
     * Returns a {@link Location} for the given node. This attempts to pick a shorter
     * location range than the entire node; for a class or method for example, it picks
     * the name node (if found). For statement constructs such as a {@code switch} statement
     * it will highlight the keyword, etc.
     *
     * @param context information about the file being parsed
     * @param element the node to create a location for
     * @return a location for the given node
     */
    @Override
    @NonNull
    public Location getNameLocation(@NonNull JavaContext context, @NonNull PsiElement element) {
        PsiElement nameNode = JavaContext.findNameElement(element);
        if (nameNode != null) {
            element = nameNode;
        }

        return getLocation(context, element);
    }

    @Override
    @NonNull
    public Location getNameLocation(@NonNull JavaContext context, @NonNull UElement element) {
        UElement nameNode = JavaContext.findNameElement(element);
        if (nameNode != null) {
            element = nameNode;
        } else if (element instanceof PsiNameIdentifierOwner) {
            PsiElement nameIdentifier = ((PsiNameIdentifierOwner) element).getNameIdentifier();
            if (nameIdentifier != null) {
                return getLocation(context, nameIdentifier);
            }
        }

        return getLocation(context, element);
    }
}
