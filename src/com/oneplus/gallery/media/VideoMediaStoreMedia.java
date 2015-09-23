package com.oneplus.gallery.media;

import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

/**
 * Media store based video media.
 */
class VideoMediaStoreMedia extends MediaStoreMedia
{
	VideoMediaStoreMedia(Uri contentUri, Cursor cursor, Handler handler)
	{
		super(contentUri, cursor, handler);
	}
}
