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

package android.os.storage;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * WARNING! Update IMountService.h and IMountService.cpp if you change this
 * file. In particular, the ordering of the methods below must match the
 * _TRANSACTION enum in IMountService.cpp
 *
 * @hide - Applications should use android.os.storage.StorageManager to access
 *       storage functions.
 */
public interface IMountService extends IInterface {
    /** Local-side IPC implementation stub class. */
    public static abstract class Stub extends Binder implements IMountService {
        private static class Proxy implements IMountService {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                mRemote = remote;
            }

            public IBinder asBinder() {
                return mRemote;
            }

            public String getInterfaceDescriptor() {
                return DESCRIPTOR;
            }

            /**
             * Registers an IMountServiceListener for receiving async
             * notifications.
             */
            public void registerListener(IMountServiceListener listener) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeStrongBinder((listener != null ? listener.asBinder() : null));
                    mRemote.transact(Stub.TRANSACTION_registerListener, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            /**
             * Unregisters an IMountServiceListener
             */
            public void unregisterListener(IMountServiceListener listener) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeStrongBinder((listener != null ? listener.asBinder() : null));
                    mRemote.transact(Stub.TRANSACTION_unregisterListener, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            /**
             * Returns true if a USB mass storage host is connected
             */
            public boolean isUsbMassStorageConnected() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                boolean _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(Stub.TRANSACTION_isUsbMassStorageConnected, _data, _reply, 0);
                    _reply.readException();
                    _result = 0 != _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }

            /**
             * Enables / disables USB mass storage. The caller should check
             * actual status of enabling/disabling USB mass storage via
             * StorageEventListener.
             */
            public void setUsbMassStorageEnabled(boolean enable) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeInt((enable ? 1 : 0));
                    mRemote.transact(Stub.TRANSACTION_setUsbMassStorageEnabled, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            /**
             * Returns true if a USB mass storage host is enabled (media is
             * shared)
             */
            public boolean isUsbMassStorageEnabled() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                boolean _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(Stub.TRANSACTION_isUsbMassStorageEnabled, _data, _reply, 0);
                    _reply.readException();
                    _result = 0 != _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }

            /**
             * Mount external storage at given mount point. Returns an int
             * consistent with MountServiceResultCode
             */
            public int mountVolume(String mountPoint) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                int _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(mountPoint);
                    mRemote.transact(Stub.TRANSACTION_mountVolume, _data, _reply, 0);
                    _reply.readException();
                    _result = _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }

            /**
             * Safely unmount external storage at given mount point. The unmount
             * is an asynchronous operation. Applications should register
             * StorageEventListener for storage related status changes.
             */
            public void unmountVolume(String mountPoint, boolean force) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(mountPoint);
                    _data.writeInt((force ? 1 : 0));
                    mRemote.transact(Stub.TRANSACTION_unmountVolume, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            /**
             * Format external storage given a mount point. Returns an int
             * consistent with MountServiceResultCode
             */
            public int formatVolume(String mountPoint) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                int _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(mountPoint);
                    mRemote.transact(Stub.TRANSACTION_formatVolume, _data, _reply, 0);
                    _reply.readException();
                    _result = _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }

            /**
             * Returns an array of pids with open files on the specified path.
             */
            public int[] getStorageUsers(String path) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                int[] _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(path);
                    mRemote.transact(Stub.TRANSACTION_getStorageUsers, _data, _reply, 0);
                    _reply.readException();
                    _result = _reply.createIntArray();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }

            /**
             * Gets the state of a volume via its mountpoint.
             */
            public String getVolumeState(String mountPoint) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                String _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(mountPoint);
                    mRemote.transact(Stub.TRANSACTION_getVolumeState, _data, _reply, 0);
                    _reply.readException();
                    _result = _reply.readString();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }

            /*
             * Creates a secure container with the specified parameters. Returns
             * an int consistent with MountServiceResultCode
             */
            public int createSecureContainer(String id, int sizeMb, String fstype, String key,
                    int ownerUid) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                int _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(id);
                    _data.writeInt(sizeMb);
                    _data.writeString(fstype);
                    _data.writeString(key);
                    _data.writeInt(ownerUid);
                    mRemote.transact(Stub.TRANSACTION_createSecureContainer, _data, _reply, 0);
                    _reply.readException();
                    _result = _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }

            /*
             * Destroy a secure container, and free up all resources associated
             * with it. NOTE: Ensure all references are released prior to
             * deleting. Returns an int consistent with MountServiceResultCode
             */
            public int destroySecureContainer(String id, boolean force) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                int _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(id);
                    _data.writeInt((force ? 1 : 0));
                    mRemote.transact(Stub.TRANSACTION_destroySecureContainer, _data, _reply, 0);
                    _reply.readException();
                    _result = _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }

            /*
             * Finalize a container which has just been created and populated.
             * After finalization, the container is immutable. Returns an int
             * consistent with MountServiceResultCode
             */
            public int finalizeSecureContainer(String id) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                int _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(id);
                    mRemote.transact(Stub.TRANSACTION_finalizeSecureContainer, _data, _reply, 0);
                    _reply.readException();
                    _result = _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }

            /*
             * Mount a secure container with the specified key and owner UID.
             * Returns an int consistent with MountServiceResultCode
             */
            public int mountSecureContainer(String id, String key, int ownerUid)
                    throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                int _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(id);
                    _data.writeString(key);
                    _data.writeInt(ownerUid);
                    mRemote.transact(Stub.TRANSACTION_mountSecureContainer, _data, _reply, 0);
                    _reply.readException();
                    _result = _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }

            /*
             * Unount a secure container. Returns an int consistent with
             * MountServiceResultCode
             */
            public int unmountSecureContainer(String id, boolean force) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                int _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(id);
                    _data.writeInt((force ? 1 : 0));
                    mRemote.transact(Stub.TRANSACTION_unmountSecureContainer, _data, _reply, 0);
                    _reply.readException();
                    _result = _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }

            /*
             * Returns true if the specified container is mounted
             */
            public boolean isSecureContainerMounted(String id) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                boolean _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(id);
                    mRemote.transact(Stub.TRANSACTION_isSecureContainerMounted, _data, _reply, 0);
                    _reply.readException();
                    _result = 0 != _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }

            /*
             * Rename an unmounted secure container. Returns an int consistent
             * with MountServiceResultCode
             */
            public int renameSecureContainer(String oldId, String newId) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                int _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(oldId);
                    _data.writeString(newId);
                    mRemote.transact(Stub.TRANSACTION_renameSecureContainer, _data, _reply, 0);
                    _reply.readException();
                    _result = _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }

            /*
             * Returns the filesystem path of a mounted secure container.
             */
            public String getSecureContainerPath(String id) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                String _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(id);
                    mRemote.transact(Stub.TRANSACTION_getSecureContainerPath, _data, _reply, 0);
                    _reply.readException();
                    _result = _reply.readString();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }

            /**
             * Gets an Array of currently known secure container IDs
             */
            public String[] getSecureContainerList() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                String[] _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(Stub.TRANSACTION_getSecureContainerList, _data, _reply, 0);
                    _reply.readException();
                    _result = _reply.createStringArray();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }

            /**
             * Shuts down the MountService and gracefully unmounts all external
             * media. Invokes call back once the shutdown is complete.
             */
            public void shutdown(IMountShutdownObserver observer)
                    throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeStrongBinder((observer != null ? observer.asBinder() : null));
                    mRemote.transact(Stub.TRANSACTION_shutdown, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            /**
             * Call into MountService by PackageManager to notify that its done
             * processing the media status update request.
             */
            public void finishMediaUpdate() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(Stub.TRANSACTION_finishMediaUpdate, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            /**
             * Mounts an Opaque Binary Blob (OBB) with the specified decryption
             * key and only allows the calling process's UID access to the
             * contents. MountService will call back to the supplied
             * IObbActionListener to inform it of the terminal state of the
             * call.
             */
            public void mountObb(String filename, String key, IObbActionListener token, int nonce)
                    throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(filename);
                    _data.writeString(key);
                    _data.writeStrongBinder((token != null ? token.asBinder() : null));
                    _data.writeInt(nonce);
                    mRemote.transact(Stub.TRANSACTION_mountObb, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            /**
             * Unmounts an Opaque Binary Blob (OBB). When the force flag is
             * specified, any program using it will be forcibly killed to
             * unmount the image. MountService will call back to the supplied
             * IObbActionListener to inform it of the terminal state of the
             * call.
             */
            public void unmountObb(String filename, boolean force, IObbActionListener token,
                    int nonce) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(filename);
                    _data.writeInt((force ? 1 : 0));
                    _data.writeStrongBinder((token != null ? token.asBinder() : null));
                    _data.writeInt(nonce);
                    mRemote.transact(Stub.TRANSACTION_unmountObb, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            /**
             * Checks whether the specified Opaque Binary Blob (OBB) is mounted
             * somewhere.
             */
            public boolean isObbMounted(String filename) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                boolean _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(filename);
                    mRemote.transact(Stub.TRANSACTION_isObbMounted, _data, _reply, 0);
                    _reply.readException();
                    _result = 0 != _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }

            /**
             * Gets the path to the mounted Opaque Binary Blob (OBB).
             */
            public String getMountedObbPath(String filename) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                String _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(filename);
                    mRemote.transact(Stub.TRANSACTION_getMountedObbPath, _data, _reply, 0);
                    _reply.readException();
                    _result = _reply.readString();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
        }

        private static final String DESCRIPTOR = "IMountService";

        static final int TRANSACTION_registerListener = IBinder.FIRST_CALL_TRANSACTION + 0;

        static final int TRANSACTION_unregisterListener = IBinder.FIRST_CALL_TRANSACTION + 1;

        static final int TRANSACTION_isUsbMassStorageConnected = IBinder.FIRST_CALL_TRANSACTION + 2;

        static final int TRANSACTION_setUsbMassStorageEnabled = IBinder.FIRST_CALL_TRANSACTION + 3;

        static final int TRANSACTION_isUsbMassStorageEnabled = IBinder.FIRST_CALL_TRANSACTION + 4;

        static final int TRANSACTION_mountVolume = IBinder.FIRST_CALL_TRANSACTION + 5;

        static final int TRANSACTION_unmountVolume = IBinder.FIRST_CALL_TRANSACTION + 6;

        static final int TRANSACTION_formatVolume = IBinder.FIRST_CALL_TRANSACTION + 7;

        static final int TRANSACTION_getStorageUsers = IBinder.FIRST_CALL_TRANSACTION + 8;

        static final int TRANSACTION_getVolumeState = IBinder.FIRST_CALL_TRANSACTION + 9;

        static final int TRANSACTION_createSecureContainer = IBinder.FIRST_CALL_TRANSACTION + 10;

        static final int TRANSACTION_finalizeSecureContainer = IBinder.FIRST_CALL_TRANSACTION + 11;

        static final int TRANSACTION_destroySecureContainer = IBinder.FIRST_CALL_TRANSACTION + 12;

        static final int TRANSACTION_mountSecureContainer = IBinder.FIRST_CALL_TRANSACTION + 13;

        static final int TRANSACTION_unmountSecureContainer = IBinder.FIRST_CALL_TRANSACTION + 14;

        static final int TRANSACTION_isSecureContainerMounted = IBinder.FIRST_CALL_TRANSACTION + 15;

        static final int TRANSACTION_renameSecureContainer = IBinder.FIRST_CALL_TRANSACTION + 16;

        static final int TRANSACTION_getSecureContainerPath = IBinder.FIRST_CALL_TRANSACTION + 17;

        static final int TRANSACTION_getSecureContainerList = IBinder.FIRST_CALL_TRANSACTION + 18;

        static final int TRANSACTION_shutdown = IBinder.FIRST_CALL_TRANSACTION + 19;

        static final int TRANSACTION_finishMediaUpdate = IBinder.FIRST_CALL_TRANSACTION + 20;

        static final int TRANSACTION_mountObb = IBinder.FIRST_CALL_TRANSACTION + 21;

        static final int TRANSACTION_unmountObb = IBinder.FIRST_CALL_TRANSACTION + 22;

        static final int TRANSACTION_isObbMounted = IBinder.FIRST_CALL_TRANSACTION + 23;

        static final int TRANSACTION_getMountedObbPath = IBinder.FIRST_CALL_TRANSACTION + 24;

        /**
         * Cast an IBinder object into an IMountService interface, generating a
         * proxy if needed.
         */
        public static IMountService asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && iin instanceof IMountService) {
                return (IMountService) iin;
            }
            return new IMountService.Stub.Proxy(obj);
        }

        /** Construct the stub at attach it to the interface. */
        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply,
                int flags) throws RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION: {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                case TRANSACTION_registerListener: {
                    data.enforceInterface(DESCRIPTOR);
                    IMountServiceListener listener;
                    listener = IMountServiceListener.Stub.asInterface(data.readStrongBinder());
                    registerListener(listener);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_unregisterListener: {
                    data.enforceInterface(DESCRIPTOR);
                    IMountServiceListener listener;
                    listener = IMountServiceListener.Stub.asInterface(data.readStrongBinder());
                    unregisterListener(listener);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_isUsbMassStorageConnected: {
                    data.enforceInterface(DESCRIPTOR);
                    boolean result = isUsbMassStorageConnected();
                    reply.writeNoException();
                    reply.writeInt((result ? 1 : 0));
                    return true;
                }
                case TRANSACTION_setUsbMassStorageEnabled: {
                    data.enforceInterface(DESCRIPTOR);
                    boolean enable;
                    enable = 0 != data.readInt();
                    setUsbMassStorageEnabled(enable);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_isUsbMassStorageEnabled: {
                    data.enforceInterface(DESCRIPTOR);
                    boolean result = isUsbMassStorageEnabled();
                    reply.writeNoException();
                    reply.writeInt((result ? 1 : 0));
                    return true;
                }
                case TRANSACTION_mountVolume: {
                    data.enforceInterface(DESCRIPTOR);
                    String mountPoint;
                    mountPoint = data.readString();
                    int resultCode = mountVolume(mountPoint);
                    reply.writeNoException();
                    reply.writeInt(resultCode);
                    return true;
                }
                case TRANSACTION_unmountVolume: {
                    data.enforceInterface(DESCRIPTOR);
                    String mountPoint;
                    mountPoint = data.readString();
                    boolean force;
                    force = 0 != data.readInt();
                    unmountVolume(mountPoint, force);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_formatVolume: {
                    data.enforceInterface(DESCRIPTOR);
                    String mountPoint;
                    mountPoint = data.readString();
                    int result = formatVolume(mountPoint);
                    reply.writeNoException();
                    reply.writeInt(result);
                    return true;
                }
                case TRANSACTION_getStorageUsers: {
                    data.enforceInterface(DESCRIPTOR);
                    String path;
                    path = data.readString();
                    int[] pids = getStorageUsers(path);
                    reply.writeNoException();
                    reply.writeIntArray(pids);
                    return true;
                }
                case TRANSACTION_getVolumeState: {
                    data.enforceInterface(DESCRIPTOR);
                    String mountPoint;
                    mountPoint = data.readString();
                    String state = getVolumeState(mountPoint);
                    reply.writeNoException();
                    reply.writeString(state);
                    return true;
                }
                case TRANSACTION_createSecureContainer: {
                    data.enforceInterface(DESCRIPTOR);
                    String id;
                    id = data.readString();
                    int sizeMb;
                    sizeMb = data.readInt();
                    String fstype;
                    fstype = data.readString();
                    String key;
                    key = data.readString();
                    int ownerUid;
                    ownerUid = data.readInt();
                    int resultCode = createSecureContainer(id, sizeMb, fstype, key, ownerUid);
                    reply.writeNoException();
                    reply.writeInt(resultCode);
                    return true;
                }
                case TRANSACTION_finalizeSecureContainer: {
                    data.enforceInterface(DESCRIPTOR);
                    String id;
                    id = data.readString();
                    int resultCode = finalizeSecureContainer(id);
                    reply.writeNoException();
                    reply.writeInt(resultCode);
                    return true;
                }
                case TRANSACTION_destroySecureContainer: {
                    data.enforceInterface(DESCRIPTOR);
                    String id;
                    id = data.readString();
                    boolean force;
                    force = 0 != data.readInt();
                    int resultCode = destroySecureContainer(id, force);
                    reply.writeNoException();
                    reply.writeInt(resultCode);
                    return true;
                }
                case TRANSACTION_mountSecureContainer: {
                    data.enforceInterface(DESCRIPTOR);
                    String id;
                    id = data.readString();
                    String key;
                    key = data.readString();
                    int ownerUid;
                    ownerUid = data.readInt();
                    int resultCode = mountSecureContainer(id, key, ownerUid);
                    reply.writeNoException();
                    reply.writeInt(resultCode);
                    return true;
                }
                case TRANSACTION_unmountSecureContainer: {
                    data.enforceInterface(DESCRIPTOR);
                    String id;
                    id = data.readString();
                    boolean force;
                    force = 0 != data.readInt();
                    int resultCode = unmountSecureContainer(id, force);
                    reply.writeNoException();
                    reply.writeInt(resultCode);
                    return true;
                }
                case TRANSACTION_isSecureContainerMounted: {
                    data.enforceInterface(DESCRIPTOR);
                    String id;
                    id = data.readString();
                    boolean status = isSecureContainerMounted(id);
                    reply.writeNoException();
                    reply.writeInt((status ? 1 : 0));
                    return true;
                }
                case TRANSACTION_renameSecureContainer: {
                    data.enforceInterface(DESCRIPTOR);
                    String oldId;
                    oldId = data.readString();
                    String newId;
                    newId = data.readString();
                    int resultCode = renameSecureContainer(oldId, newId);
                    reply.writeNoException();
                    reply.writeInt(resultCode);
                    return true;
                }
                case TRANSACTION_getSecureContainerPath: {
                    data.enforceInterface(DESCRIPTOR);
                    String id;
                    id = data.readString();
                    String path = getSecureContainerPath(id);
                    reply.writeNoException();
                    reply.writeString(path);
                    return true;
                }
                case TRANSACTION_getSecureContainerList: {
                    data.enforceInterface(DESCRIPTOR);
                    String[] ids = getSecureContainerList();
                    reply.writeNoException();
                    reply.writeStringArray(ids);
                    return true;
                }
                case TRANSACTION_shutdown: {
                    data.enforceInterface(DESCRIPTOR);
                    IMountShutdownObserver observer;
                    observer = IMountShutdownObserver.Stub.asInterface(data
                            .readStrongBinder());
                    shutdown(observer);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_finishMediaUpdate: {
                    data.enforceInterface(DESCRIPTOR);
                    finishMediaUpdate();
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_mountObb: {
                    data.enforceInterface(DESCRIPTOR);
                    String filename;
                    filename = data.readString();
                    String key;
                    key = data.readString();
                    IObbActionListener observer;
                    observer = IObbActionListener.Stub.asInterface(data.readStrongBinder());
                    int nonce;
                    nonce = data.readInt();
                    mountObb(filename, key, observer, nonce);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_unmountObb: {
                    data.enforceInterface(DESCRIPTOR);
                    String filename;
                    filename = data.readString();
                    boolean force;
                    force = 0 != data.readInt();
                    IObbActionListener observer;
                    observer = IObbActionListener.Stub.asInterface(data.readStrongBinder());
                    int nonce;
                    nonce = data.readInt();
                    unmountObb(filename, force, observer, nonce);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_isObbMounted: {
                    data.enforceInterface(DESCRIPTOR);
                    String filename;
                    filename = data.readString();
                    boolean status = isObbMounted(filename);
                    reply.writeNoException();
                    reply.writeInt((status ? 1 : 0));
                    return true;
                }
                case TRANSACTION_getMountedObbPath: {
                    data.enforceInterface(DESCRIPTOR);
                    String filename;
                    filename = data.readString();
                    String mountedPath = getMountedObbPath(filename);
                    reply.writeNoException();
                    reply.writeString(mountedPath);
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }
    }

    /*
     * Creates a secure container with the specified parameters. Returns an int
     * consistent with MountServiceResultCode
     */
    public int createSecureContainer(String id, int sizeMb, String fstype, String key, int ownerUid)
            throws RemoteException;

    /*
     * Destroy a secure container, and free up all resources associated with it.
     * NOTE: Ensure all references are released prior to deleting. Returns an
     * int consistent with MountServiceResultCode
     */
    public int destroySecureContainer(String id, boolean force) throws RemoteException;

    /*
     * Finalize a container which has just been created and populated. After
     * finalization, the container is immutable. Returns an int consistent with
     * MountServiceResultCode
     */
    public int finalizeSecureContainer(String id) throws RemoteException;

    /**
     * Call into MountService by PackageManager to notify that its done
     * processing the media status update request.
     */
    public void finishMediaUpdate() throws RemoteException;

    /**
     * Format external storage given a mount point. Returns an int consistent
     * with MountServiceResultCode
     */
    public int formatVolume(String mountPoint) throws RemoteException;

    /**
     * Gets the path to the mounted Opaque Binary Blob (OBB).
     */
    public String getMountedObbPath(String filename) throws RemoteException;

    /**
     * Gets an Array of currently known secure container IDs
     */
    public String[] getSecureContainerList() throws RemoteException;

    /*
     * Returns the filesystem path of a mounted secure container.
     */
    public String getSecureContainerPath(String id) throws RemoteException;

    /**
     * Returns an array of pids with open files on the specified path.
     */
    public int[] getStorageUsers(String path) throws RemoteException;

    /**
     * Gets the state of a volume via its mountpoint.
     */
    public String getVolumeState(String mountPoint) throws RemoteException;

    /**
     * Checks whether the specified Opaque Binary Blob (OBB) is mounted
     * somewhere.
     */
    public boolean isObbMounted(String filename) throws RemoteException;

    /*
     * Returns true if the specified container is mounted
     */
    public boolean isSecureContainerMounted(String id) throws RemoteException;

    /**
     * Returns true if a USB mass storage host is connected
     */
    public boolean isUsbMassStorageConnected() throws RemoteException;

    /**
     * Returns true if a USB mass storage host is enabled (media is shared)
     */
    public boolean isUsbMassStorageEnabled() throws RemoteException;

    /**
     * Mounts an Opaque Binary Blob (OBB) with the specified decryption key and
     * only allows the calling process's UID access to the contents.
     * MountService will call back to the supplied IObbActionListener to inform
     * it of the terminal state of the call.
     */
    public void mountObb(String filename, String key, IObbActionListener token, int nonce)
            throws RemoteException;

    /*
     * Mount a secure container with the specified key and owner UID. Returns an
     * int consistent with MountServiceResultCode
     */
    public int mountSecureContainer(String id, String key, int ownerUid) throws RemoteException;

    /**
     * Mount external storage at given mount point. Returns an int consistent
     * with MountServiceResultCode
     */
    public int mountVolume(String mountPoint) throws RemoteException;

    /**
     * Registers an IMountServiceListener for receiving async notifications.
     */
    public void registerListener(IMountServiceListener listener) throws RemoteException;

    /*
     * Rename an unmounted secure container. Returns an int consistent with
     * MountServiceResultCode
     */
    public int renameSecureContainer(String oldId, String newId) throws RemoteException;

    /**
     * Enables / disables USB mass storage. The caller should check actual
     * status of enabling/disabling USB mass storage via StorageEventListener.
     */
    public void setUsbMassStorageEnabled(boolean enable) throws RemoteException;

    /**
     * Shuts down the MountService and gracefully unmounts all external media.
     * Invokes call back once the shutdown is complete.
     */
    public void shutdown(IMountShutdownObserver observer) throws RemoteException;

    /**
     * Unmounts an Opaque Binary Blob (OBB). When the force flag is specified,
     * any program using it will be forcibly killed to unmount the image.
     * MountService will call back to the supplied IObbActionListener to inform
     * it of the terminal state of the call.
     */
    public void unmountObb(String filename, boolean force, IObbActionListener token, int nonce)
            throws RemoteException;

    /*
     * Unount a secure container. Returns an int consistent with
     * MountServiceResultCode
     */
    public int unmountSecureContainer(String id, boolean force) throws RemoteException;

    /**
     * Safely unmount external storage at given mount point. The unmount is an
     * asynchronous operation. Applications should register StorageEventListener
     * for storage related status changes.
     */
    public void unmountVolume(String mountPoint, boolean force) throws RemoteException;

    /**
     * Unregisters an IMountServiceListener
     */
    public void unregisterListener(IMountServiceListener listener) throws RemoteException;
}
