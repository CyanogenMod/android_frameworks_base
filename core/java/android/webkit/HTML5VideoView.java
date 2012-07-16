/* Copyright (c) 2011, 2012, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package android.webkit;

import android.Manifest.permission;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.Metadata;
import android.net.Uri;
import android.opengl.GLES20;
import android.os.PowerManager;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.MediaController.MediaPlayerControl;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @hide This is only used by the browser
 */
public class HTML5VideoView implements MediaPlayer.OnPreparedListener,
    MediaPlayerControl, View.OnTouchListener, TextureView.SurfaceTextureListener,
    SurfaceTexture.OnFrameAvailableListener, MediaPlayer.OnVideoSizeChangedListener
{
    private static final String LOGTAG = "HTML5VideoView";
    private static final String COOKIE = "Cookie";
    private static final String HIDE_URL_LOGS = "x-hide-urls-from-log";

    private static final long ANIMATION_DURATION = 750L; // in ms

    // For handling the seekTo before prepared, we need to know whether or not
    // the video is prepared. Therefore, we differentiate the state between
    // prepared and not prepared.
    // When the video is not prepared, we will have to save the seekTo time,
    // and use it when prepared to play.
    // NOTE: these values are in sync with VideoLayerAndroid.h in webkit side.
    // Please keep them in sync when changed.
    static final int STATE_INITIALIZED        = 0;
    static final int STATE_PREPARING          = 1;
    static final int STATE_PREPARED           = 2;
    static final int STATE_PLAYING            = 3;
    static final int STATE_BUFFERING          = 4;
    static final int STATE_RELEASED           = 5;

    static final int ANIMATION_STATE_NONE     = 0;
    static final int ANIMATION_STATE_STARTED  = 1;
    static final int ANIMATION_STATE_FINISHED = 2;
    private int mAnimationState;

    private HTML5VideoViewProxy mProxy;

    // Save the seek time when not prepared. This can happen when switching
    // video besides initial load.
    private int mSaveSeekTime;

    private MediaPlayer mPlayer;
    private int mCurrentState;

    // We need to save such info.
    private Uri mUri;
    private Map<String, String> mHeaders;

    // The timer for timeupate events.
    // See http://www.whatwg.org/specs/web-apps/current-work/#event-media-timeupdate
    private Timer mTimer;

    private boolean mIsFullscreen;

    protected boolean mPauseDuringPreparing;

    // The spec says the timer should fire every 250 ms or less.
    private static final int TIMEUPDATE_PERIOD = 250;  // ms

    private int mVideoWidth;
    private int mVideoHeight;
    private int mDuration;

    private int mFullscreenWidth;
    private int mFullscreenHeight;

    private float mInlineX;
    private float mInlineY;
    private float mInlineWidth;
    private float mInlineHeight;

    private Point mDisplaySize;
    private int[] mWebViewLocation;

    // The Media Controller only used for full screen mode
    private MediaController mMediaController;

    // Data only for MediaController
    private boolean mCanSeekBack;
    private boolean mCanSeekForward;
    private boolean mCanPause;
    private int mCurrentBufferPercentage;

    // The progress view.
    private View mProgressView;
    // The container for the progress view and video view
    private FrameLayout mLayout;

    private SurfaceTexture mSurfaceTexture;
    private VideoTextureView mTextureView;
    // m_textureNames is the texture bound with this SurfaceTexture.
    private int[] mTextureNames;
    private boolean mNeedsAttachToInlineGlContext;

    // common Video control FUNCTIONS:
    public void start() {
        if (mCurrentState == STATE_PREPARED) {
            // When replaying the same video, there is no onPrepared call.
            // Therefore, the timer should be set up here.
            if (mTimer == null)
            {
                mTimer = new Timer();
                mTimer.schedule(new TimeupdateTask(mProxy), TIMEUPDATE_PERIOD,
                        TIMEUPDATE_PERIOD);
            }
            mPlayer.start();

            setPlayerBuffering(false);
            // Notify webkit MediaPlayer that video is playing to make sure
            // webkit MediaPlayer is always synchronized with the proxy.
            // This is particularly important when using the fullscreen
            // MediaController.
            mProxy.dispatchOnPlaying();

            if (mMediaController != null)
                mMediaController.show();
        } else
            setStartWhenPrepared(true);
    }

    public void pause() {
        if (isPlaying()) {
            mPlayer.pause();
        } else if (mCurrentState == STATE_PREPARING) {
            mPauseDuringPreparing = true;
        }
        // Notify webkit MediaPlayer that video is paused to make sure
        // webkit MediaPlayer is always synchronized with the proxy
        // This is particularly important when using the fullscreen
        // MediaController.
        mProxy.dispatchOnPaused();

        if (mMediaController != null)
            mMediaController.show(0);

        // Delete the Timer to stop it since there is no stop call.
        if (mTimer != null) {
            mTimer.purge();
            mTimer.cancel();
            mTimer = null;
        }
    }

    public void attachToInlineGlContextIfNeeded() {
        if (mNeedsAttachToInlineGlContext && !mIsFullscreen
                && (mCurrentState == STATE_PREPARED ||
                    mCurrentState == STATE_PREPARING ||
                    mCurrentState == STATE_PLAYING)) {
            // Attach the previous GL texture
            try {
                mSurfaceTexture.attachToGLContext(getTextureName());
                mNeedsAttachToInlineGlContext = false;
            } catch (RuntimeException e) {
                // This can occur when the EGL context has been detached from this view.
                // Just try to re-attach at a later time.
            }
        }
    }

    public int getDuration() {
        if (mCurrentState == STATE_PREPARED) {
            return mPlayer.getDuration();
        } else {
            return -1;
        }
    }

    public int getCurrentPosition() {
        if (mCurrentState == STATE_PREPARED) {
            return mPlayer.getCurrentPosition();
        }
        return 0;
    }

    public void seekTo(int pos) {
        if (mCurrentState == STATE_PREPARED)
            mPlayer.seekTo(pos);
        else
            mSaveSeekTime = pos;
    }

    public boolean isPlaying() {
        if (mCurrentState == STATE_PREPARED) {
            return mPlayer.isPlaying();
        } else {
            return false;
        }
    }

    public void release() {
        if (mCurrentState != STATE_RELEASED) {
            stopPlayback();
            mPlayer.release();
            mSurfaceTexture.release();
        }
        mCurrentState = STATE_RELEASED;
    }

    public void stopPlayback() {
        if (mCurrentState == STATE_PREPARED) {
            mPlayer.stop();
        }
    }

    public boolean getPauseDuringPreparing() {
        return mPauseDuringPreparing;
    }

    public void setVolume(float volume) {
        if (mCurrentState != STATE_RELEASED) {
            mPlayer.setVolume(volume, volume);
        }
    }

    // Every time we start a new Video, we create a VideoView and a MediaPlayer
    HTML5VideoView(HTML5VideoViewProxy proxy, int position) {
        mPlayer = new MediaPlayer();
        mCurrentState = STATE_INITIALIZED;
        mProxy = proxy;
        mSaveSeekTime = position;
        mTimer = null;
        mPauseDuringPreparing = false;
        mIsFullscreen = false;
        mDisplaySize = new Point();
        mWebViewLocation = new int[2];
    }

    private static Map<String, String> generateHeaders(String url,
            HTML5VideoViewProxy proxy) {
        boolean isPrivate = proxy.getWebView().isPrivateBrowsingEnabled();
        String cookieValue = CookieManager.getInstance().getCookie(url, isPrivate);
        Map<String, String> headers = new HashMap<String, String>();
        if (cookieValue != null) {
            headers.put(COOKIE, cookieValue);
        }
        if (isPrivate) {
            headers.put(HIDE_URL_LOGS, "true");
        }

        return headers;
    }

    public void setVideoURI(String uri) {
        mUri = Uri.parse(uri);
        mHeaders = generateHeaders(uri, mProxy);
    }

    // When there is a frame ready from surface texture, we should tell WebView
    // to refresh.
    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        // TODO: This should support partial invalidation too.
        mProxy.getWebView().invalidate();
    }

    public void retrieveMetadata(HTML5VideoViewProxy proxy) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(mUri.toString(), mHeaders);
            mVideoWidth = Integer.parseInt(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            mVideoHeight = Integer.parseInt(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            mDuration = Integer.parseInt(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION));
            proxy.updateSizeAndDuration(mVideoWidth, mVideoHeight, mDuration);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (RuntimeException e) {
            // RuntimeException occurs when connection is not available or
            // the source type is not supported (e.g. HLS). Not calling
            // e.printStackTrace() here since it occurs quite often.
        } finally {
            retriever.release();
        }
    }

    // Listeners setup FUNCTIONS:
    public void setOnCompletionListener(HTML5VideoViewProxy proxy) {
        mPlayer.setOnCompletionListener(proxy);
    }

    private void prepareDataCommon(HTML5VideoViewProxy proxy) {
        try {
            mPlayer.setDataSource(proxy.getContext(), mUri, mHeaders);
            mPlayer.prepareAsync();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCurrentState = STATE_PREPARING;
    }

    public void prepareDataAndDisplayMode() {
        decideDisplayMode();

        mPlayer.setOnCompletionListener(mProxy);
        mPlayer.setOnPreparedListener(this);
        mPlayer.setOnErrorListener(mProxy);
        mPlayer.setOnInfoListener(mProxy);
        mPlayer.setOnVideoSizeChangedListener(this);

        prepareDataCommon(mProxy);

        // TODO: This is a workaround, after b/5375681 fixed, we should switch
        // to the better way.
        if (mProxy.getContext().checkCallingOrSelfPermission(permission.WAKE_LOCK)
                == PackageManager.PERMISSION_GRANTED) {
            mPlayer.setWakeMode(mProxy.getContext(), PowerManager.FULL_WAKE_LOCK);
        }
        if (!mIsFullscreen)
            setInlineFrameAvailableListener();
    }

    // This configures the SurfaceTexture OnFrameAvailableListener in inline mode
    private void setInlineFrameAvailableListener() {
        getSurfaceTexture().setOnFrameAvailableListener(this);
    }

    public int getCurrentState() {
        if (isPlaying()) {
            return STATE_PLAYING;
        } else {
            return mCurrentState;
        }
    }

    private final class TimeupdateTask extends TimerTask {
        private HTML5VideoViewProxy mProxy;

        public TimeupdateTask(HTML5VideoViewProxy proxy) {
            mProxy = proxy;
        }

        @Override
        public void run() {
            mProxy.onTimeupdate();
        }
    }

    public void onPrepared(MediaPlayer mp) {
        mCurrentState = STATE_PREPARED;
        seekTo(mSaveSeekTime);

        if (mProxy != null)
            mProxy.onPrepared(mp);

        if (mPauseDuringPreparing || !getStartWhenPrepared())
            mPauseDuringPreparing = false;
        else
            start();

        if (mIsFullscreen) {
            attachMediaController();
            if (mProgressView != null)
                mProgressView.setVisibility(View.GONE);
        }
    }

    public void decideDisplayMode() {
        SurfaceTexture surfaceTexture = getSurfaceTexture();
        Surface surface = new Surface(surfaceTexture);
        mPlayer.setSurface(surface);
        surface.release();
    }

    // SurfaceTexture will be created lazily here
    public SurfaceTexture getSurfaceTexture() {
        // Create the surface texture.
        if (mSurfaceTexture == null || mTextureNames == null) {
            mTextureNames = new int[1];
            GLES20.glGenTextures(1, mTextureNames, 0);
            mSurfaceTexture = new SurfaceTexture(mTextureNames[0]);
        }
        return mSurfaceTexture;
    }

    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        mVideoWidth = width;
        mVideoHeight = height;
        if (mTextureView != null) {
            // Request layout now that mVideoWidth and mVideoHeight are known
            // This will trigger onMeasure to get the display size right
            mTextureView.requestLayout();
        }
        if (mProxy != null) {
            mProxy.onVideoSizeChanged(mp, width, height);
        }
    }

    public int getTextureName() {
        if (mTextureNames != null) {
            return mTextureNames[0];
        } else {
            return 0;
        }
    }

    // This is true only when the player is buffering and paused
    private boolean mPlayerBuffering = false;

    public boolean getPlayerBuffering() {
        return mPlayerBuffering;
    }

    public void setPlayerBuffering(boolean playerBuffering) {
        mPlayerBuffering = playerBuffering;
        if (mProgressView != null)
            switchProgressView(playerBuffering);
    }

    private void switchProgressView(boolean playerBuffering) {
        if (playerBuffering)
            mProgressView.setVisibility(View.VISIBLE);
        else
            mProgressView.setVisibility(View.GONE);
    }

    class VideoTextureView extends TextureView {
        public VideoTextureView(Context context, SurfaceTexture surface) {
            super(context);
            try {
                // Detach the inline GL context
                surface.detachFromGLContext();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
            setSurfaceTexture(surface);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            mFullscreenWidth = getDefaultSize(mVideoWidth, widthMeasureSpec);
            mFullscreenHeight = getDefaultSize(mVideoHeight, heightMeasureSpec);
            if (mVideoWidth > 0 && mVideoHeight > 0) {
                if ( mVideoWidth * mFullscreenHeight > mFullscreenWidth * mVideoHeight ) {
                    mFullscreenHeight = mFullscreenWidth * mVideoHeight / mVideoWidth;
                } else if ( mVideoWidth * mFullscreenHeight < mFullscreenWidth * mVideoHeight ) {
                    mFullscreenWidth = mFullscreenHeight * mVideoWidth / mVideoHeight;
                }
            }
            setMeasuredDimension(mFullscreenWidth, mFullscreenHeight);

            if (mAnimationState == ANIMATION_STATE_NONE) {
                // Configuring VideoTextureView to inline bounds
                mTextureView.setTranslationX(getInlineXOffset());
                mTextureView.setTranslationY(getInlineYOffset());
                mTextureView.setScaleX(getInlineXScale());
                mTextureView.setScaleY(getInlineYScale());

                // inline to fullscreen zoom out animation
                mTextureView.animate().setListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator animation) {
                        mAnimationState = ANIMATION_STATE_FINISHED;
                        attachMediaController();
                    }
                });
                mTextureView.animate().setDuration(ANIMATION_DURATION);
                mAnimationState = ANIMATION_STATE_STARTED;
                mTextureView.animate().scaleX(1.0f).scaleY(1.0f).translationX(0.0f).translationY(0.0f);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            // Attach to the previous GL texture
            mNeedsAttachToInlineGlContext = true;
            attachToInlineGlContextIfNeeded();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            // Needed to update the view during orientation change when video is paused
            // Calling setOpaque() forces the layer to be updated
            setOpaque(false);
            setOpaque(true);
        }
    }

    // Note: Call this for fullscreen mode only
    // If MediaPlayer is prepared, enable the buttons
    private void attachMediaController() {
        // Get the capabilities of the player for this stream
        // This should only be called when MediaPlayer is in prepared state
        // Otherwise data will return invalid values
        if (mIsFullscreen && mCurrentState == STATE_PREPARED) {
            if (mMediaController == null) {
                MediaController mc = new FullscreenMediaController(mProxy.getContext(), mLayout);
                mc.setSystemUiVisibility(mLayout.getSystemUiVisibility());
                mMediaController = mc;
                mMediaController.setMediaPlayer(this);
                mMediaController.setAnchorView(mTextureView);
                mMediaController.setEnabled(false);
            }

            Metadata data = mPlayer.getMetadata(MediaPlayer.METADATA_ALL,
                    MediaPlayer.BYPASS_METADATA_FILTER);
            if (data != null) {
                mCanPause = !data.has(Metadata.PAUSE_AVAILABLE)
                    || data.getBoolean(Metadata.PAUSE_AVAILABLE);
                mCanSeekBack = !data.has(Metadata.SEEK_BACKWARD_AVAILABLE)
                    || data.getBoolean(Metadata.SEEK_BACKWARD_AVAILABLE);
                mCanSeekForward = !data.has(Metadata.SEEK_FORWARD_AVAILABLE)
                    || data.getBoolean(Metadata.SEEK_FORWARD_AVAILABLE);
            } else {
                mCanPause = mCanSeekBack = mCanSeekForward = true;
            }
            // mMediaController status depends on the Metadata result, so put it
            // after reading the MetaData
            mMediaController.setEnabled(true);

            if (mAnimationState == ANIMATION_STATE_FINISHED) {
                // If paused, should show the controller for ever!
                if (getStartWhenPrepared() || isPlaying())
                    mMediaController.show();
                else
                    mMediaController.show(0);
            }
        }
    }

    private void toggleMediaControlsVisiblity() {
        if (mMediaController.isShowing())
            mMediaController.hide();
        else
            mMediaController.show();
    }

    public boolean fullscreenExited() {
        return (mLayout == null);
    }

    private final WebChromeClient.CustomViewCallback mCallback =
        new WebChromeClient.CustomViewCallback() {
            public void onCustomViewHidden() {
                mProxy.prepareExitFullscreen();
            }
        };

    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
    }

    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    /**
     * Invoked when the specified {@link SurfaceTexture} is about to be destroyed.
     * If returns true, no rendering should happen inside the surface texture after this method
     * is invoked. If returns false, the client needs to call {@link SurfaceTexture#release()}.
     *
     * @param surface The surface about to be destroyed
     */
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        // Tells the TextureView not to free the buffer
        return false;
    }

    public void enterFullscreenVideoState(WebViewClassic webView, float x, float y, float w, float h) {
        if (mIsFullscreen == true)
            return;
        mIsFullscreen = true;
        mAnimationState = ANIMATION_STATE_NONE;
        mCurrentBufferPercentage = 0;
        mPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
        mInlineX = x;
        mInlineY = y;
        mInlineWidth = w;
        mInlineHeight = h;

        assert(mSurfaceTexture != null);
        mTextureView = new VideoTextureView(mProxy.getContext(), getSurfaceTexture());
        mTextureView.setOnTouchListener(this);
        mTextureView.setFocusable(true);
        mTextureView.setFocusableInTouchMode(true);
        mTextureView.requestFocus();

        mLayout = new FrameLayout(mProxy.getContext());
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.CENTER);
        mTextureView.setVisibility(View.VISIBLE);
        mTextureView.setSurfaceTextureListener(this);

        mLayout.addView(mTextureView, layoutParams);

        mLayout.setVisibility(View.VISIBLE);
        WebChromeClient client = webView.getWebChromeClient();
        if (client != null) {
            client.onShowCustomView(mLayout, mCallback);
            // Plugins like Flash will draw over the video so hide
            // them while we're playing.
            if (webView.getViewManager() != null)
                webView.getViewManager().hideAll();

            // Add progress view
            mProgressView = client.getVideoLoadingProgressView();
            if (mProgressView != null) {
                mLayout.addView(mProgressView, layoutParams);
                if (mCurrentState != STATE_PREPARED)
                    mProgressView.setVisibility(View.VISIBLE);
                else
                    mProgressView.setVisibility(View.GONE);
            }
        }
    }

    public void exitFullscreenVideoState(float x, float y, float w, float h) {
        if (mIsFullscreen == false) {
            return;
        }
        mIsFullscreen = false;

        mInlineX = x;
        mInlineY = y;
        mInlineWidth = w;
        mInlineHeight = h;

        // Don't show the controller after exiting the full screen.
        if (mMediaController != null) {
            mMediaController.hide();
            mMediaController = null;
        }

        if (mAnimationState == ANIMATION_STATE_STARTED) {
            mTextureView.animate().cancel();
            finishExitingFullscreen();
        } else {
            // fullscreen to inline zoom in animation
            mTextureView.animate().setListener(new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator animation) {
                    finishExitingFullscreen();
                }
            });

            mTextureView.animate().setDuration(ANIMATION_DURATION);
            mTextureView.animate().scaleX(getInlineXScale()).scaleY(getInlineYScale()).translationX(getInlineXOffset()).translationY(getInlineYOffset());
        }
    }

    public boolean isFullscreenMode() {
        return mIsFullscreen;
    }

    // MediaController FUNCTIONS:
    public boolean canPause() {
        return mCanPause;
    }

    public boolean canSeekBackward() {
        return mCanSeekBack;
    }

    public boolean canSeekForward() {
        return mCanSeekForward;
    }

    public int getBufferPercentage() {
        if (mPlayer != null) {
            return mCurrentBufferPercentage;
        }
        return 0;
    }

    private boolean mStartWhenPrepared = false;

    public void setStartWhenPrepared(boolean willPlay) {
        mStartWhenPrepared  = willPlay;
    }

    public boolean getStartWhenPrepared() {
        return mStartWhenPrepared;
    }

    public void showControllerInFullscreen() {
        if (mMediaController != null) {
            mMediaController.show(0);
        }
    }

    // Other listeners functions:
    private MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener =
        new MediaPlayer.OnBufferingUpdateListener() {
        public void onBufferingUpdate(MediaPlayer mp, int percent) {
            mCurrentBufferPercentage = percent;
        }
    };

    public boolean onTouch(View v, MotionEvent event) {
        if (mIsFullscreen && mMediaController != null)
            toggleMediaControlsVisiblity();
        return false;
    }

    static class FullscreenMediaController extends MediaController {

        View mVideoView;

        public FullscreenMediaController(Context context, View video) {
            super(context);
            mVideoView = video;
        }

        @Override
        public void show() {
            super.show();
            if (mVideoView != null) {
                mVideoView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }

        @Override
        public void hide() {
            if (mVideoView != null) {
                mVideoView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            }
            super.hide();
        }
    }

    private float getInlineXOffset() {
        updateDisplaySize();
        if (mInlineWidth < 0 || mInlineHeight < 0)
            return 0;
        else
            return mInlineX + mWebViewLocation[0] - (mDisplaySize.x - mInlineWidth) / 2;
    }

    private float getInlineYOffset() {
        updateDisplaySize();
        if (mInlineWidth < 0 || mInlineHeight < 0)
            return 0;
        else
            return mInlineY + mWebViewLocation[1] - (mDisplaySize.y - mInlineHeight) / 2;
    }

    private float getInlineXScale() {
        if (mInlineWidth < 0 || mInlineHeight < 0 || mFullscreenWidth == 0)
            return 0;
        else
            return mInlineWidth / mFullscreenWidth;
    }

    private float getInlineYScale() {
        if (mInlineWidth < 0 || mInlineHeight < 0 || mFullscreenHeight == 0)
            return 0;
        else
            return mInlineHeight / mFullscreenHeight;
    }

    private void updateDisplaySize() {
        WindowManager wm = (WindowManager)mProxy.getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        display.getSize(mDisplaySize);

        mProxy.getWebView().getWebView().getLocationOnScreen(mWebViewLocation);
        mWebViewLocation[1] += mProxy.getWebView().getVisibleTitleHeight();
    }

    private void finishExitingFullscreen() {
        mProxy.dispatchOnStopFullscreen();
        mLayout.removeView(mTextureView);
        mTextureView = null;
        if (mProgressView != null) {
            mLayout.removeView(mProgressView);
            mProgressView = null;
        }
        mLayout = null;
        // Re enable plugin views.
        mProxy.getWebView().getViewManager().showAll();
        // Set the frame available listener back to the inline listener
        setInlineFrameAvailableListener();
    }

}
