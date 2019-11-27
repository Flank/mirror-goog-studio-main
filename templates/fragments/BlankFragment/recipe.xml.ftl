<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <dependency mavenUrl="com.android.support:support-v4:${buildApi}.+"/>
    <merge from="root/res/values/strings.xml" to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <instantiate from="root/res/layout/fragment_blank.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${escapeXmlAttribute(fragmentName)}.xml" />

    <open file="${escapeXmlAttribute(resOut)}/layout/${escapeXmlAttribute(fragmentName)}.xml" />

    <instantiate from="root/src/app_package/BlankFragment.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${fragmentClass}.${ktOrJavaExt}" />

    <open file="${escapeXmlAttribute(srcOut)}/${fragmentClass}.${ktOrJavaExt}" />
</recipe>
