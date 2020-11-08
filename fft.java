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
public class fft extends IUIComponent implements IAudioHandler, IPublishListener, ActionListener {
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
	private double[] psd;
	private double[] win;
	private boolean dow;
	private boolean log;
	private boolean auto;
	private int gain;
	private AudioDescriptor adsc;
	private boolean needpaint = true;
	private Color dgray = new Color(0x1f,0x1f,0x1f);

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
		dat = new double[2*adsc.blen/adsc.size];// always allocate for two channels
		psd = new double[dat.length/2+2];		// add two for spectral maxima values
		win = new double[dat.length/2];
		logger.logMsg("fft: dat.len="+dat.length);
		// Calculate window coefficients (hamming)
		for (int s=0; s<win.length; s++)
			win[s] = (double) (0.54 - 0.46*Math.cos(2*Math.PI*s/win.length));
		// [re]attach ourselves to audio data
		audio.remHandler(this);
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

	protected synchronized void paintComponent(Graphics g) {
		// skip if not visible or doesn't need painting
		if (!isVisible() || !needpaint)
			return;
		// time render
		long stime=System.nanoTime();
		// Clear to black
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, getWidth(), getHeight());
		// Step size and FFT offset for resampling to screen size
		float step = (float)(dat.length/2)/(float)getWidth();
		int off = getWidth()/2;
		// adjust step and offset if only single channel data
		if (adsc.chns<2) {
			step = step/2;
			off = 0;
		}
		g.setColor(Color.DARK_GRAY);
		if (dow)
			g.drawString("step(ham): "+step, 2, 24);
		else
			g.drawString("step(raw): "+step, 2, 24);
		// demod filter (TODO: move to waterfall?)
		int flo = (int)publish.getPublish("demod-filter-low", Integer.MIN_VALUE);
		int fhi = (int)publish.getPublish("demod-filter-high", Integer.MAX_VALUE);
		if (flo > Integer.MIN_VALUE && fhi < Integer.MAX_VALUE) {
			g.setColor(dgray);
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
		float h = (float)(auto ? (log ? (getHeight())/Math.log10(pmax+1.0) : (getHeight())/pmax) : gain);
		g.drawString("gain("+(auto?'A':'-')+"):"+h, getWidth()-100, 12);
		int ly = 0;
		for (int p=0; p<getWidth()-1; p++) {
			// offset and wrap index to display negative freqs, then positives..
			int i = (p+off) % getWidth();
			int y = log ?
				(int)(Math.log10(getMax(psd, (int)(i*step), (int)step)+1.0)*h) :
				(int)(getMax(psd, (int)(i*step), (int)step)*h);
			g.drawLine(p, getHeight()-ly, p+1, getHeight()-y);
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
		needpaint = false;
		logger.logMsg("fft: render (nsecs) psd/tune/ret: " +
			(ptime-stime) + "/" +
			(ttime-ptime) + "/" +
			(rtime-ttime));
	}
	// Find largest magnitude value in a array from offset o, length l
	private double getMax(double[]a, int o, int l) {
		double r = 0;
		for (int i=o; i<o+l; i++) {
			if (Math.abs(a[i])>r)
				r=a[i];
		}
		return r;
	}

	public synchronized void receive(ByteBuffer buf) {
		// Convert to array of floats (scaled -1 to 1).. and apply windowing function if required
		int div = 2<<(adsc.bits-1);
		for (int s=0; s<dat.length; s+=2) {
			dat[s] = ((double)(buf.getShort()) / (double)div) * (dow ? win[s/2] : 1);
			if (adsc.chns>1)
				dat[s+1] = ((double)(buf.getShort()) / (double)div) * (dow ? win[s/2] : 1);
			else
				dat[s+1] = 0;
		}
		// FFT
		DoubleFFT_1D fft = new DoubleFFT_1D(dat.length/2);
		fft.complexForward(dat);
		// Calculate power spectral density (PSD)
		double m = 0;
		int p = -1;
		for (int s=0; s<dat.length-1; s+=2) {
			psd[s/2] = Math.sqrt((dat[s]*dat[s]) + (dat[s+1]*dat[s+1]));	// Compute PSD
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
		psd[psd.length-2] = (double)p;
		psd[psd.length-1] = m;
		// publish for other displays
		publish.setPublish("fft-psd", psd);
		needpaint = true;
	}

	public void hotKey(char c) {
	}
}
