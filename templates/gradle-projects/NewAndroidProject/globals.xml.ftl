<?xml version="1.0"?>
<globals>
    <global id="topOut" type="string" value="." />
    <global id="sdkDir" type="string" value="unset" />
    <global id="gradlePluginVersion" type="string" value="2.2.3" />

    <global id="hasSdkDir" type="boolean" value="<#if sdkDir??>true<#else>false</#if>" />
    <global id="isLowMemory" type="boolean" value="false" />
    <global id="kotlinVersion" type="string" value="${kotlinVersion!'1.1.2'}" />
    <#include "root://activities/common/kotlin_globals.xml.ftl" />
</globals>
