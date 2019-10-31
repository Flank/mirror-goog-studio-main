<?xml version="1.0"?>
<globals>
    <global id="hasNoActionBar" type="boolean" value="false" />
    <global id="isLauncher" type="boolean" value="${isNewModule?string}" />
    <global id="includePermissionCheck" type="boolean" value="${(targetApi gte 23)?string}" />
    <#include "../common/common_globals.xml.ftl" />
</globals>
