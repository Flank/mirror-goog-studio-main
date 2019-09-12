<?xml version="1.0"?>
<globals>
    <#include "../common/globals.xml.ftl" />

    <#if isNewProject!false>
        <#assign sharedModule="shared" />
        <global id="sharedModule" value="${sharedModule}" />
        <global id="serviceManifestOut" value="${escapeXmlAttribute(topOut)}/${sharedModule}/${manifestDir}" />
        <global id="serviceSrcOut" value="${escapeXmlAttribute(topOut)}/${sharedModule}/${srcDir}/${sharedModule}" />
        <global id="serviceResOut" value="${escapeXmlAttribute(topOut)}/${sharedModule}/${resDir}" />
        <global id="serviceProjectOut" value="${escapeXmlAttribute(topOut)}/${sharedModule}" />
        <global id="mobileProjectOut" value="${escapeXmlAttribute(topOut)}/${MobileprojectName}" />
        <global id="automotiveProjectOut" value="${escapeXmlAttribute(topOut)}/${AutomotiveprojectName}" />
        <global id="sharedPackageName" value="${packageName}.${sharedModule}" />
    <#else>
        <global id="serviceManifestOut" value="${escapeXmlAttribute(manifestOut)}" />
        <global id="serviceSrcOut" value="${escapeXmlAttribute(srcOut)}" />
        <global id="serviceResOut" value="${escapeXmlAttribute(resOut)}" />
        <global id="serviceProjectOut" value="." />
        <global id="sharedPackageName" value="${packageName}" />
    </#if>
    <#include "root://activities/common/kotlin_globals.xml.ftl" />
</globals>
