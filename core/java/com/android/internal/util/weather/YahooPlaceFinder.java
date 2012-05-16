
package com.android.internal.util.weather;

import android.content.Context;

public class YahooPlaceFinder {

    private static final String YAHOO_API_BASE_REV_URL = "http://where.yahooapis.com/geocode?appid=jYkTZp64&q=%1$s,+%2$s&gflags=R";
    private static final String YAHOO_API_BASE_URL = "http://where.yahooapis.com/geocode?appid=jYkTZp64&q=%1$s";

    public static String reverseGeoCode(Context c, double latitude, double longitude) {

        String url = String.format(YAHOO_API_BASE_REV_URL, String.valueOf(latitude),
                String.valueOf(longitude));
        String response = new HttpRetriever().retrieve(url);
        return new WeatherXmlParser(c).parsePlaceFinderResponse(response);

    }

    public static String GeoCode(Context c, String location) {
        String url = String.format(YAHOO_API_BASE_URL, location).replace(' ', '+');
        String response = new HttpRetriever().retrieve(url);
        return new WeatherXmlParser(c).parsePlaceFinderResponse(response);
    }

}
