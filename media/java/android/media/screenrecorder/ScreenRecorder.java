/*
 * Copyright (C) 2013 The ChameleonOS Open Source Project
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

package android.media.screenrecorder;

import android.util.Log;
import android.view.Surface;

import java.lang.IllegalArgumentException;

/**
 * {@hide}
 */
public class ScreenRecorder {
    static {
        System.loadLibrary("screenrecorder");
    }

    private static final String TAG = "ScreenRecorder";
    private static ScreenRecorder sScreenRecorder = null;

    private static ScreenRecorderCallbacks sCallbacks;

    public static final int STATE_UNINITIALIZED = 0;
    public static final int STATE_IDLE = 1;
    public static final int STATE_RECORDING = 2;

    private static int sState = STATE_UNINITIALIZED;

    private ScreenRecorder() {
    }

    /**
     * There can be only one!
     * @return
     */
    public static ScreenRecorder getInstance() {
        if (sScreenRecorder == null) {
            sScreenRecorder = new ScreenRecorder();
        }
        return sScreenRecorder;
    }

    /**
     * Initialize the screen recorder.
     * @param rotation The display orientation to record, can be one of the following:
     *                    Surface.ROTATION_0
     *                    Surface.ROTATION_90
     *                    Surface.ROTATION_180
     *                    Surface.ROTATION_270
     * @param width Width of the video output.
     * @param height Height of the video output.
     * @param bitRate Bitrate to record at, default is 4000000.
     * @param timeLimitSec Maximum time to record for.  Maximum allowed is 300 seconds (5 minutes).
     */
    public void init(int rotation, int width, int height,
                     int bitRate, int timeLimitSec, boolean recordAudio) {
        if (sState == STATE_RECORDING) {
            throw new IllegalStateException("ScreenRecorder is currently recording.");
        }
        if (rotation != Surface.ROTATION_0 && rotation != Surface.ROTATION_90
                && rotation != Surface.ROTATION_180 && rotation != Surface.ROTATION_270) {
            throw new IllegalArgumentException("Invalid rotation: " + rotation);
        }
        if (width <= 0) {
            throw new IllegalArgumentException("Invalid width: " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("Invalid height: " + height);
        }
        native_init(rotation, width, height, bitRate, timeLimitSec, recordAudio);
        sState = STATE_IDLE;
    }

    /**
     * Start recording the display to the specified file.
     * @param fileName Filename including path to save video to.
     * @return True if video recording is able to start.
     */
    public boolean start(String fileName) {
        if (sState != STATE_IDLE) {
            throw new IllegalStateException("ScreenRecorder is not idle.");
        }
        boolean result = native_start(fileName);
        if (result) sState = STATE_RECORDING;
        return result;
    }

    /**
     * Stop recording video.  Call this to stop recording before the specified time is up.
     */
    public void stop() {
        if (sState != STATE_RECORDING) {
            throw new IllegalStateException("ScreenRecorder is not recording.");
        }
        native_stop();
        sState = STATE_IDLE;
    }

    /**
     * Get the current state of the screen recorder.
     * @return The current state
     */
    public int getState() {
        return sState;
    }

    /**
     * Register to recieve callbacks from the screen recorder.
     * @param callbacks
     */
    public void setScreenRecorderCallbacks(ScreenRecorderCallbacks callbacks) {
        sCallbacks = callbacks;
    }

    // callbacks from libscreenrecorder
    private void onRecordingStarted() {
        if (sCallbacks != null) sCallbacks.onRecordingStarted();
    }

    private void onRecordingFinished() {
        if (sCallbacks != null) sCallbacks.onRecordingFinished();
    }

    private void onError(int error, String message) {
        if (sCallbacks != null) sCallbacks.onRecordingError(message);
    }

    public interface ScreenRecorderCallbacks {
        public void onRecordingStarted();
        public void onRecordingFinished();
        public void onRecordingError(String error);
    }

    private static native void native_init(int rotation, int width, int height,
                                           int bitRate, int timeLimitSec, boolean recordAudio);
    private static native boolean native_start(String fileName);
    private static native void native_stop();

}
