package com.oneplus.gallery.media;

import com.oneplus.database.CursorUtils;
import com.oneplus.gallery.MediaType;

import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Images.ImageColumns;

/**
 * Media store based photo media.
 */
class PhotoMediaStoreMedia extends MediaStoreMedia implements PhotoMedia
{
	// Fields.
	private volatile int m_Orientation;
	
	
	// Constructor.
	PhotoMediaStoreMedia(MediaSet mediaSet, Cursor cursor, boolean isOriginal, Handler handler)
	{
		super(mediaSet, getContentUri(cursor), cursor, isOriginal, handler);
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
	
	
	// Get orientation.
	@Override
	public int getOrientation()
	{
		return m_Orientation;
	}
	
	
	// Get media type.
	@Override
	public MediaType getType()
	{
		return MediaType.PHOTO;
	}
	
	
	// Setup photo size.
	@Override
	protected void setupSize(Cursor cursor, int[] result)
	{
		// call super
		super.setupSize(cursor, result);
		
		// decode size
		if(result[0] <= 0 || result[1] <= 0)
		{
			String filePath = this.getFilePath();
			if(filePath != null)
			{
				try
				{
					BitmapFactory.Options options = new BitmapFactory.Options();
					options.inJustDecodeBounds = true;
					BitmapFactory.decodeFile(filePath, options);
					result[0] = options.outWidth;
					result[1] = options.outHeight;
				}
				catch(Throwable ex)
				{}
			}
		}
		
		// get orientation
		m_Orientation = CursorUtils.getInt(cursor, ImageColumns.ORIENTATION, 0);
		
		// rotate size
		switch(m_Orientation)
		{
			case 90:
			case 270:
			{
				int temp = result[0];
				result[0] = result[1];
				result[1] = temp;
				break;
			}
		}
	}
}
