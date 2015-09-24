package com.oneplus.gallery;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
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
	private static final String FRAGMENT_TAG_DEFAULT_MEDIA_LIST = "GalleryActivity.DefaultMediaList";
	private static final String FRAGMENT_TAG_MEDIA_LIST = "GalleryActivity.MediaList";
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
	private MediaList m_DefaultMediaList;
	private MediaSetFragment m_DefaultMediaListFragment;
	private MediaSet m_DefaultMediaSet;
	private ViewPager m_EntryViewPager;
	private MediaSetFragment m_MediaListFragment;
	private MediaSetList m_MediaSetList;
	private MediaSetListFragment m_MediaSetListFragment;
	
	
	// Event handlers.
	private final EventHandler<ListChangeEventArgs> m_MediaSetAddedHandler = new EventHandler<ListChangeEventArgs>()
	{
		@Override
		public void onEventReceived(EventSource source, EventKey<ListChangeEventArgs> key, ListChangeEventArgs e)
		{
			onMediaSetAdded(e);
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
	
	
	// Called when creating.
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		// call super
		super.onCreate(savedInstanceState);
		
		// setup UI
		this.setupUI();
	}
	
	
	// Called after creating default media list fragment.
	private void onDefaultMediaListFragmentReady(MediaSetFragment fragment)
	{
		Log.v(TAG, "onDefaultMediaListFragmentReady()");
		
		// open default media list
		if(m_DefaultMediaList == null)
		{
			if(m_DefaultMediaSet == null)
				Log.w(TAG, "onDefaultMediaListFragmentReady() - Default media set is not ready");
			else
			{
				Log.v(TAG, "onDefaultMediaListFragmentReady() - Open default media list");
				m_DefaultMediaList = m_DefaultMediaSet.openMediaList(MediaComparator.TAKEN_TIME, -1, 0);
			}
		}
		
		// show default media list
		fragment.set(MediaSetFragment.PROP_MEDIALIST, m_DefaultMediaList);
	}
	
	
	// Called when destroying.
	protected void onDestroy() 
	{
		// call super
		super.onDestroy();
	}
	
	
	// Called after creating media list fragment.
	private void onMediaListFragmentReady(MediaSetFragment fragment)
	{
		Log.v(TAG, "onMediaListFragmentReady()");
	}
	
	
	// Called when media set added.
	private void onMediaSetAdded(ListChangeEventArgs e)
	{
		//
	}
	
	
	// Called after creating media set list fragment.
	private void onMediaSetListFragmentReady(MediaSetListFragment fragment)
	{
		Log.v(TAG, "onMediaSetListFragmentReady()");
		
		// set media set list
		fragment.set(MediaSetListFragment.PROP_MEDIA_SET_LIST, m_MediaSetList);
	}
	
	
	// Called when media set removed.
	private void onMediaSetRemoved(ListChangeEventArgs e)
	{
		//
	}
	
	
	// Called when starting.
	@Override
	protected void onStart()
	{
		// call super
		super.onStart();
		
		// setup media set list
		this.setupMediaSetList();
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
		
		// add event handlers
		m_MediaSetList.addHandler(MediaSetList.EVENT_MEDIA_SET_ADDED, m_MediaSetAddedHandler);
		m_MediaSetList.addHandler(MediaSetList.EVENT_MEDIA_SET_REMOVED, m_MediaSetRemovedHandler);
	}
	
	
	// Setup UI.
	private void setupUI()
	{
		// setup content view
		this.setContentView(R.layout.activity_gallery);
		
		// find existent fragment
		FragmentManager fragmentManager = this.getFragmentManager();
		m_DefaultMediaListFragment = (MediaSetFragment)fragmentManager.findFragmentByTag(FRAGMENT_TAG_DEFAULT_MEDIA_LIST);
		m_MediaSetListFragment = (MediaSetListFragment)fragmentManager.findFragmentByTag(FRAGMENT_TAG_MEDIA_SET_LIST);
		m_MediaListFragment = (MediaSetFragment)fragmentManager.findFragmentByTag(FRAGMENT_TAG_MEDIA_LIST);
		if(m_DefaultMediaListFragment != null)
			this.onDefaultMediaListFragmentReady(m_DefaultMediaListFragment);
		if(m_MediaSetListFragment != null)
			this.onMediaSetListFragmentReady(m_MediaSetListFragment);
		if(m_MediaListFragment != null)
			this.onMediaListFragmentReady(m_MediaListFragment);
		
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
						if(m_DefaultMediaListFragment == null)
						{
							m_DefaultMediaListFragment = new MediaSetFragment();
							onDefaultMediaListFragmentReady(m_DefaultMediaListFragment);
							newFragment = true;
						}
						fragment = m_DefaultMediaListFragment;
						tag = FRAGMENT_TAG_DEFAULT_MEDIA_LIST;
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
