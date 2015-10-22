package com.oneplus.gallery;

import com.oneplus.base.component.Component;
import com.oneplus.base.component.ComponentBuilder;
import com.oneplus.gallery.cache.CacheManager;
import com.oneplus.gallery.media.MediaManager;
import com.oneplus.gallery.media.MediaManagerBuilder;
import com.oneplus.gallery.media.OPMediaManager;
import com.oneplus.gallery.media.ThumbnailImageManagerBuilder;

/**
 * OnePlus Gallery application.
 */
public final class OPGalleryApplication extends GalleryApplication
{
	// Component builders.
	private static final ComponentBuilder[] COMPONENT_BUILDERS = new ComponentBuilder[]{
		new MediaManagerBuilder(),
		new ThumbnailImageManagerBuilder(),
	};
	
	
	// Fields.
	private volatile OPMediaManager m_MediaManager;
	
	
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
	
	
	// Find component.
	@SuppressWarnings("unchecked")
	@Override
	public <TComponent extends Component> TComponent findComponent(Class<TComponent> componentType)
	{
		if(componentType == MediaManager.class || componentType == OPMediaManager.class)
		{
			if(m_MediaManager == null)
				m_MediaManager = super.findComponent(OPMediaManager.class);
			return (TComponent)m_MediaManager;
		}
		return super.findComponent(componentType);
	}
	
	
	// Create.
	@Override
	public void onCreate()
	{
		super.onCreate();
		CacheManager.initialize();
	}
	
	
	// Terminate.
	@Override
	public void onTerminate()
	{
		CacheManager.deinitialize();
		
		// release references
		m_MediaManager = null;
		
		// call super
		super.onTerminate();
	}
}
