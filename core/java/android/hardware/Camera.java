/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.hardware;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.io.IOException;

import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.graphics.ImageFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

/**
 * The Camera class is used to set image capture settings, start/stop preview,
 * snap pictures, and retrieve frames for encoding for video.  This class is a
 * client for the Camera service, which manages the actual camera hardware.
 *
 * <p>To access the device camera, you must declare the
 * {@link android.Manifest.permission#CAMERA} permission in your Android
 * Manifest. Also be sure to include the
 * <a href="{@docRoot}guide/topics/manifest/uses-feature-element.html">&lt;uses-feature></a>
 * manifest element to declare camera features used by your application.
 * For example, if you use the camera and auto-focus feature, your Manifest
 * should include the following:</p>
 * <pre> &lt;uses-permission android:name="android.permission.CAMERA" />
 * &lt;uses-feature android:name="android.hardware.camera" />
 * &lt;uses-feature android:name="android.hardware.camera.autofocus" /></pre>
 *
 * <p>To take pictures with this class, use the following steps:</p>
 *
 * <ol>
 * <li>Obtain an instance of Camera from {@link #open(int)}.
 *
 * <li>Get existing (default) settings with {@link #getParameters()}.
 *
 * <li>If necessary, modify the returned {@link Camera.Parameters} object and call
 * {@link #setParameters(Camera.Parameters)}.
 *
 * <li>If desired, call {@link #setDisplayOrientation(int)}.
 *
 * <li><b>Important</b>: Pass a fully initialized {@link SurfaceHolder} to
 * {@link #setPreviewDisplay(SurfaceHolder)}.  Without a surface, the camera
 * will be unable to start the preview.
 *
 * <li><b>Important</b>: Call {@link #startPreview()} to start updating the
 * preview surface.  Preview must be started before you can take a picture.
 *
 * <li>When you want, call {@link #takePicture(Camera.ShutterCallback,
 * Camera.PictureCallback, Camera.PictureCallback, Camera.PictureCallback)} to
 * capture a photo.  Wait for the callbacks to provide the actual image data.
 *
 * <li>After taking a picture, preview display will have stopped.  To take more
 * photos, call {@link #startPreview()} again first.
 *
 * <li>Call {@link #stopPreview()} to stop updating the preview surface.
 *
 * <li><b>Important:</b> Call {@link #release()} to release the camera for
 * use by other applications.  Applications should release the camera
 * immediately in {@link android.app.Activity#onPause()} (and re-{@link #open()}
 * it in {@link android.app.Activity#onResume()}).
 * </ol>
 *
 * <p>To quickly switch to video recording mode, use these steps:</p>
 *
 * <ol>
 * <li>Obtain and initialize a Camera and start preview as described above.
 *
 * <li>Call {@link #unlock()} to allow the media process to access the camera.
 *
 * <li>Pass the camera to {@link android.media.MediaRecorder#setCamera(Camera)}.
 * See {@link android.media.MediaRecorder} information about video recording.
 *
 * <li>When finished recording, call {@link #reconnect()} to re-acquire
 * and re-lock the camera.
 *
 * <li>If desired, restart preview and take more photos or videos.
 *
 * <li>Call {@link #stopPreview()} and {@link #release()} as described above.
 * </ol>
 *
 * <p>This class is not thread-safe, and is meant for use from one event thread.
 * Most long-running operations (preview, focus, photo capture, etc) happen
 * asynchronously and invoke callbacks as necessary.  Callbacks will be invoked
 * on the event thread {@link #open(int)} was called from.  This class's methods
 * must never be called from multiple threads at once.</p>
 *
 * <p class="caution"><strong>Caution:</strong> Different Android-powered devices
 * may have different hardware specifications, such as megapixel ratings and
 * auto-focus capabilities. In order for your application to be compatible with
 * more devices, you should not make assumptions about the device camera
 * specifications.</p>
 */
public class Camera {
    private static final String TAG = "Camera";

    // These match the enums in frameworks/base/include/camera/Camera.h
    private static final int CAMERA_MSG_ERROR            = 0x001;
    private static final int CAMERA_MSG_SHUTTER          = 0x002;
    private static final int CAMERA_MSG_FOCUS            = 0x004;
    private static final int CAMERA_MSG_ZOOM             = 0x008;
    private static final int CAMERA_MSG_PREVIEW_FRAME    = 0x010;
    private static final int CAMERA_MSG_VIDEO_FRAME      = 0x020;
    private static final int CAMERA_MSG_POSTVIEW_FRAME   = 0x040;
    private static final int CAMERA_MSG_RAW_IMAGE        = 0x080;
    private static final int CAMERA_MSG_COMPRESSED_IMAGE = 0x100;
    private static final int CAMERA_MSG_ALL_MSGS         = 0x1FF;

    private int mNativeContext; // accessed by native methods
    private EventHandler mEventHandler;
    private ShutterCallback mShutterCallback;
    private PictureCallback mRawImageCallback;
    private PictureCallback mJpegCallback;
    private PreviewCallback mPreviewCallback;
    private PictureCallback mPostviewCallback;
    private AutoFocusCallback mAutoFocusCallback;
    private OnZoomChangeListener mZoomListener;
    private ErrorCallback mErrorCallback;
    private boolean mOneShot;
    private boolean mWithBuffer;

    /**
     * Returns the number of physical cameras available on this device.
     */
    public native static int getNumberOfCameras();

    /**
     * Returns the information about a particular camera.
     * If {@link #getNumberOfCameras()} returns N, the valid id is 0 to N-1.
     */
    public native static void getCameraInfo(int cameraId, CameraInfo cameraInfo);

    /**
     * Information about a camera
     */
    public static class CameraInfo {
        /**
         * The facing of the camera is opposite to that of the screen.
         */
        public static final int CAMERA_FACING_BACK = 0;

        /**
         * The facing of the camera is the same as that of the screen.
         */
        public static final int CAMERA_FACING_FRONT = 1;

        /**
         * The direction that the camera faces to. It should be
         * CAMERA_FACING_BACK or CAMERA_FACING_FRONT.
         */
        public int facing;

        /**
         * The orientation of the camera image. The value is the angle that the
         * camera image needs to be rotated clockwise so it shows correctly on
         * the display in its natural orientation. It should be 0, 90, 180, or 270.
         *
         * For example, suppose a device has a naturally tall screen. The
         * back-facing camera sensor is mounted in landscape. You are looking at
         * the screen. If the top side of the camera sensor is aligned with the
         * right edge of the screen in natural orientation, the value should be
         * 90. If the top side of a front-facing camera sensor is aligned with
         * the right of the screen, the value should be 270.
         *
         * @see #setDisplayOrientation(int)
         * @see #setRotation(int)
         * @see #setPreviewSize(int, int)
         * @see #setPictureSize(int, int)
         * @see #setJpegThumbnailSize(int, int)
         */
        public int orientation;
    };

    /**
     * Creates a new Camera object to access a particular hardware camera.
     *
     * <p>You must call {@link #release()} when you are done using the camera,
     * otherwise it will remain locked and be unavailable to other applications.
     *
     * <p>Your application should only have one Camera object active at a time
     * for a particular hardware camera.
     *
     * <p>Callbacks from other methods are delivered to the event loop of the
     * thread which called open().  If this thread has no event loop, then
     * callbacks are delivered to the main application event loop.  If there
     * is no main application event loop, callbacks are not delivered.
     *
     * <p class="caution"><b>Caution:</b> On some devices, this method may
     * take a long time to complete.  It is best to call this method from a
     * worker thread (possibly using {@link android.os.AsyncTask}) to avoid
     * blocking the main application UI thread.
     *
     * @param cameraId the hardware camera to access, between 0 and
     *     {@link #getNumberOfCameras()}-1.
     * @return a new Camera object, connected, locked and ready for use.
     * @throws RuntimeException if connection to the camera service fails (for
     *     example, if the camera is in use by another process).
     */
    public static Camera open(int cameraId) {
        return new Camera(cameraId);
    }

    /**
     * Creates a new Camera object to access the first back-facing camera on the
     * device. If the device does not have a back-facing camera, this returns
     * null.
     * @see #open(int)
     */
    public static Camera open() {
        int numberOfCameras = getNumberOfCameras();
        CameraInfo cameraInfo = new CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
                return new Camera(i);
            }
        }
        return null;
    }

    Camera(int cameraId) {
        mShutterCallback = null;
        mRawImageCallback = null;
        mJpegCallback = null;
        mPreviewCallback = null;
        mPostviewCallback = null;
        mZoomListener = null;

        Looper looper;
        if ((looper = Looper.myLooper()) != null) {
            mEventHandler = new EventHandler(this, looper);
        } else if ((looper = Looper.getMainLooper()) != null) {
            mEventHandler = new EventHandler(this, looper);
        } else {
            mEventHandler = null;
        }

        native_setup(new WeakReference<Camera>(this), cameraId);
    }

    protected void finalize() {
        native_release();
    }

    private native final void native_setup(Object camera_this, int cameraId);
    private native final void native_release();


    /**
     * Disconnects and releases the Camera object resources.
     *
     * <p>You must call this as soon as you're done with the Camera object.</p>
     */
    public final void release() {
        native_release();
    }

    /**
     * Unlocks the camera to allow another process to access it.
     * Normally, the camera is locked to the process with an active Camera
     * object until {@link #release()} is called.  To allow rapid handoff
     * between processes, you can call this method to release the camera
     * temporarily for another process to use; once the other process is done
     * you can call {@link #reconnect()} to reclaim the camera.
     *
     * <p>This must be done before calling
     * {@link android.media.MediaRecorder#setCamera(Camera)}.
     *
     * <p>If you are not recording video, you probably do not need this method.
     *
     * @throws RuntimeException if the camera cannot be unlocked.
     */
    public native final void unlock();

    /**
     * Re-locks the camera to prevent other processes from accessing it.
     * Camera objects are locked by default unless {@link #unlock()} is
     * called.  Normally {@link #reconnect()} is used instead.
     *
     * <p>If you are not recording video, you probably do not need this method.
     *
     * @throws RuntimeException if the camera cannot be re-locked (for
     *     example, if the camera is still in use by another process).
     */
    public native final void lock();

    /**
     * Reconnects to the camera service after another process used it.
     * After {@link #unlock()} is called, another process may use the
     * camera; when the process is done, you must reconnect to the camera,
     * which will re-acquire the lock and allow you to continue using the
     * camera.
     *
     * <p>This must be done after {@link android.media.MediaRecorder} is
     * done recording if {@link android.media.MediaRecorder#setCamera(Camera)}
     * was used.
     *
     * <p>If you are not recording video, you probably do not need this method.
     *
     * @throws IOException if a connection cannot be re-established (for
     *     example, if the camera is still in use by another process).
     */
    public native final void reconnect() throws IOException;

    /**
     * Sets the {@link Surface} to be used for live preview.
     * A surface is necessary for preview, and preview is necessary to take
     * pictures.  The same surface can be re-set without harm.
     *
     * <p>The {@link SurfaceHolder} must already contain a surface when this
     * method is called.  If you are using {@link android.view.SurfaceView},
     * you will need to register a {@link SurfaceHolder.Callback} with
     * {@link SurfaceHolder#addCallback(SurfaceHolder.Callback)} and wait for
     * {@link SurfaceHolder.Callback#surfaceCreated(SurfaceHolder)} before
     * calling setPreviewDisplay() or starting preview.
     *
     * <p>This method must be called before {@link #startPreview()}.  The
     * one exception is that if the preview surface is not set (or set to null)
     * before startPreview() is called, then this method may be called once
     * with a non-null parameter to set the preview surface.  (This allows
     * camera setup and surface creation to happen in parallel, saving time.)
     * The preview surface may not otherwise change while preview is running.
     *
     * @param holder containing the Surface on which to place the preview,
     *     or null to remove the preview surface
     * @throws IOException if the method fails (for example, if the surface
     *     is unavailable or unsuitable).
     */
    public final void setPreviewDisplay(SurfaceHolder holder) throws IOException {
        if (holder != null) {
            setPreviewDisplay(holder.getSurface());
        } else {
            setPreviewDisplay((Surface)null);
        }
    }

    private native final void setPreviewDisplay(Surface surface);

    /**
     * Callback interface used to deliver copies of preview frames as
     * they are displayed.
     *
     * @see #setPreviewCallback(Camera.PreviewCallback)
     * @see #setOneShotPreviewCallback(Camera.PreviewCallback)
     * @see #setPreviewCallbackWithBuffer(Camera.PreviewCallback)
     * @see #startPreview()
     */
    public interface PreviewCallback
    {
        /**
         * Called as preview frames are displayed.  This callback is invoked
         * on the event thread {@link #open(int)} was called from.
         *
         * @param data the contents of the preview frame in the format defined
         *  by {@link android.graphics.ImageFormat}, which can be queried
         *  with {@link android.hardware.Camera.Parameters#getPreviewFormat()}.
         *  If {@link android.hardware.Camera.Parameters#setPreviewFormat(int)}
         *             is never called, the default will be the YCbCr_420_SP
         *             (NV21) format.
         * @param camera the Camera service object.
         */
        void onPreviewFrame(byte[] data, Camera camera);
    };

    /**
     * Starts capturing and drawing preview frames to the screen.
     * Preview will not actually start until a surface is supplied with
     * {@link #setPreviewDisplay(SurfaceHolder)}.
     *
     * <p>If {@link #setPreviewCallback(Camera.PreviewCallback)},
     * {@link #setOneShotPreviewCallback(Camera.PreviewCallback)}, or
     * {@link #setPreviewCallbackWithBuffer(Camera.PreviewCallback)} were
     * called, {@link Camera.PreviewCallback#onPreviewFrame(byte[], Camera)}
     * will be called when preview data becomes available.
     */
    public native final void startPreview();

    /**
     * Stops capturing and drawing preview frames to the surface, and
     * resets the camera for a future call to {@link #startPreview()}.
     */
    public native final void stopPreview();

    /**
     * Return current preview state.
     *
     * FIXME: Unhide before release
     * @hide
     */
    public native final boolean previewEnabled();

    /**
     * Installs a callback to be invoked for every preview frame in addition
     * to displaying them on the screen.  The callback will be repeatedly called
     * for as long as preview is active.  This method can be called at any time,
     * even while preview is live.  Any other preview callbacks are overridden.
     *
     * @param cb a callback object that receives a copy of each preview frame,
     *     or null to stop receiving callbacks.
     */
    public final void setPreviewCallback(PreviewCallback cb) {
        mPreviewCallback = cb;
        mOneShot = false;
        mWithBuffer = false;
        // Always use one-shot mode. We fake camera preview mode by
        // doing one-shot preview continuously.
        setHasPreviewCallback(cb != null, false);
    }

    /**
     * Installs a callback to be invoked for the next preview frame in addition
     * to displaying it on the screen.  After one invocation, the callback is
     * cleared. This method can be called any time, even when preview is live.
     * Any other preview callbacks are overridden.
     *
     * @param cb a callback object that receives a copy of the next preview frame,
     *     or null to stop receiving callbacks.
     */
    public final void setOneShotPreviewCallback(PreviewCallback cb) {
        mPreviewCallback = cb;
        mOneShot = true;
        mWithBuffer = false;
        setHasPreviewCallback(cb != null, false);
    }

    private native final void setHasPreviewCallback(boolean installed, boolean manualBuffer);

    /**
     * Installs a callback to be invoked for every preview frame, using buffers
     * supplied with {@link #addCallbackBuffer(byte[])}, in addition to
     * displaying them on the screen.  The callback will be repeatedly called
     * for as long as preview is active and buffers are available.
     * Any other preview callbacks are overridden.
     *
     * <p>The purpose of this method is to improve preview efficiency and frame
     * rate by allowing preview frame memory reuse.  You must call
     * {@link #addCallbackBuffer(byte[])} at some point -- before or after
     * calling this method -- or no callbacks will received.
     *
     * The buffer queue will be cleared if this method is called with a null
     * callback, {@link #setPreviewCallback(Camera.PreviewCallback)} is called,
     * or {@link #setOneShotPreviewCallback(Camera.PreviewCallback)} is called.
     *
     * @param cb a callback object that receives a copy of the preview frame,
     *     or null to stop receiving callbacks and clear the buffer queue.
     * @see #addCallbackBuffer(byte[])
     */
    public final void setPreviewCallbackWithBuffer(PreviewCallback cb) {
        mPreviewCallback = cb;
        mOneShot = false;
        mWithBuffer = true;
        setHasPreviewCallback(cb != null, true);
    }

    /**
     * Adds a pre-allocated buffer to the preview callback buffer queue.
     * Applications can add one or more buffers to the queue. When a preview
     * frame arrives and there is still at least one available buffer, the
     * buffer will be used and removed from the queue. Then preview callback is
     * invoked with the buffer. If a frame arrives and there is no buffer left,
     * the frame is discarded. Applications should add buffers back when they
     * finish processing the data in them.
     *
     * <p>The size of the buffer is determined by multiplying the preview
     * image width, height, and bytes per pixel.  The width and height can be
     * read from {@link Camera.Parameters#getPreviewSize()}.  Bytes per pixel
     * can be computed from
     * {@link android.graphics.ImageFormat#getBitsPerPixel(int)} / 8,
     * using the image format from {@link Camera.Parameters#getPreviewFormat()}.
     *
     * <p>This method is only necessary when
     * {@link #setPreviewCallbackWithBuffer(PreviewCallback)} is used.  When
     * {@link #setPreviewCallback(PreviewCallback)} or
     * {@link #setOneShotPreviewCallback(PreviewCallback)} are used, buffers
     * are automatically allocated.
     *
     * @param callbackBuffer the buffer to add to the queue.
     *     The size should be width * height * bits_per_pixel / 8.
     * @see #setPreviewCallbackWithBuffer(PreviewCallback)
     */
    public native final void addCallbackBuffer(byte[] callbackBuffer);

    private class EventHandler extends Handler
    {
        private Camera mCamera;

        public EventHandler(Camera c, Looper looper) {
            super(looper);
            mCamera = c;
        }

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
            case CAMERA_MSG_SHUTTER:
                if (mShutterCallback != null) {
                    mShutterCallback.onShutter();
                }
                return;

            case CAMERA_MSG_RAW_IMAGE:
                if (mRawImageCallback != null) {
                    mRawImageCallback.onPictureTaken((byte[])msg.obj, mCamera);
                }
                return;

            case CAMERA_MSG_COMPRESSED_IMAGE:
                if (mJpegCallback != null) {
                    mJpegCallback.onPictureTaken((byte[])msg.obj, mCamera);
                }
                return;

            case CAMERA_MSG_PREVIEW_FRAME:
                if (mPreviewCallback != null) {
                    PreviewCallback cb = mPreviewCallback;
                    if (mOneShot) {
                        // Clear the callback variable before the callback
                        // in case the app calls setPreviewCallback from
                        // the callback function
                        mPreviewCallback = null;
                    } else if (!mWithBuffer) {
                        // We're faking the camera preview mode to prevent
                        // the app from being flooded with preview frames.
                        // Set to oneshot mode again.
                        setHasPreviewCallback(true, false);
                    }
                    cb.onPreviewFrame((byte[])msg.obj, mCamera);
                }
                return;

            case CAMERA_MSG_POSTVIEW_FRAME:
                if (mPostviewCallback != null) {
                    mPostviewCallback.onPictureTaken((byte[])msg.obj, mCamera);
                }
                return;

            case CAMERA_MSG_FOCUS:
                if (mAutoFocusCallback != null) {
                    mAutoFocusCallback.onAutoFocus(msg.arg1 == 0 ? false : true, mCamera);
                }
                return;

            case CAMERA_MSG_ZOOM:
                if (mZoomListener != null) {
                    mZoomListener.onZoomChange(msg.arg1, msg.arg2 != 0, mCamera);
                }
                return;

            case CAMERA_MSG_ERROR :
                Log.e(TAG, "Error " + msg.arg1);
                if (mErrorCallback != null) {
                    mErrorCallback.onError(msg.arg1, mCamera);
                }
                return;

            default:
                Log.e(TAG, "Unknown message type " + msg.what);
                return;
            }
        }
    }

    private static void postEventFromNative(Object camera_ref,
                                            int what, int arg1, int arg2, Object obj)
    {
        Camera c = (Camera)((WeakReference)camera_ref).get();
        if (c == null)
            return;

        if (c.mEventHandler != null) {
            Message m = c.mEventHandler.obtainMessage(what, arg1, arg2, obj);
            c.mEventHandler.sendMessage(m);
        }
    }

    /**
     * Callback interface used to notify on completion of camera auto focus.
     *
     * <p>Devices that do not support auto-focus will receive a "fake"
     * callback to this interface. If your application needs auto-focus and
     * should not be installed on devices <em>without</em> auto-focus, you must
     * declare that your app uses the
     * {@code android.hardware.camera.autofocus} feature, in the
     * <a href="{@docRoot}guide/topics/manifest/uses-feature-element.html">&lt;uses-feature></a>
     * manifest element.</p>
     *
     * @see #autoFocus(AutoFocusCallback)
     */
    public interface AutoFocusCallback
    {
        /**
         * Called when the camera auto focus completes.  If the camera does not
         * support auto-focus and autoFocus is called, onAutoFocus will be
         * called immediately with success.
         *
         * @param success true if focus was successful, false if otherwise
         * @param camera  the Camera service object
         */
        void onAutoFocus(boolean success, Camera camera);
    };

    /**
     * Starts camera auto-focus and registers a callback function to run when
     * the camera is focused.  This method is only valid when preview is active
     * (between {@link #startPreview()} and before {@link #stopPreview()}).
     *
     * <p>Callers should check
     * {@link android.hardware.Camera.Parameters#getFocusMode()} to determine if
     * this method should be called. If the camera does not support auto-focus,
     * it is a no-op and {@link AutoFocusCallback#onAutoFocus(boolean, Camera)}
     * callback will be called immediately.
     *
     * <p>If your application should not be installed
     * on devices without auto-focus, you must declare that your application
     * uses auto-focus with the
     * <a href="{@docRoot}guide/topics/manifest/uses-feature-element.html">&lt;uses-feature></a>
     * manifest element.</p>
     *
     * <p>If the current flash mode is not
     * {@link android.hardware.Camera.Parameters#FLASH_MODE_OFF}, flash may be
     * fired during auto-focus, depending on the driver and camera hardware.<p>
     *
     * @param cb the callback to run
     * @see #cancelAutoFocus()
     */
    public final void autoFocus(AutoFocusCallback cb)
    {
        mAutoFocusCallback = cb;
        native_autoFocus();
    }
    private native final void native_autoFocus();

    /**
     * Cancels any auto-focus function in progress.
     * Whether or not auto-focus is currently in progress,
     * this function will return the focus position to the default.
     * If the camera does not support auto-focus, this is a no-op.
     *
     * @see #autoFocus(Camera.AutoFocusCallback)
     */
    public final void cancelAutoFocus()
    {
        mAutoFocusCallback = null;
        native_cancelAutoFocus();
    }
    private native final void native_cancelAutoFocus();

    /**
     * Callback interface used to signal the moment of actual image capture.
     *
     * @see #takePicture(ShutterCallback, PictureCallback, PictureCallback, PictureCallback)
     */
    public interface ShutterCallback
    {
        /**
         * Called as near as possible to the moment when a photo is captured
         * from the sensor.  This is a good opportunity to play a shutter sound
         * or give other feedback of camera operation.  This may be some time
         * after the photo was triggered, but some time before the actual data
         * is available.
         */
        void onShutter();
    }

    /**
     * Callback interface used to supply image data from a photo capture.
     *
     * @see #takePicture(ShutterCallback, PictureCallback, PictureCallback, PictureCallback)
     */
    public interface PictureCallback {
        /**
         * Called when image data is available after a picture is taken.
         * The format of the data depends on the context of the callback
         * and {@link Camera.Parameters} settings.
         *
         * @param data   a byte array of the picture data
         * @param camera the Camera service object
         */
        void onPictureTaken(byte[] data, Camera camera);
    };

    /**
     * Equivalent to takePicture(shutter, raw, null, jpeg).
     *
     * @see #takePicture(ShutterCallback, PictureCallback, PictureCallback, PictureCallback)
     */
    public final void takePicture(ShutterCallback shutter, PictureCallback raw,
            PictureCallback jpeg) {
        takePicture(shutter, raw, null, jpeg);
    }
    private native final void native_takePicture();

    /**
     * Triggers an asynchronous image capture. The camera service will initiate
     * a series of callbacks to the application as the image capture progresses.
     * The shutter callback occurs after the image is captured. This can be used
     * to trigger a sound to let the user know that image has been captured. The
     * raw callback occurs when the raw image data is available (NOTE: the data
     * may be null if the hardware does not have enough memory to make a copy).
     * The postview callback occurs when a scaled, fully processed postview
     * image is available (NOTE: not all hardware supports this). The jpeg
     * callback occurs when the compressed image is available. If the
     * application does not need a particular callback, a null can be passed
     * instead of a callback method.
     *
     * <p>This method is only valid when preview is active (after
     * {@link #startPreview()}).  Preview will be stopped after the image is
     * taken; callers must call {@link #startPreview()} again if they want to
     * re-start preview or take more pictures.
     *
     * <p>After calling this method, you must not call {@link #startPreview()}
     * or take another picture until the JPEG callback has returned.
     *
     * @param shutter   the callback for image capture moment, or null
     * @param raw       the callback for raw (uncompressed) image data, or null
     * @param postview  callback with postview image data, may be null
     * @param jpeg      the callback for JPEG image data, or null
     */
    public final void takePicture(ShutterCallback shutter, PictureCallback raw,
            PictureCallback postview, PictureCallback jpeg) {
        mShutterCallback = shutter;
        mRawImageCallback = raw;
        mPostviewCallback = postview;
        mJpegCallback = jpeg;
        native_takePicture();
    }

    /**
     * Zooms to the requested value smoothly. The driver will notify {@link
     * OnZoomChangeListener} of the zoom value and whether zoom is stopped at
     * the time. For example, suppose the current zoom is 0 and startSmoothZoom
     * is called with value 3. The
     * {@link Camera.OnZoomChangeListener#onZoomChange(int, boolean, Camera)}
     * method will be called three times with zoom values 1, 2, and 3.
     * Applications can call {@link #stopSmoothZoom} to stop the zoom earlier.
     * Applications should not call startSmoothZoom again or change the zoom
     * value before zoom stops. If the supplied zoom value equals to the current
     * zoom value, no zoom callback will be generated. This method is supported
     * if {@link android.hardware.Camera.Parameters#isSmoothZoomSupported}
     * returns true.
     *
     * @param value zoom value. The valid range is 0 to {@link
     *              android.hardware.Camera.Parameters#getMaxZoom}.
     * @throws IllegalArgumentException if the zoom value is invalid.
     * @throws RuntimeException if the method fails.
     * @see #setZoomChangeListener(OnZoomChangeListener)
     */
    public native final void startSmoothZoom(int value);

    /**
     * Stops the smooth zoom. Applications should wait for the {@link
     * OnZoomChangeListener} to know when the zoom is actually stopped. This
     * method is supported if {@link
     * android.hardware.Camera.Parameters#isSmoothZoomSupported} is true.
     *
     * @throws RuntimeException if the method fails.
     */
    public native final void stopSmoothZoom();

    /**
     * Set the clockwise rotation of preview display in degrees. This affects
     * the preview frames and the picture displayed after snapshot. This method
     * is useful for portrait mode applications. Note that preview display of
     * front-facing cameras is flipped horizontally before the rotation, that
     * is, the image is reflected along the central vertical axis of the camera
     * sensor. So the users can see themselves as looking into a mirror.
     *
     * This does not affect the order of byte array passed in {@link
     * PreviewCallback#onPreviewFrame}, JPEG pictures, or recorded videos. This
     * method is not allowed to be called during preview.
     *
     * If you want to make the camera image show in the same orientation as
     * the display, you can use the following code.<p>
     * <pre>
     * public static void setCameraDisplayOrientation(Activity activity,
     *         int cameraId, android.hardware.Camera camera) {
     *     android.hardware.Camera.CameraInfo info =
     *             new android.hardware.Camera.CameraInfo();
     *     android.hardware.Camera.getCameraInfo(cameraId, info);
     *     int rotation = activity.getWindowManager().getDefaultDisplay()
     *             .getRotation();
     *     int degrees = 0;
     *     switch (rotation) {
     *         case Surface.ROTATION_0: degrees = 0; break;
     *         case Surface.ROTATION_90: degrees = 90; break;
     *         case Surface.ROTATION_180: degrees = 180; break;
     *         case Surface.ROTATION_270: degrees = 270; break;
     *     }
     *
     *     int result;
     *     if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
     *         result = (info.orientation + degrees) % 360;
     *         result = (360 - result) % 360;  // compensate the mirror
     *     } else {  // back-facing
     *         result = (info.orientation - degrees + 360) % 360;
     *     }
     *     camera.setDisplayOrientation(result);
     * }
     * </pre>
     * @param degrees the angle that the picture will be rotated clockwise.
     *                Valid values are 0, 90, 180, and 270. The starting
     *                position is 0 (landscape).
     * @see #setPreviewDisplay(SurfaceHolder)
     */
    public native final void setDisplayOrientation(int degrees);

    /**
     * Callback interface for zoom changes during a smooth zoom operation.
     *
     * @see #setZoomChangeListener(OnZoomChangeListener)
     * @see #startSmoothZoom(int)
     */
    public interface OnZoomChangeListener
    {
        /**
         * Called when the zoom value has changed during a smooth zoom.
         *
         * @param zoomValue the current zoom value. In smooth zoom mode, camera
         *                  calls this for every new zoom value.
         * @param stopped whether smooth zoom is stopped. If the value is true,
         *                this is the last zoom update for the application.
         * @param camera  the Camera service object
         */
        void onZoomChange(int zoomValue, boolean stopped, Camera camera);
    };

    /**
     * Registers a listener to be notified when the zoom value is updated by the
     * camera driver during smooth zoom.
     *
     * @param listener the listener to notify
     * @see #startSmoothZoom(int)
     */
    public final void setZoomChangeListener(OnZoomChangeListener listener)
    {
        mZoomListener = listener;
    }

    // Error codes match the enum in include/ui/Camera.h

    /**
     * Unspecified camera error.
     * @see Camera.ErrorCallback
     */
    public static final int CAMERA_ERROR_UNKNOWN = 1;

    /**
     * Media server died. In this case, the application must release the
     * Camera object and instantiate a new one.
     * @see Camera.ErrorCallback
     */
    public static final int CAMERA_ERROR_SERVER_DIED = 100;

    /**
     * Callback interface for camera error notification.
     *
     * @see #setErrorCallback(ErrorCallback)
     */
    public interface ErrorCallback
    {
        /**
         * Callback for camera errors.
         * @param error   error code:
         * <ul>
         * <li>{@link #CAMERA_ERROR_UNKNOWN}
         * <li>{@link #CAMERA_ERROR_SERVER_DIED}
         * </ul>
         * @param camera  the Camera service object
         */
        void onError(int error, Camera camera);
    };

    /**
     * Registers a callback to be invoked when an error occurs.
     * @param cb The callback to run
     */
    public final void setErrorCallback(ErrorCallback cb)
    {
        mErrorCallback = cb;
    }

    private native final void native_setParameters(String params);
    private native final String native_getParameters();

    /**
     * Changes the settings for this Camera service.
     *
     * @param params the Parameters to use for this Camera service
     * @throws RuntimeException if any parameter is invalid or not supported.
     * @see #getParameters()
     */
    public void setParameters(Parameters params) {
        try {
            native_setParameters(params.flatten());
        } catch (RuntimeException ex) {
            Log.e(TAG, "Failed to set all parameters");
        }
    }

    /**
     * Returns the current settings for this Camera service.
     * If modifications are made to the returned Parameters, they must be passed
     * to {@link #setParameters(Camera.Parameters)} to take effect.
     *
     * @see #setParameters(Camera.Parameters)
     */
    public Parameters getParameters() {
        Parameters p = new Parameters();
        String s = native_getParameters();
        p.unflatten(s);
        return p;
    }

    /**
     * Image size (width and height dimensions).
     */
    public class Size {
        /**
         * Sets the dimensions for pictures.
         *
         * @param w the photo width (pixels)
         * @param h the photo height (pixels)
         */
        public Size(int w, int h) {
            width = w;
            height = h;
        }
        /**
         * Compares {@code obj} to this size.
         *
         * @param obj the object to compare this size with.
         * @return {@code true} if the width and height of {@code obj} is the
         *         same as those of this size. {@code false} otherwise.
         */
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Size)) {
                return false;
            }
            Size s = (Size) obj;
            return width == s.width && height == s.height;
        }
        @Override
        public int hashCode() {
            return width * 32713 + height;
        }
        /** width of the picture */
        public int width;
        /** height of the picture */
        public int height;
    };

    /**
     * Handles the Touch Co-ordinate.
     */
    public class Coordinate {
        /**
         * Sets the x,y co-ordinates for a touch event
         *
         * @param x the x co-ordinate (pixels)
         * @param y the y co-ordinate (pixels)
         */
        public Coordinate(int x, int y) {
            xCoordinate = x;
            yCoordinate = y;
        }
        /**
         * Compares {@code obj} to this co-ordinate.
         *
         * @param obj the object to compare this co-ordinate with.
         * @return {@code true} if the xCoordinate and yCoordinate of {@code obj} is the
         *         same as those of this coordinate. {@code false} otherwise.
         */
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Coordinate)) {
                return false;
            }
            Coordinate c = (Coordinate) obj;
            return xCoordinate == c.xCoordinate && yCoordinate == c.yCoordinate;
        }

        /** x co-ordinate for the touch event*/
        public int xCoordinate;

        /** y co-ordinate for the touch event */
        public int yCoordinate;
    };

    /**
     * Camera service settings.
     *
     * <p>To make camera parameters take effect, applications have to call
     * {@link Camera#setParameters(Camera.Parameters)}. For example, after
     * {@link Camera.Parameters#setWhiteBalance} is called, white balance is not
     * actually changed until {@link Camera#setParameters(Camera.Parameters)}
     * is called with the changed parameters object.
     *
     * <p>Different devices may have different camera capabilities, such as
     * picture size or flash modes. The application should query the camera
     * capabilities before setting parameters. For example, the application
     * should call {@link Camera.Parameters#getSupportedColorEffects()} before
     * calling {@link Camera.Parameters#setColorEffect(String)}. If the
     * camera does not support color effects,
     * {@link Camera.Parameters#getSupportedColorEffects()} will return null.
     */
    public class Parameters {
        // Parameter keys to communicate with the camera driver.
        private static final String KEY_PREVIEW_SIZE = "preview-size";
        private static final String KEY_PREVIEW_FORMAT = "preview-format";
        private static final String KEY_PREVIEW_FRAME_RATE = "preview-frame-rate";
        private static final String KEY_PREVIEW_FPS_RANGE = "preview-fps-range";
        private static final String KEY_PREVIEW_FRAME_RATE_MODE = "preview-frame-rate-mode";
        private static final String KEY_PREVIEW_FRAME_RATE_AUTO_MODE = "frame-rate-auto";
        private static final String KEY_PREVIEW_FRAME_RATE_FIXED_MODE = "frame-rate-fixed";
        private static final String KEY_PICTURE_SIZE = "picture-size";
        private static final String KEY_PICTURE_FORMAT = "picture-format";
        private static final String KEY_JPEG_THUMBNAIL_SIZE = "jpeg-thumbnail-size";
        private static final String KEY_JPEG_THUMBNAIL_WIDTH = "jpeg-thumbnail-width";
        private static final String KEY_JPEG_THUMBNAIL_HEIGHT = "jpeg-thumbnail-height";
        private static final String KEY_JPEG_THUMBNAIL_QUALITY = "jpeg-thumbnail-quality";
        private static final String KEY_JPEG_QUALITY = "jpeg-quality";
        private static final String KEY_ROTATION = "rotation";
        private static final String KEY_GPS_LATITUDE = "gps-latitude";
        private static final String KEY_GPS_LONGITUDE = "gps-longitude";
        private static final String KEY_GPS_ALTITUDE = "gps-altitude";
        private static final String KEY_GPS_TIMESTAMP = "gps-timestamp";
        private static final String KEY_GPS_PROCESSING_METHOD = "gps-processing-method";
        private static final String KEY_WHITE_BALANCE = "whitebalance";
        private static final String KEY_EFFECT = "effect";
        private static final String KEY_TOUCH_AF_AEC = "touch-af-aec";
        private static final String KEY_TOUCH_INDEX_AEC = "touch-index-aec";
        private static final String KEY_TOUCH_INDEX_AF = "touch-index-af";
        private static final String KEY_ANTIBANDING = "antibanding";
        private static final String KEY_SCENE_MODE = "scene-mode";
        private static final String KEY_FLASH_MODE = "flash-mode";
        private static final String KEY_FOCUS_MODE = "focus-mode";
        private static final String KEY_ISO_MODE = "iso";
        private static final String KEY_LENSSHADE = "lensshade";
        private static final String KEY_FOCAL_LENGTH = "focal-length";
        private static final String KEY_HORIZONTAL_VIEW_ANGLE = "horizontal-view-angle";
        private static final String KEY_VERTICAL_VIEW_ANGLE = "vertical-view-angle";
        private static final String KEY_EXPOSURE_COMPENSATION = "exposure-compensation";
        private static final String KEY_MAX_EXPOSURE_COMPENSATION = "max-exposure-compensation";
        private static final String KEY_MIN_EXPOSURE_COMPENSATION = "min-exposure-compensation";
        private static final String KEY_EXPOSURE_COMPENSATION_STEP = "exposure-compensation-step";
        private static final String KEY_AUTO_EXPOSURE = "meter-mode";
        private static final String KEY_ZOOM = "zoom";
        private static final String KEY_MAX_ZOOM = "max-zoom";
        private static final String KEY_ZOOM_RATIOS = "zoom-ratios";
        private static final String KEY_ZOOM_SUPPORTED = "zoom-supported";
        private static final String KEY_SMOOTH_ZOOM_SUPPORTED = "smooth-zoom-supported";
        private static final String KEY_FOCUS_DISTANCES = "focus-distances";
        private static final String KEY_CAF = "continuous-af";
        private static final String KEY_SHARPNESS = "sharpness";
        private static final String KEY_MAX_SHARPNESS = "sharpness-max";
        private static final String KEY_DEFAULT_SHARPNESS = "sharpness-def";
        private static final String KEY_CONTRAST = "contrast";
        private static final String KEY_MAX_CONTRAST = "contrast-max";
        private static final String KEY_DEFAULT_CONTRAST = "contrast-def";
        private static final String KEY_SATURATION = "saturation";
        private static final String KEY_MAX_SATURATION = "saturation-max";
        private static final String KEY_DEFAULT_SATURATION = "saturation-def";
        private static final String KEY_BRIGHTNESS = "brightness";
        private static final String KEY_MAX_BRIGHTNESS = "brightness-max";
        private static final String KEY_DEFAULT_BRIGHTNESS = "brightness-def";
        private static final String KEY_SMART_CONTRAST = "smart-contrast";
        
        // Parameter key suffix for supported values.
        private static final String SUPPORTED_VALUES_SUFFIX = "-values";

        private static final String TRUE = "true";

        // Values for white balance settings.
        public static final String WHITE_BALANCE_AUTO = "auto";
        public static final String WHITE_BALANCE_INCANDESCENT = "incandescent";
        public static final String WHITE_BALANCE_FLUORESCENT = "fluorescent";
        public static final String WHITE_BALANCE_WARM_FLUORESCENT = "warm-fluorescent";
        public static final String WHITE_BALANCE_DAYLIGHT = "daylight";
        public static final String WHITE_BALANCE_CLOUDY_DAYLIGHT = "cloudy-daylight";
        public static final String WHITE_BALANCE_TWILIGHT = "twilight";
        public static final String WHITE_BALANCE_SHADE = "shade";

        // Values for color effect settings.
        public static final String EFFECT_NONE = "none";
        public static final String EFFECT_MONO = "mono";
        public static final String EFFECT_NEGATIVE = "negative";
        public static final String EFFECT_SOLARIZE = "solarize";
        public static final String EFFECT_SEPIA = "sepia";
        public static final String EFFECT_POSTERIZE = "posterize";
        public static final String EFFECT_WHITEBOARD = "whiteboard";
        public static final String EFFECT_BLACKBOARD = "blackboard";
        public static final String EFFECT_AQUA = "aqua";

        // Values for touch af/aec settings.
        public static final String TOUCH_AF_AEC_OFF = "touch-off";
        public static final String TOUCH_AF_AEC_ON = "touch-on";

        // Values for auto exposure settings.
        public static final String AUTO_EXPOSURE_FRAME_AVG = "meter-average";
        public static final String AUTO_EXPOSURE_CENTER_WEIGHTED = "meter-center";
        public static final String AUTO_EXPOSURE_SPOT_METERING = "meter-spot";

        // Values for antibanding settings.
        public static final String ANTIBANDING_AUTO = "auto";
        public static final String ANTIBANDING_50HZ = "50hz";
        public static final String ANTIBANDING_60HZ = "60hz";
        public static final String ANTIBANDING_OFF = "off";

        //Values for ISO settings

        public static final String ISO_AUTO = "auto";
        public static final String ISO_HJR = "deblur";
        public static final String ISO_100 = "100";
        public static final String ISO_200 = "200";
        public static final String ISO_400 = "400";
        public static final String ISO_800 = "800";
        public static final String ISO_1250 = "1250";
        
        //Values for Lens Shading

        public static final String LENSSHADE_ENABLE = "enable";
        public static final String LENSSHADE_DISABLE= "disable";



        // Values for flash mode settings.
        /**
         * Flash will not be fired.
         */
        public static final String FLASH_MODE_OFF = "off";

        /**
         * Flash will be fired automatically when required. The flash may be fired
         * during preview, auto-focus, or snapshot depending on the driver.
         */
        public static final String FLASH_MODE_AUTO = "auto";

        /**
         * Flash will always be fired during snapshot. The flash may also be
         * fired during preview or auto-focus depending on the driver.
         */
        public static final String FLASH_MODE_ON = "on";

        /**
         * Flash will be fired in red-eye reduction mode.
         */
        public static final String FLASH_MODE_RED_EYE = "red-eye";

        /**
         * Constant emission of light during preview, auto-focus and snapshot.
         * This can also be used for video recording.
         */
        public static final String FLASH_MODE_TORCH = "torch";

        /**
         * Scene mode is off.
         */
        public static final String SCENE_MODE_AUTO = "auto";

        /**
         * Take photos of fast moving objects. Same as {@link
         * #SCENE_MODE_SPORTS}.
         */
        public static final String SCENE_MODE_ACTION = "action";

        /**
         * Take people pictures.
         */
        public static final String SCENE_MODE_PORTRAIT = "portrait";

        /**
         * Take pictures on distant objects.
         */
        public static final String SCENE_MODE_LANDSCAPE = "landscape";

        /**
         * Take photos at night.
         */
        public static final String SCENE_MODE_NIGHT = "night";

        /**
         * Take people pictures at night.
         */
        public static final String SCENE_MODE_NIGHT_PORTRAIT = "night-portrait";

        /**
         * Take photos in a theater. Flash light is off.
         */
        public static final String SCENE_MODE_THEATRE = "theatre";

        /**
         * Take pictures on the beach.
         */
        public static final String SCENE_MODE_BEACH = "beach";

        /**
         * Take pictures on the snow.
         */
        public static final String SCENE_MODE_SNOW = "snow";

        /**
         * Take sunset photos.
         */
        public static final String SCENE_MODE_SUNSET = "sunset";

        /**
         * Avoid blurry pictures (for example, due to hand shake).
         */
        public static final String SCENE_MODE_STEADYPHOTO = "steadyphoto";

        /**
         * For shooting firework displays.
         */
        public static final String SCENE_MODE_FIREWORKS = "fireworks";

        /**
         * Take photos of fast moving objects. Same as {@link
         * #SCENE_MODE_ACTION}.
         */
        public static final String SCENE_MODE_SPORTS = "sports";

        /**
         * Take indoor low-light shot.
         */
        public static final String SCENE_MODE_PARTY = "party";

        /**
         * Capture the naturally warm color of scenes lit by candles.
         */
        public static final String SCENE_MODE_CANDLELIGHT = "candlelight";

        /**
         * Applications are looking for a barcode. Camera driver will be
         * optimized for barcode reading.
         */
        public static final String SCENE_MODE_BARCODE = "barcode";

        /**
         * Auto-focus mode. Applications should call {@link
         * #autoFocus(AutoFocusCallback)} to start the focus in this mode.
         */
        public static final String FOCUS_MODE_AUTO = "auto";

        /**
         * Focus is set at infinity. Applications should not call
         * {@link #autoFocus(AutoFocusCallback)} in this mode.
         */
        public static final String FOCUS_MODE_INFINITY = "infinity";

        /**
         * Macro (close-up) focus mode. Applications should call
         * {@link #autoFocus(AutoFocusCallback)} to start the focus in this
         * mode.
         */
        public static final String FOCUS_MODE_MACRO = "macro";

        /**
         * Focus is fixed. The camera is always in this mode if the focus is not
         * adjustable. If the camera has auto-focus, this mode can fix the
         * focus, which is usually at hyperfocal distance. Applications should
         * not call {@link #autoFocus(AutoFocusCallback)} in this mode.
         */
        public static final String FOCUS_MODE_FIXED = "fixed";

        /**
         * Extended depth of field (EDOF). Focusing is done digitally and
         * continuously. Applications should not call {@link
         * #autoFocus(AutoFocusCallback)} in this mode.
         */
        public static final String FOCUS_MODE_EDOF = "edof";

        /**
         * Continuous auto focus mode intended for video recording. The camera
         * continuously tries to focus. This is ideal for shooting video.
         * Applications still can call {@link
         * #takePicture(Camera.ShutterCallback, Camera.PictureCallback,
         * Camera.PictureCallback)} in this mode but the subject may not be in
         * focus. Auto focus starts when the parameter is set. Applications
         * should not call {@link #autoFocus(AutoFocusCallback)} in this mode.
         * To stop continuous focus, applications should change the focus mode
         * to other modes.
         */
        public static final String FOCUS_MODE_CONTINUOUS_VIDEO = "continuous-video";

        // Indices for focus distance array.
        /**
         * The array index of near focus distance for use with
         * {@link #getFocusDistances(float[])}.
         */
        public static final int FOCUS_DISTANCE_NEAR_INDEX = 0;

        /**
         * The array index of optimal focus distance for use with
         * {@link #getFocusDistances(float[])}.
         */
        public static final int FOCUS_DISTANCE_OPTIMAL_INDEX = 1;

        /**
         * The array index of far focus distance for use with
         * {@link #getFocusDistances(float[])}.
         */
        public static final int FOCUS_DISTANCE_FAR_INDEX = 2;

        /**
         * The array index of minimum preview fps for use with {@link
         * #getPreviewFpsRange(int[])} or {@link
         * #getSupportedPreviewFpsRange()}.
         */
        public static final int PREVIEW_FPS_MIN_INDEX = 0;

        /**
         * The array index of maximum preview fps for use with {@link
         * #getPreviewFpsRange(int[])} or {@link
         * #getSupportedPreviewFpsRange()}.
         */
        public static final int PREVIEW_FPS_MAX_INDEX = 1;

        // Formats for setPreviewFormat and setPictureFormat.
        private static final String PIXEL_FORMAT_YUV422SP = "yuv422sp";
        private static final String PIXEL_FORMAT_YUV420SP = "yuv420sp";
        private static final String PIXEL_FORMAT_YUV422I = "yuv422i-yuyv";
        private static final String PIXEL_FORMAT_RGB565 = "rgb565";
        private static final String PIXEL_FORMAT_JPEG = "jpeg";

        //Values for Continuous AF

        public static final String CAF_OFF = "caf-off";
        public static final String CAF_ON = "caf-on";

        private HashMap<String, String> mMap;

        private Parameters() {
            mMap = new HashMap<String, String>();
        }

        /**
         * Writes the current Parameters to the log.
         * @hide
         * @deprecated
         */
        public void dump() {
            Log.e(TAG, "dump: size=" + mMap.size());
            for (String k : mMap.keySet()) {
                Log.e(TAG, "dump: " + k + "=" + mMap.get(k));
            }
        }

        /**
         * Creates a single string with all the parameters set in
         * this Parameters object.
         * <p>The {@link #unflatten(String)} method does the reverse.</p>
         *
         * @return a String with all values from this Parameters object, in
         *         semi-colon delimited key-value pairs
         */
        public String flatten() {
            StringBuilder flattened = new StringBuilder();
            for (String k : mMap.keySet()) {
                flattened.append(k);
                flattened.append("=");
                flattened.append(mMap.get(k));
                flattened.append(";");
            }
            // chop off the extra semicolon at the end
            flattened.deleteCharAt(flattened.length()-1);
            return flattened.toString();
        }

        /**
         * Takes a flattened string of parameters and adds each one to
         * this Parameters object.
         * <p>The {@link #flatten()} method does the reverse.</p>
         *
         * @param flattened a String of parameters (key-value paired) that
         *                  are semi-colon delimited
         */
        public void unflatten(String flattened) {
            mMap.clear();

            StringTokenizer tokenizer = new StringTokenizer(flattened, ";");
            while (tokenizer.hasMoreElements()) {
                String kv = tokenizer.nextToken();
                int pos = kv.indexOf('=');
                if (pos == -1) {
                    continue;
                }
                String k = kv.substring(0, pos);
                String v = kv.substring(pos + 1);
                mMap.put(k, v);
            }
        }

        public void remove(String key) {
            mMap.remove(key);
        }

        /**
         * Sets a String parameter.
         *
         * @param key   the key name for the parameter
         * @param value the String value of the parameter
         */
        public void set(String key, String value) {
            if (key.indexOf('=') != -1 || key.indexOf(';') != -1) {
                Log.e(TAG, "Key \"" + key + "\" contains invalid character (= or ;)");
                return;
            }
            if (value.indexOf('=') != -1 || value.indexOf(';') != -1) {
                Log.e(TAG, "Value \"" + value + "\" contains invalid character (= or ;)");
                return;
            }

            mMap.put(key, value);
        }

        /**
         * Sets an integer parameter.
         *
         * @param key   the key name for the parameter
         * @param value the int value of the parameter
         */
        public void set(String key, int value) {
            mMap.put(key, Integer.toString(value));
        }

        /**
         * Returns the value of a String parameter.
         *
         * @param key the key name for the parameter
         * @return the String value of the parameter
         */
        public String get(String key) {
            return mMap.get(key);
        }

        /**
         * Returns the value of an integer parameter.
         *
         * @param key the key name for the parameter
         * @return the int value of the parameter
         */
        public int getInt(String key) {
            String value = mMap.get(key);
            return value == null ? 0 : Integer.parseInt(value);
        }

        /**
         * Sets the dimensions for preview pictures.
         *
         * The sides of width and height are based on camera orientation. That
         * is, the preview size is the size before it is rotated by display
         * orientation. So applications need to consider the display orientation
         * while setting preview size. For example, suppose the camera supports
         * both 480x320 and 320x480 preview sizes. The application wants a 3:2
         * preview ratio. If the display orientation is set to 0 or 180, preview
         * size should be set to 480x320. If the display orientation is set to
         * 90 or 270, preview size should be set to 320x480. The display
         * orientation should also be considered while setting picture size and
         * thumbnail size.
         *
         * @param width  the width of the pictures, in pixels
         * @param height the height of the pictures, in pixels
         * @see #setDisplayOrientation(int)
         * @see #getCameraInfo(int, CameraInfo)
         * @see #setPictureSize(int, int)
         * @see #setJpegThumbnailSize(int, int)
         */
        public void setPreviewSize(int width, int height) {
            String v = Integer.toString(width) + "x" + Integer.toString(height);
            set(KEY_PREVIEW_SIZE, v);
        }

        /**
         * Returns the dimensions setting for preview pictures.
         *
         * @return a Size object with the height and width setting
         *          for the preview picture
         */
        public Size getPreviewSize() {
            String pair = get(KEY_PREVIEW_SIZE);
            return strToSize(pair);
        }

        /**
         * Gets the supported preview sizes.
         *
         * @return a list of Size object. This method will always return a list
         *         with at least one element.
         */
        public List<Size> getSupportedPreviewSizes() {
            String str = get(KEY_PREVIEW_SIZE + SUPPORTED_VALUES_SUFFIX);
            return splitSize(str);
        }

        /**
         * Sets the dimensions for EXIF thumbnail in Jpeg picture. If
         * applications set both width and height to 0, EXIF will not contain
         * thumbnail.
         *
         * Applications need to consider the display orientation. See {@link
         * #setPreviewSize(int,int)} for reference.
         *
         * @param width  the width of the thumbnail, in pixels
         * @param height the height of the thumbnail, in pixels
         * @see #setPreviewSize(int,int)
         */
        public void setJpegThumbnailSize(int width, int height) {
            set(KEY_JPEG_THUMBNAIL_WIDTH, width);
            set(KEY_JPEG_THUMBNAIL_HEIGHT, height);
        }

        /**
         * Returns the dimensions for EXIF thumbnail in Jpeg picture.
         *
         * @return a Size object with the height and width setting for the EXIF
         *         thumbnails
         */
        public Size getJpegThumbnailSize() {
            return new Size(getInt(KEY_JPEG_THUMBNAIL_WIDTH),
                            getInt(KEY_JPEG_THUMBNAIL_HEIGHT));
        }

        /**
         * Gets the supported jpeg thumbnail sizes.
         *
         * @return a list of Size object. This method will always return a list
         *         with at least two elements. Size 0,0 (no thumbnail) is always
         *         supported.
         */
        public List<Size> getSupportedJpegThumbnailSizes() {
            String str = get(KEY_JPEG_THUMBNAIL_SIZE + SUPPORTED_VALUES_SUFFIX);
            return splitSize(str);
        }

        /**
         * Sets the quality of the EXIF thumbnail in Jpeg picture.
         *
         * @param quality the JPEG quality of the EXIF thumbnail. The range is 1
         *                to 100, with 100 being the best.
         */
        public void setJpegThumbnailQuality(int quality) {
            set(KEY_JPEG_THUMBNAIL_QUALITY, quality);
        }

        /**
         * Returns the quality setting for the EXIF thumbnail in Jpeg picture.
         *
         * @return the JPEG quality setting of the EXIF thumbnail.
         */
        public int getJpegThumbnailQuality() {
            return getInt(KEY_JPEG_THUMBNAIL_QUALITY);
        }

        /**
         * Sets Jpeg quality of captured picture.
         *
         * @param quality the JPEG quality of captured picture. The range is 1
         *                to 100, with 100 being the best.
         */
        public void setJpegQuality(int quality) {
            set(KEY_JPEG_QUALITY, quality);
        }

        /**
         * Returns the quality setting for the JPEG picture.
         *
         * @return the JPEG picture quality setting.
         */
        public int getJpegQuality() {
            return getInt(KEY_JPEG_QUALITY);
        }

        /**
         * Sets the rate at which preview frames are received. This is the
         * target frame rate. The actual frame rate depends on the driver.
         *
         * @param fps the frame rate (frames per second)
         * @deprecated replaced by {@link #setPreviewFpsRange(int,int)}
         */
        @Deprecated
        public void setPreviewFrameRate(int fps) {
            set(KEY_PREVIEW_FRAME_RATE, fps);
        }

        /**
         * Returns the setting for the rate at which preview frames are
         * received. This is the target frame rate. The actual frame rate
         * depends on the driver.
         *
         * @return the frame rate setting (frames per second)
         * @deprecated replaced by {@link #getPreviewFpsRange(int[])}
         */
        @Deprecated
        public int getPreviewFrameRate() {
            return getInt(KEY_PREVIEW_FRAME_RATE);
        }

        /**
         * Gets the supported preview frame rates.
         *
         * @return a list of supported preview frame rates. null if preview
         *         frame rate setting is not supported.
         * @deprecated replaced by {@link #getSupportedPreviewFpsRange()}
         */
        @Deprecated
        public List<Integer> getSupportedPreviewFrameRates() {
            String str = get(KEY_PREVIEW_FRAME_RATE + SUPPORTED_VALUES_SUFFIX);
            return splitInt(str);
        }

        /**
         * Sets the maximum and maximum preview fps. This controls the rate of
         * preview frames received in {@link PreviewCallback}. The minimum and
         * maximum preview fps must be one of the elements from {@link
         * #getSupportedPreviewFpsRange}.
         *
         * @param min the minimum preview fps (scaled by 1000).
         * @param max the maximum preview fps (scaled by 1000).
         * @throws RuntimeException if fps range is invalid.
         * @see #setPreviewCallbackWithBuffer(Camera.PreviewCallback)
         * @see #getSupportedPreviewFpsRange()
         */
        public void setPreviewFpsRange(int min, int max) {
            set(KEY_PREVIEW_FPS_RANGE, "" + min + "," + max);
        }

        /**
         * Returns the current minimum and maximum preview fps. The values are
         * one of the elements returned by {@link #getSupportedPreviewFpsRange}.
         *
         * @return range the minimum and maximum preview fps (scaled by 1000).
         * @see #PREVIEW_FPS_MIN_INDEX
         * @see #PREVIEW_FPS_MAX_INDEX
         * @see #getSupportedPreviewFpsRange()
         */
        public void getPreviewFpsRange(int[] range) {
            if (range == null || range.length != 2) {
                throw new IllegalArgumentException(
                        "range must be an array with two elements.");
            }
            splitInt(get(KEY_PREVIEW_FPS_RANGE), range);
        }

        /**
         * Gets the supported preview fps (frame-per-second) ranges. Each range
         * contains a minimum fps and maximum fps. If minimum fps equals to
         * maximum fps, the camera outputs frames in fixed frame rate. If not,
         * the camera outputs frames in auto frame rate. The actual frame rate
         * fluctuates between the minimum and the maximum. The values are
         * multiplied by 1000 and represented in integers. For example, if frame
         * rate is 26.623 frames per second, the value is 26623.
         *
         * @return a list of supported preview fps ranges. This method returns a
         *         list with at least one element. Every element is an int array
         *         of two values - minimum fps and maximum fps. The list is
         *         sorted from small to large (first by maximum fps and then
         *         minimum fps).
         * @see #PREVIEW_FPS_MIN_INDEX
         * @see #PREVIEW_FPS_MAX_INDEX
         */
        public List<int[]> getSupportedPreviewFpsRange() {
            String str = get(KEY_PREVIEW_FPS_RANGE + SUPPORTED_VALUES_SUFFIX);
            return splitRange(str);
        }

        /**
         * Sets the image format for preview pictures.
         * <p>If this is never called, the default format will be
         * {@link android.graphics.ImageFormat#NV21}, which
         * uses the NV21 encoding format.</p>
         *
         * @param pixel_format the desired preview picture format, defined
         *   by one of the {@link android.graphics.ImageFormat} constants.
         *   (E.g., <var>ImageFormat.NV21</var> (default),
         *                      <var>ImageFormat.RGB_565</var>, or
         *                      <var>ImageFormat.JPEG</var>)
         * @see android.graphics.ImageFormat
         */
        public void setPreviewFormat(int pixel_format) {
            String s = cameraFormatForPixelFormat(pixel_format);
            if (s == null) {
                throw new IllegalArgumentException(
                        "Invalid pixel_format=" + pixel_format);
            }

            set(KEY_PREVIEW_FORMAT, s);
        }

        /**
         * Returns the image format for preview frames got from
         * {@link PreviewCallback}.
         *
         * @return the preview format.
         * @see android.graphics.ImageFormat
         */
        public int getPreviewFormat() {
            return pixelFormatForCameraFormat(get(KEY_PREVIEW_FORMAT));
        }

        /**
         * Gets the supported preview formats.
         *
         * @return a list of supported preview formats. This method will always
         *         return a list with at least one element.
         * @see android.graphics.ImageFormat
         */
        public List<Integer> getSupportedPreviewFormats() {
            String str = get(KEY_PREVIEW_FORMAT + SUPPORTED_VALUES_SUFFIX);
            ArrayList<Integer> formats = new ArrayList<Integer>();
            for (String s : split(str)) {
                int f = pixelFormatForCameraFormat(s);
                if (f == ImageFormat.UNKNOWN) continue;
                formats.add(f);
            }
            return formats;
        }

        /**
         * Sets the dimensions for pictures.
         *
         * Applications need to consider the display orientation. See {@link
         * #setPreviewSize(int,int)} for reference.
         *
         * @param width  the width for pictures, in pixels
         * @param height the height for pictures, in pixels
         * @see #setPreviewSize(int,int)
         *
         */
        public void setPictureSize(int width, int height) {
            String v = Integer.toString(width) + "x" + Integer.toString(height);
            set(KEY_PICTURE_SIZE, v);
        }

        /**
         * Returns the dimension setting for pictures.
         *
         * @return a Size object with the height and width setting
         *          for pictures
         */
        public Size getPictureSize() {
            String pair = get(KEY_PICTURE_SIZE);
            return strToSize(pair);
        }

        /**
         * Gets the supported picture sizes.
         *
         * @return a list of supported picture sizes. This method will always
         *         return a list with at least one element.
         */
        public List<Size> getSupportedPictureSizes() {
            String str = get(KEY_PICTURE_SIZE + SUPPORTED_VALUES_SUFFIX);
            return splitSize(str);
        }

        /**
         * Sets the image format for pictures.
         *
         * @param pixel_format the desired picture format
         *                     (<var>ImageFormat.NV21</var>,
         *                      <var>ImageFormat.RGB_565</var>, or
         *                      <var>ImageFormat.JPEG</var>)
         * @see android.graphics.ImageFormat
         */
        public void setPictureFormat(int pixel_format) {
            String s = cameraFormatForPixelFormat(pixel_format);
            if (s == null) {
                throw new IllegalArgumentException(
                        "Invalid pixel_format=" + pixel_format);
            }

            set(KEY_PICTURE_FORMAT, s);
        }

        /**
         * Returns the image format for pictures.
         *
         * @return the picture format
         * @see android.graphics.ImageFormat
         */
        public int getPictureFormat() {
            return pixelFormatForCameraFormat(get(KEY_PICTURE_FORMAT));
        }

        /**
         * Gets the supported picture formats.
         *
         * @return supported picture formats. This method will always return a
         *         list with at least one element.
         * @see android.graphics.ImageFormat
         */
        public List<Integer> getSupportedPictureFormats() {
            String str = get(KEY_PICTURE_FORMAT + SUPPORTED_VALUES_SUFFIX);
            ArrayList<Integer> formats = new ArrayList<Integer>();
            for (String s : split(str)) {
                int f = pixelFormatForCameraFormat(s);
                if (f == ImageFormat.UNKNOWN) continue;
                formats.add(f);
            }
            return formats;
        }

        private String cameraFormatForPixelFormat(int pixel_format) {
            switch(pixel_format) {
            case ImageFormat.NV16:      return PIXEL_FORMAT_YUV422SP;
            case ImageFormat.NV21:      return PIXEL_FORMAT_YUV420SP;
            case ImageFormat.YUY2:      return PIXEL_FORMAT_YUV422I;
            case ImageFormat.RGB_565:   return PIXEL_FORMAT_RGB565;
            case ImageFormat.JPEG:      return PIXEL_FORMAT_JPEG;
            default:                    return null;
            }
        }

        private int pixelFormatForCameraFormat(String format) {
            if (format == null)
                return ImageFormat.UNKNOWN;

            if (format.equals(PIXEL_FORMAT_YUV422SP))
                return ImageFormat.NV16;

            if (format.equals(PIXEL_FORMAT_YUV420SP))
                return ImageFormat.NV21;

            if (format.equals(PIXEL_FORMAT_YUV422I))
                return ImageFormat.YUY2;

            if (format.equals(PIXEL_FORMAT_RGB565))
                return ImageFormat.RGB_565;

            if (format.equals(PIXEL_FORMAT_JPEG))
                return ImageFormat.JPEG;

            return ImageFormat.UNKNOWN;
        }

        /**
         * Sets the rotation angle in degrees relative to the orientation of
         * the camera. This affects the pictures returned from JPEG {@link
         * PictureCallback}. The camera driver may set orientation in the
         * EXIF header without rotating the picture. Or the driver may rotate
         * the picture and the EXIF thumbnail. If the Jpeg picture is rotated,
         * the orientation in the EXIF header will be missing or 1 (row #0 is
         * top and column #0 is left side).
         *
         * If applications want to rotate the picture to match the orientation
         * of what users see, apps should use {@link
         * android.view.OrientationEventListener} and {@link CameraInfo}.
         * The value from OrientationEventListener is relative to the natural
         * orientation of the device. CameraInfo.orientation is the angle
         * between camera orientation and natural device orientation. The sum or
         * of the two is the rotation angle for back-facing camera. The
         * difference of the two is the rotation angle for front-facing camera.
         * Note that the JPEG pictures of front-facing cameras are not mirrored
         * as in preview display.
         *
         * For example, suppose the natural orientation of the device is
         * portrait. The device is rotated 270 degrees clockwise, so the device
         * orientation is 270. Suppose a back-facing camera sensor is mounted in
         * landscape and the top side of the camera sensor is aligned with the
         * right edge of the display in natural orientation. So the camera
         * orientation is 90. The rotation should be set to 0 (270 + 90).
         *
         * The reference code is as follows.
         *
         * public void public void onOrientationChanged(int orientation) {
         *     if (orientation == ORIENTATION_UNKNOWN) return;
         *     android.hardware.Camera.CameraInfo info =
         *            new android.hardware.Camera.CameraInfo();
         *     android.hardware.Camera.getCameraInfo(cameraId, info);
         *     orientation = (orientation + 45) / 90 * 90;
         *     int rotation = 0;
         *     if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
         *         rotation = (info.orientation - orientation + 360) % 360;
         *     } else {  // back-facing camera
         *         rotation = (info.orientation + orientation) % 360;
         *     }
         *     mParameters.setRotation(rotation);
         * }
         *
         * @param rotation The rotation angle in degrees relative to the
         *                 orientation of the camera. Rotation can only be 0,
         *                 90, 180 or 270.
         * @throws IllegalArgumentException if rotation value is invalid.
         * @see android.view.OrientationEventListener
         * @see #getCameraInfo(int, CameraInfo)
         */
        public void setRotation(int rotation) {
            if (rotation == 0 || rotation == 90 || rotation == 180
                    || rotation == 270) {
                set(KEY_ROTATION, Integer.toString(rotation));
            } else {
                throw new IllegalArgumentException(
                        "Invalid rotation=" + rotation);
            }
        }

        /**
         * Sets GPS latitude coordinate. This will be stored in JPEG EXIF
         * header.
         *
         * @param latitude GPS latitude coordinate.
         */
        public void setGpsLatitude(double latitude) {
            set(KEY_GPS_LATITUDE, Double.toString(latitude));
        }

        /**
         * Sets GPS longitude coordinate. This will be stored in JPEG EXIF
         * header.
         *
         * @param longitude GPS longitude coordinate.
         */
        public void setGpsLongitude(double longitude) {
            set(KEY_GPS_LONGITUDE, Double.toString(longitude));
        }

        /**
         * Sets GPS altitude. This will be stored in JPEG EXIF header.
         *
         * @param altitude GPS altitude in meters.
         */
        public void setGpsAltitude(double altitude) {
            set(KEY_GPS_ALTITUDE, Double.toString(altitude));
        }

        /**
         * Sets GPS timestamp. This will be stored in JPEG EXIF header.
         *
         * @param timestamp GPS timestamp (UTC in seconds since January 1,
         *                  1970).
         */
        public void setGpsTimestamp(long timestamp) {
            set(KEY_GPS_TIMESTAMP, Long.toString(timestamp));
        }

        /**
         * Sets GPS processing method. It will store up to 32 characters
         * in JPEG EXIF header.
         *
         * @param processing_method The processing method to get this location.
         */
        public void setGpsProcessingMethod(String processing_method) {
            set(KEY_GPS_PROCESSING_METHOD, processing_method);
        }

        /**
         * Removes GPS latitude, longitude, altitude, and timestamp from the
         * parameters.
         */
        public void removeGpsData() {
            remove(KEY_GPS_LATITUDE);
            remove(KEY_GPS_LONGITUDE);
            remove(KEY_GPS_ALTITUDE);
            remove(KEY_GPS_TIMESTAMP);
            remove(KEY_GPS_PROCESSING_METHOD);
        }

        /**
         * Gets the current white balance setting.
         *
         * @return current white balance. null if white balance setting is not
         *         supported.
         * @see #WHITE_BALANCE_AUTO
         * @see #WHITE_BALANCE_INCANDESCENT
         * @see #WHITE_BALANCE_FLUORESCENT
         * @see #WHITE_BALANCE_WARM_FLUORESCENT
         * @see #WHITE_BALANCE_DAYLIGHT
         * @see #WHITE_BALANCE_CLOUDY_DAYLIGHT
         * @see #WHITE_BALANCE_TWILIGHT
         * @see #WHITE_BALANCE_SHADE
         *
         */
        public String getWhiteBalance() {
            return get(KEY_WHITE_BALANCE);
        }

        /**
         * Sets the white balance.
         *
         * @param value new white balance.
         * @see #getWhiteBalance()
         */
        public void setWhiteBalance(String value) {
            set(KEY_WHITE_BALANCE, value);
        }

        /**
         * Gets the supported white balance.
         *
         * @return a list of supported white balance. null if white balance
         *         setting is not supported.
         * @see #getWhiteBalance()
         */
        public List<String> getSupportedWhiteBalance() {
            String str = get(KEY_WHITE_BALANCE + SUPPORTED_VALUES_SUFFIX);
            return split(str);
        }

        /**
         * Gets the current color effect setting.
         *
         * @return current color effect. null if color effect
         *         setting is not supported.
         * @see #EFFECT_NONE
         * @see #EFFECT_MONO
         * @see #EFFECT_NEGATIVE
         * @see #EFFECT_SOLARIZE
         * @see #EFFECT_SEPIA
         * @see #EFFECT_POSTERIZE
         * @see #EFFECT_WHITEBOARD
         * @see #EFFECT_BLACKBOARD
         * @see #EFFECT_AQUA
         */
        public String getColorEffect() {
            return get(KEY_EFFECT);
        }

        /**
         * Sets the current color effect setting.
         *
         * @param value new color effect.
         * @see #getColorEffect()
         */
        public void setColorEffect(String value) {
            set(KEY_EFFECT, value);
        }

        /**
         * Gets the supported color effects.
         *
         * @return a list of supported color effects. null if color effect
         *         setting is not supported.
         * @see #getColorEffect()
         */
        public List<String> getSupportedColorEffects() {
            String str = get(KEY_EFFECT + SUPPORTED_VALUES_SUFFIX);
            return split(str);
        }

        /**
         * Gets the current Touch AF/AEC setting.
         *
         * @return one of TOUCH_AF_AEC_XXX string constant. null if Touch AF/AEC
         *         setting is not supported.
         *
         */
        public String getTouchAfAec() {
            return get(KEY_TOUCH_AF_AEC);
        }

        /**
         * Sets the current TOUCH AF/AEC setting.
         *
         * @param value TOUCH_AF_AEC_XXX string constants.
         *
         */
        public void setTouchAfAec(String value) {
            set(KEY_TOUCH_AF_AEC, value);
        }

       /**
         * Gets the supported Touch AF/AEC setting.
         *
         * @return a List of TOUCH_AF_AEC_XXX string constants. null if TOUCH AF/AEC
         *         setting is not supported.
         *
         */
        public List<String> getSupportedTouchAfAec() {
            String str = get(KEY_TOUCH_AF_AEC + SUPPORTED_VALUES_SUFFIX);
            return split(str);
        }

        /**
         * Sets the touch co-ordinate for Touch AEC.
         *
         * @param x  the x co-ordinate of the touch event
         * @param y the y co-ordinate of the touch event
         *
         */
        public void setTouchIndexAec(int x, int y) {
            String v = Integer.toString(x) + "x" + Integer.toString(y);
            set(KEY_TOUCH_INDEX_AEC, v);
        }

        /**
         * Returns the touch co-ordinates of the touch event.
         *
         * @return a Index object with the x and y co-ordinated
         *          for the touch event
         *
         */
        public Coordinate getTouchIndexAec() {
            String pair = get(KEY_TOUCH_INDEX_AEC);
            return strToCoordinate(pair);
        }

        /**
         * Sets the touch co-ordinate for Touch AF.
         *
         * @param x  the x co-ordinate of the touch event
         * @param y the y co-ordinate of the touch event
         *
         */
        public void setTouchIndexAf(int x, int y) {
            String v = Integer.toString(x) + "x" + Integer.toString(y);
            set(KEY_TOUCH_INDEX_AF, v);
        }

        /**
         * Returns the touch co-ordinates of the touch event.
         *
         * @return a Index object with the x and y co-ordinated
         *          for the touch event
         *
         */
        public Coordinate getTouchIndexAf() {
            String pair = get(KEY_TOUCH_INDEX_AF);
            return strToCoordinate(pair);
        }

        /**
         * Get Sharpness level
         *
         * @return sharpness level
         */
        public int getSharpness(){
            return getInt(KEY_SHARPNESS, 0);
        }

        /**
         * Set Sharpness Level
         *
         * @param sharpness level
         */
        public void setSharpness(int sharpness){
            if((sharpness < 0) || (sharpness > getMaxSharpness()) )
                throw new IllegalArgumentException(
                        "Invalid Sharpness " + sharpness);

            set(KEY_SHARPNESS, String.valueOf(sharpness));
        }

        /**
         * Get Max Sharpness Level
         *
         * @return max sharpness level
         */
        public int getMaxSharpness(){
            return getInt(KEY_MAX_SHARPNESS, 0);
        }

        /**
         * Get default sharpness level
         * 
         * @return default sharpness level
         */
        public int getDefaultSharpness() {
            return getInt(KEY_DEFAULT_SHARPNESS, 0);
        }

        /**
         * Get Contrast level
         *
         * @return contrast level
         */
        public int getContrast(){
            return getInt(KEY_CONTRAST, 0);
        }

        /**
         * Set Contrast Level
         *
         * @param contrast level
         */
        public void setContrast(int contrast){
            if((contrast < 0 ) || (contrast > getMaxContrast()))
                throw new IllegalArgumentException(
                        "Invalid Contrast " + contrast);

            set(KEY_CONTRAST, String.valueOf(contrast));
        }

        /**
         * Get Max Contrast Level
         *
         * @return max contrast level
         */
        public int getMaxContrast(){
            return getInt(KEY_MAX_CONTRAST, 0);
        }

        /**
         * Get default contrast level
         * 
         * @return default contrast level
         */
        public int getDefaultContrast() {
            return getInt(KEY_DEFAULT_CONTRAST, 0);
        }

        /**
         * Get Saturation level
         *
         * @return saturation level
         */
        public int getSaturation(){
            return getInt(KEY_SATURATION, 0);
        }

        /**
         * Set Saturation Level
         *
         * @param saturation level
         */
        public void setSaturation(int saturation){
            if((saturation < 0 ) || (saturation > getMaxSaturation()))
                throw new IllegalArgumentException(
                        "Invalid Saturation " + saturation);

            set(KEY_SATURATION, String.valueOf(saturation));
        }

        /**
         * Get Max Saturation Level
         *
         * @return max contrast level
         */
        public int getMaxSaturation(){
            return getInt(KEY_MAX_SATURATION, 0);
        }

        /**
         * Get default saturation level
         * 
         * @return default saturation level
         */
        public int getDefaultSaturation() {
            return getInt(KEY_DEFAULT_SATURATION, 0);
        }

        /**
         * Get brightness level
         *
         * @return brightness level
         */
        public int getBrightness(){
            return getInt(KEY_BRIGHTNESS, 0);
        }

        /**
         * Set brightness level
         *
         * @param brightness level
         */
        public void setBrightness(int brightness){
            if((brightness < 0 ) || (brightness > getMaxBrightness()))
                throw new IllegalArgumentException(
                        "Invalid Brightness " + brightness);

            set(KEY_BRIGHTNESS, String.valueOf(brightness));
        }

        /**
         * Get Max Brightness Level
         *
         * @return max brightness level
         */
        public int getMaxBrightness(){
            return getInt(KEY_MAX_BRIGHTNESS, 0);
        }

        /**
         * Get default brightness level
         * 
         * @return default brightness level
         */
        public int getDefaultBrightness() {
            return getInt(KEY_DEFAULT_BRIGHTNESS, 0);
        }

        /**
         * Gets the current antibanding setting.
         *
         * @return current antibanding. null if antibanding setting is not
         *         supported.
         * @see #ANTIBANDING_AUTO
         * @see #ANTIBANDING_50HZ
         * @see #ANTIBANDING_60HZ
         * @see #ANTIBANDING_OFF
         */
        public String getAntibanding() {
            return get(KEY_ANTIBANDING);
        }

        /**
         * Sets the antibanding.
         *
         * @param antibanding new antibanding value.
         * @see #getAntibanding()
         */
        public void setAntibanding(String antibanding) {
            set(KEY_ANTIBANDING, antibanding);
        }

        /**
         * Gets the supported antibanding values.
         *
         * @return a list of supported antibanding values. null if antibanding
         *         setting is not supported.
         * @see #getAntibanding()
         */
        public List<String> getSupportedAntibanding() {
            String str = get(KEY_ANTIBANDING + SUPPORTED_VALUES_SUFFIX);
            return split(str);
        }

        /**
         * Gets the frame rate mode setting.
         *
         * @return one of FRAME_RATE_XXX_MODE string constant. null if this
         *         setting is not supported.
         */
        public String getPreviewFrameRateMode() {
            return get(KEY_PREVIEW_FRAME_RATE_MODE);
        }

        /**
         * Sets the frame rate mode.
         *
         * @param value FRAME_RATE_XXX_MODE string constants.
         */
        public void setPreviewFrameRateMode(String value) {
            set(KEY_PREVIEW_FRAME_RATE_MODE, value);
        }

        /**
         * Gets the supported frame rate modes.
         *
         * @return a List of FRAME_RATE_XXX_MODE string constant. null if this
         *         setting is not supported.
         */
        public List<String> getSupportedPreviewFrameRateModes() {
            String str = get(KEY_PREVIEW_FRAME_RATE_MODE + SUPPORTED_VALUES_SUFFIX);
            return split(str);
        }

        /**
         * Gets the current scene mode setting.
         *
         * @return one of SCENE_MODE_XXX string constant. null if scene mode
         *         setting is not supported.
         * @see #SCENE_MODE_AUTO
         * @see #SCENE_MODE_ACTION
         * @see #SCENE_MODE_PORTRAIT
         * @see #SCENE_MODE_LANDSCAPE
         * @see #SCENE_MODE_NIGHT
         * @see #SCENE_MODE_NIGHT_PORTRAIT
         * @see #SCENE_MODE_THEATRE
         * @see #SCENE_MODE_BEACH
         * @see #SCENE_MODE_SNOW
         * @see #SCENE_MODE_SUNSET
         * @see #SCENE_MODE_STEADYPHOTO
         * @see #SCENE_MODE_FIREWORKS
         * @see #SCENE_MODE_SPORTS
         * @see #SCENE_MODE_PARTY
         * @see #SCENE_MODE_CANDLELIGHT
         */
        public String getSceneMode() {
            return get(KEY_SCENE_MODE);
        }

        /**
         * Sets the scene mode. Changing scene mode may override other
         * parameters (such as flash mode, focus mode, white balance). For
         * example, suppose originally flash mode is on and supported flash
         * modes are on/off. In night scene mode, both flash mode and supported
         * flash mode may be changed to off. After setting scene mode,
         * applications should call getParameters to know if some parameters are
         * changed.
         *
         * @param value scene mode.
         * @see #getSceneMode()
         */
        public void setSceneMode(String value) {
            set(KEY_SCENE_MODE, value);
        }

        /**
         * Gets the supported scene modes.
         *
         * @return a list of supported scene modes. null if scene mode setting
         *         is not supported.
         * @see #getSceneMode()
         */
        public List<String> getSupportedSceneModes() {
            String str = get(KEY_SCENE_MODE + SUPPORTED_VALUES_SUFFIX);
            return split(str);
        }

        /**
         * Gets the current flash mode setting.
         *
         * @return current flash mode. null if flash mode setting is not
         *         supported.
         * @see #FLASH_MODE_OFF
         * @see #FLASH_MODE_AUTO
         * @see #FLASH_MODE_ON
         * @see #FLASH_MODE_RED_EYE
         * @see #FLASH_MODE_TORCH
         */
        public String getFlashMode() {
            return get(KEY_FLASH_MODE);
        }

        /**
         * Sets the flash mode.
         *
         * @param value flash mode.
         * @see #getFlashMode()
         */
        public void setFlashMode(String value) {
            set(KEY_FLASH_MODE, value);
        }

        /**
         * Gets the supported flash modes.
         *
         * @return a list of supported flash modes. null if flash mode setting
         *         is not supported.
         * @see #getFlashMode()
         */
        public List<String> getSupportedFlashModes() {
            String str = get(KEY_FLASH_MODE + SUPPORTED_VALUES_SUFFIX);
            return split(str);
        }

        /**
         * Gets the current focus mode setting.
         *
         * @return current focus mode. This method will always return a non-null
         *         value. Applications should call {@link
         *         #autoFocus(AutoFocusCallback)} to start the focus if focus
         *         mode is FOCUS_MODE_AUTO or FOCUS_MODE_MACRO.
         * @see #FOCUS_MODE_AUTO
         * @see #FOCUS_MODE_INFINITY
         * @see #FOCUS_MODE_MACRO
         * @see #FOCUS_MODE_FIXED
         * @see #FOCUS_MODE_EDOF
         * @see #FOCUS_MODE_CONTINUOUS_VIDEO
         */
        public String getFocusMode() {
            return get(KEY_FOCUS_MODE);
        }

        /**
         * Sets the focus mode.
         *
         * @param value focus mode.
         * @see #getFocusMode()
         */
        public void setFocusMode(String value) {
            set(KEY_FOCUS_MODE, value);
        }

        /**
         * Gets the supported focus modes.
         *
         * @return a list of supported focus modes. This method will always
         *         return a list with at least one element.
         * @see #getFocusMode()
         */
        public List<String> getSupportedFocusModes() {
            String str = get(KEY_FOCUS_MODE + SUPPORTED_VALUES_SUFFIX);
            return split(str);
        }

        /**
         * Gets the focal length (in millimeter) of the camera.
         *
         * @return the focal length. This method will always return a valid
         *         value.
         */
        public float getFocalLength() {
            return Float.parseFloat(get(KEY_FOCAL_LENGTH));
        }

        /**
         * Gets the horizontal angle of view in degrees.
         *
         * @return horizontal angle of view. This method will always return a
         *         valid value.
         */
        public float getHorizontalViewAngle() {
            return Float.parseFloat(get(KEY_HORIZONTAL_VIEW_ANGLE));
        }

        /**
         * Gets the vertical angle of view in degrees.
         *
         * @return vertical angle of view. This method will always return a
         *         valid value.
         */
        public float getVerticalViewAngle() {
            return Float.parseFloat(get(KEY_VERTICAL_VIEW_ANGLE));
        }

        /**
         * Gets the current exposure compensation index.
         *
         * @return current exposure compensation index. The range is {@link
         *         #getMinExposureCompensation} to {@link
         *         #getMaxExposureCompensation}. 0 means exposure is not
         *         adjusted.
         */
        public int getExposureCompensation() {
            return getInt(KEY_EXPOSURE_COMPENSATION, 0);
        }

        /**
         * Sets the exposure compensation index.
         *
         * @param value exposure compensation index. The valid value range is
         *        from {@link #getMinExposureCompensation} (inclusive) to {@link
         *        #getMaxExposureCompensation} (inclusive). 0 means exposure is
         *        not adjusted. Application should call
         *        getMinExposureCompensation and getMaxExposureCompensation to
         *        know if exposure compensation is supported.
         */
        public void setExposureCompensation(int value) {
            set(KEY_EXPOSURE_COMPENSATION, value);
        }

        /**
         * Gets the maximum exposure compensation index.
         *
         * @return maximum exposure compensation index (>=0). If both this
         *         method and {@link #getMinExposureCompensation} return 0,
         *         exposure compensation is not supported.
         */
        public int getMaxExposureCompensation() {
            return getInt(KEY_MAX_EXPOSURE_COMPENSATION, 0);
        }

        /**
         * Gets the minimum exposure compensation index.
         *
         * @return minimum exposure compensation index (<=0). If both this
         *         method and {@link #getMaxExposureCompensation} return 0,
         *         exposure compensation is not supported.
         */
        public int getMinExposureCompensation() {
            return getInt(KEY_MIN_EXPOSURE_COMPENSATION, 0);
        }

        /**
         * Gets the exposure compensation step.
         *
         * @return exposure compensation step. Applications can get EV by
         *         multiplying the exposure compensation index and step. Ex: if
         *         exposure compensation index is -6 and step is 0.333333333, EV
         *         is -2.
         */
        public float getExposureCompensationStep() {
            return getFloat(KEY_EXPOSURE_COMPENSATION_STEP, 0);
        }

        /**
         * Gets current zoom value. This also works when smooth zoom is in
         * progress. Applications should check {@link #isZoomSupported} before
         * using this method.
         *
         * @return the current zoom value. The range is 0 to {@link
         *         #getMaxZoom}. 0 means the camera is not zoomed.
         */
        public int getZoom() {
            return getInt(KEY_ZOOM, 0);
        }

        /**
         * Sets current zoom value. If the camera is zoomed (value > 0), the
         * actual picture size may be smaller than picture size setting.
         * Applications can check the actual picture size after picture is
         * returned from {@link PictureCallback}. The preview size remains the
         * same in zoom. Applications should check {@link #isZoomSupported}
         * before using this method.
         *
         * @param value zoom value. The valid range is 0 to {@link #getMaxZoom}.
         */
        public void setZoom(int value) {
            set(KEY_ZOOM, value);
        }

        /**
         * Returns true if zoom is supported. Applications should call this
         * before using other zoom methods.
         *
         * @return true if zoom is supported.
         */
        public boolean isZoomSupported() {
            String str = get(KEY_ZOOM_SUPPORTED);
            return TRUE.equals(str) && getMaxZoom() > 0;
        }

        /**
         * Gets the maximum zoom value allowed for snapshot. This is the maximum
         * value that applications can set to {@link #setZoom(int)}.
         * Applications should call {@link #isZoomSupported} before using this
         * method. This value may change in different preview size. Applications
         * should call this again after setting preview size.
         *
         * @return the maximum zoom value supported by the camera.
         */
        public int getMaxZoom() {
            return getInt(KEY_MAX_ZOOM, 0);
        }

        /**
         * Gets the zoom ratios of all zoom values. Applications should check
         * {@link #isZoomSupported} before using this method.
         *
         * @return the zoom ratios in 1/100 increments. Ex: a zoom of 3.2x is
         *         returned as 320. The number of elements is {@link
         *         #getMaxZoom} + 1. The list is sorted from small to large. The
         *         first element is always 100. The last element is the zoom
         *         ratio of the maximum zoom value.
         */
        public List<Integer> getZoomRatios() {
            return splitInt(get(KEY_ZOOM_RATIOS));
        }

        /**
         * Returns true if smooth zoom is supported. Applications should call
         * this before using other smooth zoom methods.
         *
         * @return true if smooth zoom is supported.
         */
        public boolean isSmoothZoomSupported() {
            String str = get(KEY_SMOOTH_ZOOM_SUPPORTED);
            return TRUE.equals(str);
        }

        /**
         * Gets the distances from the camera to where an object appears to be
         * in focus. The object is sharpest at the optimal focus distance. The
         * depth of field is the far focus distance minus near focus distance.
         *
         * Focus distances may change after calling {@link
         * #autoFocus(AutoFocusCallback)}, {@link #cancelAutoFocus}, or {@link
         * #startPreview()}. Applications can call {@link #getParameters()}
         * and this method anytime to get the latest focus distances. If the
         * focus mode is FOCUS_MODE_CONTINUOUS_VIDEO, focus distances may change
         * from time to time.
         *
         * This method is intended to estimate the distance between the camera
         * and the subject. After autofocus, the subject distance may be within
         * near and far focus distance. However, the precision depends on the
         * camera hardware, autofocus algorithm, the focus area, and the scene.
         * The error can be large and it should be only used as a reference.
         *
         * Far focus distance >= optimal focus distance >= near focus distance.
         * If the focus distance is infinity, the value will be
         * Float.POSITIVE_INFINITY.
         *
         * @param output focus distances in meters. output must be a float
         *        array with three elements. Near focus distance, optimal focus
         *        distance, and far focus distance will be filled in the array.
         * @see #FOCUS_DISTANCE_NEAR_INDEX
         * @see #FOCUS_DISTANCE_OPTIMAL_INDEX
         * @see #FOCUS_DISTANCE_FAR_INDEX
         */
        public void getFocusDistances(float[] output) {
            if (output == null || output.length != 3) {
                throw new IllegalArgumentException(
                        "output must be an float array with three elements.");
            }
            splitFloat(get(KEY_FOCUS_DISTANCES), output);
        }

        /**
         * Gets the current Continuous AF setting.
         *
         * @return one of CONTINUOUS_AF_XXX string constant. null if continuous AF
         *         setting is not supported.
         *
         */
        public String getContinuousAf() {
            return get(KEY_CAF);
        }

        /**
         * Sets the current Continuous AF mode.
         * @param value CONTINUOUS_AF_XXX string constants.
         *
         */
        public void setContinuousAf(String value) {
            set(KEY_CAF, value);
        }

        /**
         * Gets the supported Continuous AF modes.
         *
         * @return a List of CONTINUOUS_AF_XXX string constant. null if continuous AF
         *         setting is not supported.
         *
         */
        public List<String> getSupportedContinuousAfModes() {
            String str = get(KEY_CAF + SUPPORTED_VALUES_SUFFIX);
            return split(str);
        }

        /**
         * Gets the current ISO setting.
         *
         * @return one of ISO_XXX string constant. null if ISO
         *         setting is not supported.
         */
        public String getISOValue() {
            return get(KEY_ISO_MODE);
        }

        /**
         * Sets the ISO.
         *
         * @param iso ISO_XXX string constant.
         */
        public void setISOValue(String iso) {
            set(KEY_ISO_MODE, iso);
        }

         /**
         * Gets the supported ISO values.
         *
         * @return a List of FLASH_MODE_XXX string constants. null if flash mode
         *         setting is not supported.
         */
        public List<String> getSupportedIsoValues() {
            String str = get(KEY_ISO_MODE + SUPPORTED_VALUES_SUFFIX);
            return split(str);
        }

         /**
         * Gets the current LensShade Mode.
         *
         * @return LensShade Mode
         */
        public String getLensShade() {
            return get(KEY_LENSSHADE);
        }

        /**
         * Sets the current LensShade Mode.
         *
         * @return LensShade Mode
         */
        public void setLensShade(String lensshade) {
            set(KEY_LENSSHADE, lensshade);
        }

         /**
         * Gets the supported Lensshade modes.
         *
         * @return a List of LENS_MODE_XXX string constants. null if lens mode
         *         setting is not supported.
         */
        public List<String> getSupportedLensShadeModes() {
            String str = get(KEY_LENSSHADE + SUPPORTED_VALUES_SUFFIX);
            return split(str);
        }

         /**
         * Gets the current auto exposure setting.
         *
         * @return one of AUTO_EXPOSURE_XXX string constant. null if auto exposure
         *         setting is not supported.
         */
        public String getAutoExposure() {
            return get(KEY_AUTO_EXPOSURE);
        }

        /**
         * Sets the current auto exposure setting.
         *
         * @param value AUTO_EXPOSURE_XXX string constants.
         */
        public void setAutoExposure(String value) {
            set(KEY_AUTO_EXPOSURE, value);
        }

        /**
         * Gets the supported auto exposure setting.
         *
         * @return a List of AUTO_EXPOSURE_XXX string constants. null if auto exposure
         *         setting is not supported.
         */
        public List<String> getSupportedAutoexposure() {
            String str = get(KEY_AUTO_EXPOSURE + SUPPORTED_VALUES_SUFFIX);
            return split(str);
        }

        /**
         * Sets the smart-contrast feature
         * @param enabled SMART_CONTRAST desired status.
         */
        public void setSmartContrastEnabled(boolean enabled) {
            set(KEY_SMART_CONTRAST, enabled ? "on" : "off");
        }
        
        /**
         * Gets the value of smart-contrast
         *
         * @return if smart-contrast is enabled
         */
        public boolean isSmartContrastEnabled() {
            return "on".equals(get(KEY_SMART_CONTRAST));
        }
        
        // Splits a comma delimited string to an ArrayList of String.
        // Return null if the passing string is null or the size is 0.
        private ArrayList<String> split(String str) {
            if (str == null) return null;

            // Use StringTokenizer because it is faster than split.
            StringTokenizer tokenizer = new StringTokenizer(str, ",");
            ArrayList<String> substrings = new ArrayList<String>();
            while (tokenizer.hasMoreElements()) {
                substrings.add(tokenizer.nextToken());
            }
            return substrings;
        }

        // Splits a comma delimited string to an ArrayList of Integer.
        // Return null if the passing string is null or the size is 0.
        private ArrayList<Integer> splitInt(String str) {
            if (str == null) return null;

            StringTokenizer tokenizer = new StringTokenizer(str, ",");
            ArrayList<Integer> substrings = new ArrayList<Integer>();
            while (tokenizer.hasMoreElements()) {
                String token = tokenizer.nextToken();
                substrings.add(Integer.parseInt(token));
            }
            if (substrings.size() == 0) return null;
            return substrings;
        }

        private void splitInt(String str, int[] output) {
            if (str == null) return;

            StringTokenizer tokenizer = new StringTokenizer(str, ",");
            int index = 0;
            while (tokenizer.hasMoreElements()) {
                String token = tokenizer.nextToken();
                output[index++] = Integer.parseInt(token);
            }
        }

        // Splits a comma delimited string to an ArrayList of Float.
        private void splitFloat(String str, float[] output) {
            if (str == null) return;

            StringTokenizer tokenizer = new StringTokenizer(str, ",");
            int index = 0;
            while (tokenizer.hasMoreElements()) {
                String token = tokenizer.nextToken();
                output[index++] = Float.parseFloat(token);
            }
        }

        // Returns the value of a float parameter.
        private float getFloat(String key, float defaultValue) {
            if (!mMap.containsKey(key)) {
                return defaultValue;
            }
            try {
                return Float.parseFloat(mMap.get(key));
            } catch (NumberFormatException ex) {
                return defaultValue;
            }
        }

        // Returns the value of a integer parameter.
        private int getInt(String key, int defaultValue) {
            if (!mMap.containsKey(key)) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(mMap.get(key));
            } catch (NumberFormatException ex) {
                return defaultValue;
            }
        }

        // Splits a comma delimited string to an ArrayList of Size.
        // Return null if the passing string is null or the size is 0.
        private ArrayList<Size> splitSize(String str) {
            if (str == null) return null;

            StringTokenizer tokenizer = new StringTokenizer(str, ",");
            ArrayList<Size> sizeList = new ArrayList<Size>();
            while (tokenizer.hasMoreElements()) {
                Size size = strToSize(tokenizer.nextToken());
                if (size != null) sizeList.add(size);
            }
            if (sizeList.size() == 0) return null;
            return sizeList;
        }

        // Parses a string (ex: "480x320") to Size object.
        // Return null if the passing string is null.
        private Size strToSize(String str) {
            if (str == null) return null;

            int pos = str.indexOf('x');
            if (pos != -1) {
                String width = str.substring(0, pos);
                String height = str.substring(pos + 1);
                return new Size(Integer.parseInt(width),
                                Integer.parseInt(height));
            }
            Log.e(TAG, "Invalid size parameter string=" + str);
            return null;
        }

        // Splits a comma delimited string to an ArrayList of int array.
        // Example string: "(10000,26623),(10000,30000)". Return null if the
        // passing string is null or the size is 0.
        private ArrayList<int[]> splitRange(String str) {
            if (str == null || str.charAt(0) != '('
                    || str.charAt(str.length() - 1) != ')') {
                Log.e(TAG, "Invalid range list string=" + str);
                return null;
            }

            ArrayList<int[]> rangeList = new ArrayList<int[]>();
            int endIndex, fromIndex = 1;
            do {
                int[] range = new int[2];
                endIndex = str.indexOf("),(", fromIndex);
                if (endIndex == -1) endIndex = str.length() - 1;
                splitInt(str.substring(fromIndex, endIndex), range);
                rangeList.add(range);
                fromIndex = endIndex + 3;
            } while (endIndex != str.length() - 1);

            if (rangeList.size() == 0) return null;
            return rangeList;
        }

        // Splits a comma delimited string to an ArrayList of Coordinate.
        // Return null if the passing string is null or the Coordinate is 0.
        private ArrayList<Coordinate> splitCoordinate(String str) {
            if (str == null) return null;

            StringTokenizer tokenizer = new StringTokenizer(str, ",");
            ArrayList<Coordinate> coordinateList = new ArrayList<Coordinate>();
            while (tokenizer.hasMoreElements()) {
                Coordinate c = strToCoordinate(tokenizer.nextToken());
                if (c != null) coordinateList.add(c);
            }
            if (coordinateList.size() == 0) return null;
            return coordinateList;
        }

        // Parses a string (ex: "500x500") to Coordinate object.
        // Return null if the passing string is null.
        private Coordinate strToCoordinate(String str) {
            if (str == null) return null;

            int pos = str.indexOf('x');
            if (pos != -1) {
                String x = str.substring(0, pos);
                String y = str.substring(pos + 1);
                return new Coordinate(Integer.parseInt(x),
                                Integer.parseInt(y));
            }
            Log.e(TAG, "Invalid Coordinate parameter string=" + str);
            return null;
        }
    };
}
