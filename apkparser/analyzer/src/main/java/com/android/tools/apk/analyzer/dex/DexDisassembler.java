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
package com.android.tools.apk.analyzer.dex;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.apk.analyzer.internal.SigUtils;
import com.android.tools.apk.analyzer.internal.rewriters.FieldReferenceWithNameRewriter;
import com.android.tools.apk.analyzer.internal.rewriters.MethodReferenceWithNameRewriter;
import com.android.tools.proguard.ProguardMap;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.jf.baksmali.Adaptors.ClassDefinition;
import org.jf.baksmali.Adaptors.MethodDefinition;
import org.jf.baksmali.BaksmaliOptions;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.rewriter.DexRewriter;
import org.jf.dexlib2.rewriter.Rewriter;
import org.jf.dexlib2.rewriter.RewriterModule;
import org.jf.dexlib2.rewriter.Rewriters;
import org.jf.dexlib2.rewriter.TypeRewriter;
import org.jf.dexlib2.util.ReferenceUtil;
import org.jf.util.IndentingWriter;

public class DexDisassembler {
    @NonNull private final DexFile dexFile;
    @Nullable private final ProguardMap proguardMap;

    public DexDisassembler(@NonNull DexBackedDexFile dexFile, @Nullable ProguardMap proguardMap) {
        this.dexFile = proguardMap == null ? dexFile : rewriteDexFile(dexFile, proguardMap);
        this.proguardMap = proguardMap;
    }

    @NonNull
    public String disassembleMethod(@NonNull String fqcn, @NonNull String methodDescriptor)
            throws IOException {
        fqcn = PackageTreeCreator.decodeClassName(SigUtils.typeToSignature(fqcn), proguardMap);
        Optional<? extends ClassDef> classDef = getClassDef(fqcn);
        if (!classDef.isPresent()) {
            throw new IllegalStateException("Unable to locate class definition for " + fqcn);
        }

        Optional<? extends Method> method =
                StreamSupport.stream(classDef.get().getMethods().spliterator(), false)
                        .filter(m -> methodDescriptor.equals(ReferenceUtil.getMethodDescriptor(m)))
                        .findFirst();

        if (!method.isPresent()) {
            throw new IllegalStateException(
                    "Unable to locate method definition in class for method " + methodDescriptor);
        }

        return getMethodDexCode(classDef.get(), method.get());
    }

    @NonNull
    public String disassembleMethod(@NonNull String fqcn, @NonNull MethodReference methodRef)
            throws IOException {
        fqcn = PackageTreeCreator.decodeClassName(SigUtils.typeToSignature(fqcn), proguardMap);
        Optional<? extends ClassDef> classDef = getClassDef(fqcn);
        if (!classDef.isPresent()) {
            throw new IllegalStateException("Unable to locate class definition for " + fqcn);
        }
        MethodReference finalMethodRef =
                proguardMap != null
                        ? getRewriter(proguardMap).getMethodReferenceRewriter().rewrite(methodRef)
                        : methodRef;

        Optional<? extends Method> method =
                StreamSupport.stream(classDef.get().getMethods().spliterator(), false)
                        .filter(finalMethodRef::equals)
                        .findFirst();

        if (!method.isPresent()) {
            throw new IllegalStateException(
                    "Unable to locate method definition in class for method " + methodRef);
        }

        return getMethodDexCode(classDef.get(), method.get());
    }

    @NonNull
    private static String getMethodDexCode(ClassDef classDef, Method method) throws IOException {
        BaksmaliOptions options = new BaksmaliOptions();
        ClassDefinition classDefinition = new ClassDefinition(options, classDef);

        StringWriter writer = new StringWriter(1024);
        try (IndentingWriter iw = new IndentingWriter(writer)) {
            MethodImplementation methodImpl = method.getImplementation();
            if (methodImpl == null) {
                MethodDefinition.writeEmptyMethodTo(iw, method, options);
            } else {
                MethodDefinition methodDefinition =
                        new MethodDefinition(classDefinition, method, methodImpl);
                methodDefinition.writeTo(iw);
            }
        }

        return writer.toString().replace("\r", "");
    }

    @NonNull
    public String disassembleClass(@NonNull String fqcn) throws IOException {
        fqcn = PackageTreeCreator.decodeClassName(SigUtils.typeToSignature(fqcn), proguardMap);
        Optional<? extends ClassDef> classDef = getClassDef(fqcn);
        if (!classDef.isPresent()) {
            throw new IllegalStateException("Unable to locate class definition for " + fqcn);
        }

        BaksmaliOptions options = new BaksmaliOptions();
        ClassDefinition classDefinition = new ClassDefinition(options, classDef.get());

        StringWriter writer = new StringWriter(1024);
        try (IndentingWriter iw = new IndentingWriter(writer)) {
            classDefinition.writeTo(iw);
        }
        return writer.toString().replace("\r", "");
    }

    private static DexFile rewriteDexFile(@NonNull DexFile dexFile, @NonNull ProguardMap map) {
        DexRewriter rewriter = getRewriter(map);
        return rewriter.rewriteDexFile(dexFile);
    }

    @NonNull
    private static DexRewriter getRewriter(@NonNull ProguardMap map) {
        return new DexRewriter(
                new RewriterModule() {
                    @NonNull
                    @Override
                    public Rewriter<String> getTypeRewriter(@NonNull Rewriters rewriters) {
                        return new TypeRewriter() {
                            @NonNull
                            @Override
                            public String rewrite(@NonNull String typeName) {
                                return SigUtils.typeToSignature(
                                        PackageTreeCreator.decodeClassName(typeName, map));
                            }
                        };
                    }

                    @NonNull
                    @Override
                    public Rewriter<FieldReference> getFieldReferenceRewriter(
                            @NonNull Rewriters rewriters) {
                        return new FieldReferenceWithNameRewriter(rewriters) {
                            @Override
                            public String rewriteName(FieldReference fieldReference) {
                                return PackageTreeCreator.decodeFieldName(fieldReference, map);
                            }
                        };
                    }

                    @NonNull
                    @Override
                    public Rewriter<MethodReference> getMethodReferenceRewriter(
                            @NonNull Rewriters rewriters) {
                        return new MethodReferenceWithNameRewriter(rewriters) {
                            @Override
                            public String rewriteName(MethodReference methodReference) {
                                return PackageTreeCreator.decodeMethodName(methodReference, map);
                            }
                        };
                    }
                });
    }

    @NonNull
    private Optional<? extends ClassDef> getClassDef(@NonNull String fqcn) {
        return dexFile.getClasses()
                .stream()
                .filter(c -> fqcn.equals(SigUtils.signatureToName(c.getType())))
                .findFirst();
    }
}
