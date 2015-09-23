package com.oneplus.gallery.media;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.Files.FileColumns;

import com.oneplus.base.Handle;
import com.oneplus.base.HandlerBaseObject;
import com.oneplus.base.HandlerUtils;

/**
 * Media set based-on media store.
 */
public abstract class MediaStoreMediaSet extends HandlerBaseObject implements MediaSet
{
	// Constants.
	private static final Uri CONTENT_URI = Files.getContentUri("external");
	private static final int MSG_MEDIA_COUNT_CHANGED = -10000;
	private static final int MSG_ADD_MEDIA_TO_MEDIA_LIST = -10010;
	
	
	// Fields.
	private List<MediaListImpl> m_ActiveMediaLists;
	private volatile Handle m_MediaCountRefreshHandle;
	private final Type m_Type;
	private String m_QueryCondition;
	private String[] m_QueryConditionArgs;
	private final MediaManager.ContentProviderAccessCallback m_RefreshMediaCountCallback = new MediaManager.ContentProviderAccessCallback()
	{
		@Override
		public void onAccessContentProvider(ContentResolver contentResolver, Uri contentUri, ContentProviderClient client) throws RemoteException
		{
			int count = refreshMediaCount(contentResolver, contentUri, client);
			HandlerUtils.sendMessage(MediaStoreMediaSet.this, MSG_MEDIA_COUNT_CHANGED, count, 0, null);
		}
	};
	
	
	// Media list implementation.
	private final class MediaListImpl extends BasicMediaList
	{
		public MediaListImpl(MediaComparator comparator, int maxMediaCount)
		{
			super(comparator, maxMediaCount);
		}
		
		@Override
		public void release()
		{
			super.release();
			this.clearMedia();
			onMediaListReleased(this);
		}
	}
	
	
	/**
	 * Initialize new MediaStoreMediaSet instance.
	 * @param type Media set type.
	 */
	protected MediaStoreMediaSet(Type type)
	{
		super(true);
		if(type == null)
			throw new IllegalArgumentException("No type specified.");
		m_Type = type;
	}
	
	
	// Add media to media list.
	private void addMediaToMediaList(MediaListImpl mediaList, Media media)
	{
		if(!mediaList.get(MediaList.PROP_IS_RELEASED))
			mediaList.addMedia(media);
	}
	private void addMediaToMediaList(MediaListImpl mediaList, List<Media> media, boolean isSorted)
	{
		if(!mediaList.get(MediaList.PROP_IS_RELEASED))
			mediaList.addMedia(media, isSorted);
	}

	
	// Get media set type.
	@Override
	public Type getType()
	{
		return m_Type;
	}
	
	
	// Handle message.
	@SuppressWarnings("unchecked")
	@Override
	protected void handleMessage(Message msg)
	{
		switch(msg.what)
		{
			case MSG_ADD_MEDIA_TO_MEDIA_LIST:
			{
				Object[] params = (Object[])msg.obj;
				if(params[1] instanceof Media)
					this.addMediaToMediaList((MediaListImpl)params[0], (Media)params[1]);
				else
					this.addMediaToMediaList((MediaListImpl)params[0], (List<Media>)params[1], msg.arg1 != 0);
				break;
			}
			
			case MSG_MEDIA_COUNT_CHANGED:
				this.setReadOnly(PROP_MEDIA_COUNT, msg.arg1);
				break;
				
			default:
				super.handleMessage(msg);
				break;
		}
	}
	
	
	// Called when media list released.
	private void onMediaListReleased(MediaListImpl mediaList)
	{
		if(m_ActiveMediaLists.remove(mediaList) && m_ActiveMediaLists.isEmpty())
			; // TODO
	}
	

	// Open media list.
	@Override
	public MediaList openMediaList(final MediaComparator comparator, final int maxMediaCount, int flags)
	{
		// check state
		this.verifyAccess();
		
		// check parameter
		if(comparator == null)
			throw new IllegalArgumentException("No comparator.");
		
		// create media list
		final MediaListImpl mediaList = new MediaListImpl(comparator, maxMediaCount);
		if(m_ActiveMediaLists == null)
			m_ActiveMediaLists = new ArrayList<>();
		m_ActiveMediaLists.add(mediaList);
		
		// start updating media list
		MediaManager.accessContentProvider(CONTENT_URI, new MediaManager.ContentProviderAccessCallback()
		{
			@Override
			public void onAccessContentProvider(ContentResolver contentResolver, Uri contentUri, ContentProviderClient client) throws RemoteException
			{
				String sortOrder = comparator.getContentProviderSortOrder();
				if(maxMediaCount >= 0)
					sortOrder += (" LIMIT " + maxMediaCount);
				Cursor cursor = client.query(contentUri, MediaStoreMedia.MEDIA_COLUMNS, m_QueryCondition, m_QueryConditionArgs, sortOrder);
				boolean isFirstMedia = true;
				List<Media> tempMediaList = null;
				Handler handler = getHandler();
				if(cursor != null)
				{
					try
					{
						while(cursor.moveToNext())
						{
							Media media = MediaStoreMedia.create(contentUri, cursor, handler);
							if(media == null)
								continue;
							if(isFirstMedia)
							{
								isFirstMedia = false;
								HandlerUtils.sendMessage(MediaStoreMediaSet.this, MSG_ADD_MEDIA_TO_MEDIA_LIST, 1, 0, new Object[]{ mediaList, media });
							}
							else
							{
								if(tempMediaList == null)
									tempMediaList = new ArrayList<>();
								tempMediaList.add(media);
								if(tempMediaList.size() >= 64)
								{
									if(mediaList.get(MediaList.PROP_IS_RELEASED))
									{
										tempMediaList = null;
										break;
									}
									HandlerUtils.sendMessage(MediaStoreMediaSet.this, MSG_ADD_MEDIA_TO_MEDIA_LIST, 1, 0, new Object[]{ mediaList, tempMediaList });
									tempMediaList = null;
								}
							}
						}
						if(tempMediaList != null)
							HandlerUtils.sendMessage(MediaStoreMediaSet.this, MSG_ADD_MEDIA_TO_MEDIA_LIST, 1, 0, new Object[]{ mediaList, tempMediaList });
					}
					finally
					{
						cursor.close();
					}
				}
			}
		});
		
		// complete
		return mediaList;
	}
	
	
	/**
	 * Refresh media count.
	 * @param clearFirst True to clear media count first.
	 */
	protected void refreshMediaCount(boolean clearFirst)
	{
		// check state
		this.verifyAccess();
		
		// clear media count
		if(clearFirst)
			this.setReadOnly(PROP_MEDIA_COUNT, null);
		
		// refresh
		Handle.close(m_MediaCountRefreshHandle);
		m_MediaCountRefreshHandle = MediaManager.accessContentProvider(CONTENT_URI, m_RefreshMediaCountCallback);
	}
	
	
	// Refresh media count.
	protected int refreshMediaCount(ContentResolver contentResolver, Uri contentUri, ContentProviderClient client) throws RemoteException
	{
		Cursor cursor = client.query(contentUri, new String[]{ "Count(" + FileColumns._ID + ")" }, m_QueryCondition, m_QueryConditionArgs, null);
		if(cursor != null)
		{
			try
			{
				if(cursor.moveToNext())
					return cursor.getInt(0);
			}
			finally
			{
				cursor.close();
			}
		}
		return 0;
	}
	
	
	/**
	 * Set condition to query from media store.
	 * @param condition Condition.
	 * @param conditionArgs Arguments for condition.
	 */
	protected void setQueryCondition(String condition, String... conditionArgs)
	{
		this.verifyAccess();
		m_QueryCondition = ("(" + FileColumns.MEDIA_TYPE + "=" + FileColumns.MEDIA_TYPE_IMAGE + " OR " + FileColumns.MEDIA_TYPE + "=" + FileColumns.MEDIA_TYPE_VIDEO + ")");
		m_QueryConditionArgs = conditionArgs;
		if(condition != null)
			m_QueryCondition += (" AND " + condition);
		this.refreshMediaCount(true);
	}
}
