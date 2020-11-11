package com.ashbysoft.java_sdr;

import java.awt.Color;
import java.awt.Graphics;
import java.nio.ByteBuffer;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

//Yay for JTransforms - the fastest Java FFT so far :)
import edu.emory.mathcs.jtransforms.fft.FloatFFT_1D;

@SuppressWarnings("serial")
public class fft extends IUIComponent implements IAudioHandler, IPublishListener, ActionListener {
	public final String CFG_FFTHAM = "fft-hamming";

	private IUIHost host;
	private IConfig config;
	private ILogger logger;
	private IAudio audio;
	private IPublish publish;
	private float[] dat;
	private float[] psd;
	private float[] win;
	private boolean dow;
	private AudioDescriptor adsc;
	private Color tcol = new Color(0x1f,0x1f,0x0);

	public fft(IConfig cfg, IPublish pub, ILogger lg,
		IUIHost hst, IAudio aud) {
		host = hst;
		config = cfg;
		logger = lg;
		publish = pub;
		setup(aud);
		// Build our menu
		JMenu top = new JMenu("FFT");
		top.setMnemonic(KeyEvent.VK_T);
		JMenuItem item = new JMenuItem("Hamming window on/off", KeyEvent.VK_H);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK));
		item.setActionCommand("fft-hamming");
		item.addActionListener(this);
		top.add(item);
		host.addMenu(top);
		// Grab saved config
		dow = config.getIntConfig(CFG_FFTHAM, 1)!=0 ? true : false;
		// detect changes to audio
		pub.listen(this);
	}

	public void notify(String key, Object val) {
		// check for audio device change
		if ("audio-change".equals(key)) {
			setup((IAudio)val);
		}
	}

	private synchronized void setup(IAudio aud) {
		audio = aud;
		adsc = audio.getAudioDescriptor();
		// Allocate buffers according to format..
		dat = new float[2*adsc.blen/adsc.size];// always allocate for two channels
		psd = new float[dat.length/2+2];		// add two for spectral maxima values
		win = new float[dat.length/2];
		logger.logMsg("fft: dat.len="+dat.length);
		// Calculate window coefficients (hamming)
		for (int s=0; s<win.length; s++)
			win[s] = (float) (0.54 - 0.46*Math.cos(2*Math.PI*s/win.length));
		// [re]attach ourselves to audio data
		audio.remHandler(this);
		audio.addHandler(this);
	}

	public void actionPerformed(ActionEvent e) {
		// Menu actions
		if ("fft-hamming".equals(e.getActionCommand()))
			dow = !dow;
		config.setIntConfig(CFG_FFTHAM, dow ? 1:0);
	}

	protected synchronized void paintComponent(Graphics g) {
		// skip if not visible
		if (!isVisible())
			return;
		// time render
		long stime=System.nanoTime();
		// Clear to black
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, getWidth(), getHeight());
		// Step size and display offset for resampling to screen size
		float step = (float)(dat.length/2)/(float)getWidth();
		int off = getWidth()/2;
		// demod filter (TODO: move to waterfall?)
		int flo = (int)publish.getPublish("demod-filter-low", Integer.MIN_VALUE);
		int fhi = (int)publish.getPublish("demod-filter-high", Integer.MAX_VALUE);
		if (flo > Integer.MIN_VALUE && fhi < Integer.MAX_VALUE) {
			g.setColor(tcol);
			int ts = (int)((float)getWidth()*(float)flo/(float)adsc.rate)+off;
			int te = (int)((float)getWidth()*(float)fhi/(float)adsc.rate)+off;
			g.fillRect(ts, 0, te-ts, getHeight());
		}
		// Reticle
		g.setColor(Color.DARK_GRAY);
		int yh = getHeight()/10;
		int db = -10;
		for (int y=yh; y<getHeight(); y+=yh) {
			g.drawLine(0, y, getWidth(), y);
			g.drawString(db+"dB", 2, y+12);
			db-=10;
		}
		int fs = (adsc.rate/20/10)*10;	// estimate frequency step per vertical, round to 10x multiple
		int xs = (int)((float)getWidth()*(float)fs/(float)adsc.rate);
		int freq = 0;
		for (int x=0; x<off; x+=xs) {
			// draw symmetrical lines either side of centre, label with frequency
			g.drawLine(off+x, 0, off+x, getHeight());
			g.drawString(freq+"Hz", off+x, 12);
			if (x>0) {
				g.drawLine(off-x, 0, off-x, getHeight());
				g.drawString("-"+freq+"Hz", off-x, 12);
			}
			freq+=fs;
		}
		g.setColor(Color.WHITE);
		g.drawString("step: "+step, 2, 24);
		if (dow)
			g.drawString("win:hamming", 2, 36);
		else
			g.drawString("win:none/rect", 2, 36);
		long rtime=System.nanoTime();
		// maxima
		g.setColor(Color.GREEN);
		float pmax = psd[psd.length-1];
		float fmax = psd[psd.length-2];
		g.drawString(String.format("max: %.1f@%.1f",pmax,fmax), getWidth()/2-60, 24);
		// FFT points
		float ys = (float)getHeight()/-100.0f;	// Y scale to place -100dBFS at bottom edge
		int ly = (int)(psd[0]*ys);
		for (int p=0; p<getWidth()-1; p++) {
			// offset and wrap display index as FFT is 0-<pos>-<neg> ordered
			int i = (p+off) % getWidth();
			int y = (int)(getMax(psd, (int)(p*step), (int)step)*ys);
			g.drawLine(i-1, ly, i, y);
			ly = y;
		}
		long ptime=System.nanoTime();
		// BPSK tuning bar(s) (TODO: move to waterfall?)
		boolean dbar = true;
		g.setColor(Color.CYAN);
		for (int fc=0; dbar; fc++) {
			dbar = false;
			String nm = "FUNcube"+fc+"-bpsk-centre";
			int cb = (int)publish.getPublish(nm, -1);
			if (cb>0) {
				int tc = (int)((float)cb/step)+off;
				g.drawLine(tc, getHeight(), tc, 0);
				g.drawString(nm+":"+cb, tc+5, getHeight()*5/6);
				dbar = true;
			}
			nm = "FUNcube"+fc+"-bpsk-tune";
			cb = (int)publish.getPublish(nm, -1);
			if (cb>0) {
				int tc = (int)((float)getWidth()*(float)cb/(float)adsc.rate)+off;
				g.drawLine(tc, getHeight(), tc, 0);
				g.drawString(nm+":"+cb, tc+5, getHeight()*5/6);
				dbar = true;
			}
		}
		long ttime=System.nanoTime();
		logger.logMsg("fft: render (nsecs) ret/psd/tune: " +
			(rtime-stime) + "/" +
			(ptime-rtime) + "/" +
			(ttime-ptime));
	}
	// Find largest value in a array from offset o, length l
	private float getMax(float[] a, int o, int l) {
		float r = a[o];
		for (int i=o+1; i<o+l; i++) {
			if (a[i]>r)
				r=a[i];
		}
		return r;
	}

	public synchronized void receive(float[] buf) {
		// copy locally
		System.arraycopy(buf, 0, dat, 0, dat.length);
		// FFT
		FloatFFT_1D fft = new FloatFFT_1D(dat.length/2);
		fft.complexForward(dat);
		// Calculate power spectral density
		// correction factor, (2/N)^2
		float fs = (float)adsc.rate;
		float cf = 2f/(float)(dat.length/2);
		cf = cf * cf;
		float m = -Float.MAX_VALUE;
		int p = -1;
		for (int s=0; s<dat.length-1; s+=2) {
			// https://pysdr.org/content/sampling.html#calculating-power-spectral-density
			// => absolute vector length, times 2/N, then squared, then log10.
			// We can avoid a sqrt+square by use of squared 2/N (cf) then log10
			psd[s/2] = 10f*(float)Math.log10( ((dat[s]*dat[s]) + (dat[s+1]*dat[s+1])) * cf);
			if (m<psd[s/2]) {
				m=psd[s/2];
				p = s;
			}
		}
		// convert array index to actual frequency offset (given psd runs: zero->f/2->-f/2->zero)
		if (p<dat.length/2) {
			// lower half - positive frequencies up to f/2
			p = p*adsc.rate/dat.length;
		} else {
			// upper half - negative frequencies
			p -= dat.length;
			p = p*adsc.rate/dat.length;
		}
		// Stash maxima frequency and size
		psd[psd.length-2] = (float)p;
		psd[psd.length-1] = m;
		// publish for other displays
		publish.setPublish("fft-psd", psd);
		repaint();
	}

	public void hotKey(char c) {
	}
}
