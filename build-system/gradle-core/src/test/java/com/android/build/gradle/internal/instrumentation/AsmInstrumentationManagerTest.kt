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

package com.android.build.gradle.internal.instrumentation

import com.android.SdkConstants.DOT_CLASS
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.testutils.TestInputsGenerator
import com.android.utils.FileUtils
import com.google.common.io.ByteStreams
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ASM7
import org.objectweb.asm.Type
import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.net.URLClassLoader
import java.util.zip.ZipFile
import kotlin.reflect.jvm.jvmName

@RunWith(Parameterized::class)
class AsmInstrumentationManagerTest(private val testMode: TestMode) {
    enum class TestMode {
        DIR,
        JAR
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "testMode_{0}")
        fun modes(): List<TestMode> {
            return listOf(TestMode.DIR, TestMode.JAR)
        }
    }

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val apiVersion: Int = ASM7
    private val classesHierarchyData = ClassesHierarchyData(apiVersion)

    private lateinit var inputDir: File
    private lateinit var inputJar: File
    private lateinit var outputDir: File
    private lateinit var classes: Map<String, ByteArray>

    @Before
    fun setUp() {
        inputDir = temporaryFolder.newFolder()
        outputDir = temporaryFolder.newFolder()

        val srcClasses = listOf(
            I::class.java,
            InterfaceExtendsI::class.java,
            ClassImplementsI::class.java,
            ClassWithNoInterfacesOrSuperclasses::class.java,
            ClassExtendsOneClassAndImplementsTwoInterfaces::class.java,
            ClassExtendsAClassThatExtendsAnotherClassAndImplementsTwoInterfaces::class.java
        )

        if (testMode == TestMode.DIR) {
            TestInputsGenerator.pathWithClasses(inputDir.toPath(), srcClasses)
            val classesFiles = getClassesFilesMap(inputDir)
            classes = classesFiles.mapValues {
                ByteStreams.toByteArray(FileInputStream(it.value))
            }
            classesHierarchyData.addClassesFromDir(inputDir)
        } else {
            inputJar = File(inputDir, "classes.jar")
            TestInputsGenerator.pathWithClasses(inputJar.toPath(), srcClasses)
            classes = getClassesByteArrayMapFromJar(inputJar)
            classesHierarchyData.addClassesFromJar(inputJar)
        }

        classesHierarchyData.addClass(Type.getInternalName(Object::class.java), null, emptyList())
    }

    private fun AsmInstrumentationManager.instrument() {
        if (testMode == TestMode.DIR) {
            instrumentClassesFromDirectoryToDirectory(inputDir, outputDir)
        } else {
            instrumentClassesFromJarToJar(inputJar, File(outputDir, inputJar.name))
        }
    }

    @Test
    fun testNoVisitors() {
        // Given no visitors, when the instrumentation manager is invoked
        AsmInstrumentationManager(
            listOf(),
            apiVersion,
            classesHierarchyData
        ).instrument()

        // Then the classes should be copied to the destination

        val outputClasses = getOutputClassesByteArrayMap()
        assertThat(outputClasses).hasSize(classes.size)

        outputClasses.forEach { (className, byteArray) ->
            assertThat(byteArray).isEqualTo(classes[className])
        }
    }

    @Test
    fun testDataIsSentCorrectly() {
        val visitorFactory = getConfiguredVisitorFactory(DataCapturerVisitorFactory::class.java) {
            it.a = "Custom"
            it.b = 1234
        } as DataCapturerVisitorFactory

        AsmInstrumentationManager(
            listOf(visitorFactory),
            apiVersion,
            classesHierarchyData
        ).instrument()

        val outputClasses = getOutputClassesByteArrayMap()
        assertThat(outputClasses).hasSize(classes.size)

        outputClasses.forEach { (className, classFile) ->
            assertThat(getClassContentDiff(classes[className], classFile)).isEmpty()
        }

        assertThat(visitorFactory.instrumentationContext.apiVersion.get()).isEqualTo(apiVersion)
        assertThat(visitorFactory.parameters.get().a).isEqualTo("Custom")
        assertThat(visitorFactory.parameters.get().b).isEqualTo(1234)

        val classDataList = visitorFactory.capturedClassData.sortedBy { it.className }

        assertThat(classDataList).hasSize(6)
        classDataList[0].assertEquals(
            ClassExtendsAClassThatExtendsAnotherClassAndImplementsTwoInterfaces::class.java.name,
            listOf(InterfaceExtendsI::class.java.name, I::class.java.name),
            listOf(
                ClassExtendsOneClassAndImplementsTwoInterfaces::class.java.name,
                ClassWithNoInterfacesOrSuperclasses::class.java.name,
                Object::class.java.name
            )
        )
        classDataList[1].assertEquals(
            ClassExtendsOneClassAndImplementsTwoInterfaces::class.java.name,
            listOf(InterfaceExtendsI::class.java.name, I::class.java.name),
            listOf(ClassWithNoInterfacesOrSuperclasses::class.java.name, Object::class.java.name)
        )
        classDataList[2].assertEquals(
            ClassImplementsI::class.java.name,
            listOf(I::class.java.name),
            listOf(Object::class.java.name)
        )
        classDataList[3].assertEquals(
            ClassWithNoInterfacesOrSuperclasses::class.java.name,
            listOf(),
            listOf(Object::class.java.name)
        )
        classDataList[4].assertEquals(I::class.java.name, listOf(), listOf(Object::class.java.name))
        classDataList[5].assertEquals(
            InterfaceExtendsI::class.java.name,
            listOf(I::class.java.name),
            listOf(Object::class.java.name)
        )
    }

    @Test
    fun testTwoModifyingVisitors() {
        // Given two visitors:
        // The first one annotates methods named "f1" or "f2" in classes implementing the interface
        // I with "FirstVisitorAnnotation"
        // The second one annotates methods named "f3" or "f4" in classes implementing the interface
        // J with "SecondVisitorAnnotation"

        // When the instrumentation manager is invoked

        AsmInstrumentationManager(
            listOf(
                getConfiguredVisitorFactory(FirstVisitorAnnotationAddingFactory::class.java),
                getConfiguredVisitorFactory(SecondVisitorAnnotationAddingFactory::class.java)
            ),
            apiVersion,
            classesHierarchyData
        ).instrument()

        val instrumentedClassesLoader = if (testMode == TestMode.DIR) {
            InstrumentedClassesLoader(
                arrayOf(outputDir.toURI().toURL()),
                this::class.java.classLoader
            )
        } else {
            InstrumentedClassesLoader(
                arrayOf(outputDir.listFiles()!!.first().toURI().toURL()),
                this::class.java.classLoader
            )
        }

        // Then classes I and ClassWithNoInterfacesOrSuperclasses should be the same

        val outputClasses = getOutputClassesByteArrayMap()
        assertThat(outputClasses).hasSize(classes.size)

        checkInstrumentedClassAnnotatedMethods(
            instrumentedClassesLoader = instrumentedClassesLoader,
            className = I::class.java.name,
            expectedAnnotatedMethods = emptyList()
        )

        checkInstrumentedClassAnnotatedMethods(
            instrumentedClassesLoader = instrumentedClassesLoader,
            className = ClassWithNoInterfacesOrSuperclasses::class.java.name,
            expectedAnnotatedMethods = emptyList()
        )

        // InterfaceExtendsI.f2 should be annotated with FirstVisitorAnnotation

        checkInstrumentedClassAnnotatedMethods(
            instrumentedClassesLoader = instrumentedClassesLoader,
            className = InterfaceExtendsI::class.java.name,
            expectedAnnotatedMethods = listOf("f2" to listOf(FirstVisitorAnnotation::class.java.name))
        )

        // ClassImplementsI.f1 should be annotated with FirstVisitorAnnotation
        // ClassImplementsI.f2 should be annotated with FirstVisitorAnnotation

        checkInstrumentedClassAnnotatedMethods(
            instrumentedClassesLoader = instrumentedClassesLoader,
            className = ClassImplementsI::class.java.name,
            expectedAnnotatedMethods = listOf(
                "f1" to listOf(FirstVisitorAnnotation::class.java.name),
                "f2" to listOf(FirstVisitorAnnotation::class.java.name)
            )
        )

        // ClassExtendsOneClassAndImplementsTwoInterfaces.f2 should be annotated with FirstVisitorAnnotation
        // ClassExtendsOneClassAndImplementsTwoInterfaces.f3 should be annotated with SecondVisitorAnnotation

        checkInstrumentedClassAnnotatedMethods(
            instrumentedClassesLoader = instrumentedClassesLoader,
            className = ClassExtendsOneClassAndImplementsTwoInterfaces::class.java.name,
            expectedAnnotatedMethods = listOf(
                "f2" to listOf(FirstVisitorAnnotation::class.java.name),
                "f3" to listOf(SecondVisitorAnnotation::class.java.name)
            )
        )

        // The above annotated methods should be inherited
        // ClassExtendsAClassThatExtendsAnotherClassAndImplementsTwoInterfaces.f4 should be annotated with
        // SecondVisitorAnnotation

        checkInstrumentedClassAnnotatedMethods(
            instrumentedClassesLoader = instrumentedClassesLoader,
            className = ClassExtendsAClassThatExtendsAnotherClassAndImplementsTwoInterfaces::class.java.name,
            expectedAnnotatedMethods = listOf(
                "f2" to listOf(FirstVisitorAnnotation::class.java.name),
                "f3" to listOf(SecondVisitorAnnotation::class.java.name),
                "f4" to listOf(SecondVisitorAnnotation::class.java.name)
            )
        )
    }

    class InstrumentedClassesLoader(urls: Array<URL>, parent: ClassLoader) :
        URLClassLoader(urls, parent) {

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            var loadedClass = findLoadedClass(name)
            if (loadedClass == null) {
                loadedClass = try {
                    findClass(name)
                } catch (e: ClassNotFoundException) {
                    super.loadClass(name, resolve)
                }
            }
            if (resolve) {
                resolveClass(loadedClass)
            }
            return loadedClass
        }
    }

    private fun checkInstrumentedClassAnnotatedMethods(
        instrumentedClassesLoader: ClassLoader,
        className: String,
        expectedAnnotatedMethods: List<Pair<String, List<String>>>
    ) {
        val annotatedMethods =
            instrumentedClassesLoader.loadClass(className).methods
                .map {
                    it.name to it.annotations.mapNotNull { annotation ->
                        annotation.annotationClass.jvmName.takeIf { name -> name != "jdk.internal.HotSpotIntrinsicCandidate" }
                    }
                }.filter { it.second.isNotEmpty() }

        assertThat(annotatedMethods).containsExactlyElementsIn(expectedAnnotatedMethods)
    }

    private fun getConfiguredVisitorFactory(
        visitorFactoryClass: Class<out AsmClassVisitorFactory<Params>>,
        paramsConfig: (Params) -> Unit = {}
    ): AsmClassVisitorFactory<Params> {
        return AsmClassVisitorFactoryEntry(
            visitorFactoryClass,
            paramsConfig
        ).also {
            it.configure(
                FakeObjectFactory.factory,
                apiVersion
            )
        }.visitorFactory
    }

    private fun ClassData.assertEquals(
        className: String,
        interfaces: List<String>,
        superClasses: List<String>
    ) {
        assertThat(this.className).isEqualTo(className)
        assertThat(this.interfaces).containsExactlyElementsIn(interfaces)
        assertThat(this.superClasses).containsExactlyElementsIn(superClasses)
    }

    private fun getClassesFilesMap(dir: File): Map<String, File> {
        return FileUtils.getAllFiles(dir).map {
            "com.android.build.gradle.internal.instrumentation.${it.name.removeSuffix(DOT_CLASS)}" to it
        }.toMap()
    }

    private fun getClassesByteArrayMapFromDir(dir: File): Map<String, ByteArray> {
        return getClassesFilesMap(dir).mapValues {
            ByteStreams.toByteArray(FileInputStream(it.value))
        }
    }

    private fun getClassesByteArrayMapFromJar(jar: File): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipFile(jar).use {
            val entries = it.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                it.getInputStream(entry).use { inputStream ->
                    result[entry.name.removeSuffix(DOT_CLASS).replace('/', '.')] =
                        ByteStreams.toByteArray(inputStream)
                }
            }
        }
        return result
    }

    private fun getOutputClassesByteArrayMap(): Map<String, ByteArray> {
        if (testMode == TestMode.JAR) {
            return getClassesByteArrayMapFromJar(outputDir.listFiles()!![0])
        }
        return getClassesByteArrayMapFromDir(outputDir)
    }

    open class Params : InstrumentationParameters {
        lateinit var a: String
        var b: Int = -1
    }

    @Retention(AnnotationRetention.RUNTIME)
    annotation class FirstVisitorAnnotation

    @Retention(AnnotationRetention.RUNTIME)
    annotation class SecondVisitorAnnotation

    abstract class FirstVisitorAnnotationAddingFactory : AsmClassVisitorFactory<Params> {

        override fun createClassVisitor(
            classData: ClassData,
            nextClassVisitor: ClassVisitor
        ): ClassVisitor {
            return AnnotationAddingVisitor(
                setOf("f1", "f2"),
                Type.getDescriptor(FirstVisitorAnnotation::class.java),
                instrumentationContext.apiVersion.get(),
                nextClassVisitor
            )
        }

        override fun isInstrumentable(
            classData: ClassData
        ): Boolean {
            return classData.interfaces.contains(I::class.java.name)
        }
    }

    abstract class SecondVisitorAnnotationAddingFactory : AsmClassVisitorFactory<Params> {
        override fun createClassVisitor(
            classData: ClassData,
            nextClassVisitor: ClassVisitor
        ): ClassVisitor {
            return AnnotationAddingVisitor(
                setOf("f3", "f4"),
                Type.getDescriptor(SecondVisitorAnnotation::class.java),
                instrumentationContext.apiVersion.get(),
                nextClassVisitor
            )
        }

        override fun isInstrumentable(
            classData: ClassData
        ): Boolean {
            return classData.interfaces.contains(InterfaceExtendsI::class.java.name)
        }
    }

    class AnnotationAddingVisitor(
        private val methodNamesToBeAnnotated: Set<String>,
        private val annotationDescriptor: String,
        val apiVersion: Int,
        classVisitor: ClassVisitor
    ) : ClassVisitor(apiVersion, classVisitor) {
        override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor {
            if (methodNamesToBeAnnotated.contains(name)) {
                return object : MethodVisitor(
                    apiVersion,
                    super.visitMethod(access, name, descriptor, signature, exceptions)
                ) {
                    private var annotationVisited = false

                    override fun visitCode() {
                        visitAnnotation(annotationDescriptor, true)
                        annotationVisited = true
                        super.visitCode()
                    }

                    override fun visitEnd() {
                        // We don't visit code in interfaces
                        if (!annotationVisited) {
                            visitAnnotation(annotationDescriptor, true)
                        }
                        super.visitEnd()
                    }
                }
            }
            return super.visitMethod(access, name, descriptor, signature, exceptions)
        }
    }

    abstract class DataCapturerVisitorFactory : AsmClassVisitorFactory<Params> {
        val capturedClassData: MutableList<ClassData> = mutableListOf()

        override fun createClassVisitor(
            classData: ClassData,
            nextClassVisitor: ClassVisitor
        ): ClassVisitor {
            capturedClassData.add(classData)
            return object :
                ClassVisitor(instrumentationContext.apiVersion.get(), nextClassVisitor) {}
        }

        override fun isInstrumentable(
            classData: ClassData
        ): Boolean {
            return true
        }
    }
}