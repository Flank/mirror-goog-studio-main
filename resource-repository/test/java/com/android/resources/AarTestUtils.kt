/*
 * Copyright (C) 2021 The Android Open Source Project
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
@file:JvmName("AarTestUtils")

package com.android.resources

import com.android.SdkConstants.DOT_AAR
import com.android.ide.common.util.toPathString
import com.android.resources.aar.AarSourceResourceRepository
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

const val AAR_LIBRARY_NAME = "com.test:test-library:1.0.0"
const val AAR_PACKAGE_NAME = "com.test.testlibrary"
const val TEST_DATA_DIR = "tools/base/resource-repository/test/resources/aar"

@JvmOverloads
fun getTestAarRepositoryFromExplodedAar(libraryDirName: String = "my_aar_lib"): AarSourceResourceRepository {
  return AarSourceResourceRepository.create(
      resolveWorkspacePath("$TEST_DATA_DIR/$libraryDirName/res"),
      AAR_LIBRARY_NAME
  )
}

@JvmOverloads
fun getTestAarRepository(tempDir: Path, libraryDirName: String = "my_aar_lib"): AarSourceResourceRepository {
  val aar = createAar(tempDir, libraryDirName)
  return AarSourceResourceRepository.create(aar, AAR_LIBRARY_NAME)
}

/**
 * Creates an .aar file for the [libraryDirName] library. The name of the .aar file is determined by [libraryDirName].
 *
 * @return the path to the resulting .aar file in the temporary directory
 */
@JvmOverloads
fun createAar(tempDir: Path, libraryDirName: String = "my_aar_lib"): Path {
  val sourceDirectory = resolveWorkspacePath("$TEST_DATA_DIR/$libraryDirName")
  return createAar(sourceDirectory, tempDir)
}

private fun createAar(sourceDirectory: Path, tempDir: Path): Path {
  val aarFile = tempDir.resolve(sourceDirectory.fileName.toString() + DOT_AAR)
  ZipOutputStream(Files.newOutputStream(aarFile)).use { zip ->
    Files.walkFileTree(sourceDirectory, object : SimpleFileVisitor<Path>() {
      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        val relativePath = FileUtil.toSystemIndependentName(sourceDirectory.relativize(file).toString())
        createZipEntry(relativePath, Files.readAllBytes(file), zip)
        return FileVisitResult.CONTINUE
      }
    })
  }
  return aarFile
}

private fun createZipEntry(name: String, content: ByteArray, zip: ZipOutputStream) {
  val entry = ZipEntry(name)
  zip.putNextEntry(entry)
  zip.write(content)
  zip.closeEntry()
}

fun getTestAarRepositoryWithResourceFolders(libraryDirName: String, vararg resources: String): AarSourceResourceRepository {
  val root = resolveWorkspacePath("$TEST_DATA_DIR/$libraryDirName/res").toPathString()
  return AarSourceResourceRepository.create(
    root,
    resources.map { resource -> root.resolve(resource) },
    AAR_LIBRARY_NAME,
    null
  )
}
