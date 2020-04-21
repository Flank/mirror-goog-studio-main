/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.res.shrinker.usages;

import static com.android.SdkConstants.DOT_CLASS;
import static com.android.SdkConstants.DOT_JAR;
import static org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.res.shrinker.ResourceShrinkerModel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Records resource usages, detects usages of WebViews and {@code Resources#getIdentifier},
 * gathers string constants from compiled .class files.
 */
public class JvmClassesUsageRecorder implements ResourceUsageRecorder {
    private final Path root;

    /**
     * Constructs resource usages recorder from compiled .class files.
     *
     * @param root directory starting from which all .class and .jar files are analyzed.
     */
    public JvmClassesUsageRecorder(Path root) {
        this.root = root;
    }

    @Override
    public void recordUsages(@NonNull ResourceShrinkerModel model) throws IOException {
        recordUsagesFrom(model, root);
    }

    private void recordUsagesFrom(ResourceShrinkerModel model, Path startPath) throws IOException {
        // Record resource usages from jvm compiled classes. The following cases are covered:
        // 1. Integer constant which refers to resource id.
        // 2. Reference to static field in R classes.
        // 3. Usages of android.content.res.Resources.getIdentifier(...) and
        //    android.webkit.WebView.load...
        // 4. All strings which might be used to reference resources by name via
        //    Resources.getIdentifier.

        Files.walk(startPath)
                .filter(path -> Files.isRegularFile(path))
                .filter(path -> isJar(path) || isClass(path))
                .forEach(
                        path -> {
                            if (isClass(path)) {
                                recordClass(model, path);
                            } else {
                                recordJar(model, path);
                            }
                        });
    }

    private void recordClass(ResourceShrinkerModel model, Path path) {
        try {
            ClassesUsageSupport usageSupport = new ClassesUsageSupport(model);
            byte[] bytes = Files.readAllBytes(path);
            ClassReader classReader = new ClassReader(bytes);
            String className = classReader.getClassName();
            if (usageSupport.isResourceClass(className)) {
                return;
            }
            classReader.accept(
                    new UsageVisitor(model, usageSupport, path, className),
                    SKIP_DEBUG | SKIP_FRAMES);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void recordJar(ResourceShrinkerModel model, Path path) {
        try (FileSystem zip = FileSystems.newFileSystem(path, null)) {
            recordUsagesFrom(model, zip.getPath("/"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isJar(Path path) {
        return path.toString().endsWith(DOT_JAR);
    }

    private static boolean isClass(Path path) {
        return path.toString().endsWith(DOT_CLASS);
    }

    /**
     * Class visitor responsible for looking for resource references in code. It looks for
     * R.type.name references (as well as inlined constants for these, in the case of non-library
     * code), as well as looking both for Resources#getIdentifier calls and recording string
     * literals, used to handle dynamic lookup of resources.
     */
    private static final class UsageVisitor extends ClassVisitor {
        private final Path mJarFile;
        private final String mCurrentClass;
        private final ResourceShrinkerModel mModel;
        private final ClassesUsageSupport mUsageSupport;

        public UsageVisitor(
                ResourceShrinkerModel model,
                ClassesUsageSupport usageSupport,
                Path jarFile,
                String name) {
            super(Opcodes.ASM5);
            mJarFile = jarFile;
            mCurrentClass = name;
            mModel = model;
            mUsageSupport = usageSupport;
        }

        @Override
        public MethodVisitor visitMethod(
                int access, final String name, String desc, String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM5) {
                @Override
                public void visitLdcInsn(Object cst) {
                    handleCodeConstant(cst, "ldc");
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                    if (opcode == Opcodes.GETSTATIC) {
                        mUsageSupport.referencedStaticField(owner, name);
                    }
                }

                @Override
                public void visitMethodInsn(
                        int opcode, String owner, String name, String desc, boolean itf) {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                    mUsageSupport.referencedMethodInvocation(owner, name, desc, mCurrentClass);
                }

                @Override
                public AnnotationVisitor visitAnnotationDefault() {
                    return new UsageVisitor.AnnotationUsageVisitor();
                }

                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    return new UsageVisitor.AnnotationUsageVisitor();
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(
                        int parameter, String desc, boolean visible) {
                    return new UsageVisitor.AnnotationUsageVisitor();
                }
            };
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            return new UsageVisitor.AnnotationUsageVisitor();
        }

        @Override
        public FieldVisitor visitField(
                int access, String name, String desc, String signature, Object value) {
            handleCodeConstant(value, "field");
            return new FieldVisitor(Opcodes.ASM5) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    return new UsageVisitor.AnnotationUsageVisitor();
                }
            };
        }

        private class AnnotationUsageVisitor extends AnnotationVisitor {
            public AnnotationUsageVisitor() {
                super(Opcodes.ASM5);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String name, String desc) {
                return new UsageVisitor.AnnotationUsageVisitor();
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                return new UsageVisitor.AnnotationUsageVisitor();
            }

            @Override
            public void visit(String name, Object value) {
                handleCodeConstant(value, "annotation");
                super.visit(name, value);
            }
        }

        /** Invoked when an ASM visitor encounters a constant: record corresponding reference */
        private void handleCodeConstant(@Nullable Object cst, @NonNull String context) {
            if (cst instanceof Integer) {
                Integer value = (Integer) cst;
                mUsageSupport.referencedInt(context, value, mJarFile, mCurrentClass);
            } else if (cst instanceof Long) {
                Long value = (Long) cst;
                mUsageSupport.referencedInt(context, value.intValue(), mJarFile, mCurrentClass);
            } else if (cst instanceof int[]) {
                int[] values = (int[]) cst;
                for (int value : values) {
                    mUsageSupport.referencedInt(context, value, mJarFile, mCurrentClass);
                }
            } else if (cst instanceof String) {
                String string = (String) cst;
                mUsageSupport.referencedString(string);
            }
        }
    }
}
