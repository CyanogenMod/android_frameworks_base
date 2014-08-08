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

import android.util.Log;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class ClientsList {

    private static final String TAG = "ClientsList";
    private static ClientsList instance = null;

    public static class ClientScanResult {
        public String ipAddr;
        public String hwAddr;
        public String device;
        public boolean isReachable;
    }

    protected ClientsList() {
    }

    public static ClientsList getInstance() {
        if (instance == null) {
            instance = new ClientsList();
        }
        return instance;
    }

    /**
     * Gets a list of the clients connected to the Hotspot, reachable timeout is 3000
     *
     * @param onlyReachables {@code false} if the list should contain unreachable
     *                       (probably disconnected) clients, {@code true} otherwise
     * @return ArrayList of {@link ClientScanResult}
     */
    public ArrayList<ClientScanResult> getClientList(boolean onlyReachables) {
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
                        InetAddress address = InetAddress.getByName(splitted[0]);
                        boolean isReachable = address.isReachable(3000);

                        if (!onlyReachables || isReachable) {
                            ClientScanResult client = new ClientScanResult();
                            client.ipAddr = splitted[0];
                            client.hwAddr = mac;
                            client.device = splitted[5];
                            client.isReachable = isReachable;
                            result.add(client);
                        }
                    }
                }
            }
        } catch (UnknownHostException e) {
            Log.d(TAG, "catch UnknownHostException hit in run", e);
        } catch (FileNotFoundException e) {
            Log.d(TAG, "catch FileNotFoundException hit in run", e);
        } catch (IOException e) {
            Log.d(TAG, "catch IOException hit in run", e);
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
}
