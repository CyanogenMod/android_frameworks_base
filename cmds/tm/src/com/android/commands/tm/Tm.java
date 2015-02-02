/*
**
** Copyright 2015, The CyanogenMod Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/


package com.android.commands.tm;

import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ParceledListSlice;
import android.content.res.IThemeService;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.ThemesContract;
import android.util.AndroidException;
import com.android.internal.os.BaseCommand;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Tm extends BaseCommand {

    IThemeService mTs;
    IPackageManager mPm;

    /**
     * Command-line entry point.
     *
     * @param args The command-line arguments
     */
    public static void main(String[] args) {
        (new Tm()).run(args);
    }

    public void onShowUsage(PrintStream out) {
        out.println(
                "usage: tm [subcommand] [options]\n" +
                "       tm list\n" +
                "       tm apply [package_name]\n" +
                "\n" +
                "tm list: return a list of theme packages.\n" +
                "\n" +
                "tm apply: applies the theme specified by package_name.\n"
                );
    }

    public void onRun() throws Exception {
        mTs = IThemeService.Stub.asInterface(ServiceManager.getService("themes"));
        if (mTs == null) {
            System.err.println(NO_SYSTEM_ERROR_CODE);
            throw new AndroidException("Can't connect to theme service; is the system running?");
        }

        mPm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
        if (mPm == null) {
            System.err.println(NO_SYSTEM_ERROR_CODE);
            throw new AndroidException("Can't connect to package manager; is the system running?");
        }

        String op = nextArgRequired();

        if (op.equals("list")) {
            runListThemePackages();
        } else if (op.equals("apply")) {
            runApplyTheme();
        } else {
            showError("Error: unknown command '" + op + "'");
            return;
        }
    }

    private void runListThemePackages() throws Exception {
        List<PackageInfo> packages = getInstalledPackages(mPm, 0, UserHandle.USER_OWNER);

        for (PackageInfo info : packages) {
            if (info.isThemeApk) {
                System.out.print("package:");
                System.out.println(info.packageName);
            }
        }
    }

    private void runApplyTheme() throws Exception {
        String pkgName = nextArg();
        if (pkgName == null) {
            System.err.println("Error: didn't specify theme package to apply");
            return;
        }
        PackageInfo info = mPm.getPackageInfo(pkgName, 0, UserHandle.USER_OWNER);
        if (info == null) {
            System.err.println("Error: invalid package name");
            return;
        }
        if (!info.isThemeApk) {
            System.err.println("Error: package is not a theme");
            return;
        }

        Map<String, String> componentMap = new HashMap<String, String>();
        componentMap.put(ThemesContract.ThemesColumns.MODIFIES_OVERLAYS, pkgName);
        componentMap.put(ThemesContract.ThemesColumns.MODIFIES_STATUS_BAR, pkgName);
        componentMap.put(ThemesContract.ThemesColumns.MODIFIES_NAVIGATION_BAR, pkgName);
        mTs.requestThemeChange(componentMap);
    }

    @SuppressWarnings("unchecked")
    private List<PackageInfo> getInstalledPackages(IPackageManager pm, int flags, int userId)
            throws RemoteException {
        ParceledListSlice<PackageInfo> slice = pm.getInstalledPackages(flags, userId);
        return slice.getList();
    }

}
