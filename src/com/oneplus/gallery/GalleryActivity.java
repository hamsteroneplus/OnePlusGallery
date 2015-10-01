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

import com.oneplus.base.BaseActivity;
import com.oneplus.base.EventHandler;
import com.oneplus.base.EventKey;
import com.oneplus.base.EventSource;
import com.oneplus.base.Log;
import com.oneplus.base.ScreenSize;
import com.oneplus.gallery.media.Media;
import com.oneplus.gallery.media.MediaComparator;
import com.oneplus.gallery.media.MediaList;
import com.oneplus.gallery.media.MediaManager;
import com.oneplus.gallery.media.MediaSet;
import com.oneplus.gallery.media.MediaSetList;

/**
 * Gallery activity.
 */
public class GalleryActivity extends BaseActivity
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
	private ViewPager m_EntryViewPager;
	private View m_FilmstripContainer;
	private FilmstripFragment m_FilmstripFragment;
	private View m_GridViewContainer;
	private GridViewFragment m_GridViewFragment;
	private MediaList m_MediaList;
	private MediaSetList m_MediaSetList;
	private MediaSetListFragment m_MediaSetListFragment;
	private Mode m_Mode = Mode.ENTRY;
	
	
	// Event handlers.
	private final EventHandler<ListItemEventArgs<Media>> m_GridViewMediaClickedHandler = new EventHandler<ListItemEventArgs<Media>>()
	{
		@Override
		public void onEventReceived(EventSource source, EventKey<ListItemEventArgs<Media>> key, ListItemEventArgs<Media> e)
		{
			onMediaClickedInGridView(e);
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
	@SuppressWarnings("incomplete-switch")
	private boolean changeMode(Mode mode)
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
					m_GridViewContainer.setTranslationY(screenSize.getHeight());
					m_GridViewContainer.animate().translationY(0).alpha(1).setDuration(300).start();
				}
				break;
				
			case FILMSTRIP:
				if(m_FilmstripContainer == null)
				{
					Log.e(TAG, "changeMode() - No filmstrip container");
					return false;
				}
				m_FilmstripContainer.setVisibility(View.VISIBLE);
				m_FilmstripContainer.setAlpha(0f);
				m_FilmstripContainer.animate().alpha(1).setDuration(300).start();
				break;
		}
		
		// exit previous mode
		switch(prevMode)
		{
			case GRID_VIEW:
				if(m_GridViewContainer != null && mode == Mode.ENTRY)
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
				break;
				
			case FILMSTRIP:
				if(m_FilmstripContainer != null)
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
		
		// call super
		super.onDestroy();
	}
	
	
	// Called after creating filmstrip fragment.
	private void onFilmstripFragmentReady(FilmstripFragment fragment)
	{
		Log.v(TAG, "onFilmstripFragmentReady()");
		
		// attach
		//
	}
	
	
	// Called after creating grid view fragment.
	private void onGridViewFragmentReady(GridViewFragment fragment)
	{
		Log.v(TAG, "onGridViewFragmentReady()");
		
		// attach
		fragment.addHandler(GridViewFragment.EVENT_MEDIA_CLICKED, m_GridViewMediaClickedHandler);
	}
	
	
	// Called after clicking media in grid view.
	private void onMediaClickedInGridView(ListItemEventArgs<Media> e)
	{
		// check state
		int index = e.getIndex();
		MediaList mediaList;
		if(m_Mode == Mode.GRID_VIEW)
			mediaList = m_GridViewFragment.get(GridViewFragment.PROP_MEDIA_LIST);
		else
			mediaList = m_DefaultMediaList;
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
		
		// show default media set
		m_EntryViewPager.setCurrentItem(0, false);
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
	
	
	// Called when starting.
	@Override
	protected void onStart()
	{
		Log.d(TAG, "onStart");
		// call super
		super.onStart();
		
		// setup media set list
		this.setupMediaSetList();
		
		// HomeBtn and resume need set medialist for gridviewfragment, otherwise girdview show nothing
		if(m_DefaultGridViewFragment != null && m_DefaultMediaSet != null) {
			m_DefaultMediaList = m_DefaultMediaSet.openMediaList(MediaComparator.TAKEN_TIME, -1, 0);
			m_DefaultGridViewFragment.set(GridViewFragment.PROP_IS_CAMERA_ROLL, true);
			m_DefaultGridViewFragment.set(GridViewFragment.PROP_MEDIA_LIST, m_DefaultMediaList);
		}
	}
	
	
	// Called when stopping.
	@Override
	protected void onStop()
	{
		// release media set list
		this.releaseMediaSetList();
		
		// call super
		super.onStop();
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
		
		Log.w(GalleryActivity.class.getSimpleName(), "releaseSharedMediaSetList()");
		
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
		
	}
	
	
	// Setup UI.
	private void setupUI()
	{
		// setup content view
		this.setContentView(R.layout.activity_gallery);
		
		// find views
		m_GridViewContainer = this.findViewById(R.id.grid_view_container);
		m_FilmstripContainer = this.findViewById(R.id.filmstrip_container);
		
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
	}
}
