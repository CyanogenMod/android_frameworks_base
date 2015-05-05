/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.graphics;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for font config files.
 *
 * @hide
 */
public class FontListParser {

    public static class Config {
        Config() {
            families = new ArrayList<Family>();
            aliases = new ArrayList<Alias>();
        }
        public List<Family> families;
        public List<Alias> aliases;
    }

    public static class Font {
        Font(String fontName, int weight, boolean isItalic) {
            this.fontName = fontName;
            this.weight = weight;
            this.isItalic = isItalic;
        }
        public String fontName;
        public int weight;
        public boolean isItalic;
    }

    public static class Alias {
        public String name;
        public String toName;
        public int weight;
    }

    public static class Family {
        public Family(String name, List<Font> fonts, String lang, String variant) {
            this.name = name;
            this.fonts = fonts;
            this.lang = lang;
            this.variant = variant;
        }

        public String name;
        public List<Font> fonts;
        public String lang;
        public String variant;
    }

    /* Parse fallback list (no names) */
    public static Config parse(File configFilename, String fontDir)
            throws XmlPullParserException, IOException {
        FileInputStream in = null;
        in = new FileInputStream(configFilename);
        return FontListParser.parse(in, fontDir);
    }

    /* Parse fallback list (no names) */
    public static Config parse(InputStream in, String fontDir)
            throws XmlPullParserException, IOException {
        BufferedInputStream bis = null;
        try {
            // wrap input stream in a BufferedInputStream, if it's not already, for mark support
            if (!(in instanceof BufferedInputStream)) {
                bis = new BufferedInputStream(in);
            } else {
                bis = (BufferedInputStream) in;
            }
            // mark the beginning so we can reset to this position after checking format
            bis.mark(in.available());
            if (isLegacyFormat(bis)) {
                return parseLegacyFormat(bis, fontDir);
            } else {
                return parseNormalFormat(bis, fontDir);
            }
        } finally {
            if (bis != null) bis.close();
        }
    }

    public static boolean isLegacyFormat(InputStream in)
            throws XmlPullParserException, IOException {
        if (!in.markSupported()) {
            throw new IllegalArgumentException("InputStream does not support mark");
        }
        boolean isLegacy = false;

        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(in, null);
        parser.nextTag();
        parser.require(XmlPullParser.START_TAG, null, "familyset");
        String version = parser.getAttributeValue(null, "version");
        isLegacy = version == null;

        // reset the stream so we can read it
        in.reset();

        return isLegacy;
    }

    public static Config parseLegacyFormat(InputStream in, String dirName)
            throws XmlPullParserException, IOException {
        try {
            List<LegacyFontListParser.Family> legacyFamilies = LegacyFontListParser.parse(in);
            FontListConverter converter = new FontListConverter(legacyFamilies, dirName);
            return converter.convert();
        } finally {
            in.close();
        }
    }

    public static Config parseNormalFormat(InputStream in, String dirName)
            throws XmlPullParserException, IOException {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, null);
            parser.nextTag();
            return readFamilies(parser, dirName);
    }

    private static Config readFamilies(XmlPullParser parser, String dirPath)
            throws XmlPullParserException, IOException {
        Config config = new Config();
        parser.require(XmlPullParser.START_TAG, null, "familyset");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            if (parser.getName().equals("family")) {
                config.families.add(readFamily(parser, dirPath));
            } else if (parser.getName().equals("alias")) {
                config.aliases.add(readAlias(parser));
            } else {
                skip(parser);
            }
        }
        return config;
    }

    private static Family readFamily(XmlPullParser parser, String dirPath)
            throws XmlPullParserException, IOException {
        String name = parser.getAttributeValue(null, "name");
        String lang = parser.getAttributeValue(null, "lang");
        String variant = parser.getAttributeValue(null, "variant");
        List<Font> fonts = new ArrayList<Font>();
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            String tag = parser.getName();
            if (tag.equals("font")) {
                String weightStr = parser.getAttributeValue(null, "weight");
                int weight = weightStr == null ? 400 : Integer.parseInt(weightStr);
                boolean isItalic = "italic".equals(parser.getAttributeValue(null, "style"));
                String filename = parser.nextText();
                String fullFilename = dirPath + File.separatorChar + filename;
                fonts.add(new Font(fullFilename, weight, isItalic));
            } else {
                skip(parser);
            }
        }
        return new Family(name, fonts, lang, variant);
    }

    private static Alias readAlias(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        Alias alias = new Alias();
        alias.name = parser.getAttributeValue(null, "name");
        alias.toName = parser.getAttributeValue(null, "to");
        String weightStr = parser.getAttributeValue(null, "weight");
        if (weightStr == null) {
            alias.weight = 400;
        } else {
            alias.weight = Integer.parseInt(weightStr);
        }
        skip(parser);  // alias tag is empty, ignore any contents and consume end tag
        return alias;
    }

    private static void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        int depth = 1;
        while (depth > 0) {
            switch (parser.next()) {
            case XmlPullParser.START_TAG:
                depth++;
                break;
            case XmlPullParser.END_TAG:
                depth--;
                break;
            }
        }
    }
}
