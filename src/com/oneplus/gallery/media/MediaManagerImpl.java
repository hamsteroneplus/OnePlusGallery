package com.oneplus.gallery.media;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.oneplus.base.Handle;
import com.oneplus.base.HandleSet;
import com.oneplus.base.HandlerBaseObject;
import com.oneplus.base.HandlerUtils;
import com.oneplus.base.ListHandlerBaseObject;
import com.oneplus.base.Log;
import com.oneplus.base.component.BasicComponent;
import com.oneplus.database.CursorUtils;
import com.oneplus.gallery.GalleryApplication;
import com.oneplus.gallery.ListChangeEventArgs;
import com.oneplus.gallery.media.MediaSet.Type;
import com.oneplus.gallery.providers.GalleryDatabaseManager;
import com.oneplus.gallery.providers.GalleryDatabaseManager.ExtraMediaInfo;
import com.oneplus.io.Path;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Files.FileColumns;

/**
 * Media manager.
 */
public class MediaManagerImpl extends BasicComponent implements OPMediaManager
{
	// Constants.
	private static final Uri CONTENT_URI_FILE = Files.getContentUri("external");
	private static final Uri CONTENT_URI_IMAGE = Images.Media.EXTERNAL_CONTENT_URI;
	private static final Uri CONTENT_URI_VIDEO = Video.Media.EXTERNAL_CONTENT_URI;
	private static final String PATTERN_SPECIFIC_CONTENT_URI = ".+/[\\d]+$";
	private static final String[] DIR_COLUMNS = new String[]{
		FileColumns.PARENT,
		FileColumns.DATA,
	};
	private static final String[] MEDIA_ID_COLUMNS = new String[]{
		MediaColumns._ID,
	};
	private static final String DIR_QUERY_CONDITION = 
			"(" 
					+ FileColumns.MEDIA_TYPE + "=" + FileColumns.MEDIA_TYPE_IMAGE
					+ " OR " + FileColumns.MEDIA_TYPE + "=" + FileColumns.MEDIA_TYPE_VIDEO
			+ ")"
			+ " AND NOT (" + FileColumns.DATA + " LIKE ?)"
	;
	private static final String MEDIA_QUERY_CONDITION = 
			FileColumns.MEDIA_TYPE + "=" + FileColumns.MEDIA_TYPE_IMAGE
			+ " OR " + FileColumns.MEDIA_TYPE + "=" + FileColumns.MEDIA_TYPE_VIDEO
	;
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static final List<Class<?>> SYSTEM_MEDIA_SET_PRIORITIES = (List)Arrays.asList(
		CameraRollMediaSet.class
	);
	private static final long INTERVAL_CHECK_CONTENT_CHANGES = 2000;
	private static final long DURATION_RELEASE_MEDIA_TABLE_DELAY = 3000;
	private static final int MSG_ACCESS_CONTENT_PROVIDER = 10000;
	private static final int MSG_SETUP_MEDIA_TABLE = 10001;
	private static final int MSG_RELEASE_MEDIA_TABLE = 10002;
	private static final int MSG_SYNC_MEDIA_TABLE = 10003;
	private static final int MSG_REGISTER_CONTENT_CHANGED_CB = 10010;
	private static final int MSG_UNREGISTER_CONTENT_CHANGED_CB = 10011;
	private static final int MSG_DIR_MEDIA_SET_CREATED = 10020;
	private static final int MSG_DIR_MEDIA_SET_DELETED = 10021;
	private static final int MSG_REFRESH_MEDIA_SET_LISTS = 10030;
	private static final int MSG_CHECK_CONTENT_CHANGES = 10040;
	private static final int MSG_NOTIFY_CONTENT_CHANGED = 10041;
	private static final int MSG_REGISTER_MEDIA_CHANGED_CB = 10050;
	private static final int MSG_UNREGISTER_MEDIA_CHANGED_CB = 10051;
	private static final int MSG_DELETE_MEDIA = 10060;
	private static final int MSG_UPDATE_ONEPLUS_FLAGS = 10070;
	
	
	// Fields.
	private final List<Handle> m_ActivateHandles = new ArrayList<>();
	private final List<WeakReference<MediaSetListImpl>> m_ActiveMediaSetLists = new ArrayList<>();
	private CameraRollMediaSet m_CameraRollMediaSet;
	private HashMap<Uri, ContentObserver> m_ContentObservers;
	private ContentResolver m_ContentResolver;
	private volatile HandlerThread m_ContentThread;
	private volatile Handler m_ContentThreadHandler;
	private final HashMap<Integer, DirectoryMediaSet> m_DirectoryMediaSets = new HashMap<>();
	private final Object m_Lock = new Object();
	private final List<MediaChangeCallbackHandle> m_MediaChangeCallbackHandles = new ArrayList<>();
	private Handle m_MediaContentChangeCBHandle;
	private final Map<Long, Media> m_MediaTable = new HashMap<>();
	private TempMediaSet m_TempMediaSet;
	
	
	// Call-backs.
	private final ContentChangeCallback m_MediaContentChangeCB = new ContentChangeCallback()
	{
		@Override
		public void onContentChanged(Uri contentUri)
		{
			if(contentUri != null && contentUri.getPath().matches(PATTERN_SPECIFIC_CONTENT_URI))
				;
			else 
			{
				if(!m_ContentThreadHandler.hasMessages(MSG_SYNC_MEDIA_TABLE))
					m_ContentThreadHandler.sendEmptyMessage(MSG_SYNC_MEDIA_TABLE);
				HandlerUtils.sendMessage(MediaManagerImpl.this, MSG_REFRESH_MEDIA_SET_LISTS);
			}
		}
	};
	
	
	// Handle for content provider access.
	private final class ContentProviderAccessHandle extends Handle
	{
		public final ContentProviderAccessCallback callback;
		public final Uri contentUri;
		
		public ContentProviderAccessHandle(Uri contentUri, ContentProviderAccessCallback callback)
		{
			super("ContentProviderAccess");
			this.contentUri = contentUri;
			this.callback = callback;
		}
		
		public void complete()
		{
			this.closeDirectly();
		}

		@Override
		protected void onClose(int flags)
		{
			cancelContentProviderAccess(this);
		}
	}
	
	
	// Base class for call-back related handle.
	private abstract static class CallbackHandle<TCallback> extends Handle
	{
		// Fields.
		public final TCallback callback;
		public final Handler callbackHandler;
		public final Thread callbackThread;
		
		// Constructor.
		protected CallbackHandle(String name, TCallback callback, Handler callbackHandler)
		{
			super(name);
			this.callback = callback;
			this.callbackHandler = callbackHandler;
			this.callbackThread = (callbackHandler != null ? callbackHandler.getLooper().getThread() : null);
		}
	}
	
	
	// Handle for media change call-back.
	private final class MediaChangeCallbackHandle extends CallbackHandle<MediaChangeCallback>
	{
		// Constructor.
		public MediaChangeCallbackHandle(MediaChangeCallback callback, Handler handler)
		{
			super("MediaChangeCallback", callback, handler);
		}
		
		// Call onMediaCreated().
		public void callOnMediaCreated(final long id, final Media media)
		{
			if(this.callbackThread != null && this.callbackThread != Thread.currentThread())
			{
				this.callbackHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						callback.onMediaCreated(id, media);
					}
				});
			}
			else
				this.callback.onMediaCreated(id, media);
		}
		
		// Call onMediaDeleted().
		public void callOnMediaDeleted(final long id, final Media media)
		{
			if(this.callbackThread != null && this.callbackThread != Thread.currentThread())
			{
				this.callbackHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						callback.onMediaDeleted(id, media);
					}
				});
			}
			else
				this.callback.onMediaDeleted(id, media);
		}
		
		// Call onMediaUpdated().
		public void callOnMediaUpdated(final long id, final Media media)
		{
			if(this.callbackThread != null && this.callbackThread != Thread.currentThread())
			{
				this.callbackHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						callback.onMediaUpdated(id, media);
					}
				});
			}
			else
				this.callback.onMediaUpdated(id, media);
		}

		// Close handle.
		@Override
		protected void onClose(int flags)
		{
			if(!isContentThread())
				Message.obtain(m_ContentThreadHandler, MSG_UNREGISTER_MEDIA_CHANGED_CB, this).sendToTarget();
			else
				unregisterMediaChangeCallback(this);
		}
	}
	
	
	// Handle for media deletion.
	private static final class MediaDeletionHandle extends CallbackHandle<MediaDeletionCallback>
	{
		// Fields.
		public final Media media;
		
		// Constructor.
		public MediaDeletionHandle(Media media, MediaDeletionCallback callback, Handler handler)
		{
			super("MediaDeletion", callback, handler);
			this.media = media;
		}
		
		// Call onDeletionCompleted().
		public void callOnDeletionCompleted(final boolean success)
		{
			if(this.callbackThread != null && this.callbackThread != Thread.currentThread())
			{
				this.callbackHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						callback.onDeletionCompleted(media, success);
					}
				});
			}
			else
				this.callback.onDeletionCompleted(this.media, success);
		}
		
		// Call onDeletionStarted().
		public void callOnDeletionStarted()
		{
			if(this.callbackThread != null && this.callbackThread != Thread.currentThread())
			{
				this.callbackHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						callback.onDeletionStarted(media);
					}
				});
			}
			else
				this.callback.onDeletionStarted(this.media);
		}

		// Close handle.
		@Override
		protected void onClose(int flags)
		{}
	}
	
	
	// Handle for content changed call-back.
	private final class ContentChangeCallbackHandle extends Handle
	{
		private static final int MSG_CONTENT_CHANGED = 10000;
		public final Uri contentUri;
		private final ContentChangeCallback m_Callback;
		private final Handler m_CallbackHandler;
		
		public ContentChangeCallbackHandle(Uri contentUri, ContentChangeCallback callback, Handler handler)
		{
			super("ContentChangeCallback");
			this.contentUri = contentUri;
			m_Callback = callback;
			if(handler != null)
			{
				m_CallbackHandler = new Handler(handler.getLooper())
				{
					@Override
					public void handleMessage(Message msg)
					{
						switch(msg.what)
						{
							case MSG_CONTENT_CHANGED:
								m_Callback.onContentChanged((Uri)msg.obj);
								break;
						}
					}
				};
			}
			else
				m_CallbackHandler = null;
		}

		@Override
		protected void onClose(int flags)
		{
			if(isContentThread())
				unregisterContentChangedCallback(this);
			else
				Message.obtain(m_ContentThreadHandler, MSG_UNREGISTER_CONTENT_CHANGED_CB, this).sendToTarget();
		}
		
		public void notifyContentChanged(Uri contentUri)
		{
			if(!Handle.isValid(this))
				return;
			if(contentUri == null)
				contentUri = this.contentUri;
			if(m_CallbackHandler != null && Thread.currentThread() != m_CallbackHandler.getLooper().getThread())
				Message.obtain(m_CallbackHandler, MSG_CONTENT_CHANGED, contentUri).sendToTarget();
			else
				m_Callback.onContentChanged(contentUri);
		}
	}
	
	
	// Content observer.
	private final class ContentObserver extends android.database.ContentObserver
	{
		public final List<ContentChangeCallbackHandle> callbackHandles = new ArrayList<>();
		public final Uri contentUri;
		public long lastChangedTime;
		
		public ContentObserver(Uri contentUri, Handler handler)
		{
			super(handler);
			this.contentUri = contentUri;
		}
		
		public void notifyChange(boolean resetChangeTime)
		{
			this.notifyChange(this.contentUri, resetChangeTime);
		}
		public void notifyChange(Uri contentUri, boolean resetChangeTime)
		{
			if(resetChangeTime)
				this.lastChangedTime = 0;
			for(int i = this.callbackHandles.size() - 1 ; i >= 0 ; --i)
				this.callbackHandles.get(i).notifyContentChanged(contentUri);
		}
		
		@Override
		public void onChange(boolean selfChange)
		{
			this.onChange(selfChange, null);
		}
		@Override
		public void onChange(boolean selfChange, Uri uri)
		{
			this.lastChangedTime = SystemClock.elapsedRealtime();
			if(get(PROP_IS_ACTIVE) && !m_ContentThreadHandler.hasMessages(MSG_CHECK_CONTENT_CHANGES))
				m_ContentThreadHandler.sendEmptyMessageDelayed(MSG_CHECK_CONTENT_CHANGES, INTERVAL_CHECK_CONTENT_CHANGES);
		}
	}
	
	
	// Media set list implementation.
	private final class MediaSetListImpl extends ListHandlerBaseObject<MediaSet> implements MediaSetList, Comparator<MediaSet>
	{
		// Fields.
		private final List<MediaSet> m_List = new ArrayList<>();
		
		// Add media set.
		public void addMediaSet(MediaSet set)
		{
			int index = Collections.binarySearch(m_List, set, this);
			if(index < 0)
				index = ~index;
			m_List.add(index, set);
			ListChangeEventArgs e = ListChangeEventArgs.obtain(index);
			this.raise(EVENT_MEDIA_SET_ADDED, e);
			e.recycle();
		}
		
		// Compare media sets.
		@Override
		public int compare(MediaSet lhs, MediaSet rhs)
		{
			// compare type
			int result = (lhs.getType().ordinal() - rhs.getType().ordinal());
			if(result != 0)
				return result;
			
			// compare system defined media sets
			if(lhs.getType() == Type.SYSTEM)
			{
				int priorityL = SYSTEM_MEDIA_SET_PRIORITIES.indexOf(lhs.getClass());
				int priorityR = SYSTEM_MEDIA_SET_PRIORITIES.indexOf(rhs.getClass());
				if(priorityL >= 0)
				{
					if(priorityR >= 0)
						return (priorityL - priorityR);
					return -1;
				}
				else if(priorityR >= 0)
					return 1;
			}
			
			// compare name
			String nameL = lhs.get(MediaSet.PROP_NAME);
			String nameR = rhs.get(MediaSet.PROP_NAME);
			if(nameL != null)
				return nameL.compareTo(nameR);
			if(nameR != null)
				return 1;
			return 0;
		}
		
		// Get media set.
		@Override
		public MediaSet get(int location)
		{
			return m_List.get(location);
		}
		
		// Release.
		@Override
		public void release()
		{
			super.release();
			int size = m_List.size();
			if(size > 0)
			{
				m_List.clear();
				ListChangeEventArgs e = ListChangeEventArgs.obtain(0, size - 1);
				this.raise(EVENT_MEDIA_SET_REMOVED, e);
				e.recycle();
			}
			onMediaSetListReleased(this);
		}
		
		// Remove media set.
		public boolean removeMediaSet(MediaSet set)
		{
			int index = Collections.binarySearch(m_List, set, this);
			if(index < 0)
				return false;
			ListChangeEventArgs e = ListChangeEventArgs.obtain(index);
			this.raise(EVENT_MEDIA_SET_REMOVING, e);
			m_List.remove(index);
			this.raise(EVENT_MEDIA_SET_REMOVED, e);
			e.recycle();
			return true;
		}

		// Get list size.
		@Override
		public int size()
		{
			return m_List.size();
		}
	}
	
	
	// Temporary media set.
	private static final class TempMediaSet extends HandlerBaseObject implements MediaSet
	{
		// Constructor.
		public TempMediaSet()
		{
			super(true);
		}

		// Delete media set.
		@Override
		public Handle delete(DeletionCallback callback, Handler handler, int flags)
		{
			Log.e(TAG, "openMediaList() - Cannot delete temporary media set");
			return null;
		}

		// Delete media.
		@Override
		public Handle deleteMedia(Media media, MediaDeletionCallback callback, Handler handler, int flags)
		{
			return null;
		}

		// Get media set type.
		@Override
		public Type getType()
		{
			return Type.OTHER;
		}

		@Override
		public MediaList openMediaList(MediaComparator comparator, int maxMediaCount, int flags)
		{
			Log.e(TAG, "openMediaList() - Cannot open media list");
			return MediaList.EMPTY;
		}
	}
	
	
	// Constructor.
	MediaManagerImpl(GalleryApplication application)
	{
		super("Media manager", application, true);
	}
	
	
	/**
	 * Access content provider in content thread.
	 * @param contentUri Content URI to access.
	 * @param callback Call-back to access content provider.
	 * @return Handle to this operation.
	 */
	@Override
	public Handle accessContentProvider(Uri contentUri, ContentProviderAccessCallback callback)
	{
		if(contentUri == null)
		{
			Log.e(TAG, "accessContentProvider() - No content URI");
			return null;
		}
		if(callback == null)
		{
			Log.e(TAG, "accessContentProvider() - No call-back");
			return null;
		}
		ContentProviderAccessHandle handle = new ContentProviderAccessHandle(contentUri, callback);
		synchronized(m_Lock)
		{
			startContentThread();
			if(!m_ContentThreadHandler.sendMessage(Message.obtain(m_ContentThreadHandler, MSG_ACCESS_CONTENT_PROVIDER, handle)))
			{
				Log.e(TAG, "accessContentProvider() - Fail to send message to content thread");
				return null;
			}
		}
		return handle;
	}
	
	
	// Access content provider (in content thread).
	private void accessContentProvider(ContentProviderAccessHandle handle)
	{
		ContentProviderClient client = null;
		try
		{
			if(m_ContentResolver == null)
				m_ContentResolver = GalleryApplication.current().getContentResolver();
			client = m_ContentResolver.acquireUnstableContentProviderClient(handle.contentUri);
			handle.callback.onAccessContentProvider(m_ContentResolver, handle.contentUri, client);
		}
		catch(Throwable ex)
		{
			Log.e(TAG, "accessContentProvider() - Fail to access content provider", ex);
		}
		finally
		{
			if(client != null)
				client.release();
			handle.complete();
		}
	}
	
	
	/**
	 * Activate media manager.
	 * @return Handle to activation.
	 */
	@Override
	public Handle activate()
	{
		// check state
		verifyAccess();
		
		// create handle
		Handle handle = new Handle("ActivateMediaManager")
		{
			@Override
			protected void onClose(int flags)
			{
				deactivate(this);
			}
		};
		
		// activate
		m_ActivateHandles.add(handle);
		if(m_ActivateHandles.size() == 1)
		{
			Log.w(TAG, "activate()");
			
			// activate
			startContentThread();
			m_ContentThreadHandler.removeMessages(MSG_RELEASE_MEDIA_TABLE);
			m_ContentThreadHandler.sendEmptyMessage(MSG_SETUP_MEDIA_TABLE);
			m_ContentThreadHandler.sendEmptyMessage(MSG_CHECK_CONTENT_CHANGES);
			
			// register content change call-back
			HandleSet handles = new HandleSet();
			Handler handler = this.getHandler();
			handles.addHandle(registerContentChangedCallback(CONTENT_URI_IMAGE, m_MediaContentChangeCB, handler));
			handles.addHandle(registerContentChangedCallback(CONTENT_URI_VIDEO, m_MediaContentChangeCB, handler));
			m_MediaContentChangeCBHandle = handles;
			
			// update property
			this.setReadOnly(PROP_IS_ACTIVE, true);
		}
		
		// complete
		return handle;
	}
	
	
	/**
	 * Add OnePlus flags to media extra info. 
	 * @param contentUri Content URI to update.
	 * @param flags New flags to add.
	 * @return True if update successfully.
	 */
	@Override
	public boolean addOnePlusFlags(Uri contentUri, final int flags)
	{
		Message msg = Message.obtain(m_ContentThreadHandler, MSG_UPDATE_ONEPLUS_FLAGS, new Object[] {contentUri, flags, true});
		return m_ContentThreadHandler.sendMessage(msg);
	}
	
	
	// Cancel content provider access.
	private void cancelContentProviderAccess(ContentProviderAccessHandle handle)
	{
		if(m_ContentThreadHandler != null)
			m_ContentThreadHandler.removeMessages(MSG_ACCESS_CONTENT_PROVIDER, handle);
	}
	
	
	// Check all content changes (in content thread).
	private void checkContentChanges()
	{
		// check state
		if(m_ContentObservers.isEmpty() || !get(PROP_IS_ACTIVE))
			return;
		
		// check changes
		for(ContentObserver observer : m_ContentObservers.values())
		{
			if(observer.lastChangedTime > 0)
				observer.notifyChange(true);
		}
	}
	
	
	/**
	 * Create new media set list.
	 * @return Media set list.
	 */
	@Override
	public MediaSetList createMediaSetList()
	{
		// check state
		verifyAccess();
		
		// create list
		MediaSetListImpl list = new MediaSetListImpl();
		m_ActiveMediaSetLists.add(new WeakReference<MediaSetListImpl>(list));
		Log.v(TAG, "createMediaSetList() - Active list count : ", m_ActiveMediaSetLists.size());
		
		// register content change call-back
		if(m_ActiveMediaSetLists.size() == 1)
		{
			HandleSet handles = new HandleSet();
			Handler handler = this.getHandler();
			handles.addHandle(registerContentChangedCallback(CONTENT_URI_IMAGE, m_MediaContentChangeCB, handler));
			handles.addHandle(registerContentChangedCallback(CONTENT_URI_VIDEO, m_MediaContentChangeCB, handler));
			m_MediaContentChangeCBHandle = handles;
		}
		
		// create system sets
		if(m_CameraRollMediaSet == null)
			m_CameraRollMediaSet = new CameraRollMediaSet();
		list.addMediaSet(m_CameraRollMediaSet);
		
		// refresh directory sets
		if(m_ActiveMediaSetLists.size() == 1)
			refreshDirectoryMediaSets();
		
		// complete
		return list;
	}
	
	
	/**
	 * Create temporary {@link Media} instance for specific content URI.
	 * @param contentUri Content URI of media.
	 * @param callback Call-back to received created media.
	 * @return Handle to media creation.
	 */
	@Override
	public Handle createTemporaryMedia(final Uri contentUri, final MediaCreationCallback callback)
	{
		// check parameter
		verifyAccess();
		if(contentUri == null)
		{
			Log.e(TAG, "createTemporaryMedia() - No content URI");
			return null;
		}
		if(callback == null)
		{
			Log.e(TAG, "createTemporaryMedia() - No call-back to receive result");
			return null;
		}
		
		// create temporary media set
		if(m_TempMediaSet == null)
		{
			Log.v(TAG, "createTemporaryMedia() - Create temporary media set");
			m_TempMediaSet = new TempMediaSet();
		}
		
		// create handle
		final Handle handle = new Handle("CreateTempMedia")
		{
			@Override
			protected void onClose(int flags)
			{}
		};
		
		// create media
		Log.v(TAG, "createTemporaryMedia() - Content URI : ", contentUri);
		final boolean isFileUri = contentUri.getScheme().equals("file");
		Handle accessHandle = accessContentProvider(CONTENT_URI_FILE, new ContentProviderAccessCallback()
		{
			@Override
			public void onAccessContentProvider(ContentResolver contentResolver, Uri uri, ContentProviderClient client) throws RemoteException
			{
				// check state
				if(!Handle.isValid(handle))
					return;
				
				// check file path
				String selection;
				String[] selectionArgs;
				
				if(isFileUri)
				{
					selection = (MediaColumns.DATA + "=?");
					selectionArgs = new String[]{ contentUri.getPath() };
				}
				else
				{
					uri = Uri.parse(CONTENT_URI_FILE + "/" + Path.getFileName(contentUri.getPath()));
					selection = null;
					selectionArgs = null;
				}
				
				// query
				Media media = null;
				try
				{
					Cursor cursor = client.query(uri, MediaStoreMedia.getMediaColumns(), selection, selectionArgs, null);
					if(cursor != null)
					{
						try
						{
							if(cursor.moveToNext())
								media = MediaStoreMedia.create(cursor, new ExtraMediaInfo(-1, -1), getHandler());
							else
								Log.e(TAG, "createTemporaryMedia() - Content not found");
						}
						finally
						{
							cursor.close();
						}
					}
					else
						Log.e(TAG, "createTemporaryMedia() - Content not found");
				}
				catch(Throwable ex)
				{
					Log.e(TAG, "createTemporaryMedia() - Fail to create", ex);
				}
				final Media result = media;
				getHandler().post(new Runnable()
				{
					@Override
					public void run()
					{
						if(Handle.isValid(handle))
							callback.onCreationCompleted(handle, (result != null ? result.getContentUri() : contentUri), result);
					}
				});
			}
		});
		if(!Handle.isValid(accessHandle))
		{
			Log.e(TAG, "createTemporaryMedia() - Fail to access content provider");
			return null;
		}
		
		// complete
		return handle;
	}
	
	
	// Deactivate media manager.
	private void deactivate(Handle handle)
	{
		// check state
		verifyAccess();
		
		// deactivate
		if(!m_ActivateHandles.remove(handle) || !m_ActivateHandles.isEmpty())
			return;
		
		Log.w(TAG, "deactivate()");
		
		// stop checking content changes
		if(m_ContentThreadHandler != null)
			m_ContentThreadHandler.removeMessages(MSG_CHECK_CONTENT_CHANGES);
		
		// unregister content change call-back
		m_MediaContentChangeCBHandle = Handle.close(m_MediaContentChangeCBHandle);
		
		// release media table later
		m_ContentThreadHandler.sendEmptyMessageDelayed(MSG_RELEASE_MEDIA_TABLE, DURATION_RELEASE_MEDIA_TABLE_DELAY);
		
		// update property
		this.setReadOnly(PROP_IS_ACTIVE, false);
	}
	
	
	/**
	 * Delete media.
	 * @param media Media to delete.
	 * @param callback Call-back to receive deletion state.
	 * @param handler Handler to perform call-back.
	 * @return Handle to media deletion.
	 */
	@Override
	public Handle deleteMedia(Media media, MediaDeletionCallback callback, Handler handler)
	{
		if(media == null)
		{
			Log.e(TAG, "deleteMedia() - No media to delete");
			return null;
		}
		MediaDeletionHandle handle = new MediaDeletionHandle(media, callback, handler);
		startContentThread();
		Message.obtain(m_ContentThreadHandler, MSG_DELETE_MEDIA, handle).sendToTarget();
		return handle;
	}
	
	
	// Delete media (in content thread).
	private void deleteMedia(MediaDeletionHandle handle)
	{
		// check state
		if(!Handle.isValid(handle))
			return;
		
		// start deletion
		handle.callOnDeletionStarted();
		
		// delete file directly
		Uri contentUri = handle.media.getContentUri();
		if(contentUri == null)
		{
			String filePath = handle.media.getFilePath();
			if(filePath != null)
			{
				Log.v(TAG, "deleteMedia() - Delete ", filePath);
				try
				{
					File file = new File(filePath);
					handle.callOnDeletionCompleted(!file.exists() || file.delete());
				}
				catch(Throwable ex)
				{
					Log.e(TAG, "deleteMedia() - Fail to delete " + filePath, ex);
					handle.callOnDeletionCompleted(false);
				}
			}
			else
			{
				Log.e(TAG, "deleteMedia() - No content URI and file path to delete");
				handle.callOnDeletionCompleted(false);
			}
			return;
		}
		
		// get media ID
		long id = ContentUris.parseId(contentUri);
		if(id < 0)
		{
			Log.e(TAG, "deleteMedia() - Invalid content URI : " + contentUri);
			handle.callOnDeletionCompleted(false);
			return;
		}
		
		Log.v(TAG, "deleteMedia() - Delete ", contentUri);
		
		// delete media
		accessContentProvider(contentUri, new ContentProviderAccessCallback()
		{
			@Override
			public void onAccessContentProvider(ContentResolver contentResolver, Uri contentUri, ContentProviderClient client) throws RemoteException
			{
				client.delete(contentUri, null, null);
			}
		});
		
		// remove from media table
		Media removedMedia;
		synchronized(m_MediaTable)
		{
			removedMedia = m_MediaTable.remove(id);
		}
		
		// call-back
		if(removedMedia != null)
		{
			for(int i = m_MediaChangeCallbackHandles.size() - 1 ; i >= 0 ; --i)
				m_MediaChangeCallbackHandles.get(i).callOnMediaDeleted(id, removedMedia);
		}
		
		// completed
		handle.callOnDeletionCompleted(true);
	}
	
	
	// Get Looper for content thread.
	private Looper getContentThreadLooper()
	{
		startContentThread();
		return m_ContentThreadHandler.getLooper();
	}
	
	
	// Handle content thread message.
	private void handleContentThreadMessage(Message msg)
	{
		switch(msg.what)
		{
			case MSG_ACCESS_CONTENT_PROVIDER:
				accessContentProvider((ContentProviderAccessHandle)msg.obj);
				break;
				
			case MSG_CHECK_CONTENT_CHANGES:
				checkContentChanges();
				break;
				
			case MSG_DELETE_MEDIA:
				deleteMedia((MediaDeletionHandle)msg.obj);
				break;
				
			case MSG_NOTIFY_CONTENT_CHANGED:
				notifyContentChangedDirectly((Uri)msg.obj);
				break;
				
			case MSG_REGISTER_CONTENT_CHANGED_CB:
				registerContentChangedCallback((ContentChangeCallbackHandle)msg.obj);
				break;
				
			case MSG_REGISTER_MEDIA_CHANGED_CB:
				registerMediaChangeCallback((MediaChangeCallbackHandle)msg.obj);
				break;
				
			case MSG_RELEASE_MEDIA_TABLE:
				releaseMediaTable(msg.arg1 != 0);
				break;
				
			case MSG_SETUP_MEDIA_TABLE:
				setupMediaTable();
				break;
				
			case MSG_SYNC_MEDIA_TABLE:
				syncMediaTable();
				break;
				
			case MSG_UNREGISTER_CONTENT_CHANGED_CB:
				unregisterContentChangedCallback((ContentChangeCallbackHandle)msg.obj);
				break;
				
			case MSG_UNREGISTER_MEDIA_CHANGED_CB:
				unregisterMediaChangeCallback((MediaChangeCallbackHandle)msg.obj);
				break;
				
			case MSG_UPDATE_ONEPLUS_FLAGS:
				Object[] objs = (Object[])msg.obj;
				updateOnePlusFlags((Uri)objs[0], (Integer)objs[1], (Boolean)objs[2]);
				break;
		}
	}
	
	
	// Handle main thread message.
	@Override
	protected void handleMessage(Message msg)
	{
		switch(msg.what)
		{
			case MSG_DIR_MEDIA_SET_CREATED:
				onDirectoryMediaSetCreated(msg.arg1, (String)msg.obj);
				break;
				
			case MSG_DIR_MEDIA_SET_DELETED:
				onDirectoryMediaSetDeleted(msg.arg1);
				break;
				
			case MSG_REFRESH_MEDIA_SET_LISTS:
				refreshMediaSetLists();
				break;
				
			default:
				super.handleMessage(msg);
				break;
		}
	}
	
	
	/**
	 * Check whether current thread is content thread or not.
	 * @return True if current thread is content thread.
	 */
	@Override
	public boolean isContentThread()
	{
		return (Thread.currentThread() == m_ContentThread);
	}
	
	
	/**
	 * Notify content changed directly.
	 * @param contentUri Content URI.
	 */
	public void notifyContentChanged(Uri contentUri)
	{
		if(contentUri == null)
		{
			Log.e(TAG, "notifyContentChanged() - No content URI");
			return;
		}
		
		Log.v(TAG, "notifyContentChanged() - Content URI : ", contentUri);
		
		if(isContentThread())
			notifyContentChangedDirectly(contentUri);
		else
		{
			startContentThread();
			Message.obtain(m_ContentThreadHandler, MSG_NOTIFY_CONTENT_CHANGED, contentUri).sendToTarget();
		}
	}
	
	
	// Notify content change (in content thread).
	private void notifyContentChangedDirectly(Uri contentUri)
	{
		String uriString = contentUri.toString();
		for(ContentObserver observer : m_ContentObservers.values())
		{
			if(uriString.startsWith(observer.contentUri.toString()))
			{
				observer.notifyChange(contentUri, false);
				break;
			}
		}
	}
	
	
	// Called when media set is deleted 
	@Override
	public void notifyMediaSetDeleted(MediaSet mediaSet)
	{
		if(mediaSet == null)
		{
			Log.w(TAG, "notifyMediaSetDeleted() - mediaSet is null");
			return;
		}
		
		if(mediaSet.getType() != Type.SYSTEM)
		{
			for(int i = m_ActiveMediaSetLists.size() - 1 ; i >= 0 ; --i)
			{
				MediaSetListImpl list = m_ActiveMediaSetLists.get(i).get();
				if(list != null)
					list.removeMediaSet(mediaSet);
				else
					m_ActiveMediaSetLists.remove(i);
			}
			mediaSet.release();
		}	
	}
	
	
	/**
	 * Obtain media instance.
	 * @param cursor Media ID.
	 * @return Media instance, or Null if instance is not created yet.
	 */
	@Override
	public Media obtainMedia(long id)
	{
		synchronized(m_MediaTable)
		{
			return m_MediaTable.get(id);
		}
	}
	
	
	/**
	 * Obtain media instance.
	 * @param cursor Cursor to read media information.
	 * @param idColumnIndex Column index of {@link MediaColumns#_ID}, or negative number to find index automatically.
	 * @return Media instance, or Null if fail to create instance.
	 */
	@Override
	public Media obtainMedia(Cursor cursor, int idColumnIndex)
	{
		// check parameter
		if(cursor == null)
		{
			Log.e(TAG, "obtainMedia() - No cursor");
			return null;
		}
		
		// obtain media
		try
		{
			// get ID
			Long id;
			if(idColumnIndex >= 0)
				id = cursor.getLong(idColumnIndex);
			else
				id = CursorUtils.getLong(cursor, MediaColumns._ID, -1);
			if(id < 0)
			{
				Log.e(TAG, "obtainMedia() - No media ID");
				return null;
			}
			
			// get extra media info
			ExtraMediaInfo extraInfo = GalleryDatabaseManager.getExtraMediaInfo(id);
			
			// obtain media
			synchronized(m_MediaTable)
			{
				// use current media
				Media media = m_MediaTable.get(id);
				if(media != null)
				{
					if(media instanceof MediaStoreMedia)
					{
						if(((MediaStoreMedia)media).update(cursor, extraInfo))
						{
							for(int i = m_MediaChangeCallbackHandles.size() - 1 ; i >= 0 ; --i)
								m_MediaChangeCallbackHandles.get(i).callOnMediaUpdated(id, media);
						}
					}
					return media;
				}
				
				// create new media
				int mediaCount = m_MediaTable.size();
				media = MediaStoreMedia.create(cursor, extraInfo, getHandler());
				if(media == null)
					return null;
				m_MediaTable.put(id, media);
				
				// check whether this is new media or not
				if(m_MediaTable.size() != mediaCount)
				{
					for(int i = m_MediaChangeCallbackHandles.size() - 1 ; i >= 0 ; --i)
						m_MediaChangeCallbackHandles.get(i).callOnMediaCreated(id, media);
				}
				
				// complete
				return media;
			}
		}
		catch(Throwable ex)
		{
			Log.e(TAG, "obtainMedia() - Fail to obtain", ex);
			return null;
		}
	}
	
	
	// Called when new directory media set created. (in main thread)
	private void onDirectoryMediaSetCreated(int id, String path)
	{
		if(m_ActiveMediaSetLists.isEmpty() || m_DirectoryMediaSets.containsKey(id))
			return;
		DirectoryMediaSet set = new DirectoryMediaSet(path, id);
		m_DirectoryMediaSets.put(id, set);
		for(int i = m_ActiveMediaSetLists.size() - 1 ; i >= 0 ; --i)
		{
			MediaSetListImpl list = m_ActiveMediaSetLists.get(i).get();
			if(list != null)
				list.addMediaSet(set);
			else
				m_ActiveMediaSetLists.remove(i);
		}
	}
	
	
	// Called when directory media set deleted. (in main thread)
	private void onDirectoryMediaSetDeleted(int id)
	{
		DirectoryMediaSet set = m_DirectoryMediaSets.get(id);
		if(set == null)
			return;
		m_DirectoryMediaSets.remove(id);
		for(int i = m_ActiveMediaSetLists.size() - 1 ; i >= 0 ; --i)
		{
			MediaSetListImpl list = m_ActiveMediaSetLists.get(i).get();
			if(list != null)
				list.removeMediaSet(set);
			else
				m_ActiveMediaSetLists.remove(i);
		}
		set.release();
	}
	
	
	// Initialize.
	@Override
	protected void onInitialize()
	{
		// call super
		super.onInitialize();
		
		// start content thread
		this.startContentThread();
	}
	
	
	// Called when media set list released. (in main thread)
	private void onMediaSetListReleased(MediaSetListImpl list)
	{
		for(int i = m_ActiveMediaSetLists.size() - 1 ; i >= 0 ; --i)
		{
			MediaSetListImpl candList = m_ActiveMediaSetLists.get(i).get();
			if(candList == list)
			{
				m_ActiveMediaSetLists.remove(i);
				
				Log.v(TAG, "onMediaSetListReleased() - Active list count : ", m_ActiveMediaSetLists.size());
				
				if(m_ActiveMediaSetLists.isEmpty())
				{
					// release system sets
					if(m_CameraRollMediaSet != null)
					{
						m_CameraRollMediaSet.release();
						m_CameraRollMediaSet = null;
					}
					
					// release directory sets
					for(DirectoryMediaSet set : m_DirectoryMediaSets.values())
						set.release();
					m_DirectoryMediaSets.clear();
				}
			}
			else if(candList == null)
				m_ActiveMediaSetLists.remove(i);
		}
	}
	
	
	/**
	 * Post action to content thread.
	 * @param r Action to run.
	 * @return True if action posted to content thread successfully.
	 */
	public boolean postToContentThread(Runnable r)
	{
		return postToContentThread(r, 0);
	}
	
	
	/**
	 * Post action to content thread.
	 * @param r Action to run.
	 * @param delayMillis Delay time in milliseconds.
	 * @return True if action posted to content thread successfully.
	 */
	@Override
	public boolean postToContentThread(Runnable r, long delayMillis)
	{
		startContentThread();
		if(delayMillis <= 0)
			return m_ContentThreadHandler.post(r);
		return m_ContentThreadHandler.postDelayed(r, delayMillis);
	}
	
	
	// Refresh directory media sets. (in main thread)
	private void refreshDirectoryMediaSets()
	{
		// get current directory ID
		final HashSet<Integer> dirIdTable;
		if(m_DirectoryMediaSets != null)
			dirIdTable = new HashSet<>(m_DirectoryMediaSets.keySet());
		else
			dirIdTable = new HashSet<>();
		
		// refresh
		accessContentProvider(CONTENT_URI_FILE, new ContentProviderAccessCallback()
		{
			@Override
			public void onAccessContentProvider(ContentResolver contentResolver, Uri contentUri, ContentProviderClient client) throws RemoteException
			{
				String dcimPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
				Cursor cursor = client.query(contentUri, DIR_COLUMNS, DIR_QUERY_CONDITION, new String[]{ dcimPath + "/%" }, null);
				if(cursor != null)
				{
					try
					{
						// add media sets
						HashSet<Integer> newDirIdTable = new HashSet<>();
						while(cursor.moveToNext())
						{
							int id = cursor.getInt(0);
							if(dirIdTable.remove(id) || !newDirIdTable.add(id))
								continue;
							String path = Path.getDirectoryPath(cursor.getString(1));
							Message.obtain(getHandler(), MSG_DIR_MEDIA_SET_CREATED, id, 0, path).sendToTarget();
						}
						
						// remove media sets
						for(int id : dirIdTable)
							Message.obtain(getHandler(), MSG_DIR_MEDIA_SET_DELETED, id, 0).sendToTarget();
					}
					finally
					{
						cursor.close();
					}
				}
			}
		});
	}
	
	
	// Refresh media set lists (in main thread).
	private void refreshMediaSetLists()
	{
		// check state
		if(m_ActiveMediaSetLists.isEmpty())
			return;
		
		Log.v(TAG, "refreshMediaSetLists()");
		
		// refresh
		refreshDirectoryMediaSets();
	}
	
	
	/**
	 * Register content change call-back.
	 * @param contentUri Content URI to listen.
	 * @param callback Call-back.
	 * @return Handle to registered call-back.
	 */
	public Handle registerContentChangedCallback(Uri contentUri, ContentChangeCallback callback)
	{
		return registerContentChangedCallback(contentUri, callback, null);
	}
	
	
	/**
	 * Register content change call-back.
	 * @param contentUri Content URI to listen.
	 * @param callback Call-back.
	 * @param handler Handler to perform call-back.
	 * @return Handle to registered call-back.
	 */
	public Handle registerContentChangedCallback(Uri contentUri, ContentChangeCallback callback, Handler handler)
	{
		if(contentUri == null)
		{
			Log.e(TAG, "registerContentChangedCallback() - No content URI");
			return null;
		}
		if(callback == null)
		{
			Log.e(TAG, "registerContentChangedCallback() - No call-back");
			return null;
		}
		ContentChangeCallbackHandle handle = new ContentChangeCallbackHandle(contentUri, callback, handler);
		if(isContentThread())
			registerContentChangedCallback(handle);
		else
		{
			startContentThread();
			Message.obtain(m_ContentThreadHandler, MSG_REGISTER_CONTENT_CHANGED_CB, handle).sendToTarget();
		}
		return handle;
	}
	
	
	// Register content change call-back (in content thread).
	private void registerContentChangedCallback(ContentChangeCallbackHandle handle)
	{
		if(m_ContentObservers == null)
			m_ContentObservers = new HashMap<>();
		ContentObserver observer = m_ContentObservers.get(handle.contentUri);
		if(observer == null)
		{
			Log.v(TAG, "registerContentChangedCallback() - Register to ", handle.contentUri);
			if(m_ContentResolver == null)
				m_ContentResolver = GalleryApplication.current().getContentResolver();
			observer = new ContentObserver(handle.contentUri, m_ContentThreadHandler);
			m_ContentObservers.put(handle.contentUri, observer);
			m_ContentResolver.registerContentObserver(handle.contentUri, true, observer);
		}
		observer.callbackHandles.add(handle);
	}
	
	
	/**
	 * Register call-back to monitor media change.
	 * @param callback Call-back to add.
	 * @param handler Handler to perform call-back.
	 * @return Handle to call-back.
	 */
	@Override
	public Handle registerMediaChangeCallback(MediaChangeCallback callback, Handler handler)
	{
		if(callback == null)
		{
			Log.e(TAG, "registerMediaChangeCallback() - No call-back to register");
			return null;
		}
		MediaChangeCallbackHandle handle = new MediaChangeCallbackHandle(callback, handler);
		if(!isContentThread())
		{
			startContentThread();
			Message.obtain(m_ContentThreadHandler, MSG_REGISTER_MEDIA_CHANGED_CB, handle).sendToTarget();
		}
		else
			registerMediaChangeCallback(handle);
		return handle;
	}
	
	
	// Register media change call-back (in content thread).
	private void registerMediaChangeCallback(MediaChangeCallbackHandle handle)
	{
		m_MediaChangeCallbackHandles.add(handle);
	}
	
	
	// Release media table (in content thread).
	private void releaseMediaTable(boolean clearId)
	{
		Log.w(TAG, "releaseMediaTable() - Clear ID : " + clearId);
		synchronized(m_MediaTable)
		{
			if(clearId)
				m_MediaTable.clear();
			else if(!m_MediaTable.isEmpty())
			{
				Long[] idArray = new Long[m_MediaTable.size()];
				m_MediaTable.keySet().toArray(idArray);
				for(int i = idArray.length - 1 ; i >= 0 ; --i)
					m_MediaTable.put(idArray[i], null);
			}
		}
		m_ContentThreadHandler.removeMessages(MSG_SYNC_MEDIA_TABLE);
	}
	
	
	/**
	 * Remove OnePlus flags from media extra info. 
	 * @param contentUri Content URI to update.
	 * @param flags Flags to remove.
	 * @return True if update successfully.
	 */
	public boolean removeOnePlusFlags(Uri contentUri, final int flags)
	{
		Message msg = Message.obtain(m_ContentThreadHandler, MSG_UPDATE_ONEPLUS_FLAGS, new Object[]{contentUri, flags, false});
		return m_ContentThreadHandler.sendMessage(msg);
	}
	
	
	/**
	 * Change favorite state for specific media.
	 * @param contentUri Content URI of media.
	 * @param isFavorite True to set as favorite media, False otherwise.
	 * @return True if update successfully.
	 */
	@Override
	public boolean setFavorite(Uri contentUri, boolean isFavorite)
	{
		Message msg = Message.obtain(m_ContentThreadHandler, MSG_UPDATE_ONEPLUS_FLAGS, new Object[]{contentUri, ONEPLUS_FLAG_FAVORITE, isFavorite});
		return m_ContentThreadHandler.sendMessage(msg);
	}
	
	
	// Setup media table (in content thread).
	private void setupMediaTable()
	{
		// check state
		synchronized(m_MediaTable)
		{
			if(!m_MediaTable.isEmpty())
			{
				Log.w(TAG, "setupMediaTable() - Ready, " + m_MediaTable.size() + " entries");
				syncMediaTable();
				return;
			}
		}
		
		// create table with IDs
		try
		{
			// create table with IDs
			long time = SystemClock.elapsedRealtime();
			ContentResolver contentResolver = GalleryApplication.current().getContentResolver();
			ContentProviderClient client = contentResolver.acquireUnstableContentProviderClient(CONTENT_URI_FILE);
			if(client != null)
			{
				Cursor cursor = null;
				try
				{
					cursor = client.query(CONTENT_URI_FILE, MEDIA_ID_COLUMNS, MEDIA_QUERY_CONDITION, null, null);
					synchronized(m_MediaTable)
					{
						while(cursor.moveToNext())
						{
							long id = cursor.getLong(0);
							m_MediaTable.put(id, null);
						}
					}
				}
				finally
				{
					if(cursor != null)
						cursor.close();
					client.release();
					time = (SystemClock.elapsedRealtime() - time);
					Log.w(TAG, "setupMediaTable() - Take " + time + " ms to setup table with " + m_MediaTable.size() + " entries");
				}
			}
			else
				Log.e(TAG, "setupMediaTable() - Fail to acquire content provider client");
		}
		catch(Throwable ex)
		{
			Log.e(TAG, "setupMediaTable() - Fail to setup table", ex);
		}
	}
	
	
	// Start content thread.
	private void startContentThread()
	{
		if(m_ContentThreadHandler != null)
			return;
		synchronized(m_Lock)
		{
			if(m_ContentThreadHandler == null)
			{
				m_ContentThread = new HandlerThread("Gallery media content thread");
				Log.v(TAG, "startContentThread() - Start content thread [start]");
				m_ContentThread.start();
				m_ContentThreadHandler = new Handler(m_ContentThread.getLooper())
				{
					@Override
					public void handleMessage(Message msg)
					{
						handleContentThreadMessage(msg);
					}
				};
				Log.v(TAG, "startContentThread() - Start content thread [end]");
			}
		}
	}
	
	
	// Synchronize media table with media provider (in content thread).
	private void syncMediaTable()
	{
		long time = SystemClock.elapsedRealtime();
		accessContentProvider(CONTENT_URI_FILE, new ContentProviderAccessCallback()
		{
			@Override
			public void onAccessContentProvider(ContentResolver contentResolver, Uri contentUri, ContentProviderClient client) throws RemoteException
			{
				// copy current IDs
				HashSet<Long> currentIDs;
				synchronized(m_MediaTable)
				{
					currentIDs = new HashSet<Long>(m_MediaTable.keySet());
				}
				
				// synchronize
				Cursor cursor = client.query(CONTENT_URI_FILE, MEDIA_ID_COLUMNS, MEDIA_QUERY_CONDITION, null, null);
				if(cursor != null)
				{
					synchronized(m_MediaTable)
					{
						// add entries
						int addedCount = 0;
						while(cursor.moveToNext())
						{
							Long id = cursor.getLong(0);
							if(!currentIDs.remove(id))
							{
								++addedCount;
								m_MediaTable.put(id, null);
								for(int i = m_MediaChangeCallbackHandles.size() - 1 ; i >= 0 ; --i)
									m_MediaChangeCallbackHandles.get(i).callOnMediaCreated(id, null);
							}
						}
						if(addedCount > 0)
							Log.w(TAG, "syncMediaTable() - Add " + addedCount + " entries");
						
						// remove entries
						if(!currentIDs.isEmpty())
						{
							for(Long id : currentIDs)
							{
								Media media = m_MediaTable.remove(id);
								for(int i = m_MediaChangeCallbackHandles.size() - 1 ; i >= 0 ; --i)
									m_MediaChangeCallbackHandles.get(i).callOnMediaDeleted(id, media);
							}
							Log.w(TAG, "syncMediaTable() - Remove " + currentIDs.size() + " entries");
						}
					}
				}
				else
				{
					Log.e(TAG, "syncMediaTable() - Fail to query");
					return;
				}
			}
		});
		time = (SystemClock.elapsedRealtime() - time);
		Log.w(TAG, "syncMediaTable() - Take " + time + " ms to synchronize");
	}
	
	
	// Unregister content change call-back (in content thread)
	private void unregisterContentChangedCallback(ContentChangeCallbackHandle handle)
	{
		if(m_ContentObservers == null)
			return;
		ContentObserver observer = m_ContentObservers.get(handle.contentUri);
		if(observer == null || !observer.callbackHandles.remove(handle) || !observer.callbackHandles.isEmpty())
			return;
		Log.v(TAG, "unregisterContentChangedCallback() - Unregister from ", handle.contentUri);
		m_ContentObservers.remove(handle.contentUri);
		m_ContentResolver.unregisterContentObserver(observer);
	}
	
	
	// Unregister media change call-back (in content thread).
	private void unregisterMediaChangeCallback(MediaChangeCallbackHandle handle)
	{
		m_MediaChangeCallbackHandles.remove(handle);
	}
	
	
	// Update OnePlus flags column.
	private boolean updateOnePlusFlags(Uri contentUri, int flags, boolean add)
	{
		// check state
		if(contentUri == null)
		{
			Log.e(TAG, "updateOnePlusFlags() - No content URI to set");
			return false;
		}
		
		Log.v(TAG, "updateOnePlusFlags() - Uri: ", contentUri, ", flags: ", flags, ", add: ", add);
		
		// get id & extra media info
		long id = ContentUris.parseId(contentUri);
		ExtraMediaInfo extraMediaInfo = GalleryDatabaseManager.getExtraMediaInfo(id);
		
		// if there isn't mapped extra media info, creates new one.
		// Else, update it.
		boolean success = false;
		if(extraMediaInfo == null)
		{
			extraMediaInfo = new ExtraMediaInfo(id, flags);
			if(GalleryDatabaseManager.addExtraMediaInfo(extraMediaInfo) > -1)
				success = true;
		}
		else
		{
			// check flags
			int currentFlags = extraMediaInfo.oneplusFlags;
			int newFlags;
			if(add)
				newFlags = (currentFlags | flags);
			else
				newFlags = (currentFlags & ~flags);
			if(currentFlags == newFlags)
				return true;
			
			// update flags
			extraMediaInfo.oneplusFlags = newFlags;
			if(GalleryDatabaseManager.updateExtraMediaInfo(extraMediaInfo) > 0)
				success = true;
		}
		
		// if success, update media
		if(success)
		{
			synchronized(m_MediaTable)
			{
				// use current media
				Media media = m_MediaTable.get(id);
				if(media != null)
				{
					if(media instanceof MediaStoreMedia)
					{
						if(((MediaStoreMedia)media).update(null, extraMediaInfo))
						{
							Log.v(TAG, "updateOnePlusFlags() - Update success");
							for(int i = m_MediaChangeCallbackHandles.size() - 1 ; i >= 0 ; --i)
								m_MediaChangeCallbackHandles.get(i).callOnMediaUpdated(id, media);
						}
						else
							Log.v(TAG, "updateOnePlusFlags() - Update failed");
					}
				}
				else
					Log.w(TAG, "updateOnePlusFlags() - Media is null");
			}
		}
		else
			Log.w(TAG, "updateOnePlusFlags() - No need to update");
			
		// complete
		return success;
	}
}
