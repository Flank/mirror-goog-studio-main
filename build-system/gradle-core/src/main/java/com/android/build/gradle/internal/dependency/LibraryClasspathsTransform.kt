package com.android.build.gradle.internal.dependency

import com.android.SdkConstants
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.tasks.Classpath
import java.nio.file.Files
import java.nio.file.Path
import org.gradle.api.provider.Provider
import java.io.File
import java.util.zip.ZipInputStream

/** Transform to extract all .class filepaths contained within the JAR files of an exploded AAR. */
abstract class LibraryClasspathsTransform : TransformAction<GenericTransformParameters> {

    @get:Classpath
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(transformOutputs: TransformOutputs) {
        val explodedAar = inputArtifact.get().asFile
        // List each .class file within each jar in the exploded AAR.
        val classes = getClassesFromExplodedAar(explodedAar)
        val outputFile = transformOutputs.file(
                "class_and_aar_name${explodedAar.name}${SdkConstants.DOT_TXT}")

        Files.createFile(outputFile.toPath())
        writeClassPathsToFile(outputFile, classes)
    }
}

/** Gets a list of .class filepaths from all JAR files stored within an exploded AAR File. */
fun getClassesFromExplodedAar(explodedAar: File): List<String> {
    return AarTransformUtil.getJars(explodedAar)
            .flatMap {
                getClassesInJar(it.toPath())
            }
}

/** Gets a list of .class filepaths within a JAR file. */
fun getClassesInJar(jarFile: Path): List<String> {
    val classes = mutableListOf<String>()
    ZipInputStream(jarFile.toFile().inputStream()).use { zipEntry ->
        while (true) {
            val entry = zipEntry.nextEntry ?: break
            if (entry.name.endsWith(SdkConstants.DOT_CLASS)) {
                classes.add(entry.name)
            }
        }
    }
    return classes
}

/** Writes to .class filepaths line by line to the outputFile. */
fun writeClassPathsToFile(outputFile: File, classPaths: Collection<String>): File {
    outputFile.bufferedWriter().use { writer ->
        classPaths.forEach { classPath ->
            writer.append(classPath)
            writer.newLine()
        }
    }
    return outputFile
}
