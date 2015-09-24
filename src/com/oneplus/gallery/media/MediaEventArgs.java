package com.oneplus.gallery.media;

import com.oneplus.base.EventArgs;

/**
 * Data for {@link Media} related events.
 */
public class MediaEventArgs extends EventArgs
{
	// Fields.
	private final Media m_Media;
	
	
	/**
	 * Initialize new MediaEventArgs instance.
	 * @param media Related media.
	 */
	public MediaEventArgs(Media media)
	{
		m_Media = media;
	}
	
	
	/**
	 * Get related media.
	 * @return Media.
	 */
	public final Media getMedia()
	{
		return m_Media;
	}
}
