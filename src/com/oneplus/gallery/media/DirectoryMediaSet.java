package com.oneplus.gallery.media;

import android.provider.MediaStore.Files.FileColumns;

import com.oneplus.base.PropertyKey;
import com.oneplus.io.Path;

/**
 * Media set based-on specific directory.
 */
public class DirectoryMediaSet extends MediaStoreMediaSet
{
	// Fields.
	private final int m_Id;
	
	
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
		this.setQueryCondition(FileColumns.PARENT + "=?", Integer.toString(id));
	}
	
	
	/**
	 * Get directory ID in media store.
	 * @return Directory ID.
	 */
	public final int getDirectoryId()
	{
		return m_Id;
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
