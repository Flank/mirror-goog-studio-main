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

import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.assertNotNull

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
    compileSdkVersion 30
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
import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.internal.services.BuildServicesKt
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.BuiltArtifactsLoader
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.gradle.internal.tasks.BaseTask
import com.android.build.gradle.internal.workeractions.WorkActionAdapter
import com.android.build.gradle.internal.workeractions.DecoratedWorkParameters

import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.FilterConfigurationImpl
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl
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
        SingleArtifact.APK.INSTANCE,
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
        123,
        "123",
        new VariantOutputConfigurationImpl(false,
          [
            new FilterConfigurationImpl(FilterConfiguration.FilterType.DENSITY, identifier)
          ]),
        Collections.emptyMap()
      )
    }
}

interface MyWorkItemParameters extends DecoratedWorkParameters {
    abstract RegularFileProperty getOutputFile()
}


abstract class ConsumerTask extends BaseTask {
    private final WorkerExecutor workerExecutor

    @InputFiles
    abstract DirectoryProperty getInputDir()

    @OutputDirectory
    abstract DirectoryProperty getOutputDir()

    @Inject
    public ConsumerTask(
      WorkerExecutor workerExecutor) {
      this.workerExecutor = workerExecutor
    }

    @org.gradle.api.tasks.Internal
    ArtifactTransformationRequest replacementRequest

    abstract static class WorkItem implements WorkActionAdapter<MyWorkItemParameters> {
      MyWorkItemParameters myParameters

      @Inject
      WorkItem(MyWorkItemParameters parameters) {
         myParameters = parameters;
      }

      @Override
      MyWorkItemParameters getParameters() {
        return myParameters;
      }

      void doExecute() {
         FileWriter writer = new FileWriter(myParameters.getOutputFile().get().getAsFile())
         writer.write("task " + getName() + " was here !")
         writer.close()
      }
    }

    @TaskAction
    void taskAction() {

      replacementRequest.submit(
                this,
                workerExecutor.noIsolation(),
                WorkItem.class
            ) { BuiltArtifact builtArtifact, Directory outputLocation, MyWorkItemParameters parameters ->
            parameters.outputFile.set(outputLocation.file(new File(builtArtifact.outputFile).name).getAsFile())
            return parameters.outputFile.get().getAsFile()
            }
    }
}

abstract class VerifierTask extends DefaultTask {

    @InputFiles
    abstract DirectoryProperty getInputDir()

    @Internal
    abstract Property<BuiltArtifactsLoader> getArtifactsLoader()

    @TaskAction
    void taskAction() {

      BuiltArtifacts  transformed = getArtifactsLoader().get().load(getInputDir().get())
      assert transformed != null
      assert transformed.elements.size == 3
      transformed.elements.each { builtArtifact ->
        assert new File(builtArtifact.getOutputFile()).getName().toString().equals(builtArtifact.getFilters().first().identifier)
      }
      System.out.println("Verification finished successfully")
    }
}

androidComponents.onVariants(androidComponents.selector().all(), {
  TaskProvider outputTask = tasks.register(it.getName() + 'ProducerTask', ProducerTask) { task ->
    task.getVariantName().set(it.getName())
  }

  it.artifacts.use(outputTask)
        .wiredWith({ it.getOutputDir() })
        .toCreate(SingleArtifact.APK.INSTANCE)

  TaskProvider consumerTask = tasks.register(it.getName() + 'ConsumerTask', ConsumerTask)
  ArtifactTransformationRequest replacementRequest = it.artifacts.use(consumerTask)
    .wiredWithDirectories(
        { it.getInputDir() },
        { it.getOutputDir() }
    )
    .toTransformMany(SingleArtifact.APK.INSTANCE)

  consumerTask.configure { task ->
    task.replacementRequest = replacementRequest
    task.getOutputDir().set(new File(project.layout.buildDir.getAsFile().get(), "acme_apks"))
    task.analyticsService.set(BuildServicesKt.getBuildService(task.project.gradle.sharedServices, AnalyticsService.class))
  }

  tasks.register(it.getName() + 'Verifier', VerifierTask) { task ->
    task.getInputDir().set(
      it.artifacts.get(SingleArtifact.APK.INSTANCE)
    )
    task.getArtifactsLoader().set(it.artifacts.getBuiltArtifactsLoader())
  }
})
        """.trimIndent()
        )
        val model = project.executeAndReturnModel("clean")
        val debugVariant = model.onlyModel.variants.filter { it.name == "debug" }.single()
        val assembleTaskOutputListingFile = debugVariant.mainArtifact.assembleTaskOutputListingFile
        // assert that the listing file location produced by the new task has been recorded in the
        // model.
        Truth.assertThat(assembleTaskOutputListingFile).contains("acme_apks")

        val result = project.executor().run("debugVerifier")
        Truth.assertThat(result.didWorkTasks).containsExactly(
            ":debugProducerTask", ":debugConsumerTask", ":debugVerifier")

        // and check the listing file content.
        val listingFile = File(assembleTaskOutputListingFile)
        val updatedApks =
                BuiltArtifactsLoaderImpl.loadFromFile(listingFile, listingFile.parentFile.toPath())
        assertNotNull(updatedApks)
        Truth.assertThat(updatedApks.elements).hasSize(3)
        updatedApks.elements.forEach { builtArtifact ->
            Truth.assertThat(builtArtifact.outputFile).contains("acme_apks")
        }
    }
}
