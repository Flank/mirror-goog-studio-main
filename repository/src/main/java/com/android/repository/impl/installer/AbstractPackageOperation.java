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

package com.android.repository.impl.installer;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.DelegatingProgressIndicator;
import com.android.repository.api.Installer;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.repository.api.Uninstaller;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.repository.util.InstallerUtil;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Frameworks for concrete {@link Installer}s and {@link Uninstaller}s that manages creation of temp
 * directories, writing package metadata and install status, and resuming in-progress installs.
 */
public abstract class AbstractPackageOperation implements PackageOperation {

    /**
     * Key used in the properties file for the temporary path.
     */
    private static final String PATH_KEY = "path";

    /**
     * The concrete type of the installer. TODO: do we actually need this?
     */
    private static final String CLASSNAME_KEY = "class";

    /**
     * Name of the marker file that's written into the temporary directory when the prepare phase
     * has completed successfully.
     */
    private static final String PREPARE_COMPLETE_FN = ".prepareComplete";

    /**
     * Name of the directory created in the final install location containing data to get the
     * install restarted if it stops.
     */
    private static final String INSTALL_DATA_FN = ".installData";

    /**
     * Prefix used when creating temporary directories.
     */
    static final String TEMP_DIR_PREFIX = "PackageOperation";

    /**
     * Status of the installer.
     */
    private InstallStatus mInstallStatus = InstallStatus.NOT_STARTED;

    /**
     * Properties written to the final install folder, used to restart the installer if needed.
     */
    private Properties mInstallProperties;

    private PackageOperation mFallbackOperation;

    private final Object mStateChangeLock = new Object();

    private enum StartTaskStatus {STARTED, ALREADY_DONE, FAILED}

    /**
     * Listeners that will be notified when the status changes.
     */
    private List<StatusChangeListener> mListeners = Lists.newArrayList();

    private final RepoManager mRepoManager;

    protected final FileOp mFop;

    private DelegatingProgressIndicator mProgress;

    protected AbstractPackageOperation(@NonNull RepoManager repoManager, @NonNull FileOp fop) {
        mRepoManager = repoManager;
        mFop = fop;
    }

    /**
     * Subclasses should override this to prepare a package for (un)installation, including
     * downloading, unzipping, etc. as needed. No modification to the actual SDK should happen
     * during this time.
     *
     * @param installTempPath The dir that should be used for any intermediate processing.
     * @param progress        For logging and progress display
     */
    protected abstract boolean doPrepare(@NonNull File installTempPath,
            @NonNull ProgressIndicator progress);

    /**
     * Subclasses should implement this to do any install/uninstall completion actions required.
     *
     * @param installTemp The temporary dir in which we prepared the (un)install. May be {@code
     *                    null} if for example the installer removed the installer properties file,
     *                    but should not be normally.
     * @param progress    For logging and progress indication.
     * @return {@code true} if the operation succeeded, {@code false} otherwise.
     * @see #complete(ProgressIndicator)
     */
    protected abstract boolean doComplete(@Nullable File installTemp,
            @NonNull ProgressIndicator progress);

    /**
     * Finds the prepared files using the installer metadata, and calls {@link
     * #doComplete(File, ProgressIndicator)}.
     *
     * @param progress A {@link ProgressIndicator}, to show install progress and facilitate
     *                 logging.
     * @return {@code true} if the install was successful, {@code false} otherwise.
     */
    @Override
    public final boolean complete(@NonNull ProgressIndicator progress) {
        addProgress(progress);
        StartTaskStatus startResult = startTask(InstallStatus.RUNNING);
        if (startResult != StartTaskStatus.STARTED) {
            return startResult == StartTaskStatus.ALREADY_DONE;
        }
        if (mInstallProperties == null) {
            try {
                mInstallProperties = readInstallProperties(mFop.toPath(getLocation(mProgress)));
            } catch (IOException e) {
                // We won't have a temp path, but try to continue anyway
            }
        }
        boolean result = false;
        String installTempPath = null;
        if (mInstallProperties != null) {
            installTempPath = mInstallProperties.getProperty(PATH_KEY);
        }
        File installTemp = installTempPath == null ? null : new File(installTempPath);
        try {
            // Re-validate the install path, in case something was changed since prepare.
            if (!InstallerUtil
                    .checkValidPath(getLocation(mProgress), getRepoManager(), mProgress)) {
                return false;
            }

            result = doComplete(installTemp, mProgress);
            mProgress.logInfo(String.format("\"%1$s\" complete.", getName()));
        } finally {
            if (!result && mProgress.isCanceled()) {
                cleanup();
            }
            result &= updateStatus(result ? InstallStatus.COMPLETE : InstallStatus.FAILED);
            if (result && installTemp != null) {
                mFop.deleteFileOrFolder(installTemp);
            }
            getRepoManager().installEnded(getPackage());
            getRepoManager().markLocalCacheInvalid();
        }

        mProgress.setFraction(1);
        mProgress.setIndeterminate(false);
        mProgress.logInfo(
                String.format("\"%1$s\" %2$s.", getName(), result ? "finished" : "failed"));
        return result;
    }

    @NonNull
    private StartTaskStatus startTask(@NonNull InstallStatus inProgress) {
        boolean alreadyStarted = false;
        CompletableFuture<Void> f = new CompletableFuture<>();
        synchronized (mStateChangeLock) {
            if (mInstallStatus == InstallStatus.FAILED) {
                return StartTaskStatus.FAILED;
            } else if (mInstallStatus.compareTo(inProgress) > 0) {
                return StartTaskStatus.ALREADY_DONE;
            } else if (mInstallStatus == inProgress) {
                registerStateChangeListener((op, p) -> {
                    // Complete only if we've moved on. Since the listeners are retrieved outside
                    // this synchronized block, it's possible for the update to be to the current
                    // inProgress state rather than away from it.
                    if (op.getInstallStatus().compareTo(inProgress) > 0) {
                        f.complete(null);
                    }
                });
                alreadyStarted = true;
            } else {
                // Don't use updateStatus here, since we don't want the listeners to fire in the
                // synchronized block.
                mInstallStatus = inProgress;
            }
        }
        boolean success;
        if (alreadyStarted) {
            // Method isn't expected to return while task is in process. Wait for existing one.
            try {
                f.get();
                success = getInstallStatus() != InstallStatus.FAILED;
            } catch (InterruptedException | ExecutionException e) {
                // Shouldn't happen, but if it does consider us to be failed.
                success = false;
            }
        } else {
            // Now fire the listeners for actually starting
            success = updateStatus(inProgress);
        }
        if (!success) {
            mProgress.setFraction(1);
            mProgress.setIndeterminate(false);
            mProgress.logInfo(String.format("\"%1$s\" failed.", getName()));
            return StartTaskStatus.FAILED;
        }
        return alreadyStarted ? StartTaskStatus.ALREADY_DONE : StartTaskStatus.STARTED;
    }

    /**
     * Looks in {@code installPath} for an install properties file and returns the contents if
     * found.
     */
    @Nullable
    private static Properties readInstallProperties(@NonNull Path installPath) throws IOException {
        Path metaDir = installPath.resolve(InstallerUtil.INSTALLER_DIR_FN);
        Path dataFile = metaDir.resolve(INSTALL_DATA_FN);

        if (Files.exists(dataFile)) {
            Properties installProperties = new Properties();
            try (InputStream inStream = Files.newInputStream(dataFile)) {
                installProperties.load(inStream);
                return installProperties;
            }
        }
        return null;
    }

    private void cleanup() {
        mFop.deleteFileOrFolder(new File(getLocation(mProgress), InstallerUtil.INSTALLER_DIR_FN));
    }

    /**
     * Writes information used to restore the operation state if needed, then calls {@link
     * #doPrepare(File, ProgressIndicator)}
     *
     * @param progress A {@link ProgressIndicator}, to show progress and facilitate logging.
     * @return {@code true} if the operation succeeded, {@code false} otherwise.
     */
    @Override
    public final boolean prepare(@NonNull ProgressIndicator progress) {
        addProgress(progress);
        StartTaskStatus startResult = startTask(InstallStatus.PREPARING);
        if (startResult != StartTaskStatus.STARTED) {
            return startResult == StartTaskStatus.ALREADY_DONE;
        }

        mProgress.logInfo(String.format("Preparing \"%1$s\".", getName()));
        try {
            File dest = getLocation(mProgress);

            mInstallProperties = readOrCreateInstallProperties(dest);
        } catch (IOException e) {
            mProgress.logWarning("Failed to read or create install properties file.");
            return false;
        }
        getRepoManager().installBeginning(getPackage(), this);
        boolean result = false;
        try {
            if (!InstallerUtil
                    .checkValidPath(getLocation(mProgress), getRepoManager(), mProgress)) {
                return false;
            }

            File installTempPath = writeInstallerMetadata();
            if (installTempPath == null) {
                mProgress.logInfo(String.format("\"%1$s\" failed.", getName()));
                return false;
            }
            File prepareCompleteMarker = new File(installTempPath, PREPARE_COMPLETE_FN);
            if (!mFop.exists(prepareCompleteMarker)) {
                if (doPrepare(installTempPath, mProgress)) {
                    mFop.createNewFile(prepareCompleteMarker);
                    result = updateStatus(InstallStatus.PREPARED);
                }
            } else {
                mProgress.logInfo("Found existing prepared package.");
                result = true;
            }
        } catch (IOException e) {
            result = false;
        } finally {
            if (!result) {
                getRepoManager().installEnded(getPackage());
                updateStatus(InstallStatus.FAILED);
                // If there was a failure don't clean up the files, so we can continue if requested
                if (mProgress.isCanceled()) {
                    cleanup();
                }
            }
        }
        mProgress.logInfo(String.format("\"%1$s\" %2$s.", getName(), result ? "ready" : "failed"));
        return result;
    }

    /**
     * Looks in {@code affectedPath} for an install properties file and returns the contents if
     * found. If not found, creates and populates it.
     *
     * @param affectedPath The path on which this operation acts (either to write to or delete
     *                     from)
     * @return The read or created properties.
     */
    @NonNull
    private Properties readOrCreateInstallProperties(@NonNull File affectedPath)
            throws IOException {
        Properties installProperties = readInstallProperties(mFop.toPath(affectedPath));
        if (installProperties != null) {
            return installProperties;
        }
        installProperties = new Properties();

        File metaDir = new File(affectedPath, InstallerUtil.INSTALLER_DIR_FN);
        if (!mFop.exists(metaDir)) {
            mFop.mkdirs(metaDir);
        }
        File dataFile = new File(metaDir, INSTALL_DATA_FN);
        File installTempPath = FileOpUtils.getNewTempDir(TEMP_DIR_PREFIX, mFop);
        if (installTempPath == null) {
            deleteOrphanedTempDirs();
            installTempPath = FileOpUtils.getNewTempDir(TEMP_DIR_PREFIX, mFop);
            if (installTempPath == null) {
                throw new IOException("Failed to create temp path");
            }
        }
        installProperties.put(PATH_KEY, installTempPath.getPath());
        installProperties.put(CLASSNAME_KEY, getClass().getName());
        mFop.createNewFile(dataFile);
        try (OutputStream out = mFop.newFileOutputStream(dataFile)) {
            installProperties.store(out, null);
        }
        return installProperties;
    }

    private void deleteOrphanedTempDirs() {
        Path root = mFop.toPath(mRepoManager.getLocalPath());
        Path suffixPath = mFop.toPath(new File(InstallerUtil.INSTALLER_DIR_FN, INSTALL_DATA_FN));
        try {
            Set<File> tempDirs = Files.walk(root)
                    .filter(path -> path.endsWith(suffixPath))
                    .map(this::getPathPropertiesOrNull)
                    .filter(Objects::nonNull)
                    .map(props -> props.getProperty(PATH_KEY))
                    .map(File::new)
                    .collect(Collectors.toSet());
            FileOpUtils.retainTempDirs(tempDirs, TEMP_DIR_PREFIX, mFop);
        } catch (IOException e) {
            mProgress.logWarning("Error while searching for in-use temporary directories.", e);
        }
    }

    @Nullable
    private Properties getPathPropertiesOrNull(@NonNull Path path) {
        try {
            return readInstallProperties(path.getParent().getParent());
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    private File writeInstallerMetadata()
            throws IOException {
        File installPath = getLocation(mProgress);
        Properties installProperties = readOrCreateInstallProperties(installPath);
        File installTempPath = new File((String) installProperties.get(PATH_KEY));
        if (!mFop.exists(installPath) && !mFop.mkdirs(installPath) ||
                !mFop.isDirectory(installPath)) {
            mProgress.logWarning("Failed to create output directory: " + installPath);
            return null;
        }
        mFop.deleteOnExit(installTempPath);
        return installTempPath;
    }

    @NonNull
    @Override
    public RepoManager getRepoManager() {
        return mRepoManager;
    }

    /**
     * Registers a listener that will be called when the {@link InstallStatus} of
     * this installer changes.
     */
    @Override
    public final void registerStateChangeListener(@NonNull StatusChangeListener listener) {
        synchronized (mStateChangeLock) {
            mListeners.add(listener);
        }
    }

    /**
     * Gets the current {@link InstallStatus} of this installer.
     */
    @Override
    @NonNull
    public final InstallStatus getInstallStatus() {
        return mInstallStatus;
    }

    /**
     * Sets our status to {@code status} and notifies our listeners. If any listener throws an
     * exception we will stop processing listeners and update our status to {@code
     * InstallStatus.FAILED} (calling the listeners again with that status update).
     */
    protected final boolean updateStatus(@NonNull InstallStatus status) {
        List<StatusChangeListener> listeners;
        synchronized (mStateChangeLock) {
            mInstallStatus = status;
            listeners = new ArrayList<>(mListeners);
        }
        try {
            for (StatusChangeListener listener : listeners) {
                try {
                    listener.statusChanged(this, mProgress);
                } catch (Exception e) {
                    if (status != InstallStatus.FAILED) {
                        throw e;
                    }
                    // else ignore and continue with the other listeners
                }
            }
        } catch (Exception e) {
            mProgress.logError("Failed to update status to " + status, e);
            updateStatus(InstallStatus.FAILED);
            return false;
        }
        return true;
    }


    @Override
    @Nullable
    public PackageOperation getFallbackOperation() {
        return mFallbackOperation;
    }

    @Override
    public void setFallbackOperation(@Nullable PackageOperation mFallbackOperation) {
        this.mFallbackOperation = mFallbackOperation;
    }

    private final Object mProgressLock = new Object();

    private void addProgress(@NonNull ProgressIndicator progress) {
        synchronized (mProgressLock) {
            if (mProgress == null) {
                mProgress = new DelegatingProgressIndicator(progress);
            } else {
                mProgress.addDelegate(progress);
            }
        }
    }
}

