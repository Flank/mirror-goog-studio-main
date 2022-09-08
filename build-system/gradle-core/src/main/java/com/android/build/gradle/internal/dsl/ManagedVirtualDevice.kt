package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.ManagedVirtualDevice
import java.io.Serializable
import javax.inject.Inject
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

open class ManagedVirtualDevice @Inject constructor(private val name: String) :
    ManagedVirtualDevice {

    override fun getName(): String = name

    @get: Input
    override var device = ""

    @get: Input
    override var apiLevel = -1

    @get: Input
    override var systemImageSource = "google"

    @get: Input
    override var require64Bit = false
}
