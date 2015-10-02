package com.oneplus.gallery;

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
	 * Property to get or set action bar visibility.
	 */
	public static final PropertyKey<Boolean> PROP_IS_ACTION_BAR_VISIBLE = new PropertyKey<>("IsActionBarVisible", Boolean.class, GalleryFragment.class, false);
	/**
	 * Property to get or set whether {@link #ACTION_ID_BACK} action is needed or not.
	 */
	public static final PropertyKey<Boolean> PROP_IS_BACK_ACTION_NEEDED = new PropertyKey<>("IsBackActionNeeded", Boolean.class, GalleryFragment.class, true);
	
	
	/**
	 * Raised after clicking action item.
	 */
	public static final EventKey<ActionItemEventArgs> EVENT_ACTION_ITEM_CLICKED = new EventKey<>("ActionItemClicked", ActionItemEventArgs.class, GalleryFragment.class);
}
