<globals>
    <global id="topOut" value="." />
    <global id="projectOut" value="." />

    <global id="postprocessingSupported" type="boolean" value="${(compareVersions(gradlePluginVersion, '3.1.0-dev') >= 0)?string}" />
    <global id="unitTestsSupported" type="boolean" value="${(compareVersions(gradlePluginVersion, '1.1.0') >= 0)?string}" />
    <global id="improvedTestDeps" type="boolean" value="${(compareVersions(gradlePluginVersion, '3.0.0') >= 0)?string}" />
</globals>
