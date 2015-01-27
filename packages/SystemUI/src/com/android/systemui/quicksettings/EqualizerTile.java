/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.quicksettings;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.RemoteControlClient;
import android.media.RemoteController;
import android.media.audiofx.AudioEffect;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;
import com.pheelicks.visualizer.AudioData;
import com.pheelicks.visualizer.FFTData;
import com.pheelicks.visualizer.renderer.BarGraphRenderer;
import com.pheelicks.visualizer.renderer.Renderer;

public class EqualizerTile extends QuickSettingsTile implements
        QuickSettingsTileView.OnPrepareListener {
    private boolean mLinked;
    private int mCurrentPlayState;
    private AudioManager mAudioManager;
    private RemoteController mRemoteController;
    private QuickTileVisualizer mVisualizer;
    private boolean mWifiDisplayActive;

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

    private RemoteController.OnClientUpdateListener mRCClientUpdateListener =
            new RemoteController.OnClientUpdateListener() {
        @Override
        public void onClientChange(boolean clearing) {
            if (clearing) {
                updateState(RemoteControlClient.PLAYSTATE_STOPPED);
            }
        }

        @Override
        public void onClientPlaybackStateUpdate(int state) {
            updateState(state);
        }

        @Override
        public void onClientPlaybackStateUpdate(int state, long stateChangeTimeMs,
                long currentPosMs, float speed) {
            updateState(state);
        }

        @Override
        public void onClientTransportControlUpdate(int transportControlFlags) {
            // Do nothing here
        }

        @Override
        public void onClientMetadataUpdate(RemoteController.MetadataEditor metadataEditor) {
            // Do nothing here
        }
    };

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
        public TileBarGraphRenderer(int divisions, Paint paint, int dbfuzz, int dbFactor) {
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

    public EqualizerTile(Context context, QuickSettingsController qsc) {
        super(context, qsc, R.layout.quick_settings_tile_equalizer);

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        // Until we get a callback
        mCurrentPlayState = RemoteControlClient.PLAYSTATE_NONE;

        mRemoteController = new RemoteController(context, mRCClientUpdateListener);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSettingsActivity(new Intent(
                        AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL));
            }
        };
        qsc.registerAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION, this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(intent.getAction())) {
            NetworkInfo networkInfo =
                    (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            mWifiDisplayActive = networkInfo.isConnected();
            updateResources();
        }
    }

    @Override
    protected View getImageView() {
        return mVisualizer;
    }

    @Override
    void onPostCreate() {
        mVisualizer = (QuickTileVisualizer) mTile.findViewById(R.id.visualizer_view);
        mVisualizer.setEnabled(false);

        Resources res = mContext.getResources();
        Paint paint = new Paint();
        paint.setStrokeWidth(res.getDimensionPixelSize(R.dimen.eqalizer_path_stroke_width));
        paint.setAntiAlias(true);
        paint.setColor(res.getColor(R.color.equalizer_fill_color));
        paint.setPathEffect(new DashPathEffect(new float[] {
                res.getDimensionPixelSize(R.dimen.eqalizer_path_effect_1),
                res.getDimensionPixelSize(R.dimen.eqalizer_path_effect_2)
        }, 0));

        int bars = res.getInteger(R.integer.equalizer_divisions);
        mVisualizer.addRenderer(new TileBarGraphRenderer(bars, paint,
                res.getInteger(R.integer.equalizer_db_fuzz),
                res.getInteger(R.integer.equalizer_db_fuzz_factor)));

        updateResources();
        super.onPostCreate();

        mTile.setOnPrepareListener(this);

        mAudioManager.registerRemoteController(mRemoteController);
        mTile.setVisibility(View.GONE);
    }

    @Override
    public void onDestroy() {
        mAudioManager.unregisterRemoteController(mRemoteController);
        super.onDestroy();
    }

    @Override
    public void onPrepare() {
        mLinkVisualizer.run();
    }

    @Override
    public void onUnprepare() {
        mUnlinkVisualizer.run();
    }

    @Override
    public void updateResources() {
        mLabel = mContext.getString(R.string.quick_settings_equalizer);
        super.updateResources();
    }

    @Override
    public void updateQuickSettings() {
        super.updateQuickSettings();

        boolean isMusicPlaying = isMusicPlaying();
        mVisualizer.setEnabled(isMusicPlaying);

        mTile.setVisibility(isMusicPlaying ? View.VISIBLE : View.GONE);
    }

    private void updateState(int newPlaybackState) {
        if (newPlaybackState != mCurrentPlayState) {
            mCurrentPlayState = newPlaybackState;
            updateResources();
        }
    }

    private boolean isMusicPlaying() {
        switch (mCurrentPlayState) {
            case RemoteControlClient.PLAYSTATE_PLAYING:
                if (mWifiDisplayActive) {
                    return true;
                }
                // Transport controls include remote playback clients (e.g. Chromecast)
                // so we don't want to return true in this case to avoid an empty
                // equalizer tile.
                if (mAudioManager.isMusicActiveRemotely()) {
                    return false;
                }

                return true;
            default:
                return false;
        }
    }
}
