<?xml version="1.0"?>
<globals>
    <#include "root://gradle-projects/common/globals.xml.ftl" />
    <#include "root://activities/common/kotlin_globals.xml.ftl" />
    <global id="srcOut" value="${srcDir}/${slashedPackageName(packageName)}" />
</globals>
