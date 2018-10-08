<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <dependency mavenUrl="androidx.preference:preference:1.1+" />

    <#include "../common/recipe_manifest.xml.ftl" />

    <merge from="root/res/values/strings.xml.ftl"
            to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <merge from="root/res/values/arrays.xml.ftl"
            to="${escapeXmlAttribute(resOut)}/values/arrays.xml" />

    <copy from="root/res/layout/settings_activity.xml"
                    to="${escapeXmlAttribute(resOut)}/layout/settings_activity.xml" />

    <#if multipleScreens>
        <copy from="root/res/drawable"
                to="${escapeXmlAttribute(resOut)}/drawable" />

        <copy from="root/res/xml/messages_preferences.xml"
                to="${escapeXmlAttribute(resOut)}/xml/messages_preferences.xml" />

        <copy from="root/res/xml/sync_preferences.xml"
                to="${escapeXmlAttribute(resOut)}/xml/sync_preferences.xml" />

        <instantiate from="root/res/xml/header_preferences.xml.ftl"
                to="${escapeXmlAttribute(resOut)}/xml/header_preferences.xml" />

        <instantiate from="root/src/app_package/MultipleScreenSettingsActivity.${ktOrJavaExt}.ftl"
                to="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />
    <#else>
        <copy from="root/res/xml/root_preferences.xml"
                to="${escapeXmlAttribute(resOut)}/xml/root_preferences.xml" />

        <instantiate from="root/src/app_package/SingleScreenSettingsActivity.${ktOrJavaExt}.ftl"
                to="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />
    </#if>
    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />
</recipe>
