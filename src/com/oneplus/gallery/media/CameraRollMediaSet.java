package com.oneplus.gallery.media;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.RemoteException;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video.VideoColumns;

import com.oneplus.base.HandlerBaseObject;
import com.oneplus.gallery.GalleryApplication;
import com.oneplus.gallery.R;
import com.oneplus.gallery.media.MediaManager.ContentProviderAccessCallback;

/**
 * Camera roll.
 */
public class CameraRollMediaSet extends HandlerBaseObject implements MediaSet
{
	// Constants.
	private static final String[] MEDIA_COLUMNS = new String[]{
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
	private static final String MEDIA_QUERY_CONDITIONS = (FileColumns.DATA + " LIKE '" + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/Camera/%'");
	
	
	//
	private final class MediaList extends BasicMediaList
	{
		public MediaList(MediaComparator comparator, int maxMediaCount)
		{
			super(comparator, maxMediaCount);
		}
		
		@Override
		public void release()
		{
			//
			super.release();
		}
	}
	
	
	// Fields.
	//
	
	
	/**
	 * Initialize new CameraRollMediaSet instance.
	 */
	public CameraRollMediaSet()
	{
		super(true);
		this.setReadOnly(PROP_NAME, GalleryApplication.current().getString(R.string.media_set_name_camera_roll));
	}

	
	// Get type of set.
	@Override
	public Type getType()
	{
		return Type.SYSTEM;
	}

	
	// open media list.
	@Override
	public MediaList openMediaList(final MediaComparator comparator, int maxMediaCount, int flags)
	{
		// check state
		this.verifyAccess();
		
		// check parameter
		if(comparator == null)
			throw new IllegalArgumentException("No comparator.");
		
		//
		final MediaList mediaList = new MediaList(comparator, maxMediaCount);
		MediaManager.accessContentProvider(Files.getContentUri("external"), new ContentProviderAccessCallback()
		{
			@Override
			public void onAccessContentProvider(ContentResolver contentResolver, Uri contentUri, ContentProviderClient client) throws RemoteException
			{
				final List<Media> tempList = new ArrayList<>();
				Cursor cursor = client.query(contentUri, MEDIA_COLUMNS, MEDIA_QUERY_CONDITIONS, null, comparator.getContentProviderSortOrder());
				while(cursor.moveToNext())
				{
					Media media = MediaStoreMedia.create(contentUri, cursor, getHandler());
					if(media != null)
						tempList.add(media);
				}
				mediaList.getHandler().post(new Runnable()
				{
					@Override
					public void run()
					{
						mediaList.addMedia(tempList, true);
					}
				});
			}
		});
		return mediaList;
	}
}
