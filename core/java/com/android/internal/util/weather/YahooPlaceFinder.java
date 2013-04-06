/*
 * Copyright (C) 2012 The AOKP Project
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

package com.android.internal.util.weather;

import android.content.Context;
import android.net.Uri;

public class YahooPlaceFinder {

    private static final String YAHOO_API_BASE_URL = "http://query.yahooapis.com/v1/public/yql?q=" +
            Uri.encode("select woeid from geo.placefinder where text =");

    public static String reverseGeoCode(Context c, double latitude, double longitude) {

        String formattedCoordinates = String.format("\"%s %s\" and gflags=\"R\"",
                String.valueOf(latitude), String.valueOf(longitude));
        String url = YAHOO_API_BASE_URL + Uri.encode(formattedCoordinates);
        String response = new HttpRetriever().retrieve(url);
        return new WeatherXmlParser(c).parsePlaceFinderResponse(response);

    }

    public static String GeoCode(Context c, String location) {
        String url = YAHOO_API_BASE_URL + Uri.encode(String.format("\"%s\"",location));
        String response = new HttpRetriever().retrieve(url);
        return new WeatherXmlParser(c).parsePlaceFinderResponse(response);
    }

}