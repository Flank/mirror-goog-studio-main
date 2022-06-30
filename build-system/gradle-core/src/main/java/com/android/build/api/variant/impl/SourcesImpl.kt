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

package com.android.build.api.variant.impl

import com.android.build.api.component.impl.DefaultSourcesProvider
import com.android.build.api.variant.SourceDirectories
import com.android.build.api.variant.Sources
import com.android.build.gradle.api.AndroidSourceDirectorySet
import com.android.build.gradle.internal.api.DefaultAndroidSourceDirectorySet
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import com.android.build.gradle.internal.services.VariantServices
import org.gradle.api.file.Directory
import java.io.File

/**
 * Implementation of [Sources] for a particular source type like java, kotlin, etc...
 *
 * @param defaultSourceProvider function to provide initial content of the sources for a specific
 * [SourceType]. These are all the basic folders set for main. buildTypes and flavors including
 * those set through the DSL settings.
 * @param projectDirectory the project's folder as a [Directory]
 * @param variantServices the variant's [VariantServices]
 * @param variantSourceSet optional variant specific [DefaultAndroidSourceSet] if there is one, null
 * otherwise (if the application does not have product flavor, there won't be one).
 */
class SourcesImpl(
    private val defaultSourceProvider: DefaultSourcesProvider,
    private val projectDirectory: Directory,
    private val variantServices: VariantServices,
    private val variantSourceSet: DefaultAndroidSourceSet?,
): Sources {

    override val java: FlatSourceDirectoriesImpl =
        FlatSourceDirectoriesImpl(
            SourceType.JAVA.folder,
            variantServices,
            variantSourceSet?.java?.filter
        ).also { sourceDirectoriesImpl ->

            defaultSourceProvider.java.run {
                sourceDirectoriesImpl.addSources(this)
            }
            resetVariantSourceSet(sourceDirectoriesImpl, variantSourceSet?.java)
        }

    override val kotlin: FlatSourceDirectoriesImpl =
        FlatSourceDirectoriesImpl(
            SourceType.KOTLIN.folder,
            variantServices,
            null,
        ).also { sourceDirectoriesImpl ->

            defaultSourceProvider.kotlin.run {
                sourceDirectoriesImpl.addSources(this)
            }
            resetVariantSourceSet(
                sourceDirectoriesImpl,
                variantSourceSet?.kotlin as DefaultAndroidSourceDirectorySet?)
        }

    override val res: ResSourceDirectoriesImpl =
        ResSourceDirectoriesImpl(
            SourceType.RES.folder,
            variantServices,
            variantSourceSet?.res?.filter
        ).also { sourceDirectoriesImpl ->
            defaultSourceProvider.res.run {
                forEach {
                    sourceDirectoriesImpl.addSources(it)
                }
            }
            resetVariantSourceSet(sourceDirectoriesImpl, variantSourceSet?.res)
        }

    override val assets: AssetSourceDirectoriesImpl =
        AssetSourceDirectoriesImpl(
            SourceType.ASSETS.folder,
            variantServices,
            variantSourceSet?.assets?.filter
        ).also { sourceDirectoriesImpl ->

            defaultSourceProvider.assets.run {
                forEach {
                    sourceDirectoriesImpl.addSources(it)
                }
            }
            resetVariantSourceSet(sourceDirectoriesImpl, variantSourceSet?.assets)
        }

    override val jniLibs: AssetSourceDirectoriesImpl =
        AssetSourceDirectoriesImpl(
            SourceType.JNI_LIBS.folder,
            variantServices,
            variantSourceSet?.jniLibs?.filter
        ).also { sourceDirectoriesImpl ->

            defaultSourceProvider.jniLibs.run {
                forEach {
                    sourceDirectoriesImpl.addSources(it)
                }
            }
            resetVariantSourceSet(sourceDirectoriesImpl, variantSourceSet?.jniLibs)
        }

    override val shaders: AssetSourceDirectoriesImpl? =
        defaultSourceProvider.shaders?.let { listOfDirectoryEntries ->
            AssetSourceDirectoriesImpl(
                SourceType.SHADERS.folder,
                variantServices,
                variantSourceSet?.shaders?.filter
            ).also { sourceDirectoriesImpl ->

                listOfDirectoryEntries.run {
                    forEach {
                        sourceDirectoriesImpl.addSources(it)
                    }
                }
                resetVariantSourceSet(sourceDirectoriesImpl, variantSourceSet?.shaders)
            }
        }

    override val mlModels: AssetSourceDirectoriesImpl =
        AssetSourceDirectoriesImpl(
            SourceType.ML_MODELS.folder,
            variantServices,
            variantSourceSet?.mlModels?.filter
        ).also { sourceDirectoriesImpl ->
            defaultSourceProvider.mlModels.run {
                forEach {
                    sourceDirectoriesImpl.addSources(it)
                }
            }
            resetVariantSourceSet(sourceDirectoriesImpl, variantSourceSet?.mlModels)
        }

    override val aidl: SourceDirectories.Flat? by lazy {
        defaultSourceProvider.aidl?.let { defaultAidlDirectories ->
            FlatSourceDirectoriesImpl(
                SourceType.AIDL.folder,
                variantServices,
                variantSourceSet?.aidl?.filter
            ).also { sourceDirectoriesImpl ->
                sourceDirectoriesImpl.addSources(defaultAidlDirectories)
                resetVariantSourceSet(sourceDirectoriesImpl, variantSourceSet?.aidl)
            }
        }
    }

    override val renderscript: SourceDirectories.Flat? by lazy {
        defaultSourceProvider.renderscript?.let { defaultRenderscriptDirectories ->
            FlatSourceDirectoriesImpl(
                SourceType.RENDERSCRIPT.folder,
                variantServices,
                variantSourceSet?.renderscript?.filter
            ).also { sourceDirectoriesImpl ->
                sourceDirectoriesImpl.addSources(defaultRenderscriptDirectories)
                resetVariantSourceSet(sourceDirectoriesImpl, variantSourceSet?.renderscript)
            }
        }
    }

    internal val extras: NamedDomainObjectContainer<FlatSourceDirectoriesImpl> by lazy {
        variantServices.domainObjectContainer(
            FlatSourceDirectoriesImpl::class.java,
            SourceProviderFactory(
                variantServices,
                projectDirectory,
            ),
        )
    }

    override fun getByName(name: String): SourceDirectories.Flat = extras.maybeCreate(name)

    class SourceProviderFactory(
        private val variantServices: VariantServices,
        private val projectDirectory: Directory,
    ): NamedDomainObjectFactory<FlatSourceDirectoriesImpl> {

        override fun create(name: String): FlatSourceDirectoriesImpl =
            FlatSourceDirectoriesImpl(
                _name = name,
                variantServices = variantServices,
                variantDslFilters = null
            )
    }

    /**
     * reset the original variant specific source set in
     * [com.android.build.gradle.internal.core.VariantSources] since the variant
     * specific folders are owned by this abstraction (so users can add it if needed).
     * TODO, make the VariantSources unavailable to other components in
     * AGP as they should all use this [SourcesImpl] from now on.
     */
    private fun resetVariantSourceSet(
        target: SourceDirectoriesImpl,
        sourceSet: AndroidSourceDirectorySet?,
    ) {
        if (sourceSet != null) {
            for (srcDir in sourceSet.srcDirs) {
                target.addSource(
                    FileBasedDirectoryEntryImpl(
                        name = "variant",
                        directory = srcDir,
                        filter = sourceSet.filter,
                        // since it was part of the original set of sources for the module, we
                        // must add it back to the model as it is expecting to have variant sources.
                        shouldBeAddedToIdeModel = true
                    )
                )
            }
            sourceSet.setSrcDirs(emptyList<File>())
        }
    }
}
