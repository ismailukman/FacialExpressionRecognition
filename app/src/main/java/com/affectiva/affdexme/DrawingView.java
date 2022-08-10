/**
 * Copyright (c) 2016 Affectiva Inc.
 * See the file license.txt for copying permission.
 */

package com.affectiva.affdexme;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import com.affectiva.android.affdex.sdk.detector.Face;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * This class contains a SurfaceView and its own thread that draws to it.
 * It is used to display the facial tracking dots over a user's face.
 */
public class DrawingView extends SurfaceView implements SurfaceHolder.Callback {

    private final static String LOG_TAG = "AffdexMe";
    private final float MARGIN = 4;
    private Bitmap appearanceMarkerBitmap_genderMale_glassesOn;
    private Bitmap appearanceMarkerBitmap_genderFemale_glassesOn;
    private Bitmap appearanceMarkerBitmap_genderUnknown_glassesOn;
    private Bitmap appearanceMarkerBitmap_genderUnknown_glassesOff;
    private Bitmap appearanceMarkerBitmap_genderMale_glassesOff;
    private Bitmap appearanceMarkerBitmap_genderFemale_glassesOff;
    private Map<String, Bitmap> emojiMarkerBitmapToEmojiTypeMap;
    private SurfaceHolder surfaceHolder;
    private DrawingThread drawingThread; //DrawingThread object
    private DrawingViewConfig drawingViewConfig;
    private DrawingThreadEventListener listener;

    //three constructors required of any custom view
    public DrawingView(Context context) {
        super(context);
        initView();
    }

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public DrawingView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView();
    }

    private static int getDrawable(@NonNull Context context, @NonNull String name) {
        return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
    }

    public void setEventListener(DrawingThreadEventListener listener) {
        this.listener = listener;

        if (drawingThread != null) {
            drawingThread.setEventListener(listener);
        }
    }

    public void requestBitmap() {
        if (listener == null) {
            String msg = "Attempted to request screenshot without first attaching event listener";
            Log.e(LOG_TAG, msg);
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            return;
        }
        if (drawingThread == null || drawingThread.isStopped()) {
            String msg = "Attempted to request screenshot without a running drawing thread";
            Log.e(LOG_TAG, msg);
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            return;
        }
        drawingThread.requestCaptureBitmap = true;
    }

    @SuppressWarnings("ResourceType")
    void initView() {
        surfaceHolder = getHolder(); //The SurfaceHolder object will be used by the thread to request canvas to draw on SurfaceView
        surfaceHolder.setFormat(PixelFormat.TRANSPARENT); //set to Transparent so this surfaceView does not obscure the one it is overlaying (the one displaying the camera).
        surfaceHolder.addCallback(this); //become a Listener to the three events below that SurfaceView generates
        drawingViewConfig = new DrawingViewConfig();

        //Default values
        Paint emotionLabelPaint = new Paint();
        emotionLabelPaint.setColor(Color.parseColor("#ff8000")); //Orange
        emotionLabelPaint.setStyle(Paint.Style.FILL);
        emotionLabelPaint.setTextAlign(Paint.Align.CENTER);
        emotionLabelPaint.setTextSize(48);

        Paint emotionValuePaint = new Paint();
        emotionValuePaint.setColor(Color.parseColor("#514a40")); //Grey
        emotionValuePaint.setStyle(Paint.Style.FILL);
        emotionValuePaint.setTextAlign(Paint.Align.CENTER);
        emotionValuePaint.setTextSize(48);

        Paint metricBarPaint = new Paint();
        metricBarPaint.setColor(Color.GREEN);
        metricBarPaint.setStyle(Paint.Style.FILL);
        int metricBarWidth = 150;

        //load and parse XML attributes
        int[] emotionLabelAttrs = {
                android.R.attr.textStyle,      // 0
                android.R.attr.textColor,      // 1
                android.R.attr.shadowColor,    // 2
                android.R.attr.shadowDy,       // 3
                android.R.attr.shadowRadius,   // 4
                android.R.attr.layout_weight,  // 5
                android.R.attr.textSize};      // 6
        TypedArray a = getContext().obtainStyledAttributes(R.style.metricName, emotionLabelAttrs);
        if (a != null) {
            emotionLabelPaint.setColor(a.getColor(1, emotionLabelPaint.getColor()));
            emotionLabelPaint.setShadowLayer(
                    a.getFloat(4, 1.0f),
                    a.getFloat(3, 2.0f), a.getFloat(3, 2.0f),
                    a.getColor(2, Color.BLACK));
            emotionLabelPaint.setTextSize(a.getDimensionPixelSize(6, 48));
            emotionLabelPaint.setFakeBoldText("bold".equalsIgnoreCase(a.getString(0)));
            a.recycle();
        }

        int[] emotionValueAttrs = {
                android.R.attr.textColor,         // 0
                android.R.attr.textSize,          // 1
                R.styleable.custom_attributes_metricBarLength};  // 2
        a = getContext().obtainStyledAttributes(R.style.metricPct, emotionValueAttrs);
        if (a != null) {
            emotionValuePaint.setColor(a.getColor(0, emotionValuePaint.getColor()));
            emotionValuePaint.setTextSize(a.getDimensionPixelSize(1, 36));
            metricBarWidth = a.getDimensionPixelSize(2, 150);
            a.recycle();
        }

        drawingViewConfig.setDominantEmotionLabelPaints(emotionLabelPaint, emotionValuePaint);
        drawingViewConfig.setDominantEmotionMetricBarConfig(metricBarPaint, metricBarWidth);
        drawingThread = new DrawingThread(surfaceHolder, drawingViewConfig, listener);

        //statically load the emoji bitmaps on-demand and cache
        emojiMarkerBitmapToEmojiTypeMap = new HashMap<>();
    }

    public void setTypeface(Typeface face) {
        drawingViewConfig.dominantEmotionLabelPaint.setTypeface(face);
        drawingViewConfig.dominantEmotionValuePaint.setTypeface(face);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (drawingThread.isStopped()) {
            drawingThread = new DrawingThread(surfaceHolder, drawingViewConfig, listener);
        }
        drawingThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        //command thread to stop, and wait until it stops
        boolean retry = true;
        drawingThread.stopThread();
        while (retry) {
            try {
                drawingThread.join();
                retry = false;
            } catch (InterruptedException e) {
                Log.e(LOG_TAG, e.getMessage());
            }
        }
        cleanup();
    }

    public boolean isDimensionsNeeded() {
        return drawingViewConfig.isDimensionsNeeded;
    }

    public void invalidateDimensions() {
        drawingViewConfig.isDimensionsNeeded = true;
    }

    public void updateViewDimensions(int surfaceViewWidth, int surfaceViewHeight, int imageWidth, int imageHeight) {
        try {
            drawingViewConfig.updateViewDimensions(surfaceViewWidth, surfaceViewHeight, imageWidth, imageHeight);
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, "Attempted to set a dimension with a negative value", e);
        }
    }

    public void setThickness(int t) {
        try {
            drawingViewConfig.setDrawThickness(t);
            drawingThread.setThickness(t);
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, "Attempted to set a thickness with a negative value", e);
        }
    }

    public boolean getDrawPointsEnabled() {
        return drawingViewConfig.isDrawPointsEnabled;
    }

    public void setDrawPointsEnabled(boolean b) {
        drawingViewConfig.isDrawPointsEnabled = b;
    }

    public boolean getDrawAppearanceMarkersEnabled() {
        return drawingViewConfig.isDrawAppearanceMarkersEnabled;
    }

    public void setDrawAppearanceMarkersEnabled(boolean b) {
        drawingViewConfig.isDrawAppearanceMarkersEnabled = b;
    }

    public boolean getDrawEmojiMarkersEnabled() {
        return drawingViewConfig.isDrawEmojiMarkersEnabled;
    }

    public void setDrawEmojiMarkersEnabled(boolean b) {
        drawingViewConfig.isDrawEmojiMarkersEnabled = b;
    }

    public void updatePoints(List<Face> faces, boolean isPointsMirrored) {
        drawingThread.updatePoints(faces, isPointsMirrored);
    }

    public void invalidatePoints() {
        drawingThread.invalidatePoints();
    }

    /**
     * To be called when this view element is potentially being destroyed
     * I.E. when the Activity's onPause() gets called.
     */
    public void cleanup() {
        if (emojiMarkerBitmapToEmojiTypeMap != null) {
            for (Bitmap bitmap : emojiMarkerBitmapToEmojiTypeMap.values()) {
                bitmap.recycle();
            }
            emojiMarkerBitmapToEmojiTypeMap.clear();
        }

        if (appearanceMarkerBitmap_genderMale_glassesOn != null) {
            appearanceMarkerBitmap_genderMale_glassesOn.recycle();
        }
        if (appearanceMarkerBitmap_genderFemale_glassesOn != null) {
            appearanceMarkerBitmap_genderFemale_glassesOn.recycle();
        }
        if (appearanceMarkerBitmap_genderUnknown_glassesOn != null) {
            appearanceMarkerBitmap_genderUnknown_glassesOn.recycle();
        }
        if (appearanceMarkerBitmap_genderUnknown_glassesOff != null) {
            appearanceMarkerBitmap_genderUnknown_glassesOff.recycle();
        }
        if (appearanceMarkerBitmap_genderMale_glassesOff != null) {
            appearanceMarkerBitmap_genderMale_glassesOff.recycle();
        }
        if (appearanceMarkerBitmap_genderFemale_glassesOff != null) {
            appearanceMarkerBitmap_genderFemale_glassesOff.recycle();
        }
    }

    interface DrawingThreadEventListener {
        void onBitmapGenerated(Bitmap bitmap);
    }

    class FacesSharer {
        boolean isPointsMirrored;
        List<Face> facesToDraw;

        public FacesSharer() {
            isPointsMirrored = false;
            facesToDraw = new ArrayList<>();
        }
    }

    //Inner Thread class
    class DrawingThread extends Thread {
        private final FacesSharer sharer;
        private final SurfaceHolder mSurfaceHolder;
        private Paint trackingPointsPaint;
        private Paint boundingBoxPaint;
        private Paint dominantEmotionScoreBarPaint;
        private volatile boolean stopFlag = false; //boolean to indicate when thread has been told to stop
        private volatile boolean requestCaptureBitmap = false; //boolean to indicate a snapshot of the surface has been requested
        private DrawingViewConfig config;
        private DrawingThreadEventListener listener;

        public DrawingThread(SurfaceHolder surfaceHolder, DrawingViewConfig con, DrawingThreadEventListener listener) {
            mSurfaceHolder = surfaceHolder;

            //statically load the Appearance marker bitmaps so they only have to load once
            appearanceMarkerBitmap_genderMale_glassesOn = ImageHelper.loadBitmapFromInternalStorage(getContext(), "male_glasses.png");
            appearanceMarkerBitmap_genderMale_glassesOff = ImageHelper.loadBitmapFromInternalStorage(getContext(), "male_noglasses.png");
            appearanceMarkerBitmap_genderFemale_glassesOn = ImageHelper.loadBitmapFromInternalStorage(getContext(), "female_glasses.png");
            appearanceMarkerBitmap_genderFemale_glassesOff = ImageHelper.loadBitmapFromInternalStorage(getContext(), "female_noglasses.png");
            appearanceMarkerBitmap_genderUnknown_glassesOn = ImageHelper.loadBitmapFromInternalStorage(getContext(), "unknown_glasses.png");
            appearanceMarkerBitmap_genderUnknown_glassesOff = ImageHelper.loadBitmapFromInternalStorage(getContext(), "unknown_noglasses.png");

            trackingPointsPaint = new Paint();
            trackingPointsPaint.setColor(Color.WHITE);
            boundingBoxPaint = new Paint();
            boundingBoxPaint.setColor(Color.WHITE);
            boundingBoxPaint.setStyle(Paint.Style.STROKE);
            dominantEmotionScoreBarPaint = new Paint();
            dominantEmotionScoreBarPaint.setColor(Color.GREEN);
            dominantEmotionScoreBarPaint.setStyle(Paint.Style.STROKE);

            config = con;
            sharer = new FacesSharer();
            this.listener = listener;

            setThickness(config.drawThickness);
        }

        public void setEventListener(DrawingThreadEventListener listener) {
            this.listener = listener;
        }

        void setValenceOfBoundingBox(float valence) {
            //prepare the color of the bounding box using the valence score. Red for -100, White for 0, and Green for +100, with linear interpolation in between.
            if (valence > 0) {
                float colorScore = ((100f - valence) / 100f) * 255;
                boundingBoxPaint.setColor(Color.rgb((int) colorScore, 255, (int) colorScore));
            } else {
                float colorScore = ((100f + valence) / 100f) * 255;
                boundingBoxPaint.setColor(Color.rgb(255, (int) colorScore, (int) colorScore));
            }
        }

        public void stopThread() {
            stopFlag = true;
        }

        public boolean isStopped() {
            return stopFlag;
        }

        //Updates thread with latest faces returned by the onImageResults() event.
        public void updatePoints(List<Face> faces, boolean isPointsMirrored) {
            synchronized (sharer) {
                sharer.facesToDraw.clear();
                if (faces != null) {
                    sharer.facesToDraw.addAll(faces);
                }
                sharer.isPointsMirrored = isPointsMirrored;
            }
        }

        void setThickness(int thickness) {
            boundingBoxPaint.setStrokeWidth(thickness);
        }

        //Inform thread face detection has stopped, so pending faces are no longer valid.
        public void invalidatePoints() {
            synchronized (sharer) {
                sharer.facesToDraw.clear();
            }
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            while (!stopFlag) {

                /**
                 * We use SurfaceHolder.lockCanvas() to get the canvas that draws to the SurfaceView.
                 * After we are done drawing, we let go of the canvas using SurfaceHolder.unlockCanvasAndPost()
                 * **/
                Canvas c = null;
                Canvas screenshotCanvas = null;
                Bitmap screenshotBitmap = null;
                try {
                    c = mSurfaceHolder.lockCanvas();

                    if (requestCaptureBitmap) {
                        Rect surfaceBounds = mSurfaceHolder.getSurfaceFrame();
                        screenshotBitmap = Bitmap.createBitmap(surfaceBounds.width(), surfaceBounds.height(), Bitmap.Config.ARGB_8888);
                        screenshotCanvas = new Canvas(screenshotBitmap);
                        requestCaptureBitmap = false;
                    }

                    if (c != null) {
                        synchronized (mSurfaceHolder) {
                            c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); //clear previous dots
                            draw(c, screenshotCanvas);
                        }
                    }

                } finally {
                    if (c != null) {
                        mSurfaceHolder.unlockCanvasAndPost(c);
                    }
                    if (screenshotBitmap != null && listener != null) {
                        listener.onBitmapGenerated(Bitmap.createBitmap(screenshotBitmap));
                        screenshotBitmap.recycle();
                    }
                }
            }

            config = null; //nullify object to avoid memory leak
        }

        void draw(@NonNull Canvas c, @Nullable Canvas c2) {
            Face nextFaceToDraw;
            boolean mirrorPoints;
            boolean multiFaceMode;
            int index = 0;

            synchronized (sharer) {
                mirrorPoints = sharer.isPointsMirrored;
                multiFaceMode = sharer.facesToDraw.size() > 1;

                if (sharer.facesToDraw.isEmpty()) {
                    nextFaceToDraw = null;
                } else {
                    nextFaceToDraw = sharer.facesToDraw.get(index);
                    index++;
                }
            }

            while (nextFaceToDraw != null) {

                drawFaceAttributes(c, nextFaceToDraw, mirrorPoints, multiFaceMode);

                if (c2 != null) {
                    drawFaceAttributes(c2, nextFaceToDraw, false, multiFaceMode);
                }

                synchronized (sharer) {
                    mirrorPoints = sharer.isPointsMirrored;

                    if (index < sharer.facesToDraw.size()) {
                        nextFaceToDraw = sharer.facesToDraw.get(index);
                        index++;
                    } else {
                        nextFaceToDraw = null;
                    }
                }
            }
        }

        private void drawFaceAttributes(Canvas c, Face face, boolean mirrorPoints, boolean isMultiFaceMode) {
            //Coordinates around which to draw bounding box.
            //Default to an 'inverted' box, where the absolute max and min values of the surface view are inside-out
            Rect boundingRect = new Rect(config.surfaceViewWidth, config.surfaceViewHeight, 0, 0);

            for (PointF point : face.getFacePoints()) {
                //transform from the camera coordinates to our screen coordinates
                //The camera preview is displayed as a mirror, so X pts have to be mirrored back.
                float x;
                if (mirrorPoints) {
                    x = (config.imageWidth - point.x) * config.screenToImageRatio;
                } else {
                    x = (point.x) * config.screenToImageRatio;
                }
                float y = (point.y) * config.screenToImageRatio;

                //For some reason I needed to add each point twice to make sure that all the
                //points get properly registered in the bounding box.
                boundingRect.union(Math.round(x), Math.round(y));
                boundingRect.union(Math.round(x), Math.round(y));

                //Draw facial tracking dots.
                if (config.isDrawPointsEnabled) {
                    c.drawCircle(x, y, config.drawThickness, trackingPointsPaint);
                }
            }

            //Draw the bounding box.
            if (config.isDrawPointsEnabled) {
                drawBoundingBox(c, face, boundingRect);
            }

            float heightOffset = findNecessaryHeightOffset(boundingRect, face);

            //Draw the Appearance markers (gender / glasses)
            if (config.isDrawAppearanceMarkersEnabled) {
                drawAppearanceMarkers(c, face, boundingRect, heightOffset);
            }

            //Draw the Emoji markers
            if (config.isDrawEmojiMarkersEnabled) {
                drawDominantEmoji(c, face, boundingRect, heightOffset);
            }

            //Only draw the dominant emotion bar in multiface mode
            if (isMultiFaceMode) {
                drawDominantEmotion(c, face, boundingRect);
            }
        }

        private float findNecessaryHeightOffset(Rect boundingBox, Face face) {
            Bitmap appearanceBitmap = getAppearanceBitmapForFace(face);
            Bitmap emojiBitmap = getDominantEmojiBitmapForFace(face);

            float appearanceBitmapHeight = (appearanceBitmap != null) ? appearanceBitmap.getHeight() : 0;
            float emojiBitmapHeight = (emojiBitmap != null) ? emojiBitmap.getHeight() : 0;
            float spacingBetween = (appearanceBitmapHeight > 0 && emojiBitmapHeight > 0) ? MARGIN : 0;
            float totalHeightRequired = appearanceBitmapHeight + emojiBitmapHeight + spacingBetween;

            float bitmapHeightOverflow = Math.max(totalHeightRequired - boundingBox.height(), 0);

            return bitmapHeightOverflow / 2;  // distribute the overflow evenly on both sides of the bounding box
        }

        private void drawBoundingBox(Canvas c, Face f, Rect boundingBox) {
            setValenceOfBoundingBox(f.emotions.getValence());
            c.drawRect(boundingBox.left,
                    boundingBox.top,
                    boundingBox.right,
                    boundingBox.bottom,
                    boundingBoxPaint);
        }

        private void drawAppearanceMarkers(Canvas c, Face f, Rect boundingBox, float offset) {
            Bitmap bitmap = getAppearanceBitmapForFace(f);
            if (bitmap != null) {
                drawBitmapIfNotRecycled(c, bitmap, boundingBox.right + MARGIN, boundingBox.bottom - bitmap.getHeight() + offset);
            }
        }

        private Bitmap getAppearanceBitmapForFace(Face f) {
            Bitmap bitmap = null;
            switch (f.appearance.getGender()) {
                case MALE:
                    if (Face.GLASSES.YES.equals(f.appearance.getGlasses())) {
                        bitmap = appearanceMarkerBitmap_genderMale_glassesOn;
                    } else {
                        bitmap = appearanceMarkerBitmap_genderMale_glassesOff;
                    }
                    break;
                case FEMALE:
                    if (Face.GLASSES.YES.equals(f.appearance.getGlasses())) {
                        bitmap = appearanceMarkerBitmap_genderFemale_glassesOn;
                    } else {
                        bitmap = appearanceMarkerBitmap_genderFemale_glassesOff;
                    }
                    break;
                case UNKNOWN:
                    if (Face.GLASSES.YES.equals(f.appearance.getGlasses())) {
                        bitmap = appearanceMarkerBitmap_genderUnknown_glassesOn;
                    } else {
                        bitmap = appearanceMarkerBitmap_genderUnknown_glassesOff;
                    }
                    break;
                default:
                    Log.e(LOG_TAG, "Unknown gender: " + f.appearance.getGender());
            }
            return bitmap;
        }

        private void drawBitmapIfNotRecycled(Canvas c, Bitmap b, float posX, float posY) {
            if (!b.isRecycled()) {
                c.drawBitmap(b, posX, posY, null);
            }
        }

        private void drawDominantEmoji(Canvas c, Face f, Rect boundingBox, float offset) {
            drawEmojiFromCache(c, f.emojis.getDominantEmoji().name(), boundingBox.right + MARGIN, boundingBox.top - offset);
        }

        private void drawDominantEmotion(Canvas c, Face f, Rect boundingBox) {
            Pair<String, Float> dominantMetric = findDominantEmotion(f);

            if (dominantMetric == null || dominantMetric.first.isEmpty()) {
                return;
            }

            String emotionText = dominantMetric.first;
            String emotionValue = Math.round(dominantMetric.second) + "%";

            Rect emotionTextBounds = new Rect();
            config.dominantEmotionLabelPaint.getTextBounds(emotionText, 0, emotionText.length(), emotionTextBounds);

            Rect emotionValueBounds = new Rect();
            config.dominantEmotionValuePaint.getTextBounds(emotionValue, 0, emotionValue.length(), emotionValueBounds);

            float drawAtX = boundingBox.exactCenterX();
            float drawAtY = boundingBox.bottom + MARGIN + emotionTextBounds.height();
            c.drawText(emotionText, drawAtX, drawAtY, config.dominantEmotionLabelPaint);

            //draws the colored bar that appears behind our score
            drawAtY += MARGIN + emotionValueBounds.height();
            int halfWidth = Math.round(config.metricBarWidth / 200.0f * dominantMetric.second);
            c.drawRect(drawAtX - halfWidth, drawAtY - emotionValueBounds.height(), drawAtX + halfWidth, drawAtY, config.dominantEmotionMetricBarPaint);

            //draws the score
            c.drawText(emotionValue, drawAtX, drawAtY, config.dominantEmotionValuePaint);
        }

        private Pair<String, Float> findDominantEmotion(Face f) {
            String dominantMetricName = "";
            Float dominantMetricValue = 50.0f; // no emotion is dominant unless at least greater than this value

            if (f.emotions.getAnger() > dominantMetricValue) {
                dominantMetricName = MetricsManager.getCapitalizedName(MetricsManager.Emotions.ANGER);
                dominantMetricValue = f.emotions.getAnger();
            }
            if (f.emotions.getContempt() > dominantMetricValue) {
                dominantMetricName = MetricsManager.getCapitalizedName(MetricsManager.Emotions.CONTEMPT);
                dominantMetricValue = f.emotions.getContempt();
            }
            if (f.emotions.getDisgust() > dominantMetricValue) {
                dominantMetricName = MetricsManager.getCapitalizedName(MetricsManager.Emotions.DISGUST);
                dominantMetricValue = f.emotions.getDisgust();
            }
            if (f.emotions.getFear() > dominantMetricValue) {
                dominantMetricName = MetricsManager.getCapitalizedName(MetricsManager.Emotions.FEAR);
                dominantMetricValue = f.emotions.getFear();
            }
            if (f.emotions.getJoy() > dominantMetricValue) {
                dominantMetricName = MetricsManager.getCapitalizedName(MetricsManager.Emotions.JOY);
                dominantMetricValue = f.emotions.getJoy();
            }
            if (f.emotions.getSadness() > dominantMetricValue) {
                dominantMetricName = MetricsManager.getCapitalizedName(MetricsManager.Emotions.SADNESS);
                dominantMetricValue = f.emotions.getSadness();
            }
            if (f.emotions.getSurprise() > dominantMetricValue) {
                dominantMetricName = MetricsManager.getCapitalizedName(MetricsManager.Emotions.SURPRISE);
                dominantMetricValue = f.emotions.getSurprise();
            }
            // Ignore VALENCE and ENGAGEMENT

            if (dominantMetricName.isEmpty()) {
                return null;
            } else {
                return new Pair<>(dominantMetricName, dominantMetricValue);
            }
        }

        void drawEmojiFromCache(Canvas c, String emojiName, float markerPosX, float markerPosY) {
            Bitmap emojiBitmap;

            try {
                emojiBitmap = getEmojiBitmapByName(emojiName);
            } catch (FileNotFoundException e) {
                Log.e(LOG_TAG, "Error, file not found!", e);
                return;
            }

            if (emojiBitmap != null) {
                c.drawBitmap(emojiBitmap, markerPosX, markerPosY, null);
            }
        }

        private Bitmap getDominantEmojiBitmapForFace(Face f) {
            try {
                return getEmojiBitmapByName(f.emojis.getDominantEmoji().name());
            } catch (FileNotFoundException e) {
                Log.e(LOG_TAG, "Dominant emoji bitmap not available", e);
                return null;
            }
        }

        Bitmap getEmojiBitmapByName(String emojiName) throws FileNotFoundException {
            // No bitmap necessary if emoji is unknown
            if (emojiName.equals(Face.EMOJI.UNKNOWN.name())) {
                return null;
            }

            String emojiResourceName = emojiName.trim().replace(' ', '_').toLowerCase(Locale.US).concat("_emoji");
            String emojiFileName = emojiResourceName + ".png";

            //Try to get the emoji from the cache
            Bitmap desiredEmojiBitmap = emojiMarkerBitmapToEmojiTypeMap.get(emojiFileName);

            if (desiredEmojiBitmap != null) {
                //emoji bitmap found in the cache
                return desiredEmojiBitmap;
            }

            //Cache miss, try and load the bitmap from disk
            desiredEmojiBitmap = ImageHelper.loadBitmapFromInternalStorage(getContext(), emojiFileName);

            if (desiredEmojiBitmap != null) {
                //emoji bitmap found in the app storage


                //Bitmap loaded, add to cache for subsequent use.
                emojiMarkerBitmapToEmojiTypeMap.put(emojiFileName, desiredEmojiBitmap);

                return desiredEmojiBitmap;
            }

            Log.d(LOG_TAG, "Emoji not found on disk: " + emojiFileName);

            //Still unable to find the file, try to locate the emoji resource
            final int resourceId = getDrawable(getContext(), emojiFileName);

            if (resourceId == 0) {
                //unrecognised emoji file name
                throw new FileNotFoundException("Resource not found for file named: " + emojiFileName);
            }

            desiredEmojiBitmap = BitmapFactory.decodeResource(getResources(), resourceId);

            if (desiredEmojiBitmap == null) {
                //still unable to load the resource from the file
                throw new FileNotFoundException("Resource id [" + resourceId + "] but could not load bitmap: " + emojiFileName);
            }

            //Bitmap loaded, add to cache for subsequent use.
            emojiMarkerBitmapToEmojiTypeMap.put(emojiFileName, desiredEmojiBitmap);

            return desiredEmojiBitmap;
        }
    }

    class DrawingViewConfig {
        private int imageWidth = 1;
        private int surfaceViewWidth = 0;
        private int surfaceViewHeight = 0;
        private float screenToImageRatio = 0;
        private int drawThickness = 0;
        private boolean isDrawPointsEnabled = true; //by default, have the drawing thread draw tracking dots
        private boolean isDimensionsNeeded = true;
        private boolean isDrawAppearanceMarkersEnabled = true; //by default, draw the appearance markers
        private boolean isDrawEmojiMarkersEnabled = true; //by default, draw the dominant emoji markers

        private Paint dominantEmotionLabelPaint;
        private Paint dominantEmotionMetricBarPaint;
        private Paint dominantEmotionValuePaint;
        private int metricBarWidth;

        public void setDominantEmotionLabelPaints(Paint labelPaint, Paint valuePaint) {
            dominantEmotionLabelPaint = labelPaint;
            dominantEmotionValuePaint = valuePaint;
        }

        public void setDominantEmotionMetricBarConfig(Paint metricBarPaint, int metricBarWidth) {
            dominantEmotionMetricBarPaint = metricBarPaint;
            this.metricBarWidth = metricBarWidth;
        }

        public void updateViewDimensions(int surfaceViewWidth, int surfaceViewHeight, int imageWidth, int imageHeight) {
            if (surfaceViewWidth <= 0 || surfaceViewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
                throw new IllegalArgumentException("All dimensions submitted to updateViewDimensions() must be positive");
            }
            this.imageWidth = imageWidth;
            this.surfaceViewWidth = surfaceViewWidth;
            this.surfaceViewHeight = surfaceViewHeight;
            screenToImageRatio = (float) surfaceViewWidth / imageWidth;
            isDimensionsNeeded = false;
        }

        public void setDrawThickness(int t) {

            if (t <= 0) {
                throw new IllegalArgumentException("Thickness must be positive.");
            }

            drawThickness = t;
        }
    }
}
