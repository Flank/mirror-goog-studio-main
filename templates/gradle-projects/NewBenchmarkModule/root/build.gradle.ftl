<#import "root://activities/common/kotlin_macros.ftl" as kt>
apply plugin: 'com.android.library'
apply plugin: 'androidx.benchmark'
<@kt.addKotlinPlugins />

android {
    compileSdkVersion ${buildApi}
<#if explicitBuildToolsVersion!false>
    buildToolsVersion "${buildToolsVersion}"
</#if>

    defaultConfig {
        minSdkVersion ${minApi}
        targetSdkVersion ${targetApi}
        versionCode 1
        versionName "1.0"

        testInstrumentationRunner "${getMaterialComponentName('android.support.test.runner.AndroidJUnitRunner', true)}"
    }

    buildTypes {
        debug {
            // Since debuggable can't be modified by gradle for library modules,
            // it must be done in a manifest - see src/androidTest/AndroidManifest.xml
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'benchmark-proguard-rules.pro'
        }
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
