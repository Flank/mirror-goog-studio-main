// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    <#if includeKotlinSupport!false>ext.kotlin_version = '1.1.2-2'</#if>
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:${gradlePluginVersion}'
        <#if includeKotlinSupport!false>classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"</#if>

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

allprojects {
    repositories {
        jcenter()
<#if includeKotlinSupport!false>
        mavenCentral()
</#if>
    }
}

task clean(type: Delete) {
    delete rootProject.buildDir
}
