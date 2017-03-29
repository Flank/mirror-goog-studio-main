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
package com.android.repository.testframework;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.Channel;
import com.android.repository.api.Dependency;
import com.android.repository.api.License;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.RepositorySource;
import com.android.repository.impl.meta.Archive;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.RepoPackageImpl;
import com.android.repository.impl.meta.TypeDetails;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.Comparator;

/**
 * A fake {@link RepoPackage} for use in unit tests, that contains some behavior (e.g. around
 * {@link FakeRemotePackage.FakeArchive}) to make it a little easier to work with in some situations
 * than a plain mock would be.
 *
 * Probably you want {@link FakeLocalPackage} or {@link FakeRemotePackage}.
 */
@SuppressWarnings("ConstantConditions")
public abstract class FakePackage implements RepoPackage {
    /**
     * A fake {@link LocalPackage} for use in unit tests.
     */
    public static class FakeLocalPackage extends FakePackage implements LocalPackage {
        private File mLocation;

        @Override
        @NonNull
        public File getLocation() {
            return mLocation;
        }

        @Override
        public void setInstalledPath(@NonNull File root) {
            mLocation = root;
        }

        public FakeLocalPackage(@NonNull String path) {
            super(path);
            String packagePath = getPath().replace(RepoPackage.PATH_SEPARATOR, File.separatorChar);
            mLocation = new File("/sdk", packagePath);
        }

        @Override
        public int compareTo(@NonNull RepoPackage other) {
            return super.compareTo(other);
        }
    }

    /**
     * A fake {@link RemotePackage} for use in unit tests.
     */
    public static class FakeRemotePackage extends FakePackage implements RemotePackage {
        private FakeArchive mArchive = new FakeArchive();
        private Channel mChannel;

        public FakeRemotePackage(@NonNull String path) {
            super(path);
        }

        public void setCompleteUrl(String url) {
            mArchive.setCompleteUrl(url);
        }

        public void setPatchInfo(String url, Revision src) {
            mArchive.setPatchInfo(url, src);
        }

        @Override
        @Nullable
        public Archive getArchive() {
            return mArchive;
        }

        @Override
        @NonNull
        public RepositorySource getSource() {
            return null;
        }

        public void setChannel(Channel channel) {
            mChannel = channel;
        }

        @Override
        public void setSource(@NonNull RepositorySource source) {
        }

        @Override
        @NonNull
        public File getInstallDir(@NonNull RepoManager manager, @NonNull ProgressIndicator progress) {
            return new File(manager.getLocalPath(),
                    getPath().replace(RepoPackage.PATH_SEPARATOR, File.separatorChar));
        }

        @Override
        @NonNull
        public Channel getChannel() {
            return mChannel == null ? Channel.DEFAULT : mChannel;
        }

        @Override
        public int compareTo(@NonNull RepoPackage other) {
            return super.compareTo(other);
        }

        private static class FakeArchive extends Archive {

            private String mCompleteUrl;
            private String mPatchUrl;
            private Revision mPatchSrc;

            public void setCompleteUrl(String url) {
                mCompleteUrl = url;
            }

            public void setPatchInfo(String url, Revision src) {
                mPatchUrl = url;
                mPatchSrc = src;
            }

            @NonNull
            @Override
            public CompleteType getComplete() {
                if (mCompleteUrl != null) {
                    CompleteType result = createFactory().createCompleteType();
                    result.setUrl(mCompleteUrl);
                    return result;
                }
                return null;
            }

            @Nullable
            @Override
            protected PatchesType getPatches() {
                if (mPatchUrl != null) {
                    PatchType patch = createFactory().createPatchType();
                    patch.setBasedOn(createFactory().createRevisionType(mPatchSrc));
                    patch.setUrl(mPatchUrl);
                    PatchesType result = createFactory().createPatchesType();
                    result.getPatch().add(patch);
                    return result;
                }
                return null;
            }

            @NonNull
            @Override
            public CommonFactory createFactory() {
                return RepoManager.getCommonModule().createLatestFactory();
            }
        }
    }

    private final String mPath;
    private Revision mVersion = new Revision(1);
    private Collection<Dependency> mDependencies = ImmutableList.of();
    private TypeDetails mDetails;
    private String mDisplayName = "fake package";
    private License mLicense;
    private boolean mObsolete;

    public FakePackage(@NonNull String path) {
        mPath = path;
    }

    public void setTypeDetails(TypeDetails details) {
        mDetails = details;
    }

    @Override
    @NonNull
    public TypeDetails getTypeDetails() {
        return mDetails == null ? (TypeDetails) RepoManager.getGenericModule()
                .createLatestFactory().createGenericDetailsType() : mDetails;
    }

    @Override
    @NonNull
    public Revision getVersion() {
        return mVersion;
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return mDisplayName;
    }

    public void setDisplayName(@NonNull String displayName) {
        mDisplayName = displayName;
    }

    @Override
    @Nullable
    public License getLicense() {
        return mLicense;
    }

    public void setLicense(@Nullable License license) {
        mLicense = license;
    }

    @Override
    @NonNull
    public Collection<Dependency> getAllDependencies() {
        return mDependencies;
    }

    @Override
    @NonNull
    public String getPath() {
        return mPath;
    }

    @Override
    public boolean obsolete() {
        return mObsolete;
    }

    public void setObsolete(boolean obsolete) {
        mObsolete = obsolete;
    }

    @Override
    @NonNull
    public CommonFactory createFactory() {
        return RepoManager.getCommonModule().createLatestFactory();
    }

    @Override
    @NonNull
    public RepoPackageImpl asMarshallable() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(@NonNull RepoPackage other) {
        return Comparator.comparing(RepoPackage::getPath)
          .thenComparing(RepoPackage::getVersion)
          .thenComparing(repoPackage -> repoPackage instanceof LocalPackage)
          .compare(this, other);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof RepoPackage && ((RepoPackage) obj).getPath().equals(getPath())
                && ((RepoPackage) obj).getVersion().equals(getVersion());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getPath(), getVersion());
    }

    @Override
    public String toString() {
        return mPath;
    }

    public void setDependencies(@NonNull Collection<Dependency> dependencies) {
        mDependencies = dependencies;
    }

    public void setRevision(@NonNull Revision revision) {
        mVersion = revision;
    }

}
