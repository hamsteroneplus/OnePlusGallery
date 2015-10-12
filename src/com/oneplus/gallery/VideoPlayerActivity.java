package com.oneplus.gallery;

import java.util.Locale;
import java.util.Map;

import com.oneplus.base.Handle;
import com.oneplus.base.HandlerUtils;
import com.oneplus.base.Log;
import com.oneplus.base.ScreenSize;

import android.content.Intent;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

public class VideoPlayerActivity extends GalleryActivity
{
	// Constants
	private static final long DURATION_ANIMATION = 150;
	private static final int INTERVAL_UPDATE_ELAPSED_TIME_MILLIS = 1000;
	private static final int MSG_UPDATE_ELAPSED_TIME = 10001;
	
	
	// Fields
	private ViewVisibilityState m_ControlsVisibilityState = ViewVisibilityState.INVISIBLE;
	private View m_FooterContainer;
	private GestureDetector m_GestureDetector;
	private View m_HeaderContainer;
	private boolean m_IsControlsVisible;
	private boolean m_IsPauseBySeekBar;
	private boolean m_IsVideoPlaying;
	private View m_MediaControlContainer;
	private TextView m_MediaControlDurationTextView;
	private TextView m_MediaControlElapsedTextView;
	private SeekBar m_MediaControlSeekBar;
	private ImageButton m_PlayButton;
	private Handle m_StatusBarVisibilityHandle;
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
		
			default:
				super.handleMessage(msg);
				break;
		}
	}
	
	
	// Call when onConfigurationChanged
	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		// call super
		super.onConfigurationChanged(newConfig);
		
		//
		Log.v(TAG, "onConfigurationChanged");
	}

	
	// Call when onCreate
	@Override
	protected void onCreate(Bundle savedInstanceState, Map<String, Object> extras)
	{
		// call super
		super.onCreate(savedInstanceState, extras);
		
		// get data and type
		Intent intent = this.getIntent();
		m_VideoUri = intent.getData();
		
		// hide status bar by default
		this.setStatusBarVisibility(false);
		
		Log.v(TAG, "onCreate() - Uri: ", m_VideoUri);
		
		// set content view
		View mainContainer = View.inflate(this, R.layout.activity_video_player, null);
		this.setContentView(mainContainer);
		
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
		m_VideoView.setOnTouchListener(new View.OnTouchListener()
		{	
			@Override
			public boolean onTouch(View v, MotionEvent event) 
			{
				return m_GestureDetector.onTouchEvent(event);
			}
		});
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
		
		// setup header
		m_HeaderContainer = mainContainer.findViewById(R.id.video_player_header_container);

		// set header margin top
		ScreenSize screenSize = this.get(GalleryActivity.PROP_SCREEN_SIZE);
		RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)m_HeaderContainer.getLayoutParams();
		params.setMargins(0, screenSize.getStatusBarSize(), 0, 0);
		
		// setup footer
		m_FooterContainer = mainContainer.findViewById(R.id.video_player_footer_container);
	}
	
	
	// Call when onDestroy
	@Override
	protected void onDestroy()
	{
		Log.v(TAG, "onDestroy()");
		
		// call super
		super.onDestroy();
	}
	
	
	// Call when onPause
	@Override
	protected void onPause()
	{
		Log.v(TAG, "onPause()");
		
		// stop
		this.stop();
		
		// reset flags
		m_IsControlsVisible = false;
		m_ControlsVisibilityState = ViewVisibilityState.INVISIBLE;
		
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
	
	
	// Call when video completion
	private void onVideoCompletion(MediaPlayer mp)
	{	
		// complete
		this.finish();
	}
	
	
	// Call when video prepared
	private void onVideoPrepared(MediaPlayer mp)
	{
		// set duration
		this.setVideoDurationTimeMillis(m_VideoView.getDuration());
		
		// update seek bar max
		this.updateSeekBarMax();
		
		// start
		this.start();
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
	
	
	// Seek to progress
	private void seekToProgress(int progress)
	{
		// calculate time millis
		int timeMillisPerInterval = INTERVAL_UPDATE_ELAPSED_TIME_MILLIS;
		int timeMillis = progress * timeMillisPerInterval;
		
		// update elapsed time
		this.setVideoElapsedTimeMillis(timeMillis);
		
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
	
	
	// Set status bar visibility
	private void setStatusBarVisibility(boolean visible)
	{
		if(visible)
			m_StatusBarVisibilityHandle = Handle.close(m_StatusBarVisibilityHandle);
		else if(!Handle.isValid(m_StatusBarVisibilityHandle))
		{
			Gallery gallery = this.getGallery();
			if(gallery != null)
				m_StatusBarVisibilityHandle = gallery.setStatusBarVisibility(false);
			else
				Log.e(TAG, "setStatusBarVisibility() - No gallery");
		}
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
	
	
	// Start playing
	private void start()
	{
		Log.v(TAG, "start() - Position: ", m_VideoView.getCurrentPosition());
		
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
	
	
	// Update controls visibility
	private void updateControlsVisibility(boolean animation)
	{
		Log.v(TAG, "updateControlsVisibility() - Visible: ", m_IsControlsVisible);

		// set status bar visibility
		this.setStatusBarVisibility(m_IsControlsVisible);
		
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
					}
				}).start();
				
				// media controls animation
				m_MediaControlContainer.animate().alpha(1f).setDuration(DURATION_ANIMATION).withEndAction(new Runnable()
				{	
					@Override
					public void run()
					{
						m_ControlsVisibilityState = ViewVisibilityState.VISIBLE;
					}
				}).start();
				
				// header animation
				m_HeaderContainer.animate().alpha(1f).setDuration(DURATION_ANIMATION).withEndAction(new Runnable()
				{	
					@Override
					public void run()
					{
						m_ControlsVisibilityState = ViewVisibilityState.VISIBLE;
					}
				}).start();

				// footer animation
				m_FooterContainer.animate().alpha(1f).setDuration(DURATION_ANIMATION).withEndAction(new Runnable()
				{	
					@Override
					public void run()
					{
						m_ControlsVisibilityState = ViewVisibilityState.VISIBLE;
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
			}
		}
		else
		{
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
