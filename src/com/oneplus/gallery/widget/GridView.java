package com.oneplus.gallery.widget;

import android.content.Context;
import android.os.Parcelable;
import android.util.AttributeSet;

/**
 * Extended {@link android.widget.GridView}.
 */
public class GridView extends android.widget.GridView
{
	// Fields.
	private boolean m_SaveInstanceState = true;
	
	
	/**
	 * Initialize new GridView instance.
	 * @param context Context.
	 * @param attrs Attributes.
	 */
	public GridView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}
	
	
	// Called when saving instance state.
	@Override
	public Parcelable onSaveInstanceState()
	{
		Parcelable state = super.onSaveInstanceState();
		return (m_SaveInstanceState ? state : null);
	}
	
	
	/**
	 * Enable or disable saving instance state.
	 * @param enabled Whether saving instance state is enabled or not.
	 */
	public void setSaveInstanceStateEnabled(boolean enabled)
	{
		m_SaveInstanceState = enabled;
	}
}
