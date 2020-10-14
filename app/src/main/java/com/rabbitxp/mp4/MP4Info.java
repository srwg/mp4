package com.rabbitxp.mp4;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class MP4Info {
	private RandomAccessFile m_File;
	private int [] m_KeyTimes;
	
	public MP4Info(File f) throws IOException {
		m_File = new RandomAccessFile(f, "r");
		x_getKeyFrameTable();
		m_File.close();
	}
	
	public final int [] getKeyTimes() {
		return m_KeyTimes;
	}

	private void x_searchAtom(String atom, long [] range) throws IOException {
		byte[] b = new byte[4];
		while (range[0] < range[1]) {
			m_File.seek(range[0]);
			int size = m_File.readInt();
			m_File.read(b);
			if (size == 1) throw new IOException(new String(b) + " atom size too big.");
			if (new String(b).equals(atom)) {
				range[1] = range[0] + size;
				range[0] += 8;
				return;
			} 
			range[0] += size;
		}
		throw new IOException(atom + " atom not found.");
	}
	
	static private int[] computeFailure(byte[] pattern) {
		int[] failure = new int[pattern.length];
		int j = 0;
		for (int i = 1; i < pattern.length; i++) {
			while (j > 0 && pattern[j] != pattern[i]) j = failure[j - 1];
			if (pattern[j] == pattern[i]) j++;
			failure[i] = j;
		}
		return failure;
	}
	
	static private int indexOf(byte[] data, String pats) {
		byte [] pattern = pats.getBytes();
		int[] failure = computeFailure(pattern);
		int j = 0;
		for (int i = 0; i < data.length; i++) {
			while (j > 0 && pattern[j] != data[i]) j = failure[j - 1];
			if (pattern[j] == data[i]) j++;
			if (j == pattern.length) return i - pattern.length + 1;
		}
		return -1;
	}
	
	private void x_getKeyFrameTable() throws IOException {
		long [] range = new long[2];
		range[0] = 0;
		range[1] = m_File.length();
		x_searchAtom("moov", range);
		long v_end = range[1];
		while (true) {
			// search for next mdia atom
			x_searchAtom("trak", range);
			long t_end = range[1];
			x_searchAtom("mdia", range);
			// read the whole mdia atom
			byte [] mb = new byte[(int)(range[1] - range[0]) + 4];
			m_File.read(mb);
			ByteBuffer bb = ByteBuffer.wrap(mb);
			// continue if this atom is not video
			int index = indexOf(mb, "vide");
			if (index < 0) {
				range[0] = t_end;
				range[1] = v_end; 
				continue;
			}
			// check mdhd for time scale
			index = indexOf(mb, "mdhd");
			if (index < 0) throw new IOException("mdhd atom not found.");
			// get time scaling and duration
			int ver = mb[index + 4];
			int t_scale;
			long t_dur;
			if (ver == 0) { 
				t_scale = bb.getInt(index + 16);
				t_dur = bb.getInt(index + 20);
			} else {
				t_scale = bb.getInt(index + 24);
				t_dur = bb.getLong(index + 28);
			}
			// check stts for frame duration (rate)
			index = indexOf(mb, "stts");
			if (index < 0) throw new IOException("stts atom not found.");
			int n_fm = bb.getInt(index + 8);
			int[] dfdt = new int[n_fm * 2];
			bb.position(index + 12);
			bb.asIntBuffer().get(dfdt);
			int n_f = 0;
			for (int fm = 0; fm < n_fm; fm++) {
				n_f += dfdt[fm * 2];
			}
			int[] t_fm = new int[n_f + 1];
			int t = 0;
			int f = 0;
			t_fm [f] = t;
			for (int fm = 0; fm < n_fm; fm++) {
				for (int df = 0; df < dfdt[fm * 2]; df++) {
					t += dfdt[fm * 2 + 1];
					t_fm[++f] = t;
				}
			}
			// check stss for key frame positiion
			index = indexOf(mb, "stss");
			if (index < 0) throw new IOException("stss atom not found.");
			int n_kf = bb.getInt(index + 8);
			m_KeyTimes = new int[n_kf + 1];
			bb.position(index + 12);
			bb.asIntBuffer().get(m_KeyTimes);
			// construct the time table
			for (int k=0; k< n_kf; k++) {
				m_KeyTimes[k] = (int) (1000.0 * t_fm[m_KeyTimes[k] - 1] / t_scale);
			}
			m_KeyTimes[n_kf] = (int) (1000.0 * t_dur / t_scale);
			return;
		}
	}
}