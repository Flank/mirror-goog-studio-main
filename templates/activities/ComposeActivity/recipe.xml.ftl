<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <dependency mavenUrl="${resolveDependency("androidx.ui:ui-framework:+", "0.1.0-dev03")}" />
    <dependency mavenUrl="${resolveDependency("androidx.ui:ui-layout:+", "0.1.0-dev03")}" />
    <dependency mavenUrl="${resolveDependency("androidx.ui:ui-material:+", "0.1.0-dev03")}" />
    <dependency mavenUrl="${resolveDependency("androidx.ui:ui-tooling:+", "0.1.0-dev03")}" />

    <#include "../common/recipe_manifest.xml.ftl" />
    <#include "../common/recipe_no_actionbar.xml.ftl" />

    <instantiate from="root/src/app_package/MainActivity.kt.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.kt" />

    <requireJavaVersion version="1.8" kotlinSupport="true" />
    <setBuildFeature name="compose" value="true" />

    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.kt" />
</recipe>
