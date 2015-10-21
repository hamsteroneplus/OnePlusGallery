package com.oneplus.gallery.media;

import com.oneplus.base.component.Component;
import com.oneplus.base.component.ComponentCreationPriority;
import com.oneplus.gallery.GalleryAppComponentBuilder;
import com.oneplus.gallery.GalleryApplication;

/**
 * Component builder for {@link OPMediaManager}.
 */
public final class MediaManagerBuilder extends GalleryAppComponentBuilder
{
	/**
	 * Initialize new MediaManagerBuilder instance.
	 */
	public MediaManagerBuilder()
	{
		super(ComponentCreationPriority.LAUNCH, MediaManagerImpl.class);
	}

	
	// Create component.
	@Override
	protected Component create(GalleryApplication application)
	{
		return new MediaManagerImpl(application);
	}
}
