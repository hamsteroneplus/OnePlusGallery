package com.oneplus.gallery.cache;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Bitmap;

import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.cache.HybridBitmapLruCache;
import com.oneplus.gallery.GalleryApplication;

/**
 * Cache manager.
 */
public final class CacheManager
{
	// Constants.
	private static final String TAG = "CacheManager";
	private static final long CAPACITY_SMALL_THUMB_MEM_CACHE = (32L << 20);
	private static final long CAPACITY_SMALL_THUMB_MEM_CACHE_IDLE = (16L << 20);
	private static final long CAPACITY_SMALL_THUMB_DISK_CACHE = (128L << 20);
	
	
	// Fields.
	private static final List<Handle> m_ActivationHandles = new ArrayList<>();
	private static final Object m_Lock = new Object();
	private static volatile boolean m_IsInitialized;
	private static volatile Thread m_MainThread;
	private static volatile HybridBitmapLruCache<ImageCacheKey> m_SmallThumbImageCache;
	
	
	// Constructor.
	private CacheManager()
	{}
	
	
	/**
	 * Activate cache manager.
	 * @return Handle to activation.
	 */
	public static Handle activate()
	{
		// check state
		verifyState();
		
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
			Log.v(TAG, "activate()");
		
		// complete
		return handle;
	}
	
	
	// Deactivate.
	private static void deactivate(Handle handle)
	{
		// check thread
		verifyThread();
		
		// remove handle
		if(!m_ActivationHandles.remove(handle) || !m_ActivationHandles.isEmpty())
			return;
		
		Log.v(TAG, "deactivate()");
		
		// deactivate
		synchronized(m_Lock)
		{
			if(m_SmallThumbImageCache != null)
				m_SmallThumbImageCache.trim(CAPACITY_SMALL_THUMB_MEM_CACHE_IDLE, null);
		}
	}
	
	
	/**
	 * Deinitialize cache image manager.
	 */
	public static void deinitialize()
	{
		synchronized(m_Lock)
		{
			// check state
			if(!m_IsInitialized)
				return;
			if(Thread.currentThread() != m_MainThread)
				throw new RuntimeException("Access from another thread.");
			
			Log.v(TAG, "deinitialize()");
			
			// clear activation handle
			m_ActivationHandles.clear();
			
			// close disk caches
			if(m_SmallThumbImageCache != null)
			{
				m_SmallThumbImageCache.close();
				m_SmallThumbImageCache = null;
			}
			
			// update state
			m_IsInitialized = false;
		}
	}
	
	
	/**
	 * Get disk cache for small thumbnail image.
	 * @return Cache for small thumbnail image.
	 */
	public static HybridBitmapLruCache<ImageCacheKey> getSmallThumbnailImageCache()
	{
		if(m_SmallThumbImageCache != null)
			return m_SmallThumbImageCache;
		synchronized(m_Lock)
		{
			if(m_SmallThumbImageCache == null)
			{
				if(!m_IsInitialized)
				{
					Log.e(TAG, "getSmallThumbnailImageCache() - Cache manager is not initialized");
					return null;
				}
				m_SmallThumbImageCache = new HybridBitmapLruCache<>(GalleryApplication.current(), "SmallThumbnail", Bitmap.Config.RGB_565, Bitmap.CompressFormat.JPEG, CAPACITY_SMALL_THUMB_MEM_CACHE, CAPACITY_SMALL_THUMB_DISK_CACHE);
			}
		}
		return m_SmallThumbImageCache;
	}
	
	
	/**
	 * Initialize cache manager.
	 */
	public static void initialize()
	{
		synchronized(m_Lock)
		{
			// check state
			if(m_IsInitialized)
				return;
			
			Log.v(TAG, "initialize()");
			
			// check thread
			m_MainThread = Thread.currentThread();
			
			// create disk caches
			getSmallThumbnailImageCache();
			
			// update state
			m_IsInitialized = true;
		}
	}
	
	
	// Check thread and initialization state.
	private static void verifyState()
	{
		if(!m_IsInitialized)
			throw new RuntimeException("Cache manager is not initialized.");
		verifyThread();
	}
	
	
	// Check thread.
	private static void verifyThread()
	{
		if(m_MainThread != Thread.currentThread())
			throw new RuntimeException("Access from another thread.");
	}
}
