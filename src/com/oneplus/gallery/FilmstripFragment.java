package com.oneplus.gallery;

import java.util.ArrayDeque;
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

import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.base.PropertyKey;
import com.oneplus.base.Rotation;
import com.oneplus.gallery.media.Media;
import com.oneplus.gallery.media.MediaList;
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
	private final static BitmapPool BITMAP_POOL_LOW_RESOLUTION = new BitmapPool("FilmstripLowResBitmapPool", 32 << 20, 16 << 20, Bitmap.Config.RGB_565, 3, 0);
	private final static BitmapPool BITMAP_POOL_MEDIUM_RESOLUTION = new BitmapPool("FilmstripMediumResBitmapPool", 32 << 20, 16 << 20, Bitmap.Config.ARGB_8888, 3, 0);
	private final static int DISTANCE_ANIMATION_TRANSLATION = 50;
	private static final long DURATION_ANIMATION = 150;

	
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
		public void prepareItemView(int position, ViewGroup container)
		{
			FilmstripFragment.this.onPrepareItemView(position, container);
		}
		
		@Override
		public int getCount()
		{
			return FilmstripFragment.this.onGetCount();
		}
	};
	private Queue<FilmstripItem> m_FilmstripItemPool = new ArrayDeque<>();
	private final SparseArray<FilmstripItem> m_FilmstripItems = new SparseArray<>();
	private int m_FilmstripScrollMode;
	private FilmstripState m_FilmstripState;
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
	private boolean m_IsOverScaledDown;
	private boolean m_IsScaled;
	private boolean m_IsToolbarVisible;
	private Handle m_LowResBitmapActiveHandle;
	private final BitmapPool.Callback m_LowResBitmapDecodeCallback = new BitmapPool.Callback() 
	{
		@Override
		public void onBitmapDecoded(Handle handle, String filePath, Bitmap bitmap) 
		{
			FilmstripFragment.this.onLowResImageDecoded(handle, filePath, bitmap);
		}
	};
	private Handle m_LowResBitmapDecodeHandle;
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
	private Handle m_MediumResBitmapDecodeHandle;
	private float m_ScaleFactor;
	private View m_ShareButton;
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
		private final View m_ControlsContainer;
		private ImageDecodeState m_ImageDecodeState;
		private Media m_Media; 
		private final ImageView m_PlayButton;
		private final ScaleImageView m_ScaleImageView;
		
		// Constructor
		public FilmstripItem(Media media)
		{
			// find containers & controls
			m_Container = View.inflate(FilmstripFragment.this.getActivity(), R.layout.layout_filmstrip_item, null);
			m_ControlsContainer = m_Container.findViewById(R.id.filmstrip_item_controls_container);
			
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
			m_PlayButton = (ImageView)m_ControlsContainer.findViewById(R.id.filmstrip_item_play_button);
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
			FilmstripFragment.this.playVideo(m_Media);
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
			m_ScaleImageView.setImageSize(width, height);
			m_ScaleImageView.setImageBounds(BoundsType.FIT_SHORT_SIDE);

			// set video controls visible if media info is video
			if(isVideo)
			{
				m_ControlsContainer.setVisibility(View.VISIBLE);
				m_ScaleImageView.setImageBoundsChangeEnabled(false);
				m_ScaleImageView.setImageScaleRatio(-1, 1);
			}
			else
			{
				m_ControlsContainer.setVisibility(View.INVISIBLE);
				m_ScaleImageView.setImageBoundsChangeEnabled(true);
				m_ScaleImageView.setImageScaleRatio(-1, -1);
			}

			Log.v(TAG, "updateMediaInfo() - File path: ", media.getFilePath(), ", hash: ", this.hashCode());
		}
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
	
	
	// Cancel decoding low resolution image
	private void cancelDecodingLowResolutionImage(Media media)
	{
		if(Handle.isValid(m_LowResBitmapDecodeHandle))
		{
			Log.v(TAG, "cancelDecodingLowResolutionImage() - Cancel decoding low-resolution bitmap : ", media.getFilePath());
			m_LowResBitmapDecodeHandle = Handle.close(m_LowResBitmapDecodeHandle);
		}
	}
	
	
	// Cancel decoding medium resolution image
	private void cancelDecodingMediumResolutionImage(Media media)
	{
		if(Handle.isValid(m_MediumResBitmapDecodeHandle))
		{
			Log.v(TAG, "cancelDecodingMediumResolutionImage() - Cancel decoding medium-resolution bitmap : ", media.getFilePath());
			m_MediumResBitmapDecodeHandle = Handle.close(m_MediumResBitmapDecodeHandle);
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
	
	
	// Close fragment
	private void closeFragment()
	{
		// raise action back
		this.raise(EVENT_ACTION_ITEM_CLICKED, new ActionItemEventArgs(ACTION_ID_BACK));
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
	private void decodeLowResolutionImage(Media media)
	{
		Log.v(TAG, "decodeLowResolutionImage() - Start decoding low-resolution bitmap : ", media.getFilePath());
		m_LowResBitmapDecodeHandle = Handle.close(m_LowResBitmapDecodeHandle);
		m_LowResBitmapDecodeHandle = BITMAP_POOL_LOW_RESOLUTION.decode(media.getFilePath(), 512, 512, BitmapPool.FLAG_ASYNC | BitmapPool.FLAG_URGENT, m_LowResBitmapDecodeCallback, this.getHandler());
	}
	
	
	// Start decode medium resolution image
	private void decodeMediumResolutionImage(Media media)
	{
		Log.v(TAG, "decodeMediumResolutionImage() - Start decoding medium-resolution bitmap : ", media.getFilePath());
		m_MediumResBitmapDecodeHandle = Handle.close(m_MediumResBitmapDecodeHandle);
		m_MediumResBitmapDecodeHandle = BITMAP_POOL_MEDIUM_RESOLUTION.decode(media.getFilePath(), 1920, 1920, BitmapPool.FLAG_ASYNC | BitmapPool.FLAG_URGENT, m_MediumResBitmapDecodeCallback, this.getHandler());
	}
	
	
	// Delete page
	private void deletePage(int position)
	{
		// check position
		if(!validatePosition(position))
			return;

		// TODO: delete page from activity
		Media media = m_MediaList.get(position);
		GalleryActivity galleryActivity = this.getGalleryActivity();
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
	}
	
	
	// Call when onCreateView
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		// setup film strip view
		View view = inflater.inflate(R.layout.fragment_filmstrip, null);
		m_FilmstripView = (FilmstripView)view.findViewById(R.id.filmstrip_view);
		m_FilmstripView.setAdapter(m_FilmstripAdapter);
		
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
		
		return view;
	}
	
	
	// Call when filmstrip view get count
	private int onGetCount()
	{
		return (m_MediaList != null) ? m_MediaList.size() : 0;
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
	private void onLowResImageDecoded(Handle handle, String filePath, Bitmap bitmap)
	{
		// check state
		if(m_LowResBitmapDecodeHandle != handle)
		{
			Log.v(TAG, "onLowResImageDecoded() - Drop bitmap : ", filePath);
			return;
		}

		// update state
		m_LowResBitmapDecodeHandle = null;
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
	
	
	// Call when media index updated
	private void onMediaIndexUpdated()
	{
		m_FilmstripView.setCurrentItem(m_CurrentMediaIndex, false);
	}
	
	
	// Call when medium resolution image decoded
	private void onMediumResImageDecoded(Handle handle, String filePath, Bitmap bitmap)
	{
		// check state
		if(m_MediumResBitmapDecodeHandle != handle)
		{
			Log.v(TAG, "onMediumResImageDecoded() - Drop bitmap : ", filePath);
			return;
		}

		// update state
		m_MediumResBitmapDecodeHandle = null;
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
			m_FilmstripView.setCurrentItem(-1, false);
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
			m_FilmstripView.setCurrentItem(newPosition, false);
		}
	}
	
	
	// Call when onPause
	@Override
	public void onPause()
	{
		// reset
		this.resetFilmstripState();
		
		// deactivate bitmap pools
		m_HighResBitmapActiveHandle = Handle.close(m_HighResBitmapActiveHandle);
		m_MediumResBitmapActiveHandle = Handle.close(m_MediumResBitmapActiveHandle);
		m_LowResBitmapActiveHandle = Handle.close(m_LowResBitmapActiveHandle);
		
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
		
		// decode thumbnail images
		filmstripItem.setImageDecodeState(ImageDecodeState.NONE);
		filmstripItem.setImageDrawable(m_DummyThumbDrawable);
		this.decodeLowResolutionImage(media);
		this.decodeMediumResolutionImage(media);
	}
	
	
	// Call when onResume
	@Override
	public void onResume()
	{
		// call super
		super.onResume();
		
		// active bitmap pools
		m_HighResBitmapActiveHandle = BITMAP_POOL_HIGH_RESOLUTION.activate();
		m_MediumResBitmapActiveHandle = BITMAP_POOL_MEDIUM_RESOLUTION.activate();
		m_LowResBitmapActiveHandle = BITMAP_POOL_LOW_RESOLUTION.activate();
		
		// set filmstrip state
		this.setFilmstripState(FilmstripState.BROWSE_SINGLE_PAGE);

		// set toolbars visible
		this.setToolbarVisibility(true, true);
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
				this.decodeLowResolutionImage(media);
				this.decodeMediumResolutionImage(media);
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
				if(!(media instanceof VideoMedia) && !item.isStretchedImage())
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
					if(!(media instanceof VideoMedia) && !item.isStretchedImage())
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
		Intent playIntent = new Intent(Intent.ACTION_VIEW);
		playIntent.setDataAndType(media.getContentUri(),"video/*");

		// start activity
		this.startActivity(playIntent);
	}
	
	
	// Reset filmstrip state
	private void resetFilmstripState()
	{
		Log.v(TAG, "resetFilmstripState()");

		// reset gallery state
		this.setFilmstripState(FilmstripState.BACKGROUND);
		
		// reset media index
		m_CurrentMediaIndex = -1;
		this.onMediaIndexUpdated();
	}

	
	// Set props
	@Override
	public <TValue> boolean set(PropertyKey<TValue> key, TValue value)
	{
		if(key == PROP_CURRENT_MEDIA_INDEX)
			return this.setCurrentMediaIndexProp((Integer)value);
		if(key == PROP_MEDIA_LIST)
			return this.setMediaListProp((MediaList)value);
		return super.set(key, value);
	}
	
	
	// Set PROP_CURRENT_MEDIA_INDEX
	private boolean setCurrentMediaIndexProp(Integer value)
	{
		// check state
		if(m_CurrentMediaIndex == value)
			return true;
		
		// set value
		int oldValue = m_CurrentMediaIndex;
		m_CurrentMediaIndex = value;
		
		// update media index
		this.onMediaIndexUpdated();
		
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
		
		// update media list
		this.onMediaListUpdated();

		// complete
		return this.notifyPropertyChanged(PROP_MEDIA_LIST, oldList, list);
	}
	
	
	// Set toolbar visibility
	private void setToolbarVisibility(boolean visible, boolean animation)
	{		
		// set visibility
		m_IsToolbarVisible = visible;
		
		// check edit button
		this.updateEditButtonVisibility();
		
		// update
		this.updateToolbarVisibility(animation);
	}
	
	
	// Share page
	private void sharePage(int position)
	{
		// check position
		if(!validatePosition(position))
			return;

		// TODO: share page from activity
		Media media = m_MediaList.get(position);
		GalleryActivity galleryActivity = this.getGalleryActivity();
	}
	
	
	// Show page details
	private void showPageDetails(int position)
	{
		// check position
		if(!validatePosition(position))
			return;

		// TODO: show page details from activity
		Media media = m_MediaList.get(position);
		GalleryActivity galleryActivity = this.getGalleryActivity();
	}
	
	
	// Update edit button visibility
	private void updateEditButtonVisibility() 
	{
		if(m_IsToolbarVisible) 
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
						m_HeaderContainer.setTranslationY(-DISTANCE_ANIMATION_TRANSLATION);
	
						// set footer init state
						m_FooterContainer.setVisibility(View.VISIBLE);
						m_FooterContainer.setAlpha(0);
						m_FooterContainer.setTranslationY(DISTANCE_ANIMATION_TRANSLATION);
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
				m_HeaderContainer.animate().alpha(1f).translationY(0f).setDuration(DURATION_ANIMATION).withEndAction(new Runnable()
				{	
					@Override
					public void run()
					{
						m_ToolbarVisibilityState = ViewVisibilityState.VISIBLE;
					}
				}).start();

				// footer animation
				m_FooterContainer.animate().alpha(1f).translationY(0f).setDuration(DURATION_ANIMATION).withEndAction(new Runnable()
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
				m_HeaderContainer.setTranslationY(0);

				// footer
				m_FooterContainer.setVisibility(View.VISIBLE);
				m_FooterContainer.setAlpha(1);
				m_FooterContainer.setTranslationY(0);

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
						m_HeaderContainer.setTranslationY(0);
	
						// set footer init state
						m_FooterContainer.setAlpha(1);
						m_FooterContainer.setTranslationY(0);
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
				m_HeaderContainer.animate().alpha(0f).translationY(-DISTANCE_ANIMATION_TRANSLATION).setDuration(DURATION_ANIMATION).withEndAction(new Runnable()
				{	
					@Override
					public void run()
					{
						m_HeaderContainer.setVisibility(View.GONE);
						m_ToolbarVisibilityState = ViewVisibilityState.INVISIBLE;
					}
				}).start();

				// footer animation
				m_FooterContainer.animate().alpha(0f).translationY(DISTANCE_ANIMATION_TRANSLATION).setDuration(DURATION_ANIMATION).withEndAction(new Runnable()
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
