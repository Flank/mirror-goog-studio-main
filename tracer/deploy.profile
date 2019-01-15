# A set of trace points to trace a build and deploy from studio
# Mainly gradle events, as studio is all done via code

# Main configuration step
Trace: org.gradle.configuration.DefaultBuildConfigurer::configure
# Main execution step
Trace: org.gradle.execution.DefaultBuildExecuter::execute
Trace: org.gradle.execution.DefaultBuildConfigurationActionExecuter::configure

# Android plugin IDEA side
Trace: com.android.builder.sdk.PlatformLoader
Trace: com.android.builder.sdk.SdkLoader
Trace: com.android.sdklib.repository.AndroidSdkHandler::*
Trace: com.android.build.gradle.internal.SdkHandler
Trace: com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider::*
Trace: com.android.tools.idea.gradle.run.DefaultGradleBuilder::*
Trace: com.android.tools.idea.gradle.run.DefaultGradleTaskRunner::*
Trace: com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker::*

# Android plugin gradle side
Trace: com.android.build.gradle.BasePlugin
# START REPO MANAGER (Bug #122905819)
Trace: com.android.repository.impl.manager.RepoManagerImpl::*
Trace: com.android.repository.impl.manager.LocalRepoLoaderImpl::*
# END REPO MANAGER
Annotation: org.gradle.api.tasks.TaskAction
Trace: com.android.builder.internal.aapt.v2.Aapt2DaemonImpl::*
Trace: com.android.builder.internal.aapt.compiler.*

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
Trace: org.gradle.launcher.daemon.server.exec.ExecuteBuild::*

# IDEA
Trace: com.intellij.util.messages.impl.MessageBusImpl::sendMessage
Trace: com.intellij.execution.impl.ExecutionManagerImpl::*
