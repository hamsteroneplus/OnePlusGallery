package com.oneplus.gallery.media;

import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.gallery.GalleryApplication;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.RemoteException;

/**
 * Media manager.
 */
public class MediaManager
{
	// Constants.
	private static final String TAG = "MediaManager";
	private static final int MSG_ACCESS_CONTENT_PROVIDER = 10000;
	
	
	// Fields.
	private static ContentResolver m_ContentResolver;
	private static volatile HandlerThread m_ContentThread;
	private static volatile Handler m_ContentThreadHandler;
	private static volatile Handler m_Handler;
	private static final Object m_Lock = new Object();
	
	
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
	
	
	// Access content provider (in content thread)
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
	
	
	// Cancel content provider access.
	private static void cancelContentProviderAccess(ContentProviderAccessHandle handle)
	{
		if(m_ContentThreadHandler != null)
			m_ContentThreadHandler.removeMessages(MSG_ACCESS_CONTENT_PROVIDER, handle);
	}
	
	
	/**
	 * Initialize media manager in current thread.
	 */
	public static synchronized void initialize()
	{
		if(m_Handler != null)
			throw new IllegalStateException("Already initialized.");
		m_Handler = new Handler();
	}
	
	
	// Handle content thread message.
	private static void handleContentThreadMessage(Message msg)
	{
		switch(msg.what)
		{
			case MSG_ACCESS_CONTENT_PROVIDER:
				accessContentProvider((ContentProviderAccessHandle)msg.obj);
				break;
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
}
