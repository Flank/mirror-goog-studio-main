/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.testutils

import com.google.common.hash.Hashing
import java.nio.file.Files
import java.nio.file.Path

class MavenRepoGenerator constructor(val libraries: List<Library>) {

    companion object {
        fun libraryWithFixtures(
            mavenCoordinate: String,
            packaging: String,
            mainLibrary: LibraryBuilder.() -> Unit,
            fixtureLibrary: (LibraryBuilder.() -> Unit)? = null,
        ): Library {
            val mainLibraryBuilder = LibraryBuilderImpl().also {
                mainLibrary(it)
            }

            val fixtureLibraryBuilder = fixtureLibrary?.let { action ->
                LibraryBuilderImpl().also { action(it) }
            }

            return Library(
                mavenCoordinate,
                packaging,
                mainLibraryBuilder.toData(),
                fixtureLibraryBuilder?.toData()
            )
        }
    }

    interface LibraryBuilder {
        var artifact: ByteArray?
        val dependencies: MutableList<String>
    }

    internal interface LibraryData {
        val artifact: ByteArray
        val dependencies: List<MavenCoordinate>
    }

    class Library internal constructor(
        mavenCoordinate: String,
        internal val packaging: String,
        internal val mainArtifact: LibraryData,
        internal val fixtureArtifact: LibraryData? = null
    ) {
        constructor(
            mavenCoordinate: String,
            packaging: String,
            artifact: ByteArray,
            vararg dependencies: String
        ): this(mavenCoordinate, packaging, LibraryBuilderImpl(artifact, *dependencies).toData())

        constructor(
            mavenCoordinate: String,
            jar: ByteArray,
            vararg dependencies: String
        ) : this(mavenCoordinate, "jar", jar, *dependencies)

        constructor(
            mavenCoordinate: String,
            vararg dependencies: String
        ) : this(mavenCoordinate, TestInputsGenerator.jarWithEmptyClasses(listOf()), *dependencies)

        val mavenCoordinate = MavenCoordinate.parse(mavenCoordinate)

        fun generatePom(): String {
            val sb = StringBuilder(
                """
                |<?xml version="1.0" encoding="UTF-8"?>
                |<project
                |    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
                |    xmlns="http://maven.apache.org/POM/4.0.0"
                |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                |""".trimMargin())
            if (fixtureArtifact != null) {
                // this is required for Gradle to look for a .module file
                sb.append( """
                |  <!-- This module was also published with a richer model, Gradle metadata,  -->
                |  <!-- which should be used instead. Do not delete the following line which  -->
                |  <!-- is to indicate to Gradle or any Gradle module metadata file consumer  -->
                |  <!-- that they should prefer consuming it instead. -->
                |  <!-- do_not_remove: published-with-gradle-metadata -->
                |""".trimMargin())
            }
            sb.append("""
                |  <modelVersion>4.0.0</modelVersion>
                |  <groupId>${mavenCoordinate.groupId}</groupId>
                |  <artifactId>${mavenCoordinate.artifactId}</artifactId>
                |  <version>${mavenCoordinate.version}</version>
                |""".trimMargin())
            if (packaging != "jar") {
                sb.append( "  <packaging>$packaging</packaging>\n")
            }
            sb.append("  <dependencies>\n")
            for (dependency in mainArtifact.dependencies) {
                sb.append(
                    """
                    |    <dependency>
                    |      <groupId>${dependency.groupId}</groupId>
                    |      <artifactId>${dependency.artifactId}</artifactId>
                    |      <version>${dependency.version}</version>
                    |      <scope>compile</scope>
                    |    </dependency>
                    |""".trimMargin()
                )
            }

            sb.append(
                """
                    |  </dependencies>
                    |</project>
                    |""".trimMargin()
            )

            return sb.toString()
        }

        fun generateModule(
            mainArtifactPath: Path,
            fixtureArtifactPath: Path
        ): String {

            val mainArtifactFile = mainArtifactPath.toFile()
            val mainArtifactName = mainArtifactFile.name
            val mainArtifactSize = mainArtifactFile.length()
            val mainArtifactBytes = mainArtifactFile.readBytes()
            val mainArtifactSha512 = Hashing.sha512().hashBytes(mainArtifactBytes).toString()
            val mainArtifactSha256 = Hashing.sha256().hashBytes(mainArtifactBytes).toString()
            val mainArtifactSha1 = Hashing.sha1().hashBytes(mainArtifactBytes).toString()
            val mainArtifactMd5 = Hashing.md5().hashBytes(mainArtifactBytes).toString()

            val fixtureArtifactFile = fixtureArtifactPath.toFile()
            val fixtureArtifactName = fixtureArtifactFile.name
            val fixtureArtifactSize = fixtureArtifactFile.length()
            val fixtureArtifactBytes = fixtureArtifactFile.readBytes()
            val fixtureArtifactSha512 = Hashing.sha512().hashBytes(fixtureArtifactBytes).toString()
            val fixtureArtifactSha256 = Hashing.sha256().hashBytes(fixtureArtifactBytes).toString()
            val fixtureArtifactSha1 = Hashing.sha1().hashBytes(fixtureArtifactBytes).toString()
            val fixtureArtifactMd5 = Hashing.md5().hashBytes(fixtureArtifactBytes).toString()

            return """
{
  "formatVersion": "1.1",
  "component": {
    "group": "${mavenCoordinate.groupId}",
    "module": "${mavenCoordinate.artifactId}",
    "version": "${mavenCoordinate.version}",
    "attributes": {
      "org.gradle.status": "release"
    }
  },
  "createdBy": {
    "gradle": {
      "version": "7.1"
    }
  },
  "variants": [
    {
      "name": "releaseApiPublication",
      "attributes": {
        "org.gradle.category": "library",
        "org.gradle.dependency.bundling": "external",
        "org.gradle.libraryelements": "$packaging",
        "org.gradle.usage": "java-api"
      },
      "dependencies": [],
      "files": [
        {
          "name": "$mainArtifactName",
          "url": "$mainArtifactName",
          "size": $mainArtifactSize,
          "sha512": "$mainArtifactSha512",
          "sha256": "$mainArtifactSha256",
          "sha1": "$mainArtifactSha1",
          "md5": "$mainArtifactMd5"
        }
      ]
    },
    {
      "name": "releaseRuntimePublication",
      "attributes": {
        "org.gradle.category": "library",
        "org.gradle.dependency.bundling": "external",
        "org.gradle.libraryelements": "$packaging",
        "org.gradle.usage": "java-runtime"
      },
      "dependencies": [],
      "files": [
        {
          "name": "$mainArtifactName",
          "url": "$mainArtifactName",
          "size": $mainArtifactSize,
          "sha512": "$mainArtifactSha512",
          "sha256": "$mainArtifactSha256",
          "sha1": "$mainArtifactSha1",
          "md5": "$mainArtifactMd5"
        }
      ]
    },
    {
      "name": "releaseTestFixturesApiPublication",
      "attributes": {
        "org.gradle.category": "library",
        "org.gradle.dependency.bundling": "external",
        "org.gradle.libraryelements": "$packaging",
        "org.gradle.usage": "java-api"
      },
      "dependencies": [
        {
          "group": "${mavenCoordinate.groupId}",
          "module": "${mavenCoordinate.artifactId}",
          "version": {
            "requires": "${mavenCoordinate.version}"
          }
        }
      ],
      "files": [
        {
          "name": "$fixtureArtifactName",
          "url": "$fixtureArtifactName",
          "size": $fixtureArtifactSize,
          "sha512": "$fixtureArtifactSha512",
          "sha256": "$fixtureArtifactSha256",
          "sha1": "$fixtureArtifactSha1",
          "md5": "$fixtureArtifactMd5"
        }
      ],
      "capabilities": [
        {
          "group": "${mavenCoordinate.groupId}",
          "name": "${mavenCoordinate.artifactId}-test-fixtures",
          "version": "${mavenCoordinate.version}"
        }
      ]
    },
    {
      "name": "releaseTestFixturesRuntimePublication",
      "attributes": {
        "org.gradle.category": "library",
        "org.gradle.dependency.bundling": "external",
        "org.gradle.libraryelements": "$packaging",
        "org.gradle.usage": "java-runtime"
      },
      "dependencies": [
        {
          "group": "${mavenCoordinate.groupId}",
          "module": "${mavenCoordinate.artifactId}",
          "version": {
            "requires": "${mavenCoordinate.version}"
          }
        }
      ],
      "files": [
        {
          "name": "$fixtureArtifactName",
          "url": "$fixtureArtifactName",
          "size": $fixtureArtifactSize,
          "sha512": "$fixtureArtifactSha512",
          "sha256": "$fixtureArtifactSha256",
          "sha1": "$fixtureArtifactSha1",
          "md5": "$fixtureArtifactMd5"
        }
      ],
      "capabilities": [
        {
          "group": "${mavenCoordinate.groupId}",
          "name": "${mavenCoordinate.artifactId}-test-fixtures",
          "version": "${mavenCoordinate.version}"
        }
      ]
    }
  ]
}
""".trimIndent()
        }
    }

    data class MavenCoordinate(
        val groupId: String,
        val artifactId: String,
        val version: String
    ) {

        fun getDirName(): String = "${groupId.replace('.', '/')}/$artifactId/$version/"
        fun getFileName(
            ext: String,
            isFixture: Boolean = false
        ): String = if (isFixture) {
            "$artifactId-$version-test-fixtures.$ext"
        } else {
            "$artifactId-$version.$ext"
        }
        override fun toString(): String = "$groupId:$artifactId:$version"

        companion object {
            fun parse(mavenCoordinate: String): MavenCoordinate {
                val split = mavenCoordinate.split(':')
                if (split.size != 3) {
                    throw IllegalArgumentException("Maven co-ordinate should be group:artifact:version")
                }
                return MavenCoordinate(
                    groupId = split[0],
                    artifactId = split[1],
                    version = split[2]
                )
            }
        }

    }

    fun generate(rootDir: Path) {
        libraries.forEach { library ->
            val dir = rootDir.resolve(library.mavenCoordinate.getDirName())
            Files.createDirectories(dir)

            val mainArtifactFile = dir.resolve(library.mavenCoordinate.getFileName(library.packaging))
            Files.write(mainArtifactFile, library.mainArtifact.artifact)

            Files.write(
                dir.resolve(library.mavenCoordinate.getFileName("pom")),
                library.generatePom().toByteArray()
            )

            library.fixtureArtifact?.let { fixture ->
                val fixtureArtifactFile = dir.resolve(
                    library.mavenCoordinate.getFileName(
                        library.packaging,
                        isFixture = true
                    )
                )
                Files.write(fixtureArtifactFile, fixture.artifact)

                Files.write(
                    dir.resolve(library.mavenCoordinate.getFileName("module")),
                    library.generateModule(
                        mainArtifactFile,
                        fixtureArtifactFile
                    ).toByteArray()
                )
            }
        }
    }

    class LibraryBuilderImpl(
        override var artifact: ByteArray? = null,
        override val dependencies: MutableList<String> = mutableListOf()
    ): LibraryBuilder {

        constructor(
            artifact: ByteArray,
            vararg dependencies: String
        ): this(artifact, dependencies.toMutableList())

        internal fun toData(): LibraryData {
            return LibraryDataImpl(artifact!!, dependencies.map { MavenCoordinate.parse(it) })
        }
    }

    class LibraryDataImpl(
        override val artifact: ByteArray,
        override val dependencies: List<MavenCoordinate>
    ): LibraryData
}
