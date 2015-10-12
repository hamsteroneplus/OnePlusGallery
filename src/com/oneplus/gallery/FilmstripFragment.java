package com.oneplus.gallery;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Size;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.oneplus.base.EventHandler;
import com.oneplus.base.EventKey;
import com.oneplus.base.EventSource;
import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.base.PropertyKey;
import com.oneplus.base.ScreenSize;
import com.oneplus.gallery.media.Media;
import com.oneplus.gallery.media.MediaList;
import com.oneplus.gallery.media.PhotoMedia;
import com.oneplus.gallery.media.ThumbnailImageManager;
import com.oneplus.gallery.media.VideoMedia;
import com.oneplus.media.BitmapPool;
import com.oneplus.widget.FilmstripView;
import com.oneplus.widget.ScaleImageView;
import com.oneplus.widget.ScaleImageView.BoundsType;

/**
 * Filmstrip fragment.
 */
public class FilmstripFragment extends GalleryFragment
{
	/**
	 * Property to get or set index of current media.
	 */
	public final static PropertyKey<Integer> PROP_CURRENT_MEDIA_INDEX = new PropertyKey<>("CurrentMediaIndex", Integer.class, FilmstripFragment.class, 0, -1);
	/**
	 * Read-only property to get filmstrip state.
	 */
	public final static PropertyKey<FilmstripState> PROP_FILMSTRIP_STATE = new PropertyKey<>("FilmstripState", FilmstripState.class, FilmstripFragment.class, FilmstripState.BACKGROUND);
	/**
	 * Property to get or set media list to display.
	 */
	public final static PropertyKey<MediaList> PROP_MEDIA_LIST = new PropertyKey<>("MediaList", MediaList.class, FilmstripFragment.class, 0, null);
	
	
	// Constants
	private final static BitmapPool BITMAP_POOL_HIGH_RESOLUTION = new BitmapPool("FilmstripHighResBitmapPool", 64 << 20, 16 << 20, Bitmap.Config.ARGB_8888, 2, 0);
	private final static BitmapPool BITMAP_POOL_MEDIUM_RESOLUTION = new BitmapPool("FilmstripMediumResBitmapPool", 64 << 20, 16 << 20, Bitmap.Config.ARGB_8888, 3, 0);
	private static final long DURATION_ANIMATION = 150;
	private static final int PRE_DECODE_THUMB_WINDOW_SIZE = 2;
	private static final int PRE_DECODE_THUMB_WINDOW_SIZE_SMALL = 3;

	
	// Fields
	private View m_BackButton;
	private View m_CollectButton;
	private int m_CurrentMediaIndex;
	private View m_DeleteButton;
	private View m_DetailsButton;
	private final Drawable m_DummyThumbDrawable = new ColorDrawable(Color.argb(255, 80, 80, 80));
	private View m_EditorButton;
	private View m_EditorButtonContainer;
	private Size m_FakePhotoSize;
	private Size m_FakeVideoSize;
	private FilmstripView.Adapter m_FilmstripAdapter = new FilmstripView.Adapter()
	{	
		@Override
		public int getCount()
		{
			return FilmstripFragment.this.onGetCount();
		}
		
		@Override
		public void prepareItemView(int position, ViewGroup container)
		{
			FilmstripFragment.this.onPrepareItemView(position, container);
		}
		
		@Override
		public void releaseItemView(int position, ViewGroup container)
		{
			FilmstripFragment.this.onReleaseItemView(position, container);
		};
	};
	private Queue<FilmstripItem> m_FilmstripItemPool = new ArrayDeque<>();
	private final SparseArray<FilmstripItem> m_FilmstripItems = new SparseArray<>();
	private int m_FilmstripScrollMode;
	private FilmstripState m_FilmstripState = FilmstripState.BACKGROUND;
	private FilmstripView m_FilmstripView;
	private View m_FooterContainer;
	private View m_HeaderContainer;
	private Handle m_HighResBitmapActiveHandle;
	private final BitmapPool.Callback m_HighResBitmapDecodeCallback = new BitmapPool.Callback() 
	{
		@Override
		public void onBitmapDecoded(Handle handle, String filePath, Bitmap bitmap) 
		{
			FilmstripFragment.this.onHighResImageDecoded(handle, filePath, bitmap);
		}
	};
	private Handle m_HighResBitmapDecodeHandle;
	private boolean m_IsActionEditSupported;
	private boolean m_IsInstanceStateSaved;
	private boolean m_IsOverScaledDown;
	private boolean m_IsScaled;
	private boolean m_IsToolbarVisible = true;
	private final ThumbnailImageManager.DecodingCallback m_LowResBitmapDecodeCallback = new ThumbnailImageManager.DecodingCallback()
	{
		@Override
		public void onThumbnailImageDecoded(Handle handle, Media media, Bitmap thumb)
		{
			FilmstripFragment.this.onLowResImageDecoded(handle, media, thumb);
		}
	};
	private List<BitmapDecodeInfo> m_LowResBitmapDecodeInfos = new ArrayList<>();
	private EventHandler<ListChangeEventArgs> m_MediaChangedEventHandler = new EventHandler<ListChangeEventArgs>()
	{
		@Override
		public void onEventReceived(EventSource source, EventKey<ListChangeEventArgs> key, ListChangeEventArgs e)
		{
			FilmstripFragment.this.onMediaListUpdated(e.getStartIndex(), e.getEndIndex());
		}
	};
	private MediaList m_MediaList;
	private Handle m_MediumResBitmapActiveHandle;
	private final BitmapPool.Callback m_MediumResBitmapDecodeCallback = new BitmapPool.Callback() 
	{
		@Override
		public void onBitmapDecoded(Handle handle, String filePath, Bitmap bitmap) 
		{
			FilmstripFragment.this.onMediumResImageDecoded(handle, filePath, bitmap);
		}
	};
	private List<BitmapDecodeInfo> m_MediumResBitmapDecodeInfos = new ArrayList<>();
	private Handle m_NavBarVisibilityHandle;
	private Queue<BitmapDecodeInfo> m_ReusedBitmapDecodeInfos = new ArrayDeque<>();
	private float m_ScaleFactor;
	private View m_ShareButton;
	private Handle m_StatusBarVisibilityHandle;
	private Handle m_ThumbManagerActivateHandle;
	private ViewVisibilityState m_ToolbarVisibilityState = ViewVisibilityState.INVISIBLE;
	
	
	// Enum
	public enum FilmstripState
	{
		BACKGROUND,
		BROWSE_SINGLE_PAGE,
		VIEW_DETAILS
	}
	private enum ImageDecodeState
	{
		NONE,
		SMALL_THUMB_DECODED,
		THUMB_DECODED,
		LARGE_IMAGE_DECODED,
	}
	private enum ViewVisibilityState
	{
		IN_ANIMATING,
		VISIBLE,
		OUT_ANIMATING,
		INVISIBLE
	}
	
	
	// Inner class
	private class FilmstripItem
	{
		// Public fields
		private final View m_Container;
		private ImageDecodeState m_ImageDecodeState;
		private Media m_Media; 
		private final ImageView m_PlayButton;
		private final ScaleImageView m_ScaleImageView;
		
		// Constructor
		public FilmstripItem(Media media)
		{
			// find containers & controls
			m_Container = View.inflate(FilmstripFragment.this.getActivity(), R.layout.layout_filmstrip_item, null);
			
			// setup scale image view
			m_ScaleImageView = (ScaleImageView)m_Container.findViewById(R.id.filmstrip_item_scale_image_view);
			m_ScaleImageView.setOnGestureCallback(new ScaleImageView.GestureCallback()
			{	
				@Override
				public void onGestureEnd(ScaleImageView view)
				{
					FilmstripFragment.this.onScaleImageGestureEnd(FilmstripItem.this);
				}
				
				@Override
				public void onGestureStart(ScaleImageView view, MotionEvent e)
				{
					FilmstripFragment.this.onScaleImageGestureStart(FilmstripItem.this, e);
				}
				
				@Override
				public boolean onDoubleTap(ScaleImageView view, MotionEvent e)
				{				
					return FilmstripFragment.this.onScaleImageGestureDoubleTap(FilmstripItem.this, e);
				}
				
				@Override
				public boolean onScale(ScaleImageView view, float factor, float pivotX, float pivotY)
				{
					return FilmstripFragment.this.onScaleImageGestureScale(FilmstripItem.this, factor, pivotX, pivotY);
				}
				
				@Override
				public boolean onSingleTapUp(ScaleImageView view, MotionEvent e)
				{					
					return FilmstripFragment.this.onScaleImageGestureSingleTapUp(FilmstripItem.this, e);
				}
			});
			m_ScaleImageView.setOnStateChangedCallback(new ScaleImageView.StateCallback()
			{
				@Override
				public void onBoundsChanged(ScaleImageView view, int left, int top, int right, int bottom)
				{
					FilmstripFragment.this.onScaleImageBoundsChanged(view, left, top, right, bottom);
				}
				public void onBoundsTypeChanged(ScaleImageView view, ScaleImageView.BoundsType oldType, ScaleImageView.BoundsType newType)
				{
					FilmstripFragment.this.onScaleImageBoundsTypeChanged(view, oldType, newType);
				};
			});
			
			// setup play button
			m_PlayButton = (ImageView)m_Container.findViewById(R.id.filmstrip_item_play_button);
			m_PlayButton.setOnClickListener(new View.OnClickListener()
			{	
				@Override
				public void onClick(View v)
				{
					FilmstripItem.this.onPlayButtonClick();
				}
			});
			
			// update media
			this.updateMedia(media);
		}
		
		// Get container
		public View getContainer()
		{
			return m_Container;
		}
		
		// Get image bounds type
		public BoundsType getImageBoundsType()
		{
			return m_ScaleImageView.getImageBoundsType();
		}
		
		public ImageDecodeState getImageDecodeState()
		{
			return m_ImageDecodeState;
		}
		
		// Get media
		public Media getMedia()
		{
			return m_Media;
		}
		
		// Check is stretch image
		private boolean isStretchedImage()
		{
			return m_ScaleImageView.isStretchedImage();
		}
		
		// Call when play button click
		private void onPlayButtonClick()
		{
			// start video player
			FilmstripFragment.this.playVideo(getMedia());
		}
		
		// Set image bounds
		public void setImageBounds(BoundsType boundsType)
		{
			m_ScaleImageView.setImageBounds(boundsType);
		}
		
		// Set image decode state
		public void setImageDecodeState(ImageDecodeState state)
		{
			m_ImageDecodeState = state;
		}
		
		// Set image drawable
		public void setImageDrawable(Drawable drawable)
		{
			m_ScaleImageView.setImageDrawable(drawable);
		}
		
		// Set media
		public void setMedia(Media media)
		{
			m_Media = media;
		}
		
		// Set position
		public void setPosition(int position)
		{
			m_ScaleImageView.setTag(position);
		}
		
		// update media
		public void updateMedia(Media media)
		{
			// update media info
			m_Media = media;
			boolean isVideo = (media instanceof VideoMedia);
			
			// set scale image view size and image bounds
			int width = media.getWidth();
			int height = media.getHeight();
			if(width <= 0 || height <= 0)
			{
				// set fake size if image size less than 0
				if(isVideo)
				{
					width = m_FakeVideoSize.getWidth();
					height = m_FakeVideoSize.getHeight();
				}
				else
				{
					width = m_FakePhotoSize.getWidth();
					height = m_FakePhotoSize.getHeight();
				}
			}

			// set video controls visible if media info is video
			if(isVideo)
			{
				m_PlayButton.setVisibility(View.VISIBLE);
				m_ScaleImageView.setImageSize(width, height);
				m_ScaleImageView.setImageBounds(BoundsType.FIT_SHORT_SIDE);
				m_ScaleImageView.setImageBoundsChangeEnabled(false);
				m_ScaleImageView.setImageScaleRatio(-1, 1);
			}
			else
			{
				m_PlayButton.setVisibility(View.INVISIBLE);
				m_ScaleImageView.setImageSize(width, height);
				m_ScaleImageView.setImageBounds(BoundsType.FIT_SHORT_SIDE);
				m_ScaleImageView.setImageBoundsChangeEnabled(true);
				m_ScaleImageView.setImageScaleRatio(-1, -1);
			}

			Log.v(TAG, "updateMedia() - File path: ", media.getFilePath(), ", hash: ", this.hashCode());
		}
	}
	
	
	// Bitmap decode info
	private static final class BitmapDecodeInfo
	{
		public Handle decodeHandle;
		public String filePath;
	}
	
	
	// Cancel decoding high resolution image
	private void cancelDecodingHighResolutionImage(Media media)
	{
		if(Handle.isValid(m_HighResBitmapDecodeHandle))
		{
			Log.v(TAG, "cancelDecodingHighResolutionImage() - Cancel decoding high-resolution bitmap : ", media.getFilePath());
			m_HighResBitmapDecodeHandle = Handle.close(m_HighResBitmapDecodeHandle);
		}
	}
	
	
	// Cancel decoding images
	private void cancelDecodingImages()
	{
		Log.v(TAG, "cancelDecodingImages()");
		
		for(int i = m_LowResBitmapDecodeInfos.size() - 1 ; i >= 0 ; --i)
		{
			BitmapDecodeInfo info = m_LowResBitmapDecodeInfos.get(i);
			info.decodeHandle = Handle.close(info.decodeHandle);
			m_ReusedBitmapDecodeInfos.add(info);
		}
		for(int i = m_MediumResBitmapDecodeInfos.size() - 1 ; i >= 0 ; --i)
		{
			BitmapDecodeInfo info = m_MediumResBitmapDecodeInfos.get(i);
			info.decodeHandle = Handle.close(info.decodeHandle);
			m_ReusedBitmapDecodeInfos.add(info);
		}
		m_HighResBitmapDecodeHandle = Handle.close(m_HighResBitmapDecodeHandle);
		m_LowResBitmapDecodeInfos.clear();
		m_ReusedBitmapDecodeInfos.clear();
	}
	
	
	// Cancel decoding low resolution image
	private void cancelDecodingLowResolutionImage(Media media)
	{
		if(m_LowResBitmapDecodeInfos.size() > 0)
		{
			for(int i = m_LowResBitmapDecodeInfos.size() - 1; i >= 0 ; i--)
			{
				BitmapDecodeInfo info = m_LowResBitmapDecodeInfos.get(i);
				if(info.filePath.equals(media.getFilePath()))
				{
					Log.v(TAG, "cancelDecodingLowResolutionImage() - Cancel decoding low-resolution bitmap : ", media.getFilePath());
					m_LowResBitmapDecodeInfos.remove(i);
					info.decodeHandle = Handle.close(info.decodeHandle);
					m_ReusedBitmapDecodeInfos.add(info);
					break;
				}
			}
		}
	}
	
	
	// Cancel decoding medium resolution image
	private void cancelDecodingMediumResolutionImage(Media media)
	{
		if(m_MediumResBitmapDecodeInfos.size() > 0)
		{
			for(int i = m_MediumResBitmapDecodeInfos.size() - 1; i >= 0; i--)
			{
				BitmapDecodeInfo info = m_MediumResBitmapDecodeInfos.get(i);
				if(info.filePath.equals(media.getFilePath()))
				{
					Log.v(TAG, "cancelDecodingMediumResolutionImage() - Cancel decoding medium-resolution bitmap : ", media.getFilePath());
					m_MediumResBitmapDecodeInfos.remove(i);
					info.decodeHandle = Handle.close(info.decodeHandle);
					m_ReusedBitmapDecodeInfos.add(info);
					break;
				}
			}
		}
	}
	
	
	// Check is action edit supported
	private void checkActionEditSupported()
	{
		PackageManager packageManager = this.getGalleryActivity().getPackageManager();
		Intent intent = new Intent(Intent.ACTION_EDIT);
		intent.setType("image/*");
		List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, 0);
		if(activities.size() > 0)
			m_IsActionEditSupported = true;
		else
			m_IsActionEditSupported = false;
	}
	
	
	// Check image decoding
	private void checkImageDecoding(int position)
	{
		// check state
		if(m_MediaList == null)
			return;
		boolean decodeImage = true;
		if(position < 0 || position > (m_MediaList.size() - 1))
			decodeImage = false;
		
		// prepare index
		int center = position;
		int maxIndex = m_MediaList.size() - 1;
		int preDecodeStart = center - PRE_DECODE_THUMB_WINDOW_SIZE;
		int preDecodeEnd = center + PRE_DECODE_THUMB_WINDOW_SIZE;
		int preDecodeLowStart = preDecodeStart - PRE_DECODE_THUMB_WINDOW_SIZE_SMALL;
		int preDecodeLowEnd = preDecodeEnd + PRE_DECODE_THUMB_WINDOW_SIZE_SMALL;
		if(preDecodeStart < 0)
			preDecodeStart = 0;
		if(preDecodeLowStart < 0)
			preDecodeLowStart = 0;
		if(preDecodeEnd > maxIndex)
			preDecodeEnd = maxIndex;
		if(preDecodeLowEnd > maxIndex)
			preDecodeLowEnd = maxIndex;
		
		Log.v(TAG, "checkImageDecoding() - Center: ", center, ", start: ", preDecodeStart, ", end: ", preDecodeEnd, ", low start: ", preDecodeLowStart, ", low end: ", preDecodeLowEnd);
		
		// check cancel
		for(int i = m_LowResBitmapDecodeInfos.size() - 1; i >= 0 ; i--)
		{
			BitmapDecodeInfo decodeInfo = m_LowResBitmapDecodeInfos.get(i);
			boolean cancel = true;
			for(int j = preDecodeLowStart ; j <= preDecodeLowEnd ; j++)
			{
				if(decodeInfo.filePath.equals(m_MediaList.get(j).getFilePath()))
				{
					cancel = false;
					break;
				}
			}
			if(cancel)
			{
				m_LowResBitmapDecodeInfos.remove(i);
				m_ReusedBitmapDecodeInfos.add(decodeInfo);
				decodeInfo.decodeHandle = Handle.close(decodeInfo.decodeHandle);
			}
		}
		for(int i = m_MediumResBitmapDecodeInfos.size() - 1; i >= 0 ; i--)
		{
			BitmapDecodeInfo decodeInfo = m_MediumResBitmapDecodeInfos.get(i);
			boolean cancel = true;
			for(int j = preDecodeStart ; j <= preDecodeEnd ; j++)
			{
				if(decodeInfo.filePath.equals(m_MediaList.get(j).getFilePath()))
				{
					cancel = false;
					break;
				}
			}
			if(cancel)
			{
				m_MediumResBitmapDecodeInfos.remove(i);
				m_ReusedBitmapDecodeInfos.add(decodeInfo);
				decodeInfo.decodeHandle = Handle.close(decodeInfo.decodeHandle);
			}
		}
		
		// start decode
		if(decodeImage)
		{
			for(int i = preDecodeLowStart; i <= preDecodeLowEnd; i++)
				this.decodeLowResolutionImage(m_MediaList.get(i), (i == center));
			for(int i = preDecodeStart; i <= preDecodeEnd; i++)
				this.decodeMediumResolutionImage(m_MediaList.get(i), (i == center));
		}
	}
	
	
	// Close fragment
	private void closeFragment()
	{
		this.getGalleryActivity().goBack();
	}
	
	
	// Collect page
	private void collectPage(int position)
	{
		// check position
		if(!validatePosition(position))
			return;

		// TODO: collect page from activity
		Media media = m_MediaList.get(position);
		GalleryActivity galleryActivity = this.getGalleryActivity();
	}
	
	
	// Create drawable to display
	private Drawable createDrawableForDisplay(Bitmap bitmap)
	{
		if(bitmap != null)
		{
			BitmapDrawable drawable = new BitmapDrawable(this.getActivity().getResources(), bitmap);
			drawable.setFilterBitmap(true);
			return drawable;
		}
		return null;
	}
	
	
	// Start decode high resolution image
	private void decodeHighResolutionImage(Media media)
	{
		Log.v(TAG, "decodeHighResolutionImage() - Start decoding high-resolution bitmap : ", media.getFilePath());
		m_HighResBitmapDecodeHandle = Handle.close(m_HighResBitmapDecodeHandle);
		m_HighResBitmapDecodeHandle = BITMAP_POOL_HIGH_RESOLUTION.decode(media.getFilePath(), 4096, 4096, BitmapPool.FLAG_ASYNC | BitmapPool.FLAG_URGENT, m_HighResBitmapDecodeCallback, this.getHandler());
	}
	
	
	// Start decode low resolution image
	private void decodeLowResolutionImage(Media media, boolean urgent)
	{
		// check media & decode
		String filePath = media.getFilePath();
		BitmapDecodeInfo decodeInfo = this.findBitmapDecodeInfo(m_LowResBitmapDecodeInfos, filePath);
		if((media instanceof PhotoMedia) && (decodeInfo == null || urgent))
		{
			// if media is photo and it's not decoding or urgent, decode it 
			if(decodeInfo == null)
			{
				decodeInfo = m_ReusedBitmapDecodeInfos.poll();
				if(decodeInfo == null)
					decodeInfo = new BitmapDecodeInfo();
				decodeInfo.filePath = filePath;
				m_LowResBitmapDecodeInfos.add(decodeInfo);
			}
			decodeInfo.decodeHandle = ThumbnailImageManager.decodeSmallThumbnailImage(media, ThumbnailImageManager.FLAG_URGENT, m_LowResBitmapDecodeCallback, this.getHandler());
			
			Log.v(TAG, "decodeLowResolutionImage() - Start decoding low-resolution bitmap : ", filePath);
		}
	}
	
	
	// Start decode medium resolution image
	private void decodeMediumResolutionImage(Media media, boolean urgent)
	{
		// check media & decode
		String filePath = media.getFilePath();
		BitmapDecodeInfo decodeInfo = this.findBitmapDecodeInfo(m_MediumResBitmapDecodeInfos, filePath);
		if(decodeInfo == null || urgent)
		{
			// if media is photo and it's not decoding or urgent, decode it 
			if(decodeInfo == null)
			{
				decodeInfo = m_ReusedBitmapDecodeInfos.poll();
				if(decodeInfo == null)
					decodeInfo = new BitmapDecodeInfo();
				decodeInfo.filePath = filePath;
				m_MediumResBitmapDecodeInfos.add(decodeInfo);
			}
			decodeInfo.decodeHandle = BITMAP_POOL_MEDIUM_RESOLUTION.decode(media.getFilePath(), 1920, 1920, BitmapPool.FLAG_URGENT, m_MediumResBitmapDecodeCallback, this.getHandler());
			
			Log.v(TAG, "decodeMediumResolutionImage() - Start decoding medium-resolution bitmap : ", filePath);
		}
	}
	
	
	// Delete page
	private void deletePage(int position)
	{
		// check position
		if(!validatePosition(position) || !this.isAttachedToGallery())
			return;

		// delete page
		Media media = m_MediaList.get(position);
		this.getGallery().deleteMedia(media);
	}
	
	
	// Editor page
	private void editorPage(int position)
	{
		// check position
		if(!validatePosition(position))
			return;

		// TODO: editor page from activity
		Media media = m_MediaList.get(position);
		GalleryActivity galleryActivity = this.getGalleryActivity();
	}
	
	
	// Find bitmap decode info
	private BitmapDecodeInfo findBitmapDecodeInfo(List<BitmapDecodeInfo> list, String filePath)
	{
		if(list == null || filePath == null)
			return null;
		for(int i = list.size() - 1 ; i >= 0 ; --i)
		{
			BitmapDecodeInfo info = list.get(i);
			if(filePath.equals(info.filePath))
				return info;
		}
		return null;
	}
	
	
	// Get props
	@SuppressWarnings("unchecked")
	@Override
	public <TValue> TValue get(PropertyKey<TValue> key)
	{
		if(key == PROP_CURRENT_MEDIA_INDEX)
			return (TValue)(Integer)m_CurrentMediaIndex;
		if(key == PROP_FILMSTRIP_STATE)
			return (TValue)m_FilmstripState;
		if(key == PROP_MEDIA_LIST)
			return (TValue)m_MediaList;
		return super.get(key);
	}
	
	
	// Called when backing to initial UI state.
	@Override
	protected void onBackToInitialUIState()
	{
		// call super
		super.onBackToInitialUIState();
		
		// set filmstrip state
		this.setFilmstripState(FilmstripState.BROWSE_SINGLE_PAGE);
		
		// reset tool bar
		this.setToolbarVisibility(true, false);
	}
	
	
	// Call when onCreate
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		// call super
		super.onCreate(savedInstanceState);
		
		// get fake photo, video size
		GalleryActivity galleryActivity = this.getGalleryActivity();
		int fakePhotoWidth = galleryActivity.getResources().getInteger(R.integer.fake_photo_width);
		int fakePhotoHeight = galleryActivity.getResources().getInteger(R.integer.fake_photo_height);
		int fakeVideoWidth = galleryActivity.getResources().getInteger(R.integer.fake_video_width);
		int fakeVideoHeight = galleryActivity.getResources().getInteger(R.integer.fake_video_height);
		m_FakePhotoSize = new Size(fakePhotoWidth, fakePhotoHeight);
		m_FakeVideoSize = new Size(fakeVideoWidth, fakeVideoHeight);
		
		// check editor supported
		this.checkActionEditSupported();
		
		// enable logs
		this.enablePropertyLogs(PROP_CURRENT_MEDIA_INDEX, LOG_PROPERTY_CHANGE);
		this.enablePropertyLogs(PROP_MEDIA_LIST, LOG_PROPERTY_CHANGE);
		this.enablePropertyLogs(PROP_FILMSTRIP_STATE, LOG_PROPERTY_CHANGE);
	}
	
	
	// Call when onCreateView
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		Log.v(TAG, "onCreateView() - Fragment: ", this.hashCode());
		
		// setup film strip view
		View view = inflater.inflate(R.layout.fragment_filmstrip, null);
		m_FilmstripView = (FilmstripView)view.findViewById(R.id.filmstrip_view);
		m_FilmstripView.setAdapter(m_FilmstripAdapter);
		m_FilmstripView.setScrollListener(new FilmstripView.ScrollListener()
		{
			@Override
			public void onItemSelected(int position)
			{
				FilmstripFragment.this.setCurrentMediaIndexProp(position, false);
			}
		});
		if(m_MediaList != null && m_CurrentMediaIndex >= 0 && m_CurrentMediaIndex <= m_MediaList.size() - 1)
		{
			// set current item and check bitmap decoding
			m_FilmstripView.setCurrentItem(m_CurrentMediaIndex, false);
			this.checkImageDecoding(m_CurrentMediaIndex);
		}
			
		
		// header
		m_HeaderContainer = view.findViewById(R.id.filmstrip_header_container);
		m_BackButton = view.findViewById(R.id.filmstrip_header_button_back);
		m_BackButton.setOnClickListener(new View.OnClickListener()
		{		
			@Override
			public void onClick(View v)
			{
				FilmstripFragment.this.closeFragment();
			}
		});
		m_DetailsButton = view.findViewById(R.id.filmstrip_header_button_details);
		m_DetailsButton.setOnClickListener(new View.OnClickListener()
		{		
			@Override
			public void onClick(View v)
			{
				FilmstripFragment.this.showPageDetails(m_FilmstripView.getCurrentItem());
			}
		});
		
		// set header margin top
		GalleryActivity galleryActivity = this.getGalleryActivity();
		ScreenSize screenSize = galleryActivity.get(GalleryActivity.PROP_SCREEN_SIZE);
		RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)m_HeaderContainer.getLayoutParams();
		params.setMargins(0, screenSize.getStatusBarSize(), 0, 0);
		
		// footer
		m_FooterContainer = view.findViewById(R.id.filmstrip_footer_container);
		m_ShareButton = view.findViewById(R.id.filmstrip_footer_button_share);
		m_ShareButton.setOnClickListener(new View.OnClickListener()
		{		
			@Override
			public void onClick(View v)
			{
				FilmstripFragment.this.sharePage(m_FilmstripView.getCurrentItem());
			}
		});
		m_CollectButton = view.findViewById(R.id.filmstrip_footer_button_collect);
		m_CollectButton.setOnClickListener(new View.OnClickListener()
		{		
			@Override
			public void onClick(View v)
			{
				FilmstripFragment.this.collectPage(m_FilmstripView.getCurrentItem());
			}
		});
		m_EditorButtonContainer = view.findViewById(R.id.filmstrip_footer_button_editor_container);
		m_EditorButton = view.findViewById(R.id.filmstrip_footer_button_editor);
		m_EditorButton.setOnClickListener(new View.OnClickListener()
		{		
			@Override
			public void onClick(View v)
			{
				FilmstripFragment.this.editorPage(m_FilmstripView.getCurrentItem());
			}
		});
		m_DeleteButton = view.findViewById(R.id.filmstrip_footer_button_delete);
		m_DeleteButton.setOnClickListener(new View.OnClickListener()
		{		
			@Override
			public void onClick(View v)
			{
				FilmstripFragment.this.deletePage(m_FilmstripView.getCurrentItem());
			}
		});
		
		// setup toolbar
		if(m_IsToolbarVisible)
			this.setToolbarVisibility(true, false);
		
		return view;
	}
	
	
	// Call when filmstrip view get count
	private int onGetCount()
	{
		return (m_MediaList != null && m_CurrentMediaIndex != -1) ? m_MediaList.size() : 0;
	}
	
	
	// Called when high-resolution image decoded.
	private void onHighResImageDecoded(Handle handle, String filePath, Bitmap bitmap)
	{
		// check state
		if(m_HighResBitmapDecodeHandle != handle)
		{
			Log.v(TAG, "onHighResImageDecoded() - Drop bitmap : ", filePath);
			return;
		}
		
		// update state
		m_HighResBitmapDecodeHandle = null;
		if(bitmap == null)
		{
			Log.w(TAG, "onHighResImageDecoded() - Fail to decode bitmap");
			return;
		}
		
		// update display
		boolean isItemFound = false;
		for(int i = m_FilmstripItems.size() - 1 ; i >= 0 ; --i)
		{
			Object obj = m_FilmstripItems.valueAt(i);
			if(!(obj instanceof FilmstripItem))
				continue;
			FilmstripItem filmstripItem = (FilmstripItem)obj;
			if(filmstripItem != null && filmstripItem.getMedia() != null && filePath.equals(filmstripItem.getMedia().getFilePath()))
			{
				if(filmstripItem.getImageBoundsType() != BoundsType.FIT_SHORT_SIDE)
				{
					filmstripItem.setImageDecodeState(ImageDecodeState.LARGE_IMAGE_DECODED);
					filmstripItem.setImageDrawable(this.createDrawableForDisplay(bitmap));
					Log.v(TAG, "onHighResImageDecoded() - Update high-resolution bitmap : ", filePath);
				}
				else
					Log.v(TAG, "onHighResImageDecoded() - High-resolution bitmap is not needed : ", filePath);
				isItemFound = true;
				break;
			}
		}
		if(!isItemFound)
			Log.v(TAG, "onHighResImageDecoded() - Item not found : ", filePath);
	}
	
	
	// Call when low resolution image decoded
	private void onLowResImageDecoded(Handle handle, Media media, Bitmap bitmap)
	{
		// check state
		String filePath = media.getFilePath();
		BitmapDecodeInfo decodeInfo = this.findBitmapDecodeInfo(m_LowResBitmapDecodeInfos, filePath);
		if(decodeInfo == null)
		{
			Log.v(TAG, "onLowResImageDecoded() - Drop bitmap : ", filePath);
			return;
		}

		// update state
		m_LowResBitmapDecodeInfos.remove(decodeInfo);
		m_ReusedBitmapDecodeInfos.add(decodeInfo);
		if(bitmap == null)
		{
			Log.w(TAG, "onLowResImageDecoded() - Fail to decode bitmap");
			return;
		}

		// update display
		boolean isItemFound = false;
		for(int i = m_FilmstripItems.size() - 1 ; i >= 0 ; --i)
		{
			Object obj = m_FilmstripItems.valueAt(i);
			if(!(obj instanceof FilmstripItem))
				continue;
			FilmstripItem filmstripItem = (FilmstripItem)obj;
			if(filmstripItem != null && filmstripItem.getMedia() != null && filePath.equals(filmstripItem.getMedia().getFilePath()))
			{
				if(filmstripItem.getImageDecodeState() == ImageDecodeState.NONE)
				{
					filmstripItem.setImageDecodeState(ImageDecodeState.SMALL_THUMB_DECODED);
					filmstripItem.setImageDrawable(this.createDrawableForDisplay(bitmap));
					Log.v(TAG, "onLowResImageDecoded() - Update low-resolution bitmap : ", filePath);
					isItemFound = true;
					break;
				}
			}
		}
		if(!isItemFound)
			Log.v(TAG, "onLowResImageDecoded() - Item not found : ", filePath);
	}
	
	
	// Call when medium resolution image decoded
	private void onMediumResImageDecoded(Handle handle, String filePath, Bitmap bitmap)
	{
		// check state
		BitmapDecodeInfo decodeInfo = this.findBitmapDecodeInfo(m_MediumResBitmapDecodeInfos, filePath);
		if(decodeInfo == null)
		{
			Log.v(TAG, "onMediumResImageDecoded() - Drop bitmap : ", filePath);
			return;
		}

		// update state
		m_MediumResBitmapDecodeInfos.remove(decodeInfo);
		m_ReusedBitmapDecodeInfos.add(decodeInfo);
		if(bitmap == null)
		{
			Log.w(TAG, "onMediumResImageDecoded() - Fail to decode bitmap");
			return;
		}

		// update display
		boolean isItemFound = false;
		for(int i = m_FilmstripItems.size() - 1 ; i >= 0 ; --i)
		{
			Object obj = m_FilmstripItems.valueAt(i);
			if(!(obj instanceof FilmstripItem))
				continue;
			FilmstripItem filmstripItem = (FilmstripItem)obj;
			if(filmstripItem != null && filmstripItem.getMedia() != null && filePath.equals(filmstripItem.getMedia().getFilePath()))
			{
				if(filmstripItem.getImageDecodeState() == ImageDecodeState.NONE)
					this.cancelDecodingLowResolutionImage(filmstripItem.getMedia());
				filmstripItem.setImageDecodeState(ImageDecodeState.THUMB_DECODED);
				filmstripItem.setImageDrawable(this.createDrawableForDisplay(bitmap));
				Log.v(TAG, "onMediumResImageDecoded() - Update medium-resolution bitmap : ", filePath);
				isItemFound = true;
				break;
			}
		}
		if(!isItemFound)
			Log.v(TAG, "onMediumResImageDecoded() - Item not found : ", filePath);
	}
	
	
	// Call when media list updated
	private void onMediaListUpdated()
	{
		this.onMediaListUpdated(-1, -1);
	}
	private void onMediaListUpdated(int startIndex, int endIndex)
	{
		// check media list
		if(m_MediaList == null)
			return;
		
		// check index
		if(startIndex > endIndex)
			return;
		
		Log.v(TAG, "onMediaListUpdated() - Start: ", startIndex, ", end: ", endIndex);
		
		// update media list
		if(startIndex < 0 || startIndex > (m_MediaList.size() - 1) ||
				endIndex < 0 || endIndex > (m_MediaList.size() - 1))
		{
			// set default item to -1
			m_FilmstripAdapter.notifyDataSetChanged();
			this.setCurrentMediaIndexProp(-1, true);
		}
		else
		{
			// update media list and keep current item
			int newPosition = m_FilmstripView.getCurrentItem();
			if(newPosition >= startIndex && newPosition >= endIndex)
				newPosition += (endIndex - startIndex + 1);
			else if(newPosition >= startIndex && newPosition <= endIndex)
				newPosition += (newPosition - startIndex + 1);
			m_FilmstripAdapter.notifyDataSetChanged();
			this.setCurrentMediaIndexProp(newPosition, true);
		}
	}
	
	
	// Call when onPause
	@Override
	public void onPause()
	{
		Log.v(TAG, "onPause()");
		/*
		// cancel decoding
		this.cancelDecodingImages();
		
		// deactivate bitmap pools
		m_HighResBitmapActiveHandle = Handle.close(m_HighResBitmapActiveHandle);
		m_MediumResBitmapActiveHandle = Handle.close(m_MediumResBitmapActiveHandle);
		
		// reset tool bar and system UI
		if(!m_IsInstanceStateSaved)
		{
			// hide tool bar
			this.setToolbarVisibility(false, false);
			
			// restore status bar
			this.setStatusBarVisibility(true);
		}
		*/
		
		// call super
		super.onPause();
	}
	
	
	// Call when filmstrip view prepare item view
	private void onPrepareItemView(int position, ViewGroup container)
	{
		Log.v(TAG, "onPrepareItemView() - Position: ", position);
		
		// prepare media
		Media media = m_MediaList.get(position);
		
		// poll a view from item pool or inflate a new item from layout
		FilmstripItem filmstripItem = m_FilmstripItemPool.poll();
		if(filmstripItem == null)
			filmstripItem = new FilmstripItem(media);
		else
			filmstripItem.updateMedia(media);
		
		// add view to item container
		container.addView(filmstripItem.getContainer());
		container.setTag(filmstripItem);
		
		// set position to scale image view
		filmstripItem.setPosition(position);
		
		// add to filmstrip items
		m_FilmstripItems.put(position, filmstripItem);
		
		// reset states
		filmstripItem.setImageDecodeState(ImageDecodeState.NONE);
		filmstripItem.setImageDrawable(m_DummyThumbDrawable);
		
		// check decoding
		this.checkImageDecoding(position);
	}
	
	
	private void onReleaseItemView(int position, ViewGroup container)
	{
		Log.v(TAG, "onReleaseItemView() - Position: ", position);
		
		// add releasing view back to pool to reuse it
		FilmstripItem reusedItem = (FilmstripItem)container.getTag();
		if(reusedItem != null)
		{
			// update state
			m_FilmstripItemPool.add(reusedItem);
			container.setTag(null);
			reusedItem.setImageDrawable(m_DummyThumbDrawable);
			reusedItem.setMedia(null);
		}
		m_FilmstripItems.remove(position);

		// check decoding
		this.checkImageDecoding(position);
		
		// remove all views
		container.removeAllViews();
	}
	
	
	// Call when onResume
	@Override
	public void onResume()
	{
		// call super
		super.onResume();
		
		Log.v(TAG, "onResume()");
		
		// active bitmap pools
		m_HighResBitmapActiveHandle = BITMAP_POOL_HIGH_RESOLUTION.activate();
		m_MediumResBitmapActiveHandle = BITMAP_POOL_MEDIUM_RESOLUTION.activate();
		
		// update state
		m_IsInstanceStateSaved = false;
	}
	
	
	// Called when saving instance state.
	@Override
	public void onSaveInstanceState(Bundle outState)
	{
		m_IsInstanceStateSaved = true;
		super.onSaveInstanceState(outState);
	}
	
	
	// Call when scale image view bounds changed
	private void onScaleImageBoundsChanged(ScaleImageView view, int left, int top, int right, int bottom)
	{
		// set over scale down
		Rect fitBounds = view.getFitToScreenShortSideBounds();
		if(!m_IsOverScaledDown)
		{
			if(fitBounds.width() > (right - left) || fitBounds.height() > (bottom - top))
			{
				Log.v(TAG, "onScaleImageBoundsChanged() - Over scaled");
				m_IsOverScaledDown = true;
			}
		}
		else
		{
			if(fitBounds.width() <= (right - left) && fitBounds.height() <= (bottom - top))
			{
				Log.v(TAG, "onScaleImageBoundsChanged() - Over scaled cancel");
				m_IsOverScaledDown = false;
			}
		}
	}
	
	
	// Call when scale image view bounds type changed
	private void onScaleImageBoundsTypeChanged(ScaleImageView view, ScaleImageView.BoundsType oldType, ScaleImageView.BoundsType newType)
	{
		// check media list
		if(m_MediaList == null)
			return;
		
		Log.v(TAG, "onScaleImageBoundsTypeChanged() - Old: ", oldType, ", new: ", newType, ", scale image view: ", view.hashCode());
		
		// check old type & new type
		int position = (Integer)view.getTag();
		if(oldType == ScaleImageView.BoundsType.FIT_SHORT_SIDE)
		{
			// load high-resolution bitmap
			Media media = m_MediaList.get(position);
			this.decodeHighResolutionImage(media);
			
			// reset over scale state
			m_IsOverScaledDown = false;
		}
		else if(newType == ScaleImageView.BoundsType.FIT_SHORT_SIDE)
		{			
			// cancel decoding high-resolution bitmap
			Media media = m_MediaList.get(position);
			this.cancelDecodingHighResolutionImage(media);
			
			// show thumbnail image
			FilmstripItem filmstripItem = m_FilmstripItems.get(position);
			if(filmstripItem != null)
			{
				filmstripItem.setImageDecodeState(ImageDecodeState.NONE);
				this.decodeLowResolutionImage(media, true);
				this.decodeMediumResolutionImage(media, true);
			}
			else
				Log.e(TAG, "onScaleImageBoundsTypeChanged() - No filmstrip item");
			
			// change to browse fast state
			if(m_IsOverScaledDown)
			{
				m_IsOverScaledDown = false;
				this.setFilmstripState(FilmstripState.BROWSE_SINGLE_PAGE);
			}
		}
		else
		{
			// reset over scale state
			m_IsOverScaledDown = false;
		}
	}
	
	
	// Call when scale image double tap
	private boolean onScaleImageGestureDoubleTap(FilmstripItem item, MotionEvent e)
	{
		// double tap
		Media media = item.getMedia();
		switch(m_FilmstripState)
		{
			case BROWSE_SINGLE_PAGE:
				if((media instanceof PhotoMedia) && !item.isStretchedImage())
					this.setFilmstripState(FilmstripState.VIEW_DETAILS);
				return false;
			case VIEW_DETAILS:
				this.setFilmstripState(FilmstripState.BROWSE_SINGLE_PAGE);
				return false;
			default:
				return false;
		}
	}
	
	
	// Call when scale image gesture end
	private void onScaleImageGestureEnd(FilmstripItem item)
	{
		// scale
		if(m_IsScaled)
		{
			this.updateFilmstripScrollMode();
		}
	}
	
	
	// Call when scale image scale
	private boolean onScaleImageGestureScale(FilmstripItem item, float factor, float pivotX, float pivotY)
	{
		// set states
		m_IsScaled = true;
		m_ScaleFactor *= factor;
		
		// multiply factors
		Media media = item.getMedia();
		switch(m_FilmstripState)
		{
			case BROWSE_SINGLE_PAGE:
				if(m_ScaleFactor > 1)
				{
					if((media instanceof PhotoMedia) && !item.isStretchedImage())
						this.setFilmstripState(FilmstripState.VIEW_DETAILS);
				}
				return false;
			default:
				return false;
		}
	}
	
	
	// Call when scale image single tap
	private boolean onScaleImageGestureSingleTapUp(FilmstripItem item, MotionEvent e)
	{
		// single tap up
		switch(m_FilmstripState)
		{
			case BROWSE_SINGLE_PAGE:
			case VIEW_DETAILS:
				FilmstripFragment.this.setToolbarVisibility(!m_IsToolbarVisible, true);
				return true;
			default:
				return false;
		}
	}
	
	
	// Call when scale image gesture start
	private void onScaleImageGestureStart(FilmstripItem item, MotionEvent e)
	{
		m_ScaleFactor = 1;
		m_IsScaled = false;
	}
	
	
	// Called when onStart
	@Override
	public void onStart()
	{
		// call super
		super.onStart();
		
		// activate thumbnail image manager
		if(!Handle.isValid(m_ThumbManagerActivateHandle))
			m_ThumbManagerActivateHandle = ThumbnailImageManager.activate();
	}
	
	
	// Call when onStop
	@Override
	public void onStop()
	{
		Log.v(TAG, "onStop()");
		
		// cancel decoding
		this.cancelDecodingImages();
		
		// deactivate bitmap pools
		m_HighResBitmapActiveHandle = Handle.close(m_HighResBitmapActiveHandle);
		m_MediumResBitmapActiveHandle = Handle.close(m_MediumResBitmapActiveHandle);
		
		// reset state
		if(!m_IsInstanceStateSaved)
		{
			// hide tool bar
			this.setToolbarVisibility(false, false);
			
			// restore status bar
			this.setSystemUiVisibility(true);
			
			// reset state
			this.resetFilmstripState();
		}
		else
			Log.v(TAG, "onStop() - Instance state saved, prevent resetting state");
		
		// deactivate thumbnail image manager
		m_ThumbManagerActivateHandle = Handle.close(m_ThumbManagerActivateHandle);
		
		// call super 
		super.onStop();
	}
	
	
	// Call when onDestroyView
	@Override
	public void onDestroyView()
	{
		Log.v(TAG, "onDestroyView()");
		
		// destroy view
		
		// call super
		super.onDestroyView();
	}
	
	
	// Play video
	private void playVideo(Media media)
	{
		// check state
		FilmstripState state = m_FilmstripState;
		switch(state)
		{
			case BROWSE_SINGLE_PAGE:
				break;
			default:
				Log.w(TAG, "playVideo() - Cannot play video page in current state: " + state);
				return;
		}
		if(media == null)
			return;

		// play video page
		this.playVideoDirectly(media);
	}
	
	
	// Play video directly
	private void playVideoDirectly(Media media)
	{
		// prepare intent
		Intent videoPlayerIntent = new Intent(this.getActivity(), VideoPlayerActivity.class);
		videoPlayerIntent.setDataAndType(media.getContentUri(),"video/*");

		// start activity
		this.startActivity(videoPlayerIntent);
	}
	
	
	// Reset filmstrip state
	private void resetFilmstripState()
	{
		Log.v(TAG, "resetFilmstripState()");

		// reset gallery state
		this.setFilmstripState(FilmstripState.BACKGROUND);
	}

	
	// Set props
	@Override
	public <TValue> boolean set(PropertyKey<TValue> key, TValue value)
	{
		if(key == PROP_CURRENT_MEDIA_INDEX)
			return this.setCurrentMediaIndexProp((Integer)value, true);
		if(key == PROP_MEDIA_LIST)
			return this.setMediaListProp((MediaList)value);
		return super.set(key, value);
	}
	
	
	// Set PROP_CURRENT_MEDIA_INDEX
	private boolean setCurrentMediaIndexProp(Integer value, boolean updateFilmstrip)
	{
		// check state
		if(m_CurrentMediaIndex == value)
			return true;
		if(value < 0 || m_MediaList == null || (value > m_MediaList.size() - 1))
			value = -1;
		
		// set value
		int oldValue = m_CurrentMediaIndex;
		m_CurrentMediaIndex = value;
		
		// set current item
		if(updateFilmstrip && m_FilmstripView != null)
			m_FilmstripView.setCurrentItem(m_CurrentMediaIndex, false);
		
		// check image decoding
		this.checkImageDecoding(m_CurrentMediaIndex);
		
		// complete
		return this.notifyPropertyChanged(PROP_CURRENT_MEDIA_INDEX, oldValue, value);
	}
	
	
	// Set filmstrip scroll mode
	private void setFilmstripScrollMode(int scrollMode)
	{
		// check scroll mode
		if(m_FilmstripScrollMode == scrollMode)
			return;
		
		// set scroll mode
		Log.v(TAG, "setFilmstripScrollMode() - Scroll mode: ", scrollMode);
		m_FilmstripScrollMode = scrollMode;
		m_FilmstripView.setScrollMode(scrollMode);
	}
	
	
	// Set filmstrip state
	private void setFilmstripState(FilmstripState state)
	{
		// check state
		if(state == m_FilmstripState)
			return;
		
		// set state
		FilmstripState oldState = m_FilmstripState;
		m_FilmstripState = state;
		
		// update gallery items bounds 
		switch(state)
		{
			case BACKGROUND:
				for(int i = m_FilmstripItems.size() - 1 ; i >= 0 ; --i)
				{
					Object obj = m_FilmstripItems.valueAt(i);
					if(obj instanceof FilmstripItem)
					{
						FilmstripItem item = (FilmstripItem)obj;
						if(item.getMedia() != null)
							item.setImageBounds(BoundsType.FIT_SHORT_SIDE);
					}
				}
				break;
		}
		
		// update filmstrip scroll mode
		this.updateFilmstripScrollMode();
		
		// set property
		this.notifyPropertyChanged(PROP_FILMSTRIP_STATE, oldState, state);
	}
	
	
	// Set media list
	private boolean setMediaListProp(MediaList list)
	{
		// check state
		if(m_MediaList == list)
			return true;

		// set value
		MediaList oldList = m_MediaList;
		m_MediaList = list;
		
		// setup call-backs
		if(oldList != null)
		{
			oldList.removeHandler(MediaList.EVENT_MEDIA_ADDED, m_MediaChangedEventHandler);
			oldList.removeHandler(MediaList.EVENT_MEDIA_REMOVED, m_MediaChangedEventHandler);
		}
		if(m_MediaList != null)
		{
			m_MediaList.addHandler(MediaList.EVENT_MEDIA_ADDED, m_MediaChangedEventHandler);
			m_MediaList.addHandler(MediaList.EVENT_MEDIA_REMOVED, m_MediaChangedEventHandler);
		}
		
		// update media list
		this.onMediaListUpdated();

		// complete
		return this.notifyPropertyChanged(PROP_MEDIA_LIST, oldList, list);
	}
	
	
	// Set system UI visibility
	private void setSystemUiVisibility(boolean visible)
	{
		if(visible)
		{
			m_NavBarVisibilityHandle = Handle.close(m_NavBarVisibilityHandle);
			m_StatusBarVisibilityHandle = Handle.close(m_StatusBarVisibilityHandle);
		}
		else
		{
			Gallery gallery = this.getGallery();
			if(gallery != null)
			{
				if(!Handle.isValid(m_NavBarVisibilityHandle))
					m_NavBarVisibilityHandle = gallery.setNavigationBarVisibility(false);
				if(!Handle.isValid(m_StatusBarVisibilityHandle))
					m_StatusBarVisibilityHandle = gallery.setStatusBarVisibility(false);
			}
			else
				Log.e(TAG, "setSystemUiVisibility() - No gallery");
		}
	}
	
	
	// Set toolbar visibility
	private void setToolbarVisibility(boolean visible, boolean animation)
	{		
		// set visibility
		m_IsToolbarVisible = visible;
		
		// check edit button
		this.updateEditButtonVisibility();
		
		// set status bar visibility
		this.setSystemUiVisibility(visible);
		
		// update
		this.updateToolbarVisibility(animation);
	}
	
	
	// Share page
	private void sharePage(int position)
	{
		// check position
		if(!validatePosition(position) || !this.isAttachedToGallery())
			return;

		// share page
		Media media = m_MediaList.get(position);
		this.getGallery().shareMedia(media);
	}
	
	
	// Show page details
	private void showPageDetails(int position)
	{
		// check position
		if(!validatePosition(position) || !this.isAttachedToGallery())
			return;

		// show media details
		Media media = m_MediaList.get(position);
		this.getGallery().showMediaDetails(media);
	}
	
	
	// Update edit button visibility
	private void updateEditButtonVisibility() 
	{
		if(m_IsToolbarVisible && m_EditorButtonContainer != null) 
		{
			// check edit supported
			if(!m_IsActionEditSupported)
			{
				m_EditorButtonContainer.setVisibility(View.GONE);
				return;
			}

			// check video case
			if(m_MediaList == null || m_MediaList.size() <= 0)
				return;
			int position = m_FilmstripView.getCurrentItem();
			if(position < 0)
				return;
			boolean isVideo = (m_MediaList.get(position) instanceof VideoMedia);
			if(isVideo)
				m_EditorButtonContainer.setVisibility(View.GONE);
			else
				m_EditorButtonContainer.setVisibility(View.VISIBLE);	
		}
	}
	
	
	// Update toolbar visibility
	private void updateToolbarVisibility(boolean animation)
	{
		// check state
		if(m_HeaderContainer == null || m_FooterContainer == null)
			return;
		switch(m_FilmstripState)
		{
			case BROWSE_SINGLE_PAGE:
			case VIEW_DETAILS:
				break;
			default:
				// only single page can show tool bar
				m_IsToolbarVisible = false;
				break;
		}

		Log.v(TAG, "updateToolbarVisibility() - Visible: ", m_IsToolbarVisible);

		// show/hide toolbar
		if(m_IsToolbarVisible)
		{
			if(animation)
			{
				switch(m_ToolbarVisibilityState)
				{
					case INVISIBLE:
						// set header init state
						m_HeaderContainer.setVisibility(View.VISIBLE);
						m_HeaderContainer.setAlpha(0);
	
						// set footer init state
						m_FooterContainer.setVisibility(View.VISIBLE);
						m_FooterContainer.setAlpha(0);
						break;
	
					case OUT_ANIMATING:
						// cancel animation
						m_HeaderContainer.animate().cancel();
						m_FooterContainer.animate().cancel();
						break;
	
					case IN_ANIMATING:
					case VISIBLE:
						return;
				}

				// header animation
				m_HeaderContainer.animate().alpha(1f).setDuration(DURATION_ANIMATION).withEndAction(new Runnable()
				{	
					@Override
					public void run()
					{
						m_ToolbarVisibilityState = ViewVisibilityState.VISIBLE;
					}
				}).start();

				// footer animation
				m_FooterContainer.animate().alpha(1f).setDuration(DURATION_ANIMATION).withEndAction(new Runnable()
				{	
					@Override
					public void run()
					{
						m_ToolbarVisibilityState = ViewVisibilityState.VISIBLE;
					}
				}).start();

				// change visibility state
				m_ToolbarVisibilityState = ViewVisibilityState.IN_ANIMATING;
			}
			else
			{
				// header
				m_HeaderContainer.setVisibility(View.VISIBLE);
				m_HeaderContainer.setAlpha(1);

				// footer
				m_FooterContainer.setVisibility(View.VISIBLE);
				m_FooterContainer.setAlpha(1);

				// change visibility state
				m_ToolbarVisibilityState = ViewVisibilityState.VISIBLE;
			}
		}
		else
		{
			if(animation)
			{
				switch(m_ToolbarVisibilityState)
				{
					case VISIBLE:
						// set header init state
						m_HeaderContainer.setAlpha(1);
	
						// set footer init state
						m_FooterContainer.setAlpha(1);
						break;
	
					case IN_ANIMATING:
						// cancel animation
						m_HeaderContainer.animate().cancel();
						m_FooterContainer.animate().cancel();
						break;
	
					case OUT_ANIMATING:
					case INVISIBLE:
						return;
				}

				// header animation
				m_HeaderContainer.animate().alpha(0f).setDuration(DURATION_ANIMATION).withEndAction(new Runnable()
				{	
					@Override
					public void run()
					{
						m_HeaderContainer.setVisibility(View.GONE);
						m_ToolbarVisibilityState = ViewVisibilityState.INVISIBLE;
					}
				}).start();

				// footer animation
				m_FooterContainer.animate().alpha(0f).setDuration(DURATION_ANIMATION).withEndAction(new Runnable()
				{	
					@Override
					public void run()
					{
						m_FooterContainer.setVisibility(View.GONE);
						m_ToolbarVisibilityState = ViewVisibilityState.INVISIBLE;
					}
				}).start();

				// change visibility state
				m_ToolbarVisibilityState = ViewVisibilityState.OUT_ANIMATING;
			}
			else
			{
				// set to gone
				m_HeaderContainer.setVisibility(View.GONE);
				m_FooterContainer.setVisibility(View.GONE);

				// change visibility state
				m_ToolbarVisibilityState = ViewVisibilityState.INVISIBLE;
			}
		}
	}
	
	
	// Update filmstrip scroll mode
	private void updateFilmstripScrollMode()
	{
		FilmstripState state = m_FilmstripState;
		// check filmstrip state
		switch(state)
		{
			case BROWSE_SINGLE_PAGE:
				this.setFilmstripScrollMode(FilmstripView.SCROLL_MODE_SINGLE_ITEM);
				break;
				
			case VIEW_DETAILS:
				this.setFilmstripScrollMode(FilmstripView.SCROLL_MODE_DISABLED);
				break;
				
			case BACKGROUND:
				this.setFilmstripScrollMode(FilmstripView.SCROLL_MODE_SINGLE_ITEM);
				break;
		}
	}
	
	
	// Validate position
	private boolean validatePosition(int position)
	{
		if(position >= 0 && position < m_MediaList.size())
			return true;
		return false;
	}
}
