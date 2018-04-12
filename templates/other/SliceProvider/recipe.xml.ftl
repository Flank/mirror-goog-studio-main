<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <dependency mavenUrl="com.android.support:support-v4:${buildApi}.+" />
    <dependency mavenUrl="androidx.slice:slices-core:1.0.0-alpha1" />
    <dependency mavenUrl="androidx.slice:slices-builders:1.0.0-alpha1" />
    <merge from="root/AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />
    <instantiate from="root/src/app_package/SliceProvider.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${className}.${ktOrJavaExt}" />
    <open file="${escapeXmlAttribute(srcOut)}/${className}.${ktOrJavaExt}" />
</recipe>
