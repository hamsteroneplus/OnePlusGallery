package com.oneplus.gallery.media;

import com.oneplus.database.CursorUtils;

import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.MediaColumns;

/**
 * Media store based photo media.
 */
class PhotoMediaStoreMedia extends MediaStoreMedia
{
	// Constructor.
	PhotoMediaStoreMedia(Cursor cursor, Handler handler)
	{
		super(getContentUri(cursor), cursor, handler);
	}
	
	
	/**
	 * Get content URI from cursor.
	 * @param cursor Cursor.
	 * @return Photo content URI.
	 */
	public static Uri getContentUri(Cursor cursor)
	{
		int id = CursorUtils.getInt(cursor, MediaColumns._ID, 0);
		if(id > 0)
			return Uri.parse(Images.Media.EXTERNAL_CONTENT_URI + "/" + id);
		return null;
	}
}
