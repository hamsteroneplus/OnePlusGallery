package com.oneplus.gallery.media;

import java.util.List;

import com.oneplus.base.BaseObject;
import com.oneplus.base.EventKey;
import com.oneplus.base.HandlerObject;
import com.oneplus.base.PropertyKey;

/**
 * Media set interface.
 */
public interface MediaSet extends BaseObject, HandlerObject, List<Media>
{
	/**
	 * Read-only property to get number of media in this set.
	 */
	PropertyKey<Integer> PROP_MEDIA_COUNT = new PropertyKey<>("MediaCount", Integer.class, MediaSet.class, 0);
	/**
	 * Property for name of this set.
	 */
	PropertyKey<CharSequence> PROP_NAME = new PropertyKey<>("Name", CharSequence.class, MediaSet.class, 0, null);
	
	
	/**
	 * Raised when media added.
	 */
	EventKey<MediaSetChangeEventArgs> EVENT_MEDIA_ADDED = new EventKey<>("MediaAdded", MediaSetChangeEventArgs.class, MediaSet.class);
	/**
	 * Raised when media removed.
	 */
	EventKey<MediaSetChangeEventArgs> EVENT_MEDIA_REMOVED = new EventKey<>("MediaRemoved", MediaSetChangeEventArgs.class, MediaSet.class);
	
	
	/**
	 * Media set type.
	 */
	enum Type
	{
		/**
		 * System defined set.
		 */
		SYSTEM,
		/**
		 * User created set.
		 */
		USER,
		/**
		 * Application created set.
		 */
		APPLICATION,
		/**
		 * Other.
		 */
		OTHER,
	}
	
	
	/**
	 * Get media set type.
	 * @return Media set type.
	 */
	Type getType();
}
