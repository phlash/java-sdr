// BPSK demodulator targeted at FUNcube telemetry downlink

// This source is released under the terms of the Creative Commons
// Non-Commercial Share Alike license:
// http://creativecommons.org/licenses/by-nc-sa/3.0/

// Author: Phil Ashby, based on previous work by Howard Long (G6LVB)
// and Duncan Hills.

package com.ashbysoft.java_sdr;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

public class FUNcubeBPSKDemod extends IUIComponent implements IPublishListener, IAudioHandler, ActionListener {

	private static final int DOWN_SAMPLE_FILTER_SIZE = 27;
	private static final double[] dsFilter = {
		-6.103515625000e-004F,  /* filter tap #    0 */
		-1.220703125000e-004F,  /* filter tap #    1 */
		+2.380371093750e-003F,  /* filter tap #    2 */
		+6.164550781250e-003F,  /* filter tap #    3 */
		+7.324218750000e-003F,  /* filter tap #    4 */
		+7.629394531250e-004F,  /* filter tap #    5 */
		-1.464843750000e-002F,  /* filter tap #    6 */
		-3.112792968750e-002F,  /* filter tap #    7 */
		-3.225708007813e-002F,  /* filter tap #    8 */
		-1.617431640625e-003F,  /* filter tap #    9 */
		+6.463623046875e-002F,  /* filter tap #   10 */
		+1.502380371094e-001F,  /* filter tap #   11 */
		+2.231445312500e-001F,  /* filter tap #   12 */
		+2.518310546875e-001F,  /* filter tap #   13 */
		+2.231445312500e-001F,  /* filter tap #   14 */
		+1.502380371094e-001F,  /* filter tap #   15 */
		+6.463623046875e-002F,  /* filter tap #   16 */
		-1.617431640625e-003F,  /* filter tap #   17 */
		-3.225708007813e-002F,  /* filter tap #   18 */
		-3.112792968750e-002F,  /* filter tap #   19 */
		-1.464843750000e-002F,  /* filter tap #   20 */
		+7.629394531250e-004F,  /* filter tap #   21 */
		+7.324218750000e-003F,  /* filter tap #   22 */
		+6.164550781250e-003F,  /* filter tap #   23 */
		+2.380371093750e-003F,  /* filter tap #   24 */
		-1.220703125000e-004F,  /* filter tap #   25 */
		-6.103515625000e-004F   /* filter tap #   26 */
	};
	private static final double DOWN_SAMPLE_MULT = 0.9*32767.0;	// XXX: Voodoo from Howard?
	private static final int MATCHED_FILTER_SIZE = 65;
	private static final double[] dmFilter = {
		-0.0101130691F,-0.0086975143F,-0.0038246093F,+0.0033563764F,+0.0107237026F,+0.0157790936F,+0.0164594107F,+0.0119213911F,
		+0.0030315224F,-0.0076488191F,-0.0164594107F,-0.0197184277F,-0.0150109226F,-0.0023082460F,+0.0154712381F,+0.0327423589F,
		+0.0424493086F,+0.0379940454F,+0.0154712381F,-0.0243701991F,-0.0750320094F,-0.1244834076F,-0.1568500423F,-0.1553748911F,
		-0.1061032953F,-0.0015013786F,+0.1568500423F,+0.3572048240F,+0.5786381191F,+0.7940228249F,+0.9744923010F,+1.0945250059F,
		+1.1366117829F,+1.0945250059F,+0.9744923010F,+0.7940228249F,+0.5786381191F,+0.3572048240F,+0.1568500423F,-0.0015013786F,
		-0.1061032953F,-0.1553748911F,-0.1568500423F,-0.1244834076F,-0.0750320094F,-0.0243701991F,+0.0154712381F,+0.0379940454F,
		+0.0424493086F,+0.0327423589F,+0.0154712381F,-0.0023082460F,-0.0150109226F,-0.0197184277F,-0.0164594107F,-0.0076488191F,
		+0.0030315224F,+0.0119213911F,+0.0164594107F,+0.0157790936F,+0.0107237026F,+0.0033563764F,-0.0038246093F,-0.0086975143F,
		-0.0101130691F,
		-0.0101130691F,-0.0086975143F,-0.0038246093F,+0.0033563764F,+0.0107237026F,+0.0157790936F,+0.0164594107F,+0.0119213911F,
		+0.0030315224F,-0.0076488191F,-0.0164594107F,-0.0197184277F,-0.0150109226F,-0.0023082460F,+0.0154712381F,+0.0327423589F,
		+0.0424493086F,+0.0379940454F,+0.0154712381F,-0.0243701991F,-0.0750320094F,-0.1244834076F,-0.1568500423F,-0.1553748911F,
		-0.1061032953F,-0.0015013786F,+0.1568500423F,+0.3572048240F,+0.5786381191F,+0.7940228249F,+0.9744923010F,+1.0945250059F,
		+1.1366117829F,+1.0945250059F,+0.9744923010F,+0.7940228249F,+0.5786381191F,+0.3572048240F,+0.1568500423F,-0.0015013786F,
		-0.1061032953F,-0.1553748911F,-0.1568500423F,-0.1244834076F,-0.0750320094F,-0.0243701991F,+0.0154712381F,+0.0379940454F,
		+0.0424493086F,+0.0327423589F,+0.0154712381F,-0.0023082460F,-0.0150109226F,-0.0197184277F,-0.0164594107F,-0.0076488191F,
		+0.0030315224F,+0.0119213911F,+0.0164594107F,+0.0157790936F,+0.0107237026F,+0.0033563764F,-0.0038246093F,-0.0086975143F,
		-0.0101130691F
	};
	private static final int SYNC_VECTOR_SIZE = 65;
	private static final byte[] SYNC_VECTOR = {	// 65 symbol known pattern
		1,1,1,1,1,1,1,-1,-1,-1,-1,1,1,1,-1,1,1,1,1,-1,-1,1,-1,1,1,-1,-1,1,-1,-1,1,-1,-1,-1,-1,-1,-1,1,-1,-1,-1,1,-1,-1,1,1,-1,-1,-1,1,-1,1,1,1,-1,1,-1,1,1,-1,1,1,-1,-1,-1
	};
	private static final int FEC_BITS_SIZE = 5200;
	private static final int FEC_BLOCK_SIZE = 256;
	private static final double RX_CARRIER_FREQ = 1200.0;
	private static final int DOWN_SAMPLE_RATE = 9600;
	private static final int BIT_RATE = 1200;
	private static final int SAMPLES_PER_BIT = DOWN_SAMPLE_RATE/BIT_RATE;
	private static final double VCO_PHASE_INC = 2.0*Math.PI*RX_CARRIER_FREQ/(double)DOWN_SAMPLE_RATE;
	private static final double BIT_SMOOTH1 = 1.0/200.0;
	private static final double BIT_SMOOTH2 = 1.0/800.0;
	private static final double BIT_PHASE_INC = 1.0/(double)DOWN_SAMPLE_RATE;
	private static final double BIT_TIME = 1.0/(double)BIT_RATE;
	private static final int SINCOS_SIZE = 256;
	private double[] sinTab = new double[SINCOS_SIZE];
	private double[] cosTab = new double[SINCOS_SIZE];

	private static final String CFG_TUNING = "bpsk-tuning";
	private static final String CFG_DOFFT = "bpsk-dofft";
	private static final String CFG_UPPER = "bpsk-upper";

	private String name;
	private IUIHost host;
	private IConfig config;
	private IPublish publish;
	private ILogger logger;
	private AudioDescriptor adsc;
	private int samples;
	private boolean doFFT = false;
	private boolean doUp = false;
	private boolean decodeOK = false;
	private byte[] decoded = new byte[FEC_BLOCK_SIZE];
	private FECDecoder decoder = new FECDecoder();
	private double tuning, tuPhaseInc;
	private int cntRaw, cntDS, cntBit, cntFEC, cntDec, dmErrBits;
	private double energy1, energy2;

	// debugging stuff
	private double[] tuned;
	private int tunedIdx;
	private double[] downSmpl;
	private int downSmplIdx;
	private double[] demodIQ;
	private int[] demodBits;
	private double[] demodRe;
	private int demodIdx, demodLastBit;

	public FUNcubeBPSKDemod(int idx, IConfig cfg, IPublish pub, ILogger log,
		IUIHost hst, IAudio aud) {
		this.name = "FUNcube"+idx;
		this.host = hst;
		this.config = cfg;
		this.publish = pub;
		this.logger = log;

		JMenu top = new JMenu(name);
		top.setMnemonic(KeyEvent.VK_0+idx);
		JMenuItem item = new JMenuItem("BPSK Frequency...", KeyEvent.VK_F);
		item.setActionCommand("bpsk-freq");
		item.addActionListener(this);
		top.add(item);
		item = new JMenuItem("Freq: +10Hz", KeyEvent.VK_EQUALS);
		item.setActionCommand("bpsk-plus10");
		item.addActionListener(this);
		top.add(item);
		item = new JMenuItem("Freq: -10Hz", KeyEvent.VK_MINUS);
		item.setActionCommand("bpsk-sub10");
		item.addActionListener(this);
		top.add(item);
		item = new JMenuItem("FFT/Tune Switch", KeyEvent.VK_T);
		item.setActionCommand("bpsk-fft-tune");
		item.addActionListener(this);
		top.add(item);
		item = new JMenuItem("Track high On/Off", KeyEvent.VK_H);
		item.setActionCommand("bpsk-high");
		item.addActionListener(this);
		top.add(item);
		host.addMenu(top);

		for (int n=0; n<SINCOS_SIZE; n++) {
			sinTab[n] = Math.sin(n*2.0*Math.PI/SINCOS_SIZE);
			cosTab[n] = Math.cos(n*2.0*Math.PI/SINCOS_SIZE);
		}
		setup(aud);
		publish.listen(this);
	}

	public void notify(String key, Object val) {
		if("audio-change".equals(key) && 
			val instanceof IAudio)
			setup((IAudio)val);
	}

	public void actionPerformed(ActionEvent e) {
		if ("bpsk-freq".equals(e.getActionCommand())) {
			tuning = freqDialog();
		} else if ("bpsk-plus10".equals(e.getActionCommand())) {
			tuning += 10.0;
		} else if ("bpsk-sub10".equals(e.getActionCommand())) {
			tuning -= 10.0;
		} else if ("bpsk-fft-tune".equals(e.getActionCommand())) {
			doFFT = !doFFT;
			config.setIntConfig(name+"-"+CFG_DOFFT, doFFT ? 1 : 0);
		} else if ("bpsk-high".equals(e.getActionCommand())) {
			doUp = !doUp;
			config.setIntConfig(name+"-"+CFG_UPPER, doUp ? 1 : 0);
		}
		config.setIntConfig(name+"-"+CFG_TUNING, (int)tuning);
		tuPhaseInc = 2.0*Math.PI*tuning/(double)adsc.rate;
		dmMaxCorr=0;
	}

	private void setup(IAudio audio) {
		this.adsc = audio.getAudioDescriptor();
		this.samples=adsc.blen/adsc.size;
		tuning = (double)config.getIntConfig(name+"-"+CFG_TUNING, 12000);
		tuPhaseInc = 2.0*Math.PI*tuning/(double)adsc.rate;
		tuned = new double[samples*2];
		tunedIdx = 0;
		doFFT = 0!=config.getIntConfig(name+"-"+CFG_DOFFT, 0);
		doUp = 0!=config.getIntConfig(name+"-"+CFG_UPPER, 0);
		demodIQ = new double[samples*DOWN_SAMPLE_RATE/adsc.rate*2];
		demodIdx = 0;
		downSmpl = new double[demodIQ.length];
		downSmplIdx= 0;
		demodBits = new int[demodIQ.length/2];
		demodRe = new double[demodIQ.length/2];
		audio.remHandler(this);
		audio.addHandler(this);
	}

	@Override
	protected void paintComponent(Graphics g) {
		// render time
		long stime=System.nanoTime();
		// erase bg
		g.setColor(Color.BLACK);
		g.fillRect(0,0, getWidth(), getHeight());
		// stats (text)
		g.setColor(Color.GREEN);
		g.drawString("decodeOK="+decodeOK+" dmErrBits="+dmErrBits+" raw="+cntRaw+" ds="+cntDS+" bit="+cntBit+" fec="+cntFEC+" dec="+cntDec, 10, 20);
		if (doFFT)
			g.drawString("centreBin="+centreBin, getWidth()-250, 20);
		else
			g.drawString("tuning="+(int)tuning, getWidth()-250, 20);
		g.drawString("e1="+energy1, getWidth()-250, 36);
		g.drawString("e2="+energy2, getWidth()-250, 52);
		g.drawString("eO="+dmEnergyOut, getWidth()-250, 68);
		g.drawString("com="+dmMaxCorr+" co="+dmCorr, getWidth()-250, 84);
		long ttime=System.nanoTime();

		// vector and linear plots of baseband IQ values.. plot of demodulated bits
		double mi = 0.0, mq = 0.0;
		for (int n=0; n<demodIQ.length; n+=2) {
			if (mi<Math.abs(demodIQ[n]))
				mi=Math.abs(demodIQ[n]);
			if (mq<Math.abs(demodIQ[n+1]))
				mq=Math.abs(demodIQ[n+1]);
		}
		double xs = 100.0/mi, ys = 100.0/mq;
		g.setColor(Color.WHITE);
		g.drawRect(1, getHeight()-201, 200, 200);
		int xo = getWidth()-demodIQ.length/2-1;
		g.drawRect(xo-1, getHeight()-403, demodIQ.length/2, 200);
		int lx=101, ly=getHeight()-101;
		for (int n=0; n<demodIQ.length; n+=2) {
			g.setColor(Color.YELLOW);
			int x = 101+(int)(demodIQ[n]*xs);
			int y = getHeight()-101-(int)(demodIQ[n+1]*ys);
			g.drawLine(lx, ly, x, y);
			lx = x;
			ly = y;
			if (n>1) {
				g.setColor(Color.RED);
				g.drawLine(xo+n/2, getHeight()-301-(int)(demodIQ[n-2]*xs), xo+n/2+1, getHeight()-301-(int)(demodIQ[n]*xs));
				g.setColor(Color.BLUE);
				g.drawLine(xo+n/2, getHeight()-301-(int)(demodIQ[n-1]*xs), xo+n/2+1, getHeight()-301-(int)(demodIQ[n+1]*xs));
			}
		}
		g.setColor(Color.GRAY);
		double rs = 50.0/(mi*mq);
		for (int n=1; n<demodRe.length; n++) {
			g.drawLine(xo+n, getHeight()-301-(int)(demodRe[n-1]*rs), xo+n+1, getHeight()-301-(int)(demodRe[n]*rs));
		}
		g.setColor(Color.GREEN);
		for (int n=1; n<demodBits.length; n++) {
			g.drawLine(xo+n, getHeight()-301-(demodBits[n-1]*50), xo+n+1, getHeight()-301-(demodBits[n]*50));
		}
		g.drawString("len="+demodIQ.length/2+" mi="+((int)(mi*10.0)/10.0)+" mq="+((int)(mq*10.0)/10.0), xo, getHeight()-205);
		long iqtime=System.nanoTime();
		// fft of tuned signal
		DoubleFFT_1D fft = new DoubleFFT_1D(tuned.length/2);
		fft.complexForward(tuned);
		double[] psd = new double[tuned.length/2];
		double max = 0.0;
		int mo = 0;
		for (int n=0; n<tuned.length; n+=2) {
			psd[n/2] = Math.sqrt(tuned[n]*tuned[n]+tuned[n+1]*tuned[n+1]);
			if (max<psd[n/2]) {
				max=psd[n/2];
				mo = n/2;
			}
		}
		long ftime=System.nanoTime();
		xo = 9+SINCOS_SIZE+10;
		double pi = (double)(psd.length/2)/(double)(getWidth()-xo);
		g.setColor(Color.WHITE);
		g.drawRect(xo-1, getHeight()-203, getWidth()-xo+1, 202);
		g.setColor(Color.GRAY);
		for (int n=0; n<getWidth()-xo; n+=(getWidth()-xo)/48) {		// assumes 48kHz positive frequency range
			g.drawLine(xo+n, getHeight()-1, xo+n, getHeight()-202);
		}
		g.setColor(Color.GREEN);
		ys = 100.0/max;
		ly = (int)(psd[0]*ys);
		for (int n=1; n<getWidth()-xo; n++) {
			int i = (int)(pi*(double)n);
			int y = (int)(getMax(psd,i,(int)pi)*ys);
			g.drawLine(xo+n-1, getHeight()-1-ly, xo+n, getHeight()-1-y);
			ly = y;
		}
		g.drawString("samples="+samples+" pi="+((int)(10.0*pi)/10.0)+" psd="+((int)(max*10.0)/10.0)+"@"+mo, xo+5, getHeight()-190);
		long ptime=System.nanoTime();

		// fft of downsampled signal
		fft = new DoubleFFT_1D(downSmpl.length/2);
		fft.complexForward(downSmpl);
		psd = new double[downSmpl.length/2];
		max = 0.0;
		mo = 0;
		for (int n=0; n<downSmpl.length; n+=2) {
			psd[n/2] = Math.sqrt(downSmpl[n]*downSmpl[n]+downSmpl[n+1]*downSmpl[n+1]);
			if (max<psd[n/2]) {
				max=psd[n/2];
				mo = n/2;
			}
		}
		pi = (double)(psd.length/2)/(double)(getWidth()-xo);
		g.setColor(Color.CYAN);
		ys = 100.0/max;
		ly = (int)(psd[0]*ys);
		for (int n=1; n<getWidth()-xo; n++) {
			int i = (int)(pi*(double)n);
			int y = (int)(getMax(psd,i,(int)pi)*ys);
			g.drawLine(xo+n-1, getHeight()-1-ly, xo+n, getHeight()-1-y);
			ly = y;
		}
		g.drawString("downS="+downSmpl.length/2+" pi="+((int)(10.0*pi)/10.0)+" psd="+((int)(max*10.0)/10.0)+"@"+mo, xo+5, getHeight()-170);
		long dtime=System.nanoTime();

		// decoded bytes (oh yeah..)
		if (decodeOK) {
			for (int n=0; n<FEC_BLOCK_SIZE; n+=16) {
				for (int l=0; l<16 && n+l<FEC_BLOCK_SIZE; l++) {
					g.drawString(String.format("%02x ", decoded[n+l] & 0xff), 10+(20*l), 40+n);
				}
			}
		}
		long etime=System.nanoTime();
		logger.logMsg("FUN render (nsecs) txt/iq/fft/psd/dwn/dec: " +
			(ttime-stime) + "/" +
			(iqtime-ttime) + "/" +
			(ftime-iqtime) + "/" +
			(ptime-ftime) + "/" +
			(dtime-ptime) + "/" +
			(etime-dtime));
	}
	
	private double getMax(double[] d, int o, int l) {
		double m = d[o];
		for (int i=o+1; i<o+l; i++) {
			if (m<d[i])
				m=d[i];
		}
		return m;
	}

	@Override
	public void receive(float[] buf) {
		if (doFFT)
			doBufferFFT(buf);
		else
			doBufferTune(buf);
		repaint();
	}

	private void doBufferTune(float[] buf) {
		for (int n=0; n<demodBits.length; n++) {
			demodBits[n]=0;
			demodRe[n]=0.0;
		}
		for (int n=0; n<samples; n++) {
			double i = (double)buf[n*2];
			double q = (double)buf[n*2+1];
			// Software tuning..
			RxMixTuner(i, q);
		}
		publish.setPublish(name+"-bpsk-centre", -1);
		publish.setPublish(name+"-bpsk-tune", (int)tuning);
	}

	private double tuPhase = 0.0;
	private void RxMixTuner(double i, double q) {
		// advance phase of tuning frequency
		tuPhase+=tuPhaseInc;
		if (tuPhase>2.0*Math.PI)
			tuPhase-=2.0*Math.PI;
		// mix with input signal (unless negative)
		if (tuPhase>0.0) {
			double mi=i*cosTab[(int)(tuPhase*(double)SINCOS_SIZE/(2.0*Math.PI))%SINCOS_SIZE];
			double mq=q*sinTab[(int)(tuPhase*(double)SINCOS_SIZE/(2.0*Math.PI))%SINCOS_SIZE];

			// down sample and feed demodulator
			RxDownSample(mi, mq);
		} else {
			RxDownSample(i, q);
		}
	}

	private static final double CFREQ_INV_AVERAGE_FACTOR = 1.0F - (2.0F / (1+1));
	private static final double CFREQ_AVERAGE_FACTOR = 2.0F / (1+1);
	private static final double PSD_INV_AVERAGE_FACTOR = 1.0F - (2.0F / (10+1));
	private static final double PSD_AVERAGE_FACTOR = 2.0F / (10+1);
	private double avePeakPower = 0.0;
	private double aveCentreBin = 0.0;
	private int centreBin = 0;
	private void doBufferFFT(float[] buf) {
		// Forward + inverse FFT solution (as per C++ code)
		for (int n=0; n<demodBits.length; n++) {
			demodBits[n]=0;
			demodRe[n]=0.0;
		}
		double[] fftFwd = new double[samples*2];
		double[] fftRev = new double[samples*2];
		double[] psd = new double[samples];
		double[] avePsd = new double[samples];
		for (int n=0; n<samples; n++) {
			fftFwd[2*n] = (double)buf[n*2];
			fftFwd[2*n+1]=(double)buf[n*2+1];
			fftRev[2*n] = 0.0;
			fftRev[2*n+1]=0.0;
		}
		DoubleFFT_1D fft = new DoubleFFT_1D(samples);
		fft.complexForward(fftFwd);
		// search forward fft for maximum power point, with copious averaging
		for(int i=0;i<samples/2;i++) {
			psd[i] = Math.sqrt(fftFwd[2*i]*fftFwd[2*i]+fftFwd[2*i+1]*fftFwd[2*i+1]);
		}
		double maxBin = 0.0;
		int binPos = -1;
		int beg = doUp ? samples/4 : 0;
		int end = doUp ? samples/2 : samples/4;
		avePsd[0]=0;
		for(int i=beg+75;i<end-75;i++) {	
			avePsd[i]=0;
			for(int j=i-50;j<i+50;j++) {
				avePsd[i] += psd[j];
			}		
	
			if(maxBin<avePsd[i]) {
				maxBin = avePsd[i];
				binPos = i;
			}
		}
		if (centreBin<0) centreBin=0;
		if (centreBin>end-1) centreBin=end-1;
		avePeakPower=(PSD_AVERAGE_FACTOR * avePsd[centreBin]) + (PSD_INV_AVERAGE_FACTOR * avePeakPower);
		if(maxBin>(avePeakPower/4)*5 && binPos>0)
		{
			aveCentreBin = (CFREQ_AVERAGE_FACTOR * (float)binPos) + (CFREQ_INV_AVERAGE_FACTOR * aveCentreBin);
			centreBin = (int)(aveCentreBin+1.0F);
		}
		// clamp centreBin above 102..
		if (centreBin<102) centreBin = 102;
		// publish for other modules
		publish.setPublish(name+"-bpsk-tune", -1);
		publish.setPublish(name+"-bpsk-centre", centreBin);
		// reverse fft around peak power point..
		System.arraycopy(fftFwd,2*(centreBin-102),fftRev,0,2*204);
		fft.complexInverse(fftRev, true);
		// feed downsampler..
		for (int i=0; i<samples; i++) {
			RxDownSample(fftRev[2*i], fftRev[2*i]);	// yes it drops Q component..
		}
	}

	// Down sample from input rate to DOWN_SAMPLE_RATE and low pass filter
	private double[][] dsBuf = new double[DOWN_SAMPLE_FILTER_SIZE][2];
	private int dsPos = DOWN_SAMPLE_FILTER_SIZE-1, dsCnt = 0;
	private double HOWARD_FUDGE_FACTOR = 0.9 * 32768.0;
	private void RxDownSample(double i, double q) {
		tuned[tunedIdx]=i;
		tuned[tunedIdx+1]=q;
		tunedIdx = (tunedIdx+2) % tuned.length;
		dsBuf[dsPos][0]=i;
		dsBuf[dsPos][1]=q;
		if (++dsCnt>=adsc.rate/DOWN_SAMPLE_RATE) {	// typically 96000/9600
			double fi = 0.0, fq = 0.0;
			// apply low pass FIR
			for (int n=0; n<DOWN_SAMPLE_FILTER_SIZE; n++) {
				int dsi = (n+dsPos)%DOWN_SAMPLE_FILTER_SIZE; 
				fi+=dsBuf[dsi][0]*dsFilter[n];
				fq+=dsBuf[dsi][1]*dsFilter[n];
			}
			dsCnt=0;
			// feed down sampled values to demodulator
			RxDemodulate(fi * HOWARD_FUDGE_FACTOR, fq * HOWARD_FUDGE_FACTOR);
		}
		dsPos--;
		if (dsPos<0)
			dsPos=DOWN_SAMPLE_FILTER_SIZE-1;
		cntRaw++;
	}
	
	private double vcoPhase = 0.0;
	private double[][] dmBuf = new double[MATCHED_FILTER_SIZE][2];
	private int dmPos = MATCHED_FILTER_SIZE-1;
	private double[] dmEnergy = new double[SAMPLES_PER_BIT+2];
	private int dmBitPos = 0, dmPeakPos = 0, dmNewPeak = 0, dmCorr = 0, dmMaxCorr = 0;
	private double dmEnergyOut = 1.0;
	private int[] dmHalfTable = {4,5,6,7,0,1,2,3};
	private double dmBitPhase = 0.0;
	private double[] dmLastIQ = new double[2];
	private byte[] dmFECCorr = new byte[FEC_BITS_SIZE];
	private byte[] dmFECBits = new byte[FEC_BITS_SIZE];
	private void RxDemodulate(double i, double q) {
		// debugging
		downSmpl[downSmplIdx]=i;
		downSmpl[downSmplIdx+1]=q;
		downSmplIdx=(downSmplIdx+2)%downSmpl.length;
		// advance phase of VCO, wrap at 2*Pi
		vcoPhase += VCO_PHASE_INC;
		if (vcoPhase > 2.0*Math.PI)
			vcoPhase -= 2.0*Math.PI;
		// quadrature demodulate carrier to base band with VCO, store in FIR buffer
		dmBuf[dmPos][0]=i*cosTab[(int)(vcoPhase*(double)SINCOS_SIZE/(2.0*Math.PI))%SINCOS_SIZE];
		dmBuf[dmPos][1]=q*sinTab[(int)(vcoPhase*(double)SINCOS_SIZE/(2.0*Math.PI))%SINCOS_SIZE];
		// apply FIR (base band smoothing, root raised cosine)
		double fi = 0.0, fq = 0.0;
		for (int n=0; n<MATCHED_FILTER_SIZE; n++) {
			int dmi = (MATCHED_FILTER_SIZE-dmPos+n);
			fi+=dmBuf[n][0]*dmFilter[dmi];
			fq+=dmBuf[n][1]*dmFilter[dmi];
		}
		dmPos--;
		if (dmPos<0)
			dmPos=MATCHED_FILTER_SIZE-1;

		// debug IQ data
		demodIdx = (demodIdx+2)%demodIQ.length;
		demodIQ[demodIdx]=fi;
		demodIQ[demodIdx+1]=fq;

		// store smoothed bit energy
		energy1 = fi*fi+fq*fq;
		dmEnergy[dmBitPos] = (dmEnergy[dmBitPos]*(1.0-BIT_SMOOTH1))+(energy1*BIT_SMOOTH1);
		// at peak bit energy? decode 
		if (dmBitPos==dmPeakPos) {
			dmEnergyOut = (dmEnergyOut*(1.0-BIT_SMOOTH2))+(energy1*BIT_SMOOTH2);
			double di = -(dmLastIQ[0]*fi + dmLastIQ[1]*fq);
			double dq = dmLastIQ[0]*fq - dmLastIQ[1]*fi;
			dmLastIQ[0]=fi;
			dmLastIQ[1]=fq;
			energy2 = Math.sqrt(di*di+dq*dq);
			if (energy2>100.0) {	// TODO: work out where these magic numbers come from!
				boolean bit = di<0.0;	// is that a 1 or 0?
				// debug bit
				//for (int bi=demodLastBit; bi!=demodIdx; bi=(bi+1)%demodBits.length)
				//	demodBits[bi] = demodBits[demodLastBit];
				demodRe[demodIdx/2] = di;
				demodBits[demodIdx/2]=bit ? 1 : -1;
				demodLastBit=demodIdx/2;
				// copy bit into rolling buffer of FEC bits
				System.arraycopy(dmFECCorr,1,dmFECCorr,0,dmFECCorr.length-1);
				dmFECCorr[dmFECCorr.length-1] = (byte)(bit ? 1: -1);
				// detect sync vector by correlation
				dmCorr = 0;
				for (int n=0; n<SYNC_VECTOR_SIZE; n++) {
					dmCorr+=dmFECCorr[n*80]*SYNC_VECTOR[n];
				}
				if (dmCorr>=45) {
					// good correlation, attempt full FEC decode
					for (int n=0; n<FEC_BITS_SIZE; n++) {
						dmFECBits[n] = (byte)(dmFECCorr[n]==1 ? 0xc0 : 0x40);
					}
					dmErrBits=decoder.FECDecode(dmFECBits, decoded);
					cntFEC++;
					dmMaxCorr=0;
					decodeOK = dmErrBits<0 ? false : true;
					cntDec += (decodeOK ? 1 : 0);
				}
				if (dmCorr > dmMaxCorr)
					dmMaxCorr=dmCorr;
				cntBit++;
			}
		}
		// half-way into next bit? reset peak energy point
		if (dmBitPos==dmHalfTable[dmPeakPos])
			dmPeakPos = dmNewPeak;
		dmBitPos = (dmBitPos+1) % SAMPLES_PER_BIT;
		// advance phase of bit position
		dmBitPhase += BIT_PHASE_INC;
		if (dmBitPhase>=BIT_TIME) {
			dmBitPhase-=BIT_TIME;
			dmBitPos=0;	// TODO: Is this a kludge?
			// rolled round another bit, measure new peak energy position
			double eMax = -1.0e10F;
			for (int n=0; n<SAMPLES_PER_BIT; n++) {
				if (dmEnergy[n]>eMax) {
					dmNewPeak=n;
					eMax=dmEnergy[n];
				}
			}
		}
		cntDS++;
	}

	/* Costas loop demodulator..
	private double vcoPhase=0.0;
	private double vcoPhaseInc=VCO_PHASE_INC;
	private double dmbuf[MATCHED_FILTER_SIZE][2];
	private void RxDemodulate(double i, double q) {
		// debugging
		downSmpl[downSmplIdx]=i;
		downSmpl[downSmplIdx+1]=q;
		downSmplIdx=(downSmplIdx+2)%downSmpl.length;
		// advance phase of VCO, wrap at 2*Pi
		vcoPhase += vcoPhaseInc;
		if (vcoPhase > 2.0*Math.PI)
			vcoPhase -= 2.0*Math.PI;
		// quadrature demodulate carrier to base band with VCO, store in FIR buffer
		dmBuf[dmPos][0]=i*cosTab[(int)(vcoPhase*(double)SINCOS_SIZE/(2.0*Math.PI))%SINCOS_SIZE];
		dmBuf[dmPos][1]=q*sinTab[(int)(vcoPhase*(double)SINCOS_SIZE/(2.0*Math.PI))%SINCOS_SIZE];
	}
  */

	@Override
	public void hotKey(char c) {
	}
	
	private double freqDialog() {
		try {
			String tune = JOptionPane.showInputDialog(this, "Set tuning frequency",
				"BPSK demodulator", JOptionPane.QUESTION_MESSAGE);
			double r=Double.parseDouble(tune);
			return r;
		} catch (Exception e) {
		}
		return 0.0;
	}
}
