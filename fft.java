import java.awt.Color;
import java.awt.Graphics;
import java.nio.ByteBuffer;

import javax.sound.sampled.AudioFormat;
import javax.swing.JPanel;

//Yay for JTransforms - the fastest Java FFT so far :)
import edu.emory.mathcs.jtransforms.fft.FloatFFT_1D;

@SuppressWarnings("serial")
public class fft extends JPanel implements jsdr.JsdrTab {
	private jsdr parent;
	private float[] dat;
	private float[] spc;
	private float[] win;
	private boolean dow = true;
	private boolean log = true;
	private AudioFormat fmt;

	public fft(jsdr p, AudioFormat af, int bufsize) {
		parent = p;
		// Allocate buffers according to format..
		int sbytes = (af.getSampleSizeInBits()+7)/8;
		dat = new float[bufsize/sbytes/af.getChannels()*2];
		spc = new float[dat.length+1];	// add one for spectal maxima
		win = new float[dat.length/2];
		// Calculate window coefficients (hamming)
		for (int s=0; s<win.length; s++)
			win[s] = (float) (0.54 - 0.46*Math.cos(2*Math.PI*s/win.length));
		fmt = af;
		// Reg hot keys
		p.regHotKey('h', "Toggle Hamming window");
		p.regHotKey('l', "Toggle log scale");
	}

	protected void paintComponent(Graphics g) {
		// Reticle
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, getWidth(), getHeight());
		g.setColor(Color.DARK_GRAY);
		g.drawLine(0, getHeight()/3, getWidth(), getHeight()/3);
		g.drawLine(0, getHeight()*2/3, getWidth(), getHeight()*2/3);
		int my1 = getHeight()*2/3-2;
		int my2 = getHeight()*2/3+2;
		int mxs = getWidth()/12;	// produces 8kHz markers
		for (int x=mxs; x<getWidth()-1; x+=mxs) {
			g.drawLine(x, my1, x, my2);
		}
		if (fmt.getChannels()<2) {
			g.drawString("0 Hz", 2, getHeight()*2/3-2);
			g.drawString(""+fmt.getSampleRate()/2+"Hz", getWidth()-50, getHeight()*2/3-2);
		} else {
			g.drawString("-"+fmt.getSampleRate()/2+"Hz", 2, getHeight()*2/3-2);
			g.drawString("+"+fmt.getSampleRate()/2+"Hz", getWidth()-80, getHeight()*2/3-2);
		}
		// Step size for resampling to screen size
		float s = (float)(dat.length/2)/(float)getWidth();
		int t = (int)Math.ceil(s);
		if (dow)
			g.drawString("step(win): "+s, 2, 24);
		else
			g.drawString("step(raw): "+s, 2, 24);
		// Scale factor to fit -1 to 1 float sample data into 1/3 screen height
		float h = (float)(getHeight()/6);
		// Offset down screen
		int o = getHeight()/6;
		// I in top 1/3rd
		g.setColor(Color.RED);
		g.drawString("I: "+parent.ic, 2, 12);
		int ly = 0;
		for (int p=0; p<getWidth()-1; p++) {
			int y = (int)(getMax(dat, 2*(int)(p*s), t)*h);
			g.drawLine(p, ly+o, p+1, y+o);
			ly = y;
		}
		// Q in middle 1/3rd
		g.setColor(Color.BLUE);
		g.drawString("Q: "+parent.qc, 2, getHeight()/3+12);
		o = getHeight()/2;
		ly = 0;
		for (int p=0; p<getWidth()-1; p++) {
			int y = (int)(getMax(dat, 2*(int)(p*s)+1, t)*h);
			g.drawLine(p, ly+o, p+1, y+o);
			ly = y;
		}
		// PSD in lower 1/3rd (log scale if selected)
		g.setColor(Color.GREEN);
		if (log)
			g.drawString("PSD(log): "+Math.log10(spc[spc.length-1]), 2, getHeight()*2/3+12);
		else
			g.drawString("PSD(raw): "+spc[spc.length-1], 2, getHeight()*2/3+12);
		o = getHeight();
		h = log ? (getHeight()/3)/(float)Math.log10(spc[spc.length-1]) : (getHeight()/3)/spc[spc.length-1];
		ly = 0;
		int off = getWidth()/2;
		// adjust scale and offset if only single channel data
		if (fmt.getChannels()<2) {
			s = s/2;
			off = 0;
		}
		for (int p=0; p<getWidth()-1; p++) {
			// offset and wrap index to display negative freqs, then positives..
			int i = (p+off) % getWidth();
			int y = log ? (int)(Math.log10(getMax(spc, 2*(int)(i*s), t))*h) : (int)(getMax(spc, 2*(int)(i*s), t)*h);
			g.drawLine(p, o-ly, p+1, o-y);
			ly = y;
//					if (2*(int)(p*s)<=spos && spos<=2*(int)((p+1)*s)) {
//						g.drawString("Max", p, o-y-2);
//					}
		}
	}
	// Find largest magnitude value in a array from offset o, length l (step always 2)
	private float getMax(float[]a, int o, int l) {
		float r = 0;
		for (int i=o; i<o+l; i+=2) {
			if (Math.abs(a[i])>r)
				r=a[i];
		}
		return r;
	}

	public void newBuffer(ByteBuffer buf) {
		// Convert to array of floats (scaled -1 to 1).. and apply windowing function if required
		int div = 2<<(fmt.getSampleSizeInBits()-1);
		for (int s=0; s<dat.length; s+=2) {
			dat[s] = ((float)(buf.getShort()+parent.ic) / (float)div) * (dow ? win[s/2] : 1);
			if (fmt.getChannels()>1)
				dat[s+1] = ((float)(buf.getShort()+parent.qc) / (float)div) * (dow ? win[s/2] : 1);
			else
				dat[s+1] = 0;
		}
		// Copy to preserve original input
		System.arraycopy(dat, 0, spc, 0, dat.length);
		// FFT
		FloatFFT_1D fft = new FloatFFT_1D(spc.length/2);
		fft.complexForward(spc);
		// Calculate power spectral density (PSD)
		float m = 0;
		int p = -1;
		for (int s=0; s<spc.length-1; s+=2) {
			spc[s] = (float)Math.sqrt((spc[s]*spc[s]) + (spc[s+1]*spc[s+1]));	// Compute PSD
			if (m<spc[s]) {
				m=spc[s];
				p = s;
			}
		}
		// Stash maxima for scaling display
		spc[spc.length-1] = m;
		// Upcall for scanner
		if (fmt.getChannels()<2)		// convert array index to frequency offset
			p = (p*(int)fmt.getSampleRate())/(2*dat.length);
		else
			p = (p*(int)fmt.getSampleRate())/dat.length - (int)fmt.getSampleRate()/2;
		parent.spectralMaxima(m, p);
		// Skip redraw unless we are visible
		if (isVisible())
			repaint();
	}

	public void hotKey(char c) {
		if ('h'==c)
			dow = !dow;
		if ('l'==c)
			log = !log;
	}
}
