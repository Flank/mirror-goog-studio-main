/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.deployer;

import com.android.tools.deployer.model.Apk;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * From design doc:
 *
 * <p>oid = sha1( sha1(installer base.apk), sha1(installed spilt.apk), sha1(dex1), sha1(dex2), ...).
 */
public class OverlayId implements Serializable {

    // Hash a version number as part of the ID. This is to make sure the OID file on the device is
    // computed the same way as the current Studio and not by a version of Studio that computes
    // this differently. This is also strict string comparison with no backward capability.
    public static final String SCHEMA_VERSION = "1.0";

    // The installed APKs backing this overlay.
    private final ImmutableList<Apk> installedApks;

    // A mapping from each file in the overlay to its computed checksum. Each key value is the
    // file's full path within the overlay.
    private final ImmutableSortedMap<String, String> overlayFiles;

    // The set of overlay files that are swapped dex classes from Apply Changes or Apply Code
    // Changes deployments. All files in this set are also keys in the overlayFiles map.
    private final ImmutableSortedSet<String> swappedDex;

    private final String sha;

    // Distinguish between a "true" base install and an install that has OID but zero overlay file.
    private final boolean baseInstall;

    public OverlayId(List<Apk> apks) throws DeployerException {
        installedApks = ImmutableList.sortedCopyOf(Comparator.comparing(apk -> apk.name), apks);
        overlayFiles = ImmutableSortedMap.of();
        swappedDex = ImmutableSortedSet.of();
        sha = computeShaHex(getRepresentation());
        baseInstall = true;
    }

    private OverlayId(
            ImmutableList<Apk> installedApks,
            ImmutableSortedMap<String, String> overlayFiles,
            ImmutableSortedSet<String> swappedDex)
            throws DeployerException {
        this.installedApks = installedApks;
        this.overlayFiles = overlayFiles;
        this.swappedDex = swappedDex;
        sha = computeShaHex(getRepresentation());
        baseInstall = false;
    }

    public List<Apk> getInstalledApks() {
        return installedApks;
    }

    public Set<String> getOverlayFiles() {
        return overlayFiles.keySet();
    }

    public Set<String> getSwappedDexFiles() {
        return swappedDex;
    }

    public String getRepresentation() {
        StringBuilder rep = new StringBuilder();
        rep.append("Apply Changes Overlay ID\n");
        rep.append("Schema Version ");
        rep.append(SCHEMA_VERSION);
        rep.append("\n");

        for (Apk apk : installedApks) {
            rep.append(String.format("Real APK %s has checksum of %s\n", apk.name, apk.checksum));
        }

        for (Map.Entry<String, String> delta : overlayFiles.entrySet()) {
            rep.append(
                    String.format(
                            " Has overlayfile %s with checksum %s\n",
                            delta.getKey(), delta.getValue()));
        }
        return rep.toString();
    }

    public boolean isBaseInstall() {
        return baseInstall;
    }

    public String getSha() {
        return sha;
    }

    private static String computeShaHex(String input) throws DeployerException {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw DeployerException.operationNotSupported("SHA-256 not supported on host");
        }

        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest(input.getBytes(StandardCharsets.UTF_8))) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static Builder builder(OverlayId prevOverlayId) {
        return new Builder(prevOverlayId);
    }

    public static class Builder {
        private final ImmutableList<Apk> installedApks;
        private final SortedMap<String, String> overlayFiles;
        private final Set<String> swappedDex;

        private Builder(OverlayId prevOverlayId) {
            installedApks = prevOverlayId.installedApks;
            overlayFiles = new TreeMap<>(prevOverlayId.overlayFiles);
            swappedDex = new HashSet<>(prevOverlayId.swappedDex);
        }

        public Builder addOverlayFile(String file, long checksum) {
            overlayFiles.put(file, String.format(Locale.US, "%d", checksum));
            return this;
        }

        public Builder addSwappedDex(String name, long checksum) {
            final String file = String.format(Locale.US, "%s.dex", name);
            overlayFiles.put(file, String.format(Locale.US, "%d", checksum));
            swappedDex.add(file);
            return this;
        }

        public Builder removeOverlayFile(String file) {
            overlayFiles.remove(file);
            swappedDex.remove(file);
            return this;
        }

        public OverlayId build() throws DeployerException {
            return new OverlayId(
                    installedApks,
                    ImmutableSortedMap.copyOfSorted(overlayFiles),
                    ImmutableSortedSet.copyOf(swappedDex));
        }
    }
}
