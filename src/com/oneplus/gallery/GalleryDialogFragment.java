package com.oneplus.gallery;

import android.app.DialogFragment;
import android.content.DialogInterface;

/**
 * Dialog fragment in Gallery.
 */
public abstract class GalleryDialogFragment extends DialogFragment
{
	/**
	 * Initialize new GalleryDialogFragment instance.
	 */
	protected GalleryDialogFragment()
	{
		this.setRetainInstance(true);
	}
	
	
	/**
	 * Get {@link GalleryActivity} which is currently attached to.
	 * @return {@link GalleryActivity} instance.
	 */
	public GalleryActivity getGalleryActivity()
	{
		return (GalleryActivity)this.getActivity();
	}
	
	
	// Called when cancelling dialog.
	@Override
	public void onCancel(DialogInterface dialog)
	{
		this.dismissAllowingStateLoss();
	}
	
	
	// Called when dialog dismissed.
	@Override
	public void onDismiss(DialogInterface dialog)
	{}
}
