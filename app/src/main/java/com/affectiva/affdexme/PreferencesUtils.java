/**
 * Copyright (c) 2016 Affectiva Inc.
 * See the file license.txt for copying permission.
 */

package com.affectiva.affdexme;

import android.content.SharedPreferences;
import android.util.Log;

/**
 * A helper class to translate strings held in preferences into values to be used by the application.
 */
public class PreferencesUtils {

    static final int DEFAULT_FPS = 20;
    private final static String LOG_TAG = "AffdexMe";

    /**
     * Attempt to parse and return FPS set by user. If the FPS is invalid, we set it to be the default FPS.
     */
    public static int getFrameProcessingRate(SharedPreferences pref) {
        String rateString = pref.getString("rate", String.valueOf(DEFAULT_FPS));
        int toReturn;
        try {
            toReturn = Integer.parseInt(rateString);
        } catch (Exception e) {
            saveFrameProcessingRate(pref, DEFAULT_FPS);
            return DEFAULT_FPS;
        }
        if (toReturn > 0) {
            return toReturn;
        } else {
            saveFrameProcessingRate(pref, DEFAULT_FPS);
            return DEFAULT_FPS;
        }
    }

    private static void saveFrameProcessingRate(SharedPreferences pref, int rate) {
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("rate", String.valueOf(rate));
        editor.commit();
    }

    public static MetricsManager.Metrics getMetricFromPrefs(SharedPreferences pref, int index) {
        MetricsManager.Metrics metric;
        try {
            String stringFromPref = pref.getString(String.format("metric_display_%d", index), defaultMetric(index).toString());
            metric = parseSavedMetric(stringFromPref);
        } catch (IllegalArgumentException e) {
            metric = defaultMetric(index);
            SharedPreferences.Editor editor = pref.edit();
            editor.putString(String.format("metric_display_%d", index), defaultMetric(index).toString());
            editor.commit();
        }
        return metric;
    }

    public static void saveMetricToPrefs(SharedPreferences.Editor editor, int index, MetricsManager.Metrics metric) {
        if (metric.getType().equals(MetricsManager.MetricType.Emoji)) {
            MetricsManager.Emojis emoji = (MetricsManager.Emojis) metric;
            editor.putString(String.format("metric_display_%d", index), emoji.getDisplayName());
        } else {
            editor.putString(String.format("metric_display_%d", index), metric.toString());
        }
    }

    static private MetricsManager.Metrics defaultMetric(int index) {
        switch (index) {
            case 0:
                return MetricsManager.Emotions.ANGER;
            case 1:
                return MetricsManager.Emotions.DISGUST;
            case 2:
                return MetricsManager.Emotions.FEAR;
            case 3:
                return MetricsManager.Emotions.JOY;
            case 4:
                return MetricsManager.Emotions.SADNESS;
            case 5:
                return MetricsManager.Emotions.SURPRISE;
        }

        return MetricsManager.Emotions.ANGER;
    }

    /**
     * We attempt to parse the string as any known metric.
     */
    static MetricsManager.Metrics parseSavedMetric(String metricString) throws IllegalArgumentException {
        try {
            MetricsManager.Emotions emotion;
            emotion = MetricsManager.Emotions.valueOf(metricString);
            return emotion;
        } catch (IllegalArgumentException emotionParseFailed) {
            Log.v(LOG_TAG, "Not an Emotion...");
        }
        try {
            MetricsManager.Expressions expression;
            expression = MetricsManager.Expressions.valueOf(metricString);
            return expression;
        } catch (IllegalArgumentException expressionParseFailed) {
            Log.v(LOG_TAG, "Not an Expression...");
        }
        try {
            MetricsManager.Emojis emoji;
            emoji = MetricsManager.Emojis.getEnum(metricString);
            return emoji;
        } catch (IllegalArgumentException expressionParseFailed) {
            Log.v(LOG_TAG, "Not an Emoji...");
        }
        throw new IllegalArgumentException("String did not match any known metric");
    }
}
