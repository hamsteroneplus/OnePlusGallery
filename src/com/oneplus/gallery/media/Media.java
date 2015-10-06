package com.oneplus.gallery.media;

import android.net.Uri;

import com.oneplus.base.HandlerObject;
import com.oneplus.gallery.MediaType;

/**
 * Interface to represent a media item.
 */
public interface Media extends HandlerObject
{
	/**
	 * Get content URI of this media.
	 * @return Content URI.
	 */
	Uri getContentUri();
	
	
	/**
	 * Get file path of this media.
	 * @return File path.
	 */
	String getFilePath();
	
	
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
	 * Check whether this is original file or not.
	 * @return True if this is original file.
	 */
	boolean isOriginal();
}
