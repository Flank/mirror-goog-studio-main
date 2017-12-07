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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.Serializable
import java.lang.reflect.Constructor

abstract class TaskDelegateWithWorker(projectOptions: ProjectOptions) {

    private val useExecutor: Boolean = projectOptions.get(BooleanOption.ENABLE_GRADLE_WORKERS)

    init {
        if (!useExecutor) {
            constructorCache = mutableMapOf()
        }
    }

    /**
     * Returns the class of the worker action runnable. Must extend [Runnable]
     */
    abstract fun getWorkerClass(): Class<out Runnable>

    /**
     * Returns the single parameters that is passed to the worker runnable constructor.
     * Must be [Serializable]
     */
    abstract fun getWorkerParam(): Serializable

    fun run(executor: WorkerExecutor) {
        if (useExecutor) {
            executor.submit(getWorkerClass()) {
                it.params(getWorkerParam())
                it.isolationMode = IsolationMode.NONE
            }
        } else {
            val workerParam = getWorkerParam()

            val constructor = constructorCache.computeIfAbsent(getWorkerClass(), { theClass ->
                findConstructor(theClass, workerParam.javaClass)
            })

            val runnable = constructor.newInstance(workerParam)
            runnable.run()
        }
    }

    private fun findConstructor(theClass: Class<out Runnable>, paramClass: Class<*>): Constructor<out Runnable> {
        @Suppress("UNCHECKED_CAST")
        val constructors: Array<out Constructor<Runnable>>
                = theClass.declaredConstructors as Array<out Constructor<Runnable>>

        for (constructor in constructors) {
            if (checkCompatibility(paramClass, constructor.parameterTypes)) {
                return constructor
            }
        }

        throw RuntimeException(
                "Unable to find constructor for ${theClass.canonicalName} with single parameter" +
                        " of type ${paramClass.canonicalName}")
    }

    companion object {
        lateinit var constructorCache: MutableMap<Class<out Runnable>, Constructor<out Runnable>>
    }
}

private fun checkCompatibility(argumentType: Class<*>, parameterTypes: Array<Class<*>?>): Boolean {
    if (parameterTypes.size != 1) {
        return false
    }

    val parameterType: Class<*> = parameterTypes[0] ?: return false

    return parameterType.isAssignableFrom(argumentType)
}
