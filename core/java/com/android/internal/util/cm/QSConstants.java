package com.android.internal.util.cm;

import java.util.ArrayList;

public class QSConstants {

	private QSConstants() {}

	public static final String TILE_WIFI = "wifi";
	public static final String TILE_BLUETOOTH = "bt";
	public static final String TILE_INVERSION = "inversion";
	public static final String TILE_CELLULAR = "cell";
	public static final String TILE_AIRPLANE = "airplane";
	public static final String TILE_ROTATION = "rotation";
	public static final String TILE_FLASHLIGHT = "flashlight";
	public static final String TILE_LOCATION = "location";
	public static final String TILE_CAST = "cast";
	public static final String TILE_HOTSPOT = "hotspot";
	public static final String TILE_NOTIFICATIONS = "notifications";
	public static final String TILE_DATA = "data";
	public static final String TILE_ROAMING = "roaming";
	public static final String TILE_DDS = "dds";
	public static final String TILE_APN = "apn";

	protected static final ArrayList<String> TILES_DEFAULT
		= new ArrayList<String>();

	static {
		TILES_DEFAULT.add(TILE_WIFI);
		TILES_DEFAULT.add(TILE_BLUETOOTH);
		TILES_DEFAULT.add(TILE_CELLULAR);
		TILES_DEFAULT.add(TILE_AIRPLANE);
		TILES_DEFAULT.add(TILE_ROTATION);
		TILES_DEFAULT.add(TILE_FLASHLIGHT);
		TILES_DEFAULT.add(TILE_LOCATION);
		TILES_DEFAULT.add(TILE_CAST);
	}

	protected static final ArrayList<String> TILES_AVAILABLE
		= new ArrayList<String>();

	static {
		TILES_AVAILABLE.add(TILE_WIFI);
		TILES_AVAILABLE.add(TILE_BLUETOOTH);
		TILES_AVAILABLE.add(TILE_INVERSION);
		TILES_AVAILABLE.add(TILE_CELLULAR);
		TILES_AVAILABLE.add(TILE_AIRPLANE);
		TILES_AVAILABLE.add(TILE_ROTATION);
		TILES_AVAILABLE.add(TILE_FLASHLIGHT);
		TILES_AVAILABLE.add(TILE_LOCATION);
		TILES_AVAILABLE.add(TILE_CAST);
		TILES_AVAILABLE.add(TILE_HOTSPOT);
		TILES_AVAILABLE.add(TILE_NOTIFICATIONS);
		TILES_AVAILABLE.add(TILE_DATA);
		TILES_AVAILABLE.add(TILE_ROAMING);
		TILES_AVAILABLE.add(TILE_DDS);
		TILES_AVAILABLE.add(TILE_APN);
	}

}
