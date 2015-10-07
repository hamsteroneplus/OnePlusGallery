package com.oneplus.gallery;

import android.app.Activity;
import com.oneplus.base.BaseFragment;
import com.oneplus.base.EventKey;
import com.oneplus.base.PropertyKey;

/**
 * Base class for fragment in Gallery.
 */
public abstract class GalleryFragment extends BaseFragment
{
	/**
	 * ID for back action.
	 */
	public static final String ACTION_ID_BACK = "GalleryFragment.Action.Back";
	
	
	/**
	 * Property to get or set action bar can be shown or not.
	 */
	public static final PropertyKey<Boolean> PROP_HAS_ACTION_BAR = new PropertyKey<>("HasActionBar", Boolean.class, GalleryFragment.class, PropertyKey.FLAG_NOT_NULL, false);
	/**
	 * Property to get or set whether {@link #ACTION_ID_BACK} action is needed or not.
	 */
	public static final PropertyKey<Boolean> PROP_IS_BACK_ACTION_NEEDED = new PropertyKey<>("IsBackActionNeeded", Boolean.class, GalleryFragment.class, PropertyKey.FLAG_NOT_NULL, true);
	/**
	 * Property to get or set fragment title.
	 */
	public static final PropertyKey<String> PROP_TITLE = new PropertyKey<>("Title", String.class, GalleryFragment.class, 0, null);
	
	
	/**
	 * Raised after clicking action item.
	 */
	public static final EventKey<ActionItemEventArgs> EVENT_ACTION_ITEM_CLICKED = new EventKey<>("ActionItemClicked", ActionItemEventArgs.class, GalleryFragment.class);
	
	
	// Fields.
	private GalleryActivity m_GalleryActivity;
	
	
	/**
	 * Get {@link GalleryActivity} instance which this fragment attach to.
	 * @return {@link GalleryActivity}, or Null if this fragment is not attached yet.
	 */
	public final GalleryActivity getGalleryActivity()
	{
		return m_GalleryActivity;
	}
	
	
	// Called after attaching to activity.
	@Override
	public void onAttach(Activity activity)
	{
		// call super
		super.onAttach(activity);
		
		// attach to gallery activity
		m_GalleryActivity = (GalleryActivity)activity;
	}
	
	
	// Called after detaching from activity.
	@Override
	public void onDetach()
	{
		// detach from gallery activity
		m_GalleryActivity = null;
		
		// call super
		super.onDetach();
	}
}
