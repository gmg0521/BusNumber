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

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.lakue.lakuepopupactivity.PopupActivity;
import com.lakue.lakuepopupactivity.PopupGravity;
import com.lakue.lakuepopupactivity.PopupType;

import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;

import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public abstract class CameraActivity extends AppCompatActivity
        implements OnImageAvailableListener,
        Camera.PreviewCallback,
        CompoundButton.OnCheckedChangeListener, NavigationView.OnNavigationItemSelectedListener {
  private static final Logger LOGGER = new Logger();

  private static final int PERMISSIONS_REQUEST = 1;

  private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
  public static boolean isTime;
  private static TimerTask mTask;
  private static Timer mTimer;
  protected int previewWidth = 0;
  protected int previewHeight = 0;
  private final boolean debug = false;
  private Handler handler;
  private HandlerThread handlerThread;
  private boolean useCamera2API;
  private boolean isProcessingFrame = false;
  private final byte[][] yuvBytes = new byte[3][];
  private int[] rgbBytes = null;
  private int yRowStride;
  private Runnable postInferenceCallback;
  private Runnable imageConverter;

  // Tesseract-Two
  public TessOCR tessOCR;
  // Google TTS
  private static TextToSpeech tts;
  // 파일업로더
  public static FileUploader fileUpLoader;

  private ActionBarDrawerToggle mActionBarDrawerToggle;
  private DrawerLayout mDrawerLayout;

  Menu menu;
//  MenuItem menuItem;
  NavigationView mNavigationView;

  public static Boolean isDarkT;
  public static Boolean isBiggerT;
  private SharedPreferences sp;


  @RequiresApi(api = Build.VERSION_CODES.M)
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    sp = getSharedPreferences("sp", Activity.MODE_PRIVATE);
    isDarkT = sp.getBoolean("isDark", true);
    isBiggerT = sp.getBoolean("isBigger", false);
    LOGGER.e("깜깜쓰 ? "+isDarkT+"      큰 글씨?"+isBiggerT);

    setTheme (isDarkT == isBiggerT ? (isDarkT ? R.style.AppTheme_BBeono2 : R.style.AppTheme_WBeono) : (isDarkT ? R.style.AppTheme_BBeono : R.style.AppTheme_WBeono2));

    LOGGER.d("onCreate " + this);
    super.onCreate(null);

    setContentView(R.layout.tfe_od_activity_camera);
    mNavigationView = (NavigationView) findViewById(R.id.nav_view);
    menu = mNavigationView.getMenu();
    //스위치(on-off) 추가
    mNavigationView.getMenu().findItem(R.id.nav_changeBG)
            .setActionView(new Switch(this));

    mNavigationView.getMenu().findItem(R.id.nav_fontSize)
            .setActionView(new Switch(this));
    ((Switch) mNavigationView.getMenu().findItem(R.id.nav_changeBG).getActionView()).setChecked(isDarkT);
    ((Switch) mNavigationView.getMenu().findItem(R.id.nav_fontSize).getActionView()).setChecked(isBiggerT);

    mNavigationView.setNavigationItemSelectedListener(this);
    //switch listener 등록
    ((Switch) mNavigationView.getMenu().findItem(R.id.nav_changeBG).getActionView()).setOnCheckedChangeListener(
            new CompoundButton.OnCheckedChangeListener() {
              @Override
              public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                LOGGER.e("배경색이 검정색인가유 ..."+isChecked);
                isDarkT = isChecked;
              }
            });
    ((Switch) mNavigationView.getMenu().findItem(R.id.nav_fontSize).getActionView()).setOnCheckedChangeListener(
            new CompoundButton.OnCheckedChangeListener() {
              @Override
              public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                LOGGER.e("큰글씨 인가 ..."+isChecked);
                isBiggerT = isChecked;
              }
            });

    tts = new TextToSpeech(this, status -> {
      if (status == TextToSpeech.SUCCESS) {
        int result = tts.setLanguage(Locale.KOREA);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
          Log.e("TTS", "This Language is not supported");
        } else {
          isTime = true;

          // TEST OCR + TTS
          Bitmap myBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.unnamed5);

          tessOCR = new TessOCR(getApplicationContext());
          ttsSpeak(tessOCR.processImage(tessOCR.preProcessImg(myBitmap)));
        }
      }
    });


    try {
      fileUpLoader = new FileUploader(getApplicationContext());
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }

    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer);

    //툴바
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar actionBar = getSupportActionBar();

    actionBar.setDisplayHomeAsUpEnabled(true);
    actionBar.setHomeAsUpIndicator(R.drawable.caret);

    if (hasPermission()) {
      setFragment();
    } else {
      requestPermission();
    }
  }

  protected int[] getRgbBytes() {
    imageConverter.run();
    return rgbBytes;
  }

  protected int getLuminanceStride() {
    return yRowStride;
  }

  protected byte[] getLuminance() {
    return yuvBytes[0];
  }

  /** Callback for android.hardware.Camera API */
  @Override
  public void onPreviewFrame(final byte[] bytes, final Camera camera) {
    if (isProcessingFrame) {
      LOGGER.w("Dropping frame!");
      return;
    }

    try {
      // Initialize the storage bitmaps once when the resolution is known.
      if (rgbBytes == null) {
        Camera.Size previewSize = camera.getParameters().getPreviewSize();
        previewHeight = previewSize.height;
        previewWidth = previewSize.width;
        rgbBytes = new int[previewWidth * previewHeight];
        onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), 90);
      }
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      return;
    }

    isProcessingFrame = true;
    yuvBytes[0] = bytes;
    yRowStride = previewWidth;

    imageConverter =
            () -> ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);

    postInferenceCallback =
            () -> {
              camera.addCallbackBuffer(bytes);
              isProcessingFrame = false;
            };
    processImage();
  }

  /** Callback for Camera2 API */
  @Override
  public void onImageAvailable(final ImageReader reader) {
    // We need wait until we have some size from onPreviewSizeChosen
    if (previewWidth == 0 || previewHeight == 0) {
      return;
    }
    if (rgbBytes == null) {
      rgbBytes = new int[previewWidth * previewHeight];
    }
    try {
      final Image image = reader.acquireLatestImage();

      if (image == null) {
        return;
      }

      if (isProcessingFrame) {
        image.close();
        return;
      }
      isProcessingFrame = true;
      Trace.beginSection("imageAvailable");
      final Plane[] planes = image.getPlanes();
      fillBytes(planes, yuvBytes);
      yRowStride = planes[0].getRowStride();
      final int uvRowStride = planes[1].getRowStride();
      final int uvPixelStride = planes[1].getPixelStride();

      imageConverter =
              () -> ImageUtils.convertYUV420ToARGB8888(
                      yuvBytes[0],
                      yuvBytes[1],
                      yuvBytes[2],
                      previewWidth,
                      previewHeight,
                      yRowStride,
                      uvRowStride,
                      uvPixelStride,
                      rgbBytes);

      postInferenceCallback =
              () -> {
                image.close();
                isProcessingFrame = false;
              };

      processImage();
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      Trace.endSection();
      return;
    }
    Trace.endSection();
  }

  //홈버튼 햄버거
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    mDrawerLayout = findViewById(R.id.drawer);

    if (id == android.R.id.home) {
      if (!mDrawerLayout.isDrawerOpen(Gravity.LEFT)) {
        mDrawerLayout.openDrawer(Gravity.LEFT);
      } else mDrawerLayout.closeDrawer(Gravity.LEFT);
    }
    return super.onOptionsItemSelected(item);
  }

  //서랍 메뉴 선택할 때 메소드
  @Override
  public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
    int selectedItemId = menuItem.getItemId();
    LOGGER.e("뭔가 선택해따 ");

    if (selectedItemId == R.id.nav_fontSize || selectedItemId == R.id.nav_changeBG) {
      ((Switch) menuItem.getActionView()).toggle();
      Toast.makeText(CameraActivity.this, "변경된 테마는 앱 재시작 후에 적용됩니다아아", Toast.LENGTH_SHORT).show();
      return false;
    }
    else if (selectedItemId == R.id.nav_chkLog) {
      Intent intent = new Intent(getBaseContext(), PopupActivity.class);
      intent.putExtra("type", PopupType.NORMAL);
      intent.putExtra("gravity", PopupGravity.CENTER);
      intent.putExtra("title", "공지사항");
      intent.putExtra("content", TessOCR.chkLog.toString());
      intent.putExtra("buttonCenter", "종료");
      startActivityForResult(intent, 1);
    }
//    else if (selectedItemId == android.R.id.home) {
//      finish();
//    }

    //드로어 닫기
    mDrawerLayout = findViewById(R.id.drawer);
    mDrawerLayout.closeDrawer(GravityCompat.START);
    return true;
  }

  @Override
  public synchronized void onStart() {
    LOGGER.d("onStart " + this);
    super.onStart();
  }

  @Override
  public synchronized void onResume() {
    LOGGER.d("onResume " + this);
    super.onResume();

    handlerThread = new HandlerThread("inference");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
  }

  @Override
  public synchronized void onPause() {
    LOGGER.d("onPause " + this);

    handlerThread.quitSafely();
    try {
      handlerThread.join();
      handlerThread = null;
      handler = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }

    super.onPause();
  }

  @Override
  public synchronized void onStop() {

    //여기예욧...!!**
    SharedPreferences.Editor editor = sp.edit();
    editor.putBoolean("isDark", isDarkT);
    editor.putBoolean("isBigger", isBiggerT);
    editor.commit();

    LOGGER.d("onStop " + this);
    super.onStop();
  }

  @Override
  public synchronized void onDestroy() {
    LOGGER.d("onDestroy " + this);
    super.onDestroy();
    if (tts != null){
      tts.stop();
      tts.shutdown();
      tts = null;
    }
  }

  protected synchronized void runInBackground(final Runnable r) {
    if (handler != null) {
      handler.post(r);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  @Override
  public void onRequestPermissionsResult(
          final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == PERMISSIONS_REQUEST) {
      if (allPermissionsGranted(grantResults)) {
        setFragment();
      } else {
        requestPermission();
      }
    }
  }

  private static boolean allPermissionsGranted(final int[] grantResults) {
    for (int result : grantResults) {
      if (result != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  private boolean hasPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
    } else {
      return true;
    }
  }

  private void requestPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
        Toast.makeText(
                CameraActivity.this,
                "Camera permission is required for this demo",
                Toast.LENGTH_LONG)
                .show();
      }
      requestPermissions(new String[] {PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
    }
  }

  // Returns true if the device supports the required hardware level, or better.
  private boolean isHardwareLevelSupported(
          CameraCharacteristics characteristics, int requiredLevel) {
    int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
    if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
      return requiredLevel == deviceLevel;
    }
    // deviceLevel is not LEGACY, can use numerical sort
    return requiredLevel <= deviceLevel;
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  private String chooseCamera() {
    final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    try {
      for (final String cameraId : manager.getCameraIdList()) {
        final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

        // We don't use a front facing camera in this sample.
        final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
          continue;
        }

        final StreamConfigurationMap map =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map == null) {
          continue;
        }

        // Fallback to camera1 API for internal cameras that don't have full support.
        // This should help with legacy situations where using the camera2 API causes
        // distorted or otherwise broken previews.
        useCamera2API =
                (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
                        || isHardwareLevelSupported(
                        characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
        LOGGER.i("Camera API lv2?: %s", useCamera2API);
        return cameraId;
      }
    } catch (CameraAccessException e) {
      LOGGER.e(e, "Not allowed to access camera");
    }

    return null;
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  protected void setFragment() {
    String cameraId = chooseCamera();

    Fragment fragment;
    if (useCamera2API) {
      CameraConnectionFragment camera2Fragment =
              CameraConnectionFragment.newInstance(
                      (size, rotation) -> {
                        previewHeight = size.getHeight();
                        previewWidth = size.getWidth();
                        CameraActivity.this.onPreviewSizeChosen(size, rotation);
                      },
                      this,
                      getLayoutId(),
                      getDesiredPreviewFrameSize());

      camera2Fragment.setCamera(cameraId);
      fragment = camera2Fragment;
    } else {
      fragment =
              new LegacyCameraConnectionFragment(this, getLayoutId(), getDesiredPreviewFrameSize());
    }

    getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
  }

  protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    for (int i = 0; i < planes.length; ++i) {
      final ByteBuffer buffer = planes[i].getBuffer();
      if (yuvBytes[i] == null) {
        LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
        yuvBytes[i] = new byte[buffer.capacity()];
      }
      buffer.get(yuvBytes[i]);
    }
  }

  public boolean isDebug() {
    return debug;
  }

  protected void readyForNextImage() {
    if (postInferenceCallback != null) {
      postInferenceCallback.run();
    }
  }

  protected int getScreenOrientation() {
    switch (getWindowManager().getDefaultDisplay().getRotation()) {
      case Surface.ROTATION_270:
        return 270;
      case Surface.ROTATION_180:
        return 180;
      case Surface.ROTATION_90:
        return 90;
      default:
        return 0;
    }
  }

  @Override
  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//    setUseNNAPI(isChecked);
//    if (isChecked) apiSwitchCompat.setText("NNAPI");
//    else apiSwitchCompat.setText("TFLITE");
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public static void ttsSpeak(String text){
    if(isTime) {
      tts.setPitch(1.0f);
      tts.setSpeechRate(1.0f);
      tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "id1");
    }
  }

  public static void setTime() {
    isTime = false;
    mTask = new TimerTask() {
      @Override
      public void run() {
        if (!isTime){
          isTime = true;
          mTimer.cancel();
        }
      }
    };
    mTimer = new Timer();
    mTimer.schedule(mTask, 3000);
  }

  protected abstract void processImage();

  protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

  protected abstract int getLayoutId();

  protected abstract Size getDesiredPreviewFrameSize();

}