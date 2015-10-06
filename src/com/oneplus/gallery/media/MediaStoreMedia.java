package com.oneplus.gallery.media;

import com.oneplus.database.CursorUtils;

import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video.VideoColumns;

/**
 * {@link Media} implementation based-on media store.
 */
public abstract class MediaStoreMedia implements Media
{
	/**
	 * Columns to be queried from media store.
	 */
	public static final String[] MEDIA_COLUMNS = new String[]{
		MediaColumns._ID,
		FileColumns.MEDIA_TYPE,
		FileColumns.DATA,
		FileColumns.SIZE,
		MediaColumns.MIME_TYPE,
		ImageColumns.DATE_TAKEN,
		MediaColumns.WIDTH,
		MediaColumns.HEIGHT,
		ImageColumns.ORIENTATION,
		VideoColumns.DURATION,
	};
	
	
	// Fields.
	private final Uri m_ContentUri;
	private final String m_FilePath;
	private final Handler m_Handler;
	private final boolean m_IsOriginal;
	private final String m_MimeType;
	private final int[] m_Size = new int[2];
	private long m_TakenTime;
	
	
	/**
	 * Initialize new MediaStoreMedia instance.
	 * @param contentUri Content URI.
	 * @param cursor Cursor to read data.
	 * @param isOriginal True if this is original file.
	 * @param handler Handler.
	 */
	protected MediaStoreMedia(Uri contentUri, Cursor cursor, boolean isOriginal, Handler handler)
	{
		// check parameter
		if(handler == null)
			throw new IllegalArgumentException("No handler");
		
		// save handler
		m_Handler = handler;
		
		// save info
		m_ContentUri = contentUri;
		m_IsOriginal = isOriginal;
		m_FilePath = CursorUtils.getString(cursor, MediaColumns.DATA);
		m_MimeType = CursorUtils.getString(cursor, FileColumns.MIME_TYPE);
		
		// get size
		this.setupSize(cursor, m_Size);
		
		// get taken time
		m_TakenTime = this.setupTakenTime(cursor);
	}
	
	
	/**
	 * Create {@link MediaStoreMedia} instance.
	 * @param cursor Cursor to read data.
	 * @param isOriginal True if this is original file.
	 * @param handler Handler.
	 * @return Create media instance, or Null if fail to create.
	 */
	public static MediaStoreMedia create(Cursor cursor, boolean isOriginal, Handler handler)
	{
		// create media by media type
		switch(CursorUtils.getInt(cursor, FileColumns.MEDIA_TYPE, FileColumns.MEDIA_TYPE_NONE))
		{
			case FileColumns.MEDIA_TYPE_IMAGE:
				return new PhotoMediaStoreMedia(cursor, isOriginal, handler);
			case FileColumns.MEDIA_TYPE_VIDEO:
				return new VideoMediaStoreMedia(cursor, isOriginal, handler);
			case FileColumns.MEDIA_TYPE_NONE:
				break;
			default:
				return null;
		}
		
		// create media by MIME type
		String mimeType = CursorUtils.getString(cursor, FileColumns.MIME_TYPE);
		if(mimeType != null)
		{
			if(mimeType.startsWith("image/"))
				return new PhotoMediaStoreMedia(cursor, isOriginal, handler);
			if(mimeType.startsWith("video/"))
				return new VideoMediaStoreMedia(cursor, isOriginal, handler);
		}
		
		// cannot check file type
		return null;
	}
	
	
	// Get content URI.
	@Override
	public Uri getContentUri()
	{
		return m_ContentUri;
	}
	
	
	/**
	 * Get content URI from cursor.
	 * @param cursor Cursor.
	 * @return Content URI.
	 */
	public static Uri getContentUri(Cursor cursor)
	{
		return getContentUri(cursor, CursorUtils.getInt(cursor, FileColumns.MEDIA_TYPE, FileColumns.MEDIA_TYPE_NONE));
	}
	
	
	/**
	 * Get content URI from cursor.
	 * @param cursor Cursor.
	 * @param mediaType Media type defined in {@link FileColumns}.
	 * @return Content URI.
	 */
	public static Uri getContentUri(Cursor cursor, int mediaType)
	{
		switch(mediaType)
		{
			case FileColumns.MEDIA_TYPE_IMAGE:
				return PhotoMediaStoreMedia.getContentUri(cursor);
			case FileColumns.MEDIA_TYPE_VIDEO:
				return VideoMediaStoreMedia.getContentUri(cursor);
			default:
				return null;
		}
	}
	
	
	// Get file path.
	@Override
	public String getFilePath()
	{
		return m_FilePath;
	}
	
	
	// Get handler.
	@Override
	public Handler getHandler()
	{
		return m_Handler;
	}
	
	
	// Get height.
	@Override
	public int getHeight()
	{
		return m_Size[1];
	}
	
	
	// Get MIME type.
	@Override
	public String getMimeType()
	{
		return m_MimeType;
	}
	
	
	// Get taken time.
	@Override
	public long getTakenTime()
	{
		return m_TakenTime;
	}
	
	
	// Get width.
	@Override
	public int getWidth()
	{
		return m_Size[0];
	}
	
	
	// Calculate hash code.
	@Override
	public int hashCode()
	{
		if(m_FilePath != null)
			return m_FilePath.hashCode();
		return m_ContentUri.hashCode();
	}
	
	
	// Check thread.
	@Override
	public boolean isDependencyThread()
	{
		return (m_Handler.getLooper().getThread() == Thread.currentThread());
	}
	
	
	// Check whether this is original file or not.
	@Override
	public boolean isOriginal()
	{
		return m_IsOriginal;
	}
	
	
	/**
	 * Setup media size.
	 * @param cursor Cursor.
	 * @param result Result array to receive width and height.
	 */
	protected void setupSize(Cursor cursor, int[] result)
	{
		result[0] = CursorUtils.getInt(cursor, MediaColumns.WIDTH, 0);
		result[1] = CursorUtils.getInt(cursor, MediaColumns.HEIGHT, 0);
	}
	
	
	/**
	 * Setup taken time.
	 * @param cursor Cursor.
	 * @return Taken time in milliseconds.
	 */
	protected long setupTakenTime(Cursor cursor)
	{
		return CursorUtils.getLong(cursor, ImageColumns.DATE_TAKEN, 0);
	}
	
	
	// Get readable string.
	@Override
	public String toString()
	{
		if(m_FilePath != null)
			return ("[" + m_ContentUri + ", File = " + m_FilePath + "]");
		return ("[" + m_ContentUri + "]");
	}
}
