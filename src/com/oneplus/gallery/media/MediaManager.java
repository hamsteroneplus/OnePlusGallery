package com.oneplus.gallery.media;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.oneplus.base.Handle;
import com.oneplus.base.HandleSet;
import com.oneplus.base.HandlerBaseObject;
import com.oneplus.base.ListHandlerBaseObject;
import com.oneplus.base.Log;
import com.oneplus.gallery.GalleryApplication;
import com.oneplus.gallery.ListChangeEventArgs;
import com.oneplus.gallery.media.MediaSet.Type;
import com.oneplus.io.Path;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
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
public class MediaManager
{
	// Constants.
	private static final String TAG = "MediaManager";
	private static final Uri CONTENT_URI_FILE = Files.getContentUri("external");
	private static final Uri CONTENT_URI_IMAGE = Images.Media.EXTERNAL_CONTENT_URI;
	private static final Uri CONTENT_URI_VIDEO = Video.Media.EXTERNAL_CONTENT_URI;
	private static final String[] DIR_COLUMNS = new String[]{
		FileColumns.PARENT,
		FileColumns.DATA,
	};
	private static final String DIR_QUERY_CONDITION = 
			"(" 
					+ FileColumns.MEDIA_TYPE + "=" + FileColumns.MEDIA_TYPE_IMAGE
					+ " OR " + FileColumns.MEDIA_TYPE + "=" + FileColumns.MEDIA_TYPE_VIDEO
			+ ")"
			+ " AND NOT (" + FileColumns.DATA + " LIKE ?)"
	;
	private static final long INTERVAL_CHECK_CONTENT_CHANGES = 2000;
	private static final int MSG_ACCESS_CONTENT_PROVIDER = 10000;
	private static final int MSG_REGISTER_CONTENT_CHANGED_CB = 10010;
	private static final int MSG_UNREGISTER_CONTENT_CHANGED_CB = 10011;
	private static final int MSG_DIR_MEDIA_SET_CREATED = 10020;
	private static final int MSG_DIR_MEDIA_SET_DELETED = 10021;
	private static final int MSG_REFRESH_MEDIA_SET_LISTS = 10030;
	private static final int MSG_CHECK_CONTENT_CHANGES = 10040;
	
	
	// Fields.
	private static final List<Handle> m_ActivateHandles = new ArrayList<>();
	private static final List<ActiveStateCallback> m_ActiveStateCallbacks = new ArrayList<>();
	private static final List<WeakReference<MediaSetListImpl>> m_ActiveMediaSetLists = new ArrayList<>();
	private static CameraRollMediaSet m_CameraRollMediaSet;
	private static HashMap<Uri, ContentObserver> m_ContentObservers;
	private static ContentResolver m_ContentResolver;
	private static volatile HandlerThread m_ContentThread;
	private static volatile Handler m_ContentThreadHandler;
	private static final HashMap<Integer, DirectoryMediaSet> m_DirectoryMediaSets = new HashMap<>();
	private static volatile Handler m_Handler;
	private static final Object m_Lock = new Object();
	private static Handle m_MediaSetListContentChangeCBHandle;
	private static TempMediaSet m_TempMediaSet;
	
	
	// Call-backs.
	private static final ContentChangeCallback m_MediaSetListContentChangeCB = new ContentChangeCallback()
	{
		@Override
		public void onContentChanged(Uri contentUri)
		{
			m_Handler.sendEmptyMessage(MSG_REFRESH_MEDIA_SET_LISTS);
		}
	};
	
	
	/**
	 * Call-back to access content provider.
	 */
	public interface ContentProviderAccessCallback
	{
		/**
		 * Called when ready to access content provider.
		 * @param contentResolver Content resolver.
		 * @param client Content provider client.
		 */
		void onAccessContentProvider(ContentResolver contentResolver, Uri contentUri, ContentProviderClient client) throws RemoteException;
	}
	
	
	/**
	 * Call-back interface for media manager active state change.
	 */
	public interface ActiveStateCallback
	{
		/**
		 * Called if media manager is activated.
		 */
		void onActivated();
		
		/**
		 * Called if media manager is deactivated.
		 */
		void onDeactivated();
	}
	
	
	/**
	 * Call-back interface for content change.
	 */
	public interface ContentChangeCallback
	{
		/**
		 * Called when content changed.
		 * @param contentUri Changed content URI.
		 */
		void onContentChanged(Uri contentUri);
	}
	
	
	/**
	 * Call-back interface for media creation.
	 */
	public interface MediaCreationCallback
	{
		/**
		 * Called when creation completed.
		 * @param handle Handle returned from {@link MediaManager#createTemporaryMedia(Uri)}.
		 * @param contentUri Content URI of media.
		 * @param media Created media, or Null if creation failed.
		 */
		void onCreationCompleted(Handle handle, Uri contentUri, Media media);
	}
	
	
	/**
	 * {@link Handler} runs on content thread.
	 */
	public static abstract class ContentThreadHandler extends Handler
	{
		/**
		 * Initialize new ContentThreadHandler instance.
		 */
		protected ContentThreadHandler()
		{
			super(getContentThreadLooper());
		}
	}
	
	
	// Handle for content provider access.
	private static final class ContentProviderAccessHandle extends Handle
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
	
	
	// Handle for content changed call-back.
	private static final class ContentChangeCallbackHandle extends Handle
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
	private static final class ContentObserver extends android.database.ContentObserver
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
			if(resetChangeTime)
				this.lastChangedTime = 0;
			for(int i = this.callbackHandles.size() - 1 ; i >= 0 ; --i)
				this.callbackHandles.get(i).notifyContentChanged(this.contentUri);
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
			if(isActive() && !m_ContentThreadHandler.hasMessages(MSG_CHECK_CONTENT_CHANGES))
				m_ContentThreadHandler.sendEmptyMessageDelayed(MSG_CHECK_CONTENT_CHANGES, INTERVAL_CHECK_CONTENT_CHANGES);
		}
	}
	
	
	// Media set list implementation.
	private static final class MediaSetListImpl extends ListHandlerBaseObject<MediaSet> implements MediaSetList, Comparator<MediaSet>
	{
		// Fields.
		@SuppressWarnings({ "unchecked", "rawtypes" })
		private static final List<Class<?>> SYSTEM_MEDIA_SET_PRIORITIES = (List)Arrays.asList(
			CameraRollMediaSet.class
		);
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
	private MediaManager()
	{}
	
	
	/**
	 * Access content provider in content thread.
	 * @param contentUri Content URI to access.
	 * @param callback Call-back to access content provider.
	 * @return Handle to this operation.
	 */
	public static Handle accessContentProvider(Uri contentUri, ContentProviderAccessCallback callback)
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
	private static void accessContentProvider(ContentProviderAccessHandle handle)
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
	public static Handle activate()
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
			for(int i = m_ActiveStateCallbacks.size() - 1 ; i >= 0 ; --i)
				m_ActiveStateCallbacks.get(i).onActivated();
			startContentThread();
			m_ContentThreadHandler.sendEmptyMessage(MSG_CHECK_CONTENT_CHANGES);
		}
		
		// complete
		return handle;
	}
	
	
	/**
	 * Add {@link ActiveStateCallback}.
	 * @param callback Call-back to add.
	 */
	public static void addActiveStateCallback(ActiveStateCallback callback)
	{
		verifyAccess();
		m_ActiveStateCallbacks.add(callback);
	}
	
	
	// Cancel content provider access.
	private static void cancelContentProviderAccess(ContentProviderAccessHandle handle)
	{
		if(m_ContentThreadHandler != null)
			m_ContentThreadHandler.removeMessages(MSG_ACCESS_CONTENT_PROVIDER, handle);
	}
	
	
	// Check all content changes (in content thread).
	private static void checkContentChanges()
	{
		// check state
		if(m_ContentObservers.isEmpty() || !isActive())
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
	public static MediaSetList createMediaSetList()
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
			handles.addHandle(registerContentChangedCallback(CONTENT_URI_IMAGE, m_MediaSetListContentChangeCB, m_Handler));
			handles.addHandle(registerContentChangedCallback(CONTENT_URI_VIDEO, m_MediaSetListContentChangeCB, m_Handler));
			m_MediaSetListContentChangeCBHandle = handles;
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
	public static Handle createTemporaryMedia(final Uri contentUri, final MediaCreationCallback callback)
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
					Cursor cursor = client.query(uri, MediaStoreMedia.MEDIA_COLUMNS, selection, selectionArgs, null);
					if(cursor != null)
					{
						try
						{
							if(cursor.moveToNext())
								media = MediaStoreMedia.create(m_TempMediaSet, cursor, true, m_Handler);
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
				m_Handler.post(new Runnable()
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
	private static void deactivate(Handle handle)
	{
		// check state
		verifyAccess();
		
		// deactivate
		if(!m_ActivateHandles.remove(handle) || !m_ActivateHandles.isEmpty())
			return;
		
		Log.w(TAG, "deactivate()");
		
		// call-back
		for(int i = m_ActiveStateCallbacks.size() - 1 ; i >= 0 ; --i)
			m_ActiveStateCallbacks.get(i).onDeactivated();
		
		// stop checking content changes
		if(m_ContentThreadHandler != null)
			m_ContentThreadHandler.removeMessages(MSG_CHECK_CONTENT_CHANGES);
	}
	
	
	// Get Looper for content thread.
	private static Looper getContentThreadLooper()
	{
		startContentThread();
		return m_ContentThreadHandler.getLooper();
	}
	
	
	// Handle content thread message.
	private static void handleContentThreadMessage(Message msg)
	{
		switch(msg.what)
		{
			case MSG_ACCESS_CONTENT_PROVIDER:
				accessContentProvider((ContentProviderAccessHandle)msg.obj);
				break;
				
			case MSG_CHECK_CONTENT_CHANGES:
				checkContentChanges();
				break;
				
			case MSG_REGISTER_CONTENT_CHANGED_CB:
				registerContentChangedCallback((ContentChangeCallbackHandle)msg.obj);
				break;
				
			case MSG_UNREGISTER_CONTENT_CHANGED_CB:
				unregisterContentChangedCallback((ContentChangeCallbackHandle)msg.obj);
				break;
		}
	}
	
	
	// Handle main thread message.
	private static void handleMessage(Message msg)
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
		}
	}
	
	
	/**
	 * Initialize media manager in current thread.
	 */
	public static synchronized void initialize()
	{
		if(m_Handler != null)
			throw new IllegalStateException("Already initialized.");
		m_Handler = new Handler()
		{
			@Override
			public void handleMessage(Message msg)
			{
				MediaManager.handleMessage(msg);
			}
		};
	}
	
	
	/**
	 * Check media manager active state.
	 * @return True is media manager is active.
	 */
	public static boolean isActive()
	{
		return !m_ActivateHandles.isEmpty();
	}
	
	
	/**
	 * Check whether current thread is content thread or not.
	 * @return True if current thread is content thread.
	 */
	public static boolean isContentThread()
	{
		return (Thread.currentThread() == m_ContentThread);
	}
	
	
	// Called when media set is deleted 
	public static void notifyMediaSetDeleted(MediaSet mediaSet)
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
	
	// Called when new directory media set created. (in main thread)
	private static void onDirectoryMediaSetCreated(int id, String path)
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
	private static void onDirectoryMediaSetDeleted(int id)
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
	
	// Called when media set list released. (in main thread)
	private static void onMediaSetListReleased(MediaSetListImpl list)
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
					
					// unregister content change call-back
					m_MediaSetListContentChangeCBHandle = Handle.close(m_MediaSetListContentChangeCBHandle);
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
	public static boolean postToContentThread(Runnable r)
	{
		return postToContentThread(r, 0);
	}
	
	
	/**
	 * Post action to content thread.
	 * @param r Action to run.
	 * @param delayMillis Delay time in milliseconds.
	 * @return True if action posted to content thread successfully.
	 */
	public static boolean postToContentThread(Runnable r, long delayMillis)
	{
		startContentThread();
		if(delayMillis <= 0)
			return m_ContentThreadHandler.post(r);
		return m_ContentThreadHandler.postDelayed(r, delayMillis);
	}
	
	
	// Refresh directory media sets. (in main thread)
	private static void refreshDirectoryMediaSets()
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
							Message.obtain(m_Handler, MSG_DIR_MEDIA_SET_CREATED, id, 0, path).sendToTarget();
						}
						
						// remove media sets
						for(int id : dirIdTable)
							Message.obtain(m_Handler, MSG_DIR_MEDIA_SET_DELETED, id, 0).sendToTarget();
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
	private static void refreshMediaSetLists()
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
	public static Handle registerContentChangedCallback(Uri contentUri, ContentChangeCallback callback)
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
	public static Handle registerContentChangedCallback(Uri contentUri, ContentChangeCallback callback, Handler handler)
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
	private static void registerContentChangedCallback(ContentChangeCallbackHandle handle)
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
	 * Remove {@link ActiveStateCallback}.
	 * @param callback Call-back to remove.
	 */
	public static void removeActiveStateCallback(ActiveStateCallback callback)
	{
		verifyAccess();
		m_ActiveStateCallbacks.remove(callback);
	}
	
	
	// Start content thread.
	private static void startContentThread()
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
	
	
	// Register content change call-back (in content thread)
	private static void unregisterContentChangedCallback(ContentChangeCallbackHandle handle)
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
	
	
	// Check current thread.
	private static void verifyAccess()
	{
		if(!GalleryApplication.current().isDependencyThread())
			throw new RuntimeException("Not in main thread.");
	}
}
