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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.os.PowerManager;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTileView;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.pheelicks.visualizer.AudioData;
import com.pheelicks.visualizer.FFTData;
import com.pheelicks.visualizer.VisualizerView;
import com.pheelicks.visualizer.renderer.Renderer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VisualizerTile extends QSTile<QSTile.State>
        implements MediaSessionManager.OnActiveSessionsChangedListener, KeyguardMonitor.Callback {

    private Map<MediaSession.Token, CallbackInfo> mCallbacks = new HashMap<>();
    private MediaSessionManager mMediaSessionManager;
    private KeyguardMonitor mKeyguardMonitor;
    private VisualizerView mVisualizer;
    private ImageView mStaticVisualizerIcon;
    private boolean mLinked;
    private boolean mIsAnythingPlaying;
    private boolean mListening;
    private boolean mPowerSaveModeEnabled;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGING.equals(intent.getAction())) {
                mPowerSaveModeEnabled = intent.getBooleanExtra(PowerManager.EXTRA_POWER_SAVE_MODE,
                        false);
                checkIfPlaying();
            }
        }
    };

    public VisualizerTile(Host host) {
        super(host);
        mMediaSessionManager = (MediaSessionManager)
                mContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
        mKeyguardMonitor = host.getKeyguardMonitor();
        mKeyguardMonitor.addCallback(this);

        mContext.registerReceiver(mReceiver,
                new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGING));
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mPowerSaveModeEnabled = pm.isPowerSaveMode();

        // initialize state
        if (!mPowerSaveModeEnabled) {
            List<MediaController> activeSessions = mMediaSessionManager.getActiveSessions(null);
            for (MediaController activeSession : activeSessions) {
                PlaybackState playbackState = activeSession.getPlaybackState();
                if (playbackState != null && playbackState.getState()
                        == PlaybackState.STATE_PLAYING) {
                    mIsAnythingPlaying = true;
                    break;
                }
            }
        }
        if (mIsAnythingPlaying && !mLinked) {
            AsyncTask.execute(mLinkVisualizer);
        } else if (!mIsAnythingPlaying && mLinked) {
            AsyncTask.execute(mUnlinkVisualizer);
        }
    }

    @Override
    public QSTileView createTileView(Context context) {
        return new QSTileView(context) {
            @Override
            protected View createIcon() {
                Resources r = mContext.getResources();

                mVisualizer = new VisualizerView(mContext);
                mVisualizer.setEnabled(false);
                mVisualizer.setVisibility(View.VISIBLE);
                mVisualizer.setAlpha(1.f);

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

                mStaticVisualizerIcon = new ImageView(mContext);
                mStaticVisualizerIcon.setId(android.R.id.icon);
                mStaticVisualizerIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                mStaticVisualizerIcon.setImageResource(R.drawable.ic_qs_visualizer_static);
                mStaticVisualizerIcon.setVisibility(View.VISIBLE);
                mStaticVisualizerIcon.setAlpha(0.f);

                FrameLayout visualizerContainer = new FrameLayout(mContext);
                visualizerContainer.addView(mVisualizer, new FrameLayout.LayoutParams(
                        r.getDimensionPixelSize(R.dimen.qs_tile_icon_size_visualizer_width),
                        r.getDimensionPixelSize(R.dimen.qs_tile_icon_size_visualizer_height),
                        Gravity.TOP | Gravity.CENTER_HORIZONTAL
                ));
                visualizerContainer.addView(mStaticVisualizerIcon, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
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
        state.visible = true;
        state.label = mContext.getString(R.string.quick_settings_visualizer_label);

        mUiHandler.post(mUpdateVisibilities);
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            mMediaSessionManager.addOnActiveSessionsChangedListener(this, null);
        } else {
            mMediaSessionManager.removeOnActiveSessionsChangedListener(this);
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
        mKeyguardMonitor.removeCallback(this);
        mContext.unregisterReceiver(mReceiver);
    }

    private void checkIfPlaying() {
        boolean anythingPlaying = false;
        if (!mPowerSaveModeEnabled) {
            for (Map.Entry<MediaSession.Token, CallbackInfo> entry : mCallbacks.entrySet()) {
                if (entry.getValue().isPlaying()) {
                    anythingPlaying = true;
                    break;
                }
            }
        }
        if (anythingPlaying != mIsAnythingPlaying) {
            mIsAnythingPlaying = anythingPlaying;
            if (mIsAnythingPlaying && !mLinked) {
                AsyncTask.execute(mLinkVisualizer);
            } else if (!mIsAnythingPlaying && mLinked) {
                AsyncTask.execute(mUnlinkVisualizer);
            }

            mHandler.removeCallbacks(mRefreshStateRunnable);
            mHandler.postDelayed(mRefreshStateRunnable, 50);
        }
    }

    @Override
    public void onKeyguardChanged() {
        if (mKeyguardMonitor.isShowing()) {
            if (mLinked) {
                // explicitly unlink
                AsyncTask.execute(mUnlinkVisualizer);
            }
        } else {
            // no keyguard, relink if there's something playing
            if (mIsAnythingPlaying && !mLinked) {
                AsyncTask.execute(mLinkVisualizer);
            } else if (!mIsAnythingPlaying && mLinked) {
                AsyncTask.execute(mUnlinkVisualizer);
            }
        }
        refreshState();
    }

    private final Runnable mRefreshStateRunnable = new Runnable() {
        @Override
        public void run() {
            refreshState();
        }
    };

    private final Runnable mUpdateVisibilities = new Runnable() {
        @Override
        public void run() {
            boolean showVz = mIsAnythingPlaying && !mKeyguardMonitor.isShowing();
            mVisualizer.animate().cancel();
            mVisualizer.animate()
                    .setDuration(200)
                    .alpha(showVz ? 1.f : 0.f);

            mStaticVisualizerIcon.animate().cancel();
            mStaticVisualizerIcon.animate()
                    .setDuration(200)
                    .alpha(showVz ? 0.f : 1.f);
        }
    };

    private final Runnable mLinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (mVisualizer != null) {
                if (!mLinked && !mKeyguardMonitor.isShowing()) {
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
