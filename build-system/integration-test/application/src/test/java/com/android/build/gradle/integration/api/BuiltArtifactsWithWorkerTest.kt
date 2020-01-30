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

package com.android.build.gradle.integration.api

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class BuiltArtifactsWithWorkerTest {
    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Test
    fun buildApp() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
apply from: "../commonHeader.gradle"
buildscript { apply from: "../commonBuildScript.gradle" }

apply from: "../commonLocalRepo.gradle"

apply plugin: 'com.android.application'

android {
    defaultConfig.minSdkVersion 14
    compileSdkVersion 29
    lintOptions.checkReleaseBuilds = false
    defaultConfig {
        minSdkVersion rootProject.supportLibMinSdk
        testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'
    }
}

android {
  splits {
    density {
      enable true
      exclude "ldpi", "tvdpi", "xxxhdpi", "400dpi", "560dpi"
      compatibleScreens 'small', 'normal', 'large', 'xlarge'
    }
  }
}

import javax.inject.Inject
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.api.artifact.WorkItemParameters

import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.artifact.ArtifactTransformationRequest

abstract class ProducerTask extends DefaultTask {

    @Input
    abstract Property<String> getVariantName()

    @OutputDirectory
    abstract DirectoryProperty getOutputDir()

    @TaskAction
    void taskAction() {
      File outputFolder = getOutputDir().get().getAsFile()
      outputFolder.mkdirs()

      new BuiltArtifactsImpl(
        BuiltArtifacts.METADATA_FILE_VERSION,
        InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST.INSTANCE,
        "com.android.test",
        getVariantName().get(),
        [
          createBuiltArtifact(outputFolder, "xhdpi"),
          createBuiltArtifact(outputFolder, "xxhdpi"),
          createBuiltArtifact(outputFolder, "xxxhdpi")
        ]

      ).save(getOutputDir().get())
    }

    BuiltArtifactImpl createBuiltArtifact(File outputFolder, String identifier) {
      File outputFile = new File(outputFolder, identifier)
      FileWriter writer = new FileWriter(outputFile)
      writer.write("task " + getName() + " was here !")
      writer.close()
      return new BuiltArtifactImpl(
        outputFile.getAbsolutePath(),
        new HashMap<String, String>(),
        123,
        "123",
        true,
        VariantOutputConfiguration.OutputType.ONE_OF_MANY,
        [
          new FilterConfiguration(FilterConfiguration.FilterType.DENSITY, identifier)
        ],
        identifier,
        identifier
      )
    }
}


abstract class ConsumerTask extends DefaultTask {
    private final WorkerExecutor workerExecutor

    @InputFiles
    abstract DirectoryProperty getCompatibleManifests()

    @OutputDirectory
    abstract DirectoryProperty getOutputDir()

    @Inject
    public ConsumerTask(WorkerExecutor workerExecutor) {
      this.workerExecutor = workerExecutor
    }

    @org.gradle.api.tasks.Internal
    ArtifactTransformationRequest replacementRequest

    static class MyWorkItemParameters extends WorkItemParameters implements Serializable {
      File outputFile

      File getOutputFile() {
       return outputFile
      }

      File initProperties(BuiltArtifact builtArtifact, Directory outputLocation) {
        this.outputFile = outputLocation.file(new File(builtArtifact.outputFile).name).getAsFile()
        return this.outputFile
      }
    }

    abstract static class WorkItem extends WorkAction<MyWorkItemParameters> {
      MyWorkItemParameters myParameters

      @Inject
      WorkItem(MyWorkItemParameters parameters) {
         myParameters = parameters;
      }

      void execute() {
         FileWriter writer = new FileWriter(myParameters.getOutputFile())
         writer.write("task " + getName() + " was here !")
         writer.close()
      }
    }

    @TaskAction
    void taskAction() {
      replacementRequest.submit(
                workerExecutor.noIsolation(),
                MyWorkItemParameters.class,
                WorkItem.class
            ) { }
    }
}

abstract class VerifierTask extends DefaultTask {

    @InputFiles
    abstract DirectoryProperty getInputDir()

    @TaskAction
    void taskAction() {

      BuiltArtifacts  transformed = BuiltArtifacts.getLoader().load(getInputDir().get())
      assert transformed != null
      assert transformed.elements.size == 3
      transformed.elements.each { builtArtifact ->
        assert new File(builtArtifact.getOutputFile()).getName().toString().equals(builtArtifact.getFilters().first().identifier)
      }
      System.out.println("Verification finished successfully")
    }
}

File currentDir = new File(System.getProperty("user.dir"))

android.onVariantProperties {
  TaskProvider outputTask = tasks.register(it.getName() + 'ProducerTask', ProducerTask) { task ->
    task.getVariantName().set(it.getName())
  }

  it.operations.replace(outputTask) { it.getOutputDir() }.on(
    InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST.INSTANCE)

  TaskProvider consumerTask = tasks.register(it.getName() + 'ConsumerTask', ConsumerTask)
  ArtifactTransformationRequest replacementRequest = it.operations.use(consumerTask)
    .toRead(InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST.INSTANCE) { it.getCompatibleManifests() }
    .andWrite(InternalArtifactType.MERGED_MANIFESTS.INSTANCE) { it.getOutputDir() }

  consumerTask.configure { task ->
    task.replacementRequest = replacementRequest
  }

  tasks.register(it.getName() + 'Verifier', VerifierTask) { task ->
    task.getInputDir().set(
      it.operations.get(InternalArtifactType.MERGED_MANIFESTS.INSTANCE)
    )
  }
}
        """.trimIndent()
        )
        val result = project.executor().run("clean", "debugVerifier")
        Truth.assertThat(result.didWorkTasks).containsExactly(
            ":debugProducerTask", ":debugConsumerTask", ":debugVerifier")
    }
}