<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <dependency mavenUrl="com.android.support:design:${buildApi}.+" />
    <dependency mavenUrl="com.android.support.constraint:constraint-layout:+" />
    <dependency mavenUrl="androidx.ui:ui-framework:+" />
    <dependency mavenUrl="androidx.ui:ui-android-view:+" />

    <#include "../common/recipe_theme.xml.ftl" />
    <#include "../common/recipe_manifest.xml.ftl" />

    <instantiate from="root/src/app_package/MainActivity.kt.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.kt" />
    <instantiate from="root/src/app_package/MainComponent.kt.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${layoutClass}.kt" />

    <open file="${escapeXmlAttribute(srcOut)}/${layoutClass}.kt" />
</recipe>
