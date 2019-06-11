<?xml version="1.0"?>
<globals>
    <global id="requireTheme" type="boolean" value="true" />
    <#include "../common/common_globals.xml.ftl" />
    <global id="simpleLayoutName" value="${contentLayoutName}" />
    <global id="appBarLayoutName" value="${layoutName}" />
    <global id="fragmentClass" value="${activityClass}Fragment" />
    <global id="firstFragmentClass" value="${layoutToFragment(firstFragmentLayoutName)}" />
    <global id="secondFragmentClass" value="${layoutToFragment(secondFragmentLayoutName)}" />
</globals>
