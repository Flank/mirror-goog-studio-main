<#macro instantiateFragmentAndViewModel fragmentPrefix withSafeArgs=false>
    <global id="navFragmentPrefix" value="${fragmentPrefix}" />
    <#assign navFragmentPrefix="${fragmentPrefix}" />
    <global id="firstFragmentClass" value="${underscoreToCamelCase(fragmentPrefix)}Fragment" />
    <global id="navViewModelClass" value="${underscoreToCamelCase(navFragmentPrefix)}ViewModel" />

    <#assign inputDir="root://activities/common/navigation/src" /> 
    <#assign escapedResOut="${escapeXmlAttribute(resOut)}" />
    <#assign escapedSrcOut="${escapeXmlAttribute(srcOut)}" />

    <#if withSafeArgs>
        <#assign secondFragmentPrefix="${fragmentPrefix}_second" />
        <#assign secondFragmentClass="${underscoreToCamelCase(secondFragmentPrefix)}Fragment" />
        <#assign secondFragmentLayoutName="fragment_${secondFragmentPrefix}" />
        <global id="secondFragmentLayoutName" value="${secondFragmentLayoutName}" />
        <global id="secondFragmentClass" value="${secondFragmentClass}" />
        <instantiate from="${inputDir}/res/layout/fragment_second.xml.ftl"
            to="${escapedResOut}/layout/${secondFragmentLayoutName}.xml" />
        <instantiate from="${inputDir}/ui/SecondFragment.${ktOrJavaExt}.ftl"
            to="${escapedSrcOut}/ui/${fragmentPrefix}/${secondFragmentClass}.${ktOrJavaExt}" />
        <instantiate from="${inputDir}/res/layout/fragment_first_with_safeargs.xml.ftl"
            to="${escapedResOut}/layout/fragment_${fragmentPrefix}.xml" />
        <instantiate from="${inputDir}/ui/FirstFragmentWithSafeArgs.${ktOrJavaExt}.ftl"
            to="${escapedSrcOut}/ui/${fragmentPrefix}/${underscoreToCamelCase(fragmentPrefix)}Fragment.${ktOrJavaExt}" />
    <#else>
        <instantiate from="${inputDir}/res/layout/fragment_first.xml.ftl"
            to="${escapedResOut}/layout/fragment_${fragmentPrefix}.xml" />
        <instantiate from="${inputDir}/ui/FirstFragment.${ktOrJavaExt}.ftl"
            to="${escapedSrcOut}/ui/${fragmentPrefix}/${underscoreToCamelCase(fragmentPrefix)}Fragment.${ktOrJavaExt}" />
    </#if>

    <instantiate from="${inputDir}/ui/ViewModel.${ktOrJavaExt}.ftl"
        to="${escapedSrcOut}/ui/${fragmentPrefix}/${underscoreToCamelCase(fragmentPrefix)}ViewModel.${ktOrJavaExt}" />
    <merge from="${inputDir}/res/values/strings.xml.ftl"
             to="${escapedResOut}/values/strings.xml" />
</#macro>

<#macro navigationDependencies>
    <dependency mavenUrl="android.arch.navigation:navigation-fragment:+" />
    <dependency mavenUrl="android.arch.navigation:navigation-ui:+" />
    <dependency mavenUrl="android.arch.lifecycle:extensions:+"/>
    <#if generateKotlin>
        <dependency mavenUrl="android.arch.navigation:navigation-fragment-ktx:+" />
        <dependency mavenUrl="android.arch.navigation:navigation-ui-ktx:+" />
    </#if>
    <!--
    navigation-ui depends on the stable version of design library. This is to remove the
    lint warning for the generated project may not use the same version of the support
    library.
    -->
    <#if !useAndroidX >
        <dependency mavenUrl="com.android.support:design:${buildApi}.+"/>
    </#if>
</#macro>

<#macro addSafeArgsPlugin>
    <#--
    Only use the Java version of the plugin to avoid Java and Kotlin version of the plugins are
    added in the same project.
    -->
    <apply plugin="androidx.navigation.safeargs" />
</#macro>

<#macro addSafeArgsPluginToClasspath>
    <#if useAndroidX>
        <classpath mavenUrl="androidx.navigation:navigation-safe-args-gradle-plugin:+"/>
    <#else>
        <classpath mavenUrl="android.arch.navigation:navigation-safe-args-gradle-plugin:+"/>
    </#if>
</#macro>
