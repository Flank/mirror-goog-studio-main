<?xml version="1.0"?>
<globals>
    <#include "../common/common_globals.xml.ftl" />
    <global id="projectOut" value="." />
    <global id="manifestOut" value="${manifestDir}" />
    <global id="srcOut" value="${srcDir}/${slashedPackageName(packageName)}" />
    <global id="debugResOut" value="${escapeXmlAttribute(projectOut)}/src/debug/res" />
    <global id="releaseResOut" value="${escapeXmlAttribute(projectOut)}/src/release/res" />
    <global id="resOut" value="${resDir}" />
    <global id="simpleName" value="${activityToLayout(activityClass)}" />
    <global id="relativePackage" value="<#if relativePackage?has_content>${relativePackage}<#else>${packageName}</#if>" />
</globals>
