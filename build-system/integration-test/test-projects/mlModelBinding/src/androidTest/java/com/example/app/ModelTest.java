package com.example.app;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.app.ml.MobilenetQuantMetadata;
import com.example.app.ml.StylePredictQuantMetadata;
import com.example.app.ml.StyleTransferQuantMetadata;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.tensorflow.lite.support.image.TensorImage;
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

        // Pass in image and style array to get styled image
        StyleTransferQuantMetadata styleTransferQuantized384 =
                StyleTransferQuantMetadata.newInstance(appContext);
        TensorImage tower = new TensorImage();
        tower.load(
                BitmapFactory.decodeResource(appContext.getResources(), R.drawable.eiffel_tower));
        StyleTransferQuantMetadata.Outputs transferOutputs =
                styleTransferQuantized384.process(
                        tower, predictOutputs.getStyleBottleneckAsTensorBuffer());

        Bitmap bitmap = transferOutputs.getStyledImageAsTensorImage().getBitmap();
        assertNotNull(bitmap);
        assertEquals("Bitmap width should be 384.", bitmap.getWidth(), 384);
        assertEquals("Bitmap height should be 384.", bitmap.getHeight(), 384);
    }
}
