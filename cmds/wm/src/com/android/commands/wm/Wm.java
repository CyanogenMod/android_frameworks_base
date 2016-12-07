/*
**
** Copyright 2013, The Android Open Source Project
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


package com.android.commands.wm;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.AndroidException;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.IWindowManager;
import com.android.internal.os.BaseCommand;

import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Wm extends BaseCommand {

    private IWindowManager mWm;

    /**
     * Command-line entry point.
     *
     * @param args The command-line arguments
     */
    public static void main(String[] args) {
        (new Wm()).run(args);
    }

    @Override
    public void onShowUsage(PrintStream out) {
        out.println(
                "usage: wm [subcommand] [options]\n" +
                "       wm size [reset|WxH|WdpxHdp]\n" +
                "       wm density [reset|DENSITY]\n" +
                "       wm overscan [reset|LEFT,TOP,RIGHT,BOTTOM]\n" +
                "       wm scaling [off|auto]\n" +
                "       wm screen-capture [userId] [true|false]\n" +
                "\n" +
                "wm size: return or override display size.\n" +
                "         width and height in pixels unless suffixed with 'dp'.\n" +
                "\n" +
                "wm density: override display density.\n" +
                "\n" +
                "wm overscan: set overscan area for display.\n" +
                "\n" +
                "wm scaling: set display scaling mode.\n" +
                "\n" +
                "wm screen-capture: enable/disable screen capture.\n" +
                "\n" +
                "wm dismiss-keyguard: dismiss the keyguard, prompting the user for auth if " +
                "necessary.\n"
                );
    }

    @Override
    public void onRun() throws Exception {
        mWm = IWindowManager.Stub.asInterface(ServiceManager.checkService(
                        Context.WINDOW_SERVICE));
        if (mWm == null) {
            System.err.println(NO_SYSTEM_ERROR_CODE);
            throw new AndroidException("Can't connect to window manager; is the system running?");
        }

        String op = nextArgRequired();

        if (op.equals("size")) {
            runDisplaySize();
        } else if (op.equals("density")) {
            runDisplayDensity();
        } else if (op.equals("overscan")) {
            runDisplayOverscan();
        } else if (op.equals("scaling")) {
            runDisplayScaling();
        } else if (op.equals("screen-capture")) {
            runSetScreenCapture();
        } else if (op.equals("dismiss-keyguard")) {
            runDismissKeyguard();
        } else {
            showError("Error: unknown command '" + op + "'");
            return;
        }
    }

    private void runSetScreenCapture() throws Exception {
        String userIdStr = nextArg();
        String enableStr = nextArg();
        int userId;
        boolean disable;

        try {
            userId = Integer.parseInt(userIdStr);
        } catch (NumberFormatException e) {
            System.err.println("Error: bad number " + e);
            return;
        }

        disable = !Boolean.parseBoolean(enableStr);

        try {
            mWm.setScreenCaptureDisabled(userId, disable);
        } catch (RemoteException e) {
            System.err.println("Error: Can't set screen capture " + e);
        }
    }

    private void runDisplaySize() throws Exception {
        String size = nextArg();
        int w, h;
        if (size == null) {
            Point initialSize = new Point();
            Point baseSize = new Point();
            try {
                mWm.getInitialDisplaySize(Display.DEFAULT_DISPLAY, initialSize);
                mWm.getBaseDisplaySize(Display.DEFAULT_DISPLAY, baseSize);
                System.out.println("Physical size: " + initialSize.x + "x" + initialSize.y);
                if (!initialSize.equals(baseSize)) {
                    System.out.println("Override size: " + baseSize.x + "x" + baseSize.y);
                }
            } catch (RemoteException e) {
            }
            return;
        } else if ("reset".equals(size)) {
            w = h = -1;
        } else {
            int div = size.indexOf('x');
            if (div <= 0 || div >= (size.length()-1)) {
                System.err.println("Error: bad size " + size);
                return;
            }
            String wstr = size.substring(0, div);
            String hstr = size.substring(div+1);
            try {
                w = parseDimension(wstr);
                h = parseDimension(hstr);
            } catch (NumberFormatException e) {
                System.err.println("Error: bad number " + e);
                return;
            }
        }

        try {
            if (w >= 0 && h >= 0) {
                // TODO(multidisplay): For now Configuration only applies to main screen.
                mWm.setForcedDisplaySize(Display.DEFAULT_DISPLAY, w, h);
            } else {
                mWm.clearForcedDisplaySize(Display.DEFAULT_DISPLAY);
            }
        } catch (RemoteException e) {
        }
    }

    private void runDisplayDensity() throws Exception {
        String densityStr = nextArg();
        int density;
        if (densityStr == null) {
            try {
                int initialDensity = mWm.getInitialDisplayDensity(Display.DEFAULT_DISPLAY);
                int baseDensity = mWm.getBaseDisplayDensity(Display.DEFAULT_DISPLAY);
                System.out.println("Physical density: " + initialDensity);
                if (initialDensity != baseDensity) {
                    System.out.println("Override density: " + baseDensity);
                }
            } catch (RemoteException e) {
            }
            return;
        } else if ("reset".equals(densityStr)) {
            density = -1;
        } else {
            try {
                density = Integer.parseInt(densityStr);
            } catch (NumberFormatException e) {
                System.err.println("Error: bad number " + e);
                return;
            }
            if (density < 72) {
                System.err.println("Error: density must be >= 72");
                return;
            }
        }

        try {
            if (density > 0) {
                // TODO(multidisplay): For now Configuration only applies to main screen.
                mWm.setForcedDisplayDensityForUser(Display.DEFAULT_DISPLAY, density,
                        UserHandle.USER_CURRENT);
            } else {
                mWm.clearForcedDisplayDensityForUser(Display.DEFAULT_DISPLAY,
                        UserHandle.USER_CURRENT);
            }
        } catch (RemoteException e) {
        }
    }

    private void runDisplayOverscan() throws Exception {
        String overscanStr = nextArgRequired();
        Rect rect = new Rect();
        if ("reset".equals(overscanStr)) {
            rect.set(0, 0, 0, 0);
        } else {
            final Pattern FLATTENED_PATTERN = Pattern.compile(
                    "(-?\\d+),(-?\\d+),(-?\\d+),(-?\\d+)");
            Matcher matcher = FLATTENED_PATTERN.matcher(overscanStr);
            if (!matcher.matches()) {
                System.err.println("Error: bad rectangle arg: " + overscanStr);
                return;
            }
            rect.left = Integer.parseInt(matcher.group(1));
            rect.top = Integer.parseInt(matcher.group(2));
            rect.right = Integer.parseInt(matcher.group(3));
            rect.bottom = Integer.parseInt(matcher.group(4));
        }

        try {
            mWm.setOverscan(Display.DEFAULT_DISPLAY, rect.left, rect.top, rect.right, rect.bottom);
        } catch (RemoteException e) {
        }
    }

    private void runDisplayScaling() throws Exception {
        String scalingStr = nextArgRequired();
        if ("auto".equals(scalingStr)) {
            mWm.setForcedDisplayScalingMode(Display.DEFAULT_DISPLAY, 0);
        } else if ("off".equals(scalingStr)) {
            mWm.setForcedDisplayScalingMode(Display.DEFAULT_DISPLAY, 1);
        } else {
            System.err.println("Error: scaling must be 'auto' or 'off'");
        }
    }

    private void runDismissKeyguard() throws Exception {
        mWm.dismissKeyguard();
    }

    private int parseDimension(String s) throws NumberFormatException {
        if (s.endsWith("px")) {
            return Integer.parseInt(s.substring(0, s.length() - 2));
        }
        if (s.endsWith("dp")) {
            int density;
            try {
                density = mWm.getBaseDisplayDensity(Display.DEFAULT_DISPLAY);
            } catch (RemoteException e) {
                density = DisplayMetrics.DENSITY_DEFAULT;
            }
            return Integer.parseInt(s.substring(0, s.length() - 2)) * density /
                    DisplayMetrics.DENSITY_DEFAULT;
        }
        return Integer.parseInt(s);
    }
}
