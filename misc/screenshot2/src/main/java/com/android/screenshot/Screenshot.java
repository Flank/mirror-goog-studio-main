/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.screenshot;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.Log.ILogOutput;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.RawImage;
import com.android.ddmlib.TimeoutException;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import java.util.zip.Deflater;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import org.w3c.dom.Node;

/**
 * Connects to a device using ddmlib and dumps its event log as long as the device is connected.
 */
public class Screenshot {
    private static final String ICC_PROFILE_DISPLAY_P3 =
              "AAACSAAAAAAEAAAAbW50clJHQiBYWVogB+EAAgAIAA0AFAAKYWNzcAAAAAAAAAAAR09PRwAAAAAA"
            + "AAAAAAAAAAAAAAAAAPbWAAEAAAAA0y1HT09Hn6A6MCwYip4tkAyRUvYYTAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAALZGVzYwAAAQgAAAAyY3BydAAAATwAAABad3RwdAAAAZgAAAAUYmtw"
            + "dAAAAawAAAAUclhZWgAAAcAAAAAUZ1hZWgAAAdQAAAAUYlhZWgAAAegAAAAUclRSQwAAAfwAAAAg"
            + "Y2hhZAAAAhwAAAAsYlRSQwAAAfwAAAAgZ1RSQwAAAfwAAAAgbWx1YwAAAAAAAAABAAAADGVuVVMA"
            + "AAAWAAAAHABEAGkAcwBwAGwAYQB5ACAAUAAzAAAAAG1sdWMAAAAAAAAAAQAAAAxlblVTAAAAPgAA"
            + "ABwAQwBvAHAAeQByAGkAZwBoAHQAIAAoAGMAKQAgADIAMAAxADcAIABHAG8AbwBnAGwAZQAgAEkA"
            + "bgBjAC4AAAAAWFlaIAAAAAAAAPNRAAEAAAABFsxYWVogAAAAAAAAAAAAAAAAAAAAAFhZWiAAAAAA"
            + "AACD3wAAPb////+8WFlaIAAAAAAAAEq/AACxNwAACrlYWVogAAAAAAAAKDgAABELAADIuXBhcmEA"
            + "AAAAAAMAAAACZmYAAPKnAAANWQAAE9AAAApbc2YzMgAAAAAAAQxCAAAF3v//8ycAAAeTAAD9kP//"
            + "+6P///2kAAAD3AAAwG4=";
    private static final String ICC_PROFILE_SRGB =
              "AAAMSExpbm8CEAAAbW50clJHQiBYWVogB84AAgAJAAYAMQAAYWNzcE1TRlQAAAAASUVDIHNSR0IA"
            + "AAAAAAAAAAAAAAAAAPbWAAEAAAAA0y1IUCAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAARY3BydAAAAVAAAAAzZGVzYwAAAYQAAABsd3RwdAAAAfAAAAAUYmtw"
            + "dAAAAgQAAAAUclhZWgAAAhgAAAAUZ1hZWgAAAiwAAAAUYlhZWgAAAkAAAAAUZG1uZAAAAlQAAABw"
            + "ZG1kZAAAAsQAAACIdnVlZAAAA0wAAACGdmlldwAAA9QAAAAkbHVtaQAAA/gAAAAUbWVhcwAABAwA"
            + "AAAkdGVjaAAABDAAAAAMclRSQwAABDwAAAgMZ1RSQwAABDwAAAgMYlRSQwAABDwAAAgMdGV4dAAA"
            + "AABDb3B5cmlnaHQgKGMpIDE5OTggSGV3bGV0dC1QYWNrYXJkIENvbXBhbnkAAGRlc2MAAAAAAAAA"
            + "EnNSR0IgSUVDNjE5NjYtMi4xAAAAAAAAAAAAAAASc1JHQiBJRUM2MTk2Ni0yLjEAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFhZWiAAAAAAAADzUQABAAAA"
            + "ARbMWFlaIAAAAAAAAAAAAAAAAAAAAABYWVogAAAAAAAAb6IAADj1AAADkFhZWiAAAAAAAABimQAA"
            + "t4UAABjaWFlaIAAAAAAAACSgAAAPhAAAts9kZXNjAAAAAAAAABZJRUMgaHR0cDovL3d3dy5pZWMu"
            + "Y2gAAAAAAAAAAAAAABZJRUMgaHR0cDovL3d3dy5pZWMuY2gAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAZGVzYwAAAAAAAAAuSUVDIDYxOTY2LTIuMSBEZWZhdWx0"
            + "IFJHQiBjb2xvdXIgc3BhY2UgLSBzUkdCAAAAAAAAAAAAAAAuSUVDIDYxOTY2LTIuMSBEZWZhdWx0"
            + "IFJHQiBjb2xvdXIgc3BhY2UgLSBzUkdCAAAAAAAAAAAAAAAAAAAAAAAAAAAAAGRlc2MAAAAAAAAA"
            + "LFJlZmVyZW5jZSBWaWV3aW5nIENvbmRpdGlvbiBpbiBJRUM2MTk2Ni0yLjEAAAAAAAAAAAAAACxS"
            + "ZWZlcmVuY2UgVmlld2luZyBDb25kaXRpb24gaW4gSUVDNjE5NjYtMi4xAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAB2aWV3AAAAAAATpP4AFF8uABDPFAAD7cwABBMLAANcngAAAAFYWVogAAAAAABM"
            + "CVYAUAAAAFcf521lYXMAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAKPAAAAAnNpZyAAAAAAQ1JU"
            + "IGN1cnYAAAAAAAAEAAAAAAUACgAPABQAGQAeACMAKAAtADIANwA7AEAARQBKAE8AVABZAF4AYwBo"
            + "AG0AcgB3AHwAgQCGAIsAkACVAJoAnwCkAKkArgCyALcAvADBAMYAywDQANUA2wDgAOUA6wDwAPYA"
            + "+wEBAQcBDQETARkBHwElASsBMgE4AT4BRQFMAVIBWQFgAWcBbgF1AXwBgwGLAZIBmgGhAakBsQG5"
            + "AcEByQHRAdkB4QHpAfIB+gIDAgwCFAIdAiYCLwI4AkECSwJUAl0CZwJxAnoChAKOApgCogKsArYC"
            + "wQLLAtUC4ALrAvUDAAMLAxYDIQMtAzgDQwNPA1oDZgNyA34DigOWA6IDrgO6A8cD0wPgA+wD+QQG"
            + "BBMEIAQtBDsESARVBGMEcQR+BIwEmgSoBLYExATTBOEE8AT+BQ0FHAUrBToFSQVYBWcFdwWGBZYF"
            + "pgW1BcUF1QXlBfYGBgYWBicGNwZIBlkGagZ7BowGnQavBsAG0QbjBvUHBwcZBysHPQdPB2EHdAeG"
            + "B5kHrAe/B9IH5Qf4CAsIHwgyCEYIWghuCIIIlgiqCL4I0gjnCPsJEAklCToJTwlkCXkJjwmkCboJ"
            + "zwnlCfsKEQonCj0KVApqCoEKmAquCsUK3ArzCwsLIgs5C1ELaQuAC5gLsAvIC+EL+QwSDCoMQwxc"
            + "DHUMjgynDMAM2QzzDQ0NJg1ADVoNdA2ODakNww3eDfgOEw4uDkkOZA5/DpsOtg7SDu4PCQ8lD0EP"
            + "Xg96D5YPsw/PD+wQCRAmEEMQYRB+EJsQuRDXEPURExExEU8RbRGMEaoRyRHoEgcSJhJFEmQShBKj"
            + "EsMS4xMDEyMTQxNjE4MTpBPFE+UUBhQnFEkUahSLFK0UzhTwFRIVNBVWFXgVmxW9FeAWAxYmFkkW"
            + "bBaPFrIW1hb6Fx0XQRdlF4kXrhfSF/cYGxhAGGUYihivGNUY+hkgGUUZaxmRGbcZ3RoEGioaURp3"
            + "Gp4axRrsGxQbOxtjG4obshvaHAIcKhxSHHscoxzMHPUdHh1HHXAdmR3DHeweFh5AHmoelB6+Hukf"
            + "Ex8+H2kflB+/H+ogFSBBIGwgmCDEIPAhHCFIIXUhoSHOIfsiJyJVIoIiryLdIwojOCNmI5QjwiPw"
            + "JB8kTSR8JKsk2iUJJTglaCWXJccl9yYnJlcmhya3JugnGCdJJ3onqyfcKA0oPyhxKKIo1CkGKTgp"
            + "aymdKdAqAio1KmgqmyrPKwIrNitpK50r0SwFLDksbiyiLNctDC1BLXYtqy3hLhYuTC6CLrcu7i8k"
            + "L1ovkS/HL/4wNTBsMKQw2zESMUoxgjG6MfIyKjJjMpsy1DMNM0YzfzO4M/E0KzRlNJ402DUTNU01"
            + "hzXCNf02NzZyNq426TckN2A3nDfXOBQ4UDiMOMg5BTlCOX85vDn5OjY6dDqyOu87LTtrO6o76Dwn"
            + "PGU8pDzjPSI9YT2hPeA+ID5gPqA+4D8hP2E/oj/iQCNAZECmQOdBKUFqQaxB7kIwQnJCtUL3QzpD"
            + "fUPARANER0SKRM5FEkVVRZpF3kYiRmdGq0bwRzVHe0fASAVIS0iRSNdJHUljSalJ8Eo3Sn1KxEsM"
            + "S1NLmkviTCpMcky6TQJNSk2TTdxOJU5uTrdPAE9JT5NP3VAnUHFQu1EGUVBRm1HmUjFSfFLHUxNT"
            + "X1OqU/ZUQlSPVNtVKFV1VcJWD1ZcVqlW91dEV5JX4FgvWH1Yy1kaWWlZuFoHWlZaplr1W0VblVvl"
            + "XDVchlzWXSddeF3JXhpebF69Xw9fYV+zYAVgV2CqYPxhT2GiYfViSWKcYvBjQ2OXY+tkQGSUZOll"
            + "PWWSZedmPWaSZuhnPWeTZ+loP2iWaOxpQ2maafFqSGqfavdrT2una/9sV2yvbQhtYG25bhJua27E"
            + "bx5veG/RcCtwhnDgcTpxlXHwcktypnMBc11zuHQUdHB0zHUodYV14XY+dpt2+HdWd7N4EXhueMx5"
            + "KnmJeed6RnqlewR7Y3vCfCF8gXzhfUF9oX4BfmJ+wn8jf4R/5YBHgKiBCoFrgc2CMIKSgvSDV4O6"
            + "hB2EgITjhUeFq4YOhnKG14c7h5+IBIhpiM6JM4mZif6KZIrKizCLlov8jGOMyo0xjZiN/45mjs6P"
            + "No+ekAaQbpDWkT+RqJIRknqS45NNk7aUIJSKlPSVX5XJljSWn5cKl3WX4JhMmLiZJJmQmfyaaJrV"
            + "m0Kbr5wcnImc951kndKeQJ6unx2fi5/6oGmg2KFHobaiJqKWowajdqPmpFakx6U4pammGqaLpv2n"
            + "bqfgqFKoxKk3qamqHKqPqwKrdavprFys0K1ErbiuLa6hrxavi7AAsHWw6rFgsdayS7LCszizrrQl"
            + "tJy1E7WKtgG2ebbwt2i34LhZuNG5SrnCuju6tbsuu6e8IbybvRW9j74KvoS+/796v/XAcMDswWfB"
            + "48JfwtvDWMPUxFHEzsVLxcjGRsbDx0HHv8g9yLzJOsm5yjjKt8s2y7bMNcy1zTXNtc42zrbPN8+4"
            + "0DnQutE80b7SP9LB00TTxtRJ1MvVTtXR1lXW2Ndc1+DYZNjo2WzZ8dp22vvbgNwF3IrdEN2W3hze"
            + "ot8p36/gNuC94UThzOJT4tvjY+Pr5HPk/OWE5g3mlucf56noMui86Ubp0Opb6uXrcOv77IbtEe2c"
            + "7ijutO9A78zwWPDl8XLx//KM8xnzp/Q09ML1UPXe9m32+/eK+Bn4qPk4+cf6V/rn+3f8B/yY/Sn9"
            + "uv5L/tz/bf//";

    public static void main(String[] args) {
        boolean device = false;
        boolean emulator = false;
        String serial = null;
        String filepath = null;
        boolean landscape = false;

        if (args.length == 0) {
            printUsageAndQuit();
        }

        // parse command line parameters.
        int index = 0;
        do {
            String argument = args[index++];

            if ("-d".equals(argument)) {
                if (emulator || serial != null) {
                    printAndExit("-d conflicts with -e and -s", false /* terminate */);
                }
                device = true;
            } else if ("-e".equals(argument)) {
                if (device || serial != null) {
                    printAndExit("-e conflicts with -d and -s", false /* terminate */);
                }
                emulator = true;
            } else if ("-s".equals(argument)) {
                // quick check on the next argument.
                if (index == args.length) {
                    printAndExit("Missing serial number after -s", false /* terminate */);
                }

                if (device || emulator) {
                    printAndExit("-s conflicts with -d and -e", false /* terminate */);
                }

                serial = args[index++];
            } else if ("-l".equals(argument)) {
                landscape = true;
            } else {
                // get the filepath and break.
                filepath = argument;

                // should not be any other device.
                if (index < args.length) {
                    printAndExit("Too many arguments!", false /* terminate */);
                }
            }
        } while (index < args.length);

        /*
         * If no command-line switches and no serial number was passed on the
         * command-line, try to read a serial number from the shell environment.
         */
        if (!device && !emulator && serial == null) {
            String envSerial = System.getenv("ANDROID_SERIAL");
            if (envSerial != null) {
                serial = envSerial;
            }
        }

        if (filepath == null) {
            printUsageAndQuit();
        }

        Log.setLogOutput(new ILogOutput() {
            @Override
            public void printAndPromptLog(LogLevel logLevel, String tag, String message) {
                System.err.println(logLevel.getStringValue() + ":" + tag + ":" + message);
            }

            @Override
            public void printLog(LogLevel logLevel, String tag, String message) {
                System.err.println(logLevel.getStringValue() + ":" + tag + ":" + message);
            }
        });

        // init the lib
        // [try to] ensure ADB is running
        String adbLocation = getAdbLocation();

        AndroidDebugBridge.init(false /* debugger support */);

        try {
            AndroidDebugBridge bridge = AndroidDebugBridge.createBridge(
                    adbLocation, true /* forceNewBridge */);

            // we can't just ask for the device list right away, as the internal thread getting
            // them from ADB may not be done getting the first list.
            // Since we don't really want getDevices() to be blocking, we wait here manually.
            int count = 0;
            while (!bridge.hasInitialDeviceList()) {
                try {
                    Thread.sleep(100);
                    count++;
                } catch (InterruptedException e) {
                    // pass
                }

                // let's not wait > 10 sec.
                if (count > 100) {
                    System.err.println("Timeout getting device list!");
                    return;
                }
            }

            // now get the devices
            IDevice[] devices = bridge.getDevices();

            if (devices.length == 0) {
                printAndExit("No devices found!", true /* terminate */);
            }

            IDevice target = null;

            if (emulator || device) {
                for (IDevice d : devices) {
                    // this test works because emulator and device can't both be true at the same
                    // time.
                    if (d.isEmulator() == emulator) {
                        // if we already found a valid target, we print an error and return.
                        if (target != null) {
                            if (emulator) {
                                printAndExit("Error: more than one emulator launched!",
                                        true /* terminate */);
                            } else {
                                printAndExit("Error: more than one device connected!",true /* terminate */);
                            }
                        }
                        target = d;
                    }
                }
            } else if (serial != null) {
                for (IDevice d : devices) {
                    if (serial.equals(d.getSerialNumber())) {
                        target = d;
                        break;
                    }
                }
            } else {
                if (devices.length > 1) {
                    printAndExit("Error: more than one emulator or device available!",
                            true /* terminate */);
                }
                target = devices[0];
            }

            if (target != null) {
                try {
                    System.out.println("Taking screenshot from: " + target.getSerialNumber());
                    getDeviceImage(target, filepath, landscape);
                    System.out.println("Success.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                printAndExit("Could not find matching device/emulator.", true /* terminate */);
            }
        } finally {
            AndroidDebugBridge.terminate();
        }
    }

    private static String getAdbLocation() {
        String toolsDir = System.getProperty("com.android.screenshot.bindir"); //$NON-NLS-1$
        if (toolsDir == null) {
            return null;
        }

        File sdk = new File(toolsDir).getParentFile();

        // check if adb is present in platform-tools
        File platformTools = new File(sdk, "platform-tools");
        File adb = new File(platformTools, SdkConstants.FN_ADB);
        if (adb.exists()) {
            return adb.getAbsolutePath();
        }

        // check if adb is present in the tools directory
        adb = new File(toolsDir, SdkConstants.FN_ADB);
        if (adb.exists()) {
            return adb.getAbsolutePath();
        }

        // check if we're in the Android source tree where adb is in $ANDROID_HOST_OUT/bin/adb
        String androidOut = System.getenv("ANDROID_HOST_OUT");
        if (androidOut != null) {
            String adbLocation = androidOut + File.separator + "bin" + File.separator +
                    SdkConstants.FN_ADB;
            if (new File(adbLocation).exists()) {
                return adbLocation;
            }
        }

        return null;
    }


    /*
     * Grab an image from an ADB-connected device.
     */
    private static void getDeviceImage(IDevice device, String filepath, boolean landscape)
            throws IOException {
        RawImage rawImage;

        try {
            rawImage = device.getScreenshot();
        } catch (TimeoutException e) {
            printAndExit("Unable to get frame buffer: timeout", true /* terminate */);
            return;
        } catch (Exception ioe) {
            printAndExit("Unable to get frame buffer: " + ioe.getMessage(), true /* terminate */);
            return;
        }

        // device/adb not available?
        if (rawImage == null)
            return;

        if (landscape) {
            rawImage = rawImage.getRotated();
        }

        // convert raw data to an Image
        BufferedImage image = new BufferedImage(rawImage.width, rawImage.height,
                BufferedImage.TYPE_INT_ARGB);

        int index = 0;
        int IndexInc = rawImage.bpp >> 3;
        for (int y = 0 ; y < rawImage.height ; y++) {
            for (int x = 0 ; x < rawImage.width ; x++) {
                int value = rawImage.getARGB(index);
                index += IndexInc;
                image.setRGB(x, y, value);
            }
        }

        File outFile = new File(filepath);

        ImageWriter pngWriter = getWriter(image, "png");
        if (pngWriter == null) {
            throw new IOException("Failed to find png writer");
        }

        try {
            //noinspection ResultOfMethodCallIgnored
            outFile.delete();

            ImageOutputStream outputStream = ImageIO.createImageOutputStream(outFile);
            pngWriter.setOutput(outputStream);

            if (hasColorSpace(rawImage)) {
                ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(image);
                ImageWriteParam writeParams = pngWriter.getDefaultWriteParam();
                IIOMetadata metadata = pngWriter.getDefaultImageMetadata(type, writeParams);

                byte[] data = deflate(
                        Base64.getDecoder().decode(getIccProfile(rawImage.colorSpace)));

                Node node = metadata.getAsTree("javax_imageio_png_1.0");
                IIOMetadataNode iccp = new IIOMetadataNode("iCCP");
                iccp.setUserObject(data);
                iccp.setAttribute("profileName", getIccProfileName(rawImage.colorSpace));
                iccp.setAttribute("compressionMethod", "deflate");
                node.appendChild(iccp);

                metadata.setFromTree("javax_imageio_png_1.0", node);

                pngWriter.write(new IIOImage(image, null, metadata));
            } else {
                pngWriter.write(image);
            }
            pngWriter.dispose();

            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            throw new IOException("Failed to write PNG output", e);
        }
    }

    private static boolean hasColorSpace(RawImage rawImage) {
        return rawImage.colorSpace != RawImage.COLOR_SPACE_UNKNOWN;
    }

    private static byte[] deflate(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);

        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();

        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            out.write(buffer, 0, count);
        }
        data = out.toByteArray();
        return data;
    }

    @NonNull
    private static String getIccProfileName(int colorSpace) {
        if (colorSpace == RawImage.COLOR_SPACE_DISPLAY_P3) {
            return "Display P3";
        }
        return "sRGB";
    }

    @NonNull
    private static String getIccProfile(int colorSpace) {
        if (colorSpace == RawImage.COLOR_SPACE_DISPLAY_P3) {
            return ICC_PROFILE_DISPLAY_P3;
        }
        return ICC_PROFILE_SRGB;
    }

    private static ImageWriter getWriter(RenderedImage image, String format) {
        ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(image);
        Iterator<ImageWriter> iterator = ImageIO.getImageWriters(type, format);
        if (iterator.hasNext()) {
            return iterator.next();
        } else {
            return null;
        }
    }

    private static void printUsageAndQuit() {
        // 80 cols marker:  01234567890123456789012345678901234567890123456789012345678901234567890123456789
        System.out.println("Usage: screenshot2 [-d | -e | -s SERIAL] [-l] OUT_FILE");
        System.out.println("");
        System.out.println("    -d      Uses the first device found.");
        System.out.println("    -e      Uses the first emulator found.");
        System.out.println("    -s      Targets the device by serial number.");
        System.out.println("");
        System.out.println("    -l      Rotate images for landscape mode.");
        System.out.println("");

        System.exit(1);
    }

    private static void printAndExit(String message, boolean terminate) {
        System.out.println(message);
        if (terminate) {
            AndroidDebugBridge.terminate();
        }
        System.exit(1);
    }
}
