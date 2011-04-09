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

package com.android.mediaframeworktest.unit;

import android.media.MediaRecorder;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.Suppress;

/**
 * Unit test class to test the set of valid and invalid states that
 * MediaRecorder.setOutputFormat() method can be called.
 */
public class MediaRecorderSetOutputFormatStateUnitTest extends AndroidTestCase implements MediaRecorderMethodUnderTest {
    private MediaRecorderStateUnitTestTemplate mTestTemplate = new MediaRecorderStateUnitTestTemplate();

    /**
     * 1. It is valid to call setOutputFormat() in the following states:
     *    {Initialized}.
     * 2. It is invalid to call setOutputFormat() in the following states:
     *    {Initial, Prepared, DataSourceConfigured, Recording, Error}
     *
     * @param stateErrors the MediaRecorderStateErrors to check against.
     */
    public void checkStateErrors(MediaRecorderStateErrors stateErrors) {
        // Valid states.
        assertTrue(!stateErrors.errorInInitializedState);

        // Invalid states.
        assertTrue(stateErrors.errorInInitialState);
        assertTrue(stateErrors.errorInInitialStateAfterReset);
        assertTrue(stateErrors.errorInInitialStateAfterStop);
        assertTrue(stateErrors.errorInPreparedState);
        assertTrue(stateErrors.errorInRecordingState);
        assertTrue(stateErrors.errorInErrorState);
        assertTrue(stateErrors.errorInDataSourceConfiguredState);
    }

    public void invokeMethodUnderTest(MediaRecorder recorder) {
        recorder.setOutputFormat(MediaRecorderStateUnitTestTemplate.OUTPUT_FORMAT);
    }

    @MediumTest
    public void testSetOutputFormat() {
        mTestTemplate.runTestOnMethod(this);
    }

    @Override
    public String toString() {
        return "setOutputFormat()";
    }
}
