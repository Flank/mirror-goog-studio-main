@rem Invoked by Android Build Launchcontrol for continuous builds.
@rem Windows Android Studio Remote Bazel Execution Script.
setlocal enabledelayedexpansion
set PATH=c:\tools\msys64\usr\bin;%PATH%
@rem Expected arguments:
set OUTDIR=%1
set DISTDIR=%2
set BUILDNUMBER=%3

set TESTTAGFILTERS=-no_windows,-no_test_windows,-qa_sanity,-qa_fast,-qa_unreliable,-perfgate_only

@rem The current directory the executing script is in.
set SCRIPTDIR=%~dp0
CALL :NORMALIZE_PATH "%SCRIPTDIR%..\..\.."
set BASEDIR=%RETVAL%

@rem Generate a UUID for use as the Bazel invocation ID
FOR /F "tokens=*" %%F IN ('uuidgen') DO (
SET INVOCATIONID=%%F
)

echo "Called with the following:  OUTDIR=%OUTDIR%, DISTDIR=%DISTDIR%, BUILDNUMBER=%BUILDNUMBER%, SCRIPTDIR=%SCRIPTDIR%, BASEDIR=%BASEDIR%"

set TARGETS=
for /f %%i in (%SCRIPTDIR%targets.win) do set TARGETS=!TARGETS! %%i

@echo studio_win.cmd time: %time%
@rem Run Bazel
CALL %SCRIPTDIR%bazel.cmd ^
 --max_idle_secs=60 ^
 test ^
 --keep_going ^
 --config=remote ^
 --build_tag_filters=-no_windows ^
 --invocation_id=%INVOCATIONID% ^
 --build_event_binary_file=%DISTDIR%\bazel-%BUILDNUMBER%.bes ^
 --test_tag_filters=%TESTTAGFILTERS% ^
 --profile=%DISTDIR%\winprof%BUILDNUMBER%.json ^
 --discard_analysis_cache ^
 -- //tools/base/bazel:perfgate_logs_collector ^
 %TARGETS%

SET EXITCODE=%errorlevel%
@echo studio_win.cmd time: %time%

IF NOT EXIST %DISTDIR%\ GOTO ENDSCRIPT

echo "<meta http-equiv="refresh" content="0; URL='https://source.cloud.google.com/results/invocations/%INVOCATIONID%'" />" > %DISTDIR%\upsalite_test_results.html
@echo studio_win.cmd time: %time%

@rem copy skia parser artifact to dist dir
copy %BASEDIR%\bazel-bin\tools\base\dynamic-layout-inspector\skiaparser.zip %DISTDIR%
@echo studio_win.cmd time: %time%

@rem Extract perfgate data
%BASEDIR%\bazel-bin\tools\base\bazel\perfgate_logs_collector.exe %BASEDIR%\bazel-testlogs %DISTDIR%\bazel-%BUILDNUMBER%.bes %DISTDIR%\bazel-%BUILDNUMBER%.bes.zip %DISTDIR%\bazel-%BUILDNUMBER%.bes.txt

@echo studio_win.cmd time: %time%

cd %BASEDIR%\bazel-testlogs
FOR /F "tokens=*" %%F IN ('C:\cygwin64\bin\find.exe . -type f -name "*outputs.zip"') DO (
  C:\cygwin64\bin\zip.exe -ur %DISTDIR%\perfgate_data.zip %%F
)

@echo studio_win.cmd time: %time%

@rem until bazel clean is fixed on windows, remove perfgate data amanually.
CALL del /s /q outputs.zip
@echo studio_win.cmd time: %time%

@rem We must cd back into %BASEDIR% so bazel config files are properly located.
cd %BASEDIR%

:ENDSCRIPT
@rem We will explicitly clear the Bazel cache between runs to keep data hermetic.
@echo studio_win.cmd time: %time%
CALL %SCRIPTDIR%bazel.cmd clean --expunge
@rem On windows we must explicitly shut down bazel.  Otherwise file handles remain open.
@echo studio_win.cmd time: %time%
CALL %SCRIPTDIR%bazel.cmd shutdown
@echo studio_win.cmd time: %time%
@rem We also must call the kill-processes.py python script and kill all processes still open
@rem within the src directory.  This is due to the fact go/ab builds must be removable after
@rem execution, and any open processes will prevent this removal on windows.
CALL %BASEDIR%\tools\vendor\adt_infra_internal\build\scripts\slave\kill-processes.cmd %BASEDIR%
@echo studio_win.cmd time: %time%
EXIT /B %exitcode%

@rem HELPER FUNCTIONS
:NORMALIZE_PATH
  SET RETVAL=%~dpfn1
  EXIT /B
