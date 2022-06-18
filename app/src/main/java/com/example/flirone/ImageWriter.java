package com.example.flirone;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

import static org.opencv.imgproc.Imgproc.ADAPTIVE_THRESH_MEAN_C;
import static org.opencv.imgproc.Imgproc.THRESH_BINARY;
import static org.opencv.imgproc.Imgproc.THRESH_BINARY_INV;
import static org.opencv.imgproc.Imgproc.THRESH_OTSU;

public class ImageWriter {

    private final Context context;

    public ImageWriter(Context context) {
        this.context = context;
    }

    /**
     * Writes an image to file system.
     *
     * @param image Bitmap to write
     * @param name  File name of the image
     * @param time  Current timestamp
     */
    private void writeImage(Bitmap image, String name, String time) {
        String IMAGES_FOLDER_NAME = "FLIR_App";
        OutputStream out = null;

        try {

            // If Android Q or later, use MediaStore
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = context.getContentResolver();

                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/" + IMAGES_FOLDER_NAME);
                contentValues.put(MediaStore.MediaColumns.DATE_TAKEN, time);
                contentValues.put(MediaStore.MediaColumns.DATE_ADDED, time);

                Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                try {
                    out = resolver.openOutputStream(Objects.requireNonNull(imageUri));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

                // *** UNTESTED ***
                // If before android Q, use legacy file storage
            } else {
                String imagesDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DCIM).toString() + File.separator + IMAGES_FOLDER_NAME;

                File file = new File(imagesDir);

                if (!file.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.mkdir();
                }

                File imageFile = new File(imagesDir, name + ".png");
                out = new FileOutputStream(imageFile);

            }
            if (out != null) {
                image.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
                out.close();
            }
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    /**
     * Saves FIR and RGB image to file system
     *
     * @param images the receiveImages to save
     */
    public void saveImages(FrameDataHolder images) {
        Date now = new Date();

        @SuppressLint("SimpleDateFormat")
        String timeStamp = new SimpleDateFormat("yyy_MM_dd_HH_mm_ss_SSS").format(now);

        try {
            writeImage(images.firBitmap, "fir_" + timeStamp + ".png", Long.toString(now.getTime()));
            writeImage(images.rgbBitmap, "rgb_" + timeStamp + ".png", Long.toString(now.getTime()));
            writeImage(edgesim(images.rgbBitmap), "blck_" + timeStamp + ".png", Long.toString(now.getTime()));
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public static Bitmap edgesim(Bitmap originalimage) {

        Bitmap image;

        Mat img = new Mat();
        Utils.bitmapToMat(originalimage, img);

        Imgproc.cvtColor(img,img, Imgproc.COLOR_BGR2GRAY);
        Imgproc.adaptiveThreshold(img, img, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 19, -0.10);

        image= Bitmap.createBitmap(img.cols(), img.rows(), Bitmap.Config.ARGB_8888);

        Utils.matToBitmap(img, image);

        return image;
    }

}