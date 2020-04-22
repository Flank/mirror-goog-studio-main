package com.android.builder.compiling

import java.nio.file.Path

class BuildConfigData private constructor(
        val outputPath: Path,
        val buildConfigPackageName: String,
        val buildConfigName: String,
        val buildConfigFields: MutableList<BuildConfigField>
) {

    class Builder(
            private var outputPath: Path? = null,
            private var buildConfigPackageName: String? = null,
            private var buildConfigName: String = "BuildConfig",
            private val buildConfigFields: MutableList<BuildConfigField> = mutableListOf()
    ) {

        fun setOutputPath(outputPath: Path) =
                apply { this.outputPath = outputPath }

        fun setBuildConfigPackageName(buildConfigPackageName: String) =
                apply { this.buildConfigPackageName = buildConfigPackageName }

        fun setBuildConfigName(buildConfigName: String) =
                apply { this.buildConfigName = buildConfigName }

        @JvmOverloads
        fun addStringField(name: String, value: String, comment: String? = null) = apply {
            buildConfigFields += BuildConfigField("String", name, value, comment)
        }

        @JvmOverloads
        fun addIntField(name: String, value: Int, comment: String? = null) = apply {
            buildConfigFields += BuildConfigField("int", name, value, comment)
        }

        @JvmOverloads
        fun addBooleanDebugField(name: String, value: String, comment: String? = null) = apply {
            buildConfigFields += BuildConfigField("boolean", name, value, comment)
        }

        @JvmOverloads
        fun addBooleanField(name: String, value: Boolean, comment: String? = null) = apply {
            buildConfigFields += BuildConfigField("boolean", name, value, comment)
        }

        @JvmOverloads
        fun addItem(type: String, name: String, value: Any, comment: String? = null) = apply {
            buildConfigFields += BuildConfigField(type, name, value, comment)
        }

        fun build() = BuildConfigData(
                outputPath ?: error("outputPath is required."),
                buildConfigPackageName ?: error("buildConfigPackageName is required."),
                buildConfigName,
                buildConfigFields
        )
    }
}