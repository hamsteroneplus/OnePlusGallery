package com.oneplus.gallery.cache;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Bitmap;

import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.base.component.BasicComponent;
import com.oneplus.cache.HybridBitmapLruCache;
import com.oneplus.gallery.GalleryApplication;

/**
 * Cache manager.
 */
final class CacheManagerImpl extends BasicComponent implements CacheManager
{
	// Constants.
	private static final String TAG = "CacheManager";
	private static final long CAPACITY_SMALL_THUMB_MEM_CACHE = (32L << 20);
	private static final long CAPACITY_SMALL_THUMB_MEM_CACHE_IDLE = (16L << 20);
	private static final long CAPACITY_SMALL_THUMB_DISK_CACHE = (128L << 20);
	private static final long CAPACITY_THUMB_MEM_CACHE = (1L << 20);
	private static final long CAPACITY_THUMB_MEM_CACHE_IDLE = (1L << 20);
	private static final long CAPACITY_THUMB_DISK_CACHE = (0L << 20);
	
	
	// Fields.
	private final List<Handle> m_ActivationHandles = new ArrayList<>();
	private final Object m_Lock = new Object();
	private volatile HybridBitmapLruCache<ImageCacheKey> m_SmallThumbImageCache;
	private volatile HybridBitmapLruCache<ImageCacheKey> m_ThumbImageCache;
	
	
	// Constructor.
	CacheManagerImpl(GalleryApplication application)
	{
		super("Cache manager", application, true);
	}
	
	
	/**
	 * Activate cache manager.
	 * @return Handle to activation.
	 */
	@Override
	public Handle activate(int flags)
	{
		// check state
		this.verifyAccess();
		if(!this.isRunningOrInitializing(true))
			return null;
		
		// create handle
		Handle handle = new Handle("ActivateCacheManager")
		{
			@Override
			protected void onClose(int flags)
			{
				deactivate(this);
			}
		};
		m_ActivationHandles.add(handle);
		if(m_ActivationHandles.size() == 1)
		{
			Log.v(TAG, "activate()");
			this.setReadOnly(PROP_IS_ACTIVE, true);
		}
		
		// complete
		return handle;
	}
	
	
	// Deactivate.
	private void deactivate(Handle handle)
	{
		// check thread
		this.verifyAccess();
		
		// remove handle
		if(!m_ActivationHandles.remove(handle) || !m_ActivationHandles.isEmpty())
			return;
		
		Log.v(TAG, "deactivate()");
		
		// deactivate
		synchronized(m_Lock)
		{
			if(m_SmallThumbImageCache != null)
				m_SmallThumbImageCache.trim(CAPACITY_SMALL_THUMB_MEM_CACHE_IDLE, null);
			if(m_ThumbImageCache != null)
				m_ThumbImageCache.trim(CAPACITY_THUMB_MEM_CACHE_IDLE, null);
		}
		
		// update property
		this.setReadOnly(PROP_IS_ACTIVE, false);
	}
	
	
	/**
	 * Get cache for small thumbnail image.
	 * @return Cache for small thumbnail image.
	 */
	@Override
	public HybridBitmapLruCache<ImageCacheKey> getSmallThumbnailImageCache()
	{
		return m_SmallThumbImageCache;
	}
	
	
	// Get cache for thumbnail image.
	@Override
	public HybridBitmapLruCache<ImageCacheKey> getThumbnailImageCache()
	{
		return m_ThumbImageCache;
	}
	
	
	// Deinitialize.
	@Override
	protected void onDeinitialize()
	{
		// clear activation handle
		m_ActivationHandles.clear();
		this.setReadOnly(PROP_IS_ACTIVE, false);
		
		// close caches
		if(m_SmallThumbImageCache != null)
		{
			m_SmallThumbImageCache.close();
			m_SmallThumbImageCache = null;
		}
		if(m_ThumbImageCache != null)
		{
			m_ThumbImageCache.close();
			m_ThumbImageCache = null;
		}
		
		// call super
		super.onDeinitialize();
	}
	
	
	// Initialize.
	@Override
	protected void onInitialize()
	{
		// call super
		super.onInitialize();
		
		// create caches
		m_SmallThumbImageCache = new HybridBitmapLruCache<>(GalleryApplication.current(), "SmallThumbnail", Bitmap.Config.RGB_565, Bitmap.CompressFormat.JPEG, CAPACITY_SMALL_THUMB_MEM_CACHE, CAPACITY_SMALL_THUMB_DISK_CACHE);
		m_ThumbImageCache = new HybridBitmapLruCache<>(GalleryApplication.current(), "Thumbnail", Bitmap.Config.RGB_565, Bitmap.CompressFormat.JPEG, CAPACITY_THUMB_MEM_CACHE, CAPACITY_THUMB_DISK_CACHE);
	}
}
