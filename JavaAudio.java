// Java SE audio implementation of IAudio
package com.ashbysoft.java_sdr;

import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

public class JavaAudio implements Runnable, IAudio {
    private final String m_pfx = "audio";  // used for logging and config keys
    private final String CFG_ADEV = m_pfx+"-device";
    private final String CFG_RATE = m_pfx+"-rate";
    private final String CFG_BITS = m_pfx+"-bits";
    private final String CFG_MODE = m_pfx+"-mode";
    private final String CFG_ICOR = m_pfx+"-ic";
    private final String CFG_QCOR = m_pfx+"-qc";
    private final String CFG_WAVE = m_pfx+"-wav-file";
    private final String CFG_FCDF = m_pfx+"-fcd_tune";
    private IConfig m_cfg;
    private IPublish m_pub;
    private ILogger m_log;
    private AudioFormat m_af;
    private int m_blen;
    private int m_ic;
    private int m_qc;
    private String m_wav;
    private String m_dev;
    private Thread m_thr;
    private boolean m_pau;
    private FCD m_fcd;
    private int m_freq;

    private ArrayList<IAudioHandler> m_hands;

    public JavaAudio(IConfig cfg, IPublish pub, ILogger log) {
        m_cfg = cfg;
        m_pub = pub;
        m_log = log;
        m_hands = new ArrayList<IAudioHandler>();
        logMsg("configuring audio..");

		// The audio format
		int rate = m_cfg.getIntConfig(CFG_RATE, 96000);	// Default 96kHz sample rate
		int bits = m_cfg.getIntConfig(CFG_BITS, 16);		// Default 16 bits/sample
		int chan = m_cfg.getConfig(CFG_MODE, "IQ").equals("IQ") ? 2 : 1; // IQ mode => 2 channels
		int size = (bits+7)/8 * chan;							// Round up bits to bytes..
		m_af = new AudioFormat(
			AudioFormat.Encoding.PCM_SIGNED,    // We always expect signed PCM
			rate, bits, chan, size, rate,       // We always have frame rate==sample rate
			false	                            // We always expect little endian samples
		);
		// Choose a buffer size that gives us ~10Hz buffer rate
		m_blen = rate*size/10;
        logMsg(m_af.toString()+ " blen="+m_blen);

        // The I/Q DC correction values (TODO: per device??)
        m_ic = m_cfg.getIntConfig(CFG_ICOR, 0);
		m_qc = m_cfg.getIntConfig(CFG_QCOR, 0);
        logMsg("ic="+m_ic+", qc="+m_qc);

        // The input device
        m_dev = m_cfg.getConfig(CFG_ADEV, "FUNcube Dongle");
        logMsg("dev="+m_dev);

		// TODO: Raw recording file
		m_wav = m_cfg.getConfig(CFG_WAVE, "");
        logMsg("wav="+m_wav);
        logMsg("done");
    }

    // IAudio
    public AudioDescriptor getAudioDescriptor() {
        return new AudioDescriptor(
            (int)m_af.getSampleRate(),
            m_af.getSampleSizeInBits(),
            m_af.getChannels(),
            m_af.getFrameSize(),
            m_blen);
    }

    public String[] getAudioDevices() {
        ArrayList<String> res = new ArrayList<String>();
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (int m=0; m<mixers.length; m++) {
            Mixer mx = AudioSystem.getMixer(mixers[m]);
            if (mx.getTargetLineInfo().length>0) {
                res.add(mixers[m].getName()+'/'+mixers[m].getDescription());
                logMsg("enumerted: "+res.get(res.size()-1));
            }
        }
        return res.toArray(new String[res.size()]);
    }

    public String getAudioSource() {
        return m_dev;
    }

    public void setAudioSource(String src) {
        synchronized(this) {
            if (m_thr!=null) {
                m_log.statusMsg("Audio in use");
                return;
            }
            m_dev = src;
        }
    }

    public void Start() {
        synchronized(this) {
            if (m_thr!=null)
                return;     // multiple start requests are harmless
            Resume();
		    m_thr = new Thread(this);
            m_thr.start();
        }
        logMsg("started");
    }

    public void Pause() {
        m_pau = true;
        logMsg("pause="+m_pau);
    }

    public void Resume() {
        m_pau = false;
        logMsg("pause="+m_pau);
    }

    public void Stop() {
        Thread thr = null;
        synchronized(this) {
            if (null==m_thr)
                return;     // multiple stop requests are harmless
            thr = m_thr;
            m_thr = null;
        }
        // wait outside lock - avoids deadlock risk
        try { thr.join(); } catch (Exception e) {}
        logMsg("stopped");
    }

    public void addHandler(IAudioHandler hand) {
        synchronized(m_hands) {
            m_hands.add(hand);
        }
    }

    public void remHandler(IAudioHandler hand) {
        synchronized(m_hands) {
            m_hands.remove(hand);
        }
    }

    // Runnable
	public void run() {
		// Open the appropriate file/device..
		AudioInputStream audio = null;
        TargetDataLine line = null;
		boolean isFile = false;
		if (m_dev.startsWith("file:")) {
			isFile = true;
			File fin = new File(m_dev.substring(5));
//TODO			frame.setTitle(frame.getTitle()+": "+fin);
			if (fin.canRead()) {
				try {
					audio = AudioSystem.getAudioInputStream(fin);
					// Check format
					AudioFormat af = audio.getFormat();
					if (!compareFormat(af, m_af)) {
						audio.close();
						audio = null;
						m_log.alertMsg("Incompatible audio format: "+fin+": "+af);
					}
				} catch (Exception e) {}
			} else {
				m_log.statusMsg("Unable to open file: "+fin);
			}
		} else {
//			frame.setTitle(frame.getTitle()+": "+dev);
			if (m_dev.indexOf("FUNcube Dongle")>=0) {
				// FCD in use, we can tune it ourselves..
				m_fcd = FCD.getFCD(m_log);
				while (FCD.FME_APP!=m_fcd.fcdGetMode()) {
					m_log.statusMsg("FCD not present or not in app mode, retrying..");
					try {
						Thread.sleep(1000);
					} catch(Exception e) {}
				}
				m_freq = m_cfg.getIntConfig(CFG_FCDF, 100000);
				if (FCD.FME_APP!=m_fcd.fcdAppSetFreqkHz(m_freq))
                    m_log.alertMsg("Unable to tune FCD");
                else
                    logMsg("FCD tuned @"+m_freq);
			}
			Mixer.Info[] mixers = AudioSystem.getMixerInfo();
			int m;
			for (m=0; m<mixers.length; m++) {
                String mn = mixers[m].getName()+'/'+mixers[m].getDescription();
				Mixer mx = AudioSystem.getMixer(mixers[m]);
				// NB: Linux puts the device name in description field, Windows in name field.. sheesh.
				if (mn.indexOf(m_dev)>=0 && mx.getTargetLineInfo().length>0) {
					// Found mixer/device with target lines, try and get a capture line in specified format
					try {
						line = (TargetDataLine) AudioSystem.getTargetDataLine(m_af, mixers[m]);
						line.open(m_af, m_blen);
						line.start();
						audio = new AudioInputStream(line);
					} catch (Exception e) {
						m_log.alertMsg("Unable to open audio device: "+m_dev+ ": "+e.getMessage());
					}
					break;
				}
			}
		}
		if (audio!=null) {
			m_log.statusMsg("Audio from: " + m_dev + "@" + m_af.getSampleRate());
			try {
				byte[] tmp = new byte[m_blen];
				ByteBuffer buf = ByteBuffer.allocate(m_blen);
				buf.order(m_af.isBigEndian() ?
					ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
				long otime = System.nanoTime();
				while (m_thr!=null) {
					long stime=System.nanoTime();
					int l=0, rds=0;
/* TODO scanner					if (fscan!=null) {		// Skip first buffer(~100ms) after retune when scanning..
						while (l<tmp.length) {
							l+=audio.read(tmp, l, tmp.length-l);
							++rds;
						}
						logMsg("audio reads (skip)="+rds);
					} */
					buf.clear();
					l=rds=0;
					while (l<tmp.length) {
						l+=audio.read(tmp, l, tmp.length-l);
						++rds;
					}
					long rtime=System.nanoTime();
					buf.put(tmp);
/* TODO wave out					if (wout!=null)
						wout.write(tmp);
					long wtime=System.nanoTime(); */
/* TODO scanner					if (fscan!=null) {		// Retune ASAP after each buffer..
						if (freq<2000000) {
							fcdSetFreq(freq+100);
						} else {
							fscan = null;
							statusMsg("Scan complete!");
						}
					} */
					long ftime=System.nanoTime();
                    long[] ttimes = null;
                    // lock handlers list while processing them!
                    synchronized(m_hands) {
                        ttimes = new long[m_hands.size()];
                        for (int t=0; t<m_hands.size(); t++) {
                            buf.rewind();
                            m_hands.get(t).receive(buf);
                            ttimes[t]=System.nanoTime();
                        }
                    }
					long etime=System.nanoTime();
					StringBuffer sb = new StringBuffer(
//						(wout!=null?wave+":":"") +
						"running (secs): ");
                    sb.append((etime-otime)/1000000000);
                    sb.append(" rds=").append(rds);
					sb.append(" proc times (nsecs) rd[/wr]/fcd/[tab[,...]/cyc: ");
					sb.append(rtime-stime);
//					sb.append("/"+(wtime-rtime));
					sb.append("/"+(ftime-rtime));
					if (ttimes!=null && ttimes.length>0) sb.append("/"+(ttimes[0]-ftime));
					for (int t=1; ttimes!=null && t<ttimes.length; t++)
						sb.append(","+(ttimes[t]-ttimes[t-1]));
					sb.append("/"+(etime-stime));
					logMsg(sb.toString());
					if (isFile) {
						int tot=(int)((etime-stime)/1000000);
						Thread.sleep(tot<100 ? 100-tot : 0);
					}
				}
                audio.close();
                if (!isFile) {
                    line.stop();
                    line.close();
                }
                if (m_fcd!=null) {
                    FCD.dropFCD();
                    m_fcd = null;
                }
			} catch (Exception e) {
				m_log.statusMsg("Audio oops: "+Arrays.toString(e.getStackTrace()));
			}
		}
		else {
			m_log.statusMsg("No audio device opened");
        }
        m_log.statusMsg("Audio input done");
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

    private void logMsg(String msg) {
        m_log.logMsg(m_pfx+": "+msg);
    }
}
