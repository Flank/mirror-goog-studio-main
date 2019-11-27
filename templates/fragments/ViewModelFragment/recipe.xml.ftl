<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <dependency mavenUrl="com.android.support:support-v4:${buildApi}.+"/>
    <dependency mavenUrl="android.arch.lifecycle:extensions:+"/>
    <#assign escapedResOut="${escapeXmlAttribute(resOut)}">
    <#assign escapedSrcOut="${escapeXmlAttribute(srcOut)}">

    <#if generateKotlin && useAndroidX>
        <dependency mavenUrl="androidx.lifecycle:lifecycle-viewmodel-ktx:+"/>
    </#if>

    <instantiate from="root/res/layout/blank_fragment.xml.ftl"
                   to="${escapedResOut}/layout/${escapeXmlAttribute(layoutName)}.xml" />

    <open file="${escapedResOut}/layout/${escapeXmlAttribute(layoutName)}.xml" />

    <instantiate from="root/src/app_package/BlankFragment.${ktOrJavaExt}.ftl"
                   to="${escapedSrcOut}/${fragmentClass}.${ktOrJavaExt}" />

    <open file="${escapedSrcOut}/${fragmentClass}.${ktOrJavaExt}" />

    <instantiate from="root/src/app_package/BlankViewModel.${ktOrJavaExt}.ftl"
                   to="${escapedSrcOut}/${viewModelName}.${ktOrJavaExt}" />
</recipe>
