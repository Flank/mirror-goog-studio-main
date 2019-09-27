<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <dependency mavenUrl="com.android.support:design:${buildApi}.+" />
    <dependency mavenUrl="com.android.support.constraint:constraint-layout:+" />

    <#include "../common/recipe_manifest.xml.ftl" />

    <copy from="root/res/drawable"
            to="${escapeXmlAttribute(resOut)}/drawable" />

    <instantiate from="root/src/app_package/MainActivity.kt.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.kt" />
    <instantiate from="root/src/app_package/MainComponent.kt.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${layoutClass}.kt" />

    <merge from="root/res/values/dimens.xml"
             to="${escapeXmlAttribute(resOut)}/values/dimens.xml" />
    <merge from="root/res/values/strings.xml"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />
    <copy from="root/res/menu/navigation.xml"
            to="${escapeXmlAttribute(resOut)}/menu/navigation.xml" />

    <open file="${escapeXmlAttribute(srcOut)}/${layoutClass}.kt" />
</recipe>
