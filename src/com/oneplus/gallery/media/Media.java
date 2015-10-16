package com.oneplus.gallery.media;

import android.location.Location;
import android.net.Uri;
import android.os.Handler;

import com.oneplus.base.Handle;
import com.oneplus.base.HandlerObject;
import com.oneplus.gallery.MediaType;

/**
 * Interface to represent a media item.
 */
public interface Media extends HandlerObject
{
	/**
	 * Call-back interface for retrieving media details.
	 */
	public interface MediaDetailsCallback
	{
		/**
		 * Called when media details retrieved.
		 * @param media Media.
		 * @param handle Handle returned from {@link Media#getDetails(MediaDetailsCallback, Handler) getDetails(MediaDetailsCallback, Handler)}.
		 * @param details Media details.
		 */
		void onMediaDetailsRetrieved(Media media, Handle handle, MediaDetails details);
	}
	
	
	/**
	 * Get content URI of this media.
	 * @return Content URI.
	 */
	Uri getContentUri();
	
	
	/**
	 * Start getting media details.
	 * @param callback Call-back to receive media details.
	 * @param handler Handler to perform call-back.
	 * @return Handle to this operation.
	 */
	Handle getDetails(MediaDetailsCallback callback, Handler handler);
	
	
	/**
	 * Get file path of this media.
	 * @return File path.
	 */
	String getFilePath();
	
	
	/**
	 * Get file size.
	 * @return File size in bytes.
	 */
	long getFileSize();
	
	
	/**
	 * Get last modified time.
	 * @return Last modified time.
	 */
	long getLastModifiedTime();
	
	
	/**
	 * Get geographic location of media.
	 * @return Location.
	 */
	Location getLocation();
	
	
	/**
	 * Get MIME type of this media.
	 * @return MIME type.
	 */
	String getMimeType();
	
	
	/**
	 * Get media height.
	 * @return Media height.
	 */
	int getHeight();
	
	
	/**
	 * Get taken time.
	 * @return Taken time in milliseconds.
	 */
	long getTakenTime();
	
	
	/**
	 * Get media type.
	 * @return Media type.
	 */
	MediaType getType();
	
	
	/**
	 * Get media width.
	 * @return Media width.
	 */
	int getWidth();
	
	
	/**
	 * Check whether this is a favorite media or not.
	 * @return True if this is a favorite media.
	 */
	boolean isFavorite();
}
