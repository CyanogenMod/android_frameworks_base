/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
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
 */

package com.android.internal.util.wifi;

import android.content.Context;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.InterruptedException;
import java.lang.Process;
import java.lang.Runtime;
import java.util.ArrayList;

public class ClientsList {

    private static final String TAG = "ClientsList";

    /**
     * Gets a list of the clients connected to the Hotspot, reachable deadline(-w) is 3(sec)
     *
     * @param onlyReachables {@code false} if the list should contain unreachable
     *                       (probably disconnected) clients, {@code true} otherwise
     * @return ArrayList of {@link ClientScanResult}
     */
    public static ArrayList<ClientScanResult> get(boolean onlyReachables, Context context) {
        BufferedReader br = null;
        ArrayList<ClientScanResult> result = new ArrayList<ClientScanResult>();

        try {
            br = new BufferedReader(new FileReader("/proc/net/arp"));
            String line;

            while ((line = br.readLine()) != null) {
                String[] splitted = line.split(" +");

                if (splitted.length >= 6) {
                    // Basic sanity check
                    String mac = splitted[3];

                    if (mac.matches("..:..:..:..:..:..")) {
                        boolean isReachable = false;
                        if (onlyReachables) {
                           isReachable = isReachableByPing(splitted[0]);
                        }
                        if (!onlyReachables || (onlyReachables && isReachable)) {
                            ClientScanResult client = new ClientScanResult();
                            client.ipAddr = splitted[0];
                            if (mac.equals("00:00:00:00:00:00")) {
                                client.hwAddr = "---:---:---:---:---:---";
                            } else {
                                client.hwAddr = mac;
                            }
                            client.device = splitted[5];
                            client.isReachable = isReachable;
                            client.vendor = getVendor(mac, context);
                            result.add(client);
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            Log.d(TAG, "catch FileNotFoundException hit in run", e);
        } catch (IOException e) {
            Log.d(TAG, "catch IOException hit in run", e);
        } catch (XmlPullParserException e) {
            Log.d(TAG, "catch XmlPullParserException hit in run", e);
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        }

        return result;
    }

    public static String getVendor(String mac, Context context)
            throws XmlPullParserException, IOException {
        class Item {
            public String mac;
            public String vendor;
        }
        String[] macS = mac.split(":");
        mac = macS[0] + ":" + macS[1] + ":" + macS[2];
        XmlPullParser parser = context.getResources().getXml(com.android.internal.R.xml.vendors);

        int eventType = parser.getEventType();
        Item currentProduct = null;

        while (eventType != XmlPullParser.END_DOCUMENT) {
            String name;
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    name = parser.getName();
                    if (name.equals("item")) {
                        currentProduct = new Item();
                    } else if (currentProduct != null) {
                        if (name.equals("item-mac")) {
                            currentProduct.mac = parser.nextText();
                        } else if (name.equals("item-vendor")) {
                            currentProduct.vendor = parser.nextText();
                        }
                    }
                    break;
                case XmlPullParser.END_TAG:
                    name = parser.getName();
                    if (name.equalsIgnoreCase("item") && currentProduct != null) {
                        if (currentProduct.mac.equalsIgnoreCase(mac)) return currentProduct.vendor;
                    }
            }
            eventType = parser.next();
        }
        return "";
    }

    private static boolean isReachableByPing(String client) {
        try {
            Runtime runtime = Runtime.getRuntime();
            Process mIpAddrProcess = runtime.exec("/system/bin/ping -c 1 -w 3 " + client);
            int mExitValue = mIpAddrProcess.waitFor();
            return (mExitValue == 0);
        } catch (InterruptedException e) {
            // Ignore
        } catch (IOException e) {
            // Ignore
        }
        return false;
    }

    public static class ClientScanResult {
        public String ipAddr;
        public String hwAddr;
        public String device;
        public String vendor;
        public boolean isReachable;
    }
}
