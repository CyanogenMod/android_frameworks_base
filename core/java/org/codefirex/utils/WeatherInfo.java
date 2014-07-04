/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2012 Zhenghong Wang
 * Copyright (C) 2014 codefireX
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

package org.codefirex.utils;

import java.io.FileNotFoundException;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.provider.MediaStore;

public class WeatherInfo implements Parcelable {
	public static final String WEATHER_AUTH = "org.codefirex.cfxweather.icons";
	public static final String DATA_AUTH = "org.codefirex.cfxweather.data";
	public static final Uri ICON_URI = Uri.parse("content://" + WEATHER_AUTH
			+ "/icons/#");
	public static final Uri DATA_URI = Uri.parse("content://" + DATA_AUTH);
	public static final String WEATHER_STATE_KEY = "weather_state_key";
	public static final String WEATHER_INFO_KEY = "weather_info_key";

	public static final int DEGREE_F = 1;
	public static final int DEGREE_C = 2;
	public static final String DEGREE_SYMBOL = "\u00B0";

	public String mTitle;
	public String mDescription;
	public String mLanguage;
	public String mLastBuildDate;
	public String mLocationCity;
	public String mLocationRegion; // region may be null
	public String mLocationCountry;

	public int mDegreeScale;
	public String mWindChillF;
	public String mWindChillC;
	public String mWindDirection;
	public String mWindSpeed;

	public String mAtmosphereHumidity;
	public String mAtmosphereVisibility;
	public String mAtmospherePressure;
	public String mAtmosphereRising;

	public String mAstronomySunrise;
	public String mAstronomySunset;

	public String mConditionTitle;
	public String mConditionLat;
	public String mConditionLon;

	/*
	 * information in tag "yweather:condition"
	 */
	public int mCurrentCode;
	public String mCurrentText;
	public String mCurrentTempC;
	public String mCurrentTempF;
	public String mCurrentConditionIconURL;
	public String mCurrentConditionDate;

	/*
	 * information in the first tag "yweather:forecast"
	 */

	/*
	 * information in the second tag "yweather:forecast"
	 */

	ForecastInfo mForecastInfo1 = new ForecastInfo();
	ForecastInfo mForecastInfo2 = new ForecastInfo();
	ForecastInfo mForecastInfo3 = new ForecastInfo();
	ForecastInfo mForecastInfo4 = new ForecastInfo();

	public WeatherInfo(){}

	public static Parcelable.Creator<WeatherInfo> CREATOR = new Parcelable.Creator<WeatherInfo>() {

		@Override
		public WeatherInfo createFromParcel(Parcel source) {
			return new WeatherInfo(source);
		}

		@Override
		public WeatherInfo[] newArray(int size) {
			return new WeatherInfo[size];
		}

	};

	private WeatherInfo(Parcel in) {
		mTitle = in.readString();
		mDescription = in.readString();
		mLanguage = in.readString();
		mLastBuildDate = in.readString();
		mLocationCity = in.readString();
		mLocationRegion = in.readString();
		mLocationCountry = in.readString();

		mDegreeScale = in.readInt();
		mWindChillF = in.readString();
		mWindChillC = in.readString();
		mWindDirection = in.readString();
		mWindSpeed = in.readString();

		mAtmosphereHumidity = in.readString();
		mAtmosphereVisibility = in.readString();
		mAtmospherePressure = in.readString();
		mAtmosphereRising = in.readString();

		mAstronomySunrise = in.readString();
		mAstronomySunset = in.readString();

		mConditionTitle = in.readString();
		mConditionLat = in.readString();
		mConditionLon = in.readString();

		mCurrentCode = in.readInt();
		mCurrentText = in.readString();
		mCurrentTempF = in.readString();
		mCurrentTempC = in.readString();
		mCurrentConditionIconURL = in.readString();
		mCurrentConditionDate = in.readString();

		mForecastInfo1 = (ForecastInfo) in.readParcelable(ForecastInfo.class
				.getClassLoader());
		mForecastInfo2 = (ForecastInfo) in.readParcelable(ForecastInfo.class
				.getClassLoader());
		mForecastInfo3 = (ForecastInfo) in.readParcelable(ForecastInfo.class
				.getClassLoader());
		mForecastInfo4 = (ForecastInfo) in.readParcelable(ForecastInfo.class
				.getClassLoader());
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(mTitle);
		dest.writeString(mDescription);
		dest.writeString(mLanguage);
		dest.writeString(mLastBuildDate);
		dest.writeString(mLocationCity);
		dest.writeString(mLocationRegion);
		dest.writeString(mLocationCountry);

		dest.writeInt(mDegreeScale);
		dest.writeString(mWindChillF);
		dest.writeString(mWindChillC);
		dest.writeString(mWindDirection);
		dest.writeString(mWindSpeed);

		dest.writeString(mAtmosphereHumidity);
		dest.writeString(mAtmosphereVisibility);
		dest.writeString(mAtmospherePressure);
		dest.writeString(mAtmosphereRising);

		dest.writeString(mAstronomySunrise);
		dest.writeString(mAstronomySunset);

		dest.writeString(mConditionTitle);
		dest.writeString(mConditionLat);
		dest.writeString(mConditionLon);

		dest.writeInt(mCurrentCode);
		dest.writeString(mCurrentText);
		dest.writeString(mCurrentTempF);
		dest.writeString(mCurrentTempC);
		dest.writeString(mCurrentConditionIconURL);
		dest.writeString(mCurrentConditionDate);

		dest.writeParcelable(mForecastInfo1, 0);
		dest.writeParcelable(mForecastInfo2, 0);
		dest.writeParcelable(mForecastInfo3, 0);
		dest.writeParcelable(mForecastInfo4, 0);
	}

	public static String addSymbol(String temp) {
		return temp + DEGREE_SYMBOL;
	}

	public static Drawable getIconFromProvider(Context context, int code) {
		Bitmap bmp = getBitmapFromProvider(context, code);
		Drawable d = new BitmapDrawable(context.getResources(),bmp);
		return d;
	}	

	public static Bitmap getBitmapFromProvider(Context context, int code) {
		Uri uri = Uri.parse(ICON_URI + String.valueOf(code));
		ParcelFileDescriptor descriptor;
		try {
			descriptor = context.getContentResolver().openFileDescriptor(uri, "r");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}
		return BitmapFactory.decodeFileDescriptor(descriptor.getFileDescriptor());
	}

	public static WeatherInfo getInfoFromProvider(Context context) {
		ContentResolver resolver = context.getContentResolver();
		Cursor cursor = resolver
				.query(DATA_URI, new String[] { MediaStore.MediaColumns.DATA },
						null, null, null);
		Bundle b = cursor.getExtras();
		WeatherInfo info = b.getParcelable(WEATHER_INFO_KEY);
		return info;
	}

	public static boolean getWeatherEnabled(Context context) {
		ContentResolver resolver = context.getContentResolver();
		Cursor cursor = resolver
				.query(DATA_URI, new String[] { MediaStore.MediaColumns.DATA },
						null, null, null);
		Bundle b = cursor.getExtras();
		boolean state = b.getBoolean(WEATHER_STATE_KEY, false);
		return state;
	}

	public ForecastInfo getForecastInfo1() {
		return mForecastInfo1;
	}

	public void setForecastInfo1(ForecastInfo forecastInfo1) {
		mForecastInfo1 = forecastInfo1;
	}

	public ForecastInfo getForecastInfo2() {
		return mForecastInfo2;
	}

	public void setForecastInfo2(ForecastInfo forecastInfo2) {
		mForecastInfo2 = forecastInfo2;
	}

	public ForecastInfo getForecastInfo3() {
		return mForecastInfo3;
	}

	public void setForecastInfo3(ForecastInfo forecastInfo3) {
		mForecastInfo1 = forecastInfo3;
	}

	public ForecastInfo getForecastInfo4() {
		return mForecastInfo4;
	}

	public void setForecastInfo4(ForecastInfo forecastInfo4) {
		mForecastInfo4 = forecastInfo4;
	}

	public String getCurrentConditionDate() {
		return mCurrentConditionDate;
	}

	public void setCurrentConditionDate(String currentConditionDate) {
		mCurrentConditionDate = currentConditionDate;
	}

	static private String turnFtoC(String tempF) {
		return String.valueOf((Integer.parseInt(tempF) - 32) * 5 / 9);
	}

	public int getCurrentCode() {
		return mCurrentCode;
	}

	public void setCurrentCode(int currentCode) {
		mCurrentCode = currentCode;
		// mCurrentConditionIconURL = "http://l.yimg.com/a/i/us/we/52/"
		// + currentCode + ".gif";
		mCurrentConditionIconURL = ICON_URI + String.valueOf(currentCode);
	}

	public void setCurrentScale(int scale) {
		mDegreeScale = scale;
		mForecastInfo1.setCurrentScale(scale);
		mForecastInfo2.setCurrentScale(scale);
		mForecastInfo3.setCurrentScale(scale);
		mForecastInfo4.setCurrentScale(scale);
	}

	public int getCurrentScale() {
		return mDegreeScale;
	}

	public String getCurrentTemp() {
		return  mDegreeScale == DEGREE_F ? mCurrentTempF : mCurrentTempC;
	}

	public String getCurrentTemp(int scale) {
		return scale == DEGREE_F ? mCurrentTempF : mCurrentTempC;
	}

	public String getCurrentTempF() {
		return mCurrentTempF;
	}

	public void setCurrentTempF(String currentTempF) {
		mCurrentTempF = currentTempF;
		mCurrentTempC = turnFtoC(currentTempF);
	}

	public Uri getCurrentConditionIconURL() {
		return Uri.parse(mCurrentConditionIconURL);
	}

	public String getCurrentTempC() {
		return mCurrentTempC;
	}

	public String getTitle() {
		return mTitle;
	}

	public void setTitle(String title) {
		mTitle = title;
	}

	public String getDescription() {
		return mDescription;
	}

	public void setDescription(String description) {
		mDescription = description;
	}

	public String getLanguage() {
		return mLanguage;
	}

	public void setLanguage(String language) {
		mLanguage = language;
	}

	public String getLastBuildDate() {
		return mLastBuildDate;
	}

	public void setLastBuildDate(String lastBuildDate) {
		mLastBuildDate = lastBuildDate;
	}

	public String getLocationCity() {
		return mLocationCity;
	}

	public void setLocationCity(String locationCity) {
		mLocationCity = locationCity;
	}

	public String getLocationRegion() {
		return mLocationRegion;
	}

	public void setLocationRegion(String locationRegion) {
		mLocationRegion = locationRegion;
	}

	public String getLocationCountry() {
		return mLocationCountry;
	}

	public void setLocationCountry(String locationCountry) {
		mLocationCountry = locationCountry;
	}

	public String getWindChillF() {
		return mWindChillF;
	}

	public String getWindChillC() {
		return mWindChillC;
	}

	public void setWindChill(String windChill) {
		mWindChillF = windChill;
		mWindChillC = turnFtoC(windChill);
	}

	public String getWindDirection() {
		return mWindDirection;
	}

	public void setWindDirection(String windDirection) {
		mWindDirection = windDirection;
	}

	public String getWindSpeed() {
		return mWindSpeed;
	}

	public void setWindSpeed(String windSpeed) {
		mWindSpeed = windSpeed;
	}

	public String getAtmosphereHumidity() {
		return mAtmosphereHumidity;
	}

	public void setAtmosphereHumidity(String atmosphereHumidity) {
		mAtmosphereHumidity = atmosphereHumidity;
	}

	public String getAtmosphereVisibility() {
		return mAtmosphereVisibility;
	}

	public void setAtmosphereVisibility(String atmosphereVisibility) {
		mAtmosphereVisibility = atmosphereVisibility;
	}

	public String getAtmospherePressure() {
		return mAtmospherePressure;
	}

	public void setAtmospherePressure(String atmospherePressure) {
		mAtmospherePressure = atmospherePressure;
	}

	public String getAtmosphereRising() {
		return mAtmosphereRising;
	}

	public void setAtmosphereRising(String atmosphereRising) {
		mAtmosphereRising = atmosphereRising;
	}

	public String getAstronomySunrise() {
		return mAstronomySunrise;
	}

	public void setAstronomySunrise(String astronomySunrise) {
		mAstronomySunrise = astronomySunrise;
	}

	public String getAstronomySunset() {
		return mAstronomySunset;
	}

	public void setAstronomySunset(String astronomySunset) {
		mAstronomySunset = astronomySunset;
	}

	public String getConditionTitle() {
		return mConditionTitle;
	}

	public void setConditionTitle(String conditionTitle) {
		mConditionTitle = conditionTitle;
	}

	public String getConditionLat() {
		return mConditionLat;
	}

	public void setConditionLat(String conditionLat) {
		mConditionLat = conditionLat;
	}

	public String getConditionLon() {
		return mConditionLon;
	}

	public void setConditionLon(String conditionLon) {
		mConditionLon = conditionLon;
	}

	public String getCurrentText() {
		return mCurrentText;
	}

	public void setCurrentText(String currentText) {
		mCurrentText = currentText;
	}

	public void setCurrentTempC(String currentTempC) {
		mCurrentTempC = currentTempC;
	}

	public static class ForecastInfo implements Parcelable {
		private String mForecastDay;
		private String mForecastDate;
		private int mForecastCode;
		private int mDegreeScale;
		private String mForecastTempHighC;
		private String mForecastTempLowC;
		private String mForecastTempHighF;
		private String mForecastTempLowF;
		private String mForecastConditionIconURL;
		private String mForecastText;

		public static Parcelable.Creator<ForecastInfo> CREATOR = new Parcelable.Creator<ForecastInfo>() {

			@Override
			public ForecastInfo createFromParcel(Parcel source) {
				return new ForecastInfo(source);
			}

			@Override
			public ForecastInfo[] newArray(int size) {
				return new ForecastInfo[size];
			}

		};

		public ForecastInfo() {
		}

		private ForecastInfo(Parcel in) {
			mForecastDay = in.readString();
			mForecastDate = in.readString();
			mForecastCode = in.readInt();
			mDegreeScale = in.readInt();
			mForecastTempHighC = in.readString();
			mForecastTempLowC = in.readString();
			mForecastTempHighF = in.readString();
			mForecastTempLowF = in.readString();
			mForecastConditionIconURL = in.readString();
			mForecastText = in.readString();
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeString(mForecastDay);
			dest.writeString(mForecastDate);
			dest.writeInt(mForecastCode);
			dest.writeInt(mDegreeScale);
			dest.writeString(mForecastTempHighC);
			dest.writeString(mForecastTempLowC);
			dest.writeString(mForecastTempHighF);
			dest.writeString(mForecastTempLowF);
			dest.writeString(mForecastConditionIconURL);
			dest.writeString(mForecastText);
		}

		public String getForecastDay() {
			return mForecastDay;
		}

		public void setForecastDay(String forecastDay) {
			mForecastDay = forecastDay;
		}

		public String getForecastDate() {
			return mForecastDate;
		}

		public void setForecastDate(String forecastDate) {
			mForecastDate = forecastDate;
		}

		public int getForecastCode() {
			return mForecastCode;
		}

		public void setForecastCode(int forecastCode) {
			mForecastCode = forecastCode;
			// mForecastConditionIconURL = "http://l.yimg.com/a/i/us/we/52/"
			// + forecastCode + ".gif";
			mForecastConditionIconURL = ICON_URI
					+ String.valueOf(forecastCode);
		}

		public void setCurrentScale(int scale) {
			mDegreeScale = scale;
		}

		public int getCurrentScale() {
			return mDegreeScale;
		}

		public String getForecastHighTemp() {
			return mDegreeScale == DEGREE_F ? mForecastTempHighF
					: mForecastTempHighC;
		}

		public String getForecastLowTemp() {
			return mDegreeScale == DEGREE_F ? mForecastTempLowF
					: mForecastTempLowC;
		}

		public String getForecastHighTemp(int scale) {
			return scale == DEGREE_F ? mForecastTempHighF : mForecastTempHighC;
		}

		public String getForecastLowTemp(int scale) {
			return scale == DEGREE_F ? mForecastTempLowF : mForecastTempLowC;
		}

		public String getForecastTempHighC() {
			return mForecastTempHighC;
		}

		public void setForecastTempHighC(String forecastTempHighC) {
			mForecastTempHighC = forecastTempHighC;
		}

		public String getForecastTempLowC() {
			return mForecastTempLowC;
		}

		public void setForecastTempLowC(String forecastTempLowC) {
			mForecastTempLowC = forecastTempLowC;
		}

		public String getForecastTempHighF() {
			return mForecastTempHighF;
		}

		public void setForecastTempHighF(String forecastTempHighF) {
			mForecastTempHighF = forecastTempHighF;
			mForecastTempHighC = turnFtoC(forecastTempHighF);
		}

		public String getForecastTempLowF() {
			return mForecastTempLowF;
		}

		public void setForecastTempLowF(String forecastTempLowF) {
			mForecastTempLowF = forecastTempLowF;
			mForecastTempLowC = turnFtoC(forecastTempLowF);
		}

		public Uri getForecastConditionIconURL() {
			return Uri.parse(mForecastConditionIconURL);
		}

		public String getForecastText() {
			return mForecastText;
		}

		public void setForecastText(String forecastText) {
			mForecastText = forecastText;
		}
	}
}
