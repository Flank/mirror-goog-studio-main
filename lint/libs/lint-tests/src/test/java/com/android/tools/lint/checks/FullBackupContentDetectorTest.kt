/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class FullBackupContentDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return FullBackupContentDetector()
    }

    fun testOk() {
        lint().files(
            xml(
                "res/xml/backup.xml",
                """
                <full-backup-content>
                     <include domain="file" path="dd"/>
                     <exclude domain="file" path="dd/fo3o.txt"/>
                     <exclude domain="file" path="dd/ss/foo.txt"/>
                </full-backup-content>
                """
            ).indented()
        ).run().expectClean()
    }

    fun test20890435() {
        val expected = """
            res/xml/backup.xml:5: Error: foo.xml is not in an included path [FullBackupContent]
                 <exclude domain="sharedpref" path="foo.xml"/>
                                                    ~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/xml/backup.xml",
                """
                <full-backup-content>
                     <include domain="file" path="dd"/>
                     <exclude domain="file" path="dd/fo3o.txt"/>
                     <exclude domain="file" path="dd/ss/foo.txt"/>
                     <exclude domain="sharedpref" path="foo.xml"/>
                </full-backup-content>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testImplicitInclude() {
        // If there is no include, then everything is considered included
        lint().files(
            xml(
                "res/xml/backup.xml",
                """
                <full-backup-content>
                     <exclude domain="file" path="dd/fo3o.txt"/>
                </full-backup-content>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testImplicitPath() {
        // If you specify an include, but no path attribute, that's defined to mean include
        // everything
        lint().files(
            xml(
                "res/xml/backup.xml",
                """
                <full-backup-content>
                     <include domain="file"/>
                     <exclude domain="file" path="dd/fo3o.txt"/>
                     <include domain="sharedpref" path="something"/>
                </full-backup-content>
                """
            ).indented()
        ).run().expectClean()
    }

    // Regression test for b/118866569
    fun testCurrentDirectoryPath() {
        // You can use "." in the path to reference the current directory; see
        // https://developer.android.com/guide/topics/data/autobackup#XMLSyntax
        lint().files(
            xml(
                "res/xml/backup.xml",
                """
                <full-backup-content>
                     <include domain="file" path="."/>
                     <exclude domain="file" path="dd/fo3o.txt"/>
                     <include domain="sharedpref" path="."/>
                     <exclude domain="sharedpref" path="device.xml"/>
                </full-backup-content>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testSuppressed() {
        lint().files(
            xml(
                "res/xml/backup.xml",
                """
                <full-backup-content xmlns:tools="http://schemas.android.com/tools">
                     <include domain="file" path="dd"/>
                     <exclude domain="file" path="dd/fo3o.txt"/>
                     <exclude domain="file" path="dd/ss/foo.txt"/>
                     <exclude domain="sharedpref" path="foo.xml" tools:ignore="FullBackupContent"/>
                </full-backup-content>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testIncludeWrongDomain() {
        // Ensure that the path prefix check is done independently for each domain
        val expected = """
            res/xml/backup.xml:3: Error: abc/def.txt is not in an included path [FullBackupContent]
                 <exclude domain="external" path="abc/def.txt"/>
                                                  ~~~~~~~~~~~
            res/xml/backup.xml:5: Error: def/ghi.txt is not in an included path [FullBackupContent]
                 <exclude domain="external" path="def/ghi.txt"/>
                                                  ~~~~~~~~~~~
            2 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/xml/backup.xml",
                """
                <full-backup-content>
                     <include domain="file" path="abc"/>
                     <exclude domain="external" path="abc/def.txt"/>
                     <include domain="file" path="def"/>
                     <exclude domain="external" path="def/ghi.txt"/>
                </full-backup-content>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testValidation() {
        val expected =
            """
            res/xml/backup.xml:6: Error: Subdirectories are not allowed for domain sharedpref [FullBackupContent]
                 <include domain="sharedpref" path="dd/subdir"/>
                                                    ~~~~~~~~~
            res/xml/backup.xml:7: Error: Paths are not allowed to contain .. [FullBackupContent]
                 <include domain="file" path="../outside"/>
                                              ~~~~~~~~~~
            res/xml/backup.xml:8: Error: Paths are not allowed to contain // [FullBackupContent]
                 <include domain="file" path="//wrong"/>
                                              ~~~~~~~
            res/xml/backup.xml:10: Error: Include dd is also excluded [FullBackupContent]
                 <exclude domain="external" path="dd"/>
                                                  ~~
                res/xml/backup.xml:9: Unnecessary/conflicting <include>
                 <include domain="external" path="dd"/>
                                            ~~~~~~~~~
            res/xml/backup.xml:11: Error: Unexpected domain unknown-domain, expected one of root, file, database, sharedpref, external [FullBackupContent]
                 <exclude domain="unknown-domain" path="dd"/>
                                  ~~~~~~~~~~~~~~
            res/xml/backup.xml:11: Error: dd is not in an included path [FullBackupContent]
                 <exclude domain="unknown-domain" path="dd"/>
                                                        ~~
            res/xml/backup.xml:12: Error: Missing domain attribute, expected one of root, file, database, sharedpref, external [FullBackupContent]
                 <include path="dd"/>
                 ~~~~~~~~~~~~~~~~~~~~
            res/xml/backup.xml:14: Error: Unexpected element <wrongtag> [FullBackupContent]
                 <wrongtag />
                  ~~~~~~~~
            8 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/xml/backup.xml",
                """
                <full-backup-content>
                     <include domain="root" path="dd"/>
                     <include domain="file" path="dd"/>
                     <include domain="database" path="dd"/>
                     <include domain="sharedpref" path="dd"/>
                     <include domain="sharedpref" path="dd/subdir"/>
                     <include domain="file" path="../outside"/>
                     <include domain="file" path="//wrong"/>
                     <include domain="external" path="dd"/>
                     <exclude domain="external" path="dd"/>
                     <exclude domain="unknown-domain" path="dd"/>
                     <include path="dd"/>
                     <include domain="root" />
                     <wrongtag />
                </full-backup-content>
                """
            ).indented()
        ).run().expect(expected)
    }
}
