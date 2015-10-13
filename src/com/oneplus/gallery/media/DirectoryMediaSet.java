package com.oneplus.gallery.media;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Files.FileColumns;
import java.io.File;

import com.oneplus.base.Log;
import com.oneplus.base.PropertyKey;
import com.oneplus.database.CursorUtils;
import com.oneplus.io.Path;

/**
 * Media set based-on specific directory.
 */
public class DirectoryMediaSet extends MediaStoreMediaSet
{
	// Fields.
	private final int m_Id;
	private final String m_DirectoryPath;
	
	
	/**
	 * Initialize new DirectoryMediaSet instance.
	 * @param directoryPath Directory path.
	 * @param id Directory ID in media store.
	 */
	public DirectoryMediaSet(String directoryPath, int id)
	{
		// call super
		super(Type.APPLICATION, true);
		
		// setup name
		super.set(PROP_NAME, Path.getFileName(directoryPath));
		
		// setup query condition
		m_Id = id;
		m_DirectoryPath = directoryPath;
		this.setQueryCondition(FileColumns.PARENT + "=?", Integer.toString(id));
	}
		
	
	@Override
	protected boolean delete(ContentResolver contentResolver, Uri contentUri, ContentProviderClient client) throws RemoteException {

		// delete image/video files whichs are in this directory
		Log.v(TAG, "delete() - remove all files in the directory");
		client.delete(contentUri, FileColumns.PARENT + "=? AND ("+ FileColumns.MEDIA_TYPE + "=" + FileColumns.MEDIA_TYPE_IMAGE
				+ " OR " + FileColumns.MEDIA_TYPE + "=" + FileColumns.MEDIA_TYPE_VIDEO+")", new String[] {Integer.toString(m_Id)});
		
		// get directory path
		String directoryPath = "";
		Cursor cursor = client.query(contentUri, MediaStoreMedia.MEDIA_COLUMNS, FileColumns._ID+"= ?", new String[] {Integer.toString(m_Id)}, null);
		
		try{
			
			if(cursor.moveToFirst()){	
				directoryPath = CursorUtils.getString(cursor, MediaColumns.DATA);
			}
		}
		catch (Throwable tr) {
			Log.e(TAG, "delete() - failed to get directory path",tr);
		}finally{
			cursor.close();
		}
		Log.v(TAG, "delete() - directoryPath is "+directoryPath);
		
		// try to delete directory
		File directory = new File(directoryPath);
		
		if(!directory.exists())
		{
			Log.w(TAG, "delete() - directory is not existed");
			return false;
		}
		
		if(!directory.isDirectory())
		{
			Log.w(TAG, "delete() - directory is not legal");
			return false;
		}
		
		if(directory.delete())
		{
			// remove data from content provider
			Log.v(TAG, "delete() - remove the directory");
			client.delete(contentUri, "_id = ?", new String[] {Integer.toString(m_Id)});
		}
		else
		{
			Log.w(TAG, "delete() - directory is not empty");
		}
		
		return true;
	}


	/**
	 * Get directory ID in media store.
	 * @return Directory ID.
	 */
	public final int getDirectoryId()
	{
		return m_Id;
	}
	
	/**
	 * Get directory path in media store.
	 * @return Directory path.
	 */
	public final String getDirectoryPath()
	{
		return m_DirectoryPath;
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
