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

package com.android.ide.common.vectordrawable;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.io.Files;
import java.awt.Component;
import java.awt.Dimension;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Calendar;
import javax.swing.DefaultListSelectionModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Support a command line tool to convert SVG files to VectorDrawables and display them.
 */
public class VdCommandLineTool {

    // Show Vector Drawables in a table, below are the parameters for the table.
    private static final int COLUMN_NUMBER = 8;

    private static final int ROW_HEIGHT = 80;

    private static final Dimension MAX_WINDOW_SIZE = new Dimension(600, 600);

    public static final String BROKEN_FILE_EXTENSION = ".broken";

    private static final boolean DBG_COPY_BROKEN_SVG = false;

    private static void exitWithErrorMessage(String message) {
        System.err.println(message);
        System.exit(-1);
    }

    public static void main(String[] args) {
        VdCommandLineOptions options = new VdCommandLineOptions();
        String criticalError = options.parse(args);
        if (criticalError != null) {
            exitWithErrorMessage(criticalError + "\n\n" + VdCommandLineOptions.COMMAND_LINE_OPTION);
        }

        boolean needsConvertSvg = options.getConvertSvg();
        boolean needsDisplayXml = options.getDisplayXml();

        File[] filesToDisplay = options.getInputFiles();
        if (needsConvertSvg) {
            filesToDisplay = convertSVGToXml(options);
        }
        if (needsDisplayXml) {
            displayXmlAsync(filesToDisplay);
        }
    }

    private static void displayXmlAsync(final File[] displayFiles) {
        SwingUtilities.invokeLater(() -> displayXml(displayFiles));
    }

    private static class MyTableModel extends AbstractTableModel {

        private VdIcon[] mIconList;

        public MyTableModel(VdIcon[] iconList) {
            mIconList = iconList;
        }

        @Override
        public String getColumnName(int column) {
            return null;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            int index = rowIndex * COLUMN_NUMBER + columnIndex;
            if (index < 0) {
                return null;
            }
            return mIconList.length > index ? mIconList[index] : null;
        }

        @Override
        public int getRowCount() {
            return mIconList.length / COLUMN_NUMBER +
                    ((mIconList.length % COLUMN_NUMBER == 0) ? 0 : 1);
        }

        @Override
        public int getColumnCount() {
            return Math.min(COLUMN_NUMBER, mIconList.length);
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return Icon.class;
        }
    }

    private static void displayXml(File[] inputFiles) {
        ArrayList<VdIcon> iconList = new ArrayList<>();
        int validXmlFileCounter = 0;
        for (File xmlFile : inputFiles) {
            if (!xmlFile.isFile() || xmlFile.length() == 0) {
                continue;
            }
            String xmlFilename = xmlFile.getName();
            if (xmlFilename.isEmpty()) {
                continue;
            }
            if (!xmlFilename.endsWith(SdkConstants.DOT_XML)) {
                continue;
            }

            try {
                VdIcon icon = new VdIcon(xmlFile.toURI().toURL());
                icon.enableCheckerBoardBackground(true);
                iconList.add(icon);
            } catch (IllegalArgumentException | IOException e) {
                e.printStackTrace();
            }
            validXmlFileCounter++;
        }
        System.out.println("Showing " + validXmlFileCounter + " valid icons");

        MyTableModel model = new MyTableModel(iconList.toArray(new VdIcon[0]));

        JTable table = new JTable(model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                JComponent c = (JComponent)super.prepareRenderer(renderer, row, column);
                VdIcon icon = (VdIcon)getValueAt(row, column);
                c.setToolTipText(icon != null ? icon.getName() : null);
                return c;
            }
        };
        table.setOpaque(false);
        table.setPreferredScrollableViewportSize(MAX_WINDOW_SIZE);
        table.setRowHeight(ROW_HEIGHT);
        table.setSelectionModel(new DefaultListSelectionModel() {
            @Override
            public void setSelectionInterval(int index0, int index1) {
                // do nothing
            }
        });
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        if (table.getPreferredSize().getHeight() > MAX_WINDOW_SIZE.getHeight()) {
            JScrollPane pane = new JScrollPane(table);
            frame.getContentPane().add(pane);
        } else {
            frame.getContentPane().add(table);
        }
        frame.pack();
        frame.setVisible(true);
    }

    private static final String AOSP_HEADER = "<!--\n" +
            "Copyright (C) " + Calendar.getInstance().get(Calendar.YEAR) +
            " The Android Open Source Project\n\n" +
            "   Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
            "    you may not use this file except in compliance with the License.\n" +
            "    You may obtain a copy of the License at\n\n" +

            "         http://www.apache.org/licenses/LICENSE-2.0\n\n" +

            "    Unless required by applicable law or agreed to in writing, software\n" +
            "    distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
            "    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
            "    See the License for the specific language governing permissions and\n" +
            "    limitations under the License.\n" +
            "-->\n";


    private static File[] convertSVGToXml(VdCommandLineOptions options) {
        File[] inputSVGFiles = options.getInputFiles();
        File outputDir = options.getOutputDir();
        int totalSvgFileCounter = 0;
        int errorSvgFileCounter = 0;
        ArrayList<File> allOutputFiles = new ArrayList<>();
        for (File inputSVGFile : inputSVGFiles) {
            String svgFilename = inputSVGFile.getName();
            if (!svgFilename.endsWith(SdkConstants.DOT_SVG)) {
                continue;
            }
            String svgFilenameWithoutExtension = svgFilename.substring(0,
                    svgFilename.lastIndexOf('.'));

            File outputFile = new File(outputDir,
                    svgFilenameWithoutExtension + SdkConstants.DOT_XML);
            allOutputFiles.add(outputFile);
            try {
                ByteArrayOutputStream byteArrayOutStream = new ByteArrayOutputStream();

                String error = Svg2Vector.parseSvgToXml(inputSVGFile, byteArrayOutStream);

                if (!error.isEmpty()) {
                    errorSvgFileCounter++;
                    System.err.println("error is " + error);
                    if (DBG_COPY_BROKEN_SVG) {
                        // Copy the broken svg file in the same directory but with a new extension.
                        String brokenFileName = svgFilename + BROKEN_FILE_EXTENSION;
                        File brokenSvgFile = new File(outputDir, brokenFileName);
                        Files.copy(inputSVGFile, brokenSvgFile);
                    }
                }

                // Override the size info if needed. Negative value will be ignored.
                String vectorXmlContent = byteArrayOutStream.toString();
                if (options.getForceHeight() > 0 || options.getForceWidth() > 0) {
                    Document vdDocument = parseVdStringIntoDocument(vectorXmlContent, null);

                    if (vdDocument != null) {
                        VdOverrideInfo info = new VdOverrideInfo(options.getForceWidth(),
                                options.getForceHeight(), null, 1,
                                false /*auto mirrored*/);
                        vectorXmlContent = VdPreview.overrideXmlContent(vdDocument, info, null);
                    }
                }
                if (options.isAddHeader()) {
                    vectorXmlContent = AOSP_HEADER + vectorXmlContent;
                }

                // Write the final result into the output XML file.
                try (PrintWriter writer = new PrintWriter(outputFile)) {
                    writer.print(vectorXmlContent);
                }
            } catch (Exception e) {
                System.err.println("exception" + e.getMessage());
                e.printStackTrace();
            }
            totalSvgFileCounter++;
        }

        System.out.println("Convert " + totalSvgFileCounter + " SVG files in total, errors found in "
                + errorSvgFileCounter + " files");
        return allOutputFiles.toArray(new File[0]);
    }

    /**
     * Parses a vector drawable XML file into a {@link Document} object.
     *
     * @param xmlFileContent the content of the VectorDrawable's XML file.
     * @param errorLog when errors were found, log them in this builder if it is not null.
     * @return parsed document or null if errors happened.
     */
    @Nullable
    private static Document parseVdStringIntoDocument(
            @NonNull String xmlFileContent, @Nullable StringBuilder errorLog) {
        try {
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            return db.parse(new InputSource(new StringReader(xmlFileContent)));
        } catch (Exception e) {
            if (errorLog != null) {
                errorLog.append("Exception while parsing XML file:\n").append(e.getMessage());
            }
            return null;
        }
    }
}
