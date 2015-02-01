/*
 * Copyright (C) 2015 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.tiles;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.audiofx.AudioEffect;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.AsyncTask;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTileView;
import com.pheelicks.visualizer.AudioData;
import com.pheelicks.visualizer.FFTData;
import com.pheelicks.visualizer.VisualizerView;
import com.pheelicks.visualizer.renderer.Renderer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VisualizerTile extends QSTile<QSTile.State>
        implements MediaSessionManager.OnActiveSessionsChangedListener {

    private Map<MediaSession.Token, CallbackInfo> mCallbacks = new HashMap<>();
    private MediaSessionManager mMediaSessionManager;
    private VisualizerView mVisualizer;
    private boolean mLinked;
    private boolean mTileVisible;
    private boolean mListening;

    public VisualizerTile(Host host) {
        super(host);
        mMediaSessionManager = (MediaSessionManager)
                mContext.getSystemService(Context.MEDIA_SESSION_SERVICE);

        // initialize state
        List<MediaController> activeSessions = mMediaSessionManager.getActiveSessions(null);
        for (MediaController activeSession : activeSessions) {
            PlaybackState playbackState = activeSession.getPlaybackState();
            if (playbackState != null && playbackState.getState() == PlaybackState.STATE_PLAYING) {
                mTileVisible = true;
                break;
            }
        }
    }

    @Override
    public QSTileView createTileView(Context context) {
        return new QSTileView(context) {
            @Override
            protected View createIcon() {
                mVisualizer = new VisualizerView(mContext);
                mVisualizer.setEnabled(false);

                Resources r = mContext.getResources();
                Paint paint = new Paint();
                paint.setStrokeWidth(r.getDimensionPixelSize(
                        R.dimen.visualizer_path_stroke_width));
                paint.setAntiAlias(true);
                paint.setColor(r.getColor(R.color.visualizer_fill_color));
                paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{
                        r.getDimensionPixelSize(R.dimen.visualizer_path_effect_1),
                        r.getDimensionPixelSize(R.dimen.visualizer_path_effect_2)
                }, 0));
                mVisualizer.addRenderer(new TileBarGraphRenderer(
                        r.getInteger(R.integer.visualizer_divisions),
                        paint,
                        r.getInteger(R.integer.visualizer_db_fuzz),
                        r.getInteger(R.integer.visualizer_db_fuzz_factor))
                );
                FrameLayout visualizerContainer = new FrameLayout(mContext);
                visualizerContainer.addView(mVisualizer, new FrameLayout.LayoutParams(
                        r.getDimensionPixelSize(R.dimen.qs_tile_icon_size_visualizer),
                        FrameLayout.LayoutParams.MATCH_PARENT, Gravity.RIGHT
                ));
                return visualizerContainer;
            }
        };
    }

    @Override
    protected State newTileState() {
        return new State();
    }

    @Override
    protected void handleClick() {
        mHost.startSettingsActivity(new Intent(
                AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL));
    }

    @Override
    protected void handleUpdateState(State state, Object arg) {
        state.visible = mTileVisible;
        state.label = mContext.getString(R.string.quick_settings_visualizer_label);
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            mMediaSessionManager.addOnActiveSessionsChangedListener(this, null);
            if (mTileVisible) {
                AsyncTask.execute(mLinkVisualizer);
            }
        } else {
            mMediaSessionManager.removeOnActiveSessionsChangedListener(this);
            AsyncTask.execute(mUnlinkVisualizer);
        }
    }

    @Override
    public void onActiveSessionsChanged(@Nullable List<MediaController> controllers) {
        if (controllers != null) {
            for (MediaController controller : controllers) {
                if (!mCallbacks.containsKey(controller.getSessionToken())) {
                    mCallbacks.put(controller.getSessionToken(), new CallbackInfo(controller));
                }
            }
        }
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        for (Map.Entry<MediaSession.Token, CallbackInfo> entry : mCallbacks.entrySet()) {
            entry.getValue().unregister();
        }
        mCallbacks.clear();
    }

    private final Runnable mLinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (mVisualizer != null) {
                if (!mLinked) {
                    mVisualizer.link(0);
                    mLinked = true;
                }
            }
        }
    };

    private final Runnable mUnlinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (mVisualizer != null) {
                if (mLinked) {
                    mVisualizer.unlink();
                    mLinked = false;
                }
            }
        }
    };

    private void checkIfPlaying() {
        boolean anythingPlaying = false;
        for (Map.Entry<MediaSession.Token, CallbackInfo> entry : mCallbacks.entrySet()) {
            if (entry.getValue().isPlaying()) {
                anythingPlaying = true;
                break;
            }
        }
        if (anythingPlaying != mTileVisible) {
            mTileVisible = anythingPlaying;
            refreshState();
        }
    }

    private class CallbackInfo {
        MediaController.Callback mCallback;
        MediaController mController;
        boolean mIsPlaying;

        public CallbackInfo(final MediaController controller) {
            this.mController = controller;
            mCallback = new MediaController.Callback() {
                @Override
                public void onSessionDestroyed() {
                    destroy();
                    checkIfPlaying();
                }

                @Override
                public void onPlaybackStateChanged(@NonNull PlaybackState state) {
                    mIsPlaying = state.getState() == PlaybackState.STATE_PLAYING;
                    checkIfPlaying();
                }
            };
            controller.registerCallback(mCallback);
        }

        public boolean isPlaying() {
            return mIsPlaying;
        }

        public void unregister() {
            mController.unregisterCallback(mCallback);
            mIsPlaying = false;
        }

        public void destroy() {
            unregister();
            mCallbacks.remove(mController.getSessionToken());
            mController = null;
            mCallback = null;
        }
    }

    private static class TileBarGraphRenderer extends Renderer {
        private int mDivisions;
        private Paint mPaint;
        private int mDbFuzz;
        private int mDbFuzzFactor;

        /**
         * Renders the FFT data as a series of lines, in histogram form
         *
         * @param divisions - must be a power of 2. Controls how many lines to draw
         * @param paint - Paint to draw lines with
         * @param dbfuzz - final dB display adjustment
         * @param dbFactor - dbfuzz is multiplied by dbFactor.
         */
        public TileBarGraphRenderer(int divisions, Paint paint,
                int dbfuzz, int dbFactor) {
            super();
            mDivisions = divisions;
            mPaint = paint;
            mDbFuzz = dbfuzz;
            mDbFuzzFactor = dbFactor;
        }

        @Override
        public void onRender(Canvas canvas, AudioData data, Rect rect) {
            // Do nothing, we only display FFT data
        }

        @Override
        public void onRender(Canvas canvas, FFTData data, Rect rect) {
            for (int i = 0; i < data.bytes.length / mDivisions; i++) {
                mFFTPoints[i * 4] = i * 4 * mDivisions;
                mFFTPoints[i * 4 + 2] = i * 4 * mDivisions;
                byte rfk = data.bytes[mDivisions * i];
                byte ifk = data.bytes[mDivisions * i + 1];
                float magnitude = (rfk * rfk + ifk * ifk);
                int dbValue = (int) (10 * Math.log10(magnitude));

                mFFTPoints[i * 4 + 1] = rect.height();
                mFFTPoints[i * 4 + 3] = rect.height() - (dbValue * mDbFuzzFactor + mDbFuzz);
            }

            canvas.drawLines(mFFTPoints, mPaint);
        }
    }
}
