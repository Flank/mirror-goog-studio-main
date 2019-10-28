<#import "root://activities/common/kotlin_macros.ftl" as kt>
apply plugin: 'com.android.library'
apply plugin: 'androidx.benchmark'
<@kt.addKotlinPlugins />

android {
    compileSdkVersion <#if buildApiString?matches("^\\d+$")>${buildApiString}<#else>'${buildApiString}'</#if>
<#if explicitBuildToolsVersion!false>
    buildToolsVersion "${buildToolsVersion}"
</#if>

    compileOptions {
        sourceCompatibility = 1.8
        targetCompatibility = 1.8
    }

<#if (language!'Java')?string == 'Kotlin'>
     kotlinOptions {
        jvmTarget = "1.8"
     }

</#if>
    defaultConfig {
        minSdkVersion ${minApi}
        targetSdkVersion <#if targetApiString?matches("^\\d+$")>${targetApiString}<#else>'${targetApiString}'</#if>
        versionCode 1
        versionName "1.0"

        testInstrumentationRunner 'androidx.benchmark.junit4.AndroidBenchmarkRunner'
    }

<#if compareVersionsIgnoringQualifiers(gradlePluginVersion, '3.6.0') gte 0>
    testBuildType = "release"

</#if>
    buildTypes {
        debug {
            // Since debuggable can't be modified by gradle for library modules,
            // it must be done in a manifest - see src/androidTest/AndroidManifest.xml
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'benchmark-proguard-rules.pro'
        }
<#if compareVersionsIgnoringQualifiers(gradlePluginVersion, '3.6.0') gte 0>

        release {
            isDefault = true
        }
</#if>
    }
}

dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar'])

    // Add your dependencies here. Note that you cannot benchmark code
    // in an app module this way - you will need to move any code you
    // want to benchmark to a library module:
    // https://developer.android.com/studio/projects/android-library#Convert

<#if generateKotlin>
    <@kt.addKotlinDependencies />

</#if>
}
