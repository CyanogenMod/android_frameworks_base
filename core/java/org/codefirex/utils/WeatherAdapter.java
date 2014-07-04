package org.codefirex.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class WeatherAdapter extends BroadcastReceiver {
	public static final String WEATHER_ACTION = "org.codefirex.cfxweather.cfx_weather_state";
	public static final String WEATHER_SERVICE_STATE = "cfx_weather_service_state";

	public static final int STATE_ON = 1;
	public static final int STATE_OFF = 2;
	public static final int STATE_REFRESHING = 3;
	public static final int STATE_UPDATED = 4;
	public static final int STATE_SCALE = 5;
	public static final String SCALE_TYPE = "scale_type";

	public interface WeatherListener {
		public void onServiceStateChanged(int state);
	}

	private Context mContext;
	private int mServiceState;
	private WeatherListener mListener;
	private WeatherInfo mWeatherInfo;

	public WeatherAdapter(Context context) {
		this(context, null);
	}

	public WeatherAdapter(Context context, WeatherListener listener) {
		mContext = context;
		mListener = listener;
		mWeatherInfo = WeatherInfo.getInfoFromProvider(mContext);
	}	

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		if (WEATHER_ACTION.equals(action)) {
			mServiceState = intent.getIntExtra(WEATHER_SERVICE_STATE, STATE_OFF);
			if (STATE_SCALE == mServiceState) {
				int scale = intent.getIntExtra(SCALE_TYPE, WeatherInfo.DEGREE_F);
				mWeatherInfo.setCurrentScale(scale);
			}
			if (mListener != null) {
				mListener.onServiceStateChanged(mServiceState);
			}
		}
	}

	public void startUpdates() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(WEATHER_ACTION );
		mContext.registerReceiver(WeatherAdapter.this, filter);
	}

	public void stopUpdates() {
		mContext.unregisterReceiver(WeatherAdapter.this);
	}

	public void setWeatherListener(WeatherListener listener) {
		if (mListener != null) {
			mListener = null;
		}
		mListener = listener;
	}

	public boolean getEnabled() {
		return WeatherInfo.getWeatherEnabled(mContext);
	}

	public int getServiceState() {
		return mServiceState;
	}

	// get WeatheInfo last state as held by adapter
	// used when temp scale type changes
	public WeatherInfo getLastKnownWeather() {
		return mWeatherInfo;
	}

	public WeatherInfo getLatestWeather() {
		mWeatherInfo = WeatherInfo.getInfoFromProvider(mContext);
		return mWeatherInfo;
	}
}
