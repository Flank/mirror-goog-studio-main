/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.ide.common.resources;

import static java.util.Locale.US;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.SdkUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Provides access to locale information such as language names and
 * language to region name mappings for the various locales.
 * <p>
 * This is derived from a number of sources:
 * <li>
 *     <li> ICU (com.ibm.icu:icu4j:71.1 or later)
 *     <li> ISO 639-2: http://www.loc.gov/standards/iso639-2/ISO-639-2_utf-8.txt
 *     <li> ISO 3166-2: https://www.iso.org/obp/ui/#iso:code:3166:AQ
 * </li>
 */
public class LocaleManager {
    /** Utility methods only */
    private LocaleManager() {
    }

    /**
     * Returns the name of the given region for a 2 letter region code, in English.
     *
     * @param regionCode the 2 letter region code (ISO 3166-1 alpha-2),
     *                   or the 3 letter region ode (ISO 3166-2 alpha-3)
     * @return the name of the given region for a region code, in English, or
     *         null if not known
     */
    @Nullable
    public static String getRegionName(@NonNull String regionCode) {
        if (regionCode.length() == 2) {
            assert Character.isUpperCase(regionCode.charAt(0))
                    && Character.isUpperCase(regionCode.charAt(1)) : regionCode;
            int index = Arrays.binarySearch(ISO_3166_1_CODES, regionCode);
            if (index < 0 || index >= ISO_3166_1_TO_2.length) {
                return null;
            }
            return ISO_3166_2_NAMES[ISO_3166_1_TO_2[index]];
        } else if (regionCode.length() == 3) {
            assert Character.isUpperCase(regionCode.charAt(0))
                    && Character.isUpperCase(regionCode.charAt(1))
                    && Character.isUpperCase(regionCode.charAt(2)) : regionCode;
            int index = Arrays.binarySearch(ISO_3166_2_CODES, regionCode);
            if (index < 0) {
                return null;
            }
            return ISO_3166_2_NAMES[index];
        }

        return null;
    }

    /**
     * Returns the name of the given language for a language code, in English.
     *
     * @param languageCode the 2 letter language code (ISO 639-1), or
     *                     3 letter language code (ISO 639-2)
     * @return the name of the given language for a language code, in English, or
     *         null if not known
     */
    @Nullable
    public static String getLanguageName(@NonNull String languageCode) {
        if (languageCode.length() == 2) {
            assert Character.isLowerCase(languageCode.charAt(0))
                    && Character.isLowerCase(languageCode.charAt(1)) : languageCode;
            int index = Arrays.binarySearch(ISO_639_1_CODES, languageCode);
            if (index < 0 || index >= ISO_639_1_TO_2.length) {
                return null;
            }
            return ISO_639_2_NAMES[ISO_639_1_TO_2[index]];
        } else if (languageCode.length() == 3) {
            assert Character.isLowerCase(languageCode.charAt(0))
                    && Character.isLowerCase(languageCode.charAt(1))
                    && Character.isLowerCase(languageCode.charAt(2)) : languageCode;
            int index = Arrays.binarySearch(ISO_639_2_CODES, languageCode);
            if (index < 0) {
                return null;
            }
            return ISO_639_2_NAMES[index];
        }

        return null;
    }

    /**
     * Returns all the known language codes
     *
     * @return all the known language codes
     */
    @NonNull
    public static List<String> getLanguageCodes() {
        return getLanguageCodes(false);
    }

    /**
     * Returns all the known language codes
     *
     * @param include3 If true, include 3-letter language codes as well (for
     *                 languages not available as 2-letter languages)
     * @return all the known language codes
     */
    @NonNull
    public static List<String> getLanguageCodes(boolean include3) {
        if (!include3) {
            return Arrays.asList(ISO_639_1_CODES);
        } else {
            List<String> codes = new ArrayList<>(ISO_639_2_CODES.length);
            for (int i = 0; i < ISO_639_2_TO_1.length; i++) {
                int iso2 = ISO_639_2_TO_1[i];
                if (iso2 != -1) {
                    codes.add(ISO_639_1_CODES[iso2]);
                } else {
                    codes.add(ISO_639_2_CODES[i]);
                }
            }
            return codes;
        }
    }

    /**
     * Returns all the known region codes
     *
     * @return all the known region codes
     */
    @NonNull
    public static List<String> getRegionCodes() {
        return getRegionCodes(false);
    }

    /**
     * Returns all the known region codes
     *
     * @param include3 If true, include 3-letter region codes as well (for
     *                 regions not available as 2-letter regions)
     * @return all the known region codes
     */
    @NonNull
    public static List<String> getRegionCodes(boolean include3) {
        if (!include3) {
            return Arrays.asList(ISO_3166_1_CODES);
        } else {
            List<String> codes = new ArrayList<>(ISO_3166_2_CODES.length);
            for (int i = 0; i < ISO_3166_2_TO_1.length; i++) {
                int iso2 = ISO_3166_2_TO_1[i];
                if (iso2 != -1) {
                    codes.add(ISO_3166_1_CODES[iso2]);
                } else {
                    codes.add(ISO_3166_2_CODES[i]);
                }
            }
            return codes;
        }
    }

    /**
     * Returns true if the given language code represents a valid/known 2 or 3 letter
     * language code. (By convention, language codes should be lower case.)
     *
     * @param languageCode the language code to look up
     * @return true if this is a known language
     */
    public static boolean isValidLanguageCode(@NonNull String languageCode) {
        if (languageCode.length() == 2) {
            assert Character.isLowerCase(languageCode.charAt(0))
                    && Character.isLowerCase(languageCode.charAt(1)) : languageCode;
            return Arrays.binarySearch(ISO_639_1_CODES, languageCode) >= 0;
        } else if (languageCode.length() == 3) {
            assert Character.isLowerCase(languageCode.charAt(0))
                    && Character.isLowerCase(languageCode.charAt(1))
                    && Character.isLowerCase(languageCode.charAt(2)) : languageCode;
            return Arrays.binarySearch(ISO_639_2_CODES, languageCode) >= 0;
        }

        return false;
    }

    /**
     * Returns true if the given region code represents a valid/known 2 or 3 letter
     * region code. (By convention, region codes should be upper case.)
     *
     * @param regionCode the region code to look up
     * @return true if this is a known region
     */
    public static boolean isValidRegionCode(@NonNull String regionCode) {
        if (regionCode.length() == 2) {
            assert Character.isUpperCase(regionCode.charAt(0))
                    && Character.isUpperCase(regionCode.charAt(1)) : regionCode;
            return Arrays.binarySearch(ISO_3166_1_CODES, regionCode) >= 0;
        } else if (regionCode.length() == 3) {
            assert Character.isUpperCase(regionCode.charAt(0))
                    && Character.isUpperCase(regionCode.charAt(1))
                    && Character.isUpperCase(regionCode.charAt(2)) : regionCode;
            return Arrays.binarySearch(ISO_3166_2_CODES, regionCode) >= 0;
        }

        return false;
    }

    /**
     * Like {@link #getLanguageRegion(String)}, but does not take user preferences
     * and locations into consideration.
     */
    @Nullable
    public static String getDefaultLanguageRegion(@NonNull String languageCode) {
        if (languageCode.length() == 2) {
            assert Character.isLowerCase(languageCode.charAt(0))
                    && Character.isLowerCase(languageCode.charAt(1)) : languageCode;
            int index = Arrays.binarySearch(ISO_639_1_CODES, languageCode);
            if (index < 0 || index >= ISO_639_1_TO_2.length) {
                return null;
            }
            int regionIndex = LANGUAGE_REGION[ISO_639_1_TO_2[index]];
            if (regionIndex != -1) {
                int twoLetterIndex = ISO_3166_2_TO_1[regionIndex];
                if (twoLetterIndex != -1) {
                    return ISO_3166_1_CODES[twoLetterIndex];
                } else {
                    return ISO_3166_2_CODES[regionIndex];
                }
            }
            return null;
        } else if (languageCode.length() == 3) {
            assert Character.isLowerCase(languageCode.charAt(0))
                    && Character.isLowerCase(languageCode.charAt(1))
                    && Character.isLowerCase(languageCode.charAt(2)) : languageCode;
            int index = Arrays.binarySearch(ISO_639_2_CODES, languageCode);
            if (index < 0) {
                return null;
            }
            return getRegionCode(LANGUAGE_REGION[index]);
        }

        assert false : languageCode;
        return null;
    }

    /**
     * Returns the region code for the given language. <b>Note that there can be
     * many regions that speak a given language; this just picks one</b> based
     * on a set of heuristics.
     *
     * @param languageCode the language to look up
     * @return the corresponding region code, if any
     */
    @Nullable
    public static String getLanguageRegion(@NonNull String languageCode) {
        // Try to pick one language based on various heuristics:

        // (1) Check to see if the user has deliberately picked a preferred
        //     region for this language with an option. That should always
        //     win. Example: STUDIO_LOCALES="en_GB,pt_PT" says that for English
        //     we should always use the region GB and for Portuguese we should always
        //     use the region PT. Also allow en-GB and en-rGB.
        String option = System.getenv("STUDIO_LOCALES");
        if (option == null) {
            option = System.getProperty("studio.locales");
        }
        if (option != null) {
            for (String regionLocale : Splitter.on(',').trimResults().split(option)) {
                if (SdkUtils.startsWithIgnoreCase(regionLocale, languageCode)) {
                    if (regionLocale.length() == 5 && ((regionLocale.charAt(2) == '_')
                            || regionLocale.charAt(2) == '-')) {
                        return regionLocale.substring(3).toUpperCase(US);
                    } else if (regionLocale.length() == 6 && regionLocale.charAt(2) == '-'
                            && regionLocale.charAt(3) == 'r') {
                        return regionLocale.substring(4).toUpperCase(US);
                    }
                }
            }
        }

        // (2) Check the user's locale; if it happens to be in the same language
        //     as the target language, and it specifies a region, use that region
        Locale locale = Locale.getDefault();
        if (languageCode.equalsIgnoreCase(locale.getLanguage())) {
            String country = locale.getCountry();
            if (!country.isEmpty()) {
                country = country.toUpperCase(US);
                if (country.length() == 2) {
                    return country;
                }
            }
        }

        // Do we have multiple known regions for this locale? If so, try to pick
        // among them based on heuristics.
        List<String> regions = getRelevantRegions(languageCode);
        if (regions.size() > 1) {
            // Some languages are used in a huge number of regions (English is
            // in 90+ regions for example), and similarly, some regions have multiple
            // major languages (Switzerland for example). In these cases we don't want
            // to show a region flag for the local region (e.g. for Switzerland you
            // would see the same flag for German, French, Italian, ...).
            // Therefore, only use region lookup for a subset of languages where
            // we're not sure.
            List<String> relevant = getDisambiguateRegions(languageCode);

            // (3) Check the user's country. The user may not be using the target
            //     language, but if the current country matches one of the relevant
            //     regions, use it.
            String country = locale.getCountry();
            if (!country.isEmpty() && relevant != null) {
                country = country.toUpperCase(US);
                if (country.length() == 2 && regions.contains(country) &&
                        (relevant.isEmpty() || relevant.contains(country))) {
                    return country;
                }
            }

            // (4) Look at the user's network location; if we can resolve
            //     the domain name, the TLD might be an ISO 3166 country code:
            //     http://en.wikipedia.org/wiki/Country_code_top-level_domain
            //     If so, and that country code is in one of the candidate regions,
            //     use it. (Note the exceptions listed in there; we should treat
            //     "uk" as "gb" for ISO code lookup.)
            //
            //   NOTE DONE: It turns out this is tricky. Looking up the current domain
            //     typically requires a network connection, sometimes it can
            //     take seconds, and even the domain name may not be helpful;
            //     it may be for example a .com address.


            // (5) Use the timezone! The timezone can give us a very good clue
            //     about the region. In many cases we can get an exact match,
            //     e.g. if we're looking at the timezone Europe/Lisbon we know
            //     the region is PT. (In the future we could extend this to
            //     not only map from timezone to region code, but to look at
            //     the continent and raw offsets for further clues to guide the
            //     region choice.)
            if (relevant != null) {
                String region = getTimeZoneRegionAlpha2(TimeZone.getDefault());
                if (region == null) {
                    // Unknown timezone: ambiguous and don't attempt to guess
                    return null;
                }
                if (regions.contains(region) && (relevant.isEmpty() || relevant.contains(region))) {
                    return region;
                }
            }

            //
            // (6) Look at installed locales, and limit our options to the regions
            //     found in locales for the given language.
            //     For example, on my system, the LocaleManager provides 90
            //     relevant regions for English, but my system only has 11,
            //     so we can eliminate the remaining 79 from consideration.
            //     (Sadly, it doesn't look like the local locales are sorted
            //     in any way significant for the user, so we can't just assume
            //     that the first locale of the target language is somehow special.)
            Locale candidate = null;
            for (Locale available : Locale.getAvailableLocales()) {
                if (languageCode.equals(available.getLanguage()) &&
                        regions.contains(available.getCountry())) {
                    if (candidate != null) {
                        candidate = null; // more than one match; doesn't help us
                        break;
                    } else {
                        candidate = available;
                    }
                }
            }
            if (candidate != null && relevant != null &&
                    (relevant.isEmpty() || relevant.contains(candidate.getCountry()))) {
                return candidate.getCountry();
            }

            //
            // (7) Give preference to a region that has the same region code
            //     as the language code; this is usually where the language is named
            //     after a region
            char first = Character.toUpperCase(languageCode.charAt(0));
            char second = Character.toUpperCase(languageCode.charAt(1));
            for (String r : regions) {
                if (r.charAt(0) == first && r.charAt(1) == second) {
                    return r;
                }
            }
        } else if (regions.size() == 1) {
            return regions.get(0);
        }

        // Finally just pick the default one
        return getDefaultLanguageRegion(languageCode);
    }

    @Nullable
    private static List<String> getDisambiguateRegions(@NonNull String languageCode) {
        if ("ar".equals(languageCode) || "zh".equals(languageCode)) {
            return Collections.emptyList();
        } else if ("en".equals(languageCode)) {
            return Arrays.asList("US", "GB");
        } else if ("es".equals(languageCode)) {
            return Arrays.asList("MX", "AR", "CL", "CO", "CR", "CU", "DO", "GT", "HN", "NI",
                    "PA", "PY", "SV", "UY", "VE", "ME");
        } else if ("pt".equals(languageCode)) {
            return Arrays.asList("PT", "BR");
        } else if ("ru".equals(languageCode)) {
            return Collections.singletonList("ru");
        } else {
            return null;
        }
    }

    /**
     * Get the region code (either 3166-1 or if necessary, 3166-2) for the given
     * 3166-2 region code
     */
    private static String getRegionCode(int index) {
        if (index != -1) {
            int twoLetterIndex = ISO_3166_2_TO_1[index];
            if (twoLetterIndex != -1) {
                return ISO_3166_1_CODES[twoLetterIndex];
            } else {
                return ISO_3166_2_CODES[index];
            }
        }

        return null;
    }

    /** Returns the relevant regions for the given language, if known. */
    @NonNull
    public static List<String> getRelevantRegions(@NonNull String languageCode) {
        int languageIndex;
        if (languageCode.length() == 2) {
            assert Character.isLowerCase(languageCode.charAt(0))
                    && Character.isLowerCase(languageCode.charAt(1)) : languageCode;
            int index = Arrays.binarySearch(ISO_639_1_CODES, languageCode);
            if (index < 0 || index >= ISO_639_1_TO_2.length) {
                return Collections.emptyList();
            }
            languageIndex = ISO_639_1_TO_2[index];
        } else if (languageCode.length() == 3) {
            assert Character.isLowerCase(languageCode.charAt(0))
                    && Character.isLowerCase(languageCode.charAt(1))
                    && Character.isLowerCase(languageCode.charAt(2)) : languageCode;
            languageIndex = Arrays.binarySearch(ISO_639_2_CODES, languageCode);
            if (languageIndex < 0) {
                return Collections.emptyList();
            }
        } else {
            assert false : languageCode;
            return Collections.emptyList();
        }

        int[] regionIndices = LANGUAGE_REGIONS[languageIndex];
        if (regionIndices == null) { // only returns non-null when there are multiple
            String regionCode = getRegionCode(LANGUAGE_REGION[languageIndex]);
            return regionCode != null ? Collections.singletonList(regionCode)
                    : Collections.emptyList();

        }

        List<String> result = new ArrayList<>(regionIndices.length);
        for (int regionIndex : regionIndices) {
            String regionCode = getRegionCode(regionIndex);
            if (regionCode != null) {
                result.add(regionCode);
            }
        }
        return result;
    }

    /**
     * Returns the corresponding ISO 639 alpha-2 code given an alpha-3 code
     *
     * @param languageCode the ISO 639 alpha-3 code
     * @return the corresponding ISO 639 alpha-2 code, if any
     */
    @Nullable
    public static String getLanguageAlpha2(@NonNull String languageCode) {
        assert languageCode.length() == 3 : languageCode;
        assert Character.isLowerCase(languageCode.charAt(0))
                && Character.isLowerCase(languageCode.charAt(1))
                && Character.isLowerCase(languageCode.charAt(2)) : languageCode;
        int index = Arrays.binarySearch(ISO_639_2_CODES, languageCode);
        if (index < 0) {
            return null;
        }
        int alpha2 = ISO_639_2_TO_1[index];
        if (alpha2 != -1) {
            return ISO_639_1_CODES[alpha2];
        }
        return null;
    }

    /**
     * Returns the corresponding ISO 639 alpha-3 code given an alpha-2 code
     *
     * @param languageCode the ISO 639 alpha-2 code
     * @return the corresponding ISO 639 alpha-3 code, if any
     */
    @Nullable
    public static String getLanguageAlpha3(@NonNull String languageCode) {
        assert languageCode.length() == 2 : languageCode;
        assert Character.isLowerCase(languageCode.charAt(0))
                && Character.isLowerCase(languageCode.charAt(1)) : languageCode;
        int index = Arrays.binarySearch(ISO_639_1_CODES, languageCode);
        if (index < 0) {
            return null;
        }
        int alpha2 = ISO_639_1_TO_2[index];
        if (alpha2 != -1) {
            return ISO_639_2_CODES[alpha2];
        }
        return null;
    }

    /**
     * Returns the corresponding ISO 3166 alpha-2 code given an alpha-3 code
     *
     * @param regionCode the ISO 3166 alpha-3 code
     * @return the corresponding ISO 3166 alpha-2 code, if any
     */
    @Nullable
    public static String getRegionAlpha2(@NonNull String regionCode) {
        assert regionCode.length() == 3 : regionCode;
        assert Character.isUpperCase(regionCode.charAt(0))
                && Character.isUpperCase(regionCode.charAt(1))
                && Character.isUpperCase(regionCode.charAt(2)) : regionCode;
        int index = Arrays.binarySearch(ISO_3166_2_CODES, regionCode);
        if (index < 0) {
            return null;
        }
        int alpha2 = ISO_3166_2_TO_1[index];
        if (alpha2 != -1) {
            return ISO_3166_1_CODES[alpha2];
        }
        return null;
    }

    /**
     * Returns the corresponding ISO 3166 alpha-3 code given an alpha-2 code
     *
     * @param regionCode the ISO 3166 alpha-2 code
     * @return the corresponding ISO 3166 alpha-3 code, if any
     */
    @Nullable
    public static String getRegionAlpha3(@NonNull String regionCode) {
        assert regionCode.length() == 2 : regionCode;
        assert Character.isUpperCase(regionCode.charAt(0))
                && Character.isUpperCase(regionCode.charAt(1)) : regionCode;
        int index = Arrays.binarySearch(ISO_3166_1_CODES, regionCode);
        if (index < 0) {
            return null;
        }
        int alpha2 = ISO_3166_1_TO_2[index];
        if (alpha2 != -1) {
            return ISO_3166_2_CODES[alpha2];
        }
        return null;
    }

    /**
     * Guess the 3-letter region code containing the given time zone
     *
     * @param zone The timezone to look up
     * @return the corresponding 3 letter region code
     */
    @SuppressWarnings("SpellCheckingInspection")
    @Nullable
    @VisibleForTesting
    public static String getTimeZoneRegionAlpha3(@NonNull TimeZone zone) {
        int index = getTimeZoneRegionIndex(zone);
        if (index != -1) {
            return ISO_3166_2_CODES[index];
        }

        return null;
    }

    /**
     * Guess the 2-letter region code containing the given time zone
     *
     * @param zone The timezone to look up
     * @return the corresponding 2 letter region code
     */
    @SuppressWarnings("SpellCheckingInspection")
    @Nullable
    @VisibleForTesting
    public static String getTimeZoneRegionAlpha2(@NonNull TimeZone zone) {
        int index = getTimeZoneRegionIndex(zone);
        if (index != -1) {
            index = ISO_3166_2_TO_1[index];
            if (index != -1) {
                return ISO_3166_1_CODES[index];
            }
        }

        return null;
    }

    // The remainder of this class is generated by generate-locale-data
    // DO NOT EDIT MANUALLY

    private static int getTimeZoneRegionIndex(@NonNull TimeZone zone) {
        // Instead of String#hashCode, use this to ensure stable across platforms
        String id = zone.getID();
        int hashedId = 0;
        for (int i = 0, n = id.length(); i < n; i++) {
            hashedId = 31 * hashedId + id.charAt(i);
        }
        switch (zone.getRawOffset()) {
            case -39600000:
                switch (hashedId) {
                    case -596311663:
                        return 166;
                    case -1853972947:
                        return 233;
                    default:
                        return 10;
                }
            case -36000000:
                switch (hashedId) {
                    case -605081605:
                        return 48;
                    case -1660850775:
                        return 186;
                    case 946756753:
                        return 233;
                    default:
                        return 235;
                }
            case -34200000:
                return 186;
            case -32400000:
                switch (hashedId) {
                    case 1404986049:
                        return 186;
                    default:
                        return 235;
                }
            case -28800000:
                switch (hashedId) {
                    case 2116473746:
                        return 175;
                    case 900028252:
                    case 364935240:
                        return 39;
                    case -1536188513:
                    case 79537:
                    case 1946599416:
                        return 235;
                    default:
                        return 143;
                }
            case -25200000:
                switch (hashedId) {
                    case 202222115:
                    case 611591843:
                    case 2142546433:
                    case 1532263802:
                    case -641163936:
                        return 143;
                    case -1203556979:
                    case 1392614359:
                    case -1927172530:
                    case 1159165634:
                    case -1968700029:
                    case 79382:
                    case 2011698031:
                    case -657922306:
                        return 235;
                    default:
                        return 39;
                }
            case -21600000:
                switch (hashedId) {
                    case 1335284620:
                        return 29;
                    case -355081471:
                        return 52;
                    case 662067781:
                        return 66;
                    case 268098540:
                        return 93;
                    case -1192934179:
                        return 99;
                    case -496169397:
                        return 165;
                    case -610612331:
                        return 201;
                    case 35870737:
                    case -2089950224:
                        return 42;
                    case 1033313139:
                    case 958016402:
                    case 1650383341:
                    case -1436528620:
                    case -905842704:
                    case -380253810:
                        return 143;
                    case -1997850159:
                    case 1290869225:
                    case 1793201705:
                    case 1334007082:
                    case 99854508:
                    case 569007676:
                    case 1837303604:
                    case -1958461186:
                        return 39;
                    default:
                        return 235;
                }
            case -18000000:
                switch (hashedId) {
                    case 1675357736:
                        return 25;
                    case 1344376451:
                        return 49;
                    case 407688513:
                        return 66;
                    case -1611524809:
                        return 101;
                    case 1360610048:
                        return 56;
                    case 1360273357:
                        return 143;
                    case 1732450137:
                        return 174;
                    case 2039677810:
                        return 176;
                    case -223474994:
                        return 216;
                    case 1503655288:
                    case 2111569:
                        return 53;
                    case 1135364699:
                    case -163519108:
                        return 113;
                    case -615687308:
                    case 42696295:
                    case -1756511823:
                    case 1213658776:
                        return 32;
                    case 1908749375:
                    case -1694184172:
                    case 695184620:
                    case 1356626855:
                    case 622452689:
                    case 977509670:
                    case 151241566:
                    case 1826315056:
                    case -792567293:
                        return 39;
                    default:
                        return 235;
                }
            case -14400000:
                switch (hashedId) {
                    case 2116646736:
                        return 13;
                    case 830494036:
                        return 3;
                    case 1501639611:
                        return 8;
                    case -1204380126:
                        return 0;
                    case -77181033:
                        return 33;
                    case -2123999328:
                        return 27;
                    case 1572742253:
                        return 30;
                    case 1617469984:
                        return 31;
                    case 1553377762:
                        return 20;
                    case -204998961:
                        return 54;
                    case -1482667423:
                        return 62;
                    case -432820086:
                        return 64;
                    case -1047459319:
                        return 91;
                    case -1187130823:
                        return 92;
                    case -1594534092:
                        return 86;
                    case 1493585930:
                        return 96;
                    case -1396416648:
                        return 122;
                    case -1395152331:
                        return 129;
                    case -492237152:
                        return 137;
                    case 10078612:
                        return 155;
                    case -1922505842:
                        return 154;
                    case 1367207089:
                        return 184;
                    case -191720853:
                        return 213;
                    case -40156994:
                        return 225;
                    case -760053978:
                        return 238;
                    case -777581977:
                        return 239;
                    case 1826464710:
                        return 240;
                    case -611834443:
                    case -2036395347:
                        return 42;
                    case -691236908:
                    case 79506:
                        return 181;
                    case -82660415:
                    case 1911738030:
                        return 241;
                    case -1680637607:
                    case 1275531960:
                    case -2087755565:
                    case -640330778:
                    case -95289381:
                    case -2011036567:
                        return 39;
                    default:
                        return 32;
                }
            case -12600000:
                return 39;
            case -10800000:
                switch (hashedId) {
                    case -1940316618:
                        return 42;
                    case -105805749:
                        return 75;
                    case -770987206:
                        return 94;
                    case 413002919:
                        return 204;
                    case -159391975:
                        return 208;
                    case 1987071743:
                        return 234;
                    case -1134084912:
                    case 2039749182:
                        return 92;
                    case 1231674648:
                    case -1203975328:
                    case -1203852432:
                    case -615687308:
                    case -1887400619:
                    case 1646238717:
                    case 42696295:
                    case 1793082297:
                    case -1756511823:
                    case -612056498:
                    case -1523781592:
                    case 65649:
                    case 1213658776:
                    case 1213776064:
                        return 32;
                    default:
                        return 8;
                }
            case -7200000:
                return 32;
            case -3600000:
                switch (hashedId) {
                    case -133690113:
                        return 51;
                    case -246124943:
                        return 92;
                    default:
                        return 183;
                }
            case 0:
                switch (hashedId) {
                    case -896333693:
                        return 21;
                    case -2133882145:
                        return 69;
                    case -2002672065:
                        return 70;
                    case 1811467169:
                        return 82;
                    case 1799826075:
                        return 83;
                    case -1256614937:
                        return 92;
                    case -12863673:
                        return 87;
                    case 889260510:
                        return 85;
                    case -5318512:
                        return 88;
                    case -1242472063:
                        return 104;
                    case 457741181:
                        return 114;
                    case 2061328666:
                        return 127;
                    case -3562122:
                        return 138;
                    case -12902420:
                        return 146;
                    case 2035412001:
                        return 153;
                    case -1469296608:
                        return 197;
                    case 844576499:
                        return 200;
                    case 1802544234:
                        return 194;
                    case 103410438:
                        return 207;
                    case -495790896:
                        return 218;
                    case -1262455546:
                    case 302807382:
                        return 44;
                    case -1917036507:
                    case -1585848154:
                        return 77;
                    case 2160119:
                    case 300259341:
                        return 107;
                    case -1722575083:
                    case -1000832298:
                        return 110;
                    case -1677314468:
                    case 518707320:
                    case 794006110:
                        return 183;
                    default:
                        return 80;
                }
            case 3600000:
                switch (hashedId) {
                    case -2142864836:
                        return 6;
                    case 747709736:
                        return 5;
                    case 291514280:
                        return 2;
                    case 804593244:
                        return 15;
                    case 1036497278:
                        return 26;
                    case -516035308:
                        return 18;
                    case 455493998:
                        return 19;
                    case 1841315615:
                        return 46;
                    case -12866559:
                        return 38;
                    case 965375949:
                        return 47;
                    case 930574244:
                        return 41;
                    case 57523521:
                        return 45;
                    case 641004357:
                        return 58;
                    case -862787273:
                        return 63;
                    case -977866396:
                        return 65;
                    case -611902065:
                        return 79;
                    case -1792691717:
                        return 84;
                    case 301988171:
                        return 89;
                    case 911784828:
                        return 100;
                    case 1643067635:
                        return 102;
                    case -1407095582:
                        return 112;
                    case -667021103:
                        return 130;
                    case 432607731:
                        return 134;
                    case 552727310:
                        return 139;
                    case -1834768363:
                        return 149;
                    case 720852545:
                        return 145;
                    case -675325160:
                        return 147;
                    case 1266363370:
                        return 160;
                    case 337689424:
                        return 162;
                    case 1809928993:
                        return 164;
                    case 1107183657:
                        return 167;
                    case 562540219:
                        return 205;
                    case -1783944015:
                        return 211;
                    case -1262503490:
                        return 210;
                    case 1108411820:
                        return 198;
                    case -1871032358:
                        return 209;
                    case -1348903496:
                        return 202;
                    case -1793726419:
                        return 217;
                    case 1817919522:
                        return 226;
                    case -1042753021:
                        return 237;
                    case 228701359:
                    case 2079834968:
                        return 59;
                    case 1801750059:
                    case 539516618:
                        return 70;
                    case 68470:
                    case -672549154:
                        return 76;
                    case -1121325742:
                    case 73413677:
                        return 128;
                    case -72083073:
                    case -1407181132:
                        return 168;
                    default:
                        return 180;
                }
            case 7200000:
                switch (hashedId) {
                    case 250954279:
                        return 4;
                    case -669373067:
                        return 23;
                    case 579178556:
                        return 17;
                    case 308014032:
                        return 37;
                    case 2046322969:
                        return 46;
                    case 1469914287:
                        return 71;
                    case -1854672812:
                        return 73;
                    case 213620546:
                        return 90;
                    case -1678352343:
                        return 115;
                    case -468176592:
                        return 126;
                    case 302201054:
                        return 132;
                    case -820952635:
                        return 133;
                    case -1407101538:
                        return 135;
                    case 1327019492:
                        return 157;
                    case -1305089392:
                        return 189;
                    case 1640682817:
                        return 190;
                    case 251969386:
                        return 191;
                    case 581080470:
                        return 193;
                    case -495845057:
                        return 206;
                    case 1088211684:
                        return 215;
                    case 790198029:
                        return 212;
                    case 1587535273:
                        return 250;
                    case 292038242:
                        return 251;
                    case 159021648:
                        return 252;
                    case -2046172313:
                        return -1;
                    case -1121325742:
                    case 73413677:
                        return 128;
                    case 597833397:
                    case 1289680939:
                        return 140;
                    case 302127113:
                    case 66486:
                        return 152;
                    case -1439446106:
                    case -296610415:
                        return 185;
                    case 44334398:
                    case 540421055:
                    case 660679831:
                        return 57;
                    case 65091:
                    case 1801619315:
                    case 66911291:
                        return 67;
                    case 511371267:
                    case -1868494453:
                    case -2095341728:
                        return 111;
                    default:
                        return 232;
                }
            case 10800000:
                switch (hashedId) {
                    case -1744032040:
                        return 24;
                    case -675084931:
                        return 28;
                    case -1039536273:
                        return 61;
                    case -1495834442:
                        return 72;
                    case -1745250846:
                        return 109;
                    case -1396737853:
                        return 50;
                    case -195337532:
                        return 124;
                    case 1137056040:
                        return 141;
                    case -1663926768:
                        return 187;
                    case -5956312:
                        return 192;
                    case 1356856394:
                        return 203;
                    case -1210461823:
                        return 230;
                    case -1001939224:
                        return 231;
                    case -1439622607:
                        return 249;
                    case -453821949:
                        return 159;
                    case -2046172313:
                        return -1;
                    case -24907990:
                    case -24904146:
                        return 68;
                    case 1656950469:
                    case 68408:
                        return 118;
                    case 207779975:
                    case -359165265:
                    case -1778564402:
                        return 227;
                    default:
                        return 190;
                }
            case 12600000:
                return 108;
            case 14400000:
                switch (hashedId) {
                    case -1675354028:
                        return 7;
                    case -1439595506:
                        return 16;
                    case 1375514249:
                        return 81;
                    case -1510507213:
                        return 156;
                    case -138196720:
                        return 172;
                    case -200488828:
                        return 188;
                    case -1131894135:
                        return 214;
                    case -2046172313:
                        return -1;
                    case 1612067903:
                    case 77181:
                        return 9;
                    default:
                        return 190;
                }
            case 16200000:
                return 1;
            case 18000000:
                switch (hashedId) {
                    case 1625237543:
                        return 190;
                    case 1836129403:
                        return 220;
                    case 1957264442:
                    case 79320:
                        return 173;
                    case -1188197398:
                    case 1941538258:
                        return 222;
                    case -1967893801:
                    case -913591097:
                        return 236;
                    default:
                        return 117;
                }
            case 19800000:
                switch (hashedId) {
                    case -452104218:
                        return 131;
                    default:
                        return 105;
                }
            case 20700000:
                return 169;
            case 21600000:
                switch (hashedId) {
                    case -1403567769:
                        return 106;
                    case -1505128528:
                        return 119;
                    case 49913296:
                    case 1547325344:
                        return 35;
                    case 1958400136:
                    case 88135602:
                        return 43;
                    case -490238295:
                    case 1998854729:
                        return 117;
                    case -1675948833:
                    case -1675741970:
                    case 66083:
                        return 22;
                    default:
                        return 190;
                }
            case 23400000:
                switch (hashedId) {
                    case -737802333:
                        return 40;
                    default:
                        return 148;
                }
            case 25200000:
                switch (hashedId) {
                    case 82971018:
                        return 55;
                    case 1075312767:
                        return 120;
                    case -173837534:
                        return 125;
                    case -1439402982:
                        return 150;
                    case -1738808822:
                        return 219;
                    case 1063310893:
                    case -788096746:
                        return 103;
                    case 1214715332:
                    case 14814128:
                    case 85303:
                        return 242;
                    default:
                        return 190;
                }
            case 28800000:
                switch (hashedId) {
                    case -455817678:
                        return 34;
                    case -156810007:
                        return 177;
                    case 43451613:
                        return 229;
                    case 307946178:
                    case 1811257630:
                        return 14;
                    case 404568855:
                    case -390386883:
                        return 97;
                    case -463608032:
                    case -84259736:
                        return 103;
                    case -1667637192:
                    case -1667637186:
                        return 136;
                    case -99068543:
                    case -1778758162:
                        return 158;
                    case 663100500:
                    case -808657565:
                        return 190;
                    case 133428255:
                    case 499614468:
                        return 195;
                    case -264449833:
                    case -2119377995:
                    case -1043408894:
                        return 150;
                    default:
                        return 43;
                }
            case 31500000:
                return 14;
            case 32400000:
                switch (hashedId) {
                    case -996350568:
                        return 103;
                    case 174722043:
                        return 182;
                    case -1304192311:
                        return 178;
                    case -1439528217:
                        return 223;
                    case -1661964753:
                    case 81326:
                        return 123;
                    case -1660747039:
                    case 73771:
                    case 71341030:
                        return 116;
                    default:
                        return 190;
                }
            case 34200000:
                return 14;
            case 36000000:
                switch (hashedId) {
                    case -596509280:
                        return 95;
                    case -1689443992:
                        return 151;
                    case 1882983613:
                        return 179;
                    case -1315980288:
                    case -596124262:
                    case -711962206:
                        return 78;
                    case 1988570398:
                    case -572853474:
                    case 1409241312:
                    case -402306110:
                    case 1755599521:
                    case 1491561941:
                        return 190;
                    default:
                        return 14;
                }
            case 37800000:
                return 14;
            case 39600000:
                switch (hashedId) {
                    case -1819305733:
                        return 161;
                    case -566871917:
                        return 163;
                    case -245513103:
                        return 179;
                    case -1314212085:
                        return 243;
                    case 180455737:
                    case 82420:
                        return 199;
                    case -1905248083:
                    case 1199139305:
                    case -1762267155:
                        return 78;
                    default:
                        return 190;
                }
            case 43200000:
                switch (hashedId) {
                    case -596550328:
                        return 74;
                    case -1660560468:
                        return 121;
                    case -1306030177:
                        return 170;
                    case -540079540:
                        return 228;
                    case -596051542:
                        return 233;
                    case -1574841606:
                        return 244;
                    case 2137001642:
                    case 780835460:
                    case -1861183774:
                        return 144;
                    case -488745714:
                    case -345416640:
                    case -572853474:
                        return 190;
                    default:
                        return 171;
                }
            case 45900000:
                return 171;
            case 46800000:
                switch (hashedId) {
                    case -1524947876:
                        return 121;
                    case 515611329:
                        return 221;
                    case -796973095:
                        return 224;
                    default:
                        return 245;
                }
            case 50400000:
                return 121;
        }
        return -1;
    }

    private static final String[] ISO_639_2_CODES = new String[] {
            "aar", "abk", "ace", "ach", "ada", "ady", "afa", "afh", "afr",
            "ain", "aka", "akk", "ale", "alg", "alt", "amh", "ang", "anp",
            "apa", "ara", "arc", "arg", "arn", "arp", "art", "arw", "asm",
            "ast", "ath", "aus", "ava", "ave", "awa", "aym", "aze", "bad",
            "bai", "bak", "bal", "bam", "ban", "bas", "bat", "bej", "bel",
            "bem", "ben", "ber", "bho", "bih", "bik", "bin", "bis", "bla",
            "bnt", "bod", "bos", "bra", "bre", "btk", "bua", "bug", "bul",
            "byn", "cad", "cai", "car", "cat", "cau", "ceb", "cel", "ces",
            "cha", "chb", "che", "chg", "chk", "chm", "chn", "cho", "chp",
            "chr", "chu", "chv", "chy", "cmc", "cnr", "cop", "cor", "cos",
            "cpe", "cpf", "cpp", "cre", "crh", "crp", "csb", "cus", "cym",
            "dak", "dan", "dar", "day", "del", "den", "deu", "dgr", "din",
            "div", "doi", "dra", "dsb", "dua", "dum", "dyu", "dzo", "efi",
            "egy", "eka", "ell", "elx", "eng", "enm", "epo", "est", "eus",
            "ewe", "ewo", "fan", "fao", "fas", "fat", "fij", "fil", "fin",
            "fiu", "fon", "fra", "frm", "fro", "frr", "frs", "fry", "ful",
            "fur", "gaa", "gay", "gba", "gem", "gez", "gil", "gla", "gle",
            "glg", "glv", "gmh", "goh", "gon", "gor", "got", "grb", "grc",
            "grn", "gsw", "guj", "gwi", "hai", "hat", "hau", "haw", "heb",
            "her", "hil", "him", "hin", "hit", "hmn", "hmo", "hrv", "hsb",
            "hun", "hup", "hye", "iba", "ibo", "ido", "iii", "ijo", "iku",
            "ile", "ilo", "ina", "inc", "ind", "ine", "inh", "ipk", "ira",
            "iro", "isl", "ita", "jav", "jbo", "jpn", "jpr", "jrb", "kaa",
            "kab", "kac", "kal", "kam", "kan", "kar", "kas", "kat", "kau",
            "kaw", "kaz", "kbd", "kgp", "kha", "khi", "khm", "kho", "kik",
            "kin", "kir", "kmb", "kok", "kom", "kon", "kor", "kos", "kpe",
            "krc", "krl", "kro", "kru", "kua", "kum", "kur", "kut", "lad",
            "lah", "lam", "lao", "lat", "lav", "lez", "lim", "lin", "lit",
            "lol", "loz", "lrc", "ltz", "lua", "lub", "lug", "lui", "lun",
            "luo", "lus", "mad", "mag", "mah", "mai", "mak", "mal", "man",
            "map", "mar", "mas", "mdf", "mdr", "men", "mga", "mic", "min",
            "mis", "mkd", "mkh", "mlg", "mlt", "mnc", "mni", "mno", "moh",
            "mon", "mos", "mri", "msa", "mul", "mun", "mus", "mwl", "mwr",
            "mya", "myn", "myv", "mzn", "nah", "nai", "nap", "nau", "nav",
            "nbl", "nde", "ndo", "nds", "nep", "new", "nia", "nic", "niu",
            "nld", "nno", "nob", "nog", "non", "nor", "nqo", "nso", "nub",
            "nwc", "nya", "nym", "nyn", "nyo", "nzi", "oci", "oji", "ori",
            "orm", "osa", "oss", "ota", "oto", "paa", "pag", "pal", "pam",
            "pan", "pap", "pau", "pcm", "peo", "phi", "phn", "pli", "pol",
            "pon", "por", "pra", "pro", "pus", "que", "raj", "rap", "rar",
            "roa", "roh", "rom", "ron", "run", "rup", "rus", "sad", "sag",
            "sah", "sai", "sal", "sam", "san", "sas", "sat", "scn", "sco",
            "sel", "sem", "sga", "sgn", "shn", "sid", "sin", "sio", "sit",
            "sla", "slk", "slv", "sma", "sme", "smi", "smj", "smn", "smo",
            "sms", "sna", "snd", "snk", "sog", "som", "son", "sot", "spa",
            "sqi", "srd", "srn", "srp", "srr", "ssa", "ssw", "suk", "sun",
            "sus", "sux", "swa", "swe", "syc", "syr", "tah", "tai", "tam",
            "tat", "tel", "tem", "ter", "tet", "tgk", "tgl", "tha", "tig",
            "tir", "tiv", "tkl", "tlh", "tli", "tmh", "tog", "ton", "tpi",
            "tsi", "tsn", "tso", "tuk", "tum", "tup", "tur", "tut", "tvl",
            "twi", "tyv", "udm", "uga", "uig", "ukr", "umb", "und", "urd",
            "uzb", "vai", "ven", "vie", "vol", "vot", "wak", "wal", "war",
            "was", "wen", "wln", "wol", "xal", "xho", "yao", "yap", "yid",
            "yor", "ypk", "yrl", "zap", "zbl", "zen", "zgh", "zha", "zho",
            "znd", "zul", "zun", "zza"
    };

    @SuppressWarnings("WrongTerminology")
    private static final String[] ISO_639_2_NAMES = new String[] {
            "Afar",                                 // Code aar/aa
            "Abkhazian",                            // Code abk/ab
            "Achinese",                             // Code ace
            "Acoli",                                // Code ach
            "Adangme",                              // Code ada
            "Adyghe; Adygei",                       // Code ady
            "Afro-Asiatic languages",               // Code afa
            "Afrihili",                             // Code afh
            "Afrikaans",                            // Code afr/af
            "Ainu",                                 // Code ain
            "Akan",                                 // Code aka/ak
            "Akkadian",                             // Code akk
            "Aleut",                                // Code ale
            "Algonquian languages",                 // Code alg
            "Southern Altai",                       // Code alt
            "Amharic",                              // Code amh/am
            "English, Old (ca.450-1100)",           // Code ang
            "Angika",                               // Code anp
            "Apache languages",                     // Code apa
            "Arabic",                               // Code ara/ar
            "Official Aramaic (700-300 BCE); Imperial Aramaic (700-300 BCE)",// Code arc
            "Aragonese",                            // Code arg/an
            "Mapudungun; Mapuche",                  // Code arn
            "Arapaho",                              // Code arp
            "Artificial languages",                 // Code art
            "Arawak",                               // Code arw
            "Assamese",                             // Code asm/as
            "Asturian; Bable; Leonese; Asturleonese",// Code ast
            "Athapascan languages",                 // Code ath
            "Australian languages",                 // Code aus
            "Avaric",                               // Code ava/av
            "Avestan",                              // Code ave/ae
            "Awadhi",                               // Code awa
            "Aymara",                               // Code aym/ay
            "Azerbaijani",                          // Code aze/az
            "Banda languages",                      // Code bad
            "Bamileke languages",                   // Code bai
            "Bashkir",                              // Code bak/ba
            "Baluchi",                              // Code bal
            "Bambara",                              // Code bam/bm
            "Balinese",                             // Code ban
            "Basa",                                 // Code bas
            "Baltic languages",                     // Code bat
            "Beja; Bedawiyet",                      // Code bej
            "Belarusian",                           // Code bel/be
            "Bemba",                                // Code bem
            "Bangla",                               // Code ben/bn
            "Berber languages",                     // Code ber
            "Bhojpuri",                             // Code bho
            "Bihari languages",                     // Code bih/bh
            "Bikol",                                // Code bik
            "Bini; Edo",                            // Code bin
            "Bislama",                              // Code bis/bi
            "Siksika",                              // Code bla
            "Bantu languages",                      // Code bnt
            "Tibetan",                              // Code bod/bo
            "Bosnian",                              // Code bos/bs
            "Braj",                                 // Code bra
            "Breton",                               // Code bre/br
            "Batak languages",                      // Code btk
            "Buriat",                               // Code bua
            "Buginese",                             // Code bug
            "Bulgarian",                            // Code bul/bg
            "Blin; Bilin",                          // Code byn
            "Caddo",                                // Code cad
            "Central American Indian languages",    // Code cai
            "Galibi Carib",                         // Code car
            "Catalan",                              // Code cat/ca
            "Caucasian languages",                  // Code cau
            "Cebuano",                              // Code ceb
            "Celtic languages",                     // Code cel
            "Czech",                                // Code ces/cs
            "Chamorro",                             // Code cha/ch
            "Chibcha",                              // Code chb
            "Chechen",                              // Code che/ce
            "Chagatai",                             // Code chg
            "Chuukese",                             // Code chk
            "Mari",                                 // Code chm
            "Chinook jargon",                       // Code chn
            "Choctaw",                              // Code cho
            "Chipewyan; Dene Suline",               // Code chp
            "Cherokee",                             // Code chr
            "Church Slavic; Old Slavonic; Church Slavonic; Old Bulgarian; Old Church Slavonic",// Code chu/cu
            "Chuvash",                              // Code chv/cv
            "Cheyenne",                             // Code chy
            "Chamic languages",                     // Code cmc
            "Montenegrin",                          // Code cnr
            "Coptic",                               // Code cop
            "Cornish",                              // Code cor/kw
            "Corsican",                             // Code cos/co
            "Creoles and pidgins, English based",   // Code cpe
            "Creoles and pidgins, French-based",    // Code cpf
            "Creoles and pidgins, Portuguese-based",// Code cpp
            "Cree",                                 // Code cre/cr
            "Crimean Tatar; Crimean Turkish",       // Code crh
            "Creoles and pidgins",                  // Code crp
            "Kashubian",                            // Code csb
            "Cushitic languages",                   // Code cus
            "Welsh",                                // Code cym/cy
            "Dakota",                               // Code dak
            "Danish",                               // Code dan/da
            "Dargwa",                               // Code dar
            "Land Dayak languages",                 // Code day
            "Delaware",                             // Code del
            "Slave (Athapascan)",                   // Code den
            "German",                               // Code deu/de
            "Dogrib",                               // Code dgr
            "Dinka",                                // Code din
            "Divehi; Dhivehi; Maldivian",           // Code div/dv
            "Dogri",                                // Code doi
            "Dravidian languages",                  // Code dra
            "Lower Sorbian",                        // Code dsb
            "Duala",                                // Code dua
            "Dutch, Middle (ca.1050-1350)",         // Code dum
            "Dyula",                                // Code dyu
            "Dzongkha",                             // Code dzo/dz
            "Efik",                                 // Code efi
            "Egyptian (Ancient)",                   // Code egy
            "Ekajuk",                               // Code eka
            "Greek",                                // Code ell/el
            "Elamite",                              // Code elx
            "English",                              // Code eng/en
            "English, Middle (1100-1500)",          // Code enm
            "Esperanto",                            // Code epo/eo
            "Estonian",                             // Code est/et
            "Basque",                               // Code eus/eu
            "Ewe",                                  // Code ewe/ee
            "Ewondo",                               // Code ewo
            "Fang",                                 // Code fan
            "Faroese",                              // Code fao/fo
            "Persian",                              // Code fas/fa
            "Fanti",                                // Code fat
            "Fijian",                               // Code fij/fj
            "Filipino; Pilipino",                   // Code fil
            "Finnish",                              // Code fin/fi
            "Finno-Ugrian languages",               // Code fiu
            "Fon",                                  // Code fon
            "French",                               // Code fra/fr
            "French, Middle (ca.1400-1600)",        // Code frm
            "French, Old (842-ca.1400)",            // Code fro
            "Northern Frisian",                     // Code frr
            "Eastern Frisian",                      // Code frs
            "Western Frisian",                      // Code fry/fy
            "Fulah",                                // Code ful/ff
            "Friulian",                             // Code fur
            "Ga",                                   // Code gaa
            "Gayo",                                 // Code gay
            "Gbaya",                                // Code gba
            "Germanic languages",                   // Code gem
            "Geez",                                 // Code gez
            "Gilbertese",                           // Code gil
            "Scottish Gaelic",                      // Code gla/gd
            "Irish",                                // Code gle/ga
            "Galician",                             // Code glg/gl
            "Manx",                                 // Code glv/gv
            "German, Middle High (ca.1050-1500)",   // Code gmh
            "German, Old High (ca.750-1050)",       // Code goh
            "Gondi",                                // Code gon
            "Gorontalo",                            // Code gor
            "Gothic",                               // Code got
            "Grebo",                                // Code grb
            "Greek, Ancient (to 1453)",             // Code grc
            "Guarani",                              // Code grn/gn
            "Swiss German; Alemannic; Alsatian",    // Code gsw
            "Gujarati",                             // Code guj/gu
            "Gwich'in",                             // Code gwi
            "Haida",                                // Code hai
            "Haitian; Haitian Creole",              // Code hat/ht
            "Hausa",                                // Code hau/ha
            "Hawaiian",                             // Code haw
            "Hebrew",                               // Code heb/iw
            "Herero",                               // Code her/hz
            "Hiligaynon",                           // Code hil
            "Himachali languages; Western Pahari languages",// Code him
            "Hindi",                                // Code hin/hi
            "Hittite",                              // Code hit
            "Hmong; Mong",                          // Code hmn
            "Hiri Motu",                            // Code hmo/ho
            "Croatian",                             // Code hrv/hr
            "Upper Sorbian",                        // Code hsb
            "Hungarian",                            // Code hun/hu
            "Hupa",                                 // Code hup
            "Armenian",                             // Code hye/hy
            "Iban",                                 // Code iba
            "Igbo",                                 // Code ibo/ig
            "Ido",                                  // Code ido/io
            "Sichuan Yi",                           // Code iii/ii
            "Ijo languages",                        // Code ijo
            "Inuktitut",                            // Code iku/iu
            "Interlingue; Occidental",              // Code ile/ie
            "Iloko",                                // Code ilo
            "Interlingua",                          // Code ina/ia
            "Indic languages",                      // Code inc
            "Indonesian",                           // Code ind/in
            "Indo-European languages",              // Code ine
            "Ingush",                               // Code inh
            "Inupiaq",                              // Code ipk/ik
            "Iranian languages",                    // Code ira
            "Iroquoian languages",                  // Code iro
            "Icelandic",                            // Code isl/is
            "Italian",                              // Code ita/it
            "Javanese",                             // Code jav/jv
            "Lojban",                               // Code jbo
            "Japanese",                             // Code jpn/ja
            "Judeo-Persian",                        // Code jpr
            "Judeo-Arabic",                         // Code jrb
            "Kara-Kalpak",                          // Code kaa
            "Kabyle",                               // Code kab
            "Kachin; Jingpho",                      // Code kac
            "Kalaallisut",                          // Code kal/kl
            "Kamba",                                // Code kam
            "Kannada",                              // Code kan/kn
            "Karen languages",                      // Code kar
            "Kashmiri",                             // Code kas/ks
            "Georgian",                             // Code kat/ka
            "Kanuri",                               // Code kau/kr
            "Kawi",                                 // Code kaw
            "Kazakh",                               // Code kaz/kk
            "Kabardian",                            // Code kbd
            "Kaingang",                             // Code kgp
            "Khasi",                                // Code kha
            "Khoisan languages",                    // Code khi
            "Khmer",                                // Code khm/km
            "Khotanese; Sakan",                     // Code kho
            "Kikuyu",                               // Code kik/ki
            "Kinyarwanda",                          // Code kin/rw
            "Kyrgyz",                               // Code kir/ky
            "Kimbundu",                             // Code kmb
            "Konkani",                              // Code kok
            "Komi",                                 // Code kom/kv
            "Kongo",                                // Code kon/kg
            "Korean",                               // Code kor/ko
            "Kosraean",                             // Code kos
            "Kpelle",                               // Code kpe
            "Karachay-Balkar",                      // Code krc
            "Karelian",                             // Code krl
            "Kru languages",                        // Code kro
            "Kurukh",                               // Code kru
            "Kuanyama; Kwanyama",                   // Code kua/kj
            "Kumyk",                                // Code kum
            "Kurdish",                              // Code kur/ku
            "Kutenai",                              // Code kut
            "Ladino",                               // Code lad
            "Lahnda",                               // Code lah
            "Lamba",                                // Code lam
            "Lao",                                  // Code lao/lo
            "Latin",                                // Code lat/la
            "Latvian",                              // Code lav/lv
            "Lezghian",                             // Code lez
            "Limburgan; Limburger; Limburgish",     // Code lim/li
            "Lingala",                              // Code lin/ln
            "Lithuanian",                           // Code lit/lt
            "Mongo",                                // Code lol
            "Lozi",                                 // Code loz
            "Northern Luri",                        // Code lrc
            "Luxembourgish",                        // Code ltz/lb
            "Luba-Lulua",                           // Code lua
            "Luba-Katanga",                         // Code lub/lu
            "Ganda",                                // Code lug/lg
            "Luiseno",                              // Code lui
            "Lunda",                                // Code lun
            "Luo (Kenya and Tanzania)",             // Code luo
            "Lushai",                               // Code lus
            "Madurese",                             // Code mad
            "Magahi",                               // Code mag
            "Marshallese",                          // Code mah/mh
            "Maithili",                             // Code mai
            "Makasar",                              // Code mak
            "Malayalam",                            // Code mal/ml
            "Mandingo",                             // Code man
            "Austronesian languages",               // Code map
            "Marathi",                              // Code mar/mr
            "Masai",                                // Code mas
            "Moksha",                               // Code mdf
            "Mandar",                               // Code mdr
            "Mende",                                // Code men
            "Irish, Middle (900-1200)",             // Code mga
            "Mi'kmaq; Micmac",                      // Code mic
            "Minangkabau",                          // Code min
            "Uncoded languages",                    // Code mis
            "Macedonian",                           // Code mkd/mk
            "Mon-Khmer languages",                  // Code mkh
            "Malagasy",                             // Code mlg/mg
            "Maltese",                              // Code mlt/mt
            "Manchu",                               // Code mnc
            "Manipuri",                             // Code mni
            "Manobo languages",                     // Code mno
            "Mohawk",                               // Code moh
            "Mongolian",                            // Code mon/mn
            "Mossi",                                // Code mos
            "Māori",                                // Code mri/mi
            "Malay",                                // Code msa/ms
            "Multiple languages",                   // Code mul
            "Munda languages",                      // Code mun
            "Creek",                                // Code mus
            "Mirandese",                            // Code mwl
            "Marwari",                              // Code mwr
            "Burmese",                              // Code mya/my
            "Mayan languages",                      // Code myn
            "Erzya",                                // Code myv
            "Mazanderani",                          // Code mzn
            "Nahuatl languages",                    // Code nah
            "North American Indian languages",      // Code nai
            "Neapolitan",                           // Code nap
            "Nauru",                                // Code nau/na
            "Navajo; Navaho",                       // Code nav/nv
            "Ndebele, South; South Ndebele",        // Code nbl/nr
            "North Ndebele",                        // Code nde/nd
            "Ndonga",                               // Code ndo/ng
            "Low German; Low Saxon; German, Low; Saxon, Low",// Code nds
            "Nepali",                               // Code nep/ne
            "Nepal Bhasa; Newari",                  // Code new
            "Nias",                                 // Code nia
            "Niger-Kordofanian languages",          // Code nic
            "Niuean",                               // Code niu
            "Dutch",                                // Code nld/nl
            "Norwegian Nynorsk",                    // Code nno/nn
            "Norwegian Bokmål",                     // Code nob/nb
            "Nogai",                                // Code nog
            "Norse, Old",                           // Code non
            "Norwegian",                            // Code nor/no
            "N'Ko",                                 // Code nqo
            "Pedi; Sepedi; Northern Sotho",         // Code nso
            "Nubian languages",                     // Code nub
            "Classical Newari; Old Newari; Classical Nepal Bhasa",// Code nwc
            "Chichewa; Chewa; Nyanja",              // Code nya/ny
            "Nyamwezi",                             // Code nym
            "Nyankole",                             // Code nyn
            "Nyoro",                                // Code nyo
            "Nzima",                                // Code nzi
            "Occitan (post 1500)",                  // Code oci/oc
            "Ojibwa",                               // Code oji/oj
            "Odia",                                 // Code ori/or
            "Oromo",                                // Code orm/om
            "Osage",                                // Code osa
            "Ossetic",                              // Code oss/os
            "Turkish, Ottoman (1500-1928)",         // Code ota
            "Otomian languages",                    // Code oto
            "Papuan languages",                     // Code paa
            "Pangasinan",                           // Code pag
            "Pahlavi",                              // Code pal
            "Pampanga; Kapampangan",                // Code pam
            "Punjabi",                              // Code pan/pa
            "Papiamento",                           // Code pap
            "Palauan",                              // Code pau
            "Nigerian Pidgin",                      // Code pcm
            "Persian, Old (ca.600-400 B.C.)",       // Code peo
            "Philippine languages",                 // Code phi
            "Phoenician",                           // Code phn
            "Pali",                                 // Code pli/pi
            "Polish",                               // Code pol/pl
            "Pohnpeian",                            // Code pon
            "Portuguese",                           // Code por/pt
            "Prakrit languages",                    // Code pra
            "Provençal, Old (to 1500); Occitan, Old (to 1500)",// Code pro
            "Pashto",                               // Code pus/ps
            "Quechua",                              // Code que/qu
            "Rajasthani",                           // Code raj
            "Rapanui",                              // Code rap
            "Rarotongan; Cook Islands Maori",       // Code rar
            "Romance languages",                    // Code roa
            "Romansh",                              // Code roh/rm
            "Romany",                               // Code rom
            "Romanian",                             // Code ron/ro
            "Rundi",                                // Code run/rn
            "Aromanian; Arumanian; Macedo-Romanian",// Code rup
            "Russian",                              // Code rus/ru
            "Sandawe",                              // Code sad
            "Sango",                                // Code sag/sg
            "Yakut",                                // Code sah
            "South American Indian languages",      // Code sai
            "Salishan languages",                   // Code sal
            "Samaritan Aramaic",                    // Code sam
            "Sanskrit",                             // Code san/sa
            "Sasak",                                // Code sas
            "Santali",                              // Code sat
            "Sicilian",                             // Code scn
            "Scots",                                // Code sco
            "Selkup",                               // Code sel
            "Semitic languages",                    // Code sem
            "Irish, Old (to 900)",                  // Code sga
            "Sign Languages",                       // Code sgn
            "Shan",                                 // Code shn
            "Sidamo",                               // Code sid
            "Sinhala",                              // Code sin/si
            "Siouan languages",                     // Code sio
            "Sino-Tibetan languages",               // Code sit
            "Slavic languages",                     // Code sla
            "Slovak",                               // Code slk/sk
            "Slovenian",                            // Code slv/sl
            "Southern Sami",                        // Code sma
            "Northern Sami",                        // Code sme/se
            "Sami languages",                       // Code smi
            "Lule Sami",                            // Code smj
            "Inari Sami",                           // Code smn
            "Samoan",                               // Code smo/sm
            "Skolt Sami",                           // Code sms
            "Shona",                                // Code sna/sn
            "Sindhi",                               // Code snd/sd
            "Soninke",                              // Code snk
            "Sogdian",                              // Code sog
            "Somali",                               // Code som/so
            "Songhai languages",                    // Code son
            "Sotho, Southern",                      // Code sot/st
            "Spanish",                              // Code spa/es
            "Albanian",                             // Code sqi/sq
            "Sardinian",                            // Code srd/sc
            "Sranan Tongo",                         // Code srn
            "Serbian",                              // Code srp/sr
            "Serer",                                // Code srr
            "Nilo-Saharan languages",               // Code ssa
            "Swati",                                // Code ssw/ss
            "Sukuma",                               // Code suk
            "Sundanese",                            // Code sun/su
            "Susu",                                 // Code sus
            "Sumerian",                             // Code sux
            "Swahili",                              // Code swa/sw
            "Swedish",                              // Code swe/sv
            "Classical Syriac",                     // Code syc
            "Syriac",                               // Code syr
            "Tahitian",                             // Code tah/ty
            "Tai languages",                        // Code tai
            "Tamil",                                // Code tam/ta
            "Tatar",                                // Code tat/tt
            "Telugu",                               // Code tel/te
            "Timne",                                // Code tem
            "Tereno",                               // Code ter
            "Tetum",                                // Code tet
            "Tajik",                                // Code tgk/tg
            "Tagalog",                              // Code tgl/tl
            "Thai",                                 // Code tha/th
            "Tigre",                                // Code tig
            "Tigrinya",                             // Code tir/ti
            "Tiv",                                  // Code tiv
            "Tokelau",                              // Code tkl
            "Klingon; tlhIngan-Hol",                // Code tlh
            "Tlingit",                              // Code tli
            "Tamashek",                             // Code tmh
            "Tonga (Nyasa)",                        // Code tog
            "Tongan",                               // Code ton/to
            "Tok Pisin",                            // Code tpi
            "Tsimshian",                            // Code tsi
            "Tswana",                               // Code tsn/tn
            "Tsonga",                               // Code tso/ts
            "Turkmen",                              // Code tuk/tk
            "Tumbuka",                              // Code tum
            "Tupi languages",                       // Code tup
            "Turkish",                              // Code tur/tr
            "Altaic languages",                     // Code tut
            "Tuvalu",                               // Code tvl
            "Twi",                                  // Code twi/tw
            "Tuvinian",                             // Code tyv
            "Udmurt",                               // Code udm
            "Ugaritic",                             // Code uga
            "Uyghur",                               // Code uig/ug
            "Ukrainian",                            // Code ukr/uk
            "Umbundu",                              // Code umb
            "Undetermined",                         // Code und
            "Urdu",                                 // Code urd/ur
            "Uzbek",                                // Code uzb/uz
            "Vai",                                  // Code vai
            "Venda",                                // Code ven/ve
            "Vietnamese",                           // Code vie/vi
            "Volapük",                              // Code vol/vo
            "Votic",                                // Code vot
            "Wakashan languages",                   // Code wak
            "Wolaitta; Wolaytta",                   // Code wal
            "Waray",                                // Code war
            "Washo",                                // Code was
            "Sorbian languages",                    // Code wen
            "Walloon",                              // Code wln/wa
            "Wolof",                                // Code wol/wo
            "Kalmyk; Oirat",                        // Code xal
            "Xhosa",                                // Code xho/xh
            "Yao",                                  // Code yao
            "Yapese",                               // Code yap
            "Yiddish",                              // Code yid/ji
            "Yoruba",                               // Code yor/yo
            "Yupik languages",                      // Code ypk
            "Nheengatu",                            // Code yrl
            "Zapotec",                              // Code zap
            "Blissymbols; Blissymbolics; Bliss",    // Code zbl
            "Zenaga",                               // Code zen
            "Standard Moroccan Tamazight",          // Code zgh
            "Zhuang; Chuang",                       // Code zha/za
            "Chinese",                              // Code zho/zh
            "Zande languages",                      // Code znd
            "Zulu",                                 // Code zul/zu
            "Zuni",                                 // Code zun
            "Zaza; Dimili; Dimli; Kirdki; Kirmanjki; Zazaki"// Code zza
    };

    private static final String[] ISO_639_1_CODES = new String[] {
            "aa", "ab", "ae", "af", "ak", "am", "an", "ar", "as", "av", "ay",
            "az", "ba", "be", "bg", "bh", "bi", "bm", "bn", "bo", "br", "bs",
            "ca", "ce", "ch", "co", "cr", "cs", "cu", "cv", "cy", "da", "de",
            "dv", "dz", "ee", "el", "en", "eo", "es", "et", "eu", "fa", "ff",
            "fi", "fj", "fo", "fr", "fy", "ga", "gd", "gl", "gn", "gu", "gv",
            "ha", "he", "hi", "ho", "hr", "ht", "hu", "hy", "hz", "ia", "id",
            "ie", "ig", "ii", "ik", "in", "io", "is", "it", "iu", "iw", "ja",
            "ji", "jv", "ka", "kg", "ki", "kj", "kk", "kl", "km", "kn", "ko",
            "kr", "ks", "ku", "kv", "kw", "ky", "la", "lb", "lg", "li", "ln",
            "lo", "lt", "lu", "lv", "mg", "mh", "mi", "mk", "ml", "mn", "mr",
            "ms", "mt", "my", "na", "nb", "nd", "ne", "ng", "nl", "nn", "no",
            "nr", "nv", "ny", "oc", "oj", "om", "or", "os", "pa", "pi", "pl",
            "ps", "pt", "qu", "rm", "rn", "ro", "ru", "rw", "sa", "sc", "sd",
            "se", "sg", "si", "sk", "sl", "sm", "sn", "so", "sq", "sr", "ss",
            "st", "su", "sv", "sw", "ta", "te", "tg", "th", "ti", "tk", "tl",
            "tn", "to", "tr", "ts", "tt", "tw", "ty", "ug", "uk", "ur", "uz",
            "ve", "vi", "vo", "wa", "wo", "xh", "yi", "yo", "za", "zh", "zu"

    };

    // Each element corresponds to an ISO 639-1 code, and contains the index
    // for the corresponding ISO 639-2 code
    private static final int[] ISO_639_1_TO_2 = new int[] {
              0,   1,  31,   8,  10,  15,  21,  19,  26,  30,  33,
             34,  37,  44,  62,  49,  52,  39,  46,  55,  58,  56,
             67,  74,  72,  89,  93,  71,  82,  83,  98, 100, 105,
            108, 115, 126, 119, 121, 123, 404, 124, 125, 130, 143,
            134, 132, 129, 137, 142, 152, 151, 153, 162, 164, 154,
            168, 170, 174, 177, 178, 167, 180, 182, 171, 191, 193,
            189, 184, 186, 196, 193, 185, 199, 200, 188, 170, 203,
            476, 201, 214, 230, 224, 238, 217, 209, 222, 211, 231,
            215, 213, 240, 229,  88, 226, 246, 255, 258, 249, 250,
            245, 251, 257, 247, 282, 265, 290, 280, 268, 288, 271,
            291, 283, 297, 304, 317, 307, 310, 308, 315, 316, 320,
            306, 305, 325, 330, 331, 333, 332, 335, 342, 349, 350,
            355, 352, 356, 361, 364, 363, 366, 225, 373, 406, 398,
            391, 368, 384, 388, 389, 395, 397, 401, 405, 408, 411,
            403, 413, 417, 416, 422, 424, 428, 430, 432, 444, 429,
            442, 439, 447, 443, 423, 450, 420, 454, 455, 458, 459,
            461, 462, 463, 470, 471, 473, 476, 477, 484, 485, 487

    };

    // Each element corresponds to an ISO 639-2 code, and contains the index
    // for the corresponding ISO 639-1 code, or -1 if not represented
    private static final int[] ISO_639_2_TO_1 = new int[] {
              0,   1,  -1,  -1,  -1,  -1,  -1,  -1,   3,  -1,   4,
             -1,  -1,  -1,  -1,   5,  -1,  -1,  -1,   7,  -1,   6,
             -1,  -1,  -1,  -1,   8,  -1,  -1,  -1,   9,   2,  -1,
             10,  11,  -1,  -1,  12,  -1,  17,  -1,  -1,  -1,  -1,
             13,  -1,  18,  -1,  -1,  15,  -1,  -1,  16,  -1,  -1,
             19,  21,  -1,  20,  -1,  -1,  -1,  14,  -1,  -1,  -1,
             -1,  22,  -1,  -1,  -1,  27,  24,  -1,  23,  -1,  -1,
             -1,  -1,  -1,  -1,  -1,  28,  29,  -1,  -1,  -1,  -1,
             92,  25,  -1,  -1,  -1,  26,  -1,  -1,  -1,  -1,  30,
             -1,  31,  -1,  -1,  -1,  -1,  32,  -1,  -1,  33,  -1,
             -1,  -1,  -1,  -1,  -1,  34,  -1,  -1,  -1,  36,  -1,
             37,  -1,  38,  40,  41,  35,  -1,  -1,  46,  42,  -1,
             45,  -1,  44,  -1,  -1,  47,  -1,  -1,  -1,  -1,  48,
             43,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  50,  49,  51,
             54,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  52,  -1,  53,
             -1,  -1,  60,  55,  -1,  75,  63,  -1,  -1,  57,  -1,
             -1,  58,  59,  -1,  61,  -1,  62,  -1,  67,  71,  68,
             -1,  74,  66,  -1,  64,  -1,  70,  -1,  -1,  69,  -1,
             -1,  72,  73,  78,  -1,  76,  -1,  -1,  -1,  -1,  -1,
             84,  -1,  86,  -1,  89,  79,  88,  -1,  83,  -1,  -1,
             -1,  -1,  85,  -1,  81, 139,  93,  -1,  -1,  91,  80,
             87,  -1,  -1,  -1,  -1,  -1,  -1,  82,  -1,  90,  -1,
             -1,  -1,  -1,  99,  94, 102,  -1,  97,  98, 100,  -1,
             -1,  -1,  95,  -1, 101,  96,  -1,  -1,  -1,  -1,  -1,
             -1, 104,  -1,  -1, 107,  -1,  -1, 109,  -1,  -1,  -1,
             -1,  -1,  -1,  -1,  -1, 106,  -1, 103, 111,  -1,  -1,
             -1,  -1, 108,  -1, 105, 110,  -1,  -1,  -1,  -1,  -1,
            112,  -1,  -1,  -1,  -1,  -1,  -1, 113, 122, 121, 115,
            117,  -1, 116,  -1,  -1,  -1,  -1, 118, 119, 114,  -1,
             -1, 120,  -1,  -1,  -1,  -1, 123,  -1,  -1,  -1,  -1,
            124, 125, 127, 126,  -1, 128,  -1,  -1,  -1,  -1,  -1,
             -1, 129,  -1,  -1,  -1,  -1,  -1,  -1, 130, 131,  -1,
            133,  -1,  -1, 132, 134,  -1,  -1,  -1,  -1, 135,  -1,
            137, 136,  -1, 138,  -1, 144,  -1,  -1,  -1,  -1, 140,
             -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1, 145,
             -1,  -1,  -1, 146, 147,  -1, 143,  -1,  -1,  -1, 148,
             -1, 149, 142,  -1,  -1, 150,  -1, 154,  39, 151, 141,
             -1, 152,  -1,  -1, 153,  -1, 155,  -1,  -1, 157, 156,
             -1,  -1, 171,  -1, 158, 169, 159,  -1,  -1,  -1, 160,
            164, 161,  -1, 162,  -1,  -1,  -1,  -1,  -1,  -1, 166,
             -1,  -1, 165, 168, 163,  -1,  -1, 167,  -1,  -1, 170,
             -1,  -1,  -1, 172, 173,  -1,  -1, 174, 175,  -1, 176,
            177, 178,  -1,  -1,  -1,  -1,  -1,  -1, 179, 180,  -1,
            181,  -1,  -1,  77, 183,  -1,  -1,  -1,  -1,  -1,  -1,
            184, 185,  -1, 186,  -1,  -1
    };
    private static final String[] ISO_3166_2_CODES = new String[] {
            "ABW", "AFG", "AGO", "AIA", "ALA", "ALB", "AND", "ARE", "ARG",
            "ARM", "ASM", "ATA", "ATF", "ATG", "AUS", "AUT", "AZE", "BDI",
            "BEL", "BEN", "BES", "BFA", "BGD", "BGR", "BHR", "BHS", "BIH",
            "BLM", "BLR", "BLZ", "BMU", "BOL", "BRA", "BRB", "BRN", "BTN",
            "BVT", "BWA", "CAF", "CAN", "CCK", "CHE", "CHL", "CHN", "CIV",
            "CMR", "COD", "COG", "COK", "COL", "COM", "CPV", "CRI", "CUB",
            "CUW", "CXR", "CYM", "CYP", "CZE", "DEU", "DGA", "DJI", "DMA",
            "DNK", "DOM", "DZA", "ECU", "EGY", "ERI", "ESH", "ESP", "EST",
            "ETH", "FIN", "FJI", "FLK", "FRA", "FRO", "FSM", "GAB", "GBR",
            "GEO", "GGY", "GHA", "GIB", "GIN", "GLP", "GMB", "GNB", "GNQ",
            "GRC", "GRD", "GRL", "GTM", "GUF", "GUM", "GUY", "HKG", "HMD",
            "HND", "HRV", "HTI", "HUN", "IDN", "IMN", "IND", "IOT", "IRL",
            "IRN", "IRQ", "ISL", "ISR", "ITA", "JAM", "JEY", "JOR", "JPN",
            "KAZ", "KEN", "KGZ", "KHM", "KIR", "KNA", "KOR", "KWT", "LAO",
            "LBN", "LBR", "LBY", "LCA", "LIE", "LKA", "LSO", "LTU", "LUX",
            "LVA", "MAC", "MAF", "MAR", "MCO", "MDA", "MDG", "MDV", "MEX",
            "MHL", "MKD", "MLI", "MLT", "MMR", "MNE", "MNG", "MNP", "MOZ",
            "MRT", "MSR", "MTQ", "MUS", "MWI", "MYS", "MYT", "NAM", "NCL",
            "NER", "NFK", "NGA", "NIC", "NIU", "NLD", "NOR", "NPL", "NRU",
            "NZL", "OMN", "PAK", "PAN", "PCN", "PER", "PHL", "PLW", "PNG",
            "POL", "PRI", "PRK", "PRT", "PRY", "PSE", "PYF", "QAT", "REU",
            "ROU", "RUS", "RWA", "SAU", "SDN", "SEN", "SGP", "SGS", "SHN",
            "SJM", "SLB", "SLE", "SLV", "SMR", "SOM", "SPM", "SRB", "SSD",
            "STP", "SUR", "SVK", "SVN", "SWE", "SWZ", "SXM", "SYC", "SYR",
            "TCA", "TCD", "TGO", "THA", "TJK", "TKL", "TKM", "TLS", "TON",
            "TTO", "TUN", "TUR", "TUV", "TWN", "TZA", "UGA", "UKR", "UMI",
            "URY", "USA", "UZB", "VAT", "VCT", "VEN", "VGB", "VIR", "VNM",
            "VUT", "WLF", "WSM", "XEA", "XIC", "XKK", "YEM", "ZAF", "ZMB",
            "ZWE"
    };

    private static final String[] ISO_3166_2_NAMES = new String[] {
            "Aruba",                                // Code ABW/AW
            "Afghanistan",                          // Code AFG/AF
            "Angola",                               // Code AGO/AO
            "Anguilla",                             // Code AIA/AI
            "Åland Islands",                        // Code ALA/AX
            "Albania",                              // Code ALB/AL
            "Andorra",                              // Code AND/AD
            "United Arab Emirates",                 // Code ARE/AE
            "Argentina",                            // Code ARG/AR
            "Armenia",                              // Code ARM/AM
            "American Samoa",                       // Code ASM/AS
            "Antarctica",                           // Code ATA/AQ
            "French Southern Territories (the)",    // Code ATF/TF
            "Antigua & Barbuda",                    // Code ATG/AG
            "Australia",                            // Code AUS/AU
            "Austria",                              // Code AUT/AT
            "Azerbaijan",                           // Code AZE/AZ
            "Burundi",                              // Code BDI/BI
            "Belgium",                              // Code BEL/BE
            "Benin",                                // Code BEN/BJ
            "Caribbean Netherlands",                // Code BES/BQ
            "Burkina Faso",                         // Code BFA/BF
            "Bangladesh",                           // Code BGD/BD
            "Bulgaria",                             // Code BGR/BG
            "Bahrain",                              // Code BHR/BH
            "Bahamas",                              // Code BHS/BS
            "Bosnia & Herzegovina",                 // Code BIH/BA
            "St. Barthélemy",                       // Code BLM/BL
            "Belarus",                              // Code BLR/BY
            "Belize",                               // Code BLZ/BZ
            "Bermuda",                              // Code BMU/BM
            "Bolivia",                              // Code BOL/BO
            "Brazil",                               // Code BRA/BR
            "Barbados",                             // Code BRB/BB
            "Brunei",                               // Code BRN/BN
            "Bhutan",                               // Code BTN/BT
            "Bouvet Island",                        // Code BVT/BV
            "Botswana",                             // Code BWA/BW
            "Central African Republic",             // Code CAF/CF
            "Canada",                               // Code CAN/CA
            "Cocos (Keeling) Islands",              // Code CCK/CC
            "Switzerland",                          // Code CHE/CH
            "Chile",                                // Code CHL/CL
            "China",                                // Code CHN/CN
            "Côte d’Ivoire",                        // Code CIV/CI
            "Cameroon",                             // Code CMR/CM
            "Congo - Kinshasa",                     // Code COD/CD
            "Congo - Brazzaville",                  // Code COG/CG
            "Cook Islands",                         // Code COK/CK
            "Colombia",                             // Code COL/CO
            "Comoros",                              // Code COM/KM
            "Cape Verde",                           // Code CPV/CV
            "Costa Rica",                           // Code CRI/CR
            "Cuba",                                 // Code CUB/CU
            "Curaçao",                              // Code CUW/CW
            "Christmas Island",                     // Code CXR/CX
            "Cayman Islands",                       // Code CYM/KY
            "Cyprus",                               // Code CYP/CY
            "Czechia",                              // Code CZE/CZ
            "Germany",                              // Code DEU/DE
            "Diego Garcia",                         // Code DGA/DG
            "Djibouti",                             // Code DJI/DJ
            "Dominica",                             // Code DMA/DM
            "Denmark",                              // Code DNK/DK
            "Dominican Republic",                   // Code DOM/DO
            "Algeria",                              // Code DZA/DZ
            "Ecuador",                              // Code ECU/EC
            "Egypt",                                // Code EGY/EG
            "Eritrea",                              // Code ERI/ER
            "Western Sahara",                       // Code ESH/EH
            "Spain",                                // Code ESP/ES
            "Estonia",                              // Code EST/EE
            "Ethiopia",                             // Code ETH/ET
            "Finland",                              // Code FIN/FI
            "Fiji",                                 // Code FJI/FJ
            "Falkland Islands",                     // Code FLK/FK
            "France",                               // Code FRA/FR
            "Faroe Islands",                        // Code FRO/FO
            "Micronesia",                           // Code FSM/FM
            "Gabon",                                // Code GAB/GA
            "United Kingdom",                       // Code GBR/GB
            "Georgia",                              // Code GEO/GE
            "Guernsey",                             // Code GGY/GG
            "Ghana",                                // Code GHA/GH
            "Gibraltar",                            // Code GIB/GI
            "Guinea",                               // Code GIN/GN
            "Guadeloupe",                           // Code GLP/GP
            "Gambia",                               // Code GMB/GM
            "Guinea-Bissau",                        // Code GNB/GW
            "Equatorial Guinea",                    // Code GNQ/GQ
            "Greece",                               // Code GRC/GR
            "Grenada",                              // Code GRD/GD
            "Greenland",                            // Code GRL/GL
            "Guatemala",                            // Code GTM/GT
            "French Guiana",                        // Code GUF/GF
            "Guam",                                 // Code GUM/GU
            "Guyana",                               // Code GUY/GY
            "Hong Kong SAR China",                  // Code HKG/HK
            "Heard Island and McDonald Islands",    // Code HMD/HM
            "Honduras",                             // Code HND/HN
            "Croatia",                              // Code HRV/HR
            "Haiti",                                // Code HTI/HT
            "Hungary",                              // Code HUN/HU
            "Indonesia",                            // Code IDN/ID
            "Isle of Man",                          // Code IMN/IM
            "India",                                // Code IND/IN
            "British Indian Ocean Territory",       // Code IOT/IO
            "Ireland",                              // Code IRL/IE
            "Iran",                                 // Code IRN/IR
            "Iraq",                                 // Code IRQ/IQ
            "Iceland",                              // Code ISL/IS
            "Israel",                               // Code ISR/IL
            "Italy",                                // Code ITA/IT
            "Jamaica",                              // Code JAM/JM
            "Jersey",                               // Code JEY/JE
            "Jordan",                               // Code JOR/JO
            "Japan",                                // Code JPN/JP
            "Kazakhstan",                           // Code KAZ/KZ
            "Kenya",                                // Code KEN/KE
            "Kyrgyzstan",                           // Code KGZ/KG
            "Cambodia",                             // Code KHM/KH
            "Kiribati",                             // Code KIR/KI
            "St. Kitts & Nevis",                    // Code KNA/KN
            "South Korea",                          // Code KOR/KR
            "Kuwait",                               // Code KWT/KW
            "Laos",                                 // Code LAO/LA
            "Lebanon",                              // Code LBN/LB
            "Liberia",                              // Code LBR/LR
            "Libya",                                // Code LBY/LY
            "St. Lucia",                            // Code LCA/LC
            "Liechtenstein",                        // Code LIE/LI
            "Sri Lanka",                            // Code LKA/LK
            "Lesotho",                              // Code LSO/LS
            "Lithuania",                            // Code LTU/LT
            "Luxembourg",                           // Code LUX/LU
            "Latvia",                               // Code LVA/LV
            "Macao SAR China",                      // Code MAC/MO
            "St. Martin",                           // Code MAF/MF
            "Morocco",                              // Code MAR/MA
            "Monaco",                               // Code MCO/MC
            "Moldova",                              // Code MDA/MD
            "Madagascar",                           // Code MDG/MG
            "Maldives",                             // Code MDV/MV
            "Mexico",                               // Code MEX/MX
            "Marshall Islands",                     // Code MHL/MH
            "North Macedonia",                      // Code MKD/MK
            "Mali",                                 // Code MLI/ML
            "Malta",                                // Code MLT/MT
            "Myanmar (Burma)",                      // Code MMR/MM
            "Montenegro",                           // Code MNE/ME
            "Mongolia",                             // Code MNG/MN
            "Northern Mariana Islands",             // Code MNP/MP
            "Mozambique",                           // Code MOZ/MZ
            "Mauritania",                           // Code MRT/MR
            "Montserrat",                           // Code MSR/MS
            "Martinique",                           // Code MTQ/MQ
            "Mauritius",                            // Code MUS/MU
            "Malawi",                               // Code MWI/MW
            "Malaysia",                             // Code MYS/MY
            "Mayotte",                              // Code MYT/YT
            "Namibia",                              // Code NAM/NA
            "New Caledonia",                        // Code NCL/NC
            "Niger",                                // Code NER/NE
            "Norfolk Island",                       // Code NFK/NF
            "Nigeria",                              // Code NGA/NG
            "Nicaragua",                            // Code NIC/NI
            "Niue",                                 // Code NIU/NU
            "Netherlands",                          // Code NLD/NL
            "Norway",                               // Code NOR/NO
            "Nepal",                                // Code NPL/NP
            "Nauru",                                // Code NRU/NR
            "New Zealand",                          // Code NZL/NZ
            "Oman",                                 // Code OMN/OM
            "Pakistan",                             // Code PAK/PK
            "Panama",                               // Code PAN/PA
            "Pitcairn Islands",                     // Code PCN/PN
            "Peru",                                 // Code PER/PE
            "Philippines",                          // Code PHL/PH
            "Palau",                                // Code PLW/PW
            "Papua New Guinea",                     // Code PNG/PG
            "Poland",                               // Code POL/PL
            "Puerto Rico",                          // Code PRI/PR
            "North Korea",                          // Code PRK/KP
            "Portugal",                             // Code PRT/PT
            "Paraguay",                             // Code PRY/PY
            "Palestinian Territories",              // Code PSE/PS
            "French Polynesia",                     // Code PYF/PF
            "Qatar",                                // Code QAT/QA
            "Réunion",                              // Code REU/RE
            "Romania",                              // Code ROU/RO
            "Russia",                               // Code RUS/RU
            "Rwanda",                               // Code RWA/RW
            "Saudi Arabia",                         // Code SAU/SA
            "Sudan",                                // Code SDN/SD
            "Senegal",                              // Code SEN/SN
            "Singapore",                            // Code SGP/SG
            "South Georgia and the South Sandwich Islands",// Code SGS/GS
            "St. Helena",                           // Code SHN/SH
            "Svalbard & Jan Mayen",                 // Code SJM/SJ
            "Solomon Islands",                      // Code SLB/SB
            "Sierra Leone",                         // Code SLE/SL
            "El Salvador",                          // Code SLV/SV
            "San Marino",                           // Code SMR/SM
            "Somalia",                              // Code SOM/SO
            "St. Pierre & Miquelon",                // Code SPM/PM
            "Serbia",                               // Code SRB/RS
            "South Sudan",                          // Code SSD/SS
            "São Tomé & Príncipe",                  // Code STP/ST
            "Suriname",                             // Code SUR/SR
            "Slovakia",                             // Code SVK/SK
            "Slovenia",                             // Code SVN/SI
            "Sweden",                               // Code SWE/SE
            "Eswatini",                             // Code SWZ/SZ
            "Sint Maarten",                         // Code SXM/SX
            "Seychelles",                           // Code SYC/SC
            "Syria",                                // Code SYR/SY
            "Turks & Caicos Islands",               // Code TCA/TC
            "Chad",                                 // Code TCD/TD
            "Togo",                                 // Code TGO/TG
            "Thailand",                             // Code THA/TH
            "Tajikistan",                           // Code TJK/TJ
            "Tokelau",                              // Code TKL/TK
            "Turkmenistan",                         // Code TKM/TM
            "Timor-Leste",                          // Code TLS/TL
            "Tonga",                                // Code TON/TO
            "Trinidad & Tobago",                    // Code TTO/TT
            "Tunisia",                              // Code TUN/TN
            "Turkey",                               // Code TUR/TR
            "Tuvalu",                               // Code TUV/TV
            "Taiwan",                               // Code TWN/TW
            "Tanzania",                             // Code TZA/TZ
            "Uganda",                               // Code UGA/UG
            "Ukraine",                              // Code UKR/UA
            "U.S. Outlying Islands",                // Code UMI/UM
            "Uruguay",                              // Code URY/UY
            "United States",                        // Code USA/US
            "Uzbekistan",                           // Code UZB/UZ
            "Vatican City",                         // Code VAT/VA
            "St. Vincent & Grenadines",             // Code VCT/VC
            "Venezuela",                            // Code VEN/VE
            "British Virgin Islands",               // Code VGB/VG
            "U.S. Virgin Islands",                  // Code VIR/VI
            "Vietnam",                              // Code VNM/VN
            "Vanuatu",                              // Code VUT/VU
            "Wallis & Futuna",                      // Code WLF/WF
            "Samoa",                                // Code WSM/WS
            "Ceuta & Melilla",                      // Code XEA/EA
            "Canary Islands",                       // Code XIC/IC
            "Kosovo",                               // Code XKK/XK
            "Yemen",                                // Code YEM/YE
            "South Africa",                         // Code ZAF/ZA
            "Zambia",                               // Code ZMB/ZM
            "Zimbabwe"                              // Code ZWE/ZW
    };

    private static final String[] ISO_3166_1_CODES = new String[] {
            "AD", "AE", "AF", "AG", "AI", "AL", "AM", "AO", "AQ", "AR", "AS",
            "AT", "AU", "AW", "AX", "AZ", "BA", "BB", "BD", "BE", "BF", "BG",
            "BH", "BI", "BJ", "BL", "BM", "BN", "BO", "BQ", "BR", "BS", "BT",
            "BV", "BW", "BY", "BZ", "CA", "CC", "CD", "CF", "CG", "CH", "CI",
            "CK", "CL", "CM", "CN", "CO", "CR", "CU", "CV", "CW", "CX", "CY",
            "CZ", "DE", "DG", "DJ", "DK", "DM", "DO", "DZ", "EA", "EC", "EE",
            "EG", "EH", "ER", "ES", "ET", "FI", "FJ", "FK", "FM", "FO", "FR",
            "GA", "GB", "GD", "GE", "GF", "GG", "GH", "GI", "GL", "GM", "GN",
            "GP", "GQ", "GR", "GS", "GT", "GU", "GW", "GY", "HK", "HM", "HN",
            "HR", "HT", "HU", "IC", "ID", "IE", "IL", "IM", "IN", "IO", "IQ",
            "IR", "IS", "IT", "JE", "JM", "JO", "JP", "KE", "KG", "KH", "KI",
            "KM", "KN", "KP", "KR", "KW", "KY", "KZ", "LA", "LB", "LC", "LI",
            "LK", "LR", "LS", "LT", "LU", "LV", "LY", "MA", "MC", "MD", "ME",
            "MF", "MG", "MH", "MK", "ML", "MM", "MN", "MO", "MP", "MQ", "MR",
            "MS", "MT", "MU", "MV", "MW", "MX", "MY", "MZ", "NA", "NC", "NE",
            "NF", "NG", "NI", "NL", "NO", "NP", "NR", "NU", "NZ", "OM", "PA",
            "PE", "PF", "PG", "PH", "PK", "PL", "PM", "PN", "PR", "PS", "PT",
            "PW", "PY", "QA", "RE", "RO", "RS", "RU", "RW", "SA", "SB", "SC",
            "SD", "SE", "SG", "SH", "SI", "SJ", "SK", "SL", "SM", "SN", "SO",
            "SR", "SS", "ST", "SV", "SX", "SY", "SZ", "TC", "TD", "TF", "TG",
            "TH", "TJ", "TK", "TL", "TM", "TN", "TO", "TR", "TT", "TV", "TW",
            "TZ", "UA", "UG", "UM", "US", "UY", "UZ", "VA", "VC", "VE", "VG",
            "VI", "VN", "VU", "WF", "WS", "XK", "YE", "YT", "ZA", "ZM", "ZW"

    };

    // Each element corresponds to an ISO2 code, and contains the index
    // for the corresponding ISO3 code
    private static final int[] ISO_3166_1_TO_2 = new int[] {
              6,   7,   1,  13,   3,   5,   9,   2,  11,   8,  10,
             15,  14,   0,   4,  16,  26,  33,  22,  18,  21,  23,
             24,  17,  19,  27,  30,  34,  31,  20,  32,  25,  35,
             36,  37,  28,  29,  39,  40,  46,  38,  47,  41,  44,
             48,  42,  45,  43,  49,  52,  53,  51,  54,  55,  57,
             58,  59,  60,  61,  63,  62,  64,  65, 246,  66,  71,
             67,  69,  68,  70,  72,  73,  74,  75,  78,  77,  76,
             79,  80,  91,  81,  94,  82,  83,  84,  92,  87,  85,
             86,  89,  90, 196,  93,  95,  88,  96,  97,  98,  99,
            100, 101, 102, 247, 103, 107, 111, 104, 105, 106, 109,
            108, 110, 112, 114, 113, 115, 116, 118, 119, 120, 121,
             50, 122, 182, 123, 124,  56, 117, 125, 126, 129, 130,
            131, 127, 132, 133, 134, 135, 128, 138, 139, 140, 149,
            137, 141, 144, 145, 146, 148, 150, 136, 151, 155, 153,
            154, 147, 156, 142, 157, 143, 158, 152, 160, 161, 162,
            163, 164, 165, 167, 168, 169, 170, 166, 171, 172, 174,
            176, 186, 179, 177, 173, 180, 204, 175, 181, 185, 183,
            178, 184, 187, 188, 189, 205, 190, 191, 192, 199, 214,
            193, 211, 195, 197, 210, 198, 209, 200, 202, 194, 203,
            208, 206, 207, 201, 213, 215, 212, 216, 217,  12, 218,
            219, 220, 221, 223, 222, 226, 224, 227, 225, 228, 229,
            230, 232, 231, 233, 235, 234, 236, 237, 238, 239, 240,
            241, 242, 243, 244, 245, 248, 249, 159, 250, 251, 252

    };

    // Each element corresponds to an ISO3 code, and contains the index
    // for the corresponding ISO2 code, or -1 if not represented
    private static final int[] ISO_3166_2_TO_1 = new int[] {
             13,   2,   7,   4,  14,   5,   0,   1,   9,   6,  10,
              8, 218,   3,  12,  11,  15,  23,  19,  24,  29,  20,
             18,  21,  22,  31,  16,  25,  35,  36,  26,  28,  30,
             17,  27,  32,  33,  34,  40,  37,  38,  42,  45,  47,
             43,  46,  39,  41,  44,  48, 121,  51,  49,  50,  52,
             53, 126,  54,  55,  56,  57,  58,  60,  59,  61,  62,
             64,  66,  68,  67,  69,  65,  70,  71,  72,  73,  76,
             75,  74,  77,  78,  80,  82,  83,  84,  87,  88,  86,
             94,  89,  90,  79,  85,  92,  81,  93,  95,  96,  97,
             98,  99, 100, 101, 103, 106, 107, 108, 104, 110, 109,
            111, 105, 112, 114, 113, 115, 116, 127, 117, 118, 119,
            120, 122, 124, 125, 128, 129, 133, 138, 130, 131, 132,
            134, 135, 136, 137, 150, 143, 139, 140, 141, 144, 157,
            159, 145, 146, 147, 155, 148, 142, 149, 151, 161, 153,
            154, 152, 156, 158, 160, 249, 162, 163, 164, 165, 166,
            167, 172, 168, 169, 170, 171, 173, 174, 180, 175, 183,
            176, 179, 187, 178, 181, 184, 123, 186, 188, 185, 177,
            189, 190, 191, 193, 194, 195, 198, 207, 200,  91, 201,
            203, 196, 205, 212, 206, 208, 182, 192, 210, 211, 209,
            204, 202, 199, 215, 213, 197, 214, 216, 217, 219, 220,
            221, 222, 224, 223, 226, 228, 225, 227, 229, 230, 231,
            233, 232, 234, 236, 235, 237, 238, 239, 240, 241, 242,
            243, 244, 245, 246,  63, 102, 247, 248, 250, 251, 252

    };
    // Language afr: ZAF,NAM
    private static final int[] REGIONS_AFR = new int[] { 250,160 };
    // Language ara: ARE,BHR,COM,DJI,DZA,EGY,ERI,ESH,IRQ,ISR,JOR,KWT,LBN,LBY,MAR,MRT,OMN,PSE,QAT,SAU,SDN,SOM,SSD,SYR,TCD,TUN,YEM
    private static final int[] REGIONS_ARA = new int[] { 7,24,50,61,65,67,68,69,109,111,115,124,126,128,138,153,172,185,187,192,193,203,206,215,217,226,249 };
    // Language ben: BGD,IND
    private static final int[] REGIONS_BEN = new int[] { 22,105 };
    // Language bod: CHN,IND
    private static final int[] REGIONS_BOD = new int[] { 43,105 };
    // Language cat: AND,ESP,FRA,ITA
    private static final int[] REGIONS_CAT = new int[] { 6,70,76,112 };
    // Language dan: DNK,GRL
    private static final int[] REGIONS_DAN = new int[] { 63,92 };
    // Language deu: DEU,AUT,BEL,CHE,ITA,LIE,LUX
    private static final int[] REGIONS_DEU = new int[] { 59,15,18,41,112,130,134 };
    // Language ell: GRC,CYP
    private static final int[] REGIONS_ELL = new int[] { 90,57 };
    // Language eng: USA,AIA,ARE,ASM,ATG,AUS,AUT,BDI,BEL,BHS,BLZ,BMU,BRB,BWA,CAN,CCK,CHE,CMR,COK,CXR,CYM,CYP,DEU,DGA,DMA,DNK,ERI,FIN,FJI,FLK,FSM,GBR,GGY,GHA,GIB,GMB,GRD,GUM,GUY,HKG,IMN,IND,IOT,IRL,ISR,JAM,JEY,KEN,KIR,KNA,LBR,LCA,LSO,MAC,MDG,MDV,MHL,MLT,MNP,MSR,MUS,MWI,MYS,NAM,NFK,NGA,NIU,NLD,NRU,NZL,PAK,PCN,PHL,PLW,PNG,PRI,RWA,SDN,SGP,SHN,SLB,SLE,SSD,SVN,SWE,SWZ,SXM,SYC,TCA,TKL,TON,TTO,TUV,TZA,UGA,UMI,VCT,VGB,VIR,VUT,WSM,ZAF,ZMB,ZWE
    private static final int[] REGIONS_ENG = new int[] { 235,3,7,10,13,14,15,17,18,25,29,30,33,37,39,40,41,45,48,55,56,57,59,60,62,63,68,73,74,75,78,80,82,83,84,87,91,95,96,97,104,105,106,107,111,113,114,118,121,122,127,129,132,136,141,142,144,147,151,154,156,157,158,160,163,164,166,167,170,171,173,175,177,178,179,181,191,193,195,197,199,200,206,210,211,212,213,214,216,221,224,225,228,230,231,233,238,240,241,243,245,250,251,252 };
    // Language ewe: GHA,TGO
    private static final int[] REGIONS_EWE = new int[] { 83,218 };
    // Language fao: FRO,DNK
    private static final int[] REGIONS_FAO = new int[] { 77,63 };
    // Language fas: IRN,AFG
    private static final int[] REGIONS_FAS = new int[] { 108,1 };
    // Language fra: FRA,BDI,BEL,BEN,BFA,BLM,CAF,CAN,CHE,CIV,CMR,COD,COG,COM,DJI,DZA,GAB,GIN,GLP,GNQ,GUF,HTI,LUX,MAF,MAR,MCO,MDG,MLI,MRT,MTQ,MUS,MYT,NCL,NER,PYF,REU,RWA,SEN,SPM,SYC,SYR,TCD,TGO,TUN,VUT,WLF
    private static final int[] REGIONS_FRA = new int[] { 76,17,18,19,21,27,38,39,41,44,45,46,47,50,61,65,79,85,86,89,94,101,134,137,138,139,141,146,153,155,156,159,161,162,186,188,191,194,204,214,215,217,218,226,243,244 };
    // Language ful: SEN,BFA,CMR,GHA,GIN,GMB,GNB,LBR,MRT,NER,NGA,SLE
    private static final int[] REGIONS_FUL = new int[] { 194,21,45,83,85,87,88,127,153,162,164,200 };
    // Language gle: IRL,GBR
    private static final int[] REGIONS_GLE = new int[] { 107,80 };
    // Language hau: NGA,GHA,NER
    private static final int[] REGIONS_HAU = new int[] { 164,83,162 };
    // Language hrv: HRV,BIH
    private static final int[] REGIONS_HRV = new int[] { 100,26 };
    // Language ita: ITA,CHE,SMR,VAT
    private static final int[] REGIONS_ITA = new int[] { 112,41,202,237 };
    // Language kor: KOR,PRK
    private static final int[] REGIONS_KOR = new int[] { 123,182 };
    // Language lin: COD,AGO,CAF,COG
    private static final int[] REGIONS_LIN = new int[] { 46,2,38,47 };
    // Language lrc: IRN,IRQ
    private static final int[] REGIONS_LRC = new int[] { 108,109 };
    // Language msa: MYS,BRN,IDN,SGP
    private static final int[] REGIONS_MSA = new int[] { 158,34,103,195 };
    // Language nep: NPL,IND
    private static final int[] REGIONS_NEP = new int[] { 169,105 };
    // Language nld: NLD,ABW,BEL,BES,CUW,SUR,SXM
    private static final int[] REGIONS_NLD = new int[] { 167,0,18,20,54,208,213 };
    // Language nob: NOR,SJM
    private static final int[] REGIONS_NOB = new int[] { 168,198 };
    // Language orm: ETH,KEN
    private static final int[] REGIONS_ORM = new int[] { 72,118 };
    // Language oss: RUS,GEO
    private static final int[] REGIONS_OSS = new int[] { 190,81 };
    // Language pan: PAK,IND
    private static final int[] REGIONS_PAN = new int[] { 173,105 };
    // Language por: PRT,AGO,BRA,CHE,CPV,GNB,GNQ,LUX,MAC,MOZ,STP,TLS
    private static final int[] REGIONS_POR = new int[] { 183,2,32,41,51,88,89,134,136,152,207,223 };
    // Language pus: AFG,PAK
    private static final int[] REGIONS_PUS = new int[] { 1,173 };
    // Language que: PER,BOL,ECU
    private static final int[] REGIONS_QUE = new int[] { 176,31,66 };
    // Language ron: ROU,MDA
    private static final int[] REGIONS_RON = new int[] { 189,140 };
    // Language rus: RUS,BLR,KAZ,KGZ,MDA,UKR
    private static final int[] REGIONS_RUS = new int[] { 190,28,117,119,140,232 };
    // Language sme: NOR,FIN,SWE
    private static final int[] REGIONS_SME = new int[] { 168,73,211 };
    // Language snd: PAK,IND
    private static final int[] REGIONS_SND = new int[] { 173,105 };
    // Language som: SOM,DJI,ETH,KEN
    private static final int[] REGIONS_SOM = new int[] { 203,61,72,118 };
    // Language spa: ESP,ARG,BLZ,BOL,BRA,CHL,COL,CRI,CUB,DOM,ECU,GNQ,GTM,HND,MEX,NIC,PAN,PER,PHL,PRI,PRY,SLV,URY,USA,VEN,XEA,XIC
    private static final int[] REGIONS_SPA = new int[] { 70,8,29,31,32,42,49,52,53,64,66,89,93,99,143,165,174,176,177,181,184,201,234,235,239,246,247 };
    // Language sqi: ALB,MKD,XKK
    private static final int[] REGIONS_SQI = new int[] { 5,145,248 };
    // Language srp: SRB,BIH,MNE,XKK
    private static final int[] REGIONS_SRP = new int[] { 205,26,149,248 };
    // Language swa: TZA,COD,KEN,UGA
    private static final int[] REGIONS_SWA = new int[] { 230,46,118,231 };
    // Language swe: SWE,ALA,FIN
    private static final int[] REGIONS_SWE = new int[] { 211,4,73 };
    // Language tam: IND,LKA,MYS,SGP
    private static final int[] REGIONS_TAM = new int[] { 105,131,158,195 };
    // Language tir: ERI,ETH
    private static final int[] REGIONS_TIR = new int[] { 68,72 };
    // Language tur: TUR,CYP
    private static final int[] REGIONS_TUR = new int[] { 227,57 };
    // Language urd: IND,PAK
    private static final int[] REGIONS_URD = new int[] { 105,173 };
    // Language uzb: UZB,AFG
    private static final int[] REGIONS_UZB = new int[] { 236,1 };
    // Language yor: NGA,BEN
    private static final int[] REGIONS_YOR = new int[] { 164,19 };
    // Language yrl: BRA,COL,VEN
    private static final int[] REGIONS_YRL = new int[] { 32,49,239 };
    // Language zho: CHN,HKG,MAC,SGP,TWN
    private static final int[] REGIONS_ZHO = new int[] { 43,97,136,195,229 };

    private static final int[][] LANGUAGE_REGIONS = new int[][] {
            null, null, null, null, null, null, null, null, REGIONS_AFR,
            null, null, null, null, null, null, null, null, null,
            null, REGIONS_ARA, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, REGIONS_BEN, null, null, null,
            null, null, null, null, null, REGIONS_BOD, null,
            null, null, null, null, null, null, null, null, null,
            null, REGIONS_CAT, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            REGIONS_DAN, null, null, null, null, REGIONS_DEU,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, REGIONS_ELL, null, REGIONS_ENG,
            null, null, null, null, REGIONS_EWE, null, null,
            REGIONS_FAO, REGIONS_FAS, null, null, null, null,
            null, null, REGIONS_FRA, null, null, null, null,
            null, REGIONS_FUL, null, null, null, null, null,
            null, null, null, REGIONS_GLE, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, REGIONS_HAU, null, null, null,
            null, null, null, null, null, null, REGIONS_HRV,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, REGIONS_ITA, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            REGIONS_KOR, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, REGIONS_LIN, null, null, null,
            REGIONS_LRC, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, REGIONS_MSA, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, REGIONS_NEP,
            null, null, null, null, REGIONS_NLD, null, REGIONS_NOB,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, REGIONS_ORM,
            null, REGIONS_OSS, null, null, null, null, null,
            null, REGIONS_PAN, null, null, null, null, null,
            null, null, null, null, REGIONS_POR, null, null,
            REGIONS_PUS, REGIONS_QUE, null, null, null, null,
            null, null, REGIONS_RON, null, null, REGIONS_RUS,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, REGIONS_SME,
            null, null, null, null, null, null, REGIONS_SND,
            null, null, REGIONS_SOM, null, null, REGIONS_SPA,
            REGIONS_SQI, null, null, REGIONS_SRP, null, null,
            null, null, null, null, null, REGIONS_SWA, REGIONS_SWE,
            null, null, null, null, REGIONS_TAM, null, null,
            null, null, null, null, null, null, null, REGIONS_TIR,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, REGIONS_TUR, null,
            null, null, null, null, null, null, null, null, null,
            REGIONS_URD, REGIONS_UZB, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, REGIONS_YOR, null, REGIONS_YRL,
            null, null, null, null, null, REGIONS_ZHO, null,
            null, null, null
    };

    private static final int[] LANGUAGE_REGION = new int[] {
             72,  81,  -1,  -1,  -1,  -1,  -1,  -1,
            250,  -1,  83,  -1,  -1,  -1,  -1,  72,
             -1,  -1,  -1,   7,  -1,  70,  -1,  -1,
             -1,  -1, 105,  -1,  -1,  -1,  16,  -1,
             -1,  31,  16,  -1,  -1, 190,  -1, 146,
             -1,  -1,  -1,  -1,  28,  -1,  22,  -1,
             -1, 105,  -1,  -1, 243,  -1,  -1,  43,
             26,  -1,  76,  -1,  -1,  -1,  23,  -1,
             -1,  -1,  -1,   6,  -1,  -1,  -1,  58,
             95,  -1, 190,  -1,  -1,  -1,  -1,  -1,
             -1,  -1,  -1, 190,  -1,  -1,  -1,  -1,
             80,  76,  -1,  -1,  -1,  39,  -1,  -1,
             -1,  -1,  80,  -1,  63,  -1,  -1,  -1,
             -1,  59,  -1,  -1, 142,  -1,  -1,  -1,
             -1,  -1,  -1,  35,  -1,  -1,  -1,  90,
             -1, 235,  -1,  -1,  71,  70,  83,  -1,
             -1,  77, 108,  -1,  74,  -1,  73,  -1,
             -1,  76,  -1,  -1,  -1,  -1, 167, 194,
             -1,  -1,  -1,  -1,  -1,  -1,  -1,  80,
            107,  70, 104,  -1,  -1,  -1,  -1,  -1,
             -1,  -1, 184,  -1, 105,  -1,  -1, 101,
            164,  -1, 111, 160,  -1,  -1, 105,  -1,
             -1, 179, 100,  -1, 102,  -1,   9,  -1,
            164,  -1,  43,  -1,  39,  -1,  -1,  -1,
             -1, 103,  -1,  -1, 235,  -1,  -1, 110,
            112, 103,  -1, 116,  -1,  -1,  -1,  -1,
             -1,  92,  -1, 105,  -1, 105,  81, 164,
             -1, 117,  -1,  32,  -1,  -1, 120,  -1,
            118, 191, 119,  -1,  -1, 190,   2, 123,
             -1,  -1,  -1,  -1,  -1,  -1,   2,  -1,
            227,  -1,  -1,  -1,  -1, 125,  -1, 135,
             -1, 167,  46, 133,  -1,  -1, 108, 134,
             -1,  46, 231,  -1,  -1,  -1,  -1,  -1,
             -1, 144,  -1,  -1, 105,  -1,  -1, 105,
             -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,
            145,  -1, 141, 147,  -1,  -1,  -1,  -1,
            150,  -1, 171, 158,  -1,  -1,  -1,  -1,
             -1, 148,  -1,  -1, 108,  -1,  -1,  -1,
            170, 235, 250, 252, 160,  -1, 169,  -1,
             -1,  -1,  -1, 167, 168, 168,  -1,  -1,
            168,  -1,  -1,  -1,  -1, 157,  -1,  -1,
             -1,  -1,  76,  39, 105,  72,  -1, 190,
             -1,  -1,  -1,  -1,  -1,  -1, 173,  -1,
             -1, 164,  -1,  -1,  -1,  -1, 180,  -1,
            183,  -1,  -1,   1, 176,  -1,  -1,  -1,
             -1,  41,  -1, 189,  17,  -1, 190,  -1,
             38,  -1,  -1,  -1,  -1, 105,  -1,  -1,
             -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,
            131,  -1,  -1,  -1, 209, 210,  -1, 168,
             -1,  -1,  -1, 245,  -1, 252, 173,  -1,
             -1, 203,  -1, 132,  70,   5, 112,  -1,
            205,  -1,  -1, 212,  -1, 103,  -1,  -1,
            230, 211,  -1,  -1, 186,  -1, 105, 190,
            105,  -1,  -1,  -1, 220, 177, 219,  -1,
             68,  -1,  -1,  -1,  -1,  -1,  -1, 224,
             -1,  -1,  37, 152, 222,  -1,  -1, 227,
             -1,  -1,  83,  -1,  -1,  -1,  43, 232,
             -1,  -1, 105, 236,  -1, 250, 242,  -1,
             -1,  -1,  -1,  -1,  -1,  -1,  18, 194,
             -1, 250,  -1,  -1, 235, 164,  -1,  32,
             -1,  -1,  -1, 138,  43,  43,  -1, 250,
             -1,  -1
    };

    static {
        // These maps should have been generated programmatically; look for accidental edits
        //noinspection ConstantConditions
        assert ISO_639_2_CODES.length == 490;
        //noinspection ConstantConditions
        assert ISO_639_2_NAMES.length == 490;
        //noinspection ConstantConditions
        assert ISO_639_2_TO_1.length == 490;
        //noinspection ConstantConditions
        assert ISO_639_1_CODES.length == 187;
        //noinspection ConstantConditions
        assert ISO_639_1_TO_2.length == 187;

        //noinspection ConstantConditions
        assert ISO_3166_2_CODES.length == 253;
        //noinspection ConstantConditions
        assert ISO_3166_2_NAMES.length == 253;
        //noinspection ConstantConditions
        assert ISO_3166_2_TO_1.length == 253;
        //noinspection ConstantConditions
        assert ISO_3166_1_CODES.length == 253;
        //noinspection ConstantConditions
        assert ISO_3166_1_TO_2.length == 253;

        //noinspection ConstantConditions
        assert LANGUAGE_REGION.length == 490;
        //noinspection ConstantConditions
        assert LANGUAGE_REGIONS.length == 490;
    }
}
