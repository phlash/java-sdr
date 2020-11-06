package com.ashbysoft.java_sdr;

// Demodulate AM or FM from sample stream

// Integrate with spectrum display to allow graphical selection of filter band

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Color;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

@SuppressWarnings("serial")
public class demod extends IUIComponent implements IAudioHandler, IPublishListener, ActionListener, Runnable {

	private static final String CFG_DEMOD_FLOW = "demod-filter-low";
	private static final String CFG_DEMOD_FHGH = "demod-filter-high";
	private static final String CFG_DEMOD_MODE = "demod-mode";
	private static final String CFG_DEMOD_OUTP = "demod-output";
	private static final String CFG_DEMOD_FIRE = "demod-fir-enable";
	private static final String CFG_DEMOD_AGCE = "demod-agc-enable";

	private static final int MODE_OFF = 0;
	private static final int MODE_RAW = 1;
	private static final int MODE_AM  = 2;
	private static final int MODE_FM  = 3;

	private IConfig config;
	private IPublish publish;
	private ILogger logger;
	private IUIHost host;
	private IAudio audio;
	private SourceDataLine sdl;
	private Thread output;
	// demodulation mode & options
	private int mode;
	private boolean dofir, doagc;
	// band-pass frequencies
	private int flo, fhi;
	// sample processing buffer
	private int[] sam;
	// FIR band-pass filter to select a section of the passband
	private int[] fir;
	private int fof;
	private double[] wfir;
	// FIR low-pass filter to remove images after down conversion
	private int[] lpf;
	private int lof;
	private double[] wlpf;
	// down conversion carrier absolute phase & phase increment
	private double car;
	private double phs;
	// demodulated audio buffer
	private ByteBuffer bbf;
	// AGC state and FM demod state
	private int max, avg, li, lq;
	// dirty graphics
	boolean needpaint = true;

	public demod(IConfig cfg, IPublish pub, ILogger log,
		IUIHost hst, IAudio aud) {
		config = cfg;
		publish = pub;
		logger = log;
		host = hst;
		audio = aud;
		publish.listen(this);
		// delay buffers for FIR filters (currently fixed order 20)
		fir = new int[42];
		lpf = new int[42];
		// weights for FIR filters
		wfir = new double[21];
		wlpf = new double[21];
		// initial settings
		mode = cfg.getIntConfig(CFG_DEMOD_MODE, MODE_OFF);
		dofir = cfg.getIntConfig(CFG_DEMOD_FIRE, 0)>0 ? true : false;
		doagc = cfg.getIntConfig(CFG_DEMOD_AGCE, 0)>0 ? true : false;
		// initial filter points
		flo = cfg.getIntConfig(CFG_DEMOD_FLOW, Integer.MIN_VALUE);
		fhi = cfg.getIntConfig(CFG_DEMOD_FHGH, Integer.MAX_VALUE);
		// initial input audio setup
		setup(audio);
		// build menu
		JMenu top = new JMenu("Demod");
		top.setMnemonic(KeyEvent.VK_D);
		JMenuItem item = new JMenuItem("Output Device..", KeyEvent.VK_D);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK));
		item.setActionCommand("demod-dev");
		item.addActionListener(this);
		top.add(item);
		item = new JMenuItem("Mode: Off", KeyEvent.VK_O);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK));
		item.setActionCommand("demod-off");
		item.addActionListener(this);
		top.add(item);
		item = new JMenuItem("Mode: Raw", KeyEvent.VK_R);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK));
		item.setActionCommand("demod-raw");
		item.addActionListener(this);
		top.add(item);
		item = new JMenuItem("Mode: AM", KeyEvent.VK_A);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK));
		item.setActionCommand("demod-am");
		item.addActionListener(this);
		top.add(item);
		item = new JMenuItem("Mode: FM", KeyEvent.VK_F);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK));
		item.setActionCommand("demod-fm");
		item.addActionListener(this);
		top.add(item);
		item = new JMenuItem("AGC On/Off", KeyEvent.VK_G);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK));
		item.setActionCommand("demod-agc");
		item.addActionListener(this);
		top.add(item);
		item = new JMenuItem("FIR On/Off", KeyEvent.VK_F);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
		item.setActionCommand("demod-fir");
		item.addActionListener(this);
		top.add(item);
		host.addMenu(top);
		// audio output thread
		this.output = new Thread(this);
		this.output.start();
		// audio output device
		String dev = cfg.getConfig(CFG_DEMOD_OUTP, "default");
		openAudio(findDev(dev));
	}

	public void notify(String key, Object val) {
		if ("audio-change".equals(key)) {
			setup((IAudio)val);
		}
	}

	public void actionPerformed(ActionEvent e) {
		if ("demod-dev".equals(e.getActionCommand()))
			openDev();
		else if ("demod-off".equals(e.getActionCommand()))
			mode = MODE_OFF;
		else if ("demod-raw".equals(e.getActionCommand()))
			mode = MODE_RAW;
		else if ("demod-am".equals(e.getActionCommand()))
			mode = MODE_AM;
		else if ("demod-fm".equals(e.getActionCommand()))
			mode = MODE_FM;
		else if ("demod-agc".equals(e.getActionCommand()))
			doagc = !doagc;
		else if ("demod-fir".equals(e.getActionCommand()))
			dofir = !dofir;
		config.setIntConfig(CFG_DEMOD_MODE, mode);
		config.setIntConfig(CFG_DEMOD_FIRE, dofir ? 1 : 0);
		config.setIntConfig(CFG_DEMOD_AGCE, doagc ? 1 : 0);
	}

	private synchronized void setup(IAudio aud) {
		// drop output audio if any (buffers about to change)
		if (sdl!=null) {
			SourceDataLine tmp = sdl;
			sdl = null;
			tmp.close();
		}
		audio = aud;
		AudioDescriptor ad = audio.getAudioDescriptor();
		int sbytes = (ad.bits+7)/8;
		// internal sample buffer
		sam = new int[ad.blen/sbytes/ad.chns*2];
		// audio output buffer
		bbf = ByteBuffer.allocate(sam.length*2);
		bbf.order(ByteOrder.LITTLE_ENDIAN);
		// recalculate FIR weights
		weights();
		// [re]attach ourselves to audio data
		audio.remHandler(this);
		audio.addHandler(this);
	}

	private void openDev() {
		// detect output audio devices		
		Mixer.Info[] mxs = AudioSystem.getMixerInfo();
		ArrayList<Mixer.Info> lst = new ArrayList<Mixer.Info>();
		for (int m=0; m<mxs.length; m++) {
			// If we have any source lines, this is an output capable mixer
			if (AudioSystem.getMixer(mxs[m]).getSourceLineInfo().length>0) {
				lst.add(mxs[m]);
			}
		}
		// choose please user..
		Object selected = JOptionPane.showInputDialog(this,
			"Please select an audio device",
			"Open Audio Output",
			JOptionPane.QUESTION_MESSAGE,
			null,
			lst.toArray(new Mixer.Info[lst.size()]),
			null);
		if (selected!=null) {
			openAudio((Mixer.Info)selected);
			config.setConfig(CFG_DEMOD_OUTP, ((Mixer.Info)selected).getName());
		}
	}

	private Mixer.Info findDev(String name) {
		Mixer.Info[] mxs = AudioSystem.getMixerInfo();
		ArrayList<Mixer.Info> lst = new ArrayList<Mixer.Info>();
		for (int m=0; m<mxs.length; m++) {
			if ((mxs[m].getName().indexOf(name)>=0 || mxs[m].getDescription().indexOf(name)>=0) &&
				AudioSystem.getMixer(mxs[m]).getSourceLineInfo().length>0)
				return mxs[m];
		}
		logger.statusMsg("output audio not found: "+name);
		return null;
	}

	private void openAudio(Mixer.Info mix) {
		if (null==mix)
			return;
		SourceDataLine tmp = sdl;
		if (sdl!=null) {
			sdl = null;
			tmp.close();
		}
		// Audio output format retains input sample rate for ease of coding, otherwise S16_le :)
		int rate = audio.getAudioDescriptor().rate;
		AudioFormat fmt = new AudioFormat(
			AudioFormat.Encoding.PCM_SIGNED,
			rate, 16, 2, 4, rate, false
		);
		try {
			tmp = AudioSystem.getSourceDataLine(fmt, mix);
			tmp.open();
			tmp.start();
			sdl = tmp;
			logger.statusMsg("audio out: "+mix);
		} catch (Exception e) {
			logger.statusMsg("audio open failed: "+e.getMessage());
		}
	}

	// generate FIR band-pass filter weights according to:
	// http://www.labbookpages.co.uk/audio/firWindowing.html
	// using the bandpass equation, with a hamming window,
	// then the low-pass image rejection filter weights,
	// and finaly, the down conversion carrier phase shift/sample.
	private void weights() {
		// all-pass?
		if (Integer.MIN_VALUE==flo) {
			for (int i=0; i<wfir.length; i++)
				wlpf[i]=wfir[i]=0;
			wfir[(wfir.length-1)/2]=1;
			wlpf[(wlpf.length-1)/2]=1;
		// band-pass
		} else {
			// normalise frequencies to sample rate
			int rate = audio.getAudioDescriptor().rate;
			double nlo = (double)flo/rate;
			double nhi = (double)fhi/rate;
			double nlp = (double)(fhi-flo)/rate;
			// filter order == length-1
			int ord = wfir.length-1;
			// calculate weights, apply hamming window
			for (int n=0; n<wfir.length; n++) {
				if (n==ord/2) {
					wfir[n]=2*(nhi-nlo);
					wlpf[n]=2*nlp;
				} else {
					wfir[n]=
						 (Math.sin(2*Math.PI*nhi*(double)(n-ord/2))/(Math.PI*(double)(n-ord/2)))
						-(Math.sin(2*Math.PI*nlo*(double)(n-ord/2))/(Math.PI*(double)(n-ord/2)));
					wlpf[n]=
						  Math.sin(2*Math.PI*nlp*(double)(n-ord/2))/(Math.PI*(double)(n-ord/2));
				}
				wfir[n] *= 0.54 - 0.46*Math.cos(2*Math.PI*(double)n/(double)ord);
				wlpf[n] *= 0.54 - 0.46*Math.cos(2*Math.PI*(double)n/(double)ord);
			}
			// calculate phase advance per input sample for down conversion carrier at flo
			phs = 2*Math.PI*nlo;
			car = 0;
		}
		// clear previous samples (if any)
		for (int i=0; i<fir.length; i++)
			lpf[i]=fir[i]=0;
		fof=fir.length-2;
		lof=lpf.length-2;
	}

	// Apply FIR filter to incoming sample stream
	private int filter(int in[], int out[], int[] buf, double[] w, int o) {
		// put the current sample at start of delay buffer
		buf[o]=in[0];
		buf[o+1]=in[1];
		// weight and sum output I/Q
		double oi=0;
		double oq=0;
		for (int i=0; i<buf.length; i+=2) {
			int ti=(o+i)%buf.length;
			oi = oi+buf[ti]*w[i/2];
			oq = oq+buf[ti+1]*w[i/2];
		}
		out[0] = (int)oi;
		out[1] = (int)oq;
		// move back in delay buffer
		o=(o-2);
		if (o<0) o=buf.length-2;
		return o;
	}

	public void receive(ByteBuffer buf) {
		// AM demodulator:
		//   determine AGC factor while measuring input amplitude and averaging it
		//   subtract average from each amplitude , scale for AGC and output in mono
		// FM demodulator (quadrature delay technique):
		//   determine AGC factor while measuring phase rotation rate (inter sample vector product)
		//   apply AGC to measured phase rotation rate and output in mono (so far!)
		max = 1;
		avg = 0;
		AudioDescriptor ad = audio.getAudioDescriptor();
		for(int s=0; s<sam.length; s+=2) {
			sam[s] = buf.getShort();
			if (ad.chns>1)
				sam[s+1] = buf.getShort();
			else
				sam[s+1] = 0;
			// apply selection filter?
			if (dofir) {
				//int[] sh = { 0, 0 };
				//complex_gen(sh, mod);
				int[] fs = { sam[s], sam[s+1] };
				int[] os = { 0, 0 };
				//complex_mod(fs, sh, os);
				fof=filter(fs, os, fir, wfir, fof);
				// calculate immediate values of carrier I/Q from phase, advance carrier
				double ci = Math.cos(car);
				double cq = Math.sin(car);
				car += phs;
				if (car > 2*Math.PI)
					car -= 2*Math.PI;
				// complex multiply to mix carrier and signal: (a+ib).(c+id) = ((ac-bd)+i(ad+bc))
				fs[0]=(int)(os[0]*ci-os[1]*cq);
				fs[1]=(int)(os[0]*cq+os[1]*ci);
				// LPF to remove image from product
				lof=filter(fs, os, lpf, wlpf, lof);
				sam[s]=os[0];
				sam[s+1]=os[1];
			}
			// Demodulate
			// ..Off
			if (MODE_OFF==mode) {
				// silence generator
				sam[s] = sam[s+1] = 0;
			// ..Raw
			} else if (MODE_RAW==mode) {
				; // do nothing, as expected for raw pass through..
			// ..AM
			} else if (MODE_AM==mode) {
				// measure amplitude of sample, update running average
				sam[s] = (int)Math.sqrt(sam[s]*sam[s]+sam[s+1]*sam[s+1]);
				avg = ((s/2)*avg+sam[s])/(s/2+1); 
			// ..FM
			} else if (MODE_FM==mode){
				// http://kom.aau.dk/group/05gr506/report/node10.html#SECTION04615000000000000000
				int v = (li*sam[s+1])-(lq*sam[s]); 
				li = sam[s];
				lq = sam[s+1];
				sam[s] = v;
			}
			max = Math.max(max, Math.abs(sam[s]));
		}
		// fix up maximum for AM
		if (MODE_AM==mode) {
			max -= avg;
		}
		// Write audio buffer, apply AGC if enabled
		bbf.clear();
		for (int s=0; s<sam.length; s+=2) {
			sam[s] = (MODE_AM==mode ? sam[s]-avg : sam[s]) * (doagc ? 8192/max : 1);
			short v = (short) sam[s];
			// Left
			bbf.putShort(v);
			// right
			bbf.putShort(v);
		}
		needpaint = true;
	}

	public void hotKey(char c) {
		if ('b'==c) {
			dofir = !dofir;
		} else if ('n'==c) {
			flo = +1000;
			fhi = +2000;
		} else if ('w'==c) {
			flo = 0;
			fhi = +10000;
		} else if ('r'==c) {
			flo = fhi = Integer.MIN_VALUE;
		} else if ('a'==c) {
			doagc = !doagc;
		}
		weights();
		publish.setPublish(CFG_DEMOD_FLOW, this.flo);
		publish.setPublish(CFG_DEMOD_FHGH, this.fhi);
	}

	// Output audio pump - blocks in write, may over or underrun input resulting in repeat/skip
	// of buffers - we'll live with that for now.
	public void run() {
		while (output!=null) {
			if (sdl!=null) {
				byte[] tmp = bbf.array();
				sdl.write(tmp, 0, tmp.length);
			} else {
				try { Thread.sleep(100); } catch (Exception e) {}
			}
		}
	}

	private static final String[] s_mode = { "Off", "Raw", "AM", "FM" };
	public void paintComponent(Graphics g) {
		// render time
		long stime=System.nanoTime();
		// render audio waveform buffer
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, getWidth(), getHeight());
		g.setColor(Color.GRAY);
		double scale = (sam.length/2)/getWidth();
		int ly=getHeight()/2;
		g.drawString("mode: "+s_mode[mode]+ ", scale: "+scale + ", agc: " + doagc + ", fir: "+dofir + ", filter("+flo+","+fhi+") max: "+max + " avg: " + avg, 2, 12);
		g.setColor(Color.MAGENTA);
		for (int x=0; x<getWidth()-1; x++) {
			int i = (int)((double)x*scale);
			int y = getHeight()/2 - getMax(sam, i*2, (int)scale)*getHeight()/16384;
			g.drawLine(x, ly, x+1, y);
			ly = y;
		}
		long atime=System.nanoTime();
		// show filter weights..
		g.setColor(Color.RED);
		scale = (double)getWidth() / (double)21;
		int lx=0;
		ly=getHeight()/2;
		for (int n=0; n<wfir.length; n++) {
			int x = (int)((double)n*scale);
			int y = getHeight()/2 - (int)(wfir[n]*(double)getHeight()/2);
			g.drawLine(lx, ly, x, y);
			g.drawString("("+n+","+String.format("%04f", wfir[n])+")", x, (n%2)==0 ? y-10 : y+10);
			lx = x;
			ly = y;
		}
		long etime=System.nanoTime();
		logger.logMsg("demod render (nsecs) aud/fil: " + (atime-stime) + "/" + (etime-atime));
	}

	// Find largest magnitude value in a array from offset o, length l
	private int getMax(int[] a, int o, int l) {
		int r = 0;
		for (int i=o; i<o+l; i+=2) { // special step by two because this is demodulated IQ samples..
			if (Math.abs(a[i])>r)
				r=a[i];
		}
		return r;
	}
}
