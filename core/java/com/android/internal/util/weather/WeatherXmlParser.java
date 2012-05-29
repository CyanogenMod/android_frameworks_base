/******************************************************************************
 * Class       : YahooWeatherHelper.java                                                                  *
 * Parser helper for Yahoo                                                    *
 *                                                                            *
 * Version     : v1.0                                                         *
 * Date        : May 06, 2011                                                 *
 * Copyright (c)-2011 DatNQ some right reserved                               *
 * You can distribute, modify or what ever you want but WITHOUT ANY WARRANTY  *
 * Be honest by keep credit of this file                                      *
 *                                                                            *
 * If you have any concern, feel free to contact with me via email, i will    *
 * check email in free time                                                   * 
 * Email: nguyendatnq@gmail.com                                               *
 * ---------------------------------------------------------------------------*
 * Modification Logs:                                                         *
 *   KEYCHANGE  DATE          AUTHOR   DESCRIPTION                            *
 * ---------------------------------------------------------------------------*
 *    -------   May 06, 2011  DatNQ    Create new                             *
 ******************************************************************************/
/*
 * Modification into Android-internal WeatherXmlParser.java
 * Copyright (C) 2012 The AOKP Project
 */

package com.android.internal.util.weather;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import android.content.Context;
import android.util.Log;

public class WeatherXmlParser {

    protected static final String TAG = "WeatherXmlParser";

    /** Yahoo attributes */
    private static final String PARAM_YAHOO_LOCATION = "yweather:location";
    private static final String PARAM_YAHOO_UNIT = "yweather:units";
    private static final String PARAM_YAHOO_ATMOSPHERE = "yweather:atmosphere";
    private static final String PARAM_YAHOO_CONDITION = "yweather:condition";
    private static final String PARAM_YAHOO_WIND = "yweather:wind";
    private static final String PARAM_YAHOO_FORECAST = "yweather:forecast";

    private static final String ATT_YAHOO_CITY = "city";
    private static final String ATT_YAHOO_TEMP = "temp";
    private static final String ATT_YAHOO_CODE = "code";
    private static final String ATT_YAHOO_TEMP_UNIT = "temperature";
    private static final String ATT_YAHOO_HUMIDITY = "humidity";
    private static final String ATT_YAHOO_TEXT = "text";
    private static final String ATT_YAHOO_DATE = "date";
    private static final String ATT_YAHOO_SPEED = "speed";
    private static final String ATT_YAHOO_DIRECTION = "direction";
    private static final String ATT_YAHOO_TODAY_HIGH = "high";
    private static final String ATT_YAHOO_TODAY_LOW = "low";

    private Context mContext;

    public WeatherXmlParser(Context context) {
        mContext = context;
    }

    public WeatherInfo parseWeatherResponse(Document docWeather) {
        if (docWeather == null) {
            Log.e(TAG, "Invalid doc weather");
            return null;
        }

        String strCity = null;
        String strDate = null;
        String strCondition = null;
        String strCondition_code = null;
        String strTemp = null;
        String strTempUnit = null;
        String strHumidity = null;
        String strWindSpeed = null;
        String strWindDir = null;
        String strSpeedUnit = null;
        String strHigh = null;
        String strLow = null;

        try {
            Element root = docWeather.getDocumentElement();
            root.normalize();

            NamedNodeMap locationNode = root.getElementsByTagName(PARAM_YAHOO_LOCATION).item(0)
                    .getAttributes();
            if (locationNode != null) {
                strCity = locationNode.getNamedItem(ATT_YAHOO_CITY).getNodeValue();
            }

            NamedNodeMap unitNode = root.getElementsByTagName(PARAM_YAHOO_UNIT).item(0)
                    .getAttributes();

            if (locationNode != null) {
                strTempUnit = unitNode.getNamedItem(ATT_YAHOO_TEMP_UNIT).getNodeValue();
                strSpeedUnit = unitNode.getNamedItem(ATT_YAHOO_SPEED).getNodeValue();
            }

            NamedNodeMap atmosNode = root.getElementsByTagName(PARAM_YAHOO_ATMOSPHERE).item(0)
                    .getAttributes();
            if (atmosNode != null) {
                strHumidity = atmosNode.getNamedItem(ATT_YAHOO_HUMIDITY).getNodeValue();
            }

            NamedNodeMap conditionNode = root.getElementsByTagName(PARAM_YAHOO_CONDITION).item(0)
                    .getAttributes();
            if (conditionNode != null) {
                strCondition = conditionNode.getNamedItem(ATT_YAHOO_TEXT).getNodeValue();
                strCondition_code = conditionNode.getNamedItem(ATT_YAHOO_CODE).getNodeValue();
                strCondition = WeatherInfo.getTranslatedConditionString(mContext, Integer.parseInt(strCondition_code), strCondition);
                strTemp = conditionNode.getNamedItem(ATT_YAHOO_TEMP).getNodeValue();
                strDate = conditionNode.getNamedItem(ATT_YAHOO_DATE).getNodeValue();
            }

            NamedNodeMap temNode = root.getElementsByTagName(PARAM_YAHOO_WIND).item(0)
                    .getAttributes();
            if (temNode != null) {
                strWindSpeed = temNode.getNamedItem(ATT_YAHOO_SPEED).getNodeValue();
                strWindDir = temNode.getNamedItem(ATT_YAHOO_DIRECTION).getNodeValue();
            }

            NamedNodeMap fcNode = root.getElementsByTagName(PARAM_YAHOO_FORECAST).item(0).getAttributes();
            if (fcNode != null) {
                strHigh = fcNode.getNamedItem(ATT_YAHOO_TODAY_HIGH).getNodeValue();
                strLow = fcNode.getNamedItem(ATT_YAHOO_TODAY_LOW).getNodeValue();
            }
        } catch (Exception e) {
            Log.e(TAG, "Something wrong with parser data: " + e.toString());
            return null;
        }

        /* Weather info */
        WeatherInfo yahooWeatherInfo = new WeatherInfo(mContext, strCity, strDate, strCondition, strCondition_code, strTemp,
                strTempUnit, strHumidity, strWindSpeed, strWindDir, strSpeedUnit, strLow, strHigh, System.currentTimeMillis());

        Log.d(TAG, "Weather updated for " + strCity + ": " + strDate + ", " + strCondition + "(" + strCondition_code
                + "), " + strTemp + strTempUnit + ", " + strHumidity + "% humidity, "  + ", wind: " + strWindDir + " at "
                + strWindSpeed + strSpeedUnit + ", low: " + strLow  + strTempUnit + " high: " + strHigh + strTempUnit);

        return yahooWeatherInfo;
    }

    public String parsePlaceFinderResponse(String response) {
        try {

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(response)));

            NodeList resultNodes = doc.getElementsByTagName("Result");

            Node resultNode = resultNodes.item(0);
            NodeList attrsList = resultNode.getChildNodes();

            for (int i = 0; i < attrsList.getLength(); i++) {
                Node node = attrsList.item(i);
                Node firstChild = node.getFirstChild();
                if ("woeid".equalsIgnoreCase(node.getNodeName()) && firstChild != null) {
                    return firstChild.getNodeValue();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        return null;
    }
}
