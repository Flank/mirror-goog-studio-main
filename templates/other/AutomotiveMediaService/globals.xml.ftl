<?xml version="1.0"?>
<globals>
    <#include "../common/globals.xml.ftl" />

    <#if Mobileincluded!false>
        <#assign sharedModule="shared" />
        <global id="sharedModule" value="${sharedModule}" />
        <global id="serviceManifestOut" value="${escapeXmlAttribute(topOut)}/${sharedModule}/${manifestDir}" />
        <global id="serviceSrcOut" value="${escapeXmlAttribute(topOut)}/${sharedModule}/${srcDir}" />
        <global id="serviceResOut" value="${escapeXmlAttribute(topOut)}/${sharedModule}/${resDir}" />
        <global id="serviceProjectOut" value="${escapeXmlAttribute(topOut)}/${sharedModule}" />
        <global id="mobileProjectOut" value="${escapeXmlAttribute(topOut)}/${MobileprojectName}" />
        <global id="automotiveProjectOut" value="${escapeXmlAttribute(topOut)}/${AutomotiveprojectName}" />
    <#else>
        <global id="serviceManifestOut" value="${escapeXmlAttribute(manifestOut)}" />
        <global id="serviceSrcOut" value="${escapeXmlAttribute(srcOut)}" />
        <global id="serviceResOut" value="${escapeXmlAttribute(resOut)}" />
        <global id="serviceProjectOut" value="." />
    </#if>
    <#include "root://activities/common/kotlin_globals.xml.ftl" />
</globals>
