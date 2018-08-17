<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<#import "root://activities/common/navigation/navigation_common_macros.ftl" as navigation>
<recipe>
    <@kt.addAllKotlinDependencies />
    <#include "../common/recipe_manifest.xml.ftl" />

<#if navigationType == "Navigation Drawer">
    <#--
    While the navigation option is behind the flag (NPW_NAVIGATION_SUPPORT), creating another top level
    and content layouts for Navigation Drawer not to break the existing templates.
    -->
    <global id="layoutName" value="nav_activity_main" />
    <global id="simpleLayoutName" value="nav_content_main" />
</#if>

<#if useFragment>
    <#include "recipe_fragment.xml.ftl" />
<#else>
    <#include "../common/recipe_simple.xml.ftl" />
</#if>

<#if hasAppBar>
    <#include "../common/recipe_app_bar.xml.ftl" />
</#if>

    <instantiate from="root/src/app_package/SimpleActivity.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />
    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />

<#if useFragment>
    <open file="${escapeXmlAttribute(resOut)}/layout/${fragmentLayoutName}.xml" />
<#else>
    <open file="${escapeXmlAttribute(resOut)}/layout/${simpleLayoutName}.xml" />
</#if>

<#if navigationType == "Navigation Drawer">
    <@navigation.instantiateFragmentAndViewModel fragmentPrefix="home" />
    <@navigation.instantiateFragmentAndViewModel fragmentPrefix="page1" />
    <@navigation.instantiateFragmentAndViewModel fragmentPrefix="page2" />
    <instantiate from="root://activities/common/navigation/navigation_drawer/res/layout/nav_header_main.xml.ftl"
                 to="${escapeXmlAttribute(resOut)}/layout/nav_header_main.xml" />
    <instantiate from="root://activities/common/navigation/navigation_drawer/res/menu/activity_main_drawer.xml.ftl"
                 to="${escapeXmlAttribute(resOut)}/menu/activity_main_drawer.xml" />
    <merge from="root://activities/common/navigation/navigation_drawer/res/values/strings.xml.ftl"
           to="${escapeXmlAttribute(resOut)}/values/strings.xml" />
    <merge from="root://activities/common/navigation/navigation_drawer/res/values/dimens.xml.ftl"
           to="${escapeXmlAttribute(resOut)}/values/dimens.xml" />
    <merge from="root://activities/common/navigation/navigation_drawer/res/values-w820dp/dimens.xml.ftl"
           to="${escapeXmlAttribute(resOut)}/values-w820dp/dimens.xml" />
    <copy from="root://activities/common/navigation/navigation_drawer/res/drawable"
          to="${escapeXmlAttribute(resOut)}/drawable" />
    <instantiate from="root://activities/common/root/res/layout/app_bar.xml.ftl"
                 to="${escapeXmlAttribute(resOut)}/layout/app_bar_main.xml" />
    <instantiate from="root://activities/common/navigation/navigation_drawer/res/layout/nav_activity_main.xml.ftl"
                 to="${escapeXmlAttribute(resOut)}/layout/nav_activity_main.xml" />
    <instantiate from="root://activities/common/navigation/navigation_drawer/res/layout/nav_content_main.xml.ftl"
                 to="${escapeXmlAttribute(resOut)}/layout/nav_content_main.xml" />
</#if>
</recipe>
