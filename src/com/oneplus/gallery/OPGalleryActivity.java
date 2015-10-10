package com.oneplus.gallery;

import java.util.Map;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.oneplus.base.EventHandler;
import com.oneplus.base.EventKey;
import com.oneplus.base.EventSource;
import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.base.PropertyChangeEventArgs;
import com.oneplus.base.PropertyChangedCallback;
import com.oneplus.base.PropertyKey;
import com.oneplus.base.PropertySource;
import com.oneplus.base.ScreenSize;
import com.oneplus.gallery.media.Media;
import com.oneplus.gallery.media.MediaComparator;
import com.oneplus.gallery.media.MediaList;
import com.oneplus.gallery.media.MediaManager;
import com.oneplus.gallery.media.MediaSet;
import com.oneplus.gallery.media.MediaSetList;
import com.oneplus.gallery.widget.ViewPager;
import com.oneplus.widget.ViewUtils;

/**
 * Gallery activity.
 */
public class OPGalleryActivity extends GalleryActivity
{
	// Constants.
	private static final String FRAGMENT_TAG_DEFAULT_GRID_VIEW = "GalleryActivity.DefaultGridView";
	private static final String FRAGMENT_TAG_FILMSTRIP = "GalleryActivity.Filmstrip";
	private static final String FRAGMENT_TAG_GRID_VIEW = "GalleryActivity.GridView";
	private static final String FRAGMENT_TAG_MEDIA_SET_LIST = "GalleryActivity.MediaSetList";
	private static final String STATE_KEY_PREFIX = (OPGalleryActivity.class.getName() + ".");
	private static final String STATE_KEY_MODE = (STATE_KEY_PREFIX + "Mode");
	private static final String STATE_KEY_MEDIA_SET_LIST = (STATE_KEY_PREFIX + "MediaSetList");
	private static final String STATE_KEY_DEFAULT_MEDIA_LIST = (STATE_KEY_PREFIX + "DefaultMediaList");
	private static final String STATE_KEY_MEDIA_LIST = (STATE_KEY_PREFIX + "MediaList");
	private static final long DURATION_RELEASE_MEDIA_SET_LIST_DELAY = 3000;
	private static final long DURATION_FRAGMENT_ENTER_ANIMATION = 300;
	private static final long DURATION_FRAGMENT_EXIT_ANIMATION = 300;
	
	
	// Static fields.
	private static MediaSetList m_SharedMediaSetList;
	private static int m_SharedMediaSetListRefCount;
	private static final Runnable m_ReleaseSharedMediaSetListRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			releaseSharedMediaSetList();
		}
	};
	
	
	// Fields.
	private GridViewFragment m_DefaultGridViewFragment;
	private MediaList m_DefaultMediaList;
	private MediaSet m_DefaultMediaSet;
	private View m_EntryPageContainer;
	private ViewGroup m_EntryPageTabContainer;
	private ViewPager m_EntryViewPager;
	private View m_FilmstripContainer;
	private FilmstripFragment m_FilmstripFragment;
	private View m_GridViewContainer;
	private GridViewFragment m_GridViewFragment;
	private boolean m_IsFilmstripFragmentAdded;
	private boolean m_IsGridViewFragmentAdded;
	private boolean m_IsInstanceStateSaved;
	private MediaList m_MediaList;
	private Handle m_MediaManagerActivateHandle;
	private MediaSetList m_MediaSetList;
	private MediaSetListFragment m_MediaSetListFragment;
	private Mode m_Mode = Mode.ENTRY;
	
	
	// Event handlers.
	private final EventHandler<ListItemEventArgs<Media>> m_GridViewMediaClickedHandler = new EventHandler<ListItemEventArgs<Media>>()
	{
		@Override
		public void onEventReceived(EventSource source, EventKey<ListItemEventArgs<Media>> key, ListItemEventArgs<Media> e)
		{
			onMediaClickedInGridView(e, source == m_DefaultGridViewFragment);
		}
	};
	private final EventHandler<ListChangeEventArgs> m_MediaRemovedFromMediaListHandler = new EventHandler<ListChangeEventArgs>()
	{
		@Override
		public void onEventReceived(EventSource source, EventKey<ListChangeEventArgs> key, ListChangeEventArgs e)
		{
			onMediaRemovedFromMediaList((MediaList)source, e);
		}
	};
	private final EventHandler<ListChangeEventArgs> m_MediaSetAddedHandler = new EventHandler<ListChangeEventArgs>()
	{
		@Override
		public void onEventReceived(EventSource source, EventKey<ListChangeEventArgs> key, ListChangeEventArgs e)
		{
			onMediaSetAdded(e);
		}
	};
	private final EventHandler<ListItemEventArgs<MediaSet>> m_MediaSetClickedHandler = new EventHandler<ListItemEventArgs<MediaSet>>()
	{
		@Override
		public void onEventReceived(EventSource source, EventKey<ListItemEventArgs<MediaSet>> key, ListItemEventArgs<MediaSet> e)
		{
			onMediaSetClicked(e);
		}
	};
	private final EventHandler<ListChangeEventArgs> m_MediaSetRemovedHandler = new EventHandler<ListChangeEventArgs>()
	{
		@Override
		public void onEventReceived(EventSource source, EventKey<ListChangeEventArgs> key, ListChangeEventArgs e)
		{
			onMediaSetRemoved(e);
		}
	};
	
	
	// Call-backs.
	private final PropertyChangedCallback<Boolean> m_IsSelectionModeChangedCallback = new PropertyChangedCallback<Boolean>()
	{
		@Override
		public void onPropertyChanged(PropertySource source, PropertyKey<Boolean> key, PropertyChangeEventArgs<Boolean> e)
		{
			if(source == m_DefaultGridViewFragment)
				onDefaultGridViewSelectionStateChanged(e.getNewValue());
			else if(source == m_MediaSetListFragment)
				onMediaSetListSelectionStateChanged(e.getNewValue());
		}
	};
	
	
	// Represent activity mode.
	private enum Mode
	{
		ENTRY,
		GRID_VIEW,
		FILMSTRIP,
	}
	
	
	// Attach to media list.
	private void attachToMediaList(MediaList mediaList)
	{
		if(mediaList != null)
			mediaList.addHandler(MediaList.EVENT_MEDIA_REMOVED, m_MediaRemovedFromMediaListHandler);
	}
	
	
	// Change current mode.
	private boolean changeMode(Mode mode)
	{
		return this.changeMode(mode, true);
	}
	@SuppressWarnings("incomplete-switch")
	private boolean changeMode(Mode mode, boolean animate)
	{
		// check state
		Mode prevMode = m_Mode;
		if(prevMode == mode)
			return true;
		
		Log.v(TAG, "changeMode() - Change mode from ", prevMode, " to ", mode);
		
		// enter new mode
		switch(mode)
		{
			case GRID_VIEW:
				if(!this.openGridView(animate && prevMode != Mode.FILMSTRIP))
					return false;
				break;
				
			case FILMSTRIP:
				if(!this.openFilmstrip(animate))
					return false;
				this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
				break;
		}
		
		// exit previous mode
		switch(prevMode)
		{
			case GRID_VIEW:
				if(mode == Mode.ENTRY)
					this.closeGridView(animate);
				break;
				
			case FILMSTRIP:
				this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
				this.closeFilmstrip(animate);
				if(mode == Mode.ENTRY)
					this.closeGridView(animate);
				break;
		}
		
		// complete
		m_Mode = mode;
		return true;
	}
	
	
	// Close filmstrip.
	private void closeFilmstrip(boolean animate)
	{
		// check state
		if(m_FilmstripContainer == null || m_FilmstripContainer.getVisibility() != View.VISIBLE)
			return;
		
		// show status bar
		if(!this.get(PROP_IS_STATUS_BAR_VISIBLE))
			this.setStatusBarVisibility(true, FLAG_CANCELABLE);
		
		// close
		if(animate)
		{
			m_FilmstripContainer.animate().alpha(0).setDuration(DURATION_FRAGMENT_EXIT_ANIMATION).withEndAction(new Runnable()
			{
				@Override
				public void run()
				{
					onFilmstripClosed();
				}
			}).start();
		}
		else
			this.onFilmstripClosed();
	}
	
	
	// Close grid view
	private void closeGridView(boolean animate)
	{
		// check state
		if(m_GridViewContainer == null || m_GridViewContainer.getVisibility() != View.VISIBLE)
			return;
		
		// close
		if(animate)
		{
			ScreenSize screenSize = this.get(PROP_SCREEN_SIZE);
			m_GridViewContainer.animate().translationY(screenSize.getHeight()).alpha(0).setDuration(DURATION_FRAGMENT_EXIT_ANIMATION).withEndAction(new Runnable()
			{
				@Override
				public void run()
				{
					onGridViewClosed();
				}
			}).start();
		}
		else
			this.onGridViewClosed();
	}
	
	
	// Detach from media list.
	private void detachFromMediaList(MediaList mediaList)
	{
		if(mediaList != null)
			mediaList.removeHandler(MediaList.EVENT_MEDIA_REMOVED, m_MediaRemovedFromMediaListHandler);
	}
	
	
	// Go to previous state.
	@Override
	public boolean goBack()
	{
		switch(m_Mode)
		{
			case GRID_VIEW:
				this.changeMode(Mode.ENTRY);
				return true;
				
			case FILMSTRIP:
			{
				MediaList mediaList = m_FilmstripFragment.get(FilmstripFragment.PROP_MEDIA_LIST);
				if(mediaList != m_DefaultMediaList)
					this.changeMode(Mode.GRID_VIEW);
				else
					this.changeMode(Mode.ENTRY);
				return true;
			}
				
			default:
				return super.goBack();
		}
	}
	
	
	// Called when pressing back button.
	@SuppressWarnings("incomplete-switch")
	@Override
	public void onBackPressed()
	{
		switch(m_Mode)
		{
			case ENTRY:
				if(m_DefaultGridViewFragment != null && m_DefaultGridViewFragment.get(GridViewFragment.PROP_IS_SELECTION_MODE))
				{
					m_DefaultGridViewFragment.set(GridViewFragment.PROP_IS_SELECTION_MODE, false);
					return;
				}
				if(m_MediaSetListFragment != null && m_MediaSetListFragment.get(MediaSetListFragment.PROP_IS_SELECTION_MODE))
				{
					m_MediaSetListFragment.set(MediaSetListFragment.PROP_IS_SELECTION_MODE, false);
					return;
				}
				break;
				
			case GRID_VIEW:
				if(m_GridViewFragment != null && m_GridViewFragment.get(GridViewFragment.PROP_IS_SELECTION_MODE))
				{
					m_GridViewFragment.set(GridViewFragment.PROP_IS_SELECTION_MODE, false);
					return;
				}
				break;
		}
		super.onBackPressed();
	}
	
	
	// Called when creating.
	@Override
	protected void onCreate(Bundle savedInstanceState, Map<String, Object> tempInstanceState)
	{
		// call super
		super.onCreate(savedInstanceState, tempInstanceState);

		// setup media set list
		this.setupMediaSetList();
		
		// check saved state
		boolean isValidTempState = (tempInstanceState != null);
		Mode savedMode = null;
		MediaSetList savedMediaSetList = null;
		MediaList savedDefaultMediaList = null;
		MediaList savedMediaList = null;
		if(isValidTempState)
		{
			// get saved states
			savedMode = (Mode)tempInstanceState.get(STATE_KEY_MODE);
			savedMediaSetList = (MediaSetList)tempInstanceState.get(STATE_KEY_MEDIA_SET_LIST);
			savedDefaultMediaList = (MediaList)tempInstanceState.get(STATE_KEY_DEFAULT_MEDIA_LIST);
			savedMediaList = (MediaList)tempInstanceState.get(STATE_KEY_MEDIA_LIST);
			
			// validate saved states
			isValidTempState = (savedMode != null
					&& savedMediaSetList == m_MediaSetList
			);
			
			// use of release saved states
			if(isValidTempState)
			{
				m_DefaultMediaList = savedDefaultMediaList;
				m_MediaList = savedMediaList;
				if(m_MediaList != m_DefaultMediaList)
					this.attachToMediaList(m_MediaList);
			}
			else
			{
				if(savedDefaultMediaList != null)
					savedDefaultMediaList.release();
				if(savedMediaList != null)
					savedMediaList.release();
			}
		}
		
		// setup UI
		this.setupUI();
		
		// restore mode
		if(isValidTempState)
		{
			switch(savedMode)
			{
				case GRID_VIEW:
					this.changeMode(Mode.GRID_VIEW, false);
					break;
				case FILMSTRIP:
					if(m_MediaList != null && m_MediaList != m_DefaultMediaList)
						this.changeMode(Mode.GRID_VIEW, false);
					this.changeMode(Mode.FILMSTRIP, false);
					break;
				default:
					break;
			}
		}
	}
	
	
	// Called after creating default grid view fragment.
	private void onDefaultGridViewFragmentReady(GridViewFragment fragment)
	{
		Log.v(TAG, "onDefaultGridViewFragmentReady()");
		
		// initialize
		fragment.set(GridViewFragment.PROP_HAS_ACTION_BAR, false);
		
		// attach
		fragment.addCallback(GridViewFragment.PROP_IS_SELECTION_MODE, m_IsSelectionModeChangedCallback);
		fragment.addHandler(GridViewFragment.EVENT_MEDIA_CLICKED, m_GridViewMediaClickedHandler);
		
		// open default media list
		if(m_DefaultMediaList == null)
		{
			if(m_DefaultMediaSet == null)
				Log.w(TAG, "onDefaultGridViewFragmentReady() - Default media set is not ready");
			else
			{
				Log.v(TAG, "onDefaultGridViewFragmentReady() - Open default media list");
				m_DefaultMediaList = m_DefaultMediaSet.openMediaList(MediaComparator.TAKEN_TIME, -1, 0);
			}
		}
		
		// show default media list
		fragment.set(GridViewFragment.PROP_IS_CAMERA_ROLL, true);
		fragment.set(GridViewFragment.PROP_MEDIA_LIST, m_DefaultMediaList);
	}
	
	
	// Called when selection state changed in default grid view.
	private void onDefaultGridViewSelectionStateChanged(boolean isSelectionMode)
	{
		if(isSelectionMode)
		{
			m_EntryPageTabContainer.setVisibility(View.GONE);
			m_EntryViewPager.lockPosition();
		}
		else
		{
			m_EntryPageTabContainer.setVisibility(View.VISIBLE);
			m_EntryViewPager.unlockPosition();
		}
	}
	
	
	// Called when destroying.
	protected void onDestroy() 
	{
		// detach from fragment
		if(m_DefaultGridViewFragment != null)
		{
			m_DefaultGridViewFragment.removeCallback(GridViewFragment.PROP_IS_SELECTION_MODE, m_IsSelectionModeChangedCallback);
			m_DefaultGridViewFragment.removeHandler(GridViewFragment.EVENT_MEDIA_CLICKED, m_GridViewMediaClickedHandler);
		}
		if(m_GridViewFragment != null)
			m_GridViewFragment.removeHandler(GridViewFragment.EVENT_MEDIA_CLICKED, m_GridViewMediaClickedHandler);
		if(m_MediaSetListFragment != null)
		{
			m_MediaSetListFragment.removeCallback(MediaSetListFragment.PROP_IS_SELECTION_MODE, m_IsSelectionModeChangedCallback);
			m_MediaSetListFragment.removeHandler(MediaSetListFragment.EVENT_MEDIA_SET_CLICKED, m_MediaSetClickedHandler);
		}
		
		// release media set list
		this.releaseMediaSetList();
		
		// call super
		super.onDestroy();
	}
	
	
	// Called when entry view page selected.
	private void onEntryViewPagerPageSelected(int position)
	{
		for(int i = m_EntryPageTabContainer.getChildCount() - 1 ; i >= 0 ; --i)
		{
			TextView tab = (TextView)m_EntryPageTabContainer.getChildAt(i);
			tab.setTextAppearance(this , position == i ? R.style.EntryPageTabText_Selected : R.style.EntryPageTabText);
		}
	}
	
	
	// Called when filmstrip is closed completely.
	private void onFilmstripClosed()
	{
		m_FilmstripContainer.setVisibility(View.GONE);
		m_FilmstripFragment.backToInitialUIState();
		m_FilmstripFragment.set(FilmstripFragment.PROP_MEDIA_LIST, null);
		if(!m_FilmstripFragment.isDetached())
		{
			if(this.get(PROP_STATE) != State.DESTROYED)
				this.getFragmentManager().beginTransaction().detach(m_FilmstripFragment).commitAllowingStateLoss();
			else
				Log.w(TAG, "onFilmstripClosed() - Activity has been destroyed, no need to detach fragment");
		}
	}
	
	
	// Called after creating filmstrip fragment.
	private void onFilmstripFragmentReady(FilmstripFragment fragment)
	{
		Log.v(TAG, "onFilmstripFragmentReady()");
		
		// initialize
		fragment.set(FilmstripFragment.PROP_HAS_ACTION_BAR, false);
		
		// attach
		//
	}
	
	
	// Called when grid view closed completely.
	private void onGridViewClosed()
	{
		m_GridViewContainer.setVisibility(View.GONE);
		m_GridViewFragment.backToInitialUIState();
		m_GridViewFragment.set(GridViewFragment.PROP_IS_SELECTION_MODE, false);
		m_GridViewFragment.set(GridViewFragment.PROP_MEDIA_LIST, null);
		if(!m_GridViewFragment.isDetached())
		{
			if(this.get(PROP_STATE) != State.DESTROYED)
				this.getFragmentManager().beginTransaction().detach(m_GridViewFragment).commitAllowingStateLoss();
			else
				Log.w(TAG, "onGridViewClosed() - Activity has been destroyed, no need to detach fragment");
		}
		if(m_MediaList != null && m_MediaList != m_DefaultMediaList)
		{
			this.detachFromMediaList(m_MediaList);
			m_MediaList.release();
			m_MediaList = null;
		}
	}
	
	
	// Called after creating grid view fragment.
	private void onGridViewFragmentReady(GridViewFragment fragment)
	{
		Log.v(TAG, "onGridViewFragmentReady()");
		
		// initialize
		fragment.set(GridViewFragment.PROP_HAS_ACTION_BAR, true);
		
		// attach
		fragment.addHandler(GridViewFragment.EVENT_MEDIA_CLICKED, m_GridViewMediaClickedHandler);
	}
	
	
	// Called after clicking media in grid view.
	private void onMediaClickedInGridView(ListItemEventArgs<Media> e, boolean isDefaultGridView)
	{
		// check state
		int index = e.getIndex();
		MediaList mediaList = (isDefaultGridView ? m_DefaultMediaList : m_MediaList);
		if(mediaList == null)
		{
			Log.e(TAG, "onMediaClickedInGridView() - No media list");
			return;
		}
		if(index < 0 || index >= mediaList.size())
		{
			Log.e(TAG, "onMediaClickedInGridView() - Invalid media index : " + index);
			return;
		}
		if(m_FilmstripFragment == null)
		{
			Log.e(TAG, "onMediaClickedInGridView() - No filmstrip fragment");
			return;
		}
		
		// show media
		m_FilmstripFragment.set(FilmstripFragment.PROP_MEDIA_LIST, mediaList);
		m_FilmstripFragment.set(FilmstripFragment.PROP_CURRENT_MEDIA_INDEX, index);
		if(this.changeMode(Mode.FILMSTRIP))
			m_FilmstripFragment.backToInitialUIState();
		else
		{
			Log.e(TAG, "onMediaClickedInGridView() - Fail to change mode");
			m_FilmstripFragment.set(FilmstripFragment.PROP_MEDIA_LIST, null);
		}
	}
	
	
	// Called when media removed from media list.
	private void onMediaRemovedFromMediaList(MediaList mediaList, ListChangeEventArgs e)
	{
		if(m_MediaList == mediaList && m_MediaList.isEmpty() && m_Mode != Mode.ENTRY)
		{
			Log.w(TAG, "onMediaRemovedFromMediaList() - Media list is empty, change to ENTRY mode");
			this.changeMode(Mode.ENTRY);
		}
	}
	
	
	// Called when media set added.
	private void onMediaSetAdded(ListChangeEventArgs e)
	{
		//
	}
	
	
	// Called after clicking media set.
	private void onMediaSetClicked(ListItemEventArgs<MediaSet> e)
	{
		// close current media list
		if(m_MediaList != null && m_MediaList != m_DefaultMediaList)
		{
			this.detachFromMediaList(m_MediaList);
			m_MediaList.release();
			m_MediaList = null;
		}
		
		// open media list
		MediaSet set = e.getItem();
		if(set != m_DefaultMediaSet)
		{
			m_MediaList = set.openMediaList(MediaComparator.TAKEN_TIME, -1, 0);
			this.attachToMediaList(m_MediaList);
		}
		else
			m_MediaList = m_DefaultMediaList;
		
		// show grid view
		if(set != m_DefaultMediaSet)
		{
			if(m_GridViewFragment == null)
			{
				Log.e(TAG, "onMediaSetClicked() - No grid view fragment");
				if(m_MediaList != null && m_MediaList != m_DefaultMediaList)
				{
					this.detachFromMediaList(m_MediaList);
					m_MediaList.release();
					m_MediaList = null;
				}
				return;
			}
			m_GridViewFragment.set(GridViewFragment.PROP_TITLE, set.get(MediaSet.PROP_NAME));
			m_GridViewFragment.set(GridViewFragment.PROP_MEDIA_LIST, m_MediaList);
			m_GridViewFragment.backToInitialUIState();
			this.changeMode(Mode.GRID_VIEW);
		}
		else if(m_DefaultGridViewFragment != null)
		{
			m_DefaultGridViewFragment.set(GridViewFragment.PROP_MEDIA_LIST, m_MediaList);
			m_EntryViewPager.setCurrentItem(0, true);
		}
	}
	
	
	// Called after creating media set list fragment.
	private void onMediaSetListFragmentReady(MediaSetListFragment fragment)
	{
		Log.v(TAG, "onMediaSetListFragmentReady()");
		
		// initialize
		fragment.set(MediaSetListFragment.PROP_HAS_ACTION_BAR, false);
		
		// attach
		fragment.addCallback(MediaSetListFragment.PROP_IS_SELECTION_MODE, m_IsSelectionModeChangedCallback);
		fragment.addHandler(MediaSetListFragment.EVENT_MEDIA_SET_CLICKED, m_MediaSetClickedHandler);
		
		// set media set list
		fragment.set(MediaSetListFragment.PROP_MEDIA_SET_LIST, m_MediaSetList);
	}
	
	
	// Called when selection state changed in media set list.
	private void onMediaSetListSelectionStateChanged(boolean isSelectionMode)
	{
		if(isSelectionMode)
		{
			m_EntryPageTabContainer.setVisibility(View.GONE);
			m_EntryViewPager.lockPosition();
		}
		else
		{
			m_EntryPageTabContainer.setVisibility(View.VISIBLE);
			m_EntryViewPager.unlockPosition();
		}
	}
	
	
	// Called when media set removed.
	private void onMediaSetRemoved(ListChangeEventArgs e)
	{
		//
	}
	
	
	// Called when re-launch.
	@Override
	protected void onNewIntent(Intent intent)
	{
		// call super
		super.onNewIntent(intent);
		
		// reset current mode
		this.changeMode(Mode.ENTRY, false);
		
		// reset UI state
		if(m_DefaultGridViewFragment != null)
			m_DefaultGridViewFragment.backToInitialUIState();
		if(m_MediaSetListFragment != null)
			m_MediaSetListFragment.backToInitialUIState();
		
		// show default media set
		m_EntryViewPager.setCurrentItem(0, false);
	}
	
	
	// Called when resuming.
	@Override
	protected void onResume()
	{
		// call super
		super.onResume();
		
		// activate media manager
		if(!Handle.isValid(m_MediaManagerActivateHandle))
			m_MediaManagerActivateHandle = MediaManager.activate();
	}
	
	
	// Called when pausing.
	@Override
	protected void onPause()
	{
		// deactivate media manager
		m_MediaManagerActivateHandle = Handle.close(m_MediaManagerActivateHandle);
		
		// update state
		m_IsInstanceStateSaved = false;
		
		// call super
		super.onPause();
	}
	
	
	// Called when saving instance state.
	@Override
	protected void onSaveInstanceState(Bundle outState, Map<String, Object> tempOutState)
	{
		// save current mode
		tempOutState.put(STATE_KEY_MODE, m_Mode);
		
		// save media set list
		tempOutState.put(STATE_KEY_MEDIA_SET_LIST, m_MediaSetList);
		
		// save media lists
		tempOutState.put(STATE_KEY_DEFAULT_MEDIA_LIST, m_DefaultMediaList);
		tempOutState.put(STATE_KEY_MEDIA_LIST, m_MediaList);
		
		// call super
		super.onSaveInstanceState(outState, tempOutState);
		
		// update state
		m_IsInstanceStateSaved = true;
	}
	
	
	// Called when status bar visibility changed.
	@Override
	protected void onStatusBarVisibilityChanged(boolean isVisible)
	{
		super.onStatusBarVisibilityChanged(isVisible);
		this.updateUIMargins(isVisible);
	}
	
	
	// Open filmstrip.
	private boolean openFilmstrip(boolean animate)
	{
		// check state
		if(m_FilmstripFragment == null)
			return false;
		
		// find views
		if(m_FilmstripContainer == null)
		{
			m_FilmstripContainer = this.findViewById(R.id.filmstrip_container);
			if(m_FilmstripContainer == null)
				return false;
		}
		
		// attach fragment
		if(!m_IsFilmstripFragmentAdded)
		{
			this.getFragmentManager().beginTransaction().add(R.id.filmstrip_fragment_container, m_FilmstripFragment, FRAGMENT_TAG_FILMSTRIP).commit();
			m_IsFilmstripFragmentAdded = true;
		}
		else if(m_FilmstripFragment.isDetached())
			this.getFragmentManager().beginTransaction().attach(m_FilmstripFragment).commit();
		
		// open
		m_FilmstripContainer.setVisibility(View.VISIBLE);
		m_FilmstripContainer.requestFocus();
		if(animate)
		{
			m_FilmstripContainer.setAlpha(0f);
			m_FilmstripContainer.animate().alpha(1).setDuration(DURATION_FRAGMENT_ENTER_ANIMATION).start();
		}
		else
			m_FilmstripContainer.setAlpha(1f);
		
		// complete
		return true;
	}
	
	
	// Open grid view.
	private boolean openGridView(boolean animate)
	{
		// check state
		if(m_GridViewFragment == null)
			return false;
		
		// find views
		if(m_GridViewContainer == null)
		{
			m_GridViewContainer = this.findViewById(R.id.grid_view_container);
			if(m_GridViewContainer == null)
				return false;
		}
		
		// attach fragment
		if(!m_IsGridViewFragmentAdded)
		{
			this.getFragmentManager().beginTransaction().add(R.id.grid_view_fragment_container, m_GridViewFragment, FRAGMENT_TAG_GRID_VIEW).commit();
			m_IsGridViewFragmentAdded = true;
		}
		else if(m_GridViewFragment.isDetached())
			this.getFragmentManager().beginTransaction().attach(m_GridViewFragment).commit();
		
		// open
		ScreenSize screenSize = this.get(PROP_SCREEN_SIZE);
		m_GridViewContainer.setVisibility(View.VISIBLE);
		m_GridViewContainer.requestFocus();
		if(animate)
		{
			m_GridViewContainer.setTranslationY(screenSize.getHeight());
			m_GridViewContainer.animate().translationY(0).alpha(1).setDuration(DURATION_FRAGMENT_ENTER_ANIMATION).start();
		}
		else
		{
			m_GridViewContainer.setTranslationY(0);
			m_GridViewContainer.setAlpha(1f);
		}
		
		// complete
		return true;
	}
	
	
	// Release media set list.
	private void releaseMediaSetList()
	{
		// check state
		if(m_MediaSetList == null)
			return;
		
		// remove event handlers
		this.detachFromMediaList(m_MediaList);
		m_MediaSetList.removeHandler(MediaSetList.EVENT_MEDIA_SET_ADDED, m_MediaSetAddedHandler);
		m_MediaSetList.removeHandler(MediaSetList.EVENT_MEDIA_SET_REMOVED, m_MediaSetRemovedHandler);
		
		// release later
		--m_SharedMediaSetListRefCount;
		if(m_SharedMediaSetListRefCount <= 0)
		{
			Log.v(TAG, "releaseMediaSetList() - Release shared list later");
			GalleryApplication.current().getHandler().postDelayed(m_ReleaseSharedMediaSetListRunnable, DURATION_RELEASE_MEDIA_SET_LIST_DELAY);
		}
		
		// close media lists
		if(!m_IsInstanceStateSaved)
		{
			if(m_DefaultMediaList != null)
			{
				m_DefaultMediaList.release();
				m_DefaultMediaList = null;
			}
			if(m_MediaList != null)
			{
				m_MediaList.release();
				m_MediaList = null;
			}
		}
		
		// clear references
		m_MediaSetList = null;
		m_DefaultMediaSet = null;
	}
	
	
	// Release shared media set list.
	private static void releaseSharedMediaSetList()
	{
		// check state
		if(m_SharedMediaSetList == null)
			return;
		
		Log.w(OPGalleryActivity.class.getSimpleName(), "releaseSharedMediaSetList()");
		
		m_SharedMediaSetList.release();
		m_SharedMediaSetList = null;
		m_SharedMediaSetListRefCount = 0;
	}
	
	
	// Setup media set list.
	private void setupMediaSetList()
	{
		// check state
		if(m_MediaSetList != null)
			return;
		
		// cancel releasing
		GalleryApplication.current().getHandler().removeCallbacks(m_ReleaseSharedMediaSetListRunnable);
		
		// create list
		if(m_SharedMediaSetList != null)
			Log.v(TAG, "setupMediaSetList() - Use existent list");
		else
		{
			Log.v(TAG, "setupMediaSetList() - Create new list");
			m_SharedMediaSetList = MediaManager.createMediaSetList();
		}
		m_MediaSetList = m_SharedMediaSetList;
		++m_SharedMediaSetListRefCount;
		
		// find default set
		m_DefaultMediaSet = (m_MediaSetList.isEmpty() ? null : m_MediaSetList.get(0));
		if(m_DefaultMediaSet == null)
			Log.w(TAG, "setupMediaSetList() - No default set");
		
		// show media set list
		if(m_MediaSetListFragment != null)
			m_MediaSetListFragment.set(MediaSetListFragment.PROP_MEDIA_SET_LIST, m_MediaSetList);
	}
	
	
	// Setup UI.
	private void setupUI()
	{
		// setup content view
		this.setContentView(R.layout.activity_gallery);
		
		// find views
		m_EntryPageContainer = this.findViewById(R.id.entry_page_container);
		m_EntryPageTabContainer = (ViewGroup)m_EntryPageContainer.findViewById(R.id.entry_page_tab_container);
		m_GridViewContainer = this.findViewById(R.id.grid_view_container);
		
		// setup margins
		this.updateUIMargins(this.get(PROP_IS_STATUS_BAR_VISIBLE));
		
		// create fragments
		FragmentManager fragmentManager = this.getFragmentManager();
		m_DefaultGridViewFragment = (GridViewFragment)fragmentManager.findFragmentByTag(FRAGMENT_TAG_DEFAULT_GRID_VIEW);
		m_MediaSetListFragment = (MediaSetListFragment)fragmentManager.findFragmentByTag(FRAGMENT_TAG_MEDIA_SET_LIST);
		m_GridViewFragment = (GridViewFragment)fragmentManager.findFragmentByTag(FRAGMENT_TAG_GRID_VIEW);
		m_FilmstripFragment = (FilmstripFragment)fragmentManager.findFragmentByTag(FRAGMENT_TAG_FILMSTRIP);
		if(m_DefaultGridViewFragment != null)
			this.onDefaultGridViewFragmentReady(m_DefaultGridViewFragment);
		if(m_MediaSetListFragment != null)
			this.onMediaSetListFragmentReady(m_MediaSetListFragment);
		if(m_GridViewFragment == null)
			m_GridViewFragment = new GridViewFragment();
		else
			m_IsGridViewFragmentAdded = true;
		if(m_FilmstripFragment == null)
			m_FilmstripFragment = new FilmstripFragment();
		else
			m_IsFilmstripFragmentAdded = true;
		this.onGridViewFragmentReady(m_GridViewFragment);
		this.onFilmstripFragmentReady(m_FilmstripFragment);
		
		// prepare entry view pager
		m_EntryViewPager = (ViewPager)this.findViewById(R.id.entry_view_pager);
		m_EntryViewPager.setPageMargin(this.getResources().getDimensionPixelSize(R.dimen.entry_page_margin));
		m_EntryViewPager.setOverScrollMode(View.OVER_SCROLL_NEVER);
		m_EntryViewPager.setAdapter(new PagerAdapter()
		{
			private FragmentTransaction m_FragmentTransaction;
			
			@Override
			public void destroyItem(ViewGroup container, int position, Object object)
			{
				if(m_FragmentTransaction == null)
					m_FragmentTransaction = getFragmentManager().beginTransaction();
				m_FragmentTransaction.detach((Fragment)object);
			}
			
			@Override
			public void finishUpdate(ViewGroup container)
			{
				if(m_FragmentTransaction != null)
				{
					m_FragmentTransaction.commitAllowingStateLoss();
					m_FragmentTransaction = null;
					getFragmentManager().executePendingTransactions();
				}
			}
			
			@Override
			public int getCount()
			{
				return 2;
			}
			
			@Override
			public Object instantiateItem(ViewGroup container, int position)
			{
				// begin transaction
				if(m_FragmentTransaction == null)
					m_FragmentTransaction = getFragmentManager().beginTransaction();
				
				// prepare fragment
				final Fragment fragment;
				final String tag;
				boolean newFragment = false;
				switch(position)
				{
					case 0:
						if(m_DefaultGridViewFragment == null)
						{
							m_DefaultGridViewFragment = new GridViewFragment();
							onDefaultGridViewFragmentReady(m_DefaultGridViewFragment);
							newFragment = true;
						}
						fragment = m_DefaultGridViewFragment;
						tag = FRAGMENT_TAG_DEFAULT_GRID_VIEW;
						break;
					case 1:
						if(m_MediaSetListFragment == null)
						{
							m_MediaSetListFragment = new MediaSetListFragment();
							onMediaSetListFragmentReady(m_MediaSetListFragment);
							newFragment = true;
						}
						fragment = m_MediaSetListFragment;
						tag = FRAGMENT_TAG_MEDIA_SET_LIST;
						break;
					default:
						throw new IllegalArgumentException();
				}
				if(newFragment)
					m_FragmentTransaction.add(container.getId(), fragment, tag);
				else
					m_FragmentTransaction.attach(fragment);
				return fragment;
			}

			@Override
			public boolean isViewFromObject(View view, Object object)
			{
				return ((Fragment)object).getView() == view;
			}
		});
		m_EntryViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener()
		{
			@Override
			public void onPageSelected(int position)
			{
				onEntryViewPagerPageSelected(position);
			}
			
			@Override
			public void onPageScrolled(int position, float arg1, int arg2)
			{}
			
			@Override
			public void onPageScrollStateChanged(int state)
			{}
		});
		this.onEntryViewPagerPageSelected(0);
		
		// setup entry tab control
		for(int i = m_EntryPageTabContainer.getChildCount() - 1 ; i >= 0 ; --i)
		{
			final int position = i;
			m_EntryPageTabContainer.getChildAt(i).setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					m_EntryViewPager.setCurrentItem(position, true);
				}
			});
		}
	}
	
	
	// Update UI margins according to current state.
	private void updateUIMargins(boolean isStatusBarVisible)
	{
		// check state
		if(m_EntryPageContainer == null)
			return;
		
		// update margins
		ScreenSize screenSize = this.get(PROP_SCREEN_SIZE);
		int topMargin = (isStatusBarVisible ? screenSize.getStatusBarSize() : 0);
		ViewUtils.setMargins(m_EntryPageContainer, 0, topMargin, 0, 0);
		ViewUtils.setMargins(m_GridViewContainer, 0, topMargin, 0, 0);
	}
}
