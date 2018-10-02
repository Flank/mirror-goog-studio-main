<#import "root://gradle-projects/common/proguard_macros.ftl" as proguard>

<#-- Some common elements used in multiple files -->
<#macro watchProjectDependencies>
<#if WearprojectName?has_content && NumberOfEnabledFormFactors?has_content && NumberOfEnabledFormFactors gt 1 && Wearincluded>
    wearApp project(':${WearprojectName}')
    ${getConfigurationName("compile")} 'com.google.android.gms:play-services-wearable:+'
</#if>
</#macro>

<#macro generateManifest packageName hasApplicationBlock=false>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
        <#if isDynamicInstantApp>
            xmlns:dist="http://schemas.android.com/apk/distribution"
        </#if>
        package="${packageName}"<#if !hasApplicationBlock>/</#if>><#if hasApplicationBlock>

        <#if isDynamicInstantApp>
        <dist:module
            dist:instant="true" />
        </#if>
    <application <#if minApiLevel gte 4 && buildApi gte 4>android:allowBackup="true"</#if>
        android:label="@string/app_name"<#if copyIcons>
        android:icon="@mipmap/ic_launcher"<#if buildApi gte 25 && targetApi gte 25>
        android:roundIcon="@mipmap/ic_launcher_round"</#if><#elseif assetName??>
        android:icon="@drawable/${assetName}"</#if><#if buildApi gte 17>
        android:supportsRtl="true"</#if>
        android:theme="@style/AppTheme"/>
</manifest></#if>
</#macro>

<#macro androidConfig hasApplicationId=false applicationId='' hasTests=false canHaveCpp=false isBaseFeature=false canUseProguard=false>
android {
    compileSdkVersion <#if buildApiString?matches("^\\d+$")>${buildApiString}<#else>'${buildApiString}'</#if>
    <#if explicitBuildToolsVersion!false>buildToolsVersion "${buildToolsVersion}"</#if>

    <#if isBaseFeature>
    baseFeature true
    </#if>

    defaultConfig {
    <#if hasApplicationId>
        applicationId "${applicationId}"
    </#if>
        minSdkVersion <#if minApi?matches("^\\d+$")>${minApi}<#else>'${minApi}'</#if>
        targetSdkVersion <#if targetApiString?matches("^\\d+$")>${targetApiString}<#else>'${targetApiString}'</#if>
        versionCode 1
        versionName "1.0"

    <#if hasTests>
        testInstrumentationRunner "${getMaterialComponentName('android.support.test.runner.AndroidJUnitRunner', useAndroidX)}"
    </#if>

    <#if canHaveCpp && (includeCppSupport!false)>
        externalNativeBuild {
            cmake {
                cppFlags "${cppFlags}"
            }
        }
    </#if>
    }
<#if javaVersion?? && (javaVersion != "1.6" && buildApi lt 21 || javaVersion != "1.7")>

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_${javaVersion?replace('.','_','i')}
        targetCompatibility JavaVersion.VERSION_${javaVersion?replace('.','_','i')}
    }
</#if>
<#if isInstantApp?? && !isInstantApp>
    <#-- Remove android-kotlin-extensions from the default template. Now dataBinding can be used -->
    dataBinding {
        enabled true
    }
</#if>
<#if canUseProguard>
<@proguard.proguardConfig />
</#if>

<#if canHaveCpp && (includeCppSupport!false)>
    externalNativeBuild {
        cmake {
            path "src/main/cpp/CMakeLists.txt"
        }
    }
</#if>
}
</#macro>
