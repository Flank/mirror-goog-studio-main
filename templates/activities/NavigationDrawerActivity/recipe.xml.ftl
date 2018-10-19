<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <#if !(hasDependency('com.android.support:support-v4'))>
        <dependency mavenUrl="com.android.support:support-v4:${buildApi}.+"/>
    </#if>

    <#include "../common/recipe_manifest.xml.ftl" />

    <merge from="root/${resIn}/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <merge from="root/${resIn}/values/dimens.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/dimens.xml" />

    <instantiate from="root/${resIn}/menu/main.xml.ftl"
            to="${escapeXmlAttribute(resOut)}/menu/${menuName}.xml" />

    <copy from="root/res-buildApi22/drawable"
            to="${escapeXmlAttribute(resOut)}/drawable" />
    <copy from="root/res-buildApi22/drawable-v21"
            to="${escapeXmlAttribute(resOut)}/drawable<#if includeImageDrawables>-v21</#if>" />

    <#if includeImageDrawables>
        <copy from="root/res-buildApi22/values/drawables.xml"
                to="${escapeXmlAttribute(resOut)}/values/drawables.xml" />
    </#if>

    <#if !(hasDependency('com.android.support:design'))>
        <dependency mavenUrl="com.android.support:design:${buildApi}.+"/>
    </#if>

    <#if !(hasDependency('com.android.support:appcompat-v7'))>
        <dependency mavenUrl="com.android.support:appcompat-v7:${buildApi}.+"/>
    </#if>

    <#if useNavController>
        <dependency mavenUrl="com.android.support.constraint:constraint-layout:+" />

        <instantiate from="root/res-buildApi22/layout/navigation_content_main.xml.ftl"
                     to="${escapeXmlAttribute(resOut)}/layout/${simpleLayoutName}.xml" />

        <#if (isNewProject!false) && !(excludeMenu!false)>
            <#include "../common/recipe_simple_menu.xml.ftl" />
        </#if>

        <#import "root://activities/common/navigation/navigation_common_macros.ftl" as navigation>
        <@navigation.instantiateFragmentAndViewModel fragmentPrefix="home" />
        <@navigation.instantiateFragmentAndViewModel fragmentPrefix="gallery" />
        <@navigation.instantiateFragmentAndViewModel fragmentPrefix="slideshow" />
        <@navigation.instantiateFragmentAndViewModel fragmentPrefix="tools" />
        <@navigation.instantiateFragmentAndViewModel fragmentPrefix="share" />
        <@navigation.instantiateFragmentAndViewModel fragmentPrefix="send" />
        <@navigation.navigationDependencies />

        <instantiate from="root/res-buildApi22/navigation/mobile_navigation.xml.ftl"
                     to="${escapeXmlAttribute(resOut)}/navigation/mobile_navigation.xml" />
        <open file="${escapeXmlAttribute(resOut)}/navigation/mobile_navigation.xml" />
    <#else>
        <#include "../common/recipe_simple.xml.ftl" />
    </#if>

    <#if hasAppBar>
        <#include "../common/recipe_app_bar.xml.ftl" />
    <#else>
        <#include "../common/recipe_no_actionbar.xml.ftl" />
    </#if>
    <#if buildApi gte 21>
        <instantiate from="root/res-buildApi22/values-v21/no_actionbar_styles_v21.xml.ftl"
                        to="${escapeXmlAttribute(resOut)}/values-v21/styles.xml" />
    </#if>

    <instantiate from="root/res-buildApi22/menu/drawer.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/menu/${drawerMenu}.xml" />

    <instantiate from="root/res-buildApi22/layout/navigation_view.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />
    <instantiate from="root/res-buildApi22/layout/navigation_header.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${navHeaderLayoutName}.xml" />

    <instantiate from="root/src-buildApi22/app_package/DrawerActivity.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />

    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />

    <open file="${escapeXmlAttribute(resOut)}/layout/${contentLayoutName}.xml" />
</recipe>
