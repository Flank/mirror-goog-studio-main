<#-- Macro used to add the necessary dependencies to support kotlin to
an app build.gradle -->

<#macro addKotlinPlugins>
<#if generateKotlin>
<#compress>
apply plugin: 'kotlin-android'
apply plugin: 'kotlin-android-extensions'
</#compress>
</#if>
</#macro>

<#macro addKotlinDependencies>
<#if generateKotlin>${getConfigurationName("compile")} "org.jetbrains.kotlin:kotlin-stdlib-jre7:$kotlin_version"</#if>
</#macro>

// TODO: There is not a way (?) to add data to the "top" level gradle file -> "ext.kotlin_version = 'x.x.x'",
//       classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version" and mavenCentral()
// TODO: <apply plugin /> Is adding the dependencies at the *end* of build.gradle
// TODO: The two macros above, addKotlinPlugins and addKotlinDependencies, are duplicating the work of addAllKotlinDependencies, when
//       creating a new project (isNewProject == true). The only reason is the above bug on <apply plugin />
<#macro addAllKotlinDependencies>
  <#if !isNewProject && (language!'Java')?string == 'Kotlin'>
    <apply plugin="kotlin-android" />
    <apply plugin="kotlin-android-extensions" />
    <dependency mavenUrl="org.jetbrains.kotlin:kotlin-stdlib-jre7:$kotlin_version"/>
  </#if>
</#macro>