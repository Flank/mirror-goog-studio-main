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

package com.android.build.gradle.internal.cxx.model

import com.android.build.api.component.impl.ComponentImpl
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.dsl.CmakeOptions
import com.android.build.gradle.internal.dsl.ExternalNativeBuild
import com.android.build.gradle.internal.dsl.NdkBuildOptions
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.utils.FileUtils.join
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import java.io.File

/**
 * The purpose of this mock is to check for laziness during construction
 * of ABI, Variant, and Module models. It throws an exception if any Gradle
 * API function is called except for a strictly defined set that is needed
 * to determine whether this build.gradle contains a C/C++ build.
 */
class EmptyGlobalMock {
    private val throwUnmocked = RuntimeExceptionAnswer()
    val global: GlobalScope = Mockito.mock(
        GlobalScope::class.java,
        throwUnmocked
    )

    val componentImpl: ComponentImpl = Mockito.mock(
        ComponentImpl::class.java,
        throwUnmocked
    )

//    val variantScope: VariantScope = Mockito.mock(
//        VariantScope::class.java,
//        throwUnmocked
//    )
//    val baseVariantData: BaseVariantData = Mockito.mock(
//        BaseVariantData::class.java,
//        throwUnmocked
//    )
    val extension: BaseExtension = Mockito.mock(
        BaseExtension::class.java,
        throwUnmocked
    )
    val externalNativeBuild: ExternalNativeBuild = Mockito.mock(
        ExternalNativeBuild::class.java,
        throwUnmocked
    )
    val cmake: CmakeOptions = Mockito.mock(
        CmakeOptions::class.java,
        throwUnmocked
    )
    val ndkBuild: NdkBuildOptions = Mockito.mock(
        NdkBuildOptions::class.java,
        throwUnmocked
    )
    init {
        // DON'T ADD NEW STUFF HERE JUST TO GET THE TEST TO PASS. See class comment.
        Mockito.doReturn(extension).`when`(global).extension
        Mockito.doReturn(externalNativeBuild).`when`(extension).externalNativeBuild
        Mockito.doReturn(cmake).`when`(externalNativeBuild).cmake
        Mockito.doReturn(ndkBuild).`when`(externalNativeBuild).ndkBuild
        Mockito.doReturn(join(File("path"), "to", "CMakeLists.txt")).`when`(cmake).path
        Mockito.doReturn(null).`when`(ndkBuild).path
        Mockito.doReturn(null).`when`(cmake).buildStagingDirectory
        Mockito.doReturn(null).`when`(ndkBuild).buildStagingDirectory
        // DON'T ADD NEW STUFF HERE JUST TO GET THE TEST TO PASS. See class comment.
    }

    class RuntimeExceptionAnswer : Answer<Any> {
        override fun answer(invocation: InvocationOnMock): Any {
            throw RuntimeException(invocation.method.toGenericString() + " is not stubbed")
        }
    }
}
