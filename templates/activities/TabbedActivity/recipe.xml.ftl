<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />

    <#if !(hasDependency('com.android.support:appcompat-v7'))>
        <dependency mavenUrl="com.android.support:appcompat-v7:${buildApi}.+"/>
    </#if>

    <#if !(hasDependency('com.android.support:design'))>
        <dependency mavenUrl="com.android.support:design:${buildApi}.+"/>
    </#if>

    <#if !(hasDependency('com.android.support.constraint:constraint-layout'))>
        <dependency mavenUrl="com.android.support.constraint:constraint-layout:+" />
    </#if>

    <#if !(hasDependency('android.arch.lifecycle:extensions'))>
        <dependency mavenUrl="android.arch.lifecycle:extensions:1.+" />
    </#if>

    <#include "../common/recipe_manifest.xml.ftl" />

    <merge from="root/res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />
    <merge from="root/res/values/styles.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/styles.xml" />
    <merge from="root/res/values/dimens.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/dimens.xml" />
    <merge from="root/res/values-w820dp/dimens.xml"
             to="${escapeXmlAttribute(resOut)}/values-w820dp/dimens.xml" />

    <instantiate from="root/res/layout/app_bar_activity.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />
    <instantiate from="root/res/layout/fragment_simple.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${fragmentLayoutName}.xml" />

    <instantiate from="root/src/app_package/TabsActivity.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />
    <instantiate from="root/src/app_package/ui/main/PageViewModel.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/ui/main/PageViewModel.${ktOrJavaExt}" />
    <instantiate from="root/src/app_package/ui/main/PlaceholderFragment.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/ui/main/PlaceholderFragment.${ktOrJavaExt}" />
    <instantiate from="root/src/app_package/ui/main/SectionsPagerAdapter.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/ui/main/SectionsPagerAdapter.${ktOrJavaExt}" />
    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />
    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />
    <open file="${escapeXmlAttribute(resOut)}/layout/${fragmentLayoutName}.xml" />
</recipe>
