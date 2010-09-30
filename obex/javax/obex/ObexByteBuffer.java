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

package javax.obex;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class ObexByteBuffer {
    private static final int REALLOC_EXTRA_SPACE = 24;

    private byte[] mBuffer;

    private int mIndex;

    private int mLength;

    public ObexByteBuffer(int initialSize) {
        mBuffer = new byte[initialSize];
        mIndex = 0;
        mLength = 0;
    }

    /**
     * Mark bytes at beginning or valid data as invalid.
     * @param numBytes Number of bytes to consume.
     */
    private void consume(int numBytes) {
        mLength -= numBytes;
        if (mLength > 0) {
            mIndex += numBytes;
        } else {
            mIndex = 0;
        }
    }

    /**
     * Make room in for new data (if needed).
     * @param numBytes Number of bytes to make room for.
     */
    private void acquire(int numBytes) {
        int remainingSpace = mBuffer.length - (mIndex + mLength);

        // Do we need to grow or shuffle?
        if (remainingSpace < numBytes) {
            int availableSpace = mBuffer.length - mLength;
            if (availableSpace < numBytes) {
                // Need to grow. Add some extra space to avoid small growth.
                byte[] newbuf = new byte[mLength + numBytes + REALLOC_EXTRA_SPACE];
                System.arraycopy(mBuffer, mIndex, newbuf, 0, mLength);
                mBuffer = newbuf;
            } else {
                // Need to shuffle
                System.arraycopy(mBuffer, mIndex, mBuffer, 0, mLength);
            }
            mIndex = 0;
        }
    }

    /**
     * Get the internal byte array. Use with care.
     * @return the internal byte array
     */
    public byte[] getBytes() {
        return mBuffer;
    }

    /**
     * Get number of written but not consumed bytes.
     * @return number of bytes
     */
    public int getLength() {
        return mLength;
    }

    /**
     * Discard all unconsumed bytes.
     */
    public void reset() {
        mIndex = 0;
        mLength = 0;
    }

    /**
     * Read and consume one byte.
     * @return Next unconsumed byte.
     */
    public byte read() {
        if (mLength == 0) {
            throw new ArrayIndexOutOfBoundsException();
        }
        mLength--;
        return mBuffer[mIndex++];
    }

    /**
     * Read and consume bytes, and write them into a byte array.
     * Will read (dest.length - destOffset) bytes.
     * @param dest Array to copy data into.
     * @param destOffset Where to start writing in dest.
     * @return number of read bytes.
     */
    public int read(byte[] dest, int destOffset) {
        return read(dest, destOffset, mLength);
    }

    /**
     * Read and consume bytes, and write them into a byte array.
     * Will read (length - destOffset) bytes.
     * @param dest Array to copy data into.
     * @param destOffset Where to start writing in dest.
     * @param length Number of bytes to read.
     * @return number of read bytes.
     */
    public int read(byte[] dest, int destOffset, int length) {
        peek(0, dest, destOffset, length);
        consume(length);
        return length;
    }

    /**
     * Read and consume bytes, and write them into another ObexByteBuffer.
     * @param dest ObexByteBuffer to copy data into.
     * @param length Number of bytes to read.
     * @return number of read bytes.
     */
    public int read(ObexByteBuffer dest, int length) {
        if (length > mLength) {
            throw new ArrayIndexOutOfBoundsException();
        }
        dest.write(mBuffer, mIndex, length);
        consume(length);

        return length;
    }

    /**
     * Read and consume all unconsumed bytes, and write them into an OutputStream.
     * @param dest OutputStream to copy data into.
     * @return number of read bytes.
     */
    public int read(OutputStream stream) throws IOException {
        return read(stream, mLength);
    }

    /**
     * Read and consume bytes, and write them into an OutputStream.
     * @param dest OutputStream to copy data into.
     * @param length Number of bytes to read.
     * @return number of read bytes.
     */
    public int read(OutputStream destStream, int length) throws IOException {
        peek(destStream, length);
        consume(length);
        return length;
    }

    /**
     * Read (but don't consume) one byte.
     * @param offset Offset into unconsumed bytes.
     * @return Requested unconsumed byte.
     */
    public byte peek(int offset) {
        if (offset > mLength) {
            throw new ArrayIndexOutOfBoundsException();
        }
        return mBuffer[mIndex + offset];
    }

    /**
     * Read (but don't consume) bytes and write them into a byte array.
     * Will read dest.length bytes.
     * @param offset Offset into unconsumed bytes.
     * @param dest Array to copy data into.
     */
    public void peek(int offset, byte[] dest) {
        peek(offset, dest, 0, dest.length);
    }

    /**
     * Read (but don't consume) bytes and write them into a byte array.
     * Will read (length - destOffset) bytes.
     * @param offset Offset into unconsumed bytes.
     * @param dest Array to copy data into.
     * @param destOffset Where to start writing in dest.
     * @param length Number of bytes to read.
     */
    public void peek(int offset, byte[] dest, int destOffset, int length) {
        if (offset > mLength || (offset + length) > mLength) {
            throw new ArrayIndexOutOfBoundsException();
        }
        System.arraycopy(mBuffer, mIndex + offset, dest, destOffset, length);
    }

    /**
     * Read (but don't consume) bytes, and write them into an OutputStream.
     * @param dest OutputStream to copy data into.
     * @param length Number of bytes to read.
     */
    public void peek(OutputStream stream, int length) throws IOException {
        if (length > mLength) {
            throw new ArrayIndexOutOfBoundsException();
        }
        stream.write(mBuffer, mIndex, length);
    }

    /**
     * Write a new byte.
     * @param src Byte to write.
     */
    public void write(byte src) {
        acquire(1);
        mBuffer[mIndex + mLength] = src;
        mLength++;
    }

    /**
     * Read bytes from a byte array and add to unconsumed bytes.
     * Will read/write src.length bytes.
     * @param src Array to read from.
     */
    public void write(byte[] src) {
        write(src, 0, src.length);
    }

    /**
     * Read bytes from a byte array and add to unconsumed bytes.
     * Will read/write (src.length - srcOffset) bytes.
     * @param src Array to read from.
     * @param srcOffset Offset into source array.
     */
    public void write(byte[] src, int srcOffset) {
        write(src, srcOffset, src.length - srcOffset);
    }

    /**
     * Read bytes from a byte array and add to unconsumed bytes.
     * Will read/write (srcLength - srcOffset) bytes.
     * @param src Array to read from.
     * @param srcOffset Offset into source array.
     * @param srcLength Number of bytes to read/write.
     */
    public void write(byte[] src, int srcOffset, int srcLength) {
        // Make sure we have space.
        acquire(srcLength);

        // Add the new data at the end
        System.arraycopy(src, srcOffset, mBuffer, mIndex + mLength, srcLength);
        mLength += srcLength;
    }

    /**
     * Read bytes from another ObexByteBuffer and add to unconsumed bytes.
     * Will read/write src.getLength() bytes. The bytes in src will not be consumed.
     * @param src ObexByteBuffer to read from.
     * @param srcOffset Offset into source array.
     */
    public void write(ObexByteBuffer src) {
        write(src.mBuffer, 0, src.getLength());
    }

    /**
     * Read bytes from another ObexByteBuffer and add to unconsumed bytes.
     * Will read/write (src.getLength() - srcOffset) bytes. The bytes in src will not
     * be consumed.
     * @param src ObexByteBuffer to read from.
     * @param srcOffset Offset into source array.
     */
    public void write(ObexByteBuffer src, int srcOffset) {
        if (srcOffset > src.mLength) {
            throw new ArrayIndexOutOfBoundsException();
        }
        write(src.mBuffer, src.mIndex + srcOffset, src.mLength - src.mIndex - srcOffset);
    }

    /**
     * Read bytes from another ObexByteBuffer and add to unconsumed bytes.
     * Will read/write (srcLength - srcOffset) bytes. The bytes in src will not be
     * consumed.
     * @param src ObexByteBuffer to read from.
     * @param srcOffset Offset into source array.
     * @param srcLength Number of bytes to read/write.
     */
    public void write(ObexByteBuffer src, int srcOffset, int srcLength) {
        if (srcOffset > src.mLength || (srcOffset + srcLength) > src.mLength) {
            throw new ArrayIndexOutOfBoundsException();
        }
        write(src.mBuffer, src.mIndex + srcOffset, srcLength);
    }

    /**
     * Read bytes from an InputStream and add to unconsumed bytes.
     * @param src InputStream to read from
     * @param srcLength Number of bytes to read
     * @throws IOException
     */
    public void write(InputStream src, int srcLength) throws IOException {
        // First make sure we have space.
        acquire(srcLength);

        // Read data until the requested number of bytes have been read.
        int numBytes = 0;
        do {
            int readBytes = src.read(mBuffer, mIndex + mLength + numBytes, srcLength - numBytes);
            if (readBytes == -1) {
                throw new IOException();
            }
            numBytes += readBytes;
        } while (numBytes != srcLength);
        mLength += numBytes;
    }
}
