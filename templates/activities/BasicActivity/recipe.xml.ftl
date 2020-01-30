<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<#import "root://activities/common/navigation/navigation_common_macros.ftl" as navigation>
<recipe>
    <@kt.addAllKotlinDependencies />
    <#include "../common/recipe_manifest.xml.ftl" />
    <#include "../common/recipe_app_bar.xml.ftl" />

    <#--
        This block is equivalent to recipe_simple.xml.ftl except for the fragment contains NavHostFragment.
    -->
    <dependency mavenUrl="com.android.support:appcompat-v7:${buildApi}.+"/>
    <dependency mavenUrl="com.android.support.constraint:constraint-layout:+" />
    <instantiate from="root/res/layout/fragment_simple.xml.ftl"
                 to="${escapeXmlAttribute(resOut)}/layout/${simpleLayoutName}.xml" />
    <#if (isNewModule!false) && !(excludeMenu!false)>
        <#include "../common/recipe_simple_menu.xml.ftl" />
    </#if>
    <#--------->

    <instantiate from="root/src/app_package/BasicActivity.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />

    <instantiate from="root/src/app_package/FirstFragment.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${firstFragmentClass}.${ktOrJavaExt}" />
    <instantiate from="root/src/app_package/SecondFragment.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${secondFragmentClass}.${ktOrJavaExt}" />
    <instantiate from="root/res/layout/fragment_first.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${firstFragmentLayoutName}.xml" />
    <instantiate from="root/res/layout/fragment_second.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${secondFragmentLayoutName}.xml" />
    <merge from="root/res/navigation/nav_graph.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/navigation/${navigationGraphName}.xml" />
    <merge from="root/res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <#if generateKotlin>
        <dependency mavenUrl="android.arch.navigation:navigation-fragment-ktx:+"/>
        <dependency mavenUrl="android.arch.navigation:navigation-ui-ktx:+"/>
    <#else>
        <dependency mavenUrl="android.arch.navigation:navigation-fragment:+"/>
        <dependency mavenUrl="android.arch.navigation:navigation-ui:+"/>
    </#if>
    <#if generateKotlin>
        <requireJavaVersion version="1.8" kotlinSupport="true" />
    </#if>

    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />
    <open file="${escapeXmlAttribute(resOut)}/layout/${simpleLayoutName}.xml" />
</recipe>
