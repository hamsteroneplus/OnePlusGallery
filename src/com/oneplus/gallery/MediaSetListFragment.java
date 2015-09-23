package com.oneplus.gallery;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
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
	private Button m_AddAlbumButton;
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
		
		m_AddAlbumButton = (Button)getView().findViewById(R.id.add_album_buttom);
		m_MediaSetListView = (ListView)getView().findViewById(R.id.media_set_listview);
		
		m_AddAlbumButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// show dialog
				final EditText input = new EditText(m_Activity);
				input.setHint("我的最愛");
				input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(140)});
				input.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
		        new AlertDialog.Builder(m_Activity)
		                .setTitle("新建圖集")
		                .setView(input)
		                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
		                    public void onClick(DialogInterface dialog, int whichButton) {
		                    	String newAlbumName = input.getText().toString();

		                    	// TODO: create new album
		                    	
		            			InputMethodManager imm = (InputMethodManager)m_Activity.getSystemService(Context.INPUT_METHOD_SERVICE);
		            			imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
		                    }
		                })
		                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
		                    public void onClick(DialogInterface dialog, int whichButton) {
		                    	InputMethodManager imm = (InputMethodManager)m_Activity.getSystemService(Context.INPUT_METHOD_SERVICE);
		            			imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
		                    	dialog.dismiss();
		                    }
		                })
                .show();
			}
		});
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
