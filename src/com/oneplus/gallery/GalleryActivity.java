package com.oneplus.gallery;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import com.oneplus.base.BaseActivity;
import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.base.PropertyKey;
import com.oneplus.base.ScreenSize;
import com.oneplus.gallery.media.Media;
import com.oneplus.util.ListUtils;

/**
 * Base class for activity in Gallery.
 */
public abstract class GalleryActivity extends BaseActivity
{
	/**
	 * Read-only property to get status bar visibility.
	 */
	public static final PropertyKey<Boolean> PROP_IS_STATUS_BAR_VISIBLE = new PropertyKey<>("IsStatusBarVisible", Boolean.class, GalleryActivity.class, false);
	/**
	 * Read-only property to get screen size.
	 */
	public static final PropertyKey<ScreenSize> PROP_SCREEN_SIZE = new PropertyKey<>("ScreenSize", ScreenSize.class, GalleryActivity.class, PropertyKey.FLAG_READONLY, null);
	
	
	/**
	 * Flag to indicate that operation can be cancelled.
	 */
	public static final int FLAG_CANCELABLE = 0x1;
	
	
	// Constants.
	private static final int REQUEST_CODE_SHARE_MEDIA = (Integer.MAX_VALUE - 1);
	
	
	// Fields.
	private boolean m_IsSharingMedia;
	private ScreenSize m_ScreenSize;
	private final List<StatusBarVisibilityHandle> m_StatusBarVisibilityHandles = new ArrayList<>();
	
	
	// Handle for status bar visibility.
	private final class StatusBarVisibilityHandle extends Handle
	{
		public final int flags;
		public final boolean isVisible;
		
		public StatusBarVisibilityHandle(boolean isVisible, int flags)
		{
			super("StatusBarVisibility");
			this.isVisible = isVisible;
			this.flags = flags;
		}
		
		public void drop()
		{
			this.closeDirectly();
		}

		@Override
		protected void onClose(int flags)
		{
			restoreStatusBarVisibility(this, true);
		}
	}
	
	
	// Check status bar visibility.
	private void checkStatusBarVisibility(Boolean requestedVisibility)
	{
		Window window = this.getWindow();
		if(window != null)
			this.checkStatusBarVisibility(window.getDecorView().getSystemUiVisibility(), requestedVisibility);
	}
	private void checkStatusBarVisibility(int systemUiVisiblity, Boolean requestedVisibility)
	{
		// check requested visibility
		boolean isVisible = ((systemUiVisiblity & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0);
		if(requestedVisibility == null)
			requestedVisibility = isVisible;
		for(int i = m_StatusBarVisibilityHandles.size() - 1 ; i >= 0 ; --i)
		{
			StatusBarVisibilityHandle handle = m_StatusBarVisibilityHandles.get(i);
			if(handle.isVisible != isVisible)
			{
				if((handle.flags & FLAG_CANCELABLE) != 0)
				{
					handle.drop();
					m_StatusBarVisibilityHandles.remove(i);
				}
				else
					requestedVisibility = !isVisible;
			}
		}
		
		// check default visibility
		if(m_StatusBarVisibilityHandles.isEmpty() && !isVisible)
			requestedVisibility = true;
		
		// update visibility
		if(requestedVisibility != isVisible)
		{
			if(requestedVisibility)
				systemUiVisiblity &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
			else
				systemUiVisiblity |= View.SYSTEM_UI_FLAG_FULLSCREEN;
			systemUiVisiblity |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
			this.getWindow().getDecorView().setSystemUiVisibility(systemUiVisiblity);
			return;
		}
		
		// update state
		if(this.setReadOnly(PROP_IS_STATUS_BAR_VISIBLE, isVisible))
			this.onStatusBarVisibilityChanged(isVisible);
	}
	
	
	/**
	 * Delete media.
	 * @param mediaToDelete Media to delete.
	 * @return True if media deleted successfully.
	 */
	public boolean deleteMedia(Media mediaToDelete)
	{
		if(mediaToDelete == null)
		{
			Log.e(TAG, "deleteMedia() - No media to delete");
			return false;
		}
		return this.deleteMedia(Arrays.asList(mediaToDelete));
	}
	
	
	/**
	 * Delete media.
	 * @param mediaToDelete Media to delete.
	 * @return True if media deleted successfully.
	 */
	public boolean deleteMedia(Collection<Media> mediaToDelete)
	{
		// complete
		return true;
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
	protected void onCreate(Bundle savedInstanceState)
	{
		// call super
		super.onCreate(savedInstanceState);
		
		// initialize screen size
		this.updateScreenSize();
		
		// initialize system UI state
		View decorView = this.getWindow().getDecorView();
		decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
		decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener()
		{
			@Override
			public void onSystemUiVisibilityChange(int visibility)
			{
				checkStatusBarVisibility(visibility, null);
			}
		});
		this.checkStatusBarVisibility(decorView.getSystemUiVisibility(), null);
	}
	
	
	/**
	 * Called when status bar visibility changed.
	 * @param isVisible True if status bar is visible.
	 */
	protected void onStatusBarVisibilityChanged(boolean isVisible)
	{}
	
	
	// Restore status bar visibility.
	private void restoreStatusBarVisibility(StatusBarVisibilityHandle handle, boolean updateStatusBar)
	{
		boolean isLast = ListUtils.isLastObject(m_StatusBarVisibilityHandles, handle);
		if(m_StatusBarVisibilityHandles.remove(handle) && updateStatusBar && isLast)
			this.checkStatusBarVisibility(null);
	}
	
	
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
	 * Set status bar visibility.
	 * @param isVisible True to show status bar.
	 * @return Handle to this operation.
	 */
	public Handle setStatusBarVisibility(boolean isVisible)
	{
		return this.setStatusBarVisibility(isVisible, FLAG_CANCELABLE);
	}
	
	
	/**
	 * Set status bar visibility.
	 * @param isVisible True to show status bar.
	 * @param flags Flags:
	 * <ul>
	 *   <li>{@link #FLAG_CANCELABLE}</li>
	 * </ul>
	 * @return Handle to this operation.
	 */
	public Handle setStatusBarVisibility(boolean isVisible, int flags)
	{
		this.verifyAccess();
		this.checkStatusBarVisibility(isVisible);
		StatusBarVisibilityHandle handle = new StatusBarVisibilityHandle(isVisible, flags);
		m_StatusBarVisibilityHandles.add(handle);
		return handle;
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
	
	
	// Update screen size.
	private void updateScreenSize()
	{
		ScreenSize oldSize = m_ScreenSize;
		m_ScreenSize = new ScreenSize(this, false);
		this.notifyPropertyChanged(PROP_SCREEN_SIZE, oldSize, m_ScreenSize);
	}
}
