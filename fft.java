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
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

@SuppressWarnings("serial")
public class fft extends IUIComponent implements IAudioHandler, ActionListener {
	public final String CFG_FFTHAM = "fft-hamming";
	public final String CFG_FFTLOG = "fft-log";
	public final String CFG_FFTGAIN= "fft-gain";
	public final String CFG_FFTAUTO= "fft-auto";

	private IUIHost host;
	private IConfig config;
	private ILogger logger;
	private IAudio audio;
	private IPublish publish;
	private double[] dat;
	private double[] spc;
	private double[] psd;
	private double[] win;
	private boolean dow;
	private boolean log;
	private boolean auto;
	private int gain;
	private AudioDescriptor adsc;

	public fft(IConfig cfg, IPublish pub, ILogger lg,
		IUIHost hst, IAudio aud) {
		host = hst;
		config = cfg;
		logger = lg;
		audio = aud;
		publish = pub;
		adsc = audio.getAudioDescriptor();
		// Allocate buffers according to format..
		int sbytes = (adsc.bits+7)/8;
		dat = new double[adsc.blen/sbytes/adsc.chns*2];
		spc = new double[dat.length];
		psd = new double[dat.length/2+2];	// add two for spectral maxima values
		win = new double[dat.length/2];
		logger.logMsg("fft: dat.len="+dat.length);
		// Calculate window coefficients (hamming)
		for (int s=0; s<win.length; s++)
			win[s] = (double) (0.54 - 0.46*Math.cos(2*Math.PI*s/win.length));
		// Build our menu
		JMenu top = new JMenu("FFT");
		top.setMnemonic(KeyEvent.VK_T);
		JMenuItem item = new JMenuItem("Hamming window on/off", KeyEvent.VK_H);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK));
		item.setActionCommand("fft-hamming");
		item.addActionListener(this);
		top.add(item);
		item = new JMenuItem("Log/lin scale", KeyEvent.VK_L);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK));
		item.setActionCommand("fft-loglin");
		item.addActionListener(this);
		top.add(item);
		item = new JMenuItem("Gain: auto on/off", KeyEvent.VK_A);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK));
		item.setActionCommand("fft-gainauto");
		item.addActionListener(this);
		top.add(item);
		item = new JMenuItem("Gain: +1");
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, 0));
		item.setActionCommand("fft-gainadd");
		item.addActionListener(this);
		top.add(item);
		item = new JMenuItem("Gain: -1");
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.SHIFT_DOWN_MASK));
		item.setActionCommand("fft-gainsub");
		item.addActionListener(this);
		top.add(item);
		host.addMenu(top);
		// Grab saved config
		dow = config.getIntConfig(CFG_FFTHAM, 1)!=0 ? true : false;
		log = config.getIntConfig(CFG_FFTLOG, 1)!=0 ? true : false;
		auto = config.getIntConfig(CFG_FFTAUTO, 1)!=0 ? true : false;
		gain = config.getIntConfig(CFG_FFTGAIN, 100);
		// attach ourselves to audio data
		audio.addHandler(this);
	}

	public void actionPerformed(ActionEvent e) {
		// Menu actions
		if ("fft-hamming".equals(e.getActionCommand()))
			dow = !dow;
		else if ("fft-loglin".equals(e.getActionCommand()))
			log = !log;
		else if ("fft-gainauto".equals(e.getActionCommand()))
			auto = !auto;
		else if ("fft-gainadd".equals(e.getActionCommand()))
			gain++;
		else if ("fft-gainsub".equals(e.getActionCommand()))
			gain = (gain>0 ? gain-1 : 0);
		config.setIntConfig(CFG_FFTHAM, dow ? 1:0);
		config.setIntConfig(CFG_FFTLOG, log ? 1:0);
		config.setIntConfig(CFG_FFTAUTO, auto ? 1:0);
		config.setIntConfig(CFG_FFTGAIN, gain);
	}

	protected void paintComponent(Graphics g) {
		// time render
		long stime=System.nanoTime();
		// Clear to black
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, getWidth(), getHeight());
		// Step size and FFT offset for resampling to screen size
		float s = (float)(dat.length/2)/(float)getWidth();
		int off = getWidth()/2;
		// adjust step and offset if only single channel data
		if (adsc.chns<2) {
			s = s/2;
			off = 0;
		}
		int t = (int)Math.ceil(s);
		g.setColor(Color.DARK_GRAY);
		if (dow)
			g.drawString("step(ham): "+s+"/"+t, 2, 24);
		else
			g.drawString("step(raw): "+s+"/"+t, 2, 24);
		// Scale factor to fit -1 to 1 float sample data into screen height
		float h = (float)(getHeight()/2);
		// PSD and demod filter (log scale if selected)
		int flo = tryParse(publish.getPublish("demod-filter-low", null), Integer.MIN_VALUE);
		int fhi = tryParse(publish.getPublish("demod-filter-high", null), Integer.MAX_VALUE);
		if (flo > Integer.MIN_VALUE && fhi < Integer.MAX_VALUE) {
			g.setColor(Color.decode("0x0f0f0f"));
			int wd = (adsc.chns<2) ? getWidth() : getWidth()/2;
			int ts = (int)((float)flo/(adsc.rate/2)*(float)wd)+off;
			int tw = (int)(((float)fhi-flo)/(adsc.rate/2)*(float)wd);
			g.fillRect(ts, 0, tw, getHeight());
		}
		g.setColor(Color.GREEN);
		double pmax = psd[psd.length-1];
		if (log)
			g.drawString("PSD(log): "+Math.log10(pmax+1.0), 2, 12);
		else
			g.drawString("PSD(lin): "+pmax, 2, 12);
		h = (float)(auto ? (log ? (getHeight())/Math.log10(pmax+1.0) : (getHeight())/pmax) : gain);
		g.drawString("gain("+(auto?'A':'-')+"):"+h, getWidth()-100, 12);
		int ly = 0;
		for (int p=0; p<getWidth()-1; p++) {
			// offset and wrap index to display negative freqs, then positives..
			int i = (p+off) % getWidth();
			int y = log ? (int)(Math.log10(getMax(psd, (int)(i*s), t/2)+1.0)*h) : (int)(getMax(psd, (int)(i*s), t/2)*h);
			g.drawLine(p, getHeight()-ly, p+1, getHeight()-y);
			ly = y;
//					if (2*(int)(p*s)<=spos && spos<=2*(int)((p+1)*s)) {
//						g.drawString("Max", p, o-y-2);
//					}
		}
		long ptime=System.nanoTime();
		// BPSK tuning bar(s) (if available)
		boolean dbar = true;
		g.setColor(Color.CYAN);
		for (int fc=0; dbar; fc++) {
			dbar = false;
			String nm = "FUNcube"+fc+"-bpsk-centre";
			int cb = tryParse(publish.getPublish(nm, null), -1);
			if (cb>0) {
				int tc = (int)((float)cb/s)+off;
				g.drawLine(tc, getHeight(), tc, 0);
				g.drawString(nm+":"+cb, tc+5, getHeight()*5/6);
				dbar = true;
			}
			nm = "FUNcube"+fc+"-bpsk-tune";
			cb = tryParse(publish.getPublish(nm, null), -1);
			if (cb>0) {
				int wd = (adsc.chns<2) ? getWidth() : getWidth()/2;
				int tc = (int)((float)cb/(adsc.rate/2)*(float)wd)+off;
				g.drawLine(tc, getHeight(), tc, 0);
				g.drawString(nm+":"+cb, tc+5, getHeight()*5/6);
				dbar = true;
			}
		}
		long ttime=System.nanoTime();
		// Reticle
		g.setColor(Color.DARK_GRAY);
		g.drawLine(0, 35, getWidth(), 35);
		int my1 = 33;
		int my2 = 37;
		int mxs = getWidth()/20;
		boolean abv = true;
		for (int x=0; x<getWidth()-mxs; x+=mxs) {
			g.drawLine(x, my1, x, my2);
			double _mf;
			if (adsc.chns<2) {
				_mf = adsc.rate/2.0 * (double)x / (double)getWidth();
			} else {
				_mf = (adsc.rate * (double)x / (double)getWidth()) - adsc.rate/2.0;
			}
			g.drawString((int)_mf + "Hz", x, abv ? my1 : my2+12);
			abv = !abv;
		}
		long rtime=System.nanoTime();
		logger.logMsg("fft: render (nsecs) psd/tune/ret: " +
			(ptime-stime) + "/" +
			(ttime-ptime) + "/" +
			(rtime-ttime));
	}
	// Find largest magnitude value in a array from offset o, length l, stride 2
	private double getMax(double[]a, int o, int l) {
		double r = 0;
		for (int i=o; i<o+l; i+=2) {
			if (Math.abs(a[i])>r)
				r=a[i];
		}
		return r;
	}
	// Parse int or return default
	private int tryParse(Object s, int def) {
		try {
			if (s instanceof String)
				return Integer.parseInt((String)s);
		} catch (NumberFormatException e) {
		}
		return def;
	}

	public void receive(ByteBuffer buf) {
		// Convert to array of floats (scaled -1 to 1).. and apply windowing function if required
		int div = 2<<(adsc.bits-1);
		for (int s=0; s<dat.length; s+=2) {
			dat[s] = ((double)(buf.getShort()) / (double)div) * (dow ? win[s/2] : 1);
			if (adsc.chns>1)
				dat[s+1] = ((double)(buf.getShort()) / (double)div) * (dow ? win[s/2] : 1);
			else
				dat[s+1] = 0;
		}
		// Copy to preserve original input
		System.arraycopy(dat, 0, spc, 0, dat.length);
		// FFT
		DoubleFFT_1D fft = new DoubleFFT_1D(spc.length/2);
		fft.complexForward(spc);
		// Calculate power spectral density (PSD)
		double m = 0;
		int p = -1;
		for (int s=0; s<spc.length-1; s+=2) {
			psd[s/2] = Math.sqrt((spc[s]*spc[s]) + (spc[s+1]*spc[s+1]));	// Compute PSD
			if (m<psd[s/2]) {
				m=psd[s/2];
				p = s;
			}
		}
		// convert array index to actual frequency offset
		if (adsc.chns<2)
			p = (p*(int)adsc.rate)/(2*dat.length);
		else
			p = (p*(int)adsc.rate)/dat.length - (int)adsc.rate/2;
		// Stash maxima frequency and size
		psd[psd.length-2] = (double)p;
		psd[psd.length-1] = m;
		// publish for other displays
		publish.setPublish("fft-psd", psd);
		// Skip redraw unless we are visible
		if (isVisible())
			repaint();
	}

	public void hotKey(char c) {
	}
}
