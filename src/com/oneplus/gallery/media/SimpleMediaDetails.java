package com.oneplus.gallery.media;

import java.util.Map;

/**
 * Simple implementation of {@link MediaDetails}.
 */
public class SimpleMediaDetails implements MediaDetails
{
	// Fields.
	private final Map<Key<?>, Object> m_Values;
	
	
	/**
	 * Initialize new SimpleMediaDetails instance.
	 * @param values Item values.
	 */
	public SimpleMediaDetails(Map<Key<?>, Object> values)
	{
		m_Values = values;
	}


	// Get item value.
	@SuppressWarnings("unchecked")
	@Override
	public <T> T get(Key<T> key, T defaultValue)
	{
		if(key != null && m_Values != null)
		{
			Object value = m_Values.get(key);
			if(value != null)
			{
				try
				{
					return (T)value;
				}
				catch(ClassCastException ex)
				{}
			}
		}
		return defaultValue;
	}
}
