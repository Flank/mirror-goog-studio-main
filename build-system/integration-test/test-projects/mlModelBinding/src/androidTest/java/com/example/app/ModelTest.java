package com.example.app;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.app.ml.MobilenetQuantMetadata;
import com.example.app.ml.SsdMobilenetOdtMetadataV12;
import com.example.app.ml.StylePredictQuantMetadata;
import com.example.app.ml.StyleTransferQuantMetadata;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.ops.CastOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.common.ops.QuantizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.support.model.Model;

/** Instrumented test, which will execute on an Android device. */
@RunWith(AndroidJUnit4.class)
public class ModelTest {
    @Test
    public void verifyImageClassificationModel() throws IOException {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        Model.Options options = new Model.Options.Builder().setDevice(Model.Device.CPU).build();
        MobilenetQuantMetadata model = MobilenetQuantMetadata.newInstance(appContext, options);
        TensorImage tensorImage =
                TensorImage.fromBitmap(
                        BitmapFactory.decodeResource(appContext.getResources(), R.drawable.flower));
        MobilenetQuantMetadata.Outputs outputs = model.process(tensorImage);
        List<Category> probabilities = outputs.getProbabilityAsCategoryList();
        model.close();

        float prob = 0;
        String result = "";
        for (Category category : probabilities) {
            if (category.getScore() > prob) {
                prob = category.getScore();
                result = category.getLabel();
            }
        }
        assertEquals("Top1 label in image classification model is wrong.", "daisy", result);
    }

    @Test
    public void verifyImageClassificationModelWithFallbackApi() throws IOException {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeOp(224, 224, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
                        .add(new NormalizeOp(new float[] {127.5f}, new float[] {127.5f}))
                        .add(new QuantizeOp(128f, 0.0078125f))
                        .add(new CastOp(DataType.UINT8))
                        .build();

        Model.Options options = new Model.Options.Builder().setDevice(Model.Device.CPU).build();
        MobilenetQuantMetadata model = MobilenetQuantMetadata.newInstance(appContext, options);
        TensorImage tensorImage =
                TensorImage.fromBitmap(
                        BitmapFactory.decodeResource(appContext.getResources(), R.drawable.flower));
        MobilenetQuantMetadata.Outputs outputs =
                model.process(imageProcessor.process(tensorImage).getTensorBuffer());
        // TODO(jackqdyulei): verify getProbabilityAsTensorBuffer once we have versioned
        // MetadataExtractor, in which we can get label list easily.
        List<Category> probabilities = outputs.getProbabilityAsCategoryList();
        model.close();

        float prob = 0;
        String result = "";
        for (Category category : probabilities) {
            if (category.getScore() > prob) {
                prob = category.getScore();
                result = category.getLabel();
            }
        }
        assertEquals("Top1 label in image classification model is wrong.", "daisy", result);
    }

    @Test
    public void verifyStyleTransferModel() throws IOException {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // First get style array
        StylePredictQuantMetadata stylePredictQuantized256 =
                StylePredictQuantMetadata.newInstance(appContext);
        TensorImage flower =
                TensorImage.fromBitmap(
                        BitmapFactory.decodeResource(appContext.getResources(), R.drawable.flower));
        StylePredictQuantMetadata.Outputs predictOutputs = stylePredictQuantized256.process(flower);
        assertNotNull(predictOutputs.getStyleBottleneckAsTensorBuffer().getBuffer());
        stylePredictQuantized256.close();

        // Pass in image and style array to get styled image
        StyleTransferQuantMetadata styleTransferQuantized384 =
                StyleTransferQuantMetadata.newInstance(appContext);
        TensorImage tower = new TensorImage();
        tower.load(
                BitmapFactory.decodeResource(appContext.getResources(), R.drawable.eiffel_tower));
        StyleTransferQuantMetadata.Outputs transferOutputs =
                styleTransferQuantized384.process(
                        tower, predictOutputs.getStyleBottleneckAsTensorBuffer());
        styleTransferQuantized384.close();

        Bitmap bitmap = transferOutputs.getStyledImageAsTensorImage().getBitmap();
        assertNotNull(bitmap);
        assertEquals("Bitmap width should be 384.", bitmap.getWidth(), 384);
        assertEquals("Bitmap height should be 384.", bitmap.getHeight(), 384);
    }

    @Test
    public void verifyObjectDetectionModel() throws IOException {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        SsdMobilenetOdtMetadataV12 model =
                SsdMobilenetOdtMetadataV12.newInstance(
                        appContext, new Model.Options.Builder().build());
        TensorImage image =
                TensorImage.fromBitmap(
                        BitmapFactory.decodeResource(appContext.getResources(), R.drawable.chairs));

        SsdMobilenetOdtMetadataV12.Outputs outputs = model.process(image);
        List<SsdMobilenetOdtMetadataV12.DetectionResult> results = outputs.getDetectionResultList();

        assertEquals(10, results.size());
        SsdMobilenetOdtMetadataV12.DetectionResult result =
                results.stream()
                        .max(
                                Comparator.comparing(
                                        SsdMobilenetOdtMetadataV12.DetectionResult
                                                ::getScoresAsFloat))
                        .get();

        assertEquals("chair", result.getClassesAsString());
        assertEquals(0.824f, result.getScoresAsFloat(), 0.2f);
        RectF boundingBox = result.getLocationsAsRectF();
        assertEquals(503.2682f, boundingBox.left, 150f);
        assertEquals(152.63864f, boundingBox.top, 150f);
        assertEquals(1126.391f, boundingBox.right, 150f);
        assertEquals(1135.8333f, boundingBox.bottom, 150f);
    }
}
