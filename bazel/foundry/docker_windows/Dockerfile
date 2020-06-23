# This file is a modified version of the default image provided by RBE team.
# Please see https://foundry-windows-internal.googlesource.com/dev/+/master/docker/Dockerfile
# for original file.

# This Dockerfile creates an image that:
# - Has the correct MTU setting for networking from inside the container to work.
# - Has Visual Studio 2015 Build Tools installed.
# - Has msys2 + git, curl, zip, unzip installed.
# - Has Python 2.7 installed.
# - Has Bazel installed.

# Use the latest Windows Server Core image.
# Originally this was supposed to be :ltsc2019, but it seems like there was an issue with that build at the time (6/2020),
# where MSVC silently fails to install.
# This build is a little more recent. Once ltsc2019 is updated, probably we could start using it again.

FROM mcr.microsoft.com/windows/servercore:10.0.17763.1040

SHELL ["powershell.exe", "-ExecutionPolicy", "Bypass", "-Command", "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; $VerbosePreference = 'Continue';"]

# TODO(b/112379377): Workaround until bug is fixed.
RUN netsh interface ipv4 set subinterface \"vEthernet (Ethernet) 2\" mtu=1460 store=persistent

# Install Visual Studio 2015 Build Tools.
RUN Invoke-WebRequest "https://download.microsoft.com/download/5/f/7/5f7acaeb-8363-451f-9425-68a90f98b238/visualcppbuildtools_full.exe" \
                -OutFile visualcppbuildtools_full.exe -UseBasicParsing ; \
        Start-Process -FilePath 'visualcppbuildtools_full.exe' -ArgumentList '/quiet', '/NoRestart' -Wait ; \
        Remove-Item .\visualcppbuildtools_full.exe;

# Install 7-Zip and add it to the path.
ADD https://www.7-zip.org/a/7z1801-x64.msi C:\\TEMP\\7z.msi
RUN Start-Process msiexec.exe -ArgumentList \"/i C:\\TEMP\\7z.msi /qn /norestart /log C:\\TEMP\\7z_install_log.txt\" -wait
RUN $oldpath = (Get-ItemProperty -Path 'Registry::HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment' -Name PATH).path; \
  $newpath = \"$oldpath;C:\Program Files\7-Zip\"; \
  Set-ItemProperty -Path 'Registry::HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment' -Name PATH -Value $newPath

# Install msys2, and add some extra tools.
ADD http://repo.msys2.org/distrib/x86_64/msys2-base-x86_64-20161025.tar.xz C:\\TEMP\\msys2.tar.xz
RUN 7z x C:\TEMP\msys2.tar.xz -oC:\TEMP\msys2.tar
RUN 7z x C:\TEMP\msys2.tar -oC:\tools
RUN $oldpath = (Get-ItemProperty -Path 'Registry::HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment' -Name PATH).path; \
  $newpath = \"$oldpath;C:\tools\msys64;C:\tools\msys64\usr\bin\"; \
  Set-ItemProperty -Path 'Registry::HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment' -Name PATH -Value $newPath
# Install additional mysys2 binaries
RUN Start-Process msys2 -ArgumentList 'pacman -noconfirm -Syuu curl zip unzip' -Wait
# Install Visual C++ Redistributable for Visual Studio 2015:
ADD https://download.microsoft.com/download/9/3/F/93FCF1E7-E6A4-478B-96E7-D4B285925B00/vc_redist.x64.exe C:\\TEMP\\vc_redist.x64.exe
RUN C:\TEMP\vc_redist.x64.exe /quiet /install

# Install Python 2.7.
ADD https://www.python.org/ftp/python/2.7.14/python-2.7.14.amd64.msi C:\\TEMP\\python.msi
RUN Start-Process msiexec.exe -ArgumentList \"/i C:\\TEMP\\python.msi /qn /norestart /log C:\\TEMP\\python_install_log.txt\" -wait
RUN $oldpath = (Get-ItemProperty -Path 'Registry::HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment' -Name PATH).path; \
  $newpath = \"$oldpath;C:\Python27\"; \
  Set-ItemProperty -Path 'Registry::HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment' -Name PATH -Value $newPath

RUN Set-ItemProperty -Path 'Registry::HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment' -Name BAZEL_SH -Value \"C:\tools\msys64\usr\bin\bash.exe\"
RUN Set-ItemProperty -Path 'Registry::HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment' -Name BAZEL_VC -Value \"C:\Program Files (x86)\Microsoft Visual Studio 14.0\VC\"

# TODO: This requires entropy so may be problematic on a headless machine: https://wiki.archlinux.org/index.php/Pacman/Package_signing#Initializing_the_keyring
RUN Start-Process msys2 -ArgumentList 'pacman-key --init' -Wait

# Set Android Studio Custom environment Variable
ENV STUDIO_IMAGE=true

run reg add HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\AppModelUnlock /t REG_DWORD /f /v AllowDevelopmentWithoutDevLicense /d 1
run reg add HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Control\FileSystem /t REG_DWORD /f /v LongPathsEnabled /d 1

# Restore default shell for Windows containers.
SHELL ["cmd.exe", "/s", "/c"]
