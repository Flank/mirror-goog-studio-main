set PATH=C:\Program Files\Java\jdk1.7.0_75\bin;%PATH%


:: Code under repo is checked out to %KOKORO_ARTIFACTS_DIR%\git.
:: The final directory name in this path is determined by the scm name specified
:: in the job configuration
cd %KOKORO_ARTIFACTS_DIR%\git\aosp-tools-base\game-tools\native\GameToolsWinLauncher

set VS_ROOT=%ProgramFiles(x86)%\Microsoft Visual Studio\2017\Community
:: Make MSBuild available
set PATH=%VS_ROOT%\MSBuild\15.0\Bin;%PATH%

msbuild /p:Configuration=Release /p:Platform=x64

exit %ERRORLEVEL%
