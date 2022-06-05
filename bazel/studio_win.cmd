@rem Invoked by Android Build Launchcontrol for continuous builds.
@rem Windows Android Studio Remote Bazel Execution Script.
setlocal enabledelayedexpansion
set PATH=c:\tools\msys64\usr\bin;%PATH%

@rem The current directory the executing script is in.
set SCRIPTDIR=%~dp0
call :normalize_path "%SCRIPTDIR%..\..\.." BASEDIR

@rem Read commandline arguments with a loop
set /a _pos_arg=1
:read_args
  if -%1-==-- goto end_read_args
  @rem Extract flags in the loop
  if %1==--detect_flakes (
    set /a DETECT_FLAKES=1
  ) else (
    @rem Assign to variables with predefined name "_arg#"
    set /a _pos_arg+=1
    set _arg%_pos_arg%=%1
  )
  shift
  goto read_args
:end_read_args

@rem Positional arguments:
set OUTDIR=%_arg1%
set DISTDIR=%_arg2%
set BUILDNUMBER=%_arg3%

if not defined DISTDIR (
  set DISTDIR=%TEMP%
)

if not defined BUILDNUMBER (
  set BUILD_TYPE=LOCAL
) else if "%BUILDNUMBER:~0,1%"=="P" (
  @rem It is a presubmit build if the build number starts with "P"
  set BUILD_TYPE=PRESUBMIT
) else (
  set BUILD_TYPE=POSTSUBMIT
)

@echo "Called with: OUTDIR=%OUTDIR%, DISTDIR=%DISTDIR%, BUILDNUMBER=%BUILDNUMBER%, SCRIPTDIR=%SCRIPTDIR%, BASEDIR=%BASEDIR%"
@echo "Build type: %BUILD_TYPE%"

:run_bazel_test
setlocal
  @rem Run tests multiple times to aid flake detection.
  if "%DETECT_FLAKES%"=="1" (
    set ATTEMPTS=--flaky_test_attempts=3
    set NOCACHE=--nocache_test_results
    set CONDITIONAL_FLAGS=!ATTEMPTS! !NOCACHE!
  ) else if %BUILD_TYPE%==POSTSUBMIT (
    set NOCACHE=--nocache_test_results
    set FLAKY_ATTEMPTS=--flaky_test_attempts=2
    set CONDITIONAL_FLAGS=!NOCACHE! !FLAKY_ATTEMPTS! !ANTS!
  )

  set TESTTAGFILTERS=-no_windows,-no_test_windows,-qa_smoke,-qa_fast,-qa_unreliable,-perfgate

  @rem Generate a UUID for use as the Bazel invocation ID
  for /f "tokens=*" %%f in ('uuidgen') do (
    set INVOCATIONID=%%f
  )
  if exist %DISTDIR%\ (
    echo ^<head^>^<meta http-equiv="refresh" content="0; url='https://fusion2.corp.google.com/invocations/%INVOCATIONID%'" /^>^</head^> > %DISTDIR%\upsalite_test_results.html
  )

  set TARGETS=
  for /f %%i in (%SCRIPTDIR%targets.win) do set TARGETS=!TARGETS! %%i

  @rem For presubmit builds, try download bazelrc to deflake builds
  if %BUILD_TYPE%==PRESUBMIT (
    call :download_flake_retry_rc auto-retry.bazelrc %DISTDIR%\flake-retry BAZELRC
    if defined BAZELRC set BAZELRC_FLAGS=--bazelrc=!BAZELRC!
  )

  @echo studio_win.cmd time: %time%
  @rem Run Bazel
  call %SCRIPTDIR%bazel.cmd ^
  --max_idle_secs=60 ^
  %BAZELRC_FLAGS% ^
  test ^
  --config=ci ^
  --config=ants ^
  --tool_tag=studio_win.cmd ^
  --build_tag_filters=-no_windows ^
  --invocation_id=%INVOCATIONID% ^
  --build_event_binary_file=%DISTDIR%\bazel-%BUILDNUMBER%.bes ^
  --test_tag_filters=%TESTTAGFILTERS% ^
  --build_metadata=ANDROID_BUILD_ID=%BUILDNUMBER% ^
  --build_metadata=ab_build_id=%BUILDNUMBER% ^
  --build_metadata=ab_target=studio-win ^
  --profile=%DISTDIR%\winprof%BUILDNUMBER%.json.gz ^
  %CONDITIONAL_FLAGS% ^
  -- ^
  //tools/base/profiler/native/trace_processor_daemon ^
  %TARGETS%
endlocal & set /a EXITCODE=%ERRORLEVEL%

@echo studio_win.cmd time: %time%

if not exist %DISTDIR%\ goto endscript

@echo studio_win.cmd time: %time%

@rem copy skia parser artifact to dist dir
copy %BASEDIR%\bazel-bin\tools\base\dynamic-layout-inspector\skia\skiaparser.zip %DISTDIR%
if errorlevel 1 (
  set /a EXITCODE=1
  goto endscript
)
@rem copy trace processor daemon artifact to dist dir
copy %BASEDIR%\bazel-bin\tools\base\profiler\native\trace_processor_daemon\trace_processor_daemon.exe %DISTDIR%
if errorlevel 1 (
  set /a EXITCODE=1
  goto endscript
)

@rem build Windows Launcher
call %BASEDIR%\tools\base\intellij-native\build-win-launcher.cmd %OUTDIR% %DISTDIR% %BUILDNUMBER%
if errorlevel 1 (
  set /a EXITCODE=1
  goto endscript
)

@echo studio_win.cmd time: %time%

:collect_logs
setlocal
  if %BUILD_TYPE%==POSTSUBMIT (
    set PERFGATE_ARG=-perfzip %DISTDIR%\perfgate_data.zip
  )

  call %SCRIPTDIR%bazel.cmd ^
  --max_idle_secs=60 ^
  run //tools/vendor/adt_infra_internal/rbe/logscollector:logs-collector ^
  --config=ci ^
  -- ^
  -bes %DISTDIR%\bazel-%BUILDNUMBER%.bes ^
  -testlogs %DISTDIR%\logs\junit ^
  %PERFGATE_ARG%
endlocal

if errorlevel 1 (
  @echo Bazel logs-collector failed
  if %BUILD_TYPE%==POSTSUBMIT (
    set /a EXITCODE=1
  )
  goto endscript
)

@echo studio_win.cmd time: %time%

:endscript
@rem On windows we must explicitly shut down bazel. Otherwise file handles remain open.
@echo studio_win.cmd time: %time%
call %SCRIPTDIR%bazel.cmd shutdown
@echo studio_win.cmd time: %time%

set /a BAZEL_EXITCODE_TEST_FAILURES=3

if %BUILD_TYPE%==POSTSUBMIT (
  if %EXITCODE% equ %BAZEL_EXITCODE_TEST_FAILURES% (
    exit /b 0
  )
)
exit /b %EXITCODE%

@rem Normalizes a path from Arg 1 and store the result into Arg 2.
:normalize_path
  set %2=%~dpfn1
  exit /b

@rem Downloads a file (Arg 1) from the known-flakes directory on GCS, store it to a directory (Arg 2)
@rem and save the full path to a variable (Arg 3) if successful.
:download_flake_retry_rc
  setlocal
  set GCS_PATH=gs://adt-byob/known-flakes/studio-win/%1
  mkdir %2
  call gsutil cp %GCS_PATH% %2\%1
  if %ERRORLEVEL% neq 0 (
    endlocal & exit /b
  )
  endlocal & set %3=%2\%1
  exit /b
