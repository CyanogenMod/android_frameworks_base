/*
 * Copyright (c) 2012-2014 The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2012 The Android Open Source Project
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
 *
 */

package org.codeaurora.camera;

import android.hardware.Camera;

import java.util.ArrayList;

import android.os.Bundle;

import android.os.SystemProperties;

/**
 * {@hide} Information about a face identified through Extended camera face
 *
 * <p>
 * When face detection is used with a camera, the {@link FaceDetectionListener}
 * returns a list of face objects for use in focusing and metering.
 * </p>
 *
 * @see FaceDetectionListener
 */
public class ExtendedFace extends android.hardware.Camera.Face {
    public ExtendedFace() {
        super();
    }

    private int smileDegree = 0;
    private int smileScore = 0;
    private int blinkDetected = 0;
    private int faceRecognized = 0;
    private int gazeAngle = 0;
    private int updownDir = 0;
    private int leftrightDir = 0;
    private int rollDir = 0;
    private int leyeBlink = 0;
    private int reyeBlink = 0;
    private int leftrightGaze = 0;
    private int topbottomGaze = 0;

    private static final String STR_TRUE = "true";
    private static final String STR_FALSE = "false";

    /**
     * The smilie degree for the detection of the face.
     *
     * @see #startFaceDetection()
     */
    public int getSmileDegree() {
        return smileDegree;
    }

    /**
     * The smilie score for the detection of the face.
     *
     * @see #startFaceDetection()
     */
    public int getSmileScore() {
        return smileScore;
    }

    /**
     * The smilie degree for the detection of the face.
     *
     * @see #startFaceDetection()
     */
    public int getBlinkDetected() {
        return blinkDetected;
    }

    /**
     * If face is recognized.
     *
     * @see #startFaceDetection()
     */
    public int getFaceRecognized() {
        return faceRecognized;
    }

    /**
     * The gaze angle for the detected face.
     *
     * @see #startFaceDetection()
     */
    public int getGazeAngle() {
        return gazeAngle;
    }

    /**
     * The up down direction for the detected face.
     *
     * @see #startFaceDetection()
     */
    public int getUpDownDirection() {
        return updownDir;
    }

    /**
     * The left right direction for the detected face.
     *
     * @see #startFaceDetection()
     */
    public int getLeftRightDirection() {
        return leftrightDir;
    }

    /**
     * The roll direction for the detected face.
     *
     * @see #startFaceDetection()
     */
    public int getRollDirection() {
        return rollDir;
    }

    /**
     * The degree of left eye blink for the detected face.
     *
     * @see #startFaceDetection()
     */
    public int getLeftEyeBlinkDegree() {
        return leyeBlink;
    }

    /**
     * The degree of right eye blink for the detected face.
     *
     * @see #startFaceDetection()
     */
    public int getRightEyeBlinkDegree() {
        return reyeBlink;
    }

    /**
     * The gaze degree of left-right direction for the detected face.
     *
     * @see #startFaceDetection()
     */
    public int getLeftRightGazeDegree() {
        return leftrightGaze;
    }

    /**
     * The gaze degree of up-down direction for the detected face.
     *
     * @see #startFaceDetection()
     */
    public int getTopBottomGazeDegree() {
        return topbottomGaze;
    }

    private static final String BUNDLE_KEY_SMILE_SCORE = "smileScore";
    private static final String BUNDLE_KEY_SMILE_VALUE = "smileValue";
    private static final String BUNDLE_KEY_BLINK_DETECTED = "blinkDetected";
    private static final String BUNDLE_KEY_LEFT_EYE_CLOSED_VALUE = "leftEyeClosedValue";
    private static final String BUNDLE_KEY_RIGHT_EYE_CLOSED_VALUE = "rightEyeClosedValue";
    private static final String BUNDLE_KEY_FACE_PITCH_DEGREE = "facePitchDegree";
    private static final String BUNDLE_KEY_FACE_YAW_DEGREE = "faceYawDegree";
    private static final String BUNDLE_KEY_FACE_ROLL_DEGREE = "faceRollDegree";
    private static final String BUNDLE_KEY_GAZE_UP_DOWN_DEGREE = "gazeUpDownDegree";
    private static final String BUNDLE_KEY_GAZE_LEFT_RIGHT_DEGREE = "gazeLeftRightDegree";
    private static final String BUNDLE_KEY_FACE_RECOGNIZED = "faceRecognized";

    public Bundle getExtendedFaceInfo() {
        Bundle faceInfo = new Bundle();
        faceInfo.putInt(BUNDLE_KEY_SMILE_VALUE, this.smileDegree);

        faceInfo.putInt(BUNDLE_KEY_LEFT_EYE_CLOSED_VALUE, this.leyeBlink);
        faceInfo.putInt(BUNDLE_KEY_RIGHT_EYE_CLOSED_VALUE, this.reyeBlink);

        faceInfo.putInt(BUNDLE_KEY_FACE_PITCH_DEGREE, this.updownDir);
        faceInfo.putInt(BUNDLE_KEY_FACE_YAW_DEGREE, this.leftrightDir);
        faceInfo.putInt(BUNDLE_KEY_FACE_ROLL_DEGREE, this.rollDir);
        faceInfo.putInt(BUNDLE_KEY_GAZE_UP_DOWN_DEGREE, this.topbottomGaze);
        faceInfo.putInt(BUNDLE_KEY_GAZE_LEFT_RIGHT_DEGREE, this.leftrightGaze);

        faceInfo.putInt(BUNDLE_KEY_BLINK_DETECTED, this.blinkDetected);
        faceInfo.putInt(BUNDLE_KEY_SMILE_SCORE, this.smileScore);
        faceInfo.putInt(BUNDLE_KEY_FACE_RECOGNIZED, this.faceRecognized);

        return faceInfo;
    }

}
