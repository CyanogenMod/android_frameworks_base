package com.android.systemui.statusbar.policy;

import org.codefirex.utils.WeatherAdapter;
import org.codefirex.utils.WeatherAdapter.WeatherListener;
import org.codefirex.utils.WeatherInfo;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.widget.TextView;

public class WeatherView extends TextView implements WeatherListener {
	private static final String TAG = "WeatherView";

	boolean mServiceEnabled = false;
	boolean mViewEnabled = false;

	Context mContext;
	Handler mHandler;
	SettingsObserver mObserver;
	WeatherAdapter mAdapter;
	WeatherInfo mInfo;

	public WeatherView(Context context) {
		this(context, null);
	}

	public WeatherView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public WeatherView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mContext = context;
		mHandler = new Handler();
		mObserver = new SettingsObserver(mHandler);
		mObserver.observe();
		mAdapter = new WeatherAdapter(mContext, this);
		mInfo = mAdapter.getLatestWeather();
        mServiceEnabled = mAdapter.getEnabled();
		mAdapter.startUpdates();
		if(mServiceEnabled) {
			updateWeather();
			mObserver.onChange(true);
		}
	}

	class SettingsObserver extends ContentObserver {
		public SettingsObserver(Handler handler) {
			super(handler);
		}

		void observe() {
			ContentResolver resolver = mContext.getContentResolver();
			resolver.registerContentObserver(Settings.System
					.getUriFor(Settings.System.SYSTEMUI_WEATHER_HEADER_VIEW),
					false, this);
			onChange(true);
		}

		@Override
		public void onChange(boolean selfChange) {
			ContentResolver resolver = mContext.getContentResolver();
			mViewEnabled = Settings.System.getBoolean(resolver,
					Settings.System.SYSTEMUI_WEATHER_HEADER_VIEW, false);
			updateVisibility();
		}
	}

	private void updateVisibility() {
		setVisibility((mServiceEnabled && mViewEnabled) ? VISIBLE : INVISIBLE);
	}

	private void updateWeather() {
		String weather = mInfo.getCurrentText();
		if ("unknown".equals(weather)) {
			setText("");
			return;
		}
		String temp = WeatherInfo.addSymbol(mInfo.getCurrentTemp());
		StringBuilder bb = new StringBuilder().append(weather).append(" ")
				.append(temp);
		setText(bb.toString());
	}

	@Override
	public void onServiceStateChanged(int state) {
		switch (state) {
		case WeatherAdapter.STATE_ON:
			mServiceEnabled = true;
			mObserver.onChange(true);
			break;
		case WeatherAdapter.STATE_OFF:
			mServiceEnabled = false;
			mObserver.onChange(true);
			break;
		case WeatherAdapter.STATE_REFRESHING:
			break;
		case WeatherAdapter.STATE_SCALE:
			mInfo = mAdapter.getLastKnownWeather();
			updateWeather();
			break;
		case WeatherAdapter.STATE_UPDATED:
			mInfo = mAdapter.getLatestWeather();
			updateWeather();
			break;
		default:
			break;
		}

	}
}
