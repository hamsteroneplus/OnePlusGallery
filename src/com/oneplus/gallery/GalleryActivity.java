package com.oneplus.gallery;

import java.util.HashMap;
import java.util.Map;

import android.app.Fragment;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.SparseArray;

import com.oneplus.base.BaseActivity;
import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.base.PropertyChangeEventArgs;
import com.oneplus.base.PropertyChangedCallback;
import com.oneplus.base.PropertyKey;
import com.oneplus.base.PropertySource;
import com.oneplus.base.ScreenSize;

/**
 * Base class for activity in Gallery.
 */
public abstract class GalleryActivity extends BaseActivity
{
	/**
	 * Read-only property to get screen size.
	 */
	public static final PropertyKey<ScreenSize> PROP_SCREEN_SIZE = new PropertyKey<>("ScreenSize", ScreenSize.class, GalleryActivity.class, PropertyKey.FLAG_READONLY, null);
	
	
	/**
	 * Key of extra data in {@link Intent} for ID of shared {@link Gallery} instance. 
	 */
	public static final String EXTRA_SHARED_GALLERY_ID = "com.oneplus.gallery.GalleryActivity.extra.SHARED_GALLERY_ID";
	
	
	// Fields.
	private SparseArray<ActivityResultHandle> m_ActivityResultHandles;
	private Gallery m_Gallery;
	private Handle m_GalleryAttachHandle;
	private boolean m_IsInstanceStateSaved;
	private ScreenSize m_ScreenSize;
	
	
	// Call-backs.
	private final PropertyChangedCallback<Boolean> m_NavBarVisibilityCallback = new PropertyChangedCallback<Boolean>()
	{
		@Override
		public void onPropertyChanged(PropertySource source, PropertyKey<Boolean> key, PropertyChangeEventArgs<Boolean> e)
		{
			onNavigationBarVisibilityChanged(e.getNewValue());
		}
	};
	private final PropertyChangedCallback<Boolean> m_StatusBarVisibilityCallback = new PropertyChangedCallback<Boolean>()
	{
		@Override
		public void onPropertyChanged(PropertySource source, PropertyKey<Boolean> key, PropertyChangeEventArgs<Boolean> e)
		{
			onStatusBarVisibilityChanged(e.getNewValue());
		}
	};
	
	
	/**
	 * Activity result call-back interface.
	 */
	public interface ActivityResultCallback
	{
		/**
		 * Called when activity result received.
		 * @param handle Handle returned from {@link GalleryActivity#startActivityForResult(Intent, ActivityResultCallback)}.
		 * @param result Result code.
		 * @param data Result data.
		 */
		void onActivityResult(Handle handle, int result, Intent data);
	}
	
	
	// Fragment for sharing instance state.
	private static final class InstanceStateFragment extends Fragment
	{
		// Constants.
		public static final String TAG = "GalleryActivity.InstanceState";
		
		// Fields.
		public SparseArray<ActivityResultHandle> activityResultHandles;
		public final Map<String, Object> extras = new HashMap<>();
		public Gallery gallery;
		
		// Constructor.
		public InstanceStateFragment()
		{
			this.setRetainInstance(true);
		}
	}
	
	
	// Class for activity result handle.
	private static final class ActivityResultHandle extends Handle
	{
		// Fields.
		public final ActivityResultCallback callback;
		
		// Constructor.
		public ActivityResultHandle(ActivityResultCallback callback)
		{
			super("ActivityResult");
			this.callback = callback;
		}

		// Close handle.
		@Override
		protected void onClose(int flags)
		{}
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
	
	
	/**
	 * Get related {@link Gallery}.
	 * @return {@link Gallery}.
	 */
	public final Gallery getGallery()
	{
		return m_Gallery;
	}
	
	
	/**
	 * Go back to previous state.
	 */
	public boolean goBack()
	{
		return false;
	}
	
	
	// Called after getting result from activity.
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		ActivityResultHandle handle = m_ActivityResultHandles.get(requestCode);
		if(handle != null)
		{
			m_ActivityResultHandles.delete(requestCode);
			if(Handle.isValid(handle) && handle.callback != null)
				handle.callback.onActivityResult(handle, resultCode, data);
			return;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}
	
	
	// Called after pressing back button.
	@Override
	public void onBackPressed()
	{
		if(!this.goBack())
			super.onBackPressed();
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
	protected final void onCreate(Bundle savedInstanceState)
	{
		// call super
		super.onCreate(savedInstanceState);
		
		// setup instance state
		Intent intent = this.getIntent();
		String sharedGalleryId = (intent != null ? intent.getStringExtra(EXTRA_SHARED_GALLERY_ID) : null);
		InstanceStateFragment stateFragment;
		m_Gallery = Gallery.fromId(sharedGalleryId);
		if(m_Gallery == null)
		{
			stateFragment = (InstanceStateFragment)this.getFragmentManager().findFragmentByTag(InstanceStateFragment.TAG);
			if(stateFragment != null)
			{
				Log.w(TAG, "onCreate() - Use existent Gallery : " + stateFragment.gallery.getId());
				m_Gallery = stateFragment.gallery;
				m_ActivityResultHandles = stateFragment.activityResultHandles;
			}
			else
			{
				Log.w(TAG, "onCreate() - Create new Gallery");
				m_Gallery = new Gallery();
				m_ActivityResultHandles = new SparseArray<>();
			}
		}
		else
		{
			Log.w(TAG, "onCreate() - Use shared Gallery : " + m_Gallery.getId());
			stateFragment = null;
			m_ActivityResultHandles = new SparseArray<>();
		}
		
		// attach to gallery
		m_GalleryAttachHandle = m_Gallery.attachActivity(this);
		if(!Handle.isValid(m_GalleryAttachHandle))
		{
			Log.e(TAG, "onCreate() - Fail to attach to Gallery");
			this.finish();
			return;
		}
		m_Gallery.addCallback(Gallery.PROP_IS_NAVIGATION_BAR_VISIBLE, m_NavBarVisibilityCallback);
		m_Gallery.addCallback(Gallery.PROP_IS_STATUS_BAR_VISIBLE, m_StatusBarVisibilityCallback);
		
		// initialize screen size
		this.updateScreenSize();
		
		// create
		this.onCreate(savedInstanceState, stateFragment != null ? stateFragment.extras : null);
	}
	
	
	/**
	 * Called when creating activity.
	 * @param savedInstanceState Saved instance state.
	 * @param extras Extra instance state.
	 */
	protected void onCreate(Bundle savedInstanceState, Map<String, Object> extras)
	{}
	
	
	// Called when destroying.
	@Override
	protected void onDestroy()
	{
		// detach from gallery
		m_Gallery.removeCallback(Gallery.PROP_IS_NAVIGATION_BAR_VISIBLE, m_NavBarVisibilityCallback);
		m_Gallery.removeCallback(Gallery.PROP_IS_STATUS_BAR_VISIBLE, m_StatusBarVisibilityCallback);
		m_GalleryAttachHandle = Handle.close(m_GalleryAttachHandle);
		
		// release gallery
		if(!m_IsInstanceStateSaved)
		{
			Log.w(TAG, "onDestroy() - Release Gallery");
			m_Gallery.release();
		}
		
		// release references
		m_ActivityResultHandles = null;
		
		// call super
		super.onDestroy();
	}
	
	
	/**
	 * Called when navigation bar visibility changed.
	 * @param isVisible True if navigation bar is visible.
	 */
	protected void onNavigationBarVisibilityChanged(boolean isVisible)
	{}
	
	
	// Restore instance state
	@Override
	protected final void onRestoreInstanceState(Bundle savedInstanceState)
	{
		super.onRestoreInstanceState(savedInstanceState);
		InstanceStateFragment stateFragment = (InstanceStateFragment)this.getFragmentManager().findFragmentByTag(InstanceStateFragment.TAG);
		this.onRestoreInstanceState(savedInstanceState, stateFragment != null ? stateFragment.extras : null);
	}
	
	
	/**
	 * Called when restoring instance state.
	 * @param savedInstanceState Saved instance state.
	 * @param extras Extra instance state.
	 */
	protected void onRestoreInstanceState(Bundle savedInstanceState, Map<String, Object> extras)
	{}
	
	
	// Called when resuming
	@Override
	protected void onResume()
	{
		super.onResume();
		m_IsInstanceStateSaved = false;
	}
	
	
	// Save instance state.
	@Override
	protected final void onSaveInstanceState(Bundle outState)
	{
		// get instance state fragment
		InstanceStateFragment stateFragment = (InstanceStateFragment)this.getFragmentManager().findFragmentByTag(InstanceStateFragment.TAG);
		if(stateFragment == null)
		{
			stateFragment = new InstanceStateFragment();
			this.getFragmentManager().beginTransaction().add(stateFragment, InstanceStateFragment.TAG).commit();
		}
		
		// save instance state
		Log.w(TAG, "onSaveInstanceState() - Keep Gallery instance : " + m_Gallery.getId());
		stateFragment.gallery = m_Gallery;
		stateFragment.activityResultHandles = m_ActivityResultHandles;
		this.onSaveInstanceState(outState, stateFragment.extras);
		
		// call super
		super.onSaveInstanceState(outState);
		
		// update state
		m_IsInstanceStateSaved = true;
	}
	
	
	/**
	 * Called when saving instance state.
	 * @param outState Bundle to save instance state.
	 * @param extras Extra instance state.
	 */
	protected void onSaveInstanceState(Bundle outState, Map<String, Object> extras)
	{}
	
	
	/**
	 * Called when status bar visibility changed.
	 * @param isVisible True if status bar is visible.
	 */
	protected void onStatusBarVisibilityChanged(boolean isVisible)
	{}
	
	
	/**
	 * Start activity for result.
	 * @param intent Intent to start activity.
	 * @param callback Result call-back.
	 * @return Handle to this operation.
	 */
	public Handle startActivityForResult(Intent intent, ActivityResultCallback callback)
	{
		// check parameter
		if(intent == null)
		{
			Log.e(TAG, "startActivityForResult() - No intent");
			return null;
		}
		
		// check state
		this.verifyAccess();
		
		// generate request code
		int requestCode = 64;
		for( ; requestCode > 0 ; --requestCode)
		{
			if(m_ActivityResultHandles.get(requestCode) == null)
				break;
		}
		if(requestCode <= 0)
		{
			Log.e(TAG, "startActivityForResult() - No available request code");
			return null;
		}
		
		// create handle
		ActivityResultHandle handle = new ActivityResultHandle(callback);
		m_ActivityResultHandles.put(requestCode, handle);
		
		// start activity for result
		try
		{
			this.startActivityForResult(intent, requestCode);
			return handle;
		}
		catch(Throwable ex)
		{
			Log.e(TAG, "startActivityForResult() - Fail to start activity", ex);
			m_ActivityResultHandles.delete(requestCode);
			return null;
		}
	}
	
	
	// Update screen size.
	private void updateScreenSize()
	{
		ScreenSize oldSize = m_ScreenSize;
		m_ScreenSize = new ScreenSize(this, false);
		this.notifyPropertyChanged(PROP_SCREEN_SIZE, oldSize, m_ScreenSize);
	}
}
