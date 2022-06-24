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

package com.android.ide.common.generate.locale;

import com.android.ide.common.resources.LocaleManager;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.ibm.icu.util.ULocale;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Code which generates the lookup tables in {@link LocaleManager}.
 * <p>
 * This basically uses ICU data to look up language codes, language names,
 * region codes and region names, and augments these with data from
 * ISO 639 and 3166, and then generates code tables for the LocaleManager.
 * It can optionally also directly update that class; see the UPDATE_REPO property.
 * <p>
 * It also contains a lot of code to compute timezone tables; these are
 * no longer used (this was to try to guess a default region for a given
 * language, but that's a tricky thing to do which we're no longer attempting.
 */
public class LocaleTableGenerator {
    private static final boolean DEBUG = true;
    private static final boolean ESCAPE_UNICODE = false;
    private static final boolean INCLUDE_TIMEZONE_COMMENTS = false;
    private static final int PADDING = 16;
    // point to your own source repo here
    private static final File UPDATE_REPO = new File("");

    private final Map<String, String> mLanguage3to2;
    private final Map<String, String> mRegion3to2;
    private final Map<String, String> mLanguage2to3;
    private final Map<String, String> mRegion2to3;
    private final Map<String, String> mRegionName;
    private final Map<String, String> mLanguageName;
    private final Multimap<String, String> mAssociatedRegions;
    private final List<String> mLanguage2Codes;
    private final List<String> mLanguage3Codes;
    private final List<String> mRegion2Codes;
    private final List<String> mRegion3Codes;
    private final Map<String, Integer> mLanguage2Index;
    private final Map<String, Integer> mLanguage3Index;
    private final Map<String, Integer> mRegion2Index;
    private final Map<String, Integer> mRegion3Index;
    private final Map<String, List<String>> mRegionLists;
    @SuppressWarnings("StringBufferField") private StringBuilder mOutput;
    private Map<TimeZone, String> mZoneToRegion;
    private List<TimeZone> mZoneCandidates;

    public static void main(String[] args) {
        new LocaleTableGenerator().generate();
    }

    private void importLanguageSpec() {
        // Read in contents of http://www.loc.gov/standards/iso639-2/ISO-639-2_utf-8.txt
        byte[] bytes = new byte[0];
        try {
            HttpURLConnection connection = null;
            InputStream stream = LocaleTableGenerator.class.getResourceAsStream("/ISO-639-2_utf-8.txt");
            if (stream == null) {
                // Try to fetch it remotely
                URL url = new URL("https://www.loc.gov/standards/iso639-2/ISO-639-2_utf-8.txt");
                connection = (HttpURLConnection) url.openConnection();
                stream = connection.getInputStream();
                if (stream == null) {
                    System.err.println("Couldn't find 639-2 spec; download from "
                            + "http://www.loc.gov/standards/iso639-2/ISO-639-2_utf-8.txt and place "
                            + "in source folder");
                    System.exit(-1);
                }
            }
            bytes = ByteStreams.toByteArray(stream);
            stream.close();
            if (connection != null) {
                connection.disconnect();
            }
        } catch (IOException e) {
            assert false;
        }
        if (bytes.length == 0L) {
            System.err.println("Could not read language spec");
            System.exit(-1);
        }
        String spec = new String(bytes, Charsets.UTF_8);
        if (spec.charAt(0) == '\ufeff') { // Strip off UTF-8 BOM
            spec = spec.substring(1);
        }
        int count3 = 0;
        int count2 = 0;
        int matched = 0;
        for (String line : Splitter.on('\n').trimResults().omitEmptyStrings().split(spec)) {
            String[] components = line.split("\\|");
            assert components.length == 5 : line;
            String iso2 = components[2];
            String iso3 = components[0];
            String languageName = components[3];
            assert iso3 != null : line;
            assert iso2 != null : line;
            assert languageName != null : line;
            if (iso3.contains("-") || languageName.contains("Reserved")) {
                // e.g. "qaa-qtz|||Reserved for local use|"
                continue;
            }
            assert iso3.length() == 3 : iso3;

            // 3 languages in that spec have deprecated codes which will not work right;
            // see LocaleFolderDetector#DEPRECATED_CODE for details
            switch (iso2) {
                case "he":
                    iso2 = "iw";
                    break;
                case "id":
                    iso2 = "in";
                    break;
                case "yi":
                    iso2 = "ji";
                    break;
            }

            if (!iso2.isEmpty() && mLanguage2to3.containsKey(iso2)) {
                // We already know about this one
                matched++;
                continue;
            } else if ("zxx".equals(iso3)) {
                // "No linguistic content
                continue;
            }
            if (!iso2.isEmpty()) {
                assert iso2.length() == 2 : iso2;
                mLanguage2to3.put(iso2, iso3);
                count2++;
            }
            if (!mLanguageName.containsKey(iso3)) {
                count3++;
                mLanguageName.put(iso3, languageName);
            }
        }

        if (DEBUG) {
            System.out.println("Added in " + count3 + " extra language codes from the "
                    + "ISO-639-2 spec, " + count2
                    + " of them for 2-letter codes, and " + matched
                    + " were ignored because we had ICU data.");
        }
    }

    private int findTagContentBegin(String html, String tag, int from) {
        int offset = from;
        while (offset < html.length()) {
            if (html.charAt(offset) == '<' && html.regionMatches(offset + 1, tag, 0, tag.length())) {
                while (html.charAt(offset) != '>') {
                    offset++;
                }
                return offset + 1;
            }
            offset++;
        }

        return -1;
    }

    private String getTagContent(String html, int start) {
        int end = html.indexOf('<', start);
        return html.substring(start, end).trim();
    }

    private void importRegionSpec() {
        try {
            InputStream stream = LocaleTableGenerator.class.getResourceAsStream("/obp.html");
            if (stream == null) {
                System.err.println("Visit https://www.iso.org/obp/ui/#search/code/ to see the full set of ISO 2 and 3 codes,");
                System.err.println("switch the \"Results per page\" dropdown to the max (300), and save the page.");
                System.err.println("Then check the .html file into the sdk-common/generate-locale-data/src/main/resources/");
                System.err.println("folder and rerun.");
                System.exit(-1);
            }

            byte[] bytes = ByteStreams.toByteArray(stream);
            String s = new String(bytes, Charsets.UTF_8);

            int count3 = 0;
            int matched = 0;
            int found = 0;

            for (String line : s.split("<tr")) {
                int offset = findTagContentBegin(line, "button", 0);
                if (offset == -1) {
                    continue;
                }
                String region = getTagContent(line, offset);
                int skip = findTagContentBegin(line, "td", offset);
                if (skip == -1) {
                    continue;
                }
                int iso2Begin = findTagContentBegin(line, "td", skip);
                if (iso2Begin == -1) {
                    continue;
                }
                String iso2 = getTagContent(line, iso2Begin);
                int iso3Begin = findTagContentBegin(line, "td", iso2Begin);
                if (iso3Begin == -1) {
                    continue;
                }

                String iso3 = getTagContent(line, iso3Begin);
                found++;

                String icuRegionName = mRegionName.get(iso3);
                String icuRegion3 = mRegion2to3.get(iso2);
                if (icuRegion3 == null || icuRegionName == null) {
                    mRegion2to3.put(iso2, iso3);
                    mRegionName.put(iso3, region);
                    count3++; // AQ/ATA (Antarctica)
                } else if (!iso3.equals(icuRegion3)) {
                    assert false : "Unexpected disagreement in the data; ICU vs ISO spec";
                } else {
                    matched++;
                }
            }

            if (found < 100) {
                System.err.println("Only found " + found + " entries in the obp database file.");
                System.err.println("This is suspicious and probably means the formatting changed");
                System.err.println("and the code to extract info from the HTML table needs to");
                System.err.println("be updated.");
                System.exit(-1);
            }
            if (DEBUG) {
                System.out.println("Added in " + count3 + " extra region codes from the OBP spec, "
                        + "and " + matched + " were ignored because we already had ICU data.");
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void importLanguageSpec639_3() {
        // This method reads in additional language data for iso-639-3; however, this is apparently
        // a MUCH larger spec -- it adds in over 7000 new languages, so leaving this out for now.
        if (true) {
            // If you put this back in sometime in the future, be sure to add attribution to
            //     ISO 639-3: http://www.iso639-3.sil.org/
            // to the LocaleManager javadoc.
            return;
        }

        InputStream stream = LocaleTableGenerator.class.getResourceAsStream("/iso-639-3.zip");
        if (stream == null) {
            System.err.println(""
                    + "Visit https://iso639-3.sil.org/code_tables/download_tables and\n"
                    + "download the UTF code tables zip file, then rename and copy it into\n"
                    + "    sdk-common/generate-locale-data/src/main/resources/iso-639-3.zip\n"
                    + "and rerun.");
            System.exit(-1);
        }

        int count3 = 0;
        int count2 = 0;
        int matched = 0;

        try {
            JarInputStream jarInputStream = new JarInputStream(stream);
            boolean found = false;
            while (true) {
                JarEntry entry = jarInputStream.getNextJarEntry();
                if (entry == null) {
                    break;
                } else if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.matches("iso-639-3_Code_Tables_\\d+/iso-639-3_\\d+\\.tab")) {
                    found = true;
                    byte[] bytes = ByteStreams.toByteArray(jarInputStream);
                    String s = new String(bytes, Charsets.UTF_8).replace("\r\n", "\n");
                    Pattern pattern = Pattern.compile("([^\t]*)\t([^\t]*)\t([^\t]*)\t([^\t]*)\t([^\t]*)\t([^\t]*)\t([^\t]*)\t([^\t]*)");
                    for (String line : s.split("\n")) {
                        Matcher matcher = pattern.matcher(line);
                        if (matcher.find()) {
                            String iso3 = matcher.group(1); // id
                            String iso2 = matcher.group(3); // part 2t
                            String languageName = matcher.group(7); // ref_name
                            if (iso3.equals("Id") || languageName.equals("Ref_Name")) {
                                // This i the header line
                                continue;
                            }
                            if (!mLanguageName.containsKey(iso3)) {
                                if ("zxx".equals(iso3)) {
                                    // "No linguistic content
                                    continue;
                                }
                                mLanguageName.put(iso3, languageName);
                                count3++;
                                if (!iso2.isEmpty()) {
                                    mLanguage2to3.put(iso2, iso3);
                                    count2++;
                                }
                            } else {
                                matched++;
                            }
                        }
                    }
                }
            }
            jarInputStream.close();
            if (!found) {
                System.err.println("Didn't find the ISO 639-3 table file in the zip; the naming");
                System.err.println("scheme must have changed since this code was written.");
                System.exit(-1);
            }

            if (DEBUG) {
                System.out.println("Added in " + count3 + " extra language codes from the "
                        + "ISO-639-2 spec, " + count2
                        + " of them for 2-letter codes, and " + matched
                        + " were ignored because we had ICU data.");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public LocaleTableGenerator() {
        mLanguage2to3 = Maps.newHashMap();
        mRegion2to3 = Maps.newHashMap();
        mLanguageName = Maps.newHashMap();
        mRegionName = Maps.newHashMap();
        mAssociatedRegions = ArrayListMultimap.create();

        // First, import data from ICU. This is where we get most of the data;
        // it will populate the mLanguageName map which records mapping from the
        // ISO-639 three letter language codes to the corresponding language name,
        // and the mLanguage2to3 map which records the two letter language codes
        // (for those that have it) to the corresponding 3 letter language code.
        // Ditto for mRegionnName and mRegion2to3, which records the ISO-3166
        // region codes, names, and 2 and 3-letter code mappings.

        importFromIcu4j();

        // We've pulled data out of ICU, but this does not include all the languages
        // and regions from ISO 639 and ISO 3166, so we import those specs too
        // and complement the data. (In theory we could skip importing the ICU data
        // and only use the ISO data but this changes the region names and language
        // names a bit; keeping things compatible with previous versions for now.)
        //
        // For languages in particular, a lot are missing (as of 2022 around 54 languages that
        // have 2-letter language codes, such as Tagalog (tl/tgl) and Fijian (fj/fij),
        // and another 297 languages with only 3-letter language codes such as Dakota (dak).
        // For languages for example, we are missing dak (Dakota), and many others.
        importLanguageSpec();

        // Import regions too; here the ICU data is pretty complete and we're only missing
        // around 5 regions, such as Antarctica (AQ/ATA)
        importRegionSpec();

        // Augment with a few ISO 639-3 codes
        importLanguageSpec639_3();

        // UMN.49: http://en.wikipedia.org/wiki/UN_M.49
        populateUNM49();

        mLanguage3to2 = Maps.newHashMap();
        for (Map.Entry<String,String> entry : mLanguage2to3.entrySet()) {
            mLanguage3to2.put(entry.getValue(), entry.getKey());
        }
        mRegion3to2 = Maps.newHashMap();
        for (Map.Entry<String,String> entry : mRegion2to3.entrySet()) {
            mRegion3to2.put(entry.getValue(), entry.getKey());
        }

        // Register deprecated codes manually. This is done *after* we produce the
        // reverse map above, such that we make sure we don't accidentally have
        // the 3-letter new code map back and override the proper 3-letter code
        // with the deprecated code
        if (!mLanguageName.containsKey("ind")) {
            mLanguageName.put("ind", "Indonesian");
        }
        if (!mLanguageName.containsKey("yid")) {
            mLanguageName.put("ji", "Yiddish");
        }
        if (!mLanguageName.containsKey("heb")) {
            mLanguageName.put("iw", "Hebrew");
        }
        mLanguage2to3.put("in", "ind"); // proper 3-letter code, but NOT mapping back: should be id
        mLanguage2to3.put("ji", "yid"); // proper 3-letter code, but NOT mapping back: should be yi
        mLanguage2to3.put("iw", "heb"); // proper 3-letter code, but NOT mapping back: should be he
        // Make sure the forward map (from 3 to 2) is also using the primary (new) code
        mLanguage3to2.put("ind", "in");
        mLanguage3to2.put("heb", "iw");
        mLanguage3to2.put("yid", "ji");


        mLanguage2Codes = sorted(mLanguage2to3.keySet());
        mLanguage3Codes = sorted(mLanguageName.keySet());
        mRegion2Codes = sorted(mRegion2to3.keySet());
        mRegion3Codes = sorted(mRegionName.keySet());

        mLanguage2Index = Maps.newHashMap();
        for (int i = 0, n = mLanguage2Codes.size(); i < n; i++) {
            mLanguage2Index.put(mLanguage2Codes.get(i), i);
        }
        mLanguage3Index = Maps.newHashMap();
        for (int i = 0, n = mLanguage3Codes.size(); i < n; i++) {
            mLanguage3Index.put(mLanguage3Codes.get(i), i);
        }
        mRegion2Index = Maps.newHashMap();
        for (int i = 0, n = mRegion2Codes.size(); i < n; i++) {
            mRegion2Index.put(mRegion2Codes.get(i), i);
        }
        mRegion3Index = Maps.newHashMap();
        for (int i = 0, n = mRegion3Codes.size(); i < n; i++) {
            mRegion3Index.put(mRegion3Codes.get(i), i);
        }

        // Compute list of ordered regions
        mRegionLists = Maps.newHashMap();
        for (String code : mLanguage3Codes) {
            Collection<String> regions = mAssociatedRegions.get(code);
            final String defaultRegion = getDefaultRegionFor(code);
            if (regions == null || regions.size() < 2) {
                if (regions != null && regions.size() == 1 && defaultRegion != null) {
                    assert regions.iterator().next().equals(defaultRegion);
                }
                if (regions != null && regions.size() == 1) {
                    mRegionLists.put(code, Collections.singletonList(regions.iterator().next()));
                } else if (defaultRegion != null) {
                    mRegionLists.put(code, Collections.singletonList(defaultRegion));
                }
            } else {
                final List<String> sorted = Lists.newArrayList(regions);
                Collections.sort(sorted, new Comparator<String>() {
                    @Override
                    public int compare(String o1, String o2) {
                        int rank1 = o1.equals(defaultRegion) ? 0 : 1;
                        int rank2 = o2.equals(defaultRegion) ? 0 : 1;
                        int delta = rank1 - rank2;
                        if (delta == 0) {
                            delta = o1.compareTo(o2);
                            assert delta != 0 :
                                    "Found more than one occurrence of " + o1 + " in " + sorted;
                        }
                        return delta;
                    }
                });
                mRegionLists.put(code, sorted);
            }
        }
    }

    private void importFromIcu4j() {
        for (ULocale locale : ULocale.getAvailableLocales()) {
            String language2 = locale.getLanguage();
            String language3 = locale.getISO3Language();
            if (language2.equals(language3)) {
                continue;
            } else if (language3.isEmpty()) {
                if (language2.length() == 3) {
                    // ISO language
                    language3 = language2;
                } else {
                    // suspicious; skip this one. Misconfigured ICU?
                    continue; // THIS IS TRUE FOR ALL ISO3-only languages
                }
            } else {
                mLanguage2to3.put(language2, language3);
            }
            mLanguageName.put(language3, locale.getDisplayLanguage());

            String region3 = locale.getISO3Country();
            if (region3 != null && !region3.isEmpty()) {
                mRegionName.put(region3, locale.getDisplayCountry());
                String region2 = locale.getCountry();
                if (!region3.equals(region2)) {
                    mRegion2to3.put(region2, region3);
                }

                if (!mAssociatedRegions.containsEntry(language3, region3)) {
                    mAssociatedRegions.put(language3, region3);
                }
            }

            if (locale.getFallback() != null && !locale.getFallback().toString().isEmpty() &&
                     !locale.toString().startsWith(locale.getFallback().toString())) {
                System.out.println("Fallback for " + locale + " is " + locale.getFallback());
            }
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    private void registerMacroLanguage(String iso2, String iso3) {
        if (!mLanguage2to3.containsKey(iso2)) {
            assert mLanguageName.containsKey(iso3) : iso2;
            mLanguage2to3.put(iso2, iso3);
        }
    }

    private void populateUNM49() {
        // TODO: Populate region names from http://en.wikipedia.org/wiki/UN_M.49, e.g.
        // via something like the following (but we're not doing this, since ICU4J
        // can't provide actual region names:
        //        for (Region region : Region.getAvailable(Region.RegionType.CONTINENT)) {
        //            mRegionName.put(String.valueOf(region.getNumericCode()), region.toString());
        //        }
        // What we want here are codes like
        //  001 World
        //  002 Africa
        //  015 Northern Africa
        //  014 Eastern Africa
        // etc
    }

    private String stripTrailing(String s) {
        return s.replaceAll(" +\n", "\n");
    }

    private void generate() {
        String code1 = generateTimeZoneLookup();
        String code2 = generateLocaleTables();

        String header =
                "    // The remainder of this class is generated by generate-locale-data\n" +
                        "    // DO NOT EDIT MANUALLY\n\n";
        String code = header + stripTrailing(code1) + "\n" + stripTrailing(code2);
        if (DEBUG) {
            int lines = 0;
            for (int i = 0, n = code.length(); i < n; i++) {
                if (code.charAt(i) == '\n') {
                    lines++;
                }
            }
            System.out.println("Generated " + lines + " lines.");
        }
        try {
            File tempFile = File.createTempFile("LocaleData", ".java");
            Files.asCharSink(tempFile, Charsets.UTF_8).write(code);
            System.out.println("Wrote updated locale data code fragment to " + tempFile);

            if (UPDATE_REPO.getPath().length() > 0) {
                File file = new File(UPDATE_REPO, "tools/base/sdk-common/src/main/java/com/android/ide/common/resources/LocaleManager.java");
                if (!file.exists()) {
                    System.out.println("Did not find " + file + ": cannot update in place");
                    System.exit(-1);
                }
                String current = Files.asCharSource(file, Charsets.UTF_8).read();
                int index = current.indexOf(header);
                if (index == -1) {
                    System.err.println("Could not find the header in " + file + ": " + header);
                    System.exit(-1);
                }
                String updated = current.substring(0, index) + code + "}\n";
                Files.asCharSink(file, Charsets.UTF_8).write(updated);
                System.out.println("Updated " + file + " in place!");
            } else {
                System.out.println("FYI: You can update the UPDATE_REPO property in " +
                        LocaleTableGenerator.class.getSimpleName() +
                        " to have the generator update LocaleManager in place!");
            }

        } catch (IOException ioe) {
            ioe.printStackTrace();
            assert false;
        }
    }

    private String generateLocaleTables() {
        StringBuilder sb = new StringBuilder(5000);

        int level = 1;
        generateLanguageTables(sb, level);
        generateRegionTables(sb, level);
        generateRegionMappingTables(sb, level);
        generateAssertions(sb, level);

        if (DEBUG) {
            System.out.println("Number of languages=" + mLanguageName.size());
            System.out.println("Number of regions=" + mRegionName.size());
        }

        return sb.toString();
    }

    private void generateRegionMappingTables(StringBuilder sb, int level) {
        for (String code : mLanguage3Codes) {
            List<String> sorted = mRegionLists.get(code);
            if (sorted != null && sorted.size() > 1) {
                indent(sb, level);
                sb.append("// Language ").append(code).append(": ")
                        .append(Joiner.on(",").join(sorted)).append("\n");

                indent(sb, level);
                sb.append("private static final int[] ").append(getLanguageRegionFieldName(code))
                        .append(" = new int[] { ");
                boolean first = true;
                for (String region : sorted) {
                    Integer integer = mRegion3Index.get(region);
                    assert integer != null : region;
                    if (first) {
                        first = false;
                    } else {
                        sb.append(",");
                    }
                    sb.append(integer);
                }
                sb.append(" };\n");
            }
        }

        // Format preferred regions (multiple)
        sb.append("\n");
        indent(sb, level);
        sb.append("private static final int[][] LANGUAGE_REGIONS = new int[][] {\n");
        int column = 0;
        int tableIndent = level + 2;
        indent(sb, tableIndent);
        int lineBegin = sb.length();
        for (int i = 0, n = mLanguage3Codes.size(); i < n; i++) {
            String code = mLanguage3Codes.get(i);
            List<String> sorted = mRegionLists.get(code);
            if (sorted != null && sorted.size() > 1) {
                sb.append(getLanguageRegionFieldName(code));
            } else {
                sb.append("null");
            }
            if (i < n - 1) {
                sb.append(", ");
            }
            column++;
            if (sb.length() - lineBegin > 60) {
                sb.append("\n");
                lineBegin = sb.length();
                if (i < n - 1) {
                    indent(sb, tableIndent);
                }
            }
        }
        sb.append("\n");
        indent(sb, level);
        sb.append("};\n");

        sb.append("\n");

        // Format preferred region (just one)
        indent(sb, level);
        sb.append("private static final int[] LANGUAGE_REGION = new int[] {\n");
        column = 0;
        indent(sb, tableIndent);
        for (int i = 0, n = mLanguage3Codes.size(); i < n; i++) {
            String iso3 = mLanguage3Codes.get(i);
            List<String> sorted = mRegionLists.get(iso3);
            Integer index = -1;
            if (sorted != null && !sorted.isEmpty()) {
                index = mRegion3Index.get(sorted.get(0));
            }
            append(sb, String.valueOf(index), 3, true);
            if (i < n - 1) {
                sb.append(", ");
            }
            column++;
            if (column == 8) {
                column = 0;
                sb.append("\n");
                if (i < n - 1) {
                    indent(sb, tableIndent);
                }
            }
        }
        sb.append("\n");
        indent(sb, level);
        sb.append("};\n");
        sb.append("\n");
    }

    private static String getLanguageRegionFieldName(String languageCode) {
        assert languageCode.length() == 3 : languageCode;
        return "REGIONS_" + languageCode.toUpperCase(Locale.US);
    }

    private String getDefaultRegionFor(String languageCode) {
        assert languageCode.length() == 3 : languageCode;

        String iso2Language = getIso2Language(languageCode);
        String hardcoded = iso2Language != null ? LocaleManager.getDefaultLanguageRegion(
                iso2Language) : null;
        if (hardcoded != null) {
            if (getIso3Region(hardcoded) == null) {
                assert false : "Couldn't find an ISO 3 region for " + hardcoded + " with language code " + languageCode + " (region-2 is " + hardcoded;
            }
            hardcoded = getIso3Region(hardcoded);
            assert hardcoded != null : languageCode;
        }

        Collection<String> regions = mAssociatedRegions.get(languageCode);
        if (regions != null && !regions.isEmpty()) {
            // Violated for example for gsw (Swiss German, with regions CHE, FRA, LIE)
            //assert iso2Language != null : languageCode + " didn't have an iso 2 code but does have regions " + regions; // We only have
            if (regions.size() == 1) {
                // Make
                String region = regions.iterator().next();
                assert hardcoded == null || region.equals(hardcoded) : region + " vs " + hardcoded;
                return region;
            }

            if (hardcoded == null || regions.contains(hardcoded)) {
                return hardcoded;
            } else {
                assert false : "Didn't find region " + hardcoded + " in expected regions " + regions + " for " + languageCode;
            }
        }

        return hardcoded;
    }

    private void generateAssertions(StringBuilder sb, int level) {
        indent(sb, level);
        sb.append("static {\n");
        level++;


        indent(sb, level);
        sb.append(
                "// These maps should have been generated programmatically; look for accidental edits\n");
        indent(sb, level);
        sb.append("//noinspection ConstantConditions\n");
        indent(sb, level);
        sb.append("assert ISO_639_2_CODES.length == ").append(mLanguage3Codes.size()).append(";\n");
        indent(sb, level);
        sb.append("//noinspection ConstantConditions\n");
        indent(sb, level);
        sb.append("assert ISO_639_2_NAMES.length == ").append(mLanguage3Codes.size()).append(";\n");
        indent(sb, level);
        sb.append("//noinspection ConstantConditions\n");
        indent(sb, level);
        sb.append("assert ISO_639_2_TO_1.length == ").append(mLanguage3Codes.size()).append(";\n");
        indent(sb, level);
        sb.append("//noinspection ConstantConditions\n");
        indent(sb, level);
        sb.append("assert ISO_639_1_CODES.length == ").append(mLanguage2Codes.size()).append(";\n");
        indent(sb, level);
        sb.append("//noinspection ConstantConditions\n");
        indent(sb, level);
        sb.append("assert ISO_639_1_TO_2.length == ").append(mLanguage2Codes.size()).append(";\n");
        sb.append("\n");
        indent(sb, level);
        sb.append("//noinspection ConstantConditions\n");
        indent(sb, level);
        sb.append("assert ISO_3166_2_CODES.length == ").append(mRegion3Codes.size()).append(";\n");
        indent(sb, level);
        sb.append("//noinspection ConstantConditions\n");
        indent(sb, level);
        sb.append("assert ISO_3166_2_NAMES.length == ").append(mRegion3Codes.size()).append(";\n");
        indent(sb, level);
        sb.append("//noinspection ConstantConditions\n");
        indent(sb, level);
        sb.append("assert ISO_3166_2_TO_1.length == ").append(mRegion3Codes.size()).append(";\n");
        indent(sb, level);
        sb.append("//noinspection ConstantConditions\n");
        indent(sb, level);
        sb.append("assert ISO_3166_1_CODES.length == ").append(mRegion2Codes.size()).append(";\n");
        indent(sb, level);
        sb.append("//noinspection ConstantConditions\n");
        indent(sb, level);
        sb.append("assert ISO_3166_1_TO_2.length == ").append(mRegion2Codes.size()).append(";\n");
        sb.append("\n");
        indent(sb, level);
        sb.append("//noinspection ConstantConditions\n");
        indent(sb, level);
        sb.append("assert LANGUAGE_REGION.length == ").append(mLanguage3Codes.size()).append(";\n");
        indent(sb, level);
        sb.append("//noinspection ConstantConditions\n");
        indent(sb, level);
        sb.append("assert LANGUAGE_REGIONS.length == ").append(mLanguage3Codes.size()).append(";\n");

        level--;
        indent(sb, level);
        sb.append("}\n");
    }

    @SuppressWarnings("UnusedDeclaration")
    private String getIso3Language(String iso2Code) {
        assert iso2Code.length() == 3 : iso2Code;
        return mLanguage2to3.get(iso2Code);
    }

    private String getIso2Language(String iso3Code) {
        assert iso3Code.length() == 3 : iso3Code + " was " + iso3Code.length() + " chars";
        return mLanguage3to2.get(iso3Code);
    }

    private String getIso3Region(String iso2Code) {
        assert iso2Code.length() == 2 : iso2Code;
        return mRegion2to3.get(iso2Code);
    }

    private String getIso2Region(String iso3Code) {
        assert iso3Code.length() == 3 : iso3Code;
        return mRegion3to2.get(iso3Code);
    }

    private void generateLanguageTables(StringBuilder sb, int level) {

        // Format ISO 3 code table
        indent(sb, level);
        sb.append("private static final String[] ISO_639_2_CODES = new String[] {\n");
        int column = 0;
        int tableIndent = level + 2;
        indent(sb, tableIndent);
        for (int i = 0, n = mLanguage3Codes.size(); i < n; i++) {
            String code = mLanguage3Codes.get(i);
            sb.append('"').append(code).append('"');
            if (i < n - 1) {
                sb.append(", ");
            }
            column++;
            if (column == 9) {
                column = 0;
                sb.append("\n");
                if (i < n - 1) {
                    indent(sb, tableIndent);
                }
            }
        }
        sb.append("\n");
        indent(sb, level);
        sb.append("};\n");

        sb.append("\n");

        // ISO 3 language names
        indent(sb, level);
        sb.append("@SuppressWarnings(\"WrongTerminology\")\n");
        indent(sb, level);
        sb.append("private static final String[] ISO_639_2_NAMES = new String[] {\n");
        for (int i = 0, n = mLanguage3Codes.size(); i < n; i++) {
            String code = mLanguage3Codes.get(i);
            String name = mLanguageName.get(code);
            assert name != null : code;
            indent(sb, tableIndent);
            String literal = '"' + escape(name) + '"' + (i < n -1 ? "," : "");
            append(sb, literal, 40, false);
            sb.append("// Code ").append(code);
            String iso2 = getIso2Language(code);
            if (iso2 != null) {
                sb.append("/").append(iso2);
            }
            sb.append("\n");
        }
        indent(sb, level);
        sb.append("};\n");

        sb.append("\n");

        // Format ISO 2 code table
        indent(sb, level);
        sb.append("private static final String[] ISO_639_1_CODES = new String[] {\n");
        column = 0;
        indent(sb, tableIndent);

        for (int i = 0, n = mLanguage2Codes.size(); i < n; i++) {
            String code = mLanguage2Codes.get(i);
            sb.append('"').append(code).append('"');
            if (i < n - 1) {
                sb.append(", ");
            }
            column++;
            if (column == 11) {
                column = 0;
                sb.append("\n");
                if (i < n - 1) {
                    indent(sb, tableIndent);
                }
            }
        }

        sb.append("\n");
        indent(sb, level);
        sb.append("};\n");
        sb.append("\n");

        // Format iso2 to iso3 mapping table
        indent(sb, level);
        sb.append("// Each element corresponds to an ISO 639-1 code, and contains the index\n");
        indent(sb, level);
        sb.append("// for the corresponding ISO 639-2 code\n");
        indent(sb, level);
        sb.append("private static final int[] ISO_639_1_TO_2 = new int[] {\n");
        column = 0;
        indent(sb, tableIndent);
        for (int i = 0, n = mLanguage2Codes.size(); i < n; i++) {
            String iso2 = mLanguage2Codes.get(i);
            String iso3 = mLanguage2to3.get(iso2);
            int index = mLanguage3Index.get(iso3);
            append(sb, String.valueOf(index), 3, true);
            if (i < n - 1) {
                sb.append(", ");
            }
            column++;
            if (column == 11) {
                column = 0;
                sb.append("\n");
                if (i < n - 1) {
                    indent(sb, tableIndent);
                }
            }
        }
        sb.append("\n");
        indent(sb, level);
        sb.append("};\n");

        sb.append("\n");

        // Format iso3 to iso2 mapping table
        indent(sb, level);
        sb.append("// Each element corresponds to an ISO 639-2 code, and contains the index\n");
        indent(sb, level);
        sb.append("// for the corresponding ISO 639-1 code, or -1 if not represented\n");
        indent(sb, level);
        sb.append("private static final int[] ISO_639_2_TO_1 = new int[] {\n");
        column = 0;
        indent(sb, tableIndent);
        for (int i = 0, n = mLanguage3Codes.size(); i < n; i++) {
            String iso3 = mLanguage3Codes.get(i);
            int index = -1;
            String iso2 = mLanguage3to2.get(iso3);
            if (iso2 != null) {
                index = mLanguage2Index.get(iso2);
            }
            append(sb, String.valueOf(index), 3, true);
            if (i < n - 1) {
                sb.append(", ");
            }
            column++;
            if (column == 11) {
                column = 0;
                sb.append("\n");
                if (i < n - 1) {
                    indent(sb, tableIndent);
                }
            }
        }
        sb.append("\n");
        indent(sb, level);
        sb.append("};\n");
    }

    private void generateRegionTables(StringBuilder sb, int level) {
        // Format ISO 3 code table
        indent(sb, level);
        sb.append("private static final String[] ISO_3166_2_CODES = new String[] {\n");
        int column = 0;
        int tableIndent = level + 2;
        indent(sb, tableIndent);
        for (int i = 0, n = mRegion3Codes.size(); i < n; i++) {
            String code = mRegion3Codes.get(i);
            sb.append('"').append(code).append('"');
            if (i < n - 1) {
                sb.append(", ");
            }
            column++;
            if (column == 9) {
                column = 0;
                sb.append("\n");
                if (i < n - 1) {
                    indent(sb, tableIndent);
                }
            }
        }
        sb.append("\n");
        indent(sb, level);
        sb.append("};\n");

        sb.append("\n");

        // ISO 3 language names
        indent(sb, level);
        sb.append("private static final String[] ISO_3166_2_NAMES = new String[] {\n");
        for (int i = 0, n = mRegion3Codes.size(); i < n; i++) {
            String code = mRegion3Codes.get(i);
            String name = mRegionName.get(code);
            assert name != null : code;
            indent(sb, tableIndent);
            String literal = '"' + escape(name) + '"' + (i < n -1 ? "," : "");
            append(sb, literal, 40, false);
            sb.append("// Code ").append(code);
            String iso2 = getIso2Region(code);
            if (iso2 != null) {
                sb.append("/").append(iso2);
            }

            sb.append("\n");
        }
        indent(sb, level);
        sb.append("};\n");

        sb.append("\n");

        // Format ISO 2 code table
        indent(sb, level);
        sb.append("private static final String[] ISO_3166_1_CODES = new String[] {\n");
        column = 0;
        indent(sb, tableIndent);

        for (int i = 0, n = mRegion2Codes.size(); i < n; i++) {
            String code = mRegion2Codes.get(i);
            sb.append('"').append(code).append('"');
            if (i < n - 1) {
                sb.append(", ");
            }
            column++;
            if (column == 11) {
                column = 0;
                sb.append("\n");
                if (i < n - 1) {
                    indent(sb, tableIndent);
                }
            }
        }
        sb.append("\n");
        indent(sb, level);
        sb.append("};\n");
        sb.append("\n");

        // Format iso2 to iso3 mapping table
        indent(sb, level);
        sb.append("// Each element corresponds to an ISO2 code, and contains the index\n");
        indent(sb, level);
        sb.append("// for the corresponding ISO3 code\n");
        indent(sb, level);
        sb.append("private static final int[] ISO_3166_1_TO_2 = new int[] {\n");
        column = 0;
        indent(sb, tableIndent);
        for (int i = 0, n = mRegion2Codes.size(); i < n; i++) {
            String iso2 = mRegion2Codes.get(i);
            String iso3 = mRegion2to3.get(iso2);
            assert iso3 != null;
            int index = mRegion3Index.get(iso3);
            append(sb, String.valueOf(index), 3, true);
            if (i < n - 1) {
                sb.append(", ");
            }
            column++;
            if (column == 11) {
                column = 0;
                sb.append("\n");
                if (i < n - 1) {
                    indent(sb, tableIndent);
                }
            }
        }
        sb.append("\n");
        indent(sb, level);
        sb.append("};\n");

        sb.append("\n");

        // Format iso3 to iso2 mapping table
        indent(sb, level);
        sb.append("// Each element corresponds to an ISO3 code, and contains the index\n");
        indent(sb, level);
        sb.append("// for the corresponding ISO2 code, or -1 if not represented\n");
        indent(sb, level);
        sb.append("private static final int[] ISO_3166_2_TO_1 = new int[] {\n");
        column = 0;
        indent(sb, tableIndent);
        for (int i = 0, n = mRegion3Codes.size(); i < n; i++) {
            String iso3 = mRegion3Codes.get(i);
            int index = -1;
            String iso2 = mRegion3to2.get(iso3);
            if (iso2 != null) {
                index = mRegion2Index.get(iso2);
            }
            append(sb, String.valueOf(index), 3, true);
            if (i < n - 1) {
                sb.append(", ");
            }
            column++;
            if (column == 11) {
                column = 0;
                sb.append("\n");
                if (i < n - 1) {
                    indent(sb, tableIndent);
                }
            }
        }
        sb.append("\n");
        indent(sb, level);
        sb.append("};\n");
    }

    private static void append(StringBuilder sb, String string, int width, boolean rhs) {
        if (!rhs) {
            sb.append(string);
        }
        for (int i = width - string.length(); i > 0; i--) {
            sb.append(' ');
        }
        if (rhs) {
            sb.append(string);
        }
    }

    private static void indent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) {
            sb.append("    ");
        }
    }

    private static String escape(String s) {
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (c >= 128) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0, m = s.length(); j < m; j++) {
                    char d = s.charAt(j);
                    if (d < 128 || !ESCAPE_UNICODE) {
                        sb.append(d);
                    }
                    else {
                        sb.append('\\');
                        sb.append('u');
                        sb.append(String.format("%04x", (int) d));
                    }
                }
                return sb.toString();
            }
        }
        return s;
    }


    private static <T extends Comparable> List<T> sorted(Collection<T> list) {
        List<T> sorted = Lists.newArrayList(list);
        Collections.sort(sorted);
        return sorted;
    }

    /** Produce a map from a region code to relevant timezones */
    private static Map<String, Set<TimeZone>> getRegionToZones() {
        Map<String, Set<TimeZone>> zones = new HashMap<String, Set<TimeZone>>();
        for (Locale locale : Locale.getAvailableLocales()) {
            String countryCode = locale.getCountry();
            if (countryCode.length() != 2) {
                continue;
            }
            Set<TimeZone> timezones = zones.get(countryCode);
            if (timezones == null) {
                timezones = Sets.newHashSet();
                zones.put(countryCode, timezones);
            }
            String[] ids = com.ibm.icu.util.TimeZone.getAvailableIDs(countryCode);
            for (String id : ids) {
                timezones.add(TimeZone.getTimeZone(id));
            }
        }
        return zones;
    }

    /** Produces an inverse mapping for the result from {@link #getRegionToZones()} */
    private static Map<TimeZone, String> getZoneToRegion(
            Map<String, Set<TimeZone>> regionToZones) {
        final Map<TimeZone, String> zoneToRegion = Maps.newHashMap();
        for (Map.Entry<String, Set<TimeZone>> entry : regionToZones.entrySet()) {
            String region = entry.getKey();
            for (TimeZone zone : entry.getValue()) {
                if (zone.getID().equals("GMT")) {
                    continue;
                }
                if (zoneToRegion.containsKey(zone) && !zoneToRegion.get(zone).equals(region)) {
                    System.err.println(
                            "Didn't expect multiple regions to have the same time zone: " +
                                    zone.getID() + " in both " + zoneToRegion.get(zone) +
                                    " and now " + region);
                    System.exit(-1);
                }
                zoneToRegion.put(zone, region);
            }
        }

        return zoneToRegion;
    }

    private static int hashedId(TimeZone zone) {
        // Instead of String#hashCode, use this to ensure stable across platforms
        int h = 0;
        String id = zone.getID();
        for (int i = 0, n = id.length(); i < n; i++) {
            h = 31 * h + id.charAt(i);
        }
        return h;
    }

    private List<TimeZone> sortByRegion(Collection<TimeZone> zones) {
        List<TimeZone> sorted = Lists.newArrayList(zones);

        final Map<String,Integer> regionFrequency = Maps.newHashMap();
        for (TimeZone zone : zones) {
            String region = mZoneToRegion.get(zone);
            Integer frequency = regionFrequency.get(region);
            if (frequency == null) {
                regionFrequency.put(region, 1);
            } else {
                regionFrequency.put(region, frequency + 1);
            }
        }

        Collections.sort(sorted, new Comparator<TimeZone>() {
            // Sort such that the timezones are all sorted (alphabetically) grouped
            // by the same target region, and the regions in turn are sorted
            // by frequency
            @Override
            public int compare(TimeZone o1, TimeZone o2) {
                String r1 = mZoneToRegion.get(o1);
                String r2 = mZoneToRegion.get(o2);
                assert r1 != null : o1.getID();
                assert r2 != null : o2.getID();
                int delta = r1.compareTo(r2);
                if (delta == 0) { // Same region: compare alphabetically by id
                    return o1.getID().compareTo(o2.getID());
                } else {
                    // Different regions: compare by frequency
                    int f1 = regionFrequency.get(r1);
                    int f2 = regionFrequency.get(r2);
                    delta = f1 - f2;
                    if (delta == 0) {
                        delta = r1.compareTo(r2); // If same region frequency, alphabetical
                        if (delta != 0) {
                            // But sort empty strings last
                            if (r1.isEmpty()) {
                                return 1;
                            } else if (r2.isEmpty()) {
                                return -1;
                            }
                        }
                    }
                }

                return delta;
            }
        });
        return sorted;
    }

    public String generateTimeZoneLookup() {
        Map<String, Set<TimeZone>> regionToZones = getRegionToZones(); // ICU data
        Map<TimeZone, String> zoneToRegion = getZoneToRegion(regionToZones);

        // Make sure this is generated on a recent JDK which has a lot of
        // the new time zones
        String property = System.getProperty("java.version");
        assert property.startsWith("1.") : property;
        assert property.charAt(2) >= '8' : "Use a more recent JRE when generating data";

        List<TimeZone> candidates = Lists.newArrayList(zoneToRegion.keySet());

        mZoneToRegion = zoneToRegion;
        mZoneCandidates = candidates;
        mOutput = new StringBuilder(1000);

        // Generate lookup tree for regions
        final Multimap<String, TimeZone> mContinentMap = ArrayListMultimap.create();
        for (TimeZone zone : mZoneCandidates) {
            String id = zone.getID();
            int index = id.indexOf('/');
            String continent = "";
            if (index != -1) {
                continent = id.substring(0, index);
            }
            mContinentMap.put(continent, zone);
        }
        List<String> sortedContinents = Lists.newArrayList(mContinentMap.keySet());
        Collections.sort(sortedContinents, new Comparator<String>() {
            // Sort by decreasing timezone-per continent count, but put the
            // no-continent codes at the end
            @Override
            public int compare(String o1, String o2) {
                boolean e1 = o1.isEmpty();
                boolean e2 = o2.isEmpty();
                if (e1 && e2) {
                    return 0;
                } else if (e1) {
                    return 1;
                } else if (e2) {
                    return -1;
                } else {
                    int delta = mContinentMap.get(o2).size() - mContinentMap.get(o1).size();
                    if (delta != 0) {
                        return delta;
                    }
                    return o1.compareTo(o2);
                }
            }
        });

        int level = 1;

        if (INCLUDE_TIMEZONE_COMMENTS) {
            indent(mOutput, level);
            mOutput.append("@SuppressWarnings(\"SpellCheckingInspection\")\n");
        }
        indent(mOutput, level);
        mOutput.append("private static int getTimeZoneRegionIndex(@NonNull TimeZone zone) {\n");
        level++;

        indent(mOutput, level);
        mOutput.append("// Instead of String#hashCode, use this to ensure stable across platforms\n");
        indent(mOutput, level);
        mOutput.append("String id = zone.getID();\n");
        indent(mOutput, level);
        mOutput.append("int hashedId = 0;\n");
        indent(mOutput, level);
        mOutput.append("for (int i = 0, n = id.length(); i < n; i++) {\n");
        indent(mOutput, level + 1);
        mOutput.append("hashedId = 31 * hashedId + id.charAt(i);\n");
        indent(mOutput, level);
        mOutput.append("}\n");

        indent(mOutput, level);
        mOutput.append("switch (zone.getRawOffset()) {\n");

        Multimap<Integer,TimeZone> offsetMap = ArrayListMultimap.create();
        for (TimeZone zone : mZoneCandidates) {
            int rawOffset = zone.getRawOffset();
            offsetMap.put(rawOffset, zone);
        }
        makeJdkTimezoneCorrections(offsetMap);

        level++;
        for (Integer offset : sorted(offsetMap.keySet())) {
            indent(mOutput, level);
            mOutput.append("case ").append(offset).append(":");

            if (INCLUDE_TIMEZONE_COMMENTS) {
                mOutput.append(" // ");
                mOutput.append(String.valueOf(offset / 3600000.0));
                mOutput.append(" hours");
            }
            mOutput.append("\n");

            generateZones(level + 1, offsetMap.get(offset));
        }
        level--;

        indent(mOutput, level);
        mOutput.append("}\n");

        indent(mOutput, level);
        mOutput.append("return -1;\n");
        level--;
        indent(mOutput, level);
        mOutput.append("}\n");

        return mOutput.toString();
    }

    private void makeJdkTimezoneCorrections(Multimap<Integer,TimeZone> offsetMap) {
        // There have been some timezone corrections; since our switch-based lookup is
        // based on offsets, we should list these corrected timezones in both switch
        // blocks.
        //
        // For example, in JDK 6, the timezone "America/Rio_Branco" had rawOffset -14400000,
        // and in JDK 8 it's -18000000. Therefore, we should have switch statements for
        // both.

        // JDK 1.6 to JDK 1.7:
        correctOffset(offsetMap, "Africa/Tripoli", 3600000, 7200000);
        correctOffset(offsetMap, "Libya", 3600000, 7200000);
        correctOffset(offsetMap, "America/Argentina/San_Luis", -14400000, -10800000);
        correctOffset(offsetMap, "America/Eirunepe", -14400000, -10800000);
        correctOffset(offsetMap, "America/Porto_Acre", -14400000, -10800000);
        correctOffset(offsetMap, "America/Rio_Branco", -14400000, -10800000);
        correctOffset(offsetMap, "Brazil/Acre", -14400000, -10800000);
        //correctOffset(offsetMap, "Pacific/Fakaofo", 50400000, 46800000);

        // JDK 1.7 to JDK 1.8:
        correctOffset(offsetMap, "Europe/Simferopol", 7200000, 14400000);
        //correctId(offsetMap, "Asia/Riyadh87", "Mideast/Riyadh87", 11224000);
        //correctId(offsetMap, "Asia/Riyadh88", "Mideast/Riyadh88", 11224000);
        //correctId(offsetMap, "Asia/Riyadh89", "Mideast/Riyadh89", 11224000);

        // JDK 1.8 to JDK 1. EA:
        //correctId(offsetMap, null, "Asia/Chita", 28800000);
        //correctId(offsetMap, null, "Asia/Srednekolymsk", 39600000);
        //correctId(offsetMap, null, "Pacific/Bougainville", 39600000);
        correctOffset(offsetMap, "Asia/Irkutsk", 32400000, 28800000);
        correctOffset(offsetMap, "Asia/Kashgar", 28800000, 21600000);
        correctOffset(offsetMap, "Asia/Khandyga", 36000000, 32400000);
        correctOffset(offsetMap, "Asia/Krasnoyarsk", 28800000, 25200000);
        correctOffset(offsetMap, "Asia/Magadan", 43200000, 36000000);
        correctOffset(offsetMap, "Asia/Novosibirsk", 25200000, 21600000);
        correctOffset(offsetMap, "Asia/Omsk", 25200000, 21600000);
        correctOffset(offsetMap, "Asia/Sakhalin", 39600000, 36000000);
        correctOffset(offsetMap, "Asia/Urumqi", 28800000, 21600000);
        correctOffset(offsetMap, "Asia/Ust-Nera", 39600000, 36000000);
        correctOffset(offsetMap, "Asia/Vladivostok", 39600000, 36000000);
        correctOffset(offsetMap, "Asia/Yakutsk", 36000000, 32400000);
        correctOffset(offsetMap, "Asia/Yekaterinburg", 21600000, 18000000);
        correctOffset(offsetMap, "Europe/Kaliningrad", 10800000, 7200000);
        correctOffset(offsetMap, "Europe/Moscow", 14400000, 10800000);
        correctOffset(offsetMap, "Europe/Simferopol", 14400000, 10800000);
        correctOffset(offsetMap, "Europe/Volgograd", 14400000, 10800000);
        correctOffset(offsetMap, "W-SU", 14400000, 10800000);

        correctRegion(offsetMap, "Europe/Simferopol", 7200000, 14400000, 10800000, "");
    }

    private void correctOffset(
            Multimap<Integer, TimeZone> offsetMap,
            String id,
            int rawOffset1,
            int rawOffset2) {
        TimeZone old = findZone(id);
        if (old == null) {
            // Not relevant for our usage
            return;
        }
        TimeZone zone1 = findZone(offsetMap, id, rawOffset1);
        if (zone1 == null) {
            addZone(offsetMap, id, rawOffset1, old);
        }
        TimeZone zone2 = findZone(offsetMap, id, rawOffset2);
        if (zone2 == null) {
            addZone(offsetMap, id, rawOffset2, old);
        }
    }

    private void correctRegion(
            Multimap<Integer, TimeZone> offsetMap,
            String id,
            int rawOffset1,
            int rawOffset2,
            int rawOffset3,
            String region) {
        TimeZone zone1 = findZone(offsetMap, id, rawOffset1);
        if (zone1 != null) {
            mZoneToRegion.put(zone1, region);
        }
        TimeZone zone2 = findZone(offsetMap, id, rawOffset2);
        if (zone2 != null) {
            mZoneToRegion.put(zone2, region);
        }
        TimeZone zone3 = findZone(offsetMap, id, rawOffset3);
        if (zone3 != null) {
            mZoneToRegion.put(zone3, region);
        }
    }

    private void addZone(Multimap<Integer, TimeZone> offsetMap, String id, int rawOffset,
            TimeZone old) {
        TimeZone zone = new SimpleTimeZone(rawOffset, id);
        offsetMap.put(rawOffset, zone);
        mZoneCandidates.add(zone);
        mZoneToRegion.put(zone, mZoneToRegion.get(old));
    }

    private void correctId(
            Multimap<Integer, TimeZone> offsetMap,
            String id1,
            String id2,
            int rawOffset) {
        if (id1 != null) {
            TimeZone zone1 = findZone(offsetMap, id1, rawOffset);
            if (zone1 == null && id2 != null) {
                TimeZone old = findZone(id2);
                addZone(offsetMap, id1, rawOffset, old);
            }
        }
        if (id2 != null) {
            TimeZone zone2 = findZone(offsetMap, id2, rawOffset);
            if (zone2 == null && id1 != null) {
                TimeZone old = findZone(id1);
                addZone(offsetMap, id2, rawOffset, old);
            }
        }
    }

    private TimeZone findZone(String id) {
        for (TimeZone zone : mZoneCandidates) {
            if (zone.getID().equals(id)) {
                return zone;
            }
        }

        return null;
    }

    private static TimeZone findZone(Multimap<Integer, TimeZone> offsetMap,
            String id, int rawOffset) {
        Collection<TimeZone> zones = offsetMap.get(rawOffset);
        if (zones == null) {
            return null;
        }

        for (TimeZone zone : zones) {
            if (id.equals(zone.getID())) {
                return zone;
            }
        }

        return null;
    }

    private void generateZones(int level, Collection<TimeZone> zones) {
        assert !zones.isEmpty();

        // See if they all map to the same region
        boolean regionsDiffer = false;
        String sameRegion = mZoneToRegion.get(zones.iterator().next());
        for (TimeZone zone : zones) {
            String region = mZoneToRegion.get(zone);
            if (!sameRegion.equals(region)) {
                regionsDiffer = true;
                break;
            }
        }
        if (!regionsDiffer) {
            returnRegion(zones, level);
            return;
        }

        indent(mOutput, level);
        mOutput.append("switch (hashedId) {\n");
        level++;

        Map<Integer,TimeZone> hashCodes = Maps.newHashMap();
        List<TimeZone> sorted = sortByRegion(zones);
        String lastRegion = mZoneToRegion.get(sorted.get(zones.size() - 1));
        for (int i = 0, n = sorted.size(); i < n; i++) {
            TimeZone zone = sorted.get(i);
            int hash = hashedId(zone);
            if (hashCodes.containsKey(hash)) {
                System.err.println("Timezones clash: same hash " + hash + " for "
                        + zone.getID() + " and "
                        + hashCodes.get(hash));
                System.exit(-1);
            }

            String region = mZoneToRegion.get(zone);

            if (i < n - 1 && region.equals(lastRegion)) {
                // TODO: Combine into a list instead
                if (INCLUDE_TIMEZONE_COMMENTS) {
                    indent(mOutput, level);
                    pad(7);
                    mOutput.append("// ").append(escape(zone.getID()));
                    mOutput.append("\n");
                }
                continue;
            } else if (i == n - 1) {
                indent(mOutput, level);
                mOutput.append("default:\n");
            } else {
                indent(mOutput, level);
                mOutput.append("case ").append(hash).append(":");
                String text = String.valueOf(hash);
                pad(text);

                if (INCLUDE_TIMEZONE_COMMENTS) {
                    mOutput.append(" // ").append(escape(zone.getID()));
                }
                mOutput.append("\n");
            }
            if (i < n - 1 && region.equals(mZoneToRegion.get(sorted.get(i + 1)))) {
                // Don't return each one; share a single body when the regions are the same
                continue;
            }
            returnRegion(zone, level + 1);
        }

        level--;
        indent(mOutput, level);
        mOutput.append("}\n");
    }

    private void pad(int space) {
        int padding = PADDING + space;
        for (int j = 0; j < padding; j++) {
            mOutput.append(' ');
        }
    }

    private void pad(String text) {
        int padding = PADDING - text.length();
        for (int j = 0; j < padding; j++) {
            mOutput.append(' ');
        }
    }

    private void returnRegion(Collection<TimeZone> zones, int level) {
        String region = mZoneToRegion.get(zones.iterator().next());
        List<String> ids = Lists.newArrayList();
        for (TimeZone zone : zones) {
            ids.add(zone.getID());
            assert region.equals(mZoneToRegion.get(zone)) : zone + " vs " + region;
        }
        indent(mOutput, level);
        if (region.isEmpty()) {
            mOutput.append("return -1;\n");
            return;
        }
        int index = mRegion3Index.get(getIso3Region(region));
        mOutput.append("return ").append(index).append(";");
        if (INCLUDE_TIMEZONE_COMMENTS) {
            pad(-9);
            mOutput.append("// ").append(escape(mRegionName.get(getIso3Region(region))));
        }
        mOutput.append("\n");
    }

    private void returnRegion(TimeZone zone, int level) {
        returnRegion(Collections.singletonList(zone), level);
    }
}
