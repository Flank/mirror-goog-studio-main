<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <#include "../common/recipe_manifest.xml.ftl" />
    <@kt.addAllKotlinDependencies />

<#if generateLayout || (includeCppSupport!false)>
    <#include "../common/recipe_simple.xml.ftl" />
    <open file="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />
</#if>

<#if includeCppSupport!false>
    <instantiate from="root/src/app_package/EmptyActivityWithCppSupport.${ktOrJavaExt}.ftl"
                 to="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />

    <mkdir at="${escapeXmlAttribute(nativeSrcOut)}" />

    <instantiate from="root/src/app_package/native-lib.cpp.ftl"
                   to="${escapeXmlAttribute(nativeSrcOut)}/native-lib.cpp" />
<#else>
    <instantiate from="root/src/app_package/EmptyActivity.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />
</#if>
    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />
</recipe>
