/***************************************************************************
 *  This file is part of java-sdr.
 *
 *  CopyRight (C) 2011-2020  Phil Ashby
 *
 *  java-sdr is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  java-sdr is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with java-sdr.  If not, see <http://www.gnu.org/licenses/>.
 *
 ***************************************************************************/

// FUNcube Dongle interface, uses calls to fcdctl for control & Javax.sound for
// audio (aka I/Q) samples

package com.ashbysoft.java_sdr;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.Line;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;

import java.util.List;
import java.util.Arrays;

public class FCD {
	// Return consts..
	public static final int OK = 0;
	public static final int ERR = -1;
	// fcdGetVersion return values..
	public static final int FCD_VERSION_NONE = 0;/*!< No FCD detected */
	public static final int FCD_VERSION_1    = 1;/*!< Original FCD. */
	public static final int FCD_VERSION_1_1  = 2;/*!< Bias T added in 1.1 */
	public static final int FCD_VERSION_2    = 3;/*!< Pro+ */
	public static final int FCD_VERSION_UNK  = 4;/* Sorry no idea! */
	// fcdGetMode return values..
	public static final int FCD_MODE_NONE    = 0;/*!< No FCD detected */
	public static final int FCD_MODE_BL      = 1;/*!< Bootloader */
	public static final int FCD_MODE_APP     = 2;/*!< Application */

	// tuner_rf_filter_t values
	public static final int TRFE_0_4 = 0;
	public static final int TRFE_4_8 = 1;
	public static final int TRFE_8_16 = 2;
	public static final int TRFE_16_32 = 3;
	public static final int TRFE_32_75 = 4;
	public static final int TRFE_75_125 = 5;
	public static final int TRFE_125_250 = 6;
	public static final int TRFE_145 = 7;
	public static final int TRFE_410_875 = 8;
	public static final int TRFE_435 = 9;
	public static final int TRFE_875_2000 = 10;

	// tuner_if_filter_t values
	public static final int TIFE_200KHZ = 0;
	public static final int TIFE_300KHZ = 1;
	public static final int TIFE_600KHZ = 2;
	public static final int TIFE_1536KHZ = 3;
	public static final int TIFE_5MHZ = 4;
	public static final int TIFE_6MHZ = 5;
	public static final int TIFE_7MHZ = 6;
	public static final int TIFE_8MHZ = 7;

	// Private constructor (must use factory)
	private ILogger logger;
	private String fcdctl;
	private FCD(ILogger log, String path) {
		logger = log;
		fcdctl = path;
		logger.logMsg(String.format("fcd: using fcdctl @ %s", path));
	}

	// General execution handler
	private String runFcdctl(String[] args) {
		String[] cmd = new String[args.length+1];
		cmd[0] = fcdctl;
		System.arraycopy(args, 0, cmd, 1, args.length);
		logger.logMsg("fcd: fcdctl args: "+Arrays.toString(cmd));
		try {
			Process proc = Runtime.getRuntime().exec(cmd);
			BufferedReader stdout = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String response = stdout.readLine();
			int exit = proc.waitFor();
			if (exit!=0) {
				throw new Exception("non-zero exit code: "+exit);
			}
			logger.logMsg("fcd: fcdctl response: "+response);
			return response;
		} catch (Exception e) {
			logger.logMsg("fcd: unable to run fcdctl: " + e.getMessage());
		}
		return null;
	}
	// Methods called by client
	public int fcdGetVersion() {
		String status = runFcdctl(new String[]{ "-m", "-s" });
		if (null==status)
			return FCD_VERSION_NONE;
		else if (status.indexOf("V1.0")>0)
			return FCD_VERSION_1;
		else if (status.indexOf("V1.1")>0)
			return FCD_VERSION_1_1;
		else if (status.indexOf("V2.0")>0)
			return FCD_VERSION_2;
		logger.logMsg("fcd: unknown version string: "+status);
		return FCD_VERSION_UNK;
	}
	public int fcdGetMode() {
		String status = runFcdctl(new String[]{ "-m", "-s" });
		if (null==status)
			return FCD_MODE_NONE;
		else if (status.indexOf("BL")>0)
			return FCD_MODE_BL;
		else
			return FCD_MODE_APP;
	}
	public int fcdGetFwVerStr(StringBuffer fwver) {
		String status = runFcdctl(new String[]{ "-m", "-s" });
		if (null==status)
			return ERR;
		fwver.append(status);
		return OK;
	}
	public int fcdAppReset() {
		String status = runFcdctl(new String[]{ "-m", "-r" });
		if (null==status)
			return ERR;
		return OK;
	}
	public int fcdAppSetFreqkHz(int freq) {
		String f = String.format("%g", (float)freq/1e3);
		String status = runFcdctl(new String[]{ "-m", "-f", f});
		if (null==status)
			return ERR;
		return OK;
	}
	public int fcdAppSetFreq(int freq) {
		String f = String.format("%g", (float)freq/1e6);
		String status = runFcdctl(new String[]{ "-m", "-f", f});
		if (null==status)
			return ERR;
		return OK;
	}
	public int fcdAppGetFreq() {
		String status = runFcdctl(new String[]{ "-m", "-s" });
		// parse freq from response
		if (null==status)
			return ERR;
		int off = status.indexOf("FREQ");
		off += (off>0? 5: 0);
		int end = off>0? status.indexOf(" ", off): 0;
		int freq = ERR;
		try {
			freq = Integer.parseInt(status.substring(off, end));
		} catch (Exception e) { logger.logMsg("fcd: unparseable frequency?"); }
		return freq;
	}

	// Factory method
	private static FCD cached = null;
	public static FCD getFCD(ILogger log) { return getFCD(log, null); }
	public static synchronized FCD getFCD(ILogger log, String partialPath) {
		if (cached!=null)
			return cached;
		String found = null;
		// Search various places for fcdctl binary
		try {
			File us = new File(FCD.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
			log.logMsg("fcd: looking in: "+us);
			File chk = new File(us, "fcdctl");
			if (chk.canExecute())
				found = chk.getPath();
		} catch (Exception e) {}
		if (found!=null)
			cached = new FCD(log, found);
		return cached;
	}
	public static synchronized void dropFCD() {
		if (cached!=null) {
			cached = null;
		}
	}

	// Audio interface: returns a javax.sound.sampled.TargetDataLine suitable for recording I/Q data.
	// Returns null if FCD is not found in audio subsystem, or format is unsupported.
	public TargetDataLine getLine(AudioFormat af) {
		Mixer.Info[] mixers = AudioSystem.getMixerInfo();
		int m;
		for (m=0; m<mixers.length; m++) {
			Mixer mix = AudioSystem.getMixer(mixers[m]);
			// Test for a FUNcube Dongle, unfortunately by string matching - thanks Java..
			// addendum: Linux puts the USB name in description field, Windows in name field.. sheesh.
			logger.logMsg("fcd: mixer: (" + mix.getClass().getName() + "): " + mixers[m].getDescription() + '/' + mixers[m].getName());
			boolean hasLines = false;
			for (Line.Info inf: mix.getTargetLineInfo()) {
				logger.logMsg(" - target line: " + inf.toString());
				hasLines = true;
			}
			if ((mixers[m].getDescription().indexOf("FUNcube Dongle")>=0 ||
			    mixers[m].getName().indexOf("FUNcube Dongle")>=0) && hasLines)	{
				// Found mixer/device, with target data lines (USB PortMixer's don't, USB DirectAudioDevice's do.. great)
				try {
					return (TargetDataLine) AudioSystem.getTargetDataLine(af, mixers[m]);
				} catch (LineUnavailableException le) {
					le.printStackTrace();
				}
			}
		}
		return null;
	}

	// Test entry point
	public static void main(String[] args) {
		FCD test = FCD.getFCD(new ILogger() {
			public void logMsg(String s) { System.err.println(s); }
			public void statusMsg(String s) { logMsg(s); }
			public void alertMsg(String s) { logMsg(s); }
		});
		int mode = test.fcdGetMode();
		int hwvr = test.fcdGetVersion();
		int freq = test.fcdAppGetFreq();
		System.out.println("mode=" + mode + " version="+hwvr + " freq=" + freq);
		if (mode==FCD_MODE_APP) {
			System.out.println("Tuning to 100MHz with kHz API");
			mode = test.fcdAppSetFreqkHz(100000);
			System.out.println("Tuning to 107.5MHz with Hz API");
			mode = test.fcdAppSetFreq(107500000);
			StringBuffer sb = new StringBuffer("Version: ");
			test.fcdGetFwVerStr(sb);
			System.out.println(sb);
			System.out.println("Sampling audio for 5 seconds..");
			int rate = hwvr < FCD_VERSION_2 ? 96000 : 192000;
			AudioFormat af = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, (float)rate, 16, 2, 4, (float)rate, false);
			TargetDataLine dl = test.getLine(af);
			if (dl!=null) {
				// calculate buffer size for ~0.1secs of data
				byte[] buf = new byte[rate/10*4];
				try {
					FileOutputStream dmp = null;
					if (args.length>0)
						dmp = new FileOutputStream(args[0]);
					dl.open(af, rate/10*4);
					dl.start();
					for (int cnt=0; cnt<50; cnt++) {
						int len = dl.read(buf, 0, buf.length);
						if (dmp!=null)
							dmp.write(buf, 0, len);
						System.out.println("\r"+cnt+": "+len);
						System.out.flush();
					}
					dl.stop();
					dl.close();
					if (dmp!=null)
						dmp.close();
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		} else if (mode==FCD_MODE_BL) {
			System.out.println("Bootloader mode");
		} else {
			System.out.println("No FCD detected");
		}
	}
}
