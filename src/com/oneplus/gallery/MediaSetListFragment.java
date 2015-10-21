package com.oneplus.gallery;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
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
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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
import com.oneplus.cache.HybridBitmapLruCache;
import com.oneplus.gallery.cache.ImageCacheKey;
import com.oneplus.gallery.media.CameraRollMediaSet;
import com.oneplus.gallery.media.DirectoryMediaSet;
import com.oneplus.gallery.media.MediaComparator;
import com.oneplus.gallery.media.MediaList;
import com.oneplus.gallery.media.MediaSet;
import com.oneplus.gallery.media.MediaSetList;
import com.oneplus.gallery.media.ThumbnailImageManager;
import com.oneplus.media.BitmapPool;
import com.oneplus.media.CenterCroppedBitmapPool;

/**
 * Fragment to display media set list.
 */
public class MediaSetListFragment extends GalleryFragment
{
	// constant 
	private static final long MEMORY_CACHE_SIZE = 20 * 1024 * 1024;
	private static final long DISK_CACHE_SIZE = 200 * 1024 * 1024;
	private static final String COVER_IMAGE_CACHE_NAME = "MediaSetCoverImage";
	private static final String CAMERA_ROLL_COVER_IMAGE_KEY = "ThankYou9527";
	
	// static fields
	private static HybridBitmapLruCache<String> m_CoverImageCache;
	private static volatile Executor m_CacheImageLoaderExecutor;
	private static BitmapPool m_CenterCropBitmapPool = new CenterCroppedBitmapPool("MediaSetListFragmentCenterCropBitmapPool", 32 << 20, Bitmap.Config.RGB_565, 4, BitmapPool.FLAG_NO_EMBEDDED_THUMB);
	
	// Fields
	private Activity m_Activity;
	private RelativeLayout m_AddAlbumButton;
	private Hashtable<String, ArrayList<Handle>> m_MediaSetDecodingHandles = new Hashtable<>();
	private MediaSetListAdapter m_MediaSetListAdapter;
	private ListView m_MediaSetListView;
	private MediaSetList m_MediaSetList;
	private LinkedList<MediaSet> m_MediaSetDecodeQueue = new LinkedList<>();
	private ArrayList<MediaSet> m_DecodingMediaSets = new ArrayList<>();
	private SharedPreferences m_Preference;
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
	private Gallery.MediaSetDeletionCallback m_MediaSetDeleteCallback = new Gallery.MediaSetDeletionCallback() {

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
	 * Cache image loaded call-back interface.
	 */
	public interface CacheImageLoadedCallback
	{
		/**
		 * Called when cache image loaded.
		 * @param cachedImage Loaded cache image
		 */
		void onCacheImageLoaded(Bitmap cachedImage);
	}
	private static final class LoadCacheImageTask implements Runnable
	{	
		private static final long TIME_OUT = 800;
		
		private volatile String key;
		private volatile Bitmap defaultBitmap;
		private volatile Handler callbackHandler;
		private volatile CacheImageLoadedCallback callback;
		
		public LoadCacheImageTask(String key, Bitmap defaultBitmap, Handler callbackHandler, CacheImageLoadedCallback callback)
		{
			this.key = key;
			this.defaultBitmap = defaultBitmap;
			this.callbackHandler = callbackHandler;
			this.callback = callback;
		}
		
		@Override
		public void run() {
			
			final Bitmap bitmapInCache = m_CoverImageCache.get(key, defaultBitmap, TIME_OUT);
			
			if(callbackHandler != null && this.callbackHandler.getLooper().getThread() != Thread.currentThread())
			{
				this.callbackHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						if(callback != null)
							callback.onCacheImageLoaded(bitmapInCache);
					}
				});
			}
			else
			{
				if(callback != null)
					callback.onCacheImageLoaded(bitmapInCache);
			}	
		}		
	}
	
	/**
	 * Initialize new MediaSetListFragment instance.
	 */
	public MediaSetListFragment()
	{}
	
	
	// Attach to media set.
	private void attachToMediaSet(MediaSet set)
	{
		set.addCallback(MediaSet.PROP_MEDIA_COUNT, m_MediaCountChangedCallback);
		set.addCallback(MediaSet.PROP_NAME, m_MediaSetNameChangedCallback);
	}
	
	
	// Schedule cover decoding.
	private void decodeMediaSetCovers()
	{
		if(m_CoverImageCache == null)
		{
			Log.v(TAG, "decodeMediaSetCovers() - cache in not ready yet.");
			return;
		}
		
		if(m_MediaSetList != null)
		{
			for(int i = 0 ; i < m_MediaSetList.size() ; i++)
			{		
				MediaSet mediaSet = m_MediaSetList.get(i);		
				
				if(!m_MediaSetDecodeQueue.contains(mediaSet))
					m_MediaSetDecodeQueue.add(mediaSet);			
			}
			createMediaListCoverImageFromQueue();
		}
	}
	
	private void initializeCoverImageCache(Context context)
	{
		if(m_CoverImageCache == null)
		{
			// create cover image cache
			m_CoverImageCache =  new HybridBitmapLruCache<>(GalleryApplication.current(), COVER_IMAGE_CACHE_NAME, Bitmap.Config.RGB_565, Bitmap.CompressFormat.JPEG, MEMORY_CACHE_SIZE, DISK_CACHE_SIZE);	
		}
	}
	
	// Detach from media set.
	private void detachFromMediaSet(MediaSet set)
	{
		set.removeCallback(MediaSet.PROP_MEDIA_COUNT, m_MediaCountChangedCallback);
		set.removeCallback(MediaSet.PROP_NAME, m_MediaSetNameChangedCallback);
	}
	
	
	// Called when attaching to gallery.
	@Override
	protected void onAttachToGallery(Gallery gallery)
	{
		// call super
		super.onAttachToGallery(gallery);
		
		// add call-backs.
		gallery.addCallback(Gallery.PROP_IS_NAVIGATION_BAR_VISIBLE, new PropertyChangedCallback<Boolean>()
		{
			@Override
			public void onPropertyChanged(PropertySource source, PropertyKey<Boolean> key, PropertyChangeEventArgs<Boolean> e)
			{
				if(m_AddAlbumButton != null)
					m_AddAlbumButton.requestLayout();
			}
		});
	}
	
	
	// Called when backing to initial UI state.
	@Override
	protected void onBackToInitialUIState()
	{
		// scroll to top
		if(m_MediaSetListView != null && m_MediaSetListAdapter != null && m_MediaSetListAdapter.getCount() > 0)
			m_MediaSetListView.setSelection(0);
		
		// cancel selection mode
		this.set(PROP_IS_SELECTION_MODE, false);
	}
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		m_Activity =  this.getActivity();
		m_MediaSetListAdapter = new MediaSetListAdapter();
		
		initializeCoverImageCache(m_Activity);
		if(m_CacheImageLoaderExecutor == null)
			m_CacheImageLoaderExecutor = Executors.newFixedThreadPool(4);
		
		m_Preference = m_Activity.getSharedPreferences("CoverImageInfo", Context.MODE_PRIVATE);
		
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
		this.setMediaSetList(null);
		super.onDestroy();
		
		removeCallback(PROP_IS_SELECTION_MODE, m_IsSelectionModeChangedCallback);
	}
	
	
	// Called when media count changed.
	private void onMediaCountChanged(MediaSet mediaSet, PropertyChangeEventArgs<Integer> e)
	{	
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
						getGallery().deleteMediaSet(m_SelectedMediaSet, m_MediaSetDeleteCallback);
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
		
		// remove useless cover image info in preference
		CoverImageInfo.removeUselessCoverImageInfo(m_Preference, m_MediaSetList);
		
		// decode mediaSetCover image
		this.decodeMediaSetCovers();
		
		onSelectionModeChanged(get(PROP_IS_SELECTION_MODE));
	}
	
	
	
	@Override
	public void onPause() {
		super.onPause();
			
		// clear decoding handles
		if(m_MediaSetDecodingHandles != null)
		{
			Enumeration<ArrayList<Handle>> handleLists = m_MediaSetDecodingHandles.elements();
			while(handleLists.hasMoreElements())
			{
				ArrayList<Handle> list = (ArrayList<Handle>)handleLists.nextElement();
				for(Handle handle : list)
					Handle.close(handle);
			}
			m_MediaSetDecodingHandles.clear();
		}
		
		if(m_DecodingMediaSets != null)
			m_DecodingMediaSets.clear();
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
		
		// leave selection mode if nothing is selected
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
			
			viewInfo.titleText.setText(String.valueOf(mediaSet.get(MediaSet.PROP_NAME)));
			Integer mediaCount = mediaSet.get(MediaSet.PROP_MEDIA_COUNT);
			viewInfo.sizeTextView.setText(mediaCount != null ? String.valueOf(mediaCount) : "");
			
			Bitmap coverImage = m_CoverImageCache.get(CoverImageInfo.getMediaSetImageKey(mediaSet), null, 0);
			
			if(coverImage != null)
			{
				//Log.v(TAG, "getView() - coverImage for "+CoverImageInfo.getMediaSetImageKey(mediaSet));
				viewInfo.coverImage.setImageBitmap(coverImage);
			}
			else
			{
				//Log.v(TAG, "getView() - coverImage is null for "+CoverImageInfo.getMediaSetImageKey(mediaSet));
				viewInfo.coverImage.setImageDrawable(null);
				
				// adjust the priority of showing mediaSet
				if(m_MediaSetDecodeQueue.contains(mediaSet))
				{
					//Log.v(TAG, "getView() - in decode queue, move to first");
					
					m_MediaSetDecodeQueue.remove(mediaSet);
					m_MediaSetDecodeQueue.addFirst(mediaSet);
					
					createMediaListCoverImageFromQueue(true, true);
				}
				else if(!m_DecodingMediaSets.contains(mediaSet))
				{
					
					//Log.v(TAG, "getView() - not in decode queue / decoding list , add to first");
					
					m_MediaSetDecodeQueue.addFirst(mediaSet);
					
					createMediaListCoverImageFromQueue(true, true);
				}				
				else
				{
					//Log.v(TAG, "getView() - in decoding list, wait");
				}
			}
			
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
	
	private void decodeSingleCoverImage(final MediaSet mediaSet, MediaList mediaList, boolean isUrgent)
	{
		if(mediaList == null || mediaList.isEmpty())
		{
			Log.v(TAG, "decodeSingleCoverImage() - mediaList is not ready");
			return;
		}
		
		// stop handle which is decoding the same mediaSet
		ArrayList<Handle> mediaSetDecodingHandleList = m_MediaSetDecodingHandles.get(CoverImageInfo.getMediaSetImageKey(mediaSet));
		if(mediaSetDecodingHandleList != null)
		{
			for(Handle handle : mediaSetDecodingHandleList)
				Handle.close(handle);
			mediaSetDecodingHandleList.clear();
		}
		else
			mediaSetDecodingHandleList =  new ArrayList<Handle>();
			
		// create singleCoverImage
		final int coverWidth = m_Activity.getResources().getDisplayMetrics().widthPixels;
		final int coverHeight = m_Activity.getResources().getDimensionPixelSize(R.dimen.media_set_list_item_cover_image_height);
		
		int flag = BitmapPool.FLAG_ASYNC;
		if(isUrgent)
			flag = flag | BitmapPool.FLAG_URGENT;
		
		Handle handle = m_CenterCropBitmapPool.decode(mediaList.get(0).getFilePath(), coverWidth, coverHeight, flag, new BitmapPool.Callback() {
			@Override
			public void onBitmapDecoded(Handle handle, String filePath, Bitmap bitmap) {	
				
				// remove from decoding set
				m_DecodingMediaSets.remove(mediaSet);
				
				// update bitmap table
				m_CoverImageCache.add(CoverImageInfo.getMediaSetImageKey(mediaSet), bitmap);		
				
				// notify data changed
				if(m_MediaSetListAdapter != null)
					m_MediaSetListAdapter.notifyDataSetChanged();
				
				// decode next media set
				createMediaListCoverImageFromQueue();
			}
		}, getHandler());			
		mediaSetDecodingHandleList.add(handle);
		
		m_MediaSetDecodingHandles.put(CoverImageInfo.getMediaSetImageKey(mediaSet), mediaSetDecodingHandleList);
		
	}
	
	private void decodeGridCoverImage(int targetGridCount, final int gridPerRow,  final MediaSet mediaSet, MediaList mediaList, boolean isUrgent)
	{
		if(mediaList == null || mediaList.size() < targetGridCount)
		{
			Log.v(TAG, "decodeGridCoverImage() - mediaList is not ready");
			return;
		}
		
		// stop handle which is decoding the same mediaSet
		ArrayList<Handle> mediaSetDecodingHandleList = m_MediaSetDecodingHandles.get(CoverImageInfo.getMediaSetImageKey(mediaSet));
		if(mediaSetDecodingHandleList != null)
		{
			for(Handle handle : mediaSetDecodingHandleList)
				Handle.close(handle);
			mediaSetDecodingHandleList.clear();
		}
		else
			mediaSetDecodingHandleList =  new ArrayList<Handle>();
		
		
		// create gridCoverImage
		final int coverWidth = m_Activity.getResources().getDisplayMetrics().widthPixels;
		final int coverHeight = m_Activity.getResources().getDimensionPixelSize(R.dimen.media_set_list_item_cover_image_height);
		
		final int gridSize = (int)Math.sqrt( (coverWidth * coverHeight) / targetGridCount);
		
//		Bitmap bitmapInCache = m_CoverImageCache.get(CoverImageInfo.getMediaSetImageKey(mediaSet), null, 0);
			
//		boolean isGridCoverSizeCache = (bitmapInCache != null && bitmapInCache.getWidth() == coverWidth && bitmapInCache.getHeight() == coverHeight);
//		final Bitmap gridCover = isGridCoverSizeCache ? bitmapInCache.copy(Bitmap.Config.RGB_565, true) : Bitmap.createBitmap(coverWidth, coverHeight, Bitmap.Config.RGB_565);
		final Bitmap gridCover = Bitmap.createBitmap(coverWidth, coverHeight, Bitmap.Config.RGB_565);
		final Canvas canvas = new Canvas(gridCover);
		
		for(int i=0; i<targetGridCount; i++)
		{
			
			final int index = i;
	
			int flag = ThumbnailImageManager.FLAG_ASYNC;
			if(isUrgent)
				flag = flag | ThumbnailImageManager.FLAG_URGENT;
			
			Handle handle = ThumbnailImageManager.decodeSmallThumbnailImage(mediaList.get(i), flag, new ThumbnailImageManager.DecodingCallback()
			{
				@Override
				public void onThumbnailImageDecoded(Handle handle, Media media, Bitmap thumb)
				{
					if(thumb == null)
					{
						Log.w(TAG, "onThumbnailImageDecoded() - thumb is null");
						return;
					}	
					
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
					
					// remove from decoding set
					m_DecodingMediaSets.remove(mediaSet);
					
					// update bitmap table
					m_CoverImageCache.add(CoverImageInfo.getMediaSetImageKey(mediaSet), gridCover);
					
					// notify data changed
					if(m_MediaSetListAdapter != null)
						m_MediaSetListAdapter.notifyDataSetChanged();
					
					// decode next media set
					createMediaListCoverImageFromQueue();
					
				}
			}, getHandler());
			mediaSetDecodingHandleList.add(handle);	
			
		}
		m_MediaSetDecodingHandles.put(CoverImageInfo.getMediaSetImageKey(mediaSet), mediaSetDecodingHandleList);
		
	}
	
	private void createMediaListCoverImageFromQueue()
	{
		createMediaListCoverImageFromQueue(false, false);
	}
	
	private void createMediaListCoverImageFromQueue(boolean needToNotifyAdapter, boolean isUrgent)
	{
		if(m_MediaSetDecodeQueue.isEmpty())
		{
			//Log.w(TAG, "createMediaListCoverImageFromQueue() - m_MediaSetDecodeQueue is empty");
			return;
		}
		m_DecodingMediaSets.add(m_MediaSetDecodeQueue.peek());
		createMediaListCoverImage(m_MediaSetDecodeQueue.poll(), needToNotifyAdapter, isUrgent);	
	}	
	
	private void createMediaListCoverImage(final MediaSet mediaSet, final boolean needToNotifyAdapter, final boolean isUrgent)
	{
		if(mediaSet == null)
		{
			Log.w(TAG, "createMediaListCoverImage() - mediaSet is null");
			m_DecodingMediaSets.remove(mediaSet);
			createMediaListCoverImageFromQueue();
			return;
		}
		
		Integer mediaSetSize = mediaSet.get(MediaSet.PROP_MEDIA_COUNT);

		if(mediaSetSize == null)
		{
			Log.w(TAG, "createMediaListCoverImage() - mediaSetSize is null");
			m_DecodingMediaSets.remove(mediaSet);
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
			m_CoverImageCache.remove(CoverImageInfo.getMediaSetImageKey(mediaSet));
			
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
				
				// try to decode image when all images are ready
				if(e.getEndIndex() == targetGridCount-1)
				{
					if(CoverImageInfo.isInCache(m_Preference, CoverImageInfo.getMediaSetImageKey(mediaSet), mediaList))
					{
						// load image from cache		
						LoadCacheImageTask task = new LoadCacheImageTask(CoverImageInfo.getMediaSetImageKey(mediaSet), null, getHandler(), new CacheImageLoadedCallback() {
							
							@Override
							public void onCacheImageLoaded(Bitmap cachedImage) {				

								if(cachedImage == null)
								{
									// there is no cache image, need to decode image
									if(targetGridCount == 1)
										decodeSingleCoverImage(mediaSet, mediaList, isUrgent);
									else
										decodeGridCoverImage(targetGridCount, gridPerRow, mediaSet, mediaList, isUrgent);	
								}
								else
								{								
									if(needToNotifyAdapter && m_MediaSetListAdapter != null)
										m_MediaSetListAdapter.notifyDataSetChanged();
									
									// remove from decoding set
									m_DecodingMediaSets.remove(mediaSet);
									
									// decode next media set
									createMediaListCoverImageFromQueue();
								}
								
								// release media list
								mediaList.release();
							}
						});
						m_CacheImageLoaderExecutor.execute(task);
					}
					else
					{
						// cover image has been changed, need to reload
						
						// update cover image info
						CoverImageInfo.updateCoverImageInfo(m_Preference, CoverImageInfo.getMediaSetImageKey(mediaSet), mediaList);
						
						// decode image
						if(targetGridCount == 1)
							decodeSingleCoverImage(mediaSet, mediaList, isUrgent);
						else
							decodeGridCoverImage(targetGridCount, gridPerRow, mediaSet, mediaList, isUrgent);	
						
						// release media list
						mediaList.release();
					}
				}
				else
				{
					// wait for event
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
	
	private static final class CoverImageInfo
	{
		private static final int THRESHOLD_TO_REMOVE_USELESS_COVER_IMAGE_INFO = 10;
		
		private static String getMediaListImagesHashValueString(MediaList mediaList)
		{
			if(mediaList == null)
				return null;
			
			StringBuffer result = new StringBuffer();
			
			for(int i=0; i<mediaList.size(); i++)
			{
				ImageCacheKey inputKey = new ImageCacheKey(mediaList.get(i));
				result.append("[").append(i).append("]").append(inputKey.hashCode());
			}
			
			return result.toString();
		}
		
		public static boolean isInCache(SharedPreferences preference, String imageKey, MediaList mediaList)
		{
			String hashCode = preference.getString(imageKey, "");

			if(TextUtils.isEmpty(hashCode))
				return false;
			
			return hashCode.equals(getMediaListImagesHashValueString(mediaList));
		}
		
		public static void updateCoverImageInfo(SharedPreferences preference, String imageKey, MediaList mediaList)
		{
			preference.edit().putString(imageKey, getMediaListImagesHashValueString(mediaList)).commit();
		}	
		
		public static void removeUselessCoverImageInfo(SharedPreferences preference, MediaSetList mediaSetList)
		{
			if(preference == null || mediaSetList == null)
				return;
			
			Map<String, ?> preferenceKeys = preference.getAll();
			
			if(preferenceKeys == null)
				return;

			if(Math.abs(preferenceKeys.size() - mediaSetList.size()) <= THRESHOLD_TO_REMOVE_USELESS_COVER_IMAGE_INFO)
				return;

			for (Map.Entry<String, ?> entry : preferenceKeys.entrySet()) {
				//Log.v("CoverImageInfo", entry.getKey() + ": " + entry.getValue().toString());
				
				boolean isUseless = true;
				
				for(MediaSet mediaSet : mediaSetList)
				{
					if(entry.getKey().equals(getMediaSetImageKey(mediaSet)))
					{
						isUseless = false;
						break;
					}		
				}
				
				if(isUseless)
					preference.edit().remove(entry.getKey()).commit();						
			}
		}

		public static String getMediaSetImageKey(MediaSet mediaSet)
		{
			if(mediaSet instanceof DirectoryMediaSet)
				return ((DirectoryMediaSet)mediaSet).getDirectoryPath();
			else if(mediaSet instanceof CameraRollMediaSet)
				return CAMERA_ROLL_COVER_IMAGE_KEY;
			else
				return null;
		}
		
	}

}
