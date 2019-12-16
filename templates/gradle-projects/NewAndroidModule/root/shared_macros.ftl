<#import "root://gradle-projects/common/proguard_macros.ftl" as proguard>

<#-- Some common elements used in multiple files -->

<#macro generateManifest packageName hasApplicationBlock=false>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="${packageName}"<#if !hasApplicationBlock>/</#if>><#if hasApplicationBlock>

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/AppTheme" />
</manifest></#if>
</#macro>

<#macro androidConfig hasApplicationId=false applicationId='' hasTests=false canHaveCpp=false canUseProguard=false>
android {
    compileSdkVersion <#if buildApiString?matches("^\\d+$")>${buildApiString}<#else>'${buildApiString}'</#if>
    <#if explicitBuildToolsVersion!false>buildToolsVersion "${buildToolsVersion}"</#if>

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
    <#if canUseProguard && (isLibraryProject!false)>
        consumerProguardFiles 'consumer-rules.pro'
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

<#if canUseProguard>
<@proguard.proguardConfig />
</#if>

<#if canHaveCpp && (includeCppSupport!false)>
    externalNativeBuild {
        cmake {
            path "src/main/cpp/CMakeLists.txt"
            version "3.10.2"
        }
    }
</#if>
}
</#macro>
