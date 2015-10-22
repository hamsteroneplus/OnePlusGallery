package com.oneplus.gallery;

import com.oneplus.base.Handle;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.DialogInterface;

/**
 * Dialog fragment in Gallery.
 */
public abstract class GalleryDialogFragment extends DialogFragment
{
	// Fields
	private Handle m_DialogHandle;
	
	
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
	
	
	// Dismiss dialog
	@Override
	public void dismiss()
	{
		super.dismiss();
		m_DialogHandle = Handle.close(m_DialogHandle);
	}
	
	
	// Dismiss and allow state loss
	@Override
	public void dismissAllowingStateLoss()
	{
		super.dismissAllowingStateLoss();
		m_DialogHandle = Handle.close(m_DialogHandle);
	}
	
	@Override
	public void onAttach(Activity activity) 
	{
		if(!Handle.isValid(m_DialogHandle))
			m_DialogHandle = ((GalleryActivity)activity).getGallery().notifyShowDialog();
		super.onAttach(activity);
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
