package com.oneplus.gallery.cache;

import com.oneplus.base.Handle;
import com.oneplus.base.PropertyKey;
import com.oneplus.base.component.Component;
import com.oneplus.cache.HybridBitmapLruCache;

/**
 * Cache manager interface.
 */
public interface CacheManager extends Component
{
	/**
	 * Read-only property to check whether cache manager is active or not.
	 */
	PropertyKey<Boolean> PROP_IS_ACTIVE = new PropertyKey<>("IsActive", Boolean.class, CacheManager.class, false);
	
	
	/**
	 * Activate cache manager.
	 * @param flags Flags, reserved.
	 * @return Handle to activation.
	 */
	Handle activate(int flags);
	
	
	/**
	 * Get cache for small thumbnail image.
	 * @return Cache for small thumbnail image.
	 */
	HybridBitmapLruCache<ImageCacheKey> getSmallThumbnailImageCache();
	
	
	/**
	 * Get cache for thumbnail image.
	 * @return Cache for thumbnail image.
	 */
	HybridBitmapLruCache<ImageCacheKey> getThumbnailImageCache();
}
