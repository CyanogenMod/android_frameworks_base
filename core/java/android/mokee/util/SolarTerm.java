/*
 * Copyright (C) 2013 The MoKee OpenSource Project
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

package android.mokee.util;

import android.content.res.Resources;

import com.android.internal.R;

public class SolarTerm {
   static int START_YEAR = 1970;

   static int[] arrays = new int[] {
    0x95,   0xB4,   0x96,   0xA5,   0x96,   0x97,   0x88,   0x78,   0x78,   0x69,   0x78,   0x87,       //1970
    0x96,   0xB4,   0x96,   0xA6,   0x97,   0x97,   0x78,   0x79,   0x79,   0x69,   0x78,   0x77,       //1971
    0x96,   0xA4,   0xA5,   0xA5,   0xA6,   0xA6,   0x88,   0x88,   0x88,   0x78,   0x87,   0x87,       //1972
    0xA5,   0xB5,   0x96,   0xA5,   0xA6,   0x96,   0x88,   0x78,   0x78,   0x78,   0x87,   0x87,       //1973
    0x95,   0xB4,   0x96,   0xA5,   0x96,   0x97,   0x88,   0x78,   0x78,   0x69,   0x78,   0x87,       //1974
    0x96,   0xB4,   0x96,   0xA6,   0x97,   0x97,   0x78,   0x79,   0x78,   0x69,   0x78,   0x77,       //1975
    0x96,   0xA4,   0xA5,   0xB5,   0xA6,   0xA6,   0x88,   0x89,   0x88,   0x78,   0x87,   0x87,       //1976
    0xA5,   0xB4,   0x96,   0xA5,   0x96,   0x96,   0x88,   0x88,   0x78,   0x78,   0x87,   0x87,       //1977
    0x95,   0xB4,   0x96,   0xA5,   0x96,   0x97,   0x88,   0x78,   0x78,   0x79,   0x78,   0x87,       //1978
    0x96,   0xB4,   0x96,   0xA6,   0x96,   0x97,   0x78,   0x79,   0x78,   0x69,   0x78,   0x77,       //1979
    0x96,   0xA4,   0xA5,   0xB5,   0xA6,   0xA6,   0x88,   0x88,   0x88,   0x78,   0x87,   0x87,       //1980
    0xA5,   0xB4,   0x96,   0xA5,   0xA6,   0x96,   0x88,   0x88,   0x78,   0x78,   0x77,   0x87,       //1981
    0x95,   0xB4,   0x96,   0xA5,   0x96,   0x97,   0x88,   0x78,   0x78,   0x79,   0x77,   0x87,       //1982
    0x95,   0xB4,   0x96,   0xA5,   0x96,   0x97,   0x78,   0x79,   0x78,   0x69,   0x78,   0x77,       //1983
    0x96,   0xB4,   0xA5,   0xB5,   0xA6,   0xA6,   0x87,   0x88,   0x88,   0x78,   0x87,   0x87,       //1984
    0xA5,   0xB4,   0xA6,   0xA5,   0xA6,   0x96,   0x88,   0x88,   0x78,   0x78,   0x87,   0x87,       //1985
    0xA5,   0xB4,   0x96,   0xA5,   0x96,   0x97,   0x88,   0x78,   0x78,   0x79,   0x77,   0x87,       //1986
    0x95,   0xB4,   0x96,   0xA5,   0x96,   0x97,   0x88,   0x79,   0x78,   0x69,   0x78,   0x87,       //1987
    0x96,   0xB4,   0xA5,   0xB5,   0xA6,   0xA6,   0x87,   0x88,   0x88,   0x78,   0x87,   0x86,       //1988
    0xA5,   0xB4,   0xA5,   0xA5,   0xA6,   0x96,   0x88,   0x88,   0x88,   0x78,   0x87,   0x87,       //1989
    0xA5,   0xB4,   0x96,   0xA5,   0x96,   0x96,   0x88,   0x78,   0x78,   0x79,   0x77,   0x87,       //1990
    0x95,   0xB4,   0x96,   0xA5,   0x86,   0x97,   0x88,   0x78,   0x78,   0x69,   0x78,   0x87,       //1991
    0x96,   0xB4,   0xA5,   0xB5,   0xA6,   0xA6,   0x87,   0x88,   0x88,   0x78,   0x87,   0x86,       //1992
    0xA5,   0xB3,   0xA5,   0xA5,   0xA6,   0x96,   0x88,   0x88,   0x88,   0x78,   0x87,   0x87,       //1993
    0xA5,   0xB4,   0x96,   0xA5,   0x96,   0x96,   0x88,   0x78,   0x78,   0x78,   0x87,   0x87,       //1994
    0x95,   0xB4,   0x96,   0xA5,   0x96,   0x97,   0x88,   0x76,   0x78,   0x69,   0x78,   0x87,       //1995
    0x96,   0xB4,   0xA5,   0xB5,   0xA6,   0xA6,   0x87,   0x88,   0x88,   0x78,   0x87,   0x86,       //1996
    0xA5,   0xB3,   0xA5,   0xA5,   0xA6,   0xA6,   0x88,   0x88,   0x88,   0x78,   0x87,   0x87,       //1997
    0xA5,   0xB4,   0x96,   0xA5,   0x96,   0x96,   0x88,   0x78,   0x78,   0x78,   0x87,   0x87,       //1998
    0x95,   0xB4,   0x96,   0xA5,   0x96,   0x97,   0x88,   0x78,   0x78,   0x69,   0x78,   0x87,       //1999
    0x96,   0xB4,   0xA5,   0xB5,   0xA6,   0xA6,   0x87,   0x88,   0x88,   0x78,   0x87,   0x86,       //2000
    0xA5,   0xB3,   0xA5,   0xA5,   0xA6,   0xA6,   0x88,   0x88,   0x88,   0x78,   0x87,   0x87,       //2001
    0xA5,   0xB4,   0x96,   0xA5,   0x96,   0x96,   0x88,   0x78,   0x78,   0x78,   0x87,   0x87,       //2002
    0x95,   0xB4,   0x96,   0xA5,   0x96,   0x97,   0x88,   0x78,   0x78,   0x69,   0x78,   0x87,       //2003
    0x96,   0xB4,   0xA5,   0xB5,   0xA6,   0xA6,   0x87,   0x88,   0x88,   0x78,   0x87,   0x86,       //2004
    0xA5,   0xB3,   0xA5,   0xA5,   0xA6,   0xA6,   0x88,   0x88,   0x88,   0x78,   0x87,   0x87,       //2005
    0xA5,   0xB4,   0x96,   0xA5,   0xA6,   0x96,   0x88,   0x88,   0x78,   0x78,   0x87,   0x87,       //2006
    0x95,   0xB4,   0x96,   0xA5,   0x96,   0x97,   0x88,   0x78,   0x78,   0x69,   0x78,   0x87,       //2007
    0x96,   0xB4,   0xA5,   0xB5,   0xA6,   0xA6,   0x87,   0x88,   0x87,   0x78,   0x87,   0x86,       //2008
    0xA5,   0xB3,   0xA5,   0xB5,   0xA6,   0xA6,   0x88,   0x88,   0x88,   0x78,   0x87,   0x87,       //2009
    0xA5,   0xB4,   0x96,   0xA5,   0xA6,   0x96,   0x88,   0x88,   0x78,   0x78,   0x87,   0x87,       //2010
    0x95,   0xB4,   0x96,   0xA5,   0x96,   0x97,   0x88,   0x78,   0x78,   0x79,   0x78,   0x87,       //2011
    0x95,   0xB4,   0xA5,   0xB5,   0xA5,   0xA6,   0x87,   0x88,   0x87,   0x78,   0x87,   0x86,       //2012
    0xA5,   0xB3,   0xA5,   0xB5,   0xA6,   0xA6,   0x87,   0x88,   0x88,   0x78,   0x87,   0x87,       //2013
    0xA5,   0xB4,   0x96,   0xA5,   0xA6,   0x96,   0x88,   0x88,   0x78,   0x78,   0x87,   0x87,       //2014
    0x95,   0xB4,   0x96,   0xA5,   0x96,   0x97,   0x88,   0x78,   0x78,   0x79,   0x77,   0x87,       //2015
    0x95,   0xB4,   0xA5,   0xB4,   0xA5,   0xA6,   0x87,   0x88,   0x87,   0x78,   0x87,   0x86,       //2016
    0xA5,   0xC3,   0xA5,   0xB5,   0xA6,   0xA6,   0x87,   0x88,   0x88,   0x78,   0x87,   0x87,       //2017
    0xA5,   0xB4,   0xA6,   0xA5,   0xA6,   0x96,   0x88,   0x88,   0x78,   0x78,   0x87,   0x87,       //2018
    0xA5,   0xB4,   0x96,   0xA5,   0x96,   0x96,   0x88,   0x78,   0x78,   0x79,   0x77,   0x87,       //2019
    0x95,   0xB4,   0xA5,   0xB4,   0xA5,   0xA6,   0x97,   0x87,   0x87,   0x78,   0x87,   0x86,       //2020
    0xA5,   0xC3,   0xA5,   0xB5,   0xA6,   0xA6,   0x87,   0x88,   0x88,   0x78,   0x87,   0x86,       //2021
    0xA5,   0xB4,   0xA5,   0xA5,   0xA6,   0x96,   0x88,   0x88,   0x88,   0x78,   0x87,   0x87,       //2022
    0xA5,   0xB4,   0x96,   0xA5,   0x96,   0x96,   0x88,   0x78,   0x78,   0x79,   0x77,   0x87,       //2023
    0x95,   0xB4,   0xA5,   0xB4,   0xA5,   0xA6,   0x97,   0x87,   0x87,   0x78,   0x87,   0x96,       //2024
    0xA5,   0xC3,   0xA5,   0xB5,   0xA6,   0xA6,   0x87,   0x88,   0x88,   0x78,   0x87,   0x86,       //2025
    0xA5,   0xB3,   0xA5,   0xA5,   0xA6,   0xA6,   0x88,   0x88,   0x88,   0x78,   0x87,   0x87,       //2026
    0xA5,   0xB4,   0x96,   0xA5,   0x96,   0x96,   0x88,   0x78,   0x78,   0x78,   0x87,   0x87,       //2027
    0x95,   0xB4,   0xA5,   0xB4,   0xA5,   0xA6,   0x97,   0x87,   0x87,   0x78,   0x87,   0x96,       //2028
    0xA5,   0xC3,   0xA5,   0xB5,   0xA6,   0xA6,   0x87,   0x88,   0x88,   0x78,   0x87,   0x86,       //2029
    0xA5,   0xB3,   0xA5,   0xA5,   0xA6,   0xA6,   0x88,   0x88,   0x88,   0x78,   0x87,   0x87,       //2030
    0xA5,   0xB4,   0x96,   0xA5,   0x96,   0x96,   0x88,   0x78,   0x78,   0x78,   0x87,   0x87,       //2031
    0x95,   0xB4,   0xA5,   0xB4,   0xA5,   0xA6,   0x97,   0x87,   0x87,   0x78,   0x87,   0x96,       //2032
    0xA5,   0xC3,   0xA5,   0xB5,   0xA6,   0xA6,   0x88,   0x88,   0x88,   0x78,   0x87,   0x86,       //2033
    0xA5,   0xB3,   0xA5,   0xA5,   0xA6,   0xA6,   0x88,   0x78,   0x88,   0x78,   0x87,   0x87,       //2034
    0xA5,   0xB4,   0x96,   0xA5,   0xA6,   0x96,   0x88,   0x88,   0x78,   0x78,   0x87,   0x87,       //2035
    0x95,   0xB4,   0xA5,   0xB4,   0xA5,   0xA6,   0x97,   0x87,   0x87,   0x78,   0x87,   0x96,       //2036
    0xA5,   0xC3,   0xA5,   0xB5,   0xA6,   0xA6,   0x87,   0x88,   0x88,   0x78,   0x87,   0x86,       //2037
    0xA5,   0xB3,   0xA5,   0xA5,   0xA6,   0xA6,   0x88,   0x88,   0x88,   0x78,   0x87,   0x87,       //2038
    0xA5,   0xB4,   0x96,   0xA5,   0xA6,   0x96,   0x88,   0x88,   0x78,   0x78,   0x87,   0x87,       //2039
    0x95,   0xB4,   0xA5,   0xB4,   0xA5,   0xA6,   0x97,   0x87,   0x87,   0x78,   0x87,   0x96,       //2040
    0xA5,   0xC3,   0xA5,   0xB5,   0xA5,   0xA6,   0x87,   0x88,   0x87,   0x78,   0x87,   0x86,       //2041
    0xA5,   0xB3,   0xA5,   0xB5,   0xA6,   0xA6,   0x88,   0x88,   0x88,   0x78,   0x87,   0x87,       //2042
    0xA5,   0xB4,   0x96,   0xA5,   0xA6,   0x96,   0x88,   0x88,   0x78,   0x78,   0x87,   0x87,       //2043
    0x95,   0xB4,   0xA5,   0xB4,   0xA5,   0xA6,   0x97,   0x87,   0x87,   0x88,   0x87,   0x96,       //2044
    0xA5,   0xC3,   0xA5,   0xB4,   0xA5,   0xA6,   0x87,   0x88,   0x87,   0x78,   0x87,   0x86,       //2045
    0xA5,   0xB3,   0xA5,   0xB5,   0xA6,   0xA6,   0x87,   0x88,   0x88,   0x78,   0x87,   0x87,       //2046
    0xA5,   0xB4,   0x96,   0xA5,   0xA6,   0x96,   0x88,   0x88,   0x78,   0x78,   0x87,   0x87,       //2047
    0x95,   0xB4,   0xA5,   0xB4,   0xA5,   0xA5,   0x97,   0x87,   0x87,   0x88,   0x86,   0x96,       //2048
    0xA4,   0xC3,   0xA5,   0xA5,   0xA5,   0xA6,   0x97,   0x87,   0x87,   0x78,   0x87,   0x86,       //2049
    0xA5,   0xC3,   0xA5,   0xB5,   0xA6,   0xA6,   0x87,   0x88,   0x78,   0x78,   0x87,   0x87};     //2050
   public static String getSolarTermStr (int year, int month, int day) {
       Resources res = Resources.getSystem();
       String [] solarArrayStr = res.getStringArray(com.android.internal.R.array.solar_term);
       String SolarTermStr="";
       int temp;
       int result = 0;
       int index = (year - START_YEAR) * 12 + month;
       String s = Integer.toHexString(arrays[index]);
       if (day < 15) {
           temp = 15 - Integer.parseInt(s.substring(0, 1), 16);
       } else {
           temp = Integer.parseInt(s.substring(1, 2), 16) + 15;
       }
       if(temp == day) {
           if (day > 15) {
               result = (month) * 2 + 2 ;
           } else {
               result = (month) * 2 + 1;
           }
       }
       SolarTermStr = solarArrayStr[result];
       return SolarTermStr;    
   }
}
