<?xml version="1.0"?>
<globals>
    <global id="topOut" value="." />
    <global id="projectOut" value="." />
    <global id="manifestOut" value="${manifestDir}" />
    <global id="srcOut" value="${srcDir}/${slashedPackageName(packageName)}" />
    <global id="nativeSrcOut" value="${escapeXmlAttribute(projectOut)}/src/main/cpp" />
    <global id="testOut" value="androidTest/${slashedPackageName(packageName)}" />
    <global id="unitTestOut" value="${escapeXmlAttribute(projectOut)}/src/test/java/${slashedPackageName(packageName)}" />
    <global id="resOut" value="${resDir}" />
    <global id="buildToolsVersion" value="18.0.1" />
    <global id="gradlePluginVersion" value="0.6.+" />
    <global id="unitTestsSupported" type="boolean" value="${(compareVersions(gradlePluginVersion, '1.1.0') >= 0)?string}" />

    <global id="isLibraryProject" type="boolean" value="${(!(isInstantApp!false) && (isLibraryProject!false))?string}" />
    <global id="isApplicationProject" type="boolean" value="${(!(isInstantApp!false) && !(isLibraryProject!false))?string}" />
    <global id="isInstantAppProject" type="boolean" value="${(!(isInstantApp!false) && !(isLibraryProject!false))?string}" />

    <global id="hasSplitWrapper" type="boolean" value="${(isInstantApp!false)?string}" />
    <global id="hasInstantAppWrapper" type="boolean" value="${(isInstantApp!false)?string}" />

    <global id="baseLibName" type="string" value="base" />
    <global id="baseSplitName" type="string" value="basesplit" />
    <global id="splitName" type="string" value="split" />
    <global id="instantAppProjectName" type="string" value="instantapp" />
    <global id="monolithicAppProjectName" type="string" value="app" />
    <global id="instantAppPackageName" type="string" value="${packageName}.instantapp" />
    <global id="baseLibPackageName" type="string" value="${instantAppPackageName!packageName}.baselib" />

    <global id="splitOut" type="string" value="${escapeXmlAttribute(splitDir!('./'+splitName!""))}" />
    <global id="instantAppOut" type="string" value="${escapeXmlAttribute(instantAppDir!'./instantapp')}" />
    <global id="monolithicAppOut" type="string" value="${escapeXmlAttribute(monolithicAppDir!'./app')}" />
    <global id="baseLibOut" type="string" value="${escapeXmlAttribute(baseLibDir!'./base')}" />
    <global id="baseLibResOut" type="string" value="${escapeXmlAttribute(baseLibResDir!'./base/src/main/res')}" />
    <global id="baseSplitOut" type="string" value="${escapeXmlAttribute(baseSplitDir!'./basesplit')}" />
</globals>
