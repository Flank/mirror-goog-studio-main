include ':${projectName}'
<#if hasSplitWrapper>include ':${splitName}'</#if>
<#if hasInstantAppWrapper>include ':${instantAppProjectName}', ':${monolithicAppProjectName}', ':${baseLibName}', ':${baseSplitName}'</#if>
