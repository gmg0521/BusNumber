package org.tensorflow.lite.examples.detection;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;

public class MediaScanner {
    private Context ctx;
    private String file_Path;
    private MediaScannerConnection mMediaScanner;
    private MediaScannerConnection.MediaScannerConnectionClient mMediaScannerClient;

    public static MediaScanner newInstance(Context context) {
        return new MediaScanner(context);
    }

    private MediaScanner(Context context) {
        ctx = context;
    }

    public void mediaScanning(final String path){
        if (mMediaScanner == null){
            mMediaScannerClient = new MediaScannerConnection.MediaScannerConnectionClient() {
                @Override
                public void onMediaScannerConnected() {
                    mMediaScanner.scanFile(file_Path, null);
                }

                @Override
                public void onScanCompleted(String path, Uri uri) {
                    System.out.println("::::MediaScan Success::::");
                    mMediaScanner.disconnect();
                }
            };
            mMediaScanner = new MediaScannerConnection(ctx, mMediaScannerClient);
        }
        file_Path = path;
        mMediaScanner.connect();
    }
}