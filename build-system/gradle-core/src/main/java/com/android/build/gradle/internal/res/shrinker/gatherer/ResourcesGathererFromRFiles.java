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

package com.android.build.gradle.internal.res.shrinker.gatherer;

import static com.android.SdkConstants.DOT_CLASS;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;
import static com.android.ide.common.symbols.SymbolIo.readFromAapt;
import static com.google.common.base.Charsets.UTF_8;
import static org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.internal.res.shrinker.ResourceShrinkerModel;
import com.android.ide.common.symbols.Symbol;
import com.android.ide.common.symbols.SymbolTable;
import com.android.resources.ResourceType;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Gathers application resources from R files. Supports multiple formats: compiled R classes, R java
 * files, R text files.
 */
public class ResourcesGathererFromRFiles implements ResourcesGatherer {

    private final File rFile;
    private final String packageName;

    /**
     * Constructs resources gatherer from R files.
     *
     * @param rFile location for R files, can be either file or directory. If rFile specifies
     *     directory resources are gathered from all R files inside the directory and
     *     subdirectories.
     * @param packageName of application module for resources gathered from the specified location.
     */
    public ResourcesGathererFromRFiles(@NonNull File rFile, @NonNull String packageName) {
        this.rFile = rFile;
        this.packageName = packageName;
    }

    @Override
    public void gatherResourceValues(@NonNull ResourceShrinkerModel model) throws IOException {
        gatherResourceValues(model, rFile);
    }

    private void gatherResourceValues(ResourceShrinkerModel model, File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    gatherResourceValues(model, child);
                }
            }
        } else if (file.isFile()) {
            if (file.getName().equals(SdkConstants.FN_RESOURCE_CLASS)) {
                parseResourceSourceClass(model, file);
            }
            if (file.getName().equals(SdkConstants.FN_R_CLASS_JAR)) {
                parseResourceRJar(model, file);
            }
            if (file.getName().equals(FN_RESOURCE_TEXT)) {
                addResourcesFromRTxtFile(model, file);
            }
        }
    }

    private void addResourcesFromRTxtFile(ResourceShrinkerModel model, File file) {
        try {
            SymbolTable st = readFromAapt(file, packageName);
            for (Symbol symbol : st.getSymbols().values()) {
                String symbolValue = symbol.getValue();
                if (symbol.getResourceType() == ResourceType.STYLEABLE) {
                    if (symbolValue.trim().startsWith("{")) {
                        // Only add the styleable parent, styleable children are not yet supported.
                        model.addResource(
                                symbol.getResourceType(),
                                st.getTablePackage(),
                                symbol.getName(),
                                null);
                    }
                } else {
                    model.addResource(
                            symbol.getResourceType(),
                            st.getTablePackage(),
                            symbol.getName(),
                            symbolValue);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static ResourceType extractResourceType(String entryName) {
        String rClassName = entryName.substring(entryName.lastIndexOf('/') + 1);
        if (!rClassName.startsWith("R$")) {
            return null;
        }
        String resourceTypeName =
                rClassName.substring("R$".length(), rClassName.length() - DOT_CLASS.length());
        return ResourceType.fromClassName(resourceTypeName);
    }

    private void parseResourceRJar(ResourceShrinkerModel model, File jarFile) throws IOException {
        try (ZipFile zFile = new ZipFile(jarFile)) {
            Enumeration<? extends ZipEntry> entries = zFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.endsWith(DOT_CLASS)) {
                    ResourceType resourceType = extractResourceType(entryName);
                    if (resourceType == null) {
                        continue;
                    }
                    String owner = entryName.substring(0, entryName.length() - DOT_CLASS.length());
                    byte[] classData = ByteStreams.toByteArray(zFile.getInputStream(entry));
                    parseResourceCompiledClass(model, classData, owner, resourceType);
                }
            }
        }
    }

    private void parseResourceCompiledClass(
            ResourceShrinkerModel model,
            byte[] classData,
            String owner,
            ResourceType resourceType) {
        ClassReader classReader = new ClassReader(classData);
        ClassVisitor fieldVisitor =
                new ClassVisitor(Opcodes.ASM5) {
                    @Override
                    public FieldVisitor visitField(
                            int access, String name, String desc, String signature, Object value) {
                        // We only want integer or integer array (styleable) fields
                        if (desc.equals("I") || desc.equals("[I")) {
                            String resourceValue =
                                    resourceType == ResourceType.STYLEABLE
                                            ? null
                                            : value.toString();
                            model.addResource(resourceType, packageName, name, resourceValue);
                        }
                        return null;
                    }
                };
        classReader.accept(fieldVisitor, SKIP_DEBUG | SKIP_FRAMES);
    }

    // TODO: Use PSI here
    private void parseResourceSourceClass(ResourceShrinkerModel model, File file)
            throws IOException {
        String s = Files.toString(file, UTF_8);
        // Simple parser which handles only aapt's special R output
        String pkg = null;
        int index = s.indexOf("package ");
        if (index != -1) {
            int end = s.indexOf(';', index);
            pkg = s.substring(index + "package ".length(), end).trim().replace('.', '/');
        }
        index = 0;
        int length = s.length();
        String classDeclaration = "public static final class ";
        while (true) {
            index = s.indexOf(classDeclaration, index);
            if (index == -1) {
                break;
            }
            int start = index + classDeclaration.length();
            int end = s.indexOf(' ', start);
            if (end == -1) {
                break;
            }
            String typeName = s.substring(start, end);
            ResourceType type = ResourceType.fromClassName(typeName);
            if (type == null) {
                break;
            }

            index = end;

            // Find next declaration
            for (; index < length - 1; index++) {
                char c = s.charAt(index);
                if (Character.isWhitespace(c)) {
                    //noinspection UnnecessaryContinue
                    continue;
                }

                if (c == '/') {
                    char next = s.charAt(index + 1);
                    if (next == '*') {
                        // Scan forward to comment end
                        end = index + 2;
                        while (end < length - 2) {
                            c = s.charAt(end);
                            if (c == '*' && s.charAt(end + 1) == '/') {
                                end++;
                                break;
                            } else {
                                end++;
                            }
                        }
                        index = end;
                    } else if (next == '/') {
                        // Scan forward to next newline
                        assert false
                                : s.substring(
                                        index - 1,
                                        index + 50); // we don't put line comments in R files
                    } else {
                        assert false : s.substring(index - 1, index + 50); // unexpected division
                    }
                } else if (c == 'p' && s.startsWith("public ", index)) {
                    if (type == ResourceType.STYLEABLE) {
                        start = s.indexOf(" int", index);
                        if (s.startsWith(" int[] ", start)) {
                            start += " int[] ".length();
                            end = s.indexOf('=', start);
                            assert end != -1;
                            String styleable = s.substring(start, end).trim();
                            model.addResource(ResourceType.STYLEABLE, packageName, styleable, null);
                            // TODO: Read in all the action bar ints!
                            // For now, we're simply treating all R.attr fields as used
                            index = s.indexOf(';', index);
                            if (index == -1) {
                                break;
                            }
                        } else if (s.startsWith(" int ", start)) {
                            // Read these fields in and correlate with the attr R's. Actually
                            // we don't need this for anything; the local attributes are
                            // found by the R attr thing. I just need to record the class
                            // (style).
                            // public static final int ActionBar_background = 10;
                            // ignore - jump to end
                            index = s.indexOf(';', index);
                            if (index == -1) {
                                break;
                            }
                            // For now, we're simply treating all R.attr fields as used
                        }
                    } else {
                        start = s.indexOf(" int ", index);
                        if (start != -1) {
                            start += " int ".length();
                            // e.g. abc_fade_in=0x7f040000;
                            end = s.indexOf('=', start);
                            assert end != -1;
                            String name = s.substring(start, end).trim();
                            start = end + 1;
                            end = s.indexOf(';', start);
                            assert end != -1;
                            String value = s.substring(start, end).trim();
                            model.addResource(type, packageName, name, value);
                        }
                    }
                } else if (c == '}') {
                    // Done with resource class
                    break;
                }
            }
        }
    }
}
