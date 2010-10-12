/*
 * Copyright (c) 2009, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *        * Redistributions of source code must retain the above copyright
 *            notice, this list of conditions and the following disclaimer.
 *        * Redistributions in binary form must reproduce the above copyright
 *            notice, this list of conditions and the following disclaimer in the
 *            documentation and/or other materials provided with the distribution.
 *        * Neither the name of Code Aurora nor
 *            the names of its contributors may be used to endorse or promote
 *            products derived from this software without specific prior written
 *            permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.    IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package android.hardware.fmradio;


class FmReceiverJNI {
    /**
     * General success
     */
    static final int FM_JNI_SUCCESS = 0;

    /**
     * General failure
     */
    static final int FM_JNI_FAILURE = -1;

    /**
     * native method: Open device
     * @return The file descriptor of the device
     *
     */
    static native int acquireFdNative(String path);


    /**
     * native method:
     * @param fd
     * @param control
     * @param field
     * @return
     */
    static native int audioControlNative(int fd, int control, int field);

    /**
     * native method: cancels search
     * @param fd file descriptor of device
     * @return May return
     *             {@link #FM_JNI_SUCCESS}
     *             {@link #FM_JNI_FAILURE}
     */
    static native int cancelSearchNative(int fd);

    /**
     * native method: release control of device
     * @param fd file descriptor of device
     * @return May return
     *             {@link #FM_JNI_SUCCESS}
     *             {@link #FM_JNI_FAILURE}
     */
    static native int closeFdNative(int fd);

    /**
     * native method: get frequency
     * @param fd file descriptor of device
     * @return Returns frequency in int form
     */
    static native int getFreqNative(int fd);

    /**
     * native method: set frequency
     * @param fd file descriptor of device
     * @param freq freq to be set in int form
     * @return {@link #FM_JNI_SUCCESS}
     *         {@link #FM_JNI_FAILURE}
     *
     */
    static native int setFreqNative(int fd, int freq);

    /**
     * native method: get v4l2 control
     * @param fd file descriptor of device
     * @param id v4l2 id to be retrieved
     * @return Returns current value of the
     *         v4l2 control
     */
    static native int getControlNative (int fd, int id);

    /**
     * native method: set v4l2 control
     * @param fd file descriptor of device
     * @param id v4l2 control to be set
     * @param value value to be set
     * @return {@link #FM_JNI_SUCCESS}
     *         {@link #FM_JNI_FAILURE}
     */
    static native int setControlNative (int fd, int id, int value);

    /**
     * native method: start search
     * @param fd file descriptor of device
     * @param dir search direction
     * @return {@link #FM_JNI_SUCCESS}
     *         {@link #FM_JNI_FAILURE}
     */
    static native int startSearchNative (int fd, int dir);

    /**
     * native method: get buffer
     * @param fd file descriptor of device
     * @param buff[] buffer
     * @param index index of the buffer to be retrieved
     * @return {@link #FM_JNI_SUCCESS}
     *         {@link #FM_JNI_FAILURE}
     */
    static native int getBufferNative (int fd, byte  buff[], int index);

    /**
     * native method: get RSSI value of the
     *                received signal
     * @param fd file descriptor of device
     * @return Returns signal strength in int form
     *         Signal value range from -120 to 10
     */
    static native int getRSSINative (int fd);

    /**
     * native method: set FM band
     * @param fd file descriptor of device
     * @param low lower band
     * @param high higher band
     * @return {@link #FM_JNI_SUCCESS}
     *         {@link #FM_JNI_FAILURE}
     */
    static native int setBandNative (int fd, int low, int high);

    /**
     * native method: get lower band
     * @param fd file descriptor of device
     * @return Returns lower band in int form
     */
    static native int getLowerBandNative (int fd);

    /**
     * native method: force Mono/Stereo mode
     * @param fd file descriptor of device
     * @param val force mono/stereo indicator
     * @return {@link #FM_JNI_SUCCESS}
     *         {@link #FM_JNI_FAILURE}
     */
    static native int setMonoStereoNative (int fd, int val);

    /**
     * native method: get Raw RDS data
     * @param fd file descriptor of device
     * @param buff[] buffer
     * @param count number of bytes to be read
     * @return Returns number of bytes read
     */
    static native int getRawRdsNative (int fd, byte  buff[], int count);
}
