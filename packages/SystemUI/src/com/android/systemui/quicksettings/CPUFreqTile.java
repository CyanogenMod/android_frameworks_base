package com.android.systemui.quicksettings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.RemoteException;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.util.Log;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CPUFreqTile extends QuickSettingsTile {

    private final String TAG = CPUFreqTile.class.getSimpleName();

    private final String TimeInState = "/sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state";
    private final String CurCPUFreq = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq";
    private final String MaxCPUFreq = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq";
    private final String MinCPUFreq = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq";

    private final Handler hand = new Handler();

    private static List<String> AvailableFreq = new ArrayList<String>();

    public CPUFreqTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettings();
            }
        };
    }

    @Override
    void onPostCreate() {
        hand.postDelayed(run, 0);
        super.onPostCreate();
    }

    Runnable run = new Runnable() {
        @Override
        public void run() {
            updateResources();
            hand.postDelayed(run, 1000);
        }
    };

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    @Override
    public void onDestroy() {
        hand.removeCallbacks(run);
        super.onDestroy();
    }

    private synchronized void updateTile() {
        mDrawable = R.drawable.ic_qs_cpufreq;
        mLabel = String.valueOf(Integer.parseInt(readLine(CurCPUFreq)) / 1000
                + "MHz");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        updateResources();
    }

    private void showSettings() {
        AvailableFreq.clear();

        String[] freqs = readBlock(TimeInState).split("\\r?\\n");
        for (String freq : freqs) AvailableFreq.add(freq.split(" ")[0]);

        if (Integer.parseInt(AvailableFreq.get(0)) >
                Integer.parseInt(AvailableFreq.get(AvailableFreq.size() - 1))) {
            AvailableFreq.clear();
            for (int i = freqs.length - 1; i >= 0; i--)
                AvailableFreq.add(freqs[i].split(" ")[0]);
        }

        int maxfreq = Integer.parseInt(readLine(MaxCPUFreq));
        int minfreq = Integer.parseInt(readLine(MinCPUFreq));

        LinearLayout layout = new LinearLayout(mContext);
        layout.setPadding(10, 0, 10, 10);
        layout.setOrientation(LinearLayout.VERTICAL);

        final TextView maxfreqtext = new TextView(mContext);
        maxfreqtext.setPadding(0, 20, 0, 0);
        maxfreqtext.setTextColor(mContext.getResources().getColor(android.R.color.white));
        maxfreqtext.setTextSize(16);
        maxfreqtext.setText(String.valueOf(mContext.getString(R.string.cpufreq_max)
                + ": " + maxfreq / 1000 + "MHz"));
        maxfreqtext.setGravity(Gravity.CENTER);

        final TextView minfreqtext = new TextView(mContext);
        minfreqtext.setPadding(0, 10, 0, 0);
        minfreqtext.setTextColor(mContext.getResources().getColor(android.R.color.white));
        minfreqtext.setTextSize(16);
        minfreqtext.setText(String.valueOf(mContext.getString(R.string.cpufreq_min)
                + ": " + minfreq / 1000 + "MHz"));
        minfreqtext.setGravity(Gravity.CENTER);

        final SeekBar maxfreqbar = new SeekBar(mContext);
        maxfreqbar.setMax(AvailableFreq.size() - 1);
        maxfreqbar.setProgress(AvailableFreq.indexOf(String.valueOf(maxfreq)));

        final SeekBar minfreqbar = new SeekBar(mContext);
        minfreqbar.setMax(AvailableFreq.size() - 1);
        minfreqbar.setProgress(AvailableFreq.indexOf(String.valueOf(minfreq)));

        maxfreqbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                maxfreqtext.setText(String.valueOf(mContext.getString(R.string.cpufreq_max)
                        + ": " + Integer.parseInt(AvailableFreq.get(i)) / 1000 + "MHz"));
                if (i < minfreqbar.getProgress()) minfreqbar.setProgress(i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                writeFile(AvailableFreq.get(seekBar.getProgress()), MaxCPUFreq);
            }
        });
        minfreqbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                minfreqtext.setText(String.valueOf(mContext.getString(R.string.cpufreq_min)
                        + ": " + Integer.parseInt(AvailableFreq.get(i)) / 1000 + "MHz"));
                if (i > maxfreqbar.getProgress()) maxfreqbar.setProgress(i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                writeFile(AvailableFreq.get(seekBar.getProgress()), MinCPUFreq);
            }
        });

        layout.addView(maxfreqtext);
        layout.addView(maxfreqbar);
        layout.addView(minfreqtext);
        layout.addView(minfreqbar);

        AlertDialog.Builder ab = new AlertDialog.Builder(mContext);
        AlertDialog dialog = ab.setView(layout).create();

        mStatusbarService.animateCollapsePanels();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
        try {
            WindowManagerGlobal.getWindowManagerService().dismissKeyguard();
        } catch (RemoteException e) {
        }
        dialog.show();
    }

    private String readBlock(String file) {
        try {
            BufferedReader buffreader = new BufferedReader(
                    new FileReader(file), 256);
            String line;
            StringBuilder text = new StringBuilder();
            while ((line = buffreader.readLine()) != null) {
                text.append(line);
                text.append("\n");
            }
            buffreader.close();
            return text.toString();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "unable to find " + file + " Exception: ", e);
        } catch (IOException e) {
            Log.e(TAG, "unable to read " + file + " Exception: ", e);
        }
        return "0";
    }

    private String readLine(String file) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file),
                    256);
            try {
                return reader.readLine();
            } finally {
                reader.close();
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "unable to find " + file + " Exception: ", e);
        } catch (IOException e) {
            Log.e(TAG, "unable to read " + file + " Exception: ", e);
        }
        return "0";
    }

    private void writeFile(String value, String file) {
        try {
            FileWriter fw = new FileWriter(file);
            try {
                fw.write(value);
            } finally {
                fw.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "unable to write " + file + " Exception: ", e);
        }
    }

}