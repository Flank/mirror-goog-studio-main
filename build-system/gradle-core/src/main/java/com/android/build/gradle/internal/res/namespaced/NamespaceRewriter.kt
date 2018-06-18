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

import com.android.annotations.VisibleForTesting
import com.android.ide.common.symbols.SymbolTable
import com.android.ide.common.symbols.canonicalizeValueResourceName
import com.android.ide.common.xml.XmlFormatPreferences
import com.android.ide.common.xml.XmlFormatStyle
import com.android.ide.common.xml.XmlPrettyPrinter
import com.android.resources.ResourceType
import com.android.tools.build.apkzlib.zip.StoredEntryType
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.utils.PathUtils
import com.android.utils.PositionXmlParser
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
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.io.IOException
import java.io.Writer
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

    private fun rewriteClass(originalClass: ByteArray) : ByteArray {
        val cw = ClassWriter(0)
        val crw = ClassReWriter(ASM5, cw, symbolTables, logger)
        val cr = ClassReader(originalClass)
        cr.accept(crw, 0)
        // Write inner R classes references.
        crw.writeInnerRClasses()
        return cw.toByteArray()
    }

    /**
     * Rewrites all classes from the input JAR file to be fully resource namespace aware and places
     * them in the output JAR; it will also filter out all non .class files, so that the output JAR
     * contains only the namespaced classes.
     */
    fun rewriteJar(classesJar: File, outputJar: File) {
        ZFile(classesJar).use { classes ->
            ZFile(outputJar).use { output ->
                classes.entries().forEach { entry ->
                    val name = entry.centralDirectoryHeader.name
                    if (entry.type == StoredEntryType.FILE && name.endsWith(".class")) {
                        try {
                            val outputBytes = rewriteClass(entry.read())
                            output.add(name, outputBytes.inputStream())
                        } catch (e: Exception) {
                            throw IllegalStateException(
                                    "Failed rewriting class $name from ${classesJar.absolutePath}",
                                    e
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Rewrites the input file to be fully namespaced using the provided method. Writes fully
     * namespaced document to the output.
     */
    private inline fun rewriteFile(input: Path, output: Path, method: (node: Document) -> Unit) {
        Files.newInputStream(input).buffered().use {
            // Read the file.
            val doc = try {
                PositionXmlParser.parse(it)
            } catch (e: Exception) {
                throw IOException("Failed to parse $input", e)
            }

            // Fix namespaces.
            try {
                method(doc)
            } catch (e: Exception) {
                throw IOException("Failed namespace $input", e)
            }

            // Write the new file. The PositionXmlParser uses UTF_8 when reading the file, so it
            // should be fine to write as UTF_8 too.
            Files.newOutputStream(output).bufferedWriter(Charsets.UTF_8).use {
                it.write(
                    XmlPrettyPrinter
                        .prettyPrint(
                            doc,
                            XmlFormatPreferences.defaults(),
                            XmlFormatStyle.get(doc),
                            System.lineSeparator(),
                            false
                        )
                )
            }
        }
    }

    /**
     * Rewrites the AndroidManifest.xml file to be fully resource namespace aware. Finds all
     * resource references (e.g. '@string/app_name') and makes them namespace aware (e.g.
     * '@com.foo.bar:string/app_name').
     * This will also append the package to the references to resources from this library - it is
     * not necessary, but saves us from comparing the package names.
     */
    fun rewriteManifest(inputManifest: Path, outputManifest: Path) {
        rewriteFile(inputManifest, outputManifest, this::rewriteManifestNode)
    }

    private fun rewriteManifestNode(node: Node) {
        if (node.nodeType == Node.ATTRIBUTE_NODE) {
            // The content could be a resource reference. If it is not, do not update the content.
            val content = node.nodeValue
            val namespacedContent = rewritePossibleReference(content)
            if (content != namespacedContent) {
                node.nodeValue = namespacedContent
            }
        }

        // First fix the attributes.
        node.attributes?.forEach {
            rewriteManifestNode(it)
        }

        // Now fix the children.
        node.childNodes?.forEach {
            rewriteManifestNode(it)
        }
    }

    /**
     * Rewrites a values file to be fully resource namespace aware. Finds all resource references
     * and makes them namespace aware, for example:
     * - simple references, e.g. '@string/app_name' becomes '@com.foo.bar:string/app_name'
     * - styles' parents, e.g. 'parent="@style/Parent', 'parent="Parent"' both become
     *   'parent="@com.foo.bar:style/Parent"'
     * - styles' item name references, e.g. 'item name="my_attr"' becomes
     *   'item name="com.foo.bar:my_attr" (no "@" or "attr/" in this case)
     * This will also append the package to the references to resources from this library - it is
     * not necessary, but saves us from comparing the package names.
     */
    fun rewriteValuesFile(input: Path, output: Path) {
        rewriteFile(input, output, this::rewriteValuesNode)
    }

    private fun rewriteValuesNode(node: Node) {
        if (node.nodeType == Node.TEXT_NODE) {
            // The content could be a resource reference. If it is not, do not update the content.
            val content = node.nodeValue
            val namespacedContent = rewritePossibleReference(content)
            if (content != namespacedContent) {
                node.nodeValue = namespacedContent
            }
        } else if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == "style") {
            // Styles need to be handled separately.
            rewriteStyleElement(node as Element)
            return
        }

        // First fix the attributes.
        node.attributes?.forEach {
            rewriteValuesNode(it)
        }

        // Now fix the children.
        node.childNodes?.forEach {
            rewriteValuesNode(it)
        }
    }

    /** Rewrites a style element
     *
     * e.g.
     * ```
     * <style name="AppTheme" parent="Theme.AppCompat.Light.DarkActionBar">
     *     <item name="colorPrimary">@color/colorPrimary</item>
     * </style>
     * ```
     * to
     * ```
     * <style name="AppTheme" parent="android.support.v7.appcompat:Theme.AppCompat.Light.DarkActionBar">
     *     <item name="android.support.v7.appcompat:colorPrimary">@com.example.app:color/colorPrimary</item>
     * </style>
     * ```
     * */
    private fun rewriteStyleElement(element: Element) {
        rewriteStyleParent(element)
        rewriteStyleItems(element)
    }

    private fun rewriteStyleParent(element: Element) {
        val name: String = element.attributes.getNamedItem("name")!!.nodeValue

        val originalParent: String? = element.attributes.getNamedItem("parent")?.nodeValue
        var parent: String? = null
        if (originalParent == null) {
            // Guess, maybe we have an implicit parent?
            val possibleParent = name.substringBeforeLast('.', "")
            if (!possibleParent.isEmpty()) {
                val possiblePackage = maybeFindPackage(
                    "style",
                    possibleParent,
                    logger,
                    symbolTables
                )
                if (possiblePackage != null) {
                    parent = "@$possiblePackage:style/$possibleParent"
                }
            }
        } else if (originalParent.isEmpty()) {
            // leave it alone, there is explicitly no parent
        } else {
            // Rewrite explicitly included parents
            parent = originalParent
            if (!parent.startsWith("@")) {
                parent = "@style/$parent"
            }
            parent = rewritePossibleReference(parent)
        }
        if (parent != null && parent != originalParent) {
            val parentAttribute = element.ownerDocument.createAttribute("parent")
            parentAttribute.value = parent
            element.attributes.setNamedItem(parentAttribute)
        }

    }

    private fun rewriteStyleItems(styleElement: Element) {
        styleElement.childNodes?.forEach {
            if (it.nodeType == Node.ELEMENT_NODE && it.nodeName == "item") {
                rewriteStyleItem(it as Element)
            }
        }
    }

    private fun rewriteStyleItem(styleItemElement: Element) {
        styleItemElement.attributes.forEach { attribute ->
            if (attribute.nodeName == "name") {
                rewriteStyleItemNameAttribute(attribute)
            }
        }
        styleItemElement.childNodes.forEach { node ->
            if (node.nodeType == Node.TEXT_NODE) {
                rewriteStyleItemValue(node)
            }
        }
    }

    private fun rewriteStyleItemNameAttribute(attribute: Node) {
        if (attribute.nodeValue.contains(':')) {
            return
        }
        // If the name is not from the "android:" namespace, it comes from this library or its
        // dependencies (uncommon but needs to be handled).
        val content = "@attr/${attribute.nodeValue}"
        val namespacedContent = rewritePossibleReference(content)
        if (content != namespacedContent) {
            // Prepend the package to the content
            val foundPackage =
                namespacedContent.substring(1, namespacedContent.indexOf(":"))
            attribute.nodeValue = "$foundPackage:${attribute.nodeValue}"
        }
    }

    private fun rewriteStyleItemValue(node: Node) {
        // The content could be a resource reference. If it is not, do not update the content.
        val content = node.nodeValue
        val namespacedContent = rewritePossibleReference(content)
        if (content != namespacedContent) {
            node.nodeValue = namespacedContent
        }
    }

    /**
     * Rewrites an XML file (e.g. layout) to be fully resource namespace aware. Finds all resource
     * references and makes them namespace aware, for example:
     * - simple references, e.g. '@string/app_name' becomes '@com.foo.bar:string/app_name'
     * - adds XML namespaces for dependencies (e.g. xmlns:android_support_constraint=
     *   "http://schemas.android.com/apk/res/android.support.constraint")
     * - updates XML namespaces from to the correct package, e.g  app:layout_constraintLeft_toLeftOf
     *   becomes android_support_constraint:layout_constraintLeft_toLeftOf
     * - removes res-auto namespace to make sure we don't leave anything up to luck
     * This will also append the package to the references to resources from this library - it is
     * not necessary, but saves us from comparing the package names.
     */
    fun rewriteXmlFile(input: Path, output: Path) {
        rewriteFile(input, output, this::rewriteXmlDoc)
    }

    /**
     * Rewrites all the resources from an exploded-aar input directory as passed in input to the
     * output directory.
     *
     * * Values files are processed with [#rewriteValuesFile]
     * * XML files not in raw (such as layouts) are processed with [#rewriteXmlFile]
     * * Everything else is copied as-is
     */
    fun rewriteAarResources(input: Path, output: Path) {
        if (!Files.isDirectory(input)) {
            throw IOException("expected $input to be a directory")
        }
        PathUtils.deleteRecursivelyIfExists(output)
        Files.createDirectories(output)
        Files.list(input).use {
            it.forEach { resSubdirectory ->
                val name = resSubdirectory.fileName.toString()
                val outputDir = output.resolve(name)
                Files.createDirectory(outputDir)
                if (name == "values" || name.startsWith("values-")) {
                    resSubdirectory.forEachFile(outputDir) { from, to ->
                        rewriteValuesFile(from, to)
                    }
                } else if (name == "raw" || name.startsWith("raw-")) {
                    resSubdirectory.forEachFile(outputDir) { from, to ->
                        Files.copy(from, to)
                    }
                } else {
                    resSubdirectory.forEachFile(outputDir) { from, to ->
                        if (from.fileName.toString().endsWith(".xml")) {
                            rewriteXmlFile(from, to)
                        } else {
                            Files.copy(from, to)
                        }
                    }
                }
            }
        }

    }

    private inline fun Path.forEachFile(outdir: Path, crossinline action: (Path, Path) -> Unit) {
        Files.list(this).use {
            it.forEach { file ->
                if (Files.isRegularFile(file)) {
                    action.invoke(file, outdir.resolve(file.fileName))
                }
            }
        }
    }

    private fun rewriteXmlDoc(document: Document) {
        // Get the main node. Can be a 'layout' or a 'vector' etc. This is where we will add all the
        // namespaces.
        val mainNode = getMainElement(document)

        // TODO(b/110036551): can 'res-auto' be declared anywhere deeper than the main node?
        // First, find any namespaces we need to fix - any pointing to 'res-auto'. Usually it is
        // only "xmlns:app", but let's be safe here.
        val namespacesToFix: ArrayList<String> = ArrayList()
        mainNode.attributes?.let {
            for (i in 0 until it.length) {
                val attr = it.item(i)
                if (attr.nodeName.startsWith("xmlns:")
                        && attr.nodeValue == "http://schemas.android.com/apk/res-auto") {
                    namespacesToFix.add(attr.nodeName.substring(6))
                }
            }
        }
        namespacesToFix.forEach { mainNode.removeAttribute("xmlns:$it") }

        // Add namespaces, we might not need all of them (if any), but it's safer and cheaper to add
        // all.
        for (table in symbolTables) {
            mainNode.setAttribute(
                    "xmlns:${table.tablePackage.replace('.', '_')}",
                    "http://schemas.android.com/apk/res/${table.tablePackage}")
        }

        // First fix the attributes.
        mainNode.attributes?.forEach {
            if (!it.nodeName.startsWith("xmlns:")){
                rewriteXmlNode(it, document, namespacesToFix)
            }
        }

        // Now fix the children.
        mainNode.childNodes?.forEach {
            rewriteXmlNode(it, document, namespacesToFix)
        }
    }

    /** Resource XML files should have only one main element
     * everything else should be whitespace and comments */
    private fun getMainElement(document: Document): Element {
        var candidateMainNode: Element? = null

        document.childNodes?.forEach {
            if (it.nodeType == Node.ELEMENT_NODE) {
                if (candidateMainNode != null) {
                    error("Invalid XML file - there can only be one main node.")
                }
                candidateMainNode = it as Element
            }
        }

        return candidateMainNode ?: error("Invalid XML file - missing main node.")
    }

    private fun rewriteXmlNode(node: Node, document: Document, namespacesToFix: List<String>) {
        if (node.nodeType == Node.TEXT_NODE) {
            // The content could be a resource reference. If it is not, do not update the content.
            val content = node.nodeValue
            val namespacedContent = rewritePossibleReference(content)
            if (content != namespacedContent) {
                node.nodeValue = namespacedContent
            }
        } else if (node.nodeType == Node.ATTRIBUTE_NODE && node.nodeName.contains(":")) {
            // Only fix res-auto.
            if (namespacesToFix.any { node.nodeName.startsWith("$it:")}) {
                val name =
                        node.nodeName.substring(
                                node.nodeName.indexOf(':') + 1, node.nodeName.length)
                val content = "@attr/$name"
                val namespacedContent = rewritePossibleReference(content)
                if (content != namespacedContent) {
                    // Prepend the package to the content
                    val foundPackage = namespacedContent.substring(1, namespacedContent.indexOf(":"))
                    document.renameNode(
                            node,
                            "http://schemas.android/apk/res/$foundPackage",
                            "${foundPackage.replace('.', '_')}:$name")
                }
            }
        }

        // First fix the attributes.
        node.attributes?.forEach {
            rewriteXmlNode(it, document, namespacesToFix)
        }

        // Now fix the children.
        node.childNodes?.forEach {
            rewriteXmlNode(it, document, namespacesToFix)
        }
    }

    private fun rewritePossibleReference(content: String): String {
        if (!content.startsWith("@") && !content.startsWith("?")) {
            // Not a reference, don't rewrite it.
            return content
        }
        if (!content.contains("/")) {
            // Not a reference, don't rewrite it.
            return content
        }
        if (content.startsWith("@+")) {
            // ID declarations are inheritently local, don't rewrite it.
            return content
        }
        if (content.contains(':')) {
            // The reference is already namespaced (probably @android:...), don't rewrite it.
            return content
        }

        val prefixChar = content[0]
        val trimmedContent = content.trim()
        val slashIndex = trimmedContent.indexOf('/')
        val type = trimmedContent.substring(1, slashIndex)
        val name = trimmedContent.substring(slashIndex + 1, trimmedContent.length)

        val pckg = findPackage(type, name, logger, symbolTables)

        // Rewrite the reference using the package and the un-canonicalized name.
        return "$prefixChar$pckg:$type/$name"
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
            if (outerName != null && !outerName.endsWith("/R")) {
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
            return findPackage(type, name, logger, symbolTables)
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

    private inline fun NodeList.forEach(f: (Node) -> Unit) { for (i in 0 until length) f(item(i)) }
    private inline fun NamedNodeMap.forEach(f: (Node) -> Unit) {
        for (i in 0 until length) f(item(i))
    }
}

/**
 * Finds the first package in which the R file contains a symbol with the given type and
 * name.
 */
private fun findPackage(
    type: String,
    name: String,
    logger: Logger,
    symbolTables: ImmutableList<SymbolTable>
): String {
    val result: String? =
        maybeFindPackage(type = type, name = name, logger = logger, symbolTables = symbolTables)
    return result
            ?: error(
                "In package ${symbolTables[0].tablePackage} found unknown symbol of type " +
                        "$type and name $name."
            )
}

/**
 * Finds the first package in which the R file contains a symbol with the given type and
 * name.
 */
private fun maybeFindPackage(
    type: String,
    name: String,
    logger: Logger,
    symbolTables: ImmutableList<SymbolTable>
): String? {
    val canonicalName = canonicalizeValueResourceName(name)
    var packages: ArrayList<String>? = null
    var result: String? = null

    // Go through R.txt files and find the proper package.
    for (table in symbolTables) {
        if (table.containsSymbol(getResourceType(type), canonicalName)) {
            if (result == null) {
                result = table.tablePackage
            } else {
                if (packages == null) {
                    packages = ArrayList()
                }
                packages.add(table.tablePackage)
            }
        }
    }
    if (packages != null && !packages.isEmpty()) {
        // If we have found more than one fitting package, log a warning about which one we
        // chose (the closest one in the dependencies graph).
        logger.warn(
            "In package ${symbolTables[0].tablePackage} multiple options found " +
                    "in its dependencies for resource $type $name. " +
                    "Using $result, other available: ${Joiner.on(", ").join(packages)}"
        )
    }
    // Return the first found reference.
    return result
}

private fun getResourceType(typeString: String): ResourceType =
    ResourceType.fromClassName(typeString) ?: error("Unknown type '$typeString'")


fun generatePublicFile(symbols: SymbolTable, outputDirectory: Path) {
    val values = outputDirectory.resolve("values")
    Files.createDirectories(values)
    val publicFile = values.resolve("auto-namespace-public.xml")
    if (Files.exists(publicFile)) {
        error("Internal error: Auto namespaced public file already exists")
    }
    Files.newBufferedWriter(publicFile).use {
        writePublicFile(it, symbols)
    }
}

@VisibleForTesting
internal fun writePublicFile(writer: Writer, symbols: SymbolTable) {
    writer.write("""<?xml version="1.0" encoding="utf-8"?>""")
    writer.write("\n<resources>\n\n")
    symbols.resourceTypes.forEach { resourceType ->
        symbols.getSymbolByResourceType(resourceType).forEach { symbol ->
            writer.write("    <public name=\"")
            writer.write(symbol.name)
            writer.write("\" type=\"")
            writer.write(resourceType.getName())
            writer.write("\" />\n")
        }

    }
    writer.write("\n</resources>\n")
}
