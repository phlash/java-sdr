package com.ashbysoft.sdr;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.awt.Graphics;
import java.awt.Color;
import javax.swing.JPanel;

/**
 * Display signal phase diagram from FCD :)
 */
@SuppressWarnings("serial")
public class Phase extends JPanel implements JSDR.JsdrTab {

	private JSDR parent;
	private AudioFormat fmt;
	private int[] dpy;
	private int max=1;

	public Phase(JSDR p, AudioFormat af, int bufsize) {
		parent = p;
		fmt = af;
		int sbytes = (af.getSampleSizeInBits()+7)/8;
		dpy = new int[bufsize/sbytes/af.getChannels()*2];
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
		g.drawString("I: "+parent.ic, bx+2, by+12);
		g.setColor(Color.BLUE);
		g.drawString("Q: "+parent.qc, bx+2, by+22);
		long rtime = System.nanoTime();
		// Data points from buffer..
		g.setColor(Color.YELLOW);
		for(int s=0; s<dpy.length; s+=2) {
			g.drawRect(bx+size/2+(dpy[s]*size/max), by+size/2-(dpy[s+1]*size/max), 0, 0);
		}
		g.drawString(""+max,getWidth()/2+2,12);
		long etime = System.nanoTime();
		parent.logMsg("phase render (nsecs): ret/pts: " + (rtime-stime) + "/" + (etime-rtime));
	}

	public void newBuffer(ByteBuffer buf) {
		// Skip unless we are visible
		if (!isVisible())
			return;
		// determine maxima of either axis for scaling
		max=1;
		for(int s=0; s<dpy.length; s+=2) {
			dpy[s] = buf.getShort()+parent.ic;
			if (fmt.getChannels()>1)
				dpy[s+1] = buf.getShort()+parent.qc;
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
