<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />

    <#assign escapedResOut="${escapeXmlAttribute(resOut)}">

    <merge from="root/res/values/strings.xml.ftl"
             to="${escapedResOut}/values/strings.xml" />
    <merge from="root/res/values/full_screen_colors.xml"
             to="${escapedResOut}/values/colors.xml" />
    <instantiate from="root/res/layout/fragment_fullscreen.xml.ftl"
                   to="${escapedResOut}/layout/${layoutName}.xml" />

    <instantiate from="root/src/app_package/FullscreenFragment.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${fragmentClass}.${ktOrJavaExt}" />

    <open file="${escapedResOut}/layout/${layoutName}.xml" />
</recipe>
