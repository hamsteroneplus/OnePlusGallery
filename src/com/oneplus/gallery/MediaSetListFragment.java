package com.oneplus.gallery;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toolbar;
import android.widget.Toolbar.OnMenuItemClickListener;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;

import com.oneplus.base.EventHandler;
import com.oneplus.base.EventKey;
import com.oneplus.base.EventSource;
import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.base.PropertyChangeEventArgs;
import com.oneplus.base.PropertyChangedCallback;
import com.oneplus.base.PropertyKey;
import com.oneplus.base.PropertySource;
import com.oneplus.gallery.media.Media;
import com.oneplus.gallery.media.MediaComparator;
import com.oneplus.gallery.media.MediaList;
import com.oneplus.gallery.media.MediaSet;
import com.oneplus.gallery.media.MediaSetList;
import com.oneplus.gallery.media.ThumbnailImageManager;
import com.oneplus.media.BitmapPool;

/**
 * Fragment to display media set list.
 */
public class MediaSetListFragment extends GalleryFragment
{
	// Fields
	private Activity m_Activity;
	private RelativeLayout m_AddAlbumButton;
	private ArrayList<Handle> m_DecodingImageHandles = new ArrayList<Handle>();
	private MediaSetListAdapter m_MediaSetListAdapter;
	private ListView m_MediaSetListView;
	private MediaSetList m_MediaSetList;
	private Hashtable<MediaSet, Object> m_MediaSetCoverImageTable = new Hashtable<>();
	private LinkedList<MediaSet> m_MediaSetDecodeQueue = new LinkedList<>();
	private ArrayList<MediaSet> m_SelectedMediaSet = new ArrayList<MediaSet>();
	private Handle m_ThumbManagerActivateHandle;
	private Toolbar m_Toolbar;
	private final PropertyChangedCallback<Integer> m_MediaCountChangedCallback = new PropertyChangedCallback<Integer>()
	{
		@Override
		public void onPropertyChanged(PropertySource source, PropertyKey<Integer> key, PropertyChangeEventArgs<Integer> e)
		{
			onMediaCountChanged((MediaSet)source, e);
		}
	};
	private final EventHandler<ListChangeEventArgs> m_MediaSetAddedHandler = new EventHandler<ListChangeEventArgs>()
	{
		@Override
		public void onEventReceived(EventSource source, EventKey<ListChangeEventArgs> key, ListChangeEventArgs e)
		{
			onMediaSetAdded(e);
		}
	};
	private final PropertyChangedCallback<String> m_MediaSetNameChangedCallback = new PropertyChangedCallback<String>()
	{
		@Override
		public void onPropertyChanged(PropertySource source, PropertyKey<String> key, PropertyChangeEventArgs<String> e)
		{
			onMediaSetNameChanged((MediaSet)source, e);
		}
	};
	private final EventHandler<ListChangeEventArgs> m_MediaSetRemovedHandler = new EventHandler<ListChangeEventArgs>()
	{
		@Override
		public void onEventReceived(EventSource source, EventKey<ListChangeEventArgs> key, ListChangeEventArgs e)
		{
			onMediaSetRemoved(e);
		}
	};
	private final EventHandler<ListChangeEventArgs> m_MediaSetRemovingHandler = new EventHandler<ListChangeEventArgs>()
	{
		@Override
		public void onEventReceived(EventSource source, EventKey<ListChangeEventArgs> key, ListChangeEventArgs e)
		{
			onMediaSetRemoving(e);
		}
	};
	private GalleryActivity.MediaSetDeletionCallback m_MediaSetDeleteCallback = new GalleryActivity.MediaSetDeletionCallback() {

		@Override
		public void onDeletionProcessCompleted() {
			super.onDeletionProcessCompleted();
			
			// leave selection mode
			set(PROP_IS_SELECTION_MODE, false);	
		}	
	};
	
	/**
	 * Property to get or set selection mode.
	 */
	public static final PropertyKey<Boolean> PROP_IS_SELECTION_MODE = new PropertyKey<>("IsSelectionMode", Boolean.class, MediaSetListFragment.class, PropertyKey.FLAG_NOT_NULL, false);
	/**
	 * Property to get or set MediaSet
	 */
	public final static PropertyKey<MediaSetList> PROP_MEDIA_SET_LIST = new PropertyKey<>("MediaSetList", MediaSetList.class, MediaSetListFragment.class, 0, null);
	
	
	/**
	 * Raised after clicking single media set.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static final EventKey<ListItemEventArgs<MediaSet>> EVENT_MEDIA_SET_CLICKED = new EventKey<ListItemEventArgs<MediaSet>>("MediaSetClicked", (Class)ListItemEventArgs.class, MediaSetListFragment.class);
		
	// Call-backs.
	private final PropertyChangedCallback<Boolean> m_IsSelectionModeChangedCallback = new PropertyChangedCallback<Boolean>()
	{
		@Override
		public void onPropertyChanged(PropertySource source, PropertyKey<Boolean> key, PropertyChangeEventArgs<Boolean> e)
		{
			onSelectionModeChanged(e.getNewValue());
		}
	};
	
	/**
	 * Initialize new MediaSetListFragment instance.
	 */
	public MediaSetListFragment()
	{
		this.setRetainInstance(true);
	}
	
	
	// Attach to media set.
	private void attachToMediaSet(MediaSet set)
	{
		set.addCallback(MediaSet.PROP_MEDIA_COUNT, m_MediaCountChangedCallback);
		set.addCallback(MediaSet.PROP_NAME, m_MediaSetNameChangedCallback);
	}
	
	
	// Schedule cover decoding.
	private void decodeMediaSetCovers()
	{
		if(m_MediaSetList != null)
		{
			for(int i = m_MediaSetList.size() - 1 ; i >= 0 ; --i)
			{
				MediaSet mediaSet = m_MediaSetList.get(i);
				if(!m_MediaSetCoverImageTable.containsKey(mediaSet))
				{
					m_MediaSetCoverImageTable.put(mediaSet, new Object());
					m_MediaSetDecodeQueue.add(mediaSet);
				}
			}
			this.createMediaListCoverImageFromQueue();
		}
	}
	
	
	// Detach from media set.
	private void detachFromMediaSet(MediaSet set)
	{
		set.removeCallback(MediaSet.PROP_MEDIA_COUNT, m_MediaCountChangedCallback);
		set.removeCallback(MediaSet.PROP_NAME, m_MediaSetNameChangedCallback);
	}
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		m_Activity =  this.getActivity();
		m_MediaSetListAdapter = new MediaSetListAdapter();
		
		addCallback(PROP_IS_SELECTION_MODE, m_IsSelectionModeChangedCallback);
	}

	// Create view.
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.fragment_media_set_list, container, false);
	}

	

	@Override
	public void onDestroy() {
		super.onDestroy();
		
		removeCallback(PROP_IS_SELECTION_MODE, m_IsSelectionModeChangedCallback);
	}
	
	
	// Called when media count changed.
	private void onMediaCountChanged(MediaSet mediaSet, PropertyChangeEventArgs<Integer> e)
	{
		Log.v(TAG, "onMediaCountChanged() - new media count : " + e.getNewValue());
		m_MediaSetDecodeQueue.add(mediaSet);
		createMediaListCoverImageFromQueue();
		
		// notify data changed
		if(m_MediaSetListAdapter != null)
			m_MediaSetListAdapter.notifyDataSetChanged();
	}
	
	
	// Called when media set added.
	private void onMediaSetAdded(ListChangeEventArgs e)
	{
		for(int i = e.getStartIndex(), end = e.getEndIndex() ; i <= end ; ++i)
			this.attachToMediaSet(m_MediaSetList.get(i));
		if(m_MediaSetListAdapter != null)
			m_MediaSetListAdapter.notifyDataSetChanged();
	}
	
	
	// Called when name of media set changed.
	private void onMediaSetNameChanged(MediaSet mediaSet, PropertyChangeEventArgs<String> e)
	{
		if(m_MediaSetListAdapter != null)
			m_MediaSetListAdapter.notifyDataSetInvalidated();
	}
	
	
	// Called when media set removed.
	private void onMediaSetRemoved(ListChangeEventArgs e)
	{
		if(m_MediaSetListAdapter != null)
			m_MediaSetListAdapter.notifyDataSetChanged();
	}
	
	
	// Called before removing media set.
	private void onMediaSetRemoving(ListChangeEventArgs e)
	{
		for(int i = e.getStartIndex(), end = e.getEndIndex() ; i <= end ; ++i)
			this.detachFromMediaSet(m_MediaSetList.get(i));
	}



	@Override
	public void onResume() {
		super.onResume();
		
		m_AddAlbumButton = (RelativeLayout)getView().findViewById(R.id.add_album_buttom);
		m_MediaSetListView = (ListView)getView().findViewById(R.id.media_set_listview);
		m_Toolbar = (Toolbar)getView().findViewById(R.id.media_set_toolbar);
		
		m_AddAlbumButton.setOnClickListener(new View.OnClickListener() {		
			@Override
			public void onClick(View v) {
				//TODO : add album function
			}
		});
		m_MediaSetListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				MediaSet set = m_MediaSetList.get(position);
				
				if(get(PROP_IS_SELECTION_MODE))
					updateSelectedMediaSet(set);		
				else
					raise(EVENT_MEDIA_SET_CLICKED, new ListItemEventArgs<MediaSet>(position, set));	
			}
		});
		m_MediaSetListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				
				// enter selection mode
				if(!get(PROP_IS_SELECTION_MODE))
					set(PROP_IS_SELECTION_MODE, true);		
				
				// update selected set
				MediaSet set = m_MediaSetList.get(position);
				updateSelectedMediaSet(set);
				
				return true;
			}
		});
		m_MediaSetListView.setAdapter(m_MediaSetListAdapter);
		
		m_Toolbar.getMenu().clear();
		m_Toolbar.inflateMenu(R.menu.media_set_toolbar_menu);
		m_Toolbar.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				switch (item.getItemId()) {
				
					case R.id.toolbar_delete:
						getGalleryActivity().deleteMediaSet(m_SelectedMediaSet, m_MediaSetDeleteCallback);
						break;
				}
				return false;
			}
		});
		m_Toolbar.setNavigationOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				m_Toolbar.setNavigationIcon(null);
				set(PROP_IS_SELECTION_MODE, false);					
			}
		});
		
		// decode mediaSetCover image
		this.decodeMediaSetCovers();
		
		onSelectionModeChanged(get(PROP_IS_SELECTION_MODE));
	}
	
	
	
	@Override
	public void onPause() {
		super.onPause();
		
		// clear decoding handles
		if(!m_DecodingImageHandles.isEmpty())
		{
			for(Handle handle : m_DecodingImageHandles)
				Handle.close(handle);
			m_DecodingImageHandles.clear();
		}
		m_MediaSetCoverImageTable.clear();
	}



	private void onSelectionModeChanged(boolean isSelectionMode)
	{
		if(isSelectionMode)
		{
			// show tool bar
			setToolBarVisibility(true);
			
			// hide add album button
			m_AddAlbumButton.setVisibility(View.INVISIBLE);
		}
		else
		{
			// clear all selection
			if(!m_SelectedMediaSet.isEmpty())
			{
				m_SelectedMediaSet.clear();
				
				if(m_MediaSetListAdapter != null)
					m_MediaSetListAdapter.notifyDataSetChanged();
			}	
			
			// hide tool bar
			setToolBarVisibility(false);
			
			// show add album button
			m_AddAlbumButton.setVisibility(View.VISIBLE);
		}	
	}
	
	// Called when started.
	@Override
	public void onStart()
	{
		// call super
		super.onStart();
		
		// activate thumbnail image manager
		if(!Handle.isValid(m_ThumbManagerActivateHandle))
			m_ThumbManagerActivateHandle = ThumbnailImageManager.activate();
	}
	
	// Called when stoped.
	@Override
	public void onStop()
	{
		// deactivate thumbnail image manager
		m_ThumbManagerActivateHandle = Handle.close(m_ThumbManagerActivateHandle);
		
		// call super
		super.onStop();
	}
	
	// Set property value.
	@SuppressWarnings("unchecked")
	@Override
	public <TValue> boolean set(PropertyKey<TValue> key, TValue value)
	{
		if(key == PROP_MEDIA_SET_LIST)
			return this.setMediaSetList((MediaSetList)value);
		
		return super.set(key, value);
	}
	
	private void setToolBarVisibility(boolean isVisible)
	{
		m_Toolbar.setNavigationIcon(R.drawable.button_cancel);
		m_Toolbar.setVisibility(isVisible ? View.VISIBLE : View.GONE);
	}
	
	private boolean setMediaSetList(MediaSetList newList) {	
		
		Log.v(TAG, "setMediaSetList()");	
		
		MediaSetList oldList = m_MediaSetList;
		m_MediaSetList = newList;
		if(oldList == newList)
			return false;
		
		// detach from previous list
		if(oldList != null)
		{
			oldList.removeHandler(MediaSetList.EVENT_MEDIA_SET_ADDED, m_MediaSetAddedHandler);
			oldList.removeHandler(MediaSetList.EVENT_MEDIA_SET_REMOVED, m_MediaSetRemovedHandler);
			oldList.removeHandler(MediaSetList.EVENT_MEDIA_SET_REMOVING, m_MediaSetRemovingHandler);
			for(int i = oldList.size() - 1 ; i >= 0 ; --i)
				this.detachFromMediaSet(oldList.get(i));
			m_MediaSetCoverImageTable.clear();
		}
		
		if(newList == null)
			Log.v(TAG, "setMediaSetList() - newList is null");
		else
		{
			// add event handlers
			newList.addHandler(MediaSetList.EVENT_MEDIA_SET_ADDED, m_MediaSetAddedHandler);
			newList.addHandler(MediaSetList.EVENT_MEDIA_SET_REMOVED, m_MediaSetRemovedHandler);
			newList.addHandler(MediaSetList.EVENT_MEDIA_SET_REMOVING, m_MediaSetRemovingHandler);
			
			// attach to media sets
			for(int i = m_MediaSetList.size() - 1 ; i >= 0 ; --i)
				this.attachToMediaSet(newList.get(i));
			
			// start to create cover image
			this.decodeMediaSetCovers();
		}
		
		// notify data changed
		if(m_MediaSetListAdapter != null)
			m_MediaSetListAdapter.notifyDataSetChanged();
		
		return this.notifyPropertyChanged(PROP_MEDIA_SET_LIST, oldList, newList);
	}
	
	private void updateSelectedMediaSet(MediaSet mediaSet)
	{
		if(!get(PROP_IS_SELECTION_MODE))
		{
			Log.e(TAG, "updateSelectedMediaSet() - not in selection mode");
			return;
		}
		
		// update list
		if(m_SelectedMediaSet.contains(mediaSet))
			m_SelectedMediaSet.remove(mediaSet);
		else
			m_SelectedMediaSet.add(mediaSet);
		
		// leave selection mode is nothing is selected
		if(m_SelectedMediaSet.isEmpty())
			set(PROP_IS_SELECTION_MODE, false);	
		else
		{
			// update tool bar title
			String selectedItems = String.format(getString(R.string.toolbar_selection_total), m_SelectedMediaSet.size());
			m_Toolbar.setTitle(selectedItems);
		}
		
		// notify data set changed
		if(m_MediaSetListAdapter != null)
			m_MediaSetListAdapter.notifyDataSetChanged();
	}
	
	private class MediaSetListAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			if(m_MediaSetList != null)
				return m_MediaSetList.size();
			else
				return 0;
		}

		@Override
		public Object getItem(int position) {
			if(m_MediaSetList != null)
				return m_MediaSetList.get(position);
			else
				return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			
			final ViewInfo viewInfo;
			if(convertView == null)
			{
				LayoutInflater inflater = m_Activity.getLayoutInflater();
				convertView = inflater.inflate(R.layout.layout_media_set_list_item, parent, false);
				
				viewInfo = new ViewInfo();
				viewInfo.titleText = (TextView)convertView.findViewById(R.id.media_set_title);
				viewInfo.sizeTextView = (TextView)convertView.findViewById(R.id.media_set_size);
				viewInfo.coverImage = (ImageView)convertView.findViewById(R.id.media_set_cover_image);
				viewInfo.selectedIcon = (ImageView)convertView.findViewById(R.id.selected_icon);
				
				convertView.setTag(viewInfo);
			}
			else
				viewInfo = (ViewInfo)convertView.getTag();
			
			MediaSet mediaSet = (MediaSet)getItem(position);		
			
			// adjust the priority of showing mediaSet
			if(m_MediaSetDecodeQueue.contains(mediaSet))
			{
				m_MediaSetDecodeQueue.remove(mediaSet);
				m_MediaSetDecodeQueue.addFirst(mediaSet);
				createMediaListCoverImageFromQueue();
			}
			
			viewInfo.titleText.setText(String.valueOf(mediaSet.get(MediaSet.PROP_NAME)));
			Integer mediaCount = mediaSet.get(MediaSet.PROP_MEDIA_COUNT);
			viewInfo.sizeTextView.setText(mediaCount != null ? String.valueOf(mediaCount) : "");
			if(m_MediaSetCoverImageTable.get(mediaSet) instanceof Bitmap)
				viewInfo.coverImage.setImageBitmap((Bitmap)m_MediaSetCoverImageTable.get(mediaSet));
			else
				viewInfo.coverImage.setImageDrawable(null);
			
			if(get(PROP_IS_SELECTION_MODE))
			{
				if(m_SelectedMediaSet.contains(mediaSet))
					viewInfo.selectedIcon.setVisibility(View.VISIBLE);
				else
					viewInfo.selectedIcon.setVisibility(View.INVISIBLE);
			}
			else
				viewInfo.selectedIcon.setVisibility(View.INVISIBLE);
			
			return convertView;
		}
		
	}
	
	private void createMediaListCoverImageFromQueue()
	{
		if(m_MediaSetDecodeQueue.isEmpty())
		{
			//Log.w(TAG, "createMediaListCoverImageFromQueue() - m_MediaSetDecodeQueue is empty");
			return;
		}
		
		createMediaListCoverImage(m_MediaSetDecodeQueue.poll());
	}
	
	private void createMediaListCoverImage(final MediaSet mediaSet)
	{
		if(mediaSet == null)
		{
			Log.w(TAG, "createMediaListCoverImage() - mediaSet is null");
			createMediaListCoverImageFromQueue();
			return;
		}
		
		Integer mediaSetSize = mediaSet.get(MediaSet.PROP_MEDIA_COUNT);

		if(mediaSetSize == null)
		{
			Log.w(TAG, "createMediaListCoverImage() - mediaSetSize is null");
			createMediaListCoverImageFromQueue();
			return;
		}
		
		Log.v(TAG, "createMediaListCoverImage() - mediaSetSize is "+mediaSetSize);
		
		// mediaSetCount : 0~20[1 image], 21~100[12 images], >100[27 images]
		final int targetGridCount;
		final int gridPerRow;
		final int gridPerColumn;
		if(mediaSetSize == 0)
		{
			m_MediaSetCoverImageTable.put(mediaSet, new Object());
			
			// notify data changed
			if(m_MediaSetListAdapter != null)
				m_MediaSetListAdapter.notifyDataSetChanged();
			
			return;
		}
		else if(mediaSetSize <= 20)
		{
			targetGridCount = 1;
			gridPerRow = 1;
		}
		else if(mediaSetSize <= 100)
		{
			targetGridCount = 12;
			gridPerRow = 6;
		}
		else
		{
			targetGridCount = 27;
			gridPerRow = 9;
		}
		gridPerColumn = targetGridCount / gridPerRow;
		
		Log.v(TAG, "createMediaListCoverImage() - targetGridCount is "+targetGridCount);
		
		final MediaList mediaList = mediaSet.openMediaList(MediaComparator.TAKEN_TIME, targetGridCount, 0);
		mediaList.addHandler(MediaList.EVENT_MEDIA_ADDED, new EventHandler<ListChangeEventArgs>() {

			@Override
			public void onEventReceived(EventSource source, EventKey<ListChangeEventArgs> key, ListChangeEventArgs e) {
				
				// check if updated index located in the range that needs to show images
				if(e.getStartIndex() <= targetGridCount-1 && e.getEndIndex() >= targetGridCount-1)
				{
					if(targetGridCount == 1)
					{
						Handle handle = BitmapPool.DEFAULT_THUMBNAIL.decode(mediaList.get(0).getFilePath(), 512, 512, 0, new BitmapPool.Callback() {
							@Override
							public void onBitmapDecoded(Handle handle, String filePath, Bitmap bitmap) {
						
								// update bitmap table
								m_MediaSetCoverImageTable.put(mediaSet, bitmap);
								
								// notify data changed
								if(m_MediaSetListAdapter != null)
									m_MediaSetListAdapter.notifyDataSetChanged();
								
								// decode next media set
								createMediaListCoverImageFromQueue();
								
								// remove handle
								m_DecodingImageHandles.remove(handle);
							}
							
						}, getHandler());		
						m_DecodingImageHandles.add(handle);
					}
					else
					{
						// create gridCoverImage
						final int coverWidth = m_Activity.getResources().getDisplayMetrics().widthPixels;
						final int coverHeight = m_Activity.getResources().getDimensionPixelSize(R.dimen.media_set_list_item_cover_image_height);
						
						final int gridSize = (int)Math.sqrt( (coverWidth * coverHeight) / targetGridCount);
						
						final Bitmap gridCover = Bitmap.createBitmap(coverWidth, coverHeight, Bitmap.Config.RGB_565);
						final Canvas canvas = new Canvas(gridCover);
						
						for(int i=0; i<targetGridCount; i++)
						{
							final int index = i;
							
							Handle handle = ThumbnailImageManager.decodeSmallThumbnailImage(mediaList.get(i), ThumbnailImageManager.FLAG_ASYNC, new ThumbnailImageManager.DecodingCallback()
							{
								@Override
								public void onThumbnailImageDecoded(Handle handle, Media media, Bitmap thumb)
								{
									// gridCoverImageRect
									int rectLeft = (index * gridSize) % coverWidth;
									int rectTop = (index / gridPerRow) * gridSize;
									
									// Bitmap Rect
									int bitmapRectLeft = 0;
									int bitmapRectTop = 0;
									int shortSide = 0;
									if(thumb.getHeight() >= thumb.getWidth())
									{
										shortSide = thumb.getWidth();
										bitmapRectTop = (thumb.getHeight() - thumb.getWidth())/2;
									}
									else
									{
										shortSide = thumb.getHeight();
										bitmapRectLeft = (thumb.getWidth() - thumb.getHeight())/2;
									}	
									canvas.drawBitmap(thumb, new Rect(bitmapRectLeft, bitmapRectTop, bitmapRectLeft+shortSide, bitmapRectTop+shortSide), new Rect(rectLeft, rectTop, rectLeft+gridSize, rectTop+gridSize), null);
									
									// update bitmap table
									m_MediaSetCoverImageTable.put(mediaSet, gridCover);
									
									// notify data changed
									if(m_MediaSetListAdapter != null)
										m_MediaSetListAdapter.notifyDataSetChanged();
									
									// decode next media set
									createMediaListCoverImageFromQueue();
									
									// remove handle
									m_DecodingImageHandles.remove(handle);
								}
							}, getHandler());
							m_DecodingImageHandles.add(handle);
						}		
					}
				}
				else
				{
					// wait for event or create incompleted gridCoverImage
				}
			}
		});
	}
	
	// Class to keep menu item information in related view.
	private static final class ViewInfo
	{
		public TextView titleText;
		public TextView sizeTextView; 
		public ImageView coverImage;
		public ImageView selectedIcon;
	}
	
}
