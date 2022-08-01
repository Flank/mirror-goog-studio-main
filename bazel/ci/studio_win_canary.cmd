@rem This script performs a 'bazel build' to act as a canary check
@rem for Android Build Launchcontrol releases. AB Builders running newer
@rem releases run this script to verify our CI integration has no regressions.
@rem We 'build' instead of 'test' to reduce our load on RBE. If building works,
@rem we can be highly confident testing works since all tests are executed
@rem remotely.
setlocal enabledelayedexpansion
set PATH=c:\tools\msys64\usr\bin;%PATH%

@rem The current directory the executing script is in.
set SCRIPTDIR=%~dp0
call :normalize_path "%SCRIPTDIR%..\..\..\.." BASEDIR

@rem Expected arguments:
set OUTDIR=%1
set DISTDIR=%2
set BUILDNUMBER=%3

@rem Generate a UUID for use as the Bazel invocation ID
for /f "tokens=*" %%f in ('uuidgen') do (
  set INVOCATIONID=%%f
)
if exist %DISTDIR%\ (
  echo "<head><meta http-equiv="refresh" content="0; url='https://source.cloud.google.com/results/invocations/%INVOCATIONID%'" /></head>" > %DISTDIR%\upsalite_build_results.html
)
set USE_BAZEL_VERSION=last_rc
call %BASEDIR%\tools\base\bazel\bazel.cmd ^
  --max_idle_secs=10 ^
  build ^
  --config=ci ^
  --build_tag_filters=-no_windows ^
  --invocation_id=%INVOCATIONID% ^
  --build_event_binary_file=%DISTDIR%\bazel-%BUILDNUMBER%.bes ^
  --define=meta_android_build_number=%BUILD_NUMBER% ^
  --tool_tag=studio-win-canary ^
  --verbose_failures ^
  -- ^
 //tools/...

set /a EXITCODE=%ERRORLEVEL%

exit /b %EXITCODE%

@rem Normalizes a path from Arg 1 and store the result into Arg 2.
:normalize_path
  set %2=%~dpfn1
  exit /b
