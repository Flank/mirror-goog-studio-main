package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.annotations.NonNull;
import com.android.tools.perflib.heap.Instance;
import com.google.common.escape.Escaper;
import com.google.common.html.HtmlEscapers;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Base64;

import javax.imageio.ImageIO;

/**
 * Prints summary documents as HTML.
 *
 * <p>This printer is designed to match the output of ahat's AhatPrinter as closely as possible.
 * Specifically, see formatInstance.
 */
public final class HtmlPrinter implements Printer {

    private final PrintStream mOutStream;
    private final Escaper mEscaper;

    // Number of characters of a String instance that will be shown when calling formatInstance.
    // Picked 100 characters to give context to the string, but to prevent long strings from taking
    // too much space. Feel free to change this in the future.
    private static final int MAX_PREVIEW_STRING_LENGTH = 100;

    public HtmlPrinter(@NonNull Path path) throws FileNotFoundException {
        this(new PrintStream(path.toFile()));
    }

    public HtmlPrinter(@NonNull PrintStream outStream) {
        mOutStream = outStream;
        mEscaper = HtmlEscapers.htmlEscaper();
    }

    @Override
    public void addHeading(int level, @NonNull String content) {
        mOutStream.printf("<h%d>%s</h%1$d>\n", level, mEscaper.escape(content));
    }

    @Override
    public void addParagraph(@NonNull String content) {
        mOutStream.printf("<p>%s</p>\n", mEscaper.escape(content));
    }

    @Override
    public void startTable(@NonNull String... columnHeadings) {
        mOutStream.printf("<table>\n");

        if (columnHeadings.length > 0) {
            mOutStream.printf("<tr style='border: 1px solid black;'>\n");
            for (String column : columnHeadings) {
                mOutStream.printf("<th style='border: 1px solid black;'>%s</th>\n",
                        mEscaper.escape(column));
            }
            mOutStream.printf("</tr>\n");
        }
    }

    @Override
    public void addRow(@NonNull String... values) {
        mOutStream.printf("<tr>\n");
        for (String value : values) {
            mOutStream.printf("<td>%s</td>\n", mEscaper.escape(value));
        }
        mOutStream.printf("</tr>\n");
    }

    @Override
    public void endTable() {
        mOutStream.printf("</table>\n");
    }

    @Override
    public void addImage(@NonNull Instance instance) {
        if (!HprofBitmapProvider.canGetBitmapFromInstance(instance)) {
            return;
        }
        try {
            HprofBitmapProvider bitmapProvider = new HprofBitmapProvider(instance);
            String configName = bitmapProvider.getBitmapConfigName();
            int width = bitmapProvider.getDimension().width;
            int height = bitmapProvider.getDimension().height;

            byte[] raw = bitmapProvider.getPixelBytes(new Dimension());
            int[] converted;
            if ("\"ARGB_8888\"".equals(configName)) {
                converted = new int[width * height];
                for (int i = 0; i < converted.length; i++) {
                    converted[i] = (
                            (((int) raw[i * 4 + 3] & 0xFF) << 24)
                                    + (((int) raw[i * 4 + 0] & 0xFF) << 16)
                                    + (((int) raw[i * 4 + 1] & 0xFF) << 8)
                                    + ((int) raw[i * 4 + 2] & 0xFF));
                }
            } else {
                throw new Exception("RGB_565/ALPHA_8 conversion not implemented");
            }

            int imageType = -1;
            switch (configName) {
                case "\"ARGB_8888\"":
                    imageType = BufferedImage.TYPE_4BYTE_ABGR;
                    break;
                case "\"RGB_565\"":
                    imageType = BufferedImage.TYPE_USHORT_565_RGB;
                    break;
                case "\"ALPHA_8\"":
                    imageType = BufferedImage.TYPE_BYTE_GRAY;
                    break;
            }

            BufferedImage image = new BufferedImage(width, height, imageType);
            image.setRGB(0, 0, width, height, converted, 0, width);

            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", byteOutputStream);
            byteOutputStream.flush();
            String imageDataString =
                    Base64.getEncoder().encodeToString(byteOutputStream.toByteArray());
            byteOutputStream.close();

            mOutStream.printf("<img src='data:image/png;base64,%s' \\>\n", imageDataString);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    @Override
    public String formatInstance(@NonNull Instance instance) {
        return instance.toString();
    }

    /**
     * Convert a {@link BufferedImage} into a Base64 string.
     *
     * @return the string, or null if an exception occurred.
     */
    private String bitmapAsBase64String(@NonNull BufferedImage image) {
        try {
            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", byteOutputStream);
            byteOutputStream.flush();
            String imageDataString =
                    Base64.getEncoder().encodeToString(byteOutputStream.toByteArray());
            byteOutputStream.close();
            return imageDataString;
        } catch (IOException e) {
            return null;
        }
    }
}
