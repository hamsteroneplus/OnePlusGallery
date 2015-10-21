package com.oneplus.gallery.media;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;

import com.oneplus.base.Handle;
import com.oneplus.base.PropertyKey;
import com.oneplus.base.component.Component;

/**
 * Media manager interface.
 */
public interface MediaManager extends Component
{
	/**
	 * Read-only property to check whether media manager is active or not.
	 */
	PropertyKey<Boolean> PROP_IS_ACTIVE = new PropertyKey<>("IsActive", Boolean.class, MediaManager.class, false);
	
	
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
	 * Call-back interface for media change.
	 */
	public interface MediaChangeCallback
	{
		/**
		 * Called when media created.
		 * @param id Media ID.
		 * @param media Instance of this media, may be Null.
		 */
		void onMediaCreated(long id, Media media);
		/**
		 * Called when media deleted.
		 * @param id Media ID.
		 * @param media Instance of this media, may be Null.
		 */
		void onMediaDeleted(long id, Media media);
		/**
		 * Called when media updated.
		 * @param id Media ID.
		 * @param media Instance of this media, may be Null.
		 */
		void onMediaUpdated(long id, Media media);
	}
	
	
	/**
	 * Access content provider in content thread.
	 * @param contentUri Content URI to access.
	 * @param callback Call-back to access content provider.
	 * @return Handle to this operation.
	 */
	Handle accessContentProvider(Uri contentUri, ContentProviderAccessCallback callback);
	
	
	/**
	 * Activate media manager.
	 * @return Handle to activation.
	 */
	Handle activate();
	
	
	/**
	 * Create new media set list.
	 * @return Media set list.
	 */
	MediaSetList createMediaSetList();
	
	
	/**
	 * Delete media.
	 * @param media Media to delete.
	 * @param callback Call-back to receive deletion state.
	 * @param handler Handler to perform call-back.
	 * @return Handle to media deletion.
	 */
	Handle deleteMedia(Media media, MediaDeletionCallback callback, Handler handler);
	
	
	/**
	 * Check whether current thread is content thread or not.
	 * @return True if current thread is content thread.
	 */
	boolean isContentThread();
	
	
	/**
	 * Notify that given media set has been deleted.
	 * @param mediaSet Deleted media set.
	 */
	void notifyMediaSetDeleted(MediaSet mediaSet);
	
	
	/**
	 * Post action to content thread.
	 * @param r Action to run.
	 * @param delayMillis Delay time in milliseconds.
	 * @return True if action posted to content thread successfully.
	 */
	boolean postToContentThread(Runnable r, long delayMillis);
	
	
	/**
	 * Register call-back to monitor media change.
	 * @param callback Call-back to add.
	 * @param handler Handler to perform call-back.
	 * @return Handle to call-back.
	 */
	Handle registerMediaChangeCallback(MediaChangeCallback callback, Handler handler);
}
