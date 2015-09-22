package com.oneplus.gallery.media;

import java.util.Comparator;

import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Images.ImageColumns;

/**
 * Media comparator.
 */
public abstract class MediaComparator implements Comparator<Media>
{
	/**
	 * Comparator by taken time.
	 */
	public static final MediaComparator TAKEN_TIME = new MediaComparator(ImageColumns.DATE_TAKEN + " DESC ," + MediaColumns.DATA + " ASC")
	{
		@Override
		public int compare(Media lhs, Media rhs)
		{
			// compare taken time
			long diff = (rhs.getTakenTime() - lhs.getTakenTime());
			if(diff < 0)
				return -1;
			if(diff > 1)
				return 1;
			
			// compare file path
			String pathL = lhs.getFilePath();
			String pathR = rhs.getFilePath();
			if(pathL != null)
			{
				if(pathR != null)
					return pathL.compareTo(pathR);
				return -1;
			}
			else if(pathR != null)
				return 1;
			return 0;
		}
	};
	
	
	// Fields.
	private final String m_ContentProviderSortOrder;
	
	
	// Constructor.
	private MediaComparator(String cpSortOrder)
	{
		m_ContentProviderSortOrder = cpSortOrder;
	}
	
	
	/**
	 * Get sort order for content provider access.
	 * @return Sort order.
	 */
	public final String getContentProviderSortOrder()
	{
		return m_ContentProviderSortOrder;
	}
}
