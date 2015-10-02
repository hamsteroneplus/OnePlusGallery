package com.oneplus.gallery;


import java.lang.ref.WeakReference;
import java.util.ArrayList;
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
import com.oneplus.gallery.media.VideoMedia;
import com.oneplus.media.BitmapPool;
import com.oneplus.media.CenterCroppedBitmapPool;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
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
	private MediaList m_MediaList = null;
	private GridView m_GridView;
	private GridViewItemAdapter m_GridViewItemAdapter;
	private Drawable m_GreySquare;
	private View m_EmptyMediaView;
	private int m_GridviewColumns;
	private int m_GridviewItemWidth;
	private int m_GridviewItemHeight;
	private boolean m_IsSelectionMode = false;
	private PreDecodeBitmapRunnable m_PreDecodeBitmapRunnable;
	private ImageView m_SelectedImage;
	private HashSet<Integer> m_SelectionSet = new HashSet<>(); 
	private Toolbar m_Toolbar;
	
	private static BitmapPool m_BitmapPool = new CenterCroppedBitmapPool("GridViewFragmentBitmapPool", 64 << 20, Bitmap.Config.ARGB_8888, 3);
	private static BitmapPool m_SmallBitmapPool = new CenterCroppedBitmapPool("GridViewFragmentSmallBitmapPool", 32 << 20, Bitmap.Config.RGB_565, 4, BitmapPool.FLAG_USE_EMBEDDED_THUMB_ONLY);
	
	
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
		public final BitmapPool.Callback smallThumbDecodeCallback = new BitmapPool.Callback()
		{
			public void onBitmapDecoded(Handle handle, Uri contentUri, Bitmap bitmap) 
			{
				if(handle == smallThumbDecodeHandle && bitmap != null && !thumbDecoded)
					thumbnailImageView.setImageBitmap(bitmap);
			}
			public void onBitmapDecoded(Handle handle, String filePath, Bitmap bitmap) 
			{
				if(handle == smallThumbDecodeHandle && bitmap != null && !thumbDecoded)
					thumbnailImageView.setImageBitmap(bitmap);
			}
		};
		public Handle smallThumbDecodeHandle;
		public final BitmapPool.Callback thumbDecodeCallback = new BitmapPool.Callback()
		{
			public void onBitmapDecoded(Handle handle, Uri contentUri, Bitmap bitmap) 
			{
				if(handle == thumbDecodeHandle && bitmap != null)
				{
					thumbDecoded = true;
					thumbnailImageView.setImageBitmap(bitmap);
					GridViewFragment.this.removeDecodingHandle(handle);
				}
			}
			public void onBitmapDecoded(Handle handle, String filePath, Bitmap bitmap)
			{
				if(handle == thumbDecodeHandle && bitmap != null)
				{
					thumbDecoded = true;
					thumbnailImageView.setImageBitmap(bitmap);
					GridViewFragment.this.removeDecodingHandle(handle);
				}
			}
		};
		public Handle thumbDecodeHandle;
		public boolean thumbDecoded;
		
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
		
		private final BitmapPool.Callback preDecodeCallback = new BitmapPool.Callback()
		{
			public void onBitmapDecoded(Handle handle, String filePath, Bitmap bitmap)
			{
				if(bitmap != null)
				{
					Log.d("GridViewFragment", "predecodebitmap done remove handle:" + handle.toString());
					m_HandleSet.remove(handle);
				}
			}
		};
		@Override
		public void run() {
			if(m_ActivityRef != null) {
				final MediaList medialist = m_ActivityRef.get().m_MediaList;
				if(medialist == null)
					return;
				final int visibleLastposition = m_ActivityRef.get().m_GridView.getLastVisiblePosition();
				final int visibleFirstposition = m_ActivityRef.get().m_GridView.getFirstVisiblePosition();
				final int itemWidth = m_ActivityRef.get().m_GridviewItemWidth;
				final int itemHeight = m_ActivityRef.get().m_GridviewItemHeight;
				final Handler handler = m_ActivityRef.get().getHandler();
				
				for(int i = visibleLastposition; i < (visibleLastposition + PRE_DECODE_BITMAP_COUNTS) && i < medialist.size(); ++i) {
					Media media = medialist.get(i);
					Handle handle = m_BitmapPool.decode(media.getFilePath(), itemWidth, itemHeight, BitmapPool.FLAG_ASYNC, preDecodeCallback, handler);
					m_HandleSet.add(handle);
				}
				
				for(int i = visibleFirstposition; i > (visibleFirstposition - PRE_DECODE_BITMAP_COUNTS) && i >= 0; --i) {
					Media media = medialist.get(i);
					Handle handle = m_BitmapPool.decode(media.getFilePath(), itemWidth, itemHeight, BitmapPool.FLAG_ASYNC, preDecodeCallback, handler);
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



	private void cancelAllBitmapDecodeTasks() {
		if(m_GridViewItemAdapter != null) {
			m_GridViewItemAdapter.cancelAllBitmapDecodeTasks();
		}
	}

	
	private void cancelAllSelection() {
		final int size = m_GridView.getChildCount();
		for(int i = 0; i < size ; ++i) {
			ViewGroup gridChild = (ViewGroup) m_GridView.getChildAt(i);
			  int childSize = gridChild.getChildCount();
			  for(int k = 0; k < childSize; k++) {
			    if( gridChild.getChildAt(k).getId() == R.id.item_selected) {
			      gridChild.getChildAt(k).setVisibility(View.GONE);
			    }
			  }
		}
		m_SelectionSet.clear();
		this.set(PROP_IS_SELECTION_MODE, false);
		m_Toolbar.setVisibility(View.GONE);
	}
	/**
	 * Get all selected media.
	 * @return List of selected media.
	 */
	public List<Media> getSelectedMedia()
	{
		if(m_SelectionSet == null)
			return null;
		if(m_SelectionSet.isEmpty()) 
			return null;
		// Prepare selected media list
		List<Media> mediaList = new ArrayList<>();
		for(int selectedItem : m_SelectionSet) {
			Media media = m_MediaList.get(selectedItem)	;
			mediaList.add(media);
		}
		
		return mediaList;
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
	
	private void onItemClicked(int index, View view)
	{
		// check state
		if(m_MediaList == null)
			return;
		if(this.get(PROP_IS_CAMERA_ROLL))
		{
			if(index == 0)
			{
				this.startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
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
	
	private void onItemSelected(int index, View view) {
		m_SelectedImage = (ImageView) view.findViewById(R.id.item_selected);
		// De-Select
		if(!m_SelectionSet.isEmpty() && m_SelectionSet.contains(Integer.valueOf(index))) {
			m_SelectionSet.remove(index);
			m_SelectedImage.setVisibility(View.GONE);
		}else {
			m_SelectedImage.setVisibility(View.VISIBLE);
			m_SelectionSet.add(index);	
		}
		
		
		// Exit selection mode if selection set is empty
		if(m_SelectionSet.isEmpty()) {
			this.set(PROP_IS_SELECTION_MODE, false);
			m_Toolbar.setVisibility(View.GONE);
			return;
		}
		Resources res = this.getActivity().getResources();
		String selectedItems = String.format(res.getString(R.string.toolbar_selection_total), m_SelectionSet.size());
		m_Toolbar.setTitle(selectedItems);
	}
	
	private void onMediaAdded(ListChangeEventArgs e)
	{
		// refresh items
		if(m_GridViewItemAdapter != null)
			m_GridViewItemAdapter.notifyDataSetChanged();
	}
	
	private void onMediaRemoved(ListChangeEventArgs e)
	{
		// hide grid view
		if(m_MediaList.isEmpty())
			;
		
		// refresh items
		if(m_GridViewItemAdapter != null)
			m_GridViewItemAdapter.notifyDataSetChanged();
	}


	/**
	 * Initialize new GridViewFragment instance.
	 */
	public GridViewFragment() {
		this.setRetainInstance(true);
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
		Log.d(TAG, "onResume()");
		super.onResume();
		
	}


	@Override
	public void onStop() {
		super.onStop();
		
		// Shrink BitmapPool size to 16MB when Gallery is not visible
		m_BitmapPool.shrink(16 << 20);
		m_SmallBitmapPool.shrink(16 << 20);
		
		// Cancel on-going visible gridview items 
		cancelAllBitmapDecodeTasks();
		
		// Cancel on-going invisible gridview items(pre-decode)
		if(m_PreDecodeBitmapRunnable != null) {
			m_PreDecodeBitmapRunnable.cancelAllBitmapDecoding();
			getHandler().removeCallbacks(m_PreDecodeBitmapRunnable);
		}
		
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
		if(key == PROP_IS_SELECTION_MODE) 
			m_IsSelectionMode = (boolean) value;
		
		return super.set(key, value);
	}
	
	
	private boolean setMediaList(MediaList value) {
		// check instance
		if(m_MediaList == value)
			return false;
		
		// detach from previous media list
		if(m_MediaList != null)
		{
			m_MediaList.removeHandler(MediaList.EVENT_MEDIA_ADDED, m_MediaAddedHandler);
			m_MediaList.removeHandler(MediaList.EVENT_MEDIA_REMOVED, m_MediaRemovedHandler);
		}
		
		// attach to new media list
		m_MediaList = value;
		if(m_MediaList == null)
			Log.d(TAG, "m_MediaList value null" );
		else {
			Log.d(TAG, "m_MediaList value" );
			m_MediaList.addHandler(MediaList.EVENT_MEDIA_ADDED, m_MediaAddedHandler);
			m_MediaList.addHandler(MediaList.EVENT_MEDIA_REMOVED, m_MediaRemovedHandler);
		}
		
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
		
	} 


	// Create view.
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		Log.d(TAG, "onCreateView");
		View view = inflater.inflate(R.layout.fragment_gridview, container, false);
		
		// GridView
		m_GridView = (GridView) view.findViewById(R.id.gridview);
		m_GridView.setNumColumns(m_GridviewColumns);
		if(m_GridViewItemAdapter == null)
			m_GridViewItemAdapter = new GridViewItemAdapter(this.getActivity());
		m_GridView.setAdapter(m_GridViewItemAdapter);
		m_GridView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Log.d(TAG, "gridview onItemClick event item position:" + position);
				if(m_IsSelectionMode) {
					onItemSelected(position, view);
				}else {
					onItemClicked(position, view);
				}
			}
	    });	
		m_GridView.setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				Log.d(TAG, "gridview onItemLongClick event item position:" + position);
				m_Toolbar.setNavigationIcon(R.drawable.ic_cancel);
				m_Toolbar.setVisibility(View.VISIBLE);
				GridViewFragment.this.set(PROP_IS_SELECTION_MODE, true);
				onItemSelected(position, view);
				return true;
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
					//TODO
					break;
				case R.id.toolbar_delete:
					//TODO
					break;
				}
				return false;
			}
		});
        m_Toolbar.setNavigationOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Log.d(TAG, "setNavigationOnClickListener onClick");
				cancelAllSelection();
				
			}
		});
        
        // EmptyMediaView
		m_EmptyMediaView = view.findViewById(R.id.no_photo);
		m_EmptyMediaView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent("android.media.action.STILL_IMAGE_CAMERA");
			    startActivity(intent);
			}
		});
		
		m_GridView.setEmptyView(m_EmptyMediaView);
		m_PreDecodeBitmapRunnable = new PreDecodeBitmapRunnable(this);
		return view;
	}


	@Override
	public void onDestroyView()
	{
		Log.d(TAG, "onDestroyView");
		// clear references
		if(m_GridView != null)
		{
			m_GridView.setAdapter(null);
			m_GridView = null;
		}
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

	
	private void removeDecodingHandle(Handle handle) {
		if(m_GridViewItemAdapter != null) {
			m_GridViewItemAdapter.removeDecodingHandle(handle);;
		}
	}
	
	
	private class GridViewItemAdapter extends BaseAdapter {
		
		// Private fields
		private Context m_Context = null;
		private LayoutInflater m_inflater;
		private HashSet<Handle> m_DecodeHandleSet = new HashSet<>();
		
		public GridViewItemAdapter(Context context) {
			m_Context = context;
			m_inflater = (LayoutInflater) m_Context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		public void cancelAllBitmapDecodeTasks() {
			if(m_DecodeHandleSet != null && !m_DecodeHandleSet.isEmpty()) {
				for(Handle handle : m_DecodeHandleSet) {
					Handle.close(handle);
				}
				m_DecodeHandleSet.clear();
			}
		}
		
		public int getCount() {
			if(m_MediaList != null && !m_MediaList.isEmpty()) {
				if(get(PROP_IS_CAMERA_ROLL))
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
				if(get(PROP_IS_CAMERA_ROLL))
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
			final GridViewItemHolder holder; 
			if (convertView == null) {
				//Log.d(TAG, "convertView == null getView position:" + position);
				// holder initialize
				convertView = m_inflater.inflate(R.layout.fragment_gridview_item, parent, false);
				holder = new GridViewItemHolder(convertView);
			} else {
				//recycled view
				holder = (GridViewItemHolder) convertView.getTag();
				((ViewGroup)holder.typeIconView.getParent()).setVisibility(View.GONE);
				
			}
			holder.thumbnailImageView.setImageDrawable(m_GreySquare);
			holder.position = position;
			if(m_IsSelectionMode) {
				if(m_SelectionSet.contains(holder.position) == false) {
					holder.selectedImageView.setVisibility(View.GONE);
				}else
					holder.selectedImageView.setVisibility(View.VISIBLE);
			}
			holder.thumbDecoded = false;
			boolean isCameraRoll = get(PROP_IS_CAMERA_ROLL);
			if(m_MediaList != null) {
				if(holder.position == 0 && isCameraRoll) {
					// Set item thumbnail
					holder.thumbnailImageView.setImageResource(R.drawable.camera);
					holder.contentUri = null;
					holder.smallThumbDecodeHandle = null;
					holder.thumbDecodeHandle = null;
				}else {
					Log.e(TAG, "holder.m_ItemPosition: " + holder.position);
					// -1 for the first one for CameraIcon to start camera activity
					Media media = m_MediaList.get(isCameraRoll ? position - 1 : position);
					String filePath = media.getFilePath();
					holder.contentUri = media.getContentUri();
					holder.mimeType = media.getMimeType();
					if(filePath != null)
					{
						holder.smallThumbDecodeHandle = m_SmallBitmapPool.decode(media.getFilePath(), m_GridviewItemWidth, m_GridviewItemHeight, BitmapPool.FLAG_ASYNC | BitmapPool.FLAG_URGENT, holder.smallThumbDecodeCallback, GridViewFragment.this.getHandler());
						holder.thumbDecodeHandle = m_BitmapPool.decode(media.getFilePath(), m_GridviewItemWidth, m_GridviewItemHeight, BitmapPool.FLAG_ASYNC | BitmapPool.FLAG_URGENT, holder.thumbDecodeCallback, GridViewFragment.this.getHandler());
					}
					else
					{
						int mediaType = (media instanceof VideoMedia ? BitmapPool.MEDIA_TYPE_VIDEO : BitmapPool.MEDIA_TYPE_PHOTO);
						holder.smallThumbDecodeHandle = m_SmallBitmapPool.decode(getActivity(), holder.contentUri, mediaType, m_GridviewItemWidth, m_GridviewItemHeight, BitmapPool.FLAG_ASYNC | BitmapPool.FLAG_URGENT, holder.smallThumbDecodeCallback, GridViewFragment.this.getHandler());
						holder.thumbDecodeHandle = m_BitmapPool.decode(getActivity(), holder.contentUri, mediaType, m_GridviewItemWidth, m_GridviewItemHeight, BitmapPool.FLAG_ASYNC | BitmapPool.FLAG_URGENT, holder.thumbDecodeCallback, GridViewFragment.this.getHandler());
					}
					
					m_DecodeHandleSet.add(holder.smallThumbDecodeHandle);
					m_DecodeHandleSet.add(holder.thumbDecodeHandle);
					
					if(media instanceof VideoMedia) {
						((ViewGroup)holder.typeIconView.getParent()).setVisibility(View.VISIBLE);
						holder.typeIconView.setImageResource(R.drawable.about);
						holder.durationTextView.setText(getVideoTime((VideoMedia)media));
					}
					
					// Pre-Decode Bitmap for grid view items which is not visible yet. 
					GridViewFragment.this.getHandler().removeCallbacks(m_PreDecodeBitmapRunnable);
					GridViewFragment.this.getHandler().postDelayed(m_PreDecodeBitmapRunnable,200);
				}
				
			}
			
			return convertView;
		}
		
		public void removeDecodingHandle(Handle handle) {
			if(m_DecodeHandleSet != null)
				m_DecodeHandleSet.remove(handle);
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
	        mPaint.setARGB(255, 125, 125, 125);
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
