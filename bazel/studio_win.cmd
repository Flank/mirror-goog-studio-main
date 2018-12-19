@echo off
@rem Invoked by Android Build Launchcontrol for continuous builds.
@rem Windows Android Studio Remote Bazel Execution Script.

@rem Expected arguments:
set OUTDIR=%1
set DISTDIR=%2
set BUILDNUMBER=%3

set TESTTAGFILTERSPOST=-no_windows,-no_test_windows,-qa_sanity,-qa_fast,-qa_unreliable
set TESTTAGFILTERSPSQ=-no_windows,-no_test_windows,-qa_sanity,-qa_fast,-qa_unreliable,-no_psq
set TESTTAGFILTERS=%TESTTAGFILTERSPOST%

set CONFIGOPTIONSPOST=--config=local --config=remote_common --config=postsubmit
set CONFIGOPTIONSPSQ=--config=local --config=remote_common --config=presubmit
set CONFIGOPTIONS=%CONFIGOPTIONSPOST%

set AUTHCREDS=--auth_credentials=C:\buildbot\android-studio-alphasource.json

IF "%BUILDNUMBER:~0,1%"=="P" (
  set TESTTAGFILTERS=%TESTTAGFILTERSPSQ%
  set CONFIGOPTIONS=%CONFIGOPTIONSPSQ%
)

@rem The current directory the executing script is in.
set SCRIPTDIR=%~dp0
CALL :NORMALIZE_PATH "%SCRIPTDIR%..\..\.."
set BASEDIR=%RETVAL%

@rem Capture location of command.log  Will be out as unix filepath
FOR /F "tokens=*" %%F IN ('%SCRIPTDIR%bazel.cmd info %CONFIGOPTIONS% %AUTHCREDS% command_log') DO (
SET COMMANDLOG=%%F
)
@rem convert unix path to windows path using cygpath.
FOR /F "tokens=*" %%F IN ('cygpath --windows --absolute %COMMANDLOG%') DO (
SET COMMANDLOGLOC=%%F
)

echo "Called with the following:  OUTDIR=%OUTDIR%, DISTDIR=%DISTDIR%, BUILDNUMBER=%BUILDNUMBER%, SCRIPTDIR=%SCRIPTDIR%, BASEDIR=%BASEDIR%"
echo "Command Log Location: %COMMANDLOGLOC%"

@rem Run Bazel
CALL %SCRIPTDIR%bazel.cmd --max_idle_secs=60 test %CONFIGOPTIONS% %AUTHCREDS% --build_tag_filters=-no_windows --test_tag_filters=%TESTTAGFILTERS% -- //tools/base/... -//tools/base:coverage_report -//tools/base:coverage_report_collection_binary -//tools/base:coverage_report_source_and_classes -//tools/base:coverage_report_source_and_classes.map

SET EXITCODE=%errorlevel%

IF NOT EXIST %DISTDIR%\ GOTO ENDSCRIPT
@rem Grab the upsalite_id from the stdout of the bazel command.  This is captured in command.log
@rem We cheat here and use cygwin sed.  This is not easy with native Windows tools.
FOR /F "tokens=*" %%F IN ('sed -n -e "s/.*invocation_id: //p" %COMMANDLOGLOC%') DO (
SET UPSALITEID=%%F
)
echo "<meta http-equiv="refresh" content="0; URL='https://source.cloud.google.com/results/invocations/%UPSALITEID%" />" > %DISTDIR%\upsalite_test_results.html

:ENDSCRIPT
@rem Lets run bazel clean to make sure any file handles of bazel are properly shut down
CALL %SCRIPTDIR%bazel.cmd clean --expunge
@rem On windows we must explicitly shut down bazel.  Otherwise file handles remain open.
CALL %SCRIPTDIR%bazel.cmd shutdown
@rem We also must call the kill-processes.py python script and kill all processes still open
@rem within the src directory.  This is due to the fact go/ab builds must be removable after
@rem execution, and any open processes will prevent this removal on windows.
CALL %BASEDIR%\tools\vendor\adt_infra_internal\build\scripts\slave\kill-processes.cmd %BASEDIR%
EXIT /B %exitcode%

@rem HELPER FUNCTIONS
:NORMALIZE_PATH
  SET RETVAL=%~dpfn1
  EXIT /B
