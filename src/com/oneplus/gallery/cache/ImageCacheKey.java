package com.oneplus.gallery.cache;

import java.io.File;
import java.io.Serializable;

import com.oneplus.gallery.media.Media;

import android.net.Uri;

/**
 * Key to identify image in cache.
 */
public class ImageCacheKey implements Serializable
{
	/**
	 * Media content URI.
	 */
	public final Uri contentUri;
	/**
	 * Media file path.
	 */
	public final String filePath;
	/**
	 * Media file size in bytes.
	 */
	public final long fileSize;
	/**
	 * Last media modified time.
	 */
	public final long lastModifiedTime;
	
	
	// Static fields.
	private static final long serialVersionUID = 6417044270020048991L;

	
	/**
	 * Initialize new ImageCacheKey instance.
	 * @param media {@link Media} to build key.
	 */
	public ImageCacheKey(Media media)
	{
		this.filePath = media.getFilePath();
		this.fileSize = media.getFileSize();
		this.lastModifiedTime = media.getLastModifiedTime();
		this.contentUri = (this.filePath == null ? media.getContentUri() : null);
	}
	
	
	/**
	 * Initialize new ImageCacheKey instance.
	 * @param file {@link File} to build key.
	 */
	public ImageCacheKey(File file)
	{
		this.filePath = file.getAbsolutePath();
		this.fileSize = file.length();
		this.lastModifiedTime = file.lastModified();
		this.contentUri = null;
	}
	
	
	// Check equality.
	@Override
	public boolean equals(Object o)
	{
		if(o instanceof ImageCacheKey)
		{
			ImageCacheKey anotherKey = (ImageCacheKey)o;
			if(this.lastModifiedTime != anotherKey.lastModifiedTime || this.fileSize != anotherKey.fileSize)
				return false;
			if((this.filePath != null && !this.filePath.equals(anotherKey.filePath)) || (this.filePath == null && anotherKey.filePath != null))
				return false;
			if((this.contentUri != null && !this.contentUri.equals(anotherKey.contentUri)) || (this.contentUri == null && anotherKey.contentUri != null))
				return false;
			return true;
		}
		return false;
	}
	
	
	// Get hash code.
	@Override
	public int hashCode()
	{
		int hashCode = (int)(this.lastModifiedTime & 0xFFFFFFFFL);
		if(this.filePath != null)
			hashCode |= this.filePath.hashCode();
		else if(this.contentUri != null)
			hashCode |= this.contentUri.hashCode();
		return hashCode;
	}
	
	
	// Get readable string.
	@Override
	public String toString()
	{
		if(this.filePath != null)
			return ("[" + this.filePath + " ]");
		if(this.contentUri != null)
			return ("[" + this.contentUri + " ]");
		return ("[LMT=" + this.lastModifiedTime + "]");
	}
}
