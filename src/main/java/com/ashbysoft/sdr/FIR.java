package com.ashbysoft.sdr;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.io.Console;

public class FIR implements Runnable {
	private String help = "commands: h[elp], o[utput] <n>, r[ate] <sample rate>, t[ype] w[hite]|s[in], f[req] <sin freq>\n"+
		"          e[nable], d[isable], a[llpass], l[ow] <threshold freq>, u[pper] <threshold freq>, s[hift] <freq>\n"+
		"          m[odulate], q[uit]\n";
	private boolean running = true;
	private Thread thd = null;
	private ArrayList<Mixer.Info> mix;
	private int sel = -1;
	private AudioFormat fmt = null;
	private SourceDataLine aud = null;
	private int typ = 0;
	private int flo = 500;
	private int fhi = 1500;
	private int[] sin = {1000, 0};
	private int[] shf = {500, 0};
	private ByteBuffer buf = null;
	private int[] sam = null;
	private boolean fil = false;
	private boolean mod = false;
	private double[] wfir = new double[21];
	private int[] fir = new int[21];
	private int fof = fir.length-1;

	public static void main(String[] args) {
		FIR me = new FIR();
		me.boot();
	}

	private void boot() {
		// Get a console or quit early
		Console cons = System.console();
		if (null==cons) {
			System.err.println("Sorry: need a console to run");
			System.exit(1);
		}
		// Grab list of audio devices
		Mixer.Info[] mxs = AudioSystem.getMixerInfo();
		mix = new ArrayList<Mixer.Info>();
		int m, n=0;
		for (m=0; m<mxs.length; m++) {
			// If we have any source lines, this is an output capable mixer
			if (AudioSystem.getMixer(mxs[m]).getSourceLineInfo().length>0) {
				mix.add(mxs[m]);
				cons.printf("Output["+n+"]: "+mxs[m].getName()+"\n");
				++n;
			}
		}
		// Default audio format (44.1kHz/s16_le)
		fmt = new AudioFormat(
			AudioFormat.Encoding.PCM_SIGNED,
			44100, 16, 2, 4, 44100, false
		);
		// Start audio processing thead
		thd = new Thread(this);
		thd.start();
		// Wait for commands in this thread
		cons.printf("Enter commands (h for help)\n");
		while (true) {
			cons.printf("Cmd: ");
			cons.flush();
			String line = cons.readLine();
			StringTokenizer tok = new StringTokenizer(line, " \t");
			String cmd = tok.hasMoreTokens() ? tok.nextToken() : null;
			if (null==cmd) {
				continue;
			} else if (cmd.startsWith("h")) {
				cons.printf(help);
			} else if (cmd.startsWith("o")) {
				String arg = tok.hasMoreTokens() ? tok.nextToken() : null;
				if (null!=arg) {
					try {
						sel = Integer.parseInt(arg);
					} catch (NumberFormatException e) {
						continue;
					}
				}
			} else if (cmd.startsWith("r")) {
				String arg = tok.hasMoreTokens() ? tok.nextToken() : null;
				if(null!=arg) {
					try {
						n = Integer.parseInt(arg);
					} catch (NumberFormatException e) {
						continue;
					}
					fmt = new AudioFormat(
						AudioFormat.Encoding.PCM_SIGNED,
						n, 16, 2, 4, n, false
					);
				}
			} else if (cmd.startsWith("t")) {
				String arg = tok.hasMoreTokens() ? tok.nextToken() : null;
				if (null!=arg) {
					if (arg.startsWith("w"))
						typ=0;
					else
						typ=1;
				}
			} else if (cmd.startsWith("f")) {
				String arg = tok.hasMoreTokens() ? tok.nextToken() : null;
				if (null!=arg) {
					try {
						sin[0] = Integer.parseInt(arg);
					} catch (NumberFormatException e) {
						continue;
					}
				}
			} else if (cmd.startsWith("e")) {
				fil = true;
			} else if (cmd.startsWith("d")) {
				fil = false;
			} else if (cmd.startsWith("a")) {
				weights(Integer.MIN_VALUE, Integer.MIN_VALUE);
			} else if (cmd.startsWith("l")) {
				String arg = tok.hasMoreTokens() ? tok.nextToken() : null;
				if (null!=arg) {
					try {
						flo = Integer.parseInt(arg);
						weights(flo, fhi);
					} catch (NumberFormatException e) {
						continue;
					}
				}
			} else if (cmd.startsWith("u")) {
				String arg = tok.hasMoreTokens() ? tok.nextToken() : null;
				if (null!=arg) {
					try {
						fhi = Integer.parseInt(arg);
						weights(flo, fhi);
					} catch (NumberFormatException e) {
						continue;
					}
				}
			} else if (cmd.startsWith("s")) {
				String arg = tok.hasMoreTokens() ? tok.nextToken() : null;
				if (null!=arg) {
					try {
						shf[0] = Integer.parseInt(arg);
					} catch (NumberFormatException e) {
						continue;
					}
				}
			} else if (cmd.startsWith("m")) {
				mod = !mod;
			} else if (cmd.startsWith("q")) {
				running=false;
				try { thd.join(); } catch (Exception e) {}
				System.exit(0);
			} else {
				cons.printf("Oops - unrecognised command: "+cmd+"\n");
			}
			cons.printf("[out=%d rate=%f type=%d sin=%d filt=%d ftlo=%d fthi=%d, shf=%d, mod=%d]\n",
				sel, fmt.getSampleRate(), typ, sin[0], fil?1:0,  flo, fhi, shf[0], (mod)?1:0);
		}
	}

	// generate FIR filter weights according to:
	// http://www.labbookpages.co.uk/audio/firWindowing.html
	// (including hamming window)
	private void weights(int f1, int f2) {
		// all-pass?
		if (Integer.MIN_VALUE==f1 && Integer.MIN_VALUE==f2) {
			for (int i=0; i<wfir.length; i++)
				wfir[i]=0;
			wfir[(wfir.length-1)/2]=1;
		// band-pass
		} else {
			double df1 = (double)f1/fmt.getSampleRate();
			double df2 = (double)f2/fmt.getSampleRate();
			int ord = wfir.length-1;
			for (int n=0; n<wfir.length; n++) {
				if (n==ord/2) {
					wfir[n]=2*(df2-df1);
				} else {
					wfir[n]
						= (Math.sin(2*Math.PI*df2*(n-ord/2))/(Math.PI*(n-ord/2)))
						- (Math.sin(2*Math.PI*df1*(n-ord/2))/(Math.PI*(n-ord/2)));
				}
				wfir[n] = wfir[n]*(0.54-0.46*Math.cos(2*Math.PI*n/ord));
			}
		}
		// clear previous samples (if any)
		for (int i=0; i<fir.length; i++)
			fir[i]=0;
		fof=fir.length-1;
	}

	// Apply FIR filter to incoming sample stream
	private int filter(int in) {
		// put the current sample at start of delay buffer
		fir[fof]=in;
		// weight and sum output I/Q
		double o=0;
		for (int i=0; i<fir.length; i++) {
			int ti=(fof+i)%fir.length;
			o = o+fir[ti]*wfir[i];
		}
		// move back in delay buffer
		fof=(fof-1);
		if (fof<0) fof=fir.length-1;
		return (int)o;
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
		sig[0]=(int)(Math.cos(w)*4096);
		sig[1]=(int)(Math.sin(w)*4096);
		wav[1]+=1;
		if (wav[1]>=(int)fmt.getSampleRate())
			wav[1]=0;
	}

	private void fillsam() {
		// generate either white noise (random samples) or sine at specified freq
		for (int s=0; s<sam.length; s++) {
			if (0==typ)
				sam[s] = (int)(Math.random()*8192)-4096;
			else
				sam[s] = (int)(Math.sin((2*Math.PI*sin[0]*s)/(sam.length*10))*4096);
		}
	}

	public void run() {
		int lsel = sel;
		
		// Termination check
		while (running) {
			// Close audio if sel changed
			if (sel!=lsel) {
				if (null!=aud) {
					buf=null;
					sam=null;
					aud.close();
					aud=null;
				}
				lsel=sel;
			}
			// Connect to audio output (if all specified)
			if (sel>=0 && null==aud && null!=fmt) {
				try {
					aud=AudioSystem.getSourceDataLine(fmt, mix.get(sel));
					if (null!=aud) {
						aud.open(fmt);
						aud.start();
					}
				} catch (Exception e) {
					e.printStackTrace(System.err);
					aud=null;
				}
			}
			// If we have audio out, generate buffer and process
			if (aud!=null) {
				if (null==buf) {
					int cnt = (int)fmt.getSampleRate()*4/10;	// 0.1sec buffer
					buf=ByteBuffer.allocate(cnt);
					buf.order(ByteOrder.LITTLE_ENDIAN);
					sam=new int[cnt/4];
				}
				if (mod) {
					buf.clear();
					for (int s=0; s<sam.length; s++) {
						// Testing complex modulation..
						int[] sig1 = {0, 0};
						int[] sig2 = {0, 0};
						int[] out = {0, 0};
						complex_gen(sig1, sin);
						complex_gen(sig2, shf);
						complex_mod(sig1, sig2, out);	// self-multiply..
						short v = (short)(sig1[0]);	// left=original
						buf.putShort(v);
						v = (short)(out[0]/32768);		// right=modulated
						buf.putShort(v);
					}
				} else {
					// testing fir filter..
					fillsam();
					buf.clear();
					for (int s=0; s<sam.length; s++) {
						short v = (fil) ? (short)filter(sam[s]) : (short)sam[s];
						buf.putShort(v);
						buf.putShort(v);
					}
				}
				aud.write(buf.array(), 0, buf.array().length);
			} else {
				try { Thread.sleep(10); } catch (Exception e) {}
			}
		}
		if (null!=aud)
			aud.close();
	}
}
