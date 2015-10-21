package com.oneplus.gallery.media;

import android.media.ExifInterface;
import android.util.Rational;

/**
 * Detailed photo information.
 */
public interface PhotoMediaDetails extends MediaDetails
{
	/**
	 * Key for aperture (F-Number).
	 */
	Key<Double> KEY_APERTURE = new Key<>("Photo.Aperture");
	/**
	 * Key for camera manufacturer.
	 */
	Key<String> KEY_CAMERA_MANUFACTURER = new Key<>("Photo.CameraManufacturer");
	/**
	 * Key for camera model.
	 */
	Key<String> KEY_CAMERA_MODEL = new Key<>("Photo.CameraModel");
	/**
	 * Key for focal length in millimeters.
	 */
	Key<Double> KEY_FOCAL_LENGTH = new Key<>("Photo.FocalLength");
	/**
	 * Key for flash state.
	 */
	Key<Boolean> KEY_IS_FLASH_FIRED = new Key<>("Photo.IsFlashFired");
	/**
	 * Key for ISO speed.
	 */
	Key<Integer> KEY_ISO_SPEED = new Key<>("Photo.IsoSpeed");
	/**
	 * Key for shutter speed in seconds.
	 */
	Key<Rational> KEY_SHUTTER_SPEED = new Key<>("Photo.ShutterSpeed");
	/**
	 * Key for white balance, value can be:
	 * <ul>
	 *   <li>{@link #WHITE_BALANCE_AUTO}</li>
	 *   <li>{@link #WHITE_BALANCE_MANUAL}</li>
	 * </ul>
	 */
	Key<Integer> KEY_WHITE_BALANCE = new Key<>("Photo.WhiteBalance");
	
	
	/**
	 * Auto white balance.
	 */
	int WHITE_BALANCE_AUTO = ExifInterface.WHITEBALANCE_AUTO;
	/**
	 * Manual white balance.
	 */
	int WHITE_BALANCE_MANUAL = ExifInterface.WHITEBALANCE_MANUAL;
}
