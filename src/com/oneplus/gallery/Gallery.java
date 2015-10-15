package com.oneplus.gallery;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;

import com.oneplus.base.Handle;
import com.oneplus.base.HandlerBaseObject;
import com.oneplus.base.Log;
import com.oneplus.base.PropertyChangeEventArgs;
import com.oneplus.base.PropertyChangedCallback;
import com.oneplus.base.PropertyKey;
import com.oneplus.base.PropertySource;
import com.oneplus.base.ScreenSize;
import com.oneplus.gallery.media.Media;
import com.oneplus.gallery.media.MediaSet;
import com.oneplus.util.ListUtils;

/**
 * Gallery instance.
 */
public class Gallery extends HandlerBaseObject
{
	/**
	 * Read-only property to get current activity.
	 */
	public static final PropertyKey<GalleryActivity> PROP_ACTIVITY = new PropertyKey<>("Activity", GalleryActivity.class, Gallery.class, PropertyKey.FLAG_READONLY, null);
	/**
	 * Read-only property to check if there is dialog visible.
	 */
	public static final PropertyKey<Boolean> PROP_HAS_DIALOG = new PropertyKey<>("HasDialog", Boolean.class, Gallery.class, false);
	/**
	 * Read-only property to get current media deletion state.
	 */
	public static final PropertyKey<Boolean> PROP_IS_DELETING_MEDIA = new PropertyKey<>("IsDeletingMedia", Boolean.class, Gallery.class, false);
	/**
	 * Read-only property to get navigation bar visibility.
	 */
	public static final PropertyKey<Boolean> PROP_IS_NAVIGATION_BAR_VISIBLE = new PropertyKey<>("IsNavigationBarVisible", Boolean.class, Gallery.class, true);
	/**
	 * Read-only property to get current media sharing state.
	 */
	public static final PropertyKey<Boolean> PROP_IS_SHARING_MEDIA = new PropertyKey<>("IsSharingMedia", Boolean.class, Gallery.class, false);
	/**
	 * Read-only property to get status bar visibility.
	 */
	public static final PropertyKey<Boolean> PROP_IS_STATUS_BAR_VISIBLE = new PropertyKey<>("IsStatusBarVisible", Boolean.class, Gallery.class, true);
	
	
	/**
	 * Flag to indicate that operation can be cancelled.
	 */
	public static final int FLAG_CANCELABLE = 0x1;
	
	
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
	
	
	// Constants.
	private static final long DURATION_CHECK_INSTANCES_DELAY = 3000;
	
	
	// Static fields.
	private static final Map<String, Gallery> m_Galleries = new HashMap<>();
	private static final List<WeakReference<Gallery>> m_TrackingInstances = new ArrayList<>();
	
	
	// Fields.
	private final String TAG;
	private GalleryActivity m_Activity;
	private View m_ActivityDecorView;
	private final List<ActivityHandle> m_AttachedActivityHandles = new ArrayList<>();
	private final List<Handle> m_GalleryDialogHandles = new ArrayList<>();
	private boolean m_HasNavigationBar;
	private final String m_Id;
	private final List<NavBarVisibilityHandle> m_NavBarVisibilityHandles = new ArrayList<>();
	private final List<StatusBarVisibilityHandle> m_StatusBarVisibilityHandles = new ArrayList<>();
	
	
	// Listeners.
	private final View.OnSystemUiVisibilityChangeListener m_SystemUiVisibilityListener = new View.OnSystemUiVisibilityChangeListener()
	{
		@Override
		public void onSystemUiVisibilityChange(int visibility)
		{
			if(m_Activity != null && m_Activity.get(GalleryActivity.PROP_IS_RUNNING))
				onSystemUiVisibilityChanged(visibility);
		}
	};
	
	
	// Call-backs.
	private final PropertyChangedCallback<Boolean> m_ActivityRunningStateCallback = new PropertyChangedCallback<Boolean>()
	{
		@Override
		public void onPropertyChanged(PropertySource source, PropertyKey<Boolean> key, PropertyChangeEventArgs<Boolean> e)
		{
			if(e.getNewValue())
			{
				checkSystemNavigationBarState(m_Activity);
				setSystemUiVisibility();
			}
		}
	};
	private final GalleryActivity.ActivityResultCallback m_MediaShareResultCallback = new GalleryActivity.ActivityResultCallback()
	{
		@Override
		public void onActivityResult(Handle handle, int result, Intent data)
		{
			setReadOnly(PROP_IS_SHARING_MEDIA, false);
		}
	};
	
	
	// Runnables.
	private static final Runnable m_CheckInstancesRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			checkInstances(0);
		}
	};
	
	
	// System UI visibility handle.
	private abstract class SystemUiVisibilityHandle extends Handle
	{
		// Fields.
		public final int flags;
		public final boolean isVisible;
		
		// Constructor.
		protected SystemUiVisibilityHandle(String name, boolean isVisible, int flags)
		{
			super(name);
			this.isVisible = isVisible;
			this.flags = flags;
		}
		
		// Drop this handle.
		public final void drop()
		{
			this.closeDirectly();
		}
	}
	private final class StatusBarVisibilityHandle extends SystemUiVisibilityHandle
	{
		// Constructor.
		public StatusBarVisibilityHandle(boolean isVisible, int flags)
		{
			super("StatusBarVisibility", isVisible, flags);
		}

		// Cose handle
		@Override
		protected void onClose(int flags)
		{
			restoreStatusBarVisibility(this);
		}
	}
	private final class NavBarVisibilityHandle extends SystemUiVisibilityHandle
	{
		// Constructor.
		public NavBarVisibilityHandle(boolean isVisible, int flags)
		{
			super("NavBarVisibility", isVisible, flags);
		}

		// Close handle
		@Override
		protected void onClose(int flags)
		{
			restoreNavigationBarVisibility(this);
		}
	}
	
	
	// Class for attached activity handle.
	private final class ActivityHandle extends Handle
	{
		// Fields.
		public final GalleryActivity activity;
		
		// Constructor.
		public ActivityHandle(GalleryActivity activity)
		{
			super("AttachedActivity");
			this.activity = activity;
		}
		
		// Close handle.
		@Override
		protected void onClose(int flags)
		{
			detachActivity(this);
		}
	}
	
	
	/**
	 * Initialize new Gallery instance.
	 */
	Gallery()
	{
		// call super
		super(true);
		
		// start tracking
		trackInstance(this);
		
		// check thread
		if(!GalleryApplication.current().isDependencyThread())
			throw new RuntimeException("Can only create in main thread");
		
		// generate ID
		char[] idBuffer = new char[4];
		while(true)
		{
			for(int i = idBuffer.length - 1 ; i >= 0 ; --i)
			{
				int n = (int)(Math.random() * 36);
				if(n < 10)
					idBuffer[i] = (char)('0' + n);
				else
					idBuffer[i] = (char)('a' + (n - 10));
			}
			String id = new String(idBuffer);
			if(!m_Galleries.containsKey(id))
			{
				m_Id = id;
				TAG = ("Gallery(" + id + ")");
				m_Galleries.put(id, this);
				break;
			}
		}
		Log.w(TAG, "Create, total instance count : " + m_Galleries.size());
		
		// enable logs
		this.enablePropertyLogs(PROP_ACTIVITY, LOG_PROPERTY_CHANGE);
	}
	
	
	/**
	 * Attach activity to this instance.
	 * @param activity {@link GalleryActivity} to attach.
	 * @return Handle to this operation.
	 */
	final Handle attachActivity(GalleryActivity activity)
	{
		// check state
		this.verifyAccess();
		if(this.get(PROP_IS_RELEASED))
		{
			Log.e(TAG, "attachActivity() - Instance has been released");
			return null;
		}
		if(activity == null)
		{
			Log.e(TAG, "attachActivity() - No activity");
			return null;
		}
		
		// check window
		Window window = activity.getWindow();
		if(window == null)
		{
			Log.e(TAG, "attachActivity() - No window");
			return null;
		}
		
		// attach to decor view
		if(m_ActivityDecorView != null)
		{
			m_ActivityDecorView.setOnSystemUiVisibilityChangeListener(null);
			m_ActivityDecorView = null;
		}
		m_ActivityDecorView = window.getDecorView();
		m_ActivityDecorView.setOnSystemUiVisibilityChangeListener(m_SystemUiVisibilityListener);
		
		// create handle
		ActivityHandle handle = new ActivityHandle(activity);
		m_AttachedActivityHandles.add(handle);
		
		// attach to activity
		GalleryActivity prevActivity = m_Activity;
		m_Activity = activity;
		m_Activity.addCallback(GalleryActivity.PROP_IS_RUNNING, m_ActivityRunningStateCallback);
		this.notifyPropertyChanged(PROP_ACTIVITY, prevActivity, activity);
		
		// check navigation bar
		this.checkSystemNavigationBarState(activity);
		
		// setup system UI visibility
		this.setSystemUiVisibility(this.get(PROP_IS_STATUS_BAR_VISIBLE), this.get(PROP_IS_NAVIGATION_BAR_VISIBLE) && m_HasNavigationBar);
		
		// complete
		return handle;
	}
	
	
	// Check alive instances.
	private static void checkInstances(long delayMillis)
	{
		// check immediately
		Handler handler = GalleryApplication.current().getHandler();
		handler.removeCallbacks(m_CheckInstancesRunnable);
		if(delayMillis <= 0)
		{
			for(int i = m_TrackingInstances.size() - 1 ; i >= 0 ; --i)
			{
				Object instance = m_TrackingInstances.get(i).get();
				if(instance == null)
					m_TrackingInstances.remove(i);
			}
			Log.w("Gallery", "checkInstances() - Alive instances : " + m_TrackingInstances.size());
			return;
		}
		
		// check later
		handler.postDelayed(m_CheckInstancesRunnable, delayMillis);
	}
	
	
	// Check navigation bar state
	private void checkSystemNavigationBarState(Activity activity)
	{
		// check state
		if(activity == null)
		{
			Log.w(TAG, "checkSystemNavigationBarState() - No activity to check");
			return;
		}
		
		// get window size
		Display display = activity.getWindowManager().getDefaultDisplay();
		Point size = new Point();
		Point realSize = new Point();
		display.getSize(size);
		display.getRealSize(realSize);
		
		// check navigation bar
		ScreenSize screenSize = new ScreenSize(activity, true);
		if(screenSize.getWidth() <= screenSize.getHeight())
			m_HasNavigationBar = ((realSize.y - size.y) > screenSize.getStatusBarSize());
		else
			m_HasNavigationBar = (realSize.x > size.x);
		Log.v(TAG, "checkSystemNavigationBarState() - Has navigation bar : ", m_HasNavigationBar);
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
		
		// check activity
		Activity activity = m_Activity;
		if(activity == null)
		{
			Log.w(TAG, "deleteMedia() - No activity to show dialog");
			this.deleteMedia(mediaList, mediaType, callback);
			return true;
		}
		
		// select messages
		final String title;
		final String message;
		if(mediaList.size() == 1)
		{
			switch(mediaType)
			{
				case PHOTO:
					title = activity.getString(R.string.delete_message_title_photo);
					message = activity.getString(deleteOriginal ? R.string.delete_message_photos_original : R.string.delete_message_photos);
					break;
				case VIDEO:
					title = activity.getString(R.string.delete_message_title_video);
					message = activity.getString(deleteOriginal ? R.string.delete_message_videos_original : R.string.delete_message_videos);
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
					title = String.format(activity.getString(R.string.delete_message_title_photos), mediaList.size());
					message = activity.getString(deleteOriginal ? R.string.delete_message_photos_original : R.string.delete_message_photos);
					break;
				case VIDEO:
					title = String.format(activity.getString(R.string.delete_message_title_videos), mediaList.size());
					message = activity.getString(deleteOriginal ? R.string.delete_message_videos_original : R.string.delete_message_videos);
					break;
				default:
					title = String.format(activity.getString(R.string.delete_message_title_files), mediaList.size());
					message = activity.getString(deleteOriginal ? R.string.delete_message_files_original : R.string.delete_message_files);
					break;
			}
		}
		
		// create dialog
		final MediaType finalMediaType = mediaType;
		final AlertDialog dialog = new AlertDialog.Builder(activity)
			.setTitle(title)
			.setMessage(message)
			.setPositiveButton(R.string.dialog_button_delete, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					dialog.cancel();
					deleteMedia(mediaList, finalMediaType, callback);
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
		
		// confirm
		GalleryDialogFragment dialogFragment = new GalleryDialogFragment()
		{
			@Override
			public Dialog onCreateDialog(Bundle savedInstanceState)
			{
				return dialog;
			}
		};
		dialogFragment.show(activity.getFragmentManager(), "Gallery.MediaDeletionConfirmation");
		
		// complete
		return true;
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
			Log.w(TAG, "deleteMediaSet() - Deleting media");
			return false;
		}
		if(mediaSetToDelete == null || mediaSetToDelete.isEmpty())
		{
			Log.w(TAG, "deleteMediaSet() - No media set to delete");
			return false;
		}
		
		// collect media set
		final List<MediaSet> mediaSetList = new ArrayList<>(mediaSetToDelete);
		
		// check activity
		Activity activity = m_Activity;
		if(activity == null)
		{
			Log.w(TAG, "deleteMediaSet() - No activity to show dialog");
			this.deleteMediaSet(mediaSetList, callback);
			return true;
		}
		
		// collect media
		boolean deleteOriginal = true;
		
		// select messages
		String title = null;
		String message = null;
		if(mediaSetToDelete.size() == 1)
		{
			title = String.format(activity.getString(R.string.delete_message_title_media_set), ((MediaSet)mediaSetToDelete.toArray()[0]).get(MediaSet.PROP_NAME));	
		}
		else
		{
			title = String.format(activity.getString(R.string.delete_message_title_media_sets), mediaSetToDelete.size());
		}
		message = activity.getString(deleteOriginal ? R.string.delete_message_media_set_original : R.string.delete_message_media_set);
		
		// create dialog
		final AlertDialog dialog = new AlertDialog.Builder(activity)
			.setTitle(title)
			.setMessage(message)
			.setPositiveButton(R.string.dialog_button_delete, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					dialog.cancel();
					deleteMediaSet(mediaSetList, callback);
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
		new GalleryDialogFragment()
		{
			public Dialog onCreateDialog(Bundle savedInstanceState)
			{
				return dialog;
			}
		}.show(activity.getFragmentManager(), "Gallery.MediaSetDeletionConfirmation");
		
		// complete
		return true;
	}
	
	
	// Delete media directly.
	private void deleteMedia(final List<Media> mediaList, MediaType mediaType, final MediaDeletionCallback callback)
	{
		// check state
		if(this.get(PROP_IS_DELETING_MEDIA))
		{
			Log.e(TAG, "deleteMedia() - Deleting media");
			return;
		}
		
		// get context
		Activity activity = m_Activity;
		if(activity == null)
			Log.w(TAG, "deleteMedia() - No activity to show dialog");
		
		// create dialog
		final Dialog dialog;
		final ProgressBar progressBar;
		final GalleryDialogFragment dialogFragment;
		if(activity != null)
		{
			// select title
			dialog = new Dialog(activity);
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
						Log.e(TAG, "deleteMedia() - Unknown media type");
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
			progressBar = (ProgressBar)dialog.findViewById(android.R.id.progress);
			
			// show dialog
			dialog.setCancelable(false);
			dialog.setCanceledOnTouchOutside(false);
			dialogFragment = new GalleryDialogFragment()
			{
				@Override
				public Dialog onCreateDialog(Bundle savedInstanceState)
				{
					return dialog;
				}
			};
			dialogFragment.show(activity.getFragmentManager(), "Gallery.MediaDeletion");
		}
		else
		{
			dialog = null;
			progressBar = null;
			dialogFragment = null;
		}
		
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
					setReadOnly(PROP_IS_DELETING_MEDIA, false);
				
				// update UI
				if(progressBar != null)
					progressBar.setProgress(Math.round((float)deletedCount[0] / mediaList.size() * progressBar.getMax()));
				if(isLastMedia && dialogFragment != null)
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
		Handler handler = this.getHandler();
		this.setReadOnly(PROP_IS_DELETING_MEDIA, true);
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
	
	
	// Delete media set directly.
	private void deleteMediaSet(final List<MediaSet> mediaSetToDelete, final MediaSetDeletionCallback callback)
	{
		// check state
		if(this.get(PROP_IS_DELETING_MEDIA))
		{
			Log.e(TAG, "deleteMediaSet() - Deleting media");
			return;
		}
		
		// get context
		Activity activity = m_Activity;
		if(activity == null)
			Log.w(TAG, "deleteMediaSet() - No activity to show dialog");
		
		// create dialog
		final Dialog dialog;
		final ProgressBar progressBar;
		final GalleryDialogFragment dialogFragment;
		if(activity != null)
		{
			// select title
			dialog = new Dialog(activity);
			String title;
			if(mediaSetToDelete.size() == 1)
			{
				title = String.format(activity.getString(R.string.delete_message_title_deleting_media_set), ((MediaSet)mediaSetToDelete.toArray()[0]).get(MediaSet.PROP_NAME));
			}
			else
			{
				title = String.format(activity.getString(R.string.delete_message_title_deleting_media_sets), mediaSetToDelete.size());
			}
			dialog.setTitle(title);
			
			// prepare content
			dialog.setContentView(R.layout.dialog_deletion);
			progressBar = (ProgressBar)dialog.findViewById(android.R.id.progress);
			
			// show dialog
			dialog.setCancelable(false);
			dialog.setCanceledOnTouchOutside(false);
			dialogFragment = new GalleryDialogFragment()
			{
				public Dialog onCreateDialog(Bundle savedInstanceState)
				{
					return dialog;
				}
			};
			dialogFragment.show(activity.getFragmentManager(), "Gallery.MediaSetDeletion");
		}
		else
		{
			dialog = null;
			progressBar = null;
			dialogFragment = null;
		}
		
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
				if(isLastMedia && dialogFragment != null)
					dialogFragment.dismiss();
				
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
			MediaSet mediaSet = mediaSetToDelete.get(i);
			Handle handle = mediaSet.delete(deletionCallback, handler, 0);
			if(!Handle.isValid(handle) && callback != null)
			{
				callback.onDeletionStarted(mediaSet);
				callback.onDeletionCompleted(mediaSet, false);
			}
		}
	}
	
	
	// Detach activity.
	private void detachActivity(ActivityHandle handle)
	{
		// check state
		this.verifyAccess();
		boolean isLast = ListUtils.isLastObject(m_AttachedActivityHandles, handle);
		if(!m_AttachedActivityHandles.remove(handle) || !isLast)
			return;
		
		// detach from decor view
		if(m_ActivityDecorView != null)
		{
			m_ActivityDecorView.setOnSystemUiVisibilityChangeListener(null);
			m_ActivityDecorView = null;
		}
		
		// detach from activity
		if(m_Activity != null)
			m_Activity.removeCallback(GalleryActivity.PROP_IS_RUNNING, m_ActivityRunningStateCallback);
		
		// attach to previous activity and decor view
		GalleryActivity prevActivity = m_Activity;
		m_Activity = (m_AttachedActivityHandles.isEmpty() ? null : m_AttachedActivityHandles.get(m_AttachedActivityHandles.size() - 1).activity);
		if(m_Activity != null)
		{
			Window window = m_Activity.getWindow();
			if(window != null)
			{
				m_ActivityDecorView = window.getDecorView();
				m_ActivityDecorView.setOnSystemUiVisibilityChangeListener(m_SystemUiVisibilityListener);
			}
			else
				Log.e(TAG, "detachActivity() - No window");
			m_Activity.addCallback(GalleryActivity.PROP_IS_RUNNING, m_ActivityRunningStateCallback);
		}
		
		// detach from activity
		this.notifyPropertyChanged(PROP_ACTIVITY, prevActivity, m_Activity);
		
		// update system UI visibility
		if(m_Activity != null)
		{
			this.checkSystemNavigationBarState(m_Activity);
			this.setSystemUiVisibility(this.get(PROP_IS_STATUS_BAR_VISIBLE), this.get(PROP_IS_NAVIGATION_BAR_VISIBLE) && m_HasNavigationBar);
		}
	}
	
	
	/**
	 * Get instance from ID.
	 * @param id ID of {@link Gallery}.
	 * @return {@link Gallery} instance, or Null if ID is not found.
	 */
	public static Gallery fromId(String id)
	{
		if(id != null)
			return m_Galleries.get(id);
		return null;
	}
	
	
	// Get property.
	@SuppressWarnings("unchecked")
	@Override
	public <TValue> TValue get(PropertyKey<TValue> key)
	{
		if(key == PROP_ACTIVITY)
			return (TValue)m_Activity;
		return super.get(key);
	}
	
	
	/**
	 * Get unique ID.
	 * @return Unique ID of this instance.
	 */
	public final String getId()
	{
		return m_Id;
	}
	
	
	// Nofity show dialog, call from GalleryDialogFragment.
	final Handle notifyShowDialog()
	{
		// check state
		this.verifyAccess();
		
		// create handle
		Handle handle = new Handle("Gallery Dialog Handle")
		{
			@Override
			protected void onClose(int flags)
			{
				// remove from handles
				m_GalleryDialogHandles.remove(this);
				
				// check remaining handle counts
				if(m_GalleryDialogHandles.isEmpty())
					Gallery.this.setReadOnly(PROP_HAS_DIALOG, false);
			}
		};
		m_GalleryDialogHandles.add(handle);
		this.setReadOnly(PROP_HAS_DIALOG, true);
		return handle;
	}
	
	
	// Release.
	@Override
	protected void onRelease()
	{
		// call super
		super.onRelease();
		
		// detach from activity
		if(m_ActivityDecorView != null)
		{
			m_ActivityDecorView.setOnSystemUiVisibilityChangeListener(null);
			m_ActivityDecorView = null;
		}
		m_Activity = null;
		m_AttachedActivityHandles.clear();
		m_GalleryDialogHandles.clear();
		
		// remove from table
		m_Galleries.remove(m_Id);
		
		Log.w(TAG, "Release, total instance count : " + m_Galleries.size());
		
		// check instances later
		checkInstances(DURATION_CHECK_INSTANCES_DELAY);
	}
	
	
	// Called when system UI visibility changed.
	private void onSystemUiVisibilityChanged(int visibility)
	{
		// check visibilities
		boolean isStatusBarVisible = ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0);
		boolean isNavBarVisible = (m_HasNavigationBar && (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0);
		
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
		for(int i = m_NavBarVisibilityHandles.size() - 1 ; i >= 0 ; --i)
		{
			NavBarVisibilityHandle handle = m_NavBarVisibilityHandles.get(i);
			if(handle.isVisible != isNavBarVisible)
			{
				if((handle.flags & FLAG_CANCELABLE) == 0)
					showNavBar = handle.isVisible;
				else
				{
					handle.drop();
					m_NavBarVisibilityHandles.remove(i);
				}
			}
		}
		
		// update system UI visibility
		if(showNavBar == null || (showNavBar != null && showNavBar == isNavBarVisible))
			this.setReadOnly(PROP_IS_NAVIGATION_BAR_VISIBLE, isNavBarVisible);
		if(showStatusBar == null || (showStatusBar != null && showStatusBar == isStatusBarVisible))
			this.setReadOnly(PROP_IS_STATUS_BAR_VISIBLE, isStatusBarVisible);
		if(showStatusBar != null || showNavBar != null)
			this.setSystemUiVisibility(showStatusBar, showNavBar);
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
	
	
	// Restore navigation bar visibility.
	private void restoreNavigationBarVisibility(NavBarVisibilityHandle handle)
	{
		boolean isLast = ListUtils.isLastObject(m_NavBarVisibilityHandles, handle);
		if(m_NavBarVisibilityHandles.remove(handle) && isLast)
		{
			if(m_NavBarVisibilityHandles.isEmpty())
				this.setSystemUiVisibility(null, m_HasNavigationBar);
			else
				this.setSystemUiVisibility(null, m_NavBarVisibilityHandles.get(m_NavBarVisibilityHandles.size() - 1).isVisible);
		}
	}
	
	
	// Restore status bar visibility.
	private void restoreStatusBarVisibility(StatusBarVisibilityHandle handle)
	{
		boolean isLast = ListUtils.isLastObject(m_StatusBarVisibilityHandles, handle);
		if(m_StatusBarVisibilityHandles.remove(handle) && isLast)
		{
			if(m_StatusBarVisibilityHandles.isEmpty())
				this.setSystemUiVisibility(true, null);
			else
				this.setSystemUiVisibility(m_StatusBarVisibilityHandles.get(m_StatusBarVisibilityHandles.size() - 1).isVisible, null);
		}
	}
	
	
	/**
	 * Set navigation bar visibility.
	 * @param isVisible True to show navigation bar.
	 * @return Handle to this operation.
	 */
	public Handle setNavigationBarVisibility(boolean isVisible)
	{
		return this.setNavigationBarVisibility(isVisible, FLAG_CANCELABLE);
	}
	
	
	/**
	 * Set navigation bar visibility.
	 * @param isVisible True to show navigation bar.
	 * @param flags Flags:
	 * <ul>
	 *   <li>{@link #FLAG_CANCELABLE}</li>
	 * </ul>
	 * @return Handle to this operation.
	 */
	public Handle setNavigationBarVisibility(boolean isVisible, int flags)
	{
		this.verifyAccess();
		for(int i = m_NavBarVisibilityHandles.size() - 1 ; i >= 0 ; --i)
		{
			NavBarVisibilityHandle handle = m_NavBarVisibilityHandles.get(i);
			if(handle.isVisible != isVisible && (handle.flags & FLAG_CANCELABLE) != 0)
			{
				handle.drop();
				m_NavBarVisibilityHandles.remove(i);
			}
		}
		NavBarVisibilityHandle handle = new NavBarVisibilityHandle(isVisible, flags);
		m_NavBarVisibilityHandles.add(handle);
		this.setSystemUiVisibility(null, isVisible);
		return handle;
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
		StatusBarVisibilityHandle handle = new StatusBarVisibilityHandle(isVisible, flags);
		m_StatusBarVisibilityHandles.add(handle);
		this.setSystemUiVisibility(isVisible, null);
		return handle;
	}
	
	
	// Set system UI visibility.
	private void setSystemUiVisibility()
	{
		// check status bar state
		boolean showStatusBar;
		if(m_StatusBarVisibilityHandles.isEmpty())
			showStatusBar = true;
		else
			showStatusBar = m_StatusBarVisibilityHandles.get(m_StatusBarVisibilityHandles.size() - 1).isVisible;
		
		// check navigation bar state
		boolean showNavBar;
		if(m_NavBarVisibilityHandles.isEmpty())
			showNavBar = m_HasNavigationBar;
		else
			showNavBar = m_NavBarVisibilityHandles.get(m_NavBarVisibilityHandles.size() - 1).isVisible;
		
		// set visibility
		this.setSystemUiVisibility(showStatusBar, showNavBar);
	}
	private boolean setSystemUiVisibility(Boolean isStatusBarVisible, Boolean isNavBarVisible)
	{
		// check state
		if(m_ActivityDecorView == null)
		{
			Log.e(TAG, "setSystemUiVisibility() - No window");
			return false;
		}
		if(!m_HasNavigationBar)
			isNavBarVisible = false;
		
		// prepare
		int visibility = m_ActivityDecorView.getSystemUiVisibility();
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
		visibility |= (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE);
		
		// update
		m_ActivityDecorView.setSystemUiVisibility(visibility);
		
		// update property
		if(isStatusBarVisible != null)
			this.setReadOnly(PROP_IS_STATUS_BAR_VISIBLE, isStatusBarVisible);
		if(isNavBarVisible != null)
			this.setReadOnly(PROP_IS_NAVIGATION_BAR_VISIBLE, isNavBarVisible);
		
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
		if(this.get(PROP_IS_SHARING_MEDIA))
		{
			Log.e(TAG, "shareMedia() - Waiting for previous sharing result");
			return false;
		}
		if(mediaToShare == null || mediaToShare.isEmpty())
		{
			Log.w(TAG, "shareMedia() - No media to share");
			return false;
		}
		if(m_Activity == null)
		{
			Log.e(TAG, "shareMedia() - No activity");
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
			m_Activity.startActivityForResult(Intent.createChooser(intent, m_Activity.getString(R.string.gallery_share_media)), m_MediaShareResultCallback);
		}
		catch(Throwable ex)
		{
			Log.e(TAG, "shareMedia() - Fail to start activity", ex);
			return false;
		}
		
		// complete
		this.setReadOnly(PROP_IS_SHARING_MEDIA, true);
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
		if(m_Activity == null)
		{
			Log.e(TAG, "showMediaDetails() - No activity");
			return;
		}
		MediaDetailsDialog dialog = new MediaDetailsDialog(m_Activity, media);
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
		// check state
		this.verifyAccess();
		
		Log.v(TAG, "startCamera() - Media type : ", mediaType);
		
		// get context
		Context context = m_Activity;
		if(context == null)
			context = GalleryApplication.current();
		
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
		if(!(context instanceof Activity))
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		
		// start OnePlus Camera
		intent.setComponent(new ComponentName("com.oneplus.camera", "com.oneplus.camera.OPCameraActivity"));
		try
		{
			context.startActivity(intent);
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
			context.startActivity(intent);
			return true;
		}
		catch(ActivityNotFoundException ex)
		{
			Log.w(TAG, "startCamera() - Fail to start camera", ex);
			return false;
		}
	}
	
	
	// Get readable string.
	@Override
	public String toString()
	{
		return ("Gallery(" + m_Id + ")");
	}
	
	
	// Start tracking instance.
	private static void trackInstance(Gallery instance)
	{
		m_TrackingInstances.add(new WeakReference<>(instance));
		checkInstances(0);
	}
}
