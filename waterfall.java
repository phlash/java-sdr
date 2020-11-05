package com.ashbysoft.java_sdr;

import java.util.ArrayList;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

public class waterfall extends JPanel implements IPublishListener {
    private ILogger logger;
	private ArrayList<double[]> data;
    private AudioDescriptor adsc;
    private Color peak = Color.CYAN;
    private BufferedImage buf;
    private boolean needpaint = true;
    public waterfall(IPublish pub, ILogger log, IAudio aud) {
        logger= log;
        data = new ArrayList<double[]>();
        adsc = aud.getAudioDescriptor();
        pub.listen(this);
    }

	public void notify(String key, Object val) {
		// detect waterfall data, push to start of array
		if ("fft-psd".equals(key) &&
            val instanceof double[]) {
            synchronized(this) {
    			data.add(0, ((double[])val).clone());   // clone as caller will reuse
            }
			needpaint = true;
		}
        // detect audio change
        else if ("audio-change".equals(key) &&
            val instanceof IAudio) {
            synchronized(this) {
                adsc = ((IAudio)val).getAudioDescriptor();
                data.clear();
            }
            needpaint = true;
        }
	}

    protected synchronized void paintComponent(Graphics g) {
		// skip if doesn't need painting
		if (!needpaint)
			return;
        long stime = System.nanoTime();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        // Bail if no data
        if (data.size()<1)
            return;
        // check if current image buffer is ok for size
        if (null==buf || getWidth()!=buf.getWidth() || getHeight()!=buf.getHeight())
            buf = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
		// Step size and FFT offset for resampling to screen size
		float s = (float)(data.get(0).length-2)/(float)getWidth();
		int off = getWidth()/2;
		// adjust step and offset if only single channel data
		if (adsc.chns<2) {
			s = s/2;
			off = 0;
		}
		int t = (int)Math.ceil(s);
        // remove old lines (if any)
        while (data.size() > getHeight())
            data.remove(data.size()-1);
        // iterate all visible lines, find maxima across all
        double max = 0;
        for (double[] psd : data) {
            if (psd[psd.length-1] > max)
                max = psd[psd.length-1];
        }
        long mtime = System.nanoTime();
        // intensity scaling to fit all psd values in 0-255
        float h = (float)(255.0/Math.log10(max+1.0));
        // iterate again, painting lines
        int y = 0;
        int cnt = 0;
        for (double[] psd : data) {
            //  scale to width and draw using colour intensity
            for (int p=0; p<getWidth()-1; p++) {
                // offset and wrap index to display negative freqs, then positives..
                int i = (p+off) % getWidth();
                int f = (int)(Math.log10(getMax(data.get(y), (int)(i*s), t)+1.0)*h);
                Color c = new Color(
                    (peak.getRed()*f/256)&0xff,
                    (peak.getGreen()*f/256)&0xff,
                    (peak.getBlue()*f/256)&0xff
                );
                buf.setRGB(p, y, c.getRGB());
                cnt++;
            }
            y++;
        }
        long ptime = System.nanoTime();
        g.drawImage(buf, 0, 0, null);
        long rtime = System.nanoTime();
        needpaint = false;
        logger.logMsg("waterfall: render (nsecs) max/lines/blt "+(mtime-stime)+"/"+(ptime-mtime)+"/"+(rtime-ptime));
    }

	// Find largest magnitude value in a array from offset o, length l, stride 1
	private double getMax(double[]a, int o, int l) {
		double r = 0;
		for (int i=o; i<o+l; i++) {
			if (Math.abs(a[i])>r)
				r=a[i];
		}
		return r;
	}
}