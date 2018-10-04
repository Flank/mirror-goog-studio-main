@echo off
@rem Invoked by Android Build Launchcontrol for continuous builds.
@rem Windows Android Studio Remote Bazel Execution Script.

@rem Expected arguments:
set OUTDIR="%1"
set DISTDIR="%2"
set BUILDNUMBER="%3"

@rem The current directory the executing script is in.
set SCRIPTDIR="%~dp0"

echo "Called with the following:  OUTDIR=%OUTDIR%, DISTDIR=%DISTDIR%, BUILDNUMBER=%BUILDNUMBER%, SCRIPTDIR=%SCRIPTDIR%"
