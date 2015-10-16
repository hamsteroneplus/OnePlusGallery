package com.oneplus.gallery;


import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import com.oneplus.base.EventHandler;
import com.oneplus.base.EventKey;
import com.oneplus.base.EventSource;
import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.base.PropertyKey;
import com.oneplus.gallery.media.Media;
import com.oneplus.gallery.media.MediaList;
import com.oneplus.gallery.media.MediaSet;
import com.oneplus.gallery.media.ThumbnailImageManager;
import com.oneplus.gallery.media.VideoMedia;
import com.oneplus.gallery.widget.GridView;
import com.oneplus.media.BitmapPool;
import com.oneplus.media.CenterCroppedBitmapPool;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;
import android.widget.Toolbar;
import android.widget.Toolbar.OnMenuItemClickListener;
/**
 * Fragment to display media in media set.
 */
public class GridViewFragment extends GalleryFragment {

	// constant
	private static int PRE_DECODE_BITMAP_COUNTS = 27;
	
	
	// Private fields
	private int m_AnchorPosition = GridView.INVALID_POSITION; // Keep the very first selected item position
	boolean m_MultiSelectToggleOn = false;
	private View m_EmptyMediaView;
	private GridView m_GridView;
	private GridViewItemAdapter m_GridViewItemAdapter;
	private Drawable m_GreySquare;
	private int m_GridviewColumns;
	private int m_GridviewItemWidth;
	private int m_GridviewItemHeight;
	private boolean m_HasActionBar;
	private boolean m_IsCameraRoll;
	private boolean m_IsSelectionMode = false;
	private int m_LastGridViewPosition = -1;
	private MediaList m_MediaList = null;
	private PreDecodeBitmapRunnable m_PreDecodeBitmapRunnable;
	private List<Media> m_SelectionMeidaList = new ArrayList<>();
	private List<Media> m_TempMeidaList = new ArrayList<>();
	private Handle m_ThumbManagerActivateHandle;
	private Toolbar m_Toolbar;
	private String m_ToolbarTitle = null;
	private boolean m_ToolbarActionShared = false;
	private int m_TouchedPosition = GridView.INVALID_POSITION;
//	private boolean m_SetEmptyMediaView = false;
	
	private static BitmapPool m_SmallBitmapPool = new CenterCroppedBitmapPool("GridViewFragmentSmallBitmapPool", 32 << 20, Bitmap.Config.RGB_565, 4, BitmapPool.FLAG_USE_EMBEDDED_THUMB_ONLY);
	private Gallery.MediaDeletionCallback m_DeleteCallback = new Gallery.MediaDeletionCallback() {
		@Override
		public void onDeletionProcessCompleted() {
			super.onDeletionProcessCompleted();
			exitSelectionMode();
			GridViewFragment.this.set(PROP_IS_SELECTION_MODE, false);
		}
	};
	
	/**
	 * Property to get or set whether media list is camera roll or not.
	 */
	public static final PropertyKey<Boolean> PROP_IS_CAMERA_ROLL = new PropertyKey<>("IsCameraRoll", Boolean.class, GridViewFragment.class, PropertyKey.FLAG_NOT_NULL, false);
	/**
	 * Property to get or set selection mode.
	 */
	public static final PropertyKey<Boolean> PROP_IS_SELECTION_MODE = new PropertyKey<>("IsSelectionMode", Boolean.class, GridViewFragment.class, PropertyKey.FLAG_NOT_NULL, false);
	/**
	 * Property to get or set media list to display.
	 */
	public final static PropertyKey<MediaList> PROP_MEDIA_LIST = new PropertyKey<>("MediaList", MediaList.class, GridViewFragment.class, 0, null);
	/**
	 * Property to get or set media set which is related to media list.
	 */
	public final static PropertyKey<MediaSet> PROP_MEDIA_SET = new PropertyKey<>("MediaSet", MediaSet.class, GridViewFragment.class, 0, null);
	/**
	 * Read-only property to get number of selected media.
	 */
	public static final PropertyKey<Integer> PROP_SELECTION_COUNT = new PropertyKey<>("SelectionCount", Integer.class, GridViewFragment.class, 0);

	/**
	 * Raised after clicking single media.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static final EventKey<ListItemEventArgs<Media>> EVENT_MEDIA_CLICKED = new EventKey<ListItemEventArgs<Media>>("MediaClicked", (Class)ListItemEventArgs.class, GridViewFragment.class);
	
	
	// Adapter view holder
	private class GridViewItemHolder {
		public int position;
		public Uri contentUri;
		public ImageView thumbnailImageView;
		public ImageView selectedImageView;
		public ImageView typeIconView;
		public TextView durationTextView;
		public String mimeType;
		public Handle lowResolutionThumbDecodeHandle;
		public Handle highResolutionThumbDecodeHandle;
		public boolean highThumbDecoded;
		public final BitmapPool.Callback lowResolutionThumbDecodeCallback = new BitmapPool.Callback()
		{
			public void onBitmapDecoded(Handle handle, Uri contentUri, Bitmap bitmap) 
			{
				if(handle == lowResolutionThumbDecodeHandle && bitmap != null && !highThumbDecoded)
					thumbnailImageView.setImageBitmap(bitmap);
			}
			public void onBitmapDecoded(Handle handle, String filePath, Bitmap bitmap) 
			{
				Media media = (Media)GridViewFragment.this.getGridViewItemAdapter().getItem(position);
				String mediaFilePath = null;
				if(media != null)
					mediaFilePath = media.getFilePath();
				if(filePath.equals(mediaFilePath) && bitmap != null && !highThumbDecoded) {
					thumbnailImageView.setImageBitmap(bitmap);
					GridViewFragment.this.removeLowResDecodingHandle(filePath);
				}
			}
		};
		public final ThumbnailImageManager.DecodingCallback hightResolutionThumbDecodeCallback = new ThumbnailImageManager.DecodingCallback()
		{
			@Override
			public void onThumbnailImageDecoded(Handle handle, Media media, Bitmap thumb)
			{
				if(GridViewFragment.this.getGridViewItemAdapter().getItem(position) == media && thumb != null)
				{
					highThumbDecoded = true;
					thumbnailImageView.setImageBitmap(thumb);
					GridViewFragment.this.removeDecodingHandle(media);
				}
			}
		};
		
		
		public GridViewItemHolder(View itemView)
		{
			this.thumbnailImageView = (ImageView) itemView.findViewById(R.id.item_thumbnail);
			this.typeIconView = (ImageView) itemView.findViewById(R.id.item_type);
			this.durationTextView = (TextView) itemView.findViewById(R.id.item_video_time);
			this.selectedImageView = (ImageView) itemView.findViewById(R.id.item_selected);
			itemView.setTag(this);
		}
	}
	
	
	private static class PreDecodeBitmapRunnable implements Runnable {
		
		private final WeakReference<GridViewFragment> m_ActivityRef;
		private HashSet<Handle> m_HandleSet = new HashSet<>();
		
		PreDecodeBitmapRunnable(GridViewFragment gridfragment) {
			m_ActivityRef = new WeakReference<GridViewFragment>(gridfragment);
		}
		
		public void cancelAllBitmapDecoding() {
			if(m_HandleSet != null && !m_HandleSet.isEmpty()) {
				for(Handle handle : m_HandleSet) {
					Handle.close(handle);
				}
				m_HandleSet.clear();
			}
		}
		private final ThumbnailImageManager.DecodingCallback preDecodeCallback = new ThumbnailImageManager.DecodingCallback()
		{
			@Override
			public void onThumbnailImageDecoded(Handle handle, Media media, Bitmap thumb)
			{
				m_HandleSet.remove(handle);
			}
		};
		@Override
		public void run() {
			if(m_ActivityRef != null) {
				final MediaList medialist = m_ActivityRef.get().m_MediaList;
				if(medialist == null || medialist.isEmpty())
					return;
				final int visibleLastposition = m_ActivityRef.get().m_GridView.getLastVisiblePosition();
				final int visibleFirstposition = m_ActivityRef.get().m_GridView.getFirstVisiblePosition();
				final Handler handler = m_ActivityRef.get().getHandler();
				// visibleLastPosition could be -1
				for(int i = visibleLastposition; i >= 0 && i < (visibleLastposition + PRE_DECODE_BITMAP_COUNTS) && i < medialist.size() ; ++i) {
					Media media = medialist.get(i);
					Handle handle = ThumbnailImageManager.decodeSmallThumbnailImage(media, preDecodeCallback, handler);
					m_HandleSet.add(handle);
				}
				
				for(int i = visibleFirstposition; i > (visibleFirstposition - PRE_DECODE_BITMAP_COUNTS) && i >= 0; --i) {
					Media media = medialist.get(i);
					Handle handle = ThumbnailImageManager.decodeSmallThumbnailImage(media, preDecodeCallback, handler);
					m_HandleSet.add(handle);
				}
				
			}
		}
		
	}
	
	
	// Event handlers.
	private final EventHandler<ListChangeEventArgs> m_MediaAddedHandler = new EventHandler<ListChangeEventArgs>()
	{
		@Override
		public void onEventReceived(EventSource source, EventKey<ListChangeEventArgs> key, ListChangeEventArgs e)
		{
			Log.d(TAG, "m_MediaAddedHandler onEventReceived");
			onMediaAdded(e);
		}
	};
	private final EventHandler<ListChangeEventArgs> m_MediaRemovedHandler = new EventHandler<ListChangeEventArgs>()
	{
		@Override
		public void onEventReceived(EventSource source, EventKey<ListChangeEventArgs> key, ListChangeEventArgs e)
		{
			onMediaRemoved(e);
		}
	};

	private final EventHandler<ListChangeEventArgs> m_MediaRemovingHandler = new EventHandler<ListChangeEventArgs>()
	{
		@Override
		public void onEventReceived(EventSource source, EventKey<ListChangeEventArgs> key, ListChangeEventArgs e)
		{
			onMediaRemoving(e);
		}
	};
	
	
	private void cancelAllBitmapDecodeTasks() {
		if(m_GridViewItemAdapter != null) {
			m_GridViewItemAdapter.cancelAllBitmapDecodeTasks();
		}
	}


	private void hideSelectImageIcon() {
		if(m_SelectionMeidaList.isEmpty())
			return;
		m_SelectionMeidaList.clear();	
		final int size = m_GridView.getChildCount();
		for (int i = 0; i < size; ++i) {
			ViewGroup gridChild = (ViewGroup) m_GridView.getChildAt(i);
			int childSize = gridChild.getChildCount();
			for (int k = 0; k < childSize; k++) {
				if (gridChild.getChildAt(k).getId() == R.id.item_selected) {
					gridChild.getChildAt(k).setVisibility(View.GONE);
				}
			}
		}
	}
	
	
	private void resetToolBar() {
		if(m_Toolbar != null) {
			if(m_IsCameraRoll) {
				m_Toolbar.setNavigationIcon(null);
				m_Toolbar.setVisibility(View.GONE);	
			}else {
				m_Toolbar.setNavigationIcon(R.drawable.button_previous);
				m_Toolbar.setTitle(m_ToolbarTitle);
			}
			m_Toolbar.getMenu().setGroupVisible(R.id.selectModeActionGroup, false);	
		}
	}


	private void exitSelectionMode() {
		resetToolBar();
		hideSelectImageIcon();
	}
	
	
	public GridViewItemAdapter getGridViewItemAdapter() {
		return this.m_GridViewItemAdapter;
	}
	
	/**
	 * Get all selected media.
	 * @return List of selected media.
	 */
	public List<Media> getSelectedMedia()
	{
		if(m_SelectionMeidaList == null)
			return null;
		if(m_SelectionMeidaList.isEmpty()) 
			return null;
		// Prepare selected media list
		return m_SelectionMeidaList;
	}
	
	
	// Called when backing to initial UI state.
	@Override
	protected void onBackToInitialUIState()
	{
		// scroll to top
		if(m_GridView != null && m_GridViewItemAdapter != null && m_GridViewItemAdapter.getCount() > 0)
		{
			Log.v(TAG, "onBackToInitialUIState() - Scroll grid view to top");
			m_GridView.setSelection(0);
		}
		else
		{
			Log.v(TAG, "onBackToInitialUIState() - Scroll grid view to top later");
			m_LastGridViewPosition = 0;
		}
		
		// cancel selection mode
		this.set(PROP_IS_SELECTION_MODE, false);
	}
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");
		m_GridviewItemHeight = this.getResources().getDimensionPixelSize(R.dimen.gridview_item_height);
		m_GridviewItemWidth = this.getResources().getDimensionPixelSize(R.dimen.gridview_item_width);

		// Prepare greySquare
		m_GreySquare = new SquareDrawable(m_GridviewItemWidth, m_GridviewItemHeight);
	}
	
	
	// Normal Mode
	private void onSingleItemClicked(int index, View view)
	{
		// check state
		if(m_MediaList == null)
			return;
		if(this.get(PROP_IS_CAMERA_ROLL))
		{
			if(index == 0)
			{
				this.getGallery().startCamera();
				return;
			}
			--index;
		}
		if(index < 0 || index >= m_MediaList.size())
		{
			Log.e(TAG, "onItemClicked() - Invalid index : " + index);
			return;
		}
		
		// raise event
		Media media = m_MediaList.get(index);
		this.raise(EVENT_MEDIA_CLICKED, new ListItemEventArgs<Media>(index, media));
	}
	
	
	// Selection Mode
	private void onSingleItemSelected(int index, View view) {
		if(view == null) 
			return;
		if(index == 0 && m_IsCameraRoll)
			return;
		Log.d(TAG, "onItemSelected m_AnchorPosition: " + m_AnchorPosition);
		ImageView selectedImage = (ImageView) view.findViewById(R.id.item_selected);
		// De-Select
		Media media = (Media) m_GridViewItemAdapter.getItem(index);
		if(!m_SelectionMeidaList.isEmpty() && m_SelectionMeidaList.contains(media)) {
			if(index != m_AnchorPosition) {
				m_SelectionMeidaList.remove(media);
				selectedImage.setVisibility(View.GONE);	
			}
			
		}else {
			selectedImage.setVisibility(View.VISIBLE);
			m_SelectionMeidaList.add(media);
		}
		
		
		// Exit selection mode if selection set is empty and is not in selection mode
		if(m_IsSelectionMode && m_SelectionMeidaList.isEmpty()) {
			exitSelectionMode();
			GridViewFragment.this.set(PROP_IS_SELECTION_MODE, false);
			return;
		}
		
		updateToolBarTitleImageCounts();
		
	}
	
	
	private void onMultiItemSelected(int index, View view) {
		if(view == null) 
			return;
		if(index == 0 && m_IsCameraRoll)
			return;
		ImageView selectedImage = (ImageView) view.findViewById(R.id.item_selected);
		Media media = (Media) m_GridViewItemAdapter.getItem(index);
		if(!m_SelectionMeidaList.contains(media)) {
			if(!m_TempMeidaList.isEmpty() && m_TempMeidaList.contains(media)) {
				//Dont remove anchorposition
//				if(index != m_AnchorPosition){
//					selectedImage.setVisibility(View.GONE);
//					m_TempMeidaList.remove(media);
//				}
			}else {
				selectedImage.setVisibility(View.VISIBLE);
				m_TempMeidaList.add(media);
			}
		}
		
		
		updateToolBarTitleImageCounts();
	}
	
	
	private void onMultiItemDeSelected(int index, View view) {
		if(view == null) 
			return;
		if(index == 0 && m_IsCameraRoll)
			return;
		ImageView selectedImage = (ImageView) view.findViewById(R.id.item_selected);
		Media media = (Media) m_GridViewItemAdapter.getItem(index);
		if(!m_SelectionMeidaList.contains(media)) {
			if(!m_TempMeidaList.isEmpty() && m_TempMeidaList.contains(media)) {
				//Dont remove anchorposition
				if(index != m_AnchorPosition){
					selectedImage.setVisibility(View.GONE);
					m_TempMeidaList.remove(media);
				}
			}
		}
		
		
		updateToolBarTitleImageCounts();
	}
	
	private void updateToolBarTitleImageCounts() {
		Resources res = this.getActivity().getResources();
		String selectedItems = String.format(res.getString(R.string.toolbar_selection_total), m_SelectionMeidaList.size() + m_TempMeidaList.size());
		m_Toolbar.setTitle(selectedItems);
	}
	
	
	private void onMediaAdded(ListChangeEventArgs e)
	{
		Log.d(TAG, "onMediaAdded mediaList");
		
		if(m_MediaList != null && !m_MediaList.isEmpty()) {
			this.getHandler().removeCallbacks(emptyViewRunnable);
			if(m_EmptyMediaView != null)
				m_EmptyMediaView.setVisibility(View.GONE);
			if(m_GridView != null) {
				m_GridView.setVisibility(View.VISIBLE);
			}
		}
		
		// refresh items
		if(m_GridViewItemAdapter != null) {
			m_GridViewItemAdapter.notifyDataSetChanged();
		}
	}
	
	private void onMediaRemoved(ListChangeEventArgs e)
	{
		// hide grid view
		if(m_MediaList != null && m_MediaList.isEmpty()) {
			if(m_IsCameraRoll && m_EmptyMediaView != null)
				m_EmptyMediaView.setVisibility(View.VISIBLE);
			if(m_GridView != null)
				m_GridView.setVisibility(View.GONE);
		}
		
		// refresh items
		if(m_GridViewItemAdapter != null)
			m_GridViewItemAdapter.notifyDataSetChanged();
		
	}

	private void onMediaRemoving(ListChangeEventArgs e) {
		if(m_GridViewItemAdapter == null || m_GridView == null || m_SelectionMeidaList == null){
			return;
		}
		int itemIndex = e.getStartIndex();
		
		if(m_IsSelectionMode) {
			if(m_IsCameraRoll)
				++itemIndex;
			Media media = (Media) m_GridViewItemAdapter.getItem(itemIndex);
			if(media == null) {
				Log.e(TAG, "onMediaRemoving getMedia object is null");
				return;
			}
			if(m_SelectionMeidaList.contains(media)) {
				View itemView = m_GridView.getChildAt(itemIndex - m_GridView.getFirstVisiblePosition());
				if(itemView != null) {
					ImageView selectedView = (ImageView) itemView.findViewById(R.id.item_selected);
					selectedView.setVisibility(View.GONE);
				}
				m_SelectionMeidaList.remove(media);
			}
			
			if(m_SelectionMeidaList.isEmpty()){
				exitSelectionMode();
				GridViewFragment.this.set(PROP_IS_SELECTION_MODE, false);
			}else {
				Resources res = this.getActivity().getResources();
				String selectedItems = String.format(res.getString(R.string.toolbar_selection_total), m_SelectionMeidaList.size());
				m_Toolbar.setTitle(selectedItems);
			}
		}
	}
	
	/**
	 * Initialize new GridViewFragment instance.
	 */
	public GridViewFragment() {
		onInitialize();
	}

	private void onInitialize() {
		Log.d(TAG, "onInitialize" );
	}
	
	
	@Override
	public void onPause() {
		Log.d(TAG, "onPause()");
		super.onPause();
	}


	@Override
	public void onResume() {
//		Log.d(TAG, "onResume() m_SetEmptyMediaView:" + m_SetEmptyMediaView);
		super.onResume();
	}


	@Override
	public void onStop() {
		super.onStop();
		Log.d(TAG, "onStop");
		if(m_MediaList != null)
			Log.d(TAG, "m_MediaList size: "  + m_MediaList.size());
		else
			Log.e(TAG, "m_MediaList size: 0 or NPE");
		// Shrink BitmapPool size to 16MB when Gallery is not visible
		m_SmallBitmapPool.shrink(16 << 20);
		
		// Cancel on-going visible gridview items 
		cancelAllBitmapDecodeTasks();
		
		// Cancel on-going invisible gridview items(pre-decode)
		if(m_PreDecodeBitmapRunnable != null) {
			m_PreDecodeBitmapRunnable.cancelAllBitmapDecoding();
			getHandler().removeCallbacks(m_PreDecodeBitmapRunnable);
		}

		// Clear selection after share activity start
		if(m_ToolbarActionShared) {
			if(m_IsSelectionMode) {
				exitSelectionMode();
				GridViewFragment.this.set(PROP_IS_SELECTION_MODE, false);
			}
			m_ToolbarActionShared = false;
		}
		
		// deactivate thumbnail image manager
		m_ThumbManagerActivateHandle = Handle.close(m_ThumbManagerActivateHandle);
	}
	
	
	@SuppressWarnings("unchecked")
	@Override
	public <TValue> TValue get(PropertyKey<TValue> key)
	{
		if(key == PROP_MEDIA_LIST)
			return (TValue)m_MediaList;
		return super.get(key);
	}
	
	
	// Set property value.
	@Override
	public <TValue> boolean set(PropertyKey<TValue> key, TValue value)
	{
		if(key == PROP_MEDIA_LIST)
			return this.setMediaList((MediaList)value);
		else if(key == PROP_HAS_ACTION_BAR)
			m_HasActionBar = (boolean) value;
		else if(key == PROP_IS_SELECTION_MODE)
		{
			m_IsSelectionMode = (boolean) value;
			if(!m_IsSelectionMode)
				exitSelectionMode();
		}
		else if(key == PROP_IS_CAMERA_ROLL)
			m_IsCameraRoll = (boolean) value;
		else if(key == PROP_TITLE)
		{
			m_ToolbarTitle = (String) value;
			if(m_Toolbar != null && m_HasActionBar)
				m_Toolbar.setTitle(m_ToolbarTitle);
		}
		return super.set(key, value);
	}
	
	
	private boolean setMediaList(MediaList value) {
		Log.d(TAG, "setMediaList enter");
		// check instance
		if(m_MediaList == value) {
			Log.d(TAG, "setMediaList check instance");
			return false;
			
		}
		
		// detach from previous media list
		if(m_MediaList != null)
		{
			m_MediaList.removeHandler(MediaList.EVENT_MEDIA_ADDED, m_MediaAddedHandler);
			m_MediaList.removeHandler(MediaList.EVENT_MEDIA_REMOVED, m_MediaRemovedHandler);
			m_MediaList.removeHandler(MediaList.EVENT_MEDIA_REMOVING, m_MediaRemovingHandler);
		}
		
		// attach to new media list
		m_MediaList = value;
		if(m_MediaList != null) {
			Log.d(TAG, "setMediaList m_MediaList != null");
			m_MediaList.addHandler(MediaList.EVENT_MEDIA_ADDED, m_MediaAddedHandler);
			m_MediaList.addHandler(MediaList.EVENT_MEDIA_REMOVED, m_MediaRemovedHandler);
			m_MediaList.addHandler(MediaList.EVENT_MEDIA_REMOVING, m_MediaRemovingHandler);
			
			Log.d(TAG, "setMediaList m_MediaList != null size: " + m_MediaList.size() );
		}else {
			Log.d(TAG, "setMediaList m_MediaList == null");
		}
		//TODO need mediaSet propertychange info
		
		// update UI
		if(m_GridViewItemAdapter != null)
			m_GridViewItemAdapter.notifyDataSetChanged();
		
		// Scroll gridview to top when mediaList is changed
		if(!get(PROP_IS_CAMERA_ROLL)) {
			if(m_GridView != null)
				m_GridView.setSelection(0);
		}
		// complete
		return true;
	}


	@Override
	public void onStart() {
		super.onStart();
		Log.d(TAG, "onStart()" );
		// leave gallery when was about to share photos, reset to default when re-enter
		if(m_ToolbarActionShared) {
			if(m_IsSelectionMode) {
				exitSelectionMode();
				GridViewFragment.this.set(PROP_IS_SELECTION_MODE, false);
			}
			m_ToolbarActionShared = false;
		}
		
		// activate thumbnail image manager
		if(!Handle.isValid(m_ThumbManagerActivateHandle))
			m_ThumbManagerActivateHandle = ThumbnailImageManager.activate();
	} 


	// Create view.
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		Log.d(TAG, "onCreateView");
		View view = inflater.inflate(R.layout.fragment_gridview, container, false);
		
		// GridView7
		m_GridView = (GridView) view.findViewById(R.id.gridview);
		m_GridView.setNumColumns(m_GridviewColumns);
		if(m_GridViewItemAdapter == null)
			m_GridViewItemAdapter = new GridViewItemAdapter(this.getActivity());
		m_GridView.setSaveInstanceStateEnabled(false);
		if(m_LastGridViewPosition >= 0)
		{
			Log.v(TAG, "onCreateView() - Restore grid view position to ", m_LastGridViewPosition);
			m_GridView.setSelection(m_LastGridViewPosition);
			m_LastGridViewPosition = -1;
		}
		m_GridView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Log.d(TAG, "onItemClick position:" + position);
				if(m_IsSelectionMode) {
					onSingleItemSelected(position, view);
				}else {
					onSingleItemClicked(position, view);
				}
			}
	    });	
		m_GridView.setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				// Position 0 is not valid photo item.
				if(m_IsSelectionMode || (m_IsCameraRoll && position == 0)) 
					return false;
				Log.d(TAG, "gridview onItemLongClick event item position:" + position);
				m_Toolbar.getMenu().setGroupVisible(R.id.selectModeActionGroup, true);
				m_Toolbar.setNavigationIcon(R.drawable.button_cancel);
				m_Toolbar.setVisibility(View.VISIBLE);
				m_TouchedPosition = position;
				m_AnchorPosition = position;
				
				GridViewFragment.this.set(PROP_IS_SELECTION_MODE, true);
				Media media = (Media) m_GridViewItemAdapter.getItem(position);
				Log.d(TAG, "onItemLongClick media:" + media.getFilePath());
				onSingleItemSelected(position, view);
				
				return true;
			}
		});
		m_GridView.setOnTouchListener(new OnTouchListener() {
			int previousPosition = -1;
			int downx = -1;
			int downy = -1;
			// to know if this is a scroll gesture or multi-selection gesutre
			long downElapseTime;
//			boolean multiSelectToggleOn = false;
			@Override
			public boolean onTouch(View view, MotionEvent event) {
				
				// Don't process touch event if not in selection mode
				if(!m_IsSelectionMode)
					return false;
				
				int action = event.getActionMasked();
				switch (action) {
				case MotionEvent.ACTION_DOWN:
					Log.d(TAG, "onTouchListener ACTION_DOWN ");
					downElapseTime  = SystemClock.elapsedRealtime();
					downx = (int)event.getX();
					downy = (int)event.getY();
					m_TouchedPosition = ((GridView) view).pointToPosition(downx, downy);
					break;
				case MotionEvent.ACTION_MOVE:
					Log.d(TAG, "onTouchListener ACTION_MOVE ");
					long actionTimeDiff = SystemClock.elapsedRealtime() - downElapseTime;
					// if time diff < 200, we consider this gesture is a scroll action
					if(actionTimeDiff < 200) {
						return false;
					}
					int mx = (int)event.getX();
					int my = (int)event.getY();
					if(downx == -1 && downy == -1) {
						Log.w(TAG, "onTouchListener ACTION_MOVE downx == -1 && downy == -1");
					}else {
						int deltaX = downx > mx ? downx - mx : mx - downx;
						int deltaY = downy > my ? downy - my : my - downy;
						if(deltaX > 10 || deltaY > 10) {
							if(m_MultiSelectToggleOn == false)
								break;;
						}
					}
					int movingPosition = ((GridView) view).pointToPosition(mx, my);
					// finger remains in the same position for more than 200ms 
					// consider this gesture is a multi-select action
					// so set multiselectoggleOn=true
					if( movingPosition == m_TouchedPosition) {
						if(m_MultiSelectToggleOn == false) {
							m_MultiSelectToggleOn = true;
						}else {
							// already in selection mode, and anchorpostion is invalid
							// means another multi-select action is about to trigger
							if(m_AnchorPosition == GridView.INVALID_POSITION)
								m_AnchorPosition = m_TouchedPosition;
						}
					}
					
					if(movingPosition == GridView.INVALID_POSITION) {
						break;
					}
					if(m_MultiSelectToggleOn && movingPosition != previousPosition) {
						multipleSelect(action, (GridView) view, movingPosition);
						previousPosition = movingPosition;
					}
					
					break;
				case MotionEvent.ACTION_UP:
					Log.d(TAG, "onTouchListener ACTION_UP ");
					// multi-select action is done, but not yet leave selection mode
					// merge tempMedialist to selectionmedialist
					if(m_MultiSelectToggleOn) {
						if(!m_TempMeidaList.isEmpty()) {
							for(Media media: m_TempMeidaList) {
								m_SelectionMeidaList.add(media);
							}
						}
						m_TempMeidaList.clear();	
					}
					
					
					m_AnchorPosition = GridView.INVALID_POSITION;
					m_TouchedPosition= GridView.INVALID_POSITION;
					previousPosition = -1;
					downElapseTime = 0;
					downx = -1;
					downy = -1;
					/* TODO 
					if(y > 1250) {
						Log.e(TAG, "gridview scroll 15 px");
						gridview.smoothScrollByOffset (15);
					}
					*/
					
					// return true when multiSelectToggleOn, otherwise this gesture would be considered as a click event
					// and gridview.onItemClickListener will be called which is not what we want.
					if(m_MultiSelectToggleOn) {
						m_MultiSelectToggleOn = false;
						return true;
					}
					break;
				default:
					break;
				}
				if(m_MultiSelectToggleOn)
					return true;
				else
					return false;
				
			}
		});
		
		// ToolBar
		m_Toolbar = (Toolbar) view.findViewById(R.id.toolbar);
		m_Toolbar.inflateMenu(R.menu.selection_toolbar_menu);
		m_Toolbar.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				switch (item.getItemId()) {
				case R.id.toolbar_addto:
					//TODO
					break;
				case R.id.toolbar_share:
				{
					getGallery().shareMedia(getSelectedMedia());
					m_ToolbarActionShared = true;
					break;
				}
				case R.id.toolbar_delete:
					getGallery().deleteMedia(get(PROP_MEDIA_SET), getSelectedMedia(), m_DeleteCallback);
					break;
				}
				return false;
			}
		});
        m_Toolbar.setNavigationOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if(m_IsCameraRoll || m_IsSelectionMode) {
					exitSelectionMode();
					GridViewFragment.this.set(PROP_IS_SELECTION_MODE, false);
				}else {
					GridViewFragment.this.getGalleryActivity().goBack();
				}
			}
		});
        
        if(m_HasActionBar) {
        	if(!m_IsCameraRoll) {
        		m_Toolbar.setNavigationIcon(R.drawable.button_previous);
        		m_Toolbar.setTitle(m_ToolbarTitle);
            	m_Toolbar.setVisibility(View.VISIBLE);
            	
        	}
        	if(!m_IsSelectionMode) {
        		m_Toolbar.getMenu().setGroupVisible(R.id.selectModeActionGroup, false);
        	}
        }
        
        // EmptyMediaView
		m_EmptyMediaView = view.findViewById(R.id.no_photo);
		m_EmptyMediaView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				getGallery().startCamera();
			}
		});
		
		if(m_MediaList == null || m_MediaList.isEmpty())
			this.getHandler().postDelayed(emptyViewRunnable, 200);
		else
			m_GridView.setVisibility(View.VISIBLE);
		m_PreDecodeBitmapRunnable = new PreDecodeBitmapRunnable(this);
		m_GridView.setAdapter(m_GridViewItemAdapter);
		return view;
	}//end of onCreateView
	
	private Runnable emptyViewRunnable = new Runnable() {
		
		@Override
		public void run() {
			if(m_IsCameraRoll)
				m_EmptyMediaView.setVisibility(View.VISIBLE);
		}
	};
	protected void multipleSelect(int action, GridView gridview, int position) {
		switch (action) 
		{
			case MotionEvent.ACTION_DOWN:
				Log.w(TAG, "multipleSelect() - ACTION DOWN");
				break;
			case MotionEvent.ACTION_MOVE:
				Log.w(TAG, "multipleSelect() - ACTION MOVE");
				int selectposition = position;
				Log.w(TAG, "multipleSelect() - selectionPosition:" + selectposition);
				Log.w(TAG, "multipleSelect() - m_AnchorPosition:" + m_AnchorPosition);
				if(m_AnchorPosition != selectposition) {
					
					if(m_AnchorPosition == GridView.INVALID_POSITION) {
						View itemView = gridview.getChildAt(selectposition - gridview.getFirstVisiblePosition());
						if(itemView != null) {
							onMultiItemSelected(selectposition, itemView);
						}
						m_TouchedPosition = selectposition;
						break;
					}
					
					if(selectposition != GridView.INVALID_POSITION) {
						Log.w(TAG, "multipleSelect() - m_AnchorPosition: " + m_AnchorPosition + " selectposition: " + selectposition + "  m_TouchedPosition: " + m_TouchedPosition);
						
						if(m_TouchedPosition !=  selectposition) {
							int deviate = m_AnchorPosition - selectposition ;
							Log.w(TAG, "multipleSelect() - deviate: " + deviate);
//							int startIndex = deviate < 0 ? m_TouchedPosition : selectposition;
//							int endIndex = deviate < 0 ? selectposition : m_TouchedPosition;
						
							if(deviate > 0) {
								int startIndex = m_TouchedPosition;
								int endIndex = selectposition;
										
								if( selectposition < m_AnchorPosition && m_AnchorPosition < m_TouchedPosition){
									startIndex = m_AnchorPosition +1;
									endIndex = m_TouchedPosition;
									Log.w(TAG, "multipleSelect() - while loop 1");
									while(startIndex <= endIndex) {
										View itemView = null;
										itemView = gridview.getChildAt(startIndex- gridview.getFirstVisiblePosition());
										onMultiItemDeSelected(startIndex, itemView);
										++startIndex;
									}		
								}else {
									Log.w(TAG, "multipleSelect() - while loop 2");
									while(startIndex < endIndex) {
										View itemView = null;
										itemView = gridview.getChildAt(startIndex- gridview.getFirstVisiblePosition());
										onMultiItemDeSelected(startIndex, itemView);
										++startIndex;
									}		
								}
								Log.w(TAG, "multipleSelect()1 - deSelect Enter start: " + startIndex + " endIndex: " + endIndex);
							}else {
								int startIndex = m_TouchedPosition;
								int endIndex = selectposition;
								if( m_TouchedPosition < m_AnchorPosition && m_AnchorPosition < selectposition){
									startIndex = m_TouchedPosition;
									endIndex = m_AnchorPosition;
									Log.w(TAG, "multipleSelect() - while loop 3");
									while(startIndex < endIndex) {
										View itemView = null;
										itemView = gridview.getChildAt(startIndex- gridview.getFirstVisiblePosition());
										onMultiItemDeSelected(startIndex, itemView);
										++startIndex;
									}	
								}else {
									Log.w(TAG, "multipleSelect() - while loop 4 startIndex: " + startIndex + " endIndex: " + endIndex);
									while(startIndex >= endIndex) {
										View itemView = null;
										itemView = gridview.getChildAt(startIndex- gridview.getFirstVisiblePosition());
										onMultiItemDeSelected(startIndex, itemView);
										--startIndex;
										if(startIndex < 0)
											break;
									}	
								}
								
							}
						}
						
						int deviate = m_AnchorPosition - selectposition ;
						Log.w(TAG, "multipleSelect() - deviate: " + deviate);
						int startIndex = deviate < 0 ? selectposition : m_AnchorPosition;
						int endIndex = deviate < 0 ? m_AnchorPosition : selectposition;
						while(startIndex >= endIndex) {
							View itemView = null;
							itemView = gridview.getChildAt(startIndex- gridview.getFirstVisiblePosition());
							onMultiItemSelected(startIndex, itemView);
							--startIndex;
						}
						m_TouchedPosition = selectposition;
						
						
					}
					
				}else {
					//if selection position == m_AnchorPosition, then de-select all except anchorposition
					if(m_TouchedPosition > m_AnchorPosition) {
						for(int i = m_TouchedPosition; i > m_AnchorPosition; --i) {
							View itemView = null;
							itemView = gridview.getChildAt(i- gridview.getFirstVisiblePosition());
							onMultiItemDeSelected(i, itemView);
						}	
					}else {
						for(int i = m_TouchedPosition; i < m_AnchorPosition; ++i) {
							View itemView = null;
							itemView = gridview.getChildAt(i- gridview.getFirstVisiblePosition());
							onMultiItemDeSelected(i, itemView);
						}
					}
					
					m_TouchedPosition = m_AnchorPosition;
				}
				break;
			case MotionEvent.ACTION_UP:
				break;
			default:
				break;
		}
		Log.e(TAG, "multipleSelect() - exit m_TouchPosition: " + m_TouchedPosition );
	}


	@Override
	public void onDestroyView()
	{
		Log.d(TAG, "onDestroyView");
		// clear references
		if(m_GridView != null)
		{
			m_LastGridViewPosition = m_GridView.getFirstVisiblePosition();
			m_GridView.setAdapter(null);
			m_GridView.setOnItemClickListener(null);
			m_GridView.setOnItemLongClickListener(null);
			m_GridView.setOnTouchListener(null);
			m_GridView = null;
		}

		m_Toolbar = null;
		m_EmptyMediaView = null;	
		
		// call super
		super.onDestroyView();
	}


	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		Log.d(TAG, "onAttach");
		m_GridviewColumns = this.getResources().getInteger(R.integer.griview_columns);
	}

	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Log.d(TAG, "onActivityCreated");
	}

	
	private void removeDecodingHandle(Media media) {
		if(m_GridViewItemAdapter != null) {
			m_GridViewItemAdapter.removeDecodingHandle(media);
		}
	}
	
	private void removeLowResDecodingHandle(String filePath) {
		if(m_GridViewItemAdapter != null) {
			m_GridViewItemAdapter.removeLowResDecodingHandle(filePath);
		}
	}
	
	
	private class GridViewItemAdapter extends BaseAdapter {
		
		// Private fields
		private int visibleItemPosition = -1;
		private Context m_Context = null;
		private LayoutInflater m_inflater;
//		private HashSet<Handle> m_DecodeHandleSet = new HashSet<>();
		private HashMap<Media, Handle> m_HighResolutionDecodeHandleMap = new HashMap<>();
		private HashMap<String, Handle> m_LowResolutionDecodeHandleMap = new HashMap<>();
		public GridViewItemAdapter(Context context) {
			m_Context = context;
			m_inflater = (LayoutInflater) m_Context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}
		
		
		public void cancelAllBitmapDecodeTasks() {
			if(m_HighResolutionDecodeHandleMap != null && !m_HighResolutionDecodeHandleMap.isEmpty()) {
				for(Handle handle : m_HighResolutionDecodeHandleMap.values()) {
					Handle.close(handle);
				}
				m_HighResolutionDecodeHandleMap.clear();
			}
			if(m_LowResolutionDecodeHandleMap != null && !m_LowResolutionDecodeHandleMap.isEmpty()) {
				for(Handle handle : m_LowResolutionDecodeHandleMap.values()) {
					Handle.close(handle);
				}
				m_LowResolutionDecodeHandleMap.clear();
			}
		}
		
		public int getCount() {
			if(m_MediaList != null && !m_MediaList.isEmpty()) {
				if(m_IsCameraRoll)
					return m_MediaList.size() + 1;// +1 for the first one camera icon item
				return m_MediaList.size();
			}
			else
				return 0;
		}

		public Object getItem(int position) {
			Log.d(TAG, "getItem position: " + position);
			if(m_MediaList != null)
			{
				if(m_IsCameraRoll)
				{
					if(position == 0)
						return null;
					return m_MediaList.get(position - 1);
				}
				return m_MediaList.get(position);
			}
			return null;
		}

		public long getItemId(int position) {
			Log.d(TAG, "getItemId position: " + position);
			return position;
		}

		// create a new ImageView for each item referenced by the Adapter
		public View getView(int position, View convertView, ViewGroup parent) {
			if(visibleItemPosition == -1)
				visibleItemPosition = m_GridView.getFirstVisiblePosition();
			final GridViewItemHolder holder; 
			if (convertView == null) {
				// holder initialize
				convertView = m_inflater.inflate(R.layout.fragment_gridview_item, parent, false);
				holder = new GridViewItemHolder(convertView);
			} else {
				//recycled view
				holder = (GridViewItemHolder) convertView.getTag();
				((ViewGroup)holder.typeIconView.getParent()).setVisibility(View.GONE);
				holder.selectedImageView.setVisibility(View.GONE);
			}
			holder.position = position;
			
			
			if(m_IsSelectionMode) {
				if(m_SelectionMeidaList.contains(getItem(holder.position)) == false) {
					holder.selectedImageView.setVisibility(View.GONE);
				}else
					holder.selectedImageView.setVisibility(View.VISIBLE);
			}
			
			
			holder.highThumbDecoded = false;
			if(m_MediaList != null) {
				if(holder.position == 0 && m_IsCameraRoll) {
					// Set item thumbnail
					holder.thumbnailImageView.setImageResource(R.drawable.camera);
					holder.thumbnailImageView.setBackground(m_GreySquare);
					holder.thumbnailImageView.setScaleType(ScaleType.CENTER);
					holder.contentUri = null;
					holder.lowResolutionThumbDecodeHandle = null;
					holder.highResolutionThumbDecodeHandle = null;
				}else {
					
					// Temp workaround for fixing gridview flicker.
					// 1. lowThumb image is quickly set into thumbnailImageView 
					// 2. onMediaAdded called notifyDataSetChanged, greySquare is set into thumbnailImageView
					// 3. and finally the hightThumb image is setted
					// between step 1 and step2 is why the flicker happened
					// so when the first getView entered, keep the firstVisibleItem position,  
					// if the visibleItemPosition != m_GridView.getFirstVisiblePosition() means gridview is scrolling
					if(m_GridView != null && visibleItemPosition != m_GridView.getFirstVisiblePosition())
						holder.thumbnailImageView.setImageDrawable(m_GreySquare);
					
					holder.thumbnailImageView.setScaleType(ScaleType.CENTER_CROP);
					// -1 for the first one for CameraIcon to start camera activity
					Media media = m_MediaList.get(m_IsCameraRoll ? position - 1 : position);
					String filePath = media.getFilePath();
					holder.contentUri = media.getContentUri();
					holder.mimeType = media.getMimeType();
					if(filePath != null)
					{
						holder.lowResolutionThumbDecodeHandle = m_SmallBitmapPool.decode(media.getFilePath(), m_GridviewItemWidth, m_GridviewItemHeight, BitmapPool.FLAG_URGENT/*|BitmapPool.FLAG_ASYNC*/ , holder.lowResolutionThumbDecodeCallback, GridViewFragment.this.getHandler());
					}
					else
					{
						int mediaType = (media instanceof VideoMedia ? BitmapPool.MEDIA_TYPE_VIDEO : BitmapPool.MEDIA_TYPE_PHOTO);
						holder.lowResolutionThumbDecodeHandle = m_SmallBitmapPool.decode(getActivity(), holder.contentUri, mediaType, m_GridviewItemWidth, m_GridviewItemHeight, BitmapPool.FLAG_URGENT/*|BitmapPool.FLAG_ASYNC*/, holder.lowResolutionThumbDecodeCallback, GridViewFragment.this.getHandler());
					}
					holder.highResolutionThumbDecodeHandle = ThumbnailImageManager.decodeSmallThumbnailImage(media, ThumbnailImageManager.FLAG_URGENT, holder.hightResolutionThumbDecodeCallback, GridViewFragment.this.getHandler());
					m_LowResolutionDecodeHandleMap.put(filePath, holder.lowResolutionThumbDecodeHandle);
					m_HighResolutionDecodeHandleMap.put(media, holder.highResolutionThumbDecodeHandle);
					
					if(media instanceof VideoMedia) {
						((ViewGroup)holder.typeIconView.getParent()).setVisibility(View.VISIBLE);
						holder.typeIconView.setImageResource(R.drawable.ic_video);
						holder.durationTextView.setText(getVideoTime((VideoMedia)media));
					}
					
					// Pre-Decode Bitmap for grid view items which is not visible yet. 
					GridViewFragment.this.getHandler().removeCallbacks(m_PreDecodeBitmapRunnable);
					GridViewFragment.this.getHandler().postDelayed(m_PreDecodeBitmapRunnable,200);
				}
				
			}
			
			return convertView;
		}
		
		public void removeDecodingHandle(Media media) {
			if(m_HighResolutionDecodeHandleMap != null)
				m_HighResolutionDecodeHandleMap.remove(media);
		}
		
		public void removeLowResDecodingHandle(String filePath) {
			if(m_LowResolutionDecodeHandleMap != null)
				m_LowResolutionDecodeHandleMap.remove(filePath);
		}
	}


	
	private String getVideoTime(VideoMedia media) {
		long timeInmillisec = media.getDuration();
		long duration = timeInmillisec / 1000;
		long hours = duration / 3600;
		long minutes = (duration - hours * 3600) / 60;
		long seconds = duration - (hours * 3600 + minutes * 60);
		
		return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
	}
	
	
	private class SquareDrawable extends Drawable
	{
	    private final Paint mPaint;
	    private final Rect mRect;
	    private final int mWidth;
	    private final int mHeight;

	    public SquareDrawable(int width, int height)
	    {
	    	mWidth = width;
	    	mHeight = height;
	        mPaint = new Paint();
	        mRect = new Rect();
	    }

		@Override
		public void draw(Canvas canvas) {
			// Set the correct values in the Paint
	        mPaint.setARGB(255, 150, 150, 150);
	        mPaint.setStrokeWidth(2);
	        mPaint.setStyle(Style.FILL);
	        // Adjust the rect
	        mRect.left = 0;
	        mRect.top = 0;
	        mRect.right = mWidth;
	        mRect.bottom = mHeight;
	        // Draw it
	        canvas.drawRect(mRect, mPaint); 
			
		}

		@Override
		public void setAlpha(int alpha) {}

		@Override
		public void setColorFilter(ColorFilter cf) {}

		@Override
		public int getOpacity() {
			return PixelFormat.OPAQUE;
		}
	}
}
