@rem  Builds Android Studio Windows launcher
@rem  Usage:
@rem   build-win-launcher.cmd  <out_dir> <dist_dir> <build_number>
@rem The binary is built in <out_dir>, and launcher.exe artifact copied to  <dist_dir>

@setlocal enabledelayedexpansion

set OUTDIR=%1
set DISTDIR=%2
set BUILDNUMBER=%3
set SCRIPT_DIR=%~dp0
for %%F in ("%SCRIPTDIR%..\..") do set TOP=%%~dpF
set CMAKE="C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake"
set JDK_11_x64=%TOP%prebuilts\studio\jdk\jdk11\win\

cd %OUTDIR%
mkdir WinLauncher
cd WinLauncher
set PATH=%JDK_11_x64%include;%PATH%
%CMAKE% %TOP%tools\idea\native\WinLauncher
%CMAKE% --build . --config Release -A x64
xcopy /i /e Release\WinLauncher.exe %DISTDIR%