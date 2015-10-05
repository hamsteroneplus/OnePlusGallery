package com.oneplus.gallery;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

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
import com.oneplus.gallery.media.MediaComparator;
import com.oneplus.gallery.media.MediaList;
import com.oneplus.gallery.media.MediaSet;
import com.oneplus.gallery.media.MediaSetList;
import com.oneplus.media.BitmapPool;
import com.oneplus.media.CenterCroppedBitmapPool;

/**
 * Fragment to display media set list.
 */
public class MediaSetListFragment extends GalleryFragment
{
	// Fields
	private Activity m_Activity;
	private RelativeLayout m_AddAlbumButton;
	private MediaSetListAdapter m_MediaSetListAdapter;
	private ListView m_MediaSetListView;
	private MediaSetList m_MediaSetList;
	private Hashtable<MediaSet, Object> m_MediaSetCoverImageTable = new Hashtable<>();
	private LinkedList<MediaSet> m_MediaSetDecodeQueue = new LinkedList<>();
	private static BitmapPool m_SmallBitmapPool = new CenterCroppedBitmapPool("MediaSetListFragmentSmallBitmapPool", 32 << 20, Bitmap.Config.RGB_565, 4, BitmapPool.FLAG_NO_EMBEDDED_THUMB);
	
	
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
		
		m_AddAlbumButton = (RelativeLayout)getView().findViewById(R.id.add_album_buttom);
		m_MediaSetListView = (ListView)getView().findViewById(R.id.media_set_listview);
		
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
							
							// notify data changed
							if(m_MediaSetListAdapter != null)
								m_MediaSetListAdapter.notifyDataSetChanged();
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
			Integer mediaCount = mediaSet.get(MediaSet.PROP_MEDIA_COUNT);
			viewInfo.sizeTextView.setText(mediaCount != null ? String.valueOf(mediaCount) : "");
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
				
				// check if updated index located in the range that needs to show images
				if(e.getStartIndex() <= targetGridCount-1 && e.getEndIndex() >= targetGridCount-1)
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
						// create gridCoverImage
						final int coverWidth = m_Activity.getResources().getDisplayMetrics().widthPixels;
						final int coverHeight = m_Activity.getResources().getDimensionPixelSize(R.dimen.media_set_list_item_cover_image_height);
						
						final int gridSize = (int)Math.sqrt( (coverWidth * coverHeight) / targetGridCount);
						
						final Bitmap gridCover = Bitmap.createBitmap(coverWidth, coverHeight, Bitmap.Config.RGB_565);
						final Canvas canvas = new Canvas(gridCover);
						
						for(int i=0; i<targetGridCount; i++)
						{
							final int index = i;
							
							m_SmallBitmapPool.decode(mediaList.get(i).getFilePath(), gridSize, gridSize, BitmapPool.FLAG_ASYNC | BitmapPool.FLAG_URGENT, new BitmapPool.Callback() {
								@Override
								public void onBitmapDecoded(Handle handle, String filePath, Bitmap bitmap) {
							
									// gridCoverImageRect
									int rectLeft = (index * gridSize) % coverWidth;
									int rectTop = (index / gridPerRow) * gridSize;
									
									// Bitmap Rect
									int bitmapRectLeft = 0;
									int bitmapRectTop = 0;
									int shortSide = 0;
									if(bitmap.getHeight() >= bitmap.getWidth())
									{
										shortSide = bitmap.getWidth();
										bitmapRectTop = (bitmap.getHeight() - bitmap.getWidth())/2;
									}
									else
									{
										shortSide = bitmap.getHeight();
										bitmapRectLeft = (bitmap.getWidth() - bitmap.getHeight())/2;
									}	
									canvas.drawBitmap(bitmap, new Rect(bitmapRectLeft, bitmapRectTop, bitmapRectLeft+shortSide, bitmapRectTop+shortSide), new Rect(rectLeft, rectTop, rectLeft+gridSize, rectTop+gridSize), null);
									
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
