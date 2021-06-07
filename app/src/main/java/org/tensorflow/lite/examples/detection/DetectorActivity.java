/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.detection;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Toast;

import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Detector;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
  private static final Logger LOGGER = new Logger();

  // Configuration values for the prepackaged SSD model.
  private static final int TF_OD_API_INPUT_SIZE = 600;
  private static final boolean TF_OD_API_IS_QUANTIZED = false;
  private static final String TF_OD_API_MODEL_FILE = "final_james.tflite";
  private static final String TF_OD_API_LABELS_FILE = "label.txt";
  // Minimum detection confidence to track a detection.
  private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
  private static final boolean MAINTAIN_ASPECT = false;
  private static final Size DESIRED_PREVIEW_SIZE = new Size(1920, 1080);
  private static final boolean SAVE_PREVIEW_BITMAP = false;
  private static final float TEXT_SIZE_DIP = 10;
  OverlayView trackingOverlay;
  private Integer sensorOrientation;

  private Detector detector;

  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;
  private Bitmap cropCopyBitmap = null;

  private boolean computingDetection = false;

  private long timestamp = 0;

  private Matrix frameToCropTransform;
  private Matrix cropToFrameTransform;

  private MultiBoxTracker tracker;

  private BorderedText borderedText;

  private float busLeft = 0.0f;
  private float busTop = 0.0f;
  private float busRight = 0.0f;
  private float busBottom = 0.0f;

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
      final float textSizePx =
              TypedValue.applyDimension(
                      TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
      borderedText = new BorderedText(textSizePx);
      borderedText.setTypeface(Typeface.MONOSPACE);

      tracker = new MultiBoxTracker(this);

      int cropSize = TF_OD_API_INPUT_SIZE;

      try {
        detector =
                TFLiteObjectDetectionAPIModel.create(
                        this,
                        TF_OD_API_MODEL_FILE,
                        TF_OD_API_LABELS_FILE,
                        TF_OD_API_INPUT_SIZE,
                        TF_OD_API_IS_QUANTIZED);
        cropSize = TF_OD_API_INPUT_SIZE;
      } catch (final IOException e) {
        e.printStackTrace();
        LOGGER.e(e, "Exception initializing Detector!");
        Toast toast =
                Toast.makeText(
                        getApplicationContext(), "Detector could not be initialized", Toast.LENGTH_SHORT);
        toast.show();
        finish();
      }

      previewWidth = size.getWidth();
      previewHeight = size.getHeight();

      sensorOrientation = rotation - getScreenOrientation();
      LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

      LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
      rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
      croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

      frameToCropTransform =
              ImageUtils.getTransformationMatrix(
                      previewWidth, previewHeight,
                      cropSize, cropSize,
                      sensorOrientation, MAINTAIN_ASPECT);

      cropToFrameTransform = new Matrix();
      frameToCropTransform.invert(cropToFrameTransform);

      trackingOverlay = findViewById(R.id.tracking_overlay);
      trackingOverlay.addCallback(
              canvas -> {
                tracker.draw(canvas);
                if (isDebug()) {
                  tracker.drawDebug(canvas);
                }
              });

      tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    @Override
    protected void processImage() {
      ++timestamp;
      final long currTimestamp = timestamp;
      trackingOverlay.postInvalidate();

      // No mutex needed as this method is not reentrant.
      if (computingDetection) {
        readyForNextImage();
        return;
      }
      computingDetection = true;
      LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

      rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

      readyForNextImage();

      final Canvas canvas = new Canvas(croppedBitmap);
      canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
      // For examining the actual TF input.
      if (SAVE_PREVIEW_BITMAP) {
        ImageUtils.saveBitmap(croppedBitmap);
      }

      runInBackground(
              () -> {
                LOGGER.i("Running detection on image " + currTimestamp);
                final List<Detector.Recognition> results = detector.recognizeImage(croppedBitmap);

                cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                final Canvas canvas1 = new Canvas(cropCopyBitmap);
                final Paint paint = new Paint();
                paint.setColor(Color.RED);
                paint.setStyle(Style.STROKE);
                paint.setStrokeWidth(2.0f);

                final List<Detector.Recognition> mappedRecognitions =
                        new ArrayList<>();

                for (final Detector.Recognition result : results) {
                  final RectF location = result.getLocation();
                  if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE_TF_OD_API) {
                    canvas1.drawRect(location, paint);

                    cropToFrameTransform.mapRect(location);

                    result.setLocation(location);
                    mappedRecognitions.add(result);

                    if (result.getTitle().equals("bus")) {
                      busLeft = result.getLocation().left;
                      busTop = result.getLocation().top;
                      busRight = result.getLocation().right;
                      busBottom = result.getLocation().bottom;
                    }

                    if (isTime && result.getTitle().equals("busnumber")) {
                      Toast.makeText(getApplicationContext(), "Dectected BusNumber! Try to crop image...", Toast.LENGTH_LONG).show();
                      cropImage(result.getLocation());
                      setTime();
                    }
                  }
                }

                tracker.trackResults(mappedRecognitions, currTimestamp);
                trackingOverlay.postInvalidate();

                computingDetection = false;

                runOnUiThread(
                        () -> {
                        });
              });
    }

    private void cropImage(RectF location) {

      float cropX = 0.0f;
      float cropY = 0.0f;

//  버스 번호만 Crop

      if (location.left > 20) cropX = location.left -20;
      if (location.top > 20) cropY = location.top -20;

      if (busLeft <= location.left && busTop <= location.top
          && busRight >= location.right && busBottom >= location.bottom){

        Matrix rotateMatrix = new Matrix();
        rotateMatrix.postRotate(90);

        Bitmap newBitmap = Bitmap.createBitmap(rgbFrameBitmap,
                (int) cropX,
                (int) cropY,
                (int) location.width() + 20,
                (int) location.height() + 20,
                rotateMatrix,
                MAINTAIN_ASPECT);

        CameraActivity.ttsSpeak(tessOCR.processImage(tessOCR.preProcessImg(newBitmap)));

        CameraActivity.fileUpLoader.UpdateFile(newBitmap, "capture");
      }
    }

    @Override
    protected int getLayoutId() {
      return R.layout.tfe_od_camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
      return DESIRED_PREVIEW_SIZE;
    }
}
