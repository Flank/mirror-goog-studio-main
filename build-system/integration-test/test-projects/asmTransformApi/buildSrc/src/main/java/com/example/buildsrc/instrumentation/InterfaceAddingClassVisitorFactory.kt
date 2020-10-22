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

package com.example.buildsrc.instrumentation

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.objectweb.asm.ClassVisitor

abstract class InterfaceAddingClassVisitorFactory :
    AsmClassVisitorFactory<InterfaceAddingClassVisitorFactory.Params> {

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        return InterfaceAddingClassVisitor(
            parameters.get().interfaceInternalName.get(),
            instrumentationContext.apiVersion.get(),
            nextClassVisitor
        )
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        return parameters.get().enabled.get() && parameters.get().classesToInstrument.get()
            .contains(classData.className)
    }

    interface Params : InstrumentationParameters {
        @get:Input
        val classesToInstrument: ListProperty<String>

        @get:Input
        val interfaceInternalName: Property<String>

        @get:Input
        val enabled: Property<Boolean>
    }
}
