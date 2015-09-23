package com.oneplus.gallery.media;

import android.os.Environment;
import android.provider.MediaStore.Files.FileColumns;
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
		this.setReadOnly(PROP_NAME, GalleryApplication.current().getString(R.string.media_set_name_camera_roll));
		
		// setup query condition
		this.setQueryCondition(FileColumns.DATA + " LIKE ?", new String[]{ Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/Camera/%" });
	}
}
