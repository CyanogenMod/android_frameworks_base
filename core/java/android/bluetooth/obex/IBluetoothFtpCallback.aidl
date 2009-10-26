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

/**
 * System private API for Bluetooth FTP service callbacks (async returns).
 * <p>
 * The BluetoothFtp class method documentation should be used as a reference.
 * Actions resulting from these procedures are broadcast using intents defined
 * in android.bluetooth.obex.BluetoothObexIntent.
 *
 * {@hide}
 */
interface IBluetoothFtpCallback
{
    /**
     * @param isError true if error executing createSession, false otherwise
     */
    void onCreateSessionComplete(in boolean isError);

    /**
     * Notification that this session has been closed.  The client will
     * need to reconnect if future FTP operations are required.
     */
    void onObexSessionClosed();

    /**
     * @param folder name of folder to create
     * @param isError true if error executing createFolder, false otherwise
     */
    void onCreateFolderComplete(in String folder, in boolean isError);

    /**
     * @param folder name of folder to change to
     * @param isError true if error executing changeFolder, false otherwise
     */
    void onChangeFolderComplete(in String folder, in boolean isError);

    /**
     * @param result List of Map object containing information about the current folder contents.
     * <p>
     * <ul>
     *    <li>Name (String): object name
     *    <li>Type (String): object type
     *    <li>Size (int): object size, or number of folder items
     *    <li>Permission (String): group, owner, or other permission
     *    <li>Modified (int): Last change
     *    <li>Accessed (int): Last access
     *    <li>Created (int): Created date
     * </ul>
     * @param isError true if error executing listFolder, false otherwise
     */
    void onListFolderComplete(in List<Map> result, in boolean isError);

    /**
     * @param localFilename Filename on local device
     * @param remoteFilename Filename on remote device
     * @param isError true if error executing getFile, false otherwise
     */
    void onGetFileComplete(in String localFilename, in String remoteFilename, in boolean isError);

    /**
     * @param localFilename Filename on local device
     * @param remoteFilename Filename on remote device
     * @param isError true if error executing putFile, false otherwise
     */
    void onPutFileComplete(in String localFilename, in String remoteFilename, in boolean isError);

    /**
     * @param name name of file/folder to delete
     * @param isError true if error executing delete, false otherwise
     */
    void onDeleteComplete(in String name, in boolean isError);

    /* TODO: update with any new functionality from BM3 obexd */
}
