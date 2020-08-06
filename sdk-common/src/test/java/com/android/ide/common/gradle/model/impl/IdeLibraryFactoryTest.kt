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
package com.android.ide.common.gradle.model.impl

import com.android.builder.model.AndroidLibrary
import com.android.builder.model.JavaLibrary
import com.android.builder.model.Library
import com.android.builder.model.MavenCoordinates
import com.android.ide.common.gradle.model.impl.IdeLibraryFactory.Companion.computeAddress
import com.android.ide.common.gradle.model.impl.IdeLibraryFactory.Companion.isLocalAarModule
import com.android.ide.common.gradle.model.stubs.AndroidLibraryStub
import com.android.ide.common.gradle.model.stubs.JavaLibraryStub
import com.android.ide.common.gradle.model.stubs.LibraryStub
import com.android.ide.common.gradle.model.stubs.MavenCoordinatesStub
import com.google.common.truth.Truth
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File

/** Tests for [IdeLibraryFactory].  */
class IdeLibraryFactoryTest {
  private var myLibraryFactory: IdeLibraryFactory? = null

  @Before
  @Throws(Exception::class)
  fun setUp() {
    myLibraryFactory = IdeLibraryFactory()
  }

  @Test
  fun createFromJavaLibrary() {
    // Verify JavaLibrary of module dependency returns instance of IdeModuleLibrary.
    val moduleLibrary = myLibraryFactory!!.create(JavaLibraryStub())
    Truth.assertThat(moduleLibrary).isInstanceOf(IdeModuleLibrary::class.java)

    // Verify JavaLibrary of jar dependency returns instance of IdeJavaLibrary.
    val javaLibrary: JavaLibrary = object : JavaLibraryStub() {
      override fun getProject(): String? {
        return null
      }
    }
    Truth.assertThat(myLibraryFactory!!.create(javaLibrary)).isInstanceOf(IdeJavaLibrary::class.java)
  }

  @Test
  fun createFromString() {
    Truth.assertThat(myLibraryFactory!!.create("lib", ":lib@@:", "/rootDir/lib"))
      .isInstanceOf(IdeModuleLibrary::class.java)
  }

  @Test
  fun computeMavenAddress() {
    val library: Library = object : LibraryStub() {
      override fun getResolvedCoordinates(): MavenCoordinates {
        return MavenCoordinatesStub("com.android.tools", "test", "2.1", "aar")
      }
    }
    Truth.assertThat(computeAddress(library)).isEqualTo("com.android.tools:test:2.1@aar")
  }

  @Test
  fun computeMavenAddressWithModuleLibrary() {
    val library: Library = object : AndroidLibraryStub() {
      override fun getProject(): String? {
        return ":androidLib"
      }

      override fun getProjectVariant(): String? {
        return "release"
      }
    }
    Truth.assertThat(computeAddress(library)).isEqualTo(":androidLib::release")
  }

  @Test
  fun computeMavenAddressWithModuleLibraryWithBuildId() {
    val library: Library = object : AndroidLibraryStub() {
      override fun getProject(): String? {
        return ":androidLib"
      }

      override fun getBuildId(): String? {
        return "/project/root"
      }

      override fun getProjectVariant(): String? {
        return "release"
      }
    }
    Truth.assertThat(computeAddress(library)).isEqualTo("/project/root:androidLib::release")
  }

  @Test
  fun computeMavenAddressWithNestedModuleLibrary() {
    val library: Library = object : LibraryStub() {
      override fun getResolvedCoordinates(): MavenCoordinates {
        return MavenCoordinatesStub(
          "myGroup", ":androidLib:subModule", "undefined", "aar"
        )
      }
    }
    Truth.assertThat(computeAddress(library))
      .isEqualTo("myGroup:androidLib.subModule:undefined@aar")
  }

  @Test
  fun checkIsLocalAarModule() {
    val localAarLibrary: AndroidLibrary = object : AndroidLibraryStub() {
      override fun getProject(): String {
        return ":aarModule"
      }

      override fun getBundle(): File {
        return File("/ProjectRoot/aarModule/aarModule.aar")
      }
    }
    val moduleLibrary: AndroidLibrary = object : AndroidLibraryStub() {
      override fun getProject(): String {
        return ":androidLib"
      }

      override fun getBundle(): File {
        return File("/ProjectRoot/androidLib/build/androidLib.aar")
      }
    }
    val externalLibrary: AndroidLibrary = object : AndroidLibraryStub() {
      override fun getProject(): String? {
        return null
      }
    }
    val buildFolderPaths = BuildFolderPaths()
    buildFolderPaths.setRootBuildId("project")
    buildFolderPaths.addBuildFolderMapping(
      "project", ":aarModule", File("/ProjectRoot/aarModule/build/")
    )
    buildFolderPaths.addBuildFolderMapping(
      "project", ":androidLib", File("/ProjectRoot/androidLib/build/")
    )
    Assert.assertTrue(isLocalAarModule(localAarLibrary, buildFolderPaths))
    Assert.assertFalse(isLocalAarModule(moduleLibrary, buildFolderPaths))
    Assert.assertFalse(isLocalAarModule(externalLibrary, buildFolderPaths))
  }

  @Test
  fun checkIsLocalAarModuleWithCompositeBuild() {
    // simulate project structure:
    // project(root)     - aarModule
    // project(root)     - androidLib
    //      project1     - aarModule
    //      project1     - androidLib
    val localAarLibraryInRootProject: AndroidLibrary = object : AndroidLibraryStub() {
      override fun getProject(): String {
        return ":aarModule"
      }

      override fun getBundle(): File {
        return File("/Project/aarModule/aarModule.aar")
      }

      override fun getBuildId(): String? {
        return "Project"
      }
    }
    val localAarLibraryInProject1: AndroidLibrary = object : AndroidLibraryStub() {
      override fun getProject(): String {
        return ":aarModule"
      }

      override fun getBundle(): File {
        return File("/Project1/aarModule/aarModule.aar")
      }

      override fun getBuildId(): String? {
        return "Project1"
      }
    }
    val moduleLibraryInRootProject: AndroidLibrary = object : AndroidLibraryStub() {
      override fun getProject(): String {
        return ":androidLib"
      }

      override fun getBundle(): File {
        return File("/Project/androidLib/build/androidLib.aar")
      }

      override fun getBuildId(): String? {
        return "Project"
      }
    }
    val moduleLibraryInProject1: AndroidLibrary = object : AndroidLibraryStub() {
      override fun getProject(): String {
        return ":androidLib"
      }

      override fun getBundle(): File {
        return File("/Project1/androidLib/build/androidLib.aar")
      }

      override fun getBuildId(): String? {
        return "Project1"
      }
    }
    val externalLibrary: AndroidLibrary = object : AndroidLibraryStub() {
      override fun getProject(): String? {
        return null
      }
    }
    val buildFolderPaths = BuildFolderPaths()
    buildFolderPaths.setRootBuildId("Project")
    buildFolderPaths.addBuildFolderMapping(
      "Project", ":aarModule", File("/Project/aarModule/build/")
    )
    buildFolderPaths.addBuildFolderMapping(
      "Project", ":androidLib", File("/Project/androidLib/build/")
    )
    buildFolderPaths.addBuildFolderMapping(
      "Project1", ":aarModule", File("/Project1/aarModule/build/")
    )
    buildFolderPaths.addBuildFolderMapping(
      "Project1", ":androidLib", File("/Project1/androidLib/build/")
    )
    Assert.assertTrue(isLocalAarModule(localAarLibraryInRootProject, buildFolderPaths))
    Assert.assertTrue(isLocalAarModule(localAarLibraryInProject1, buildFolderPaths))
    Assert.assertFalse(isLocalAarModule(moduleLibraryInRootProject, buildFolderPaths))
    Assert.assertFalse(isLocalAarModule(moduleLibraryInProject1, buildFolderPaths))
    Assert.assertFalse(isLocalAarModule(externalLibrary, buildFolderPaths))
  }
}