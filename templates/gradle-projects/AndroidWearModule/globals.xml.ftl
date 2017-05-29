<?xml version="1.0"?>
<globals>
    <global id="topOut" value="." />
    <global id="projectOut" value="." />
    <global id="manifestOut" value="${manifestDir}" />
    <global id="srcOut" value="${srcDir}/${slashedPackageName(packageName)}" />
    <global id="resOut" value="${resDir}" />
    <global id="generateKotlin" type="boolean"
            value="${((includeKotlinSupport!false) || (language!'Java')?string == 'Kotlin')?string}" />
</globals>
