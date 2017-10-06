<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <merge from="root/AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />
    <#assign ext=generateKotlin?string('kt', 'java')>
    <instantiate from="root/src/app_package/Service.${ext}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${className}.${ext}" />
    <open file="${escapeXmlAttribute(srcOut)}/${className}.${ext}" />
</recipe>
