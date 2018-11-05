/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.example.android.deviceconfig;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Toast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import static android.hardware.camera2.CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES;
import static android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE;
import static android.hardware.camera2.CameraCharacteristics.LENS_FACING;

class ConfigGenerator {
    private Activity mCtx;
    private final String mExtensions;
    private final String mGpuInfo;

    private static final String NS_DEVICES_XSD = "http://schemas.android.com/sdk/devices/1";

    /**
     * The "devices" element is the root element of this schema.
     *
     * It must contain one or more "device" elements that each define the
     * hardware, software, and states for a given device.
     */
    private static final String NODE_DEVICES = "devices";

    /**
     * A "device" element contains a "hardware" element, a "software" element
     * for each API version it supports, and a "state" element for each possible
     * state the device could be in.
     */
    private static final String NODE_DEVICE = "device";

    /**
     * The "hardware" element contains all of the hardware information for a
     * given device.
     */
    private static final String NODE_HARDWARE = "hardware";

    /**
     * The "software" element contains all of the software information for an
     * API version of the given device.
     */
    private static final String NODE_SOFTWARE = "software";

    /**
     * The "state" element contains all of the parameters for a given state of
     * the device. It's also capable of redefining hardware configurations if
     * they change based on state.
     */

    private static final String NODE_STATE = "state";

    private static final String NODE_KEYBOARD = "keyboard";
    private static final String NODE_TOUCH = "touch";
    private static final String NODE_GL_EXTENSIONS = "gl-extensions";
    private static final String NODE_GL_VERSION = "gl-version";
    private static final String NODE_NETWORKING = "networking";
    private static final String NODE_REMOVABLE_STORAGE = "removable-storage";
    private static final String NODE_FLASH = "flash";
    private static final String NODE_LIVE_WALLPAPER_SUPPORT = "live-wallpaper-support";
    private static final String NODE_BUTTONS = "buttons";
    private static final String NODE_CAMERA = "camera";
    private static final String NODE_LOCATION = "location";
    private static final String NODE_GPU = "gpu";
    private static final String NODE_DOCK = "dock";
    private static final String NODE_YDPI = "ydpi";
    private static final String NODE_POWER_TYPE = "power-type";
    private static final String NODE_Y_DIMENSION = "y-dimension";
    private static final String NODE_SCREEN_RATIO = "screen-ratio";
    private static final String NODE_NAV_STATE = "nav-state";
    private static final String NODE_MIC = "mic";
    private static final String NODE_RAM = "ram";
    private static final String NODE_XDPI = "xdpi";
    private static final String NODE_DIMENSIONS = "dimensions";
    private static final String NODE_ABI = "abi";
    private static final String NODE_MECHANISM = "mechanism";
    private static final String NODE_MULTITOUCH = "multitouch";
    private static final String NODE_NAV = "nav";
    private static final String NODE_PIXEL_DENSITY = "pixel-density";
    private static final String NODE_SCREEN_ORIENTATION = "screen-orientation";
    private static final String NODE_AUTOFOCUS = "autofocus";
    private static final String NODE_SCREEN_SIZE = "screen-size";
    private static final String NODE_DESCRIPTION = "description";
    private static final String NODE_BLUETOOTH_PROFILES = "bluetooth-profiles";
    private static final String NODE_SCREEN = "screen";
    private static final String NODE_SENSORS = "sensors";
    private static final String NODE_STATUS_BAR = "status-bar";
    private static final String NODE_DIAGONAL_LENGTH = "diagonal-length";
    private static final String NODE_SCREEN_TYPE = "screen-type";
    private static final String NODE_KEYBOARD_STATE = "keyboard-state";
    private static final String NODE_X_DIMENSION = "x-dimension";
    private static final String NODE_CPU = "cpu";
    private static final String NODE_INTERNAL_STORAGE = "internal-storage";
    private static final String NODE_NAME = "name";
    private static final String NODE_ID = "id";
    private static final String NODE_SKIN = "skin";
    private static final String NODE_MANUFACTURER = "manufacturer";
    private static final String NODE_API_LEVEL = "api-level";
    private static final String ATTR_DEFAULT = "default";
    private static final String ATTR_UNIT = "unit";
    private static final String UNIT_BYTES = "B";
    private static final String UNIT_KIBIBYTES = "KiB";
    private static final String UNIT_MEBIBYTES = "MiB";
    private static final String UNIT_GIBIBYTES = "GiB";
//    private static final String UNIT_TEBIBYTES = "TiB";
    private static final String LOCAL_NS = "d";
    private static final String PREFIX = LOCAL_NS + ":";

    private static final String TAG = "ConfigGenerator";

    ConfigGenerator(@NonNull Activity context, @NonNull String extensions, @NonNull String gpuInfo) {
        mCtx = context;
        mExtensions = extensions;
        mGpuInfo = gpuInfo;
    }

    static DisplayMetrics getDisplayMetrics(Activity context) {
        if (Build.VERSION.SDK_INT >= 17) {
            try {
                Display display = context.getWindowManager().getDefaultDisplay();
                DisplayMetrics metrics = new DisplayMetrics();
                display.getRealMetrics(metrics);
                return metrics;
            } catch (Exception ignored) {
            }
        }

        return context.getResources().getDisplayMetrics();
    }


    static int getScreenWidth(WindowManager windowManager, DisplayMetrics metrics) {
        Display display = windowManager.getDefaultDisplay();
        if (Build.VERSION.SDK_INT >= 17) {
            try {
                Point realSize = new Point();
                Display.class.getMethod("getRealSize", Point.class).invoke(display, realSize);
                return realSize.x;
            } catch (Exception ignored) {
            }
        }

        display.getMetrics(metrics);
        return metrics.widthPixels;
    }


    static int getScreenHeight(WindowManager windowManager, DisplayMetrics metrics) {
        Display display = windowManager.getDefaultDisplay();
        if (Build.VERSION.SDK_INT >= 17) {
            try {
                Point realSize = new Point();
                Display.class.getMethod("getRealSize", Point.class).invoke(display, realSize);
                return realSize.y;
            } catch (Exception ignored) {
            }
        }

        display.getMetrics(metrics);
        return metrics.heightPixels;
    }

    @SuppressLint("WorldReadableFiles")
    String generateConfig() {
        Resources resources = mCtx.getResources();
        PackageManager packageMgr = mCtx.getPackageManager();
        DisplayMetrics metrics = getDisplayMetrics(mCtx);
        Configuration config = resources.getConfiguration();

        int screenWidth = 0;
        int screenHeight = 0;
        WindowManager wm = (WindowManager) mCtx.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            // These sizes are not always accurate, so values derived
            // from them are marked as "TODO: approx "
            screenWidth = getScreenWidth(wm, metrics);
            screenHeight = getScreenHeight(wm, metrics);
        }

        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

            Element devices = doc.createElement(PREFIX + NODE_DEVICES);
            devices.setAttribute(XMLConstants.XMLNS_ATTRIBUTE + ":xsi",
                    XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI);
            devices.setAttribute(XMLConstants.XMLNS_ATTRIBUTE + ":" + LOCAL_NS, NS_DEVICES_XSD);
            doc.appendChild(devices);

            Element device = doc.createElement(PREFIX + NODE_DEVICE);
            devices.appendChild(device);

            Element name = doc.createElement(PREFIX + NODE_NAME);
            device.appendChild(name);
            name.appendChild(doc.createTextNode(android.os.Build.MODEL));

            Element id = doc.createElement(PREFIX + NODE_ID);
            device.appendChild(id);
            id.appendChild(doc.createTextNode(android.os.Build.MODEL));

            Element manufacturer = doc.createElement(PREFIX + NODE_MANUFACTURER);
            device.appendChild(manufacturer);
            manufacturer.appendChild(doc.createTextNode(android.os.Build.MANUFACTURER));

            Element hardware = doc.createElement(PREFIX + NODE_HARDWARE);
            device.appendChild(hardware);

            Element screen = doc.createElement(PREFIX + NODE_SCREEN);
            hardware.appendChild(screen);

            Element screenSize = doc.createElement(PREFIX + NODE_SCREEN_SIZE);
            screen.appendChild(screenSize);
            Text screenSizeText;
            switch (config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) {
            case Configuration.SCREENLAYOUT_SIZE_SMALL:
                screenSizeText = doc.createTextNode("small");
                break;
            case Configuration.SCREENLAYOUT_SIZE_NORMAL:
                screenSizeText = doc.createTextNode("normal");
                break;
            case Configuration.SCREENLAYOUT_SIZE_LARGE:
                screenSizeText = doc.createTextNode("large");
                break;
            case Configuration.SCREENLAYOUT_SIZE_XLARGE:
                screenSizeText = doc.createTextNode("xlarge");
                break;
            default:
                screenSizeText = doc.createTextNode(" ");
                break;
            }
            screenSize.appendChild(screenSizeText);

            Element diagonalLength = doc.createElement(PREFIX + NODE_DIAGONAL_LENGTH);
            screen.appendChild(diagonalLength);
            double xin = screenWidth / metrics.xdpi;
            double yin = screenHeight / metrics.ydpi;

            double diag = Math.sqrt(Math.pow(xin, 2) + Math.pow(yin, 2));
            diagonalLength.appendChild(doc.createTextNode(
                  String.format(Locale.US, "TODO: approx %1$.2f", diag)));

            Element pixelDensity = doc.createElement(PREFIX + NODE_PIXEL_DENSITY);
            screen.appendChild(pixelDensity);
            Text pixelDensityText;
            switch (metrics.densityDpi) {
            case DisplayMetrics.DENSITY_LOW:
                pixelDensityText = doc.createTextNode("ldpi");
                break;
            case DisplayMetrics.DENSITY_MEDIUM:
                pixelDensityText = doc.createTextNode("mdpi");
                break;
            case DisplayMetrics.DENSITY_TV:
                pixelDensityText = doc.createTextNode("tvdpi");
                break;
            case DisplayMetrics.DENSITY_HIGH:
                pixelDensityText = doc.createTextNode("hdpi");
                break;
            case DisplayMetrics.DENSITY_XHIGH:
                pixelDensityText = doc.createTextNode("xhdpi");
                break;
            case DisplayMetrics.DENSITY_XXHIGH:
                pixelDensityText = doc.createTextNode("xxhdpi");
                break;
            case DisplayMetrics.DENSITY_XXXHIGH:
                pixelDensityText = doc.createTextNode("xxxhdpi");
                break;
            case DisplayMetrics.DENSITY_260:
                pixelDensityText = doc.createTextNode("260dpi");
                break;
            case DisplayMetrics.DENSITY_280:
                pixelDensityText = doc.createTextNode("280dpi");
                break;
            case DisplayMetrics.DENSITY_300:
                pixelDensityText = doc.createTextNode("300dpi");
                break;
            case DisplayMetrics.DENSITY_340:
                pixelDensityText = doc.createTextNode("340dpi");
                break;
            case DisplayMetrics.DENSITY_360:
                pixelDensityText = doc.createTextNode("360dpi");
                break;
            case DisplayMetrics.DENSITY_400:
                pixelDensityText = doc.createTextNode("400dpi");
                break;
            case DisplayMetrics.DENSITY_420:
                pixelDensityText = doc.createTextNode("420dpi");
                break;
            case DisplayMetrics.DENSITY_440:
                pixelDensityText = doc.createTextNode("440dpi");
                break;
            case DisplayMetrics.DENSITY_560:
                pixelDensityText = doc.createTextNode("560dpi");
                break;
            default:
                pixelDensityText = doc.createTextNode("TODO: unknown");
            }
            pixelDensity.appendChild(pixelDensityText);

            Element screenRatio = doc.createElement(PREFIX + NODE_SCREEN_RATIO);
            screen.appendChild(screenRatio);
            Text screenRatioText;
            switch (config.screenLayout & Configuration.SCREENLAYOUT_LONG_MASK) {
            case Configuration.SCREENLAYOUT_LONG_YES:
                screenRatioText = doc.createTextNode("long");
                break;
            case Configuration.SCREENLAYOUT_LONG_NO:
                screenRatioText = doc.createTextNode("notlong");
                break;
            default:
                screenRatioText = doc.createTextNode(" ");
                break;
            }
            screenRatio.appendChild(screenRatioText);

            Element dimensions = doc.createElement(PREFIX + NODE_DIMENSIONS);
            screen.appendChild(dimensions);

            Element xDimension = doc.createElement(PREFIX + NODE_X_DIMENSION);
            dimensions.appendChild(xDimension);
            xDimension.appendChild(doc.createTextNode(Integer.toString(screenWidth)));

            Element yDimension = doc.createElement(PREFIX + NODE_Y_DIMENSION);
            dimensions.appendChild(yDimension);
            yDimension.appendChild(doc.createTextNode(Integer.toString(screenHeight)));

            Element xdpi = doc.createElement(PREFIX + NODE_XDPI);
            screen.appendChild(xdpi);
            xdpi.appendChild(doc.createTextNode("TODO: approx " + Double.toString(metrics.xdpi)));

            Element ydpi = doc.createElement(PREFIX + NODE_YDPI);
            screen.appendChild(ydpi);
            ydpi.appendChild(doc.createTextNode("TODO: approx " + Double.toString(metrics.ydpi)));

            Element touch = doc.createElement(PREFIX + NODE_TOUCH);
            screen.appendChild(touch);

            Element multitouch = doc.createElement(PREFIX + NODE_MULTITOUCH);
            touch.appendChild(multitouch);
            Text multitouchText;
            if (packageMgr
                    .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND)) {
                multitouchText = doc.createTextNode("jazz-hands");
            } else if (packageMgr
                    .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)) {
                multitouchText = doc.createTextNode("distinct");
            } else if (packageMgr.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH)) {
                multitouchText = doc.createTextNode("basic");
            } else {
                multitouchText = doc.createTextNode("none");
            }
            multitouch.appendChild(multitouchText);

            Element mechanism = doc.createElement(PREFIX + NODE_MECHANISM);
            touch.appendChild(mechanism);
            Text mechanismText;
            switch (config.touchscreen) {
            case Configuration.TOUCHSCREEN_STYLUS:
                mechanismText = doc.createTextNode("stylus");
                break;
            case Configuration.TOUCHSCREEN_FINGER:
                mechanismText = doc.createTextNode("finger");
                break;
            case Configuration.TOUCHSCREEN_NOTOUCH:
                mechanismText = doc.createTextNode("notouch");
                break;
            default:
                mechanismText = doc.createTextNode("TODO: typically \"finger\"");
            }
            mechanism.appendChild(mechanismText);

            // Create an empty place holder node for screen-type since we can't
            // actually determine it

            Element screenType = doc.createElement(PREFIX + NODE_SCREEN_TYPE);
            touch.appendChild(screenType);
            screenType.appendChild(doc.createTextNode("TODO:typically \"capacitive\""));

            Element networking = doc.createElement(PREFIX + NODE_NETWORKING);
            hardware.appendChild(networking);
            Text networkingText = doc.createTextNode("");
            networking.appendChild(networkingText);
            if (packageMgr.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
                networkingText.appendData("\nWifi");
            }
            if (packageMgr.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
                networkingText.appendData("\nBluetooth");
            }
            if (packageMgr.hasSystemFeature(PackageManager.FEATURE_NFC)) {
                networkingText.appendData("\nNFC");
            }
            networkingText.appendData("\n");

            Element sensors = doc.createElement(PREFIX + NODE_SENSORS);
            hardware.appendChild(sensors);
            Text sensorsText = doc.createTextNode("");
            sensors.appendChild(sensorsText);
            if (packageMgr.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)) {
                sensorsText.appendData("\nAccelerometer");
            }
            if (packageMgr.hasSystemFeature(PackageManager.FEATURE_SENSOR_BAROMETER)) {
                sensorsText.appendData("\nBarometer");
            }
            if (packageMgr.hasSystemFeature(PackageManager.FEATURE_SENSOR_COMPASS)) {
                sensorsText.appendData("\nCompass");
            }
            if (packageMgr.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)) {
                sensorsText.appendData("\nGPS");
            }
            if (packageMgr.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)) {
                sensorsText.appendData("\nGyroscope");
            }
            if (packageMgr.hasSystemFeature(PackageManager.FEATURE_SENSOR_LIGHT)) {
                sensorsText.appendData("\nLightSensor");
            }
            if (packageMgr.hasSystemFeature(PackageManager.FEATURE_SENSOR_PROXIMITY)) {
                sensorsText.appendData("\nProximitySensor");
            }
            if (packageMgr.hasSystemFeature(PackageManager.FEATURE_SENSOR_AMBIENT_TEMPERATURE)) {
                sensorsText.appendData("\nAmbientTemperatureSensor");
            }
            if (packageMgr.hasSystemFeature(PackageManager.FEATURE_SENSOR_HEART_RATE)) {
                sensorsText.appendData("\nHeartRate");
            }
            if (packageMgr.hasSystemFeature(PackageManager.FEATURE_SENSOR_HEART_RATE_ECG)) {
                sensorsText.appendData("\nECG");
            }
            if (packageMgr.hasSystemFeature(PackageManager.FEATURE_SENSOR_RELATIVE_HUMIDITY)) {
                sensorsText.appendData("\nRelativeHumiditySensor");
            }
            if (packageMgr.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER)) {
                sensorsText.appendData("\nStepCounter");
            }
            if (packageMgr.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_DETECTOR)) {
                sensorsText.appendData("\nStepDetector");
            }
            sensorsText.appendData("\n");

            Element mic = doc.createElement(PREFIX + NODE_MIC);
            hardware.appendChild(mic);
            Text micText;
            if (packageMgr.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
                micText = doc.createTextNode("true");
            } else {
                micText = doc.createTextNode("false");
            }
            mic.appendChild(micText);

            List<Element> cameras;
            if (android.os.Build.VERSION.SDK_INT < 23) {
                cameras = getCameraElements(doc);
            } else {
                cameras = getCamera2Elements(doc);
            }
            for (Element cam : cameras) {
                hardware.appendChild(cam);
            }

            Element keyboard = doc.createElement(PREFIX + NODE_KEYBOARD);
            hardware.appendChild(keyboard);
            Text keyboardText;
            switch (config.keyboard) {
            case Configuration.KEYBOARD_NOKEYS:
                keyboardText = doc.createTextNode("nokeys");
                break;
            case Configuration.KEYBOARD_12KEY:
                keyboardText = doc.createTextNode("12key");
                break;
            case Configuration.KEYBOARD_QWERTY:
                keyboardText = doc.createTextNode("qwerty");
                break;
            default:
                keyboardText = doc.createTextNode(" ");
            }
            keyboard.appendChild(keyboardText);

            Element nav = doc.createElement(PREFIX + NODE_NAV);
            hardware.appendChild(nav);
            Text navText;
            switch (config.navigation) {
            case Configuration.NAVIGATION_DPAD:
                navText = doc.createTextNode("dpad");
                break;
            case Configuration.NAVIGATION_TRACKBALL:
                navText = doc.createTextNode("trackball");
                break;
            case Configuration.NAVIGATION_WHEEL:
                navText = doc.createTextNode("wheel");
                break;
            case Configuration.NAVIGATION_NONAV:
                navText = doc.createTextNode("nonav");
                break;
            default:
                navText = doc.createTextNode("TODO:typically \"nonav\"");
            }
            nav.appendChild(navText);

            Element ram = doc.createElement(PREFIX + NODE_RAM);
            hardware.appendChild(ram);

            ActivityManager actManager = (ActivityManager) mCtx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            if (actManager != null) {
                actManager.getMemoryInfo(memInfo);
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                long totalMemory = memInfo.totalMem;
                long ramAmount = totalMemory / (1024 * 1024);
                ram.setAttribute(ATTR_UNIT, UNIT_MEBIBYTES);
                ram.appendChild(doc.createTextNode(Long.toString(ramAmount)));
            } else {
                // totalMemory given in bytes, divide by 1048576 to get RAM in MiB
                String line;
                long ramAmount = 0;
                String unit = UNIT_BYTES;
                try {
                    BufferedReader meminfo = new BufferedReader(new FileReader("/proc/meminfo"));
                    while ((line = meminfo.readLine()) != null) {
                        String[] vals = line.split("[\\s]+");
                        if (vals[0].equals("MemTotal:")) {
                            try {
                                /*
                                 * We're going to want it as a string eventually,
                                 * but parsing it lets us validate it's actually a
                                 * number and something strange isn't going on
                                 */
                                ramAmount = Long.parseLong(vals[1]);
                                unit = vals[2];
                                break;
                            } catch (NumberFormatException e) {
                                // Ignore
                            }
                        }
                    }
                    meminfo.close();
                } catch (FileNotFoundException e) {
                    // Ignore
                }
                if (ramAmount > 0) {
                    switch (unit) {
                        case "B":  unit = UNIT_BYTES;      break;
                        case "kB": unit = UNIT_KIBIBYTES;  break;
                        case "MB": unit = UNIT_MEBIBYTES;  break;
                        case "GB": unit = UNIT_GIBIBYTES;  break;
                        default:   unit = " ";
                    }
                }
                ram.setAttribute(ATTR_UNIT, unit);
                ram.appendChild(doc.createTextNode(Long.toString(ramAmount)));
            }

            Element buttons = doc.createElement(PREFIX + NODE_BUTTONS);
            hardware.appendChild(buttons);
            Text buttonsText;
            buttonsText = doc.createTextNode(getButtonsType());
            buttons.appendChild(buttonsText);

            long externalTotal;
            long internalTotal;
            StatFs internalStatFs = new StatFs( Environment.getRootDirectory().getAbsolutePath() );
            StatFs externalStatFs = new StatFs( Environment.getExternalStorageDirectory().getAbsolutePath() );
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                internalTotal = (internalStatFs.getBlockCountLong() * internalStatFs.getBlockSizeLong()) / (1024 * 1024);
                externalTotal = (externalStatFs.getBlockCountLong() * externalStatFs.getBlockSizeLong()) / (1024 * 1024);
            } else {
                internalTotal = ((long) internalStatFs.getBlockCount() * (long) internalStatFs.getBlockSize()) / (1024 * 1024);
                externalTotal = ((long) externalStatFs.getBlockCount() * (long) externalStatFs.getBlockSize()) / (1024 * 1024);
            }

            Element internalStorage = doc.createElement(PREFIX + NODE_INTERNAL_STORAGE);
            hardware.appendChild(internalStorage);
            internalStorage.appendChild(doc.createTextNode(Long.toString(internalTotal)));
            internalStorage.setAttribute(ATTR_UNIT, UNIT_MEBIBYTES);

            Element externalStorage = doc.createElement(PREFIX + NODE_REMOVABLE_STORAGE);
            hardware.appendChild(externalStorage);
            externalStorage.appendChild(doc.createTextNode(Long.toString(externalTotal)));
            externalStorage.setAttribute(ATTR_UNIT, UNIT_MEBIBYTES);

            // Don't know CPU, GPU types
            Element cpu = doc.createElement(PREFIX + NODE_CPU);
            hardware.appendChild(cpu);

            /* CPU
             adb shell cat /proc/cpuinfo | grep "model name"
             */
            String cpuName = null;
            try {
                String line;
                BufferedReader cpuinfo = new BufferedReader(new FileReader("/proc/cpuinfo"));
                while ((line = cpuinfo.readLine()) != null) {
                    if (line.startsWith("model name") || line.startsWith("Processor")) {
                        int index = line.indexOf(':');
                        if (index != -1) {
                            cpuName = line.substring(index + 1).trim();
                            break;
                        }
                    }
                }
                cpuinfo.close();
            } catch (FileNotFoundException e) {
                // Ignore
            }
            if (cpuName != null) {
                cpu.appendChild(doc.createTextNode(cpuName));
            } else {
                cpu.appendChild(doc.createTextNode("TODO"));
            }

            Element gpu = doc.createElement(PREFIX + NODE_GPU);
            hardware.appendChild(gpu);
            gpu.appendChild(doc.createTextNode(mGpuInfo.isEmpty() ? "TODO" : mGpuInfo));


            Element abi = doc.createElement(PREFIX + NODE_ABI);
            hardware.appendChild(abi);
            Text abiText = doc.createTextNode("");
            abi.appendChild(abiText);
            if (Build.VERSION.SDK_INT >= 21) {
                for (String abiName : Build.SUPPORTED_ABIS) {
                    abiText.appendData("\n" + abiName);
                }
                abiText.appendData("\n");
            } else {
                abiText.appendData("\n" + android.os.Build.CPU_ABI);
                abiText.appendData("\n" + android.os.Build.CPU_ABI2);
                abiText.appendData("\n");
            }

            // Don't know about either the dock or plugged-in element
            Element dock = doc.createElement(PREFIX + NODE_DOCK);
            hardware.appendChild(dock);
            dock.appendChild(doc.createTextNode(" "));

            Element pluggedIn = doc.createElement(PREFIX + NODE_POWER_TYPE);
            hardware.appendChild(pluggedIn);
            pluggedIn.appendChild(doc.createTextNode("battery"));

            Element skin = doc.createElement(PREFIX + NODE_SKIN);
            hardware.appendChild(skin);
            skin.appendChild(doc.createTextNode(android.os.Build.MODEL.toLowerCase().replace(' ', '_')));

            Element software = doc.createElement(PREFIX + NODE_SOFTWARE);
            device.appendChild(software);

            Element apiLevel = doc.createElement(PREFIX + NODE_API_LEVEL);
            software.appendChild(apiLevel);
            apiLevel.appendChild(doc.createTextNode(Integer
                    .toString(android.os.Build.VERSION.SDK_INT)));

            Element liveWallpaperSupport = doc.createElement(PREFIX + NODE_LIVE_WALLPAPER_SUPPORT);
            software.appendChild(liveWallpaperSupport);
            if (packageMgr.hasSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER)) {
                liveWallpaperSupport.appendChild(doc.createTextNode("true"));
            } else {
                liveWallpaperSupport.appendChild(doc.createTextNode("false"));
            }

            Element bluetoothProfiles = doc.createElement(PREFIX + NODE_BLUETOOTH_PROFILES);
            software.appendChild(bluetoothProfiles);
            bluetoothProfiles.appendChild(doc.createTextNode(" "));

            Element glVersion = doc.createElement(PREFIX + NODE_GL_VERSION);
            software.appendChild(glVersion);
            String glVersionString = " ";

            FeatureInfo[] features = packageMgr.getSystemAvailableFeatures();
            for (FeatureInfo feature : features) {
                if (feature.reqGlEsVersion > 0) {
                    glVersionString = feature.getGlEsVersion();
                    break;
                }
            }

            glVersion.appendChild(doc.createTextNode(glVersionString));

            Element glExtensions = doc.createElement(PREFIX + NODE_GL_EXTENSIONS);
            software.appendChild(glExtensions);
            if (!mExtensions.trim().equals("")) {
                glExtensions.appendChild(doc.createTextNode(mExtensions));
            } else {
                glExtensions.appendChild(doc.createTextNode(" "));
            }

            Element statusBar = doc.createElement(PREFIX + NODE_STATUS_BAR);
            software.appendChild(statusBar);
            statusBar.appendChild(doc.createTextNode("true"));

            // States
            Element state = doc.createElement(PREFIX + NODE_STATE);
            device.appendChild(state);
            boolean isPhone = screenHeight > screenWidth;
            state.setAttribute("name", "Portrait");
            if (isPhone) {
                state.setAttribute(ATTR_DEFAULT, "true");
            }
            Element description = doc.createElement(PREFIX + NODE_DESCRIPTION);
            state.appendChild(description);
            description.appendChild(doc.createTextNode("The device in portrait view"));
            Element orientation = doc.createElement(PREFIX + NODE_SCREEN_ORIENTATION);
            state.appendChild(orientation);
            orientation.appendChild(doc.createTextNode("port"));
            Element keyboardState = doc.createElement(PREFIX + NODE_KEYBOARD_STATE);
            state.appendChild(keyboardState);
            keyboardState.appendChild(doc.createTextNode("keyssoft"));
            Element navState = doc.createElement(PREFIX + NODE_NAV_STATE);
            state.appendChild(navState);
            navState.appendChild(doc.createTextNode("nonav"));

            state = doc.createElement(PREFIX + NODE_STATE);
            device.appendChild(state);
            state.setAttribute("name", "Landscape");
            if (!isPhone) {
                state.setAttribute(ATTR_DEFAULT, "true");
            }
            description = doc.createElement(PREFIX + NODE_DESCRIPTION);
            state.appendChild(description);
            description.appendChild(doc.createTextNode("The device in landscape view"));
            orientation = doc.createElement(PREFIX + NODE_SCREEN_ORIENTATION);
            state.appendChild(orientation);
            orientation.appendChild(doc.createTextNode("land"));
            keyboardState = doc.createElement(PREFIX + NODE_KEYBOARD_STATE);
            state.appendChild(keyboardState);
            keyboardState.appendChild(doc.createTextNode("keyssoft"));
            navState = doc.createElement(PREFIX + NODE_NAV_STATE);
            state.appendChild(navState);
            navState.appendChild(doc.createTextNode("nonav"));

            Transformer tf = TransformerFactory.newInstance().newTransformer();
            tf.setOutputProperty(OutputKeys.INDENT, "yes");
            tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            DOMSource source = new DOMSource(doc);
            String filename = String.format(Locale.US, "devices_%1$tm_%1$td_%1$ty.xml",
                                            Calendar.getInstance().getTime());
            //File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File dir = mCtx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File outFile = new File(dir, filename);
            FileOutputStream out = new FileOutputStream(new File(dir, filename));
            StreamResult result = new StreamResult(out);
            tf.transform(source, result);
            out.flush();
            out.close();
            return outFile.getAbsolutePath();
        } catch (ParserConfigurationException e) {
            error("Parser config exception", e);
        } catch (TransformerConfigurationException e) {
            error("Transformer config exception", e);
        } catch (TransformerFactoryConfigurationError e) {
            error("TransformerFactory config exception", e);
        } catch (TransformerException e) {
            error("Error transforming", e);
        } catch (IOException e) {
            error("I/O Error", e);
        }
        return null;
    }

    // For API 23 and higher, use the Camera 2 HAL
    @TargetApi(23)
    private List<Element> getCamera2Elements(Document doc) {
        List<Element> cList = new ArrayList<>();
        CameraManager cameraManager = (CameraManager)mCtx.getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) {
            return cList; // Empty
        }
        String[] cameraList;
        try {
            cameraList = cameraManager.getCameraIdList();
        } catch (android.hardware.camera2.CameraAccessException cae) {
            return cList; // Empty
        }
        for (String aCamera : cameraList) {
            CameraCharacteristics characteristics;
            try {
                characteristics = cameraManager.getCameraCharacteristics(aCamera);
            } catch (android.hardware.camera2.CameraAccessException cae) {
                continue;
            }

            Element camera = doc.createElement(PREFIX + NODE_CAMERA);
            cList.add(camera);

            Element location = doc.createElement(PREFIX + NODE_LOCATION);
            camera.appendChild(location);
            Text locationText;
            Integer facing = characteristics.get(LENS_FACING);
            if (facing != null && facing == CameraMetadata.LENS_FACING_FRONT) {
                locationText = doc.createTextNode("front");
            } else if (facing != null && facing == CameraMetadata.LENS_FACING_BACK) {
                locationText = doc.createTextNode("back");
            } else {
                locationText = doc.createTextNode(" ");
            }
            location.appendChild(locationText);

            Element autoFocus = doc.createElement(PREFIX + NODE_AUTOFOCUS);
            camera.appendChild(autoFocus);
            int[] autoFocusModes = characteristics.get(CONTROL_AF_AVAILABLE_MODES);
            Text autoFocusText = doc.createTextNode(
                    (autoFocusModes != null && autoFocusModes.length > 0) ? "true" : "false");
            autoFocus.appendChild(autoFocusText);

            Element flash = doc.createElement(PREFIX + NODE_FLASH);
            camera.appendChild(flash);
            Boolean flashInfoAvailable = characteristics.get(FLASH_INFO_AVAILABLE);
            Text flashText = doc.createTextNode(
                    (flashInfoAvailable != null && flashInfoAvailable) ? "true" : "false");
            flash.appendChild(flashText);
        }
        return cList;
    }



    // For API 9..22, use the Camera 1 HAL
    @TargetApi(9)
    private List<Element> getCameraElements(Document doc) {
        List<Element> cList = new ArrayList<>();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Element camera = doc.createElement(PREFIX + NODE_CAMERA);
            cList.add(camera);
            Element location = doc.createElement(PREFIX + NODE_LOCATION);
            camera.appendChild(location);
            Text locationText;
            Camera.CameraInfo cInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(i, cInfo);
            if (cInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                locationText = doc.createTextNode("front");
            } else if (cInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                locationText = doc.createTextNode("back");
            } else {
                locationText = doc.createTextNode(" ");
            }
            location.appendChild(locationText);

            Camera c = Camera.open(i);
            Camera.Parameters cParams = c.getParameters();

            Element autofocus = doc.createElement(PREFIX + NODE_AUTOFOCUS);
            camera.appendChild(autofocus);
            List<String> foci = cParams.getSupportedFocusModes();
            if (foci == null) {
                autofocus.appendChild(doc.createTextNode(" "));
            } else if (foci.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                autofocus.appendChild(doc.createTextNode("true"));
            } else {
                autofocus.appendChild(doc.createTextNode("false"));
            }

            Element flash = doc.createElement(PREFIX + NODE_FLASH);
            camera.appendChild(flash);
            List<String> flashes = cParams.getSupportedFlashModes();
            if (flashes == null || !flashes.contains(Camera.Parameters.FLASH_MODE_ON)) {
                flash.appendChild(doc.createTextNode("false"));
            } else {
                flash.appendChild(doc.createTextNode("true"));
            }
            c.release();
        }
        return cList;
    }

    @TargetApi(14)
    private String getButtonsType() {
        ViewConfiguration vConfig = ViewConfiguration.get(mCtx);

        if (vConfig.hasPermanentMenuKey()) {
            return "hard";
        } else {
            return "soft";
        }
    }

    private void error(String err, Throwable e) {
        Toast.makeText(mCtx, "Error Generating Configuration", Toast.LENGTH_SHORT).show();
        Log.e(TAG, err);
        Log.e(TAG, e.getLocalizedMessage());
    }
}
