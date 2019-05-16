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

import java.lang.StringBuilder
import java.nio.file.Files
import java.nio.file.Path

class MavenRepoGenerator constructor(val libraries: List<Library>) {

    class Library(
        mavenCoordinate: String,
        val jar: ByteArray,
        vararg dependencies: String
    ) {
        val mavenCoordinate = MavenCoordinate.parse(mavenCoordinate)
        val dependencies = dependencies.map { MavenCoordinate.parse(it) }

        fun generatePom(): String {
            val sb = StringBuilder(
                """
                |<?xml version="1.0" encoding="UTF-8"?>
                |<project
                |    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
                |    xmlns="http://maven.apache.org/POM/4.0.0"
                |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                |  <modelVersion>4.0.0</modelVersion>
                |  <groupId>${mavenCoordinate.groupId}</groupId>
                |  <artifactId>${mavenCoordinate.artifactId}</artifactId>
                |  <version>${mavenCoordinate.version}</version>
                |  <dependencies>
                |""".trimMargin()
            )

            for (dependency in dependencies) {
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
    }

    data class MavenCoordinate(
        val groupId: String,
        val artifactId: String,
        val version: String
    ) {

        fun getDirName(): String = "${groupId.replace('.', '/')}/$artifactId/$version/"
        fun getFileName(ext: String): String = "$artifactId-$version.$ext"

        companion object {
            fun parse(mavenCoordiante: String): MavenCoordinate {
                val split = mavenCoordiante.split(':')
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
        libraries.forEach {
            val dir = rootDir.resolve(it.mavenCoordinate.getDirName())
            Files.createDirectories(dir)
            Files.write(dir.resolve(it.mavenCoordinate.getFileName("jar")), it.jar)
            Files.write(
                dir.resolve(it.mavenCoordinate.getFileName("pom")),
                it.generatePom().toByteArray()
            )
        }
    }

}