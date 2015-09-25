package com.oneplus.gallery;


import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.oneplus.base.BaseFragment;
import com.oneplus.base.EventHandler;
import com.oneplus.base.EventKey;
import com.oneplus.base.EventSource;
import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.base.PropertyKey;
import com.oneplus.gallery.media.Media;
import com.oneplus.gallery.media.MediaList;
import com.oneplus.media.BitmapPool;
import com.oneplus.media.BitmapPool.Callback;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Fragment to display media in media set.
 */
public class GridViewFragment extends BaseFragment {

	// Private fields
	private MediaList m_MediaList = null;
	private GridView m_GridView;
	private GridViewItemAdapter m_GridViewItemAdapter;
	private Drawable m_GreySquare;
	private View m_NoMediaView;
	private int m_GridviewItemWidth;
	private int m_GridviewItemHeight;
	private static BitmapPool m_BitmapPool = new BitmapPool("GridViewFragmentBitmapPool", 64 << 20, Bitmap.Config.ARGB_8888, 3);
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
	static class GridViewItemHolder {
		int m_ItemPosition;
		ImageView m_ItemThumbnail;
		ImageView m_ItemTypeIcon;
		TextView m_ItemVideotime;
		String m_ItemType;
		String m_ItemFilePath;
		Handle m_DecodeHandle;
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
	
	
	
	/**
	 * Get all selected media.
	 * @return List of selected media.
	 */
	public List<Media> getSelectedMedia()
	{
		return Collections.EMPTY_LIST;
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
		if(index < 0 || index >= m_MediaList.size())
		{
			Log.e(TAG, "onItemClicked() - Invalid index : " + index);
			return;
		}
		
		// raise event
		Media media = m_MediaList.get(index);
		this.raise(EVENT_MEDIA_CLICKED, new ListItemEventArgs<Media>(index, media));
	}
	
	private void onMediaAdded(ListChangeEventArgs e)
	{
		// show grid view
		if(!m_MediaList.isEmpty())
			this.showGridView();
		
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

	@Override
	public void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		Log.d(TAG, "onPause");
	}

	@Override
	public void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		Log.d(TAG, "onResume");
		
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
	
	
	// Set property value.
	@SuppressWarnings("unchecked")
	@Override
	public <TValue> boolean set(PropertyKey<TValue> key, TValue value)
	{
		if(key == PROP_MEDIA_LIST)
			return this.setMediaList((MediaList)value);
		
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
		if(value != null && !value.isEmpty())
			this.showGridView();
		else
			this.showNoMedia();
		
		// complete
		return true;
	}

	private void showGridView() {
		
		if(m_GridView == null)
			return;
		m_GridView.setVisibility(View.VISIBLE);
		m_NoMediaView.setVisibility(View.GONE);
	}
	
	private void showNoMedia()
	{
		if(m_NoMediaView == null)
			return;
		m_GridView.setVisibility(View.GONE);
		if(this.get(PROP_IS_CAMERA_ROLL))
			m_NoMediaView.setVisibility(View.VISIBLE);
	}

	// Create view.
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		Log.d(TAG, "onCreateView");
		View view = inflater.inflate(R.layout.fragment_gridview, container, false);
		m_GridView = (GridView) view.findViewById(R.id.gridview);
		if(m_GridViewItemAdapter == null)
			m_GridViewItemAdapter = new GridViewItemAdapter(this.getActivity());
		m_GridView.setAdapter(m_GridViewItemAdapter);
		m_GridView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Log.d(TAG, "gridview onItemClick event item position:" + position);
				Toast.makeText(GridViewFragment.this.getActivity(), "" + position, Toast.LENGTH_SHORT).show();
				onItemClicked(position, view);
			}
	    });	
		
		m_NoMediaView = view.findViewById(R.id.no_photo);
		m_NoMediaView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent("android.media.action.STILL_IMAGE_CAMERA");
			    startActivity(intent);
			}
		});
		
		if(m_MediaList != null && !m_MediaList.isEmpty())
			this.showGridView();
		else
			this.showNoMedia();
		
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
		m_NoMediaView = null;
		// call super
		super.onDestroyView();
	}


	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		Log.d(TAG, "onAttach");
	}

	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Log.d(TAG, "onActivityCreated");
	}


	private class GridViewItemAdapter extends BaseAdapter {
		
		// Private fields
		private Context m_Context = null;
		private LayoutInflater m_inflater;

		public GridViewItemAdapter(Context context) {
			m_Context = context;
			m_inflater = (LayoutInflater) m_Context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		public int getCount() {
			int count = 0;
			if(m_MediaList != null && !m_MediaList.isEmpty()) {
				count += m_MediaList.size();
				return count + 1;// +1 for the first one camera icon item
			}
			else
				return 0;
		}

		public Object getItem(int position) {
			Log.d(TAG, "getItem position: " + position);
			if(position == 0) {
				Log.d(TAG, "getItem 0");
			}else {
				if(m_MediaList != null)
					return m_MediaList.get(position -1);
				else 
					return null;	
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
				holder = new GridViewItemHolder();
				convertView = m_inflater.inflate(R.layout.fragment_gridview_item, parent, false);
				holder.m_ItemThumbnail = (ImageView) convertView.findViewById(R.id.item_thumbnail);
				holder.m_ItemTypeIcon = (ImageView) convertView.findViewById(R.id.item_type);
				holder.m_ItemVideotime = (TextView) convertView.findViewById(R.id.item_video_time);
				// set Tag
				convertView.setTag(holder);
			} else {
				//recycled view
				holder = (GridViewItemHolder) convertView.getTag();
				((ViewGroup)holder.m_ItemTypeIcon.getParent()).setVisibility(View.GONE);
			}
			holder.m_ItemThumbnail.setImageDrawable(m_GreySquare);
			holder.m_ItemPosition = position;
			
			holder.m_ItemThumbnail.setOnTouchListener(new OnTouchListener() {
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					//Log.d(TAG, "holder touch event view: " +  v.toString());
					if(holder.m_ItemPosition == 0) {
						Intent intent = new Intent("android.media.action.STILL_IMAGE_CAMERA");
						startActivity(intent);
					}
					return false;
				}
			});
			
			if(m_MediaList != null) {
				if(holder.m_ItemPosition == 0) {
					// Set item thumbnail
					holder.m_ItemThumbnail.setImageResource(R.drawable.camera);
					holder.m_ItemFilePath = "cameraIcon";
				}else {
					Log.e(TAG, "holder.m_ItemPosition: " + holder.m_ItemPosition);
					// -1 for the first one for CameraIcon to start camera activity
					final Media media = m_MediaList.get(position - 1);
					holder.m_ItemFilePath = media.getFilePath();
					holder.m_ItemType = media.getMimeType();
					holder.m_DecodeHandle = m_BitmapPool.decode(media.getFilePath(), m_GridviewItemWidth, m_GridviewItemHeight,BitmapPool.FLAG_ASYNC|BitmapPool.FLAG_URGENT, new Callback() {
						@Override
						public void onBitmapDecoded(Handle handle, String filePath, Bitmap bitmap) {
							// if decode handle is not the same, means the origin holder is recycled, don't update image to that holder
							if(handle != holder.m_DecodeHandle)
								return;
							// Set Item thumbnail
							holder.m_ItemThumbnail.setImageBitmap(bitmap);
							// Check item type
							if(holder.m_ItemType.startsWith("video/")) {
								((ViewGroup)holder.m_ItemTypeIcon.getParent()).setVisibility(View.VISIBLE);
								holder.m_ItemTypeIcon.setImageResource(R.drawable.about);
								holder.m_ItemVideotime.setText(getVideoTime(media));
							}
						}
					}, GridViewFragment.this.getHandler());
				}
			}
			return convertView;
		}
	}


	
	private String getVideoTime(Media media) {
		MediaMetadataRetriever retriever = new MediaMetadataRetriever();
		retriever.setDataSource(media.getFilePath());
		String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
		long timeInmillisec = Long.parseLong( time );
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
