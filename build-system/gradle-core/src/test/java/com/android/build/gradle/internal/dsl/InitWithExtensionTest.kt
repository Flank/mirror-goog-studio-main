/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.HasInitWith
import com.android.build.gradle.internal.services.createDslServices
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Test that build type and product flavor also init with any extension types. */
class InitWithExtensionTest {

    private val dslServices = createDslServices()

    /** An Example extension from a third-party plugin */
    interface MyExtension : HasInitWith<MyExtension> {

        var enableFoo: Boolean
    }

    abstract class MyExtensionImpl : MyExtension {

        override fun initWith(that: MyExtension) {
            enableFoo = that.enableFoo
        }
    }

    val ProductFlavor.my: MyExtension get() = extensions.getByType(MyExtension::class.java)

    @Test
    fun testInitWithExtension() {
        val orangeFlavor =
            dslServices.newDecoratedInstance(ProductFlavor::class.java, "orange", dslServices)
        val lemonFlavor =
            dslServices.newDecoratedInstance(ProductFlavor::class.java, "lemon", dslServices)
        for (buildType in listOf(orangeFlavor, lemonFlavor)) {
            buildType.extensions.create(MyExtension::class.java, "my", MyExtensionImpl::class.java)
        }
        orangeFlavor.my.enableFoo = true
        assertWithMessage("Orange flavor custom extension is configured by the build author")
            .that(orangeFlavor.my.enableFoo).named("orangeFlavor.my.enableBar").isTrue()
        assertWithMessage("Lemon flavor custom extension should be the default")
            .that(lemonFlavor.my.enableFoo).named("lemonFlavor.my.enableBar").isFalse()
        lemonFlavor.initWith(orangeFlavor)
        assertWithMessage("Orange flavor custom extension is configured by the build author")
            .that(orangeFlavor.my.enableFoo).named("orangeFlavor.my.enableBar").isTrue()
        assertWithMessage("Lemon flavor custom extension should be initialized by initWith")
            .that(lemonFlavor.my.enableFoo).named("lemonFlavor.my.enableBar").isTrue()
    }

    val BuildType.my: MyExtension get() = extensions.getByType(MyExtension::class.java)

    @Test
    fun testBuildTypeInitWithExtension() {
        val debugBuildType = dslServices.newDecoratedInstance(BuildType::class.java, "debug", dslServices)
        val qaBuildType = dslServices.newDecoratedInstance(BuildType::class.java, "qa", dslServices)
        for (buildType in listOf(debugBuildType, qaBuildType)) {
            buildType.extensions.create(MyExtension::class.java, "my", MyExtensionImpl::class.java)
        }
        debugBuildType.my.enableFoo = true
        assertWithMessage("debug build type custom extension is configured by the build author")
            .that(debugBuildType.my.enableFoo).named("buildTypes.debug.my.enableFoo").isTrue()
        assertWithMessage("qa build type custom extension should be the default")
            .that(qaBuildType.my.enableFoo).named("buildTypes.qa.my.enableFoo").isFalse()
        qaBuildType.initWith(debugBuildType)
        assertWithMessage("debug build type custom extension is configured by the build author")
            .that(debugBuildType.my.enableFoo).named("buildTypes.debug.my.enableFoo").isTrue()
        assertWithMessage("qa build type custom extension should be initialized by initWith")
            .that(qaBuildType.my.enableFoo).named("buildTypes.qa.my.enableFoo").isTrue()
    }


}
