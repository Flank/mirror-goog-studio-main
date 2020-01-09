<#-- Macro used to add the necessary dependencies to support kotlin to
an app build.gradle -->

<#macro addKotlinPlugins>
<#if generateKotlin>
apply plugin: 'kotlin-android'
apply plugin: 'kotlin-android-extensions'
</#if>
</#macro>

<#macro addKotlinDependencies>
<#if generateKotlin>${getConfigurationName("compile")} "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"</#if>
</#macro>

<#macro setKotlinVersion>
  <setExtVar name="kotlin_version" value="${kotlinVersion}" />
  <classpath mavenUrl="org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version" />
</#macro>

// TODO: <apply plugin /> Is adding the dependencies at the *end* of build.gradle
// TODO: The two macros above, addKotlinPlugins and addKotlinDependencies, are duplicating the work of addAllKotlinDependencies, when
//       creating a new project (isNewModule == true). The only reason is the above bug on <apply plugin />
<#macro addAllKotlinDependencies>
  <#if !isNewModule && ((language!'Java')?string == 'Kotlin')>
    <apply plugin="kotlin-android" />
    <apply plugin="kotlin-android-extensions" />
    <#if !hasDependency('org.jetbrains.kotlin:kotlin-stdlib')>
        <dependency mavenUrl="org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"/>
        <@setKotlinVersion />
    </#if>
  </#if>
</#macro>

<#macro addKotlinToBaseProject>
  <#if !(isNewProject!false) && (language!'Java')?string == 'Kotlin'>
    <@setKotlinVersion />
  </#if>
</#macro>
