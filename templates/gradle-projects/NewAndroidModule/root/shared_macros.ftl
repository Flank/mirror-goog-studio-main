<#-- Some common elements used in multiple files -->
<#macro generateManifest packageName hasApplicationBlock=false isInstantApp=false splitName="">
<manifest xmlns:android="http://schemas.android.com/apk/res/android" <#if isInstantApp>
    xmlns:instant="http://schemas.android.com/instantapps"</#if>
    package="${packageName}"<#if splitName?has_content>
    split="${splitName}"</#if><#if !hasApplicationBlock>/</#if>><#if hasApplicationBlock>
    <application <#if minApiLevel gte 4 && buildApi gte 4>android:allowBackup="true"</#if>
        android:label="@string/app_name"<#if copyIcons>
        android:icon="@mipmap/ic_launcher"<#if buildApi gte 25 && targetApi gte 25>
        android:roundIcon="@mipmap/ic_launcher_round"</#if><#elseif assetName??>
        android:icon="@drawable/${assetName}"</#if><#if buildApi gte 17>
        android:supportsRtl="true"</#if>
        android:theme="@style/AppTheme"/>
</manifest></#if>
</#macro>


<#macro androidConfig hasApplicationId=false hasTests=false canHaveCpp=false>
android {
    compileSdkVersion <#if buildApiString?matches("^\\d+$")>${buildApiString}<#else>'${buildApiString}'</#if>
    buildToolsVersion "${buildToolsVersion}"

    defaultConfig {
    <#if hasApplicationId>
        applicationId "${packageName}"
    </#if>
        minSdkVersion <#if minApi?matches("^\\d+$")>${minApi}<#else>'${minApi}'</#if>
        targetSdkVersion <#if targetApiString?matches("^\\d+$")>${targetApiString}<#else>'${targetApiString}'</#if>
        versionCode 1
        versionName "1.0"

    <#if hasTests>
        testInstrumentationRunner "android.support.test.runner.AndroidJUnitRunner"
    </#if>

    <#if canHaveCpp && includeCppSupport!false && cppFlags != "">
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
<#if enableProGuard>
    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }
    }
</#if>
<#if canHaveCpp && includeCppSupport!false>
    externalNativeBuild {
        cmake {
            path "CMakeLists.txt"
        }
    }
</#if>
}
</#macro>