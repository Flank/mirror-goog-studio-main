<#macro instantiateFragmentAndViewModel fragmentPrefix >
    <global id="navFragmentPrefix" value="${fragmentPrefix}" />
    <#assign navFragmentPrefix="${fragmentPrefix}" />
    <global id="navFragmentLayout" value="fragment_${fragmentPrefix}" />
    <#assign navFragmentLayout="fragment_${fragmentPrefix}" />
    <global id="navFragmentClass" value="${underscoreToCamelCase(navFragmentPrefix)}Fragment" />
    <global id="navViewModelClass" value="${underscoreToCamelCase(navFragmentPrefix)}ViewModel" />
    <global id="navFragmentBinding" value="${underscoreToCamelCase(navFragmentLayout)}Binding" />

    <#assign inputDir="root://activities/common/navigation/src" /> 
    <instantiate from="${inputDir}/res/layout/fragment_viewmodel.xml.ftl"
        to="${escapeXmlAttribute(resOut)}/layout/fragment_${fragmentPrefix}.xml" />
    <instantiate from="${inputDir}/ui/SimpleFragment.${ktOrJavaExt}.ftl"
        to="${escapeXmlAttribute(srcOut)}/ui/${fragmentPrefix}/${underscoreToCamelCase(fragmentPrefix)}Fragment.${ktOrJavaExt}" />
    <instantiate from="${inputDir}/ui/SimpleViewModel.${ktOrJavaExt}.ftl"
        to="${escapeXmlAttribute(srcOut)}/ui/${fragmentPrefix}/${underscoreToCamelCase(fragmentPrefix)}ViewModel.${ktOrJavaExt}" />

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
    <#if generateKotlin>
        <apply plugin="androidx.navigation.safeargs.kotlin" />
    <#else>
        <apply plugin="androidx.navigation.safeargs" />
    </#if>
</#macro>

<#macro addSafeArgsPluginToClasspath>
    <#if useAndroidX>
        <classpath mavenUrl="androidx.navigation:navigation-safe-args-gradle-plugin:+"/>
    <#else>
        <#--
          TODO: Single '+' retrieves 2.x.x version, but android.arch.navigation dependency doesn'
          t have that version (androidx.navigation has). The classpath directive should convert
          the single '+' as the valid largest version number.
        -->
        <classpath mavenUrl="android.arch.navigation:navigation-safe-args-gradle-plugin:1.+"/>
    </#if>
</#macro>
