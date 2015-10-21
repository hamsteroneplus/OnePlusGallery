package com.oneplus.gallery;

import com.oneplus.base.BaseApplication;

/**
 * Gallery application.
 */
public abstract class GalleryApplication extends BaseApplication
{
	/**
	 * Get current instance.
	 * @return Gallery application instance.
	 */
	public static GalleryApplication current()
	{
		return (GalleryApplication)BaseApplication.current();
	}
	
	
	/**
	 * Create new {@link Gallery} instance.
	 * @return {@link Gallery} instance.
	 */
	public abstract Gallery createGallery();
}
