package com.oneplus.gallery.cache;

import com.oneplus.base.component.Component;
import com.oneplus.base.component.ComponentCreationPriority;
import com.oneplus.gallery.GalleryAppComponentBuilder;
import com.oneplus.gallery.GalleryApplication;

/**
 * Component builder for {@link CacheManager}.
 */
public final class CacheManagerBuilder extends GalleryAppComponentBuilder
{
	/**
	 * Initialize new CacheManagerBuilder instance.
	 */
	public CacheManagerBuilder()
	{
		super(ComponentCreationPriority.LAUNCH, CacheManagerImpl.class);
	}
	
	
	// Create component.
	@Override
	protected Component create(GalleryApplication application)
	{
		return new CacheManagerImpl(application);
	}
}
