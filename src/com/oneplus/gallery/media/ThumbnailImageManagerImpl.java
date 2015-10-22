package com.oneplus.gallery.media;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;

import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.base.Ref;
import com.oneplus.base.component.BasicComponent;
import com.oneplus.cache.Cache;
import com.oneplus.cache.HybridBitmapLruCache;
import com.oneplus.gallery.GalleryApplication;
import com.oneplus.gallery.R;
import com.oneplus.gallery.cache.CacheManager;
import com.oneplus.gallery.cache.ImageCacheKey;
import com.oneplus.media.BitmapPool;

/**
 * Thumbnail image manager.
 */
final class ThumbnailImageManagerImpl extends BasicComponent implements ThumbnailImageManager
{
	// Constants.
	private static final String TAG = "ThumbnailImageManager";
	private static final long IDLE_POOL_CAPACITY = (16 << 20);
	private static final long THUMB_POOL_CAPACITY = (64 << 20);
	private static final long MAX_CACHE_WAITING_TIME = 1000;
	private static final int MAX_FREE_DECODING_TASKS = 256;
	private static final long DURATION_CLEAR_INVALID_THUMBS_DELAY = 1500;
	private static final long DURATION_MAX_CLEAR_INVALID_THUMBS = 1000;
	
	
	// Fields.
	private final List<Handle> m_ActivationHandles = new ArrayList<>();
	private CacheManager m_CacheManager;
	private Handle m_CacheManagerActivateHandle;
	private final Queue<DecodingTask> m_FreeDecodingTasks = new ArrayDeque<>(MAX_FREE_DECODING_TASKS);
	private final Object m_Lock = new Object();
	private volatile Executor m_SmallThumbDecodeExecutor;
	private volatile BitmapPool m_SmallThumbDecoder;
	private volatile LinkedList<DecodingTask> m_SmallThumbDecodeQueue;
	private volatile int m_SmallThumbSize;
	private volatile Executor m_ThumbDecodeExecutor;
	private volatile BitmapPool m_ThumbPool;
	
	
	// Runnables.
	private final Runnable m_ClearInvalidThumbsRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			clearInvalidThumbnailImages();
		}
	};
	private final Runnable m_ClearInvalidThumbsDelayedRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			if(m_SmallThumbDecodeExecutor != null)
				m_SmallThumbDecodeExecutor.execute(m_ClearInvalidThumbsRunnable);
		}
	};
	private final Runnable m_DecodeSmallThumbRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			DecodingTask task;
			synchronized(m_Lock)
			{
				task = m_SmallThumbDecodeQueue.pollFirst();
			}
			if(task != null)
				task.run();
		}
	};
	
	
	// Handle for thumbnail image decoding.
	private final class DecodingHandle extends Handle
	{
		// Fields.
		private final DecodingTask decodingTask;
		private final Collection<DecodingTask> decodingTaskQueue;
		
		// Constructor.
		public DecodingHandle(Collection<DecodingTask> decodingTaskQueue, DecodingTask decodingTask)
		{
			super("DecodeThumbnailImage");
			this.decodingTaskQueue = decodingTaskQueue;
			this.decodingTask = decodingTask;
		}

		// Close handle.
		@Override
		protected void onClose(int flags)
		{
			synchronized(m_Lock)
			{
				this.decodingTaskQueue.remove(this.decodingTask);
			}
		}
	}
	
	
	// Thumbnail image decoding task.
	private final class DecodingTask implements Runnable
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
	ThumbnailImageManagerImpl(GalleryApplication application)
	{
		super("Thumbnail image manager", application, true);
	}
	
	
	/**
	 * Activate thumbnail image manager.
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
		{
			Log.v(TAG, "activate()");
			this.setReadOnly(PROP_IS_ACTIVE, true);
		}
		
		// activate cache manager
		if(!Handle.isValid(m_CacheManagerActivateHandle) && m_CacheManager != null)
			m_CacheManagerActivateHandle = m_CacheManager.activate(0);
		
		// cancel clearing invalid thumbnail images
		GalleryApplication.current().getHandler().removeCallbacks(m_ClearInvalidThumbsDelayedRunnable);
		
		// complete
		return handle;
	}
	
	
	// Clear invalid thumbnail images in cache.
	private void clearInvalidThumbnailImages()
	{
		// get cache
		if(m_CacheManager == null)
			return;
		Cache<ImageCacheKey, Bitmap> cache = m_CacheManager.getSmallThumbnailImageCache();
		if(cache == null)
			return;
		
		// clear
		Log.v(TAG, "clearInvalidThumbnailImages() - Start");
		final long startTime = SystemClock.elapsedRealtime();
		final int[] count = new int[1];
		cache.remove(new Cache.RemovingPredication<ImageCacheKey>()
		{
			@Override
			public boolean canRemove(ImageCacheKey key, Ref<Boolean> isCancelled)
			{
				// check time
				if((SystemClock.elapsedRealtime() - startTime) >= DURATION_MAX_CLEAR_INVALID_THUMBS)
				{
					Log.w(TAG, "clearInvalidThumbnailImages() - Take long time to clear, interrupt");
					isCancelled.set(true);
					return false;
				}
				
				// check file
				if(key.filePath != null)
				{
					try
					{
						File file = new File(key.filePath);
						if(!file.exists() || file.lastModified() != key.lastModifiedTime || file.length() != key.fileSize)
						{
							++count[0];
							return true;
						}
					}
					catch(Throwable ex)
					{
						++count[0];
						return true;
					}
				}
				return false;
			}
		});
		long time = (SystemClock.elapsedRealtime() - startTime);
		Log.v(TAG, "clearInvalidThumbnailImages() - Take ", time , " ms to removed ", count[0], " invalid images");
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
		m_CacheManagerActivateHandle = Handle.close(m_CacheManagerActivateHandle);
		
		// clear invalid thumbnail images
		GalleryApplication.current().getHandler().postDelayed(m_ClearInvalidThumbsDelayedRunnable, DURATION_CLEAR_INVALID_THUMBS_DELAY);
		
		// update property
		this.setReadOnly(PROP_IS_ACTIVE, false);
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
	public Handle decodeSmallThumbnailImage(Media media, DecodingCallback callback, Handler handler)
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
	@Override
	public Handle decodeSmallThumbnailImage(Media media, int flags, DecodingCallback callback, Handler handler)
	{
		// check parameter
		if(media == null)
		{
			Log.e(TAG, "decodeSmallThumbnailImage() - No media to decode");
			return null;
		}
		
		// create task
		HybridBitmapLruCache<ImageCacheKey> cache = (m_CacheManager != null ? m_CacheManager.getSmallThumbnailImageCache() : null);
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
		
		// create decoding queue
		synchronized(m_Lock)
		{
			if(m_SmallThumbDecodeQueue == null)
				m_SmallThumbDecodeQueue = new LinkedList<>();
		}
		
		// create handle
		DecodingHandle handle = new DecodingHandle(m_SmallThumbDecodeQueue, task);
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
		synchronized(m_Lock)
		{
			if((flags & FLAG_URGENT) == 0)
				m_SmallThumbDecodeQueue.addLast(task);
			else
				m_SmallThumbDecodeQueue.addFirst(task);
		}
		m_SmallThumbDecodeExecutor.execute(m_DecodeSmallThumbRunnable);
		return handle;
	}
	
	
	// Decode thumbnail image.
	@Override
	public Handle decodeThumbnailImage(Media media, int flags, DecodingCallback callback, Handler handler)
	{
		Log.e(TAG, "decodeThumbnailImage() - Not implemented");
		return null;
	}
	
	
	/**
	 * Get cached small thumbnail image directly.
	 * @param media Media.
	 * @return Small thumbnail image, or Null if there is no cached image in memory.
	 */
	public Bitmap getCachedSmallThumbnailImage(Media media)
	{
		if(media == null)
			return null;
		if(m_CacheManager == null)
			return null;
		Cache<ImageCacheKey, Bitmap> cache = m_CacheManager.getSmallThumbnailImageCache();
		if(cache == null)
			return null;
		return cache.get(new ImageCacheKey(media), null, 0);
	}
	
	
	// Get cached thumbnail image.
	@Override
	public Bitmap getCachedThumbnailImage(Media media)
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	
	// Obtain a decoding task.
	private DecodingTask obtainDecodingTask()
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
	private void onDecodingTaskCompleted(DecodingTask task)
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
	
	
	// Deinitialize.
	@Override
	protected void onDeinitialize()
	{
		// deactivate
		m_ActivationHandles.clear();
		m_CacheManagerActivateHandle = Handle.close(m_CacheManagerActivateHandle);
		this.setReadOnly(PROP_IS_ACTIVE, false);
		
		// call super
		super.onDeinitialize();
	}
	
	
	// Initialize.
	@Override
	protected void onInitialize()
	{
		// call super
		super.onInitialize();
		
		// find components
		m_CacheManager = GalleryApplication.current().findComponent(CacheManager.class);
		
		// create bitmap pools
		m_SmallThumbDecoder = new BitmapPool("SmallThumbDecoder", (1 << 10), Bitmap.Config.ARGB_8888, 3, 0);
		m_ThumbPool = new BitmapPool("ThumbPool", THUMB_POOL_CAPACITY, IDLE_POOL_CAPACITY, Bitmap.Config.ARGB_8888, 2, 0);
		
		// get dimensions
		Resources res = GalleryApplication.current().getResources();
		m_SmallThumbSize = res.getDimensionPixelSize(R.dimen.thumbnail_image_manager_thumb_size_small);
		
		// create executor
		m_SmallThumbDecodeExecutor = Executors.newFixedThreadPool(4);
		m_ThumbDecodeExecutor = Executors.newFixedThreadPool(4);
	}
}
