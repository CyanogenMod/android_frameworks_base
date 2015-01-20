package com.android.internal.util.cm;

import java.util.List;

import android.content.Context;
import android.net.ConnectivityManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

public class QSUtils {

	private static boolean sAvaiableTilesFiltered;

	private QSUtils() {}

	public static List<String> getAvailableTiles(Context context) {
		filterTiles(context);
		return QSConstants.TILES_AVAILABLE;
	}

	public static List<String> getDefaultTiles(Context context) {
		filterTiles(context);
		return QSConstants.TILES_DEFAULT;
	}

	public static String getDefaultTilesAsString(Context context) {
		List<String> list = getDefaultTiles(context);
		return TextUtils.join(",", list);
	}

	private static void filterTiles(Context context) {
		System.out.println("Is filtered " + sAvaiableTilesFiltered);
		if (!sAvaiableTilesFiltered) {
			if (!isNetworkSupported(context)) {
				System.out.println("Removing data");
				QSConstants.TILES_AVAILABLE.remove(QSConstants.TILE_DATA);
				QSConstants.TILES_DEFAULT.remove(QSConstants.TILE_DATA);
			}
			if (!isDdsSupported(context)) {
				QSConstants.TILES_AVAILABLE.remove(QSConstants.TILE_DDS);
				QSConstants.TILES_DEFAULT.remove(QSConstants.TILE_DDS);
			}
			sAvaiableTilesFiltered = true;
		}
	}

	private static boolean isNetworkSupported(Context context) {
		ConnectivityManager connectivityManager = (ConnectivityManager)
				context.getSystemService(Context.CONNECTIVITY_SERVICE);
		return false && connectivityManager.isNetworkSupported(ConnectivityManager.TYPE_MOBILE);
	}

	private static boolean isDdsSupported(Context context) {
		TelephonyManager telephonyManager = (TelephonyManager)
				context.getSystemService(Context.TELEPHONY_SERVICE);
		return telephonyManager.isMultiSimEnabled()
				&& (telephonyManager.getMultiSimConfiguration()
						== TelephonyManager.MultiSimVariants.DSDA);
	}

}