package org.tensorflow.lite.examples.detection;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class TessOCR {
    private final String dataPath;
    private final TessBaseAPI tess;
    private final Context ctx;
    Bitmap imgBase;

    public TessOCR(Context ctx) {
        // 데이터 경로
        this.ctx = ctx;

        dataPath = ctx.getFilesDir() + "/tesseract/";

        // 한글 & 영어 데이터 체크

        checkFile(new File(dataPath + "tessdata/"), "kor");
        checkFile(new File(dataPath + "tessdata/"), "eng");

        // 문자 인식을 수행할 tess 객체 생성
        String lang = "eng+kor";
        tess = new TessBaseAPI();
        tess.init(dataPath, lang);
    }


    // 파일 존재 확인
    private void checkFile(File dir, String lang) {
        //directory does not exist, but we can successfully create it
        if (!dir.exists()&& dir.mkdirs()){
            copyFiles(lang);
        }
        //The directory exists, but there is no data file in it
        if(dir.exists()) {
            String datafilePath = dataPath + "/tessdata/" + lang + ".traineddata";
            File datafile = new File(datafilePath);
            if (!datafile.exists()) {
                copyFiles(lang);
            }
        }
    }

    public Bitmap preProcessImg(Bitmap myBitmap) {
        Bitmap imgRoi, contoursRoi;

        OpenCVLoader.initDebug();

        Mat img1 = new Mat();
        Utils.bitmapToMat(myBitmap, img1);

        Mat imageGray1 = new Mat();
        Mat imgGaussianBlur = new Mat();
        Mat imageCny1 = new Mat();

        Imgproc.cvtColor(img1, imageGray1, Imgproc.COLOR_BGR2GRAY);
        Imgproc.GaussianBlur(imageGray1, imgGaussianBlur, new org.opencv.core.Size(3,3), 0);
        Imgproc.Canny(imgGaussianBlur, imageCny1, 10, 100,3,true);
        Imgproc.threshold(imgGaussianBlur, imageCny1, 150, 255, Imgproc.THRESH_BINARY);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        //노이즈 제거
        Imgproc.erode(imageCny1, imageCny1, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(6, 6)));
        Imgproc.dilate(imageCny1, imageCny1, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(12, 12)));

        //관심영역 추출
        Imgproc.findContours(imageCny1, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
        Imgproc.drawContours(img1, contours, -1, new Scalar(0,255,0,0), 5);

        imgBase = Bitmap.createBitmap(img1.cols(), img1.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(img1, imgBase);

//      Without Contours
        imgRoi = Bitmap.createBitmap(imageCny1.cols(), imageCny1.rows(), Bitmap.Config.ARGB_8888);
        contoursRoi = Bitmap.createBitmap(imageCny1.cols(), imageCny1.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(imageCny1, imgRoi);

        for (int idx = 0; idx >= 0; idx = (int) hierarchy.get(0, idx)[0]) {
            MatOfPoint matOfPoint = contours.get(idx);
            Rect rect = Imgproc.boundingRect(matOfPoint);

//            상자 크기에 따라 결정
//            if (rect.width < 30 || rect.height < 30
//                    || rect.width <= rect.height
//                    || rect.width <= rect.height * 3 || rect.width >= rect.height * 6)
//                continue;
            Imgproc.rectangle(imageCny1, rect, new Scalar(255,0,0,0),2); // 일단은 전부 그림
        }
        Utils.matToBitmap(imageCny1, contoursRoi);

        try {
            Toast.makeText(ctx.getApplicationContext(), "전처리 된 이미지를 저장합니다!", Toast.LENGTH_LONG).show();
            CameraActivity.fileUpLoader.UpdateFile(imgBase, "컨투어 전처리");
            CameraActivity.fileUpLoader.UpdateFile(imgRoi, "반환된 전처리");
            CameraActivity.fileUpLoader.UpdateFile(contoursRoi, "바운더리 포함 전처리");
        } catch (Exception e) {
            e.printStackTrace();
        }

//      반환값은 바운더리 없이!
        return imgRoi;
    }

    // 파일 복제
    private void copyFiles(String lang) {
        try {
            //location we want the file to be at
            String filepath = dataPath + "/tessdata/" + lang + ".traineddata";

            //get access to AssetManager
            AssetManager assetManager = ctx.getAssets();

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
    public String processImage(Bitmap bitmap){

        String OCRresult;
        tess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "1234567890");

        tess.setImage(bitmap);
        OCRresult = tess.getUTF8Text();
        String resultText = OCRresult.replaceAll("[^0-9]", "");
        Log.e("test",resultText);

        return resultText + "번 버스가 도착했습니다!";

    }



}