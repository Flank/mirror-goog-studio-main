<recipe folder="root://activities/common">

<#if appCompat && !(hasDependency('com.android.support:appcompat-v7'))>
    <dependency mavenUrl="com.android.support:appcompat-v7:${buildApi}.+"/>
</#if>
<#-- TODO: Switch Instant Apps back to ConstraintLayout once library dependency bugs are resolved -->
<#if !isInstantApp>
    <dependency mavenUrl="com.android.support.constraint:constraint-layout:+" />
</#if>

    <instantiate from="root/res/layout/simple.xml.ftl"
                 to="${escapeXmlAttribute(resOut)}/layout/${simpleLayoutName}.xml" />

<#if (isNewProject!false) && !(excludeMenu!false)>
    <#include "recipe_simple_menu.xml.ftl" />
</#if>
</recipe>
