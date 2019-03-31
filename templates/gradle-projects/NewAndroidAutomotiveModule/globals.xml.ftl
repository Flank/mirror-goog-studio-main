<?xml version="1.0"?>
<globals>
    <#include "root://gradle-projects/common/globals.xml.ftl" />
    <#include "root://gradle-projects/common/globals_android_module.xml.ftl" />
    <#assign useAndroidX=isAndroidxEnabled()>
    <global id="useAndroidX" type="boolean" value="${useAndroidX?string}" />
    <#include "root://activities/common/kotlin_globals.xml.ftl" />
</globals>
