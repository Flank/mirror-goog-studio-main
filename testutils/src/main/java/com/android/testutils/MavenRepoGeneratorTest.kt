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

import com.google.common.collect.ImmutableMap
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth

import org.junit.Test
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

class MavenRepoGeneratorTest {

    @Test
    fun generate() {

        Jimfs.newFileSystem(Configuration.unix()).use { fs ->

            val mavenRepo = MavenRepoGenerator(
                listOf(
                    MavenRepoGenerator.Library("com.example:liba:1", "liba".toByteArray()),
                    MavenRepoGenerator.Library("com.example:libb:1", "libb".toByteArray()),
                    MavenRepoGenerator.Library(
                        "com.example:libc:1",
                        "libc".toByteArray(),
                        "com.example:liba:1",
                        "com.example:libb:1"
                    )
                )
            )

            val dir = fs.getPath("/tmp/maven_repo")

            mavenRepo.generate(dir)

            val actual = ImmutableMap.builder<String, String>().apply {
                Files.walkFileTree(dir, object : SimpleFileVisitor<Path>() {
                    override fun visitFile(
                        file: Path,
                        attrs: BasicFileAttributes?
                    ): FileVisitResult {
                        put(
                            dir.relativize(file).toString(),
                            Files.readAllLines(file).joinToString("\n")
                        )
                        return FileVisitResult.CONTINUE
                    }
                })
            }.build()

            Truth.assertThat(actual).containsExactlyEntriesIn(
                mapOf(
                    "com/example/liba/1/liba-1.jar" to "liba",
                    "com/example/liba/1/liba-1.pom" to """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <project
                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
                            xmlns="http://maven.apache.org/POM/4.0.0"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>liba</artifactId>
                          <version>1</version>
                          <dependencies>
                          </dependencies>
                        </project>
                        """.trimIndent(),
                    "com/example/libb/1/libb-1.jar" to "libb",
                    "com/example/libb/1/libb-1.pom" to """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <project
                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
                            xmlns="http://maven.apache.org/POM/4.0.0"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>libb</artifactId>
                          <version>1</version>
                          <dependencies>
                          </dependencies>
                        </project>
                        """.trimIndent(),
                    "com/example/libc/1/libc-1.jar" to "libc",
                    "com/example/libc/1/libc-1.pom" to """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <project
                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
                            xmlns="http://maven.apache.org/POM/4.0.0"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>libc</artifactId>
                          <version>1</version>
                          <dependencies>
                            <dependency>
                              <groupId>com.example</groupId>
                              <artifactId>liba</artifactId>
                              <version>1</version>
                              <scope>compile</scope>
                            </dependency>
                            <dependency>
                              <groupId>com.example</groupId>
                              <artifactId>libb</artifactId>
                              <version>1</version>
                              <scope>compile</scope>
                            </dependency>
                          </dependencies>
                        </project>
                        """.trimIndent()
                )
            )

        }
    }
}