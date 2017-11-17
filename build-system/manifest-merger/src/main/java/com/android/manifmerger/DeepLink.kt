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

package com.android.manifmerger

import com.android.annotations.VisibleForTesting
import com.android.ide.common.blame.SourceFilePosition
import com.google.common.collect.ImmutableList
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.lang.StringBuilder
import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern

/** Represents a loaded <deepLink></deepLink> element from a navigation xml file.
 * Has getters for the [SourceFilePosition] and the parsed input uri information.
 */
@VisibleForTesting
class DeepLink
constructor(
        val sourceFilePosition: SourceFilePosition, uri: String, val isAutoVerify: Boolean) {

    /** The list of URI schemes. */
    val schemes: List<String>
    /** The input uri's host or `null` if input uri contains no host. */
    val host: String?
    /** The input uri's port or `-1` if input uri contains no port. */
    val port: Int
    /** The input uri's path or `"/"` if input uri contains no path. */
    val path: String

    init {
        try {
            val deepLinkUri = DeepLinkUri(uri)
            this.schemes = deepLinkUri.schemes
            this.host = deepLinkUri.host
            this.port = deepLinkUri.port
            this.path = deepLinkUri.path
        } catch (e: URISyntaxException) {
            throw DeepLinkException(e)
        }

    }

    /**
     * A class representing an RFC 2396 compliant URI, following the same rules as java.net.URI,
     * except also allowing the following deviations in the input uri string:
     *
     *
     * 1. use of ".*" or "{placeholder}" wildcards in the URI path.
     *
     *
     * 2. use of "${applicationId}" in the URI host and path.
     *
     *
     * 3. use of ".*" wildcard at the beginning of the URI host.
     */
    @VisibleForTesting
    class DeepLinkUri
    constructor(uri: String) {

        val schemes: List<String>
        val host: String?
        val port: Int
        val path: String

        init {
            var encodedUri = uri
            // chooseEncoder() calls below cannot share any character input(s), or there could
            // be inaccurate decoding.
            // Use completely new char1 and char2 characters if adding a new Encoder in the future.
            val applicationIdEncoder = chooseEncoder(encodedUri, 'a', 'b')
            val pathWildcardEncoder = chooseEncoder(encodedUri, 'c', 'd')
            val wildcardEncoder = chooseEncoder(encodedUri, 'e', 'f')
            // must check for APPLICATION_ID_PLACEHOLDER before PATH_WILDCARD.
            encodedUri = encodedUri.replace(APPLICATION_ID_PLACEHOLDER, applicationIdEncoder)
            encodedUri = encodedUri.replace(PATH_WILDCARD.toRegex(), pathWildcardEncoder)
            encodedUri = encodedUri.replace(WILDCARD, wildcardEncoder)

            // If encodedUri doesn't contain regex "^[^/]*:/" (which would indicate it contains a
            // scheme) or start with "/" (which would indicate it's just a path),
            // then we want the first part of the uri to be interpreted as the host, but
            // java.net.URI will interpret it as the scheme,
            // unless we prepend it with "//", so we do.
            if (!Pattern.compile("^[^/]*:/").matcher(encodedUri).find() && !encodedUri.startsWith("/")) {
                encodedUri = "//" + encodedUri
            }

            // Attempt to construct URI after encoding all non-compliant characters.
            // If still not compliant, will throw URISyntaxException.
            val compliantUri = URI(encodedUri)

            // assign schemes
            val compliantScheme = compliantUri.scheme
            schemes = if (compliantScheme == null) {
                DEFAULT_SCHEMES
            } else if (compliantScheme.contains(applicationIdEncoder)
                    || compliantScheme.contains(pathWildcardEncoder)
                    || compliantScheme.contains(wildcardEncoder)) {
                throw DeepLinkException(
                        "Improper use of wildcards and/or placeholders in deeplink URI scheme")
            } else {
                ImmutableList.of(compliantScheme)
            }

            // assign host
            var compliantHost: String? = compliantUri.host
            if (compliantHost != null) {
                compliantHost = compliantHost.replace(applicationIdEncoder,
                        APPLICATION_ID_PLACEHOLDER)
                if (compliantHost.startsWith(wildcardEncoder)) {
                    compliantHost = HOST_WILDCARD + compliantHost.substring(wildcardEncoder.length)
                }
                if (compliantHost.contains(pathWildcardEncoder) || compliantHost.contains(
                        wildcardEncoder)) {
                    throw DeepLinkException(
                            "Improper use of wildcards and/or placeholders in deeplink URI host")
                }
            }
            host = compliantHost

            // assign port
            port = compliantUri.port

            // assign path
            var compliantPath: String? = compliantUri.path
            if (compliantPath == null || compliantPath.isEmpty()) {
                compliantPath = "/"
            }
            compliantPath = compliantPath.replace(applicationIdEncoder, APPLICATION_ID_PLACEHOLDER)
            // decode pathWildcardEncoder to WILDCARD instead of PATH_WILDCARD
            compliantPath = compliantPath.replace(pathWildcardEncoder, WILDCARD)
            compliantPath = compliantPath.replace(wildcardEncoder, WILDCARD)
            // prepend compliantPath with "/" if not present
            compliantPath = if (compliantPath.startsWith("/")) compliantPath else "/" + compliantPath
            path = compliantPath
        }

        companion object {

            private val DEFAULT_SCHEMES = ImmutableList.of("http", "https")
            private val APPLICATION_ID_PLACEHOLDER =
                    "\${" + PlaceholderHandler.APPLICATION_ID + "}"
            // PATH_WILDCARD looks for pairs of braces with anything except other braces between them
            private val PATH_WILDCARD = "\\{[^{}]*}"
            private val WILDCARD = ".*"
            private val HOST_WILDCARD = "*"

            /**
             * Returns a string which can be used as an encoder in the input uri string; i.e., the
             * encoder can be inserted or substituted anywhere in the uri string and is guaranteed to be
             * unique in the resulting string, allowing accurate decoding.
             *
             *
             * For example, chooseEncoder("www.example.com", 'w', 'x') returns "wwwwx", which, if
             * inserted or substituted in "www.example.com", will remain the only instance of itself in
             * the resulting string. So, e.g., we could encode the "."'s with "wwwwx"'s to yield
             * "wwwwwwwxexamplewwwwxcom", which can be unambiguously decoded back to the original
             * string.
             *
             * @param uri the String which the encoder will be designed to be inserted or substituted
             * into.
             * @param char1 any char
             * @param char2 any char other than char1
             * @return a string which can be used as an encoder in uri. The returned value consists of
             * repeated char1's followed by a single char2 that's not present in the original
             * uri string.
             */
            @VisibleForTesting
            fun chooseEncoder(uri: String, char1: Char, char2: Char): String {
                if (char1 == char2) {
                    throw IllegalArgumentException("char1 and char2 must be different")
                }
                // calculate length of longest substring of repeating char1's
                var longestLength = 0
                var currentLength = 0
                for (c in uri.toCharArray()) {
                    currentLength = if (c == char1) currentLength + 1 else 0
                    longestLength = if (currentLength > longestLength) currentLength else longestLength
                }
                val sb = StringBuilder()
                for (i in 0 until longestLength + 1) {
                    sb.append(char1)
                }
                sb.append(char2)
                return sb.toString()
            }
        }
    }

    /** An exception during the evaluation of a [DeepLink].  */
    class DeepLinkException : RuntimeException {

        constructor(s: String) : super(s)

        constructor(e: Exception) : super(e)
    }
}
