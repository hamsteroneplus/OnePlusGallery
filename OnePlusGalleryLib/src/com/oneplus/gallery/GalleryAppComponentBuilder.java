package com.oneplus.gallery;

import com.oneplus.base.component.Component;
import com.oneplus.base.component.ComponentBuilder;
import com.oneplus.base.component.ComponentCreationPriority;

/**
 * Base class of gallery application component builder.
 */
public abstract class GalleryAppComponentBuilder implements ComponentBuilder
{
	// Fields.
	private final Class<?> m_ComponentType;
	private final ComponentCreationPriority m_Priority;
	
	
	/**
	 * Initialize new GalleryAppComponentBuilder instance.
	 * @param componentType Type of component.
	 */
	protected GalleryAppComponentBuilder(Class<?> componentType)
	{
		this(ComponentCreationPriority.NORMAL, componentType);
	}
	
	
	/**
	 * Initialize new GalleryAppComponentBuilder instance.
	 * @param priority Component creation priority.
	 * @param componentType Type of component.
	 */
	protected GalleryAppComponentBuilder(ComponentCreationPriority priority, Class<?> componentType)
	{
		if(priority == null)
			throw new IllegalArgumentException("No creation priority.");
		if(componentType == null)
			throw new IllegalArgumentException("No component type.");
		m_Priority = priority;
		m_ComponentType = componentType;
	}
	
	
	// Create component.
	@Override
	public Component create(Object... args)
	{
		if(args.length == 1 && args[0] instanceof GalleryApplication)
			return this.create((GalleryApplication)args[0]);
		return null;
	}
	
	
	/**
	 * Create component.
	 * @param application Gallery application.
	 * @return Created component.
	 */
	protected abstract Component create(GalleryApplication application);
	
	
	// Get creation priority.
	@Override
	public ComponentCreationPriority getPriority()
	{
		return m_Priority;
	}
	
	
	// Check component type.
	@Override
	public boolean isComponentTypeSupported(Class<?> componentType)
	{
		return (componentType != null && componentType.isAssignableFrom(m_ComponentType));
	}
}
