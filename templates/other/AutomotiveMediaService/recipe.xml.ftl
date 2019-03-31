<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <#if Mobileincluded!false>
        <merge from="root/settings.gradle.ftl"
               to="${escapeXmlAttribute(topOut)}/settings.gradle" />
        <instantiate from="root/build.gradle.ftl"
                     to="${escapeXmlAttribute(serviceProjectOut)}/build.gradle" />
        <merge from="root/mobile-build.gradle.ftl"
               to="${mobileProjectOut}/build.gradle" />
        <merge from="root/automotive-build.gradle.ftl"
               to="${automotiveProjectOut}/build.gradle" />
    <#else>
        <dependency mavenUrl="com.android.support:support-media-compat:${buildApi}.+" />
    </#if>

    <!-- Create media service -->
    <@kt.addAllKotlinDependencies />

    <merge from="root/AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(serviceManifestOut)}/AndroidManifest.xml" />

    <#if useCustomTheme>
      <merge from="root/res/values/styles.xml.ftl"
             to="${escapeXmlAttribute(serviceResOut)}/values/styles.xml" />
    </#if>

    <copy from="root/res/xml/automotive_app_desc.xml"
          to="${escapeXmlAttribute(serviceResOut)}/xml/automotive_app_desc.xml" />

    <instantiate from="root/src/app_package/MusicService.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(serviceSrcOut)}/${mediaBrowserServiceName}.${ktOrJavaExt}" />

    <open file="${escapeXmlAttribute(serviceSrcOut)}/${mediaBrowserServiceName}.${ktOrJavaExt}" />

    <#if useCustomTheme>
      <open file="${escapeXmlAttribute(serviceResOut)}/values/styles.xml" />
    </#if>
</recipe>
