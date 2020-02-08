<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<#import "root://activities/common/navigation/navigation_common_macros.ftl" as navigation>
<recipe>
    <#--
    This needs to be before addAllKotlinDependencies because the merge instruction seems
    to have non-commited documents that cause some UI tests to fail.
    -->
    <@kt.addAllKotlinDependencies />
    <dependency mavenUrl="com.android.support:design:${buildApi}.+" />
    <dependency mavenUrl="com.android.support.constraint:constraint-layout:+" />

<#if minApiLevel lt 21>
    <dependency mavenUrl="com.android.support:support-vector-drawable:${buildApi}.+" />
</#if>

    <#include "../common/recipe_manifest.xml.ftl" />

    <#assign activityMainLayout="navigation_activity_main" />

    <@navigation.instantiateFragmentAndViewModel fragmentPrefix="home" />
    <@navigation.instantiateFragmentAndViewModel fragmentPrefix="dashboard" />
    <@navigation.instantiateFragmentAndViewModel fragmentPrefix="notifications" />
    <@navigation.navigationDependencies />
    <#if generateKotlin>
        <requireJavaVersion version="1.8" kotlinSupport="true" />
    </#if>

    <instantiate from="root/res/navigation/mobile_navigation.xml.ftl"
                 to="${escapeXmlAttribute(resOut)}/navigation/mobile_navigation.xml" />
    <open file="${escapeXmlAttribute(resOut)}/navigation/mobile_navigation.xml" />

    <copy from="root/res/drawable"
            to="${escapeXmlAttribute(resOut)}/drawable" />

    <instantiate from="root/src/app_package/MainActivity.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />
    <instantiate from="root/res/layout/${activityMainLayout}.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />

    <merge from="root/res/values/dimens.xml"
             to="${escapeXmlAttribute(resOut)}/values/dimens.xml" />
    <merge from="root/res/values/strings.xml"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />
    <copy from="root/res/menu/navigation.xml"
            to="${escapeXmlAttribute(resOut)}/menu/bottom_nav_menu.xml" />

    <open file="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />

</recipe>
