<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <dependency mavenUrl="${resolveDependency("androidx.preference:preference:+", "1.1+")}" />

    <merge from="root/res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <merge from="root/res/values/arrays.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/arrays.xml" />

    <copy from="root/res/xml/root_preferences.xml"
            to="${escapeXmlAttribute(resOut)}/xml/root_preferences.xml" />

    <instantiate from="root/src/app_package/SingleScreenSettingsFragment.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${fragmentClass}.${ktOrJavaExt}" />
    <open file="${escapeXmlAttribute(srcOut)}/${fragmentClass}.${ktOrJavaExt}" />
</recipe>
