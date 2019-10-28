<?xml version="1.0"?>
<globals>
    <global id="requireTheme" type="boolean" value="true" />
    <#include "../common/common_globals.xml.ftl" />
    <global id="menuName" value="${classToResource(activityClass)}" />
    <global id="simpleLayoutName" value="${contentLayoutName}" />
    <global id="includeImageDrawables" type="boolean" value="${(minApiLevel?number lt 21)?string}" />
</globals>
