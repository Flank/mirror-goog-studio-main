// ProfilerWinLauncher.cpp : This file contains the 'main' function. Program execution begins and ends there.
//

// Disable the CMD window at startup.
#pragma comment(linker, "/SUBSYSTEM:windows /ENTRY:mainCRTStartup")
#include <windows.h>
#include <Shlwapi.h>

using namespace std;

int main()
{
	WCHAR path[MAX_PATH];
	GetModuleFileNameW(NULL, path, MAX_PATH);
	PathRemoveFileSpec(path);
	wcscat_s(path, L"\\profiler.bat");
	PROCESS_INFORMATION processInformation = { 0 };
	STARTUPINFO startupInfo = { 0 };
	CreateProcessW(NULL, path, NULL, NULL, FALSE, CREATE_NO_WINDOW, NULL, NULL, &startupInfo, &processInformation);
}
