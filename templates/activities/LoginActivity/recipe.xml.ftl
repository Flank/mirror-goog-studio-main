<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <dependency mavenUrl="com.android.support:appcompat-v7:${buildApi}.+" />
    <dependency mavenUrl="com.android.support:design:${buildApi}.+" />
    <dependency mavenUrl="com.android.support:support-annotations:${buildApi}.+" />
    <dependency mavenUrl="com.android.support.constraint:constraint-layout:+" />
    <dependency mavenUrl="android.arch.lifecycle:extensions:+"/>

    <#include "../common/recipe_theme.xml.ftl" />
    <#include "../common/recipe_manifest_strings.xml.ftl" />

    <merge from="root/AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

    <merge from="root/res/values/dimens.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/dimens.xml" />

    <merge from="root/res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <instantiate from="root/res/layout/activity_login.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />


    <@kt.addAllKotlinDependencies />
    <instantiate from="root/src/app_package/ui/login/LoginActivity.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/ui/login/${activityClass}.${ktOrJavaExt}" />
    <instantiate from="root/src/app_package/ui/login/LoginViewModel.${ktOrJavaExt}.ftl"
                       to="${escapeXmlAttribute(srcOut)}/ui/login/LoginViewModel.${ktOrJavaExt}" />
    <instantiate from="root/src/app_package/ui/login/LoginViewModelFactory.${ktOrJavaExt}.ftl"
                       to="${escapeXmlAttribute(srcOut)}/ui/login/LoginViewModelFactory.${ktOrJavaExt}"/>
    <instantiate from="root/src/app_package/data/model/LoggedInUser.${ktOrJavaExt}.ftl"
                       to="${escapeXmlAttribute(srcOut)}/data/model/LoggedInUser.${ktOrJavaExt}"/>
    <instantiate from="root/src/app_package/data/LoginDataSource.${ktOrJavaExt}.ftl"
                       to="${escapeXmlAttribute(srcOut)}/data/LoginDataSource.${ktOrJavaExt}"/>
    <instantiate from="root/src/app_package/data/LoginRepository.${ktOrJavaExt}.ftl"
                       to="${escapeXmlAttribute(srcOut)}/data/LoginRepository.${ktOrJavaExt}"/>
    <instantiate from="root/src/app_package/data/Result.${ktOrJavaExt}.ftl"
                       to="${escapeXmlAttribute(srcOut)}/data/Result.${ktOrJavaExt}"/>
    <instantiate from="root/src/app_package/ui/login/LoginFormState.${ktOrJavaExt}.ftl"
                           to="${escapeXmlAttribute(srcOut)}/ui/login/LoginFormState.${ktOrJavaExt}"/>
    <instantiate from="root/src/app_package/ui/login/LoginResult.${ktOrJavaExt}.ftl"
                               to="${escapeXmlAttribute(srcOut)}/ui/login/LoginResult.${ktOrJavaExt}"/>
    <instantiate from="root/src/app_package/ui/login/LoggedInUserView.${ktOrJavaExt}.ftl"
                                  to="${escapeXmlAttribute(srcOut)}/ui/login/LoggedInUserView.${ktOrJavaExt}"/>
    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.${ktOrJavaExt}" />
</recipe>
