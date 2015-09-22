package com.oneplus.gallery;

import android.os.Bundle;

import com.oneplus.base.BaseActivity;

/**
 * Gallery activity.
 */
public class GalleryActivity extends BaseActivity
{
	// Called when creating.
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		// call super
		super.onCreate(savedInstanceState);
		
		// setup content view
		this.setContentView(R.layout.activity_gallery);
	}
}
