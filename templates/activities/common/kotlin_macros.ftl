<#-- Macro used to add the necessary dependencies to support kotlin to
an app build.gradle -->

<#macro addKotlinPlugins>
<#if includeKotlinSupport!false>
<#compress>
apply plugin: 'kotlin-android'
apply plugin: 'kotlin-android-extensions'
</#compress>
</#if>
</#macro>

<#macro addKotlinDependencies>
<#if includeKotlinSupport!false>${getConfigurationName("compile")} "org.jetbrains.kotlin:kotlin-stdlib-jre7:$kotlin_version"</#if>
</#macro>