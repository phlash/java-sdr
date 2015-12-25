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
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;

@SuppressWarnings("serial")
public class demod extends JPanel implements jsdr.JsdrTab, ActionListener, Runnable {

	private jsdr parent;
	private AudioFormat fmt, aud;
	private Thread thr;
	private int[] sam;
	private boolean dofir, doagc;
	private int flo, fhi;
	private int[] fir;
	private int fof;
	private double[] wfir;
	private int[] mod = {0, 0};
	private ByteBuffer bbf;
	private int max, avg, li, lq;
	private JRadioButton off, raw, am, fm;
	private JComboBox sel;
	private JLabel dbg;
	private ArrayList<Mixer.Info> mix;
	private SourceDataLine out;

	public demod(jsdr p, AudioFormat af, int bufsize) {
		parent = p;
		fmt = af;
		// Audio output format retains input sample rate for ease of coding, otherwise S16_le :)
		aud = new AudioFormat(
			AudioFormat.Encoding.PCM_SIGNED,
			fmt.getSampleRate(), 16, 2, 4, fmt.getSampleRate(), false
		);
		// delay buffer for FIR filter (currently fixed order 20)
		fir = new int[42];
		// weights for FIR filter
		wfir = new double[21];
		// generate default filter as all-pass
		this.flo = Integer.MIN_VALUE;
		this.fhi = Integer.MAX_VALUE;
		weights();
		// processing buffer for demodulation
		int sbytes = (af.getSampleSizeInBits()+7)/8;
		sam = new int[bufsize/sbytes/af.getChannels()*2];
		// audio output buffer
		bbf = ByteBuffer.allocate(sam.length*2);
		bbf.order(ByteOrder.LITTLE_ENDIAN);
		// build GUI panel
		this.setLayout(new GridLayout(2,1));
		JPanel top = new JPanel();
		this.add(top);
		this.off = new JRadioButton("Off");
		this.off.setSelected(true);
		top.add(this.off);
		this.raw  = new JRadioButton("Raw");
		top.add(this.raw);
		this.am  = new JRadioButton("AM");
		top.add(this.am);
		this.fm  = new JRadioButton("FM");
		top.add(this.fm);
		ButtonGroup grp = new ButtonGroup();
		grp.add(this.off);
		grp.add(this.raw);
		grp.add(this.am);
		grp.add(this.fm);
		this.sel = new JComboBox();
		this.sel.addActionListener(this);
		top.add(this.sel);
		this.dbg = new JLabel("select an output device..");
		top.add(this.dbg);
		this .add(new MyPanel());
		// detect output audio devices		
		Mixer.Info[] mxs = AudioSystem.getMixerInfo();
		mix = new ArrayList<Mixer.Info>();
		int m;
		for (m=0; m<mxs.length; m++) {
			// If we have any source lines, this is an output capable mixer
			if (AudioSystem.getMixer(mxs[m]).getSourceLineInfo().length>0) {
				mix.add(mxs[m]);
				this.sel.addItem(mxs[m].getName() + "//" + mxs[m].getDescription());
			}
		}
		// nothing going out yet..
		this.out = null;
		// audio output thread
		this.thr = new Thread(this);
		this.thr.start();
		// register keys for filter switching
		this.dofir = false;
		this.doagc = false;
		this.mod[0] = -(this.flo+this.fhi)/2;
		this.mod[1] = 0;
		p.regHotKey('b', "Toggle Bandpass filter");
		p.regHotKey('n', "Narrow filter");
		p.regHotKey('w', "Wide filter");
		p.regHotKey('r', "Reset filter (all pass)");
		p.regHotKey('a', "Toggle AGC");
	}

	public void actionPerformed(ActionEvent e) {
		int m = sel.getSelectedIndex();
		dbg.setText("Selected: "+mix.get(m));
		// Close current audio stream if any - force new open
		if (out!=null) {
			out.close();
			out=null;
		}
	}

	// generate FIR filter weights according to:
	// http://www.labbookpages.co.uk/audio/firWindowing.html
	// using the bandpass equation, with a hamming window
	private void weights() {
		// all-pass?
		if (Integer.MIN_VALUE==flo) {
			for (int i=0; i<wfir.length; i++)
				wfir[i]=0;
			wfir[(wfir.length-1)/2]=1;
		// band-pass
		} else {
			// normalise frequency between 0 and 0.5*samplerate
			double nlo = (double)flo/fmt.getSampleRate();
			double nhi = (double)fhi/fmt.getSampleRate();
			// filter order == length-1
			int ord = wfir.length-1;
			// calculate weights, apply hamming window
			for (int n=0; n<wfir.length; n++) {
				if (n==ord/2) {
					wfir[n]=2*(nhi-nlo);
				} else {
					wfir[n]=
						 (Math.sin(2*Math.PI*nhi*(double)(n-ord/2))/(Math.PI*(double)(n-ord/2)))
						-(Math.sin(2*Math.PI*nlo*(double)(n-ord/2))/(Math.PI*(double)(n-ord/2)));
				}
				wfir[n] *= 0.54 - 0.46*Math.cos(2*Math.PI*(double)n/(double)ord);
			}
		}
		// clear previous samples (if any)
		for (int i=0; i<fir.length; i++)
			fir[i]=0;
		fof=fir.length-2;
	}

	// Apply FIR filter to incoming sample stream
	private void filter(int in[], int out[]) {
		// put the current sample at start of delay buffer
		fir[fof]=in[0];
		fir[fof+1]=in[1];
		// weight and sum output I/Q
		double oi=0;
		double oq=0;
		for (int i=0; i<fir.length; i+=2) {
			int ti=(fof+i)%fir.length;
			oi = oi+fir[ti]*wfir[i/2];
			oq = oq+fir[ti+1]*wfir[i/2];
		}
		out[0] = (int)oi;
		out[1] = (int)oq;
		// move back in delay buffer
		fof=(fof-2);
		if (fof<0) fof=fir.length-2;
	}

	// Modulate one complex sample with another
	private void complex_mod(int[] sig1, int[] sig2, int[] out) {
		// (a+ib).(c+id) = ((ac-bd)+i(ad+bc))
		out[0]=sig1[0]*sig2[0]-sig1[1]*sig2[1];
		out[1]=sig1[0]*sig2[1]+sig1[1]*sig2[0];
	}

	// Generate continuous complex carrier samples at specified frequency
	private void complex_gen(int[] sig, int[] wav) {
		double w = (2*Math.PI*wav[0]*wav[1])/fmt.getSampleRate();
		sig[0]=(int)(Math.cos(w)*8192);
		sig[1]=(int)(Math.sin(w)*8192);
		wav[1]+=1;
		if (wav[1]>=(int)fmt.getSampleRate())
			wav[1]=0;
	}

	public void newBuffer(ByteBuffer buf) {
		// AM demodulator:
		//   determine AGC factor while measuring input amplitude and averaging it
		//   subtract average from each amplitude , scale for AGC and output in mono
		// FM demodulator (quadrature delay technique):
		//   determine AGC factor while measuring phase rotation rate (inter sample vector product)
		//   apply AGC to measured phase rotation rate and output in mono (so far!)
		max = 1;
		avg = 0;
		for(int s=0; s<sam.length; s+=2) {
			sam[s] = buf.getShort()+parent.ic;
			if (fmt.getChannels()>1)
				sam[s+1] = buf.getShort()+parent.qc;
			else
				sam[s+1] = 0;
			// apply filter?
			if (dofir) {
				//int[] sh = { 0, 0 };
				//complex_gen(sh, mod);
				int[] fs = { sam[s], sam[s+1] };
				int[] os = { 0, 0 };
				//complex_mod(fs, sh, os);
				filter(fs, os);
				sam[s]=os[0];
				sam[s+1]=os[1];
			}
			// Raw
			if (raw.isSelected()) {
				; // do nothing, as expected for raw pass through..
			}
			// AM
			if (am.isSelected()) {
				// measure amplitude of sample, update running average
				sam[s] = (int)Math.sqrt(sam[s]*sam[s]+sam[s+1]*sam[s+1]);
				avg = ((s/2)*avg+sam[s])/(s/2+1);
			} 
			// FM
			if (fm.isSelected()){
				// http://kom.aau.dk/group/05gr506/report/node10.html#SECTION04615000000000000000
				int v = (li*sam[s+1])-(lq*sam[s]); 
				li = sam[s];
				lq = sam[s+1];
				sam[s] = v;
			}
			max = Math.max(max, Math.abs(sam[s]));
		}
		// fix up maximum for AM
		if (am.isSelected()) {
			max -= avg;
		}
		// Write audio buffer, apply AGC if enabled
		bbf.clear();
		for (int s=0; s<sam.length; s+=2) {
			sam[s] = (am.isSelected() ? sam[s]-avg : sam[s]) * (doagc ? 8192/max : 1);
			short v = (short) sam[s];
			// Left
			bbf.putShort(v);
			// right
			bbf.putShort(v);
		}
		repaint();
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
		if (dofir) {
			this.mod[0]=-(this.fhi-this.flo)/2;
			jsdr.publish.setProperty("demod-filter-low", ""+this.flo);
			jsdr.publish.setProperty("demod-filter-high", ""+this.fhi);
		} else {
			jsdr.publish.remove("demod-filter-low");
			jsdr.publish.remove("demod-filter-high");
		}
	}

	public void run() {
		while (thr!=null) {
			if (off.isSelected()) {
				if (out!=null) {
					out.close();
					out=null;
				}
				dbg.setText("Audio not selected");
			} else if (out==null) {
				Mixer.Info mi = mix.get(sel.getSelectedIndex());
				try {
					out = AudioSystem.getSourceDataLine(aud, mi);
					if (out!=null) {
						out.open(aud);
						out.start();
						dbg.setText("Audio started");
					}
				} catch (Exception e) {
					out = null;
					dbg.setText("Failed to open audio: " + mi);
					return;
				}
			}
			if (out!=null) {
				// write current audio buffer
				out.write(bbf.array(), 0, bbf.array().length);
			} else {
				try { Thread.sleep(100); } catch (Exception e) {}
			}
		}
	}

	private class MyPanel extends JPanel {
		public void paintComponent(Graphics g) {
			// render time
			long stime=System.nanoTime();
			// render audio waveform buffer
			g.setColor(Color.BLACK);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(Color.BLUE);
			double scale = (sam.length/2)/getWidth();
			int ly=getHeight()/2;
			g.drawString("scale: "+scale + ", fir: "+dofir + " agc: " + doagc + " ("+flo+","+fhi+") max: "+max + " avg: " + avg, 10, 10);
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
			parent.logMsg("demod render (nsecs) aud/fil: " + (atime-stime) + "/" + (etime-atime));
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
}
