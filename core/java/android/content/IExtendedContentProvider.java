package android.content;

import java.io.InputStream;

import android.net.Uri;

/**
 * The interface which "extends" IContentProvider.
 * ContentProviders may implement the interface
 * to work around the following bug.
 * If an image asset is stored in a separate file,
 * then AssetFileDescriptor.createInputStream returns stream with valid data.
 * However, if an image asset is stored in .apk or .zip file, then
 * AssetFileDescriptor.createInputStream returns a stream which does not
 * contain valid data (the bug repro with SDK 1.1 and "regular" apk).
 * To address this issue, ContentProviders may want to implement this interface.
 * 
 * @hide
 */
public interface IExtendedContentProvider {
	public InputStream openInputStream(Uri uri);
}
