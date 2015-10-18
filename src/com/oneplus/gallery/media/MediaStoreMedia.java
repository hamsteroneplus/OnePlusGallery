package com.oneplus.gallery.media;

import java.io.File;

import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.database.CursorUtils;
import com.oneplus.gallery.providers.GalleryDatabaseManager.ExtraMediaInfo;

import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video.VideoColumns;
import android.text.TextUtils;

/**
 * {@link Media} implementation based-on media store.
 */
public abstract class MediaStoreMedia implements Media
{
	/**
	 * Columns to be queried from media store.
	 */
	private static final String[] MEDIA_COLUMNS = new String[]{
		MediaColumns._ID,
		FileColumns.MEDIA_TYPE,
		FileColumns.DATA,
		FileColumns.SIZE,
		MediaColumns.MIME_TYPE,
		MediaColumns.DATE_MODIFIED,
		ImageColumns.DATE_TAKEN,
		MediaColumns.WIDTH,
		MediaColumns.HEIGHT,
		ImageColumns.LATITUDE,
		ImageColumns.LONGITUDE,
		ImageColumns.ORIENTATION,
		VideoColumns.DURATION,
	};
	
	
	// Constants
	private static final String TAG = "MediaStoreMedia";
	
	
	// Fields.
	private final Uri m_ContentUri;
	private volatile String m_FilePath;
	private volatile long m_FileSize;
	private final Handler m_Handler;
	private volatile boolean m_IsFavorite;
	private volatile long m_LastModifiedTime;
	private volatile Location m_Location;
	private final String m_MimeType;
	private final int[] m_Size = new int[2];
	private long m_TakenTime;
	
	
	// Class for media details retrieving handle.
	private final class MediaDetailsHandle extends Handle
	{
		// Fields.
		public final MediaDetailsCallback callback;
		public final Handler callbackHandler;
		public volatile AsyncTask<?, ?, ?> task;
		
		// Constructor.
		public MediaDetailsHandle(MediaDetailsCallback callback, Handler handler)
		{
			super("GetMediaDetails");
			this.callback = callback;
			this.callbackHandler = handler;
		}
		
		// Complete.
		public void complete(final MediaDetails details)
		{
			if(this.callbackHandler != null && this.callbackHandler.getLooper().getThread() != Thread.currentThread())
			{
				this.callbackHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						if(Handle.isValid(MediaDetailsHandle.this))
						{
							callback.onMediaDetailsRetrieved(MediaStoreMedia.this, MediaDetailsHandle.this, details);
							closeDirectly();
						}
					}
				});
			}
			else if(Handle.isValid(this))
			{
				this.callback.onMediaDetailsRetrieved(MediaStoreMedia.this, this, details);
				this.closeDirectly();
			}
		}

		// Close handle.
		@Override
		protected void onClose(int flags)
		{
			AsyncTask<?, ?, ?> task = this.task;
			if(task != null)
				task.cancel(true);
		}
	}
	
	
	/**
	 * Initialize new MediaStoreMedia instance.
	 * @param contentUri Content URI.
	 * @param cursor Cursor to read data.
	 * @param extraInfo Extra media info to get extended data.
	 * @param handler Handler.
	 */
	protected MediaStoreMedia(Uri contentUri, Cursor cursor, ExtraMediaInfo extraInfo, Handler handler)
	{
		// check parameter
		if(handler == null)
			throw new IllegalArgumentException("No handler");
		
		// save handler
		m_Handler = handler;
		
		// save info
		m_ContentUri = contentUri;
		m_MimeType = CursorUtils.getString(cursor, FileColumns.MIME_TYPE);
		
		// update
		this.onUpdate(cursor, extraInfo, true);
	}
	
	
	/**
	 * Create {@link MediaStoreMedia} instance.
	 * @param mediaSet {@link MediaSet}.
	 * @param cursor Cursor to read data.
	 * @param extraInfo Extra media info to get extended data.
	 * @param handler Handler.
	 * @return Create media instance, or Null if fail to create.
	 */
	public static MediaStoreMedia create(Cursor cursor, ExtraMediaInfo extraInfo, Handler handler)
	{
		// create media by media type
		switch(CursorUtils.getInt(cursor, FileColumns.MEDIA_TYPE, FileColumns.MEDIA_TYPE_NONE))
		{
			case FileColumns.MEDIA_TYPE_IMAGE:
				return new PhotoMediaStoreMedia(cursor, extraInfo, handler);
			case FileColumns.MEDIA_TYPE_VIDEO:
				return new VideoMediaStoreMedia(cursor, extraInfo, handler);
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
				return new PhotoMediaStoreMedia(cursor, extraInfo, handler);
			if(mimeType.startsWith("video/"))
				return new VideoMediaStoreMedia(cursor, extraInfo, handler);
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
	
	
	// Get details.
	@Override
	public Handle getDetails(MediaDetailsCallback callback, Handler handler)
	{
		// check parameter
		if(callback == null)
		{
			Log.e(TAG, "getDetails() - No call-back");
			return null;
		}
		
		// create handle
		final MediaDetailsHandle handle = new MediaDetailsHandle(callback, handler);
		
		// start getting details
		AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>()
		{
			@Override
			protected Void doInBackground(Void... params)
			{
				if(Handle.isValid(handle))
				{
					MediaDetails details;
					try
					{
						details = getDetails();
					}
					catch(Throwable ex)
					{
						Log.e(TAG, "getDetails() - Unhandled exception", ex);
						details = null;
					}
					handle.complete(details);
				}
				return null;
			}
		};
		handle.task = task;
		task.execute();
		return handle;
	}
	
	
	/**
	 * Get media details in background thread.
	 */
	protected MediaDetails getDetails() throws Exception
	{
		return null;
	}
	
	
	// Get file path.
	@Override
	public String getFilePath()
	{
		return m_FilePath;
	}
	
	
	// Get file size.
	@Override
	public long getFileSize()
	{
		return m_FileSize;
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
	
	
	// Get last modified time.
	@Override
	public long getLastModifiedTime()
	{
		return m_LastModifiedTime;
	}
	
	
	// Get location.
	@Override
	public Location getLocation()
	{
		return m_Location;
	}
	
	
	/**
	 * Get media store columns for media.
	 * @return Media store columns.
	 */
	public static String[] getMediaColumns()
	{
		return MEDIA_COLUMNS;
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
	
	
	// Check favorite state.
	@Override
	public boolean isFavorite()
	{
		return m_IsFavorite;
	}
	
	
	/**
	 * Update media information.
	 * @param cursor Cursor to read media information.
	 * @param fromConstructor True if method is called from constructor.
	 * @return True if one or more media information has been changed.
	 */
	protected boolean onUpdate(Cursor cursor, ExtraMediaInfo extraInfo, boolean fromConstructor)
	{
		// get path and type
		boolean changed = false;
		String prevFilePath = m_FilePath;
		m_FilePath = CursorUtils.getString(cursor, MediaColumns.DATA);
		changed = (!fromConstructor && !TextUtils.equals(prevFilePath, m_FilePath));
		
		// get file size
		File file = null;
		long prevFileSize = m_FileSize;
		m_FileSize = CursorUtils.getLong(cursor, FileColumns.SIZE, 0L);
		if(m_FileSize <= 0 && m_FilePath != null)
		{
			try
			{
				file = new File(m_FilePath);
				m_FileSize = file.length();
			}
			catch(Throwable ex)
			{}
		}
		if(!changed && !fromConstructor)
			changed = (prevFileSize != m_FileSize);
		
		// get modified time
		long prevTime = m_LastModifiedTime;
		m_LastModifiedTime = CursorUtils.getLong(cursor, MediaColumns.DATE_MODIFIED, 0L);
		if(m_LastModifiedTime > 0)
			m_LastModifiedTime *= 1000;
		else if(m_FilePath != null)
		{
			try
			{
				if(file == null)
					file = new File(m_FilePath);
				m_LastModifiedTime = file.lastModified();
			}
			catch(Throwable ex)
			{}
		}
		if(!changed && !fromConstructor)
			changed = (prevTime != m_LastModifiedTime);
		
		// get size
		int prevWidth = m_Size[0];
		int prevHeight = m_Size[1];
		this.setupSize(cursor, m_Size);
		if(!changed && !fromConstructor)
			changed = (prevWidth != m_Size[0] || prevHeight != m_Size[1]);
		
		// get location
		Location prevLocation = m_Location;
		double lat = CursorUtils.getDouble(cursor, ImageColumns.LATITUDE, 0);
		double lng = CursorUtils.getDouble(cursor, ImageColumns.LONGITUDE, 0);
		if(lat != 0 && lng != 0)
		{
			m_Location = new Location("");
			m_Location.setLatitude(lat);
			m_Location.setLongitude(lng);
		}
		if(!changed && !fromConstructor)
		{
			if(prevLocation != null)
			{
				if(m_Location != null)
					changed = (prevLocation.getLatitude() != m_Location.getLatitude() || prevLocation.getLongitude() != m_Location.getLongitude());
				else
					changed = true;
			}
			else if(m_Location != null)
				changed = true;
		}
		
		// get taken time
		prevTime = m_TakenTime;
		m_TakenTime = this.setupTakenTime(cursor);
		if(!changed && !fromConstructor)
			changed = (prevTime != m_TakenTime);
		
		// get favorite
		if(extraInfo != null)
		{
			int oneplusFlags = extraInfo.oneplusFlags;
			boolean prevFavorite = m_IsFavorite;
			m_IsFavorite = ((oneplusFlags & MediaManager.ONEPLUS_FLAG_FAVORITE) != 0);
			if(!changed && !fromConstructor)
				changed = (prevFavorite != m_IsFavorite);
		}
		
		// complete
		return changed;
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
	
	
	/**
	 * Update media information by cursor.
	 * @param cursor Cursor to read media information.
	 * @return True if one or more media information has been changed.
	 */
	public boolean update(Cursor cursor, ExtraMediaInfo info)
	{
		return this.onUpdate(cursor, info, false);
	}
}
