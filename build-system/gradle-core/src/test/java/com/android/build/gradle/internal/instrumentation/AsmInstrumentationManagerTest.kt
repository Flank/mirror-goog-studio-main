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
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
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
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ARETURN
import org.objectweb.asm.Opcodes.ASM7
import org.objectweb.asm.Type
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.net.URLClassLoader
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
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
    private val androidJar = TestUtils.resolvePlatformPath("android.jar").toFile()

    private lateinit var classesHierarchyResolver: ClassesHierarchyResolver
    private lateinit var inputDir: File
    private lateinit var inputJar: File
    private lateinit var outputDir: File
    private lateinit var classes: Map<String, ByteArray>

    /**
     * As we're setting maxs to -1, and the value is stored in an unsigned 16 bit integer, the
     * stored value will be (2^16) - 1
     */
    private val invalidMaxsValue = (1 shl 16) - 1

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

        val builder = ClassesHierarchyResolver.Builder(ClassesDataCache()).addDependenciesSources(androidJar)

        if (testMode == TestMode.DIR) {
            TestInputsGenerator.pathWithClasses(inputDir.toPath(), srcClasses)
            val classesFiles = getClassesFilesMap(inputDir)
            classes = classesFiles.mapValues {
                ByteStreams.toByteArray(FileInputStream(it.value))
            }
            builder.addProjectSources(inputDir)
        } else {
            inputJar = File(inputDir, "classes.jar")
            TestInputsGenerator.pathWithClasses(inputJar.toPath(), srcClasses)
            classes = getClassesByteArrayMapFromJar(inputJar)
            builder.addProjectSources(inputJar)
        }

        classesHierarchyResolver = builder.build()
    }

    private fun AsmInstrumentationManager.instrument(inputDir: File, outputDir: File) {
        if (testMode == TestMode.DIR) {
            instrumentClassesFromDirectoryToDirectory(inputDir, outputDir)
        } else {
            val inputJar = inputDir.listFiles()!![0]
            instrumentClassesFromJarToJar(inputJar, File(outputDir, inputJar.name))
        }
        close()
    }

    @Test
    fun testNoVisitors() {
        // Given no visitors, when the instrumentation manager is invoked
        AsmInstrumentationManager(
            listOf(),
            apiVersion,
            classesHierarchyResolver,
            FramesComputationMode.COPY_FRAMES,
            emptySet()
        ).instrument(inputDir, outputDir)

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
            classesHierarchyResolver,
            FramesComputationMode.COPY_FRAMES,
            emptySet()
        ).instrument(inputDir, outputDir)

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
            className = ClassExtendsAClassThatExtendsAnotherClassAndImplementsTwoInterfaces::class.java.name,
            annotations = listOf(Instrument::class.java.name),
            interfaces = listOf(InterfaceExtendsI::class.java.name, I::class.java.name),
            superClasses = listOf(
                ClassExtendsOneClassAndImplementsTwoInterfaces::class.java.name,
                ClassWithNoInterfacesOrSuperclasses::class.java.name,
                Object::class.java.name
            )
        )
        classDataList[1].assertEquals(
            className = ClassExtendsOneClassAndImplementsTwoInterfaces::class.java.name,
            annotations = listOf(),
            interfaces = listOf(InterfaceExtendsI::class.java.name, I::class.java.name),
            superClasses = listOf(
                ClassWithNoInterfacesOrSuperclasses::class.java.name,
                Object::class.java.name
            )
        )
        classDataList[2].assertEquals(
            className = ClassImplementsI::class.java.name,
            annotations = listOf(Instrument::class.java.name),
            interfaces = listOf(I::class.java.name),
            superClasses = listOf(Object::class.java.name)
        )
        classDataList[3].assertEquals(
            className = ClassWithNoInterfacesOrSuperclasses::class.java.name,
            annotations = listOf(),
            interfaces = listOf(),
            superClasses = listOf(Object::class.java.name)
        )
        classDataList[4].assertEquals(
            className = I::class.java.name,
            annotations = listOf(),
            interfaces = listOf(),
            superClasses = listOf(Object::class.java.name)
        )
        classDataList[5].assertEquals(
            className = InterfaceExtendsI::class.java.name,
            annotations = listOf(Instrument::class.java.name),
            interfaces = listOf(I::class.java.name),
            superClasses = listOf(Object::class.java.name)
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
            classesHierarchyResolver,
            FramesComputationMode.COPY_FRAMES,
            emptySet()
        ).instrument(inputDir, outputDir)

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

    @Test
    fun metaInfDirShouldBeIgnored() {
        if (testMode != TestMode.JAR) {
            return
        }

        val jarFile = inputDir.listFiles()!!.first()
        val newJarFile = File(inputDir, "classesWithMetaInf.jar")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(newJarFile))).use { outputJar ->
            outputJar.setLevel(Deflater.NO_COMPRESSION)
            ZipFile(jarFile).use { inputJar ->
                val entries = inputJar.entries()
                while (entries.hasMoreElements()) {
                    val oldEntry = entries.nextElement()
                    val byteArray = ByteStreams.toByteArray(inputJar.getInputStream(oldEntry))
                    val entry = ZipEntry(oldEntry.name)
                    outputJar.putNextEntry(entry)
                    outputJar.write(byteArray)
                    outputJar.closeEntry()
                }
            }

            val metaInfClassEntry = ZipEntry("META-INF/Test.class")
            outputJar.putNextEntry(metaInfClassEntry)
            outputJar.write("ASM should fail trying to load this corrupted class".toByteArray())
            outputJar.closeEntry()
        }

        jarFile.delete()

        abstract class Factory: AsmClassVisitorFactory<InstrumentationParameters.None> {

            override fun createClassVisitor(
                classContext: ClassContext,
                nextClassVisitor: ClassVisitor
            ): ClassVisitor =
                object: ClassVisitor(instrumentationContext.apiVersion.get(), nextClassVisitor) {}

            override fun isInstrumentable(classData: ClassData): Boolean = true
        }

        AsmInstrumentationManager(
            listOf(
                getConfiguredVisitorFactory(Factory::class.java)
            ),
            apiVersion,
            classesHierarchyResolver,
            FramesComputationMode.COPY_FRAMES,
            emptySet()
        ).instrument(inputDir, outputDir)
    }

    @Test
    fun testExcludes() {
        // Given two visitors:
        // The first one annotates methods named "f1" or "f2" in classes implementing the interface
        // I with "FirstVisitorAnnotation"
        // The second one annotates methods named "f3" or "f4" in classes implementing the interface
        // J with "SecondVisitorAnnotation"

        // When the instrumentation manager is invoked with the following excludes patterns
        // ["**/*ImplementsI", "com/android/build/gradle/internal/instrumentation/ClassExtendsAClassThatExtendsAnotherClassAndImplementsTwoInterfaces"]

        AsmInstrumentationManager(
            listOf(
                getConfiguredVisitorFactory(FirstVisitorAnnotationAddingFactory::class.java),
                getConfiguredVisitorFactory(SecondVisitorAnnotationAddingFactory::class.java)
            ),
            apiVersion,
            classesHierarchyResolver,
            FramesComputationMode.COPY_FRAMES,
            setOf(
                "**/*ImplementsI",
                "com/android/build/gradle/internal/instrumentation/ClassExtendsAClassThatExtendsAnotherClassAndImplementsTwoInterfaces"
            )
        ).instrument(inputDir, outputDir)

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

        // ClassImplementsI.f1 should be annotated with FirstVisitorAnnotation but is filtered out
        // ClassImplementsI.f2 should be annotated with FirstVisitorAnnotation but is filtered out

        checkInstrumentedClassAnnotatedMethods(
            instrumentedClassesLoader = instrumentedClassesLoader,
            className = ClassImplementsI::class.java.name,
            expectedAnnotatedMethods = emptyList()
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
        // SecondVisitorAnnotation but is filtered out

        checkInstrumentedClassAnnotatedMethods(
            instrumentedClassesLoader = instrumentedClassesLoader,
            className = ClassExtendsAClassThatExtendsAnotherClassAndImplementsTwoInterfaces::class.java.name,
            expectedAnnotatedMethods = listOf(
                "f2" to listOf(FirstVisitorAnnotation::class.java.name),
                "f3" to listOf(SecondVisitorAnnotation::class.java.name)
            )
        )
    }

    private fun invalidateInputClassesMaxs(): File {
        val newInputDir = temporaryFolder.newFolder()
        AsmInstrumentationManager(
            listOf(getConfiguredVisitorFactory(MaxsInvalidatingVisitorFactory::class.java)),
            apiVersion,
            classesHierarchyResolver,
            FramesComputationMode.COPY_FRAMES,
            emptySet()
        ).instrument(inputDir, newInputDir)
        return newInputDir
    }

    @Test
    fun testCopyFramesMode() {
        // First invalidate the maxs of ClassImplementsI
        val newInputDir = invalidateInputClassesMaxs()

        // The invalid maxs should be just copied
        var classContent =
            dumpClassContent(getOutputClassesByteArrayMap(newInputDir)[ClassImplementsI::class.java.name])

        // The existing methods (<init>, f1, f2) should have invalid maxs
        assertThat(classContent.count { it.contains("MAXSTACK = $invalidMaxsValue") }).isEqualTo(3)
        assertThat(classContent.count { it.contains("MAXLOCALS = $invalidMaxsValue") }).isEqualTo(3)

        // Given a visitor that injects a method to ClassImplementsI
        // When the instrumentation manager is invoked with COPY_FRAMES mode

        AsmInstrumentationManager(
            listOf(getConfiguredVisitorFactory(MethodInjectingVisitorFactory::class.java)),
            apiVersion,
            classesHierarchyResolver,
            FramesComputationMode.COPY_FRAMES,
            emptySet()
        ).instrument(newInputDir, outputDir)

        // Then all existing and injected methods should have invalid maxs
        classContent =
            dumpClassContent(getOutputClassesByteArrayMap()[ClassImplementsI::class.java.name])

        assertThat(classContent.count { it.contains("MAXSTACK = $invalidMaxsValue") }).isEqualTo(4)
        assertThat(classContent.count { it.contains("MAXLOCALS = $invalidMaxsValue") }).isEqualTo(4)
    }

    @Test
    fun testFramesComputationForInstrumentedMethodsOnly() {
        // First invalidate the maxs of ClassImplementsI
        val newInputDir = invalidateInputClassesMaxs()

        // Given a visitor that injects a method to ClassImplementsI
        // When the instrumentation manager is invoked with COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
        // mode
        AsmInstrumentationManager(
            listOf(getConfiguredVisitorFactory(MethodInjectingVisitorFactory::class.java)),
            apiVersion,
            classesHierarchyResolver,
            FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS,
            emptySet()
        ).instrument(newInputDir, outputDir)

        // Then only the existing methods (<init>, f1, f2) should have invalid maxs
        val classContent =
            dumpClassContent(getOutputClassesByteArrayMap()[ClassImplementsI::class.java.name])

        assertThat(classContent.count { it.contains("MAXSTACK = $invalidMaxsValue") }).isEqualTo(3)
        assertThat(classContent.count { it.contains("MAXLOCALS = $invalidMaxsValue") }).isEqualTo(3)
    }

    @Test
    fun testFramesComputationForInstrumentedClasses() {
        // First invalidate the maxs of ClassImplementsI
        val newInputDir = invalidateInputClassesMaxs()

        // Given a visitor that injects a method to ClassImplementsI
        // When the instrumentation manager is invoked with COMPUTE_FRAMES_FOR_INSTRUMENTED_CLASSES
        // mode
        AsmInstrumentationManager(
            listOf(getConfiguredVisitorFactory(MethodInjectingVisitorFactory::class.java)),
            apiVersion,
            classesHierarchyResolver,
            FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_CLASSES,
            emptySet()
        ).instrument(newInputDir, outputDir)

        // Then all methods should have their maxs fixed
        val classContent =
            dumpClassContent(getOutputClassesByteArrayMap()[ClassImplementsI::class.java.name])

        assertThat(classContent.count { it.contains("MAXSTACK = $invalidMaxsValue") }).isEqualTo(0)
        assertThat(classContent.count { it.contains("MAXLOCALS = $invalidMaxsValue") }).isEqualTo(0)
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

    private fun<T : InstrumentationParameters> getConfiguredVisitorFactory(
        visitorFactoryClass: Class<out AsmClassVisitorFactory<T>>,
        paramsConfig: (T) -> Unit = {}
    ): AsmClassVisitorFactory<T> {
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
        annotations: List<String>,
        interfaces: List<String>,
        superClasses: List<String>
    ) {
        assertThat(this.className).isEqualTo(className)
        assertThat(this.classAnnotations).containsExactlyElementsIn(annotations)
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

    private fun getOutputClassesByteArrayMap(outputDir: File = this.outputDir): Map<String, ByteArray> {
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
            classContext: ClassContext,
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
            classContext: ClassContext,
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

    class MaxsInvalidatingClassVisitor(
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
            val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
            return object : MethodVisitor(apiVersion, mv) {
                override fun visitMaxs(maxStack: Int, maxLocals: Int) {
                    super.visitMaxs(-1, -1)
                }
            }
        }
    }

    abstract class MaxsInvalidatingVisitorFactory : AsmClassVisitorFactory<Params> {
        override fun createClassVisitor(
            classContext: ClassContext,
            nextClassVisitor: ClassVisitor
        ): ClassVisitor {
            return MaxsInvalidatingClassVisitor(
                instrumentationContext.apiVersion.get(),
                nextClassVisitor
            )
        }

        override fun isInstrumentable(classData: ClassData): Boolean {
            return classData.classAnnotations.contains(Instrument::class.java.name) &&
                    classData.className.endsWith("ClassImplementsI")
        }
    }

    class MethodInjectingClassVisitor(
        val apiVersion: Int,
        classVisitor: ClassVisitor
    ) : ClassVisitor(apiVersion, classVisitor) {

        override fun visitEnd() {
            visitMethod(
                ACC_PUBLIC,
                "injectedMethod",
                "()Ljava/lang/String;",
                null,
                null
            ).also { methodVisitor ->
                methodVisitor.visitCode()
                methodVisitor.visitLdcInsn("This is an injected method")
                methodVisitor.visitInsn(ARETURN)
                methodVisitor.visitMaxs(-1, -1)
                methodVisitor.visitEnd()
            }

            super.visitEnd()
        }
    }

    abstract class MethodInjectingVisitorFactory : AsmClassVisitorFactory<Params> {
        override fun createClassVisitor(
            classContext: ClassContext,
            nextClassVisitor: ClassVisitor
        ): ClassVisitor {
            return MethodInjectingClassVisitor(
                instrumentationContext.apiVersion.get(),
                nextClassVisitor
            )
        }

        override fun isInstrumentable(classData: ClassData): Boolean {
            return classData.classAnnotations.contains(Instrument::class.java.name) &&
                    classData.className.endsWith("ClassImplementsI")
        }
    }

    abstract class DataCapturerVisitorFactory : AsmClassVisitorFactory<Params> {
        val capturedClassData: MutableList<ClassData> = mutableListOf()

        override fun createClassVisitor(
            classContext: ClassContext,
            nextClassVisitor: ClassVisitor
        ): ClassVisitor {
            capturedClassData.add(classContext.currentClassData)
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
