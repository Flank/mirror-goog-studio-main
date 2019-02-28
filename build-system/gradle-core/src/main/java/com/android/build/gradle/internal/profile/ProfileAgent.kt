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

package com.android.build.gradle.internal.profile

import java.lang.management.ManagementFactory
import java.util.logging.Logger
import javax.management.MBeanServerInvocationHandler
import javax.management.ObjectName
import javax.management.StandardMBean

/**
 * Singleton agent responsible for registering all profiling related MBeans in the MBean server
 * as well as providing convenience methods to access them.
 */
object ProfileAgent {

    private val mbs = ManagementFactory.getPlatformMBeanServer()
    private val objectName = ObjectName("domain:type=Gradle.agp,name=profiling")
    private val logger = Logger.getLogger(ProfileAgent::class.qualifiedName)

    @Synchronized
    fun register(buildListener: RecordingBuildListener) {
        try {
            val mbeans = mbs.queryMBeans(objectName, null)
            if (mbeans.isEmpty()) {
                val bean = ProfileMBeanImpl(buildListener)
                val mbean = StandardMBean(bean, ProfileMBean::class.java, false)
                mbs.registerMBean(mbean, objectName)
            }
        } catch (t: Throwable) {
            logger.warning("Profiling not available : $t")
        }
    }

    val getProfileMBean: ProfileMBean by lazy {
        MBeanServerInvocationHandler.newProxyInstance(
            mbs,
            objectName,
            ProfileMBean::class.java,
            false
        )
    }

    @Synchronized
    fun unregister() {
        if (mbs.isRegistered(objectName)) {
            mbs.unregisterMBean(objectName)
        }
    }
}
