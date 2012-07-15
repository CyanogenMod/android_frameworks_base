/******************************************************************************
 * Class       : HttpConnectHelper.java                                                           *
 * Main Weather activity, in this demo apps i use API from yahoo, you can     *
 * use other weather web service which you prefer                             *
 *                                                                            *
 * Version     : v1.0                                                         *
 * Date        : May 09, 2011                                                 *
 * Copyright (c)-2011 DatNQ some right reserved                               *
 * You can distribute, modify or what ever you want but WITHOUT ANY WARRANTY  *
 * Be honest by keep credit of this file                                      *
 *                                                                            *
 * If you have any concern, feel free to contact with me via email, i will    *
 * check email in free time                                                   * 
 * Email: nguyendatnq@gmail.com                                               *
 * ---------------------------------------------------------------------------*
 * Modification Logs:                                                         *
 *   KEYCHANGE  DATE          AUTHOR   DESCRIPTION                            *
 * ---------------------------------------------------------------------------*
 *    -------   May 09, 2011  DatNQ    Create new                             *
 ******************************************************************************/

/**
 * Modification into Android-internal HttpRetreiver.java
 * Copyright (C) 2012 The AOKP Project
 */


package com.android.internal.util.weather;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

import android.util.Log;

public class HttpRetriever {

    private final String TAG = getClass().getSimpleName();
    private DefaultHttpClient client = new DefaultHttpClient();
    private HttpURLConnection httpConnection;

    public String retrieve(String url) {
        HttpGet get = new HttpGet(url);
        try {
            HttpResponse getResponse = client.execute(get);
            HttpEntity getResponseEntity = getResponse.getEntity();
            if (getResponseEntity != null) {
                String response = EntityUtils.toString(getResponseEntity);
                return response;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void requestConnectServer(String strURL) throws IOException {
        httpConnection = (HttpURLConnection) new URL(strURL).openConnection();
        httpConnection.connect();

        if (httpConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            Log.e(TAG, "Something wrong with connection");
            httpConnection.disconnect();
            throw new IOException("Error in connection: " + httpConnection.getResponseCode());
        }
    }

    private void requestDisconnect() {
        if (httpConnection != null) {
            httpConnection.disconnect();
        }
    }

    public Document getDocumentFromURL(String strURL) throws IOException {
        if (strURL == null) {
            Log.e(TAG, "Invalid input URL");
            return null;
        }

        // Connect to server, get data and close
        requestConnectServer(strURL);
        String strDocContent = getDataFromConnection();
        requestDisconnect();

        if (strDocContent == null) {
            Log.e(TAG, "Cannot get XML content");
            return null;
        }

        int strContentSize = strDocContent.length();
        StringBuffer strBuff = new StringBuffer();
        strBuff.setLength(strContentSize + 1);
        strBuff.append(strDocContent);
        ByteArrayInputStream is = new ByteArrayInputStream(strDocContent.getBytes());

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db;
        Document docData = null;

        try {
            db = dbf.newDocumentBuilder();
            docData = db.parse(is);
        } catch (Exception e) {
            Log.e(TAG, "Parser data error");
            return null;
        }
        return docData;
    }

    private String getDataFromConnection() throws IOException {
        if (httpConnection == null) {
            Log.e(TAG, "Connection is null");
            return null;
        }

        String strValue = null;
        InputStream inputStream = httpConnection.getInputStream();
        if (inputStream == null) {
            Log.e(TAG, "Input stream error");
            return null;
        }

        StringBuffer strBuf = new StringBuffer();
        BufferedReader buffReader = new BufferedReader(new InputStreamReader(inputStream));
        String strLine = "";

        while ((strLine = buffReader.readLine()) != null) {
            strBuf.append(strLine + "\n");
            strValue += strLine + "\n";
        }

        // Release resource to system
        buffReader.close();
        inputStream.close();
        return strBuf.toString();
    }
}
