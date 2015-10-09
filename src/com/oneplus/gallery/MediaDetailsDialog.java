package com.oneplus.gallery;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import com.oneplus.base.Handle;
import com.oneplus.base.Log;
import com.oneplus.gallery.media.Media;
import com.oneplus.gallery.media.MediaDetails;
import com.oneplus.gallery.media.PhotoMedia;
import com.oneplus.gallery.media.PhotoMediaDetails;
import com.oneplus.gallery.media.VideoMedia;
import com.oneplus.io.FileUtils;
import com.oneplus.io.Path;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.location.Location;
import android.os.Bundle;
import android.util.Rational;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Dialog to show detailed media information.
 */
public class MediaDetailsDialog
{
	// Constants.
	private static final String TAG = "MediaDetailsDialog";
	private static final long MAX_MEDIA_DETAILS_WAITING_TIME = 500;
	
	
	// Fields.
	private Activity m_Activity;
	private AlertDialog m_Dialog;
	private final GalleryDialogFragment m_DialogFragment = new GalleryDialogFragment()
	{
		public Dialog onCreateDialog(Bundle savedInstanceState) 
		{
			m_Activity = this.getActivity();
			MediaDetailsDialog.this.onPrepareDialog(m_Activity);
			return m_Dialog;
		}
		
		public void onDismiss(DialogInterface dialog) 
		{
			if(dialog == null)
				return;
			if(m_Dialog == dialog)
			{
				m_MediaDetailsRetrievingHandle = Handle.close(m_MediaDetailsRetrievingHandle);
				m_Dialog = null;
			}
		}
	};
	private final String m_ItemStringFormat;
	private final Media m_Media;
	private Handle m_MediaDetailsRetrievingHandle;
	
	
	/**
	 * Initialize new MediaDetailsDialog instance.
	 * @param activity Owner activity.
	 * @param media Media to show details.
	 */
	public MediaDetailsDialog(Activity activity, Media media)
	{
		if(activity == null)
			throw new IllegalArgumentException("No activity.");
		if(media == null)
			throw new IllegalArgumentException("No media.");
		m_Activity = activity;
		m_Media = media;
		m_ItemStringFormat = activity.getString(R.string.media_details_item_format);
	}
	
	
	// Create view for date-time item.
	private void createDateTimeItem(ViewGroup itemContainer, int titleResId, long time)
	{
		Date date = new Date(time);
		DateFormat format = DateFormat.getDateTimeInstance();
		this.createStringItem(itemContainer, titleResId, format.format(date));
	}
	
	
	// Create view for double item.
	private void createDoubleItem(ViewGroup itemContainer, int titleResId, double value, String format)
	{
		this.createStringItem(itemContainer, titleResId, String.format(Locale.US, format, value));
	}
	
	
	// Create view for integer item.
	private void createIntItem(ViewGroup itemContainer, int titleResId, int value)
	{
		this.createStringItem(itemContainer, titleResId, Integer.toString(value));
	}
	
	
	// Create view for string item.
	private void createStringItem(ViewGroup itemContainer, int titleResId, String value)
	{
		View.inflate(m_Dialog.getContext(), R.layout.layout_media_details_dialog_item, itemContainer);
		TextView tv = (TextView)itemContainer.getChildAt(itemContainer.getChildCount() - 1);
		tv.setText(String.format(m_ItemStringFormat, m_Activity.getString(titleResId), value));
	}
	private void createStringItem(ViewGroup itemContainer, int titleResId, Object value)
	{
		if(value != null)
			this.createStringItem(itemContainer, titleResId, value.toString());
	}
	
	
	// Called when media details retrieved.
	private void onMediaDetailsRetrieved(MediaDetails details)
	{
		// check state
		if(m_Dialog == null)
			return;
		
		// show dialog
		m_Dialog.show();
		
		// hide "processing"
		View view = m_Dialog.findViewById(R.id.media_details_processing_container);
		if(view != null)
			view.setVisibility(View.GONE);
		
		// show details
		ViewGroup itemContainer = (ViewGroup)m_Dialog.findViewById(R.id.media_details_container);
		if(itemContainer != null)
		{
			switch(m_Media.getType())
			{
				case PHOTO:
					this.showPhotoDetails(itemContainer, details);
					break;
				case VIDEO:
					this.showVideoDetails(itemContainer, details);
					break;
				default:
					Log.e(TAG, "onMediaDetailsRetrieved() - Unknown media type : " + m_Media.getType());
					m_Dialog.dismiss();
					break;
			}
		}
	}
	
	
	// Called when taking too long time to retrieve media details.
	private void onMediaDetailsTimeout()
	{
		// check state
		if(m_Dialog == null || m_Dialog.isShowing())
			return;
		
		Log.w(TAG, "onMediaDetailsTimeout()");
		
		// show dialog
		m_Dialog.show();
		
		// show "processing"
		View view = m_Dialog.findViewById(R.id.media_details_processing_container);
		if(view != null)
			view.setVisibility(View.VISIBLE);
	}
	
	
	// Called when preparing dialog.
	private void onPrepareDialog(Context context)
	{
		// start getting media details
		final Object[] result = new Object[]{ false, null };
		m_MediaDetailsRetrievingHandle = m_Media.getDetails(new Media.MediaDetailsCallback()
		{
			@Override
			public void onMediaDetailsRetrieved(Media media, final Handle handle, final MediaDetails details)
			{
				synchronized(result)
				{
					if(!(Boolean)result[0])
					{
						result[0] = true;
						result[1] = details;
						result.notifyAll();
					}
					else
					{
						GalleryApplication.current().getHandler().post(new Runnable()
						{
							@Override
							public void run()
							{
								if(m_MediaDetailsRetrievingHandle == handle)
									MediaDetailsDialog.this.onMediaDetailsRetrieved(details);
							}
						});
					}
				}
			}
		}, null);
		
		// create dialog builder
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(R.string.media_details);
		builder.setView(R.layout.dialog_media_details);
		builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				m_DialogFragment.dismissAllowingStateLoss();
			}
		});
		
		// create dialog
		m_Dialog = builder.create();
		
		// waiting for media details
		if(m_MediaDetailsRetrievingHandle != null)
		{
			synchronized(result)
			{
				// wait for media details
				try
				{
					result.wait(MAX_MEDIA_DETAILS_WAITING_TIME);
				}
				catch(InterruptedException ex)
				{
					Log.e(TAG, "show() - Interrupted", ex);
				}
				
				// show dialog
				if((Boolean)result[0])
					this.onMediaDetailsRetrieved((MediaDetails)result[1]);
				else
				{
					result[0] = true;
					this.onMediaDetailsTimeout();
				}	
			}
		}
		else
			this.onMediaDetailsRetrieved(null);
	}
	
	
	/**
	 * Show dialog.
	 */
	public void show()
	{
		if(m_Dialog != null)
			return;
		m_DialogFragment.show(m_Activity.getFragmentManager(), "MediaDetailsDialog.DialogFragment");
	}
	
	
	// Show detailed photo information.
	private void showPhotoDetails(ViewGroup itemContainer, MediaDetails details)
	{
		// get file path
		PhotoMedia photoMedia = (m_Media instanceof PhotoMedia ? (PhotoMedia)m_Media : null);
		String filePath = m_Media.getFilePath();
		
		// title
		if(filePath != null)
			this.createStringItem(itemContainer, R.string.media_details_title, Path.getFileNameWithoutExtension(filePath));
		
		// taken time
		long time = m_Media.getTakenTime();
		if(time > 0)
			this.createDateTimeItem(itemContainer, R.string.media_details_time, time);
		
		// location
		Location location = m_Media.getLocation();
		if(location != null)
			this.createStringItem(itemContainer, R.string.media_details_location, String.format(Locale.US, "%.6f, %.6f", location.getLatitude(), location.getLongitude()));
		
		// width
		this.createIntItem(itemContainer, R.string.media_details_width, m_Media.getWidth());
		
		// height
		this.createIntItem(itemContainer, R.string.media_details_height, m_Media.getHeight());
		
		// orientation
		if(photoMedia != null)
			this.createIntItem(itemContainer, R.string.media_details_orientation, photoMedia.getOrientation());
		
		// file size
		long size = m_Media.getFileSize();
		if(size > 0)
			this.createStringItem(itemContainer, R.string.media_details_file_size, FileUtils.getFileSizeDescription(size));
		
		// maker
		String strValue = (details != null ? details.get(PhotoMediaDetails.KEY_CAMERA_MANUFACTURER, null) : null);
		if(strValue != null)
			this.createStringItem(itemContainer, R.string.media_details_maker, strValue);

		// model
		strValue = (details != null ? details.get(PhotoMediaDetails.KEY_CAMERA_MODEL, null) : null);
		if(strValue != null)
			this.createStringItem(itemContainer, R.string.media_details_model, strValue);

		// flash
		Boolean boolValue = (details != null ? details.get(PhotoMediaDetails.KEY_IS_FLASH_FIRED, null) : null);
		if(boolValue != null)
			this.createStringItem(itemContainer, R.string.media_details_flash, (boolValue ? m_Activity.getString(R.string.media_details_flash_on) : m_Activity.getString(R.string.media_details_flash_off)));

		// focal length
		Double doubleValue = (details != null ? details.get(PhotoMediaDetails.KEY_FOCAL_LENGTH, null) : null);
		if(doubleValue != null)
			this.createDoubleItem(itemContainer, R.string.media_details_focal_length, doubleValue, "%.2f mm");

		// WB
		Integer intValue = (details != null ? details.get(PhotoMediaDetails.KEY_WHITE_BALANCE, null) : null);
		if(intValue != null)
			this.createStringItem(itemContainer, R.string.media_details_white_balance, (intValue == PhotoMediaDetails.WHITE_BALANCE_MANUAL ? m_Activity.getString(R.string.media_details_manual) : m_Activity.getString(R.string.media_details_auto)));
		
		// aperture
		doubleValue = (details != null ? details.get(PhotoMediaDetails.KEY_APERTURE, null) : null);
		if(doubleValue != null)
			this.createDoubleItem(itemContainer, R.string.media_details_aperture, doubleValue, "f/%.1f");
		
		// exposure time
		Rational rationalValue = (details != null ? details.get(PhotoMediaDetails.KEY_SHUTTER_SPEED, null) : null);
		if(rationalValue != null)
		{
			if(rationalValue.getNumerator() < rationalValue.getDenominator())
				this.createStringItem(itemContainer, R.string.media_details_exposure_time, rationalValue);
			else
			{
				int seconds = (rationalValue.getNumerator() / rationalValue.getDenominator());
				int restNumerator = (rationalValue.getNumerator() % rationalValue.getDenominator());
				if(restNumerator != 0)
				{
					rationalValue = new Rational(restNumerator, rationalValue.getDenominator());
					this.createStringItem(itemContainer, R.string.media_details_exposure_time, String.format(Locale.US, "%d\"%s", seconds, rationalValue));
				}
				else
					this.createStringItem(itemContainer, R.string.media_details_exposure_time, String.format(Locale.US, "%d\"", seconds));
			}
		}

		// ISO
		intValue = (details != null ? details.get(PhotoMediaDetails.KEY_ISO_SPEED, null) : null);
		if(intValue != null)
			this.createIntItem(itemContainer, R.string.media_details_iso, intValue);
		
		// path
		if(filePath != null)
			this.createStringItem(itemContainer, R.string.media_details_path, filePath);
	}
	
	
	// Show detailed video information.
	private void showVideoDetails(ViewGroup itemContainer, MediaDetails details)
	{
		// get file path
		VideoMedia videoMedia = (m_Media instanceof VideoMedia ? (VideoMedia)m_Media : null);
		String filePath = m_Media.getFilePath();
		
		// title
		if(filePath != null)
			this.createStringItem(itemContainer, R.string.media_details_title, Path.getFileNameWithoutExtension(filePath));
		
		// taken time
		long time = m_Media.getTakenTime();
		if(time > 0)
			this.createDateTimeItem(itemContainer, R.string.media_details_time, time);
		
		// location
		Location location = m_Media.getLocation();
		if(location != null)
			this.createStringItem(itemContainer, R.string.media_details_location, String.format(Locale.US, "%.6f, %.6f", location.getLatitude(), location.getLongitude()));
		
		// width
		this.createIntItem(itemContainer, R.string.media_details_width, m_Media.getWidth());
		
		// height
		this.createIntItem(itemContainer, R.string.media_details_height, m_Media.getHeight());
		
		// duration
		if(videoMedia != null)
		{
			long duration = videoMedia.getDuration();
			if(duration > 0)
			{
				long hours = (duration / 3600000);
				duration %= 3600000;
				long minutes = (duration / 60000);
				duration %= 60000;
				long seconds = (duration / 1000);
				if(hours < 1)
					this.createStringItem(itemContainer, R.string.media_details_duration, String.format(Locale.US, "%02d:%02d", minutes, seconds));
				else
					this.createStringItem(itemContainer, R.string.media_details_duration, String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds));
			}
		}
		
		// path
		if(filePath != null)
			this.createStringItem(itemContainer, R.string.media_details_path, filePath);
	}
}
