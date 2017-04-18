include ':${projectName}'
<#if hasMonolithicAppWrapper>include ':${monolithicAppProjectName}'</#if>
<#if hasInstantAppWrapper>include ':${baseLibName}', ':${instantAppProjectName}'</#if>
