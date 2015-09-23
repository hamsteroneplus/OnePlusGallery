package com.oneplus.gallery;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.oneplus.base.BaseFragment;

/**
 * Fragment to display media set list.
 */
public class MediaSetListFragment extends BaseFragment
{
	// Fields
	private Activity m_Activity;
	private MediaSetListAdapter m_MediaSetListAdapter;
	private ListView m_MediaSetListView;
	
	/**
	 * Initialize new MediaSetListFragment instance.
	 */
	public MediaSetListFragment()
	{
		this.setRetainInstance(true);
	}
	
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		m_Activity =  this.getActivity();
		m_MediaSetListAdapter = new MediaSetListAdapter();
	}

	// Create view.
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.fragment_media_set_list, container, false);
	}


	@Override
	public void onResume() {
		super.onResume();
		
		m_MediaSetListView = (ListView)getView().findViewById(R.id.media_set_listview);
		m_MediaSetListView.setAdapter(m_MediaSetListAdapter);
	}
	
	private class MediaSetListAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			return 5;
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			
			ViewInfo viewInfo;
			if(convertView == null)
			{
				LayoutInflater inflater = m_Activity.getLayoutInflater();
				convertView = inflater.inflate(R.layout.layout_media_set_list_item, parent, false);
				
				viewInfo = new ViewInfo();
				viewInfo.titleText = (TextView)convertView.findViewById(R.id.media_set_title);
				viewInfo.sizeTextView = (TextView)convertView.findViewById(R.id.media_set_size);
				viewInfo.coverImage = (ImageView)convertView.findViewById(R.id.media_set_cover_image);
				
				convertView.setTag(viewInfo);
			}
			else
				viewInfo = (ViewInfo)convertView.getTag();
			
			return convertView;
		}
		
	}
	
	// Class to keep menu item information in related view.
	private static final class ViewInfo
	{
		public TextView titleText;
		public TextView sizeTextView; 
		public ImageView coverImage;
	}
	
}
