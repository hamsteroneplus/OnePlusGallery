package com.oneplus.gallery;

import com.oneplus.base.EventArgs;

/**
 * Data for action item related events.
 */
public class ActionItemEventArgs extends EventArgs
{
	// Fields.
	private final String m_Id;
	
	
	/**
	 * Initialize new ActionItemEventArgs instance.
	 * @param id Action ID.
	 */
	public ActionItemEventArgs(String id)
	{
		m_Id = id;
	}
	
	
	/**
	 * Get related action ID.
	 * @return Action ID.
	 */
	public final String getActionId()
	{
		return m_Id;
	}
}
