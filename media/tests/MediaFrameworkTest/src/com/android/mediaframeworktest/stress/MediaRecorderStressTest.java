/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.mediaframeworktest.stress;


import com.android.mediaframeworktest.MediaFrameworkTest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android.hardware.Camera;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.view.SurfaceHolder;
import com.android.mediaframeworktest.MediaRecorderStressTestRunner;

/**
 * Junit / Instrumentation test case for the media player api

 */
public class MediaRecorderStressTest extends ActivityInstrumentationTestCase2<MediaFrameworkTest> {


    private String TAG = "MediaRecorderStressTest";
    private MediaRecorder mRecorder;
    private Camera mCamera;

    private static final int NUMBER_OF_CAMERA_STRESS_LOOPS = 100;
    private static final int NUMBER_OF_RECORDER_STRESS_LOOPS = 100;
    private static final int NUMBER_OF_RECORDERANDPLAY_STRESS_LOOPS = 50;
    private static final int NUMBER_OF_SWTICHING_LOOPS_BW_CAMERA_AND_RECORDER = 200;
    private static final long WAIT_TIME_CAMERA_TEST = 3000;  // 3 second
    private static final long WAIT_TIME_RECORDER_TEST = 6000;  // 6 second
    private static final String OUTPUT_FILE = "/sdcard/temp";
    private static final String OUTPUT_FILE_EXT = ".3gp";
    private static final String MEDIA_STRESS_OUTPUT =
        "/sdcard/mediaStressOutput.txt";
    private final CameraErrorCallback mCameraErrorCallback = new CameraErrorCallback();
    private final RecorderErrorCallback mRecorderErrorCallback = new RecorderErrorCallback();

    private final static int WAIT_TIMEOUT = 10000;
    private Thread mLooperThread;
    private Handler mHandler;

    public MediaRecorderStressTest() {
        super("com.android.mediaframeworktest", MediaFrameworkTest.class);
    }

    protected void setUp() throws Exception {
        final Semaphore sem = new Semaphore(0);
        mLooperThread = new Thread() {
            @Override
            public void run() {
                Log.v(TAG, "starting looper");
                Looper.prepare();
                mHandler = new Handler();
                sem.release();
                Looper.loop();
                Log.v(TAG, "quit looper");
            }
        };
        mLooperThread.start();
        if (! sem.tryAcquire(WAIT_TIMEOUT, TimeUnit.MILLISECONDS)) {
            fail("Failed to start the looper.");
        }

        getActivity();
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHandler != null) {
            mHandler.getLooper().quit();
            mHandler = null;
        }
        if (mLooperThread != null) {
            mLooperThread.join(WAIT_TIMEOUT);
            if (mLooperThread.isAlive()) {
                fail("Failed to stop the looper.");
            }
            mLooperThread = null;
        }

        super.tearDown();
    }

    private void runOnLooper(final Runnable command) throws InterruptedException {
        final Semaphore sem = new Semaphore(0);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    command.run();
                } finally {
                    sem.release();
                }
            }
        });
        if (! sem.tryAcquire(WAIT_TIMEOUT, TimeUnit.MILLISECONDS)) {
            fail("Failed to run the command on the looper.");
        }
    }

    private final class CameraErrorCallback implements android.hardware.Camera.ErrorCallback {
        public void onError(int error, android.hardware.Camera camera) {
            if (error == android.hardware.Camera.CAMERA_ERROR_SERVER_DIED) {
                assertTrue("Camera test mediaserver died", false);
            }
        }
    }

    private final class RecorderErrorCallback implements MediaRecorder.OnErrorListener {
        public void onError(MediaRecorder mr, int what, int extra) {
            // fail the test case no matter what error come up
            assertTrue("mediaRecorder error", false);
        }
    }

    //Test case for stressing the camera preview.
    @LargeTest
    public void testStressCamera() throws Exception {
        SurfaceHolder mSurfaceHolder;
        mSurfaceHolder = MediaFrameworkTest.mSurfaceView.getHolder();
        File stressOutFile = new File(MEDIA_STRESS_OUTPUT);
        Writer output = new BufferedWriter(new FileWriter(stressOutFile, true));
        output.write("Camera start preview stress:\n");
        output.write("Total number of loops:" +
                NUMBER_OF_CAMERA_STRESS_LOOPS + "\n");
        try {
            Log.v(TAG, "Start preview");
            output.write("No of loop: ");

            for (int i = 0; i< NUMBER_OF_CAMERA_STRESS_LOOPS; i++) {
                runOnLooper(new Runnable() {
                    @Override
                    public void run() {
                        mCamera = Camera.open();
                    }
                });
                mCamera.setErrorCallback(mCameraErrorCallback);
                mCamera.setPreviewDisplay(mSurfaceHolder);
                mCamera.startPreview();
                Thread.sleep(WAIT_TIME_CAMERA_TEST);
                mCamera.stopPreview();
                mCamera.release();
                output.write(" ," + i);
            }
        } catch (Exception e) {
            assertTrue("CameraStressTest", false);
            Log.v(TAG, e.toString());
        }
        output.write("\n\n");
        output.close();
    }

    //Test case for stressing the camera preview.
    @LargeTest
    public void testStressRecorder() throws Exception {
        String filename;
        SurfaceHolder mSurfaceHolder;
        mSurfaceHolder = MediaFrameworkTest.mSurfaceView.getHolder();
        File stressOutFile = new File(MEDIA_STRESS_OUTPUT);
        Writer output = new BufferedWriter(new FileWriter(stressOutFile, true));
        output.write("H263 video record- reset after prepare Stress test\n");
        output.write("Total number of loops:" +
                NUMBER_OF_RECORDER_STRESS_LOOPS + "\n");
        try {
            output.write("No of loop: ");
            Log.v(TAG, "Start preview");
            for (int i = 0; i < NUMBER_OF_RECORDER_STRESS_LOOPS; i++) {
                runOnLooper(new Runnable() {
                    @Override
                    public void run() {
                        mRecorder = new MediaRecorder();
                    }
                });
                Log.v(TAG, "counter = " + i);
                filename = OUTPUT_FILE + i + OUTPUT_FILE_EXT;
                Log.v(TAG, filename);
                mRecorder.setOnErrorListener(mRecorderErrorCallback);
                mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                mRecorder.setOutputFile(filename);
                mRecorder.setVideoFrameRate(20);
                mRecorder.setVideoSize(176,144);
                Log.v(TAG, "setEncoder");
                mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H263);
                mSurfaceHolder = MediaFrameworkTest.mSurfaceView.getHolder();
                Log.v(TAG, "setPreview");
                mRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
                Log.v(TAG, "prepare");
                mRecorder.prepare();
                Log.v(TAG, "before release");
                Thread.sleep(WAIT_TIME_RECORDER_TEST);
                mRecorder.reset();
                mRecorder.release();
                output.write(", " + i);
            }
        } catch (Exception e) {
            assertTrue("Recorder Stress test", false);
            Log.v(TAG, e.toString());
        }
        output.write("\n\n");
        output.close();
    }

    //Stress test case for switching camera and video recorder preview.
    @LargeTest
    public void testStressCameraSwitchRecorder() throws Exception {
        String filename;
        SurfaceHolder mSurfaceHolder;
        mSurfaceHolder = MediaFrameworkTest.mSurfaceView.getHolder();
        File stressOutFile = new File(MEDIA_STRESS_OUTPUT);
        Writer output = new BufferedWriter(new FileWriter(stressOutFile, true));
        output.write("Camera and video recorder preview switching\n");
        output.write("Total number of loops:"
                + NUMBER_OF_SWTICHING_LOOPS_BW_CAMERA_AND_RECORDER + "\n");
        try {
            Log.v(TAG, "Start preview");
            output.write("No of loop: ");
            for (int i = 0; i < NUMBER_OF_SWTICHING_LOOPS_BW_CAMERA_AND_RECORDER; i++) {
                runOnLooper(new Runnable() {
                    @Override
                    public void run() {
                        mCamera = Camera.open();
                    }
                });
                mCamera.setErrorCallback(mCameraErrorCallback);
                mCamera.setPreviewDisplay(mSurfaceHolder);
                mCamera.startPreview();
                Thread.sleep(WAIT_TIME_CAMERA_TEST);
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
                Log.v(TAG, "release camera");
                filename = OUTPUT_FILE + i + OUTPUT_FILE_EXT;
                Log.v(TAG, filename);
                runOnLooper(new Runnable() {
                    @Override
                    public void run() {
                        mRecorder = new MediaRecorder();
                    }
                });
                mRecorder.setOnErrorListener(mRecorderErrorCallback);
                mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                mRecorder.setOutputFile(filename);
                mRecorder.setVideoFrameRate(20);
                mRecorder.setVideoSize(176,144);
                Log.v(TAG, "Media recorder setEncoder");
                mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H263);
                Log.v(TAG, "mediaRecorder setPreview");
                mRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
                Log.v(TAG, "prepare");
                mRecorder.prepare();
                Log.v(TAG, "before release");
                Thread.sleep(WAIT_TIME_CAMERA_TEST);
                mRecorder.release();
                Log.v(TAG, "release video recorder");
                output.write(", " + i);
            }
        } catch (Exception e) {
            assertTrue("Camer and recorder switch mode", false);
                Log.v(TAG, e.toString());
        }
        output.write("\n\n");
        output.close();
    }

    public void validateRecordedVideo(String recorded_file) {
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(recorded_file);
            mp.prepare();
            int duration = mp.getDuration();
            if (duration <= 0){
                assertTrue("stressRecordAndPlayback", false);
            }
            mp.release();
        } catch (Exception e) {
            assertTrue("stressRecordAndPlayback", false);
        }
    }

    public void removeRecodedVideo(String filename){
        File video = new File(filename);
        Log.v(TAG, "remove recorded video " + filename);
        video.delete();
    }

    //Stress test case for record a video and play right away.
    @LargeTest
    public void testStressRecordVideoAndPlayback() throws Exception {
        int iterations = MediaRecorderStressTestRunner.mIterations;
        int video_encoder = MediaRecorderStressTestRunner.mVideoEncoder;
        int audio_encoder = MediaRecorderStressTestRunner.mAudioEncdoer;
        int frame_rate = MediaRecorderStressTestRunner.mFrameRate;
        int video_width = MediaRecorderStressTestRunner.mVideoWidth;
        int video_height = MediaRecorderStressTestRunner.mVideoHeight;
        int bit_rate = MediaRecorderStressTestRunner.mBitRate;
        boolean remove_video = MediaRecorderStressTestRunner.mRemoveVideo;
        int record_duration = MediaRecorderStressTestRunner.mDuration;

        String filename;
        SurfaceHolder mSurfaceHolder;
        mSurfaceHolder = MediaFrameworkTest.mSurfaceView.getHolder();
        File stressOutFile = new File(MEDIA_STRESS_OUTPUT);
        Writer output = new BufferedWriter(
                new FileWriter(stressOutFile, true));
        output.write("Video record and play back stress test:\n");
        output.write("Total number of loops:"
                + NUMBER_OF_RECORDERANDPLAY_STRESS_LOOPS + "\n");
        try {
            output.write("No of loop: ");
            for (int i = 0; i < iterations; i++){
                filename = OUTPUT_FILE + i + OUTPUT_FILE_EXT;
                Log.v(TAG, filename);
                runOnLooper(new Runnable() {
                    @Override
                    public void run() {
                        mRecorder = new MediaRecorder();
                    }
                });
                Log.v(TAG, "iterations : " + iterations);
                Log.v(TAG, "video_encoder : " + video_encoder);
                Log.v(TAG, "audio_encoder : " + audio_encoder);
                Log.v(TAG, "frame_rate : " + frame_rate);
                Log.v(TAG, "video_width : " + video_width);
                Log.v(TAG, "video_height : " + video_height);
                Log.v(TAG, "bit rate : " + bit_rate);
                Log.v(TAG, "record_duration : " + record_duration);

                mRecorder.setOnErrorListener(mRecorderErrorCallback);
                mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                mRecorder.setOutputFile(filename);
                mRecorder.setVideoFrameRate(frame_rate);
                mRecorder.setVideoSize(video_width, video_height);
                mRecorder.setVideoEncoder(video_encoder);
                mRecorder.setAudioEncoder(audio_encoder);
                Log.v(TAG, "mediaRecorder setPreview");
                mRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
                mRecorder.prepare();
                mRecorder.start();
                Thread.sleep(record_duration);
                Log.v(TAG, "Before stop");
                mRecorder.stop();
                mRecorder.release();
                //start the playback
                MediaPlayer mp = new MediaPlayer();
                mp.setDataSource(filename);
                mp.setDisplay(MediaFrameworkTest.mSurfaceView.getHolder());
                mp.prepare();
                mp.start();
                Thread.sleep(record_duration);
                mp.release();
                validateRecordedVideo(filename);
                if (remove_video) {
                    removeRecodedVideo(filename);
                }
                output.write(", " + i);
            }
        } catch (Exception e) {
            assertTrue("record and playback", false);
                Log.v(TAG, e.toString());
        }
        output.write("\n\n");
        output.close();
    }
}
