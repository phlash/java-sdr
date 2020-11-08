// Swing based display framework for SDR, assembler & container for
// tabbed components that do all the real work. Provides config,
// publish and logger interfaces.
package com.ashbysoft.java_sdr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.HashMap;
import java.text.SimpleDateFormat;
import java.lang.reflect.Method;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
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

public class jsdr implements IConfig, IPublish, ILogger, IUIHost, IPublishListener, ActionListener, PropertyChangeListener, Runnable {

	private static final int m_ver = 2;
	private static final String m_cfg = "jsdr.properties";
	private static final String m_pfx = "jsdr-";
	private static final String CFG_VERSION = m_pfx+"version";
	private static final String CFG_TITLE = m_pfx+"title";
	private static final String CFG_WIDTH = m_pfx+"width";
	private static final String CFG_HEIGHT= m_pfx+"height";
	private static final String CFG_FREQ  = m_pfx+"fcd-frequency";
	private static final String CFG_TAB   = m_pfx+"tab-focus";
	private static final String CFG_SPLIT = m_pfx+"split-position";
	private static final String CFG_FCUBES= m_pfx+"funcube-demods";
	private static final String CFG_VERB  = m_pfx+"verbose";

	private Properties config;
	private HashMap<String, Object> publish;

	private IAudio audio;
	private boolean paused;
	private FCD fcd;
	private int freq;

	protected JFrame frame;
	protected JMenuBar menu;
	protected JLabel status;
	protected JLabel iqcorr;
	protected JLabel fcdtune;
	protected JLabel scanner;
	protected JLabel hotkeys;
	protected JSplitPane split;
	protected JTabbedPane tabs;
	private waterfall wfall;
	private ArrayList<IPublishListener> listeners;
	private HashMap<String, Method> actionMap;

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
		} catch (Exception e) {}	// Yes I know - but unparseable => use default
		config.setProperty(key, String.valueOf(def));
		return def;
	}

	public void setConfig(String key, String val) {
		config.setProperty(key, val);
	}

	public void setIntConfig(String key, int val) {
		config.setProperty(key, String.valueOf(val));
	}

	private void saveConfig() {
		try {
			FileOutputStream cfo = new FileOutputStream(m_cfg);
			setConfig(CFG_VERSION, "2");
			config.store(cfo, "(Ashbysoft *) Java SDR");
			cfo.close();
		} catch (Exception e) {
			statusMsg("Unable to save config: "+e.getMessage());
		}
	}

	// IPublish
	public Object getPublish(String prop, Object def) {
		synchronized(publish) {
			Object val = publish.get(prop);
			if (null==val)
				val = def;
			publish.put(prop, val);
			return val;
		}
	}

	public void setPublish(String prop, Object val) {
		synchronized(publish) {
			publish.put(prop, val);
			for (IPublishListener list: listeners) {
				list.notify(prop, val);
			}
		}
	}

	public void listen(IPublishListener list) {
		synchronized(publish) {
			listeners.add(list);
		}
	}

	public void unlisten(IPublishListener list) {
		synchronized(publish) {
			listeners.remove(list);
		}
	}

	// ILogger
	public void alertMsg(String msg) {
		if (frame!=null)
			JOptionPane.showMessageDialog(frame, msg, frame.getTitle(), JOptionPane.ERROR_MESSAGE);
		statusMsg(msg);
	}

	public void statusMsg(String msg) {
		if (status!=null)
			status.setText(msg);
		logMsg(msg);
	}

	private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss: ");
	public void logMsg(String msg) {
		if (getConfig(CFG_VERB, "false").equals("true")) {
			String dat = sdf.format(Calendar.getInstance().getTime());
			System.err.println(dat + msg);
		}
	}

	// IUIHost
	public void addMenu(JMenu sub) {
		menu.add(sub);
	}

	public void addHotKeys(char[] keys) {
		statusMsg("addHotkeys() - not implemented yet :(");
	}

	/* candidate for moving to addHotKeys
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
	} */

	// IPublishListener
	public void notify(String key, Object val) {
		// check for audio frame and repaint everything (out of audio thread!)
		if("audio-frame".equals(key))
			SwingUtilities.invokeLater(this);
	}

	// Runnable - in Swing thread context, forces paint of all components
	public void run() {
		frame.repaint();
	}

	// ActionListener - we choose to use reflection rather than a hard-coded
	// case statement that 'knows' all the commands and methods to call...
	// This is as close as Java comes to function pointeres... YMMV.
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

	// PropertyChangedListener
	public void propertyChange(PropertyChangeEvent e) {
		if (e.getPropertyName().equals(JSplitPane.DIVIDER_LOCATION_PROPERTY)) {
			JSplitPane src = (JSplitPane)e.getSource();
			int loc = src.getDividerLocation()*100/src.getHeight();
			setIntConfig(CFG_SPLIT, loc);
			src.setResizeWeight(0.5);		// equal resizing after first draw
		}
	}

	// Private constructor - there can be only one.
	@SuppressWarnings("serial")
	private jsdr(String[] args) {
		// Create publish property map & listeners, add ourselves
		publish= new HashMap<String, Object>();
		listeners = new ArrayList<IPublishListener>();
		listen(this);
		// Load config..
		config = new Properties();
		try {
			FileInputStream cfi = new FileInputStream(m_cfg);
			config.load(cfi);
			cfi.close();
		} catch (Exception e) {
			System.err.println("Unable to load config, using defaults");
		}
		// Check config version - dump if not compatible
		if (getIntConfig(CFG_VERSION, 0) != m_ver) {
			System.err.println("Config versions differ, using defaults");
			config.clear();
		}
		// Check for command line config overrides
		String caud = null;
		for (int i=0; i<args.length; i++) {
			int o = args[i].indexOf('=');
			if (o>0) {
				setConfig(args[i].substring(0,o), args[i].substring(o+1));
				logMsg("config override: "+args[i]);
			}
			else
				caud = args[i];
		}
		// Check/open FCD for tuning
		fcd = FCD.getFCD(this);
		freq = getIntConfig(CFG_FREQ, 435950);	// default FC-1 telmetry
		if (fcd!=null) {
			// update sample rate for detected version of FCD
			if (fcd.fcdGetVersion()==FCD.FCD_VERSION_2)
				setIntConfig("audio-rate", 192000);
			else
				setIntConfig("audio-rate", 96000);
			// read back current tuning freq (adjust for kHz)
			freq = fcd.fcdAppGetFreq()/1000;
		}

		// Create audio object
		audio = new JavaAudio(this, this, this);

		// GUI creation..
		// The main frame
		frame = new JFrame(getConfig(CFG_TITLE, "(Ashbysoft *) Java SDR"));
		frame.setSize(getIntConfig(CFG_WIDTH, 800), getIntConfig(CFG_HEIGHT, 600));
		frame.setResizable(true);

		// The top menu
		actionMap = new HashMap<String, Method>();
		menu = new JMenuBar();
		frame.setJMenuBar(menu);

		// File menu
		JMenu file = new JMenu("File");
		file.setMnemonic(KeyEvent.VK_F);
		JMenuItem item = new JMenuItem("Open File...", KeyEvent.VK_O);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
		item.setActionCommand("jsdr-open-file");
		registerHandler(item.getActionCommand(), "openFile");
		item.addActionListener(this);
		file.add(item);
		item = new JMenuItem("Open Device...", KeyEvent.VK_D);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK));
		item.setActionCommand("jsdr-open-dev");
		registerHandler(item.getActionCommand(), "openDev");
		item.addActionListener(this);
		file.add(item);
		item = new JMenuItem("Close Audio...", KeyEvent.VK_C);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
		item.setActionCommand("jsdr-close-audio");
		registerHandler(item.getActionCommand(), "closeAudio");
		item.addActionListener(this);
		file.add(item);
		item = new JMenuItem("Quit/exit", KeyEvent.VK_Q);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
		item.setActionCommand("jsdr-quit");
		registerHandler(item.getActionCommand(), "quit");
		item.addActionListener(this);
		file.add(item);
		menu.add(file);

		// Audio menu
		JMenu aud = new JMenu("Audio");
		aud.setMnemonic(KeyEvent.VK_A);
		item = new JMenuItem("[Un]pause", KeyEvent.VK_P);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, 0));
		item.setActionCommand("jsdr-pause");
		registerHandler(item.getActionCommand(), "audioPause");
		item.addActionListener(this);
		aud.add(item);
		item = new JMenuItem("Adj I: +1");
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, 0));
		item.setActionCommand("jsdr-adj-i+1");
		registerHandler(item.getActionCommand(), "adjIPlus1");
		item.addActionListener(this);
		aud.add(item);
		item = new JMenuItem("Adj I: -1");
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.SHIFT_DOWN_MASK));
		item.setActionCommand("jsdr-adj-i-1");
		registerHandler(item.getActionCommand(), "adjISub1");
		item.addActionListener(this);
		aud.add(item);
		item = new JMenuItem("Adj Q: +1");
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0));
		item.setActionCommand("jsdr-adj-q+1");
		registerHandler(item.getActionCommand(), "adjQPlus1");
		item.addActionListener(this);
		aud.add(item);
		item = new JMenuItem("Adj Q: -1");
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.SHIFT_DOWN_MASK));
		item.setActionCommand("jsdr-adj-q-1");
		registerHandler(item.getActionCommand(), "adjQSub1");
		item.addActionListener(this);
		aud.add(item);
		menu.add(aud);

		// FCD menu
		JMenu fmenu = new JMenu("FCD");
		fmenu.setMnemonic(KeyEvent.VK_C);
		item = new JMenuItem("Freq...", KeyEvent.VK_F);
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0));
		item.setActionCommand("jsdr-fcd-freq");
		registerHandler(item.getActionCommand(), "fcdDialog");
		item.addActionListener(this);
		fmenu.add(item);
		item = new JMenuItem("Tune:+1 kHz");
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, 0));
		item.setActionCommand("jsdr-fcd-plus1");
		registerHandler(item.getActionCommand(), "fcdPlus1");
		item.addActionListener(this);
		fmenu.add(item);
		item = new JMenuItem("Tune:+10 kHz");
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.SHIFT_DOWN_MASK));
		item.setActionCommand("jsdr-fcd-plus10");
		registerHandler(item.getActionCommand(), "fcdPlus10");
		item.addActionListener(this);
		fmenu.add(item);
		item = new JMenuItem("Tune:+50 kHz");
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0));
		item.setActionCommand("jsdr-fcd-plus50");
		registerHandler(item.getActionCommand(), "fcdPlus50");
		item.addActionListener(this);
		fmenu.add(item);
		item = new JMenuItem("Tune:-1 kHz");
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0));
		item.setActionCommand("jsdr-fcd-sub1");
		registerHandler(item.getActionCommand(), "fcdSub1");
		item.addActionListener(this);
		fmenu.add(item);
		item = new JMenuItem("Tune:-10 kHz");
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.SHIFT_DOWN_MASK));
		item.setActionCommand("jsdr-fcd-sub10");
		registerHandler(item.getActionCommand(), "fcdSub10");
		item.addActionListener(this);
		fmenu.add(item);
		item = new JMenuItem("Tune:-50 kHz");
		item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK));
		item.setActionCommand("jsdr-fcd-sub50");
		registerHandler(item.getActionCommand(), "fcdSub50");
		item.addActionListener(this);
		fmenu.add(item);
		// disable if no FCD
		if (null==fcd)
			fmenu.setEnabled(false);
		menu.add(fmenu);

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

		// The top-bottom split
		split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		frame.add(split);
		split.setDividerSize(3);
		split.setResizeWeight((double)getIntConfig(CFG_SPLIT, 50)/100.0);
		split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, this);

		// The tabbed display panes (in top)
		tabs = new JTabbedPane(JTabbedPane.BOTTOM);
		split.setTopComponent(tabs);

		// The waterfall & status displays (in bottom)
		JPanel sbottom = new JPanel(new BorderLayout());
		sbottom.setBackground(Color.lightGray);
		split.setBottomComponent(sbottom);
		wfall = new waterfall(this, this, audio);
		sbottom.add(wfall, BorderLayout.CENTER);

		// bevelled information box
		Box infobar = new Box(BoxLayout.X_AXIS);
		infobar.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		sbottom.add(infobar, BorderLayout.SOUTH);
		// status text
		status = new JLabel(frame.getTitle());
		infobar.add(status);
		// spacer
		infobar.add(Box.createGlue());
		// IQ corrections
		iqcorr = new JLabel();
		infobar.add(iqcorr);
		// initial text
		updateIQ();
		// FCD tuning info
		fcdtune = new JLabel();
		infobar.add(fcdtune);

		// Close handler
		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				quit();
			}
		});

		// The content in each tab
		tabs.add("Phase", new phase(this, this, this, this, audio));
		tabs.add("FFT", new fft(this, this, this, this, audio));
		tabs.add("Demod", new demod(this, this, this, this, audio));
		Color[] bks = {Color.cyan, Color.pink, Color.yellow};
		for (int t=0; t<3; t++) {
			JPanel tab = new JPanel();
			tab.setBackground(bks[t]);
			tabs.add("Tab#"+t, tab);
		}
		tabs.setSelectedIndex(getIntConfig(CFG_TAB, 0));

		// spacer & help menu, after any tab menus
		menu.add(Box.createHorizontalGlue());
		menu.add(help);
/* TODO tabs
		int nfcs = getIntConfig(CFG_FUNCUBES, 2);
		for (int fc=0; fc<nfcs; fc++) {
			String nm = "FUNcube"+fc;
			tabs.add(nm, new FUNcubeBPSKDemod(nm, this, format, bufsize));
		}
		hotkeys.setText(hotkeys.getText()+"</html>"); */
		// Done - show it!
		frame.setVisible(true);

		// initial tuning
		tuneFCD(freq);

		// Start audio immediately if command line audio device specified
		if (caud!=null) {
			if (!caud.startsWith("-run"))
				audio.setAudioSource(caud);
			audio.start();
		}
	}

	// Action handlers
	public void openFile() {
		JFileChooser choose = new JFileChooser();
		choose.setDialogTitle("Open Audio File");
		int res = choose.showOpenDialog(frame);
		if (JFileChooser.APPROVE_OPTION==res) {
			File selected = choose.getSelectedFile();
			statusMsg("audio file: "+selected);
			openAudio("file:"+selected);
		}
	}

	public void openDev() {
		Object selected = JOptionPane.showInputDialog(frame,
			"Please select an audio device",
			"Open Audio Device",
			JOptionPane.QUESTION_MESSAGE,
			null,
			audio.getAudioSources(),
			null);
		if (selected!=null) {
			statusMsg("audio dev: "+selected);
			openAudio(selected);
		}
	}

	private void openAudio(Object src) {
		audio.stop();
		if (src instanceof String)
			audio.setAudioSource((String)src);
		else
			audio.setAudioSource((AudioSource)src);
		setPublish("audio-change", audio);
		audio.start();
		paused = false;
	}

	public void closeAudio() {
		audio.stop();
		statusMsg("audio stopped");
	}

	public void quit() {
		audio.stop();
		saveConfig();
		System.exit(0);
	}

	public void audioPause() {
		if (paused) {
			audio.resume();
			paused = false;
		} else {
			audio.pause();
			paused = true;
		}
		status.setText("Paused: " + paused);
	}

	public void adjIPlus1() { audio.setICorrection(audio.getICorrection()+1); updateIQ(); }
	public void adjQPlus1() { audio.setQCorrection(audio.getQCorrection()+1); updateIQ(); }
	public void adjISub1() { audio.setICorrection(audio.getICorrection()-1); updateIQ(); }
	public void adjQSub1() { audio.setQCorrection(audio.getQCorrection()-1); updateIQ(); }
	private void updateIQ() {
		iqcorr.setText("| I/Q:"+audio.getICorrection()+'/'+audio.getQCorrection());
	}

	public void fcdDialog() {
		if (fcd!=null) {
			try {
				String tune = JOptionPane.showInputDialog(frame, "Please enter new frequency",
					frame.getTitle(), JOptionPane.QUESTION_MESSAGE);
				tuneFCD(Integer.parseInt(tune));
			} catch (Exception e) {
				statusMsg("Invalid frequency");
			}
		} else {
			statusMsg("Not an FCD, unable to tune");
		}
	}

	public void fcdPlus1() { tuneFCD(freq+1); }
	public void fcdPlus10() { tuneFCD(freq+10); }
	public void fcdPlus50() { tuneFCD(freq+50); }
	public void fcdSub1() { tuneFCD(freq-1); }
	public void fcdSub10() { tuneFCD(freq-10); }
	public void fcdSub50() { tuneFCD(freq-50); }

	private void tuneFCD(int f) {
		if (fcd!=null) {
			if (freq==f || FCD.OK==fcd.fcdAppSetFreqkHz(f)) {
				fcdtune.setText("| FCD:"+f+"kHz");
				setIntConfig(CFG_FREQ, f);
				freq = f;
				logMsg("jsdr: fcd tune to: "+freq);
			}
		} else {
			fcdtune.setText("| FCD: n/a");
		}
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
/*
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
*/
	public static void main(String[] args) {
		// Get the UI up as soon as possible, we might need to display errors..
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new jsdr(args);
			}
		});
	}
}
