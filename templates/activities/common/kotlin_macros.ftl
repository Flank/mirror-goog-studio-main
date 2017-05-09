<#-- Macros used for kotlin code
Note 1: Build API 26 R2 introduces generics that requires the template
to be modified for kotlin.
Note 2: buildApiRevision is set only for preview releases so will be 0
for a public release. Here (buildApiRevision!0) != 1 is to ensure that this
code continues to work after 26 is final.
-->
<#macro findViewById id type="View">
<#compress>
<#if buildApi gt 26 ||
    (buildApi == 26 && (buildApiRevision!0) != 1)>
findViewById<${type}>(${id})
<#else>
findViewById(${id})<#if type != "View"> as ${type}</#if>
</#if>
</#compress>
</#macro>