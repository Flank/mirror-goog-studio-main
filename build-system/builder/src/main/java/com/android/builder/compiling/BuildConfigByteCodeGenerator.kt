package com.android.builder.compiling

import com.android.builder.packaging.JarFlinger
import com.android.SdkConstants
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method
import java.nio.file.Path
import java.io.File
import java.io.IOException

/** Creates a JVM bytecode BuildConfig. */
class BuildConfigByteCodeGenerator private constructor(
        private val outputPath: Path,
        private val buildConfigPackageName: String,
        private val buildConfigName: String,
        private val buildConfigClassFields: List<BuildConfigClassField>) : BuildConfigCreator {

    private val fullyQualifiedBuildConfigClassName: String by lazy {
        "${buildConfigPackageName.replace('.', '/')}/$buildConfigName"
    }

    class Builder(
            private var outputPath: Path? = null,
            private var buildConfigPackageName: String? = null,
            private var buildConfigName: String = "BuildConfig",
            private val buildConfigClassFields: MutableList<BuildConfigClassField> = mutableListOf()
    ) {
        fun setOutputPath(outputPath: Path) =
                apply { this.outputPath = outputPath }

        fun setBuildConfigPackageName(buildConfigPackageName: String) =
                apply { this.buildConfigPackageName = buildConfigPackageName }

        fun setBuildConfigName(buildConfigName: String) =
                apply { this.buildConfigName = buildConfigName }

        fun addStringField(name: String, value: String) = apply {
            buildConfigClassFields += BuildConfigClassField.StringField(name, value)
        }

        fun addIntField(name: String, value: Int) = apply {
            buildConfigClassFields += BuildConfigClassField.IntField(name, value)
        }

        fun addDebugField(name: String, value: Boolean) = apply {
            buildConfigClassFields += BuildConfigClassField.DebugField(name, value)
        }

        fun build() = BuildConfigByteCodeGenerator(
                outputPath ?: error("outputPath is required."),
                buildConfigPackageName ?: error("buildConfigPackageName is required."),
                buildConfigName,
                buildConfigClassFields
        )
    }

    override fun getFolderPath(): File = File(outputPath.toFile(),
            "$buildConfigName${SdkConstants.DOT_JAR}")
            .also { it.mkdirs() }

    override fun getBuildConfigFile(): File =
            File(outputPath.toFile(), "$buildConfigName${SdkConstants.DOT_JAR}")

    /** Creates a JAR file within the genFolder containing a build config .class which is
     * generated based on the current class attributes.
     */
    override fun generate() = writeToJar(
            getBuildConfigFile().toPath(),
            """${buildConfigPackageName.replace('.', '/')}/$buildConfigName${SdkConstants.DOT_CLASS}""".trimMargin(),
            generateByteCode()
    )

    private fun generateByteCode() = generateUsingAsm().toByteArray()

    private fun generateUsingAsm(): ClassWriter {
        val pfsOpcodes = ACC_PUBLIC + ACC_FINAL + ACC_STATIC
        val cw = ClassWriter(COMPUTE_MAXS)

        // Class Signature
        cw.visit(
                V1_8,
                ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
                fullyQualifiedBuildConfigClassName,
                null,
                "java/lang/Object",
                null
        )

        // Field Attributes
        buildConfigClassFields.forEach {
            when (it) {
                is BuildConfigClassField.StringField -> {
                    cw.visitField(
                            pfsOpcodes,
                            it.name,
                            Type.getDescriptor(String::class.java),
                            null,
                            it.value
                    ).visitEnd()
                }
                is BuildConfigClassField.IntField -> {
                    cw.visitField(
                            pfsOpcodes,
                            it.name,
                            Type.getDescriptor(Int::class.java),
                            null,
                            it.value
                    ).visitEnd()
                }
                is BuildConfigClassField.DebugField -> {
                    cw.visitField(
                            pfsOpcodes,
                            it.name,
                            Type.getDescriptor(Boolean::class.java),
                            null,
                            it.value
                    ).visitEnd()
                }
            }
        }

        val constructorMethod = Method.getMethod("void <init> ()")
        val cGen = GeneratorAdapter(ACC_PUBLIC, constructorMethod, null, null, cw)
        cGen.loadThis()
        cGen.invokeConstructor(Type.getType(Object::class.java), constructorMethod)
        cGen.returnValue()
        cGen.endMethod()

        cw.visitEnd()

        return cw
    }

    @Throws(IOException::class)
    private fun writeToJar(
            outputPath: Path, buildConfigPackage: String, bytecodeBuildConfig: ByteArray) {
        outputPath.toFile().createNewFile()
        JarFlinger(outputPath).use { jarCreator ->
            jarCreator.addEntry(buildConfigPackage, bytecodeBuildConfig.inputStream())
        }
    }
}