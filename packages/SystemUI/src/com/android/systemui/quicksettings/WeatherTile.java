/*
* Copyright (C) 2013-2014 Dokdo Project - Gwon Hyeok
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

package com.android.systemui.quicksettings;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.StringTokenizer;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class WeatherTile extends QuickSettingsTile {

	private final String TAG = "WeatherTile";
	private final String PATH="/sdcard/Android/data/weather.txt";
	private static String IconSet;

	public WeatherTile(Context context, QuickSettingsController qsc, Handler mhandler) {
		super(context, qsc, R.layout.quick_settings_tile_weather);

		mOnClick = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				updateTile();
			}
		};

		mOnLongClick = new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				Intent intent = new Intent();
				intent.setClassName("com.cyanogenmod.lockclock", "com.cyanogenmod.lockclock.preference.Preferences");
				startSettingsActivity(intent);
				return true;
				}
			};
		}

	@Override
	void onPostCreate() {
		updateTile();
		super.onPostCreate();
	}

	private void updateTile() {
		getweatherinfo();
        }

	public void updateResources() {
		updateTile();
		super.updateResources();
	}

	void updateQuickSettings() {
		getweatherinfo();
        }

	public void getweatherinfo() {
		//Weather info download in Lock Clock WeatherUpdateService
		Intent i = new Intent();
        i.setClassName("com.cyanogenmod.lockclock", "com.cyanogenmod.lockclock.weather.WeatherUpdateService");
        i.setAction("com.cyanogenmod.lockclock.action.FORCE_WEATHER_UPDATE");
        mContext.startService(i);
        new CountDownTimer(3000, 1000) {

        public void onTick(long millisUntilFinished) {
        }

    	public void onFinish() {
    	   getinfo();
    	   }
    	}.start();
        }

	public void getinfo(){
		TextView tv = (TextView) mTile.findViewById(R.id.text);
		TextView tv1 = (TextView) mTile.findViewById(R.id.text1);
		TextView tv2 = (TextView) mTile.findViewById(R.id.text2);
		TextView tv3 = (TextView) mTile.findViewById(R.id.text3);
		ImageView iv = (ImageView)mTile.findViewById(R.id.image);

		//file check
		int check = filecheck();
		if ( check == 1  ) {
		int check2 = getline();
		if (check2 == 10 ) {

		//parse weather
		String condition = Readfile(1);
		String state = Readfile(2);
		String lowtemp = Readfile(3);
		String hightemp = Readfile(4);
		String currenttemp = Readfile(5);
		String tempunit = Readfile(6);
		String humidity = Readfile(7);
		String windstrength = Readfile(8);
		String winddirection = Readfile(9);
		String SpeedUnit = Readfile(10);

	        //transform string
	        StringTokenizer st;
	        st = new StringTokenizer(lowtemp,".");
	        String low = st.nextToken();
	        st = new StringTokenizer(hightemp,".");
	        String high = st.nextToken();
	        st = new StringTokenizer(currenttemp,".");
	        String current = st.nextToken();

                //SETIMAGE
		String resName = null;
		IconSet = Settings.System.getString(mContext.getContentResolver(), Settings.System.WEATHER_TILE_ICON);
		if ( IconSet == null) {
			resName = "@drawable/weather_color_" + condition;
		} else if ( IconSet.equals("color")) {
                        resName = "@drawable/weather_color_" + condition;
		} else if ( IconSet.equals("mono")) {
                        resName = "@drawable/weather_" + condition;
		} else if ( IconSet.equals("vclouds")) {
                        resName = "@drawable/weather_vclouds_" + condition;
		}
		String packName = mContext.getPackageName();
		int resID = mContext.getResources().getIdentifier(resName, "drawable", packName);
		iv.setImageResource(resID);

		//GET CONDITION TEXT
		String ConditionStringresName = "@string/weather_" + condition;
		int ConditionStringresID = mContext.getResources().getIdentifier(ConditionStringresName, "string", packName);

		//SETTEXT
		tv.setText(state);
		tv1.setText(current+"℃");
		tv2.setText(low+"℃ | "+high+"℃");
		tv3.setText(ConditionStringresID);
		} else {
	    	setNofile();
	    	}
		} else {
		setNofile();
		}
	}

	public void setNofile() {
		TextView tv = (TextView) mTile.findViewById(R.id.text);
		TextView tv1 = (TextView) mTile.findViewById(R.id.text1);
		TextView tv2 = (TextView) mTile.findViewById(R.id.text2);
		TextView tv3 = (TextView) mTile.findViewById(R.id.text3);
		ImageView iv = (ImageView)mTile.findViewById(R.id.image);
		tv.setText(R.string.need_to_update_weather);
		tv2.setText(R.string.no_weather_file);
		tv1.setText("");
		tv3.setText("");
	        String resName = null;
                if ( IconSet == null) {
                        resName = "@drawable/weather_color_na";
                } else if ( IconSet.equals("color")) {
                        resName = "@drawable/weather_color_na";
                } else if ( IconSet.equals("mono")) {
                        resName = "@drawable/weather_na";
                } else if ( IconSet.equals("vclouds")) {
                        resName = "@drawable/weather_vclouds_na";
                }
                String packName = mContext.getPackageName();
                int resID = mContext.getResources().getIdentifier(resName, "drawable", packName);
		iv.setImageResource(resID);
        }

	public String Readfile(int num){
		FileReader readFile;
		BufferedReader br;
		String getLine;
		try {
			readFile = new FileReader(PATH);
			br = new BufferedReader(readFile);
			String tmpStr = "";
			while((getLine = br.readLine()) != null){
				if(getLine.contains("["+num+"]")){
					tmpStr = getLine.toString();
					String gap = tmpStr.substring(3);
					return gap;
					}
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		return null;
	}

	public  int filecheck() {
		File f = new File(PATH);
		if (f.isFile()) {
			int isfile = 1;
			return isfile;
		} else {
			int isfile = 0 ;
			return isfile;
		}
	}

	public int getline(){
		FileReader readFile;
		BufferedReader br;
		String getLine;
		int line = 0;
		try {
			readFile = new FileReader(PATH);
			br = new BufferedReader(readFile);
			String tmpStr = "";
			while((getLine = br.readLine()) != null){
				line = line+1;
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		return line;
	}
}
