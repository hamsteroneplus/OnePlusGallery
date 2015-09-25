package com.oneplus.gallery;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Hashtable;
import java.util.LinkedList;

import com.oneplus.base.BaseFragment;
import com.oneplus.base.EventHandler;
import com.oneplus.base.EventKey;
import com.oneplus.base.EventSource;
import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.base.PropertyChangeEventArgs;
import com.oneplus.base.PropertyChangedCallback;
import com.oneplus.base.PropertyKey;
import com.oneplus.base.PropertySource;
import com.oneplus.gallery.media.MediaComparator;
import com.oneplus.gallery.media.MediaList;
import com.oneplus.gallery.media.MediaSet;
import com.oneplus.gallery.media.MediaSetList;
import com.oneplus.media.BitmapPool;

/**
 * Fragment to display media set list.
 */
public class MediaSetListFragment extends BaseFragment
{
	private static final int MONITORED_RAGE = 5;
	
	// Fields
	private Activity m_Activity;
	private Button m_AddAlbumButton;
	private MediaSetListAdapter m_MediaSetListAdapter;
	private ListView m_MediaSetListView;
	private MediaSetList m_MediaSetList;
	private Hashtable<MediaSet, Object> m_MediaSetCoverImageTable = new Hashtable<>();
	private LinkedList<MediaSet> m_MediaSetDecodeQueue = new LinkedList<>();
	
	
	/**
	 * Property to get or set MediaSet
	 */
	public final static PropertyKey<MediaSetList> PROP_MEDIA_SET_LIST = new PropertyKey<>("MediaSetList", MediaSetList.class, MediaSetListFragment.class, 0, null);
	
	
	/**
	 * Raised after clicking single media set.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static final EventKey<ListItemEventArgs<MediaSet>> EVENT_MEDIA_SET_CLICKED = new EventKey<ListItemEventArgs<MediaSet>>("MediaSetClicked", (Class)ListItemEventArgs.class, MediaSetListFragment.class);
	
	
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
				input.setHint("�ڪ��̷R");
				input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(140)});
				input.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
		        new AlertDialog.Builder(m_Activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
		                .setTitle("�s�ع϶�")
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
		m_MediaSetListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				MediaSet set = m_MediaSetList.get(position);
				raise(EVENT_MEDIA_SET_CLICKED, new ListItemEventArgs<MediaSet>(position, set));
			}
		});
		m_MediaSetListView.setAdapter(m_MediaSetListAdapter);
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
	
	
	private boolean setMediaSetList(MediaSetList newList) {	
		
		Log.v(TAG, "setMediaSetList()");	
		
		MediaSetList oldList = m_MediaSetList;
		m_MediaSetList = newList;	
		
		if(newList == null)
		{
			Log.v(TAG, "setMediaSetList() - newList is null");
			
			// notify data changed
			if(m_MediaSetListAdapter != null)
				m_MediaSetListAdapter.notifyDataSetChanged();
		}
		else
		{
			// start to create cover image
			for(final MediaSet mediaSet : newList)
			{
				if(!m_MediaSetCoverImageTable.containsKey(mediaSet))
				{
					m_MediaSetCoverImageTable.put(mediaSet, new Object());
					m_MediaSetDecodeQueue.add(mediaSet);
					
					// add media count property change listener
					mediaSet.addCallback(MediaSet.PROP_MEDIA_COUNT, new PropertyChangedCallback<Integer>() {

						@Override
						public void onPropertyChanged(PropertySource source, PropertyKey<Integer> key, PropertyChangeEventArgs<Integer> e) {
							Log.v(TAG, "onPropertyChanged() - new media count : "+e.getNewValue());
							m_MediaSetDecodeQueue.add(mediaSet);
							createMediaListCoverImageFromQueue();
						}
					});
				}
			}	
			createMediaListCoverImageFromQueue();
		}
		
		return this.notifyPropertyChanged(PROP_MEDIA_SET_LIST, oldList, newList);
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
			viewInfo.sizeTextView.setText(String.valueOf(mediaSet.get(MediaSet.PROP_MEDIA_COUNT)));
			if(m_MediaSetCoverImageTable.get(mediaSet) instanceof Bitmap)
				viewInfo.coverImage.setImageBitmap((Bitmap)m_MediaSetCoverImageTable.get(mediaSet));
			else
				viewInfo.coverImage.setImageDrawable(null);
			
			return convertView;
		}
		
	}
	
	private void createMediaListCoverImageFromQueue()
	{
		if(m_MediaSetDecodeQueue.isEmpty())
		{
			Log.w(TAG, "createMediaListCoverImageFromQueue() - m_MediaSetDecodeQueue is empty");
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
		if(mediaSetSize <= 20)
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
				//Log.v(TAG, "onEventReceived() - m_MediaAddedHandler : e.getStartIndex() is "+e.getStartIndex()+" , e.getEndIndex() is "+e.getEndIndex());
				//Log.v(TAG, "onEventReceived() - m_MediaAddedHandler : e.getItemCount() is "+e.getItemCount());
				
				if(e.getEndIndex() == targetGridCount-1)
				{
					if(targetGridCount == 1)
					{
						BitmapPool.DEFAULT_THUMBNAIL.decode(mediaList.get(0).getFilePath(), 512, 512, 0, new BitmapPool.Callback() {
							@Override
							public void onBitmapDecoded(Handle handle, String filePath, Bitmap bitmap) {
						
								// update bitmap table
								m_MediaSetCoverImageTable.put(mediaSet, bitmap);
								
								// notify data changed
								if(m_MediaSetListAdapter != null)
									m_MediaSetListAdapter.notifyDataSetChanged();
								
								// decode next media set
								createMediaListCoverImageFromQueue();
							}
							
						}, getHandler());
					}
					else
					{
						Log.v(TAG, "onEventReceived() - create gridCoverImage");
						// create gridCoverImage
						final int coverWidth = m_Activity.getResources().getDisplayMetrics().widthPixels;
						final int coverHeight = m_Activity.getResources().getDimensionPixelSize(R.dimen.media_set_list_item_cover_image_height);
						
						final int gridSize = (int)Math.sqrt( (coverWidth * coverHeight) / targetGridCount);
						
						final Bitmap gridCover = Bitmap.createBitmap(coverWidth, coverHeight, Bitmap.Config.RGB_565);
						final Canvas canvas = new Canvas(gridCover);
						Log.v(TAG, "onEventReceived() - gridSize is :"+gridSize);
						
						for(int i=0; i<targetGridCount; i++)
						{
							final int index = i;
							BitmapPool.DEFAULT_THUMBNAIL.decode(mediaList.get(i).getFilePath(), gridSize, gridSize, 0, new BitmapPool.Callback() {
								@Override
								public void onBitmapDecoded(Handle handle, String filePath, Bitmap bitmap) {
							
									int rectLeft = (index * gridSize) % coverWidth;
									int rectTop = (index / gridPerRow) * gridSize;
									//Log.v(TAG, "onEventReceived() - index is :"+index+" , rectLeft is :"+rectLeft+ " , rectTop is "+rectTop);
									canvas.drawBitmap(bitmap, new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()), new Rect(rectLeft, rectTop, rectLeft+gridSize, rectTop+gridSize), null);
									
									// update bitmap table
									m_MediaSetCoverImageTable.put(mediaSet, gridCover);
									
									// notify data changed
									if(m_MediaSetListAdapter != null)
										m_MediaSetListAdapter.notifyDataSetChanged();
									
									// decode next media set
									createMediaListCoverImageFromQueue();
								}
								
							}, getHandler());
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
	}
	
}
