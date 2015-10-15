package com.oneplus.gallery.media;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Environment;
import android.os.RemoteException;
import android.provider.MediaStore.Files.FileColumns;

import com.oneplus.base.Log;
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
		super(Type.SYSTEM, true);
		
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


	@Override
	protected boolean delete(ContentResolver contentResolver, Uri contentUri, ContentProviderClient client) throws RemoteException {
		
		Log.v(TAG, "delete() - remove all files in "+Environment.DIRECTORY_DCIM);
		client.delete(contentUri, FileColumns.DATA + " LIKE ?", new String[] {Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/%"});
		
		return true;
	}


	@Override
	protected void onDeleted() {
		
		// notify MediaManager
		MediaManager.notifyMediaSetDeleted(CameraRollMediaSet.this);
		
		// reset media count
		setReadOnly(PROP_MEDIA_COUNT, 0);
	}
	
	
}
