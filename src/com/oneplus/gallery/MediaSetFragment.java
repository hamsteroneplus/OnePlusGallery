package com.oneplus.gallery;


import java.util.Locale;

import com.oneplus.base.BaseFragment;
import com.oneplus.base.EventHandler;
import com.oneplus.base.EventKey;
import com.oneplus.base.EventSource;
import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.base.PropertyKey;
import com.oneplus.gallery.media.Media;
import com.oneplus.gallery.media.MediaComparator;
import com.oneplus.gallery.media.MediaList;
import com.oneplus.gallery.media.MediaSet;
import com.oneplus.media.BitmapPool;
import com.oneplus.media.BitmapPool.Callback;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
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
import android.widget.ImageView.ScaleType;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Fragment to display media in media set.
 */
public class MediaSetFragment extends BaseFragment {

	// Private fields
	private MediaList m_MediaList = null;
	private GridViewItemAdapter m_GridViewItemAdapter;
	/**
	 * Property to get or set MediaSet
	 */
	public final static PropertyKey<MediaList> PROP_MEDIALIST = new PropertyKey<>("FragmentMediaList", MediaList.class, MediaSetFragment.class, 0, null);

	// Adapter view holder
	static class GridViewItemHolder {
		int m_ItemPosition;
		ImageView m_ItemThumbnail;
		ImageView m_ItemTypeIcon;
		TextView m_ItemVideotime;
		String m_ItemType;
	}
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");
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
	 * Initialize new MediaSetFragment instance.
	 */
	public MediaSetFragment() {
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
		if(key == PROP_MEDIALIST)
			return this.setMediaList((MediaList)value);
		
		return super.set(key, value);
	}
	
	
	private boolean setMediaList(MediaList value) {
		m_MediaList = value;
		if(m_MediaList == null)
			Log.d(TAG, "m_MediaList value null" );
		else {
			Log.d(TAG, "m_MediaList value" );
			m_MediaList.addHandler(MediaList.EVENT_MEDIA_ADDED, new EventHandler<ListChangeEventArgs>(){
				@Override
				public void onEventReceived(EventSource source, EventKey<ListChangeEventArgs> key, ListChangeEventArgs e) {
					Log.e(TAG, "EVENT_MEDIA_ADDED");
					showGridView();
					if(m_GridViewItemAdapter != null) {
						m_GridViewItemAdapter.notifyDataSetChanged();
					}
				}
				
			});
		}
		return true;
	}

	private void showGridView() {
		GridView gridview = (GridView) getView().findViewById(R.id.gridview);
		View noPhotoView = getView().findViewById(R.id.no_photo);
		m_GridViewItemAdapter = new GridViewItemAdapter(this.getActivity());
		gridview.setAdapter(m_GridViewItemAdapter);
		gridview.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Log.d(TAG, "gridview onItemClick event item position:" + position);
				Toast.makeText(MediaSetFragment.this.getActivity(), "" + position, Toast.LENGTH_SHORT).show();
			}
	    });	
		gridview.setVisibility(View.VISIBLE);
		noPhotoView.setVisibility(View.GONE);
		noPhotoView.setOnClickListener(null);
	}

	// Create view.
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		Log.d(TAG, "onCreateView");
		View view = inflater.inflate(R.layout.fragment_gridview, container, false);
		GridView gridview = (GridView) view.findViewById(R.id.gridview);
		View noPhotoView = view.findViewById(R.id.no_photo);
		if(m_MediaList != null && !m_MediaList.isEmpty()) {
			m_GridViewItemAdapter = new GridViewItemAdapter(this.getActivity());
			gridview.setAdapter(m_GridViewItemAdapter);
			gridview.setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
					Log.d(TAG, "gridview onItemClick event item position:" + position);
					Toast.makeText(MediaSetFragment.this.getActivity(), "" + position, Toast.LENGTH_SHORT).show();
				}
		    });	
			noPhotoView.setVisibility(View.GONE);	
		}else {
			gridview.setVisibility(View.GONE);
			noPhotoView.setVisibility(View.VISIBLE);
			noPhotoView.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent intent = new Intent("android.media.action.STILL_IMAGE_CAMERA");
				    startActivity(intent);
				}
			});
		}
		return view;
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
		private GridViewItemHolder m_Holder = null;

		public GridViewItemAdapter(Context context) {
			m_Context = context;
			m_inflater = (LayoutInflater) m_Context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		public int getCount() {
			Log.d(TAG, "getCount");
			int count = 0;
			if(m_MediaList != null && !m_MediaList.isEmpty()) {
				count += m_MediaList.size();
				return count +1 ;
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
					return m_MediaList.get(position);
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
				// holder initialize
				holder = new GridViewItemHolder();
				convertView = m_inflater.inflate(R.layout.fragment_gridview_item, parent, false);
				holder.m_ItemThumbnail = (ImageView) convertView.findViewById(R.id.item_thumbnail);
				holder.m_ItemTypeIcon = (ImageView) convertView.findViewById(R.id.item_type);
				holder.m_ItemVideotime = (TextView) convertView.findViewById(R.id.item_video_time);
				// set Tag
				convertView.setTag(holder);
			} else {
				holder = (GridViewItemHolder) convertView.getTag();
			}

			holder.m_ItemPosition = position;
			holder.m_ItemThumbnail.setOnTouchListener(new OnTouchListener() {
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					Log.d(TAG, "holder touch event view: " +  v.toString());
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
					holder.m_ItemThumbnail.setScaleType(ScaleType.CENTER);
					holder.m_ItemThumbnail.setImageResource(R.drawable.camera);
				}else {
					// -1 for the first one for CameraIcon to start camera activity
					final Media media = m_MediaList.get(holder.m_ItemPosition - 1);
					holder.m_ItemThumbnail.setScaleType(ScaleType.CENTER_CROP);
					holder.m_ItemType = media.getMimeType();
					BitmapPool.DEFAULT_THUMBNAIL.decode(media.getFilePath(), 270, 270, new Callback() {
						@Override
						public void onBitmapDecoded(Handle handle, String filePath, Bitmap bitmap) {
							// Set Item thumbnail
							holder.m_ItemThumbnail.setImageBitmap(bitmap);
							// Check item type
							if(holder.m_ItemType.startsWith("video/")) {
								((ViewGroup)holder.m_ItemTypeIcon.getParent()).setVisibility(View.VISIBLE);
								holder.m_ItemTypeIcon.setImageResource(R.drawable.about);
								holder.m_ItemVideotime.setText(getVideoTime(media));
							}
						}
					}, MediaSetFragment.this.getHandler());
				}
			}
			return convertView;
		}
	}


	public String getVideoTime(Media media) {
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
}
