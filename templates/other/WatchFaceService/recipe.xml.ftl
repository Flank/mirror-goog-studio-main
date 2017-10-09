<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <dependency mavenUrl="com.google.android.support:wearable:+" />

    <merge from="root/AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />
    <merge from="root/AndroidManifestPermissions.xml"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />
<#if appManifestOut??>
    <merge from="root/AndroidManifestPermissions.xml"
             to="${escapeXmlAttribute(appManifestOut)}/AndroidManifest.xml" />
</#if>

    <merge from="root/build.gradle.ftl"
             to="${escapeXmlAttribute(projectOut)}/build.gradle" />

    <merge from="root/res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <copy from="root/res/xml/watch_face.xml"
            to="${escapeXmlAttribute(resOut)}/xml/watch_face.xml" />

<#if style == "analog">
    <dependency mavenUrl="com.android.support:palette-v7:${buildApi}.+" />

    <copy from="root/res/drawable-nodpi/preview_analog.png"
            to="${escapeXmlAttribute(resOut)}/drawable-nodpi/preview_analog.png" />
    <copy from="root/res/drawable-nodpi/bg.png"
            to="${escapeXmlAttribute(resOut)}/drawable-nodpi/bg.png" />
<#elseif style == "digital">
    <merge from="root/res/values/dimens.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/dimens.xml" />
    <merge from="root/res/values/colors.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/colors.xml" />
    <copy from="root/res/drawable-nodpi/preview_digital.png"
            to="${escapeXmlAttribute(resOut)}/drawable-nodpi/preview_digital.png" />
    <copy from="root/res/drawable-nodpi/preview_digital_circular.png"
            to="${escapeXmlAttribute(resOut)}/drawable-nodpi/preview_digital_circular.png" />
</#if>

<#if style == "analog">
    <instantiate from="root/src/app_package/MyAnalogWatchFaceService.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${serviceClass}.${ktOrJavaExt}" />
    <open file="${escapeXmlAttribute(srcOut)}/${serviceClass}.${ktOrJavaExt}" />
<#elseif style == "digital">
    <instantiate from="root/src/app_package/MyDigitalWatchFaceService.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${serviceClass}.${ktOrJavaExt}" />
    <open file="${escapeXmlAttribute(srcOut)}/${serviceClass}.${ktOrJavaExt}" />
</#if>

</recipe>
