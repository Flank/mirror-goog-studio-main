@echo off
@rem Invoked by Android Build Launchcontrol for continuous builds.
@rem Windows Android Studio Remote Bazel Execution Script.

@rem Expected arguments:
set OUTDIR=%1
set DISTDIR=%2
set BUILDNUMBER=%3

@rem The current directory the executing script is in.
set SCRIPTDIR=%~dp0

@rem Capture location of command.log  Will be out as unix filepath
FOR /F "tokens=*" %%F IN ('%SCRIPTDIR%bazel.cmd info command_log') DO (
SET COMMANDLOG=%%F
)
@rem convert unix path to windows path using cygpath.
FOR /F "tokens=*" %%F IN ('cygpath --windows --absolute %COMMANDLOG%') DO (
SET COMMANDLOGLOC=%%F
)

echo "Called with the following:  OUTDIR=%OUTDIR%, DISTDIR=%DISTDIR%, BUILDNUMBER=%BUILDNUMBER%, SCRIPTDIR=%SCRIPTDIR%"
echo "Command Log Location: %COMMANDLOGLOC%"

@rem Run Bazel
CALL %SCRIPTDIR%bazel.cmd --max_idle_secs=60 --bazelrc=/dev/null test --config=postsubmit --config=local --config=remote_common --build_tag_filters=-no_windows --test_tag_filters=-no_windows,-no_test_windows,-no_psq,-qa_sanity,-qa_fast,-qa_unreliable --auth_credentials=C:\buildbot\android-studio-alphasource.json -- //tools/base/...

SET EXITCODE=%errorlevel%

IF NOT EXIST %DISTDIR%\ GOTO ENDSCRIPT
@rem Grab the upsalite_id from the stdout of the bazel command.  This is captured in command.log
@rem We cheat here and use cygwin sed.  This is not easy with native Windows tools.
FOR /F "tokens=*" %%F IN ('sed -n -e "s/.*invocation_id: //p" %COMMANDLOGLOC%') DO (
SET UPSALITEID=%%F
)
echo "<meta http-equiv="refresh" content="0; URL='https://source.cloud.google.com/results/invocations/%UPSALITEID%" />" > %DISTDIR%\upsalite_test_results.html

:ENDSCRIPT
@rem On windows we must explicitly shut down bazel.  Otherwise file handles remain open.
CALL %SCRIPTDIR%bazel.cmd shutdown
EXIT /B %exitcode%
