package com.oneplus.gallery;

import com.oneplus.base.component.ComponentBuilder;
import com.oneplus.gallery.cache.CacheManager;
import com.oneplus.gallery.media.MediaManagerBuilder;
import com.oneplus.gallery.media.ThumbnailImageManager;

/**
 * OnePlus Gallery application.
 */
public final class OPGalleryApplication extends GalleryApplication
{
	// Component builders.
	private static final ComponentBuilder[] COMPONENT_BUILDERS = new ComponentBuilder[]{
		new MediaManagerBuilder(),
	};
	
	
	/**
	 * Initialize new OPGalleryApplication instance.
	 */
	public OPGalleryApplication()
	{
		this.addComponentBuilders(COMPONENT_BUILDERS);
	}
	
	
	// Create new gallery instance.
	@Override
	public Gallery createGallery()
	{
		return new OPGallery();
	}
	
	
	// Create.
	@Override
	public void onCreate()
	{
		super.onCreate();
		CacheManager.initialize();
		ThumbnailImageManager.initialize();
	}
	
	
	// Terminate.
	@Override
	public void onTerminate()
	{
		ThumbnailImageManager.deinitialize();
		CacheManager.deinitialize();
		super.onTerminate();
	}
}
