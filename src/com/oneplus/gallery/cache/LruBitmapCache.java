package com.oneplus.gallery.cache;

import android.graphics.Bitmap;
import android.util.LruCache;

/**
 * Bitmap cache based-on LRU algorithm.
 * @param <TKey> Type of key.
 */
public class LruBitmapCache<TKey> extends LruCache<TKey, Bitmap>
{
	/**
	 * Initialize new LruBitmapCache instance.
	 * @param capacity Capacity in bytes.
	 */
	public LruBitmapCache(int capacity)
	{
		super(capacity);
	}

	
	// Get bitmap size.
	@Override
	protected int sizeOf(TKey key, Bitmap bitmap)
	{
		return bitmap.getByteCount();
	}
}
