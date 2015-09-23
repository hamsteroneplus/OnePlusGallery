package com.oneplus.gallery.media;

import java.util.List;

import com.oneplus.base.BaseObject;
import com.oneplus.base.EventKey;
import com.oneplus.base.HandlerObject;
import com.oneplus.gallery.ListChangeEventArgs;

/**
 * Media set list interface.
 */
public interface MediaSetList extends List<MediaSet>, BaseObject, HandlerObject
{
	/**
	 * Raised when media set added.
	 */
	EventKey<ListChangeEventArgs> EVENT_MEDIA_SET_ADDED = new EventKey<>("MediaSetAdded", ListChangeEventArgs.class, MediaSetList.class);
	/**
	 * Raised when media set removed.
	 */
	EventKey<ListChangeEventArgs> EVENT_MEDIA_SET_REMOVED = new EventKey<>("MediaSetRemoved", ListChangeEventArgs.class, MediaSetList.class);
}
