package com.oneplus.gallery;

import java.util.ArrayDeque;
import java.util.Queue;

import com.oneplus.base.EventArgs;
import com.oneplus.base.RecyclableObject;

/**
 * Data for list change related events.
 */
public class ListChangeEventArgs extends EventArgs implements RecyclableObject
{
	// Static fields.
	private static final int POOL_SIZE = 16;
	private static final Queue<ListChangeEventArgs> POOL = new ArrayDeque<>(POOL_SIZE);
	
	
	// Fields.
	private volatile int m_EndIndex;
	private volatile boolean m_IsFreeInstance;
	private volatile int m_StartIndex;
	
	
	/**
	 * Initialize new ListChangeEventArgs instance.
	 * @param startIndex Index of first affected item in set.
	 * @param endIndex Index of last affected item in list.
	 */
	private ListChangeEventArgs(int startIndex, int endIndex)
	{
		m_StartIndex = startIndex;
		m_EndIndex = endIndex;
	}
	
	
	/**
	 * Get index of last affected item in list.
	 * @return Index of item.
	 */
	public int getEndIndex()
	{
		return m_EndIndex;
	}
	
	
	/**
	 * Get number of affected item in list.
	 * @return Number of affected item.
	 */
	public int getItemCount()
	{
		return Math.max(0, m_EndIndex - m_StartIndex + 1);
	}
	
	
	/**
	 * Get index of first affected item in list.
	 * @return Index of item.
	 */
	public int getStartIndex()
	{
		return m_StartIndex;
	}
	
	
	/**
	 * Obtain an instance from pool.
	 * @param index Index of affected item in list.
	 * @return Available instance.
	 */
	public static ListChangeEventArgs obtain(int index)
	{
		return obtain(index, index);
	}
	
	
	/**
	 * Obtain an instance from pool.
	 * @param startIndex Index of first affected item in list.
	 * @param endIndex Index of last affected item in list.
	 * @return Available instance.
	 */
	public static synchronized ListChangeEventArgs obtain(int startIndex, int endIndex)
	{
		ListChangeEventArgs e = POOL.poll();
		if(e != null)
		{
			e.m_StartIndex = startIndex;
			e.m_EndIndex = endIndex;
			e.m_IsFreeInstance = false;
		}
		else
			e = new ListChangeEventArgs(startIndex, endIndex);
		return e;
	}
	
	
	// Recycle.
	@Override
	public void recycle()
	{
		synchronized(ListChangeEventArgs.class)
		{
			if(m_IsFreeInstance)
				return;
			m_IsFreeInstance = false;
			if(POOL.size() < POOL_SIZE)
				POOL.add(this);
		}
	}
}
