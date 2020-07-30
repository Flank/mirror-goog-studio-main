@file:JvmName("RelativeResourceUtils")

package com.android.ide.common.resources

import java.io.File
import java.io.IOException
import java.lang.IllegalStateException

/**
 * Determines a resource file path relative to the source set containing the resource.
 *
 * The absolute path to the module source set is identified by the source set ordering of a module.
 * Format of the returned String is `<package name - source set module order>:<path to source set>`.
 */
fun produceRelativeSourceSetPath(
        resourceFile: File,
        packageName: String,
        moduleSourceSets: Collection<Collection<File>>
) : String {
    // Allows for relocatablity as various file system formats streamline to the same path format.
    val invariantFilePath = resourceFile.invariantSeparatorsPath
    for (sourceSet in moduleSourceSets) {
        for ((index, sourceSetFile) in sourceSet.withIndex()) {
            if (invariantFilePath.startsWith(sourceSetFile.invariantSeparatorsPath)) {
                val resIndex = sourceSetFile.invariantSeparatorsPath.length
                val relativePathToSourceSet = invariantFilePath.substring(resIndex)
                return "$packageName-$index:$relativePathToSourceSet"
            }
        }
    }

    throw IllegalArgumentException(
            "Unable to locate resourceFile (${resourceFile.absolutePath}) in source-sets.")
}

/**
 * Converts a source set identified relative resource path to an absolute path.
 *
 * The source set identifier before the ':' separator is replaced with the absolute source set
 * path and then concatenated with the path after the ':' separator.
 */
fun relativeResourcePathToAbsolutePath(
        relativePath: String,
        sourceSetPathMap: Map<String, String>): String {
    if (sourceSetPathMap.none()) {
        throw IllegalStateException(
                """Unable to get absolute path from $relativePath
                   because no relative root paths are present.""")
    }
    val separatorIndex = relativePath.indexOf(':')
    if (separatorIndex == -1) {
        throw IllegalArgumentException(
                """Source set identifier and relative path must be separated by a ':'character.
                   Relative path: $relativePath""")
    }
    val sourceSetPrefix = relativePath.substring(0, separatorIndex)
    val resourcePathFromSourceSet = relativePath.substring(separatorIndex + 1, relativePath.length)
    val absolutePath = sourceSetPathMap[sourceSetPrefix]
            ?: throw NoSuchElementException(
                    """Unable to get absolute path from $relativePath
                       because $sourceSetPrefix is not key in sourceSetPathMap.""")

    return "$absolutePath$resourcePathFromSourceSet"
}

/**
 * Parses identifier and file path into a map from a file
 * in the format 'packageName.projectName-sortedOrderPosition absolutePath'.
 */
fun readFromSourceSetPathsFile(artifactFile: File) : Map<String, String> {
    if (!artifactFile.exists() || !artifactFile.isFile) {
        throw IOException("$artifactFile does not exist or is not a file.")
    }
    return artifactFile.bufferedReader().lineSequence().associate {
        it.substringBefore(" ") to it.substringAfter(" ")
    }
}

/**
 * Writes a file containing a mapping of resource source-set absolute paths to a unique identifier
 * in the format of 'packageName.projectName-sortedOrderPosition absolutePath'.
 */
fun writeIdentifiedSourceSetsFile(
        resourceSourceSets: Collection<File>,
        packageName: String,
        projectName: String,
        output: File
) {
    var i = 0
    val sourceSetMap: Map<String, String> = resourceSourceSets
            .sortedBy(File::invariantSeparatorsPath)
            .associate { sourceSet ->
                val sourceSetFolderName = sourceSet.parentFile.name
                val appendProjectName =
                        if (packageName.endsWith(projectName)) "" else ".$projectName"
                val appId = "$packageName$appendProjectName-$sourceSetFolderName-${i++}"
                appId to sourceSet.absolutePath
            }
    output.bufferedWriter().use { bw ->
        sourceSetMap.forEach {
            bw.write("${it.key} ${it.value}\n")
        }
    }
}
