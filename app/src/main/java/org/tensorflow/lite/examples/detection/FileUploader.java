package org.tensorflow.lite.examples.detection;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class FileUploader {

    FileOutputStream fos;
    File uploadFolder;
    String Str_path;
    Context ctx;

    public FileUploader(Context ctx) throws FileNotFoundException {
        this.ctx = ctx;

        uploadFolder = Environment.getExternalStoragePublicDirectory("/DCIM/Camera/");
        if (!uploadFolder.exists()) {
            uploadFolder.mkdir();
        }
        Str_path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera/";
    }

    public void UpdateFile(Bitmap bitmap, String title){
        try {
            fos = new FileOutputStream(Str_path + title + ".png");
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }catch (
                FileNotFoundException e){
            e.printStackTrace();
        }

        MediaScanner ms = MediaScanner.newInstance(ctx.getApplicationContext());

        try {
            ms.mediaScanning(Str_path + title + ".png");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("::::ERROR::::" + e);
        }
    }
}
