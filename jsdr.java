// Swing based display framework for SDR, just a frame with some tabs to select
// display format, and the FCD input config/thread.
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

public class jsdr implements Runnable {

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
	private int lastMsecs;
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

	public void regHotKey(char c, String desc) {
		if (publish.getProperty("hotkey-"+c)==null) {
			frame.getLayeredPane().getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW).put(
					KeyStroke.getKeyStroke(c), "Key");
			if (desc!=null)
				hotkeys.setText(hotkeys.getText() + c+ ' ' + desc + "<br/>");
			publish.setProperty("hotkey-"+c, ""+desc);
		}
	}

	@SuppressWarnings("serial")
	private jsdr() {
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
		tabs.add("Spectrum", new fft(this, format, bufsize));
		tabs.add("Phase", new phase(this, format, bufsize));
		//tabs.add("Demodulator", new demod(this, format, bufsize));
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
				status.setText("Invalid frequency");
			}
		} else {
			status.setText("Not an FCD, unable to tune");
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
		if (null==fcd || FCD.FME_APP!=fcd.fcdAppSetFreqkHz(freq=f))
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
						status.setText("Incompatible audio format: "+fin+": "+af);
					}
				} catch (Exception e) {}
			} else {
				status.setText("Unable to open file: "+fin);
			}
		} else {
			frame.setTitle(frame.getTitle()+": "+dev);
			if (dev.equals("FUNcube Dongle") || frc.equals("true")) {
				// FCD in use, we can tune it ourselves..
				fcd = FCD.getFCD();
				while (FCD.FME_APP!=fcd.fcdGetMode()) {
					status.setText("FCD not present or not in app mode..");
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
				// System.err.println("Mixer: " + mixers[m].getName() + " / " + mixers[m].getDescription());
				// NB: Linux puts the device name in description field, Windows in name field.. sheesh.
				if (mixers[m].getDescription().indexOf(dev)>=0 ||
				    mixers[m].getName().indexOf(dev)>=0) {
					// Found mixer/device, try and get a capture line in specified format
					try {
						TargetDataLine line = (TargetDataLine) AudioSystem.getTargetDataLine(format, mixers[m]);
						line.open(format, bufsize);
						line.start();
						audio = new AudioInputStream(line);
					} catch (Exception e) {
						status.setText("Unable to open audio device: "+dev+ ": "+e.getMessage());
					}
					break;
				}
			}
		}
		if (audio!=null) {
			try {
				// Use a buffer large enough to produce ~10Hz refresh rate.
				byte[] tmp = new byte[bufsize];
				ByteBuffer buf = ByteBuffer.allocate(bufsize);
				buf.order(format.isBigEndian() ?
					ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
				long orig = System.currentTimeMillis();
				while (!done) {
					synchronized(frame) {
						while (paused) {
							status.setText("paused");
							frame.wait();
						}
					}
					long st=System.currentTimeMillis();
					int l=0;
					if (fscan!=null) {		// Skip first buffer(~100ms) after retune when scanning..
						while (l<tmp.length)
							l+=audio.read(tmp, l, tmp.length-l);
					}
					buf.clear();
					l=0;
					while (l<tmp.length)
						l+=audio.read(tmp, l, tmp.length-l);
					buf.put(tmp);
					if (wout!=null)
						wout.write(tmp);
					long mid=System.currentTimeMillis();
					if (fscan!=null) {		// Retune ASAP after each buffer..
						if (freq<2000000) {
							fcdSetFreq(freq+100);
						} else {
							fscan = null;
							status.setText("Scan complete!");
						}
					}
					for (int t=0; t<tabs.getTabCount(); t++) {
						Object o = tabs.getComponentAt(t);
						if (o instanceof JsdrTab) {
							buf.rewind();
							((JsdrTab)o).newBuffer(buf);
						}
					}
					long end=System.currentTimeMillis();
					status.setText((wout!=null?wave+":":"") + "running (secs): " + (end-orig)/1000 + " last cycle (msecs): "+lastMsecs);
					lastMsecs = (int)(end-mid);
					if (isFile) {
						int tot=(int)(end-st);
						Thread.sleep(tot<100 ? 100-tot : 0);
					}
				}
				status.setText("Audio input done");
			} catch (Exception e) {
				status.setText("Audio oops: "+e);
				e.printStackTrace();
			}
		}
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
				status.setText("Scan aborted, cannot write log");
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
			status.setText("Save oops: "+e);
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
				new jsdr();
			}
		});
	}

	// Interface implemented by tabbed display components
	public interface JsdrTab {
		public void newBuffer(ByteBuffer buf);
		public void hotKey(char c);
	}
}
