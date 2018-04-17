/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.lint.checks.ActionsXmlDetector.Companion.getUriTemplateParameters
import com.android.tools.lint.detector.api.Detector

class ActionsXmlDetectorTest : AbstractCheckTest() {

    override fun getDetector(): Detector {
        return ActionsXmlDetector()
    }

    fun testValidSample1() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions supportedLocales="en-US,en-GB,es-ES,es-419">
                    <action intentName="com.taxiapp.my.GET_ESTIMATE">
                        <action-display
                                labelTemplate="@array/rideActionLabel"
                                icon="@mipmap/rideActionIcon"/>

                        <fulfillment
                                urlTemplate="https://m.taxiapp.com/ul/?action=getRide{&amp;destLat,destLong}">
                            <parameter-mapping
                                    intentParameter="destination.latitude"
                                    urlParameter="destLat"/>
                            <parameter-mapping
                                    intentParameter="destination.longitude"
                                    urlParameter="destLong"/>
                        </fulfillment>

                        <parameter name="destination" type="shared.types.Location"/>
                        <parameter name="serviceClass" type="com.taxiapp.types.ServiceClass"/>
                    </action>
                </actions>
                """
            ).indented(),
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                     package="com.example.helloworld"
                     android:versionCode="1"
                     android:versionName="1.0">
                   <uses-sdk android:minSdkVersion="15" />
                   <application android:icon="@drawable/icon" android:label="@string/app_name">
                       <meta-data android:name="com.google.android.actions" android:resource="@xml/actions" />
                       <activity android:name=".HelloWorld"
                                 android:label="@string/app_name">
                       </activity>
                   </application>
                </manifest>
            """
            ).indented()
        ).run().expectClean()
    }

    fun testValidSample2() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <action intentName="actions.intent.PLAY_MUSIC">
                        <fulfillment urlTemplate="http://www.example.com/artist/{artist_name}?autoplay=true">
                            <parameter-mapping
                                    intentParameter="artist.name"
                                    urlParameter="artist_name"
                                    required="true"/>
                        </fulfillment>
                        <fulfillment urlTemplate="http://www.example.com/album/{album_name}?autoplay=true">
                            <parameter-mapping
                                    intentParameter="album.name"
                                    urlParameter="album_name"
                                    required="true"/>
                        </fulfillment>
                        <fulfillment urlTemplate=
                                             "http://www.example.com/song/{song_name}?autoplay=true">
                            <parameter-mapping
                                    intentParameter="song.name"
                                    urlParameter="song_name"
                                    required="true"/>
                        </fulfillment>
                        <fulfillment urlTemplate="http://www.example.com/music/home">
                        </fulfillment>
                    </action>
                </actions>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testMissingManifestRegistration() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                     package="com.example.helloworld"
                     android:versionCode="1"
                     android:versionName="1.0">
                   <uses-sdk android:minSdkVersion="15" />
                   <application android:icon="@drawable/icon" android:label="@string/app_name">
                       <activity android:name=".HelloWorld"
                                 android:label="@string/app_name">
                       </activity>
                   </application>
                </manifest>
                """
            ).indented(),
            xml(
                "res/xml/my_actions.xml",
                """
                <actions>
                    <action intentName="name1">
                        <fulfillment urlTemplate="foo"/>
                    </action>
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/my_actions.xml:1: Error: This action resource should be registered in the manifest under the <application> tag as <meta-data android:name="com.google.android.actions" android:resource="@xml/my_actions" /> [ValidActionsXml]
            <actions>
             ~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testSupportedLocalesOk() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions supportedLocales="en-US,en-GB,es-ES,es-419" />
                """
            ).indented()
        ).run().expectClean()
    }

    fun testSupportedLocalesBad() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions supportedLocales="en-US,en-GB,abcdefg,es-419" />
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:1: Error: Invalid BCP-47 locale qualifier abcdefg [ValidActionsXml]
            <actions supportedLocales="en-US,en-GB,abcdefg,es-419" />
                                                   ~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testNestedActions() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <action intentName="name1">
                        <action intentName="name2">
                        </action>
                    </action>
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:3: Error: Nesting <action> is not allowed [ValidActionsXml]
                    <action intentName="name2">
                     ~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testNestedFulfillment() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <action intentName="name1">
                        <fulfillment urlTemplate="foo">
                            <fulfillment urlTemplate="bar"/>
                        </fulfillment>
                    </action>
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:4: Error: Nesting <fulfillment> is not allowed [ValidActionsXml]
                        <fulfillment urlTemplate="bar"/>
                         ~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testWrongParentAction() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <action />
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:1: Error: <action> must be inside <actions> [ValidActionsXml]
            <action />
             ~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testWrongParentFulfillment() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <fulfillment urlTemplate="foo"/>
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:2: Error: <fulfillment> must be inside <action> [ValidActionsXml]
                <fulfillment urlTemplate="foo"/>
                 ~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testWrongParentParameter() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <parameter name="foo"/>
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:2: Error: <parameter> must be inside <action> [ValidActionsXml]
                <parameter name="foo"/>
                 ~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testWrongParentParameter2() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <action intentName="ok">
                        <fulfillment urlTemplate="foo">
                            <parameter name="foo"/>
                        </fulfillment>
                    </action>
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:4: Error: <parameter> must be inside <action> [ValidActionsXml]
                        <parameter name="foo"/>
                         ~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testWrongParentParameterMapping() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <parameter-mapping intentName="foo"/>
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:2: Error: <parameter-mapping> must be inside <fulfillment> [ValidActionsXml]
                <parameter-mapping intentName="foo"/>
                 ~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testWrongParentActionDisplay() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                  <action-display
                      labelTemplate="@array/rideActionLabel"
                      icon="@mipmap/rideActionIcon" />
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:2: Error: <action-display> must be inside <action> [ValidActionsXml]
              <action-display
               ~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testRequiredActionName() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <action>
                        <fulfillment urlTemplate="foo">
                        </fulfillment>
                    </action>
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:2: Error: Missing required attribute intentName [ValidActionsXml]
                <action>
                 ~~~~~~
            1 errors, 0 warnings
            """
        ).expectFixDiffs(
            """
            Fix for res/xml/actions.xml line 2: Set intentName:
            @@ -4 +4
            -     <action>
            +     <action intentName="[TODO]|" >
            """
        )
    }

    fun testActionMissesFulfillmentOrEntitySetReference() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <action intentName="ok">
                        <fulfillment urlTemplate="foo">
                        </fulfillment>
                    </action>
                    <action intentName="wrong">
                    </action>
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:6: Error: <action> must declare a <fulfillment> or a <parameter> with an <entity-set-reference> [ValidActionsXml]
                <action intentName="wrong">
                 ~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testActionProvidesEntitySetReferenceInsteadOfFulfillment() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <action intentName="ok">
                        <parameter
                            name="song"
                            type="org.schema.MusicRecording">
                          <entity-set-reference
                              type="org.schema.MusicRecording"
                              actionType="schema.org/PlayAction"
                              urlFilter="https://provider.com/track/.*" />
                        </parameter>
                    </action>
                </actions>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testFulfillmentMissesUrlTemplate() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <action intentName="ok">
                        <fulfillment />
                    </action>
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:3: Error: Missing required attribute urlTemplate [ValidActionsXml]
                    <fulfillment />
                    ~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        ).expectFixDiffs(
            """
            Fix for res/xml/actions.xml line 3: Set urlTemplate:
            @@ -5 +5
            -         <fulfillment />
            +         <fulfillment urlTemplate="[TODO]|" />
            """
        )
    }

    fun testParameterMissingName() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <action intentName="ok">
                        <parameter />
                        <fulfillment urlTemplate="foo"/>
                    </action>
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:3: Error: Missing required attribute name [ValidActionsXml]
                    <parameter />
                    ~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testParameterDuplicateNames() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <action intentName="ok">
                        <parameter name="name1" type="mytype" />
                        <parameter name="name2" type="mytype" />
                        <parameter name="name3" type="mytype" />
                        <parameter name="name1" type="mytype" />
                        <fulfillment urlTemplate="foo"/>
                    </action>
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:6: Error: <action> contains two <parameter> elements with the same name, name1 [ValidActionsXml]
                    <parameter name="name1" type="mytype" />
                               ~~~~~~~~~~~~
                res/xml/actions.xml:3: <No location-specific message
            1 errors, 0 warnings
            """
        )
    }

    fun testParameterMappingMissesName() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <action intentName="ok">
                        <fulfillment urlTemplate="foo">
                            <parameter-mapping />
                        </fulfillment>
                    </action>
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:4: Error: Missing required attribute intentParameter [ValidActionsXml]
                        <parameter-mapping />
                        ~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testParameterMappingMissesUrl() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <action intentName="ok">
                        <fulfillment urlTemplate="foo">
                            <parameter-mapping intentParameter="destination.longitude" />
                        </fulfillment>
                    </action>
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:4: Error: Missing required attribute urlParameter [ValidActionsXml]
                        <parameter-mapping intentParameter="destination.longitude" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testParameterMappingDuplicateNames() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <action intentName="ok">
                        <fulfillment urlTemplate="{destLong1,destLong2,destLong3}">
                            <parameter-mapping
                                intentParameter="destination.longitude"
                                urlParameter="destLong1" />
                            <parameter-mapping
                                intentParameter="other"
                                urlParameter="destLong2" />
                            <parameter-mapping
                                intentParameter="destination.longitude"
                                urlParameter="destLong3" />
                        </fulfillment>
                    </action>
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:11: Error: <fulfillment> contains two <parameter-mapping> elements with the same intentParameter, destination.longitude [ValidActionsXml]
                            intentParameter="destination.longitude"
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/xml/actions.xml:5: <No location-specific message
            1 errors, 0 warnings
            """
        )
    }

    fun testReferencesNotAllowed() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>

                    <action intentName="name1">
                        <fulfillment urlTemplate="@string/foo">
                          <parameter-mapping
                              intentParameter="@string/destination"
                              urlParameter="@string/parameter" />
                        </fulfillment>
                    </action>
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:4: Error: urlTemplate must be a value, not a reference [ValidActionsXml]
                    <fulfillment urlTemplate="@string/foo">
                                 ~~~~~~~~~~~~~~~~~~~~~~~~~
            res/xml/actions.xml:6: Error: intentParameter must be a value, not a reference [ValidActionsXml]
                          intentParameter="@string/destination"
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/xml/actions.xml:7: Error: urlParameter must be a value, not a reference [ValidActionsXml]
                          urlParameter="@string/parameter" />
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            3 errors, 0 warnings
            """
        )
    }

    fun testIgnoreUnknowns() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <foo>
                        <foo/>
                    </foo>
                    <action intentName="name1">
                        <bar>
                            <bar/>
                        </bar>
                        <fulfillment urlTemplate="{parameter}">
                        <bar>
                            <bar/>
                        </bar>
                        <parameter-mapping
                              intentParameter="destination"
                              urlParameter="parameter" />
                        </fulfillment>
                    </action>
                </actions>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testUrlParameterLookup() {
        assertEquals("[]", getUriTemplateParameters("").sorted().toString())
        assertEquals("[count]", getUriTemplateParameters("{count*}").sorted().toString())
        assertEquals("[count]", getUriTemplateParameters("{/count*}").sorted().toString())
        assertEquals("[count]", getUriTemplateParameters("{;count}").sorted().toString())
        assertEquals("[count]", getUriTemplateParameters("{?count}").sorted().toString())
        assertEquals("[count]", getUriTemplateParameters("{+count}").sorted().toString())
        assertEquals("[count]", getUriTemplateParameters("{#count}").sorted().toString())
        assertEquals("[count]", getUriTemplateParameters("{;count}").sorted().toString())
        assertEquals("[count]", getUriTemplateParameters("{&count}").sorted().toString())
        assertEquals("[hello, x, y]", getUriTemplateParameters("{x,hello,y}").sorted().toString())
        assertEquals("[path]", getUriTemplateParameters("{+path:6}/here").sorted().toString())
        assertEquals("[path]", getUriTemplateParameters("{#path:6}/here").sorted().toString())
        assertEquals(
            "[list, path]",
            getUriTemplateParameters("prefix{/list*,path:4}suffix").sorted().toString()
        )
        assertEquals("[who]", getUriTemplateParameters("{.who,who}").sorted().toString())
        assertEquals("[who]", getUriTemplateParameters("{.who,who}").sorted().toString())
        assertEquals("[who]", getUriTemplateParameters("{.who,who}").sorted().toString())
        assertEquals("[who]", getUriTemplateParameters("{.who,who}").sorted().toString())
        assertEquals("[who]", getUriTemplateParameters("{.who,who}").sorted().toString())
        assertEquals("[var, x]", getUriTemplateParameters("{/var,x}/here").sorted().toString())
        assertEquals("[empty, x, y]", getUriTemplateParameters("{;x,y,empty}").sorted().toString())
        assertEquals("[empty, x, y]", getUriTemplateParameters("{?x,y,empty}").sorted().toString())
        assertEquals("[empty, x, y]", getUriTemplateParameters("{&x,y,empty}").sorted().toString())

        assertEquals(
            "[bar, foo]",
            getUriTemplateParameters("a{foo*}b{+bar:5}c").sorted().toString()
        )
        assertEquals(
            "[count, two]",
            getUriTemplateParameters("a{count}b{two}c").sorted().toString()
        )

        // Malformed:
        assertEquals("[foo]", getUriTemplateParameters("a{foo,}").sorted().toString())
        assertEquals("[]", getUriTemplateParameters("a{foo").sorted().toString())
    }

    fun testMissingAndExtraParameter() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <action intentName="com.taxiapp.my.GET_ESTIMATE">
                        <fulfillment urlTemplate="https://m.taxiapp.com/ul/?action=getRide{&amp;destLat,destLong,missingParameter,url}">
                            <parameter-mapping
                                intentParameter="destination.latitude"
                                urlParameter="destLat" />
                            <parameter-mapping
                                intentParameter="destination.longitude"
                                urlParameter="destLong" />
                            <parameter-mapping
                                intentParameter="destination.extra"
                                urlParameter="extraParameter" />
                        </fulfillment>
                        <parameter name="destination" type="shared.types.Location" />
                        <parameter name="serviceClass" type="com.taxiapp.types.ServiceClass" />
                        <parameter name="song" type="org.schema.MusicRecording">
                            <entity-set-reference
                                type="org.schema.MusicRecording"
                                actionType="schema.org/PlayAction"
                                urlFilter="https://provider.com/track/.*" />
                        </parameter>
                    </action>
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:3: Error: The parameter missingParameter is not defined as a <parameter-mapping> element below [ValidActionsXml]
                    <fulfillment urlTemplate="https://m.taxiapp.com/ul/?action=getRide{&amp;destLat,destLong,missingParameter,url}">
                                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/xml/actions.xml:10: Error: The parameter extraParameter is not present in the urlTemplate [ValidActionsXml]
                        <parameter-mapping
                         ~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    fun testNotAllParametersCanBeRequired() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <action intentName="actions.intent.PLAY_MUSIC">
                        <fulfillment urlTemplate=
                                             "http://www.example.com/artist/{artist_name}?autoplay=true">
                            <parameter-mapping
                                    intentParameter="artist.name"
                                    urlParameter="artist_name"
                                    required="true"/>
                        </fulfillment>
                        <fulfillment urlTemplate=
                                             "http://www.example.com/album/{album_name}?autoplay=true">
                            <parameter-mapping
                                    intentParameter="album.name"
                                    urlParameter="album_name"
                                    required="true"/>
                        </fulfillment>
                        <fulfillment urlTemplate=
                                             "http://www.example.com/song/{song_name}?autoplay=true">
                            <parameter-mapping
                                    intentParameter="song.name"
                                    urlParameter="song_name"
                                    required="true"/>
                        </fulfillment>
                    </action>
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:2: Error: At least one <fulfillment> urlTemplate must not be required [ValidActionsXml]
                <action intentName="actions.intent.PLAY_MUSIC">
                 ~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testMissingMultipleParameters() {
        lint().files(
            xml(
                "res/xml/actions.xml",
                """
                <actions>
                    <action intentName="com.taxiapp.my.GET_ESTIMATE">
                    <fulfillment urlTemplate="https://m.taxiapp.com/ul/?action=getRide{&amp;destLat,destLong}">
                    </fulfillment>
                    </action>
                </actions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/actions.xml:3: Error: The parameters destLat and destLong are not defined as <parameter-mapping> elements below [ValidActionsXml]
                <fulfillment urlTemplate="https://m.taxiapp.com/ul/?action=getRide{&amp;destLat,destLong}">
                                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }
}
