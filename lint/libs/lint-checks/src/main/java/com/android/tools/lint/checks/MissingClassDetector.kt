/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_PKG_PREFIX
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CLASS
import com.android.SdkConstants.ATTR_FRAGMENT
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.CONSTRUCTOR_NAME
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_HEADER
import com.android.SdkConstants.TAG_PROVIDER
import com.android.SdkConstants.TAG_RECEIVER
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_STRING
import com.android.SdkConstants.VIEW_FRAGMENT
import com.android.SdkConstants.VIEW_TAG
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceFolderType.LAYOUT
import com.android.resources.ResourceFolderType.VALUES
import com.android.resources.ResourceFolderType.XML
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.lint.detector.api.ClassContext.Companion.createSignature
import com.android.tools.lint.detector.api.ClassContext.Companion.getFqcn
import com.android.tools.lint.detector.api.ClassContext.Companion.getInternalName
import com.android.tools.lint.detector.api.ClassScanner
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import com.android.tools.lint.detector.api.isStaticInnerClass
import com.android.utils.SdkUtils.endsWith
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.File.separatorChar
import java.util.ArrayList
import java.util.EnumSet
import java.util.Locale

/** Checks to ensure that classes referenced in the manifest actually exist and are included */
class MissingClassDetector : LayoutDetector(), ClassScanner {
    private var referencedClasses: MutableMap<String, Location.Handle?>? = null
    private var customViews: MutableSet<String>? = null
    private var haveClasses = false

    // ---- Implements XmlScanner ----

    override fun getApplicableElements(): Collection<String>? {
        return XmlScannerConstants.ALL
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == VALUES ||
                folderType == LAYOUT ||
                folderType == XML
    }

    override fun visitElement(
        context: XmlContext,
        element: Element
    ) {
        var pkg: String? = null
        val classNameNode: Node
        val className: String
        val tag = element.tagName
        val folderType = context.resourceFolderType
        if (folderType == VALUES) {
            if (tag != TAG_STRING) {
                return
            }
            val attr = element.getAttributeNode(ATTR_NAME) ?: return
            className = attr.value
            classNameNode = attr
        } else if (folderType == LAYOUT) {
            if (tag.indexOf('.') > 0) {
                className = tag
                classNameNode = element
            } else if (tag == VIEW_FRAGMENT || tag == VIEW_TAG) {
                val attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
                    ?: element.getAttributeNode(ATTR_CLASS)
                    ?: return
                className = attr.value
                classNameNode = attr
            } else {
                return
            }
        } else if (folderType == XML) {
            if (tag != TAG_HEADER) {
                return
            }
            val attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_FRAGMENT) ?: return
            className = attr.value
            classNameNode = attr
        } else { // Manifest file
            if (TAG_APPLICATION == tag ||
                TAG_ACTIVITY == tag ||
                TAG_SERVICE == tag ||
                TAG_RECEIVER == tag ||
                TAG_PROVIDER == tag) {
                val attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
                className = attr.value
                classNameNode = attr
                pkg = context.project.getPackage()
            } else {
                return
            }
        }
        if (className.isEmpty()) {
            return
        }
        val fqcn: String
        val dotIndex = className.indexOf('.')
        if (dotIndex <= 0) {
            if (pkg == null) {
                return // value file
            }
            fqcn = if (dotIndex == 0) {
                pkg + className
            } else {
                // According to the <activity> manifest element documentation, this is not
                // valid (http://developer.android.com/guide/topics/manifest/activity-element.html)
                // but it appears in manifest files and appears to be supported by the runtime
                // so handle this in code as well:
                "$pkg.$className"
            }
        } else {
            // else: the class name is already a fully qualified class name
            fqcn = className
            // Only look for fully qualified tracker names in analytics files
            if (folderType == VALUES && !endsWith(context.file.path, "analytics.xml")) {
                return
            }
        }
        var signature = getInternalName(fqcn)
        if (signature.isEmpty() || signature.startsWith(ANDROID_PKG_PREFIX)) {
            return
        }
        if (!context.project.reportIssues) {
            // If this is a library project not being analyzed, ignore it
            return
        }
        var handle: Location.Handle? = null
        if (!context.driver.isSuppressed(context, MISSING, element)) {
            if (referencedClasses == null) {
                referencedClasses = Maps.newHashMapWithExpectedSize(16)
                customViews = Sets.newHashSetWithExpectedSize(8)
            }
            handle = context.createLocationHandle(element)
            referencedClasses!![signature] = handle
            if (folderType == LAYOUT && tag != VIEW_FRAGMENT) {
                customViews!!.add(getInternalName(className))
            }
        }
        if (signature.indexOf('$') != -1) {
            checkInnerClass(
                context,
                element,
                pkg,
                classNameNode,
                className
            )
            // The internal name contains a $ which means it's an inner class.
            // The conversion from fqcn to internal name is a bit ambiguous:
            // "a.b.C.D" usually means "inner class D in class C in package a.b".
            // However, it can (see issue 31592) also mean class D in package "a.b.C".
            // To make sure we don't falsely complain that foo/Bar$Baz doesn't exist,
            // in case the user has actually created a package named foo/Bar and a proper
            // class named Baz, we register *both* into the reference map.
            // When generating errors we'll look for these an rip them back out if
            // it looks like one of the two variations have been seen.
            if (handle != null) {
                // Assume that each successive $ is really a capitalized package name
                // instead. In other words, for A$B$C$D (assumed to be class A with
                // inner classes A.B, A.B.C and A.B.C.D) generate the following possible
                // referenced classes A/B$C$D (class B in package A with inner classes C and C.D),
                // A/B/C$D and A/B/C/D
                while (true) {
                    val index = signature.indexOf('$')
                    if (index == -1) {
                        break
                    }
                    signature = signature.substring(0, index) + '/' + signature.substring(index + 1)
                    referencedClasses!![signature] = handle
                    if (folderType == LAYOUT && tag != VIEW_FRAGMENT) {
                        customViews!!.add(signature)
                    }
                }
            }
        }
    }

    private fun checkInnerClass(
        context: XmlContext,
        element: Element,
        pkg: String?,
        classNameNode: Node,
        className: String
    ) {
        if (pkg != null &&
            className.indexOf('$') == -1 && className.indexOf('.', 1) > 0
        ) {
            var haveUpperCase = false
            var i = 0
            val n = pkg.length
            while (i < n) {
                if (Character.isUpperCase(pkg[i])) {
                    haveUpperCase = true
                    break
                }
                i++
            }
            if (!haveUpperCase) {
                val fixed = className[0].toString() + className.substring(1).replace('.', '$')
                val message =
                    "Use '$' instead of '.' for inner classes (or use only lowercase letters in package names); replace \"$className\" with \"$fixed\""
                val location = context.getLocation(classNameNode)
                val fix = LintFix.create().replace().text(className).with(fixed).autoFix().build()
                context.report(INNERCLASS, element, location, message, fix)
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val mainProject = context.mainProject
        if (context.project === mainProject && haveClasses &&
            !mainProject.isLibrary &&
            referencedClasses != null && referencedClasses!!.isNotEmpty() &&
            context.driver.scope.contains(Scope.CLASS_FILE)
        ) {
            val classes = ArrayList(referencedClasses!!.keys)
            classes.sort()
            classLoop@
            for (owner in classes) {
                val handle = referencedClasses!![owner]
                val fqcn = getFqcn(owner)
                var signature = getInternalName(fqcn)
                if (signature != owner) {
                    if (!referencedClasses!!.containsKey(signature)) {
                        continue
                    }
                } else if (signature.indexOf('$') != -1) {
                    signature = signature.replace('$', '/')
                    if (!referencedClasses!!.containsKey(signature)) {
                        continue
                    }
                }
                referencedClasses!!.remove(owner)
                // Ignore usages of platform libraries
                if (owner.startsWith("android/")) {
                    continue
                }
                // Sanity check: make sure we can't find the missing class as source
                // anywhere either. This is relevant for example if we're running lint
                // from Gradle across all variants but the source code hasn't been
                // compiled for all the variants we're checking.
                val all: MutableList<Project> = Lists.newArrayList(mainProject.allLibraries)
                all.add(mainProject)
                for (project in all) {
                    for (root in project.javaSourceFolders) {
                        val source = File(root, owner.replace('/', separatorChar) + DOT_JAVA)
                        if (source.exists()) {
                            continue@classLoop
                        }
                    }
                }
                // One last sanity check:
                val cls = context.client.getUastParser(mainProject).evaluator.findClass(fqcn)
                if (cls != null) {
                    val expectedName = owner.substring(owner.lastIndexOf('/') + 1)
                    if (owner.contains("$") && cls.containingClass != null || expectedName == cls.name
                    ) {
                        continue
                    }
                }
                val location = handle!!.resolve()
                val parentFile = location.file.parentFile
                val target =
                    if (parentFile != null) {
                        val parent = parentFile.name
                        when (val type = ResourceFolderType.getFolderType(parent)) {
                            null -> "manifest"
                            LAYOUT -> "layout file"
                            XML -> "preference header file"
                            VALUES -> "analytics file"
                            else -> {
                                "${type.getName().toLowerCase(Locale.US)} file"
                            }
                        }
                    } else {
                        "the manifest"
                    }
                val message = "Class referenced in the $target, `$fqcn`, was not found in the project or the libraries"
                context.report(MISSING, location, message)
            }
        }
    }

    // ---- Implements ClassScanner ----

    override fun checkClass(
        context: ClassContext,
        classNode: ClassNode
    ) {
        if (!haveClasses &&
            !context.isFromClassLibrary &&
            context.project === context.mainProject
        ) {
            haveClasses = true
        }
        val curr = classNode.name
        if (referencedClasses != null && referencedClasses!!.containsKey(curr)) {
            val isCustomView = customViews!!.contains(curr)
            removeReferences(curr)
            // Ensure that the class is non static and has a null constructor!
            if (classNode.access and Opcodes.ACC_PRIVATE != 0) {
                val signature = createSignature(classNode.name, null, null)
                val message = "This class should be public ($signature)"
                context.report(INSTANTIATABLE, context.getLocation(classNode), message)
                return
            }
            if (classNode.name.indexOf('$') != -1 && !isStaticInnerClass(classNode)) {
                val signature = createSignature(classNode.name, null, null)
                val message = "This inner class should be static ($signature)"
                context.report(INSTANTIATABLE, context.getLocation(classNode), message)
                return
            }
            var hasDefaultConstructor = false
            val methodList: List<*> = classNode.methods
            for (m in methodList) {
                val method = m as MethodNode
                if (method.name == CONSTRUCTOR_NAME) {
                    if (method.desc == "()V") { // The constructor must be public
                        if (method.access and Opcodes.ACC_PUBLIC == 0) {
                            context.report(
                                INSTANTIATABLE,
                                context.getLocation(method, classNode),
                                "The default constructor must be public"
                            )
                            // Also mark that we have a constructor so we don't complain again
                            // below since we've already emitted a more specific error related
                            // to the default constructor
                        }
                        hasDefaultConstructor = true
                    }
                }
            }
            if (!hasDefaultConstructor &&
                !isCustomView &&
                !context.isFromClassLibrary &&
                context.project.reportIssues
            ) {
                val signature = createSignature(classNode.name, null, null)
                val message =
                    "This class should provide a default constructor (a public constructor with no arguments) ($signature)"
                context.report(INSTANTIATABLE, context.getLocation(classNode), message)
            }
        }
    }

    private fun removeReferences(name: String) {
        var curr = name
        referencedClasses!!.remove(curr)

        // Since "A.B.C" is ambiguous whether it's referencing a class in package A.B or
        // an inner class C in package A, we insert multiple possible references when we
        // encounter the A.B.C reference; now that we've seen the actual class we need to
        // remove all the possible permutations we've added such that the permutations
        // don't count as unreferenced classes.
        var index = curr.lastIndexOf('/')
        if (index == -1) {
            return
        }
        var hasCapitalizedPackageName = false
        for (i in index - 1 downTo 0) {
            val c = curr[i]
            if (Character.isUpperCase(c)) {
                hasCapitalizedPackageName = true
                break
            }
        }
        if (!hasCapitalizedPackageName) { // No path ambiguity
            return
        }
        while (true) {
            index = curr.lastIndexOf('/')
            if (index == -1) {
                break
            }
            curr = curr.substring(0, index) + '$' + curr.substring(index + 1)
            referencedClasses!!.remove(curr)
        }
    }

    companion object {
        /** Manifest or layout referenced classes missing from the project or libraries */
        @JvmField
        val MISSING =
            Issue.create(
                id = "MissingRegistered",
                briefDescription = "Missing registered class",
                explanation = """
                    If a class is referenced in the manifest or in a layout file, it must \
                    also exist in the project (or in one of the libraries included by the \
                    project. This check helps uncover typos in registration names, or \
                    attempts to rename or move classes without updating the XML references 
                    properly.
                    """,
                category = Category.CORRECTNESS,
                priority = 8,
                severity = Severity.ERROR,
                moreInfo = "http://developer.android.com/guide/topics/manifest/manifest-intro.html",
                androidSpecific = true,
                enabledByDefault = false, // Until http://b.android.com/229868 is fixed
                implementation = Implementation(
                    MissingClassDetector::class.java,
                    EnumSet.of(
                        Scope.MANIFEST,
                        Scope.CLASS_FILE,
                        Scope.JAVA_LIBRARIES,
                        Scope.RESOURCE_FILE
                    )
                )
            )

        /** Are activity, service, receiver etc subclasses instantiatable? */
        @JvmField
        val INSTANTIATABLE =
            Issue.create(
                id = "Instantiatable",
                briefDescription = "Registered class is not instantiatable",
                explanation = """
                    Activities, services, broadcast receivers etc. registered in the \
                    manifest file (or for custom views, in a layout file) must be \
                    "instantiatable" by the system, which means that the class must \
                    be public, it must have an empty public constructor, and if it's an \
                    inner class, it must be a static inner class.""",
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.FATAL,
                androidSpecific = true,
                implementation = Implementation(
                    MissingClassDetector::class.java,
                    Scope.CLASS_FILE_SCOPE
                )
            )

        /** Is the right character used for inner class separators? */
        @JvmField
        val INNERCLASS =
            Issue.create(
                id = "InnerclassSeparator",
                briefDescription = "Inner classes should use `${"$"}` rather than `.`",
                explanation = """
                    When you reference an inner class in a manifest file, you must use '$' \
                    instead of '.' as the separator character, i.e. Outer${"$"}Inner instead of \
                    Outer.Inner.

                    (If you get this warning for a class which is not actually an inner class, \
                    it's because you are using uppercase characters in your package name, which \
                    is not conventional.)
                    """,
                category = Category.CORRECTNESS,
                priority = 3,
                severity = Severity.WARNING,
                androidSpecific = true,
                implementation = Implementation(
                    MissingClassDetector::class.java,
                    Scope.MANIFEST_SCOPE
                )
            )
    }
}
