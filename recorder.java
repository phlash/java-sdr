// Save raw audio if requested
package com.ashbysoft.java_sdr;

import java.io.FileOutputStream;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

// internal classes, but Java gives you no other support for WAV writing :(
//import com.sun.media.sound.WaveFileWriter;

public class recorder extends IUIComponent implements IRawHandler, IPublishListener, ActionListener, Runnable {
    private ILogger logger;
    private IAudio audio;
    private JTextField file;
    private JToggleButton go;
    private boolean dorec;
    //private WaveFileWriter wave;
    private FileOutputStream wave;

    public recorder(IConfig cfg, IPublish pub, ILogger log,
		IUIHost hst, IAudio aud) {
        logger = log;
        audio = aud;
        // We are just a panel with file name and record toggle button
        file = new JTextField(cfg.getConfig("recorder-path", ""), 60);
        add(file);
        go = new JToggleButton("Record!");
        go.setActionCommand("go");
        go.addActionListener(this);
        add(go);
        dorec = false;
        audio.addRawHandler(this);
        // Shutdown hook to close file cleanly
        Thread shut = new Thread(this, "die");
        Runtime.getRuntime().addShutdownHook(shut);
    }

    public void run() {
        closeWav();
    }

    public void notify(String key, Object val) {
        if ("audio-change".equals(key) &&
            val instanceof IAudio) {
            // possibly stop recording, then..
            audio = (IAudio)val;
            audio.remRawHandler(this);
            audio.addRawHandler(this);
        }
    }

    public synchronized void actionPerformed(ActionEvent e) {
        if ("go".equals(e.getActionCommand())) {
            if (go.isSelected())
                dorec = openWav(file.getText());
            else
                dorec = closeWav();
        }
    }

    public synchronized void receive(byte[] raw) {
        // are we recording?
        if (dorec)
            // assume wave is writeable
            try { wave.write(raw); } catch (Exception e) {
                logger.statusMsg("failed to write to wav file:" + e.getMessage());
                dorec = closeWav();
            }
    }

    public void hotKey(char c) {}

    private boolean openWav(String path) {
        try {
            wave = new FileOutputStream(path);
            logger.statusMsg("recording to: "+path);
            return true;
        } catch (Exception e) {
            logger.statusMsg("unable to open: "+path);
        }
        return false;
    }

    private boolean closeWav() {
        if (wave!=null) {
            try {
                wave.close();
                logger.statusMsg("recording done");
            } catch (Exception e) {
                logger.statusMsg("error closing wav file");
            }
            wave = null;
        }
        return false;
    }
}