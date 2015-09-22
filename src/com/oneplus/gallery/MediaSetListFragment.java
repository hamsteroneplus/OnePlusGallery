package com.oneplus.gallery;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.oneplus.base.BaseFragment;

/**
 * Fragment to display media set list.
 */
public class MediaSetListFragment extends BaseFragment
{
	/**
	 * Initialize new MediaSetListFragment instance.
	 */
	public MediaSetListFragment()
	{
		this.setRetainInstance(true);
	}
	
	
	// Create view.
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		//
		TextView tv = new TextView(this.getActivity());
		tv.setText(this.TAG);
		return tv;
	}
}
