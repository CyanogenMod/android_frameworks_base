
package com.android.systemui.quicksettings;

import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.media.AudioManager;
import android.media.RemoteControlClient;
import android.media.RemoteController;
import android.media.audiofx.AudioEffect;
import android.view.View;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;
import com.pheelicks.visualizer.renderer.BarGraphRenderer;

public class EqualizerTile extends QuickSettingsTile {

    private boolean mLinked;
    private int mCurrentPlayState;
    private AudioManager mAudioManager;
    private RemoteController mRemoteController;
    private QuickTileVisualizer mVisualizer;

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
                }

                @Override
                public void onClientMetadataUpdate(RemoteController.MetadataEditor metadataEditor) {
                }
            };

    public EqualizerTile(Context context, QuickSettingsController qsc) {
        super(context, qsc, R.layout.quick_settings_tile_eq);
        mLabel = context.getString(R.string.quick_settings_equalizer);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        mCurrentPlayState = RemoteControlClient.PLAYSTATE_NONE; // until we get a callback
        mRemoteController = new RemoteController(context, mRCClientUpdateListener);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSettingsActivity(new Intent(
                        AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL));
            }
        };

    }

    @Override
    void onPostCreate() {
        super.onPostCreate();
        mVisualizer = (QuickTileVisualizer) mTile.findViewById(R.id.visualizer_view);
        mVisualizer.setEnabled(false);
        if (mVisualizer != null) {
            Paint paint = new Paint();
            paint.setStrokeWidth(12f);
            paint.setAntiAlias(true);
            paint.setColor(mContext.getResources().getColor(R.color.equalizer_fill_color));
            paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{
                    20, 2
            }, 0));
            mVisualizer.addRenderer(new BarGraphRenderer(4, paint, false));
        }

        mTile.setOnPrepareListener(new QuickSettingsTileView.OnPrepareListener() {
            @Override
            public void onPrepare() {
                mAudioManager.registerRemoteController(mRemoteController);
            }

            @Override
            public void onUnprepare() {
                mAudioManager.unregisterRemoteController(mRemoteController);
            }
        });
        mTile.setVisibility(View.GONE);
    }

    @Override
    public void updateQuickSettings() {
        super.updateQuickSettings();

        final boolean isMusicPlaying = isMusicPlaying();
        mTile.removeCallbacks(mLinkVisualizer);
        mTile.removeCallbacks(mUnlinkVisualizer);
        if (isMusicPlaying) {
            mTile.postDelayed(mLinkVisualizer, 2000);
        } else {
            mTile.postDelayed(mUnlinkVisualizer, 2000);
        }
    }

    private void updateState(int newPlaybackState) {
        if (newPlaybackState != mCurrentPlayState) {
            mCurrentPlayState = newPlaybackState;
            updateResources();
        }
    }

    private boolean isMusicPlaying() {
        switch (mCurrentPlayState) {
            case RemoteControlClient.PLAYSTATE_PAUSED:
                return mAudioManager.isMusicActive();

            case RemoteControlClient.PLAYSTATE_BUFFERING:
            case RemoteControlClient.PLAYSTATE_SKIPPING_FORWARDS:
            case RemoteControlClient.PLAYSTATE_SKIPPING_BACKWARDS:
                // don't hide it
                return true;

            case RemoteControlClient.PLAYSTATE_PLAYING:
                // transport controls include remote playback clients (e.g. Chromecast)
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

    private final Runnable mCheckVisibility = new Runnable() {
        @Override
        public void run() {
            mTile.setVisibility(isMusicPlaying() ? View.VISIBLE : View.GONE);
        }
    };

    @Override
    public void onSettingsHidden() {
        if (mVisualizer != null) {
            mVisualizer.setEnabled(false);
        }
        mCheckVisibility.run();
    }

    @Override
    public void onSettingsVisible() {
        if (mVisualizer != null) {
            mVisualizer.setEnabled(true);
        }
        mTile.postDelayed(mCheckVisibility, 1000);
    }

}
