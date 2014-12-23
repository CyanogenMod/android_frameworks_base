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
 * limitations under the License
 */

package android.telecom;

/**
 * Defines properties of a phone call which may be affected by changes to the call.
 * @hide
 */
public class CallProperties {
    /** Call is currently in a conference call. */
    public static final int CONFERENCE                      = 0x00000001;
    /** Whether the call was forwarded from another party (GSM only) */
    public static final int WAS_FORWARDED                   = 0x00000002;
    /** Whether the call is held remotely */
    public static final int HELD_REMOTELY                   = 0x00000004;
    /** Whether the dialing state is waiting for the busy remote side */
    public static final int DIALING_IS_WAITING              = 0x00000008;
    /** Whether an additional call came in and was forwarded while the call was active */
    public static final int ADDITIONAL_CALL_FORWARDED       = 0x00000010;
    /** Whether incoming calls are barred at the remote side */
    public static final int REMOTE_INCOMING_CALLS_BARRED    = 0x00000020;
}
