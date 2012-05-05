package android.content.res;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Native transport for package asset redirection information coming from the
 * AssetRedirectionManagerService.
 *
 * @hide
 */
public class PackageRedirectionMap implements Parcelable {
    private final int mNativePointer;

    public static final Parcelable.Creator<PackageRedirectionMap> CREATOR
            = new Parcelable.Creator<PackageRedirectionMap>() {
        public PackageRedirectionMap createFromParcel(Parcel in) {
            return new PackageRedirectionMap(in);
        }

        public PackageRedirectionMap[] newArray(int size) {
            return new PackageRedirectionMap[size];
        }
    };

    public PackageRedirectionMap() {
        this(nativeConstructor());
    }

    private PackageRedirectionMap(Parcel in) {
        this(nativeCreateFromParcel(in));
    }

    private PackageRedirectionMap(int nativePointer) {
        if (nativePointer == 0) {
            throw new RuntimeException();
        }
        mNativePointer = nativePointer;
    }

    @Override
    protected void finalize() throws Throwable {
        nativeDestructor(mNativePointer);
    }

    public int getNativePointer() {
        return mNativePointer;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (!nativeWriteToParcel(mNativePointer, dest)) {
            throw new RuntimeException();
        }
    }

    public int getPackageId() {
        return nativeGetPackageId(mNativePointer);
    }

    public void addRedirection(int fromIdent, int toIdent) {
        nativeAddRedirection(mNativePointer, fromIdent, toIdent);
    }

    // Used for debugging purposes only.
    public int[] getRedirectionKeys() {
        return nativeGetRedirectionKeys(mNativePointer);
    }

    // Used for debugging purposes only.
    public int lookupRedirection(int fromIdent) {
        return nativeLookupRedirection(mNativePointer, fromIdent);
    }

    private static native int nativeConstructor();
    private static native void nativeDestructor(int nativePointer);

    private static native int nativeCreateFromParcel(Parcel p);
    private static native boolean nativeWriteToParcel(int nativePointer, Parcel p);

    private native void nativeAddRedirection(int nativePointer, int fromIdent, int toIdent);
    private native int nativeGetPackageId(int nativePointer);
    private native int[] nativeGetRedirectionKeys(int nativePointer);
    private native int nativeLookupRedirection(int nativePointer, int fromIdent);
}
