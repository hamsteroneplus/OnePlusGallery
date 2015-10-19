package com.oneplus.gallery.providers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.oneplus.base.Log;
import com.oneplus.gallery.GalleryApplication;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.MediaStore.MediaColumns;

public class GalleryDatabaseManager 
{
	// Constants
	private static final int VERSION_DATABASE = 6;
	private static final String TAG = GalleryDatabaseManager.class.getSimpleName();
	private static final boolean DEBUG_LOG = true;
	private static final Object SYNC_OBJ = new Object();
	
	// DB info
	private static final String AUTHORITY = "oneplus.gallery";
	private static final String COLUMN_ALBUM_ID = "album_id";
	private static final String COLUMN_DISPLAY_NAME = MediaColumns.DISPLAY_NAME;
	private static final String COLUMN_ID = MediaColumns._ID;
	private static final String COLUMN_MEDIA_ID = "media_id";
	private static final String COLUMN_ONEPLUS_FLAGS = "oneplus_flags";
	private static final String DATABASE_NAME = "gallery.db";
	private static final String INDEX_ALBUM_ID = "album_id_index";
	private static final String INDEX_MEDIA_ID = "media_id_index";
	private static final String SELECTION_ID = COLUMN_ID + "=?";
	private static final String SELECTION_MEDIA_ID = COLUMN_MEDIA_ID + "=?";
	private static final String TABLE_ALBUM_MEDIA = "album_media";
	private static final String[] TABLE_ALUBM_MEDIA_COLUMNS = new String[] { COLUMN_ALBUM_ID, COLUMN_MEDIA_ID };
	private static final String TABLE_ALBUM = "album";
	private static final String[] TABLE_ALBUM_COLUMNS = new String[] { COLUMN_ID, COLUMN_DISPLAY_NAME };
	private static final String TABLE_MEDIA = "media";
	private static final String[] TABLE_MEDIA_COLUMNS = new String[] { COLUMN_MEDIA_ID, COLUMN_ONEPLUS_FLAGS };
	
	// IDs for Uri matcher
	private static final int ALBUM_MEDIA = 400;
	private static final int ALBUM = 300;
	private static final int ALBUM_ID = 301;
	private static final String CONTENT_ALBUM = "album";
	private static final String CONTENT_ALBUM_MEDIA = "album_media";
	private static final String CONTENT_MEDIA = "media";
	private static final int MEDIA = 100;
	private static final int MEDIA_ID = 101;
	private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
	
	
	// SQL
	private static final String SQL_CREATE_TABLE_ALBUM = 
			"CREATE TABLE " + TABLE_ALBUM + " (" +
			COLUMN_ID + " INTEGER PRIMARY KEY," +
			COLUMN_DISPLAY_NAME + " TEXT" +
			");";
	private static final String SQL_CREATE_TABLE_MEDIA = 
			"CREATE TABLE " + TABLE_MEDIA + " (" +
			COLUMN_MEDIA_ID + " INTEGER," +
			COLUMN_ONEPLUS_FLAGS + " INTEGER" +
			");";
	private static final String SQL_CREATE_TABLE_ALBUM_MEDIA = 
			"CREATE TABLE " + TABLE_ALBUM_MEDIA + " (" +
			COLUMN_ALBUM_ID + " INTEGER," +
			COLUMN_MEDIA_ID + " INTEGER" +
			");";
	private static final String SQL_CREATE_INDEX_ALBUM_ID = 
			"CREATE INDEX " + INDEX_ALBUM_ID + 
			" ON " + TABLE_ALBUM_MEDIA + "(" +
			COLUMN_ALBUM_ID +
			");";
	private static final String SQL_CREATE_INDEX_MEDIA_ID = 
			"CREATE INDEX " + INDEX_MEDIA_ID + 
			" ON " + TABLE_MEDIA + "(" +
			COLUMN_MEDIA_ID +
			");";
	
	
	// Add URIs
	static
	{
		URI_MATCHER.addURI(AUTHORITY, "/" + CONTENT_ALBUM, ALBUM);
		URI_MATCHER.addURI(AUTHORITY, "/" + CONTENT_ALBUM + "/#", ALBUM_ID);
		URI_MATCHER.addURI(AUTHORITY, "/" + CONTENT_MEDIA, MEDIA);
		URI_MATCHER.addURI(AUTHORITY, "/" + CONTENT_MEDIA + "/#", MEDIA_ID);
		URI_MATCHER.addURI(AUTHORITY, "/" + CONTENT_ALBUM_MEDIA, ALBUM_MEDIA);
	}
	
	
	// Fields
	private static DatabaseHelper m_DatabaseHelper;
	private static HashMap<Long, ExtraMediaInfo> m_ExtraMediaInfos;
	
	
	// DatabaseHelper
	private static final class DatabaseHelper extends SQLiteOpenHelper
	{
		private Context m_Context;
		
		// Constructor
		public DatabaseHelper(Context context, String name)
		{
			super(context, name, null, getDatabaseVersion(context));
			m_Context = context;
		}

		// Call when onCreate
		@Override
		public void onCreate(SQLiteDatabase db) 
		{
			updateDatabase(m_Context, db, 0, getDatabaseVersion(m_Context));
		}

		// Call when onUpgrade
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
		{
			updateDatabase(m_Context, db, oldVersion, newVersion);
		}
	}
	
	
	// ExtraMediaInfo
	public static final class ExtraMediaInfo implements Cloneable
	{
		public long id;
		public int oneplusFlags;
		
		public ExtraMediaInfo(long id, int oneplusFlags)
		{
			this.set(id, oneplusFlags);
		}
		
		@Override
		public ExtraMediaInfo clone()
		{
			try
			{
				return (ExtraMediaInfo)super.clone();
			}
			catch(CloneNotSupportedException e)
			{
				throw new RuntimeException();
			}
		}
		
		public void set(long id, int oneplusFlags)
		{
			this.id = id;
			this.oneplusFlags = oneplusFlags;
		}
	}
	
	
	// Add extra media info
	public static long addExtraMediaInfo(ExtraMediaInfo info)
	{
		long insertResult = -1;
		synchronized(SYNC_OBJ)
		{
			// check initialize
			if(m_ExtraMediaInfos == null)
				queryAllExtraMediaInfos();
			
			// get info
			long id = info.id;
			int oneplusFlags = info.oneplusFlags;
			ExtraMediaInfo value = m_ExtraMediaInfos.get(id);
			if(value == null)
			{
				if(DEBUG_LOG)
					Log.v(TAG, "addExtraMediaInfo() - Id: ", id, ", flags: ", oneplusFlags);
				
				// add new value
				value = new ExtraMediaInfo(id, oneplusFlags);
				m_ExtraMediaInfos.put(id, value);
				
				// insert to db
				SQLiteDatabase db = connectToExtraMediaDatabase();
				ContentValues cvs = new ContentValues();
				cvs.put(COLUMN_MEDIA_ID, id);
				cvs.put(COLUMN_ONEPLUS_FLAGS, oneplusFlags);
				insertResult = db.insert(TABLE_MEDIA, null, cvs);
				if(insertResult < 0)
					Log.e(TAG, "addExtraMediaInfo() - Insert failed");
			}			
		}
		return insertResult;
	}
	
	
	// Combine selection args
	private static String[] combineSelectionArgs(String[] a, String[] b)
	{
		int aSize = a.length;
		if (aSize == 0)
			return b;
		int bSize = (b != null) ? b.length : 0;
		String [] combined = new String[aSize + bSize];
		for (int i = 0; i < aSize; i++)
			combined[i] = a[i];
		for (int i = 0; i < bSize; i++)
			combined[aSize + i] = b[i];
		return combined;
	}
	
	
	// Connect to extra media database
	private static SQLiteDatabase connectToExtraMediaDatabase()
	{
		if(m_DatabaseHelper == null)
			m_DatabaseHelper = new DatabaseHelper(GalleryApplication.current(), DATABASE_NAME);
		
		// writable db
		return m_DatabaseHelper.getWritableDatabase();
	}
	
	
	// Delete
	public static int delete(Uri uri, String selection, String[] selectionArgs)
	{
		// check arguments
		if(uri == null)
			return 0;

		if(DEBUG_LOG)
			Log.v(TAG, "delete() - Uri: ", uri, ", selection: ", selection, ", selection args: ", Arrays.toString(selectionArgs));

		// find table matched
		int tableId = URI_MATCHER.match(uri);

		// delete
		int rowCounts = 0;
		switch(tableId)
		{
			case ALBUM:
				//TODO
				break;
				
			case ALBUM_ID:
				//TODO
				break;
				
			case ALBUM_MEDIA:
				//TODO
				break;
				
			case MEDIA:
				List<Long> ids = new ArrayList<Long>();
				Cursor cursor = query(uri, new String[] { COLUMN_MEDIA_ID }, selection, selectionArgs, null);
				while(cursor.moveToNext())
					ids.add(cursor.getLong(0));
				rowCounts = removeExtraMediaInfos(ids);
				break;
				
			case MEDIA_ID:
				long id = ContentUris.parseId(uri);
				rowCounts = removeExtraMediaInfo(id);
				break;
		}
		return rowCounts;
	}
	
	
	// Get album content URI
	public static Uri getAlbumContentUri()
	{
		return getAlbumContentUri(null);
	}
	public static Uri getAlbumContentUri(Long id)
	{
		return getContentUri(CONTENT_ALBUM, id);
	}
	
	
	// Get album media content URI
	public static Uri getAlbumMediaContentUri()
	{
		return getAlbumMediaContentUri(null);
	}
	public static Uri getAlbumMediaContentUri(Long id)
	{
		return getContentUri(CONTENT_ALBUM_MEDIA, id);
	}
	
	
	// Get content URI
	private static Uri getContentUri(String contentType, Long id)
	{
		return Uri.parse("content://" + AUTHORITY + "/" + contentType + ((id != null) ? "/" + id : ""));
	}
	
	
	// Get database version
	private static int getDatabaseVersion(Context context)
	{
		return VERSION_DATABASE;
	}
	
	
	// Get extra media info
	public static ExtraMediaInfo getExtraMediaInfo(long id)
	{
		// check initialize
		if(m_ExtraMediaInfos == null)
			queryAllExtraMediaInfos();
		
		// get info
		ExtraMediaInfo result = m_ExtraMediaInfos.get(id);
		
		// clone result
		return (result != null) ? result.clone() : null;
	}
	
	
	// Get media content URI
	public static Uri getMediaContentUri()
	{
		return getMediaContentUri(null);
	}
	public static Uri getMediaContentUri(Long id)
	{
		return getContentUri(CONTENT_MEDIA, id);
	}
	
	
	// Query
	public static Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
	{
		// check arguments
		if(uri == null || projection == null)
			return null;
		
		if(DEBUG_LOG)
			Log.v(TAG, "query() - Uri: ", uri, ", proj: ", Arrays.toString(projection), ", selection: ", selection, ", selection args: ", Arrays.toString(selectionArgs), ", order: ", sortOrder);
		
		// find table matched
		int tableId = URI_MATCHER.match(uri);
		
		// additional selection arguments
		List<String> addedArgs = new ArrayList<String>();
		
		// get db and query
		SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
		if (uri.getQueryParameter("distinct") != null)
			builder.setDistinct(true);
		switch(tableId)
		{
			case MEDIA_ID:
				// set table
				builder.setTables(TABLE_MEDIA);
				
				// append id to selection
				builder.appendWhere(SELECTION_MEDIA_ID);
				addedArgs.add(ContentUris.parseId(uri) + "");
				break;
			case MEDIA:
				// set table
				builder.setTables(TABLE_MEDIA);
				break;
		}
		
		// query from builder
		SQLiteDatabase db = connectToExtraMediaDatabase();
		String[] combinedArgs = combineSelectionArgs(addedArgs.toArray(new String[0]), selectionArgs);
		return builder.query(db, projection, selection, combinedArgs, null, null, sortOrder);
	}
	
	
	// Query all extra media infos
	private static void queryAllExtraMediaInfos()
	{
		synchronized(SYNC_OBJ)
		{
			// initialize extra infos
			if(m_ExtraMediaInfos == null)
				m_ExtraMediaInfos = new HashMap<>();
			m_ExtraMediaInfos.clear();
			
			if(DEBUG_LOG)
				Log.v(TAG, "queryAllExtraMediaInfos()");
			
			// query from db
			SQLiteDatabase db = connectToExtraMediaDatabase();
			Cursor cursor = db.query(TABLE_MEDIA, TABLE_MEDIA_COLUMNS, null, null, null, null, null);
			while(cursor.moveToNext())
			{
				long id = cursor.getLong(0);
				int oneplusFlags = cursor.getInt(1);
				ExtraMediaInfo info = new ExtraMediaInfo(id, oneplusFlags);
				m_ExtraMediaInfos.put(id, info);
			}
		}
	}
	
	
	// Insert
	public static Uri insert(Uri uri, ContentValues values) 
	{
		// check arguments
		if(uri == null || values == null)
			return null;
		
		if(DEBUG_LOG)
			Log.v(TAG, "insert() - Uri: ", uri, ", cvs: ", values.toString());
		
		// find table matched
		int tableId = URI_MATCHER.match(uri);

		// get db and insert
		switch(tableId)
		{
			case MEDIA_ID:				
			case MEDIA:
				Long id = values.getAsLong(COLUMN_MEDIA_ID);
				Integer oneplusFlags = values.getAsInteger(COLUMN_ONEPLUS_FLAGS);
				if(id == null || oneplusFlags == null)
				{
					Log.e(TAG, "insert() - Incorrect content value, id: " + id + ", flags: " + oneplusFlags);
					return null;
				}
				long rowId = addExtraMediaInfo(new ExtraMediaInfo(id, oneplusFlags));
				if(rowId < 0)
					return null;
				return ContentUris.withAppendedId(uri, rowId);
		}
		return null;
	}
	
	
	// Remove all extra media info
	public static int removeAllExtraMediaInfo()
	{
		int rowCounts = 0;
		synchronized(SYNC_OBJ)
		{
			// check initialize
			if(m_ExtraMediaInfos == null)
				queryAllExtraMediaInfos();
			
			if(DEBUG_LOG)
				Log.v(TAG, "removeAllExtraMediaInfo()");
			
			// remove value
			if(m_ExtraMediaInfos.size() > 0)
			{
				// remove all rows
				SQLiteDatabase db = connectToExtraMediaDatabase();
				rowCounts = db.delete(TABLE_MEDIA, null, null);
				if(rowCounts != m_ExtraMediaInfos.size())
					Log.w(TAG, "removeAllExtraMediaInfo() - Data is not sync: " + rowCounts);
				
				// clear media infos
				m_ExtraMediaInfos.clear();
			}
		}
		return rowCounts;
	}
	
	
	// Remove extra media info
	public static int removeExtraMediaInfo(long id)
	{
		int rowCounts = 0;
		synchronized(SYNC_OBJ)
		{
			// check initialize
			if(m_ExtraMediaInfos == null)
				queryAllExtraMediaInfos();
			
			if(DEBUG_LOG)
				Log.v(TAG, "removeExtraMediaInfo() - Id: ", id);
			
			// remove value
			ExtraMediaInfo deleteInfo = m_ExtraMediaInfos.remove(id);
			if(deleteInfo != null)
			{
				SQLiteDatabase db = connectToExtraMediaDatabase();
				rowCounts = db.delete(TABLE_MEDIA, SELECTION_MEDIA_ID, new String[] { id+"" });
				if(rowCounts != 1)
					Log.w(TAG, "removeExtraMediaInfo() - Data is not sync: " + rowCounts);
			}
		}
		return rowCounts;
	}
	
	
	// Remove extra media infos
	public static int removeExtraMediaInfos(List<Long> ids)
	{
		int rowCounts = 0;
		synchronized(SYNC_OBJ)
		{
			// check initialize
			if(m_ExtraMediaInfos == null)
				queryAllExtraMediaInfos();
			
			// remove value
			List<String> realRemovedIds = new ArrayList<String>();
			for(int i = 0 ; i < ids.size() ; i++)
			{
				long id = ids.get(i);
				ExtraMediaInfo deleteInfo = m_ExtraMediaInfos.remove(id);
				if(deleteInfo != null)
					realRemovedIds.add(id+"");
			}
			
			if(DEBUG_LOG)
				Log.v(TAG, "removeExtraMediaInfos() - Expected ids: ", ids, ", actual: ", realRemovedIds);
			
			// delete value from db
			int removedSize = realRemovedIds.size();
			if(removedSize > 0)
			{
				SQLiteDatabase db = connectToExtraMediaDatabase();
				rowCounts = db.delete(TABLE_MEDIA, SELECTION_MEDIA_ID, realRemovedIds.toArray(new String[0]));
				if(rowCounts != removedSize)
					Log.w(TAG, "removeExtraMediaInfo() - Data is not sync, expected: " + removedSize + ", actual: " + rowCounts);
			}
		}
		return rowCounts;
	}
	
	
	// Set extra media info
	public static boolean setExtraMediaInfo(ExtraMediaInfo info)
	{
		if(updateExtraMediaInfo(info) == 0 && addExtraMediaInfo(info) < 0)
			return false;
		return true;
	}
	
	
	// Update
	public static int update(Uri uri, ContentValues values, String selection, String[] selectionArgs)
	{
		// check arguments
		if(uri == null || values == null)
			return 0;
		
		if(DEBUG_LOG)
			Log.v(TAG, "update() - Uri: ", uri, ", cvs: ", values.toString(), ", selection: ", selection, ", selection args: ", Arrays.toString(selectionArgs));
		
		// find table matched
		int tableId = URI_MATCHER.match(uri);

		// get db and update
		switch(tableId)
		{
			case MEDIA_ID:				
			case MEDIA:
				long id = values.getAsLong(COLUMN_MEDIA_ID);
				int oneplusFlags = values.getAsInteger(COLUMN_ONEPLUS_FLAGS);
				return updateExtraMediaInfo(new ExtraMediaInfo(id, oneplusFlags));
		}
		return 0;
	}
	
	
	// Update database
	private static void updateDatabase(Context context, SQLiteDatabase db, int fromVersion, int toVersion)
	{
		if(DEBUG_LOG)
			Log.v(TAG, "updateDatabase() - From: ", fromVersion, ", to: ", toVersion);

		db.execSQL("DROP TABLE IF EXISTS media");
		db.execSQL("DROP TABLE IF EXISTS album");
		db.execSQL("DROP TABLE IF EXISTS album_media");
		
		// create latest tables
		db.execSQL(SQL_CREATE_TABLE_MEDIA);
		db.execSQL(SQL_CREATE_TABLE_ALBUM);
		db.execSQL(SQL_CREATE_TABLE_ALBUM_MEDIA);
		db.execSQL(SQL_CREATE_INDEX_ALBUM_ID);
		db.execSQL(SQL_CREATE_INDEX_MEDIA_ID);
	}
	
	
	// Update extra media info
	public static int updateExtraMediaInfo(ExtraMediaInfo info)
	{
		int rowCounts = 0;
		synchronized(SYNC_OBJ)
		{
			// check initialize
			if(m_ExtraMediaInfos == null)
				queryAllExtraMediaInfos();
			
			// get info
			long id = info.id;
			int oneplusFlags = info.oneplusFlags;
			ExtraMediaInfo value = m_ExtraMediaInfos.get(id);
			if(value != null)
			{
				if(DEBUG_LOG)
					Log.v(TAG, "updateExtraMediaInfo() - Id: ", id, ", flags: ", oneplusFlags);
				
				// set new value
				value.set(id, oneplusFlags);
				
				// update db
				SQLiteDatabase db = connectToExtraMediaDatabase();
				ContentValues cvs = new ContentValues();
				cvs.put(COLUMN_MEDIA_ID, id);
				cvs.put(COLUMN_ONEPLUS_FLAGS, oneplusFlags);
				rowCounts = db.update(TABLE_MEDIA, cvs, SELECTION_MEDIA_ID, new String[] { id+"" });
				if(rowCounts != 1)
					Log.w(TAG, "updateExtraMediaInfo() - Data is not sync: " + rowCounts);
			}
		}
		return rowCounts;
	}
}
