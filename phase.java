// Display signal phase diagram from FCD :)
package com.ashbysoft.java_sdr;

import java.util.Iterator;
import java.nio.ByteBuffer;
import java.awt.Graphics;
import java.awt.Color;

@SuppressWarnings("serial")
public class phase extends IUIComponent implements IAudioHandler, IPublishListener {

	private ILogger logger;
	private IUIHost host;
	private IAudio audio;
	private float[] dpy;

	public phase(IConfig cfg, IPublish pub, ILogger log,
		IUIHost hst, IAudio aud) {
		logger = log;
		host = hst;
		audio = aud;
		AudioDescriptor ad = audio.getAudioDescriptor();
		dpy = new float[2*ad.blen/ad.size];
		logger.logMsg("phase: dpy.length="+dpy.length);
		audio.addHandler(this);
		pub.listen(this);
	}

	public void notify(String key, Object val) {
		// check for audio stream change
		if ("audio-change".equals(key) &&
		    val instanceof IAudio) {
			AudioDescriptor ad = ((IAudio)val).getAudioDescriptor();
			synchronized(this) {
				dpy = new float[2*ad.blen/ad.size];
				audio = (IAudio)val;
				audio.remHandler(this);
				audio.addHandler(this);
			}
		}
	}

	public synchronized void paintComponent(Graphics g) {
		// skip if not visible
		if (!isVisible())
			return;
		// time render
		long stime = System.nanoTime();
		// Get constraining dimension and phase box offsets
		int size = getWidth();
		if (getHeight()<size)
			size = getHeight();
		int bx = (getWidth()-size);
		int by = (getHeight()-size)/2;
		// Background..
		g.setColor(Color.BLACK);
		g.fillRect(0,0,getWidth(),getHeight());
		// Reticle..
		g.setColor(Color.DARK_GRAY);
		g.drawOval(bx+10, by+10, size-20, size-20);
		g.drawLine(bx+size/2, by+5, bx+size/2, by+size-5);
		g.drawLine(bx+5, by+size/2, bx+size-5, by+size/2);
		// I/Q separator (if visible)
		if (bx>0) {
			g.drawLine(0, getHeight()/2, bx, getHeight()/2);
			g.drawLine(bx, 0, bx, getHeight());
		}
		// I/Q offsets
		g.setColor(Color.RED);
		g.drawString("I: "+audio.getICorrection(), 2, 12);
		g.setColor(Color.BLUE);
		g.drawString("Q: "+audio.getQCorrection(), 2, getHeight()/2+12);
		long rtime = System.nanoTime();
		// Data points from buffer..
		float max = -1;
		for (int s=0; s<dpy.length; s++) {
			float a = (float)Math.abs(dpy[s]);
			if (max<a)
				max = a;
		}
		float step = (float)(bx*2)/(float)dpy.length;
		g.setColor(Color.GRAY);
		g.drawString("step:"+1f/step, 50, 12);
		float pos = 0;
		float hiq = (float)(getHeight()/4)/max;
		float hph = (float)(size/2)/max;
		int lpix = 0;
		float lsti = 0;
		float lstq = 0;
		float avgi = 0;
		float avgq = 0;
		int acnt = 0;
		for(int s=0; s<dpy.length; s+=2) {
			avgi += dpy[s];
			avgq += dpy[s+1];
			acnt += 1;
			pos += step;
			int pix = (int)pos;
			if (pix>lpix) {
				// use IQ step scaling to sub-sample phase points - reduce drawing time!
				g.setColor(Color.YELLOW);
				g.drawRect(bx+size/2+(int)(dpy[s]*hph), by+size/2-(int)(dpy[s+1]*hph), 0, 0);
				// We have moved one pixel, draw the I/Q lines
				avgi = avgi/acnt;
				avgq = avgq/acnt;
				g.setColor(Color.RED);
				g.drawLine(pix, getHeight()/4-(int)(lsti*hiq), pix, getHeight()/4-(int)(avgi*hiq));
				g.setColor(Color.BLUE);
				g.drawLine(pix, getHeight()*3/4-(int)(lstq*hiq), pix, getHeight()*3/4-(int)(avgq*hiq));
				lpix = pix;
				lsti = avgi;
				lstq = avgq;
				acnt = 0;
				avgi = avgq = 0;
			}
		}
		g.setColor(Color.GREEN);
		g.drawString("max: "+max, bx+size/2, 12);
		long etime = System.nanoTime();
		logger.logMsg("phase render (nsecs): ret/pts: " + (rtime-stime) + "/" + (etime-rtime));
	}

	public void receive(float[] buf) {
		synchronized(this) {
			System.arraycopy(buf, 0, dpy, 0, dpy.length);
		}
		repaint();
	}

	public void hotKey(char c) {
		;
	}
}
