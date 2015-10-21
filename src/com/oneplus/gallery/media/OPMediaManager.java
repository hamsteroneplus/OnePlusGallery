package com.oneplus.gallery.media;

import com.oneplus.base.Handle;

import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore.MediaColumns;

/**
 * OnePlus Gallery media manager.
 */
public interface OPMediaManager extends MediaManager
{
	/**
	 * Flag for {@link #COLUMN_ONEPLUS_FLAGS} : Selfie media.
	 */
	int ONEPLUS_FLAG_SELFIE = 0x1;
	/**
	 * Flag for {@link #COLUMN_ONEPLUS_FLAGS} : Panorama photo.
	 */
	int ONEPLUS_FLAG_PANORAMA = 0x2;
	/**
	 * Flag for {@link #COLUMN_ONEPLUS_FLAGS} : Slow-motion video.
	 */
	int ONEPLUS_FLAG_SLOW_MOTION = 0x4;
	/**
	 * Flag for {@link #COLUMN_ONEPLUS_FLAGS} : Time-lapse video.
	 */
	int ONEPLUS_FLAG_TIME_LAPSE = 0x8;
	/**
	 * Flag for {@link #COLUMN_ONEPLUS_FLAGS} : Favorite.
	 */
	int ONEPLUS_FLAG_FAVORITE = 0x10;
	/**
	 * Flag for {@link #COLUMN_ONEPLUS_FLAGS} : Cover photo.
	 */
	int ONEPLUSP_FLAG_COVER = 0x10000;
	/**
	 * Flag for {@link #COLUMN_ONEPLUS_FLAGS} : Burst photo set.
	 */
	int ONEPLUS_FLAG_BURST = 0x20000;
	
	
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
	 * Add OnePlus flags to media extra info. 
	 * @param contentUri Content URI to update.
	 * @param flags New flags to add.
	 * @return True if update successfully.
	 */
	boolean addOnePlusFlags(Uri contentUri, final int flags);
	
	
	/**
	 * Create temporary {@link Media} instance for specific content URI.
	 * @param contentUri Content URI of media.
	 * @param callback Call-back to received created media.
	 * @return Handle to media creation.
	 */
	Handle createTemporaryMedia(final Uri contentUri, final MediaCreationCallback callback);
	
	
	/**
	 * Obtain media instance.
	 * @param cursor Media ID.
	 * @return Media instance, or Null if instance is not created yet.
	 */
	Media obtainMedia(long id);
	
	
	/**
	 * Obtain media instance.
	 * @param cursor Cursor to read media information.
	 * @param idColumnIndex Column index of {@link MediaColumns#_ID}, or negative number to find index automatically.
	 * @return Media instance, or Null if fail to create instance.
	 */
	Media obtainMedia(Cursor cursor, int idColumnIndex);
	
	
	/**
	 * Change favorite state for specific media.
	 * @param contentUri Content URI of media.
	 * @param isFavorite True to set as favorite media, False otherwise.
	 * @return True if update successfully.
	 */
	boolean setFavorite(Uri contentUri, boolean isFavorite);
}
