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
    private IConfig m_cfg;
    private IPublish m_pub;
    private ILogger m_log;
    private AudioFormat m_af;
    private AudioSource m_src;
    private int m_blen;
    private int m_ic;
    private int m_qc;
    private String m_dev;
    private Thread m_thr;
    private boolean m_pau;

    private ArrayList<IAudioHandler> m_hands;
    private ArrayList<IRawHandler> m_raws;

    public JavaAudio(IConfig cfg, IPublish pub, ILogger log) {
        m_cfg = cfg;
        m_pub = pub;
        m_log = log;
        m_hands = new ArrayList<IAudioHandler>();
        m_raws = new ArrayList<IRawHandler>();

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

        // The input device (by name)
        m_dev = m_cfg.getConfig(CFG_ADEV, "FUNcube Dongle");
        logMsg("dev="+m_dev);
        logMsg("config done");

        // Shutdown hook to close stuff
        Thread shut = new Thread(this, "die");
        Runtime.getRuntime().addShutdownHook(shut);
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

    public AudioSource[] getAudioSources() {
        ArrayList<AudioSource> srcs = new ArrayList<AudioSource>();
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (int m=0; m<mixers.length; m++) {
            Mixer mx = AudioSystem.getMixer(mixers[m]);
            if (mx.getTargetLineInfo().length>0) {
                srcs.add(new AudioSource(mixers[m].getName(), mixers[m].getDescription()));
                logMsg("getAudioSources: "+srcs.get(srcs.size()-1));
            }
        }
        return srcs.toArray(new AudioSource[srcs.size()]);
    }

    public AudioSource getAudioSource() {
        return m_src;
    }

    public void setAudioSource(String name) {
        setAudioSource(findSource(name));
    }

    public void setAudioSource(AudioSource src) {
        synchronized(this) {
            if (m_thr!=null) {
                m_log.statusMsg("Audio in use");
                return;
            }
            m_src = src;
            m_cfg.setConfig(CFG_ADEV, src.getName());
        }
    }

    public void start() {
        synchronized(this) {
            if (m_thr!=null)
                return;     // multiple start requests are harmless
            resume();
		    m_thr = new Thread(this, "run");
            m_thr.start();
        }
        logMsg("started");
    }

    public void pause() {
        m_pau = true;
        logMsg("pause="+m_pau);
    }

    public void resume() {
        m_pau = false;
        logMsg("pause="+m_pau);
    }

    public void stop() {
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

    public int getICorrection() {
        return m_ic;
    }

    public int getQCorrection() {
        return m_qc;
    }

    public void setICorrection(int i) {
        m_ic = i;
        m_cfg.setIntConfig(CFG_ICOR, m_ic);
    }

    public void setQCorrection(int q) {
        m_qc = q;
        m_cfg.setIntConfig(CFG_QCOR, m_qc);
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

     public void addRawHandler(IRawHandler hand) {
        synchronized(m_raws) {
            m_raws.add(hand);
        }
    }

    public void remRawHandler(IRawHandler hand) {
        synchronized(m_raws) {
            m_raws.remove(hand);
        }
    }
   // Runnable
	public void run() {
        // check for shutdown thread
        if ("die".equals(Thread.currentThread().getName())) {
            stop();
            return;
        }
		// Open the appropriate file/device..
		AudioInputStream audio = null;
		boolean isFile = false;
        // no selected source - find one from config
        if (null==m_src)
            m_src = findSource(m_dev);
        // check for file:<path> source
        if (m_src!=null && m_src.getName().startsWith("file:")) {
            audio = openFile(m_src.getName().substring(5));
            isFile = true;
            audio.mark(Integer.MAX_VALUE);
        // ..or open a device
        } else {
            audio = openDevice(m_src);
        }
		if (audio!=null) {
			m_log.statusMsg("Audio from: " + m_src + "@" + m_af.getSampleRate());
			try {
                // raw audio data (mono or IQ)
				byte[] raw = new byte[m_blen];
                ByteBuffer cnv = ByteBuffer.allocate(raw.length);
                cnv.order(ByteOrder.LITTLE_ENDIAN);
                // normalised IQ data buffer
				float[] buf = new float[2*m_blen/m_af.getFrameSize()];
				long otime = System.nanoTime();
                long dtime = 0;
                long etime = 0;
				while (m_thr!=null) {
                    long stime = System.nanoTime();
                    // Step#0: delay if reading from a file
					if (isFile) {
						int tot=(int)((etime-dtime)/1000000);
						Thread.sleep(tot<100 ? 100-tot : 0);
                        // paused? go around..
                        if (m_pau) {
                            logMsg("paused (file)");
                            continue;
                        }
					}
                    dtime = System.nanoTime();
                    // Step#1: raw byte read
					int l=0, rds=0;
					while (l<raw.length) {
                        int n=audio.read(raw, l, raw.length-l);
                        if (n<=0) {
                            logMsg("eof");
                            break;
                        }
						l+=n;
						++rds;
					}
                    if (l<raw.length) {
                        if (isFile) {
                            // go round again..
                            audio.reset();
                            continue;
                        } else {
                            break;
                        }
                    }
                    // raw handlers
                    synchronized(m_raws) {
                        for (IRawHandler hand : m_raws)
                            hand.receive(raw);
                    }
                    cnv.clear();
                    cnv.put(raw);
                    cnv.rewind();
					long rtime=System.nanoTime();
                    // paused? go around discarding data..
                    if (m_pau) {
                        logMsg("paused (device)");
                        continue;
                    }
                    // Step#2: read byte->short, apply I/Q correction, convert to float
                    int fs=m_af.getFrameSize();
                    int cs=m_af.getChannels();
                    int sn=0;
                    for (l=0; l<m_blen; l+=fs) {
                        // TODO: support other than 16-bit samples!
                        short s = cnv.getShort();
                        s += (short)m_ic;
                        buf[sn] = (float)s/(float)Short.MAX_VALUE;
                        sn++;
                        if (cs>1) {
                            s = cnv.getShort();
                            s += (short)m_qc;
                            buf[sn] = (float)s/(float)Short.MAX_VALUE;
                        } else {
                            buf[sn] = 0;
                        }
                        sn++;
                    }
					long ftime=System.nanoTime();
                    // Step#3: feed the handlers
                    long[] ttimes = null;
                    // NB: lock handlers list while processing them!
                    synchronized(m_hands) {
                        ttimes = new long[m_hands.size()];
                        for (int t=0; t<m_hands.size(); t++) {
                            m_hands.get(t).receive(buf);
                            ttimes[t]=System.nanoTime();
                        }
                    }
                    m_pub.setPublish("audio-frame", Boolean.TRUE);
					etime=System.nanoTime();
					StringBuffer sb = new StringBuffer(
						"running (secs): ");
                    sb.append((etime-otime)/1000000000);
                    sb.append(" rds=").append(rds);
					sb.append(" proc times (nsecs) rd/fcd/[tab[,...]/tot: ");
					sb.append(rtime-dtime);
					sb.append("/").append(ftime-rtime);
					if (ttimes!=null && ttimes.length>0) sb.append("/").append(ttimes[0]-ftime);
					for (int t=1; ttimes!=null && t<ttimes.length; t++)
						sb.append(",").append(ttimes[t]-ttimes[t-1]);
					sb.append("/").append(etime-stime);
					logMsg(sb.toString());
				}
                audio.close();
			} catch (Exception e) {
				m_log.statusMsg("Audio oops: "+Arrays.toString(e.getStackTrace()));
			}
		}
		else {
			m_log.statusMsg("No audio device opened");
        }
        m_log.statusMsg("Audio input done");
    }

    private AudioSource findSource(String part) {
        // file:<path> part is always valid
        if (part.startsWith("file:"))
            return new AudioSource(part, "file");
        for (AudioSource can : getAudioSources()) {
            // check for full match, then partial in name or description
            if (can.toString().equals(part))
                return can;
            if (can.getName().indexOf(part)>=0)
                return can;
            if (can.getDesc().indexOf(part)>=0)
                return can;
        }
        return null;
    }

    private AudioInputStream openDevice(AudioSource src) {
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (int m=0; m<mixers.length; m++) {
            AudioSource can = new AudioSource(mixers[m].getName(), mixers[m].getDescription());
            Mixer mx = AudioSystem.getMixer(mixers[m]);
            // NB: Linux puts the device name in description field, Windows in name field.. sheesh.
            if (can.equals(src) && mx.getTargetLineInfo().length>0) {
                // Found mixer/device with target lines, try and get a capture line in specified format
                try {
                    TargetDataLine line = (TargetDataLine) AudioSystem.getTargetDataLine(m_af, mixers[m]);
                    line.open(m_af, m_blen);
                    line.start();
                    return new AudioInputStream(line);
                } catch (Exception e) {
                    m_log.alertMsg("Unable to open audio device: "+src.toString()+ ": "+e.getMessage());
                }
                break;
            }
        }
        return null;
    }

    private AudioInputStream openFile(String path) {
        File fin = new File(path);
        if (fin.canRead()) {
            try {
                AudioInputStream audio = AudioSystem.getAudioInputStream(fin);
                // Check format
                AudioFormat af = audio.getFormat();
                if (!compareFormat(af, m_af)) {
                    // Try adding a format conversion..
                    logMsg("Incompatible file format, attempting conversion");
                    audio = AudioSystem.getAudioInputStream(m_af, audio);
                    af = audio.getFormat();
                }
                if (!compareFormat(af, m_af)) {
                    audio.close();
                    audio = null;
                    m_log.alertMsg("Incompatible audio format: "+fin+": "+af);
                }
                return audio;
            } catch (Exception e) {
                m_log.alertMsg("Unable to open file: " + e.getMessage());
            }
        } else {
            m_log.statusMsg("Not readable: "+fin);
        }
        return null;
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
