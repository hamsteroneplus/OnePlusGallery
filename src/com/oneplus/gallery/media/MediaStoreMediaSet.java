package com.oneplus.gallery.media;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.MediaColumns;

import com.oneplus.base.Handle;
import com.oneplus.base.HandleSet;
import com.oneplus.base.HandlerBaseObject;
import com.oneplus.base.HandlerUtils;
import com.oneplus.base.Log;
import com.oneplus.gallery.GalleryApplication;

/**
 * Media set based-on media store.
 */
public abstract class MediaStoreMediaSet extends HandlerBaseObject implements MediaSet
{
	// Constants.
	private static final Uri CONTENT_URI_FILE = Files.getContentUri("external");
	//private static final Uri CONTENT_URI_IMAGE = Images.Media.EXTERNAL_CONTENT_URI;
	//private static final Uri CONTENT_URI_VIDEO = Video.Media.EXTERNAL_CONTENT_URI;
	private static final String[] MEDIA_ID_COLUMNS = new String[]{
		MediaColumns._ID,
	};
	private static final long DURATION_UPDATE_MEDIA_ID_TABLE_DELAY = 1000;
	private static final int MSG_UPDATE_MEDIA_ID_TABLE = -10002;
	private static final int MSG_MEDIA_ID_TABLE_UPDATED = -10003;
	private static final int MSG_ADD_MEDIA_TO_MEDIA_LIST = -10010;
	private static final int MSG_REMOVE_MEDIA_FROM_MEDIA_LIST = -10011;
	private static final int MSG_MEDIA_SET_DELETED = -10021;
	
	
	// Fields.
	private List<WeakReference<MediaListImpl>> m_ActiveMediaLists;
	private Handle m_MediaChangeCallbackHandle;
	private volatile Handle m_MediaCountRefreshHandle;
	private final Set<Long> m_MediaIdTable = new HashSet<>();
	private final OPMediaManager m_MediaManager;
	private Handle m_MediaManagerActivatedHandle;
	private HandleSet m_MediaStoreContentChangedCBHandles;
	private final Type m_Type;
	private String m_QueryCondition;
	private String[] m_QueryConditionArgs;
	
	
	// Call-backs.
	private final MediaManager.MediaChangeCallback m_MediaChangeCallback = new MediaManager.MediaChangeCallback()
	{
		@Override
		public void onMediaCreated(long id, Media media)
		{
			MediaStoreMediaSet.this.onMediaCreated(id, media);
		}
		
		@Override
		public void onMediaDeleted(long id, Media media)
		{
			MediaStoreMediaSet.this.onMediaDeleted(id, media);
		}
		
		@Override
		public void onMediaUpdated(long id, Media media)
		{
			MediaStoreMediaSet.this.onMediaUpdated(id, media);
		}
	};
	
	
	// Media list implementation.
	private final class MediaListImpl extends BasicMediaList
	{
		public MediaListImpl(MediaComparator comparator, int maxMediaCount)
		{
			super(comparator, maxMediaCount);
		}
		
		public Media findMedia(Uri contentUri)
		{
			if(contentUri == null)
				return null;
			for(int i = this.size() - 1 ; i >= 0 ; --i)
			{
				Media media = this.get(i);
				if(contentUri.equals(media.getContentUri()))
					return media;
			}
			return null;
		}
		
		public void getAllContentUris(Set<Uri> result)
		{
			for(int i = this.size() - 1 ; i >= 0 ; --i)
				result.add(this.get(i).getContentUri());
		}
		
		@Override
		public void release()
		{
			super.release();
			this.clearMedia();
			onMediaListReleased(this);
		}
		
		public void removeMedia(Set<Uri> contentUris)
		{
			for(int i = this.size() - 1 ; i >= 0 ; --i)
			{
				Media media = this.get(i);
				if(contentUris.contains(media.getContentUri()))
					this.removeMedia(media);
			}
		}
	}
	
	
	// Class for media set deletion handle.
	private final class MediaSetDeletionHandle extends Handle
	{
		public final DeletionCallback callback;
		public final Handler callbackHandler;
		
		public MediaSetDeletionHandle(DeletionCallback callback, Handler handler)
		{
			super("DeleteMediaSet");
			this.callback = callback;
			this.callbackHandler = handler;
		}
		
		public void callOnDeletionCompleted(final boolean success)
		{
			if(this.callback == null)
				return;
			if(this.callbackHandler != null && this.callbackHandler.getLooper().getThread() != Thread.currentThread())
			{
				this.callbackHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						callback.onDeletionCompleted(MediaStoreMediaSet.this, success);
					}
				});
			}
			else
				this.callback.onDeletionCompleted(MediaStoreMediaSet.this, success);
		}
		
		public void callOnDeletionStarted()
		{
			if(this.callback == null)
				return;
			if(this.callbackHandler != null && this.callbackHandler.getLooper().getThread() != Thread.currentThread())
			{
				this.callbackHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						callback.onDeletionStarted(MediaStoreMediaSet.this);
					}
				});
			}
			else
				this.callback.onDeletionStarted(MediaStoreMediaSet.this);
		}

		@Override
		protected void onClose(int flags)
		{}
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
		m_MediaManager = GalleryApplication.current().findComponent(OPMediaManager.class);
		m_MediaChangeCallbackHandle = m_MediaManager.registerMediaChangeCallback(m_MediaChangeCallback, this.getHandler());
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
	
	
	// Delete this media set.
	@Override
	public Handle delete(DeletionCallback callback, Handler handler, int flags)
	{
		// check state
		this.verifyAccess();
		if(this.get(PROP_IS_RELEASED))
		{
			Log.e(TAG, "delete() - Media set is released");
			return null;
		}
		
		// create handle
		final MediaSetDeletionHandle handle = new MediaSetDeletionHandle(callback, handler);
		
		// delete asynchronously
		m_MediaManager.accessContentProvider(CONTENT_URI_FILE, new MediaManager.ContentProviderAccessCallback()
		{
			@Override
			public void onAccessContentProvider(ContentResolver contentResolver, Uri contentUri, ContentProviderClient client) throws RemoteException
			{
				if(!Handle.isValid(handle))
					return;
				handle.callOnDeletionStarted();
				boolean success;
				try
				{
					success = delete(contentResolver, contentUri, client);
				}
				catch(Throwable ex)
				{
					Log.e(TAG, "delete() - Fail to delete media set", ex);
					success = false;
				}		
				
				if(success)
				{
					// notify self in UI thread
					HandlerUtils.sendMessage(MediaStoreMediaSet.this, MSG_MEDIA_SET_DELETED);
				}
				
				handle.callOnDeletionCompleted(success);
			}
		});
		
		// complete
		return handle;
	}
	
	
	/**
	 * Delete this media set in content thread.
	 * @param contentResolver Content resolver.
	 * @param contentUri Content URI.
	 * @param client Content provider client.
	 * @return True if media deleted successfully.
	 */
	protected boolean delete(ContentResolver contentResolver, Uri contentUri, ContentProviderClient client) throws RemoteException
	{	
		return false;
	}
	
	
	// Delete media.
	@Override
	public Handle deleteMedia(Media media, MediaDeletionCallback callback, Handler handler, int flags)
	{
		// check state
		this.verifyAccess();
		if(this.get(PROP_IS_RELEASED))
		{
			Log.e(TAG, "deleteMedia() - Media set is released");
			return null;
		}
		
		// delete
		return m_MediaManager.deleteMedia(media, callback, handler);
	}
	
	
	/**
	 * Get media manager.
	 * @return Media manager.
	 */
	protected final OPMediaManager getMediaManager()
	{
		return m_MediaManager;
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
				
			case MSG_MEDIA_ID_TABLE_UPDATED:
			{
				Object[] params = (Object[])msg.obj;
				this.onMediaIdTableUpdated((Set<Long>)params[0], (Set<Long>)params[1]);
				break;
			}
				
			case MSG_MEDIA_SET_DELETED:
				this.onDeleted();
				break;
				
			case MSG_REMOVE_MEDIA_FROM_MEDIA_LIST:
			{
				Object[] params = (Object[])msg.obj;
				if(params[1] instanceof Media)
					this.removeMediaFromMediaList((MediaListImpl)params[0], (Media)params[1]);
				else if(params[1] instanceof Set<?>)
					this.removeMediaFromMediaList((MediaListImpl)params[0], (Set<Uri>)params[1]);
				break;
			}
			
			case MSG_UPDATE_MEDIA_ID_TABLE:
				this.updateMediaIdTable();
				break;
				
			default:
				super.handleMessage(msg);
				break;
		}
	}
	
	/**
	 * Called when media set has been deleted.
	 */
	protected void onDeleted()
	{
		// notify MediaManager
		m_MediaManager.notifyMediaSetDeleted(MediaStoreMediaSet.this);
		
		// release all media lists
		if(m_ActiveMediaLists != null && !m_ActiveMediaLists.isEmpty())
		{
			Log.v(TAG, "onDeleted() - Release all media lists");
			for(int i = m_ActiveMediaLists.size() - 1 ; i >= 0 ; --i)
			{
				MediaListImpl mediaList = m_ActiveMediaLists.get(i).get();
				if(mediaList != null)
					mediaList.release();
			}
		}
		
		// cancel refresh
		m_MediaCountRefreshHandle = Handle.close(m_MediaCountRefreshHandle);
		
		// reset media count
		setReadOnly(PROP_MEDIA_COUNT, 0);
	}
	
	
	/**
	 * Called when new media created.
	 * @param id ID of media.
	 * @param media Instance of created media, may be Null.
	 */
	protected void onMediaCreated(long id, Media media)
	{
		this.updateMediaIdTableDelayed();
	}
	
	
	/**
	 * Called when media has been deleted.
	 * @param id ID of media.
	 * @param media Instance of deleted media, may be Null.
	 */
	protected void onMediaDeleted(long id, Media media)
	{
		if(media == null || m_ActiveMediaLists == null)
			return;
		if(!m_MediaIdTable.remove(id))
			return;
		for(int i = m_ActiveMediaLists.size() - 1 ; i >= 0 ; --i)
		{
			MediaListImpl mediaList = m_ActiveMediaLists.get(i).get();
			if(mediaList != null)
				mediaList.removeMedia(media);
			else
				m_ActiveMediaLists.remove(i);
		}
		this.onMediaIdTableUpdated();
	}
	
	
	// Called when media ID table updated.
	private void onMediaIdTableUpdated()
	{
		this.setReadOnly(PROP_MEDIA_COUNT, m_MediaIdTable.size());
	}
	private void onMediaIdTableUpdated(Set<Long> addedId, Set<Long> removedId)
	{
		if(addedId != null)
		{
			for(Long id : addedId)
				m_MediaIdTable.add(id);
		}
		if(removedId != null)
		{
			for(Long id : removedId)
				m_MediaIdTable.remove(id);
		}
		this.setReadOnly(PROP_MEDIA_COUNT, m_MediaIdTable.size());
	}
	
	
	// Called when media list released.
	private void onMediaListReleased(MediaListImpl mediaList)
	{
		for(int i = m_ActiveMediaLists.size() - 1 ; i >= 0 ; --i)
		{
			MediaListImpl candMediaList = m_ActiveMediaLists.get(i).get();
			if(candMediaList == mediaList)
			{
				m_ActiveMediaLists.remove(i);
				Log.v(TAG, "onMediaListReleased() - Active media list count : ", m_ActiveMediaLists.size());
				break;
			}
			else if(candMediaList == null)
				m_ActiveMediaLists.remove(i);
		}
		
		// close media manager handle
		if(m_ActiveMediaLists.isEmpty())
			m_MediaManagerActivatedHandle = Handle.close(m_MediaManagerActivatedHandle);
	}
	
	
	/**
	 * Called when {@link MediaManager} activated.
	 */
	protected void onMediaManagerActivated()
	{}
	
	
	/**
	 * Called when {@link MediaManager} deactivated.
	 */
	protected void onMediaManagerDeactivated()
	{}
	
	
	/**
	 * Called when media updated.
	 * @param id ID of media.
	 * @param media Instance of updated media.
	 */
	protected void onMediaUpdated(long id, Media media)
	{}
	
	
	// Release media set.
	@Override
	protected void onRelease()
	{
		// release all media lists
		if(m_ActiveMediaLists != null && !m_ActiveMediaLists.isEmpty())
		{
			Log.v(TAG, "onRelease() - Release all media lists");
			for(int i = m_ActiveMediaLists.size() - 1 ; i >= 0 ; --i)
			{
				MediaListImpl mediaList = m_ActiveMediaLists.get(i).get();
				if(mediaList != null)
					mediaList.release();
			}
		}
		
		// cancel refresh
		m_MediaCountRefreshHandle = Handle.close(m_MediaCountRefreshHandle);
		
		// unregister content change call-back
		m_MediaManager.postToContentThread(new Runnable()
		{
			@Override
			public void run()
			{
				m_MediaStoreContentChangedCBHandles = Handle.close(m_MediaStoreContentChangedCBHandles);
			}
		}, 0);
		
		// remove call-back
		m_MediaChangeCallbackHandle = Handle.close(m_MediaChangeCallbackHandle);
		
		// clear media ID table
		m_MediaIdTable.clear();
		
		// call super
		super.onRelease();
	}
	

	// Open media list.
	@Override
	public MediaList openMediaList(final MediaComparator comparator, final int maxMediaCount, int flags)
	{
		// check state
		this.verifyReleaseState();
		
		// check parameter
		if(comparator == null)
			throw new IllegalArgumentException("No comparator.");
		
		// create media list
		final MediaListImpl mediaList = new MediaListImpl(comparator, maxMediaCount);
		if(m_ActiveMediaLists == null)
			m_ActiveMediaLists = new ArrayList<>();
		m_ActiveMediaLists.add(new WeakReference<MediaListImpl>(mediaList));
		Log.v(TAG, "openMediaList() - Active media list count : ", m_ActiveMediaLists.size());
		
		// activate media manager
		Handle.close(m_MediaManagerActivatedHandle);
		m_MediaManagerActivatedHandle = m_MediaManager.activate();
		
		// start updating media list
		m_MediaManager.accessContentProvider(CONTENT_URI_FILE, new MediaManager.ContentProviderAccessCallback()
		{
			@Override
			public void onAccessContentProvider(ContentResolver contentResolver, Uri contentUri, ContentProviderClient client) throws RemoteException
			{
				String sortOrder = comparator.getContentProviderSortOrder();
				if(maxMediaCount >= 0)
					sortOrder += (" LIMIT " + maxMediaCount);
				Cursor cursor = client.query(contentUri, MediaStoreMedia.getMediaColumns(), m_QueryCondition, m_QueryConditionArgs, sortOrder);
				int mediaReportThreshold = 4;
				List<Media> tempMediaList = null;
				if(cursor != null)
				{
					try
					{
						while(cursor.moveToNext())
						{
							Media media = m_MediaManager.obtainMedia(cursor, 0);
							if(media == null)
								continue;
							if(mediaReportThreshold == 1)
							{
								HandlerUtils.sendMessage(MediaStoreMediaSet.this, MSG_ADD_MEDIA_TO_MEDIA_LIST, 1, 0, new Object[]{ mediaList, media });
								mediaReportThreshold <<= 1;
							}
							else
							{
								if(tempMediaList == null)
									tempMediaList = new ArrayList<>();
								tempMediaList.add(media);
								if(tempMediaList.size() >= mediaReportThreshold)
								{
									if(mediaList.get(MediaList.PROP_IS_RELEASED))
									{
										tempMediaList = null;
										break;
									}
									HandlerUtils.sendMessage(MediaStoreMediaSet.this, MSG_ADD_MEDIA_TO_MEDIA_LIST, 1, 0, new Object[]{ mediaList, tempMediaList });
									tempMediaList = null;
									if(mediaReportThreshold < 64)
										mediaReportThreshold <<= 1;
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
	
	
	// Refresh media list.
	private void refreshMediaList(final MediaListImpl mediaList)
	{
		final HashSet<Uri> srcContentUris = new HashSet<>();
		mediaList.getAllContentUris(srcContentUris);
		m_MediaManager.accessContentProvider(CONTENT_URI_FILE, new MediaManager.ContentProviderAccessCallback()
		{
			@Override
			public void onAccessContentProvider(ContentResolver contentResolver, Uri contentUri, ContentProviderClient client) throws RemoteException
			{
				String sortOrder = mediaList.getComparator().getContentProviderSortOrder();
				int maxMediaCount = mediaList.getMaxMediaCount();
				if(maxMediaCount >= 0)
					sortOrder += (" LIMIT " + maxMediaCount);
				Cursor cursor = client.query(contentUri, MediaStoreMedia.getMediaColumns(), m_QueryCondition, m_QueryConditionArgs, sortOrder);
				if(cursor != null)
				{
					try
					{
						// add media
						while(cursor.moveToNext())
						{
							Uri uri = MediaStoreMedia.getContentUri(cursor);
							if(uri == null || srcContentUris.remove(uri))
								continue;
							Media media = m_MediaManager.obtainMedia(cursor, 0);
							if(media != null)
								HandlerUtils.sendMessage(MediaStoreMediaSet.this, MSG_ADD_MEDIA_TO_MEDIA_LIST, new Object[]{ mediaList, media });
						}
						
						// remove media
						if(!srcContentUris.isEmpty())
							HandlerUtils.sendMessage(MediaStoreMediaSet.this, MSG_REMOVE_MEDIA_FROM_MEDIA_LIST, new Object[]{ mediaList, srcContentUris });
					}
					finally
					{
						cursor.close();
					}
				}
			}
		});
	}
	
	
	// Refresh all media lists.
	private void refreshMediaLists()
	{
		if(m_ActiveMediaLists != null)
		{
			for(int i = m_ActiveMediaLists.size() - 1 ; i >= 0 ; --i)
			{
				MediaListImpl mediaList = m_ActiveMediaLists.get(i).get();
				if(mediaList != null)
					this.refreshMediaList(mediaList);
				else
					m_ActiveMediaLists.remove(i);
			}
		}
	}
	
	
	// Remove media from media list.
	private void removeMediaFromMediaList(MediaListImpl mediaList, Media media)
	{
		mediaList.removeMedia(media);
	}
	private void removeMediaFromMediaList(MediaListImpl mediaList, Set<Uri> media)
	{
		mediaList.removeMedia(media);
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
		this.updateMediaIdTable();
	}
	
	
	// Update media ID table.
	private void updateMediaIdTable()
	{
		// check state
		if(this.get(PROP_IS_RELEASED))
			return;
		
		// cancel pending update
		this.getHandler().removeMessages(MSG_UPDATE_MEDIA_ID_TABLE);
		
		// update
		final HashSet<Long> currentIdTable = new HashSet<Long>(m_MediaIdTable);
		m_MediaManager.accessContentProvider(CONTENT_URI_FILE, new MediaManager.ContentProviderAccessCallback()
		{
			@Override
			public void onAccessContentProvider(ContentResolver contentResolver, Uri contentUri, ContentProviderClient client) throws RemoteException
			{
				Cursor cursor = client.query(contentUri, MEDIA_ID_COLUMNS, m_QueryCondition, m_QueryConditionArgs, null);
				if(cursor != null)
				{
					HashSet<Long> newIdTable = null;
					try
					{
						while(cursor.moveToNext())
						{
							Long id = cursor.getLong(0);
							if(!currentIdTable.remove(id))
							{
								if(newIdTable == null)
									newIdTable = new HashSet<>();
								newIdTable.add(id);
							}
						}
					}
					finally
					{
						cursor.close();
					}
					HandlerUtils.sendMessage(MediaStoreMediaSet.this, MSG_MEDIA_ID_TABLE_UPDATED, new Object[]{ newIdTable, currentIdTable });
				}
			}
		});
		
		// refresh media lists
		this.refreshMediaLists();
	}
	
	
	// Update media ID table later.
	private void updateMediaIdTableDelayed()
	{
		if(this.get(PROP_IS_RELEASED))
			return;
		Handler handler = this.getHandler();
		if(!handler.hasMessages(MSG_UPDATE_MEDIA_ID_TABLE))
			handler.sendEmptyMessageDelayed(MSG_UPDATE_MEDIA_ID_TABLE, DURATION_UPDATE_MEDIA_ID_TABLE_DELAY);
	}
}
