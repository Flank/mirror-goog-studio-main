<#import "root://activities/common/kotlin_macros.ftl" as kt>
<#import "root://gradle-projects/common/proguard_macros.ftl" as proguard>
<#if isLibraryProject?? && isLibraryProject>
apply plugin: 'com.android.library'
<#else>
apply plugin: 'com.android.application'
</#if>
<@kt.addKotlinPlugins />

android {
    compileSdkVersion <#if buildApiString?matches("^\\d+$")>${buildApiString}<#else>'${buildApiString}'</#if>
    <#if explicitBuildToolsVersion!false>buildToolsVersion "${buildToolsVersion}"</#if>

    defaultConfig {
        applicationId "${packageName}"
        minSdkVersion <#if minApi?matches("^\\d+$")>${minApi}<#else>'${minApi}'</#if>
        targetSdkVersion <#if targetApiString?matches("^\\d+$")>${targetApiString}<#else>'${targetApiString}'</#if>
        versionCode 1
        versionName "1.0"

        testInstrumentationRunner "${getMaterialComponentName('android.support.test.runner.AndroidJUnitRunner', useAndroidX)}"
    }

<#if javaVersion?? && javaVersion != "1.7">

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_${javaVersion?replace('.','_','i')}
        targetCompatibility JavaVersion.VERSION_${javaVersion?replace('.','_','i')}
    }
</#if>

    <@proguard.proguardConfig />
}

dependencies {
    ${getConfigurationName("compile")} fileTree(dir: 'libs', include: ['*.jar'])
    <@kt.addKotlinDependencies />
}

