/*
 *Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 *Redistribution and use in source and binary forms, with or without
 *modification, are permitted provided that the following conditions are
 *met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 *THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 *WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 *ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 *BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 *BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 *WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 *OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 *IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.server;

import dalvik.system.PathClassLoader;

import java.lang.reflect.Constructor;

import android.util.Slog;
import android.net.Network;
import android.net.NetworkStats;
import android.util.Log;

public class NetPluginDelegate {

    private static final String TAG = "ConnectivityExtension";
    private static final boolean LOGV = false;

    private static Class tetherExtensionClass = null;
    private static Object tetherExtensionObj = null;

    public static void getTetherStats(NetworkStats uidStats, NetworkStats devStats,
            NetworkStats xtStats) {
        if (LOGV) Slog.v(TAG, "getTetherStats() E");
        loadTetherExtJar();
        try {
            tetherExtensionClass.getMethod("getTetherStats", NetworkStats.class,
                    NetworkStats.class, NetworkStats.class).invoke(tetherExtensionObj, uidStats,
                    devStats, xtStats);
        } catch (Exception e) {
            e.printStackTrace();
            Log.w(TAG, "error in invoke method");
        }
        if (LOGV) Slog.v(TAG, "getTetherStats() X");
    }

    public static void setQuota(String iface, long quota) {
        if (LOGV) Slog.v(TAG, "setQuota(" + iface + ", " + quota + ") E");
        loadTetherExtJar();
        try {
            tetherExtensionClass.getMethod("setQuota", String.class, long.class).invoke(
                    tetherExtensionObj, iface, quota);
        } catch (Exception ex) {
            Log.w(TAG, "Error calling setQuota Method on extension jar");
        }
        if (LOGV) Slog.v(TAG, "setQuota(" + iface + ", " + quota + ") X");
    }

    public static void setUpstream(Network net) {
        if (LOGV) Slog.v(TAG, "setUpstream(" + net + ") E");
        loadTetherExtJar();
        try {
            tetherExtensionClass.getMethod("setUpstream", Network.class).invoke(
                    tetherExtensionObj, net);
        } catch (Exception ex) {
            Log.w(TAG, "Error calling setUpstream Method on extension jar");
        }
        if (LOGV) Slog.v(TAG, "setUpstream(" + net + ") E");
    }


    private static void loadTetherExtJar() {
        final String realProvider = "com.qualcomm.qti.tetherstatsextension.TetherStatsReporting";
        final String realProviderPath = "/system/framework/ConnectivityExt.jar";
            if (tetherExtensionClass == null && tetherExtensionObj == null) {
                if (LOGV) Slog.v(TAG, "loading ConnectivityExt jar");
                try {

                    PathClassLoader classLoader = new PathClassLoader(realProviderPath,
                            ClassLoader.getSystemClassLoader());

                    tetherExtensionClass = classLoader.loadClass(realProvider);
                    tetherExtensionObj = tetherExtensionClass.newInstance();
                        if (LOGV)
                            Slog.v(TAG, "ConnectivityExt jar loaded");
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.w(TAG, "unable to ConnectivityExt jar");
                }
        }
    }
}
