package com.oneplus.gallery.media;

import com.oneplus.database.CursorUtils;
import com.oneplus.gallery.MediaType;
import com.oneplus.gallery.providers.GalleryDatabaseManager.ExtraMediaInfo;

import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.VideoColumns;

/**
 * Media store based video media.
 */
class VideoMediaStoreMedia extends MediaStoreMedia implements VideoMedia
{
	// Fields.
	private long m_Duration;
	
	
	// Constructor.
	VideoMediaStoreMedia(Cursor cursor, ExtraMediaInfo extraInfo, Handler handler)
	{
		// call super
		super(getContentUri(cursor), cursor, extraInfo, handler);
		
		// get duration
		m_Duration = CursorUtils.getLong(cursor, VideoColumns.DURATION, 0);
	}
	
	
	/**
	 * Get content URI from cursor.
	 * @param cursor Cursor.
	 * @return Video content URI.
	 */
	public static Uri getContentUri(Cursor cursor)
	{
		int id = CursorUtils.getInt(cursor, MediaColumns._ID, 0);
		if(id > 0)
			return Uri.parse(Video.Media.EXTERNAL_CONTENT_URI + "/" + id);
		return null;
	}
	
	
	// Get duration.
	@Override
	public long getDuration()
	{
		return m_Duration;
	}
	
	
	// Get media type.
	@Override
	public MediaType getType()
	{
		return MediaType.VIDEO;
	}
	
	
	// Setup video size
	@Override
	protected void setupSize(Cursor cursor, int[] result)
	{
		// call super
		super.setupSize(cursor, result);
		
		// get from file
		String filePath = this.getFilePath();
		if(filePath != null)
		{
			MediaMetadataRetriever retriever = null;
			try
			{
				retriever = new MediaMetadataRetriever();
				retriever.setDataSource(filePath);
				String rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
				result[0] = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
				result[1] = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
				if(rotation != null)
				{
					switch(rotation)
					{
						case "90":
						case "270":
						{
							int newWidth = result[1];
							result[1] = result[0];
							result[0] = newWidth;
							break;
						}
					}
				}
			}
			catch(Throwable ex)
			{}
			finally
			{
				if(retriever != null)
					retriever.release();
			}
		}
	}
}
