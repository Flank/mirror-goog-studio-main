<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <dependency mavenUrl="com.google.android.wearable:wearable:+" gradleConfiguration="provided" />
    
    <merge from="root/AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />
    <merge from="root/AndroidManifestPermissions.xml"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />
<#if appManifestOut??>
    <merge from="root/AndroidManifestPermissions.xml"
             to="${escapeXmlAttribute(appManifestOut)}/AndroidManifest.xml" />
</#if>

    <merge from="root/res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <merge from="root/res/values/dimens.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/dimens.xml" />

    <merge from="root/res/values-round/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values-round/strings.xml" />

    <instantiate from="root/res/layout/blank_activity.xml.ftl"
            to="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />
    <dependency mavenUrl="com.android.support:wear:+" />

    <instantiate from="root/src/app_package/BlankActivity.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />
    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />

    <open file="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />
</recipe>
