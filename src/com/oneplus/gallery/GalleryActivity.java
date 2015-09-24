package com.oneplus.gallery;

import android.app.Fragment;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;

import com.oneplus.base.BaseActivity;
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
	// Fields.
	private ViewPager m_EntryViewPager;
	private MediaSetList m_MediaSetList;
	
	
	// Create fragment for displaying camera roll.
	private MediaSetFragment createCameraRollFragment()
	{
		Log.d(TAG, "createCameraRollFragment");
		MediaSetFragment fragment = new MediaSetFragment();
		if(m_MediaSetList != null && !m_MediaSetList.isEmpty()){
			MediaSet mediaset = m_MediaSetList.get(0);
			MediaList medialist = mediaset.openMediaList(MediaComparator.TAKEN_TIME, -1, 0);
			fragment.set(MediaSetFragment.PROP_MEDIALIST, medialist);
		}
		return fragment;
	}
	
	
	// Create fragment for displaying media set list.
	private MediaSetListFragment createMediaSetListFragment()
	{
		return new MediaSetListFragment();
	}
	
	
	// Called when creating.
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		// call super
		super.onCreate(savedInstanceState);
		
		// create media set list
		m_MediaSetList = MediaManager.createMediaSetList();
		
		// setup UI
		this.setupUI();
	}
	
	
	// Called when destroying.
	protected void onDestroy() 
	{
		// release media set list
		if(m_MediaSetList != null)
			m_MediaSetList.release();
		
		// call super
		super.onDestroy();
	}
	
	
	// Setup UI.
	private void setupUI()
	{
		// setup content view
		this.setContentView(R.layout.activity_gallery);
		
		// prepare entry view pager
		m_EntryViewPager = (ViewPager)this.findViewById(R.id.entry_view_pager);
		m_EntryViewPager.setAdapter(new FragmentPagerAdapter(this.getFragmentManager())
		{
			@Override
			public int getCount()
			{
				return 2;
			}
			
			@Override
			public Fragment getItem(int position)
			{
				switch(position)
				{
					case 0:
						return createCameraRollFragment();
					case 1:
						return createMediaSetListFragment();
					default:
						throw new IllegalArgumentException("Invalid position : " + position + ".");
				}
			}
		});
		
		
	}
}
