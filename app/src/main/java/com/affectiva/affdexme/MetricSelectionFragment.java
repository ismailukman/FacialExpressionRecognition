/**
 * Copyright (c) 2016 Affectiva Inc.
 * See the file license.txt for copying permission.
 */

package com.affectiva.affdexme;

import android.app.Activity;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

import static com.affectiva.affdexme.MainActivity.NUM_METRICS_DISPLAYED;

/**
 * A fragment to display a graphical menu which allows the user to select which metrics to display.
 */
public class MetricSelectionFragment extends Fragment implements View.OnClickListener {

    final static String LOG_TAG = "AffdexMe";

    int numberOfSelectedItems = 0;

    int messageAtOrUnderLimitColor;
    int messageOverLimitColor;

    SharedPreferences sharedPreferences;

    TextView metricChooserTextView;
    GridLayout gridLayout;
    Button clearAllButton;

    HashMap<MetricsManager.Metrics, MetricSelector> metricSelectors = new HashMap<>();

    //An inner class object to control video playback in the metricSelectors
    MetricSelectionFragmentMediaPlayer fragmentMediaPlayer;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View fragmentLayout = inflater.inflate(R.layout.metric_chooser, container, false);

        initUI(fragmentLayout);

        fragmentMediaPlayer = new MetricSelectionFragmentMediaPlayer();

        restoreSettings(savedInstanceState);

        //We post the methods used to populate the gridLayout view so that they run when gridLayout has been added to the layout and sized.
        gridLayout.post(new Runnable() {
            @Override
            public void run() {
                populateGrid();
                updateAllGridItems();
            }
        });

        return fragmentLayout;
    }

    void initUI(View fragmentLayout) {
        gridLayout = (GridLayout) fragmentLayout.findViewById(R.id.metric_chooser_gridlayout);
        metricChooserTextView = (TextView) fragmentLayout.findViewById(R.id.metrics_chooser_textview);
        clearAllButton = (Button) fragmentLayout.findViewById(R.id.clear_all_button);

        clearAllButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        clearItems();
                    }
                }
        );

        messageAtOrUnderLimitColor = ContextCompat.getColor(getActivity(), R.color.white);
        messageOverLimitColor = ContextCompat.getColor(getActivity(), R.color.red);
    }


    /**
     * A method to populate the metricSelectors array using information from either a saved instance bundle (if the activity is being re-created)
     * or sharedPreferences (if the activity is being created for the first time)
     */
    void restoreSettings(Bundle bundle) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        Activity hostActivity = getActivity();
        LayoutInflater inflater = hostActivity.getLayoutInflater();
        Resources res = getResources();
        String packageName = hostActivity.getPackageName();

        //populate metricSelectors list with objects
        for (MetricsManager.Metrics metric : MetricsManager.getAllMetrics()) {
            metricSelectors.put(metric, new MetricSelector(hostActivity, inflater, res, packageName, metric));
        }

        if (bundle != null) { //if we were passed a bundle, use its data to configure the MetricSelectors
            for (MetricsManager.Metrics metric : MetricsManager.getAllMetrics()) {
                if (bundle.getBoolean(metric.toString(), false)) {
                    selectItem(metricSelectors.get(metric), true, false);
                }
            }

        } else { //otherwise, we pull the data from application preferences
            for (int i = 0; i < NUM_METRICS_DISPLAYED; i++) {
                MetricsManager.Metrics chosenMetric = PreferencesUtils.getMetricFromPrefs(sharedPreferences, i);
                selectItem(metricSelectors.get(chosenMetric), true, false);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        //save whether each MetricSelector has been selected, using the metric name as the key
        for (MetricsManager.Metrics metric : MetricsManager.getAllMetrics()) {
            bundle.putBoolean(metric.toString(), metricSelectors.get(metric).getIsSelected());
        }
        super.onSaveInstanceState(bundle);
    }

    /**
     * When the app is minimized, the active TextureView in fragmentMediaPlayer is destroyed, but throws an
     * exception if we try to remove it from its parent while in the destroyed state. Since fragmentMediaPlayer
     * begins assuming the TextureView is not attached to any parent, we run through all MetricSelectors and command
     * them to let go of the TextureView if they are the current parent.
     */
    @Override
    public void onResume() {
        super.onResume();
        for (MetricsManager.Metrics metric : MetricsManager.getAllMetrics()) {
            fragmentMediaPlayer.stopMetricSelectorPlayback(metricSelectors.get(metric));
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        saveSettings();
    }

    /* Our goal in this method is to ensure that 6 and only 6 metrics are saved in Preferences. We attempt to fill all 6 slots
    with metrics chosen by the user, but if the user did not choose 6 slots, we fill the remaining slots with any other metrics.
     */
    void saveSettings() {

        ArrayList<MetricsManager.Metrics> selectedMetrics = new ArrayList<>();

        //Add all selected metrics
        for (MetricsManager.Metrics metric : MetricsManager.getAllMetrics()) {
            if (metricSelectors.get(metric).getIsSelected()) {
                selectedMetrics.add(metric);
                if (selectedMetrics.size() >= NUM_METRICS_DISPLAYED) {
                    break;
                }
            }
        }

        //fill remaining empty slots
        if (selectedMetrics.size() < NUM_METRICS_DISPLAYED) {
            for (MetricsManager.Metrics metric : MetricsManager.getAllMetrics()) {
                if (!selectedMetrics.contains(metric)) {
                    selectedMetrics.add(metric);
                    if (selectedMetrics.size() >= NUM_METRICS_DISPLAYED) {
                        break;
                    }
                }
            }
        }

        //save list into application preferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        for (int n = 0; n < selectedMetrics.size(); n++) {
            PreferencesUtils.saveMetricToPrefs(editor, n, selectedMetrics.get(n));
        }
        editor.commit();
    }

    /* While Android offers a GridView which can automatically populate a grid from an array, we wished to divide our grid items into 'Emotions' and 'Expressions'
    categories. Therefore, we implement a scrollable GridLayout.
    Furthermore, while we wished for the grid items to take up the entire grid, Android versions lower than 21 do not support the concept of weights in a GridLayout,
    so we manually size the grid items in this method.
    Note that since this method is posted as a runnable of gridLayout, it should only be run once gridLayout has been added to the layout and sized.
     */
    void populateGrid() {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        Resources res = getResources();
        int minColumnWidth = res.getDimensionPixelSize(R.dimen.metric_chooser_column_width);

        //calculate number of columns
        int gridWidth = gridLayout.getWidth();
        int numColumns = gridWidth / minColumnWidth; //intentional integer division
        if (numColumns <= 0) {
            Log.e(LOG_TAG, "Desired Column Width too large! Unable to populate Grid");
            return;
        }
        int columnWidth = (int) ((float) gridWidth / (float) numColumns);

        //This integer reference will be used across methods to keep track of how many rows we have created.
        //Each method we pass it into leaves it at a value indicating the next row number that views should be added to.
        IntRef currentRow = new IntRef();

        addHeader("Emotions", currentRow, numColumns, inflater);
        addGridItems(currentRow, numColumns, inflater, res, columnWidth, MetricsManager.Emotions.values());
        addHeader("Expressions", currentRow, numColumns, inflater);
        addGridItems(currentRow, numColumns, inflater, res, columnWidth, MetricsManager.Expressions.values());

        // If you wanted to add Emoji as selectable metrics, you would uncomment the two lines below
//        addHeader("Emoji", currentRow, numColumns, inflater);
//        addGridItems(currentRow, numColumns, inflater, res, columnWidth, MetricsManager.Emojis.values());

        gridLayout.setColumnCount(numColumns);
        gridLayout.setRowCount(currentRow.value);
    }

    //adds a header (consisting of a TextView and border line) to the grid
    void addHeader(String name, IntRef currentRow, int numColumns, LayoutInflater inflater) {

        View header = inflater.inflate(R.layout.grid_header, null);

        //each header should take up one row and all available columns
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(GridLayout.spec(currentRow.value, 1), GridLayout.spec(0, numColumns));
        params.width = gridLayout.getWidth();
        header.setLayoutParams(params);
        gridLayout.addView(header);

        ((TextView) header.findViewById(R.id.header_text)).setText(name);

        currentRow.value += 1; //point currentRow to row where next views should be added
    }

    //adds a set of metrics (using the data from the MetricsManager object from index 'start' to index 'end')
    void addGridItems(IntRef currentRow, int numColumns, LayoutInflater inflater, Resources res, int size, MetricsManager.Metrics[] metricsToAdd) {

        //keeps track of the column we are adding to
        int col = -1; //start col at -1 so it becomes 0 during first iteration of for loop

        for (MetricsManager.Metrics metric : metricsToAdd) {

            col += 1;
            if (col >= numColumns) {
                col = 0;
                currentRow.value += 1;
            }

            MetricSelector item = metricSelectors.get(metric);
            if (item != null) {
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = size;
                params.height = size;
                params.columnSpec = GridLayout.spec(col);
                params.rowSpec = GridLayout.spec(currentRow.value);
                item.setLayoutParams(params);

                item.setOnClickListener(this);
                gridLayout.addView(item);
            } else {
                Log.e(LOG_TAG, "Unknown MetricSelector item for Metric: " + metric.toString());
            }
        }
        currentRow.value += 1; //point currentRow to row where next views should be added
    }

    @Override
    public void onClick(View v) {
        MetricSelector item = (MetricSelector) v;
        selectItem(item, !item.getIsSelected(), true); //select item if de-selected, and vice-versa
        updateAllGridItems(); //each click will result in all items being updated
    }

    /* Updates numberOfSelectedItems as well as the message presented by the text at the top of the activity
     */
    void selectItem(MetricSelector metricSelector, boolean isSelected, boolean playVideo) {
        //update numberOfSelectedItems
        boolean wasSelected = metricSelector.getIsSelected();
        if (!wasSelected && isSelected) {
            numberOfSelectedItems += 1;

            if (playVideo) {
                fragmentMediaPlayer.startMetricSelectorPlayback(metricSelector);
            }

        } else if (wasSelected && !isSelected) {
            numberOfSelectedItems -= 1;
            if (playVideo) {
                fragmentMediaPlayer.stopMetricSelectorPlayback(metricSelector);
            }
        }
        metricSelector.setIsSelected(isSelected);

        if (numberOfSelectedItems == 1) {
            metricChooserTextView.setText("1 metric chosen.");
        } else {
            metricChooserTextView.setText(String.format("%d metrics chosen.", numberOfSelectedItems));
        }

        if (numberOfSelectedItems <= NUM_METRICS_DISPLAYED) {
            metricChooserTextView.setTextColor(messageAtOrUnderLimitColor);
        } else {
            metricChooserTextView.setTextColor(messageOverLimitColor);
        }
    }

    void clearItems() {
        for (MetricsManager.Metrics metric : MetricsManager.getAllMetrics()) {
            selectItem(metricSelectors.get(metric), false, true);
        }
        updateAllGridItems();
    }

    //loop through all grid items, and update those which are tagged with an Integer (those which represent selectable metrics).
    void updateAllGridItems() {
        for (MetricsManager.Metrics metric : MetricsManager.getAllMetrics()) {
            metricSelectors.get(metric).setUnderOrOverLimit(numberOfSelectedItems <= NUM_METRICS_DISPLAYED);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        fragmentMediaPlayer.destroy();
    }

    /**
     * These are not all the MediaPlayer states defined by Android, but they are all the ones we are interested in.
     * Note that SafeMediaPlayer never stays in the STOPPED state, so we don't include it.
     */
    enum MediaPlayerState {
        IDLE, INIT, PREPARED, PLAYING
    }


    interface OnSafeMediaPlayerPreparedListener {
        void onSafeMediaPlayerPrepared();
    }

    //IntRef represents a reference to a mutable integer value
    //It is used to keep track of how many rows have been created in the populateGrid() method
    class IntRef {
        public int value;

        public IntRef() {
            value = 0;
        }
    }

    /**
     * The MetricSelector objects in this fragment will play a video when selected. To keep memory usage low, we use only one MediaPlayer
     * object to control video playback. Video is rendered on a single TextureView.
     * Chain of events that lead to video playback:
     * -When a MetricSelector is clicked, the MediaPlayer.setDataSource() is called to set the video file
     * -The TextureView is added to the view hierarchy of the MetricSelector, causing the onSurfaceTextureAvailable callback to fire
     * -The TextureView is bound to the MediaPlayer through MediaPlayer.setSurface(), then MediaPlayer.prepareAsync() is called
     * -Once preparation is complete, MediaPlayer.start() is called
     * -MediaPlayer.stop() will be called when playback finishes or the item has been de-selected, at which point the TextureView will
     * be removed from the MetricSelector's view hierarchy, causing onSurfaceTextureDestroyed(), where we call MediaPlayer.setSurface(null)
     */
    class MetricSelectionFragmentMediaPlayer {
        SafeMediaPlayer safePlayer;
        TextureView textureView;
        MetricSelector videoPlayingSelector;

        public MetricSelectionFragmentMediaPlayer() {
            safePlayer = new SafeMediaPlayer(getActivity());
            safePlayer.setOnPreparedListener(new OnSafeMediaPlayerPreparedListener() {
                @Override
                public void onSafeMediaPlayerPrepared() {
                    safePlayer.start();
                    if (!greaterThanHoneyComb()) {
                        safePlayer.seekTo(1);
                    }
                }
            });

            /**
             * Although it is best to remove the MetricSelector video cover upon reception of the
             * VIDEO_RENDERING event, this event is only available on SDK 17 and above.
             */
            if (greaterThanHoneyComb()) {
                safePlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                    @Override
                    public boolean onInfo(MediaPlayer mp, int what, int extra) {
                        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                            videoPlayingSelector.removeCover();
                        }
                        return false;
                    }
                });
            } else {
                safePlayer.setOnSeekListener(new MediaPlayer.OnSeekCompleteListener() {
                    @Override
                    public void onSeekComplete(MediaPlayer mp) {
                        videoPlayingSelector.removeCover();
                    }
                });
            }

            safePlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Uri nextVideoUri = videoPlayingSelector.getNextVideoResourceURI();
                    if (nextVideoUri == null) {
                        endVideoPlayback();
                    } else {
                        safePlayer.stopAndReset();
                        safePlayer.setDataSource(nextVideoUri);
                        safePlayer.prepareAsync();
                    }
                }
            });


            textureView = new TextureView(getActivity());
            textureView.setVisibility(View.GONE);
            textureView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    safePlayer.setSurface(new Surface(surface));
                    safePlayer.prepareAsync();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    safePlayer.stopAndReset();
                    safePlayer.setSurface(null);
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                }
            });
        }

        private void startVideoPlayback(MetricSelector metricSelector) {
            videoPlayingSelector = metricSelector;
            videoPlayingSelector.initIndex();
            Uri videoUri = metricSelector.getNextVideoResourceURI();
            if (videoUri != null) {
                safePlayer.setDataSource(videoUri);
                metricSelector.displayVideo(textureView); //will cause onSurfaceTextureAvailable to fire
            }
        }

        private void endVideoPlayback() {
            videoPlayingSelector.displayCover();
            safePlayer.stopAndReset();
            videoPlayingSelector.removeVideo(); //will cause onSurfaceTextureDestroyed() to fire
        }

        void startMetricSelectorPlayback(MetricSelector metricSelector) {
            if (videoPlayingSelector != null) {
                endVideoPlayback(); //stop previous video
            }
            startVideoPlayback(metricSelector);
        }

        void stopMetricSelectorPlayback(MetricSelector metricSelector) {
            if (metricSelector == videoPlayingSelector) { //if de-selected item is a playing video, stop it
                endVideoPlayback();
            }
        }

        public void destroy() {
            safePlayer.release(); //release resources of media player
            textureView = null;
        }

        boolean greaterThanHoneyComb() {
            return Build.VERSION.SDK_INT >= 17;
        }

    }

    /**
     * A Facade to ensure our MediaPlayer does not throw an error due to an invalid state change.
     */
    class SafeMediaPlayer {
        MediaPlayerState state;
        MediaPlayer mediaPlayer;
        Activity activity;
        OnSafeMediaPlayerPreparedListener listener = null;

        public SafeMediaPlayer(Activity activity) {
            mediaPlayer = new MediaPlayer();
            state = MediaPlayerState.IDLE;
            this.activity = activity;

            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    state = MediaPlayerState.PREPARED;
                    if (listener != null) {
                        listener.onSafeMediaPlayerPrepared();
                    }
                }
            });
        }

        void setDataSource(Uri source) {
            if (state == MediaPlayerState.IDLE) {
                try {
                    mediaPlayer.setDataSource(activity, source);
                } catch (Exception e) {
                    Log.e(LOG_TAG, e.getMessage());
                    return; //If unable to setup video, exit function
                }
                state = MediaPlayerState.INIT;
            }
        }

        void prepareAsync() {
            if (state == MediaPlayerState.INIT) {
                mediaPlayer.prepareAsync();
            }
        }

        void start() {
            if (state == MediaPlayerState.PREPARED) {
                mediaPlayer.start();
                state = MediaPlayerState.PLAYING;
            }
        }

        void seekTo(int msec) {
            if (state == MediaPlayerState.PREPARED || state == MediaPlayerState.PLAYING) {
                mediaPlayer.seekTo(msec);
            }
        }

        void stopAndReset() {
            if (state == MediaPlayerState.PLAYING || state == MediaPlayerState.PREPARED) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset(); //can be called from any state
            state = MediaPlayerState.IDLE;
        }

        void setOnPreparedListener(OnSafeMediaPlayerPreparedListener listener) {
            this.listener = listener;
        }

        //The rest of the methods are delegation methods
        void setSurface(Surface surface) {
            mediaPlayer.setSurface(surface);
        }

        void setOnCompletionListener(MediaPlayer.OnCompletionListener listener) {
            mediaPlayer.setOnCompletionListener(listener);
        }

        void setOnSeekListener(MediaPlayer.OnSeekCompleteListener listener) {
            mediaPlayer.setOnSeekCompleteListener(listener);
        }

        void setOnInfoListener(MediaPlayer.OnInfoListener listener) {
            mediaPlayer.setOnInfoListener(listener);
        }

        void release() {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

}
