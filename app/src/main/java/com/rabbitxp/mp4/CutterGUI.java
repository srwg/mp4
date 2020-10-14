package com.rabbitxp.mp4;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;

import android.Manifest;
import android.app.Activity;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnSeekCompleteListener;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.ProgressBar;

public class CutterGUI extends Activity 
implements OnSeekCompleteListener, OnCompletionListener, Callback{
	
	private Button m_PlayPauseButton;
	private Button m_CommitButton;
	private Button m_LeftButton;
	private Button m_RightButton;
	private ProgressBar m_Progress;
	private SurfaceView m_Video;
	private Surface m_Surface;
	private MediaPlayer m_Player;
	private SceneSeeker m_Seeker;
	private int[] m_KeyTime;
	private String m_File, m_Cut;
	private int m_CurKey, KEY_END, m_CurPos;
	private int m_MarkStart, m_MarkEnd, TIME_END;
	private ArrayList<Integer> m_Marks;
	private boolean m_Resume, m_IsSeeking, m_IsRewind;
	private int m_FFCur;

	public void onCreate(Bundle bd) {
        super.onCreate(bd);
		String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
		requestPermissions(permissions, 1);

		m_File = getIntent().getExtras().getString("mp4_fn");
		m_Cut = getIntent().getExtras().getString("cut_fn");
        m_KeyTime = getIntent().getExtras().getIntArray("key_time");
        KEY_END = m_KeyTime.length - 1;
        TIME_END = m_KeyTime[KEY_END];
		try {
	        m_Player = new MediaPlayer();
			m_Player.setOnSeekCompleteListener(this);
			m_Player.setOnCompletionListener(this);
			m_Player.setDataSource(m_File + ".mp4");
			m_Seeker = new SceneSeeker(m_File + ".mp4");
			m_CurKey = Integer.parseInt(new BufferedReader(new FileReader(m_File + ".pos"), 16).readLine());
		} catch (Exception e) {
			m_CurKey = 0;
		}
        setContentView(R.layout.movie);
        m_Video = (SurfaceView) findViewById(R.id.video);
        m_Video.getHolder().addCallback(this);
        m_PlayPauseButton = (Button) findViewById(R.id.b_play);
        m_CommitButton = (Button) findViewById(R.id.b_commit);
        m_LeftButton = (Button) findViewById(R.id.b_left);
        m_RightButton = (Button) findViewById(R.id.b_right);
        m_Progress = (ProgressBar) findViewById(R.id.b_progress);
        m_Progress.setMax(TIME_END);
        m_Progress.setProgress(m_KeyTime[m_CurKey]);
        m_MarkStart = 0;
        m_MarkEnd = TIME_END;
        m_Marks = new ArrayList<Integer>();
        m_Resume = true;
        m_IsSeeking = false;
		m_IsRewind = false;
		m_FFCur = 0;

		/*
		m_PlayPauseButton.setOnLongClickListener(
				new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View view) {

						return true;
					}
				}
		);*/
    }

	protected void onPause() {
    	getCurrentKey();
    	super.onPause();
    }
	
	protected void onStop() {
		saveInfo();
		super.onStop();
	}
	
	protected void onDestroy() {
		if (m_Seeker != null) {
			m_Seeker.release();
			m_Seeker = null;
		}
		if (m_Player != null) {
			m_Player.release();
			m_Player = null;
		}
		super.onDestroy();
	}
	
    protected void onSaveInstanceState(Bundle bd){
    	bd.putString("mp4_fn", m_File);
     	bd.putIntArray("key_time", m_KeyTime);
    }

	@Override
	public void onSeekComplete(MediaPlayer mp) {
		if (m_Resume) play();
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		onPause();
	}

	@Override
	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
	}

	@Override
	public void surfaceCreated(SurfaceHolder surface) {
		boolean should_prepare = (m_Surface == null);
		m_Surface = surface.getSurface();
		try {
			m_Seeker.setSurface(m_Surface);
			m_Player.setSurface(m_Surface);
			if (should_prepare) m_Player.prepare();
		} catch (Exception e) {
		} 
		if (should_prepare) {
			int mh = m_Player.getVideoHeight();
			int mw = m_Player.getVideoWidth();
			int vh = m_Video.getHeight();
			int vw = m_Video.getWidth();
			LayoutParams lp = m_Video.getLayoutParams();
			lp.width = Math.min(vw, vh * mw / mh);
			lp.height = Math.min(vh, vw * mh / mw);
			m_Video.setLayoutParams(lp);
		}
		m_Resume = true;
		m_Player.seekTo(m_KeyTime[m_CurKey + 1]);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder surface) {
	}
	
	public void play(View v) {
		if (m_Player.isPlaying()) pause();
		else play();
	}

	public void fast_forward(View v) {
		showSeek(false);
		m_Resume = m_Player.isPlaying();
		getCurrentKey();
		if (m_CurKey + 2 < KEY_END) {
			m_CurKey++;
			if (m_CurKey == m_FFCur) m_CurKey++;
			m_FFCur = m_CurKey;
			m_Player.seekTo((m_KeyTime[m_CurKey] + m_KeyTime[m_CurKey + 1]) / 2);
		}
	}

	public void fast_rewind(View v) {
		showSeek(false);
		m_Resume = false;
		m_FFCur = 0;
		boolean isRewind = m_IsRewind;
		getCurrentKey();
		if (isRewind && m_CurKey > 0) {
			m_CurKey--;
		}
		m_Player.seekTo((m_KeyTime[m_CurKey] + m_KeyTime[m_CurKey + 1]) / 2);
		m_IsRewind = true;
	}
	
	public void forward(View v) {
		jump(200);
	}

	public void rewind(View v) {
		jump(-200);
	}
	
	public void mark_left(View v) {
		m_Resume = m_Player.isPlaying();
		getCurrentKey();
		m_MarkStart = m_KeyTime[m_CurKey];
		if (m_CurKey < KEY_END) {
			m_Player.seekTo((m_KeyTime[m_CurKey] + m_KeyTime[m_CurKey + 1]) / 2);
		}
		v.setBackgroundColor(0x20ff0000);
		if (isGood()) m_CommitButton.setBackgroundColor(0x20ff0000);
	}
	
	public void mark_right(View v) {
		if (m_Player.isPlaying()) {
			m_Resume = true;
			getCurrentKey();
			m_MarkEnd = m_KeyTime[m_CurKey];
			if (m_CurKey < KEY_END) {
				m_Player.seekTo((m_KeyTime[m_CurKey] + m_KeyTime[m_CurKey + 1]) / 2);
			}
		} else if (m_IsSeeking) {
			m_MarkEnd = m_CurPos;
		} else {
			m_MarkEnd = m_Player.getCurrentPosition();
		}
		v.setBackgroundColor(0x20ff0000);
		if (isGood()) m_CommitButton.setBackgroundColor(0x2000ff00);
	}
	
	public void commit(View v) {
		int last = m_Marks.size() - 2;
		if (m_MarkStart == 0 && m_MarkEnd == TIME_END && last >= 0) {
			m_Marks.remove(last + 1);
			m_Marks.remove(last);
			return;
		}
		if (m_MarkStart > m_MarkEnd - 50) {
		} else if (last >= 0 && m_Marks.get(last + 1) >= m_MarkStart) {
			m_Marks.set(last, Math.min(m_Marks.get(last), m_MarkStart));
			m_Marks.set(last + 1, Math.max(m_Marks.get(last + 1), m_MarkEnd));
		} else {
			m_Marks.add(m_MarkStart);
			m_Marks.add(m_MarkEnd);
		}
		m_MarkStart = 0;
        m_MarkEnd = TIME_END;
		m_CommitButton.setBackgroundColor(0x00000000);
		m_LeftButton.setBackgroundColor(0x00000000);
		m_RightButton.setBackgroundColor(0x00000000);
	}

	private void saveInfo() {
		try {
	        FileWriter fw = new FileWriter(m_Cut, true);
	        for (int i=0; i< m_Marks.size()/2; i++) {
	        	fw.write(m_Marks.get(i*2).toString() +
					"\t" + m_Marks.get(i*2+1).toString() + "\n");
	        }
	        fw.close();
	        m_Marks.clear();
			fw = new FileWriter(m_File + ".pos");
			fw.write(Integer.valueOf(m_CurKey).toString() + "\n");
			fw.close();
		} catch (Exception e){
			e.printStackTrace();
		};
	}

	private void pause() {
		if (m_Player.isPlaying()) {
			m_Player.pause();
			m_PlayPauseButton.setText(">");
		}
	}
	
	private void play() {
		showSeek(false);
		if (!m_Player.isPlaying()) {
			m_PlayPauseButton.setText("");
			m_Player.start();
		}
	}
	
	private void getCurrentKey() {
		pause();
		m_IsRewind = false;
		int t = m_Player.getCurrentPosition();
		while (m_CurKey < KEY_END && m_KeyTime[m_CurKey] < t) ++m_CurKey;
		while (m_CurKey > 0 && m_KeyTime[m_CurKey] > t) --m_CurKey;
		m_Progress.setProgress(t);
	}
	
	private void showSeek(boolean show) {
		if (show == m_IsSeeking) return;
		m_IsSeeking = show;
		if (m_IsSeeking) m_CurPos = m_Player.getCurrentPosition();
		m_Player.setSurface(show ? null : m_Surface);
	}

	private void jump(int dt) {
		pause();
		showSeek(true);
		m_CurPos += dt;
		m_FFCur = 0;
		if (m_CurPos < 0) m_CurPos = 0;
		else if (m_CurPos > TIME_END - 500) m_CurPos = TIME_END - 500;
		m_Seeker.show(m_CurPos);
	}
	
	private boolean isGood() {
		if (m_MarkStart == 0 || m_MarkEnd == TIME_END) return false;
		return m_MarkEnd > m_MarkStart + 1000;
	}
}