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

package com.android.server.location;

import android.content.Context;
import android.net.Proxy;
import android.net.http.AndroidHttpClient;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.params.ConnRouteParams;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Random;
import java.io.File;
import java.io.FileOutputStream;

/**
 * A class for downloading GPS LTO data.
 *
 * {@hide}
 */
public class GpsLtoDownloader {

    private static final String TAG = "GpsLtoDownloader";
    static final boolean DEBUG = false;
    private Context mContext;
    private String[] mLtoServers;
    // to load balance our server requests
    private int mNextServerIndex;
    private static String mLtoFilePath = "/data/gps/lto.dat";
    private static FileOutputStream mFileOutputStream;

    GpsLtoDownloader(Context context, Properties properties) {
        mContext = context;
        String mPath = properties.getProperty("LTO_PATH");
        if (mPath != null) {
            mLtoFilePath = mPath;
        }
        // read Lto servers from the Properties object
        int count = 0;
        String server1 = properties.getProperty("LTO_SERVER_1");
        String server2 = properties.getProperty("LTO_SERVER_2");
        String server3 = properties.getProperty("LTO_SERVER_3");
        if (server1 != null) count++;
        if (server2 != null) count++;
        if (server3 != null) count++;

        if (count == 0) {
            Log.e(TAG, "No LTO servers were specified in the GPS configuration");
            return;
        } else {
            mLtoServers = new String[count];
            count = 0;
            if (server1 != null) mLtoServers[count++] = server1;
            if (server2 != null) mLtoServers[count++] = server2;
            if (server3 != null) mLtoServers[count++] = server3;

            // randomize first server
            Random random = new Random();
            mNextServerIndex = random.nextInt(count);
        }
    }

    boolean downloadLtoData() {
        String proxyHost = Proxy.getHost(mContext);
        int proxyPort = Proxy.getPort(mContext);
        boolean useProxy = (proxyHost != null && proxyPort != -1);
        byte[] result = null;
        int startIndex = mNextServerIndex;

        if (mLtoFilePath == null) {
            return false;
        }

        if (mLtoServers == null) {
            return false;
        }

        // load balance our requests among the available servers
        while (result == null) {
            result = doDownload(mLtoServers[mNextServerIndex], useProxy, proxyHost, proxyPort);
            // increment mNextServerIndex and wrap around if necessary
            mNextServerIndex++;
            if (mNextServerIndex == mLtoServers.length) {
                mNextServerIndex = 0;
            }
            // break if we have tried all the servers
            if (mNextServerIndex == startIndex) break;
        }
        if (result != null) {
            return saveDownload(mLtoFilePath, result);
        } else {
            return false;
        }
    }

    protected static byte[] doDownload(String url, boolean isProxySet,
            String proxyHost, int proxyPort) {
        if (DEBUG) Log.d(TAG, "Downloading LTO data from " + url);

        AndroidHttpClient client = null;
        try {
            client = AndroidHttpClient.newInstance("Android");
            HttpUriRequest req = new HttpGet(url);

            if (isProxySet) {
                HttpHost proxy = new HttpHost(proxyHost, proxyPort);
                ConnRouteParams.setDefaultProxy(req.getParams(), proxy);
            }

            req.addHeader(
                    "Accept",
                    "*/*, application/vnd.wap.mms-message, application/vnd.wap.sic");

            req.addHeader(
                    "x-wap-profile",
                    "http://www.openmobilealliance.org/tech/profiles/UAPROF/ccppschema-20021212#");

            HttpResponse response = client.execute(req);
            StatusLine status = response.getStatusLine();
            if (status.getStatusCode() != 200) { // HTTP 200 is success.
                if (DEBUG) Log.d(TAG, "HTTP error: " + status.getReasonPhrase());
                return null;
            }

            HttpEntity entity = response.getEntity();
            byte[] body = null;
            if (entity != null) {
                try {
                    if (entity.getContentLength() > 0) {
                        body = new byte[(int) entity.getContentLength()];
                        DataInputStream dis = new DataInputStream(entity.getContent());
                        try {
                            dis.readFully(body);
                        } finally {
                            try {
                                dis.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Unexpected IOException.", e);
                            }
                        }
                    }
                } finally {
                    if (entity != null) {
                        entity.consumeContent();
                    }
                }
            }
            return body;
        } catch (Exception e) {
            if (DEBUG) Log.d(TAG, "error " + e);
        } finally {
            if (client != null) {
                client.close();
            }
        }
        return null;
    }

    private static boolean saveDownload(String path, byte[] data) {
        try {
            mFileOutputStream = new FileOutputStream(new File(path));
            mFileOutputStream.write(data);
            if (mFileOutputStream != null) {
                mFileOutputStream.flush();
                mFileOutputStream.close();
            }
            return true;
        } catch (IOException localIOException) {
            localIOException.printStackTrace();
            return false;
        }
    }
}
