@setlocal EnableDelayedExpansion EnableExtensions

@if not exist "C:\tools\msys64" (
  @echo.
  @echo NOTE: Bazel for Windows currently hardcodes "C:\tools\msys64", but we
  @echo could not find an installation of msys at this path on your machine.
  @echo Consider moving your installation if you have issues using Bazel.
  @echo.
  @echo See also: https://github.com/bazelbuild/bazel/issues/2447
)

@call:defined_or_fallback BAZEL_PYTHON "C:\python_27_amd64\files\python.exe"
@call:defined_or_fallback BAZEL_SH "C:\tools\msys64\usr\bin\bash.exe"
@call:defined_or_fallback GCC "C:\tools\msys64\usr\bin\gcc.exe"

@rem Bazel for Windows currently requires Visual Studio to be installed, and
@rem will attempt to autofind it if not. We could supply a default here, but we
@rem can't know for sure exactly which version the user has installed (12.0?
@rem 14.0?), so we don't supply a fallback value for now. Instead, Bazel should
@rem show its own auto-configuration warnings.

@rem Delegate actual work to python script in this same directory
@echo.
@python %~dp0\bazel %*

@rem Exit before running into functions (defined below)
@exit /B %ERRORLEVEL%

::functions

:defined_or_fallback
@if not defined %~1 (
  @set "%~1=%~2"
  @echo.
  @echo NOTE: Environment variable "%~1" not set.
  @echo       Defaulting to "%~2"
  @echo       If this default is invalid, please manually define "%~1"
)
@exit /B 0