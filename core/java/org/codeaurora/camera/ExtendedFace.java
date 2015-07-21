/* Copyright (c) 2015, The Linux Foundataion. All rights reserved.
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
*     * Neither the name of The Linux Foundation nor the names of its
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
