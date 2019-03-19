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

package com.android.ide.common.workers

import java.lang.management.ManagementFactory
import javax.management.MBeanServerInvocationHandler
import javax.management.ObjectName

/**
 * Singleton object for [ProfileMBean] related services.
 */
object ProfileMBeans {

    const val rootObjectName = "domain:type=Gradle.agp,name=profiling"
    private val mbs = ManagementFactory.getPlatformMBeanServer()

    /**
     * Return the [ProfileMBean] for this project. Can be null in unit tests or if the
     * profiling has been disabled.
     *
     * @param projectName the project name.
     * @return the project's [ProfileMBean] or null
     */
    fun getProfileMBean(projectName: String): ProfileMBean? {
        val name = composeObjectName(projectName)
        return if (!mbs.queryMBeans(name, null).isEmpty()) {
            MBeanServerInvocationHandler.newProxyInstance(
                mbs,
                composeObjectName(projectName),
                ProfileMBean::class.java,
                false
            )
        } else null
    }

    /**
     * Compose the [ObjectName] under which the [ProfileMBean] will be registered in the
     * [javax.management.MBeanServer]
     *
     * @param projectName the project name.
     */
    fun composeObjectName(projectName: String):ObjectName {
        val name= "$rootObjectName,project=$projectName"
        return try {
            ObjectName(name)
        } catch (t: Throwable) {
            ObjectName(rootObjectName)
        }

    }
}