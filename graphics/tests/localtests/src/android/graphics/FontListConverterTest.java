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

import android.graphics.FontListParser.Alias;
import android.graphics.LegacyFontListParser.Family;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

public class FontListConverterTest extends TestCase {
    // VALID nameset includes the default name first and
    // and some other 'aliases' with it.
    private static final String[] VALID_NAMESET = {
            "sans-serif",
            "arial",
            "helvetica",
            "tahoma",
            "verdana"
    };

    // The correct fileset will have 4 files in
    // order by type (regular, bold, italic, bolditalic)
    private static final String[] VALID_FILESET = {
        "Roboto-Regular.ttf",
        "Roboto-Bold.ttf",
        "Roboto-Italic.ttf",
        "Roboto-BoldItalic.ttf"
    };

    // The legacy fontlist format considered thin, light, and black styles
    // each as part of their own familysets. The new format does not, so we need
    // to provide a test case to adapt this. Note: "condensed" is still considered
    // to be its own familyset. So we must be careful
    private static final String[] VALID_ADDITIONAL_STYLE_NAMESET = {
        "sans-serif-thin"
    };

    private static final String[] VALID_ADDITIONAL_STYLE_FILESET = {
        "Roboto-Thin.ttf",
        "Roboto-ThinItalic.ttf"
    };

    // thin, light, and black styles are part of the same family but a Roboto "condensed"
    // or Roboto "slab" would be considered part of a different family. Since the legacy
    // format would already consider these as a different family, we just have to make sure
    // they don't get brought back into a common family like thin/light/black
    private static final String[] VALID_RELATED_FAMILY_NAMESET = {
        "sans-serif-condensed"
    };

    private static final String[] VALID_RELATED_FAMILY_FILESET = {
        "RobotoCondensed-Regular.ttf",
        "RobotoCondensed-Bold.ttf",
        "RobotoCondensed-Italic.ttf",
        "RobotoCondensed-BoldItalic.ttf"
    };

    // Some typefaces will only have one style.
    private static final String[] VALID_SINGLE_STYLE_FAMIlY_NAMESET = {
        "monospace"
    };
    private static final String[] VALID_SINGLE_STYLE_FAMIlY_FILESET = {
        "DroidSansMono.ttf"
    };

    final String VALID_PATH = "/valid/path/";

    private Family sValidFamily;  // eg "sans-serif"
    private Family sValidAdditionalStyleFamily; // eg "sans-serif-light"
    private Family sValidRelatedFamily; // eg "sans-serif-condensed"
    private Family mValidSingleStyleFamily; // eg "monospace" which only uses DroidSansMono.ttf

    protected void setUp() {
        sValidFamily = new Family();
        sValidFamily.nameset = new ArrayList<String>(Arrays.asList(VALID_NAMESET));
        sValidFamily.fileset = new ArrayList<String>(Arrays.asList(VALID_FILESET));

        sValidAdditionalStyleFamily = new Family();
        sValidAdditionalStyleFamily.nameset =
                new ArrayList<String>(Arrays.asList(VALID_ADDITIONAL_STYLE_NAMESET));
        sValidAdditionalStyleFamily.fileset =
                new ArrayList<String>(Arrays.asList(VALID_ADDITIONAL_STYLE_FILESET));

        sValidRelatedFamily = new Family();
        sValidRelatedFamily.nameset =
                new ArrayList<String>(Arrays.asList(VALID_RELATED_FAMILY_NAMESET));
        sValidRelatedFamily.fileset =
                new ArrayList<String>(Arrays.asList(VALID_RELATED_FAMILY_FILESET));

        mValidSingleStyleFamily = new Family();
    }

    @SmallTest
    public void testValidAdaptedFamilyShouldHaveNameOfNamesetsFirstElement() {
        FontListConverter adapter = new FontListConverter(sValidFamily, VALID_PATH);
        FontListParser.Family convertedFamily = adapter.convertFamily(sValidFamily);
        assertEquals(VALID_NAMESET[0], convertedFamily.name);
    }

    @SmallTest
    public void testValidAdaptedFamilyShouldHaveFonts() {
        FontListConverter adapter = new FontListConverter(sValidFamily, VALID_PATH);
        FontListParser.Family convertedFamily = adapter.convertFamily(sValidFamily);
        List<FontListParser.Font> fonts = convertedFamily.fonts;
        assertEquals(VALID_FILESET.length, fonts.size());
    }

    @SmallTest
    public void testValidAdaptedFontsShouldHaveCorrectProperties() {
        FontListConverter adapter = new FontListConverter(sValidFamily, VALID_PATH);
        List<FontListParser.Font> fonts = adapter.convertFonts(Arrays.asList(VALID_FILESET));

        assertEquals(VALID_FILESET.length, fonts.size());
        for(int i=0; i < fonts.size(); i++) {
            FontListParser.Font font = fonts.get(i);
            assertEquals(VALID_PATH + VALID_FILESET[i], font.fontName);
            assertEquals("shouldBeItalic", shouldBeItalic(i), font.isItalic);
            assertEquals(FontListConverter.WEIGHTS[i], font.weight);
        }
    }

    @SmallTest
    public void testExtraNamesetsShouldConvertToAliases() {
        List<Family> families = new ArrayList<Family>();
        families.add(sValidFamily);

        FontListConverter adapter = new FontListConverter(sValidFamily, VALID_PATH);
        List<FontListParser.Alias> aliases = adapter.createAliases();

        // Be sure the aliases point to the first name in the nameset
        for(int i = 0; i < aliases.size(); i++) {
            FontListParser.Alias alias = aliases.get(i);
            assertEquals(VALID_NAMESET[0], alias.toName);
        }

        // Be sure the extra namesets are in the alias list
        for(int i = 1; i < VALID_NAMESET.length; i++) {
            assertTrue("hasAliasWithName", hasAliasWithName(aliases, VALID_NAMESET[i]));
        }
    }

    /**
     *  The legacy format treats thin, light, and black fonts to be different families
     *  The new format treats these as all part of the original
     *  eg sans-serif and sans-serif-thin become one family
     */
    @SmallTest
    public void testAdditionalStylesShouldConvertToSameFamily() {
        List<Family> families = new ArrayList<Family>();
        families.add(sValidFamily); //eg "sans-serif"
        families.add(sValidAdditionalStyleFamily); //eg "sans-serif-light"

        FontListConverter adapter = new FontListConverter(families, VALID_PATH);
        List<FontListParser.Family> convertedFamilies = adapter.convertFamilies();

        // We started with two similiar families, and now should have one
        assertEquals(1, convertedFamilies.size());

        // The name of the family should be the base name, no style modifiers
        // ie "sans-serif" not "sans-serif-light"
        FontListParser.Family convertedFamily = convertedFamilies.get(0);
        assertEquals(sValidFamily.nameset.get(0), convertedFamily.name);

        // Verify all the fonts from both families exist now in the converted Family
        List<String> combinedFileSet = new ArrayList<String>();
        combinedFileSet.addAll(sValidFamily.fileset);
        combinedFileSet.addAll(sValidAdditionalStyleFamily.fileset);
        for(String filename : combinedFileSet) {
            String fontName = VALID_PATH + filename;
            assertTrue("hasFontWithName", hasFontWithName(convertedFamily, fontName));
        }
    }

    /**
     *  When two families combine, the "varied" family (ie light, light, black) should
     *  have their namesets converted to aliases.
     *  IE sans-serif-light should point to sans-serif because the light family
     *  gets merged to sans-serif
     */
    @SmallTest
    public void testAdditionalStylesNamesShouldBecomeAliases() {
        List<Family> families = new ArrayList<Family>();
        families.add(sValidFamily); //eg "sans-serif"
        families.add(sValidAdditionalStyleFamily); //eg "sans-serif-light"

        FontListConverter adapter = new FontListConverter(families, VALID_PATH);
        List<Alias> aliases = adapter.createAliases();

        // Subtract 1 from the total length since VALID_NAMESET[0] will be the family name
        int expectedSize = VALID_NAMESET.length + VALID_ADDITIONAL_STYLE_NAMESET.length - 1;
        assertEquals(expectedSize, aliases.size());

        // All aliases should point at the base family
        for(Alias alias : aliases) {
            assertEquals(VALID_NAMESET[0], alias.toName);
        }

        // There should be an alias for every name in the merged in family
        for(String name : VALID_ADDITIONAL_STYLE_NAMESET) {
            assertTrue("hasAliasWithName", hasAliasWithName(aliases, name));
        }
    }

    /**
     * sans-serif-condensed should not get merged in with sans-serif
     */
    @SmallTest
    public void testSimiliarFontsShouldKeepSameFamily() {
        List<Family> families = new ArrayList<Family>();
        families.add(sValidFamily); //eg "sans-serif"
        families.add(sValidRelatedFamily); //eg "sans-serif-condensed"

        FontListConverter adapter = new FontListConverter(families, VALID_PATH);
        List<FontListParser.Family> convertedFamilies = adapter.convertFamilies();
        FontListParser.Family convertedValidFamily =
                getFontListFamilyWithName(convertedFamilies, VALID_NAMESET[0]);
        FontListParser.Family convertedRelatedFamily =
                getFontListFamilyWithName(convertedFamilies, VALID_RELATED_FAMILY_NAMESET[0]);


        // Valid family should only have its own fonts. Will fail if these were merged
        for(String filename : sValidFamily.fileset) {
            String fontName = VALID_PATH + filename;
            assertTrue("hasFontWithName", hasFontWithName(convertedValidFamily, fontName));
            assertFalse("hasFontWIthName", hasFontWithName(convertedRelatedFamily, fontName));
        }

        // Related family should also only have have its own fonts. Will fail if these were merged
        for(String filename : sValidRelatedFamily.fileset) {
            String fontName = VALID_PATH + filename;
            assertTrue("hasFontWithName", hasFontWithName(convertedRelatedFamily, fontName));
            assertFalse("hasFontWIthName", hasFontWithName(convertedValidFamily, fontName));
        }
    }

    private static boolean hasAliasWithName(List<Alias> aliases, String name) {
        for (Alias alias : aliases) if (name.equals(alias.name)) return true;
        return false;
    }

    private static boolean hasFontWithName(FontListParser.Family family, String name) {
        for (FontListParser.Font font : family.fonts) {
            if(font.fontName != null && font.fontName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static FontListParser.Family getFontListFamilyWithName(
            List<FontListParser.Family> families, String name) {
        for(FontListParser.Family family : families) {
            if (name.equals(family.name)) return family;
        }
        return null;
    }

    private boolean shouldBeItalic(int index) {
        // Since the fileset format is regular, bold, italic, bolditalic, anything >= 2 is italic
        return index >= 2;
    }
}
