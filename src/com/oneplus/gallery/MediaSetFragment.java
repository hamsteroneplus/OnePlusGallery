package com.oneplus.gallery;


import com.oneplus.base.BaseFragment;
import com.oneplus.base.EventHandler;
import com.oneplus.base.EventKey;
import com.oneplus.base.EventSource;
import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.base.PropertyKey;
import com.oneplus.gallery.media.MediaComparator;
import com.oneplus.gallery.media.MediaList;
import com.oneplus.gallery.media.MediaSet;
import com.oneplus.media.BitmapPool;
import com.oneplus.media.BitmapPool.Callback;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
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
	private MediaSet m_MediaSet;
	/**
	 * Property to get or set MediaSet
	 */
	public final static PropertyKey<MediaSet> PROP_MEDIASET = new PropertyKey<>("MediaSet", MediaSet.class, MediaSetFragment.class, 0, null);

	// Adapter view holder
	static class GridViewItemHolder {
		ImageView m_ItemThumbnail;
		ImageView m_ItemType;
		TextView m_Videotime;
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
		if(m_MediaSet != null) {
			Log.d(TAG, "onInitialize openMediaList" );
			m_MediaList = m_MediaSet.openMediaList(MediaComparator.TAKEN_TIME, -1, 0);
			
		}
		
		
		//TODO Add EventCallback and notify data changed 
		
	}
	
	
	// Set property value.
	@SuppressWarnings("unchecked")
	@Override
	public <TValue> boolean set(PropertyKey<TValue> key, TValue value)
	{
		if(key == PROP_MEDIASET)
			return this.setMediaSet((MediaSet)value);
		
		return super.set(key, value);
	}
	
	
	private boolean setMediaSet(MediaSet value) {
		
		m_MediaSet = value;
		if(m_MediaSet == null)
			Log.d(TAG, "setMediaSet value null" );
		else
			Log.d(TAG, "setMediaSet value" );
		return true;
	}

	// Create view.
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		Log.d(TAG, "onCreateView");
		View view = inflater.inflate(R.layout.fragment_gridview, container, false);
		GridView gridview = (GridView) view.findViewById(R.id.gridview);
		View noPhotoView = view.findViewById(R.id.no_photo);
		if(m_MediaList != null) {
			gridview.setAdapter(new GridViewItemAdapter(this.getActivity()));
			gridview.setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
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
					Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
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
			if(m_MediaList != null)
				return m_MediaList.size();
			else
				return 0;
		}

		public Object getItem(int position) {
			if(m_MediaList != null)
				return m_MediaList.get(position);
			else 
				return null;
		}

		public long getItemId(int position) {
			return position;
		}

		// create a new ImageView for each item referenced by the Adapter
		public View getView(int position, View convertView, ViewGroup parent) {
			
			if (convertView == null) {
				// if it's not recycled, initialize some attributes
				convertView = m_inflater.inflate(R.layout.fragment_gridview_item, parent, false);
				// holder initialize
				m_Holder = new GridViewItemHolder();
				m_Holder.m_ItemThumbnail = (ImageView) convertView.findViewById(R.id.item_thumbnail);
				m_Holder.m_ItemType = (ImageView) convertView.findViewById(R.id.item_type);
				m_Holder.m_Videotime = (TextView) convertView.findViewById(R.id.item_video_time);
				
				// Set thumbnail
				if(m_MediaList != null) {
					BitmapPool.DEFAULT_THUMBNAIL.decode(m_MediaList.get(position).getFilePath(), 270, 270, new Callback() {
						@Override
						public void onBitmapDecoded(Handle handle, String filePath, Bitmap bitmap) {
							m_Holder.m_ItemThumbnail.setImageBitmap(bitmap);
						}
					}, MediaSetFragment.this.getHandler());	
				}
				
				
				// Check item type
				String mimeType = m_MediaList.get(position).getMimeType();
				if(mimeType.startsWith("video/"))
					m_Holder.m_ItemType.setImageResource(R.drawable.about);
				
				// set Tag
				convertView.setTag(m_Holder);
			} else {
				m_Holder = (GridViewItemHolder) convertView.getTag();
			}

			return convertView;
		}
	}
}
