// Display signal phase diagram from FCD :)
package com.ashbysoft.java_sdr;

import java.util.Iterator;
import java.nio.ByteBuffer;
import java.awt.Graphics;
import java.awt.Color;

@SuppressWarnings("serial")
public class phase extends IUIComponent implements IAudioHandler {

	private ILogger logger;
	private IUIHost host;
	private IAudio audio;
	private int[] dpy;
	private int max=1;

	public phase(IConfig cfg, IPublish pub, ILogger log,
		IUIHost hst, IAudio aud) {
		logger = log;
		host = hst;
		audio = aud;
		AudioDescriptor ad = audio.getAudioDescriptor();
		int sbytes = (ad.bits+7)/8;
		dpy = new int[ad.blen/sbytes/ad.chns*2];
		audio.addHandler(this);
	}

	public void paintComponent(Graphics g) {
		// time render
		long stime = System.nanoTime();
		// Get constraining dimension and box offsets
		int size = getWidth();
		if (getHeight()<size)
			size = getHeight();
		int bx = (getWidth()-size)/2;
		int by = (getHeight()-size)/2;
		// Background..
		g.setColor(Color.BLACK);
		g.fillRect(0,0,getWidth(),getHeight());
		// Reticle..
		g.setColor(Color.DARK_GRAY);
		g.drawOval(bx+10, by+10, size-20, size-20);
		g.drawLine(bx+size/2, by+5, bx+size/2, by+size-5);
		g.drawLine(bx+5, by+size/2, bx+size-5, by+size/2);
		// I/Q offsets
		g.setColor(Color.RED);
		g.drawString("I: "+audio.getICorrection(), bx+2, by+12);
		g.setColor(Color.BLUE);
		g.drawString("Q: "+audio.getQCorrection(), bx+2, by+22);
		long rtime = System.nanoTime();
		// Data points from buffer..
		g.setColor(Color.YELLOW);
		for(int s=0; s<dpy.length; s+=2) {
			g.drawRect(bx+size/2+(dpy[s]*size/max), by+size/2-(dpy[s+1]*size/max), 0, 0);
		}
		g.drawString(""+max,getWidth()/2+2,12);
		long etime = System.nanoTime();
		logger.logMsg("phase render (nsecs): ret/pts: " + (rtime-stime) + "/" + (etime-rtime));
	}

	public void receive(ByteBuffer buf) {
		// Skip unless we are visible
		if (!isVisible())
			return;
		// determine maxima of either axis for scaling
		max=1;
		AudioDescriptor ad = audio.getAudioDescriptor();
		for(int s=0; s<dpy.length; s+=2) {
			dpy[s] = buf.getShort();
			if (ad.chns>1)
				dpy[s+1] = buf.getShort();
			else
				dpy[s+1] = 0;
			max = Math.max(max, Math.abs(dpy[s]));
			max = Math.max(max, Math.abs(dpy[s+1]));
		}		
		// double maxima to get a nice graph..
		max = Math.max(max*2,1);
		repaint();
	}

	public void hotKey(char c) {
		;
	}
}
