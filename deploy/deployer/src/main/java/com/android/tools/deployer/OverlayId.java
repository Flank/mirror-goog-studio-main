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
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.DexClass;
import com.google.common.collect.ImmutableSortedMap;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
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

    // overlay file to checksum map.
    private final SortedMap<String, String> apks; // Intalled Apk -> Digest
    private final SortedMap<String, String> deltas; // OverlayFile -> Checksum
    private final String sha;

    // Maintain a list of the swapped dex in this overlay. This collection is not used when
    // computing the SHA.
    private final List<String> swappedDex;

    // Distinguish between a "true" base install and an install that has OID but zero overlay file.
    private final boolean baseInstall;

    public OverlayId(
            OverlayId prevOverlayId,
            DexComparator.ChangedClasses dexOverlays,
            Set<ApkEntry> fileOverlays)
            throws DeployerException {
        apks = prevOverlayId.apks;
        deltas = new TreeMap<>(prevOverlayId.deltas);
        swappedDex = new ArrayList<>(prevOverlayId.swappedDex);
        for (DexClass cls : dexOverlays.newClasses) {
            String name = String.format("%s.dex", cls.name);
            deltas.put(name, String.format("%d", cls.checksum));
            swappedDex.add(name);
        }
        for (DexClass cls : dexOverlays.modifiedClasses) {
            String name = String.format("%s.dex", cls.name);
            deltas.put(name, String.format("%d", cls.checksum));
            swappedDex.add(name);
        }
        fileOverlays.forEach(
                file ->
                        deltas.put(
                                file.getQualifiedPath(), String.format("%d", file.getChecksum())));
        sha = computeShaHex(getRepresentation());
        baseInstall = false;
    }

    public OverlayId(OverlayId prevOverlayId, Set<ApkEntry> fileOverlays) throws DeployerException {
        apks = prevOverlayId.apks;
        deltas = new TreeMap<>(prevOverlayId.deltas);
        swappedDex = Collections.emptyList();
        fileOverlays.forEach(
                file -> deltas.put(file.getQualifiedPath(), format("%d", file.getChecksum())));
        sha = computeShaHex(getRepresentation());
        baseInstall = false;
    }

    public OverlayId(List<Apk> installedApk) throws DeployerException {
        apks = new TreeMap<>();
        deltas = ImmutableSortedMap.of();
        swappedDex = Collections.emptyList();
        installedApk.forEach(apk -> apks.put(apk.name, apk.checksum));
        this.sha = computeShaHex(getRepresentation());
        this.baseInstall = true;
    }

    public List<String> getSwappedDex() {
        return swappedDex;
    }

    public Set<String> getOverlayFiles() {
        return deltas.keySet();
    }

    public String getRepresentation() {
        StringBuilder rep = new StringBuilder();
        rep.append("Apply Changes Overlay ID\n");
        rep.append("Schema Version ");
        rep.append(SCHEMA_VERSION);
        rep.append("\n");

        for (Map.Entry<String, String> apk : apks.entrySet()) {
            rep.append(
                    String.format(
                            "Real APK %s has checksum of %s\n", apk.getKey(), apk.getValue()));
        }

        for (Map.Entry<String, String> delta : deltas.entrySet()) {
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

    private static String format(String format, Object arg) {
        return String.format(Locale.US, format, arg);
    }
}
