package com.oneplus.gallery.media;

import android.location.Location;

/**
 * Detailed media information.
 */
public interface MediaDetails
{
	/**
	 * Key of information item.
	 * @param <T> Type of item value.
	 */
	public static class Key<T>
	{
		/**
		 * Name of key.
		 */
		public final String name;
		
		/**
		 * Initialize new Key instance.
		 * @param name Name of key.
		 */
		public Key(String name)
		{
			this.name = name;
		}
		
		// Get readable string.
		@Override
		public String toString()
		{
			return this.name;
		}
	}
	
	
	/**
	 * Get item.
	 * @param key Key of item.
	 * @param defaultValue Default value if item is not found.
	 * @return Item value, or default value if item is not found.
	 */
	<T> T get(Key<T> key, T defaultValue);
}
