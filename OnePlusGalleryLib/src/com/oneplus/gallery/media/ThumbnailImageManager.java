package com.oneplus.gallery.media;

import android.graphics.Bitmap;
import android.os.Handler;

import com.oneplus.base.Handle;
import com.oneplus.base.PropertyKey;
import com.oneplus.base.component.Component;

/**
 * Thumbnail image manager interface.
 */
public interface ThumbnailImageManager extends Component
{
	/**
	 * Perform operation asynchronously.
	 */
	int FLAG_ASYNC = 0x1;
	/**
	 * Decoding with highest priority.
	 */
	int FLAG_URGENT = 0x2;
	
	
	/**
	 * Read-only property to check whether thumbnail image manager is active or not.
	 */
	PropertyKey<Boolean> PROP_IS_ACTIVE = new PropertyKey<>("IsActive", Boolean.class, ThumbnailImageManager.class, false);
	
	
	/**
	 * Thumbnail image decode call-back interface.
	 */
	public interface DecodingCallback
	{
		/**
		 * Called when thumbnail image decoded.
		 * @param handle Handle returned from decode*ThumbnailImage methods.
		 * @param media Media.
		 * @param thumb Decoded thumbnail image, or Null if fail to decode.
		 */
		void onThumbnailImageDecoded(Handle handle, Media media, Bitmap thumb);
	}
	
	
	/**
	 * Activate thumbnail image manager.
	 * @param flags Flags, reserved.
	 * @return Handle to activation.
	 */
	Handle activate(int flags);
	
	
	/**
	 * Start decoding small thumbnail image.
	 * @param media Media to decode.
	 * @param flags Flags:
	 * <ul>
	 *   <li>{@link #FLAG_ASYNC}</li>
	 *   <li>{@link #FLAG_URGENT}</li>
	 * </ul>
	 * @param callback Decoding call-back.
	 * @param handler {@link Handler} to perform call-back.
	 * @return Handle to thumbnail image decoding.
	 */
	Handle decodeSmallThumbnailImage(Media media, int flags, DecodingCallback callback, Handler handler);
	
	
	/**
	 * Start decoding thumbnail image.
	 * @param media Media to decode.
	 * @param flags Flags:
	 * <ul>
	 *   <li>{@link #FLAG_ASYNC}</li>
	 *   <li>{@link #FLAG_URGENT}</li>
	 * </ul>
	 * @param callback Decoding call-back.
	 * @param handler {@link Handler} to perform call-back.
	 * @return Handle to thumbnail image decoding.
	 */
	Handle decodeThumbnailImage(Media media, int flags, DecodingCallback callback, Handler handler);
	
	
	/**
	 * Get cached small thumbnail image directly.
	 * @param media Media.
	 * @return Small thumbnail image, or Null if there is no cached image in memory.
	 */
	Bitmap getCachedSmallThumbnailImage(Media media);
	
	
	/**
	 * Get cached thumbnail image directly.
	 * @param media Media.
	 * @return Small thumbnail image, or Null if there is no cached image in memory.
	 */
	Bitmap getCachedThumbnailImage(Media media);
}
