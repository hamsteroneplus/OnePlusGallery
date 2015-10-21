package com.oneplus.gallery;

import com.oneplus.base.EventArgs;

/**
 * Data for list item related events.
 * @param <T> Type of list item.
 */
public class ListItemEventArgs<T> extends EventArgs
{
	// Fields.
	private final int m_Index;
	private final T m_Item;
	
	
	/**
	 * Initialize new ListItemEventArgs instance.
	 * @param index Index of item.
	 * @param item Item.
	 */
	public ListItemEventArgs(int index, T item)
	{
		m_Index = index;
		m_Item = item;
	}
	
	
	/**
	 * Get index of item.
	 * @return Index of item.
	 */
	public final int getIndex()
	{
		return m_Index;
	}
	
	
	/**
	 * Get item.
	 * @return Item.
	 */
	public final T getItem()
	{
		return m_Item;
	}
}
