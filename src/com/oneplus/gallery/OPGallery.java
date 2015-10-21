package com.oneplus.gallery;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ProgressBar;

import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.gallery.media.Media;
import com.oneplus.gallery.media.MediaManager;
import com.oneplus.gallery.media.MediaSet;
import com.oneplus.gallery.media.MediaSet.Type;
import com.oneplus.gallery.media.MediaType;

final class OPGallery extends Gallery
{
	// Call-backs.
	private final GalleryActivity.ActivityResultCallback m_MediaShareResultCallback = new GalleryActivity.ActivityResultCallback()
	{
		@Override
		public void onActivityResult(Handle handle, int result, Intent data)
		{
			setReadOnly(PROP_IS_SHARING_MEDIA, false);
		}
	};
	
	
	// Delete media.
	@Override
	public boolean deleteMedia(final MediaSet mediaSet, Collection<Media> mediaToDelete, final MediaDeletionCallback callback)
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
		boolean deleteOriginal = (mediaSet == null || mediaSet.getType() != Type.USER);
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
				mediaList.add(media);
			}
		}
		if(mediaList.isEmpty())
		{
			Log.w(TAG, "deleteMedia() - No media to delete");
			return false;
		}
		
		// check activity
		Activity activity = this.get(PROP_ACTIVITY);
		if(activity == null)
		{
			Log.w(TAG, "deleteMedia() - No activity to show dialog");
			this.deleteMedia(mediaSet, mediaList, mediaType, callback);
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
					deleteMedia(mediaSet, mediaList, finalMediaType, callback);
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
	
	
	// Delete media directly.
	private void deleteMedia(MediaSet mediaSet, final List<Media> mediaList, MediaType mediaType, final MediaDeletionCallback callback)
	{
		// check state
		if(this.get(PROP_IS_DELETING_MEDIA))
		{
			Log.e(TAG, "deleteMedia() - Deleting media");
			return;
		}
		
		// get context
		Activity activity = this.get(PROP_ACTIVITY);
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
		com.oneplus.gallery.media.MediaDeletionCallback deletionCallback = new com.oneplus.gallery.media.MediaDeletionCallback()
		{
			@Override
			public void onDeletionStarted(Media media)
			{
				if(callback != null)
					callback.onDeletionStarted(media);
			}
			
			@Override
			public void onDeletionCompleted(Media media, boolean success)
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
		MediaManager mediaManager = GalleryApplication.current().findComponent(MediaManager.class);
		Handler handler = this.getHandler();
		this.setReadOnly(PROP_IS_DELETING_MEDIA, true);
		if(callback != null)
			callback.onDeletionProcessStarted();
		for(int i = mediaList.size() - 1 ; i >= 0 ; --i)
		{
			Media media = mediaList.get(i);
			Handle handle;
			if(mediaSet != null)
				handle = mediaSet.deleteMedia(media, deletionCallback, handler, 0);
			else
				handle = mediaManager.deleteMedia(media, deletionCallback, handler);
			if(!Handle.isValid(handle) && callback != null)
			{
				callback.onDeletionStarted(media);
				callback.onDeletionCompleted(media, false);
			}
		}
	}

	
	// Delete media set.
	@Override
	public boolean deleteMediaSet(Collection<MediaSet> mediaSetToDelete, final MediaSetDeletionCallback callback)
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
		Activity activity = this.get(PROP_ACTIVITY);
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
		Activity activity = this.get(PROP_ACTIVITY);
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

	
	// Share media.
	@Override
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
		GalleryActivity activity = this.get(PROP_ACTIVITY);
		if(activity == null)
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
			activity.startActivityForResult(Intent.createChooser(intent, activity.getString(R.string.gallery_share_media)), m_MediaShareResultCallback);
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
}
