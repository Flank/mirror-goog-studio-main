<#import "./shared_macros.ftl" as shared>
apply plugin: 'com.android.feature'

<@shared.androidConfig isBaseFeature=true/>

dependencies {
  application project (':${MobileprojectName}')
  feature project(':${projectName}')
  <#if backwardsCompatibility!true>compile 'com.android.support:appcompat-v7:${buildApi}.+'</#if>
}
