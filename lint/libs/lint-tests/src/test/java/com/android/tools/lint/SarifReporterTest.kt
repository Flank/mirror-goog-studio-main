/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tools.lint

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.AppCompatResourceDetector
import com.android.tools.lint.checks.AutofillDetector
import com.android.tools.lint.checks.DuplicateResourceDetector
import com.android.tools.lint.checks.HardcodedValuesDetector
import com.android.tools.lint.checks.ManifestDetector
import com.android.tools.lint.checks.MotionLayoutDetector
import com.android.tools.lint.checks.PxUsageDetector
import com.android.tools.lint.checks.infrastructure.TestFiles.gradle
import com.android.tools.lint.checks.infrastructure.TestFiles.manifest
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.Test

class SarifReporterTest {
    private val sampleManifest = manifest(
        """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="test.pkg">
                <uses-sdk android:minSdkVersion="10" />
                <uses-sdk android:minSdkVersion="10" />
            </manifest>
            """
    ).indented()

    private val sampleLayout = xml(
        "src/main/res/layout/main.xml",
        """
            <Button xmlns:android="http://schemas.android.com/apk/res/android"
                    android:id="@+id/button1"
                    android:text="Fooo" />
            """
    ).indented()

    private val sampleStrings = xml(
        "src/main/res/values/strings.xml",
        """
            <resources>
                <string name="first">First</string>
                <string name="second">Second</string>
                <string name="first">Third</string>
            </resources>
            """
    ).indented()

    private val gradleFile = gradle(
        """
            apply plugin: 'com.android.application'
            android {
                defaultConfig {
                    minSdkVersion 10
                }
                lintOptions {
                    // normally error; want to verify that when severity varies
                    // from the default it's explicitly listed as a level in the SARIF file
                    warning 'DuplicateDefinition'
                }
            }
            """
    ).indented()

    @Test
    fun testBasic() {
        lint().files(sampleManifest, sampleLayout, sampleStrings, gradleFile)
            .issues(
                ManifestDetector.MULTIPLE_USES_SDK,
                HardcodedValuesDetector.ISSUE,
                DuplicateResourceDetector.ISSUE,
                // Issue included in registry but not found in results, to make
                // sure our rules section only included encountered issues
                MotionLayoutDetector.INVALID_SCENE_FILE_REFERENCE
            )
            .stripRoot(false)
            .run()
            .expectSarif(
                """
                {
                    "＄schema" : "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
                    "version" : "2.1.0",
                    "runs" : [
                        {
                            "tool": {
                                "driver": {
                                    "name": "Android Lint",
                                    "fullName": "Android Lint (in test)",
                                    "version": "1.0",
                                    "organization": "Google",
                                    "informationUri": "https://developer.android.com/studio/write/lint",
                                    "fullDescription": {
                                        "text": "Static analysis originally for Android source code but now performing general analysis"
                                    },
                                    "language": "en-US",
                                    "rules": [
                                        {
                                            "id": "DuplicateDefinition",
                                            "shortDescription": {
                                                "text": "Duplicate definitions of resources"
                                            },
                                            "fullDescription": {
                                                "text": "You can define a resource multiple times in different resource folders; that's how string translations are done, for example. However, defining the same resource more than once in the same resource folder is likely an error, for example attempting to add a new resource without realizing that the name is already used, and so on."
                                            },
                                            "defaultConfiguration": {
                                                "level": "error",
                                                "rank": 50
                                            },
                                            "properties": {
                                                "tags": [
                                                    "Correctness"
                                                ]
                                            }
                                        },
                                        {
                                            "id": "HardcodedText",
                                            "shortDescription": {
                                                "text": "Hardcoded text"
                                            },
                                            "fullDescription": {
                                                "text": "Hardcoding text attributes directly in layout files is bad for several reasons:\n\n* When creating configuration variations (for example for landscape or portrait) you have to repeat the actual text (and keep it up to date when making changes)\n\n* The application cannot be translated to other languages by just adding new translations for existing string resources.\n\nThere are quickfixes to automatically extract this hardcoded string into a resource lookup."
                                            },
                                            "defaultConfiguration": {
                                                "level": "warning",
                                                "rank": 60
                                            },
                                            "properties": {
                                                "tags": [
                                                    "Internationalization"
                                                ]
                                            }
                                        },
                                        {
                                            "id": "MultipleUsesSdk",
                                            "shortDescription": {
                                                "text": "Multiple <uses-sdk> elements in the manifest",
                                                "markdown": "Multiple `<uses-sdk>` elements in the manifest"
                                            },
                                            "fullDescription": {
                                                "text": "The <uses-sdk> element should appear just once; the tools will not merge the contents of all the elements so if you split up the attributes across multiple elements, only one of them will take effect. To fix this, just merge all the attributes from the various elements into a single <uses-sdk> element.",
                                                "markdown": "The `<uses-sdk>` element should appear just once; the tools will **not** merge the contents of all the elements so if you split up the attributes across multiple elements, only one of them will take effect. To fix this, just merge all the attributes from the various elements into a single <uses-sdk> element."
                                            },
                                            "defaultConfiguration": {
                                                "level": "error",
                                                "rank": 50
                                            },
                                            "properties": {
                                                "tags": [
                                                    "Correctness"
                                                ]
                                            }
                                        }
                                    ]
                                }
                            },
                            "originalUriBaseIds": {
                                "%SRCROOT%": {
                                    "uri": "file://TESTROOT/app/"
                                }
                            },
                            "results": [
                                {
                                    "ruleId": "DuplicateDefinition",
                                    "ruleIndex": 0,
                                    "message": {
                                        "text": "first has already been defined in this folder",
                                        "markdown": "`first` has already been defined in this folder"
                                    },
                                    "level": "warning",
                                    "locations": [
                                        {
                                            "physicalLocation": {
                                                "artifactLocation": {
                                                    "uriBaseId": "%SRCROOT%",
                                                    "uri": "src/main/res/values/strings.xml"
                                                },
                                                "region": {
                                                    "startLine": 4,
                                                    "startColumn": 13,
                                                    "endLine": 4,
                                                    "endColumn": 25,
                                                    "charOffset": 106,
                                                    "charLength": 12,
                                                    "snippet": {
                                                        "text": "name=\"first\""
                                                    }
                                                },
                                                "contextRegion": {
                                                    "startLine": 2,
                                                    "endLine": 7,
                                                    "snippet": {
                                                        "text": "    <string name=\"first\">First</string>\n    <string name=\"second\">Second</string>\n    <string name=\"first\">Third</string>\n</resources"
                                                    }
                                                }
                                            }
                                        }
                                    ],
                                    "relatedLocations": [
                                        {
                                            "id": 1,
                                            "message": {
                                                "text": "Previously defined here"
                                            },
                                            "physicalLocation": {
                                                "artifactLocation": {
                                                    "uriBaseId": "%SRCROOT%",
                                                    "uri": "src/main/res/values/strings.xml"
                                                },
                                                "region": {
                                                    "startLine": 2,
                                                    "startColumn": 13,
                                                    "endLine": 2,
                                                    "endColumn": 25,
                                                    "charOffset": 24,
                                                    "charLength": 12,
                                                    "snippet": {
                                                        "text": "name=\"first\""
                                                    }
                                                },
                                                "contextRegion": {
                                                    "startLine": 1,
                                                    "endLine": 5,
                                                    "snippet": {
                                                        "text": "<resources>\n    <string name=\"first\">First</string>\n    <string name=\"second\">Second</string>"
                                                    }
                                                }
                                            }
                                        }
                                    ],
                                    "partialFingerprints": {
                                        "sourceContext/v1": "6567b58f48151459"
                                    }
                                },
                                {
                                    "ruleId": "MultipleUsesSdk",
                                    "ruleIndex": 2,
                                    "message": {
                                        "text": "There should only be a single <uses-sdk> element in the manifest: merge these together",
                                        "markdown": "There should only be a single `<uses-sdk>` element in the manifest: merge these together"
                                    },
                                    "level": "error",
                                    "locations": [
                                        {
                                            "physicalLocation": {
                                                "artifactLocation": {
                                                    "uriBaseId": "%SRCROOT%",
                                                    "uri": "src/main/AndroidManifest.xml"
                                                },
                                                "region": {
                                                    "startLine": 4,
                                                    "startColumn": 6,
                                                    "endLine": 4,
                                                    "endColumn": 14,
                                                    "charOffset": 142,
                                                    "charLength": 8,
                                                    "snippet": {
                                                        "text": "uses-sdk"
                                                    }
                                                },
                                                "contextRegion": {
                                                    "startLine": 2,
                                                    "endLine": 7,
                                                    "snippet": {
                                                        "text": "    package=\"test.pkg\">\n    <uses-sdk android:minSdkVersion=\"10\" />\n    <uses-sdk android:minSdkVersion=\"10\" />\n</manifest"
                                                    }
                                                }
                                            }
                                        }
                                    ],
                                    "relatedLocations": [
                                        {
                                            "id": 1,
                                            "message": {
                                                "text": "Also appears here"
                                            },
                                            "physicalLocation": {
                                                "artifactLocation": {
                                                    "uriBaseId": "%SRCROOT%",
                                                    "uri": "src/main/AndroidManifest.xml"
                                                },
                                                "region": {
                                                    "startLine": 3,
                                                    "startColumn": 6,
                                                    "endLine": 3,
                                                    "endColumn": 14,
                                                    "charOffset": 98,
                                                    "charLength": 8,
                                                    "snippet": {
                                                        "text": "uses-sdk"
                                                    }
                                                },
                                                "contextRegion": {
                                                    "startLine": 1,
                                                    "endLine": 6,
                                                    "snippet": {
                                                        "text": "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    package=\"test.pkg\">\n    <uses-sdk android:minSdkVersion=\"10\" />\n    <uses-sdk android:minSdkVersion=\"10\" />"
                                                    }
                                                }
                                            }
                                        }
                                    ],
                                    "partialFingerprints": {
                                        "sourceContext/v1": "7b9672069a5042f2"
                                    }
                                },
                                {
                                    "ruleId": "HardcodedText",
                                    "ruleIndex": 1,
                                    "message": {
                                        "text": "Hardcoded string \"Fooo\", should use @string resource",
                                        "markdown": "Hardcoded string \"Fooo\", should use `@string` resource"
                                    },
                                    "locations": [
                                        {
                                            "physicalLocation": {
                                                "artifactLocation": {
                                                    "uriBaseId": "%SRCROOT%",
                                                    "uri": "src/main/res/layout/main.xml"
                                                },
                                                "region": {
                                                    "startLine": 3,
                                                    "startColumn": 9,
                                                    "endLine": 3,
                                                    "endColumn": 28,
                                                    "charOffset": 109,
                                                    "charLength": 19,
                                                    "snippet": {
                                                        "text": "android:text=\"Fooo\""
                                                    }
                                                },
                                                "contextRegion": {
                                                    "startLine": 1,
                                                    "endLine": 3,
                                                    "snippet": {
                                                        "text": "<Button xmlns:android=\"http://schemas.android.com/apk/res/android\"\n        android:id=\"@+id/button1\"\n        android:text=\"Fooo\" />"
                                                    }
                                                }
                                            }
                                        }
                                    ],
                                    "partialFingerprints": {
                                        "sourceContext/v1": "7252b279c747fe6d"
                                    }
                                }
                            ]
                        }
                    ]
                }
                """
            )
    }

    @Test
    fun testQuickfixAlternatives() {
        lint().files(
            manifest().targetSdk(26),
            // layout file: should add segment to insert text (new attribute before the hint attr).
            // Also, this tests a fix that has multiple alternatives.
            xml(
                "res/layout/autofill.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent"
                              android:orientation="vertical">
                    <EditText
                            android:id="@+id/usernameField"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:hint="hint"
                            android:inputType="password">
                        <requestFocus/>
                    </EditText>
                </LinearLayout>"""
            ).indented(),
            // Quickfix should edit existing range (both delete and insert)
            xml(
                "res/values/pxsp.xml",
                """
                <resources>
                    <style name="Style2">
                        <item name="android:textSize">50dp</item>
                    </style>
                </resources>
                """
            ).indented()
        )
            .issues(AutofillDetector.ISSUE, PxUsageDetector.DP_ISSUE)
            .stripRoot(false)
            .run()
            .expectSarif(
                """
            {
                "＄schema" : "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
                "version" : "2.1.0",
                "runs" : [
                    {
                        "tool": {
                            "driver": {
                                "name": "Android Lint",
                                "fullName": "Android Lint (in test)",
                                "version": "1.0",
                                "organization": "Google",
                                "informationUri": "https://developer.android.com/studio/write/lint",
                                "fullDescription": {
                                    "text": "Static analysis originally for Android source code but now performing general analysis"
                                },
                                "language": "en-US",
                                "rules": [
                                    {
                                        "id": "Autofill",
                                        "shortDescription": {
                                            "text": "Use Autofill"
                                        },
                                        "fullDescription": {
                                            "text": "Specify an autofillHints attribute when targeting SDK version 26 or higher or explicitly specify that the view is not important for autofill. Your app can help an autofill service classify the data correctly by providing the meaning of each view that could be autofillable, such as views representing usernames, passwords, credit card fields, email addresses, etc.\n\nThe hints can have any value, but it is recommended to use predefined values like 'username' for a username or 'creditCardNumber' for a credit card number. For a list of all predefined autofill hint constants, see the AUTOFILL_HINT_ constants in the View reference at https://developer.android.com/reference/android/view/View.html.\n\nYou can mark a view unimportant for autofill by specifying an importantForAutofill attribute on that view or a parent view. See https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
                                            "markdown": "Specify an `autofillHints` attribute when targeting SDK version 26 or higher or explicitly specify that the view is not important for autofill. Your app can help an autofill service classify the data correctly by providing the meaning of each view that could be autofillable, such as views representing usernames, passwords, credit card fields, email addresses, etc.\n\nThe hints can have any value, but it is recommended to use predefined values like 'username' for a username or 'creditCardNumber' for a credit card number. For a list of all predefined autofill hint constants, see the `AUTOFILL_HINT_` constants in the `View` reference at https://developer.android.com/reference/android/view/View.html.\n\nYou can mark a view unimportant for autofill by specifying an `importantForAutofill` attribute on that view or a parent view. See https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int)."
                                        },
                                        "defaultConfiguration": {
                                            "level": "warning",
                                            "rank": 80
                                        },
                                        "properties": {
                                            "tags": [
                                                "Usability"
                                            ]
                                        }
                                    },
                                    {
                                        "id": "SpUsage",
                                        "shortDescription": {
                                            "text": "Using dp instead of sp for text sizes",
                                            "markdown": "Using `dp` instead of `sp` for text sizes"
                                        },
                                        "fullDescription": {
                                            "text": "When setting text sizes, you should normally use sp, or \"scale-independent pixels\". This is like the dp unit, but it is also scaled by the user's font size preference. It is recommend you use this unit when specifying font sizes, so they will be adjusted for both the screen density and the user's preference.\n\nThere are cases where you might need to use dp; typically this happens when the text is in a container with a specific dp-size. This will prevent the text from spilling outside the container. Note however that this means that the user's font size settings are not respected, so consider adjusting the layout itself to be more flexible.",
                                            "markdown": "When setting text sizes, you should normally use `sp`, or \"scale-independent pixels\". This is like the `dp` unit, but it is also scaled by the user's font size preference. It is recommend you use this unit when specifying font sizes, so they will be adjusted for both the screen density and the user's preference.\n\nThere **are** cases where you might need to use `dp`; typically this happens when the text is in a container with a specific dp-size. This will prevent the text from spilling outside the container. Note however that this means that the user's font size settings are not respected, so consider adjusting the layout itself to be more flexible."
                                        },
                                        "defaultConfiguration": {
                                            "level": "warning",
                                            "rank": 80
                                        },
                                        "properties": {
                                            "tags": [
                                                "Correctness"
                                            ]
                                        }
                                    }
                                ]
                            }
                        },
                        "originalUriBaseIds": {
                            "%SRCROOT%": {
                                "uri": "file://TESTROOT/app/"
                            }
                        },
                        "results": [
                            {
                                "ruleId": "SpUsage",
                                "ruleIndex": 1,
                                "message": {
                                    "text": "Should use \"sp\" instead of \"dp\" for text sizes",
                                    "markdown": "Should use \"`sp`\" instead of \"`dp`\" for text sizes"
                                },
                                "locations": [
                                    {
                                        "physicalLocation": {
                                            "artifactLocation": {
                                                "uriBaseId": "%SRCROOT%",
                                                "uri": "res/values/pxsp.xml"
                                            },
                                            "region": {
                                                "startLine": 3,
                                                "startColumn": 39,
                                                "endLine": 3,
                                                "endColumn": 43,
                                                "charOffset": 76,
                                                "charLength": 4,
                                                "snippet": {
                                                    "text": "50dp"
                                                }
                                            },
                                            "contextRegion": {
                                                "startLine": 1,
                                                "endLine": 6,
                                                "snippet": {
                                                    "text": "<resources>\n    <style name=\"Style2\">\n        <item name=\"android:textSize\">50dp</item>\n    </style>"
                                                }
                                            }
                                        }
                                    }
                                ],
                                "fixes": [
                                    {
                                        "description": {
                                            "text": "Replace with sp"
                                        },
                                        "artifactChanges": [
                                            {
                                                "artifactLocation": {
                                                    "uriBaseId": "%SRCROOT%",
                                                    "uri": "res/values/pxsp.xml"
                                                },
                                                "replacements": [
                                                    {
                                                        "deletedRegion": {
                                                            "startLine": 3,
                                                            "startColumn": 42,
                                                            "charOffset": 78,
                                                            "endLine": 3,
                                                            "endColumn": 44,
                                                            "charLength": 2
                                                        },
                                                        "insertedContent": {
                                                            "text": "sp\n"
                                                        }
                                                    }
                                                ]
                                            }
                                        ]
                                    }
                                ],
                                "partialFingerprints": {
                                    "sourceContext/v1": "80f628e8def57407"
                                }
                            },
                            {
                                "ruleId": "Autofill",
                                "ruleIndex": 0,
                                "message": {
                                    "text": "Missing autofillHints attribute",
                                    "markdown": "Missing `autofillHints` attribute"
                                },
                                "locations": [
                                    {
                                        "physicalLocation": {
                                            "artifactLocation": {
                                                "uriBaseId": "%SRCROOT%",
                                                "uri": "res/layout/autofill.xml"
                                            },
                                            "region": {
                                                "startLine": 5,
                                                "startColumn": 6,
                                                "endLine": 5,
                                                "endColumn": 14,
                                                "charOffset": 225,
                                                "charLength": 8,
                                                "snippet": {
                                                    "text": "EditText"
                                                }
                                            },
                                            "contextRegion": {
                                                "startLine": 3,
                                                "endLine": 8,
                                                "snippet": {
                                                    "text": "              android:layout_height=\"match_parent\"\n              android:orientation=\"vertical\">\n    <EditText\n            android:id=\"@+id/usernameField\""
                                                }
                                            }
                                        }
                                    }
                                ],
                                "fixes": [
                                    {
                                        "description": {
                                            "text": "Set autofillHints"
                                        },
                                        "artifactChanges": [
                                            {
                                                "artifactLocation": {
                                                    "uriBaseId": "%SRCROOT%",
                                                    "uri": "res/layout/autofill.xml"
                                                },
                                                "replacements": [
                                                    {
                                                        "deletedRegion": {
                                                            "startLine": 9,
                                                            "startColumn": 14,
                                                            "charOffset": 387
                                                        },
                                                        "insertedContent": {
                                                            "text": "android:autofillHints=\"\" \n"
                                                        }
                                                    }
                                                ]
                                            }
                                        ]
                                    },
                                    {
                                        "description": {
                                            "text": "Set importantForAutofill=\"no\""
                                        },
                                        "artifactChanges": [
                                            {
                                                "artifactLocation": {
                                                    "uriBaseId": "%SRCROOT%",
                                                    "uri": "res/layout/autofill.xml"
                                                },
                                                "replacements": [
                                                    {
                                                        "deletedRegion": {
                                                            "startLine": 10,
                                                            "startColumn": 14,
                                                            "charOffset": 419
                                                        },
                                                        "insertedContent": {
                                                            "text": "android:importantForAutofill=\"no\" \n"
                                                        }
                                                    }
                                                ]
                                            }
                                        ]
                                    }
                                ],
                                "partialFingerprints": {
                                    "sourceContext/v1": "e66def4d268b2112"
                                }
                            }
                        ]
                    }
                ]
            }
            """
            )
    }

    @Test
    fun testQuickfixComposite() {
        lint().files(
            manifest().targetSdk(26),
            // Creates lint fix which is composite (multiple fixes that should
            // all be applied together: the edits must be merged)
            xml(
                "res/menu/showAction1.xml",
                """
                <menu xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto">
                    <item android:id="@+id/action_settings"
                        android:title="@string/action_settings"
                        android:orderInCategory="100"
                        app:showAsAction="never" />
                </menu>
                """
            ).indented()
        )
            .issues(AppCompatResourceDetector.ISSUE)
            .stripRoot(false)
            .run()
            .expectSarif(
                """
            {
                "＄schema" : "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
                "version" : "2.1.0",
                "runs" : [
                    {
                        "tool": {
                            "driver": {
                                "name": "Android Lint",
                                "fullName": "Android Lint (in test)",
                                "version": "1.0",
                                "organization": "Google",
                                "informationUri": "https://developer.android.com/studio/write/lint",
                                "fullDescription": {
                                    "text": "Static analysis originally for Android source code but now performing general analysis"
                                },
                                "language": "en-US",
                                "rules": [
                                    {
                                        "id": "AppCompatResource",
                                        "shortDescription": {
                                            "text": "Menu namespace"
                                        },
                                        "fullDescription": {
                                            "text": "When using the appcompat library, menu resources should refer to the showAsAction (or actionViewClass, or actionProviderClass) in the app: namespace, not the android: namespace.\n\nSimilarly, when not using the appcompat library, you should be using the android:showAsAction (or actionViewClass, or actionProviderClass) attribute.",
                                            "markdown": "When using the appcompat library, menu resources should refer to the `showAsAction` (or `actionViewClass`, or `actionProviderClass`) in the `app:` namespace, not the `android:` namespace.\n\nSimilarly, when **not** using the appcompat library, you should be using the `android:showAsAction` (or `actionViewClass`, or `actionProviderClass`) attribute."
                                        },
                                        "defaultConfiguration": {
                                            "level": "error",
                                            "rank": 60
                                        },
                                        "properties": {
                                            "tags": [
                                                "Correctness"
                                            ]
                                        }
                                    }
                                ]
                            }
                        },
                        "originalUriBaseIds": {
                            "%SRCROOT%": {
                                "uri": "file://TESTROOT/app/"
                            }
                        },
                        "results": [
                            {
                                "ruleId": "AppCompatResource",
                                "ruleIndex": 0,
                                "message": {
                                    "text": "Should use android:showAsAction when not using the appcompat library",
                                    "markdown": "Should use `android:showAsAction` when not using the appcompat library"
                                },
                                "locations": [
                                    {
                                        "physicalLocation": {
                                            "artifactLocation": {
                                                "uriBaseId": "%SRCROOT%",
                                                "uri": "res/menu/showAction1.xml"
                                            },
                                            "region": {
                                                "startLine": 6,
                                                "startColumn": 9,
                                                "endLine": 6,
                                                "endColumn": 33,
                                                "charOffset": 260,
                                                "charLength": 24,
                                                "snippet": {
                                                    "text": "app:showAsAction=\"never\""
                                                }
                                            },
                                            "contextRegion": {
                                                "startLine": 4,
                                                "endLine": 9,
                                                "snippet": {
                                                    "text": "        android:title=\"@string/action_settings\"\n        android:orderInCategory=\"100\"\n        app:showAsAction=\"never\" />\n</menu"
                                                }
                                            }
                                        }
                                    }
                                ],
                                "fixes": [
                                    {
                                        "description": {
                                            "text": "Update to android:showAsAction"
                                        },
                                        "artifactChanges": [
                                            {
                                                "artifactLocation": {
                                                    "uriBaseId": "%SRCROOT%",
                                                    "uri": "res/menu/showAction1.xml"
                                                },
                                                "replacements": [
                                                    {
                                                        "deletedRegion": {
                                                            "startLine": 4,
                                                            "startColumn": 10,
                                                            "charOffset": 174
                                                        },
                                                        "insertedContent": {
                                                            "text": "android:showAsAction=\"never\" \n"
                                                        }
                                                    },
                                                    {
                                                        "deletedRegion": {
                                                            "startLine": 6,
                                                            "startColumn": 10,
                                                            "charOffset": 260,
                                                            "endLine": 6,
                                                            "endColumn": 34,
                                                            "charLength": 24
                                                        },
                                                        "insertedContent": {
                                                            "text": "\n"
                                                        }
                                                    }
                                                ]
                                            }
                                        ]
                                    }
                                ],
                                "partialFingerprints": {
                                    "sourceContext/v1": "8d3c283830163750"
                                }
                            }
                        ]
                    }
                ]
            }
           """
            )
    }

    private fun lint(): TestLintTask {
        return TestLintTask.lint().sdkHome(TestUtils.getSdk().toFile())
    }
}
