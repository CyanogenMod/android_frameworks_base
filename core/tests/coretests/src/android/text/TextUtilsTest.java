/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.text;

import android.graphics.Paint;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.test.MoreAsserts;

import com.google.android.collect.Lists;
import com.google.android.collect.Maps;

import junit.framework.TestCase;

import java.util.List;
import java.util.Map;

/**
 * TextUtilsTest tests {@link TextUtils}.
 */
public class TextUtilsTest extends TestCase {

    @SmallTest
    public void testBasic() throws Exception {
        assertEquals("", TextUtils.concat());
        assertEquals("foo", TextUtils.concat("foo"));
        assertEquals("foobar", TextUtils.concat("foo", "bar"));
        assertEquals("foobarbaz", TextUtils.concat("foo", "bar", "baz"));

        SpannableString foo = new SpannableString("foo");
        foo.setSpan("foo", 1, 2, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);

        SpannableString bar = new SpannableString("bar");
        bar.setSpan("bar", 1, 2, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);

        SpannableString baz = new SpannableString("baz");
        baz.setSpan("baz", 1, 2, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);

        assertEquals("foo", TextUtils.concat(foo).toString());
        assertEquals("foobar", TextUtils.concat(foo, bar).toString());
        assertEquals("foobarbaz", TextUtils.concat(foo, bar, baz).toString());

        assertEquals(1, ((Spanned) TextUtils.concat(foo)).getSpanStart("foo"));

        assertEquals(1, ((Spanned) TextUtils.concat(foo, bar)).getSpanStart("foo"));
        assertEquals(4, ((Spanned) TextUtils.concat(foo, bar)).getSpanStart("bar"));

        assertEquals(1, ((Spanned) TextUtils.concat(foo, bar, baz)).getSpanStart("foo"));
        assertEquals(4, ((Spanned) TextUtils.concat(foo, bar, baz)).getSpanStart("bar"));
        assertEquals(7, ((Spanned) TextUtils.concat(foo, bar, baz)).getSpanStart("baz"));

        assertTrue(TextUtils.concat("foo", "bar") instanceof String);
        assertTrue(TextUtils.concat(foo, bar) instanceof SpannedString);
    }

    @SmallTest
    public void testTemplateString() throws Exception {
        CharSequence result;

        result = TextUtils.expandTemplate("This is a ^1 of the ^2 broadcast ^3.",
                                          "test", "emergency", "system");
        assertEquals("This is a test of the emergency broadcast system.",
                     result.toString());

        result = TextUtils.expandTemplate("^^^1^^^2^3^a^1^^b^^^c",
                                          "one", "two", "three");
        assertEquals("^one^twothree^aone^b^^c",
                     result.toString());

        result = TextUtils.expandTemplate("^");
        assertEquals("^", result.toString());

        result = TextUtils.expandTemplate("^^");
        assertEquals("^", result.toString());

        result = TextUtils.expandTemplate("^^^");
        assertEquals("^^", result.toString());

        result = TextUtils.expandTemplate("shorter ^1 values ^2.", "a", "");
        assertEquals("shorter a values .", result.toString());

        try {
            TextUtils.expandTemplate("Only ^1 value given, but ^2 used.", "foo");
            fail();
        } catch (IllegalArgumentException e) {
        }

        try {
            TextUtils.expandTemplate("^1 value given, and ^0 used.", "foo");
            fail();
        } catch (IllegalArgumentException e) {
        }

        result = TextUtils.expandTemplate("^1 value given, and ^9 used.",
                                          "one", "two", "three", "four", "five",
                                          "six", "seven", "eight", "nine");
        assertEquals("one value given, and nine used.", result.toString());

        try {
            TextUtils.expandTemplate("^1 value given, and ^10 used.",
                                     "one", "two", "three", "four", "five",
                                     "six", "seven", "eight", "nine", "ten");
            fail();
        } catch (IllegalArgumentException e) {
        }

        // putting carets in the values: expansion is not recursive.

        result = TextUtils.expandTemplate("^2", "foo", "^^");
        assertEquals("^^", result.toString());

        result = TextUtils.expandTemplate("^^2", "foo", "1");
        assertEquals("^2", result.toString());

        result = TextUtils.expandTemplate("^1", "value with ^2 in it", "foo");
        assertEquals("value with ^2 in it", result.toString());
    }

    /** Fail unless text+spans contains a span 'spanName' with the given start and end. */
    private void checkContains(Spanned text, String[] spans, String spanName,
                               int start, int end) throws Exception {
        for (String i: spans) {
            if (i.equals(spanName)) {
                assertEquals(start, text.getSpanStart(i));
                assertEquals(end, text.getSpanEnd(i));
                return;
            }
        }
        fail();
    }

    @SmallTest
    public void testTemplateSpan() throws Exception {
        SpannableString template;
        Spanned result;
        String[] spans;

        // ordinary replacement

        template = new SpannableString("a^1b");
        template.setSpan("before", 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        template.setSpan("during", 1, 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        template.setSpan("after", 3, 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        template.setSpan("during+after", 1, 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        result = (Spanned) TextUtils.expandTemplate(template, "foo");
        assertEquals(5, result.length());
        spans = result.getSpans(0, result.length(), String.class);

        // value is one character longer, so span endpoints should change.
        assertEquals(4, spans.length);
        checkContains(result, spans, "before", 0, 1);
        checkContains(result, spans, "during", 1, 4);
        checkContains(result, spans, "after", 4, 5);
        checkContains(result, spans, "during+after", 1, 5);


        // replacement with empty string

        result = (Spanned) TextUtils.expandTemplate(template, "");
        assertEquals(2, result.length());
        spans = result.getSpans(0, result.length(), String.class);

        // the "during" span should disappear.
        assertEquals(3, spans.length);
        checkContains(result, spans, "before", 0, 1);
        checkContains(result, spans, "after", 1, 2);
        checkContains(result, spans, "during+after", 1, 2);
    }

    @SmallTest
    public void testStringSplitterSimple() {
        stringSplitterTestHelper("a,b,cde", new String[] {"a", "b", "cde"});
    }

    @SmallTest
    public void testStringSplitterEmpty() {
        stringSplitterTestHelper("", new String[] {});
    }

    @SmallTest
    public void testStringSplitterWithLeadingEmptyString() {
        stringSplitterTestHelper(",a,b,cde", new String[] {"", "a", "b", "cde"});
    }

    @SmallTest
    public void testStringSplitterWithInternalEmptyString() {
        stringSplitterTestHelper("a,b,,cde", new String[] {"a", "b", "", "cde"});
    }

    @SmallTest
    public void testStringSplitterWithTrailingEmptyString() {
        // A single trailing emtpy string should be ignored.
        stringSplitterTestHelper("a,b,cde,", new String[] {"a", "b", "cde"});
    }

    private void stringSplitterTestHelper(String string, String[] expectedStrings) {
        TextUtils.StringSplitter splitter = new TextUtils.SimpleStringSplitter(',');
        splitter.setString(string);
        List<String> strings = Lists.newArrayList();
        for (String s : splitter) {
            strings.add(s);
        }
        MoreAsserts.assertEquals(expectedStrings, strings.toArray(new String[]{}));
    }

    @SmallTest
    public void testTrim() {
        String[] strings = { "abc", " abc", "  abc", "abc ", "abc  ",
                             " abc ", "  abc  ", "\nabc\n", "\nabc", "abc\n" };

        for (String s : strings) {
            assertEquals(s.trim().length(), TextUtils.getTrimmedLength(s));
        }
    }

    @SmallTest
    public void testRfc822TokenizerFullAddress() {
        Rfc822Token[] tokens = Rfc822Tokenizer.tokenize("Foo Bar (something) <foo@google.com>");
        assertNotNull(tokens);
        assertEquals(1, tokens.length);
        assertEquals("foo@google.com", tokens[0].getAddress());
        assertEquals("Foo Bar", tokens[0].getName());
        assertEquals("something",tokens[0].getComment());
    }

    @SmallTest
    public void testRfc822TokenizeItemWithError() {
        Rfc822Token[] tokens = Rfc822Tokenizer.tokenize("\"Foo Bar\\");
        assertNotNull(tokens);
        assertEquals(1, tokens.length);
        assertEquals("Foo Bar", tokens[0].getAddress());
    }

    @SmallTest
    public void testRfc822FindToken() {
        Rfc822Tokenizer tokenizer = new Rfc822Tokenizer();
        //                0           1         2           3         4
        //                0 1234 56789012345678901234 5678 90123456789012345
        String address = "\"Foo\" <foo@google.com>, \"Bar\" <bar@google.com>";
        assertEquals(0, tokenizer.findTokenStart(address, 21));
        assertEquals(22, tokenizer.findTokenEnd(address, 21));
        assertEquals(24, tokenizer.findTokenStart(address, 25));
        assertEquals(46, tokenizer.findTokenEnd(address, 25));
    }

    @SmallTest
    public void testRfc822FindTokenWithError() {
        assertEquals(9, new Rfc822Tokenizer().findTokenEnd("\"Foo Bar\\", 0));
    }

    @LargeTest
    public void testEllipsize() {
        CharSequence s1 = "The quick brown fox jumps over \u00FEhe lazy dog.";
        CharSequence s2 = new Wrapper(s1);
        Spannable s3 = new SpannableString(s1);
        s3.setSpan(new StyleSpan(0), 5, 10, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextPaint p = new TextPaint();
        p.setFlags(p.getFlags() & ~p.DEV_KERN_TEXT_FLAG);

        for (int i = 0; i < 100; i++) {
            for (int j = 0; j < 3; j++) {
                TextUtils.TruncateAt kind = null;

                switch (j) {
                case 0:
                    kind = TextUtils.TruncateAt.START;
                    break;

                case 1:
                    kind = TextUtils.TruncateAt.END;
                    break;

                case 2:
                    kind = TextUtils.TruncateAt.MIDDLE;
                    break;
                }

                String out1 = TextUtils.ellipsize(s1, p, i, kind).toString();
                String out2 = TextUtils.ellipsize(s2, p, i, kind).toString();
                String out3 = TextUtils.ellipsize(s3, p, i, kind).toString();

                String keep1 = TextUtils.ellipsize(s1, p, i, kind, true, null).toString();
                String keep2 = TextUtils.ellipsize(s2, p, i, kind, true, null).toString();
                String keep3 = TextUtils.ellipsize(s3, p, i, kind, true, null).toString();

                String trim1 = keep1.replace("\uFEFF", "");

                // Are all normal output strings identical?
                assertEquals("wid " + i + " pass " + j, out1, out2);
                assertEquals("wid " + i + " pass " + j, out2, out3);

                // Are preserved output strings identical?
                assertEquals("wid " + i + " pass " + j, keep1, keep2);
                assertEquals("wid " + i + " pass " + j, keep2, keep3);

                // Does trimming padding from preserved yield normal?
                assertEquals("wid " + i + " pass " + j, out1, trim1);

                // Did preserved output strings preserve length?
                assertEquals("wid " + i + " pass " + j, keep1.length(), s1.length());

                // Does the output string actually fit in the space?
                assertTrue("wid " + i + " pass " + j, p.measureText(out1) <= i);

                // Is the padded output the same width as trimmed output?
                assertTrue("wid " + i + " pass " + j, p.measureText(keep1) == p.measureText(out1));
            }
        }
    }

    @SmallTest
    public void testDelimitedStringContains() {
        assertFalse(TextUtils.delimitedStringContains("", ',', null));
        assertFalse(TextUtils.delimitedStringContains(null, ',', ""));
        // Whole match
        assertTrue(TextUtils.delimitedStringContains("gps", ',', "gps"));
        // At beginning.
        assertTrue(TextUtils.delimitedStringContains("gps,gpsx,network,mock", ',', "gps"));
        assertTrue(TextUtils.delimitedStringContains("gps,network,mock", ',', "gps"));
        // In middle, both without, before & after a false match.
        assertTrue(TextUtils.delimitedStringContains("network,gps,mock", ',', "gps"));
        assertTrue(TextUtils.delimitedStringContains("network,gps,gpsx,mock", ',', "gps"));
        assertTrue(TextUtils.delimitedStringContains("network,gpsx,gps,mock", ',', "gps"));
        // At the end.
        assertTrue(TextUtils.delimitedStringContains("network,mock,gps", ',', "gps"));
        assertTrue(TextUtils.delimitedStringContains("network,mock,gpsx,gps", ',', "gps"));
        // Not present (but with a false match)
        assertFalse(TextUtils.delimitedStringContains("network,mock,gpsx", ',', "gps"));
    }

    /**
     * CharSequence wrapper for testing the cases where text is copied into
     * a char array instead of working from a String or a Spanned.
     */
    private static class Wrapper implements CharSequence {
        private CharSequence mString;

        public Wrapper(CharSequence s) {
            mString = s;
        }

        public int length() {
            return mString.length();
        }

        public char charAt(int off) {
            return mString.charAt(off);
        }

        public String toString() {
            return mString.toString();
        }

        public CharSequence subSequence(int start, int end) {
            return new Wrapper(mString.subSequence(start, end));
        }
    }
}
