package org.tensorflow.lite.examples.detection;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TessOCR extends AppCompatActivity {

    TessBaseAPI tess;
    String dataPath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 데이터 경로
        dataPath = getFilesDir()+ "/tesseract/";

        // 한글 & 영어 데이터 체크
        checkFile(new File(dataPath + "tessdata/"), "kor");
        checkFile(new File(dataPath + "tessdata/"), "eng");

        // 문자 인식을 수행할 tess 객체 생성
        String lang = "eng+kor";
        tess = new TessBaseAPI();
        tess.init(dataPath, lang);

//       아래는 처리할 이미지 추가

//        Bitmap myBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.unnamed5);

        //이미지 전처리 및 문자 인식 진행
//        processImage(myBitmap, false);  // 원본 이미지로 진행
//        processImage(preProcessImg(myBitmap), true);  // 전처리 된 이미지로 진행
    }

    private Bitmap preProcessImg(Bitmap myBitmap) {
        OpenCVLoader.initDebug();

        Mat img1 = new Mat();
        Utils.bitmapToMat(myBitmap, img1);

        Mat imageGray1 = new Mat();
        Mat imgGaussianBlur = new Mat();
        Mat imageCny1 = new Mat();

        Imgproc.cvtColor(img1, imageGray1, Imgproc.COLOR_BGR2GRAY);
        Imgproc.GaussianBlur(imageGray1, imgGaussianBlur, new Size(3,3), 0);
        Imgproc.adaptiveThreshold(imgGaussianBlur, imageCny1, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 99, 4);

        Bitmap resultImg = Bitmap.createBitmap(imageCny1.cols(), imageCny1.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(imageCny1, resultImg);

//        전처리된 이미지 표시
//        ImageView imageView = (ImageView) findViewById(R.id.imageView2);
//        imageView.setImageBitmap(resultImg);

        return resultImg;
    }

    // 파일 존재 확인
    private void checkFile(File dir, String lang) {
        //directory does not exist, but we can successfully create it
        if (!dir.exists()&& dir.mkdirs()){
            copyFiles(lang);
        }
        //The directory exists, but there is no data file in it
        if(dir.exists()) {
            String datafilePath = dataPath+ "/tessdata/" + lang + ".traineddata";
            File datafile = new File(datafilePath);
            if (!datafile.exists()) {
                copyFiles(lang);
            }
        }
    }

    // 파일 복제
    private void copyFiles(String lang) {
        try {
            //location we want the file to be at
            String filepath = dataPath + "/tessdata/" + lang + ".traineddata";

            //get access to AssetManager
            AssetManager assetManager = getAssets();

            //open byte streams for reading/writing
            InputStream inStream = assetManager.open("tessdata/" + lang + ".traineddata");
            OutputStream outStream = new FileOutputStream(filepath);

            //copy the file to the location specified by filepath
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, read);
            }
            outStream.flush();
            outStream.close();
            inStream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 문자 인식 및 결과 출력
    public void processImage(Bitmap bitmap, Boolean isPreProcessed){

        Toast.makeText(getApplicationContext(), "이미지가 복잡할 경우 해석 시 많은 시간이 소요될 수도 있습니다.", Toast.LENGTH_LONG).show();
        String OCRresult;
        tess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "1234567890");

        tess.setImage(bitmap);
        OCRresult = tess.getUTF8Text();
        String resultText = OCRresult.replaceAll("[^0-9]", "");

//        텍스트 뷰에 읽은 결과 출력
//        TextView OCRTextView;
//        if (!isPreProcessed) {
//            OCRTextView = (TextView) findViewById(R.id.tv_result);
//        } else {
//            OCRTextView = (TextView) findViewById(R.id.tv_result2);
//        }
//
//        OCRTextView.setText(OCRresult);
    }

}