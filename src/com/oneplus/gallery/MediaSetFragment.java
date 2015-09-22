package com.oneplus.gallery;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.oneplus.base.BaseFragment;

/**
 * Fragment to display media in media set.
 */
public class MediaSetFragment extends BaseFragment
{
	/**
	 * Initialize new MediaSetFragment instance.
	 */
	public MediaSetFragment()
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
