package com.oneplus.gallery.media;

import java.util.HashMap;
import java.util.Map;

import com.oneplus.base.Log;
import com.oneplus.database.CursorUtils;
import com.oneplus.gallery.MediaType;
import com.oneplus.gallery.media.MediaDetails.Key;
import com.oneplus.io.Path;

import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Rational;

/**
 * Media store based photo media.
 */
class PhotoMediaStoreMedia extends MediaStoreMedia implements PhotoMedia
{
	// Constants.
	private static final String TAG = "PhotoMediaStoreMedia";
	
	
	// Fields.
	private volatile int m_Orientation;
	
	
	// Class for photo details.
	private static final class PhotoDetails extends SimpleMediaDetails implements PhotoMediaDetails
	{
		public PhotoDetails(Map<Key<?>, Object> values)
		{
			super(values);
		}
	}
	
	
	// Constructor.
	PhotoMediaStoreMedia(Cursor cursor, Handler handler)
	{
		super(getContentUri(cursor), cursor, handler);
	}
	
	
	/**
	 * Get content URI from cursor.
	 * @param cursor Cursor.
	 * @return Photo content URI.
	 */
	public static Uri getContentUri(Cursor cursor)
	{
		int id = CursorUtils.getInt(cursor, MediaColumns._ID, 0);
		if(id > 0)
			return Uri.parse(Images.Media.EXTERNAL_CONTENT_URI + "/" + id);
		return null;
	}
	
	
	// Get details.
	@Override
	protected MediaDetails getDetails() throws Exception
	{
		// check file path
		String filePath = this.getFilePath();
		if(filePath == null)
		{
			Log.w(TAG, "getDetails() - No file path for " + this.getContentUri());
			return null;
		}
		
		// get format
		String mimeType = this.getMimeType();
		if(mimeType != null)
		{
			if(!mimeType.equals("image/jpeg"))
			{
				Log.w(TAG, "getDetails() - Not a JPEG file");
				return null;
			}
		}
		else
		{
			switch(Path.getExtension(filePath).toLowerCase())
			{
				case ".jpg":
				case ".jpeg":
					break;
				default:
					Log.w(TAG, "getDetails() - Not a JPEG file");
					return null;
			}
		}
		
		// prepare
		ExifInterface exif = new ExifInterface(filePath);
		Map<Key<?>, Object> values = new HashMap<>();
		
		// get aperture
		String strValue = exif.getAttribute(ExifInterface.TAG_APERTURE);
		if(strValue != null)
		{
			try
			{
				values.put(PhotoMediaDetails.KEY_APERTURE, this.toDouble(strValue));
			}
			catch(NumberFormatException ex)
			{}
		}
		
		// get camera model
		strValue = exif.getAttribute(ExifInterface.TAG_MAKE);
		if(strValue != null)
			values.put(PhotoMediaDetails.KEY_CAMERA_MANUFACTURER, strValue);
		strValue = exif.getAttribute(ExifInterface.TAG_MODEL);
		if(strValue != null)
			values.put(PhotoMediaDetails.KEY_CAMERA_MODEL, strValue);
		
		// get focal length
		strValue = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH);
		if(strValue != null)
		{
			try
			{
				values.put(PhotoMediaDetails.KEY_FOCAL_LENGTH, this.toDouble(strValue));
			}
			catch(NumberFormatException ex)
			{}
		}
		
		// get flash state
		int intValue = exif.getAttributeInt(ExifInterface.TAG_FLASH, 0);
		values.put(PhotoMediaDetails.KEY_IS_FLASH_FIRED, (intValue & 0x1) != 0);
		
		// get ISO speed
		strValue = exif.getAttribute(ExifInterface.TAG_ISO);
		if(strValue != null)
		{
			try
			{
				values.put(PhotoMediaDetails.KEY_ISO_SPEED, Integer.parseInt(strValue));
			}
			catch(NumberFormatException ex)
			{}
		}
		
		// get shutter speed
		strValue = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME);
		if(strValue != null)
		{
			try
			{
				values.put(PhotoMediaDetails.KEY_SHUTTER_SPEED, this.toRational(strValue, true));
			}
			catch(NumberFormatException ex)
			{}
		}
		
		// get white balance
		values.put(PhotoMediaDetails.KEY_WHITE_BALANCE, exif.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, PhotoMediaDetails.WHITE_BALANCE_AUTO));
		
		// complete
		return new PhotoDetails(values);
	}
	
	
	// Get orientation.
	@Override
	public int getOrientation()
	{
		return m_Orientation;
	}
	
	
	// Get media type.
	@Override
	public MediaType getType()
	{
		return MediaType.PHOTO;
	}
	
	
	// Setup photo size.
	@Override
	protected void setupSize(Cursor cursor, int[] result)
	{
		// call super
		super.setupSize(cursor, result);
		
		// decode size
		if(result[0] <= 0 || result[1] <= 0)
		{
			String filePath = this.getFilePath();
			if(filePath != null)
			{
				try
				{
					BitmapFactory.Options options = new BitmapFactory.Options();
					options.inJustDecodeBounds = true;
					BitmapFactory.decodeFile(filePath, options);
					result[0] = options.outWidth;
					result[1] = options.outHeight;
				}
				catch(Throwable ex)
				{}
			}
		}
		
		// get orientation
		m_Orientation = CursorUtils.getInt(cursor, ImageColumns.ORIENTATION, 0);
		
		// rotate size
		switch(m_Orientation)
		{
			case 90:
			case 270:
			{
				int temp = result[0];
				result[0] = result[1];
				result[1] = temp;
				break;
			}
		}
	}
	
	
	// Convert string to double.
	private Double toDouble(String value) throws NumberFormatException
	{
		if(value == null)
			return null;
		if(value.indexOf('/') < 0)
			return Double.parseDouble(value);
		Rational r = this.toRational(value, false);
		return (r != null ? r.doubleValue() : null);
	}
	
	
	// Convert string to rational.
	private Rational toRational(String value, boolean reduction) throws NumberFormatException
	{
		if(value == null)
			return null;
		int index = value.indexOf('.');
		if(index >= 0)
		{
			double doubleValue = Double.parseDouble(value);
			int d = 1;
			for(int i = value.length() - 1 ; i > index ; --i)
				d *= 10;
			int n = (int)(doubleValue * d + 0.5);
			if(reduction && n > 1 && n < d)
			{
				d /= n;
				n = 1;
			}
			return new Rational(n, d);
		}
		else
			return Rational.parseRational(value);
	}
}
