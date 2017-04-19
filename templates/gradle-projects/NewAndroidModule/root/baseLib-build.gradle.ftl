<#import "./shared_macros.ftl" as shared>
apply plugin: 'com.android.feature'

<@shared.androidConfig isBaseSplit=true/>

dependencies {
  featureSplit project(':${projectName}')
  <#if backwardsCompatibility!true>compile 'com.android.support:appcompat-v7:${buildApi}.+'</#if>
}
