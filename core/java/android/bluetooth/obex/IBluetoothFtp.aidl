/*
 * Copyright (c) 2009, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the name of Code Aurora nor
 *      the names of its contributors may be used to endorse or promote
 *      products derived from this software without specific prior written
 *      permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package android.bluetooth.obex;

import android.bluetooth.obex.IBluetoothFtpCallback;

/**
 * System private API for talking with the Bluetooth FTP service.
 * <p>
 * The BluetoothFtp class method documentation should be used as a reference.
 * Actions resulting from these procedures are broadcast using intents defined
 * in android.bluetooth.obex.BluetoothObexIntent.
 *
 * {@hide}
 */
interface IBluetoothFtp
{
    boolean createSession(in String address, in IBluetoothFtpCallback callback); // async
    boolean closeSession(in String address);
    boolean isConnectionActive(in String address);

    boolean changeFolder(in String address, in String folder); // async
    boolean createFolder(in String address, in String folder); // async
    boolean delete(in String address, in String name); // async
    boolean listFolder(in String address); // async

    boolean getFile(in String address, in String localFilename, in String remoteFilename); // async
    boolean putFile(in String address, in String localFilename, in String remoteFilename); // async
    boolean cancelTransfer(in String address, in String name);
    boolean isTransferActive(in String filename);
}
