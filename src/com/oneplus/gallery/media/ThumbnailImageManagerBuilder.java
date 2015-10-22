package com.oneplus.gallery.media;

import com.oneplus.base.component.Component;
import com.oneplus.base.component.ComponentCreationPriority;
import com.oneplus.gallery.GalleryAppComponentBuilder;
import com.oneplus.gallery.GalleryApplication;

/**
 * Component builder for {@link ThumbnailImageManager}.
 */
public final class ThumbnailImageManagerBuilder extends GalleryAppComponentBuilder
{
	/**
	 * Initialize new ThumbnailImageManagerBuilder instance.
	 */
	public ThumbnailImageManagerBuilder()
	{
		super(ComponentCreationPriority.ON_DEMAND, ThumbnailImageManagerImpl.class);
	}
		
	
	// Create component
	@Override
	protected Component create(GalleryApplication application)
	{
		return new ThumbnailImageManagerImpl(application);
	}
}
