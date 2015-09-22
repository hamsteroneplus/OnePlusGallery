package com.oneplus.gallery.media;

import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore.MediaColumns;

/**
 * {@link Media} implementation based-on media store.
 */
public abstract class MediaStoreMedia implements Media
{
	// Fields.
	private final Uri m_ContentUri;
	private final String _FilePath;
	private final Handler m_Handler;
	private int m_Height;
	private int m_Width;
	
	
	protected MediaStoreMedia(Uri baseContentUri, Cursor cursor, Handler handler)
	{
		// check parameter
		if(handler == null)
			throw new IllegalArgumentException("No handler");
		
		// get content URI
		//
	}
}
