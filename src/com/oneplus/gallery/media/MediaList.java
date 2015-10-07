package com.oneplus.gallery.media;

import java.util.List;

import com.oneplus.base.EventKey;
import com.oneplus.base.HandlerObject;
import com.oneplus.base.BaseObject;
import com.oneplus.gallery.ListChangeEventArgs;

/**
 * Media list interface.
 */
public interface MediaList extends List<Media>, BaseObject, HandlerObject
{
	/**
	 * Raised when media added.
	 */
	EventKey<ListChangeEventArgs> EVENT_MEDIA_ADDED = new EventKey<>("MediaAdded", ListChangeEventArgs.class, MediaList.class);
	/**
	 * Raised when media removed.
	 */
	EventKey<ListChangeEventArgs> EVENT_MEDIA_REMOVED = new EventKey<>("MediaRemoved", ListChangeEventArgs.class, MediaList.class);
	/**
	 * Raised before removing media.
	 */
	EventKey<ListChangeEventArgs> EVENT_MEDIA_REMOVING = new EventKey<>("MediaRemoving", ListChangeEventArgs.class, MediaList.class);
}
