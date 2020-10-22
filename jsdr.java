// Swing based display framework for SDR, assembler & container for
// tabbed components that do all the real work. Provides config,
// publish and logger interfaces.
package com.ashbysoft.java_sdr;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Properties;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.HashMap;
import java.text.SimpleDateFormat;
import java.lang.reflect.Method;

import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JFileChooser;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.BorderFactory;
import javax.swing.border.BevelBorder;

public class jsdr implements IConfig, IPublish, ILogger, IUIHost, ActionListener {

	private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss: ");
	public static final String CFG_VERSION = "version";
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

	protected JFrame frame;
	protected JMenuBar menu;
	protected JLabel status;
	protected JLabel scanner;
	protected JLabel hotkeys;
	protected JSplitPane split;
	protected JTabbedPane tabs;
	protected JPanel waterfall;
	private Properties config;
	private Properties publish;
	private ArrayList<IPublishListener> listeners;
	private HashMap<String, Method> actionMap;
	private IAudio audio;
	private int freq, lastfreq;
	private File fscan;
	private float lastMax;
	private boolean done;
	private boolean paused;
	private String wave;
	private FileOutputStream wout = null;

	// IConfig
	public String getConfig(String key, String def) {
		String val = config.getProperty(key, def);
		config.setProperty(key, val);
		return val;
	}

	public int getIntConfig(String key, int def) {
		try {
			String val = config.getProperty(key);
			if (val!=null)
				return Integer.parseInt(val);
			else
				config.setProperty(key, String.valueOf(def));
		} catch (Exception e) {
		}
		return def;
	}

	public void setConfig(String key, String val) {
		config.setProperty(key, val);
	}

	public void setIntConfig(String key, int val) {
		config.setProperty(key, String.valueOf(val));
	}

	// IPublish
	public String getPublish(String prop, String def) {
		String val = publish.getProperty(prop, def);
		return val;
	}

	public void setPublish(String prop, String val) {
		publish.setProperty(prop, val);
		synchronized(this) {
			for (IPublishListener list: listeners) {
				list.notify(prop, val);
			}
		}
	}

	public void listen(IPublishListener list) {
		synchronized(this) {
			listeners.add(list);
		}
	}

	public void unlisten(IPublishListener list) {
		synchronized(this) {
			listeners.remove(list);
		}
	}

	// ILogger
	public void alertMsg(String msg) {
		JOptionPane.showMessageDialog(frame, msg, frame.getTitle(), JOptionPane.ERROR_MESSAGE);
		statusMsg(msg);
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

	// IUIHost
	public void addTabbedComponent(IUIComponent comp) {
		// TODO: probably more work here around menu/hotkey setup & dependency injection
	}

	public void remTabbedComponent(IUIComponent comp) {
		// TODO: undo whatever add does above!
	}

	// candidate for moving to addTabbedComponent
	private void regHotKey(char c, String desc) {
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

	// ActionListener - we choose to use reflection rather than a hard-coded
	// case statement that 'knows' all the commands and methods to call...YMMV.
	public void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();
		if (actionMap.containsKey(cmd)) {
			try {
				actionMap.get(cmd).invoke(this);
			} catch (Exception ex) {
				alertMsg("Action failed: "+ex.getMessage());
			}
		} else {
			statusMsg("No handler for action: "+cmd);
		}
	}
	private void registerHandler(String cmd, String handler) {
		try {
			Method method = this.getClass().getMethod(handler);
			actionMap.put(cmd, method);
		} catch (Exception e) {
			alertMsg("Register action failed: "+e.toString());
		}
	}

	// Private constructor - there can be only one.
	@SuppressWarnings("serial")
	private jsdr(String[] args) {
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
				setConfig(args[i].substring(0,o), args[i].substring(o+1));
			// TODO raw file URL support else if (args[i].startsWith("file:"))
			//	setConfig(CFG_AUDDEV, args[i]);
		}
		// Create audio object
		audio = new JavaAudio(this, this, this);
		// The main frame
		frame = new JFrame(getConfig(CFG_TITLE, "Java SDR v0.2"));
		frame.setSize(getIntConfig(CFG_WIDTH, 800), getIntConfig(CFG_HEIGHT, 600));
		frame.setResizable(true);
		// The top menu
		actionMap = new HashMap<String, Method>();
		menu = new JMenuBar();
		frame.setJMenuBar(menu);
		// File menu
		JMenu file = new JMenu("File");
		file.setMnemonic(KeyEvent.VK_F);
		JMenuItem item = new JMenuItem("Open wav..", KeyEvent.VK_O);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
		item.setActionCommand("jsdr-open-wav");
		registerHandler(item.getActionCommand(), "openWav");
		item.addActionListener(this);
		file.add(item);
		item = new JMenuItem("Open Device..", KeyEvent.VK_D);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK));
		item.setActionCommand("jsdr-open-dev");
		registerHandler(item.getActionCommand(), "openDev");
		item.addActionListener(this);
		file.add(item);
		item = new JMenuItem("Quit..", KeyEvent.VK_Q);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
		item.setActionCommand("jsdr-quit");
		registerHandler(item.getActionCommand(), "quit");
		item.addActionListener(this);
		file.add(item);
		menu.add(file);
		// Help menu
		JMenu help = new JMenu("Help");
		help.setMnemonic(KeyEvent.VK_H);
		item = new JMenuItem("Hot keys", KeyEvent.VK_K);
		item.setActionCommand("jsdr-help-keys");
		registerHandler(item.getActionCommand(), "hotHelp");
		item.addActionListener(this);
		help.add(item);
		item = new JMenuItem("About", KeyEvent.VK_A);
		item.setActionCommand("jsdr-help-about");
		registerHandler(item.getActionCommand(), "about");
		item.addActionListener(this);
		help.add(item);
		menu.add(help);
		// The top-bottom split
		split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		frame.add(split);
		split.setResizeWeight(0.5);
		split.setDividerSize(3);
		//split.setDividerLocation((double)getIntConfig(CFG_SPLIT, 75)/100.0);
		// The tabbed display panes (in top)
		tabs = new JTabbedPane(JTabbedPane.BOTTOM);
		split.setTopComponent(tabs);
		// The waterfall (in bottom)
		waterfall = new JPanel(new BorderLayout());
		waterfall.setBackground(Color.lightGray);
		split.setBottomComponent(waterfall);
		// keyboard hotkeys
/* TODO fix hotkeys		AbstractAction act = new AbstractAction() {
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
		controls.add(hotkeys, BorderLayout.CENTER); */

		// status bar
		status = new JLabel(frame.getTitle());
		status.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		waterfall.add(status, BorderLayout.SOUTH);
		// Temporary scanner info
		// TODO scanner scanner = new JLabel("scan info..");
		//controls.add(scanner, BorderLayout.NORTH);
		// Close handler
		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				quit();
			}
		});
		// The content in each tab
		Color[] bks = {Color.cyan, Color.pink, Color.yellow};
		for (int t=0; t<6; t++) {
			JPanel tab = new JPanel();
			tab.setBackground(bks[t%3]);
			tabs.add("Tab#"+t, tab);
		}
/* TODO tabs		tabs.add("Spectrum", new fft(this, format, bufsize));
		tabs.add("Phase", new phase(this, format, bufsize));
		tabs.add("Demodulator", new demod(this, format, bufsize));
		int nfcs = getIntConfig(CFG_FUNCUBES, 2);
		for (int fc=0; fc<nfcs; fc++) {
			String nm = "FUNcube"+fc;
			tabs.add(nm, new FUNcubeBPSKDemod(nm, this, format, bufsize));
		}
		tabs.setSelectedIndex(getIntConfig(CFG_TAB, 0));
		hotkeys.setText(hotkeys.getText()+"</html>"); */
		// Done - show it!
		frame.setVisible(true);
		// Start audio thread
		fscan = null;
		done = false;
	}

	// Action handlers
	public void openWav() {
		JFileChooser choose = new JFileChooser();
		choose.setDialogTitle("Open WAV");
		int res = choose.showOpenDialog(frame);
		if (JFileChooser.APPROVE_OPTION==res) {
			File selected = choose.getSelectedFile();
			statusMsg("open WAV: "+selected);
			openAudio("file:"+selected);
		}
	}

	public void openDev() {
		Object selected = JOptionPane.showInputDialog(frame,
			"Please select an audio device",
			"Open Device",
			JOptionPane.QUESTION_MESSAGE,
			null,
			audio.getAudioDevices(),
			null);
		if (selected!=null) {
			statusMsg("open Dev: "+selected);
			openAudio(selected.toString());
		}
	}

	private void openAudio(String thing) {
		audio.Stop();
		audio.setAudioSource(thing);
		audio.Start();
	}

	public void quit() {
		if (wout!=null)
			toggleWav();
		saveConfig();
		System.exit(0);
	}

	public void hotHelp() {
		JOptionPane.showMessageDialog(frame,
			"<html><h2>Hotkey Help</h2>Placeholder</html>",
			frame.getTitle(), JOptionPane.INFORMATION_MESSAGE);
	}

	public void about() {
		JOptionPane.showMessageDialog(frame,
			"<html><h2>About Java-SDR</h2>Placeholder</html>",
			frame.getTitle(), JOptionPane.INFORMATION_MESSAGE);
	}

	private int freqDialog() {
		// TODO if (fcd!=null) {
			try {
				String tune = JOptionPane.showInputDialog(frame, "Please enter new frequency",
					frame.getTitle(), JOptionPane.QUESTION_MESSAGE);
				return Integer.parseInt(tune);
			} catch (Exception e) {
				statusMsg("Invalid frequency");
			}
		//} else {
		//	statusMsg("Not an FCD, unable to tune");
		//}
		return -1;
	}

	private boolean confirmScan() {
		//if (fcd!=null) {
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
		//}
		return false;
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
			setConfig(CFG_VERSION, "2");
			/*config.setProperty(CFG_WIDTH, String.valueOf(frame.getWidth()));
			config.setProperty(CFG_HEIGHT, String.valueOf(frame.getHeight()));
			config.setProperty(CFG_SPLIT, String.valueOf(split.getDividerLocation()*100/split.getHeight()));
			config.setProperty(CFG_TAB, String.valueOf(tabs.getSelectedIndex()));
			config.setProperty(CFG_ICORR, String.valueOf(ic));
			config.setProperty(CFG_QCORR, String.valueOf(qc));
			config.setProperty(CFG_FREQ, String.valueOf(freq));*/
			config.store(cfo, "Java SDR V0.2");
			cfo.close();
		} catch (Exception e) {
			statusMsg("Save oops: "+e);
		}
	}

	public static void main(String[] args) {
		// Get the UI up as soon as possible, we might need to display errors..
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new jsdr(args);
			}
		});
	}
}
