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
	private ArrayList<float[]> data;
    private int[] pixs;
    private MemoryImageSource mis;
    private Image img;

    public waterfall(IPublish pub, ILogger log, IAudio aud) {
        logger= log;
        data = new ArrayList<float[]>();
        adsc = aud.getAudioDescriptor();
        pub.listen(this);
    }

	public void notify(String key, Object val) {
		// detect waterfall data, push to start of array
		if ("fft-psd".equals(key) &&
            val instanceof float[]) {
            synchronized(this) {
    			data.add(0, ((float[])val).clone());   // clone as caller will reuse
            }
			repaint();
		}
        // detect audio change
        else if ("audio-change".equals(key) &&
            val instanceof IAudio) {
            synchronized(this) {
                adsc = ((IAudio)val).getAudioDescriptor();
                data.clear();
                pixs = null;
            }
            repaint();
        }
	}

    protected synchronized void paintComponent(Graphics g) {
        long stime = System.nanoTime();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        // bail if no data
        if (data.size()<1)
            return;
        // remove old lines (if any)
        while (data.size() > getHeight())
            data.remove(data.size()-1);
		// step size and display offset for resampling to screen size
		float step = (float)(data.get(0).length-2)/(float)getWidth();
		int off = getWidth()/2;
        // we'll use the default RGB color model for pixel data
        ColorModel cm = ColorModel.getRGBdefault();
        // check if current image is ok for size, or if we have no pixels yet
        if (null==pixs || getWidth()!=img.getWidth(null) || getHeight()!=img.getHeight(null)) {
            // re-create pixel data from raw
            pixs = new int[getWidth()*getHeight()];
            int line = 0;
            for (float[] psd : data) {
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
        logger.logMsg("waterfall: render (nsecs) lines/blt "+(ptime-stime)+"/"+(rtime-ptime));
    }

    private void paintLine(float[] psd, int[] pixs, int start, float step, int off) {
        // intensity scaling to map -100dBFS to 255
        float h = -2.55f;
        //  scale to width, map to color, insert pixel
        for (int p=0; p<getWidth(); p++) {
            int f = 255-(int)(getMax(psd, (int)(p*step), (int)step)*h);
            // 'cause we're looking at dbFS (+/-), we could have values outside 0-255, clamp
            f = f<0 ? 0 : f;
            f = f>255 ? 255 : f;
            Color c = new Color(
                (peak.getRed()*f/256),
                (peak.getGreen()*f/256),
                (peak.getBlue()*f/256)
            );
            int i = (p+off) % getWidth();
            pixs[start+i] = c.getRGB();
        }
    }
	// Find largest value in a array from offset o, length l, stride 1
	private float getMax(float[]a, int o, int l) {
		float r = a[o];
		for (int i=o+1; i<o+l; i++) {
			if (a[i]>r)
				r=a[i];
		}
		return r;
	}
}