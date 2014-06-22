/*
* Copyright (C) 2014 AOSB Project
* Author Hany alsamman @codex-corp
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

package com.android.systemui.recent.model;

import android.content.Intent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.media.ThumbnailUtils;
import android.database.Cursor;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff.Mode;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.CommonDataKinds.Phone;

import java.io.ByteArrayInputStream;
import java.util.Random;
import android.util.Log;

public class PersonUtils {

	public static int THUMBNAIL_SIZE = 100;

	public static Bitmap getContactIcon(long contactID, Context ctx){

		Bitmap contactThumb = loadContactPhoto(ctx, Long.valueOf(contactID));
		Bitmap contactIcon;

		if (contactThumb != null) {
			final int width = contactThumb.getWidth();
			final int height = contactThumb.getHeight();
			final int ratio = width / height;
			Bitmap resizedProfile = ThumbnailUtils.extractThumbnail(contactThumb, (THUMBNAIL_SIZE * ratio), THUMBNAIL_SIZE);
			contactIcon = GetRounded(resizedProfile);
			//if (resizedProfile != null && !resizedProfile.isRecycled()) resizedProfile.recycle();
		} else {
			Drawable myDrawable = ctx.getResources().getDrawable(com.android.systemui.R.drawable.no_person);
			Bitmap resizedNoProfile = ((BitmapDrawable) myDrawable).getBitmap();
			contactIcon = GetRounded(resizedNoProfile);
		}
		return contactIcon;
	}

	public static int getContactIDFromNumber(String contactNumber,Context context) {
		int phoneContactID = new Random().nextInt();
		Cursor contactLookupCursor = context.getContentResolver().query(Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(contactNumber)),new String[] {PhoneLookup.DISPLAY_NAME, PhoneLookup._ID}, null, null, null);
		while(contactLookupCursor.moveToNext()) {
			phoneContactID = contactLookupCursor.getInt(contactLookupCursor.getColumnIndexOrThrow(PhoneLookup._ID));
		}
		contactLookupCursor.close();

		return phoneContactID;
	}

	public static Bitmap GetRounded(final Bitmap bitmap) {
		
		int w = bitmap.getWidth();
		int h = bitmap.getHeight();

		int radius = Math.min(h / 2, w / 2);
		Bitmap output = Bitmap.createBitmap(w + 8, h + 8, Config.ARGB_8888);

		Paint p = new Paint();
		p.setAntiAlias(true);
		Canvas c = new Canvas(output);

		c.drawARGB(0, 0, 0, 0);
		p.setStyle(Style.FILL);
		c.drawCircle((w / 2) + 4, (h / 2) + 4, radius, p);
		p.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));

		c.drawBitmap(bitmap, 4, 4, p);
		p.setXfermode(null);
		p.setStyle(Style.STROKE);
		p.setColor(Color.WHITE);
		p.setStrokeWidth(2);
		c.drawCircle((w / 2) + 4, (h / 2) + 4, radius, p);
		return output;
	}

	public static Bitmap loadContactPhoto(Context ctx, long contactId) {
		Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long.valueOf(contactId));
		Uri photoUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
		Cursor cursor = ctx.getContentResolver().query(photoUri,
		                new String[] {ContactsContract.Contacts.Photo.PHOTO}, null, null, null);
		if (cursor == null) {
			return null;
		}
		try {
			if (cursor.moveToFirst()) {
				byte[] data = cursor.getBlob(0);
				if (data != null) {
					return BitmapFactory.decodeStream(new ByteArrayInputStream(data));
				}
			}
		}
		finally {
			cursor.close();
		}
		return null;
	}

	public static void OpenContact(Context ctx, Person mPerson){
		Intent intent = new Intent(Intent.ACTION_VIEW);
		Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, String.valueOf(mPerson.getContactID()));
		intent.setData(uri);
		ctx.startActivity(intent);
	}
}
