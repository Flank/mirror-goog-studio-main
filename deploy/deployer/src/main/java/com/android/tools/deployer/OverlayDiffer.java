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
import com.android.tools.deployer.model.FileDiff;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class that performs a configurable overlay diff between a newly built set of APKs and the current
 * state of an existing overlay. The diff generates a set of files to extract from the APK and push
 * to the device, and a set of files currently on device that should be deleted.
 *
 * <p>The diff throws an exception if the new set of APKs is missing a file that was contained in
 * the base installation of the overlay, or if a changed file has an unsupported type. The set of
 * supported file types is configurable.
 */
class OverlayDiffer {
    public static class Result {
        public final Collection<ApkEntry> filesToAdd;
        public final Collection<String> filesToRemove;

        private Result(Collection<ApkEntry> filesToAdd, Collection<String> filesToRemove) {
            this.filesToAdd = filesToAdd;
            this.filesToRemove = filesToRemove;
        }
    }

    private final EnumSet<ChangeType> supportedChanges;

    public OverlayDiffer(EnumSet<ChangeType> supportedChanges) {
        this.supportedChanges = supportedChanges;
    }

    /**
     * Compares a list of built APKs to a list of installed APKs to produce a collection of updates
     * that will allow the overlay to reflect the delta between the new APKs and the installed APKs.
     *
     * @param newApks the APKs being deployed to the device
     * @param overlayId the overlay info object containing the APKs that are currently installed on
     *     the device and the contents of the current overlay on the device
     * @return a {@link Result} object describing the files that must be extracted from APKs and
     *     pushed to the overlay, as well as the files that must be deleted from the overlay.
     * @throws DeployerException if a change cannot be supported by overlay update
     */
    public Result diff(List<Apk> newApks, OverlayId overlayId) throws DeployerException {
        return new Result(
                getFilesToAdd(
                        newApks, overlayId.getInstalledApks(), overlayId.getOverlayContents()),
                getFilesToRemove(newApks, overlayId.getOverlayContents()));
    }

    // Any files in the new APKs that aren't in the base APKs and are not already in the overlay
    // must be added to the overlay.
    private Collection<ApkEntry> getFilesToAdd(
            List<Apk> newApks, List<Apk> installedApks, OverlayId.Contents overlayContents)
            throws DeployerException {
        ArrayList<ApkEntry> files = new ArrayList<>();
        for (FileDiff diff : new ApkDiffer().diff(installedApks, newApks)) {
            // Ensure that we currently have IWI support enabled for every change; otherwise, throw
            // an exception to cause a fallback to delta install.
            ChangeType type = ChangeType.getType(diff);
            if (!supportedChanges.contains(type)) {
                throw DeployerException.changeNotSupportedByIWI(type);
            }
            // IWI can't currently handle deleting files that are in the base APK.
            if (diff.status == FileDiff.Status.DELETED) {
                throw DeployerException.deleteInstalledFileNotSupported();
            }

            String qualifiedPath = diff.newFile.getQualifiedPath();
            if (diff.newFile.getChecksum() != overlayContents.getFileChecksum(qualifiedPath)) {
                files.add(diff.newFile);
            }
        }
        return files;
    }

    // Any files in the overlay that are NOT in the new APK must be deleted from the overlay. This
    // handles removing any "extra" files, such as the ones added by IWI swap.
    private static Collection<String> getFilesToRemove(
            List<Apk> newApks, OverlayId.Contents overlayContents) {
        Set<String> files = new HashSet<>(overlayContents.allFiles());
        newApks.stream()
                .flatMap(apk -> apk.apkEntries.values().stream())
                .map(ApkEntry::getQualifiedPath)
                .forEach(files::remove);
        return files;
    }
}
