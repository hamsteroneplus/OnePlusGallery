package com.oneplus.gallery;

import com.oneplus.base.Log;

import android.app.Application;

/**
 * Application.
 */
public final class GalleryApplication extends Application
{
	// Constants.
	private static final String TAG = "OPGalleryApplication";
	
	
	// Called when launching application.
	@Override
	public void onCreate()
	{
		// call super
		super.onCreate();
		
		Log.v(TAG, "onCreate()");
	}
}
