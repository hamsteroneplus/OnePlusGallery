package com.oneplus.gallery;

import android.app.Fragment;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;

import com.oneplus.base.BaseActivity;
import com.oneplus.gallery.media.CameraRollMediaSet;
import com.oneplus.gallery.media.MediaComparator;
import com.oneplus.gallery.media.MediaList;
import com.oneplus.gallery.media.MediaSet;

/**
 * Gallery activity.
 */
public class GalleryActivity extends BaseActivity
{
	// Fields.
	private ViewPager m_EntryViewPager;
	
	
	// Create fragment for displaying camera roll.
	private MediaSetFragment createCameraRollFragment()
	{
		return new MediaSetFragment();
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
		
		// setup UI
		this.setupUI();
	}
	MediaSet m_MediaSet;
	
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
		
		m_MediaSet = new CameraRollMediaSet();
		MediaList list = m_MediaSet.openMediaList(MediaComparator.TAKEN_TIME, -1, 0);
	}
}
