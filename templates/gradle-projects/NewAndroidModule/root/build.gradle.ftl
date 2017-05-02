<#import "./shared_macros.ftl" as shared>
<#if isInstantApp>
apply plugin: 'com.android.feature'
<#else>
  <#if isLibraryProject>
apply plugin: 'com.android.library'
  <#else>
apply plugin: 'com.android.application'
  </#if>
</#if>
<#if includeKotlinSupport!false>
apply plugin: 'kotlin-android'
</#if>


<@shared.androidConfig hasApplicationId=isApplicationProject applicationId=packageName isBaseFeature=isBaseFeature hasTests=true canHaveCpp=true/>

dependencies {
    compile fileTree(dir: 'libs', include: ['*.jar'])
    androidTestCompile('com.android.support.test.espresso:espresso-core:${espressoVersion!"+"}', {
        exclude group: 'com.android.support', module: 'support-annotations'
    })
<#if WearprojectName?has_content && NumberOfEnabledFormFactors?has_content && NumberOfEnabledFormFactors gt 1 && Wearincluded>
    wearApp project(':${WearprojectName}')
    compile 'com.google.android.gms:play-services-wearable:+'
</#if>
<#if isInstantApp && hasInstantAppWrapper>
    implementation project(':${baseFeatureName}')
</#if>
<#if includeKotlinSupport!false>
    compile "org.jetbrains.kotlin:kotlin-stdlib-jre7:$kotlin_version"
</#if>

}
