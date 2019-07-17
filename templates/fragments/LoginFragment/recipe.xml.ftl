<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <dependency mavenUrl="com.android.support:appcompat-v7:${buildApi}.+" />
    <dependency mavenUrl="com.android.support:design:${buildApi}.+" />
    <dependency mavenUrl="com.android.support:support-annotations:${buildApi}.+" />
    <dependency mavenUrl="com.android.support.constraint:constraint-layout:+" />
    <dependency mavenUrl="android.arch.lifecycle:extensions:+"/>

    <#assign escapedResOut="${escapeXmlAttribute(resOut)}">
    <#assign escapedSrcOut="${escapeXmlAttribute(srcOut)}">

    <merge from="root/res/values/dimens.xml.ftl"
             to="${escapedResOut}/values/dimens.xml" />

    <merge from="root/res/values/strings.xml.ftl"
             to="${escapedResOut}/values/strings.xml" />

    <instantiate from="root/res/layout/fragment_login.xml.ftl"
                   to="${escapedResOut}/layout/${layoutName}.xml" />

    <instantiate from="root/src/app_package/ui/login/LoginFragment.${ktOrJavaExt}.ftl"
                   to="${escapedSrcOut}/ui/login/${fragmentClass}.${ktOrJavaExt}" />
    <instantiate from="root/src/app_package/ui/login/LoginViewModel.${ktOrJavaExt}.ftl"
                       to="${escapedSrcOut}/ui/login/LoginViewModel.${ktOrJavaExt}" />
    <instantiate from="root/src/app_package/ui/login/LoginViewModelFactory.${ktOrJavaExt}.ftl"
                       to="${escapedSrcOut}/ui/login/LoginViewModelFactory.${ktOrJavaExt}"/>
    <instantiate from="root/src/app_package/data/model/LoggedInUser.${ktOrJavaExt}.ftl"
                       to="${escapedSrcOut}/data/model/LoggedInUser.${ktOrJavaExt}"/>
    <instantiate from="root/src/app_package/data/LoginDataSource.${ktOrJavaExt}.ftl"
                       to="${escapedSrcOut}/data/LoginDataSource.${ktOrJavaExt}"/>
    <instantiate from="root/src/app_package/data/LoginRepository.${ktOrJavaExt}.ftl"
                       to="${escapedSrcOut}/data/LoginRepository.${ktOrJavaExt}"/>
    <instantiate from="root/src/app_package/data/Result.${ktOrJavaExt}.ftl"
                       to="${escapedSrcOut}/data/Result.${ktOrJavaExt}"/>
    <instantiate from="root/src/app_package/ui/login/LoginFormState.${ktOrJavaExt}.ftl"
                           to="${escapedSrcOut}/ui/login/LoginFormState.${ktOrJavaExt}"/>
    <instantiate from="root/src/app_package/ui/login/LoginResult.${ktOrJavaExt}.ftl"
                               to="${escapedSrcOut}/ui/login/LoginResult.${ktOrJavaExt}"/>
    <instantiate from="root/src/app_package/ui/login/LoggedInUserView.${ktOrJavaExt}.ftl"
                                  to="${escapedSrcOut}/ui/login/LoggedInUserView.${ktOrJavaExt}"/>
    <open file="${escapedSrcOut}/${fragmentClass}.${ktOrJavaExt}" />
    <open file="${escapedResOut}/layout/${layoutName}.xml" />
</recipe>
