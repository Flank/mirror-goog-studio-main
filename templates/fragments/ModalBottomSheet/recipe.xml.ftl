<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <dependency mavenUrl="com.android.support:support-v4:${buildApi}.+"/>
    <dependency mavenUrl="com.android.support:design:${buildApi}.+" />
    <dependency mavenUrl="com.android.support:recyclerview-v7:${buildApi}.+" />

    <#assign escapedResOut="${escapeXmlAttribute(resOut)}">
    <#assign escapedSrcOut="${escapeXmlAttribute(srcOut)}">

    <instantiate from="root/res/layout/fragment_item_list_dialog.xml"
                 to="${escapedResOut}/layout/${listLayout}.xml" />
    <instantiate from="root/res/layout/fragment_item_list_dialog_item.xml"
                 to="${escapedResOut}/layout/${itemLayout}.xml" />

    <instantiate from="root/src/app_package/ItemListDialogFragment.${ktOrJavaExt}.ftl"
                 to="${escapedSrcOut}/${fragmentClass}.${ktOrJavaExt}" />

    <open file="${escapedResOut}/layout/${listLayout}.xml" />
    <open file="${escapedSrcOut}/${fragmentClass}.${ktOrJavaExt}" />

    <merge from="root/res/values/dimens.xml"
             to="${escapedResOut}/values/dimens.xml" />

</recipe>
