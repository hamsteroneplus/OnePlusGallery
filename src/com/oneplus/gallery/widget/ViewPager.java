package com.oneplus.gallery.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

/**
 * Extended view pager.
 */
public class ViewPager extends android.support.v4.view.ViewPager
{
	// Fields.
	private boolean m_IsPositionLocked;
	
	
	/**
	 * Initialize new ViewPager instance.
	 * @param context Context.
	 * @param attrs Attributes.
	 */
	public ViewPager(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}
	
	
	/**
	 * Lock in current position.
	 */
	public void lockPosition()
	{
		m_IsPositionLocked = true;
	}
	
	
	// Called to intercept touch event.
	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) 
	{
		if(m_IsPositionLocked)
			return false;
		return super.onInterceptTouchEvent(event);
	}
	
	
	/**
	 * Unlock position.
	 */
	public void unlockPosition()
	{
		m_IsPositionLocked = false;
	}
}
