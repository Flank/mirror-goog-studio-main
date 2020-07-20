/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.sdklib.integration

import com.android.testutils.AssumeUtil
import com.android.testutils.TestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.ZipInputStream

class SdkCommandLineToolsSmokeTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun assumeLinux() {
        AssumeUtil.assumeIsLinux()
    }

    @Test
    fun sdkManagerSmokeTestOnLinux() {
        val extractedDir = extract()

        val sdkManagerBinary = extractedDir.resolve("cmdline-tools/bin/sdkmanager")

        val outFile = temporaryFolder.newFile("out")
        val errFile = temporaryFolder.newFile("err")
        val processBuilder = ProcessBuilder()
            .redirectError(errFile)
            .redirectOutput(outFile)
            .command("sh", sdkManagerBinary.toString(), "--help")

        val returnCode = processBuilder.start().waitFor()
        assertThat(returnCode).named("returnCode").isEqualTo(1)

        assertThat(Files.readAllLines(outFile.toPath())).isEmpty()
        assertThat(Files.readAllLines(errFile.toPath()).joinToString("\n"))
            .isEqualTo("""
                Usage:
                  sdkmanager [--uninstall] [<common args>] [--package_file=<file>] [<packages>...]
                  sdkmanager --update [<common args>]
                  sdkmanager --list [<common args>]
                  sdkmanager --list_installed [<common args>]
                  sdkmanager --licenses [<common args>]
                  sdkmanager --version

                With --install (optional), installs or updates packages.
                    By default, the listed packages are installed or (if already installed)
                    updated to the latest version.
                With --uninstall, uninstall the listed packages.

                    <package> is a sdk-style path (e.g. "build-tools;23.0.0" or
                             "platforms;android-23").
                    <package-file> is a text file where each line is a sdk-style path
                                   of a package to install or uninstall.
                    Multiple --package_file arguments may be specified in combination
                    with explicit paths.

                With --update, all installed packages are updated to the latest version.

                With --list, all installed and available packages are printed out.

                With --list_installed, all installed packages are printed out.

                With --licenses, show and offer the option to accept licenses for all
                     available packages that have not already been accepted.

                With --version, prints the current version of sdkmanager.

                Common Arguments:
                    --sdk_root=<sdkRootPath>: Use the specified SDK root instead of the SDK
                                              containing this tool

                    --channel=<channelId>: Include packages in channels up to <channelId>.
                                           Common channels are:
                                           0 (Stable), 1 (Beta), 2 (Dev), and 3 (Canary).

                    --include_obsolete: With --list, show obsolete packages in the
                                        package listing. With --update, update obsolete
                                        packages as well as non-obsolete.

                    --no_https: Force all connections to use http rather than https.

                    --proxy=<http | socks>: Connect via a proxy of the given type.

                    --proxy_host=<IP or DNS address>: IP or DNS address of the proxy to use.

                    --proxy_port=<port #>: Proxy port to connect to.

                    --verbose: Enable verbose output.

                * If the env var REPO_OS_OVERRIDE is set to "windows",
                  "macosx", or "linux", packages will be downloaded for that OS.
                """.trimIndent())
    }

    fun extract(): Path {
        val extractedDir = temporaryFolder.newFolder("extracted").toPath()

        val zipFile = AndroidSdkCommandLineToolsPlatform.LINUX.zipFile

        ZipInputStream(Files.newInputStream(zipFile).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    continue
                }
                val file = extractedDir.resolve(entry.name)
                Files.createDirectories(file.parent)
                Files.copy(zip, file)
            }
        }
        return extractedDir;
    }


}
