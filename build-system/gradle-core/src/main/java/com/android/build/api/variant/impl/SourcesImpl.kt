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

    override val java: SourceDirectoriesImpl =
        SourceDirectoriesImpl(
            SourceType.JAVA.name,
            projectDirectory,
            variantServices,
            variantSourceSet?.java?.filter
        ).also { sourceDirectoriesImpl ->

            defaultSourceProvider.java.run {
                sourceDirectoriesImpl.addSources(this)
            }
            resetVariantSourceSet(sourceDirectoriesImpl, variantSourceSet?.java)
        }

    override val res: SourceAndOverlayDirectoriesImpl =
        SourceAndOverlayDirectoriesImpl(
            SourceType.RES.name,
            projectDirectory,
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

    internal val extras: NamedDomainObjectContainer<SourceDirectoriesImpl> by lazy {
        variantServices.domainObjectContainer(
            SourceDirectoriesImpl::class.java,
            SourceProviderFactory(
                variantServices,
                projectDirectory,
            ),
        )
    }

    override fun getByName(name: String): SourceDirectories = extras.maybeCreate(name)

    class SourceProviderFactory(
        private val variantServices: VariantServices,
        private val projectDirectory: Directory,
    ): NamedDomainObjectFactory<SourceDirectoriesImpl> {

        override fun create(name: String): SourceDirectoriesImpl =
            SourceDirectoriesImpl(
                _name = name,
                projectDirectory = projectDirectory,
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
        target: AbstractSourceDirectoriesImpl,
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
