<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<#import "root://activities/common/navigation/navigation_common_macros.ftl" as navigation>
<recipe>
    <#--
    This needs to be before addAllKotlinDependencies because the merge instruction seems
    to have non-commited documents that cause some UI tests to fail.
    -->
    <@navigation.addSafeArgsPluginToClasspath />

    <@kt.addAllKotlinDependencies />
    <#include "../common/recipe_manifest.xml.ftl" />
    <#include "../common/recipe_app_bar.xml.ftl" />

    <#--
        This block is equivalent to recipe_simple.xml.ftl except for the fragment contains NavHostFragment.
    -->
    <#if !(hasDependency('com.android.support:appcompat-v7'))>
        <dependency mavenUrl="com.android.support:appcompat-v7:${buildApi}.+"/>
    </#if>
        <dependency mavenUrl="com.android.support.constraint:constraint-layout:+" />
        <instantiate from="root/res/layout/fragment_simple.xml.ftl"
                     to="${escapeXmlAttribute(resOut)}/layout/${simpleLayoutName}.xml" />
    <#if (isNewProject!false) && !(excludeMenu!false)>
        <#include "../common/recipe_simple_menu.xml.ftl" />
    </#if>
    <#--------->

    <instantiate from="root/src/app_package/SimpleActivity.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />

    <instantiate from="../common/navigation/src/ui/FirstFragment.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${firstFragmentClass}.${ktOrJavaExt}" />
    <instantiate from="../common/navigation/src/ui/SecondFragment.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${secondFragmentClass}.${ktOrJavaExt}" />
    <instantiate from="../common/navigation/src/res/layout/fragment_first.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${firstFragmentLayoutName}.xml" />
    <instantiate from="../common/navigation/src/res/layout/fragment_second.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${secondFragmentLayoutName}.xml" />
    <merge from="../common/navigation/src/res/navigation/nav_graph.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/navigation/nav_graph.xml" />
    <merge from="root/res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />
    <merge from="../common/navigation/src/res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <dependency mavenUrl="android.arch.navigation:navigation-fragment-ktx:+"/>
    <dependency mavenUrl="android.arch.navigation:navigation-ui-ktx:+"/>
    <@navigation.addSafeArgsPlugin />

    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />
    <open file="${escapeXmlAttribute(resOut)}/layout/${simpleLayoutName}.xml" />
</recipe>
