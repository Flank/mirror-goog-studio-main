/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.ide.common.rendering;

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.Bridge;
import com.android.ide.common.rendering.api.Capability;
import com.android.ide.common.rendering.api.DrawableParams;
import com.android.ide.common.rendering.api.Features;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.Result.Status;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.sdk.LoadStatus;
import com.android.utils.ILogger;
import com.android.utils.SdkUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.android.SdkConstants.DOT_CLASS;
import static com.android.ide.common.rendering.api.Result.Status.ERROR_REFLECTION;

/**
 * Class to use the Layout library.
 * <p>
 * Use {@link #load(String, ILogger, String)} to load the jar file.
 * <p>
 * Use the layout library with:
 * {@link #init}, {@link #supports(int)}, {@link #createSession(SessionParams)},
 * {@link #dispose()}, {@link #clearCaches(Object)}.
 */
@SuppressWarnings("deprecation")
public class LayoutLibrary {

    public static final String CLASS_BRIDGE = "com.android.layoutlib.bridge.Bridge"; //$NON-NLS-1$
    public static final String FN_ICU_JAR = "icu4j.jar"; //$NON-NLS-1$

    /** Link to the layout bridge */
    private final Bridge mBridge;
    /** Status of the layoutlib.jar loading */
    private final LoadStatus mStatus;
    /** Message associated with the {@link LoadStatus}. This is mostly used when
     * {@link #getStatus()} returns {@link LoadStatus#FAILED}.
     */
    private final String mLoadMessage;
    /** classloader used to load the jar file */
    private final ClassLoader mClassLoader;

    // Reflection data for older Layout Libraries.
    private Method mViewGetParentMethod;
    private Method mViewGetBaselineMethod;
    private Method mViewParentIndexOfChildMethod;
    private Class<?> mMarginLayoutParamClass;
    private Field mLeftMarginField;
    private Field mTopMarginField;
    private Field mRightMarginField;
    private Field mBottomMarginField;

    /**
     * Returns the {@link LoadStatus} of the loading of the layoutlib jar file.
     */
    public LoadStatus getStatus() {
        return mStatus;
    }

    /** Returns the message associated with the {@link LoadStatus}. This is mostly used when
     * {@link #getStatus()} returns {@link LoadStatus#FAILED}.
     */
    public String getLoadMessage() {
        return mLoadMessage;
    }

    /**
     * Returns the classloader used to load the classes in the layoutlib jar file.
     */
    public ClassLoader getClassLoader() {
        return mClassLoader;
    }

    /**
     * Returns a {@link LayoutLibrary} instance using the given {@link Bridge} and {@link ClassLoader}
     */
    public static LayoutLibrary load(Bridge bridge, ClassLoader classLoader) {
        return new LayoutLibrary(bridge, classLoader, LoadStatus.LOADED, null);
    }

    /**
     * Returns a list of the classes contained in the given list of jar files
     */
    private static ImmutableSet<String> getJarClasses(URL[] urls) {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        for (URL url : urls) {
            ZipInputStream zipStream = null;
            try {
                zipStream = new ZipInputStream(url.openStream());
                for (ZipEntry entry = zipStream.getNextEntry(); entry != null; entry = zipStream.getNextEntry()) {
                    String name = entry.getName();
                    if (name != null && name.endsWith(DOT_CLASS)) {
                        // Transform the class file path to the class name
                        builder.add(name.substring(0, name.length() - DOT_CLASS.length()).replace("/", "."));
                    }
                }
            }
            catch (IOException e) {
                // Ignored
            }
            finally {
                if (zipStream != null) {
                    try {
                        zipStream.close();
                    }
                    catch (IOException e) {
                        // Ignored
                    }
                }
            }
        }
        return builder.build();
    }

    /**
     * Loads the layoutlib.jar file located at the given path and returns a {@link LayoutLibrary}
     * object representing the result.
     * <p>
     * If loading failed {@link #getStatus()} will reflect this, and {@link #mBridge} will
     * be null.
     *
     * @param layoutLibJarOsPath the path of the jar file
     * @param log an optional log file.
     * @return a {@link LayoutLibrary} object always.
     */
    public static LayoutLibrary load(String layoutLibJarOsPath, ILogger log, String toolName) {

        LoadStatus status = LoadStatus.LOADING;
        String message = null;
        Bridge bridge = null;
        ClassLoader classLoader = null;

        try {
            // get the URL for the file.
            File f = new File(layoutLibJarOsPath);
            if (!f.isFile()) {
                if (log != null) {
                    log.error(null, "layoutlib.jar is missing!"); //$NON-NLS-1$
                }
            } else {
                URL[] urls;
                // TODO: The icu jar has to be in the same location as layoutlib.jar. Get rid of
                // this dependency.
                File icu4j = new File(f.getParent(), FN_ICU_JAR);
                if (icu4j.isFile()) {
                    urls = new URL[2];
                    urls[1] = SdkUtils.fileToUrl(icu4j);
                } else {
                    urls = new URL[1];
                }
                urls[0] = SdkUtils.fileToUrl(f);

                final Set<String> jarClasses = Sets.filter(getJarClasses(urls), input -> {
                    // Filter kxml classes (so we use the ones in studio) and the inner classes. The inner classes will be filtered by
                    // using the parent class name.
                    return !input.startsWith("org.xmlpull.v1") && !input.contains("$");
                });
                classLoader = new FilteredClassLoader(LayoutLibrary.class.getClassLoader(), jarClasses);

                // create a class loader. Because this jar reference interfaces
                // that are in the editors plugin, it's important to provide
                // a parent class loader.
                classLoader = new URLClassLoader(urls, classLoader);

                // load the class
                Class<?> clazz = classLoader.loadClass(CLASS_BRIDGE);
                if (clazz != null) {
                    // instantiate an object of the class.
                    Constructor<?> constructor = clazz.getConstructor();
                    if (constructor != null) {
                        Object bridgeObject = constructor.newInstance();
                        if (bridgeObject instanceof Bridge) {
                            bridge = (Bridge)bridgeObject;
                        }
                    }
                }

                if (bridge == null) {
                    status = LoadStatus.FAILED;
                    message = "Failed to load " + CLASS_BRIDGE; //$NON-NLS-1$
                    if (log != null) {
                        log.error(null,
                                "Failed to load " + //$NON-NLS-1$
                                CLASS_BRIDGE +
                                " from " +          //$NON-NLS-1$
                                layoutLibJarOsPath);
                    }
                } else {
                    // mark the lib as loaded, unless it's overridden below.
                    status = LoadStatus.LOADED;

                    // check the API
                    int api = bridge.getApiLevel();
                    if (api > Bridge.API_CURRENT) {
                        status = LoadStatus.FAILED;
                        message = String.format(
                                "This version of the rendering library is more recent than your version of %1$s. Please update %1$s", toolName);
                    }
                }
            }
        } catch (Throwable t) {
            status = LoadStatus.FAILED;
            Throwable cause = t;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            message = "Failed to load the LayoutLib: " + cause.getMessage();
            // log the error.
            if (log != null) {
                log.error(t, message);
            }
        }

        return new LayoutLibrary(bridge, classLoader, status, message);
    }

    private LayoutLibrary(Bridge bridge,  ClassLoader classLoader,
                          LoadStatus status, String message) {
        mBridge = bridge;
        mClassLoader = classLoader;
        mStatus = status;
        mLoadMessage = message;
    }


    // ------ Layout Lib API proxy

    /**
     * Returns the API level of the layout library.
     */
    public int getApiLevel() {
        if (mBridge != null) {
            return mBridge.getApiLevel();
        }

        return 0;
    }

    /**
     * Returns the revision of the library inside a given (layoutlib) API level.
     * The true version number of the library is {@link #getApiLevel()}.{@link #getRevision()}
     */
    public int getRevision() {
        if (mBridge != null) {
            return mBridge.getRevision();
        }

        return 0;
    }

    /**
     * Returns whether the LayoutLibrary supports a given {@link Capability}.
     * @return true if it supports it.
     *
     * @see Bridge#getCapabilities()
     *
     * @deprecated use {@link #supports(int)}
     */
    @Deprecated
    public boolean supports(Capability capability) {
        return supports(capability.ordinal());
    }

    /**
     * Returns whether the LayoutLibrary supports a given {@link Features}.
     *
     * @see Bridge#supports(int)
     */
    public boolean supports(int capability) {
        if (mBridge != null) {
            if (mBridge.getApiLevel() > 12) {
                // Features were introduced in API level 13.
                return mBridge.supports(capability);
            } else {
                return capability <= Features.LAST_CAPABILITY
                        && mBridge.getCapabilities().contains(Capability.values()[capability]);
            }
        }

        return false;
    }

    /**
     * Initializes the Layout Library object. This must be called before any other action is taken
     * on the instance.
     *
     * @param platformProperties The build properties for the platform.
     * @param fontLocation the location of the fonts in the SDK target.
     * @param enumValueMap map attrName ⇒ { map enumFlagName ⇒ Integer value }. This is typically
     *          read from attrs.xml in the SDK target.
     * @param log a {@link LayoutLog} object. Can be null.
     * @return true if success.
     */
    public boolean init(Map<String, String> platformProperties,
            File fontLocation,
            Map<String, Map<String, Integer>> enumValueMap,
            LayoutLog log) {
        if (mBridge != null) {
            return mBridge.init(platformProperties, fontLocation, enumValueMap, log);
        }

        return false;
    }

    /**
     * Prepares the layoutlib to unloaded.
     *
     * @see Bridge#dispose()
     */
    public boolean dispose() {
        if (mBridge != null) {
            return mBridge.dispose();
        }

        return true;
    }

    /**
     * Starts a layout session by inflating and rendering it. The method returns a
     * {@link RenderSession} on which further actions can be taken.
     * <p>
     * Before taking further actions on the scene, it is recommended to use
     * {@link #supports(int)} to check what the scene can do.
     *
     * @return a new {@link RenderSession} object that contains the result of the scene creation and
     * first rendering or null if {@link #getStatus()} doesn't return {@link LoadStatus#LOADED}.
     *
     * @see Bridge#createSession(SessionParams)
     */
    public RenderSession createSession(SessionParams params) {
        if (mBridge != null) {
            RenderSession session = mBridge.createSession(params);
            if (params.getExtendedViewInfoMode() && !supports(Features.EXTENDED_VIEWINFO)) {
                // Extended view info was requested but the layoutlib does not support it.
                // Add it manually.
                List<ViewInfo> infoList = session.getRootViews();
                if (infoList != null) {
                    for (ViewInfo info : infoList) {
                        addExtendedViewInfo(info);
                    }
                }
            }

            return session;
        }

        return null;
    }

    /**
     * Renders a Drawable. If the rendering is successful, the result image is accessible through
     * {@link Result#getData()}. It is of type {@link BufferedImage}
     * @param params the rendering parameters.
     * @return the result of the action.
     */
    public Result renderDrawable(DrawableParams params) {
        if (mBridge != null) {
            return mBridge.renderDrawable(params);
        }

        return Status.NOT_IMPLEMENTED.createResult();
    }

    /**
     * Clears the resource cache for a specific project.
     * <p>This cache contains bitmaps and nine patches that are loaded from the disk and reused
     * until this method is called.
     * <p>The cache is not configuration dependent and should only be cleared when a
     * resource changes (at this time only bitmaps and 9 patches go into the cache).
     *
     * @param projectKey the key for the project.
     *
     * @see Bridge#clearCaches(Object)
     */
    public void clearCaches(Object projectKey) {
        if (mBridge != null) {
            mBridge.clearCaches(projectKey);
        }
    }

    /**
     * Utility method returning the parent of a given view object.
     *
     * @param viewObject the object for which to return the parent.
     *
     * @return a {@link Result} indicating the status of the action, and if success, the parent
     *      object in {@link Result#getData()}
     */
    public Result getViewParent(Object viewObject) {
        if (mBridge != null) {
            Result r = mBridge.getViewParent(viewObject);
            if (r.isSuccess()) {
                return r;
            }
        }

        return getViewParentWithReflection(viewObject);
    }

    /**
     * Utility method returning the index of a given view in its parent.
     * @param viewObject the object for which to return the index.
     *
     * @return a {@link Result} indicating the status of the action, and if success, the index in
     *      the parent in {@link Result#getData()}
     */
    public Result getViewIndex(Object viewObject) {
        if (mBridge != null) {
            Result r = mBridge.getViewIndex(viewObject);
            if (r.isSuccess()) {
                return r;
            }
        }

        return getViewIndexReflection(viewObject);
    }

    /**
     * Returns true if the character orientation of the locale is right to left.
     * @param locale The locale formatted as language-region
     * @return true if the locale is right to left.
     */
    public boolean isRtl(String locale) {
        return supports(Features.RTL) && mBridge != null && mBridge.isRtl(locale);
    }

    // ------ Implementation

    private Result getViewParentWithReflection(Object viewObject) {
        // default implementation using reflection.
        try {
            if (mViewGetParentMethod == null) {
                Class<?> viewClass = Class.forName("android.view.View");
                mViewGetParentMethod = viewClass.getMethod("getParent");
            }

            return Status.SUCCESS.createResult(mViewGetParentMethod.invoke(viewObject));
        } catch (Exception e) {
            // Catch all for the reflection calls.
            return ERROR_REFLECTION.createResult(null, e);
        }
    }

    /**
     * Utility method returning the index of a given view in its parent.
     * @param viewObject the object for which to return the index.
     *
     * @return a {@link Result} indicating the status of the action, and if success, the index in
     *      the parent in {@link Result#getData()}
     */
    private Result getViewIndexReflection(Object viewObject) {
        // default implementation using reflection.
        try {
            Class<?> viewClass = Class.forName("android.view.View");

            if (mViewGetParentMethod == null) {
                mViewGetParentMethod = viewClass.getMethod("getParent");
            }

            Object parentObject = mViewGetParentMethod.invoke(viewObject);

            if (mViewParentIndexOfChildMethod == null) {
                Class<?> viewParentClass = Class.forName("android.view.ViewParent");
                mViewParentIndexOfChildMethod = viewParentClass.getMethod("indexOfChild",
                        viewClass);
            }

            return Status.SUCCESS.createResult(
                    mViewParentIndexOfChildMethod.invoke(parentObject, viewObject));
        } catch (Exception e) {
            // Catch all for the reflection calls.
            return ERROR_REFLECTION.createResult(null, e);
        }
    }

    private void addExtendedViewInfo(ViewInfo info) {
        computeExtendedViewInfo(info);

        List<ViewInfo> children = info.getChildren();
        for (ViewInfo child : children) {
            addExtendedViewInfo(child);
        }
    }

    private void computeExtendedViewInfo(ViewInfo info) {
        Object viewObject = info.getViewObject();
        Object params = info.getLayoutParamsObject();

        int baseLine = getViewBaselineReflection(viewObject);
        int leftMargin = 0;
        int topMargin = 0;
        int rightMargin = 0;
        int bottomMargin = 0;

        try {
            if (mMarginLayoutParamClass == null) {
                mMarginLayoutParamClass = Class.forName(
                        "android.view.ViewGroup$MarginLayoutParams");

                mLeftMarginField = mMarginLayoutParamClass.getField("leftMargin");
                mTopMarginField = mMarginLayoutParamClass.getField("topMargin");
                mRightMarginField = mMarginLayoutParamClass.getField("rightMargin");
                mBottomMarginField = mMarginLayoutParamClass.getField("bottomMargin");
            }

            if (mMarginLayoutParamClass.isAssignableFrom(params.getClass())) {

                leftMargin = (Integer)mLeftMarginField.get(params);
                topMargin = (Integer)mTopMarginField.get(params);
                rightMargin = (Integer)mRightMarginField.get(params);
                bottomMargin = (Integer)mBottomMarginField.get(params);
            }

        } catch (Exception e) {
            // just use 'unknown' value.
            leftMargin = Integer.MIN_VALUE;
            topMargin = Integer.MIN_VALUE;
            rightMargin = Integer.MIN_VALUE;
            bottomMargin = Integer.MIN_VALUE;
        }

        info.setExtendedInfo(baseLine, leftMargin, topMargin, rightMargin, bottomMargin);
    }

    /**
     * Utility method returning the baseline value for a given view object. This basically returns
     * View.getBaseline().
     *
     * @param viewObject the object for which to return the index.
     *
     * @return the baseline value or -1 if not applicable to the view object or if this layout
     *     library does not implement this method.
     */
    private int getViewBaselineReflection(Object viewObject) {
        // default implementation using reflection.
        try {
            if (mViewGetBaselineMethod == null) {
                Class<?> viewClass = Class.forName("android.view.View");
                mViewGetBaselineMethod = viewClass.getMethod("getBaseline");
            }

            Object result = mViewGetBaselineMethod.invoke(viewObject);
            if (result instanceof Integer) {
                return ((Integer)result).intValue();
            }

        } catch (Exception e) {
            // Catch all for the reflection calls.
        }

        return Integer.MIN_VALUE;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected LayoutLibrary() {
        mBridge = null;
        mClassLoader = null;
        mStatus = null;
        mLoadMessage = null;
    }
}
