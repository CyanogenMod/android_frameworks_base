/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.layoutlib.bridge.util;


import com.android.internal.util.ArrayUtils;

import android.util.SparseArray;

import java.lang.ref.WeakReference;

/**
 * This is a custom {@link SparseArray} that uses {@link WeakReference} around the objects added
 * to it. When the array is compacted, not only deleted indices but also empty references
 * are removed, making the array efficient at removing references that were reclaimed.
 *
 * The code is taken from {@link SparseArray} directly and adapted to use weak references.
 *
 * Because our usage means that we never actually call {@link #remove(int)} or {@link #delete(int)},
 * we must manually check if there are reclaimed references to trigger an internal compact step
 * (which is normally only triggered when an item is manually removed).
 *
 * SparseArrays map integers to Objects.  Unlike a normal array of Objects,
 * there can be gaps in the indices.  It is intended to be more efficient
 * than using a HashMap to map Integers to Objects.
 */
@SuppressWarnings("unchecked")
public class SparseWeakArray<E> {

    private static final Object DELETED_REF = new Object();
    private static final WeakReference<?> DELETED = new WeakReference(DELETED_REF);
    private boolean mGarbage = false;

    /**
     * Creates a new SparseArray containing no mappings.
     */
    public SparseWeakArray() {
        this(10);
    }

    /**
     * Creates a new SparseArray containing no mappings that will not
     * require any additional memory allocation to store the specified
     * number of mappings.
     */
    public SparseWeakArray(int initialCapacity) {
        initialCapacity = ArrayUtils.idealIntArraySize(initialCapacity);

        mKeys = new int[initialCapacity];
        mValues = new WeakReference[initialCapacity];
        mSize = 0;
    }

    /**
     * Gets the Object mapped from the specified key, or <code>null</code>
     * if no such mapping has been made.
     */
    public E get(int key) {
        return get(key, null);
    }

    /**
     * Gets the Object mapped from the specified key, or the specified Object
     * if no such mapping has been made.
     */
    public E get(int key, E valueIfKeyNotFound) {
        int i = binarySearch(mKeys, 0, mSize, key);

        if (i < 0 || mValues[i] == DELETED || mValues[i].get() == null) {
            return valueIfKeyNotFound;
        } else {
            return (E) mValues[i].get();
        }
    }

    /**
     * Removes the mapping from the specified key, if there was any.
     */
    public void delete(int key) {
        int i = binarySearch(mKeys, 0, mSize, key);

        if (i >= 0) {
            if (mValues[i] != DELETED) {
                mValues[i] = DELETED;
                mGarbage = true;
            }
        }
    }

    /**
     * Alias for {@link #delete(int)}.
     */
    public void remove(int key) {
        delete(key);
    }

    /**
     * Removes the mapping at the specified index.
     */
    public void removeAt(int index) {
        if (mValues[index] != DELETED) {
            mValues[index] = DELETED;
            mGarbage = true;
        }
    }

    private void gc() {
        int n = mSize;
        int o = 0;
        int[] keys = mKeys;
        WeakReference<?>[] values = mValues;

        for (int i = 0; i < n; i++) {
            WeakReference<?> val = values[i];

            // Don't keep any non DELETED values, but only the one that still have a valid
            // reference.
            if (val != DELETED && val.get() != null) {
                if (i != o) {
                    keys[o] = keys[i];
                    values[o] = val;
                }

                o++;
            }
        }

        mGarbage = false;
        mSize = o;

        int newSize = ArrayUtils.idealIntArraySize(mSize);
        if (newSize < mKeys.length) {
            int[] nkeys = new int[newSize];
            WeakReference<?>[] nvalues = new WeakReference[newSize];

            System.arraycopy(mKeys, 0, nkeys, 0, newSize);
            System.arraycopy(mValues, 0, nvalues, 0, newSize);

            mKeys = nkeys;
            mValues = nvalues;
        }
    }

    /**
     * Adds a mapping from the specified key to the specified value,
     * replacing the previous mapping from the specified key if there
     * was one.
     */
    public void put(int key, E value) {
        int i = binarySearch(mKeys, 0, mSize, key);

        if (i >= 0) {
            mValues[i] = new WeakReference(value);
        } else {
            i = ~i;

            if (i < mSize && (mValues[i] == DELETED || mValues[i].get() == null)) {
                mKeys[i] = key;
                mValues[i] = new WeakReference(value);
                return;
            }

            if (mSize >= mKeys.length && (mGarbage || hasReclaimedRefs())) {
                gc();

                // Search again because indices may have changed.
                i = ~binarySearch(mKeys, 0, mSize, key);
            }

            if (mSize >= mKeys.length) {
                int n = ArrayUtils.idealIntArraySize(mSize + 1);

                int[] nkeys = new int[n];
                WeakReference<?>[] nvalues = new WeakReference[n];

                // Log.e("SparseArray", "grow " + mKeys.length + " to " + n);
                System.arraycopy(mKeys, 0, nkeys, 0, mKeys.length);
                System.arraycopy(mValues, 0, nvalues, 0, mValues.length);

                mKeys = nkeys;
                mValues = nvalues;
            }

            if (mSize - i != 0) {
                // Log.e("SparseArray", "move " + (mSize - i));
                System.arraycopy(mKeys, i, mKeys, i + 1, mSize - i);
                System.arraycopy(mValues, i, mValues, i + 1, mSize - i);
            }

            mKeys[i] = key;
            mValues[i] = new WeakReference(value);
            mSize++;
        }
    }

    /**
     * Returns the number of key-value mappings that this SparseArray
     * currently stores.
     */
    public int size() {
        if (mGarbage) {
            gc();
        }

        return mSize;
    }

    /**
     * Given an index in the range <code>0...size()-1</code>, returns
     * the key from the <code>index</code>th key-value mapping that this
     * SparseArray stores.
     */
    public int keyAt(int index) {
        if (mGarbage) {
            gc();
        }

        return mKeys[index];
    }

    /**
     * Given an index in the range <code>0...size()-1</code>, returns
     * the value from the <code>index</code>th key-value mapping that this
     * SparseArray stores.
     */
    public E valueAt(int index) {
        if (mGarbage) {
            gc();
        }

        return (E) mValues[index].get();
    }

    /**
     * Given an index in the range <code>0...size()-1</code>, sets a new
     * value for the <code>index</code>th key-value mapping that this
     * SparseArray stores.
     */
    public void setValueAt(int index, E value) {
        if (mGarbage) {
            gc();
        }

        mValues[index] = new WeakReference(value);
    }

    /**
     * Returns the index for which {@link #keyAt} would return the
     * specified key, or a negative number if the specified
     * key is not mapped.
     */
    public int indexOfKey(int key) {
        if (mGarbage) {
            gc();
        }

        return binarySearch(mKeys, 0, mSize, key);
    }

    /**
     * Returns an index for which {@link #valueAt} would return the
     * specified key, or a negative number if no keys map to the
     * specified value.
     * Beware that this is a linear search, unlike lookups by key,
     * and that multiple keys can map to the same value and this will
     * find only one of them.
     */
    public int indexOfValue(E value) {
        if (mGarbage) {
            gc();
        }

        for (int i = 0; i < mSize; i++)
            if (mValues[i].get() == value)
                return i;

        return -1;
    }

    /**
     * Removes all key-value mappings from this SparseArray.
     */
    public void clear() {
        int n = mSize;
        WeakReference<?>[] values = mValues;

        for (int i = 0; i < n; i++) {
            values[i] = null;
        }

        mSize = 0;
        mGarbage = false;
    }

    /**
     * Puts a key/value pair into the array, optimizing for the case where
     * the key is greater than all existing keys in the array.
     */
    public void append(int key, E value) {
        if (mSize != 0 && key <= mKeys[mSize - 1]) {
            put(key, value);
            return;
        }

        if (mSize >= mKeys.length && (mGarbage || hasReclaimedRefs())) {
            gc();
        }

        int pos = mSize;
        if (pos >= mKeys.length) {
            int n = ArrayUtils.idealIntArraySize(pos + 1);

            int[] nkeys = new int[n];
            WeakReference<?>[] nvalues = new WeakReference[n];

            // Log.e("SparseArray", "grow " + mKeys.length + " to " + n);
            System.arraycopy(mKeys, 0, nkeys, 0, mKeys.length);
            System.arraycopy(mValues, 0, nvalues, 0, mValues.length);

            mKeys = nkeys;
            mValues = nvalues;
        }

        mKeys[pos] = key;
        mValues[pos] = new WeakReference(value);
        mSize = pos + 1;
    }

    private boolean hasReclaimedRefs() {
        for (int i = 0 ; i < mSize ; i++) {
            if (mValues[i].get() == null) { // DELETED.get() never returns null.
                return true;
            }
        }

        return false;
    }

    private static int binarySearch(int[] a, int start, int len, int key) {
        int high = start + len, low = start - 1, guess;

        while (high - low > 1) {
            guess = (high + low) / 2;

            if (a[guess] < key)
                low = guess;
            else
                high = guess;
        }

        if (high == start + len)
            return ~(start + len);
        else if (a[high] == key)
            return high;
        else
            return ~high;
    }

    private int[] mKeys;
    private WeakReference<?>[] mValues;
    private int mSize;
}
