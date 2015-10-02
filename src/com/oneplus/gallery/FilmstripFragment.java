package com.oneplus.gallery;

import com.oneplus.base.PropertyKey;
import com.oneplus.gallery.media.MediaList;

/**
 * Filmstrip fragment.
 */
public class FilmstripFragment extends GalleryFragment
{
	/**
	 * Property to get or set index of current media.
	 */
	public final static PropertyKey<Integer> PROP_CURRENT_MEDIA_INDEX = new PropertyKey<>("CurrentMediaIndex", Integer.class, FilmstripFragment.class, 0, -1);
	/**
	 * Property to get or set media list to display.
	 */
	public final static PropertyKey<MediaList> PROP_MEDIA_LIST = new PropertyKey<>("MediaList", MediaList.class, FilmstripFragment.class, 0, null);
}
