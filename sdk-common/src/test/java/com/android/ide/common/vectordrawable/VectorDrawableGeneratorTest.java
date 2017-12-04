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

import com.android.ide.common.util.GeneratorTester;
import com.android.testutils.TestResources;
import com.google.common.io.Files;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import junit.framework.TestCase;
import org.junit.Assert;

/** Tests for {@link Svg2Vector} and {@link VdPreview} classes. */
public class VectorDrawableGeneratorTest extends TestCase {
    private static final int IMAGE_SIZE = 64;
    private static final float DIFF_THRESHOLD_PERCENT = 0.1f;

    private static final GeneratorTester GENERATOR_TESTER =
            GeneratorTester.withTestDataRelativePath(
                    "tools/base/sdk-common/src/test/resources/testData/vectordrawable");

    private enum FileType {
        SVG,
        XML
    }

    private void checkVectorConversion(String testFileName, FileType type,
                                       boolean dumpXml, String expectedError) throws Exception {
        String incomingFileName;
        if (type == FileType.SVG) {
            incomingFileName = testFileName + ".svg";
        } else {
            incomingFileName = testFileName + ".xml";
        }
        String imageName = testFileName + ".png";

        String parentDir =  "vectordrawable" + File.separator;
        File parentDirFile = TestResources.getDirectory(getClass(), "/testData/vectordrawable");

        File incomingFile = new File(parentDirFile, incomingFileName);
        String xmlContent;
        if (type == FileType.SVG) {
            OutputStream outStream = new ByteArrayOutputStream();
            String errorLog = Svg2Vector.parseSvgToXml(incomingFile, outStream);
            if (expectedError != null) {
                TestCase.assertNotNull(errorLog);
                TestCase.assertFalse(errorLog.isEmpty());
                TestCase.assertTrue(errorLog.contains(expectedError));
            }
            xmlContent = outStream.toString();
            if (xmlContent == null || xmlContent.isEmpty()) {
                TestCase.fail("Empty Xml file.");
            }
            if (dumpXml) {
                File tempXmlFile = new File(parentDirFile, imageName + ".xml");
                try (PrintWriter writer = new PrintWriter(tempXmlFile)) {
                    writer.println(xmlContent);
                }
            }
        } else {
            xmlContent = Files.asCharSource(incomingFile, StandardCharsets.UTF_8).read();
        }

        VdPreview.TargetSize imageTargetSize = VdPreview.TargetSize.createSizeFromWidth(IMAGE_SIZE);
        StringBuilder builder = new StringBuilder();
        BufferedImage image =
                VdPreview.getPreviewFromVectorXml(imageTargetSize, xmlContent, builder);

        String imageNameWithParent = parentDir + imageName;
        File pngFile = new File(parentDirFile, imageName);
        if (!pngFile.exists()) {
            String golden = imageNameWithParent;
            String path = parentDirFile.getPath();
            int pos = path.replace('\\', '/').indexOf("/tools/base/");
            if (pos > 0) {
                golden = path.substring(0, pos) + File.separator
                        + GENERATOR_TESTER.getTestDataRelPath() + File.separator + imageName;
            }
            GENERATOR_TESTER.generateGoldenImage(image, golden, imageName);
            fail("Golden file " + golden + " didn't exist, created by the test.");
        } else {
            InputStream is = new FileInputStream(pngFile);
            BufferedImage goldenImage = ImageIO.read(is);
            GeneratorTester.assertImageSimilar(
                    imageNameWithParent, goldenImage, image, DIFF_THRESHOLD_PERCENT);
        }
    }

    private void checkSvgConversion(String fileName) throws Exception {
        checkVectorConversion(fileName, FileType.SVG, false, null);
    }

    private void checkXmlConversion(String filename) throws Exception {
        checkVectorConversion(filename, FileType.XML, false, null);
    }

    private void checkSvgConversionAndContainsError(String filename, String errorLog)
            throws Exception {
        checkVectorConversion(filename, FileType.SVG, false, errorLog);
    }

    private void checkSvgConversionDebug(String fileName) throws Exception {
        checkVectorConversion(fileName, FileType.SVG, true, null);
    }

    //////////////////////////////////////////////////////////
    // Tests starts here:
    public void testSvgFillAlpha() throws Exception {
        checkSvgConversion("ic_add_to_notepad_black");
    }

    public void testSvgArcto1() throws Exception {
        checkSvgConversion("test_arcto_1");
    }

    public void testSvgControlPoints01() throws Exception {
        checkSvgConversion("test_control_points_01");
    }

    public void testSvgControlPoints02() throws Exception {
        checkSvgConversion("test_control_points_02");
    }

    public void testSvgControlPoints03() throws Exception {
        checkSvgConversion("test_control_points_03");
    }

    public void testSvgContentCut() throws Exception {
        checkSvgConversion("ic_content_cut_24px");
    }

    public void testSvgInput() throws Exception {
        checkSvgConversion("ic_input_24px");
    }

    public void testSvgLiveHelp() throws Exception {
        checkSvgConversion("ic_live_help_24px");
    }

    public void testSvgLocalLibrary() throws Exception {
        checkSvgConversion("ic_local_library_24px");
    }

    public void testSvgLocalPhone() throws Exception {
        checkSvgConversion("ic_local_phone_24px");
    }

    public void testSvgMicOff() throws Exception {
        checkSvgConversion("ic_mic_off_24px");
    }

    public void testSvgShapes() throws Exception {
        checkSvgConversion("ic_shapes");
    }

    public void testSvgEllipse() throws Exception {
        checkSvgConversion("test_ellipse");
    }

    public void testSvgTempHigh() throws Exception {
        checkSvgConversion("ic_temp_high");
    }

    public void testSvgPlusSign() throws Exception {
        checkSvgConversion("ic_plus_sign");
    }

    public void testSvgPolylineStrokeWidth() throws Exception {
        checkSvgConversion("ic_polyline_strokewidth");
    }

    // Preview broken on linux, fine on chrome browser
    public void testSvgStrokeWidthTransform() throws Exception {
        checkSvgConversionAndContainsError("ic_strokewidth_transform",
                "We don't scale the stroke width!");
    }

    public void testSvgEmptyAttributes() throws Exception {
        checkSvgConversion("ic_empty_attributes");
    }

    public void testSvgSimpleGroupInfo() throws Exception {
        checkSvgConversion("ic_simple_group_info");
    }

    public void testSvgContainsError() throws Exception {
        checkSvgConversionAndContainsError("ic_contains_ignorable_error",
                "ERROR@ line 16 <switch> is not supported\n"
                        + "ERROR@ line 17 <foreignObject> is not supported");
    }

    public void testSvgLineToMoveTo() throws Exception {
        checkSvgConversion("test_lineto_moveto");
    }

    public void testSvgLineToMoveTo2() throws Exception {
        checkSvgConversion("test_lineto_moveto2");
    }

    public void testSvgLineToMoveToViewbox1() throws Exception {
        checkSvgConversion("test_lineto_moveto_viewbox1");
    }

    public void testSvgLineToMoveToViewbox2() throws Exception {
        checkSvgConversion("test_lineto_moveto_viewbox2");
    }

    public void testSvgLineToMoveToViewbox3() throws Exception {
        checkSvgConversion("test_lineto_moveto_viewbox3");
    }

    // It seems like different implementations has different results on this svg.
    public void testSvgLineToMoveToViewbox4() throws Exception {
        checkSvgConversion("test_lineto_moveto_viewbox4");
    }

    public void testSvgLineToMoveToViewbox5() throws Exception {
        checkSvgConversion("test_lineto_moveto_viewbox5");
    }

    public void testSvgColorFormats() throws Exception {
        checkSvgConversion("test_color_formats");
    }

    public void testSvgTransformArcComplex1() throws Exception {
        checkSvgConversion("test_transform_arc_complex1");
    }

    public void testSvgTransformArcComplex2() throws Exception {
        checkSvgConversion("test_transform_arc_complex2");
    }

    public void testSvgTransformArcRotateScaleTranslate() throws Exception {
        checkSvgConversion("test_transform_arc_rotate_scale_translate");
    }

    public void testSvgTransformArcScale() throws Exception {
        checkSvgConversion("test_transform_arc_scale");
    }

    public void testSvgTransformArcScaleRotate() throws Exception {
        checkSvgConversion("test_transform_arc_scale_rotate");
    }

    public void testSvgTransformArcSkewx() throws Exception {
        checkSvgConversion("test_transform_arc_skewx");
    }

    public void testSvgTransformArcSkewy() throws Exception {
        checkSvgConversion("test_transform_arc_skewy");
    }

    public void testSvgTransformBigArcComplex() throws Exception {
        checkSvgConversion("test_transform_big_arc_complex");
    }

    public void testSvgTransformBigArcComplexViewbox() throws Exception {
        checkSvgConversion("test_transform_big_arc_complex_viewbox");
    }

    public void testSvgTransformBigArcScale() throws Exception {
        checkSvgConversion("test_transform_big_arc_translate_scale");
    }

    public void testSvgTransformDegenerateArc() throws Exception {
        checkSvgConversion("test_transform_degenerate_arc");
    }

    public void testSvgTransformCircleRotate() throws Exception {
        checkSvgConversion("test_transform_circle_rotate");
    }

    public void testSvgTransformCircleScale() throws Exception {
        checkSvgConversion("test_transform_circle_scale");
    }

    public void testSvgTransformCircleMatrix() throws Exception {
        checkSvgConversion("test_transform_circle_matrix");
    }

    public void testSvgTransformRectMatrix() throws Exception {
        checkSvgConversion("test_transform_rect_matrix");
    }

    public void testSvgTransformRoundRectMatrix() throws Exception {
        checkSvgConversion("test_transform_round_rect_matrix");
    }

    public void testSvgTransformRectRotate() throws Exception {
        checkSvgConversion("test_transform_rect_rotate");
    }

    public void testSvgTransformRectScale() throws Exception {
        checkSvgConversion("test_transform_rect_scale");
    }

    public void testSvgTransformRectSkewx() throws Exception {
        checkSvgConversion("test_transform_rect_skewx");
    }

    public void testSvgTransformRectSkewy() throws Exception {
        checkSvgConversion("test_transform_rect_skewy");
    }

    public void testSvgTransformRectTranslate() throws Exception {
        checkSvgConversion("test_transform_rect_translate");
    }

    public void testSvgTransformHVLoopBasic() throws Exception {
        checkSvgConversion("test_transform_h_v_loop_basic");
    }

    public void testSvgTransformHVLoopTranslate() throws Exception {
        checkSvgConversion("test_transform_h_v_loop_translate");
    }

    public void testSvgTransformHVLoopMatrix() throws Exception {
        checkSvgConversion("test_transform_h_v_loop_matrix");
    }

    public void testSvgTransformHVACComplex() throws Exception {
        checkSvgConversion("test_transform_h_v_a_c_complex");
    }

    public void testSvgTransformHVAComplex() throws Exception {
        checkSvgConversion("test_transform_h_v_a_complex");
    }

    public void testSvgTransformHVCQ() throws Exception {
        checkSvgConversion("test_transform_h_v_c_q");
    }

    public void testSvgTransformHVCQComplex() throws Exception {
        checkSvgConversion("test_transform_h_v_c_q_complex");
    }

    // Preview broken on linux, fine on chrome browser
    public void testSvgTransformHVLoopComplex() throws Exception {
        checkSvgConversion("test_transform_h_v_loop_complex");
    }

    public void testSvgTransformHVSTComplex() throws Exception {
        checkSvgConversion("test_transform_h_v_s_t_complex");
    }

    public void testSvgTransformHVSTComplex2() throws Exception {
        checkSvgConversion("test_transform_h_v_s_t_complex2");
    }

    public void testSvgTransformCQNoMove() throws Exception {
        checkSvgConversion("test_transform_c_q_no_move");
    }
    // Preview broken on linux, fine on chrome browser
    public void testSvgTransformMultiple1() throws Exception {
        checkSvgConversion("test_transform_multiple_1");
    }

    // Preview broken on linux, fine on chrome browser
    public void testSvgTransformMultiple2() throws Exception {
        checkSvgConversion("test_transform_multiple_2");
    }

    // Preview broken on linux, fine on chrome browser
    public void testSvgTransformMultiple3() throws Exception {
        checkSvgConversion("test_transform_multiple_3");
    }

    public void testSvgTransformMultiple4() throws Exception {
        checkSvgConversion("test_transform_multiple_4");
    }

    public void testSvgTransformGroup1() throws Exception {
        checkSvgConversion("test_transform_group_1");
    }

    public void testSvgTransformGroup2() throws Exception {
        checkSvgConversion("test_transform_group_2");
    }

    public void testSvgTransformGroup3() throws Exception {
        checkSvgConversion("test_transform_group_3");
    }

    public void testSvgTransformGroup4() throws Exception {
        checkSvgConversion("test_transform_group_4");
    }

    public void testSvgTransformEllipseRotateScaleTranslate() throws Exception {
        checkSvgConversion("test_transform_ellipse_rotate_scale_translate");
    }

    public void testSvgTransformEllipseComplex() throws Exception {
        checkSvgConversion("test_transform_ellipse_complex");
    }

    public void testSvgMoveAfterCloseTransform() throws Exception {
        checkSvgConversion("test_move_after_close");
    }

    public void testSvgMoveAfterClose() throws Exception {
        checkSvgConversion("test_move_after_close_transform");
    }

    public void testSvgFillRuleEvenOdd() throws Exception {
        checkSvgConversion("test_fill_type_evenodd");
    }

    public void testSvgFillRuleNonzero() throws Exception {
        checkSvgConversion("test_fill_type_nonzero");
    }

    public void testSvgFillRuleNoRule() throws Exception {
        checkSvgConversion("test_fill_type_no_rule");
    }

    public void testSvgDefsUseTransform() throws Exception {
        checkSvgConversion("test_defs_use_shape2");
    }

    public void testSvgDefsUseColors() throws Exception {
        checkSvgConversion("test_defs_use_colors");
    }

    public void testSvgDefsUseNoGroup() throws Exception {
        checkSvgConversion("test_defs_use_no_group");
    }

    public void testSvgDefsUseNestedGroups() throws Exception {
        checkSvgConversion("test_defs_use_nested_groups");
    }

    public void testSvgDefsUseNestedGroups2() throws Exception {
        checkSvgConversion("test_defs_use_nested_groups2");
    }

    public void testSvgUseWithoutDefs() throws Exception {
        checkSvgConversion("test_use_no_defs");
    }

    public void testSvgDefsUseMultiAttrib() throws Exception {
        checkSvgConversion("test_defs_use_multi_attr");
    }

    public void testSvgDefsUseTransformRotate() throws Exception {
        checkSvgConversion("test_defs_use_transform");
    }

    public void testSvgDefsUseTransformInDefs() throws Exception {
        checkSvgConversion("test_defs_use_transform2");
    }

    public void testSvgDefsUseOrderMatters() throws Exception {
        checkSvgConversion("test_defs_use_use_first");
    }

    // Clip Path Tests
    public void testSvgClipPathGroup() throws Exception {
        checkSvgConversion("test_clip_path_group");
    }

    public void testSvgClipPathGroup2() throws Exception {
        checkSvgConversion("test_clip_path_group_2");
    }

    public void testSvgClipPathTranslateAffected() throws Exception {
        checkSvgConversion("test_clip_path_group_translate");
    }

    public void testSvgClipPathIsGroup() throws Exception {
        checkSvgConversion("test_clip_path_is_group");
    }

    public void testSvgClipPathMultiShapeClip() throws Exception {
        checkSvgConversion("test_clip_path_mult_clip");
    }

    public void testSvgClipPathOverGroup() throws Exception {
        checkSvgConversion("test_clip_path_over_group");
    }

    public void testSvgClipPathRect() throws Exception {
        checkSvgConversion("test_clip_path_rect");
    }

    public void testSvgClipPathRectOverClipPath() throws Exception {
        checkSvgConversion("test_clip_path_rect_over_circle");
    }

    public void testSvgClipPathTwoRect() throws Exception {
        checkSvgConversion("test_clip_path_two_rect");
    }

    public void testSvgClipPathSinglePath() throws Exception {
        checkSvgConversion("test_clip_path_path_over_rect");
    }

    // Style tests start here
    public void testSvgStyleBasicShapes() throws Exception {
        checkSvgConversion("test_style_basic_shapes");
    }

    public void testSvgStyleBlobfish() throws Exception {
        checkSvgConversion("test_style_blobfish");
    }

    public void testSvgStyleCircle() throws Exception {
        checkSvgConversion("test_style_circle");
    }

    public void testSvgStyleGroup() throws Exception {
        checkSvgConversion("test_style_group");
    }

    public void testSvgStyleGroupClipPath() throws Exception {
        checkSvgConversion("test_style_group_clip_path");
    }

    public void testSvgStyleGroupDuplicateAttr() throws Exception {
        checkSvgConversion("test_style_group_duplicate_attr");
    }

    public void testSvgStyleMultiClass() throws Exception {
        checkSvgConversion("test_style_multi_class");
    }

    public void testSvgStyleTwoShapes() throws Exception {
        checkSvgConversion("test_style_two_shapes");
    }

    public void testSvgStylePathClassNames() throws Exception {
        checkSvgConversion("test_style_path_class_names");
    }

    public void testSvgStyleShortVersion() throws Exception {
        checkSvgConversion("test_style_short_version");
    }

    // Gradient tests start here
    // The following gradient test files currently fail and do not have corresponding test cases:
    // test_gradient_linear_transform_matrix
    // test_gradient_linear_transform_matrix_2
    // test_gradient_linear_transform_scale_rotate
    // test_gradient_linear_transform_scale_translate_rotate
    public void testSvgGradientLinearCoordinatesNegativePercentage() throws Exception {
        checkSvgConversion("test_gradient_linear_coordinates_negative_percentage");
    }

    public void testSvgGradientLinearNoCoordinates() throws Exception {
        checkSvgConversion("test_gradient_linear_no_coordinates");
    }

    public void testSvgGradientLinearNoUnits() throws Exception {
        checkSvgConversion("test_gradient_linear_no_units");
    }

    public void testSvgGradientLinearObjectBoundingBox() throws Exception {
        checkSvgConversion("test_gradient_linear_object_bounding_box");
    }

    public void testSvgGradientLinearOffsetDecreasing() throws Exception {
        checkSvgConversion("test_gradient_linear_offset_decreasing");
    }

    public void testSvgGradientLinearOffsetOutOfBounds() throws Exception {
        checkSvgConversion("test_gradient_linear_offset_out_of_bounds");
    }

    public void testSvgGradientLinearOffsetUndefined() throws Exception {
        checkSvgConversion("test_gradient_linear_offset_undefined");
    }

    public void testSvgGradientLinearOneStop() throws Exception {
        checkSvgConversion("test_gradient_linear_one_stop");
    }

    public void testSvgGradientLinearOverlappingStops() throws Exception {
        checkSvgConversion("test_gradient_linear_overlapping_stops");
    }

    public void testSvgGradientLinearSpreadPad() throws Exception {
        checkSvgConversion("test_gradient_linear_spread_pad");
    }

    public void testSvgGradientLinearSpreadReflect() throws Exception {
        checkSvgConversion("test_gradient_linear_spread_reflect");
    }

    public void testSvgGradientLinearSpreadRepeat() throws Exception {
        checkSvgConversion("test_gradient_linear_spread_repeat");
    }

    public void testSvgGradientLinearStopOpacity() throws Exception {
        checkSvgConversion("test_gradient_linear_stop_opacity");
    }

    public void testSvgGradientLinearStopOpacityHalf() throws Exception {
        checkSvgConversion("test_gradient_linear_stop_opacity_half");
    }

    public void testSvgGradientLinearStroke() throws Exception {
        checkSvgConversion("test_gradient_linear_stroke");
    }

    public void testSvgGradientLinearThreeStops() throws Exception {
        checkSvgConversion("test_gradient_linear_three_stops");
    }

    public void testSvgGradientLinearTransformGroupScaleTranslate() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_group_scale_translate");
    }

    public void testSvgGradientLinearTransformMatrix3() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_matrix_3");
    }

    public void testSvgGradientLinearTransformMatrixScale() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_matrix_scale");
    }

    public void testSvgGradientLinearTransformRotate() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_rotate");
    }

    public void testSvgGradientLinearTransformRotateScale() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_rotate_scale");
    }

    public void testSvgGradientLinearTransformRotateTranslateScale() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_rotate_translate_scale");
    }

    public void testSvgGradientLinearTransformScale() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_scale");
    }

    public void testSvgGradientLinearTransformTranslate() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_translate");
    }

    public void testSvgGradientLinearTransformTranslateRotate() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_translate_rotate");
    }

    public void testSvgGradientLinearTransformTranslateRotateScale() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_translate_rotate_scale");
    }

    public void testSvgGradientLinearTransformTranslateScale() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_translate_scale");
    }

    public void testSvgGradientLinearTransformTranslateScaleShapeTransform() throws Exception {
        checkSvgConversion("test_gradient_linear_transform_translate_scale_shape_transform");
    }

    public void testSvgGradientLinearUserSpaceOnUse() throws Exception {
        checkSvgConversion("test_gradient_linear_user_space_on_use");
    }

    public void testSvgGradientLinearXYNumbers() throws Exception {
        checkSvgConversion("test_gradient_linear_x_y_numbers");
    }

    public void testGradientObjectTransformation() throws Exception {
        checkSvgConversion("test_gradient_object_transformation");
    }

    public void testSvgGradientComplex() throws Exception {
        checkSvgConversion("test_gradient_complex");
    }

    public void testSvgGradientComplex2() throws Exception {
        checkSvgConversion("test_gradient_complex_2");
    }

    public void testSvgGradientRadialCoordinates() throws Exception {
        checkSvgConversion("test_gradient_radial_coordinates");
    }

    public void testSvgGradientRadialNoCoordinates() throws Exception {
        checkSvgConversion("test_gradient_radial_no_coordinates");
    }

    public void testSvgGradientRadialNoStops() throws Exception {
        checkVectorConversion(
                "test_gradient_radial_no_stops", FileType.SVG, false, "has no stop info");
    }

    public void testSvgGradientRadialNoUnits() throws Exception {
        checkSvgConversion("test_gradient_radial_no_units");
    }

    public void testSvgGradientRadialObjectBoundingBox() throws Exception {
        checkSvgConversion("test_gradient_radial_object_bounding_box");
    }

    public void testSvgGradientRadialOneStop() throws Exception {
        checkSvgConversion("test_gradient_radial_one_stop");
    }

    public void testSvgGradientRadialOverlappingStops() throws Exception {
        checkSvgConversion("test_gradient_radial_overlapping_stops");
    }

    public void testSvgGradientRadialRNegative() throws Exception {
        checkSvgConversion("test_gradient_radial_r_negative");
    }

    public void testSvgGradientRadialRZero() throws Exception {
        checkSvgConversion("test_gradient_radial_r_zero");
    }

    public void testSvgGradientRadialSpreadPad() throws Exception {
        checkSvgConversion("test_gradient_radial_spread_pad");
    }

    public void testSvgGradientRadialSpreadReflect() throws Exception {
        checkSvgConversion("test_gradient_radial_spread_reflect");
    }

    public void testSvgGradientRadialSpreadRepeat() throws Exception {
        checkSvgConversion("test_gradient_radial_spread_repeat");
    }

    public void testSvgGradientRadialStopOpacity() throws Exception {
        checkSvgConversion("test_gradient_radial_stop_opacity");
    }

    public void testSvgGradientRadialStopOpacityFraction() throws Exception {
        checkSvgConversion("test_gradient_radial_stop_opacity_fraction");
    }

    public void testSvgGradientRadialStroke() throws Exception {
        checkSvgConversion("test_gradient_radial_stroke");
    }

    public void testSvgGradientRadialThreeStops() throws Exception {
        checkSvgConversion("test_gradient_radial_three_stops");
    }

    public void testSvgGradientRadialUserSpaceOnUse() throws Exception {
        checkSvgConversion("test_gradient_radial_user_space_on_use");
    }

    public void testSvgGradientRadialUserSpace2() throws Exception {
        checkSvgConversion("test_gradient_radial_user_space_2");
    }

    public void testSvgGradientRadialTransformTranslate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_translate");
    }

    public void testSvgGradientRadialTransformTranslateUserSpace() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_translate_userspace");
    }

    public void testSvgGradientRadialTransformTranslateScale() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_translate_scale");
    }

    public void testSvgGradientRadialTransformScaleTranslate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_scale_translate");
    }

    public void testSvgGradientRadialTransformMatrix() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_matrix");
    }

    public void testSvgGradientRadialTransformRotate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_rotate");
    }

    public void testSvgGradientRadialTransformRotateScale() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_rotate_scale");
    }

    public void testSvgGradientRadialTransformRotateScaleTranslate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_rotate_scale_translate");
    }

    public void testSvgGradientRadialTransformRotateTranslate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_rotate_translate");
    }

    public void testSvgGradientRadialTransformRotateTranslateScale() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_rotate_translate_scale");
    }

    public void testSvgGradientRadialTransformScale() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_scale");
    }

    public void testSvgGradientRadialTransformScaleRotate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_scale_rotate");
    }

    public void testSvgGradientRadialTransformScaleRotateTranslate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_scale_rotate_translate");
    }

    public void testSvgGradientRadialTransformScaleTranslateRotate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_scale_translate_rotate");
    }

    public void testSvgGradientRadialTransformTranslateRotate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_translate_rotate");
    }

    public void testSvgGradientRadialTransformTranslateRotateScale() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_translate_rotate_scale");
    }

    public void testSvgGradientRadialTransformTranslateScaleRotate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_translate_scale_rotate");
    }

    public void testSvgGradientRadialTransformTranslateGroupScaleTranslate() throws Exception {
        checkSvgConversion("test_gradient_radial_transform_translate_group_scale_translate");
    }

    public void testSvgGradientRadialUnitsAsNumbers() throws Exception {
        checkSvgConversion("test_gradient_radial_units_as_numbers");
    }

    public void testSvgGradientRadialCoordinatesNegativePercentage() throws Exception {
        checkSvgConversion("test_gradient_radial_coordinates_negative_percentage");
    }

    // XML files start here.
    public void testXmlIconSizeOpacity() throws Exception {
        checkXmlConversion("ic_size_opacity");
    }

    public void testXmlColorFormats() throws Exception {
        checkXmlConversion("test_xml_color_formats");
    }

    public void testXmlColorAlpha() throws Exception {
        checkXmlConversion("test_fill_stroke_alpha");
    }

    public void testXmlTransformation1() throws Exception {
        checkXmlConversion("test_xml_transformation_1");
    }

    public void testXmlTransformation2() throws Exception {
        checkXmlConversion("test_xml_transformation_2");
    }

    public void testXmlTransformation3() throws Exception {
        checkXmlConversion("test_xml_transformation_3");
    }

    public void testXmlTransformation4() throws Exception {
        checkXmlConversion("test_xml_transformation_4");
    }

    public void testXmlTransformation5() throws Exception {
        checkXmlConversion("test_xml_transformation_5");
    }

    public void testXmlTransformation6() throws Exception {
        checkXmlConversion("test_xml_transformation_6");
    }

    public void testXmlScaleStroke1() throws Exception {
        checkXmlConversion("test_xml_scale_stroke_1");
    }

    public void testXmlScaleStroke2() throws Exception {
        checkXmlConversion("test_xml_scale_stroke_2");
    }

    public void testXmlRenderOrder1() throws Exception {
        checkXmlConversion("test_xml_render_order_1");
    }

    public void testXmlRenderOrder2() throws Exception {
        checkXmlConversion("test_xml_render_order_2");
    }

    public void testXmlRepeatedA1() throws Exception {
        checkXmlConversion("test_xml_repeated_a_1");
    }

    public void testXmlRepeatedA2() throws Exception {
        checkXmlConversion("test_xml_repeated_a_2");
    }

    public void testXmlRepeatedCQ() throws Exception {
        checkXmlConversion("test_xml_repeated_cq");
    }

    public void testXmlRepeatedST() throws Exception {
        checkXmlConversion("test_xml_repeated_st");
    }

    public void testXmlStroke1() throws Exception {
        checkXmlConversion("test_xml_stroke_1");
    }

    public void testXmlStroke2() throws Exception {
        checkXmlConversion("test_xml_stroke_2");
    }

    public void testXmlStroke3() throws Exception {
        checkXmlConversion("test_xml_stroke_3");
    }

    public void testPathDataInStringResource() throws Exception {
        try {
            checkXmlConversion("test_pathData_in_string_resource");
            fail("Expecting exception.");
        } catch (ResourcesNotSupportedException e) {
            Assert.assertEquals("@string/pathDataAsString", e.getValue());
            Assert.assertEquals("android:pathData", e.getName());
        }
    }

    /**
     * We aren't really interested in the content of the image produced in this test, we're just
     * testing that resource references aren't touched when they're in the tools: attribute
     * namespace.
     */
    public void testPathDataInStringToolsResource() throws Exception {
        checkXmlConversion("test_pathData_in_string_tools_resource");
    }
}
