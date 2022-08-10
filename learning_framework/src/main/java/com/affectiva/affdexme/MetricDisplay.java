/**
 * Copyright (c) 2016 Affectiva Inc.
 * See the file license.txt for copying permission.
 */

package com.affectiva.affdexme;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.lang.reflect.Method;

/**
 * The MetricView class is used to display metric scores on top of colored bars whose color depend on the score.
 */
public class MetricDisplay extends View {

    MetricsManager.Metrics metricToDisplay; //indicates which of the 24 Affectiva Emotions and Expressions this view is displaying
    Method faceScoreMethod;

    float midX = 0; //coordinates of the center of the view
    float midY = 0;
    float halfWidth = 50;//default width and height of view
    float height = 10;
    String text = "";   //score in text format
    Paint textPaint;
    Paint boxPaint;
    float left = 0; //colored bar is drawn using left,right,top, and height variables
    float right = 0;
    float top = 0;
    float textBottom = 0; //tells our view where to draw the baseline of the font
    boolean isShadedMetricView = false;

    public MetricDisplay(Context context) {
        super(context);
        initResources(context, null);
    }

    public MetricDisplay(Context context, AttributeSet attrs) {
        super(context, attrs);
        initResources(context, attrs);
    }

    public MetricDisplay(Context context, AttributeSet attrs, int styleID) {
        super(context, attrs, styleID);
        initResources(context, attrs);
    }

    void setIsShadedMetricView(boolean b) {
        this.isShadedMetricView = b;
        if (!b) {
            boxPaint.setColor(Color.GREEN);
        }
    }

    void initResources(Context context, AttributeSet attrs) {

        boxPaint = new Paint();
        boxPaint.setColor(Color.GREEN);

        textPaint = new Paint();
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);

        int textSize = 15; //default text size value

        //load and parse XML attributes
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.custom_attributes, 0, 0);
            textPaint.setColor(a.getColor(R.styleable.custom_attributes_textColor, Color.BLACK));
            textSize = a.getDimensionPixelSize(R.styleable.custom_attributes_textSize, textSize);
            textPaint.setTextSize(textSize);
            halfWidth = a.getDimensionPixelSize(R.styleable.custom_attributes_metricBarLength, 100) / 2;
            a.recycle();
        } else {
            textPaint.setColor(Color.BLACK);
            textPaint.setTextSize(textSize);
        }

        /**
         * We set the desired height of the view to be as large as our text.
         * We also offset the bottom line at which our text is drawn, to give the appearance
         * that the text is centered vertically.
         */
        height = textSize;
        textBottom = height - 5;
    }

    public void setMetricToDisplay(MetricsManager.Metrics metricToDisplay, Method faceScoreMethod) {
        this.metricToDisplay = metricToDisplay;
        this.faceScoreMethod = faceScoreMethod;
    }

    public MetricsManager.Metrics getMetricToDisplay() {
        return this.metricToDisplay;
    }

    public Method getFaceScoreMethod() {
        return this.faceScoreMethod;
    }

    public void setTypeface(Typeface face) {
        textPaint.setTypeface(face);
    }

    public void setScore(float s) {
        text = String.format("%.0f%%", s);  //change the text of the view

        //shading mode is turned on for Valence, which causes this view to shade its color according
        //to the value of 's'
        if (isShadedMetricView) {
            if (s > 0) {
                left = midX - (halfWidth * (s / 100));
                right = midX + (halfWidth * (s / 100));
            } else {
                left = midX - (halfWidth * (-s / 100));
                right = midX + (halfWidth * (-s / 100));
            }
            if (s > 0) {
                float colorScore = ((100f - s) / 100f) * 255;
                boxPaint.setColor(Color.rgb((int) colorScore, 255, (int) colorScore));
            } else {
                float colorScore = ((100f + s) / 100f) * 255;
                boxPaint.setColor(Color.rgb(255, (int) colorScore, (int) colorScore));
            }
        } else {
            left = midX - (halfWidth * (s / 100)); //change the coordinates at which the colored bar will be drawn
            right = midX + (halfWidth * (s / 100));
        }

        invalidate(); //instruct Android to re-draw our view, now that the text has changed
    }

    /**
     * set our view to be the minimum of the sizes that Android will allow and our desired sizes
     **/
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension((int) Math.min(MeasureSpec.getSize(widthMeasureSpec), halfWidth * 2), (int) Math.min(MeasureSpec.getSize(heightMeasureSpec), height));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        midX = w / 2;
        midY = h / 2;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //draws the colored bar that appears behind our score
        canvas.drawRect(left, top, right, height, boxPaint);
        //draws the score
        canvas.drawText(text, midX, textBottom, textPaint);
    }


}
