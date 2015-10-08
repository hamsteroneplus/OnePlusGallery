package com.oneplus.gallery.media;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;

import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.cache.Cache;
import com.oneplus.cache.HybridBitmapLruCache;
import com.oneplus.gallery.GalleryApplication;
import com.oneplus.gallery.MediaType;
import com.oneplus.gallery.R;
import com.oneplus.gallery.cache.CacheManager;
import com.oneplus.gallery.cache.ImageCacheKey;
import com.oneplus.media.BitmapPool;

/**
 * Thumbnail image manager.
 */
public final class ThumbnailImageManager
{
	// Constants.
	private static final String TAG = "ThumbnailImageManager";
	private static final long IDLE_POOL_CAPACITY = (16 << 20);
	private static final long THUMB_POOL_CAPACITY = (64 << 20);
	private static final long MAX_CACHE_WAITING_TIME = 3000;
	private static final int MAX_FREE_DECODING_TASKS = 256;
	
	
	/**
	 * Perform operation asynchronously.
	 */
	public static final int FLAG_ASYNC = 0x1;
	/**
	 * Decoding with highest priority.
	 */
	public static final int FLAG_URGENT = 0x2;
	
	
	// Fields.
	private static final List<Handle> m_ActivationHandles = new ArrayList<>();
	private static Handle m_CacheManagerActivateHandle;
	private static final Queue<DecodingTask> m_FreeDecodingTasks = new ArrayDeque<>(MAX_FREE_DECODING_TASKS);
	private static volatile boolean m_IsInitialized;
	private static final Object m_Lock = new Object();
	private static volatile Thread m_MainThread;
	private static volatile Executor m_SmallThumbDecodeExecutor;
	private static volatile BitmapPool m_SmallThumbDecoder;
	private static volatile int m_SmallThumbSize;
	private static volatile Executor m_ThumbDecodeExecutor;
	private static volatile BitmapPool m_ThumbPool;
	
	
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
	
	
	// Handle for thumbnail image decoding.
	private static final class DecodingHandle extends Handle
	{
		// Fields.
		private final DecodingTask decodingTask;
		
		// Constructor.
		public DecodingHandle(DecodingTask task)
		{
			super("DecodeThumbnailImage");
			this.decodingTask = task;
		}

		// Close handle.
		@Override
		protected void onClose(int flags)
		{}
	}
	
	
	// Thumbnail image decoding task.
	private static final class DecodingTask implements Runnable
	{
		// Fields.
		public volatile BitmapPool bitmapDecoder;
		private final BitmapPool.Callback bitmapDecodingCallback = new BitmapPool.Callback()
		{
			public void onBitmapDecoded(Handle handle, String filePath, Bitmap bitmap)
			{
				if(cache != null && bitmap != null)
				{
					ImageCacheKey key = new ImageCacheKey(media);
					if(!(cache instanceof HybridBitmapLruCache<?>) || ((HybridBitmapLruCache<ImageCacheKey>)cache).peek(key) != bitmap)
						cache.add(key, bitmap);
				}
				callOnThumbnailImageDecoded(bitmap, true);
			}
			public void onBitmapDecoded(Handle handle, Uri contentUri, Bitmap bitmap)
			{
				if(cache != null && bitmap != null)
				{
					ImageCacheKey key = new ImageCacheKey(media);
					if(!(cache instanceof HybridBitmapLruCache<?>) || ((HybridBitmapLruCache<ImageCacheKey>)cache).peek(key) != bitmap)
						cache.add(key, bitmap);
				}
				callOnThumbnailImageDecoded(bitmap, true);
			}
		};
		public volatile Handle bitmapDecodingHandle;
		public volatile Cache<ImageCacheKey, Bitmap> cache;
		public volatile DecodingCallback callback;
		public volatile Handler callbackHandler;
		public volatile boolean centerCrop;
		public volatile DecodingHandle decodingHandle;
		public volatile int flags;
		public volatile Media media;
		public volatile int targetHeight;
		public volatile int targetWidth;
		
		// Call DecodingCallback.onThumbnailImageDecoded().
		public void callOnThumbnailImageDecoded(final Bitmap thumb, final boolean completed)
		{
			if(this.callbackHandler != null && this.callbackHandler.getLooper().getThread() != Thread.currentThread())
			{
				this.callbackHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						if(Handle.isValid(decodingHandle))
							callback.onThumbnailImageDecoded(decodingHandle, media, thumb);
						if(completed)
							onDecodingTaskCompleted(DecodingTask.this);
					}
				});
			}
			else if(Handle.isValid(this.decodingHandle))
			{
				this.callback.onThumbnailImageDecoded(this.decodingHandle, this.media, thumb);
				if(completed)
					onDecodingTaskCompleted(DecodingTask.this);
			}
		}
		
		// Run task.
		@Override
		public void run()
		{
			// check handle
			if(!Handle.isValid(this.decodingHandle))
			{
				onDecodingTaskCompleted(this);
				return;
			}
			
			// get from cache
			if(this.cache != null)
			{
				Bitmap thumb = this.cache.get(new ImageCacheKey(this.media), null, MAX_CACHE_WAITING_TIME);
				if(thumb != null)
				{
					this.callOnThumbnailImageDecoded(thumb, true);
					return;
				}
			}
			
			// check handle
			if(!Handle.isValid(this.decodingHandle))
			{
				onDecodingTaskCompleted(this);
				return;
			}
			
			// start decoding
			if(this.bitmapDecoder != null)
			{
				// calculate decoding size
				int originalWidth = this.media.getWidth();
				int originalHeight = this.media.getHeight();
				if(originalWidth <= 0 || originalHeight <= 0)
				{
					Log.e(TAG, "Unknown media size");
					this.callOnThumbnailImageDecoded(null, true);
					return;
				}
				float ratioX = ((float)this.targetWidth / originalWidth);
				float ratioY = ((float)this.targetHeight / originalHeight);
				float ratio = Math.min(1, Math.max(ratioX, ratioY));
				if(this.centerCrop)
					ratio = Math.min(1, Math.max(ratioX, ratioY));
				else
					ratio = Math.min(1, Math.min(ratioX, ratioY));
				final int targetWidth = (int)(originalWidth * ratio);
				final int targetHeight = (int)(originalHeight * ratio);
				
				// prepare decoding
				int decodingFlags = BitmapPool.FLAG_ASYNC;
				if((this.flags & FLAG_URGENT) != 0)
					decodingFlags |= BitmapPool.FLAG_URGENT;
				
				// start decoding
				String filePath = this.media.getFilePath();
				if(filePath != null)
					this.bitmapDecodingHandle = this.bitmapDecoder.decode(filePath, targetWidth, targetHeight, decodingFlags, this.bitmapDecodingCallback, this.callbackHandler);
				else
				{
					Uri contentUri = this.media.getContentUri();
					if(contentUri != null)
					{
						int mediaType = (this.media.getType() == MediaType.VIDEO ? BitmapPool.MEDIA_TYPE_VIDEO : BitmapPool.MEDIA_TYPE_PHOTO);
						this.bitmapDecodingHandle = this.bitmapDecoder.decode(GalleryApplication.current(), contentUri, mediaType, targetWidth, targetHeight, decodingFlags, this.bitmapDecodingCallback, this.callbackHandler);
					}
				}
				if(!Handle.isValid(this.bitmapDecodingHandle))
					this.callOnThumbnailImageDecoded(null, true);
			}
		}
	}
	
	
	// Constructor.
	private ThumbnailImageManager()
	{}
	
	
	/**
	 * Activate thumbnail image manager.
	 * @return Handle to activation.
	 */
	public static Handle activate()
	{
		// check state
		verifyState();
		
		// create handle
		Handle handle = new Handle("ActivateThumbImageManager")
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
		
		// activate cache manager
		if(!Handle.isValid(m_CacheManagerActivateHandle))
			m_CacheManagerActivateHandle = CacheManager.activate();
		
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
		m_CacheManagerActivateHandle = Handle.close(m_CacheManagerActivateHandle);
	}
	
	
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
	public static Handle decodeSmallThumbnailImage(Media media, DecodingCallback callback, Handler handler)
	{
		return decodeSmallThumbnailImage(media, 0, callback, handler);
	}
	
	
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
	public static Handle decodeSmallThumbnailImage(Media media, int flags, DecodingCallback callback, Handler handler)
	{
		// check parameter
		if(media == null)
		{
			Log.e(TAG, "decodeSmallThumbnailImage() - No media to decode");
			return null;
		}
		
		// create task
		HybridBitmapLruCache<ImageCacheKey> cache = CacheManager.getSmallThumbnailImageCache();
		DecodingTask task = obtainDecodingTask();
		task.bitmapDecoder = m_SmallThumbDecoder;
		task.cache = cache;
		task.callback = callback;
		task.callbackHandler = handler;
		task.centerCrop = true;
		task.flags = flags;
		task.media = media;
		task.targetHeight = m_SmallThumbSize;
		task.targetWidth = m_SmallThumbSize;
		
		// create handle
		DecodingHandle handle = new DecodingHandle(task);
		task.decodingHandle = handle;
		
		// use cached bitmap
		if((flags & FLAG_ASYNC) == 0)
		{
			Bitmap thumb = cache.peek(new ImageCacheKey(media));
			if(thumb != null)
			{
				task.callOnThumbnailImageDecoded(thumb, true);
				return handle;
			}
		}
		
		// start decode
		m_SmallThumbDecodeExecutor.execute(task);
		return handle;
	}
	
	
	/**
	 * Deinitialize thumbnail image manager.
	 */
	public static void deinitialize()
	{
		synchronized(m_Lock)
		{
			// check state
			if(!m_IsInitialized)
				return;
			
			Log.v(TAG, "deinitialize()");
			
			// deactivate
			m_ActivationHandles.clear();
			m_CacheManagerActivateHandle = Handle.close(m_CacheManagerActivateHandle);
			
			// update state
			m_IsInitialized = false;
		}
	}
	
	
	/**
	 * Initialize thumbnail image manager in current thread.
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
			
			// create bitmap pools
			m_SmallThumbDecoder = new BitmapPool("SmallThumbDecoder", (1 << 10), Bitmap.Config.ARGB_8888, 3, 0);
			m_ThumbPool = new BitmapPool("ThumbPool", THUMB_POOL_CAPACITY, IDLE_POOL_CAPACITY, Bitmap.Config.ARGB_8888, 2, 0);
			
			// get dimensions
			Resources res = GalleryApplication.current().getResources();
			m_SmallThumbSize = res.getDimensionPixelSize(R.dimen.thumbnail_image_manager_thumb_size_small);
			
			// create executor
			m_SmallThumbDecodeExecutor = Executors.newFixedThreadPool(4);
			m_ThumbDecodeExecutor = Executors.newFixedThreadPool(4);
			
			// update state
			m_IsInitialized = true;
		}
	}
	
	
	// Obtain a decoding task.
	private static DecodingTask obtainDecodingTask()
	{
		synchronized(m_Lock)
		{
			DecodingTask task = m_FreeDecodingTasks.poll();
			if(task != null)
				return task;
			return new DecodingTask();
		}
	}
	
	
	// Called when decoding task completed.
	private static void onDecodingTaskCompleted(DecodingTask task)
	{
		synchronized(m_Lock)
		{
			if(m_FreeDecodingTasks.size() >= MAX_FREE_DECODING_TASKS)
				return;
			m_FreeDecodingTasks.add(task);
			task.bitmapDecoder = null;
			task.bitmapDecodingHandle = null;
			task.cache = null;
			task.callback = null;
			task.callbackHandler = null;
			task.decodingHandle = null;
			task.media = null;
		}
	}
	
	
	// Check thread and initialization state.
	private static void verifyState()
	{
		if(!m_IsInitialized)
			throw new RuntimeException("Thumbnail image manager is not initialized.");
		verifyThread();
	}
	
	
	// Check thread.
	private static void verifyThread()
	{
		if(m_MainThread != Thread.currentThread())
			throw new RuntimeException("Access from another thread.");
	}
}
