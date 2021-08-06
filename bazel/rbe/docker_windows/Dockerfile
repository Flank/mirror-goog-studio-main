# This file is a modified version of the default image provided by RBE team.
# Please see https://foundry-windows-internal.googlesource.com/dev/+/master/docker/Dockerfile
# for original file.

# This Dockerfile deviates from the provided RBE image in that:
# - Upgrades msys2 from 20181211 to 20210228. This was necessary because zstd
#     packages failed to install. See
#     https://www.msys2.org/news/#2020-12-26-zstd-exemption-for-core-packages-removed
#     and RBE Bug on upgrading their msys2 version b/183609916
# - We install Python 3 as well
# - We set BAZEL_VC and STUDIO_IMAGE environment variables.
# - Turn on developer mode.
# - Install Windows 10 build tools.

# Start a temporary container in which we install 7-Zip to extract msys2
FROM mcr.microsoft.com/windows/servercore:ltsc2019 as extract-msys2
SHELL ["powershell.exe", "-ExecutionPolicy", "Bypass", "-Command", "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; $VerbosePreference = 'Continue';"]

# Install 7-Zip and add it to the path.
ADD https://www.7-zip.org/a/7z1801-x64.msi C:\\TEMP\\7z.msi
RUN Start-Process msiexec.exe -ArgumentList \"/i C:\\TEMP\\7z.msi /qn /norestart /log C:\\TEMP\\7z_install_log.txt\" -wait
RUN $oldpath = (Get-ItemProperty -Path 'Registry::HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment' -Name PATH).path; \
  $newpath = \"$oldpath;C:\Program Files\7-Zip\"; \
  Set-ItemProperty -Path 'Registry::HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment' -Name PATH -Value $newPath

# Extract msys2
ADD http://repo.msys2.org/distrib/x86_64/msys2-base-x86_64-20210228.tar.xz C:\\TEMP\\msys2.tar.xz
RUN 7z x C:\TEMP\msys2.tar.xz -oC:\TEMP\msys2.tar
RUN 7z x C:\TEMP\msys2.tar -oC:\tools

# Start building the actual image
FROM mcr.microsoft.com/windows/servercore:ltsc2019
SHELL ["powershell.exe", "-ExecutionPolicy", "Bypass", "-Command", "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; $VerbosePreference = 'Continue';"]

# TODO(b/112379377): Workaround until bug is fixed.
RUN Get-NetAdapter | Where-Object Name -like "*Ethernet*" | ForEach-Object { & netsh interface ipv4 set subinterface $_.InterfaceIndex mtu=1460 store=persistent }

# Enable Long Paths for Win32 File/Folder APIs.
RUN New-ItemProperty -Path HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem -Name LongPathsEnabled -Value 1 -PropertyType DWORD -Force

# Install Visual Studio 2015 Build Tools.
RUN Invoke-WebRequest "https://download.microsoft.com/download/5/f/7/5f7acaeb-8363-451f-9425-68a90f98b238/visualcppbuildtools_full.exe" \
                -OutFile visualcppbuildtools_full.exe -UseBasicParsing ; \
        Start-Process -FilePath 'visualcppbuildtools_full.exe' -ArgumentList '/quiet', '/NoRestart' -Wait ; \
        Remove-Item .\visualcppbuildtools_full.exe;

# From the temporary extract-msys2 container, copy the tools directory to this container
COPY --from=extract-msys2 ["C:/tools", "C:/tools"]

# Add msys2 to the PATH variable
RUN $oldpath = (Get-ItemProperty -Path 'Registry::HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment' -Name PATH).path; \
  $newpath = \"$oldpath;C:\tools\msys64;C:\tools\msys64\usr\bin\"; \
  Set-ItemProperty -Path 'Registry::HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment' -Name PATH -Value $newPath

# Bazel documentation says to use -Syuu but this doesn't work in Docker. See
# http://g/foundry-windows/PDMVXbGew7Y
RUN bash.exe -c \"curl -O http://repo.msys2.org/msys/x86_64/msys2-keyring-r21.b39fb11-1-any.pkg.tar.xz \
	&& curl -O http://repo.msys2.org/msys/x86_64/msys2-keyring-r21.b39fb11-1-any.pkg.tar.xz.sig \
	&& pacman-key --init \
	&& pacman-key --populate msys2 \
	&& pacman-key --verify msys2-keyring-r21.b39fb11-1-any.pkg.tar.xz.sig \
	&& pacman --noconfirm -U msys2-keyring-r21.b39fb11-1-any.pkg.tar.xz \
	&& (pacman-key --refresh-keys || pacman-key --refresh-keys --keyserver=hkps://peegeepee.com) \
	&& pacman --noconfirm -Syy zstd \
	&& pacman --noconfirm -Syy git curl zip unzip\"

# Install Visual C++ Redistributable for Visual Studio 2015:
ADD https://download.microsoft.com/download/9/3/F/93FCF1E7-E6A4-478B-96E7-D4B285925B00/vc_redist.x64.exe C:\\TEMP\\vc_redist.x64.exe
RUN C:\TEMP\vc_redist.x64.exe /quiet /install

# Install Python 2.7.
ADD https://www.python.org/ftp/python/2.7.18/python-2.7.18.amd64.msi C:\\TEMP\\python.msi
RUN Start-Process msiexec.exe -ArgumentList \"/i C:\\TEMP\\python.msi /qn /norestart /log C:\\TEMP\\python_install_log.txt\" -wait

# Install Python 3
ADD https://www.python.org/ftp/python/3.8.10/python-3.8.10-amd64.exe C:\\TEMP\\python3.exe
RUN Start-Process C:\TEMP\python3.exe -ArgumentList '/quiet InstallAllUsers=1 TargetDir=C:\\python3 /log C:\\TEMP\\python3_install_log.txt ' -wait

##### Android Studio Specific Changes #######

RUN Set-ItemProperty -Path 'Registry::HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Session Manager\Environment' -Name BAZEL_VC -Value \"C:\Program Files (x86)\Microsoft Visual Studio 14.0\VC\"

# Set Android Studio Custom environment Variable
ENV STUDIO_IMAGE=true

# Enable Developer mode to allow symbolic links
run reg add HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\AppModelUnlock /t REG_DWORD /f /v AllowDevelopmentWithoutDevLicense /d 1

# Restore default shell for Windows containers.
SHELL ["cmd.exe", "/s", "/c"]

# Install windows 10 build tools, version 16.6.5
# See https://docs.microsoft.com/en-us/visualstudio/releases/2019/history
ADD https://download.visualstudio.microsoft.com/download/pr/067fd8d0-753e-4161-8780-dfa3e577839e/4776935864d08e66183acd5b3647c9616da989c60afbfe100d4afc459f7e5785/vs_BuildTools.exe "C:\TEMP\vs_buildtools.exe"
RUN C:\TEMP\vs_buildtools.exe --wait --quiet --norestart --nocache \
    --add Microsoft.VisualStudio.Workload.VCTools \
    --add Microsoft.VisualStudio.Component.VC.Tools.x86.x64 \
    --add Microsoft.VisualStudio.Component.Windows10SDK.18362 \
    --add Microsoft.Component.VC.Runtime.UCRTSDK \
    --add Microsoft.VisualStudio.Component.VC.CLI.Support
