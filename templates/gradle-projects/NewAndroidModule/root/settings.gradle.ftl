include ':${projectName}'
<#if hasInstantAppWrapper>include ':${monolithicAppProjectName}', ':${baseLibName}' // ':${instantAppProjectName}',</#if>
