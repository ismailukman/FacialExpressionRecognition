/**
 * Copyright (c) 2016 Affectiva Inc.
 * See the file license.txt for copying permission.
 */

package com.affectiva.affdexme;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.Drawable;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.ImageView;

import com.affectiva.android.affdex.sdk.Frame;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ImageHelper {

    private static final String LOG_TAG = "AffdexMe";

    // Prevent instantiation of this object
    private ImageHelper() {
    }

    public static boolean checkIfImageFileExists(@NonNull final Context context, @NonNull final String fileName) {

        // path to /data/data/yourapp/app_data/images
        File directory = context.getDir("images", Context.MODE_PRIVATE);

        // File location to save image
        File imagePath = new File(directory, fileName);

        return imagePath.exists();
    }

    public static boolean deleteImageFile(@NonNull final Context context, @NonNull final String fileName) {
        // path to /data/data/yourapp/app_data/images
        File directory = context.getDir("images", Context.MODE_PRIVATE);

        // File location to save image
        File imagePath = new File(directory, fileName);

        return imagePath.delete();
    }

    public static void resizeAndSaveResourceImageToInternalStorage(@NonNull final Context context, @NonNull final String fileName, @NonNull final String resourceName) throws FileNotFoundException {
        final int resourceId = context.getResources().getIdentifier(resourceName, "drawable", context.getPackageName());

        if (resourceId == 0) {
            //unrecognised resource
            throw new FileNotFoundException("Resource not found for file named: " + resourceName);
        }
        resizeAndSaveResourceImageToInternalStorage(context, fileName, resourceId);
    }

    public static void resizeAndSaveResourceImageToInternalStorage(@NonNull final Context context, @NonNull final String fileName, final int resourceId) {
        Resources resources = context.getResources();
        Bitmap sourceBitmap = BitmapFactory.decodeResource(resources, resourceId);
        Bitmap resizedBitmap = resizeBitmapForDeviceDensity(context, sourceBitmap);
        saveBitmapToInternalStorage(context, resizedBitmap, fileName);
        sourceBitmap.recycle();
        resizedBitmap.recycle();
    }

    public static Bitmap resizeBitmapForDeviceDensity(@NonNull final Context context, @NonNull final Bitmap sourceBitmap) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();

        int targetWidth = Math.round(sourceBitmap.getWidth() * metrics.density);
        int targetHeight = Math.round(sourceBitmap.getHeight() * metrics.density);

        return Bitmap.createScaledBitmap(sourceBitmap, targetWidth, targetHeight, false);
    }

    public static void saveBitmapToInternalStorage(@NonNull final Context context, @NonNull final Bitmap bitmapImage, @NonNull final String fileName) {

        // path to /data/data/yourapp/app_data/images
        File directory = context.getDir("images", Context.MODE_PRIVATE);

        // File location to save image
        File imagePath = new File(directory, fileName);

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(imagePath);

            // Use the compress method on the BitMap object to write image to the OutputStream
            bitmapImage.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, "Exception while trying to save file to internal storage: " + imagePath, e);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Exception while trying to flush the output stream", e);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Exception wile trying to close file output stream.", e);
                }
            }
        }
    }

    public static Bitmap loadBitmapFromInternalStorage(@NonNull final Context applicationContext, @NonNull final String fileName) {

        // path to /data/data/yourapp/app_data/images
        File directory = applicationContext.getDir("images", Context.MODE_PRIVATE);

        // File location to save image
        File imagePath = new File(directory, fileName);

        try {
            return BitmapFactory.decodeStream(new FileInputStream(imagePath));
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, "Exception wile trying to load image: " + imagePath, e);
            return null;
        }
    }

    public static void preproccessImageIfNecessary(@NonNull final Context context, @NonNull final String fileName, @NonNull final String resourceName) {
        // Set this to true to force the app to always load the images for debugging purposes
        final boolean DEBUG = false;

        if (ImageHelper.checkIfImageFileExists(context, fileName)) {
            // Image file already exists, no need to load the file again.

            if (DEBUG) {
                Log.d(LOG_TAG, "DEBUG: Deleting: " + fileName);
                ImageHelper.deleteImageFile(context, fileName);
            } else {
                return;
            }
        }

        try {
            ImageHelper.resizeAndSaveResourceImageToInternalStorage(context, fileName, resourceName);
            Log.d(LOG_TAG, "Resized and saved image: " + fileName);
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, "Unable to process image: " + fileName, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the bitmap position inside an imageView.
     * Source: http://stackoverflow.com/a/26930938
     * Author: http://stackoverflow.com/users/1474079/chteuchteu
     *
     * @param imageView source ImageView
     * @return 0: left, 1: top, 2: width, 3: height
     */
    public static int[] getBitmapPositionInsideImageView(@NonNull final ImageView imageView) {
        int[] ret = new int[4];

        if (imageView.getDrawable() == null)
            return ret;

        // Get image dimensions
        // Get image matrix values and place them in an array
        float[] f = new float[9];
        imageView.getImageMatrix().getValues(f);

        // Extract the scale values using the constants (if aspect ratio maintained, scaleX == scaleY)
        final float scaleX = f[Matrix.MSCALE_X];
        final float scaleY = f[Matrix.MSCALE_Y];

        // Get the drawable (could also get the bitmap behind the drawable and getWidth/getHeight)
        final Drawable d = imageView.getDrawable();
        final int origW = d.getIntrinsicWidth();
        final int origH = d.getIntrinsicHeight();

        // Calculate the actual dimensions
        final int actW = Math.round(origW * scaleX);
        final int actH = Math.round(origH * scaleY);

        ret[2] = actW;
        ret[3] = actH;

        // Get image position
        // We assume that the image is centered into ImageView
        int imgViewW = imageView.getWidth();
        int imgViewH = imageView.getHeight();

        int top = (imgViewH - actH) / 2;
        int left = (imgViewW - actW) / 2;

        ret[0] = left;
        ret[1] = top;

        return ret;
    }

    /**
     * This is a HACK.
     * We need to update the Android SDK to make this process cleaner.
     * We should just be able to call frame.getBitmap() and have it return a bitmap no matter what type
     * of frame it is.  If any conversion between file types needs to take place, it needs to happen
     * inside the SDK layer and put the onus on the developer to know how to convert between YUV and ARGB.
     * TODO: See above
     *
     * @param frame - The Frame containing the desired image
     * @return - The Bitmap representation of the image
     */
    public static Bitmap getBitmapFromFrame(@NonNull final Frame frame) {
        Bitmap bitmap;

        if (frame instanceof Frame.BitmapFrame) {
            bitmap = ((Frame.BitmapFrame) frame).getBitmap();
        } else { //frame is ByteArrayFrame
            switch (frame.getColorFormat()) {
                case RGBA:
                    bitmap = getBitmapFromRGBFrame(frame);
                    break;
                case YUV_NV21:
                    bitmap = getBitmapFromYuvFrame(frame);
                    break;
                case UNKNOWN_TYPE:
                default:
                    Log.e(LOG_TAG, "Unable to get bitmap from unknown frame type");
                    return null;
            }
        }

        if (bitmap == null || frame.getTargetRotation().toDouble() == 0.0) {
            return bitmap;
        } else {
            return rotateBitmap(bitmap, (float) frame.getTargetRotation().toDouble());
        }
    }

    public static Bitmap getBitmapFromRGBFrame(@NonNull final Frame frame) {
        byte[] pixels = ((Frame.ByteArrayFrame) frame).getByteArray();
        Bitmap bitmap = Bitmap.createBitmap(frame.getWidth(), frame.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels));
        return bitmap;
    }

    public static Bitmap getBitmapFromYuvFrame(@NonNull final Frame frame) {
        byte[] pixels = ((Frame.ByteArrayFrame) frame).getByteArray();
        YuvImage yuvImage = new YuvImage(pixels, ImageFormat.NV21, frame.getWidth(), frame.getHeight(), null);
        return convertYuvImageToBitmap(yuvImage);
    }

    /**
     * Note: This conversion procedure is sloppy and may result in JPEG compression artifacts
     *
     * @param yuvImage - The YuvImage to convert
     * @return - The converted Bitmap
     */
    public static Bitmap convertYuvImageToBitmap(@NonNull final YuvImage yuvImage) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();
        try {
            out.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Exception while closing output stream", e);
        }
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    public static Bitmap rotateBitmap(@NonNull final Bitmap source, final float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    public static void saveBitmapToFileAsPng(@NonNull final Bitmap bitmap, @NonNull final File file) throws IOException {
        try {
            FileOutputStream outputStream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            bitmap.recycle();
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            throw new FileNotFoundException("Unable to save bitmap to file: " + file.getPath() + "\n" + e.getLocalizedMessage());
        }
    }

    public static void addPngToGallery(@NonNull final Context context, @NonNull final File imageFile) {
        ContentValues values = new ContentValues();

        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.MediaColumns.DATA, imageFile.getAbsolutePath());

        context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }
}
