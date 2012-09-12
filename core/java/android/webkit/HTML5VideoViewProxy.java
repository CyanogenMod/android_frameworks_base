/*
 * Copyright (C) 2009 The Android Open Source Project
 * Copyright (c) 2011, 2012, Code Aurora Forum. All rights reserved.
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

package android.webkit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.http.EventHandler;
import android.net.http.Headers;
import android.net.http.RequestHandle;
import android.net.http.RequestQueue;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>Proxy for HTML5 video views.</p>
 */
class HTML5VideoViewProxy extends Handler
                          implements MediaPlayer.OnPreparedListener,
                          MediaPlayer.OnCompletionListener,
                          MediaPlayer.OnErrorListener,
                          MediaPlayer.OnInfoListener,
                          MediaPlayer.OnVideoSizeChangedListener {
    // Logging tag.
    private static final String LOGTAG = "HTML5VideoViewProxy";

    // Message Ids for WebCore thread -> UI thread communication.
    private static final int PLAY                = 100;
    private static final int SEEK                = 101;
    private static final int PAUSE               = 102;
    private static final int ERROR               = 103;
    private static final int LOAD_DEFAULT_POSTER = 104;
    private static final int BUFFERING_START     = 105;
    private static final int BUFFERING_END       = 106;
    private static final int INIT                = 107;
    private static final int TERM                = 108;
    private static final int SET_VOLUME          = 109;
    private static final int LOAD                = 110;
    private static final int LOAD_METADATA       = 111;
    private static final int ENTER_FULLSCREEN    = 112;
    private static final int EXIT_FULLSCREEN     = 113;

    // Message Ids to be handled on the WebCore thread
    private static final int PREPARED          = 200;
    private static final int ENDED             = 201;
    private static final int POSTER_FETCHED    = 202;
    private static final int PAUSED            = 203;
    private static final int STOPFULLSCREEN    = 204;
    private static final int SIZE_CHANGED      = 205;
    private static final int PLAYING           = 206;

    // Timer thread -> UI thread
    private static final int TIMEUPDATE = 300;

    // The C++ MediaPlayerPrivateAndroid object.
    int mNativePointer;
    // The handler for WebCore thread messages;
    private Handler mWebCoreHandler;
    // The WebViewClassic instance that created this view.
    private WebViewClassic mWebView;
    // The poster image to be shown when the video is not playing.
    // This ref prevents the bitmap from being GC'ed.
    private Bitmap mPoster;
    // The poster downloader.
    private PosterDownloader mPosterDownloader;
    // The seek position.
    private int mSeekPosition;
    // The video layer ID
    private int mVideoLayerId;

    // A helper class to control the playback. This executes on the UI thread!
    private final class VideoPlayer {
        private HTML5VideoViewProxy mProxy;
        private HTML5VideoView mHTML5VideoView;

        private boolean isVideoSelfEnded = false;
        // The cached volume before HTML5VideoView is initialized.
        // This should be set back to -1.0f every time after the
        // function mHTML5VideoView.setVolume is called.
        private float mCachedVolume = -1.0f;
        // Cached media position used to preserve playback position when
        // resuming suspended video
        private int mCachedPosition;

        private void setPlayerBuffering(boolean playerBuffering) {
            mHTML5VideoView.setPlayerBuffering(playerBuffering);
        }

        VideoPlayer(HTML5VideoViewProxy proxy) {
            mProxy = proxy;
        }

        // Every time webView setBaseLayer, this will be called.
        // When we found the Video layer, then we set the Surface Texture to it.
        // By using the baseLayer and the current video Layer ID, we can
        // identify the exact layer on the UI thread to use the SurfaceTexture.
        // We should never save the base layer handle since its lifetime is not
        // guaranteed outside of the function call from WebView::setBaseLayer.
        //
        // This function allows layer value to be null. If layer is null, only
        // the player state will be set in native code. This allows the proxy to
        // save the player state in the native video layer.
        public void setBaseLayer(int layer) {
            if (mHTML5VideoView != null) {
                int playerState = mHTML5VideoView.getCurrentState();
                if (mHTML5VideoView.getPlayerBuffering())
                    playerState = HTML5VideoView.STATE_BUFFERING;

                nativeSendSurfaceTexture(mHTML5VideoView.getSurfaceTexture(),
                        layer, mVideoLayerId, mHTML5VideoView.getTextureName(),
                        playerState, mNativePointer);

                // Re-attach the inline GL context
                // TODO: Find a better place to call this.
                mHTML5VideoView.attachToInlineGlContextIfNeeded();
            }
        }

        public void suspend() {
            if (mHTML5VideoView != null) {
                mHTML5VideoView.pause();
                mCachedPosition = getCurrentPosition();
                mHTML5VideoView.release();
                // Call setBaseLayer to update VideoLayerAndroid player state
                // This is important for flagging the associated texture for recycling
                setBaseLayer(0);
                mHTML5VideoView = null;
                // isVideoSelfEnded is false when video playback
                // has ended but is not complete.
                // isVideoSelfEnded is true only when playback is complete.
                isVideoSelfEnded = false;
                end();
            }
        }

        public void enterFullscreenVideo(String url, float x, float y, float w, float h) {
            if (ensureHTML5VideoView(url, mCachedPosition, false)) {
                mHTML5VideoView.prepareDataAndDisplayMode();
            }
            mHTML5VideoView.enterFullscreenVideoState(mWebView, x, y, w, h);
        }

        public void exitFullscreenVideo(float x, float y, float w, float h) {
            if (mHTML5VideoView != null) {
                mHTML5VideoView.exitFullscreenVideoState(x, y, w, h);
            }
        }

        public void webkitExitFullscreenVideo() {
            if (!mHTML5VideoView.fullscreenExited() && mHTML5VideoView.isFullscreenMode()) {
                WebChromeClient client = mWebView.getWebChromeClient();
                if (client != null) {
                    client.onHideCustomView();
                }
            }
        }

        // This is on the UI thread.
        public void loadMetadata(String url) {
            if (ensureHTML5VideoView(url, 0, false)) {
                mHTML5VideoView.retrieveMetadata(mProxy);
            }
        }

        public void load(String url) {
            if (ensureHTML5VideoView(url, 0, false)) {
                mHTML5VideoView.prepareDataAndDisplayMode();
            }
        }

        public void play(String url, int time) {
            if (ensureHTML5VideoView(url, time, true)) {
                mHTML5VideoView.prepareDataAndDisplayMode();
                mHTML5VideoView.seekTo(time);
            } else {
                // Here, we handle the case when we keep playing with one video
                if (!mHTML5VideoView.isPlaying()) {
                    mHTML5VideoView.start();
                    setBaseLayer(0);
                }
            }
        }

        public boolean isPlaying() {
            return (mHTML5VideoView != null && mHTML5VideoView.isPlaying());
        }

        public int getCurrentPosition() {
            int currentPosMs = 0;
            if (mHTML5VideoView != null) {
                currentPosMs = mHTML5VideoView.getCurrentPosition();
            }
            return currentPosMs;
        }

        public void seek(int time) {
            if (time >= 0 && mHTML5VideoView != null) {
                mHTML5VideoView.seekTo(time);
            }
        }

        public void pause() {
            if (mHTML5VideoView != null) {
                mHTML5VideoView.pause();
            }
        }

        public void onPrepared() {
            if (mCachedVolume >= 0.0f) {
                mHTML5VideoView.setVolume(mCachedVolume);
                mCachedVolume = -1.0f;
            }
            setBaseLayer(0);
        }

        public void end() {
            if (mHTML5VideoView != null)
                mHTML5VideoView.showControllerInFullscreen();
            if (mProxy != null) {
                if (isVideoSelfEnded)
                    mProxy.dispatchOnEnded();
                else
                    mProxy.dispatchOnPaused();
            }
            isVideoSelfEnded = false;
        }

        public void setVolume(float volume) {
            if (mHTML5VideoView != null) {
                mHTML5VideoView.setVolume(volume);
                mCachedVolume = -1.0f;
            } else {
                mCachedVolume = volume;
            }
        }

        // Return true if we have to allocate a new HTML5VideoView.
        // Otherwise return false and we can reuse the previously allocated HTML5VideoView
        private boolean ensureHTML5VideoView(String url, int time, boolean willPlay) {
            if (mHTML5VideoView == null) {
                mHTML5VideoView = new HTML5VideoView(mProxy, time);
                mHTML5VideoView.setStartWhenPrepared(willPlay);
                mHTML5VideoView.setVideoURI(url);
                return true;
            }
            return false;
        }

        public boolean isPrepared() {
            return mHTML5VideoView.getCurrentState() >= HTML5VideoView.STATE_PREPARED;
        }
    }
    private VideoPlayer mVideoPlayer;

    // A bunch event listeners for our VideoView
    // MediaPlayer.OnPreparedListener
    public void onPrepared(MediaPlayer mp) {
        mVideoPlayer.onPrepared();
        Message msg = Message.obtain(mWebCoreHandler, PREPARED);
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("dur", new Integer(mp.getDuration()));
        map.put("width", new Integer(mp.getVideoWidth()));
        map.put("height", new Integer(mp.getVideoHeight()));
        msg.obj = map;
        mWebCoreHandler.sendMessage(msg);
    }

    //MediaPlayer.OnVideoSizeChangedListener
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        Message msg = Message.obtain(mWebCoreHandler, SIZE_CHANGED);
        Map<String, Object> map = new HashMap<String, Object>();
        if (mVideoPlayer.isPrepared())
            map.put("dur", new Integer(mp.getDuration()));
        else
            map.put("dur", new Integer(0));
        map.put("width", new Integer(width));
        map.put("height", new Integer(height));
        msg.obj = map;
        mWebCoreHandler.sendMessage(msg);
    }

    // MediaPlayer.OnCompletionListener;
    public void onCompletion(MediaPlayer mp) {
        // The video ended by itself, so we need to
        // send a message to the UI thread to dismiss
        // the video view and to return to the WebView.
        // arg1 == 1 means the video ends by itself.
        sendMessage(obtainMessage(ENDED, 1, 0));
    }

    // MediaPlayer.OnErrorListener
    public boolean onError(MediaPlayer mp, int what, int extra) {
        sendMessage(obtainMessage(ERROR));
        return false;
    }

    public void dispatchOnEnded() {
        Message msg = Message.obtain(mWebCoreHandler, ENDED);
        mWebCoreHandler.sendMessage(msg);
    }

    public void dispatchOnPaused() {
        Message msg = Message.obtain(mWebCoreHandler, PAUSED);
        mWebCoreHandler.sendMessage(msg);
    }

    public void dispatchOnPlaying() {
        Message msg = Message.obtain(mWebCoreHandler, PLAYING);
        mWebCoreHandler.sendMessage(msg);
    }

    public void dispatchOnStopFullscreen() {
        Message msg = Message.obtain(mWebCoreHandler, STOPFULLSCREEN);
        mWebCoreHandler.sendMessage(msg);
    }

    public void updateSizeAndDuration(int width, int height, int duration) {
        Message msg = Message.obtain(mWebCoreHandler, SIZE_CHANGED);
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("dur", new Integer(duration));
        map.put("width", new Integer(width));
        map.put("height", new Integer(height));
        msg.obj = map;
        mWebCoreHandler.sendMessage(msg);
    }

    public void onTimeupdate() {
        sendMessage(obtainMessage(TIMEUPDATE));
    }

    // Handler for the messages from WebCore or Timer thread to the UI thread.
    @Override
    public void handleMessage(Message msg) {
        // This executes on the UI thread.
        switch (msg.what) {
            case PLAY: {
                String url = (String) msg.obj;
                int seekPosition = msg.arg1;
                mVideoPlayer.play(url, seekPosition);
                break;
            }
            case LOAD_METADATA: {
                String url = (String) msg.obj;
                mVideoPlayer.loadMetadata(url);
                break;
            }
            case LOAD: {
                String url = (String) msg.obj;
                mVideoPlayer.load(url);
                break;
            }
            case SEEK: {
                Integer time = (Integer) msg.obj;
                mSeekPosition = time;
                mVideoPlayer.seek(mSeekPosition);
                break;
            }
            case PAUSE: {
                mVideoPlayer.pause();
                break;
            }
            case ENDED:
                if (msg.arg1 == 1)
                    mVideoPlayer.isVideoSelfEnded = true;
                mVideoPlayer.end();
                break;
            case ERROR: {
                WebChromeClient client = mWebView.getWebChromeClient();
                if (client != null) {
                    client.onHideCustomView();
                }
                break;
            }
            case LOAD_DEFAULT_POSTER: {
                WebChromeClient client = mWebView.getWebChromeClient();
                if (client != null) {
                    doSetPoster(client.getDefaultVideoPoster());
                }
                break;
            }
            case TIMEUPDATE: {
                if (mVideoPlayer.isPlaying()) {
                    sendTimeupdate();
                }
                break;
            }
            case BUFFERING_START: {
                mVideoPlayer.setPlayerBuffering(true);
                break;
            }
            case BUFFERING_END: {
                mVideoPlayer.setPlayerBuffering(false);
                break;
            }
            case INIT: {
                // Pass Proxy into webview, such that every time we have a setBaseLayer
                // call, we tell this Proxy to call the native to update the layer tree
                // for the Video Layer's surface texture info
                mWebView.registerHTML5VideoViewProxy(this);
                break;
            }
            case TERM: {
                mVideoPlayer.suspend();
                mWebView.unregisterHTML5VideoViewProxy(this);
                break;
            }
            case SET_VOLUME: {
                float vol = ((Float)msg.obj).floatValue();
                mVideoPlayer.setVolume(vol);
                break;
            }
            case ENTER_FULLSCREEN: {
                InlineVideoInfo info = (InlineVideoInfo)msg.obj;
                mVideoPlayer.enterFullscreenVideo(info.getUrl(),
                        info.getX(), info.getY(), info.getWidth(), info.getHeight());
                break;
            }
            case EXIT_FULLSCREEN: {
                InlineVideoInfo info = (InlineVideoInfo)msg.obj;
                mVideoPlayer.exitFullscreenVideo(info.getX(), info.getY(),
                        info.getWidth(), info.getHeight());
                break;
            }
        }
    }

    // Everything below this comment executes on the WebCore thread, except for
    // the EventHandler methods, which are called on the network thread.

    // A helper class that knows how to download posters
    private static final class PosterDownloader implements EventHandler {
        // The request queue. This is static as we have one queue for all posters.
        private static RequestQueue mRequestQueue;
        private static int mQueueRefCount = 0;
        // The poster URL
        private URL mUrl;
        // The proxy we're doing this for.
        private final HTML5VideoViewProxy mProxy;
        // The poster bytes. We only touch this on the network thread.
        private ByteArrayOutputStream mPosterBytes;
        // The request handle. We only touch this on the WebCore thread.
        private RequestHandle mRequestHandle;
        // The response status code.
        private int mStatusCode;
        // The response headers.
        private Headers mHeaders;
        // The handler to handle messages on the WebCore thread.
        private Handler mHandler;

        public PosterDownloader(String url, HTML5VideoViewProxy proxy) {
            try {
                mUrl = new URL(url);
            } catch (MalformedURLException e) {
                mUrl = null;
            }
            mProxy = proxy;
            mHandler = new Handler();
        }
        // Start the download. Called on WebCore thread.
        public void start() {
            retainQueue();

            if (mUrl == null) {
                return;
            }

            // Only support downloading posters over http/https.
            // FIXME: Add support for other schemes. WebKit seems able to load
            // posters over other schemes e.g. file://, but gets the dimensions wrong.
            String protocol = mUrl.getProtocol();
            if ("http".equals(protocol) || "https".equals(protocol)) {
                mRequestHandle = mRequestQueue.queueRequest(mUrl.toString(), "GET", null,
                        this, null, 0);
            }
        }
        // Cancel the download if active and release the queue. Called on WebCore thread.
        public void cancelAndReleaseQueue() {
            if (mRequestHandle != null) {
                mRequestHandle.cancel();
                mRequestHandle = null;
            }
            releaseQueue();
        }
        // EventHandler methods. Executed on the network thread.
        public void status(int major_version,
                int minor_version,
                int code,
                String reason_phrase) {
            mStatusCode = code;
        }

        public void headers(Headers headers) {
            mHeaders = headers;
        }

        public void data(byte[] data, int len) {
            if (mPosterBytes == null) {
                mPosterBytes = new ByteArrayOutputStream();
            }
            mPosterBytes.write(data, 0, len);
        }

        public void endData() {
            if (mStatusCode == 200) {
                if (mPosterBytes.size() > 0) {
                    Bitmap poster = BitmapFactory.decodeByteArray(
                            mPosterBytes.toByteArray(), 0, mPosterBytes.size());
                    mProxy.doSetPoster(poster);
                }
                cleanup();
            } else if (mStatusCode >= 300 && mStatusCode < 400) {
                // We have a redirect.
                try {
                    mUrl = new URL(mHeaders.getLocation());
                } catch (MalformedURLException e) {
                    mUrl = null;
                }
                if (mUrl != null) {
                    mHandler.post(new Runnable() {
                       public void run() {
                           if (mRequestHandle != null) {
                               mRequestHandle.setupRedirect(mUrl.toString(), mStatusCode,
                                       new HashMap<String, String>());
                           }
                       }
                    });
                }
            }
        }

        public void certificate(SslCertificate certificate) {
            // Don't care.
        }

        public void error(int id, String description) {
            cleanup();
        }

        public boolean handleSslErrorRequest(SslError error) {
            // Don't care. If this happens, data() will never be called so
            // mPosterBytes will never be created, so no need to call cleanup.
            return false;
        }
        // Tears down the poster bytes stream. Called on network thread.
        private void cleanup() {
            if (mPosterBytes != null) {
                try {
                    mPosterBytes.close();
                } catch (IOException ignored) {
                    // Ignored.
                } finally {
                    mPosterBytes = null;
                }
            }
        }

        // Queue management methods. Called on WebCore thread.
        private void retainQueue() {
            if (mRequestQueue == null) {
                mRequestQueue = new RequestQueue(mProxy.getContext());
            }
            mQueueRefCount++;
        }

        private void releaseQueue() {
            if (mQueueRefCount == 0) {
                return;
            }
            if (--mQueueRefCount == 0) {
                mRequestQueue.shutdown();
                mRequestQueue = null;
            }
        }
    }

    /**
     * Private constructor.
     * @param webView is the WebView that hosts the video.
     * @param nativePtr is the C++ pointer to the MediaPlayerPrivate object.
     */
    private HTML5VideoViewProxy(WebViewClassic webView, int nativePtr, int videoLayerId) {
        // This handler is for the main (UI) thread.
        super(Looper.getMainLooper());
        // Save the WebView object.
        mWebView = webView;
        // Save the native ptr
        mNativePointer = nativePtr;
        // Save the videoLayerId. This is needed early in order to support fullscreen mode
        // before video playback
        mVideoLayerId = videoLayerId;
        // create the message handler for this thread
        createWebCoreHandler();
        mVideoPlayer = new VideoPlayer(this);
        Message message = obtainMessage(INIT);
        sendMessage(message);
    }

    private void createWebCoreHandler() {
        mWebCoreHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case PREPARED: {
                        Map<String, Object> map = (Map<String, Object>) msg.obj;
                        Integer duration = (Integer) map.get("dur");
                        Integer width = (Integer) map.get("width");
                        Integer height = (Integer) map.get("height");
                        nativeOnPrepared(duration.intValue(), width.intValue(),
                                height.intValue(), mNativePointer);
                        break;
                    }
                    case SIZE_CHANGED: {
                        Map<String, Object> map = (Map<String, Object>) msg.obj;
                        Integer duration = (Integer) map.get("dur");
                        Integer width = (Integer) map.get("width");
                        Integer height = (Integer) map.get("height");
                        nativeOnSizeChanged(duration.intValue(), width.intValue(),
                                height.intValue(), mNativePointer);
                        break;
                    }
                    case ENDED:
                        mSeekPosition = 0;
                        nativeOnEnded(mNativePointer);
                        break;
                    case PAUSED:
                        nativeOnPaused(mNativePointer);
                        break;
                    case PLAYING:
                        nativeOnPlaying(mNativePointer);
                        break;
                    case POSTER_FETCHED:
                        Bitmap poster = (Bitmap) msg.obj;
                        nativeOnPosterFetched(poster, mNativePointer);
                        break;
                    case TIMEUPDATE:
                        nativeOnTimeupdate(msg.arg1, mNativePointer);
                        break;
                    case STOPFULLSCREEN:
                        nativeOnStopFullscreen(mNativePointer);
                        break;
                }
            }
        };
    }

    private void doSetPoster(Bitmap poster) {
        if (poster == null) {
            return;
        }
        // Save a ref to the bitmap and send it over to the WebCore thread.
        mPoster = poster;
        Message msg = Message.obtain(mWebCoreHandler, POSTER_FETCHED);
        msg.obj = poster;
        mWebCoreHandler.sendMessage(msg);
    }

    private void sendTimeupdate() {
        Message msg = Message.obtain(mWebCoreHandler, TIMEUPDATE);
        msg.arg1 = mVideoPlayer.getCurrentPosition();
        mWebCoreHandler.sendMessage(msg);
    }

    public Context getContext() {
        return mWebView.getContext();
    }

    // The public methods below are all called from WebKit only.
    /**
     * Play a video stream.
     * @param url is the URL of the video stream.
     */
    public void play(String url, int position) {
        if (url == null) {
            return;
        }
        Message message = obtainMessage(PLAY);
        message.arg1 = position;
        message.obj = url;
        sendMessage(message);
    }

    /**
     * Load a video stream.
     * @param url is the URL of the video stream.
     */
    public void loadVideo(String url) {
        if (url == null) {
            return;
        }
        Message message = obtainMessage(LOAD);
        message.obj = url;
        sendMessage(message);
    }

    /**
     * Load video metadata.
     * @param url is the URL of the video stream.
     */
    public void loadMetadata(String url) {
        if (url == null) {
            return;
        }
        Message message = obtainMessage(LOAD_METADATA);
        message.obj = url;
        sendMessage(message);
    }

    /**
     * Seek into the video stream.
     * @param  time is the position in the video stream.
     */
    public void seek(int time) {
        Message message = obtainMessage(SEEK);
        message.obj = new Integer(time);
        sendMessage(message);
    }

    /**
     * Pause the playback.
     */
    public void pause() {
        Message message = obtainMessage(PAUSE);
        sendMessage(message);
    }

    /**
     * Tear down this proxy object.
     */
    public void teardown() {
        // This is called by the C++ MediaPlayerPrivate dtor.
        // Cancel any active poster download.
        if (mPosterDownloader != null) {
            mPosterDownloader.cancelAndReleaseQueue();
        }
        Message message = obtainMessage(TERM);
        sendMessage(message);
        mNativePointer = 0;
    }

    /**
     * Load the poster image.
     * @param url is the URL of the poster image.
     */
    public void loadPoster(String url) {
        if (url == null) {
            Message message = obtainMessage(LOAD_DEFAULT_POSTER);
            sendMessage(message);
            return;
        }
        // Cancel any active poster download.
        if (mPosterDownloader != null) {
            mPosterDownloader.cancelAndReleaseQueue();
        }
        // Load the poster asynchronously
        mPosterDownloader = new PosterDownloader(url, this);
        mPosterDownloader.start();
    }

    public void enterFullscreen(String url, float x, float y, float w, float h) {
        if (url == null)
            return;
        Message message = obtainMessage(ENTER_FULLSCREEN);
        message.obj = new InlineVideoInfo(url, x, y, w, h);
        sendMessage(message);
    }

    public void exitFullscreen(float x, float y, float w, float h) {
        Message message = obtainMessage(EXIT_FULLSCREEN);
        message.obj = new InlineVideoInfo(null, x, y, w, h);
        sendMessage(message);
    }

    private static final class InlineVideoInfo {
        private String mUrl;
        private float mX;
        private float mY;
        private float mWidth;
        private float mHeight;

        public InlineVideoInfo(String url, float x, float y, float w, float h) {
            mUrl = url;
            mX = x;
            mY = y;
            mWidth = w;
            mHeight = h;
        }

        public String getUrl() {
            return mUrl;
        }

        public float getX() {
            return mX;
        }

        public float getY() {
            return mY;
        }

        public float getWidth() {
            return mWidth;
        }

        public float getHeight() {
            return mHeight;
        }
    }

    // These functions are called from UI thread only by WebView.
    public void setBaseLayer(int layer) {
        mVideoPlayer.setBaseLayer(layer);
    }

    public void pauseAndDispatch() {
        // mVideoPlayer.pause will always dispatch notification
        mVideoPlayer.pause();
    }

    public void suspend() {
        mVideoPlayer.suspend();
    }

    public void webkitEnterFullscreen() {
        nativePrepareEnterFullscreen(mNativePointer);
    }

    public void prepareExitFullscreen() {
        nativePrepareExitFullscreen(mNativePointer);
    }

    public void webKitExitFullscreen() {
        mVideoPlayer.webkitExitFullscreenVideo();
    }

    public int getVideoLayerId() {
        return mVideoLayerId;
    }
    // End functions called from UI thread only by WebView

    /**
     * Change the volume of the playback
     */
    public void setVolume(float volume) {
        Message message = obtainMessage(SET_VOLUME);
        message.obj = new Float(volume);
        sendMessage(message);
    }

    /**
     * The factory for HTML5VideoViewProxy instances.
     * @param webViewCore is the WebViewCore that is requesting the proxy.
     *
     * @return a new HTML5VideoViewProxy object.
     */
    public static HTML5VideoViewProxy getInstance(WebViewCore webViewCore, int nativePtr, int videoLayerId) {
        return new HTML5VideoViewProxy(webViewCore.getWebViewClassic(), nativePtr, videoLayerId);
    }

    /* package */ WebViewClassic getWebView() {
        return mWebView;
    }

    private native void nativeOnPrepared(int duration, int width, int height, int nativePointer);
    private native void nativeOnSizeChanged(int duration, int width, int height, int nativePointer);
    private native void nativeOnEnded(int nativePointer);
    private native void nativeOnPaused(int nativePointer);
    private native void nativeOnPlaying(int nativePointer);
    private native void nativeOnPosterFetched(Bitmap poster, int nativePointer);
    private native void nativeOnTimeupdate(int position, int nativePointer);
    private native void nativeOnStopFullscreen(int nativePointer);
    private native static boolean nativeSendSurfaceTexture(SurfaceTexture texture,
            int baseLayer, int videoLayerId, int textureName,
            int playerState, int nativePointer);
    private native void nativePrepareEnterFullscreen(int nativePointer);
    private native void nativePrepareExitFullscreen(int nativePoint);

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            sendMessage(obtainMessage(BUFFERING_START, what, extra));
        } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            sendMessage(obtainMessage(BUFFERING_END, what, extra));
        }
        return false;
    }
}
