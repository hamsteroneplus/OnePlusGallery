package com.oneplus.gallery;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import com.oneplus.base.BaseActivity;
import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.base.PropertyKey;
import com.oneplus.base.ScreenSize;
import com.oneplus.gallery.media.Media;

/**
 * Base class for activity in Gallery.
 */
public abstract class GalleryActivity extends BaseActivity
{
	/**
	 * Read-only property to get status bar visibility.
	 */
	public static final PropertyKey<Boolean> PROP_IS_STATUS_BAR_VISIBLE = new PropertyKey<>("IsStatusBarVisible", Boolean.class, GalleryActivity.class, true);
	/**
	 * Read-only property to get screen size.
	 */
	public static final PropertyKey<ScreenSize> PROP_SCREEN_SIZE = new PropertyKey<>("ScreenSize", ScreenSize.class, GalleryActivity.class, PropertyKey.FLAG_READONLY, null);
	
	
	// Constants.
	private static final int REQUEST_CODE_SHARE_MEDIA = (Integer.MAX_VALUE - 1);
	
	
	// Fields.
	private Gallery m_Gallery;
	private Handle m_GalleryAttachHandle;
	private boolean m_IsInstanceStateSaved;
	private boolean m_IsSharingMedia;
	private ScreenSize m_ScreenSize;
	
	
	// Fragment for sharing instance state.
	private static final class InstanceStateFragment extends Fragment
	{
		// Constants.
		public static final String TAG = "GalleryActivity.InstanceState";
		
		// Fields.
		public final Map<String, Object> extras = new HashMap<>();
		public Gallery gallery;
		
		// Constructor.
		public InstanceStateFragment()
		{
			this.setRetainInstance(true);
		}
	}
	
	
	// Get property.
	@SuppressWarnings("unchecked")
	@Override
	public <TValue> TValue get(PropertyKey<TValue> key)
	{
		if(key == PROP_SCREEN_SIZE)
			return (TValue)m_ScreenSize;
		return super.get(key);
	}
	
	
	/**
	 * Get related {@link Gallery}.
	 * @return {@link Gallery}.
	 */
	public final Gallery getGallery()
	{
		return m_Gallery;
	}
	
	
	/**
	 * Go back to previous state.
	 */
	public boolean goBack()
	{
		return false;
	}
	
	
	// Called after getting result from activity.
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch(requestCode)
		{
			case REQUEST_CODE_SHARE_MEDIA:
			{
				m_IsSharingMedia = false;
				break;
			}
			
			default:
				super.onActivityResult(requestCode, resultCode, data);
				break;
		}
	}
	
	
	// Called after pressing back button.
	@Override
	public void onBackPressed()
	{
		if(!this.goBack())
			super.onBackPressed();
	}
	
	
	// Called when configuration changed.
	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		// call super
		super.onConfigurationChanged(newConfig);
		
		// initialize screen size
		this.updateScreenSize();
	}
	
	
	// Called when creating.
	@Override
	protected final void onCreate(Bundle savedInstanceState)
	{
		// call super
		super.onCreate(savedInstanceState);
		
		// setup instance state
		InstanceStateFragment stateFragment = (InstanceStateFragment)this.getFragmentManager().findFragmentByTag(InstanceStateFragment.TAG);
		if(stateFragment != null)
		{
			Log.w(TAG, "onCreate() - Use existent Gallery");
			m_Gallery = stateFragment.gallery;
		}
		else
		{
			Log.w(TAG, "onCreate() - Create new Gallery");
			m_Gallery = new Gallery();
		}
		
		// attach to gallery
		m_GalleryAttachHandle = m_Gallery.attachActivity(this);
		if(!Handle.isValid(m_GalleryAttachHandle))
		{
			Log.e(TAG, "onCreate() - Fail to attach to Gallery");
			this.finish();
			return;
		}
		
		// initialize screen size
		this.updateScreenSize();
		
		// create
		this.onCreate(savedInstanceState, stateFragment != null ? stateFragment.extras : null);
	}
	
	
	/**
	 * Called when creating activity.
	 * @param savedInstanceState Saved instance state.
	 * @param extras Extra instance state.
	 */
	protected void onCreate(Bundle savedInstanceState, Map<String, Object> extras)
	{}
	
	
	// Called when destroying.
	@Override
	protected void onDestroy()
	{
		// detach from gallery
		m_GalleryAttachHandle = Handle.close(m_GalleryAttachHandle);
		
		// release gallery
		if(!m_IsInstanceStateSaved)
		{
			Log.w(TAG, "onDestroy() - Release Gallery");
			m_Gallery.release();
		}
		
		// call super
		super.onDestroy();
	}
	
	
	/**
	 * Called when navigation bar visibility changed.
	 * @param isVisible True if navigation bar is visible.
	 */
	protected void onNavigationBarVisibilityChanged(boolean isVisible)
	{}
	
	
	// Restore instance state
	@Override
	protected final void onRestoreInstanceState(Bundle savedInstanceState)
	{
		super.onRestoreInstanceState(savedInstanceState);
		InstanceStateFragment stateFragment = (InstanceStateFragment)this.getFragmentManager().findFragmentByTag(InstanceStateFragment.TAG);
		this.onRestoreInstanceState(savedInstanceState, stateFragment != null ? stateFragment.extras : null);
	}
	
	
	/**
	 * Called when restoring instance state.
	 * @param savedInstanceState Saved instance state.
	 * @param extras Extra instance state.
	 */
	protected void onRestoreInstanceState(Bundle savedInstanceState, Map<String, Object> extras)
	{}
	
	
	// Called when resuming
	@Override
	protected void onResume()
	{
		super.onResume();
		m_IsInstanceStateSaved = false;
	}
	
	
	// Save instance state.
	@Override
	protected final void onSaveInstanceState(Bundle outState)
	{
		// get instance state fragment
		InstanceStateFragment stateFragment = (InstanceStateFragment)this.getFragmentManager().findFragmentByTag(InstanceStateFragment.TAG);
		if(stateFragment == null)
		{
			stateFragment = new InstanceStateFragment();
			this.getFragmentManager().beginTransaction().add(stateFragment, InstanceStateFragment.TAG).commit();
		}
		
		// save instance state
		Log.w(TAG, "onSaveInstanceState() - Keep Gallery instance");
		stateFragment.gallery = m_Gallery;
		this.onSaveInstanceState(outState, stateFragment.extras);
		
		// call super
		super.onSaveInstanceState(outState);
		
		// update state
		m_IsInstanceStateSaved = true;
	}
	
	
	/**
	 * Called when saving instance state.
	 * @param outState Bundle to save instance state.
	 * @param extras Extra instance state.
	 */
	protected void onSaveInstanceState(Bundle outState, Map<String, Object> extras)
	{}
	
	
	/**
	 * Called when status bar visibility changed.
	 * @param isVisible True if status bar is visible.
	 */
	protected void onStatusBarVisibilityChanged(boolean isVisible)
	{}
	
	
	// Prepare sharing single media.
	private boolean prepareSharingMedia(Intent intent, Media media)
	{
		if(media == null)
		{
			Log.w(TAG, "prepareSharingMedia() - No media to share");
			return false;
		}
		Uri contentUri = media.getContentUri();
		if(contentUri != null)
			intent.putExtra(Intent.EXTRA_STREAM, contentUri);
		else
		{
			String filePath = media.getFilePath();
			if(filePath == null)
			{
				Log.w(TAG, "prepareSharingMedia() - No file path");
				return false;
			}
			intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(filePath)));
		}
		intent.setAction(Intent.ACTION_SEND);
		intent.setType(media.getMimeType());
		return true;
	}
	
	
	// Prepare sharing multiple media.
	private boolean prepareSharingMedia(Intent intent, Collection<Media> mediaCollection)
	{
		String mimeType = null;
		String mimeTypePrefix = null;
		ArrayList<Uri> uriList = new ArrayList<>();
		for(Media media : mediaCollection)
		{
			if(media == null)
				continue;
			Uri contentUri = media.getContentUri();
			if(contentUri != null)
				uriList.add(contentUri);
			else
			{
				String filePath = media.getFilePath();
				if(filePath == null)
					continue;
				uriList.add(Uri.fromFile(new File(filePath)));
			}
			String currentType = media.getMimeType();
			if(mimeType != null)
			{
				if(mimeTypePrefix.equals("*/"))
					continue;
				if(!mimeType.equals(currentType))
				{
					if(!currentType.startsWith(mimeTypePrefix))
					{
						mimeType = "*/*";
						mimeTypePrefix = "*/";
					}
					else if(mimeType.charAt(mimeType.length() - 1) != '*')
					{
						mimeType = (mimeTypePrefix + "*");
					}
				}
			}
			else
			{
				mimeType = currentType;
				int charIndex = currentType.indexOf('/');
				mimeTypePrefix = (charIndex >= 0 ? currentType.substring(0, charIndex + 1) : "*");
			}
		}
		if(uriList.isEmpty())
		{
			Log.w(TAG, "prepareSharingMedia() - No media to share");
			return false;
		}
		intent.setType(mimeType);
		intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
		intent.setAction(Intent.ACTION_SEND_MULTIPLE);
		return true;
	}
	
	
	/**
	 * Share media.
	 * @param mediaToShare Media to share.
	 * @return True if media shared successfully.
	 */
	public boolean shareMedia(Media mediaToShare)
	{
		if(mediaToShare == null)
		{
			Log.e(TAG, "shareMedia() - No media to share");
			return false;
		}
		return this.shareMedia(Arrays.asList(mediaToShare));
	}
	
	
	/**
	 * Share media.
	 * @param mediaToShare Media to share.
	 * @return True if media shared successfully.
	 */
	public boolean shareMedia(Collection<Media> mediaToShare)
	{
		// check state
		this.verifyAccess();
		if(m_IsSharingMedia)
		{
			Log.e(TAG, "shareMedia() - Waiting for previous sharing result");
			return false;
		}
		if(mediaToShare == null || mediaToShare.isEmpty())
		{
			Log.w(TAG, "shareMedia() - No media to share");
			return false;
		}
		
		// prepare intent
		Intent intent = new Intent();
		if(mediaToShare.size() == 1)
		{
			Iterator<Media> iterator = mediaToShare.iterator();
			Media media = null;
			if(iterator.hasNext())
				media = iterator.next();
			if(!this.prepareSharingMedia(intent, media))
				return false;
		}
		else
		{
			if(!this.prepareSharingMedia(intent, mediaToShare))
				return false;
		}
		
		// start activity
		try
		{
			this.startActivityForResult(Intent.createChooser(intent, "Share"), REQUEST_CODE_SHARE_MEDIA);
		}
		catch(Throwable ex)
		{
			Log.e(TAG, "shareMedia() - Fail to start activity", ex);
			return false;
		}
		
		// complete
		m_IsSharingMedia = true;
		return true;
	}
	
	
	/**
	 * Show detailed media information.
	 * @param media Media to show information.
	 */
	public void showMediaDetails(Media media)
	{
		this.verifyAccess();
		if(media == null)
		{
			Log.e(TAG, "showMediaDetails() - No media");
			return;
		}
		MediaDetailsDialog dialog = new MediaDetailsDialog(this, media);
		dialog.show();
	}
	
	
	/**
	 * Launch camera.
	 * @param mediaType Target media type to capture, or Null to launch in default mode.
	 * @return True if camera starts successfully.
	 */
	public boolean startCamera()
	{
		return this.startCamera(null);
	}
	
	
	/**
	 * Launch camera.
	 * @param mediaType Target media type to capture, or Null to launch in default mode.
	 * @return True if camera starts successfully.
	 */
	public boolean startCamera(MediaType mediaType)
	{
		Log.v(TAG, "startCamera() - Media type : ", mediaType);
		
		// prepare intent
		Intent intent = new Intent();
		if(mediaType != null)
		{
			switch(mediaType)
			{
				case PHOTO:
					intent.setAction(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
					break;
				case VIDEO:
					intent.setAction(MediaStore.INTENT_ACTION_VIDEO_CAMERA);
					break;
				default:
					Log.e(TAG, "startCamera() - Unknown media type : " + mediaType);
					return false;
			}
		}
		else
			intent.setAction(Intent.ACTION_MAIN);
		
		// start OnePlus Camera
		intent.setComponent(new ComponentName("com.oneplus.camera", "com.oneplus.camera.OPCameraActivity"));
		try
		{
			this.startActivity(intent);
			return true;
		}
		catch(ActivityNotFoundException ex)
		{
			Log.w(TAG, "startCamera() - No OnePlus Camera on this device", ex);
		}
		
		// start normal camera
		if(Intent.ACTION_MAIN.equals(intent.getAction()))
			intent.setAction(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
		intent.setComponent(null);
		try
		{
			this.startActivity(intent);
			return true;
		}
		catch(ActivityNotFoundException ex)
		{
			Log.w(TAG, "startCamera() - Fail to start camera", ex);
			return false;
		}
	}
	
	
	// Update screen size.
	private void updateScreenSize()
	{
		ScreenSize oldSize = m_ScreenSize;
		m_ScreenSize = new ScreenSize(this, false);
		this.notifyPropertyChanged(PROP_SCREEN_SIZE, oldSize, m_ScreenSize);
	}
}
