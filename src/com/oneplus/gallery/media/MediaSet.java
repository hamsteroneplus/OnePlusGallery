package com.oneplus.gallery.media;

import java.util.Comparator;

import com.oneplus.base.BaseObject;
import com.oneplus.base.HandlerObject;
import com.oneplus.base.PropertyKey;

/**
 * Media set interface.
 */
public interface MediaSet extends BaseObject, HandlerObject
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
	
	
	/**
	 * Open media list.
	 * @param comparator {@link Comparator} to sort media in list.
	 * @param maxMediaCount Maximum number of media allowed in list, negative value means unlimited.
	 * @param flags Flags, reserved.
	 * @return Media list.
	 */
	MediaList openMediaList(MediaComparator comparator, int maxMediaCount, int flags);
}
