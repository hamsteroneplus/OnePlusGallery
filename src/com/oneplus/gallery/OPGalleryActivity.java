package com.oneplus.gallery;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.oneplus.base.EventHandler;
import com.oneplus.base.EventKey;
import com.oneplus.base.EventSource;
import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.base.ScreenSize;
import com.oneplus.gallery.media.Media;
import com.oneplus.gallery.media.MediaComparator;
import com.oneplus.gallery.media.MediaList;
import com.oneplus.gallery.media.MediaManager;
import com.oneplus.gallery.media.MediaSet;
import com.oneplus.gallery.media.MediaSetList;
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
	private static final long DURATION_RELEASE_MEDIA_SET_LIST_DELAY = 3000;
	
	
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
	
	
	// Represent activity mode.
	private enum Mode
	{
		ENTRY,
		GRID_VIEW,
		FILMSTRIP,
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
		ScreenSize screenSize = new ScreenSize(this, false);
		switch(mode)
		{
			case GRID_VIEW:
				if(m_GridViewContainer == null)
				{
					Log.e(TAG, "changeMode() - No grid view container");
					return false;
				}
				if(prevMode != Mode.FILMSTRIP)
				{
					m_GridViewContainer.setVisibility(View.VISIBLE);
					if(animate)
					{
						m_GridViewContainer.setTranslationY(screenSize.getHeight());
						m_GridViewContainer.animate().translationY(0).alpha(1).setDuration(300).start();
					}
					else
					{
						m_GridViewContainer.setTranslationY(0);
						m_GridViewContainer.setAlpha(1f);
					}
				}
				break;
				
			case FILMSTRIP:
				if(m_FilmstripContainer == null)
				{
					Log.e(TAG, "changeMode() - No filmstrip container");
					return false;
				}
				m_FilmstripContainer.setVisibility(View.VISIBLE);
				if(animate)
				{
					m_FilmstripContainer.setAlpha(0f);
					m_FilmstripContainer.animate().alpha(1).setDuration(300).start();
				}
				else
				{
					m_FilmstripContainer.setAlpha(1f);
				}
				break;
		}
		
		// exit previous mode
		switch(prevMode)
		{
			case GRID_VIEW:
				if(m_GridViewContainer != null && mode == Mode.ENTRY)
				{
					if(animate)
					{
						m_GridViewContainer.animate().translationY(screenSize.getHeight()).alpha(0).setDuration(300).withEndAction(new Runnable()
						{
							@Override
							public void run()
							{
								m_GridViewContainer.setVisibility(View.GONE);
								m_GridViewFragment.set(GridViewFragment.PROP_MEDIA_LIST, null);
							}
						}).start();
					}
					else
					{
						m_GridViewContainer.setVisibility(View.GONE);
						m_GridViewFragment.set(GridViewFragment.PROP_MEDIA_LIST, null);
					}
				}
				break;
				
			case FILMSTRIP:
				if(m_FilmstripContainer != null)
				{
					if(animate)
					{
						m_FilmstripContainer.animate().alpha(0).setDuration(300).withEndAction(new Runnable()
						{
							@Override
							public void run()
							{
								m_FilmstripContainer.setVisibility(View.GONE);
								m_FilmstripFragment.set(FilmstripFragment.PROP_MEDIA_LIST, null);
							}
						}).start();
					}
					else
					{
						m_FilmstripContainer.setVisibility(View.GONE);
						m_FilmstripFragment.set(FilmstripFragment.PROP_MEDIA_LIST, null);
					}
				}
				break;
		}
		
		// complete
		m_Mode = mode;
		return true;
	}
	
	
	// Called when back button pressed.
	@Override
	public void onBackPressed()
	{
		switch(m_Mode)
		{
			case GRID_VIEW:
				this.changeMode(Mode.ENTRY);
				break;
				
			case FILMSTRIP:
			{
				MediaList mediaList = m_FilmstripFragment.get(FilmstripFragment.PROP_MEDIA_LIST);
				if(mediaList != m_DefaultMediaList)
					this.changeMode(Mode.GRID_VIEW);
				else
					this.changeMode(Mode.ENTRY);
				break;
			}
				
			default:
				super.onBackPressed();
				break;
		}
	}
	
	
	// Called when creating.
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		// call super
		super.onCreate(savedInstanceState);
		
		// setup media set list
		this.setupMediaSetList();
		
		// setup UI
		this.setupUI();
	}
	
	
	// Called after creating default grid view fragment.
	private void onDefaultGridViewFragmentReady(GridViewFragment fragment)
	{
		Log.v(TAG, "onDefaultGridViewFragmentReady()");
		
		// initialize
		fragment.set(GridViewFragment.PROP_HAS_ACTION_BAR, false);
		
		// attach
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
	
	
	// Called when destroying.
	protected void onDestroy() 
	{
		// detach from fragment
		if(m_DefaultGridViewFragment != null)
			m_DefaultGridViewFragment.removeHandler(GridViewFragment.EVENT_MEDIA_CLICKED, m_GridViewMediaClickedHandler);
		if(m_GridViewFragment != null)
			m_GridViewFragment.removeHandler(GridViewFragment.EVENT_MEDIA_CLICKED, m_GridViewMediaClickedHandler);
		if(m_MediaSetListFragment != null)
			m_MediaSetListFragment.removeHandler(MediaSetListFragment.EVENT_MEDIA_SET_CLICKED, m_MediaSetClickedHandler);
		
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
	
	
	// Called after creating filmstrip fragment.
	private void onFilmstripFragmentReady(FilmstripFragment fragment)
	{
		Log.v(TAG, "onFilmstripFragmentReady()");
		
		// initialize
		fragment.set(FilmstripFragment.PROP_HAS_ACTION_BAR, false);
		
		// attach
		//
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
		if(!this.changeMode(Mode.FILMSTRIP))
		{
			Log.e(TAG, "onMediaClickedInGridView() - Fail to change mode");
			m_FilmstripFragment.set(FilmstripFragment.PROP_MEDIA_LIST, null);
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
			m_MediaList.release();
			m_MediaList = null;
		}
		
		// open media list
		MediaSet set = e.getItem();
		if(set != m_DefaultMediaSet)
			m_MediaList = set.openMediaList(MediaComparator.TAKEN_TIME, -1, 0);
		else
			m_MediaList = m_DefaultMediaList;
		
		// show grid view
		if(set != m_DefaultMediaSet)
		{
			if(m_GridViewFragment == null)
			{
				Log.e(TAG, "onMediaSetClicked() - No grid view fragment");
				return;
			}
			m_GridViewFragment.set(GridViewFragment.PROP_MEDIA_LIST, m_MediaList);
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
		fragment.addHandler(MediaSetListFragment.EVENT_MEDIA_SET_CLICKED, m_MediaSetClickedHandler);
		
		// set media set list
		fragment.set(MediaSetListFragment.PROP_MEDIA_SET_LIST, m_MediaSetList);
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
		
		// call super
		super.onPause();
	}
	
	
	// Called when saving instance state.
	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		// detach fragments
		FragmentTransaction fragmentTransaction = this.getFragmentManager().beginTransaction();
		if(m_GridViewFragment != null)
			fragmentTransaction.detach(m_GridViewFragment);
		if(m_FilmstripFragment != null)
			fragmentTransaction.detach(m_FilmstripFragment);
		fragmentTransaction.commit();
		
		// call super
		super.onSaveInstanceState(outState);
	}
	
	
	// Called when status bar visibility changed.
	@Override
	protected void onStatusBarVisibilityChanged(boolean isVisible)
	{
		super.onStatusBarVisibilityChanged(isVisible);
		this.updateUIMargins(isVisible);
	}
	
	
	// Release media set list.
	private void releaseMediaSetList()
	{
		// check state
		if(m_MediaSetList == null)
			return;
		
		// remove event handlers
		m_MediaSetList.removeHandler(MediaSetList.EVENT_MEDIA_SET_ADDED, m_MediaSetAddedHandler);
		m_MediaSetList.removeHandler(MediaSetList.EVENT_MEDIA_SET_REMOVED, m_MediaSetRemovedHandler);
		
		// release later
		--m_SharedMediaSetListRefCount;
		if(m_SharedMediaSetListRefCount <= 0)
		{
			Log.v(TAG, "releaseMediaSetList() - Release shared list later");
			GalleryApplication.current().getHandler().postDelayed(m_ReleaseSharedMediaSetListRunnable, DURATION_RELEASE_MEDIA_SET_LIST_DELAY);
		}
		
		// close default media list
		if(m_DefaultMediaList != null)
		{
			m_DefaultMediaList.release();
			m_DefaultMediaList = null;
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
		m_FilmstripContainer = this.findViewById(R.id.filmstrip_container);
		
		// setup margins
		this.updateUIMargins(this.get(PROP_IS_STATUS_BAR_VISIBLE));
		
		// create fragments
		FragmentManager fragmentManager = this.getFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
		m_DefaultGridViewFragment = (GridViewFragment)fragmentManager.findFragmentByTag(FRAGMENT_TAG_DEFAULT_GRID_VIEW);
		m_MediaSetListFragment = (MediaSetListFragment)fragmentManager.findFragmentByTag(FRAGMENT_TAG_MEDIA_SET_LIST);
		m_GridViewFragment = (GridViewFragment)fragmentManager.findFragmentByTag(FRAGMENT_TAG_GRID_VIEW);
		m_FilmstripFragment = (FilmstripFragment)fragmentManager.findFragmentByTag(FRAGMENT_TAG_FILMSTRIP);
		if(m_DefaultGridViewFragment != null)
			this.onDefaultGridViewFragmentReady(m_DefaultGridViewFragment);
		if(m_MediaSetListFragment != null)
			this.onMediaSetListFragmentReady(m_MediaSetListFragment);
		if(m_GridViewFragment != null)
			fragmentTransaction.attach(m_GridViewFragment);
		else
		{
			m_GridViewFragment = new GridViewFragment();
			fragmentTransaction.add(R.id.grid_view_fragment_container, m_GridViewFragment, FRAGMENT_TAG_GRID_VIEW);
		}
		if(m_FilmstripFragment != null)
			fragmentTransaction.attach(m_FilmstripFragment);
		else
		{
			m_FilmstripFragment = new FilmstripFragment();
			fragmentTransaction.add(R.id.filmstrip_fragment_container, m_FilmstripFragment, FRAGMENT_TAG_FILMSTRIP);
		}
		this.onGridViewFragmentReady(m_GridViewFragment);
		this.onFilmstripFragmentReady(m_FilmstripFragment);
		fragmentTransaction.commit();
		
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
