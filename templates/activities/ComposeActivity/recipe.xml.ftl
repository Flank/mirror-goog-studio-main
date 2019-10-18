<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <dependency mavenUrl="androidx.ui:ui-layout:+" />
    <dependency mavenUrl="androidx.ui:ui-material:+" />
    <dependency mavenUrl="androidx.ui:ui-tooling:+" />
    <dependency mavenUrl="org.jetbrains.kotlin:kotlin-reflect:+" /> <!-- This should be in the list of transitive dependencies of ui-framework b/142453988 -->

    <#include "../common/recipe_theme.xml.ftl" />
    <#include "../common/recipe_manifest.xml.ftl" />

    <instantiate from="root/src/app_package/MainActivity.kt.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.kt" />

    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.kt" />
</recipe>
