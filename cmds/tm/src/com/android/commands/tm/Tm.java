/*
**
** Copyright 2015, The CyanogenMod Project
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
import android.content.pm.ThemeUtils;
import android.content.res.IThemeService;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.AndroidException;
import com.android.internal.os.BaseCommand;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Tm extends BaseCommand {
    private static final String SYSTEM_THEME = "system";

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
        List<String> components = ThemeUtils.getAllComponents();
        StringBuilder sb = new StringBuilder();
        sb.append("usage: tm [subcommand] [options]\n");
        sb.append("       tm list\n");
        sb.append("       tm apply <PACKAGE_NAME> [-c <COMPONENT> [-c <COMPONENT>] ...]\n");
        sb.append("       tm rebuild\n");
        sb.append("       tm process <PACKAGE_NAME>\n");
        sb.append("\n");
        sb.append("tm list: return a list of theme packages.\n");
        sb.append("\n");
        sb.append("tm apply: applies the components for the theme specified by PACKAGE_NAME.\n");
        sb.append("       [-c <COMPONENT> [-c <COMPONENT>] ...]\n");
        sb.append("       if no components are specified all components will be applied.\n");
        sb.append("       Valid components are:\n");
        for (String component : components) {
            sb.append("           ");
            sb.append(component);
            sb.append("\n");
        }
        sb.append("\n");
        sb.append("tm rebuild: rebuilds the resource cache.\n");
        sb.append("\n");
        sb.append("tm process: processes the theme resources for the theme specified by " +
                "PACKAGE_NAME.\n");

        out.println(sb.toString());
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
        } else if (op.equals("rebuild")) {
            runRebuildResourceCache();
        } else if (op.equals("process")) {
            runProcessTheme();
        } else {
            showError("Error: unknown command '" + op + "'");
            return;
        }
    }

    private void runListThemePackages() throws Exception {
        List<PackageInfo> packages = getInstalledPackages(mPm, 0, UserHandle.USER_OWNER);

        // there is always a "system" theme available
        System.out.println("package:system [theme]");
        for (PackageInfo info : packages) {
            if (info.isThemeApk || info.isLegacyIconPackApk) {
                System.out.print("package:");
                System.out.print(info.packageName);
                if (info.isThemeApk) {
                    System.out.println(" [theme]");
                } else {
                    System.out.println(" [icon pack]");
                }
            }
        }
    }

    private void runApplyTheme() throws Exception {
        String pkgName = nextArg();
        if (pkgName == null) {
            System.err.println("Error: didn't specify theme package to apply");
            return;
        }
        if (!SYSTEM_THEME.equals(pkgName)) {
            PackageInfo info = mPm.getPackageInfo(pkgName, 0, UserHandle.USER_OWNER);
            if (info == null) {
                System.err.println("Error: invalid package name");
                return;
            }
            if (!(info.isThemeApk || info.isLegacyIconPackApk)) {
                System.err.println("Error: package is not a theme or icon pack");
                return;
            }
        }

        Map<String, String> componentMap = new HashMap<String, String>();
        String opt;
        while ((opt=nextOption()) != null) {
            if (opt.equals("-c")) {
                componentMap.put(nextArgRequired(), pkgName);
            }
        }

        // No components specified so let's just try and apply EVERYTHING!
        if (componentMap.size() == 0) {
            List<String> components = ThemeUtils.getAllComponents();
            for (String component : components) {
                componentMap.put(component, pkgName);
            }
        }
        mTs.requestThemeChange(componentMap);
    }

    private void runRebuildResourceCache() throws Exception {
        mTs.rebuildResourceCache();
    }

    private void runProcessTheme() throws Exception {
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

        mTs.processThemeResources(pkgName);
    }

    @SuppressWarnings("unchecked")
    private List<PackageInfo> getInstalledPackages(IPackageManager pm, int flags, int userId)
            throws RemoteException {
        ParceledListSlice<PackageInfo> slice = pm.getInstalledPackages(flags, userId);
        return slice.getList();
    }

}
