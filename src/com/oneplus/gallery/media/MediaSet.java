package com.oneplus.gallery.media;

import java.util.Comparator;

import android.os.Handler;

import com.oneplus.base.BaseObject;
import com.oneplus.base.Handle;
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
	PropertyKey<Integer> PROP_MEDIA_COUNT = new PropertyKey<>("MediaCount", Integer.class, MediaSet.class, PropertyKey.FLAG_READONLY, null);
	/**
	 * Property for name of this set.
	 */
	PropertyKey<String> PROP_NAME = new PropertyKey<>("Name", String.class, MediaSet.class, 0, null);
	
	
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
	 * Media deletion call-back interface.
	 */
	interface MediaDeletionCallback
	{
		/**
		 * Called after deleting media.
		 * @param mediaSet {@link MediaSet}.
		 * @param media Media which is deleted.
		 * @param success True if media deleted successfully.
		 */
		void onDeletionCompleted(MediaSet mediaSet, Media media, boolean success);
		
		/**
		 * Called after starting deletion.
		 * @param mediaSet {@link MediaSet}.
		 * @param media Media to be deleted.
		 */
		void onDeletionStarted(MediaSet mediaSet, Media media);
	}
	
	
	/**
	 * Delete media.
	 * @param media Media to delete.
	 * @param callback Call-back to receive deletion state.
	 * @param handler Handler to perform call-back.
	 * @return Handle to this operation.
	 */
	Handle deleteMedia(Media media, MediaDeletionCallback callback, Handler handler);
	
	
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
