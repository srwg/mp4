package com.rabbitxp.mp4;

import java.io.IOException;
import java.nio.ByteBuffer;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.view.Surface;

public class SceneSeeker {
	private final static String MIME_TYPE = "video/avc";
	private MediaExtractor m_Extractor;
	private MediaCodec m_Codec;
	private MediaFormat m_Format;
	private Surface m_Surface;
	
	public void show(int pos) {
		m_Extractor.seekTo(pos * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        m_Codec.configure(m_Format, m_Surface, null, 0);
        m_Codec.start();
		ByteBuffer[] inBufs = m_Codec.getInputBuffers();
		MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
		boolean outDone = false;
		boolean inDone = false;
		while (!outDone) {
			if (!inDone) {
				int inId = m_Codec.dequeueInputBuffer(10000);
				if (inId >= 0) {
					ByteBuffer inBuf = inBufs[inId];
					int chunkSize = m_Extractor.readSampleData(inBuf, 0);
					if (chunkSize < 0) {
						m_Codec.queueInputBuffer(inId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
						inDone = true;
						continue;
					}
					long t = m_Extractor.getSampleTime();
					m_Codec.queueInputBuffer(inId, 0, chunkSize, t, 0);
					m_Extractor.advance();
				}
			}
			if (!outDone) {
				int outId = m_Codec.dequeueOutputBuffer(info, 10000);
				if (outId >= 0) {
					if (info.presentationTimeUs >= pos * 1000L) {
						m_Codec.releaseOutputBuffer(outId, true);
						outDone = true;
						inDone = true;
					} else m_Codec.releaseOutputBuffer(outId, false);
				}
			}
		}
		m_Codec.stop();
	}

	public SceneSeeker(String filename) {
		m_Extractor = new MediaExtractor();
		try {
			m_Extractor.setDataSource(filename);
		} catch (Exception e) {
		}
        for (int t_id=0; t_id < m_Extractor.getTrackCount(); t_id++) {
        	m_Format = m_Extractor.getTrackFormat(t_id);
        	if (m_Format.getString(MediaFormat.KEY_MIME).equals(MIME_TYPE)) {
        		m_Extractor.selectTrack(t_id);
        		break;
        	}
        }
		try {
			m_Codec = MediaCodec.createDecoderByType(MIME_TYPE);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void setSurface(Surface surface) {
		m_Surface = surface;
	}

	public void release() {
		if (m_Codec != null) {
			m_Codec.release();
			m_Codec = null;
		}
		if (m_Extractor != null) {
			m_Extractor.release();
			m_Extractor = null;
		}
	}
}