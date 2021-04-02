@rem This script performs a 'bazel build' to act as a canary check
@rem for Android Build Launchcontrol releases. AB Builders running newer
@rem releases run this script to verify our CI integration has no regressions.
@rem We 'build' instead of 'test' to reduce our load on RBE. If building works,
@rem we can be highly confident testing works since all tests are executed
@rem remotely.
setlocal enabledelayedexpansion
set PATH=c:\tools\msys64\usr\bin;%PATH%
@rem Expected arguments:
set OUTDIR=%1
set DISTDIR=%2
set BUILDNUMBER=%3

set SCRIPTDIR=%~dp0
CALL :NORMALIZE_PATH "%SCRIPTDIR%..\..\..\.."
set BASEDIR=%RETVAL%

@rem Generate a UUID for the Bazel invocation ID
for /F "tokens=*" %%F in ('uuidgen') DO (
  set INVOCATIONID=%%F
)

CALL %BASEDIR%/tools/base/bazel/bazel.cmd ^
 --max_idle_secs=10 ^
 build ^
 --config=dynamic ^
 --build_tag_filter=-no_windows ^
 --invocation_id=%INVOCATIONID%
 --build_event_binary_file=%DISTDIR%\bazel-%BUILDNUMBER%.bes ^
 //tools/...

set EXITCODE=%errorlevel%

echo "<meta http-equiv="refresh" content="0; URL='https://source.cloud.google.com/results/invocations/%INVOCATIONID%'"/>" > %DISTDIR%\upsalite_build_results.html

EXIT \B %EXITCODE%

@rem HELPER FUNCTIONS
:NORMALIZE_PATH
  SET RETVAL=%~dpfn1
  EXIT /B
