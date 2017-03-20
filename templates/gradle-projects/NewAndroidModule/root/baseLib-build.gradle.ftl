<#import "./shared_macros.ftl" as shared>
apply plugin: 'com.android.library'

<@shared.androidConfig />

dependencies {
  <#if backwardsCompatibility!true>compile 'com.android.support:appcompat-v7:${buildApi}.+'</#if>
}
