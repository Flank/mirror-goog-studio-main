<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <dependency mavenUrl="com.android.support:support-media-compat:${buildApi}.+" />

    <merge from="root/AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

    <#if useCustomTheme>
      <merge from="root/res/values/styles.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/styles.xml" />
    </#if>

    <instantiate from="root/src/app_package/MusicService.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${mediaBrowserServiceName}.${ktOrJavaExt}" />

    <open file="${escapeXmlAttribute(srcOut)}/${mediaBrowserServiceName}.${ktOrJavaExt}" />

    <#if useCustomTheme>
      <open file="${escapeXmlAttribute(resOut)}/values-v21/styles.xml" />
    </#if>
</recipe>
