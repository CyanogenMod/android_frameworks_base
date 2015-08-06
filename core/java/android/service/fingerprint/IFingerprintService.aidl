/*
 * Copyright (C) 2014 The Android Open Source Project
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
package android.service.fingerprint;

import android.os.Bundle;
import android.hardware.fingerprint.Fingerprint;
import android.service.fingerprint.IFingerprintServiceReceiver;

/**
 * Communication channel from client to the fingerprint service.
 * @hide
 */
interface IFingerprintService {
    // Any errors resulting from this call will be returned to the listener
    oneway void authenticate(IBinder token, int userId);

    // Any errors resulting from this call will be returned to the listener
    oneway void enroll(IBinder token, long timeout, int userId);
    
    // Any errors resulting from this call will be returned to the listener
    oneway void cancel(IBinder token, int userId);

    // Any errors resulting from this call will be returned to the listener
    oneway void remove(IBinder token, int fingerprintId, int userId);

    // Start listening for fingerprint events.
    oneway void startListening(IBinder token, IFingerprintServiceReceiver receiver, int userId);

    // Stops listening for fingerprints
    oneway void stopListening(IBinder token, int userId);

    // Get a list of fingerprints
    List<Fingerprint> getEnrolledFingerprints(IBinder token, int userId);

    // Rename fingerprint
    boolean setFingerprintName(IBinder token, int fingerprintId, String name, int userId);

    // Get num of fingerprints samples required to enroll
    int getNumEnrollmentSteps(IBinder token);

    // Return the state of FingerprintService
    int getState();
}
