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

package com.android.build.gradle.internal.incremental;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.incremental.IncrementalVisitor.ClassAndInterfacesNode;
import com.android.utils.ILogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;

/**
 * ASM related utilities methods.
 */
public class AsmUtils {

    /** Abstraction for a provider for {@link ClassNode} instances for a class name. */
    public interface ClassNodeProvider {

        /**
         * load class bytes and return a {@link ClassNode}.
         *
         * @param className the requested class to be loaded.
         * @param logger to log messages.
         * @return a {@link ClassReader} with the class' bytes or null if the class file cannot be
         *     located.
         * @throws IOException when locating/reading the class file.
         */
        @Nullable
        ClassNode loadClassNode(@NonNull String className, @NonNull ILogger logger)
                throws IOException;
    }

    public static class DirectoryBasedClassReader implements ClassNodeProvider {

        private final File binaryFolder;

        public DirectoryBasedClassReader(File binaryFolder) {
            this.binaryFolder = binaryFolder;
        }

        @Override
        @Nullable
        public ClassNode loadClassNode(@NonNull String className, @NonNull ILogger logger) {
            File outerClassFile = new File(binaryFolder, className + ".class");
            if (outerClassFile.exists()) {
                logger.verbose("Parsing %s", outerClassFile);
                try(InputStream outerClassInputStream =
                            new BufferedInputStream(new FileInputStream(outerClassFile))) {
                    return readClass(new ClassReader(outerClassInputStream));
                } catch (IOException e) {
                    logger.error(e, "Cannot parse %s", className);
                }
            }
            return null;
        }
    }

    public static class JarBasedClassReader implements ClassNodeProvider {

        private final File file;

        public JarBasedClassReader(File file) {
            this.file = file;
        }

        @Nullable
        @Override
        public ClassNode loadClassNode(@NonNull String className, @NonNull ILogger logger)
                throws IOException {
            try (JarFile jarFile = new JarFile(file)) {
                ZipEntry entry = jarFile.getEntry(className.replace(".", "/") + ".class");
                if (entry != null) {
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        return readClass(new ClassReader(is));
                    }
                }
            }
            return null;
        }
    }

    public static final ClassNodeProvider classLoaderBasedProvider =
            (className, logger) -> {
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                try (InputStream is = classLoader.getResourceAsStream(className + ".class")) {
                    if (is == null) {
                        throw new IOException("Failed to find byte code for " + className);
                    }

                    ClassReader classReader = new ClassReader(is);
                    ClassNode node = new ClassNode();
                    classReader.accept(node, ClassReader.EXPAND_FRAMES);
                    return node;
                }
            };

    @NonNull
    @VisibleForTesting
    public static List<AnnotationNode> getInvisibleAnnotationsOnClassOrOuterClasses(
            @NonNull ClassNodeProvider classReader,
            @NonNull ClassNode classNode,
            @NonNull ILogger logger)
            throws IOException {

        ImmutableList.Builder<AnnotationNode> listBuilder = ImmutableList.builder();
        do {
            @SuppressWarnings("unchecked")
            List<AnnotationNode> invisibleAnnotations = classNode.invisibleAnnotations;
            if (invisibleAnnotations != null) {
                listBuilder.addAll(invisibleAnnotations);
            }
            String outerClassName = getOuterClassName(classNode);
            classNode =
                    outerClassName != null
                            ? classReader.loadClassNode(outerClassName, logger)
                            : null;
        } while (classNode != null);
        return listBuilder.build();
    }

    /**
     * Read a {@link ClassNode} from a class name and all it's implemented interfaces also as {@link
     * ClassNode}. Store the class and its interfaces into a {@link ClassAndInterfacesNode}
     * instance.
     *
     * @param classReaderProvider provider to read class bytes from storage
     * @param parentClassName requested class name
     * @param logger logger to log
     * @return null if the parentClassName cannot be located by the provider otherwise an {@link
     *     ClassAndInterfacesNode} encapsulating the class and its directly implemented interfaces
     * @throws IOException when bytes cannot be read.
     */
    @Nullable
    public static ClassAndInterfacesNode readParentClassAndInterfaces(
            @NonNull ClassNodeProvider classReaderProvider,
            @NonNull String parentClassName,
            @NonNull String childClassName,
            int targetApi,
            @NonNull ILogger logger)
            throws IOException {
        ClassNode classNode = classReaderProvider.loadClassNode(parentClassName, logger);
        if (classNode == null) {
            // Could not locate parent class. This is as far as we can go locating parents.
            logger.warning(
                    "IncrementalVisitor parseParents could not locate %1$s "
                            + "which is an ancestor of project class %2$s.\n"
                            + "%2$s is not eligible for hot swap. \n"
                            + "If the class targets a more recent platform than %3$d,"
                            + " add a @TargetApi annotation to silence this warning.",
                    parentClassName, childClassName, targetApi);
            return null;
        }
        // now read all implemented interfaces.
        ImmutableList.Builder<ClassNode> interfaces = ImmutableList.builder();
        if (!readInterfaces(classNode, classReaderProvider, interfaces, logger)) {
            // if we cannot read any implemented interfaces, return null;
            return null;
        }
        return new ClassAndInterfacesNode(classNode, interfaces.build());
    }

    /**
     * Read all directly implemented interfaces from the passed {@link ClassNode} instance.
     *
     * @param classNode the class
     * @param classReaderProvider a provider to read class bytes from storage
     * @param interfacesList a builder to store the list of ClassNode for each directly implemented
     *     interfaces, can be empty after method returns.
     * @param logger logger to log
     * @return true if implemented interfaces could all be loaded, false otherwise.
     * @throws IOException when bytes cannot be read
     */
    public static boolean readInterfaces(
            @NonNull ClassNode classNode,
            @NonNull ClassNodeProvider classReaderProvider,
            @NonNull ImmutableList.Builder<ClassNode> interfacesList,
            @NonNull ILogger logger)
            throws IOException {
        for (String anInterface : (List<String>) classNode.interfaces) {
            ClassNode intf = loadClass(classReaderProvider, anInterface, logger);
            if (intf != null) {
                interfacesList.add(intf);
            } else {
                logger.warning(
                        "Cannot load interface %$1s, which is implemented"
                                + "by %2$s, therefore %2$s will not be eligible for hotswap.",
                        anInterface, classNode.name);
                return false;
            }
        }
        return true;
    }

    @NonNull
    public static ClassNode readClass(@NonNull ClassReader classReader) {
        ClassNode node = new ClassNode();
        classReader.accept(node, ClassReader.EXPAND_FRAMES);
        return node;
    }

    @NonNull
    public static List<ClassAndInterfacesNode> parseParents(
            @NonNull ILogger logger,
            @NonNull ClassNodeProvider classBytesReader,
            @NonNull ClassNode classNode,
            int targetApi)
            throws IOException {

        ImmutableList.Builder<ClassAndInterfacesNode> parentNodes = ImmutableList.builder();

        String currentParentName = classNode.superName;

        while (currentParentName != null) {
            ClassAndInterfacesNode parentWithInterfacesNode =
                    readParentClassAndInterfaces(
                            classBytesReader, currentParentName, classNode.name, targetApi, logger);
            if (parentWithInterfacesNode == null) {
                // May need method information from outside of the current project. The thread's
                // context class loader is configured by the caller (InstantRunTransform) to contain
                // all app's dependencies.
                parentWithInterfacesNode =
                        readParentClassAndInterfaces(
                                classLoaderBasedProvider,
                                currentParentName,
                                classNode.name,
                                targetApi,
                                logger);
                if (parentWithInterfacesNode == null) {
                    // Could not locate parent class or implemented interfaces,
                    // This is as far as we can go locating parents.
                    return ImmutableList.of();
                }
            }
            parentNodes.add(parentWithInterfacesNode);
            currentParentName = parentWithInterfacesNode.classNode.superName;
        }
        return parentNodes.build();
    }

    @Nullable
    public static ClassNode loadClass(
            @NonNull ClassNodeProvider classBytesReader,
            @NonNull String className,
            @NonNull ILogger logger)
            throws IOException {

        ClassNode classNode = classBytesReader.loadClassNode(className, logger);
        if (classNode != null) {
            return classNode;
        }
        // May need method information from outside of the current project. The thread's
        // context class loader is configured by the caller (InstantRunTransform) to contain
        // all app's dependencies.
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            return readClass(contextClassLoader, className);
        } catch (IOException e) {
            // Could not locate parent class. This is as far as we can go locating parents.
            logger.warning(e.getMessage());
            logger.warning("IncrementalVisitor parseParents could not locate %1$s", className);
        }
        return null;
    }

    @NonNull
    public static ClassNode readClass(ClassLoader classLoader, String className)
            throws IOException {
       try (InputStream is = classLoader.getResourceAsStream(className + ".class")) {
           if (is == null) {
               throw new IOException("Failed to find byte code for " + className);
           }

           ClassReader parentClassReader = new ClassReader(is);
           ClassNode node = new ClassNode();
           parentClassReader.accept(node, ClassReader.EXPAND_FRAMES);
           return node;
        }
    }

    @Nullable
    public static ClassNode parsePackageInfo(
            @NonNull File inputFile) throws IOException {

        File packageFolder = inputFile.getParentFile();
        File packageInfoClass = new File(packageFolder, "package-info.class");
        if (packageInfoClass.exists()) {
            try (InputStream reader = new BufferedInputStream(new FileInputStream(packageInfoClass))) {
                ClassReader classReader = new ClassReader(reader);
                return readClass(classReader);
            }
        }
        return null;
    }

    @Nullable
    public static String getOuterClassName(@NonNull ClassNode classNode) {
        if (classNode.outerClass != null) {
            return classNode.outerClass;
        }
        if (classNode.innerClasses != null) {
            @SuppressWarnings("unchecked")
            List<InnerClassNode> innerClassNodes = (List<InnerClassNode>) classNode.innerClasses;
            for (InnerClassNode innerClassNode : innerClassNodes) {
                if (innerClassNode.name.equals(classNode.name)) {
                    return innerClassNode.outerName;
                }
            }
        }
        return null;
    }
}
