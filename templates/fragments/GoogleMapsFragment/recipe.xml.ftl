<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <dependency mavenUrl="com.google.android.gms:play-services-maps:+" />
    <dependency mavenUrl="com.android.support:appcompat-v7:${buildApi}.+" />

    <merge from="root/AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

    <#assign finalResOut="${escapeXmlAttribute(resOut)}">
    <#assign finalDebugResOut="${escapeXmlAttribute(projectOut)}/src/debug/res">
    <#assign finalReleaseResOut="${escapeXmlAttribute(projectOut)}/src/release/res">

    <instantiate from="root/res/layout/fragment_map.xml.ftl"
                   to="${finalResOut}/layout/${layoutName}.xml" />
    <instantiate from="root/src/app_package/MapFragment.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${fragmentClass}.${ktOrJavaExt}" />

    <merge from="root/debugRes/values/google_maps_api.xml.ftl"
             to="${finalDebugResOut}/values/google_maps_api.xml" />

    <merge from="root/releaseRes/values/google_maps_api.xml.ftl"
             to="${finalReleaseResOut}/values/google_maps_api.xml" />

    <open file="${escapeXmlAttribute(srcOut)}/${fragmentClass}.${ktOrJavaExt}" />
    <open file="${finalResOut}/layout/${layoutName}.xml" />

    <!-- Display the API key instructions. -->
    <open file="${finalDebugResOut}/values/google_maps_api.xml" />
</recipe>
