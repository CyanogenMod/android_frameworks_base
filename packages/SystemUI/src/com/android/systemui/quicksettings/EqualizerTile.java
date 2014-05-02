
package com.android.systemui.quicksettings;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioManager;
import android.media.RemoteControlClient;
import android.media.RemoteController;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;
import com.pheelicks.visualizer.renderer.BarGraphRenderer;

public class EqualizerTile extends QuickSettingsTile {

    private int mCurrentPlayState;
    private AudioManager mAudioManager;
    private RemoteController mRemoteController;
    private QuickTileVisualizer mVisualizer;

    private RemoteController.OnClientUpdateListener mRCClientUpdateListener =
            new RemoteController.OnClientUpdateListener() {
                @Override
                public void onClientChange(boolean clearing) {
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
                }

                @Override
                public void onClientMetadataUpdate(RemoteController.MetadataEditor metadataEditor) {
                }
            };

    public EqualizerTile(Context context,
            QuickSettingsController qsc) {
        super(context, qsc, R.layout.quick_settings_tile_eq);
        mLabel = context.getString(R.string.quick_settings_equalizer);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        mCurrentPlayState = RemoteControlClient.PLAYSTATE_NONE; // until we get
                                                                // a callback
        mRemoteController = new RemoteController(context, mRCClientUpdateListener);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSettingsActivity(
                new Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL"));
            }
        };
    }

    @Override
    void onPostCreate() {
        super.onPostCreate();
        mVisualizer = (QuickTileVisualizer) mTile.findViewById(R.id.visualizer_view);
        if (mVisualizer != null) {
            Paint paint = new Paint();
            paint.setStrokeWidth(12f);
            paint.setAntiAlias(true);
            paint.setColor(Color.argb(150, 255, 255, 255));
            paint.setPathEffect(new android.graphics.DashPathEffect(new float[] {
                    20, 2
            }, 0));
            mVisualizer.addRenderer(new BarGraphRenderer(4, paint, false));
        }

        mTile.setOnPrepareListener(new QuickSettingsTileView.OnPrepareListener() {
            @Override
            public void onPrepare() {
                mTile.post(mLinkVisualizer);
                mAudioManager.registerRemoteController(mRemoteController);

            }

            @Override
            public void onUnprepare() {
                mTile.post(mUnlinkVisualizer);
                mAudioManager.unregisterRemoteController(mRemoteController);
            }
        });

    }

    @Override
    public void updateQuickSettings() {
        mTile.setVisibility(isMusicPlaying(mCurrentPlayState) ? View.VISIBLE : View.GONE);
        super.updateQuickSettings();
    }

    private void updateState(int newPlaybackState) {
        if (newPlaybackState != mCurrentPlayState) {
            mCurrentPlayState = newPlaybackState;
            updateResources();
        }
        if (isMusicPlaying(mCurrentPlayState)) {
            mTile.post(mLinkVisualizer);
        } else {
            mTile.post(mUnlinkVisualizer);
        }
    }

    private boolean isMusicPlaying(int playbackState) {
        // This should agree with the list in AudioService.isPlaystateActive()
        switch (playbackState) {
            case RemoteControlClient.PLAYSTATE_PLAYING:
            case RemoteControlClient.PLAYSTATE_BUFFERING:
            case RemoteControlClient.PLAYSTATE_FAST_FORWARDING:
            case RemoteControlClient.PLAYSTATE_REWINDING:
            case RemoteControlClient.PLAYSTATE_SKIPPING_BACKWARDS:
            case RemoteControlClient.PLAYSTATE_SKIPPING_FORWARDS:
                return mAudioManager.isMusicActive();
            default:
                return false;
        }
    }

    private boolean mLinked = false;
    private final Runnable mLinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (null != mVisualizer) {
                if (!mLinked) {
                    mVisualizer.link(0);
                    mLinked = true;
                }
                mVisualizer.setVisibility(View.VISIBLE);
            }
        }
    };

    private final Runnable mUnlinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (null != mVisualizer) {
                if (mLinked) {
                    mVisualizer.unlink();
                    mLinked = false;
                }
                mVisualizer.setVisibility(View.GONE);
            }
        }
    };

    private final Runnable mLinkVisualizerNoHide = new Runnable() {
        @Override
        public void run() {
            if (null != mVisualizer) {
                if (!mLinked) {
                    mVisualizer.link(0);
                    mLinked = true;
                }
            }
        }
    };

    private final Runnable mUnlinkVisualizerNoHide = new Runnable() {
        @Override
        public void run() {
            if (null != mVisualizer) {
                if (mLinked) {
                    mVisualizer.unlink();
                    mLinked = false;
                }
            }
        }
    };

    @Override
    public void onSettingsHidden() {
        mTile.post(mUnlinkVisualizerNoHide);
    }

    @Override
    public void onSettingsVisible() {
        mTile.post(mLinkVisualizerNoHide);
    };

}
