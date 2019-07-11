<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />

    <#assign escapedResOut="${escapeXmlAttribute(resOut)}">
    <#assign escapedSrcOut="${escapeXmlAttribute(srcOut)}">

    <merge from="root/res/values/strings.xml.ftl"
             to="${escapedResOut}/values/strings.xml" />
    <merge from="root/res/values/dimens.xml.ftl"
             to="${escapedResOut}/values/dimens.xml" />

    <instantiate from="root/res/layout/simple.xml.ftl"
                 to="${escapedResOut}/layout/${layoutName}.xml" />

    <open file="${escapedResOut}/layout/${layoutName}.xml" />

    <instantiate from="root/src/app_package/ScrollFragment.${ktOrJavaExt}.ftl"
                   to="${escapedSrcOut}/${fragmentClass}.${ktOrJavaExt}" />
    <open file="${escapedSrcOut}/${fragmentClass}.${ktOrJavaExt}" />

</recipe>
