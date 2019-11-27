<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <dependency mavenUrl="com.android.support:support-v4:${buildApi}.+"/>
    <dependency mavenUrl="com.android.support:recyclerview-v7:${buildApi}.+" />

    <#assign escapedResOut="${escapeXmlAttribute(resOut)}">
    <#assign escapedSrcOut="${escapeXmlAttribute(srcOut)}">

    <instantiate from="root/res/layout/fragment_list.xml"
                 to="${escapedResOut}/layout/${fragment_layout_list}.xml" />
    <instantiate from="root/res/layout/item_list_content.xml"
                 to="${escapedResOut}/layout/${fragment_layout}.xml" />

    <instantiate from="root/src/app_package/ListFragment.${ktOrJavaExt}.ftl"
                 to="${escapedSrcOut}/${fragmentClass}.${ktOrJavaExt}" />
    <instantiate from="root/src/app_package/RecyclerViewAdapter.${ktOrJavaExt}.ftl"
                 to="${escapedSrcOut}/${adapterClassName}.${ktOrJavaExt}" />
    <#include "../../activities/common/recipe_dummy_content.xml.ftl" />

    <open file="${escapedSrcOut}/${fragmentClass}.${ktOrJavaExt}" />
    <open file="${escapedResOut}/layout/${fragment_layout_list}.xml" />

    <merge from="root/res/values/dimens.xml"
             to="${escapedResOut}/values/dimens.xml" />
</recipe>
