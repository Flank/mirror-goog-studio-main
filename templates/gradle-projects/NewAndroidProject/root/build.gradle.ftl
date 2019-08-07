// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    <#if generateKotlin>ext.kotlin_version = '${kotlinVersion}'</#if>
    repositories {
        google()
        jcenter()
        <#if includeKotlinEapRepo!false>maven { url '${kotlinEapRepoUrl}' }</#if>
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:${gradlePluginVersion}'
        <#if generateKotlin>classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"</#if>

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

allprojects {
    repositories {
        google()
        jcenter()
        <#if includeKotlinEapRepo!false>maven { url '${kotlinEapRepoUrl}' }</#if>
    }
}

task clean(type: Delete) {
    delete rootProject.buildDir
}
