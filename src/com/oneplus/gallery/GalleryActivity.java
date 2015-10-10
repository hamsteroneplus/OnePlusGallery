package com.oneplus.gallery;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;

import com.oneplus.base.BaseActivity;
import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.base.PropertyKey;
import com.oneplus.base.ScreenSize;
import com.oneplus.gallery.media.Media;
import com.oneplus.gallery.media.MediaSet;
import com.oneplus.util.ListUtils;

/**
 * Base class for activity in Gallery.
 */
public abstract class GalleryActivity extends BaseActivity
{
	/**
	 * Read-only property to get current media deletion state.
	 */
	public static final PropertyKey<Boolean> PROP_IS_DELETING_MEDIA = new PropertyKey<>("IsDeletingMedia", Boolean.class, GalleryActivity.class, false);
	/**
	 * Read-only property to get navigation bar visibility.
	 */
	public static final PropertyKey<Boolean> PROP_IS_NAVIGATION_BAR_VISIBLE = new PropertyKey<>("IsNavigationBarVisible", Boolean.class, GalleryActivity.class, true);
	/**
	 * Read-only property to get current media sharing state.
	 */
	public static final PropertyKey<Boolean> PROP_IS_SHARING_MEDIA = new PropertyKey<>("IsSharingMedia", Boolean.class, GalleryActivity.class, false);
	/**
	 * Read-only property to get status bar visibility.
	 */
	public static final PropertyKey<Boolean> PROP_IS_STATUS_BAR_VISIBLE = new PropertyKey<>("IsStatusBarVisible", Boolean.class, GalleryActivity.class, true);
	/**
	 * Read-only property to get screen size.
	 */
	public static final PropertyKey<ScreenSize> PROP_SCREEN_SIZE = new PropertyKey<>("ScreenSize", ScreenSize.class, GalleryActivity.class, PropertyKey.FLAG_READONLY, null);
	
	
	/**
	 * Flag to indicate that operation can be cancelled.
	 */
	public static final int FLAG_CANCELABLE = 0x1;
	
	
	// Constants.
	private static final String STATIC_TAG = GalleryActivity.class.getSimpleName();
	private static final int REQUEST_CODE_SHARE_MEDIA = (Integer.MAX_VALUE - 1);
	
	
	// Fields.
	private boolean m_IsSharingMedia;
	private ScreenSize m_ScreenSize;
	private List<StatusBarVisibilityHandle> m_StatusBarVisibilityHandles;
	private SystemUIManager m_SystemUIManager;
	
	
	/**
	 * Media deletion call-back class.
	 */
	public static abstract class MediaDeletionCallback
	{
		/**
		 * Called after deleting media.
		 * @param media Media to be deleted.
		 * @param success True if media deleted successfully.
		 */
		public void onDeletionCompleted(Media media, boolean success)
		{}
		
		/**
		 * Called when deletion process completed.
		 */
		public void onDeletionProcessCompleted()
		{}
		
		/**
		 * Called when deletion process started.
		 */
		public void onDeletionProcessStarted()
		{}
		
		/**
		 * Called when media deletion started.
		 * @param media Media to be deleted.
		 */
		public void onDeletionStarted(Media media)
		{}
	}
	
	
	/**
	 * Media set deletion call-back class.
	 */
	public static abstract class MediaSetDeletionCallback
	{
		/**
		 * Called after deleting media set.
		 * @param mediaSet Media set to be deleted.
		 * @param success True if media set deleted successfully.
		 */
		public void onDeletionCompleted(MediaSet mediaSet, boolean success)
		{}
		
		/**
		 * Called when deletion process completed.
		 */
		public void onDeletionProcessCompleted()
		{}
		
		/**
		 * Called when deletion process started.
		 */
		public void onDeletionProcessStarted()
		{}
		
		/**
		 * Called when media set deletion started.
		 * @param mediaSet Media set to be deleted.
		 */
		public void onDeletionStarted(MediaSet mediaSet)
		{}
	}
	
	
	// Handle for status bar visibility.
	private static final class StatusBarVisibilityHandle extends Handle
	{
		public final int flags;
		public final boolean isVisible;
		public final SystemUIManager systemUIManager;
		
		public StatusBarVisibilityHandle(SystemUIManager systemUIManager, boolean isVisible, int flags)
		{
			super("StatusBarVisibility");
			this.systemUIManager = systemUIManager;
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
			this.systemUIManager.restoreStatusBarVisibility(this);
		}
	}
	
	
	// Fragment for sharing instance state.
	private static final class InstanceStateFragment extends Fragment
	{
		// Constants.
		public static final String TAG = "GalleryActivity.InstanceState";
		
		// Fields.
		public final Map<String, Object> extras = new HashMap<>();
		public boolean isDeletingMedia;
		public boolean isSharingMedia;
		public boolean isStatusBarVisible;
		public List<StatusBarVisibilityHandle> statusBarVisibilityHandles;
		public SystemUIManager systemUIManager;
		
		// Constructor.
		public InstanceStateFragment()
		{
			this.setRetainInstance(true);
		}
	}
	
	
	// System UI manager.
	private static final class SystemUIManager
	{
		// Fields.
		private GalleryActivity m_Activity;
		
		// Attach.
		public void attach(GalleryActivity activity)
		{
			m_Activity = activity;
		}
		
		// Detach.
		public void detach(GalleryActivity activity)
		{
			if(m_Activity == activity)
				m_Activity = null;
		}
		
		// Restore status bar state.
		public void restoreStatusBarVisibility(StatusBarVisibilityHandle handle)
		{
			if(m_Activity != null)
				m_Activity.restoreStatusBarVisibility(handle);
		}
	}
	
	
	/**
	 * Delete media.
	 * @param mediaToDelete Media to delete.
	 * @return True if deletion process starts successfully.
	 */
	public boolean deleteMedia(Media mediaToDelete)
	{
		return this.deleteMedia(mediaToDelete, null);
	}
	
	
	/**
	 * Delete media.
	 * @param mediaToDelete Media to delete.
	 * @param callback Call-back to receive deletion state.
	 * @return True if deletion process starts successfully.
	 */
	public boolean deleteMedia(Media mediaToDelete, MediaDeletionCallback callback)
	{
		if(mediaToDelete == null)
		{
			Log.w(TAG, "deleteMedia() - No media to delete");
			return false;
		}
		return this.deleteMedia(Arrays.asList(mediaToDelete), callback);
	}
	
	
	/**
	 * Delete media.
	 * @param mediaToDelete Media to delete.
	 * @return True if deletion process starts successfully.
	 */
	public boolean deleteMedia(final Collection<Media> mediaToDelete)
	{
		return this.deleteMedia(mediaToDelete, null);
	}
	
	
	/**
	 * Delete media.
	 * @param mediaToDelete Media to delete.
	 * @param callback Call-back to receive deletion state.
	 * @return True if deletion process starts successfully.
	 */
	public boolean deleteMedia(final Collection<Media> mediaToDelete, final MediaDeletionCallback callback)
	{
		// check state
		this.verifyAccess();
		if(this.get(PROP_IS_DELETING_MEDIA))
		{
			Log.w(TAG, "deleteMedia() - Deleting media");
			return false;
		}
		if(mediaToDelete == null || mediaToDelete.isEmpty())
		{
			Log.w(TAG, "deleteMedia() - No media to delete");
			return false;
		}
		
		// collect media
		boolean deleteOriginal = false;
		MediaType mediaType = null;
		final List<Media> mediaList = new ArrayList<Media>(mediaToDelete.size());
		for(Media media : mediaToDelete)
		{
			if(media != null)
			{
				if(mediaType == null)
					mediaType = media.getType();
				else if(mediaType != MediaType.UNKNOWN && mediaType != media.getType())
					mediaType = MediaType.UNKNOWN;
				if(media.isOriginal())
					deleteOriginal = true;
				mediaList.add(media);
			}
		}
		if(mediaList.isEmpty())
		{
			Log.w(TAG, "deleteMedia() - No media to delete");
			return false;
		}
		
		// select messages
		final String title;
		final String message;
		if(mediaList.size() == 1)
		{
			switch(mediaType)
			{
				case PHOTO:
					title = this.getString(R.string.delete_message_title_photo);
					message = this.getString(deleteOriginal ? R.string.delete_message_photos_original : R.string.delete_message_photos);
					break;
				case VIDEO:
					title = this.getString(R.string.delete_message_title_video);
					message = this.getString(deleteOriginal ? R.string.delete_message_videos_original : R.string.delete_message_videos);
					break;
				default:
					Log.e(TAG, "deleteMedia() - Unknown media type");
					return false;
			}
		}
		else
		{
			switch(mediaType)
			{
				case PHOTO:
					title = String.format(this.getString(R.string.delete_message_title_photos), mediaList.size());
					message = this.getString(deleteOriginal ? R.string.delete_message_photos_original : R.string.delete_message_photos);
					break;
				case VIDEO:
					title = String.format(this.getString(R.string.delete_message_title_videos), mediaList.size());
					message = this.getString(deleteOriginal ? R.string.delete_message_videos_original : R.string.delete_message_videos);
					break;
				default:
					title = String.format(this.getString(R.string.delete_message_title_files), mediaList.size());
					message = this.getString(deleteOriginal ? R.string.delete_message_files_original : R.string.delete_message_files);
					break;
			}
		}
		
		// confirm
		final MediaType finalMediaType = mediaType;
		GalleryDialogFragment dialogFragment = new GalleryDialogFragment()
		{
			@Override
			public Dialog onCreateDialog(Bundle savedInstanceState)
			{
				this.setRetainInstance(true);
				return new AlertDialog.Builder(this.getActivity())
					.setTitle(title)
					.setMessage(message)
					.setPositiveButton(R.string.dialog_button_delete, new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int which)
						{
							dismiss();
							deleteMedia(getGalleryActivity(), mediaList, finalMediaType, callback);
						}
					})
					.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int which)
						{
							dialog.cancel();
						}
					})
					.create();
			}
		};
		dialogFragment.show(this.getFragmentManager(), "GalleryActivity.MediaDeletionConfirmation");
		
		// complete
		return true;
	}
	
	
	// Delete media directly.
	private static void deleteMedia(GalleryActivity activity, final List<Media> mediaList, MediaType mediaType, final MediaDeletionCallback callback)
	{
		// select title
		final Dialog dialog = new Dialog(activity);
		String title;
		if(mediaList.size() == 1)
		{
			switch(mediaType)
			{
				case PHOTO:
					title = activity.getString(R.string.delete_message_title_deleting_photo);
					break;
				case VIDEO:
					title = activity.getString(R.string.delete_message_title_deleting_video);
					break;
				default:
					Log.e(STATIC_TAG, "deleteMediaDirectly() - Unknown media type");
					return;
			}
		}
		else
		{
			switch(mediaType)
			{
				case PHOTO:
					title = String.format(activity.getString(R.string.delete_message_title_deleting_photos), mediaList.size());
					break;
				case VIDEO:
					title = String.format(activity.getString(R.string.delete_message_title_deleting_videos), mediaList.size());
					break;
				default:
					title = String.format(activity.getString(R.string.delete_message_title_deleting_files), mediaList.size());
					break;
			}
		}
		dialog.setTitle(title);
		
		// prepare content
		dialog.setContentView(R.layout.dialog_deletion);
		final ProgressBar progressBar = (ProgressBar)dialog.findViewById(android.R.id.progress);
		
		// show dialog
		dialog.setCancelable(false);
		dialog.setCanceledOnTouchOutside(false);
		final GalleryDialogFragment dialogFragment = new GalleryDialogFragment()
		{
			@Override
			public Dialog onCreateDialog(Bundle savedInstanceState)
			{
				return dialog;
			}
		};
		dialogFragment.show(activity.getFragmentManager(), "GalleryActivity.MediaDeletion");
		
		// create call-back
		final int[] deletedCount = new int[1];
		MediaSet.MediaDeletionCallback deletionCallback = new MediaSet.MediaDeletionCallback()
		{
			@Override
			public void onDeletionStarted(MediaSet mediaSet, Media media)
			{
				if(callback != null)
					callback.onDeletionStarted(media);
			}
			
			@Override
			public void onDeletionCompleted(MediaSet mediaSet, Media media, boolean success)
			{
				// update state
				++deletedCount[0];
				boolean isLastMedia = (deletedCount[0] >= mediaList.size());
				if(isLastMedia)
				{
					GalleryActivity activity = dialogFragment.getGalleryActivity();
					if(activity != null)
						activity.setReadOnly(PROP_IS_DELETING_MEDIA, false);
				}
				
				// update UI
				if(progressBar != null)
					progressBar.setProgress(Math.round((float)deletedCount[0] / mediaList.size() * progressBar.getMax()));
				if(isLastMedia)
					dialogFragment.dismiss();
				
				// call-back
				if(callback != null)
				{
					callback.onDeletionCompleted(media, success);
					if(isLastMedia)
						callback.onDeletionProcessCompleted();
				}
			}
		};
		
		// delete media
		Handler handler = GalleryApplication.current().getHandler();
		activity.setReadOnly(PROP_IS_DELETING_MEDIA, true);
		if(callback != null)
			callback.onDeletionProcessStarted();
		for(int i = mediaList.size() - 1 ; i >= 0 ; --i)
		{
			Media media = mediaList.get(i);
			Handle handle = media.getMediaSet().deleteMedia(media, deletionCallback, handler, 0);
			if(!Handle.isValid(handle) && callback != null)
			{
				callback.onDeletionStarted(media);
				callback.onDeletionCompleted(media, false);
			}
		}
	}
	
	
	/**
	 * Delete media set.
	 * @param mediaSetToDelete Media set to delete.
	 * @return True if deletion process starts successfully.
	 */
	public boolean deleteMediaSet(Collection<MediaSet> mediaSetToDelete)
	{
		return this.deleteMediaSet(mediaSetToDelete, null);
	}
	
	
	/**
	 * Delete media set.
	 * @param mediaSetToDelete Media set to delete.
	 * @param callback Call-back to receive deletion state.
	 * @return True if deletion process starts successfully.
	 */
	public boolean deleteMediaSet(final Collection<MediaSet> mediaSetToDelete, final MediaSetDeletionCallback callback)
	{
		// check state
		this.verifyAccess();
		if(this.get(PROP_IS_DELETING_MEDIA))
		{
			Log.w(TAG, "deleteMedia() - Deleting media");
			return false;
		}
		if(mediaSetToDelete == null || mediaSetToDelete.isEmpty())
		{
			Log.w(TAG, "deleteMedia() - No media set to delete");
			return false;
		}
		
		// collect media
		boolean deleteOriginal = true;
		
		// select messages
		String title = null;
		String message = null;
		if(mediaSetToDelete.size() == 1)
		{
			title = String.format(this.getString(R.string.delete_message_title_media_set), ((MediaSet)mediaSetToDelete.toArray()[0]).get(MediaSet.PROP_NAME));	
		}
		else
		{
			title = String.format(this.getString(R.string.delete_message_title_media_sets), mediaSetToDelete.size());
		}
		message = this.getString(deleteOriginal ? R.string.delete_message_media_set_original : R.string.delete_message_media_set);
		
		// confirm
		AlertDialog dialog = new AlertDialog.Builder(this)
			.setTitle(title)
			.setMessage(message)
			.setPositiveButton(R.string.dialog_button_delete, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					deleteMediaSetInternal(mediaSetToDelete, callback);
				}
			})
			.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					dialog.cancel();
				}
			})
			.create();
		dialog.show();
		
		// complete
		return true;
	}
	
	private void deleteMediaSetInternal(final Collection<MediaSet> mediaSetToDelete, final MediaSetDeletionCallback callback)
	{
		// select title
		final Dialog dialog = new Dialog(this);
		String title;
		if(mediaSetToDelete.size() == 1)
		{
			title = String.format(this.getString(R.string.delete_message_title_deleting_media_set), ((MediaSet)mediaSetToDelete.toArray()[0]).get(MediaSet.PROP_NAME));
		}
		else
		{
			title = String.format(this.getString(R.string.delete_message_title_deleting_media_sets), mediaSetToDelete.size());
		}
		dialog.setTitle(title);
		
		// prepare content
		dialog.setContentView(R.layout.dialog_deletion);
		final ProgressBar progressBar = (ProgressBar)dialog.findViewById(android.R.id.progress);
		
		// show dialog
		dialog.setCancelable(false);
		dialog.show();
		
		// create call-back
		final int[] deletedCount = new int[1];
		MediaSet.DeletionCallback deletionCallback = new MediaSet.DeletionCallback()
		{
			@Override
			public void onDeletionStarted(MediaSet mediaSet)
			{
				if(callback != null)
					callback.onDeletionStarted(mediaSet);
			}
			
			@Override
			public void onDeletionCompleted(MediaSet mediaSet, boolean success)
			{
				// update state
				++deletedCount[0];
				boolean isLastMedia = (deletedCount[0] >= mediaSetToDelete.size());
				if(isLastMedia)
					setReadOnly(PROP_IS_DELETING_MEDIA, false);
				
				// update UI
				if(progressBar != null)
					progressBar.setProgress(Math.round((float)deletedCount[0] / mediaSetToDelete.size() * progressBar.getMax()));
				if(isLastMedia && dialog != null)
					dialog.dismiss();
				
				if(callback != null)
				{
					callback.onDeletionCompleted(mediaSet, success);
					if(isLastMedia)
						callback.onDeletionProcessCompleted();
				}
			}
		};
		
		// delete media
		Handler handler = this.getHandler();
		this.setReadOnly(PROP_IS_DELETING_MEDIA, true);
		if(callback != null)
			callback.onDeletionProcessStarted();
		for(int i = mediaSetToDelete.size() - 1 ; i >= 0 ; --i)
		{
			MediaSet mediaSet = (MediaSet)((List)mediaSetToDelete).get(i);
			Handle handle = mediaSet.delete(deletionCallback, handler, 0);

			if(!Handle.isValid(handle) && callback != null)
			{
				callback.onDeletionStarted(mediaSet);
				callback.onDeletionCompleted(mediaSet, false);
			}
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
			this.setReadOnly(PROP_IS_DELETING_MEDIA, stateFragment.isDeletingMedia);
			this.setReadOnly(PROP_IS_SHARING_MEDIA, stateFragment.isSharingMedia);
			this.setReadOnly(PROP_IS_STATUS_BAR_VISIBLE, stateFragment.isStatusBarVisible);
			m_SystemUIManager = stateFragment.systemUIManager;
			m_StatusBarVisibilityHandles = stateFragment.statusBarVisibilityHandles;
		}
		else
		{
			m_SystemUIManager = new SystemUIManager();
			m_StatusBarVisibilityHandles = new ArrayList<>();
		}
		m_SystemUIManager.attach(this);
		
		// initialize screen size
		this.updateScreenSize();
		
		// initialize system UI state
		View decorView = this.getWindow().getDecorView();
		decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener()
		{
			@Override
			public void onSystemUiVisibilityChange(int visibility)
			{
				onSystemUIVisibilityChanged(visibility);
			}
		});
		this.setSystemUIVisibility(this.get(PROP_IS_STATUS_BAR_VISIBLE), this.get(PROP_IS_NAVIGATION_BAR_VISIBLE));
		
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
		// detach system UI manager
		m_SystemUIManager.detach(this);
		
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
		stateFragment.isDeletingMedia = this.get(PROP_IS_DELETING_MEDIA);
		stateFragment.isSharingMedia = this.get(PROP_IS_SHARING_MEDIA);
		stateFragment.isStatusBarVisible = this.get(PROP_IS_STATUS_BAR_VISIBLE);
		stateFragment.systemUIManager = m_SystemUIManager;
		stateFragment.statusBarVisibilityHandles = m_StatusBarVisibilityHandles;
		this.onSaveInstanceState(outState, stateFragment.extras);
		
		// call super
		super.onSaveInstanceState(outState);
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
	
	
	// Called when system UI visibility changed.
	private void onSystemUIVisibilityChanged(int visibility)
	{
		// check visibilities
		boolean isStatusBarVisible = ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0);
		boolean isNavBarVisible = ((visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0);
		
		// check status bar visibility
		Boolean showStatusBar = null;
		for(int i = m_StatusBarVisibilityHandles.size() - 1 ; i >= 0 ; --i)
		{
			StatusBarVisibilityHandle handle = m_StatusBarVisibilityHandles.get(i);
			if(handle.isVisible != isStatusBarVisible)
			{
				if((handle.flags & FLAG_CANCELABLE) == 0)
					showStatusBar = handle.isVisible;
				else
				{
					handle.drop();
					m_StatusBarVisibilityHandles.remove(i);
				}
			}
		}
		
		// check navigation bar
		Boolean showNavBar = null;
		
		// update system UI visibility
		if(showStatusBar != null || showNavBar != null)
			this.setSystemUIVisibility(showStatusBar, showNavBar);
	}
	
	
	// Restore status bar visibility.
	private void restoreStatusBarVisibility(StatusBarVisibilityHandle handle)
	{
		boolean isLast = ListUtils.isLastObject(m_StatusBarVisibilityHandles, handle);
		if(m_StatusBarVisibilityHandles.remove(handle) && isLast)
		{
			if(m_StatusBarVisibilityHandles.isEmpty())
				this.setSystemUIVisibility(true, null);
			else
				this.setSystemUIVisibility(m_StatusBarVisibilityHandles.get(m_StatusBarVisibilityHandles.size() - 1).isVisible, null);
		}
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
		for(int i = m_StatusBarVisibilityHandles.size() - 1 ; i >= 0 ; --i)
		{
			StatusBarVisibilityHandle handle = m_StatusBarVisibilityHandles.get(i);
			if(handle.isVisible != isVisible && (handle.flags & FLAG_CANCELABLE) != 0)
			{
				handle.drop();
				m_StatusBarVisibilityHandles.remove(i);
			}
		}
		StatusBarVisibilityHandle handle = new StatusBarVisibilityHandle(m_SystemUIManager, isVisible, flags);
		m_StatusBarVisibilityHandles.add(handle);
		this.setSystemUIVisibility(isVisible, null);
		return handle;
	}
	
	
	// Set system UI visibility.
	private boolean setSystemUIVisibility(Boolean isStatusBarVisible, Boolean isNavBarVisible)
	{
		// check state
		Window window = this.getWindow();
		if(window == null)
		{
			Log.e(TAG, "setSystemUIVisibility() - No window");
			return false;
		}
		
		// prepare
		int visibility = window.getDecorView().getSystemUiVisibility();
		if(isStatusBarVisible != null)
		{
			if(isStatusBarVisible)
				visibility &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
			else
				visibility |= View.SYSTEM_UI_FLAG_FULLSCREEN;
		}
		if(isNavBarVisible != null)
		{
			if(isNavBarVisible)
				visibility &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
			else
				visibility |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
		}
		visibility |= (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
		
		// update
		window.getDecorView().setSystemUiVisibility(visibility);
		
		// update property
		if(isStatusBarVisible != null && this.setReadOnly(PROP_IS_STATUS_BAR_VISIBLE, isStatusBarVisible))
			this.onStatusBarVisibilityChanged(isStatusBarVisible);
		if(isNavBarVisible != null && this.setReadOnly(PROP_IS_NAVIGATION_BAR_VISIBLE, isNavBarVisible))
			this.onNavigationBarVisibilityChanged(isNavBarVisible);
		
		// complete
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
