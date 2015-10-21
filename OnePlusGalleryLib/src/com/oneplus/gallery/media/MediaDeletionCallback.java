package com.oneplus.gallery.media;

/**
 * Media deletion call-back interface.
 */
public interface MediaDeletionCallback
{
	/**
	 * Called after deleting media.
	 * @param media Media to be deleted.
	 * @param success True if media deleted successfully.
	 */
	void onDeletionCompleted(Media media, boolean success);
	
	/**
	 * Called after starting deletion.
	 * @param media Media to be deleted.
	 */
	void onDeletionStarted(Media media);
}
