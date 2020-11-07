package com.ashbysoft.java_sdr;

import java.util.ArrayList;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.MemoryImageSource;
import java.awt.image.ColorModel;
import javax.swing.JPanel;

public class waterfall extends JPanel implements IPublishListener {
    private ILogger logger;
    private AudioDescriptor adsc;
    private Color peak = Color.CYAN;
	private ArrayList<double[]> data;
    private int[] pixs;
    private MemoryImageSource mis;
    private Image img;
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
                pixs = null;
            }
            needpaint = true;
        }
	}

    protected synchronized void paintComponent(Graphics g) {
		// skip if nothing need painting
		if (!needpaint)
			return;
        long stime = System.nanoTime();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        // bail if no data
        if (data.size()<1)
            return;
        // remove old lines (if any)
        while (data.size() > getHeight())
            data.remove(data.size()-1);
		// step size and FFT offset for resampling to screen size
		float step = (float)(data.get(0).length-2)/(float)getWidth();
		int off = getWidth()/2;
		// adjust step and offset if only single channel data
		if (adsc.chns<2) {
			step = step/2;
			off = 0;
		}
        ColorModel cm = ColorModel.getRGBdefault();
        // check if current image is ok for size, or if we have no pixels yet
        if (null==pixs || getWidth()!=img.getWidth(null) || getHeight()!=img.getHeight(null)) {
            // re-create pixel data from raw
            pixs = new int[getWidth()*getHeight()];
            int line = 0;
            for (double[] psd : data) {
                paintLine(psd, pixs, line*getWidth(), step, off);
                line++;
            }
            // connect to animation image
            mis = new MemoryImageSource(getWidth(), getHeight(), cm, pixs, 0, getWidth());
            mis.setAnimated(true);
            img = createImage(mis);
        } else {
            // copy down pixels, add top line from raw
            System.arraycopy(pixs, 0, pixs, getWidth(), pixs.length-getWidth());
            paintLine(data.get(0), pixs, 0, step, off);
        }
        // push pixels!
        mis.newPixels(pixs, cm, 0, getWidth());
        long ptime = System.nanoTime();
        g.drawImage(img, 0, 0, null);
        long rtime = System.nanoTime();
        needpaint = false;
        logger.logMsg("waterfall: render (nsecs) lines/blt "+(ptime-stime)+"/"+(rtime-ptime));
    }

    private void paintLine(double[] psd, int[] pixs, int start, float step, int off) {
        // intensity scaling to fit all psd values in 0-255
        double max = psd[psd.length-1];
        float h = (float)(255.0/Math.log10(max+1.0));
        //  scale to width and draw using colour intensity
        for (int p=0; p<getWidth()-1; p++) {
            // offset and wrap index to display negative freqs, then positives..
            int i = (p+off) % getWidth();
            int f = (int)(Math.log10(getMax(psd, (int)(i*step), (int)step)+1.0)*h);
            Color c = new Color(
                (peak.getRed()*f/256)&0xff,
                (peak.getGreen()*f/256)&0xff,
                (peak.getBlue()*f/256)&0xff
            );
            pixs[start+p] = c.getRGB();
        }
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