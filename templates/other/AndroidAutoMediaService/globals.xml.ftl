<?xml version="1.0"?>
<globals>
    <global id="manifestOut" value="${manifestDir}" />
    <global id="srcOut" value="${srcDir}/${slashedPackageName(packageName)}" />
    <global id="resOut" value="${resDir}" />
    <global id="kotlinEscapedPackageName" value="${escapeKotlinIdentifiers(packageName)}" />
    <#include "root://activities/common/kotlin_globals.xml.ftl" />
</globals>
