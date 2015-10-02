package com.oneplus.gallery;

import java.util.ArrayList;
import java.util.List;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import com.oneplus.base.BaseActivity;
import com.oneplus.base.Handle;
import com.oneplus.base.PropertyKey;
import com.oneplus.base.ScreenSize;
import com.oneplus.util.ListUtils;

/**
 * Base class for activity in Gallery.
 */
public abstract class GalleryActivity extends BaseActivity
{
	/**
	 * Read-only property to get status bar visibility.
	 */
	public static final PropertyKey<Boolean> PROP_IS_STATUS_BAR_VISIBLE = new PropertyKey<>("IsStatusBarVisible", Boolean.class, GalleryActivity.class, false);
	/**
	 * Read-only property to get screen size.
	 */
	public static final PropertyKey<ScreenSize> PROP_SCREEN_SIZE = new PropertyKey<>("ScreenSize", ScreenSize.class, GalleryActivity.class, PropertyKey.FLAG_READONLY, null);
	
	
	/**
	 * Flag to indicate that operation can be cancelled.
	 */
	public static final int FLAG_CANCELABLE = 0x1;
	
	
	// Fields.
	private ScreenSize m_ScreenSize;
	private final List<StatusBarVisibilityHandle> m_StatusBarVisibilityHandles = new ArrayList<>();
	
	
	// Handle for status bar visibility.
	private final class StatusBarVisibilityHandle extends Handle
	{
		public final int flags;
		public final boolean isVisible;
		
		public StatusBarVisibilityHandle(boolean isVisible, int flags)
		{
			super("StatusBarVisibility");
			this.isVisible = isVisible;
			this.flags = flags;
		}
		
		public void drop()
		{
			this.closeDirectly();
		}

		@Override
		protected void onClose(int flags)
		{
			restoreStatusBarVisibility(this, true);
		}
	}
	
	
	// Check status bar visibility.
	private void checkStatusBarVisibility(Boolean requestedVisibility)
	{
		Window window = this.getWindow();
		if(window != null)
			this.checkStatusBarVisibility(window.getDecorView().getSystemUiVisibility(), requestedVisibility);
	}
	private void checkStatusBarVisibility(int systemUiVisiblity, Boolean requestedVisibility)
	{
		// check requested visibility
		boolean isVisible = ((systemUiVisiblity & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0);
		if(requestedVisibility == null)
			requestedVisibility = isVisible;
		for(int i = m_StatusBarVisibilityHandles.size() - 1 ; i >= 0 ; --i)
		{
			StatusBarVisibilityHandle handle = m_StatusBarVisibilityHandles.get(i);
			if(handle.isVisible != isVisible)
			{
				if((handle.flags & FLAG_CANCELABLE) != 0)
				{
					handle.drop();
					m_StatusBarVisibilityHandles.remove(i);
				}
				else
					requestedVisibility = !isVisible;
			}
		}
		
		// check default visibility
		if(m_StatusBarVisibilityHandles.isEmpty() && !isVisible)
			requestedVisibility = true;
		
		// update visibility
		if(requestedVisibility != isVisible)
		{
			if(requestedVisibility)
				systemUiVisiblity &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
			else
				systemUiVisiblity |= View.SYSTEM_UI_FLAG_FULLSCREEN;
			systemUiVisiblity |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
			this.getWindow().getDecorView().setSystemUiVisibility(systemUiVisiblity);
			return;
		}
		
		// update state
		if(this.setReadOnly(PROP_IS_STATUS_BAR_VISIBLE, isVisible))
			this.onStatusBarVisibilityChanged(isVisible);
	}
	
	
	// Get property.
	@SuppressWarnings("unchecked")
	@Override
	public <TValue> TValue get(PropertyKey<TValue> key)
	{
		if(key == PROP_SCREEN_SIZE)
			return (TValue)m_ScreenSize;
		return super.get(key);
	}
	
	
	// Called when configuration changed.
	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		// call super
		super.onConfigurationChanged(newConfig);
		
		// initialize screen size
		this.updateScreenSize();
	}
	
	
	// Called when creating.
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		// call super
		super.onCreate(savedInstanceState);
		
		// initialize screen size
		this.updateScreenSize();
		
		// initialize system UI state
		View decorView = this.getWindow().getDecorView();
		decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
		decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener()
		{
			@Override
			public void onSystemUiVisibilityChange(int visibility)
			{
				checkStatusBarVisibility(visibility, null);
			}
		});
		this.checkStatusBarVisibility(decorView.getSystemUiVisibility(), null);
	}
	
	
	/**
	 * Called when status bar visibility changed.
	 * @param isVisible True if status bar is visible.
	 */
	protected void onStatusBarVisibilityChanged(boolean isVisible)
	{}
	
	
	// Restore status bar visibility.
	private void restoreStatusBarVisibility(StatusBarVisibilityHandle handle, boolean updateStatusBar)
	{
		boolean isLast = ListUtils.isLastObject(m_StatusBarVisibilityHandles, handle);
		if(m_StatusBarVisibilityHandles.remove(handle) && updateStatusBar && isLast)
			this.checkStatusBarVisibility(null);
	}
	
	
	/**
	 * Set status bar visibility.
	 * @param isVisible True to show status bar.
	 * @return Handle to this operation.
	 */
	public Handle setStatusBarVisibility(boolean isVisible)
	{
		return this.setStatusBarVisibility(isVisible, FLAG_CANCELABLE);
	}
	
	
	/**
	 * Set status bar visibility.
	 * @param isVisible True to show status bar.
	 * @param flags Flags:
	 * <ul>
	 *   <li>{@link #FLAG_CANCELABLE}</li>
	 * </ul>
	 * @return Handle to this operation.
	 */
	public Handle setStatusBarVisibility(boolean isVisible, int flags)
	{
		this.verifyAccess();
		this.checkStatusBarVisibility(isVisible);
		StatusBarVisibilityHandle handle = new StatusBarVisibilityHandle(isVisible, flags);
		m_StatusBarVisibilityHandles.add(handle);
		return handle;
	}
	
	
	// Update screen size.
	private void updateScreenSize()
	{
		ScreenSize oldSize = m_ScreenSize;
		m_ScreenSize = new ScreenSize(this, false);
		this.notifyPropertyChanged(PROP_SCREEN_SIZE, oldSize, m_ScreenSize);
	}
}
