package com.oneplus.gallery.media;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oneplus.base.ListHandlerBaseObject;
import com.oneplus.gallery.ListChangeEventArgs;

/**
 * Basic implementation of {@link MediaList}.
 */
public abstract class BasicMediaList extends ListHandlerBaseObject<Media> implements MediaList
{
	// Fields.
	private final MediaComparator m_Comparator;
	private final List<Media> m_List = new ArrayList<Media>();
	private final int m_MaxMediaCount;
	
	
	/**
	 * Initialize new BasicMediaList instance.
	 * @param comparator Comparator for media ordering.
	 * @param maxMediaCount Maximum number of media allowed in list, negative value means unlimited.
	 */
	protected BasicMediaList(MediaComparator comparator, int maxMediaCount)
	{
		m_Comparator = comparator;
		m_MaxMediaCount = maxMediaCount;
	}
	
	
	/**
	 * Add media to list.
	 * @param media Media to add.
	 * @return Index of added position, or negative if media is already contained in list.
	 */
	protected int addMedia(Media media)
	{
		this.verifyAccess();
		int index = Collections.binarySearch(m_List, media, m_Comparator);
		if(index < 0)
		{
			// remove extra media
			index = ~index;
			if(m_MaxMediaCount >= 0 && m_List.size() >= m_MaxMediaCount)
			{
				if(index >= m_MaxMediaCount)
					return ~index;
				this.removeMediaInternal(m_MaxMediaCount - 1, m_List.size() - 1);
			}
			
			// add to list
			m_List.add(index, media);
			ListChangeEventArgs e = ListChangeEventArgs.obtain(index);
			this.raise(EVENT_MEDIA_ADDED, e);
			e.recycle();
			return index;
		}
		return -1;
	}
	
	
	/**
	 * Add media to list.
	 * @param mediaList Media to add.
	 * @param isSorted Whether given media is sorted or not.
	 */
	protected void addMedia(List<Media> mediaList, boolean isSorted)
	{
		// check thread
		this.verifyAccess();
		
		// check input
		if(mediaList == null || mediaList.isEmpty())
			return;
		
		// add directly
		if(this.addMediaDirectly(mediaList, isSorted))
			return;
		
		// add media one-by-one
		ListChangeEventArgs e;
		int startIndex = -1, endIndex = -1;
		for(int i = 0, count = mediaList.size() ; i < count ; ++i)
		{
			Media media = mediaList.get(i);
			int index = Collections.binarySearch(m_List, media, m_Comparator);
			if(index < 0)
			{
				// remove extra media
				index = ~index;
				if(m_MaxMediaCount >= 0 && m_List.size() >= m_MaxMediaCount)
				{
					if(index >= m_MaxMediaCount)
						continue;
					if(startIndex >= 0)
					{
						e = ListChangeEventArgs.obtain(startIndex, endIndex);
						this.raise(EVENT_MEDIA_ADDED, e);
						e.recycle();
						startIndex = -1;
						endIndex = -1;
					}
					this.removeMediaInternal(m_MaxMediaCount - 1, m_List.size() - 1);
				}
				
				// add media
				m_List.add(index, media);
				
				// raise event
				if(startIndex >= 0)
				{
					if(index == (endIndex + 1) || index == (startIndex - 1))
						++endIndex;
					else
					{
						e = ListChangeEventArgs.obtain(startIndex, endIndex);
						this.raise(EVENT_MEDIA_ADDED, e);
						e.recycle();
						startIndex = index;
						endIndex = index;
					}
				}
				else
				{
					startIndex = index;
					endIndex = index;
				}
			}
		}
		if(startIndex >= 0)
		{
			e = ListChangeEventArgs.obtain(startIndex, endIndex);
			this.raise(EVENT_MEDIA_ADDED, e);
			e.recycle();
		}
	}
	
	
	// Add media directly to list.
	private boolean addMediaDirectly(List<Media> mediaList, boolean isSorted)
	{
		// Initial list
		ListChangeEventArgs e;
		int currentCount = m_List.size();
		if(currentCount == 0)
		{
			if(m_MaxMediaCount < 0 || mediaList.size() <= m_MaxMediaCount)
			{
				m_List.addAll(mediaList);
				if(!isSorted)
					Collections.sort(m_List, m_Comparator);
			}
			else
			{
				if(isSorted)
				{
					for(int i = 0 ; i < m_MaxMediaCount ; ++i)
						m_List.add(mediaList.get(i));
				}
				else
				{
					m_List.addAll(mediaList);
					Collections.sort(m_List, m_Comparator);
					for(int i = m_List.size() - 1 ; i > m_MaxMediaCount ; --i)
						m_List.remove(i);
				}
			}
			e = ListChangeEventArgs.obtain(0, m_List.size() - 1);
			this.raise(EVENT_MEDIA_ADDED, e);
			e.recycle();
			return true;
		}
		
		// add directly to head or tail
		if(isSorted)
		{
			if(m_Comparator.compare(m_List.get(currentCount - 1), mediaList.get(0)) < 0)
			{
				if(m_MaxMediaCount < 0 || m_MaxMediaCount >= (currentCount + mediaList.size()))
					m_List.addAll(mediaList);
				else if(m_MaxMediaCount == currentCount)
					return true;
				else
				{
					for(int i = 0, count = (m_MaxMediaCount - currentCount) ; i < count ; ++i)
						m_List.add(mediaList.get(i));
				}
				e = ListChangeEventArgs.obtain(currentCount, m_List.size() - 1);
				this.raise(EVENT_MEDIA_ADDED, e);
				e.recycle();
				return true;
			}
			else if(m_Comparator.compare(m_List.get(0), mediaList.get(mediaList.size() - 1)) > 0)
			{
				if(m_MaxMediaCount < 0 || m_MaxMediaCount >= (currentCount + mediaList.size()))
					m_List.addAll(0, mediaList);
				else if(m_MaxMediaCount == currentCount)
					return true;
				else
				{
					for(int i = (m_MaxMediaCount - currentCount - 1) ; i >= 0 ; --i)
						m_List.add(0, mediaList.get(i));
				}
				e = ListChangeEventArgs.obtain(0, currentCount - 1);
				this.raise(EVENT_MEDIA_ADDED, e);
				e.recycle();
				return true;
			}
		}
		
		// failed
		return false;
	}
	
	
	/**
	 * Clear all media in list.
	 */
	protected void clearMedia()
	{
		this.verifyAccess();
		int count = m_List.size();
		if(count > 0)
		{
			m_List.clear();
			ListChangeEventArgs e = ListChangeEventArgs.obtain(0, count - 1);
			this.raise(EVENT_MEDIA_REMOVED, e);
			e.recycle();
		}
	}
	
	
	// Check whether media is in list or not.
	@Override
	public boolean contains(Object object)
	{
		if(object instanceof Media)
		{
			int index = Collections.binarySearch(m_List, (Media)object, m_Comparator);
			return (index >= 0);
		}
		return false;
	}
	
	
	// Get media.
	@Override
	public Media get(int location)
	{
		return m_List.get(location);
	}
	
	
	/**
	 * Get media comparator for media ordering.
	 * @return Media comparator.
	 */
	public final MediaComparator getComparator()
	{
		return m_Comparator;
	}
	
	
	/**
	 * Get maximum number of media allowed in list.
	 * @return Maximum number of media allowed in list, negative value means unlimited.
	 */
	public final int getMaxMediaCount()
	{
		return m_MaxMediaCount;
	}
	
	
	// Get index of media.
	@Override
	public int indexOf(Object object)
	{
		if(object instanceof Media)
		{
			int index = Collections.binarySearch(m_List, (Media)object, m_Comparator);
			return (index >= 0 ? index : -1);
		}
		return -1;
	}
	
	
	/**
	 * Remove media from list.
	 * @param media Media to remove.
	 * @return True if media removed from list successfully.
	 */
	protected boolean removeMedia(Media media)
	{
		this.verifyAccess();
		if(media == null)
			return false;
		int index = Collections.binarySearch(m_List, media, m_Comparator);
		if(index >= 0 && m_List.get(index) == media)
		{
			this.removeMediaInternal(index);
			return true;
		}
		return false;
	}
	
	
	/**
	 * Remove media from list.
	 * @param index Index of media to remove.
	 */
	protected void removeMedia(int index)
	{
		this.verifyAccess();
		this.removeMediaInternal(index);
	}
	
	
	// Remove media from list.
	private void removeMediaInternal(int index)
	{
		m_List.remove(index);
		ListChangeEventArgs e = ListChangeEventArgs.obtain(index);
		this.raise(EVENT_MEDIA_REMOVED, e);
		e.recycle();
	}
	private void removeMediaInternal(int startIndex, int endIndex)
	{
		for(int i = startIndex ; i <= endIndex ; ++i)
			m_List.remove(i);
		ListChangeEventArgs e = ListChangeEventArgs.obtain(startIndex, endIndex);
		this.raise(EVENT_MEDIA_REMOVED, e);
		e.recycle();
	}

	
	// Get media count.
	@Override
	public int size()
	{
		return m_List.size();
	}
}
