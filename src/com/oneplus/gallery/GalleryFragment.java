package com.oneplus.gallery;

import android.app.Activity;
import android.os.Bundle;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
	
	
	// Constants.
	private static final int MSG_BACK_TO_INITIAL_UI_STATE = -10000;
	
	
	// Fields.
	private GalleryActivity m_GalleryActivity;
	private boolean m_IsInitialUIStateNeeded;
	
	
	/**
	 * Initialize new GalleryFragment instance.
	 */
	protected GalleryFragment()
	{
		this.setRetainInstance(true);
	}
	
	
	/**
	 * Go back to initial UI state.
	 */
	public void backToInitialUIState()
	{
		this.verifyAccess();
		if(m_GalleryActivity != null && this.get(PROP_STATE) != State.NEW)
		{
			m_IsInitialUIStateNeeded = false;
			this.getHandler().removeMessages(MSG_BACK_TO_INITIAL_UI_STATE);
			this.onBackToInitialUIState();
		}
		else
			m_IsInitialUIStateNeeded = true;
	}
	
	
	/**
	 * Get {@link GalleryActivity} instance which this fragment attach to.
	 * @return {@link GalleryActivity}, or Null if this fragment is not attached yet.
	 */
	public final GalleryActivity getGalleryActivity()
	{
		return m_GalleryActivity;
	}
	
	
	// Handle message.
	@Override
	protected void handleMessage(Message msg)
	{
		switch(msg.what)
		{
			case MSG_BACK_TO_INITIAL_UI_STATE:
				m_IsInitialUIStateNeeded = false;
				this.onBackToInitialUIState();
				break;
				
			default:
				super.handleMessage(msg);
				break;
		}	
	}
	
	
	// Called after attaching to activity.
	@Override
	public void onAttach(Activity activity)
	{
		// call super
		super.onAttach(activity);
		
		// attach to gallery activity
		m_GalleryActivity = (GalleryActivity)activity;
		
		// back to initial UI state
		if(m_IsInitialUIStateNeeded && this.get(PROP_STATE) != State.NEW)
		{
			m_IsInitialUIStateNeeded = false;
			this.getHandler().sendMessageAtFrontOfQueue(Message.obtain(this.getHandler(), MSG_BACK_TO_INITIAL_UI_STATE));
		}
	}
	
	
	/**
	 * Called when backing to initial UI state.
	 */
	protected void onBackToInitialUIState()
	{}
	
	
	// Called when creating.
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		// call super
		super.onCreate(savedInstanceState);
		
		// back to initial UI state
		if(m_IsInitialUIStateNeeded && m_GalleryActivity != null)
		{
			m_IsInitialUIStateNeeded = false;
			this.getHandler().sendMessageAtFrontOfQueue(Message.obtain(this.getHandler(), MSG_BACK_TO_INITIAL_UI_STATE));
		}
	}
	
	
	// Called after detaching from activity.
	@Override
	public void onDetach()
	{
		// detach from gallery activity
		m_GalleryActivity = null;
		
		// cancel backing to initial UI state
		this.getHandler().removeMessages(MSG_BACK_TO_INITIAL_UI_STATE);
		
		// call super
		super.onDetach();
	}
}
