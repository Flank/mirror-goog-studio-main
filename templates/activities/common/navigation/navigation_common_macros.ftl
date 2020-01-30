<#macro instantiateFragmentAndViewModel fragmentPrefix >
    <global id="navFragmentPrefix" value="${fragmentPrefix}" />
    <#assign navFragmentPrefix="${fragmentPrefix}" />
    <global id="firstFragmentClass" value="${underscoreToCamelCase(fragmentPrefix)}Fragment" />
    <global id="navViewModelClass" value="${underscoreToCamelCase(navFragmentPrefix)}ViewModel" />

    <#assign inputDir="root://activities/common/navigation/src" /> 
    <#assign escapedResOut="${escapeXmlAttribute(resOut)}" />
    <#assign escapedSrcOut="${escapeXmlAttribute(srcOut)}" />

    <instantiate from="${inputDir}/res/layout/fragment_first.xml.ftl"
        to="${escapedResOut}/layout/fragment_${fragmentPrefix}.xml" />
    <instantiate from="${inputDir}/ui/FirstFragment.${ktOrJavaExt}.ftl"
        to="${escapedSrcOut}/ui/${fragmentPrefix}/${underscoreToCamelCase(fragmentPrefix)}Fragment.${ktOrJavaExt}" />

    <instantiate from="${inputDir}/ui/ViewModel.${ktOrJavaExt}.ftl"
        to="${escapedSrcOut}/ui/${fragmentPrefix}/${underscoreToCamelCase(fragmentPrefix)}ViewModel.${ktOrJavaExt}" />
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
