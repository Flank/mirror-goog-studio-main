/*
 * Copyright (C) 2017 The Android Open Source Project
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

/**
 * The {@code Symbols} package contains classes used for parsing and processing android resources
 * and generating the R.java file.
 *
 * <p>The {@link com.android.builder.symbols.Symbol} class is used to represent a single android
 * resource by a resource type, a name, a java type and a value. A set of Symbols with unique
 * type/name pairs can be represented by a {@link com.android.builder.symbols.SymbolTable}.
 *
 * <p>Various parsers in this package were introduced to enable resource parsing without the use of
 * AAPT for libraries. They provide means to scan through the resource directory, parse XML files in
 * search for declared resources and find non-XML files, such as drawables.
 *
 * <p>The parsers' flow is as follows:
 *
 * <p>Library resources are passed to a {@link com.android.builder.symbols.ResourceDirectoryParser}.
 * There the parser goes through each of the directories and takes different paths depending on the
 * directories' names and their files' types:
 *
 * <ol>
 *   <li> If we are in a {@code values} directory (directory name starts with a "values" prefix and
 *       is followed by optional qualifiers, like "-v21" or "-w820dp"), all files inside are XML
 *       files with declared values inside of them (for example {@code values/strings.xml}). Parse
 *       each file with a {@link com.android.builder.symbols.ResourceValuesXmlParser}.
 *   <li> If we are in a non-values directory, create a Symbol for each file inside the directory,
 *       with the Symbol's name as the filename without the optional extension and the Symbol's type
 *       as the directory's name without extra qualifiers. For example for file {@code
 *       drawable-v21/a.png} we would create a new Symbol with name {@code "a"} and type {@code
 *       "drawable"}.
 *   <li> Additionally, if we are in a non-values directory and are parsing a file that is an XML
 *       file, we will parse the contents of the file in search of inline declared values. For
 *       example, a file {@code layout/activity_main.xml} could contain an inline declaration of an
 *       {@code id} such as {code android:id="@+id/activity_main"}. From such a line a new Symbol
 *       should be created with a name {@code "activity_main"} and type {@code "id"}. Such inline
 *       declarations are identified by the "@+" prefix and follow a "@+type/name" pattern. This is
 *       done by calling the {@code parse} method in {@link
 *       com.android.builder.symbols.ResourceExtraXmlParser}
 * </ol>
 *
 * <p>The {@link com.android.builder.symbols.ResourceDirectoryParser} collects all {@code Symbols}
 * from aforementioned cases and collects them in a {@code SymbolTable} which is later used to
 * create the R.txt and R.java files for the library as well as R.java files for all the libraries
 * it depends on.
 *
 * <p>It is worth mentioning that with this new flow, the new pipeline needs to also create minify
 * rules in the {@code aapt_rules.txt} file since we are not calling AAPT anymore. It is done by
 * parsing the library's android manifest, creating keep rules and writing the file in method {@link
 * com.android.builder.symbols.SymbolUtils#generateMinifyKeepRules}.
 *
 * <p>{@link com.android.builder.symbols.SymbolUtils#generateMainDexKeepRules method is used when
 * AAPT2 is enabled and we need to create the {@code manifest_keep.txt} file with keep rules for
 * Dex. In this case, we keep only nodes with shared processes and filter out remaining ones: if
 * their {@code process} is null, empty or starts with a colon symbol (private process).
 *
 * <p>Naming conventions:
 *
 * <ol>
 *   <li> Resource names declared in XML files inside the {@code values} directories are allowed to
 *       contain lower- and upper-case letters, numbers and the underscore character. Dots and
 *       colons are allowed to accommodate AAPT's old behaviour, but are deprecated and the support
 *       for them might end in the near future.
 *   <li>2. File names are allowed to contain lower- and upper-case letters, numbers and the
 *       underscore character. A dot is only allowed to separate the name from the extension (for
 *       example {@code "a.png"}), the usage of two dots is only allowed for 9-patch image extension
 *       (for example {@code "a.9.png"}). It is also worth noting that some resources can be
 *       declared with a prefix like {@code aapt:} or {@code android:}. Following aapt's original
 *       behaviour, we strip the type names from those prefixes. This behaviour is deprecated and
 *       might be the support for it might end in the near future.
 * </ol>
 *
 * <p>Example:
 *
 * <p>Assume in the resources directory we have the following sub-directories and files:
 *
 * <pre>
 * +---.drawable
 * |   +---.a.png
 * +---.layout
 * |   +---.activity_main.xml
 * +---.values
 * |   +---.colors.xml
 * </pre>
 *
 * <p>Contents of {@code activity_main,xml} include a {@code FloatingActionButton}:
 *
 * <pre>
 *     (...)
 *     <android.support.design.widget.FloatingActionButton
 *         android:id="@+id/fab"
 *         android:layout_width="wrap_content"
 *         android:layout_height="wrap_content"
 *         android:layout_gravity="bottom|end"
 *         android:layout_margin="@dimen/fab_margin"
 *         app:srcCompat="@android:drawable/ic_dialog_email" />
 *     (...)
 * </pre>
 *
 * <p>And {@code colors.xml} contains:
 *
 * <pre>
 * <resources  xmlns:android="http://schemas.android.com/apk/res/android"
 *     xmlns:aapt="http://schemas.android.com/aapt">
 *     <color name="colorPrimary">#3F51B5</color>
 *     <color name="colorPrimaryDark">#303F9F</color>
 *     <color name="colorAccent">#FF4081</color>
 * </resources>
 * </pre>
 *
 * <p>Then the parsers would create a following SymbolTable:
 *
 * <table>
 *     <caption>Symbol table</caption>
 *     <tr><th>Java type  </th><th>Resource type  </th><th>Resource name    </th><th>ID</th></tr>
 *     <tr><td>int        </td><td>drawable       </td><td>a                </td><td>1 </td></tr>
 *     <tr><td>int        </td><td>layout         </td><td>activity_main    </td><td>2 </td></tr>
 *     <tr><td>int        </td><td>id             </td><td>fab              </td><td>3 </td></tr>
 *     <tr><td>int        </td><td>color          </td><td>colorPrimary     </td><td>4 </td></tr>
 *     <tr><td>int        </td><td>color          </td><td>colorPrimaryDark </td><td>5 </td></tr>
 *     <tr><td>int        </td><td>color          </td><td>colorAccent      </td><td>6 </td></tr>
 * </table>
 *
 * <p>See {@code ResourceValuesXmlParserTest} and {@code ResourceDirectoryParserTest} for more
 * examples of the parsers' behaviour.
 */
package com.android.builder.symbols;
