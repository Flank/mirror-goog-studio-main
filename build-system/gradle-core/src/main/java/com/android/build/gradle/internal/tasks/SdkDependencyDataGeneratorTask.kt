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

package com.android.build.gradle.internal.tasks

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.KeymaestroHybridEncrypter
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.tasks.OutputFile
import com.google.common.io.BaseEncoding
import java.io.FileOutputStream
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.ByteArrayOutputStream
import java.nio.file.Files;
import java.util.zip.DeflaterOutputStream
import kotlin.random.Random

/**
 * Task that generates SDK dependency block value for APKs.
 *
 * SDK dependency block is a block in APK signature v2 block that stores SDK dependency information
 * of the APK.
 */
abstract class SdkDependencyDataGeneratorTask : NonIncrementalTask() {

  private val publicKeyBase64: String = "CczY1Hsw0oqS5QUTM/s4A1xroCyjpqZAnFFOXGgQuu1WIz27yGSS+Jh75N8bMXyog6Deaq0W7P9O99Tp/IjSeA1qsds="

  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val dependencies: RegularFileProperty

  @get:OutputFile
  abstract val sdkDependencyData: RegularFileProperty

  public override fun doTaskAction() {
    FileOutputStream(sdkDependencyData.get().asFile).use {
      it.write(encrypt(addSalt(compress(Files.readAllBytes(dependencies.get().asFile.toPath())))))
    }
  }

  private fun addSalt(data: ByteArray): ByteArray {
    val salt = ByteArray(128)
    Random.Default.nextBytes(salt)
    return data + salt
  }

  private fun compress(data: ByteArray): ByteArray {
    val outputStream = ByteArrayOutputStream();
    DeflaterOutputStream(outputStream).use {
      it.write(data);
    }
    return outputStream.toByteArray()
  }

  private fun encrypt(data: ByteArray): ByteArray {
    return KeymaestroHybridEncrypter(BaseEncoding.base64().decode(publicKeyBase64)).encrypt(data);
  }

  class CreationAction(
    componentProperties: ComponentPropertiesImpl
  ) : VariantTaskCreationAction<SdkDependencyDataGeneratorTask, ComponentPropertiesImpl>(
      componentProperties
  ) {
    override val name: String = computeTaskName("sdk", "DependencyData")
    override val type: Class<SdkDependencyDataGeneratorTask> = SdkDependencyDataGeneratorTask::class.java

    override fun handleProvider(
        taskProvider: TaskProvider<out SdkDependencyDataGeneratorTask>
    ) {
      super.handleProvider(taskProvider)
      component
        .artifacts
        .producesFile(
          InternalArtifactType.SDK_DEPENDENCY_DATA,
          taskProvider,
          SdkDependencyDataGeneratorTask::sdkDependencyData,
          fileName = "sdkDependencyData.pb"
        )
    }

    override fun configure(
      task: SdkDependencyDataGeneratorTask
    ) {
      super.configure(task)
      component.artifacts.setTaskInputToFinalProduct(
          InternalArtifactType.METADATA_LIBRARY_DEPENDENCIES_REPORT, task.dependencies)
    }
  }
}