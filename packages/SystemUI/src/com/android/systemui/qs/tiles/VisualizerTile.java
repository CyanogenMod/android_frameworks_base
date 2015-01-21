package com.android.systemui.qs.tiles;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.AsyncTask;
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

    Map<MediaSession.Token, CallbackInfo> mCallbacks = new HashMap<>();

    MediaSessionManager mManager;
    VisualizerView mVisualizer;
    private boolean mLinked;

    public VisualizerTile(Host host) {
        super(host);
        mManager = (MediaSessionManager) mContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    @Override
    public QSTileView createTileView(Context context) {
        return new QSTileView(context) {
            @Override
            protected View createIcon() {
                mVisualizer = new VisualizerView(mContext);
                mVisualizer.setEnabled(false);
                Paint paint = new Paint();
                paint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.eqalizer_path_stroke_width));
                paint.setAntiAlias(true);
                paint.setColor(mContext.getResources().getColor(R.color.equalizer_fill_color));
                paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{
                        mContext.getResources().getDimensionPixelSize(R.dimen.eqalizer_path_effect_1),
                        mContext.getResources().getDimensionPixelSize(R.dimen.eqalizer_path_effect_2)
                }, 0));
                mVisualizer.addRenderer(new TileBarGraphRenderer(mContext.getResources().getInteger(
                        R.integer.equalizer_divisions),
                        paint,
                        mContext.getResources().getInteger(R.integer.equalizer_db_fuzz),
                        mContext.getResources().getInteger(R.integer.equalizer_db_fuzz_factor)));

                FrameLayout visualizerContainer = new FrameLayout(mContext);
                visualizerContainer.setClipChildren(true);
                visualizerContainer.addView(mVisualizer, new FrameLayout.LayoutParams(
                        getResources().getDimensionPixelSize(R.dimen.qs_tile_icon_size_visualizer),
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
        boolean anythingPlaying = false;
        for (Map.Entry<MediaSession.Token, CallbackInfo> entry : mCallbacks.entrySet()) {
            if (entry.getValue().isPlaying()) {
                anythingPlaying = true;
                break;
            }
        }

        state.visible = anythingPlaying;
        state.label = mContext.getString(R.string.quick_settings_visualizer_title);
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mManager.addOnActiveSessionsChangedListener(this, null);
            AsyncTask.execute(mLinkVisualizer);
        } else {
            mManager.removeOnActiveSessionsChangedListener(this);
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

    private class CallbackInfo {
        MediaController.Callback mCallback;
        MediaController mController;

        boolean isPlaying;

        public CallbackInfo(final MediaController controller) {
            this.mController = controller;
            mCallback = new MediaController.Callback() {
                @Override
                public void onSessionDestroyed() {
                    super.onSessionDestroyed();
                    controller.unregisterCallback(mCallback);
                    mCallbacks.remove(controller.getSessionToken());
                    refreshState();
                }

                @Override
                public void onPlaybackStateChanged(@NonNull PlaybackState state) {
                    super.onPlaybackStateChanged(state);
                    isPlaying = state.getState() == PlaybackState.STATE_PLAYING;
                    refreshState();
                }
            };
            controller.registerCallback(mCallback);
        }

        public boolean isPlaying() {
            return isPlaying;
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
        public TileBarGraphRenderer(int divisions,
                                    Paint paint,
                                    int dbfuzz, int dbFactor)
        {
            super();
            mDivisions = divisions;
            mPaint = paint;
            mDbFuzz = dbfuzz;
            mDbFuzzFactor = dbFactor;
        }

        @Override
        public void onRender(Canvas canvas, AudioData data, Rect rect)
        {
            // Do nothing, we only display FFT data
        }

        @Override
        public void onRender(Canvas canvas, FFTData data, Rect rect)
        {
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
