<#import "./shared_macros.ftl" as shared>
apply plugin: 'com.android.application'

<@shared.androidConfig hasApplicationId=true applicationId=instantAppPackageName+'.app' canUseProguard=true/>

dependencies {
    implementation project(':${projectName}')
    implementation project(':${baseFeatureName}')
<#if generateKotlin && useAndroidX> <#-- To fix b/112764077 -->
    implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
</#if>
    <@shared.watchProjectDependencies/>
}
