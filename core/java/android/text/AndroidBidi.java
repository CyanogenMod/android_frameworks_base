/*
 * Copyright (C) 2010 The Android Open Source Project
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

/**
 * Access the ICU bidi implementation.
 * @hide
 */
/* package */ class AndroidBidi {

    public static int bidi(int dir, char[] chs, byte[] chInfo, int n, boolean haveInfo) {
        if (chs == null || chInfo == null) {
            throw new NullPointerException();
        }

        if (n < 0 || chs.length < n || chInfo.length < n) {
            throw new IndexOutOfBoundsException();
        }

        switch(dir) {
            case Layout.DIR_REQUEST_LTR: dir = 0; break;
            case Layout.DIR_REQUEST_RTL: dir = 1; break;
            case Layout.DIR_REQUEST_DEFAULT_LTR: dir = -2; break;
            case Layout.DIR_REQUEST_DEFAULT_RTL: dir = -1; break;
            default: dir = 0; break;
        }

        int result = runBidi(dir, chs, chInfo, n, haveInfo);
        result = (result & 0x1) == 0 ? Layout.DIR_LEFT_TO_RIGHT : Layout.DIR_RIGHT_TO_LEFT;
        return result;
    }

    /**
     * @author: Eyad Aboulouz
     * Bidi text reordering and reshaping by by calling native reorderReshapeBidiText function
     * @param chs
     * @param reshapedChs
     * @param off
     * @param len
     * @return int
     * @hide
     */
    public static int reorderAndReshapeBidiText(char[] chs, char[] outputChs, int off, int len) {

        if (chs == null)
            throw new NullPointerException();

        if (off < 0 || len < 0 || off + len > chs.length)
            throw new IndexOutOfBoundsException();

        return reorderReshapeBidiText(chs, outputChs, off, len);
    }

    /**
     * @author: Eyad Aboulouz
     * Arabic text reshaping by by calling native reshapeArabicText function
     * @param chs
     * @param map
     * @param off
     * @param len
     * @return int
     * @hide
     */
    public static int reshapeReversedArabicText(char[] chs, char[] outputChs, int off, int len) {

        if (chs == null)
            throw new NullPointerException();

        if (off < 0 || len < 0 || off + len > chs.length)
            throw new IndexOutOfBoundsException();

        return reshapeArabicText(chs, outputChs, off, len);
    }

    private native static int runBidi(int dir, char[] chs, byte[] chInfo, int n, boolean haveInfo);

    private native static int reorderReshapeBidiText(char[] chs, char[] outputChs, int off, int len);

    private native static int reshapeArabicText(char[] chs, char[] outputChs, int off, int len);
}