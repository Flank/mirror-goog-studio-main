package com.android.builder.compiling

import com.android.SdkConstants
import com.android.builder.packaging.JarFlinger
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.zip.Deflater.NO_COMPRESSION

/** Creates a JVM bytecode BuildConfig. */
class BuildConfigByteCodeGenerator(private val data: BuildConfigData) : BuildConfigCreator {

    private val fullyQualifiedBuildConfigClassName: String by lazy {
        "${data.buildConfigPackageName.replace('.', '/')}/${data.buildConfigName}"
    }

    override fun getFolderPath(): File = File(data.outputPath.toFile(),
            "${data.buildConfigName}${SdkConstants.DOT_JAR}")
            .also { it.mkdirs() }

    override fun getBuildConfigFile(): File =
            File(data.outputPath.toFile(), "${data.buildConfigName}${SdkConstants.DOT_JAR}")

    /** Creates a JAR file within the genFolder containing a build config .class which is
     * generated based on the current class attributes.
     */
    override fun generate() = writeToJar(
            getBuildConfigFile().toPath(),
            """${data.buildConfigPackageName.replace('.', '/')}/${data
                    .buildConfigName}${SdkConstants.DOT_CLASS}""".trimMargin(),
            generateByteCode()
    )

    private fun generateByteCode() = generateUsingAsm().toByteArray()

    private fun generateUsingAsm(): ClassWriter {
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
        data.buildConfigFields.forEach {
            it.emit(cw)
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
            jarCreator.setCompressionLevel(NO_COMPRESSION)
            jarCreator.addEntry(buildConfigPackage, bytecodeBuildConfig.inputStream())
        }
    }
}