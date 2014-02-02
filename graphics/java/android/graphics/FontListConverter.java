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

import android.graphics.FontListParser;
import android.graphics.FontListParser.Alias;
import android.graphics.FontListParser.Font;
import android.graphics.LegacyFontListParser.Family;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

/**
 * Converts a list of Family to FontListParser.Config
 * {@hide}
 */
public class FontListConverter {
    // These array values were determined by the order
    // of fonts in a fileset. The order is:
    // "Normal, Bold, Italic, BoldItalic"
    // Additionally the weight values in L's fonts.xml
    // are used to determine a generic weight value for each type
    // e.g The 2nd entry in a fileset is the bold font.
    protected static final int[] WEIGHTS = { 400, 700, 400, 700 };
    protected static boolean[] ITALICS = { false, false, true, true };
    protected static final int DEFAULT_WEIGHT = WEIGHTS[0];

    // Arbitrarily chosen list based on L's fonts.xml.
    // There could be more out there, but we can't use a generic pattern of "fontName-styleName"
    // as "sans-serif" would be translated as a font called "sans" with the style "serif".
    public static final String[] STYLES = {
        "thin",
        "light",
        "medium",
        "black"
    };

    // Maps a "normal" family to a list of similar families differing by style
    // Example: "sans-serif" -> { sans-serif-thin, sans-serif-light, sans-serif-medium }
    private HashMap<Family, List<Family>> mFamilyVariants = new HashMap<Family, List<Family>>();
    private List<Family> mFamilies =
            new ArrayList<Family >();
    private String mFontDir;


    public FontListConverter(List<Family> families, String fontDir) {
        mFamilies.addAll(families);
        mFontDir = fontDir;
        findFamilyVariants();
    }

    public FontListConverter(Family family, String fontDir) {
        mFamilies.add(family);
        mFontDir = fontDir;
        findFamilyVariants();
    }

    private void findFamilyVariants() {
        for(Family family : mFamilies) {
            if (isNormalStyle(family)) {
                List<Family> variants = findVariants(family, mFamilies);
                mFamilyVariants.put(family, variants);
            }
        }
    }

    private List<Family> findVariants(Family normalFamily, List<Family> legacyFamilies) {
        List<Family> variants = new ArrayList<Family>();

        String normalFamilyName = normalFamily.getName();

        for(Family family : legacyFamilies) {
            String name = family.getName();

            if (name.startsWith(normalFamilyName) && !isNormalStyle(family)) {
                variants.add(family);
            }
        }
        return variants;
    }

    public FontListParser.Config convert() {
        FontListParser.Config config = new FontListParser.Config();
        config.families.addAll(convertFamilies());
        config.aliases.addAll(createAliases());
        return config;
    }

    /**
     *  A "normal" style is just standard font,
     *  eg Roboto is normal. Roboto-Thin is styled.
     */
    protected boolean isNormalStyle(Family family) {
        String name = family.getName();
        if (name == null) return false;

        for(String style : STYLES) {
            if (name.endsWith('-' + style)) {
                return false;
            }
        }
        return true;
    }

    protected List<FontListParser.Family> convertFamilies() {
        List<FontListParser.Family> convertedFamilies = new ArrayList<FontListParser.Family>();

        // Only convert normal families. Each normal family will add in its variants
        for(Family family : mFamilyVariants.keySet()) {
            convertedFamilies.add(convertFamily(family));
        }

        return convertedFamilies;
    }

    protected FontListParser.Family convertFamily(Family legacyFamily) {
        List<String> nameset = legacyFamily.nameset;
        List<String> fileset = legacyFamily.fileset;

        // Arbitrarily choose the first entry in the nameset to be the name
        String name = nameset.isEmpty() ? null : nameset.get(0);

        List<Font> fonts = convertFonts(fileset);

        // Add fonts from other variants
        for(Family variantFamily : mFamilyVariants.get(legacyFamily)) {
            fonts.addAll(convertFonts(variantFamily.fileset));
        }

        return new FontListParser.Family(name, fonts, null, null);
    }

    protected List<FontListParser.Font> convertFonts(List<String> fileset) {
        List<Font> fonts = new ArrayList<Font>();

        for(int i=0; i < fileset.size(); i++) {
            String fullpath = mFontDir + File.separatorChar + fileset.get(i);
            // fileset should only be 4 entries, but if
            // its more we can just assign a default.
            int weight = i < WEIGHTS.length ? WEIGHTS[i] : DEFAULT_WEIGHT;
            boolean isItalic = i < ITALICS.length ? ITALICS[i] : false;

            Font font = new Font(fullpath, weight, isItalic);
            fonts.add(font);
        }

        return fonts;
    }

    protected List<Alias> createAliases() {
        List<Alias> aliases = new ArrayList<Alias>();

        for(Family family : mFamilyVariants.keySet()) {
            // Get any aliases that might be from a normal family's nameset.
            // eg sans-serif, arial, helvetica, tahoma etc.
            if (isNormalStyle(family)) {
                aliases.addAll(adaptNamesetAliases(family.nameset));
            }
        }

        aliases.addAll(getAliasesForRelatedFamilies());

        return aliases;
    }

    private List<Alias> getAliasesForRelatedFamilies() {
        List<Alias> aliases = new ArrayList<Alias>();

        for(Entry<Family, List<Family>> entry : mFamilyVariants.entrySet()) {
            String toName = entry.getKey().nameset.get(0);
            List<Family> relatedFamilies = entry.getValue();
            for(Family relatedFamily : relatedFamilies) {
                aliases.addAll(adaptNamesetAliases(relatedFamily.nameset, toName));
            }
        }
        return aliases;
    }

    private List<Alias> adaptNamesetAliases(List<String> nameset, String toName) {
        List<Alias> aliases = new ArrayList<Alias>();
        for(String name : nameset) {
            Alias alias = new Alias();
            alias.name = name;
            alias.toName = toName;
            aliases.add(alias);
        }
        return aliases;
    }

    private List<Alias> adaptNamesetAliases(List<String> nameset) {
        List<Alias> aliases = new ArrayList<Alias>();
        if (nameset.size() < 2) return aliases; // An alias requires a name and toName

        String toName = nameset.get(0);
        for(int i = 1; i < nameset.size(); i++) {
            Alias alias = new Alias();
            alias.name = nameset.get(i);
            alias.toName = toName;
            aliases.add(alias);
        }

        return aliases;
    }
}
