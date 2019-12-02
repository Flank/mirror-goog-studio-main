<globals>
    <global id="topOut" value="." />
    <global id="projectOut" value="." />
    <global id="postprocessingSupported" type="boolean" value="false" />
    <global id="improvedTestDeps" type="boolean" value="${(compareVersionsIgnoringQualifiers(gradlePluginVersion, '3.0.0') >= 0)?string}" />

    <#assign useAndroidX=addAndroidXSupport!false>
    <global id="useAndroidX" type="boolean" value="${useAndroidX?string}" />
</globals>
