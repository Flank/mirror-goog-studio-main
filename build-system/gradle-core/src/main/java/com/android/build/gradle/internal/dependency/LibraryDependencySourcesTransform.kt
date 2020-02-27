package com.android.build.gradle.internal.dependency

import com.android.SdkConstants
import com.android.ide.common.symbols.IdProvider
import com.android.ide.common.symbols.parseResourceSourceSetDirectory
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import com.android.ide.common.symbols.SymbolIo


/** This transform outputs a directory containing two files listing the contents of this AAR,
 *  classes.txt where each line is a java class name of the form 'com/example/MyClass.class'
 *  and resource_symbols.txt which is of r-def form [SymbolIo.readRDef]) */
abstract class LibraryDependencySourcesTransform : TransformAction<GenericTransformParameters> {

    @get:Classpath
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(transformOutputs: TransformOutputs) {
        val explodedAar = inputArtifact.get().asFile
        // List each .class file within each jar in the exploded AAR.
        val classesInExplodedAar = getClassesFromExplodedAar(explodedAar)

        val outputDir = transformOutputs.dir(
                "dependency-sources-${explodedAar.nameWithoutExtension}")
        // Write classes and resources paths to transform output files.
        writePathsToFile(
                File(outputDir, "classes${SdkConstants.DOT_TXT}"),
                classesInExplodedAar)
        writeResourcesFromExplodedAarToFile(
                explodedAar,
                File(outputDir, "resources_symbols${SdkConstants.DOT_TXT}"))
    }
}

fun writeResourcesFromExplodedAarToFile(explodedAar: File, outputFile: File) {
    val resourceDir = explodedAar.resolve(SdkConstants.FD_RESOURCES)
    if (resourceDir.list() == null || resourceDir.listFiles()!!.none()) {
        return
    }
    val resourceSymbols = parseResourceSourceSetDirectory(
            resourceDir,
            IdProvider.constant(),
            null
    )
    SymbolIo.writeRDef(resourceSymbols, outputFile.toPath())
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

/** Write collection element by element to the outputFile. */
fun writePathsToFile(outputFile: File, paths: Collection<String>): File {
    if (!outputFile.exists()) {
        Files.createFile(outputFile.toPath())
    }
    outputFile.bufferedWriter().use { writer ->
        paths.forEach { classPath ->
            writer.append(classPath)
            writer.newLine()
        }
    }
    return outputFile
}