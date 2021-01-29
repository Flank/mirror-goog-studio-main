/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.ide.common.gradle.model

import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import java.io.File

interface IdeVariantHeader {
  val name: String
  val buildType: String
  val productFlavors: List<String>
  val displayName: String
}

interface IdeVariant: IdeVariantHeader {
  val mainArtifact: IdeAndroidArtifact
  val androidTestArtifact: IdeAndroidArtifact?
  val unitTestArtifact: IdeJavaArtifact?

  /**
   * The result of the merge of all the flavors and of the main default config. If no flavors
   * are defined then this is the same as the default config.
   */
  val mergedFlavor: IdeProductFlavor

  /** The minSdkVersion, or null if not specified. This is only the value set on this product flavor. */
  val minSdkVersion: IdeApiVersion?

  /** The targetSdkVersion, or null if not specified. This is only the value set on this product flavor. */
  val targetSdkVersion: IdeApiVersion?

  /** The maxSdkVersion, or null if not specified. This is only the value set on this produce flavor. */
  val maxSdkVersion: Int?

  val instantAppCompatible: Boolean

  /**
   * The resource configuration for this variant.
   *
   * This is the list of -c parameters for aapt.
   */
  val resourceConfigurations: Collection<String>

  /**
   * Map of generated res values where the key is the res name.
   */
  val resValues: Map<String, IdeClassField>

  /**
   * Specifies the ProGuard configuration files that the plugin should use.
   */
  val proguardFiles: Collection<File>

  /** The collection of proguard rule files for consumers of the library to use. */
  val consumerProguardFiles: Collection<File>

  /**
   * The map of key value pairs for placeholder substitution in the android manifest file.
   *
   * This map will be used by the manifest merger.
   */
  val manifestPlaceholders: Map<String, String>

  /**
   * The test application id. This is only the value set on this product flavor.
   * To get the final value, use [Variant.getExtraAndroidArtifacts] with
   * [AndroidProject.ARTIFACT_ANDROID_TEST] and then
   * [AndroidArtifact.getApplicationId]
   */
  val testApplicationId: String?

  /**
   * The test instrumentation runner. This is only the value set on this product flavor.
   * TODO: make test instrumentation runner available through the model.
   */
  val testInstrumentationRunner: String?

  /** The arguments for the test instrumentation runner.*/
  val testInstrumentationRunnerArguments: Map<String, String>

  val testedTargetVariants: List<IdeTestedTargetVariant>
}
