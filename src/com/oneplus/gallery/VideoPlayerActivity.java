package com.oneplus.gallery;

import java.util.Locale;
import java.util.Map;

import com.oneplus.base.Handle;
import com.oneplus.base.HandlerUtils;
import com.oneplus.base.Log;
import com.oneplus.base.PropertyChangeEventArgs;
import com.oneplus.base.PropertyChangedCallback;
import com.oneplus.base.PropertyKey;
import com.oneplus.base.PropertySource;
import com.oneplus.base.ScreenSize;
import com.oneplus.gallery.media.Media;
import com.oneplus.gallery.media.OPMediaManager;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

public class VideoPlayerActivity extends GalleryActivity
{
	// Constants
	private static final long DURATION_ANIMATION = 150;
	private static final long DELAY_HIDE_CONTROLS_UI_TIME_MILLIS = 3000;
	private static final int INTERVAL_UPDATE_ELAPSED_TIME_MILLIS = 1000;
	private static final int MSG_UPDATE_ELAPSED_TIME = 10001;
	private static final int MSG_HIDE_CONTROLS_UI = 10002;
	private static final String STATE_IS_CONTROLS_VISIBLE = "isControlsVisible";
	private static final String STATE_IS_VIDEO_PLAYING = "isVideoPlaying";
	private static final String STATE_VIDEO_ELAPSED_TIME_MILLIS = "videoElapsedTimeMillis";
	
	
	// Fields
	private ImageButton m_BackButton;
	private ImageButton m_CollectButton;
	private ViewVisibilityState m_ControlsVisibilityState = ViewVisibilityState.INVISIBLE;
	private boolean m_DefaultControlsVisible = false;
	private int m_DefaultVideoElapsedTimeMillis;
	private boolean m_DefaultVideoPlaying = true;
	private ImageButton m_DeleteButton;
	private ImageButton m_DetailsButton;
	private View m_FooterContainer;
	private GestureDetector m_GestureDetector;
	private View m_HeaderContainer;
	private boolean m_IsControlsVisible;
	private boolean m_IsPauseBySeekBar;
	private boolean m_IsVideoPlaying;
	private Media m_Media;
	private View m_MediaControlContainer;
	private TextView m_MediaControlDurationTextView;
	private TextView m_MediaControlElapsedTextView;
	private SeekBar m_MediaControlSeekBar;
	private int m_MediaDefaultMarginBottom;
	private ImageButton m_PlayButton;
	private ImageButton m_ShareButton;
	private View m_TouchReceiver;
	private int m_VideoDurationTimeMillis;
	private int m_VideoElapsedTimeMillis;
	private Uri m_VideoUri;
	private VideoView m_VideoView;
	
	
	// View visibility state
	private enum ViewVisibilityState
	{
		IN_ANIMATING,
		VISIBLE,
		OUT_ANIMATING,
		INVISIBLE
	}
	
	
	// Cancel hide controls UI
	private void cancelHideControlsUI()
	{
		Log.v(TAG, "cancelHideControlsUI()");
		HandlerUtils.removeMessages(this, MSG_HIDE_CONTROLS_UI);
	}
	
	
	// Collect media
	private void collectMedia()
	{
		// reset delay hide tool bar
		this.hideControlsUIDelay();
		
		// check media
		if(m_Media == null)
			return;

		// TODO: collect media
		
	}
	
	
	// Delete media
	private void deleteMedia()
	{
		// reset delay hide tool bar
		this.hideControlsUIDelay();
		
		// check media
		if(m_Media == null)
			return;
		
		// pause
		this.pause();

		// delete media
		this.getGallery().deleteMedia(null, m_Media);
	}
	
	
	// Get video duration string
	private String getVideoDurationText(long seconds)
	{
		long hours = (seconds / 3600);
		seconds -= (hours * 3600);
		long minutes = (seconds / 60);
		seconds -= (minutes * 60);
		if(hours > 0)
			return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
		else
			return String.format(Locale.US, "%02d:%02d", minutes, seconds);
	}
	
	
	// Handle messages
	@Override
	protected void handleMessage(Message msg)
	{
		switch(msg.what)
		{
			case MSG_UPDATE_ELAPSED_TIME:
				this.updateElapsedTime((Boolean)msg.obj);
				break;
				
			case MSG_HIDE_CONTROLS_UI:
				this.setControlsVisibility(false, true);
				break;
		
			default:
				super.handleMessage(msg);
				break;
		}
	}
	
	
	// Hide controls UI delay
	private void hideControlsUIDelay()
	{
		Gallery gallery = this.getGallery();
		if(m_IsControlsVisible && !gallery.get(Gallery.PROP_HAS_DIALOG) && !gallery.get(Gallery.PROP_IS_SHARING_MEDIA))
		{
			Log.v(TAG, "hideControlsUIDelay()");
			HandlerUtils.sendMessage(this, MSG_HIDE_CONTROLS_UI, true, DELAY_HIDE_CONTROLS_UI_TIME_MILLIS);
		}
	}

	
	// Call when onCreate
	@Override
	protected void onCreate(Bundle savedInstanceState, Map<String, Object> extras)
	{
		// call super
		super.onCreate(savedInstanceState, extras);
		
		// set content view
		View mainContainer = View.inflate(this, R.layout.activity_video_player, null);
		this.setContentView(mainContainer);
		
		// get data and type
		Intent intent = this.getIntent();
		m_VideoUri = intent.getData();
		
		// get last saved states
		if(extras != null)
		{
			m_DefaultControlsVisible = (Boolean)extras.get(STATE_IS_CONTROLS_VISIBLE);
			m_DefaultVideoPlaying = (Boolean)extras.get(STATE_IS_VIDEO_PLAYING);
			m_DefaultVideoElapsedTimeMillis = (Integer)extras.get(STATE_VIDEO_ELAPSED_TIME_MILLIS);
			
			Log.v(TAG, "onCreate() - Elapsed time millis: ", m_DefaultVideoElapsedTimeMillis);
		}
		
		Log.v(TAG, "onCreate() - Uri: ", m_VideoUri);
		
		// create media
		OPMediaManager mediaManager = GalleryApplication.current().findComponent(OPMediaManager.class);
		mediaManager.createTemporaryMedia(m_VideoUri, new OPMediaManager.MediaCreationCallback()
		{	
			@Override
			public void onCreationCompleted(Handle handle, Uri contentUri, Media media)
			{
				VideoPlayerActivity.this.onMediaCreated(media);
			}
		});
		
		// add has dialog callback
		Gallery gallery = this.getGallery();
		gallery.addCallback(Gallery.PROP_HAS_DIALOG, new PropertyChangedCallback<Boolean>()
		{
			@Override
			public void onPropertyChanged(PropertySource source, PropertyKey<Boolean> key, PropertyChangeEventArgs<Boolean> e)
			{
				if(e.getNewValue())
					VideoPlayerActivity.this.cancelHideControlsUI();
				else
					VideoPlayerActivity.this.hideControlsUIDelay();
			}
		});

		// add sharing callback
		gallery.addCallback(Gallery.PROP_IS_SHARING_MEDIA, new PropertyChangedCallback<Boolean>()
		{
			@Override
			public void onPropertyChanged(PropertySource source, PropertyKey<Boolean> key, PropertyChangeEventArgs<Boolean> e) 
			{
				if(e.getNewValue())
					VideoPlayerActivity.this.cancelHideControlsUI();
				else
					VideoPlayerActivity.this.hideControlsUIDelay();
			}
		});
		
		// setup gesture
		m_GestureDetector = new GestureDetector(this, new GestureDetector.OnGestureListener()
		{	
			@Override
			public boolean onSingleTapUp(MotionEvent e)
			{
				return VideoPlayerActivity.this.onVideoViewSingleTapUp(e);
			}
			
			@Override
			public void onShowPress(MotionEvent e){}	
			
			@Override
			public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) { return false; }
			
			@Override
			public void onLongPress(MotionEvent e) {}
			
			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) { return false;}
			
			@Override
			public boolean onDown(MotionEvent e)
			{
				return true;
			}
		});
		
		// setup video view
		m_VideoView = (VideoView)mainContainer.findViewById(R.id.video_player_video_view);
		m_VideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener()
		{	
			@Override
			public void onPrepared(MediaPlayer mp)
			{
				VideoPlayerActivity.this.onVideoPrepared(mp);
			}
		});
		m_VideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener()
		{	
			@Override
			public void onCompletion(MediaPlayer mp) 
			{
				VideoPlayerActivity.this.onVideoCompletion(mp);
			}
		});
		m_VideoView.setVideoURI(m_VideoUri);
		
		// setup touch receiver
		m_TouchReceiver = mainContainer.findViewById(R.id.video_player_touch_receiver);
		m_TouchReceiver.setOnTouchListener(new View.OnTouchListener()
		{	
			@Override
			public boolean onTouch(View v, MotionEvent event) 
			{
				// delay to hide tool bar if no interaction
				switch(event.getAction())
				{
					case MotionEvent.ACTION_DOWN:
						VideoPlayerActivity.this.cancelHideControlsUI();
						break;
					
					case MotionEvent.ACTION_CANCEL:
					case MotionEvent.ACTION_UP:
						VideoPlayerActivity.this.hideControlsUIDelay();
						break;
				}
				return m_GestureDetector.onTouchEvent(event);
			}
		});
		
		// setup play button
		m_PlayButton = (ImageButton)mainContainer.findViewById(R.id.video_player_play_button);
		m_PlayButton.setOnClickListener(new View.OnClickListener()
		{	
			@Override
			public void onClick(View v)
			{
				VideoPlayerActivity.this.onPlayButtonClick();
			}
		});
		
		// setup media control
		m_MediaControlContainer = mainContainer.findViewById(R.id.video_player_media_control_container);
		m_MediaControlDurationTextView = (TextView)m_MediaControlContainer.findViewById(R.id.video_player_media_control_duration);
		m_MediaControlElapsedTextView = (TextView)m_MediaControlContainer.findViewById(R.id.video_player_media_control_elapsed);
		m_MediaControlSeekBar = (SeekBar)m_MediaControlContainer.findViewById(R.id.video_player_media_control_seekbar);
		m_MediaControlSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
		{
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
			{
				VideoPlayerActivity.this.onSeekBarProgressChanged(seekBar, progress, fromUser);
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar)
			{
				VideoPlayerActivity.this.onSeekBarStartTracking(seekBar);
			}
			
			@Override
			public void onStopTrackingTouch(SeekBar seekBar)
			{
				VideoPlayerActivity.this.onSeekBarStopTracking(seekBar);
			}
		});
		m_MediaDefaultMarginBottom = this.getResources().getDimensionPixelSize(R.dimen.video_player_media_control_container_margin_bottom);
		
		// setup header
		m_HeaderContainer = mainContainer.findViewById(R.id.video_player_header_container);
		m_BackButton = (ImageButton)mainContainer.findViewById(R.id.video_player_header_button_back);
		m_BackButton.setOnClickListener(new View.OnClickListener()
		{	
			@Override
			public void onClick(View v)
			{
				// close activity
				finish();
			}
		});
		m_DetailsButton = (ImageButton)mainContainer.findViewById(R.id.video_player_header_button_details);
		m_DetailsButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v) 
			{
				VideoPlayerActivity.this.showDetails();
			}
		});

		// set header margin top
		ScreenSize screenSize = this.get(GalleryActivity.PROP_SCREEN_SIZE);
		RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)m_HeaderContainer.getLayoutParams();
		params.setMargins(0, screenSize.getStatusBarSize(), 0, 0);
		
		// setup footer
		m_FooterContainer = mainContainer.findViewById(R.id.video_player_footer_container);
		m_ShareButton = (ImageButton)mainContainer.findViewById(R.id.video_player_footer_button_share);
		m_ShareButton.setOnClickListener(new View.OnClickListener()
		{	
			@Override
			public void onClick(View v)
			{
				VideoPlayerActivity.this.shareMedia();
			}
		});
		m_CollectButton = (ImageButton)mainContainer.findViewById(R.id.video_player_footer_button_collect);
		m_CollectButton.setOnClickListener(new View.OnClickListener()
		{	
			@Override
			public void onClick(View v)
			{
				m_CollectButton.setSelected(!m_CollectButton.isSelected());
				VideoPlayerActivity.this.collectMedia();
			}
		});
		m_DeleteButton = (ImageButton)mainContainer.findViewById(R.id.video_player_footer_button_delete);
		m_DeleteButton.setOnClickListener(new View.OnClickListener()
		{	
			@Override
			public void onClick(View v)
			{
				VideoPlayerActivity.this.deleteMedia();
			}
		});
		
		// hide status bar by default
		this.setSystemUiVisibility(m_DefaultControlsVisible);
		this.setControlsVisibility(m_DefaultControlsVisible, true);
		this.updateControlsMargins(m_DefaultControlsVisible);
	}
	
	
	// Call when onDestroy
	@Override
	protected void onDestroy()
	{
		Log.v(TAG, "onDestroy()");
		
		// call super
		super.onDestroy();
	}
	
	
	// Call when media created
	private void onMediaCreated(Media media)
	{
		Log.v(TAG, "onMediaCreated() - Media: ", media.getContentUri());
		
		// set media
		m_Media = media;
		
		// set collected button
		this.updateCollectButtonSelection();
	}
	
	
	// Call when navigation bar visibility changed
	@Override
	protected void onNavigationBarVisibilityChanged(boolean visible)
	{
		// update controls margin
		this.updateControlsMargins(visible);
	}
	
	
	// Call when onPause
	@Override
	protected void onPause()
	{
		Log.v(TAG, "onPause()");
		
		// backup default states
		m_DefaultControlsVisible = m_IsControlsVisible;
		m_DefaultVideoElapsedTimeMillis = m_VideoView.getCurrentPosition();
		m_DefaultVideoPlaying = m_IsVideoPlaying;
		
		// pause
		this.pause();
		
		// call super
		super.onPause();
	}
	
	
	// Call when play button click
	private void onPlayButtonClick()
	{
		// toggle playing
		if(m_IsVideoPlaying)
			this.pause();
		else
			this.start();
	}
	
	
	// Call when onResume
	@Override
	protected void onResume()
	{
		// call super
		super.onResume();
		
		Log.v(TAG, "onResume()");
	}
	
	
	// Call when onSaveInstanceState
	@Override
	protected void onSaveInstanceState(Bundle outState, Map<String, Object> extras)
	{
		Log.v(TAG, "onSaveInstanceState()");
		
		// save states
		if(m_VideoView != null)
		{
			extras.put(STATE_IS_CONTROLS_VISIBLE, m_DefaultControlsVisible);
			extras.put(STATE_IS_VIDEO_PLAYING, m_DefaultVideoPlaying);
			extras.put(STATE_VIDEO_ELAPSED_TIME_MILLIS, m_DefaultVideoElapsedTimeMillis);
		}
		
		// call super
		super.onSaveInstanceState(outState, extras);
	}
	
	
	// Call when seek bar progress changed
	private void onSeekBarProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		if(fromUser)
			this.seekToProgress(progress);
	}
	
	
	// Call when seek bar start tracking
	private void onSeekBarStartTracking(SeekBar seekBar)
	{
		// 
		
		// pause
		if(this.pause())
			m_IsPauseBySeekBar = true;
	}
	
	
	// Call when seek bar stop tracking
	private void onSeekBarStopTracking(SeekBar seekBar)
	{
		// start if pause by seek bar
		if(m_IsPauseBySeekBar)
		{
			m_IsPauseBySeekBar = false;
			this.start();
		}
	}
	
	
	// Call when onStop
	@Override
	protected void onStop()
	{
		Log.v(TAG, "onStop()");
		
		// stop
		this.stop();
		
		// reset flags
		m_IsControlsVisible = false;
		m_ControlsVisibilityState = ViewVisibilityState.INVISIBLE;
		
		// cancel hide
		this.cancelHideControlsUI();
		
		// call super
		super.onStop();
	}
	
	
	// Call when video completion
	private void onVideoCompletion(MediaPlayer mp)
	{	
		// complete
		this.finish();
	}
	
	
	// Call when video prepared
	private void onVideoPrepared(MediaPlayer mp)
	{
		Log.v(TAG, "onVideoPrepared()");
		
		// set duration
		this.setVideoDurationTimeMillis(m_VideoView.getDuration());
		
		// update seek bar max
		this.updateSeekBarMax();
		
		// seek to
		if(m_DefaultVideoElapsedTimeMillis != 0)
			this.seekTo(m_DefaultVideoElapsedTimeMillis);
		
		// start
		this.start();
		if(!m_DefaultVideoPlaying)
			this.pause();
	}
	
	
	// Call when video view single tap up
	private boolean onVideoViewSingleTapUp(MotionEvent e)
	{
		// toggle controls visibility
		this.setControlsVisibility(!m_IsControlsVisible, true);
		
		return true;
	}
	
	
	// Pause
	private boolean pause()
	{
		if(m_IsVideoPlaying)
		{
			Log.v(TAG, "pause()");
			if(m_VideoView.isPlaying())
				m_VideoView.pause();
			m_IsVideoPlaying = false;
			this.updatePlayButtonIcon();
			HandlerUtils.removeMessages(this, MSG_UPDATE_ELAPSED_TIME);
			return true;
		}
		return false;
	}
	
	
	// Seek to
	private void seekTo(int timeMillis)
	{
		// update elapsed time
		this.setVideoElapsedTimeMillis(timeMillis);
		
		Log.v(TAG, "seekTo() - Time: ", timeMillis);
		
		// seek
		m_VideoView.seekTo(timeMillis);
	}
	
	
	// Seek to progress
	private void seekToProgress(int progress)
	{
		// calculate time millis
		int timeMillisPerInterval = INTERVAL_UPDATE_ELAPSED_TIME_MILLIS;
		int timeMillis = progress * timeMillisPerInterval;
		
		// update elapsed time
		this.setVideoElapsedTimeMillis(timeMillis);
		
		Log.v(TAG, "seekToProgress() - Progress: ", progress, ", time: ", timeMillis);
		
		// seek
		m_VideoView.seekTo(timeMillis);
	}
	
	
	// Set controls visibility
	private void setControlsVisibility(boolean visible, boolean animation)
	{
		// check visible
		if(m_IsControlsVisible == visible)
			return;
		
		// set flag
		m_IsControlsVisible = visible;
		
		// update visibility
		this.updateControlsVisibility(animation);
	}
	
	
	// Set video duration
	private void setVideoDurationTimeMillis(int timeMillis)
	{
		// check parameter
		if(m_VideoDurationTimeMillis == timeMillis)
			return;

		Log.v(TAG, "setVideoDurationTimeMillis() - Duration: ", timeMillis);

		// set time
		m_VideoDurationTimeMillis = timeMillis;

		// update
		this.updateMediaControlDurationTextView();
	}
	
	
	// Set video elapsed time
	private void setVideoElapsedTimeMillis(int timeMillis)
	{
		// check parameter
		if(m_VideoElapsedTimeMillis == timeMillis)
			return;
		
		Log.v(TAG, "setVideoElapsedTimeMillis() - Elapsed: ", timeMillis);
		
		// set time
		m_VideoElapsedTimeMillis = timeMillis;
		
		// update
		this.updateMediaControlElapsedTextView();
		this.updateSeekBarPosition();
	}
	
	
	// Share media
	private void shareMedia()
	{
		// reset delay hide tool bar
		this.hideControlsUIDelay();
		
		// check media
		if(m_Media == null)
			return;

		// pause
		this.pause();
		
		// share media
		this.getGallery().shareMedia(m_Media);
	}
	
	
	// Show details
	private void showDetails()
	{
		// reset delay hide tool bar
		this.hideControlsUIDelay();
		
		// check media
		if(m_Media == null)
			return;
		
		// pause
		this.pause();
		
		// show details
		new MediaDetailsDialog(this, m_Media).show();
	}
	
	
	// Start playing
	private void start()
	{
		// start
		m_VideoView.start();
		m_IsVideoPlaying = true;
		
		Log.v(TAG, "start() - Position: ", m_VideoView.getCurrentPosition());
		
		// update play icon
		this.updatePlayButtonIcon();
		
		// update elapsed time
		HandlerUtils.sendMessage(this, MSG_UPDATE_ELAPSED_TIME, 0, 0, true, INTERVAL_UPDATE_ELAPSED_TIME_MILLIS);
	}
	
	
	// Stop playing
	private void stop()
	{
		Log.v(TAG, "stop()");
		
		// stop playback
		m_VideoView.stopPlayback();
		m_IsVideoPlaying = false;
		this.updatePlayButtonIcon();
		HandlerUtils.removeMessages(this, MSG_UPDATE_ELAPSED_TIME);
	}
	
	
	// Update collect button selection
	private void updateCollectButtonSelection()
	{
		// check media
		if(m_Media == null)
			return;
		
		// update selection
		m_CollectButton.setSelected(m_Media.isFavorite());
	}
	
	
	// Update controls margin
	private void updateControlsMargins(boolean visible)
	{
		// check state
		if(m_HeaderContainer == null || m_FooterContainer == null || m_MediaControlContainer == null)
			return;
		
		// set margin
		ScreenSize screenSize = this.get(GalleryActivity.PROP_SCREEN_SIZE);
		RelativeLayout.LayoutParams headerParams = (RelativeLayout.LayoutParams)m_HeaderContainer.getLayoutParams();
		RelativeLayout.LayoutParams footerParams = (RelativeLayout.LayoutParams)m_FooterContainer.getLayoutParams();
		RelativeLayout.LayoutParams mediaParams = (RelativeLayout.LayoutParams)m_MediaControlContainer.getLayoutParams();
		if(visible)
		{
			int naviHeight = screenSize.getNavigationBarSize();
			if(screenSize.getWidth() > screenSize.getHeight())
			{
				headerParams.setMargins(headerParams.leftMargin, headerParams.topMargin, naviHeight, 0);
				footerParams.setMargins(footerParams.leftMargin, footerParams.topMargin, naviHeight, 0);
				mediaParams.setMargins(mediaParams.leftMargin, mediaParams.topMargin, naviHeight, m_MediaDefaultMarginBottom);
			}
			else
			{
				headerParams.setMargins(headerParams.leftMargin, headerParams.topMargin, 0, 0);
				footerParams.setMargins(footerParams.leftMargin, footerParams.topMargin, 0, naviHeight);
				mediaParams.setMargins(mediaParams.leftMargin, mediaParams.topMargin, 0, m_MediaDefaultMarginBottom + naviHeight);
			}
		}
		else
		{
			headerParams.setMargins(headerParams.leftMargin, headerParams.topMargin, 0, headerParams.bottomMargin);
			footerParams.setMargins(footerParams.leftMargin, footerParams.topMargin, 0, 0);
			mediaParams.setMargins(mediaParams.leftMargin, mediaParams.topMargin, 0, m_MediaDefaultMarginBottom);
		}
	}
	
	
	// Update controls visibility
	private void updateControlsVisibility(boolean animation)
	{
		Log.v(TAG, "updateControlsVisibility() - Visible: ", m_IsControlsVisible);

		// set status bar visibility
		this.setSystemUiVisibility(m_IsControlsVisible);
		
		// show/hide controls
		if(m_IsControlsVisible)
		{
			if(animation)
			{
				switch(m_ControlsVisibilityState)
				{
					case INVISIBLE:
						// set play button init state
						m_PlayButton.setVisibility(View.VISIBLE);
						m_PlayButton.setAlpha(0f);
						
						// set media control init state
						m_MediaControlContainer.setVisibility(View.VISIBLE);
						m_MediaControlContainer.setAlpha(0f);
						
						// set header init state
						m_HeaderContainer.setVisibility(View.VISIBLE);
						m_HeaderContainer.setAlpha(0f);
	
						// set footer init state
						m_FooterContainer.setVisibility(View.VISIBLE);
						m_FooterContainer.setAlpha(0f);
						break;
	
					case OUT_ANIMATING:
						// cancel animation
						m_PlayButton.animate().cancel();
						m_MediaControlContainer.animate().cancel();
						m_HeaderContainer.animate().cancel();
						m_FooterContainer.animate().cancel();
						break;
	
					case IN_ANIMATING:
					case VISIBLE:
						return;
				}

				// play button animation
				m_PlayButton.animate().alpha(1f).setDuration(DURATION_ANIMATION).withEndAction(new Runnable()
				{	
					@Override
					public void run()
					{
						m_ControlsVisibilityState = ViewVisibilityState.VISIBLE;
						VideoPlayerActivity.this.hideControlsUIDelay();
					}
				}).start();
				
				// media controls animation
				m_MediaControlContainer.animate().alpha(1f).setDuration(DURATION_ANIMATION).withEndAction(new Runnable()
				{	
					@Override
					public void run()
					{
						m_ControlsVisibilityState = ViewVisibilityState.VISIBLE;
						VideoPlayerActivity.this.hideControlsUIDelay();
					}
				}).start();
				
				// header animation
				m_HeaderContainer.animate().alpha(1f).setDuration(DURATION_ANIMATION).withEndAction(new Runnable()
				{	
					@Override
					public void run()
					{
						m_ControlsVisibilityState = ViewVisibilityState.VISIBLE;
						VideoPlayerActivity.this.hideControlsUIDelay();
					}
				}).start();

				// footer animation
				m_FooterContainer.animate().alpha(1f).setDuration(DURATION_ANIMATION).withEndAction(new Runnable()
				{	
					@Override
					public void run()
					{
						m_ControlsVisibilityState = ViewVisibilityState.VISIBLE;
						VideoPlayerActivity.this.hideControlsUIDelay();
					}
				}).start();

				// change visibility state
				m_ControlsVisibilityState = ViewVisibilityState.IN_ANIMATING;
			}
			else
			{
				// play button
				m_PlayButton.setVisibility(View.VISIBLE);
				m_PlayButton.setAlpha(1f);
				
				// media control
				m_MediaControlContainer.setVisibility(View.VISIBLE);
				m_MediaControlContainer.setAlpha(1f);
				
				// header
				m_HeaderContainer.setVisibility(View.VISIBLE);
				m_HeaderContainer.setAlpha(1f);

				// footer
				m_FooterContainer.setVisibility(View.VISIBLE);
				m_FooterContainer.setAlpha(1f);

				// change visibility state
				m_ControlsVisibilityState = ViewVisibilityState.VISIBLE;
				this.hideControlsUIDelay();
			}
		}
		else
		{
			// remove hide controls
			this.cancelHideControlsUI();
			
			// animation
			if(animation)
			{
				switch(m_ControlsVisibilityState)
				{
					case VISIBLE:
						// set play button init state
						m_PlayButton.setAlpha(1f);
	
						// set media controls init state
						m_MediaControlContainer.setAlpha(1f);
						
						// set header init state
						m_HeaderContainer.setAlpha(1f);
	
						// set footer init state
						m_FooterContainer.setAlpha(1f);
						break;
	
					case IN_ANIMATING:
						// cancel animation
						m_PlayButton.animate().cancel();
						m_MediaControlContainer.animate().cancel();
						m_HeaderContainer.animate().cancel();
						m_FooterContainer.animate().cancel();
						break;
	
					case OUT_ANIMATING:
					case INVISIBLE:
						return;
				}

				// play button animation
				m_PlayButton.animate().alpha(0f).setDuration(DURATION_ANIMATION).withEndAction(new Runnable()
				{	
					@Override
					public void run()
					{
						m_PlayButton.setVisibility(View.GONE);
						m_ControlsVisibilityState = ViewVisibilityState.INVISIBLE;
					}
				}).start();

				// media controls animation
				m_MediaControlContainer.animate().alpha(0f).setDuration(DURATION_ANIMATION).withEndAction(new Runnable()
				{	
					@Override
					public void run()
					{
						m_MediaControlContainer.setVisibility(View.GONE);
						m_ControlsVisibilityState = ViewVisibilityState.INVISIBLE;
					}
				}).start();
				
				// header animation
				m_HeaderContainer.animate().alpha(0f).setDuration(DURATION_ANIMATION).withEndAction(new Runnable()
				{	
					@Override
					public void run()
					{
						m_HeaderContainer.setVisibility(View.GONE);
						m_ControlsVisibilityState = ViewVisibilityState.INVISIBLE;
					}
				}).start();

				// footer animation
				m_FooterContainer.animate().alpha(0f).setDuration(DURATION_ANIMATION).withEndAction(new Runnable()
				{	
					@Override
					public void run()
					{
						m_FooterContainer.setVisibility(View.GONE);
						m_ControlsVisibilityState = ViewVisibilityState.INVISIBLE;
					}
				}).start();

				// change visibility state
				m_ControlsVisibilityState = ViewVisibilityState.OUT_ANIMATING;
			}
			else
			{
				// set to gone
				m_PlayButton.setVisibility(View.GONE);
				m_MediaControlContainer.setVisibility(View.GONE);
				m_HeaderContainer.setVisibility(View.GONE);
				m_FooterContainer.setVisibility(View.GONE);

				// change visibility state
				m_ControlsVisibilityState = ViewVisibilityState.INVISIBLE;
			}
		}
	}
	
	
	// Update elapsed time
	private void updateElapsedTime(boolean isLoop)
	{
		// set elapsed time
		int elapsed = m_VideoView.getCurrentPosition();
		this.setVideoElapsedTimeMillis(elapsed);
		if(isLoop)
			HandlerUtils.sendMessage(this, MSG_UPDATE_ELAPSED_TIME, 0, 0, true, INTERVAL_UPDATE_ELAPSED_TIME_MILLIS);
	}
	
	
	// Update elapsed text view
	private void updateMediaControlElapsedTextView()
	{
		String text = this.getVideoDurationText((m_VideoElapsedTimeMillis + 500)/1000);
		m_MediaControlElapsedTextView.setText(text); 
	}
	
	
	// Update duration text view
	private void updateMediaControlDurationTextView()
	{
		String text = this.getVideoDurationText((m_VideoDurationTimeMillis + 500)/1000);
		m_MediaControlDurationTextView.setText(text); 
	}
	
	
	// Update play button icon
	private void updatePlayButtonIcon()
	{
		// set play button icon
		if(m_IsVideoPlaying)
			m_PlayButton.setImageDrawable(this.getDrawable(R.drawable.review_video_pause));
		else
			m_PlayButton.setImageDrawable(this.getDrawable(R.drawable.review_video_play));
	}
	
	
	// Update seek bar max
	private void updateSeekBarMax()
	{
		int durationSecs = (m_VideoDurationTimeMillis + 500) / 1000;
		int intervalPerSecond = 1000 / INTERVAL_UPDATE_ELAPSED_TIME_MILLIS;
		m_MediaControlSeekBar.setMax(durationSecs * intervalPerSecond);
	}
	
	
	// Update seek bar position
	private void updateSeekBarPosition()
	{
		int timeMillisPerInterval = INTERVAL_UPDATE_ELAPSED_TIME_MILLIS;
		int progress = (m_VideoElapsedTimeMillis + 500) / timeMillisPerInterval;
		m_MediaControlSeekBar.setProgress(progress);
	}

}
