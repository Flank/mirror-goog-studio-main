<#macro instantiateFragmentAndViewModel fragmentPrefix >
  <global id="navFragmentPrefix" value="${fragmentPrefix}" />
  <#assign navFragmentPrefix="${fragmentPrefix}" />
  <global id="navFragmentLayout" value="fragment_${fragmentPrefix}" />
  <#assign navFragmentLayout="fragment_${fragmentPrefix}" />
  <global id="navFragmentClass" value="${underscoreToCamelCase(navFragmentPrefix)}Fragment" />
  <global id="navViewModelClass" value="${underscoreToCamelCase(navFragmentPrefix)}ViewModel" />
  <global id="navFragmentBinding" value="${underscoreToCamelCase(navFragmentLayout)}Binding" />

  <instantiate from="root://activities/common/navigation/navigation_drawer/res/layout/fragment_viewmodel.xml.ftl"
      to="${escapeXmlAttribute(resOut)}/layout/fragment_${fragmentPrefix}.xml" />
  <instantiate from="root://activities/common/navigation/src/ui/SimpleFragment.${ktOrJavaExt}.ftl"
      to="${escapeXmlAttribute(srcOut)}/ui/${fragmentPrefix}/${underscoreToCamelCase(fragmentPrefix)}Fragment.${ktOrJavaExt}" />
  <instantiate from="root://activities/common/navigation/src/ui/SimpleViewModel.${ktOrJavaExt}.ftl"
      to="${escapeXmlAttribute(srcOut)}/ui/${fragmentPrefix}/${underscoreToCamelCase(fragmentPrefix)}ViewModel.${ktOrJavaExt}" />

</#macro>
