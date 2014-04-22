/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.media;

import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Hashtable;

/**
 * This class handles the mini-thumb file. A mini-thumb file consists
 * of blocks, indexed by id. Each block has BYTES_PER_MINTHUMB bytes in the
 * following format:
 *
 * 1 byte status (0 = empty, 1 = mini-thumb available)
 * 8 bytes magic (a magic number to match what's in the database)
 * 4 bytes data length (LEN)
 * LEN bytes jpeg data
 * (the remaining bytes are unused)
 *
 * @hide This file is shared between MediaStore and MediaProvider and should remained internal use
 *       only.
 */
public class MiniThumbFile {
    private static final String TAG = "MiniThumbFile";
    private static final int MINI_THUMB_DATA_FILE_VERSION = 3;
    public static final int BYTES_PER_MINTHUMB = 10000;
    private static final int HEADER_SIZE = 1 + 8 + 4;
    private Uri mUri;
    private RandomAccessFile mMiniThumbFile;
    private FileChannel mChannel;
    private ByteBuffer mBuffer;
    private static final Hashtable<String, MiniThumbFile> sThumbFiles =
        new Hashtable<String, MiniThumbFile>();
    // Add following members for index file use
    private static final int INDEX_FILE_VERSION = 3;
    public static final int BYTES_PER_INDEX = 8;
    private RandomAccessFile mIndexFile;
    private FileChannel mIndexChannel;
    private ByteBuffer mIndexBuffer;

    /**
     * We store different types of thumbnails in different files. To remain backward compatibility,
     * we should hashcode of content://media/external/images/media remains the same.
     */
    public static synchronized void reset() {
        for (MiniThumbFile file : sThumbFiles.values()) {
            file.deactivate();
        }
        sThumbFiles.clear();
    }

    public static synchronized MiniThumbFile instance(Uri uri) {
        String type = uri.getPathSegments().get(1);
        MiniThumbFile file = sThumbFiles.get(type);
        // Log.v(TAG, "get minithumbfile for type: "+type);
        if (file == null) {
            file = new MiniThumbFile(
                    Uri.parse("content://media/external/" + type + "/media"));
            sThumbFiles.put(type, file);
        }

        return file;
    }

    private String randomAccessFilePath(int version) {
        String directoryName =
                Environment.getExternalStorageDirectory().toString()
                + "/DCIM/.thumbnails";
        return directoryName + "/.thumbdata" + version + "-" + mUri.hashCode();
    }

    private void removeOldFile() {
        String oldPath = randomAccessFilePath(MINI_THUMB_DATA_FILE_VERSION - 1);
        File oldFile = new File(oldPath);
        if (oldFile.exists()) {
            try {
                oldFile.delete();
            } catch (SecurityException ex) {
                // ignore
            }
        }
    }

    private RandomAccessFile miniThumbDataFile() {
        if (mMiniThumbFile == null) {
            removeOldFile();
            String path = randomAccessFilePath(MINI_THUMB_DATA_FILE_VERSION);
            File directory = new File(path).getParentFile();
            if (!directory.isDirectory()) {
                if (!directory.mkdirs()) {
                    Log.e(TAG, "Unable to create .thumbnails directory "
                            + directory.toString());
                }
            }
            File f = new File(path);
            try {
                mMiniThumbFile = new RandomAccessFile(f, "rw");
            } catch (IOException ex) {
                // Open as read-only so we can at least read the existing
                // thumbnails.
                try {
                    mMiniThumbFile = new RandomAccessFile(f, "r");
                } catch (IOException ex2) {
                    // ignore exception
                }
            }
            if (mMiniThumbFile != null) {
                mChannel = mMiniThumbFile.getChannel();
            }
        }
        return mMiniThumbFile;
    }

    private String indexFilePath(int version) {
        String directoryName =
                Environment.getExternalStorageDirectory().toString()
                + "/DCIM/.thumbnails";
        return directoryName + "/.thumbindex" + version + "-" + mUri.hashCode();
    }

    private void removeOldIndexFile() {
        String oldPath = indexFilePath(INDEX_FILE_VERSION - 1);
        File oldFile = new File(oldPath);
        if (oldFile.exists()) {
            try {
                oldFile.delete();
            } catch (SecurityException ex) {
                // ignore exception
            }
        }
    }

    private RandomAccessFile indexFile() {
        if (mIndexFile == null) {
            removeOldIndexFile();
            String path = indexFilePath(INDEX_FILE_VERSION);
            File directory = new File(path).getParentFile();
            if (!directory.isDirectory()) {
                if (!directory.mkdirs()) {
                    Log.e(TAG, "Unable to create .thumbnails directory "
                            + directory.toString());
                }
            }
            File f = new File(path);
            try {
                mIndexFile = new RandomAccessFile(f, "rw");
            } catch (IOException ex) {
                // Open as read-only so we can at least read the existing
                // thumbnail indexes.
                try {
                    mIndexFile = new RandomAccessFile(f, "r");
                } catch (IOException ex2) {
                    // ignore exception
                }
            }
            if (mIndexFile != null) {
                mIndexChannel = mIndexFile.getChannel();
            }
        }
        return mIndexFile;
    }

    public MiniThumbFile(Uri uri) {
        mUri = uri;
        mBuffer = ByteBuffer.allocateDirect(BYTES_PER_MINTHUMB);
        mIndexBuffer = ByteBuffer.allocateDirect(BYTES_PER_INDEX);
    }

    public synchronized void deactivate() {
        if (mMiniThumbFile != null) {
            try {
                mMiniThumbFile.close();
                mMiniThumbFile = null;
            } catch (IOException ex) {
                // ignore exception
            }
        }
        if (mIndexFile != null) {
            try {
                mIndexFile.close();
                mIndexFile = null;
            } catch (IOException ex) {
                // ignore exception
            }
        }
    }

    // Get the magic number for the specified id in the mini-thumb file.
    // Returns 0 if the magic is not available.
    public synchronized long getMagic(long id) {
        // check the mini thumb file for the right data.  Right is
        // defined as having the right magic number at the offset
        // reserved for this "id".
        // Firstly, find the position in thumbdata file for this "id" according to index file.
        RandomAccessFile r = miniThumbDataFile();
        RandomAccessFile ri = indexFile();
        if (r != null && ri != null) {
            FileLock lock = null;
            long index = BYTES_PER_INDEX * id;
            long pos = -1;
            try {
                mIndexBuffer.clear();
                mIndexBuffer.limit(8);

                lock = mIndexChannel.lock(index, 8, true);
                // check that we can read the following 8 bytes
                // (8 for the long)
                if (mIndexChannel.read(mIndexBuffer, index) == 8) {
                    mIndexBuffer.position(0);
                    pos = mIndexBuffer.getLong();
                }
            } catch (IOException ex) {
                Log.v(TAG, "Got exception checking file position: ", ex);
            } catch (RuntimeException ex) {
                // Other NIO related exception like disk full, read only channel..etc
                Log.e(TAG, "Got exception when reading position, id = " + id +
                        ", disk full or mount read-only? " + ex.getClass());
            } finally {
                try {
                    if (lock != null) lock.release();
                }
                catch (IOException ex) {
                    // ignore it.
                }
            }

            if (pos < 0) return 0;

            // Secondly, find the magic in thumbdata file according to the position.
            lock = null;
            try {
                mBuffer.clear();
                mBuffer.limit(1 + 8);

                lock = mChannel.lock(pos, 1 + 8, true);
                // check that we can read the following 9 bytes
                // (1 for the "status" and 8 for the long)
                if (mChannel.read(mBuffer, pos) == 9) {
                    mBuffer.position(0);
                    if (mBuffer.get() == 1) {
                        return mBuffer.getLong();
                    }
                }
            } catch (IOException ex) {
                Log.v(TAG, "Got exception checking file magic: ", ex);
            } catch (RuntimeException ex) {
                // Other NIO related exception like disk full, read only channel..etc
                Log.e(TAG, "Got exception when reading magic, id = " + id +
                        ", disk full or mount read-only? " + ex.getClass());
            } finally {
                try {
                    if (lock != null) lock.release();
                }
                catch (IOException ex) {
                    // ignore it.
                }
            }
        }
        return 0;
    }

    public synchronized void saveMiniThumbToFile(byte[] data, long id, long magic)
            throws IOException {
        RandomAccessFile r = miniThumbDataFile();
        RandomAccessFile ri = indexFile();
        if (r == null || ri == null) return;

        // Firstly, put into index file the position in thumbdata file for this "id".
        FileLock lock = null;
        long index = BYTES_PER_INDEX * id;
        long pos = mMiniThumbFile.length();
        boolean writeIndexSuccess = false;
        try {
            mIndexBuffer.clear();
            mIndexBuffer.putLong(pos);
            mIndexBuffer.flip();

            lock = mIndexChannel.lock(index, BYTES_PER_INDEX, false);
            mIndexChannel.write(mIndexBuffer, index);
            writeIndexSuccess = true;
        } catch (IOException ex) {
            Log.e(TAG, "couldn't save mini thumbnail position for "
                    + id + "; ", ex);
            throw ex;
        } catch (RuntimeException ex) {
            // Other NIO related exception like disk full, read only channel..etc
            Log.e(TAG, "couldn't save mini thumbnail position for "
                    + id + "; disk full or mount read-only? " + ex.getClass());
        } finally {
            try {
                if (lock != null) lock.release();
            }
            catch (IOException ex) {
                // ignore it.
            }
        }

        if (!writeIndexSuccess) return;

        // Secondly, put into thumbdata file thumb data for this "id" according to the position.
        lock = null;
        try {
            if (data != null) {
                if (data.length > BYTES_PER_MINTHUMB - HEADER_SIZE) {
                    // not enough space to store it.
                    return;
                }
                mBuffer.clear();
                mBuffer.put((byte) 1);
                mBuffer.putLong(magic);
                mBuffer.putInt(data.length);
                mBuffer.put(data);
                mBuffer.flip();

                lock = mChannel.lock(pos, BYTES_PER_MINTHUMB, false);
                mChannel.write(mBuffer, pos);
            }
        } catch (IOException ex) {
            Log.e(TAG, "couldn't save mini thumbnail data for "
                    + id + "; ", ex);
            throw ex;
        } catch (RuntimeException ex) {
            // Other NIO related exception like disk full, read only channel..etc
            Log.e(TAG, "couldn't save mini thumbnail data for "
                    + id + "; disk full or mount read-only? " + ex.getClass());
        } finally {
            try {
                if (lock != null) lock.release();
            }
            catch (IOException ex) {
                // ignore it.
            }
        }
    }

    /**
     * Gallery app can use this method to retrieve mini-thumbnail. Full size
     * images share the same IDs with their corresponding thumbnails.
     *
     * @param id the ID of the image (same of full size image).
     * @param data the buffer to store mini-thumbnail.
     */
    public synchronized byte [] getMiniThumbFromFile(long id, byte [] data) {
        RandomAccessFile r = miniThumbDataFile();
        RandomAccessFile ri = indexFile();
        if (r == null || ri == null) return null;

        // Firstly, find the position in thumbdata file for this "id" according to index file.
        FileLock lock = null;
        long index = BYTES_PER_INDEX * id;
        long pos = -1;
        try {
            mIndexBuffer.clear();
            mIndexBuffer.limit(8);

            lock = mIndexChannel.lock(index, 8, true);
            // check that we can read the following 8 bytes
            // (8 for the long)
            if (mIndexChannel.read(mIndexBuffer, index) == 8) {
                mIndexBuffer.position(0);
                pos = mIndexBuffer.getLong();
            }
        } catch (IOException ex) {
            Log.w(TAG, "got exception when reading position id=" + id + ", exception: " + ex);
        } catch (RuntimeException ex) {
            // Other NIO related exception like disk full, read only channel..etc
            Log.e(TAG, "Got exception when reading position, id = " + id +
                    ", disk full or mount read-only? " + ex.getClass());
        } finally {
            try {
                if (lock != null) lock.release();
            }
            catch (IOException ex) {
                // ignore it.
            }
        }

        if (pos < 0) return null;

        // Secondly, find the thumb data for this "id" in thumbdata file according to the position.
        lock = null;
        try {
            mBuffer.clear();
            lock = mChannel.lock(pos, BYTES_PER_MINTHUMB, true);
            int size = mChannel.read(mBuffer, pos);
            if (size > 1 + 8 + 4) { // flag, magic, length
                mBuffer.position(0);
                byte flag = mBuffer.get();
                long magic = mBuffer.getLong();
                int length = mBuffer.getInt();

                if (size >= 1 + 8 + 4 + length && length != 0 && magic != 0 && flag == 1 &&
                        data.length >= length) {
                    mBuffer.get(data, 0, length);
                    return data;
                }
            }
        } catch (IOException ex) {
            Log.w(TAG, "got exception when reading thumbnail id=" + id + ", exception: " + ex);
        } catch (RuntimeException ex) {
            // Other NIO related exception like disk full, read only channel..etc
            Log.e(TAG, "Got exception when reading thumbnail, id = " + id +
                    ", disk full or mount read-only? " + ex.getClass());
        } finally {
            try {
                if (lock != null) lock.release();
            }
            catch (IOException ex) {
                // ignore it.
            }
        }
        return null;
    }
}
