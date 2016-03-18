/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.test.uibench;

import java.util.Random;

public class TextUtils {
    private static final int STRING_COUNT = 200;
    private static final int SIMPLE_STRING_LENGTH = 10;

    /**
     * Create word of random assortment of lower/upper case letters
     */
    private static String randomWord(Random random, int length) {
        String result = "";
        for (int j = 0; j < length; j++) {
            // add random letter
            int base = random.nextInt(2) == 0 ? 'A' : 'a';
            result += (char)(random.nextInt(26) + base);
        }
        return result;
    }

    public static String[] buildSimpleStringList() {
        String[] strings = new String[STRING_COUNT];
        Random random = new Random(0);
        for (int i = 0; i < strings.length; i++) {
            strings[i] = randomWord(random, SIMPLE_STRING_LENGTH);
        }
        return strings;
    }

    // a small number of strings reused frequently, expected to hit
    // in the word-granularity text layout cache
    static final String[] CACHE_HIT_STRINGS = new String[] {
            "a",
            "small",
            "number",
            "of",
            "strings",
            "reused",
            "frequently"
    };

    private static final int WORDS_IN_PARAGRAPH = 150;

    // misses are fairly long 'words' to ensure they miss
    private static final int PARAGRAPH_MISS_MIN_LENGTH = 4;
    private static final int PARAGRAPH_MISS_MAX_LENGTH = 9;

    static String[] buildParagraphListWithHitPercentage(int hitPercentage) {
        if (hitPercentage < 0 || hitPercentage > 100) throw new IllegalArgumentException();

        String[] strings = new String[STRING_COUNT];
        Random random = new Random(0);
        for (int i = 0; i < strings.length; i++) {
            String result = "";
            for (int word = 0; word < WORDS_IN_PARAGRAPH; word++) {
                if (word != 0) {
                    result += " ";
                }
                if (random.nextInt(100) < hitPercentage) {
                    // add a common word, which is very likely to hit in the cache
                    result += CACHE_HIT_STRINGS[random.nextInt(CACHE_HIT_STRINGS.length)];
                } else {
                    // construct a random word, which will *most likely* miss
                    int length = PARAGRAPH_MISS_MIN_LENGTH;
                    length += random.nextInt(PARAGRAPH_MISS_MAX_LENGTH - PARAGRAPH_MISS_MIN_LENGTH);

                    result += randomWord(random, length);
                }
            }
            strings[i] = result;
        }

        return strings;
    }
}
