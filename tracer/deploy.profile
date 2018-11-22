# A set of trace points to trace a build and deploy from studio
# Mainly gradle events, as studio is all done via code

# Main configuration step
Trace: org.gradle.configuration.DefaultBuildConfigurer::configure
# Main execution step
Trace: org.gradle.execution.DefaultBuildExecuter::execute
Trace: org.gradle.execution.DefaultBuildConfigurationActionExecuter::configure

# Android plugin
Trace: com.android.build.gradle.BasePlugin
Annotation: org.gradle.api.tasks.TaskAction

# Apk step
Trace: com.android.build.gradle.tasks.PackageAndroidArtifact::doIncrementalTaskAction
Trace: com.android.build.gradle.tasks.PackageAndroidArtifact::doFullTaskAction
Trace: com.android.tools.build.apkzlib.zip.ZFile::update
Trace: com.android.tools.build.apkzlib.zfile.ApkZFileCreator
Trace: com.android.tools.build.apkzlib.zfile.ApkZFileCreatorFactory
Trace: com.android.build.gradle.internal.packaging.IncrementalPackagerBuilder

# Signing
Trace: com.android.tools.build.apkzlib.sign.SigningExtension::isCurrentSignatureAsRequested 
Trace: com.android.tools.build.apkzlib.sign.SigningExtension::onOutputZipReadyForUpdate
Trace: com.android.tools.build.apkzlib.sign.SigningExtension::onOutputZipEntriesWritten

# Flush the daemon
Flush: org.gradle.internal.buildevents.BuildResultLogger::buildFinished

# Task executors
# All: Trace: org.gradle.api.internal.tasks.execution.*
Trace: org.gradle.api.internal.tasks.execution.SkipUpToDateTaskExecuter::execute
Trace: org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter::execute