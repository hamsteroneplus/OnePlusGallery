package com.oneplus.gallery.providers;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

public class GalleryMediaProvider extends ContentProvider 
{
	// Constants
	private static final String TAG = GalleryMediaProvider.class.getSimpleName();
	
	
	// Delete
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) 
	{
		return GalleryDatabaseManager.delete(uri, selection, selectionArgs);
	}
	

	// Get type
	@Override
	public String getType(Uri uri)
	{
		// TODO Auto-generated method stub
		return null;
	}

	
	// Insert
	@Override
	public Uri insert(Uri uri, ContentValues values) 
	{
		return GalleryDatabaseManager.insert(uri, values);
	}

	
	// Call when onCreate
	@Override
	public boolean onCreate()
	{
		return true;
	}

	
	// Query
	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
	{
		return GalleryDatabaseManager.query(uri, projection, selection, selectionArgs, sortOrder);
	}

	
	// Update
	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) 
	{
		return GalleryDatabaseManager.update(uri, values, selection, selectionArgs);
	}

}
