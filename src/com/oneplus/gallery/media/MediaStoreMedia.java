package com.oneplus.gallery.media;

import com.oneplus.database.CursorUtils;

import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images.ImageColumns;

/**
 * {@link Media} implementation based-on media store.
 */
public abstract class MediaStoreMedia implements Media
{
	// Fields.
	private final Uri m_ContentUri;
	private final String m_FilePath;
	private final Handler m_Handler;
	private final String m_MimeType;
	private final int[] m_Size = new int[2];
	private long m_TakenTime;
	
	
	/**
	 * Initialize new MediaStoreMedia instance.
	 * @param baseContentUri Base content URI.
	 * @param cursor Cursor to read data.
	 * @param handler Handler.
	 */
	protected MediaStoreMedia(Uri baseContentUri, Cursor cursor, Handler handler)
	{
		// check parameter
		if(handler == null)
			throw new IllegalArgumentException("No handler");
		
		// save handler
		m_Handler = handler;
		
		// get content URI
		int id = CursorUtils.getInt(cursor, MediaColumns._ID, 0);
		if(id > 0)
			m_ContentUri = Uri.parse(baseContentUri.toString() + "/" + id);
		else
			m_ContentUri = baseContentUri;
		
		// get file path
		m_FilePath = CursorUtils.getString(cursor, MediaColumns.DATA);
		
		// get MIME type
		m_MimeType = CursorUtils.getString(cursor, FileColumns.MIME_TYPE);
		
		// get size
		this.setupSize(cursor, m_Size);
		
		// get taken time
		m_TakenTime = this.setupTakenTime(cursor);
	}
	
	
	/**
	 * Create {@link MediaStoreMedia} instance.
	 * @param baseContentUri Base content URI.
	 * @param cursor Cursor to read data.
	 * @param handler Handler.
	 * @return Create media instance, or Null if fail to create.
	 */
	public static MediaStoreMedia create(Uri baseContentUri, Cursor cursor, Handler handler)
	{
		// create media by media type
		switch(CursorUtils.getInt(cursor, FileColumns.MEDIA_TYPE, FileColumns.MEDIA_TYPE_NONE))
		{
			case FileColumns.MEDIA_TYPE_IMAGE:
				return new PhotoMediaStoreMedia(baseContentUri, cursor, handler);
			case FileColumns.MEDIA_TYPE_VIDEO:
				return null;
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
				return new PhotoMediaStoreMedia(baseContentUri, cursor, handler);
			if(mimeType.startsWith("video/"))
				return null;
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
