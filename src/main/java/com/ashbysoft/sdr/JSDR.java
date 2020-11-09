package com.ashbysoft.sdr;// Swing based display framework for SDR, just a frame with some tabs to select

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Properties;
import java.util.Calendar;
import java.text.SimpleDateFormat;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * display format, and the FCD input config/thread.
 */
public class JSDR implements Runnable, MessageOut {

	private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss: ");
	public static Properties config;
	public static final String CFG_TITLE = "title";
	public static final String CFG_WIDTH = "width";
	public static final String CFG_HEIGHT= "height";
	public static final String CFG_AUDDEV= "audio-device";
	public static final String CFG_AUDRAT= "audio-rate";
	public static final String CFG_AUDBIT= "audio-bits";
	public static final String CFG_AUDMOD= "audio-mode";
	public static final String CFG_ICORR = "i-correction";
	public static final String CFG_QCORR = "q-correction";
	public static final String CFG_FREQ  = "frequency";
	public static final String CFG_FORCE = "force-fcd";
	public static final String CFG_TAB = "tab-focus";
	public static final String CFG_SPLIT = "split-position";
	public static final String CFG_FUNCUBES = "funcube-demods";
	public static final String CFG_WAVE = "wave-out";
	public static final String CFG_VERB = "verbose";
	public static Properties publish;

	protected JFrame frame;
	protected JLabel status;
	protected JLabel scanner;
	protected JLabel hotkeys;
	protected JSplitPane split;
	protected JTabbedPane tabs;
	protected int ic, qc;
	private AudioFormat format;
	private int bufsize;
	private FCD fcd;
	private int freq, lastfreq;
	private File fscan;
	private float lastMax;
	private boolean done;
	private boolean paused;
	private String wave;
	private FileOutputStream wout = null;

	public static String getConfig(String prop, String def) {
		String val = config.getProperty(prop, def);
		config.setProperty(prop, val);
		return val;
	}

	public static int getIntConfig(String prop, int def) {
		try {
			String val = config.getProperty(prop);
			if (val!=null)
				return Integer.parseInt(val);
			else
				config.setProperty(prop, String.valueOf(def));
		} catch (Exception e) {
		}
		return def;
	}

	public static String getPublish(String prop, String def) {
		String val = publish.getProperty(prop, def);
		return val;
	}

	public static int getIntPublish(String prop, int def) {
		try {
			String val = publish.getProperty(prop);
			if (val!=null)
				return Integer.parseInt(val);
		} catch (Exception e) {
		}
		return def;
	}

	public void statusMsg(String msg) {
		status.setText(msg);
		logMsg(msg);
	}

	public void logMsg(String msg) {
		if (getConfig(CFG_VERB, "false").equals("true")) {
			String dat = sdf.format(Calendar.getInstance().getTime());
			System.err.println(dat + msg);
		}
	}

	public void regHotKey(char c, String desc) {
		if (publish.getProperty("hotkey-"+c)==null) {
			frame.getLayeredPane().getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW).put(
					KeyStroke.getKeyStroke(c), "Key");
			if (desc!=null) {
				String s = ""+c;
				// escape HTML
				if ('<'==c) s = "&lt;";
				if ('>'==c) s = "&gt;";
				hotkeys.setText(hotkeys.getText() + s+ ' ' + desc + "<br/>");
			}
			publish.setProperty("hotkey-"+c, ""+desc);
		}
	}

	@SuppressWarnings("serial")
	private JSDR() {
		// The audio format
		int rate = getIntConfig(CFG_AUDRAT, 96000);	// Default 96kHz sample rate
		int bits = getIntConfig(CFG_AUDBIT, 16);		// Default 16 bits/sample
		int chan = getConfig(CFG_AUDMOD, "IQ").equals("IQ") ? 2 : 1; // IQ mode => 2 channels
		int size = (bits+7)/8 * chan;							// Round up bits to bytes..
		format = new AudioFormat(
			AudioFormat.Encoding.PCM_SIGNED,	// We always expect signed PCM
			rate, bits, chan, size, rate,
			false	// We always expect little endian samples
		);
		// Choose a buffer size that gives us ~10Hz refresh rate
		bufsize = rate*size/10;

		// Raw recording file
		wave = config.getProperty(CFG_WAVE, "");

		// The main frame
		frame = new JFrame(getConfig(CFG_TITLE, "Java SDR v0.1"));
		frame.setSize(getIntConfig(CFG_WIDTH, 800), getIntConfig(CFG_HEIGHT, 600));
		frame.setResizable(true);
		// The top-bottom split
		split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		frame.add(split);
		split.setResizeWeight(1.0);
		split.setDividerSize(3);
		split.setDividerLocation((double)getIntConfig(CFG_SPLIT, 75)/100.0);
		// The tabbed display panes (in top)
		tabs = new JTabbedPane(JTabbedPane.BOTTOM);
		split.setTopComponent(tabs);
		// The control area (in bottom)
		JPanel controls = new JPanel();
		split.setBottomComponent(controls);
		// control layout
		controls.setLayout(new BorderLayout());
		// keyboard hotkeys
		AbstractAction act = new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				char c = e.getActionCommand().charAt(0);
				int f = -1;
				if ('p'==c) {
					paused = !paused;
					synchronized(frame) {
						frame.notify();
					}
				} else if ('i'==c)
					ic+=1;
				else if('I'==c)
					ic-=1;
				else if('q'==c)
					qc+=1;
				else if('Q'==c)
					qc-=1;
				else if('f'==c) {
					f = freqDialog();
				} else if('u'==c) {
					f = freq+1;
				} else if ('U'==c) {
					f = freq+10;
				} else if('d'==c) {
					f = freq-1;
				} else if ('D'==c) {
					f = freq-10;
				} else if ('s'==c) {
					f = freq+50;
				} else if ('S'==c) {
					f = freq-50;
				} else if ('@'==c) {
					if (confirmScan()) {
						f = freq;
						lastMax = -1;
					}
				} else if ('W'==c) {
					toggleWav();
				} else {
					Object o = tabs.getComponentAt(tabs.getSelectedIndex());
					if (o instanceof JsdrTab) {
						((JsdrTab)o).hotKey(c);
					}
				}
				if (f>=50000) {
					fcdSetFreq(f);
				}
				saveConfig();
			}
		};
		hotkeys = new JLabel(
			"<html><b>Hotkeys</b><br/>"+
			"p pause/resume input<br/>"+
			"i/I and q/Q adjust DC offsets (up/Down)<br/>" +
			"u/U tune up by 1/10kHz, d/D tune down by 1/10kHz<br/>" +
			"s/S step up/down by 50kHz<br/>" +
			"f enter frequency, @ start/stop scan<br/>" +
			"W toggle raw recording to: "+wave+"<br/>"
		);
		regHotKey('p', null);
		regHotKey('f', null);
		regHotKey('i', null);
		regHotKey('I', null);
		regHotKey('q', null);
		regHotKey('Q', null);
		regHotKey('u', null);
		regHotKey('U', null);
		regHotKey('d', null);
		regHotKey('D', null);
		regHotKey('s', null);
		regHotKey('S', null);
		regHotKey('@', null);
		regHotKey('W', null);
		frame.getLayeredPane().getActionMap().put("Key", act);
		controls.add(hotkeys, BorderLayout.CENTER);

		// status bar
		status = new JLabel(frame.getTitle());
		controls.add(status, BorderLayout.SOUTH);
		// Temporary scanner info
		scanner = new JLabel("scan info..");
		controls.add(scanner, BorderLayout.NORTH);
		// Close handler
		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				if (wout!=null)
					toggleWav();
				saveConfig();
				System.exit(0);
			}
		});
		// The content in each tab
		tabs.add("Spectrum", new FFT(this, format, bufsize));
		tabs.add("Phase", new Phase(this, format, bufsize));
		tabs.add("Demodulator", new Demod(this, format, bufsize));
		int nfcs = getIntConfig(CFG_FUNCUBES, 2);
		for (int fc=0; fc<nfcs; fc++) {
			String nm = "FUNcube"+fc;
			tabs.add(nm, new FUNcubeBPSKDemod(nm, this, format, bufsize));
		}
		tabs.setSelectedIndex(getIntConfig(CFG_TAB, 0));
		hotkeys.setText(hotkeys.getText()+"</html>");
		// Done - show it!
		frame.setVisible(true);
		// Start audio thread
		fscan = null;
		done = false;
		ic = getIntConfig(CFG_ICORR, 0);
		qc = getIntConfig(CFG_QCORR, 0);
		new Thread(this).start();
	}

	private int freqDialog() {
		if (fcd!=null) {
			try {
				String tune = JOptionPane.showInputDialog(frame, "Please enter new frequency",
					frame.getTitle(), JOptionPane.QUESTION_MESSAGE);
				return Integer.parseInt(tune);
			} catch (Exception e) {
				statusMsg("Invalid frequency");
			}
		} else {
			statusMsg("Not an FCD, unable to tune");
		}
		return -1;
	}

	private boolean confirmScan() {
		if (fcd!=null) {
			try {
				String msg = (fscan!=null) ? "Stop scan?" : "Scan from here?";
				int yn = JOptionPane.showConfirmDialog(frame, msg,
					frame.getTitle(), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
				if (0==yn) {
					if (fscan!=null)
						fscan = null;
					else {
						fscan = new File("scan.log");
						fscan.delete();
						return true;
					}
				}
			} catch (Exception e) {}
		}
		return false;
	}

	private String baseTitle = null;
	private void fcdSetFreq(int f) {
		if (baseTitle==null) baseTitle = frame.getTitle();
		lastfreq = freq;
		freq = f;
		if (null==fcd || FCD.FME_APP!=fcd.fcdAppSetFreqkHz(freq))
			frame.setTitle(baseTitle+ ": Unable to tune FCD");
		else
			frame.setTitle(baseTitle+" "+freq+" kHz");
	}

	private boolean compareFormat(AudioFormat a, AudioFormat b) {
		if (a.getChannels() == b.getChannels() &&
			a.getEncoding() == b.getEncoding() &&
			a.getFrameSize() == b.getFrameSize() &&
			a.getSampleRate() == b.getSampleRate() &&
			a.getSampleSizeInBits() == b.getSampleSizeInBits() &&
			a.isBigEndian() == b.isBigEndian())
			return true;
		return false;
	}

	// Audio input thread..
	public void run() {
		// Open the appropriate file/device..
		String dev = config.getProperty(CFG_AUDDEV, "FUNcube Dongle");	// Default to FCD
		String frc = config.getProperty(CFG_FORCE, "false");
		AudioInputStream audio = null;
		boolean isFile = false;
		if (dev.startsWith("file:")) {
			isFile = true;
			File fin = new File(dev.substring(5));
			frame.setTitle(frame.getTitle()+": "+fin);
			if (fin.canRead()) {
				try {
					audio = AudioSystem.getAudioInputStream(fin);
					// Check format
					AudioFormat af = audio.getFormat();
					if (!compareFormat(af, format)) {
						audio.close();
						audio = null;
						statusMsg("Incompatible audio format: "+fin+": "+af);
					}
				} catch (Exception e) {}
			} else {
				statusMsg("Unable to open file: "+fin);
			}
		} else {
			frame.setTitle(frame.getTitle()+": "+dev);
			if (dev.equals("FUNcube Dongle") || frc.equals("true")) {
				// FCD in use, we can tune it ourselves..
				fcd = FCD.getFCD(this);
				while (FCD.FME_APP!=fcd.fcdGetMode()) {
					statusMsg("FCD not present or not in app mode..");
					try {
						Thread.sleep(1000);
					} catch(Exception e) {}
				}
				freq = getIntConfig(CFG_FREQ, 100000);
				fcdSetFreq(freq);
			}
			Mixer.Info[] mixers = AudioSystem.getMixerInfo();
			int m;
			for (m=0; m<mixers.length; m++) {
				statusMsg("Mixer: " + mixers[m].getName() + " / " + mixers[m].getDescription());
				Mixer mx = AudioSystem.getMixer(mixers[m]);
				// NB: Linux puts the device name in description field, Windows in name field.. sheesh.
				if ((mixers[m].getDescription().indexOf(dev)>=0 ||
				    mixers[m].getName().indexOf(dev)>=0) && mx.getTargetLineInfo().length>0) {
					// Found mixer/device with target lines, try and get a capture line in specified format
					try {
						TargetDataLine line = (TargetDataLine) AudioSystem.getTargetDataLine(format, mixers[m]);
						line.open(format, bufsize);
						line.start();
						audio = new AudioInputStream(line);
						statusMsg("Audio device opened ok");
					} catch (Exception e) {
						statusMsg("Unable to open audio device: "+dev+ ": "+e.getMessage());
					}
					break;
				}
			}
		}
		if (audio!=null) {
			statusMsg("Audio from: " + dev + "@" + format.getSampleRate());
			try {
				// Use a buffer large enough to produce ~10Hz refresh rate.
				byte[] tmp = new byte[bufsize];
				ByteBuffer buf = ByteBuffer.allocate(bufsize);
				buf.order(format.isBigEndian() ?
					ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
				long otime = System.nanoTime();
				long ttimes[] = new long[tabs.getTabCount()];
				while (!done) {
					synchronized(frame) {
						while (paused) {
							scanner.setText("paused");
							frame.wait();
						}
					}
					long stime=System.nanoTime();
					int l=0, rds=0;
					if (fscan!=null) {		// Skip first buffer(~100ms) after retune when scanning..
						while (l<tmp.length) {
							l+=audio.read(tmp, l, tmp.length-l);
							++rds;
						}
						logMsg("audio reads (skip)="+rds);
					}
					buf.clear();
					l=rds=0;
					while (l<tmp.length) {
						l+=audio.read(tmp, l, tmp.length-l);
						++rds;
					}
					logMsg("audio reads="+rds);
					long rtime=System.nanoTime();
					buf.put(tmp);
					if (wout!=null)
						wout.write(tmp);
					long wtime=System.nanoTime();
					if (fscan!=null) {		// Retune ASAP after each buffer..
						if (freq<2000000) {
							fcdSetFreq(freq+100);
						} else {
							fscan = null;
							statusMsg("Scan complete!");
						}
					}
					long ftime=System.nanoTime();
					for (int t=0; t<tabs.getTabCount(); t++) {
						Object o = tabs.getComponentAt(t);
						if (o instanceof JsdrTab) {
							buf.rewind();
							((JsdrTab)o).newBuffer(buf);
						}
						ttimes[t]=System.nanoTime();
					}
					long etime=System.nanoTime();
					StringBuffer sb = new StringBuffer(
						(wout!=null?wave+":":"") +
						"running (secs): " +
						(etime-otime)/1000000000 +
						" proc times (nsecs) rd/wr/fcd/tab[,tab]/cyc: "
					);
					sb.append((rtime-stime));
					sb.append("/"+(wtime-rtime));
					sb.append("/"+(ftime-wtime));
					sb.append("/"+(ttimes[0]-ftime));
					for (int t=1; t<tabs.getTabCount(); t++)
						sb.append(","+(ttimes[t]-ttimes[t-1]));
					sb.append("/"+(etime-stime));
					logMsg(sb.toString());
					if (isFile) {
						int tot=(int)((etime-stime)/1000000);
						Thread.sleep(tot<100 ? 100-tot : 0);
					}
				}
				statusMsg("Audio input done");
			} catch (Exception e) {
				statusMsg("Audio oops: "+e);
				e.printStackTrace();
			}
		}
		else
			statusMsg("No audio device opened");
	}

	// Callback used by fft module to pass up maxima from each buffer
	public void spectralMaxima(float max, int foff) {
		// If scanning, save maxima against freq..
		if (fscan!=null) {
			try {
				String s = "" + System.currentTimeMillis()/1000 + "," + lastfreq + ","+ foff + "," + max + "\n";
				FileOutputStream fs = new FileOutputStream(fscan, true);
				fs.write(s.getBytes());
				fs.close();
			} catch (Exception e) {
				fscan = null;
				statusMsg("Scan aborted, cannot write log");
			}
			if (max>lastMax) {
				scanner.setText("Last maxima: freq: " + lastfreq + " max: " + max);
				lastMax = max;
				saveConfig();
			}
		}
	}

	private void toggleWav() {
		if (wout!=null) {
			try {
				wout.close();
			} catch (Exception e) {}
			wout = null;
		} else {
			if (wave!=null && wave.length()>0) {
				try {
					wout = new FileOutputStream(wave, true);
				} catch (Exception e) {}
			}
		}
	}

	private void saveConfig() {
		try {
			FileOutputStream cfo = new FileOutputStream("jsdr.properties");
			config.setProperty(CFG_WIDTH, String.valueOf(frame.getWidth()));
			config.setProperty(CFG_HEIGHT, String.valueOf(frame.getHeight()));
			config.setProperty(CFG_SPLIT, String.valueOf(split.getDividerLocation()*100/split.getHeight()));
			config.setProperty(CFG_TAB, String.valueOf(tabs.getSelectedIndex()));
			config.setProperty(CFG_ICORR, String.valueOf(ic));
			config.setProperty(CFG_QCORR, String.valueOf(qc));
			config.setProperty(CFG_FREQ, String.valueOf(freq));
			config.store(cfo, "Java SDR V0.1");
			cfo.close();
		} catch (Exception e) {
			statusMsg("Save oops: "+e);
		}
	}

	public static void main(String[] args) {
		// Load config..
		config = new Properties();
		publish= new Properties();
		try {
			FileInputStream cfi = new FileInputStream("jsdr.properties");
			config.load(cfi);
			cfi.close();
		} catch (Exception e) {
			System.err.println("Unable to load config, using defaults");
		}
		// Check for command line overrides
		for (int i=0; i<args.length; i++) {
			int o = args[i].indexOf('=');
			if (o>0)
				config.setProperty(args[i].substring(0,o), args[i].substring(o+1));
			else if (args[i].startsWith("file:"))
				config.setProperty(CFG_AUDDEV, args[i]);
		}
		// Get the UI up as soon as possible, we might need to display errors..
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new JSDR();
			}
		});
	}

	// Interface implemented by tabbed display components
	public interface JsdrTab {
		public void newBuffer(ByteBuffer buf);
		public void hotKey(char c);
	}
}
