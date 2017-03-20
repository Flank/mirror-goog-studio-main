// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
<#if isInstantApp>
    def instantAppProperties = new Properties()
    instantAppProperties.load(new FileInputStream('aia.properties'))
    project.ext.instantAppSdkDir = file(instantAppProperties['aia.repo'])
</#if>

    repositories {
<#if isInstantApp>
        maven {
            url(project.instantAppSdkDir.absolutePath)
        }
</#if>
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:${gradlePluginVersion}'

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

allprojects {
    repositories {
<#if isInstantApp>
        maven {
            url(project.instantAppSdkDir.absolutePath)
        }
</#if>
        jcenter()
    }
}

task clean(type: Delete) {
    delete rootProject.buildDir
}
