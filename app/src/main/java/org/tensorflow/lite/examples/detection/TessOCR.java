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
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TessOCR {
    private final String dataPath;
    private final TessBaseAPI tess;
    private final Context ctx;
    Bitmap imgBase;
    static StringBuffer chkLog = new StringBuffer();
    List<HashMap> possibleContours;

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
        possibleContours= new ArrayList<>();

        Bitmap imgRoi, contoursRoi, possibleContoursRoi, resultRectRoi = null, sub;

        OpenCVLoader.initDebug();

        Mat img1 = new Mat();
        Utils.bitmapToMat(myBitmap, img1);

        Mat imageGray1 = new Mat();
        Mat imgGaussianBlur = new Mat();
        Mat imageCny1 = new Mat();

        //1. 색조 처리 및 노이즈 제거

        Imgproc.cvtColor(img1, imageGray1, Imgproc.COLOR_BGR2GRAY);
        Imgproc.GaussianBlur(imageGray1, imgGaussianBlur, new org.opencv.core.Size(3,3), 0);
        Imgproc.Canny(imgGaussianBlur, imageCny1, 10, 100,3,true);
        Imgproc.threshold(imgGaussianBlur, imageCny1, 150, 255, Imgproc.THRESH_BINARY);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        Imgproc.erode(imageCny1, imageCny1, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(6, 6)));
        Imgproc.dilate(imageCny1, imageCny1, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(12, 12)));

        //2. 1차 관심영역 추출
        Imgproc.findContours(imageCny1, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
        Imgproc.drawContours(img1, contours, -1, new Scalar(0,255,0,0), 5);

        imgBase = Bitmap.createBitmap(img1.cols(), img1.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(img1, imgBase);

//      Without Contours
        imgRoi = Bitmap.createBitmap(imageCny1.cols(), imageCny1.rows(), Bitmap.Config.ARGB_8888);
        contoursRoi = Bitmap.createBitmap(imageCny1.cols(), imageCny1.rows(), Bitmap.Config.ARGB_8888);
        possibleContoursRoi = Bitmap.createBitmap(imageCny1.cols(), imageCny1.rows(), Bitmap.Config.ARGB_8888);
        resultRectRoi = Bitmap.createBitmap(imageCny1.cols(), imageCny1.rows(), Bitmap.Config.ARGB_8888);
        sub = Bitmap.createBitmap(imageCny1.cols(), imageCny1.rows(), Bitmap.Config.ARGB_8888);

        Utils.matToBitmap(imageCny1, imgRoi);

        List<HashMap> contoursList = new ArrayList();

        final int MIN_WIDTH = 100;
        final int MIN_HEIGHT = 170;
        final int MIN_AREA = MIN_WIDTH * MIN_HEIGHT;
        final float MIN_RATIO = 0.22f;
        final float MAX_RATIO = 0.7f;

        int cnt = 0;

        Mat copyImgcny = imageCny1.clone();
        Mat copyImgcny2 = imageCny1.clone();
        Mat copyImgcny3 = imageCny1.clone();

        if (contours.size() == 0)
            return imgRoi
                    ;
        for (int idx = 0; idx >= 0; idx = (int) hierarchy.get(0, idx)[0]) {
            MatOfPoint matOfPoint = contours.get(idx);
            Rect rect = Imgproc.boundingRect(matOfPoint);

            HashMap contours_map = new HashMap<String, Integer>();

            contours_map.put("contour", matOfPoint);
            contours_map.put("x", rect.x);
            contours_map.put("y", rect.y);
            contours_map.put("w", rect.width);
            contours_map.put("h", rect.height);
            contours_map.put("cx", (Integer) rect.x - (rect.width / 2));
            contours_map.put("cy", (Integer) rect.y - (rect.height / 2));

            contoursList.add((HashMap) contours_map);

            float area =  (Integer) contours_map.get("w") * (Integer) contours_map.get("h");
            float ratio = Float.parseFloat(contours_map.get("w").toString()) /  Float.parseFloat(contours_map.get("h").toString());

            if (area > MIN_AREA && (Integer) contours_map.get("w") > MIN_WIDTH &&
                    (Integer) contours_map.get("h") > MIN_HEIGHT &&
                    MIN_RATIO < ratio &&
                    ratio < MAX_RATIO) {

                contours_map.put("idx", cnt);
                cnt++;
                possibleContours.add(contours_map);
            }

                Imgproc.rectangle(copyImgcny, rect, new Scalar(255,255,255));
        }

        Utils.matToBitmap(copyImgcny, contoursRoi);

        Iterator<HashMap> ir = possibleContours.iterator();
        while (ir.hasNext()){
            HashMap temp = ir.next();
            double x = Double.parseDouble(String.valueOf(temp.get("x")));
            double y = Double.parseDouble(String.valueOf(temp.get("y")));
            double w = Double.parseDouble(String.valueOf(temp.get("w")));
            double h = Double.parseDouble(String.valueOf(temp.get("h")));

            Imgproc.rectangle(copyImgcny2, new Point(x, y), new Point((x + w),(y + h)), new Scalar(255, 255, 255), 2); // 일단은 전부 그림
        }

        Utils.matToBitmap(copyImgcny2, possibleContoursRoi);

        List<List> result_idx = find_chars(possibleContours);

        List<List<HashMap>> matched_result = new ArrayList();

        for (int i = 0; i < result_idx.size(); i++) {
            List idx = new ArrayList();
            for (int j = 0; j < result_idx.get(i).size(); j++) {
                idx.add(possibleContours.get((Integer) result_idx.get(i).get(j)));
            }
            matched_result.add(idx);
        }

        Iterator<List<HashMap>> ir2 = matched_result.iterator();
        while (ir2.hasNext()){
            List<HashMap> temp = ir2.next();
            Iterator<HashMap> ir3 = temp.iterator();
            while (ir3.hasNext()){
                HashMap finalResult = ir3.next();
                double x = Double.parseDouble(String.valueOf(finalResult.get("x")));
                double y = Double.parseDouble(String.valueOf(finalResult.get("y")));
                double w = Double.parseDouble(String.valueOf(finalResult.get("w")));
                double h = Double.parseDouble(String.valueOf(finalResult.get("h")));

                Imgproc.rectangle(copyImgcny3, new Point(x, y), new Point((x + w),(y + h)), new Scalar(255, 255, 255), 2);
            }
        }

        try {
            Utils.matToBitmap(copyImgcny3, resultRectRoi);
        }catch (NullPointerException e){
            e.getStackTrace();
        }

        final float NUM_WIDTH_PADDING = 1.6f;
        final float NUM_HEIGHT_PADDING = 1.2f;


        for (int i = 0; i < possibleContours.size(); i++) {
            Collections.sort(possibleContours, (o1, o2) -> Double.valueOf(o1.get("cx").toString()).compareTo(Double.parseDouble(o2.get("cx").toString())));

            HashMap sortedStart = possibleContours.get(0);
            HashMap sortedEnd = possibleContours.get(possibleContours.size()-1);


            float num_cx = (Float.parseFloat(sortedStart.get("cx").toString()) + Float.parseFloat(sortedEnd.get("cx").toString())) / 2;
            float num_cy = (Float.parseFloat(sortedStart.get("cy").toString()) + Float.parseFloat(sortedEnd.get("cy").toString())) / 2;

            int x1 = Integer.parseInt(sortedEnd.get("x").toString());
            int x2 = Integer.parseInt(sortedStart.get("x").toString());

            int width = (x1+Integer.parseInt(String.valueOf(sortedEnd.get("w")))) - x2;

            int num_width = Math.round(width * NUM_WIDTH_PADDING);

            Collections.sort(possibleContours, new Comparator<HashMap>() {
                @Override
                public int compare(HashMap o1, HashMap o2) {
                    return Double.valueOf(o1.get("cy").toString()).compareTo(Double.parseDouble(o2.get("cy").toString()));
                }
            });

            HashMap sortedStartY = possibleContours.get(0);
            HashMap sortedEndY = possibleContours.get(possibleContours.size()-1);

            int y1 = Integer.parseInt(sortedStartY.get("y").toString());
            int y2 = Integer.parseInt(sortedEndY.get("y").toString());

            int height = (y2+Integer.parseInt(sortedEndY.get("h").toString())) - y1;

            int num_height = Math.round(height * NUM_HEIGHT_PADDING);

            int subX = Integer.parseInt(String.valueOf(sortedStart.get("x")));
            int subY = Integer.parseInt(String.valueOf(sortedStart.get("y")));

            if (Integer.parseInt(String.valueOf(sortedStart.get("x"))) >= 10)
                subX = Integer.parseInt(String.valueOf(sortedStart.get("x"))) - 10;
            if (Integer.parseInt(String.valueOf(sortedStart.get("y"))) >= 10)
                subY = Integer.parseInt(String.valueOf(sortedStart.get("y"))) - 10;

            if (imgRoi.getWidth() < subX + num_width)
                num_width = imgRoi.getWidth() - subX;
            if (imgRoi.getHeight() < subY + num_height)
                num_height = imgRoi.getHeight() - subY;

            sub = Bitmap.createBitmap(imgRoi,
                    subX,
                    subY,
                    num_width,
                    num_height
            );


            CameraActivity.fileUpLoader.UpdateFile(sub, "크롭 이미지" + i);

        }

        try {
            Toast.makeText(ctx.getApplicationContext(), "전처리 된 이미지를 저장합니다!", Toast.LENGTH_LONG).show();

            CameraActivity.fileUpLoader.UpdateFile(imgBase, "컨투어 전처리");
            CameraActivity.fileUpLoader.UpdateFile(imgRoi, "반환된 전처리");
            CameraActivity.fileUpLoader.UpdateFile(contoursRoi, "모든 바운더리");
            CameraActivity.fileUpLoader.UpdateFile(possibleContoursRoi, "2차 바운더리");
            CameraActivity.fileUpLoader.UpdateFile(resultRectRoi, "최종 바운더리");
        } catch (Exception e) {
            e.printStackTrace();
        }

//      반환값은 바운더리 없이!
        return sub;
    }

    private List find_chars(List<HashMap> contours) {

        final int MAX_DIAG_MULTIPLYER = 5;
        final float MAX_ANGLE_DIFF = 12.0f;
        final float MAX_AREA_DIFF = 0.5f;
        final float MAX_WIDTH_DIFF = 0.3f;
        final float MAX_HEIGHT_DIFF = 0.2f;
        final float MIN_N_MATHCED = 2;

        List matched_result_idx = new ArrayList<>();

        for (int i = 0; i < contours.size(); i++) {
            List matched_contours_idx = new ArrayList<>();

            double cx1 = Float.parseFloat(contours.get(i).get("cx").toString());
            double cy1 = Float.parseFloat(contours.get(i).get("cy").toString());
            double w1 = Float.parseFloat(contours.get(i).get("w").toString());
            double h1 = Float.parseFloat(contours.get(i).get("h").toString());
            int idx1 = Integer.parseInt(contours.get(i).get("idx").toString());

            for (int j = 0; j < contours.size(); j++) {

                double cx2 = Float.parseFloat(contours.get(j).get("cx").toString());
                double cy2 = Float.parseFloat(contours.get(j).get("cy").toString());
                double w2 = Float.parseFloat(contours.get(j).get("w").toString());
                double h2 = Float.parseFloat(contours.get(j).get("h").toString());
                int idx2 = Integer.parseInt(contours.get(j).get("idx").toString());

                double angle_diff;
                double area_diff;
                double width_diff;
                double height_diff;

                if (idx1 == idx2)
                    continue;

                double dx = Math.abs(cx1 - cx2);
                double dy = Math.abs(cy1 - cy2);

                double diagonalLegnth1 = Math.sqrt(Math.pow(w1,2) + Math.pow(h1,2));

                double left = cx1 - cx2;
                double right = cy1 - cy2;
                double distance = Math.sqrt(Math.pow(left, 2) + Math.pow(right, 2));

                if (dx == 0){
                    angle_diff = 90;
                } else {
                    angle_diff = Math.toDegrees(Math.atan2(dy, dx));
                }

                area_diff = Math.abs(w1 * h1 - w2 * h2) / (w1 * h1);
                width_diff = Math.abs(w1 - w2) / w1;
                height_diff = Math.abs(h1 - h2) / h1;

                if ((distance < (diagonalLegnth1 * MAX_DIAG_MULTIPLYER)) &&
                        (angle_diff < MAX_ANGLE_DIFF) &&
                        (area_diff < MAX_AREA_DIFF) &&
                        ((width_diff < MAX_WIDTH_DIFF) ||
                                (height_diff < MAX_HEIGHT_DIFF)))
                    matched_contours_idx.add(idx2);
            }

            matched_contours_idx.add(idx1);

            if (matched_contours_idx.size() < MIN_N_MATHCED)
                continue;

            matched_result_idx.add(matched_contours_idx);

            List unmatched_contour_idx = new ArrayList<>();

            for (int j = 0; j < contours.size(); j++) {
                if (!matched_contours_idx.contains(Integer.parseInt(contours.get(j).get("idx").toString())))
                    unmatched_contour_idx.add(Integer.parseInt(contours.get(j).get("idx").toString()));
            }

            List unmatched_contour = new ArrayList<>();

            for (int j = 0; j < unmatched_contour_idx.size(); j++) {
                unmatched_contour.add(possibleContours.get((Integer) unmatched_contour_idx.get(j)));
            }

            List recursive_contour_list = find_chars(unmatched_contour);

            for (int j = 0; j < recursive_contour_list.size(); j++) {
                matched_result_idx.add(recursive_contour_list.get(j));
            }
            break;
        }

        return matched_result_idx;
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
        boolean does = OCRresult.isEmpty();
        String resultText = "";
        if (!does)
            resultText = OCRresult + "번 버스가 도착했습니다!";
        Log.e("test",resultText);

        chkLog.append(resultText).append("\n");

        return resultText;

    }



}