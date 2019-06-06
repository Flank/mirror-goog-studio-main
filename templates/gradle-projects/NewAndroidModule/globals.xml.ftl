<?xml version="1.0"?>
<globals>
    <#include "root://gradle-projects/common/globals.xml.ftl" />
    <#include "root://gradle-projects/common/globals_android_module.xml.ftl" />
    <#assign useAndroidX=isAndroidxEnabled()>

    <global id="isLibraryProject" type="boolean" value="${((isLibraryProject!false))?string}" />
    <global id="isApplicationProject" type="boolean" value="${(!(isLibraryProject!false))?string}" />

    <global id="baseFeatureName" type="string" value="base" />

    <global id="baseFeatureOut" type="string" value="${escapeXmlAttribute(baseFeatureDir!'./base')}" />
    <global id="baseFeatureResOut" type="string" value="${escapeXmlAttribute(baseFeatureResDir!'./base/src/main/res')}" />
    <global id="useAndroidX" type="boolean" value="${useAndroidX?string}" />
    <#include "root://activities/common/kotlin_globals.xml.ftl" />
</globals>
