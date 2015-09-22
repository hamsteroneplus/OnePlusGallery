package com.oneplus.gallery.media;

import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

/**
 * Media store based photo media.
 */
class PhotoMediaStoreMedia extends MediaStoreMedia
{
	PhotoMediaStoreMedia(Uri contentUri, Cursor cursor, Handler handler)
	{
		super(contentUri, cursor, handler);
	}
}
