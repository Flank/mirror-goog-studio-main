/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.res.namespaced

import com.android.ide.common.symbols.SymbolTable
import com.android.resources.ResourceType
import com.android.tools.build.apkzlib.zip.StoredEntryType
import com.android.tools.build.apkzlib.zip.ZFile
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ASM5
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.HashSet

/**
 * Rewrites non-namespaced resource references to be namespace aware.
 *
 * @param symbolTables a list of symbol tables for the current module and its' dependencies. The
 *      order matters, the closest modules are at the front, the furthest are at the end. The first
 *      symbol table should be for the module from which the transformed classes come from.
 */
class NamespaceRewriter(
    private val symbolTables: ImmutableList<SymbolTable>,
    private val logger: Logger = Logging.getLogger(NamespaceRewriter::class.java)
) {

    fun rewriteClass(clazz: Path, output: Path) {
        // First read the class and re-write the R class resource references.
        val originalClass = Files.readAllBytes(clazz)
        val rewrittenClass = rewriteClass(originalClass)
        Files.write(output, rewrittenClass)
    }

    fun rewriteClass(originalClass: ByteArray) : ByteArray {
        val cw = ClassWriter(0)
        val crw = ClassReWriter(ASM5, cw, symbolTables, logger)
        val cr = ClassReader(originalClass)
        cr.accept(crw, 0)
        // Write inner R classes references.
        crw.writeInnerRClasses()
        return cw.toByteArray()
    }

    fun rewriteJar(classesJar: File, outputJar: File) {
        ZFile(classesJar).use { classes ->
            ZFile(outputJar).use { output ->
                classes.entries().forEach { entry ->
                    if (entry.type == StoredEntryType.FILE) {
                        val outputBytes = rewriteClass(entry.read())
                        output.add(entry.centralDirectoryHeader.name, outputBytes.inputStream())
                    }
                }
            }
        }
    }

    /**
     * Rewrites a class to be namespaced. It removes all R inner classes references from read
     * inner classes, rewrites and collects R references using the [MethodReWriter].
     * After an [ClassReader.accept] method is called using this [ClassVisitor], the method
     * [ClassReWriter.writeInnerRClasses] needs to be called to correctly fill the InnerClasses
     * attribute for the transformed class.
     */
    private class ClassReWriter internal constructor(
        api: Int,
        cv: ClassVisitor?,
        private val symbolTables: ImmutableList<SymbolTable>,
        private val logger: Logger
    ) : ClassVisitor(api, cv) {

        private val innerClasses = HashSet<String>()

        override fun visitInnerClass(
            name: String?, outerName: String?, innerName: String?, access: Int
        ) {
            // Do not write any original R packages for now, as they might not exist anymore
            // after resource namespacing is applied. If they still exists and are referenced in
            // this class, they will be written at the end.
            if (!outerName!!.endsWith("/R")) {
                cv.visitInnerClass(name, outerName, innerName, access)
            }
        }

        override fun visitMethod(
            access: Int,
            name: String?,
            desc: String?,
            signature: String?,
            exceptions: Array<String?>?
        ) : MethodVisitor {
            return MethodReWriter(
                    api,
                    cv.visitMethod(access, name, desc, signature, exceptions),
                    this
            )
        }

        /**
         * References all found inner R classes. According to the JVM specification the
         * InnerClasses attribute must contain all inner classes referenced in the transformed
         * class even if they aren't member of this class.
         */
        fun writeInnerRClasses() {

            for (innerClass in innerClasses) {
                cv.visitInnerClass(
                        innerClass,
                        innerClass.substring(0, innerClass.lastIndexOf('$')),
                        innerClass.substring(innerClass.lastIndexOf('$') + 1, innerClass.length),
                        ACC_PUBLIC + ACC_STATIC
                )
            }
        }

        /**
         * Finds the first package in which the R file contains a symbol with the given type and
         * name.
         */
        fun findPackage(type: String, name: String): String {
            var packages:ArrayList<String>? = null
            var result:String? = null

            // Go through R.txt files and find the proper package.
            for (table in symbolTables) {
                if (table.symbols.contains(ResourceType.getEnum(type), name)) {
                    if (result == null) {
                        result = table.tablePackage
                    }
                    else {
                        if (packages == null) {
                            packages = ArrayList()
                        }
                        packages.add(table.tablePackage)
                    }
                }
            }
            if (result == null) {
                // Error out if we cannot find the symbol.
                error("In package ${symbolTables[0].tablePackage} found unknown symbol of type " +
                                "$type and name $name.")
            }
            if (packages != null && !packages.isEmpty()) {
                // If we have found more than one fitting package, log a warning about which one we
                // chose (the closest one in the dependencies graph).
                logger.warn("In package ${symbolTables[0].tablePackage} multiple options found " +
                        "in its dependencies for resource $type $name. " +
                        "Using $result, other available: ${Joiner.on(", ").join(packages)}")
            }
            // Return the first found reference.
            return result
        }

        fun addInnerClass(innerClass: String) {
            innerClasses.add(innerClass)
        }
    }

    /**
     * Rewrites field instructions to reference namespaced resources instead of the local R.
     */
    private class MethodReWriter(
        api: Int,
        mv: MethodVisitor?,
        private val crw: ClassReWriter
    ) : MethodVisitor(api, mv) {

        override fun visitFieldInsn(opcode: Int, owner: String, name: String, desc: String?) {
            if (owner.contains("/R$")) {
                val type = owner.substring(owner.lastIndexOf('$') + 1, owner.length)
                val newPkg = crw.findPackage(type, name).replace('.', '/')

                // We need to visit the inner class later. It could happen that the [newOwner]
                // is the same as the [owner] since a class can reference resources from its'
                // module, but we still need to remember all references.
                val newOwner = "$newPkg/R$$type"
                crw.addInnerClass(newOwner)

                this.mv.visitFieldInsn(opcode, newOwner, name, desc)
            } else {
                // The field instruction does not reference an R class, visit normally.
                this.mv.visitFieldInsn(opcode, owner, name, desc)
            }
        }
    }
}
