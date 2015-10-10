package com.oneplus.gallery;

import java.util.ArrayList;
import java.util.List;

import android.view.View;
import android.view.Window;

import com.oneplus.base.Handle;
import com.oneplus.base.HandlerBaseObject;
import com.oneplus.base.Log;
import com.oneplus.base.PropertyKey;
import com.oneplus.util.ListUtils;

/**
 * Gallery instance.
 */
public class Gallery extends HandlerBaseObject
{
	/**
	 * Read-only property to get current activity.
	 */
	public static final PropertyKey<GalleryActivity> PROP_ACTIVITY = new PropertyKey<>("Activity", GalleryActivity.class, Gallery.class, PropertyKey.FLAG_READONLY, null);
	/**
	 * Read-only property to get navigation bar visibility.
	 */
	public static final PropertyKey<Boolean> PROP_IS_NAVIGATION_BAR_VISIBLE = new PropertyKey<>("IsNavigationBarVisible", Boolean.class, Gallery.class, true);
	/**
	 * Read-only property to get status bar visibility.
	 */
	public static final PropertyKey<Boolean> PROP_IS_STATUS_BAR_VISIBLE = new PropertyKey<>("IsStatusBarVisible", Boolean.class, Gallery.class, true);
	
	
	/**
	 * Flag to indicate that operation can be cancelled.
	 */
	public static final int FLAG_CANCELABLE = 0x1;
	
	
	// Fields.
	private GalleryActivity m_Activity;
	private View m_ActivityDecorView;
	private final List<NavBarVisibilityHandle> m_NavBarVisibilityHandles = new ArrayList<>();
	private final List<StatusBarVisibilityHandle> m_StatusBarVisibilityHandles = new ArrayList<>();
	
	
	// Listeners.
	private final View.OnSystemUiVisibilityChangeListener m_SystemUiVisibilityListener = new View.OnSystemUiVisibilityChangeListener()
	{
		@Override
		public void onSystemUiVisibilityChange(int visibility)
		{
			onSystemUiVisibilityChanged(visibility);
		}
	};
	
	
	// System UI visibility handle.
	private abstract class SystemUiVisibilityHandle extends Handle
	{
		// Fields.
		public final int flags;
		public final boolean isVisible;
		
		// Constructor.
		protected SystemUiVisibilityHandle(String name, boolean isVisible, int flags)
		{
			super(name);
			this.isVisible = isVisible;
			this.flags = flags;
		}
		
		// Drop this handle.
		public final void drop()
		{
			this.closeDirectly();
		}
	}
	private final class StatusBarVisibilityHandle extends SystemUiVisibilityHandle
	{
		// Constructor.
		public StatusBarVisibilityHandle(boolean isVisible, int flags)
		{
			super("StatusBarVisibility", isVisible, flags);
		}

		// CLose handle
		@Override
		protected void onClose(int flags)
		{
			restoreStatusBarVisibility(this);
		}
	}
	private final class NavBarVisibilityHandle extends SystemUiVisibilityHandle
	{
		// Constructor.
		public NavBarVisibilityHandle(boolean isVisible, int flags)
		{
			super("NavBarVisibility", isVisible, flags);
		}

		// CLose handle
		@Override
		protected void onClose(int flags)
		{
			// TODO Auto-generated method stub
		}
	}
	
	
	/**
	 * Initialize new Gallery instance.
	 */
	Gallery()
	{
		super(true);
		this.enablePropertyLogs(PROP_ACTIVITY, LOG_PROPERTY_CHANGE);
	}
	
	
	/**
	 * Attach activity to this instance.
	 * @param activity {@link GalleryActivity} to attach.
	 * @return Handle to this operation.
	 */
	final Handle attachActivity(GalleryActivity activity)
	{
		// check state
		this.verifyAccess();
		if(this.get(PROP_IS_RELEASED))
		{
			Log.e(TAG, "attachActivity() - Instance has been released");
			return null;
		}
		if(activity == null)
		{
			Log.e(TAG, "attachActivity() - No activity");
			return null;
		}
		if(m_Activity != null)
		{
			Log.e(TAG, "attachActivity() - Already attached");
			return null;
		}
		
		// attach to decor view
		Window window = activity.getWindow();
		if(window == null)
		{
			Log.e(TAG, "attachActivity() - No window");
			return null;
		}
		m_ActivityDecorView = window.getDecorView();
		m_ActivityDecorView.setOnSystemUiVisibilityChangeListener(m_SystemUiVisibilityListener);
		
		// attach to activity
		m_Activity = activity;
		this.notifyPropertyChanged(PROP_ACTIVITY, null, activity);
		
		// setup system UI visibility
		this.setSystemUiVisibility(this.get(PROP_IS_STATUS_BAR_VISIBLE), this.get(PROP_IS_NAVIGATION_BAR_VISIBLE));
		
		// complete
		return new Handle("AttachGalleryActivity")
		{
			@Override
			protected void onClose(int flags)
			{
				detachActivity();
			}
		};
	}
	
	
	// Detach activity.
	private void detachActivity()
	{
		// check state
		this.verifyAccess();
		if(m_Activity == null)
			return;
		
		// detach from decor view
		if(m_ActivityDecorView != null)
		{
			m_ActivityDecorView.setOnSystemUiVisibilityChangeListener(null);
			m_ActivityDecorView = null;
		}
		
		// detach from activity
		GalleryActivity activity = m_Activity;
		m_Activity = null;
		this.notifyPropertyChanged(PROP_ACTIVITY, activity, null);
	}
	
	
	// Get property.
	@SuppressWarnings("unchecked")
	@Override
	public <TValue> TValue get(PropertyKey<TValue> key)
	{
		if(key == PROP_ACTIVITY)
			return (TValue)m_Activity;
		return super.get(key);
	}
	
	
	// Release.
	@Override
	protected void onRelease()
	{
		// call super
		super.onRelease();
		
		// detach from activity
		this.detachActivity();
	}
	
	
	// Called when system UI visibility changed.
	private void onSystemUiVisibilityChanged(int visibility)
	{
		// check visibilities
		boolean isStatusBarVisible = ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0);
		boolean isNavBarVisible = ((visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0);
		
		// check status bar visibility
		Boolean showStatusBar = null;
		for(int i = m_StatusBarVisibilityHandles.size() - 1 ; i >= 0 ; --i)
		{
			StatusBarVisibilityHandle handle = m_StatusBarVisibilityHandles.get(i);
			if(handle.isVisible != isStatusBarVisible)
			{
				if((handle.flags & FLAG_CANCELABLE) == 0)
					showStatusBar = handle.isVisible;
				else
				{
					handle.drop();
					m_StatusBarVisibilityHandles.remove(i);
				}
			}
		}
		
		// check navigation bar
		Boolean showNavBar = null;
		for(int i = m_NavBarVisibilityHandles.size() - 1 ; i >= 0 ; --i)
		{
			NavBarVisibilityHandle handle = m_NavBarVisibilityHandles.get(i);
			if(handle.isVisible != isNavBarVisible)
			{
				if((handle.flags & FLAG_CANCELABLE) == 0)
					showNavBar = handle.isVisible;
				else
				{
					handle.drop();
					m_NavBarVisibilityHandles.remove(i);
				}
			}
		}
		
		// update system UI visibility
		if(showStatusBar != null || showNavBar != null)
			this.setSystemUiVisibility(showStatusBar, showNavBar);
	}
	
	
	// Restore status bar visibility.
	private void restoreStatusBarVisibility(StatusBarVisibilityHandle handle)
	{
		boolean isLast = ListUtils.isLastObject(m_StatusBarVisibilityHandles, handle);
		if(m_StatusBarVisibilityHandles.remove(handle) && isLast)
		{
			if(m_StatusBarVisibilityHandles.isEmpty())
				this.setSystemUiVisibility(true, null);
			else
				this.setSystemUiVisibility(m_StatusBarVisibilityHandles.get(m_StatusBarVisibilityHandles.size() - 1).isVisible, null);
		}
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
		for(int i = m_StatusBarVisibilityHandles.size() - 1 ; i >= 0 ; --i)
		{
			StatusBarVisibilityHandle handle = m_StatusBarVisibilityHandles.get(i);
			if(handle.isVisible != isVisible && (handle.flags & FLAG_CANCELABLE) != 0)
			{
				handle.drop();
				m_StatusBarVisibilityHandles.remove(i);
			}
		}
		StatusBarVisibilityHandle handle = new StatusBarVisibilityHandle(isVisible, flags);
		m_StatusBarVisibilityHandles.add(handle);
		this.setSystemUiVisibility(isVisible, null);
		return handle;
	}
	
	
	// Set system UI visibility.
	private boolean setSystemUiVisibility(Boolean isStatusBarVisible, Boolean isNavBarVisible)
	{
		// check state
		if(m_ActivityDecorView == null)
		{
			Log.e(TAG, "setSystemUiVisibility() - No window");
			return false;
		}
		
		// prepare
		int visibility = m_ActivityDecorView.getSystemUiVisibility();
		if(isStatusBarVisible != null)
		{
			if(isStatusBarVisible)
				visibility &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
			else
				visibility |= View.SYSTEM_UI_FLAG_FULLSCREEN;
		}
		if(isNavBarVisible != null)
		{
			if(isNavBarVisible)
				visibility &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
			else
				visibility |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
		}
		visibility |= (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
		
		// update
		m_ActivityDecorView.setSystemUiVisibility(visibility);
		
		// update property
		if(isStatusBarVisible != null)
			this.setReadOnly(PROP_IS_STATUS_BAR_VISIBLE, isStatusBarVisible);
		if(isNavBarVisible != null)
			this.setReadOnly(PROP_IS_NAVIGATION_BAR_VISIBLE, isNavBarVisible);
		
		// complete
		return true;
	}
}
