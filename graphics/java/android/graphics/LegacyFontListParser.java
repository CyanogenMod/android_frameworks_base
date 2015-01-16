/*
 * Copyright (C) 2014 The CyanogenMod Project
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
package android.graphics;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses an XML font config. Example:
 *
 *<familyset>
 *
 *  <family>
 *      <nameset>
 *          <name>sans-serif</name>
 *          <name>arial</name>
 *      </nameset>
 *      <fileset>
 *          <file>Roboto-Regular.ttf</file>
 *          <file>Roboto-Bold.ttf</file>
 *          <file>Roboto-Italic.ttf</file>
 *          <file>Roboto-BoldItalic.ttf</file>
 *      </fileset>
 *  </family>
 *  <family>
 *    ...
 *  </family>
 *</familyset>
 * @hide
 */
public class LegacyFontListParser {
    public static class Family {
        public List<String> nameset = new ArrayList<String>();
        public List<String> fileset = new ArrayList<String>();

        public String getName() {
            if (nameset != null && !nameset.isEmpty()) {
                return nameset.get(0);
            }
            return null;
        }
    }

    public static List<Family> parse(InputStream in)
            throws XmlPullParserException, IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, null);
            parser.nextTag();
            return readFamilySet(parser);
        } finally {
            in.close();
        }
    }

    private static List<Family> readFamilySet(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        List<Family> families = new ArrayList<Family>();
        parser.require(XmlPullParser.START_TAG, null, "familyset");

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();

            // Starts by looking for the entry tag
            if (name.equals("family")) {
                Family family = readFamily(parser);
                families.add(family);
            }
        }
        return families;
    }

    private static Family readFamily(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        Family family = new Family();
        parser.require(XmlPullParser.START_TAG, null, "family");

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("nameset")) {
                List<String> nameset = readNameset(parser);
                family.nameset = nameset;
            } else if (name.equals("fileset")) {
                List<String> fileset = readFileset(parser);
                family.fileset = fileset;
            } else {
                skip(parser);
            }
        }
        return family;
    }

    private static List<String> readNameset(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        List<String> names = new ArrayList<String>();
        parser.require(XmlPullParser.START_TAG, null, "nameset");

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagname = parser.getName();
            if (tagname.equals("name")) {
                String name = readText(parser);
                names.add(name);
            } else {
                skip(parser);
            }
        }
        return names;
    }

    private static List<String> readFileset(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        List<String> files = new ArrayList<String>();
        parser.require(XmlPullParser.START_TAG, null, "fileset");

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("file")) {
                String file = readText(parser);
                files.add(file);
            } else {
                skip(parser);
            }
        }
        return files;
    }

    // For the tags title and summary, extracts their text values.
    private static String readText(XmlPullParser parser)
            throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    private static void skip(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
            case XmlPullParser.END_TAG:
                depth--;
                break;
            case XmlPullParser.START_TAG:
                depth++;
                break;
            }
        }
    }
}