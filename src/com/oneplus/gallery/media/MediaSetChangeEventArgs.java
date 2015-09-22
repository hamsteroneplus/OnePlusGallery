package com.oneplus.gallery.media;

import com.oneplus.base.EventArgs;

/**
 * Data for media set change related events.
 */
public class MediaSetChangeEventArgs extends EventArgs
{
	// Fields.
	private final int m_EndIndex;
	private final int m_StartIndex;
	
	
	/**
	 * Initialize new MediaSetChangeEventArgs instance.
	 * @param index Index of affected media in set.
	 */
	public MediaSetChangeEventArgs(int index)
	{
		this(index, index);
	}
	
	
	/**
	 * Initialize new MediaSetChangeEventArgs instance.
	 * @param startIndex Index of first affected media in set.
	 * @param endIndex Index of last affected media in set.
	 */
	public MediaSetChangeEventArgs(int startIndex, int endIndex)
	{
		m_StartIndex = startIndex;
		m_EndIndex = endIndex;
	}
	
	
	/**
	 * Get index of last affected media in set.
	 * @return Index of media.
	 */
	public int getEndIndex()
	{
		return m_EndIndex;
	}
	
	
	/**
	 * Get number of affected media in set.
	 * @return Number of affected media.
	 */
	public int getMediaCount()
	{
		return Math.max(0, m_EndIndex - m_StartIndex + 1);
	}
	
	
	/**
	 * Get index of first affected media in set.
	 * @return Index of media.
	 */
	public int getStartIndex()
	{
		return m_StartIndex;
	}
}
