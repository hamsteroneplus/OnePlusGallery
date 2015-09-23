package com.oneplus.gallery.media;

import android.os.Environment;
import android.provider.MediaStore.Files.FileColumns;

import com.oneplus.base.PropertyKey;
import com.oneplus.gallery.GalleryApplication;
import com.oneplus.gallery.R;

/**
 * Camera roll.
 */
public class CameraRollMediaSet extends MediaStoreMediaSet
{
	/**
	 * Initialize new CameraRollMediaSet instance.
	 */
	public CameraRollMediaSet()
	{
		// call super
		super(Type.SYSTEM);
		
		// setup name
		super.set(PROP_NAME, GalleryApplication.current().getString(R.string.media_set_name_camera_roll));
		
		// setup query condition
		this.setQueryCondition(FileColumns.DATA + " LIKE ?", new String[]{ Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/%" });
	}
	
	
	// Set property.
	@Override
	public <TValue> boolean set(PropertyKey<TValue> key, TValue value)
	{
		if(key == PROP_NAME)
			throw new IllegalAccessError("Cannot change name.");
		return super.set(key, value);
	}
}
