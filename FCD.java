/***************************************************************************
 *  This file is part of java-sdr.
 *
 *  CopyRight (C) 2011-2014  Phil Ashby
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

// Java binding to libfcd.so/fcd.dll using JNA (http://jna.java.net) and Java Sound API

//package uk.org.funcube.fcdapi;

import com.sun.jna.Platform;
import com.sun.jna.Library;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.Structure;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import java.net.URI;
import java.util.HashMap;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;

import java.io.FileOutputStream;

public class FCD {

	// Declare the methods of the underlying C library as an interface
	// Keep in step with fcd.h!

	// Return consts..
	public static final int FME_NONE = 0;
	public static final int FME_BL = 1;
	public static final int FME_APP = 2;

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

	public interface libfcd extends Library {
		//fcdAppGetParam
		//fcdAppSetParam
		//fcdBlErase
		//fcdBlReset
		//fcdBlSaveFirmware
		//fcdBlSaveFirmwareProg
		//fcdBlVerifyFirmware
		//fcdBlVerifyFirmwareProg
		//fcdBlWriteFirmware
		//fcdBlWriteFirmwareProg
		//fcdGetCaps
		//fcdGetCapsStr
		//fcdGetDeviceInfo
		int fcdGetMode();
		int fcdGetVersion();
		int fcdGetFwVerStr(byte[] str);
		int fcdAppReset();
		int fcdAppSetFreqkHz(int nFreq);
		int fcdAppSetFreq(int nFreq, int[] rFreq);
		/*int fcdAppGetFreq(int[] rFreq);
		int fcdAppSetLna(byte enable);
		int fcdAppGetLna(byte[] rEnable);
		int fcdAppSetRfFilter(int filter);
		int fcdAppGetRfFilter(int[] rFilter);
		int fcdAppSetMixerGain(byte enable);
		int fcdAppGetMixerGain(byte[] rEnable);
		int fcdAppSetIfGain(byte gain);
		int fcdAppGetIfGain(byte[] rGain);
		int fcdAppSetIfFilter(int filter);
		int fcdAppGetIfFilter(int[] rFilter);
		int fcdAppSetBiasTee(byte enable);
		int fcdAppGetBiasTee(byte[] rEnable);*/

	}

	// instantiate a private static instance of the library
	private static libfcd inst;
	static {
		try {
			// 0. Determine if we should load 32 or 64-bit native code..
			String dll = "fcd";
			if (Platform.is64Bit())
				dll = "fcd64";
			// Some ugly path hackery to avoid LD_LIBRARY_PATH et al:
			// 1. Get a URL for a known file in our jar file, extract path component..
			String pth = FCD.class.getResource("/FCD.class").getPath();
			//System.err.println("Res path="+pth);
			// 2. Decode via URI incase we have whitespace in the path, extract next path component..
			pth = new URI(pth).getPath();
			//System.err.println("Dec path="+pth);
			// 3. Trim off trailing text to get our installation directory.
			int i1 = pth.indexOf('!');
			int i2 = pth.lastIndexOf('/', i1>0 ? i1 : pth.length());
			if (i2>0) {
				pth = pth.substring(0, i2+1);
				System.err.println("native library path: " + pth);
				NativeLibrary.addSearchPath(dll, pth);
			}
			HashMap opts = null;
			if (Platform.isWindows())
				opts = new HashMap(){{
					put( Library.OPTION_FUNCTION_MAPPER, StdCallLibrary.FUNCTION_MAPPER );
				}};
			inst = (libfcd) Native.loadLibrary(dll, libfcd.class, opts);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Provide synchronized wrappers for library
	public synchronized int fcdGetVersion() {
		return inst.fcdGetVersion();
	}

	public synchronized int fcdGetMode() {
		return inst.fcdGetMode();
	}

	public synchronized int fcdGetFwVerStr(StringBuffer ver) {
		byte[] buf = new byte[10];		// XXX:Beware undocumented buffer size in fcd.h/c!
		int rv = inst.fcdGetFwVerStr(buf);
		if (FME_APP == rv)
			ver.append(Native.toString(buf));
		return rv;
	}

	public synchronized int fcdAppReset() {
		return inst.fcdAppReset();
	}

	public synchronized int fcdAppSetFreqkHz(int nFreq) {
		return inst.fcdAppSetFreqkHz(nFreq);
	}

	public synchronized int fcdAppSetFreq(int nFreq, int[] rFreq) {
		int[] buf = new int[1];
		int rv = inst.fcdAppSetFreq(nFreq, buf);
		if (rFreq!=null && rFreq.length>0)
			rFreq[0] = buf[0];
		return rv;
	}

/*
	public synchronized int fcdAppGetFreq(int[] rFreq) {
		int[] buf = new int[1];
		int rv = inst.fcdAppGetFreq(buf);
		if (rFreq!=null && rFreq.length>0)
			rFreq[0] = buf[0];
		return rv;
	}

	public synchronized int fcdAppSetLna(byte enable) {
		return inst.fcdAppSetLna(enable);
	}

	public synchronized int fcdAppGetLna(byte[] rEnable) {
		byte[] buf = new byte[1];
		int rv = inst.fcdAppGetLna(buf);
		if (rEnable!=null && rEnable.length>0)
			rEnable[0] = buf[0];
		return rv;
	}

	public synchronized int fcdAppSetRfFilter(int filter) {
		return inst.fcdAppSetRfFilter(filter);
	}

	public synchronized int fcdAppGetRfFilter(int[] rFilter) {
		int[] buf = new int[1];
		int rv = inst.fcdAppGetRfFilter(buf);
		if (rFilter!=null && rFilter.length>0)
			rFilter[0] = buf[0];
		return rv;
	}

	public synchronized int fcdAppSetMixerGain(byte enable) {
		return inst.fcdAppSetMixerGain(enable);
	}

	public synchronized int fcdAppGetMixerGain(byte[] rEnable) {
		byte[] buf = new byte[1];
		int rv = inst.fcdAppGetMixerGain(buf);
		if (rEnable!=null && rEnable.length>0)
			rEnable[0] = buf[0];
		return rv;
	}

	public synchronized int fcdAppSetIfGain(byte gain) {
		return inst.fcdAppSetIfGain(gain);
	}

	public synchronized int fcdAppGetIfGain(byte[] rGain) {
		byte[] buf = new byte[1];
		int rv = inst.fcdAppGetIfGain(buf);
		if (rGain!=null && rGain.length>0)
			rGain[0] = buf[0];
		return rv;
	}

	public synchronized int fcdAppSetIfFilter(int filter) {
		return inst.fcdAppSetIfFilter(filter);
	}

	public synchronized int fcdAppGetIfFilter(int[] rFilter) {
		int[] buf = new int[1];
		int rv = inst.fcdAppGetIfFilter(buf);
		if (rFilter!=null && rFilter.length>0)
			rFilter[0] = buf[0];
		return rv;
	}

	public synchronized int fcdAppSetBiasTee(byte enable) {
		return inst.fcdAppSetBiasTee(enable);
	}

	public synchronized int fcdAppGetBiasTee(byte[] rEnable) {
		byte[] buf = new byte[1];
		int rv = inst.fcdAppGetBiasTee(buf);
		if (rEnable!=null && rEnable.length>0)
			rEnable[0] = buf[0];
		return rv;
	}
*/
	// Audio interface: returns a javax.sound.sampled.TargetDataLine suitable for recording I/Q data.
	// Returns null if FCD is not found in audio subsystem, or format is unsupported.
	public TargetDataLine getLine(AudioFormat af) {
		Mixer.Info[] mixers = AudioSystem.getMixerInfo();
		int m;
		for (m=0; m<mixers.length; m++) {
			Mixer mix = AudioSystem.getMixer(mixers[m]);
			// Test for a FUNcube Dongle, unfortunately by string matching - thanks Java..
			// addendum: Linux puts the USB name in description field, Windows in name field.. sheesh.
System.err.println(mixers[m].getDescription() + '/' + mixers[m].getName());
			if (mixers[m].getDescription().indexOf("FUNcube Dongle")>=0 ||
			    mixers[m].getName().indexOf("FUNcube Dongle")>=0) {
				// Found mixer/device, try and get a capture line in specified format
				DataLine.Info dl = new DataLine.Info(TargetDataLine.class, af);
				try {
					return (TargetDataLine) mix.getLine(dl);
				} catch (LineUnavailableException le) {
					le.printStackTrace();
				}
			}
		}
		return null;
	}

	// Test entry point
	public static void main(String[] args) {
		FCD test = new FCD();
		int mode = test.fcdGetMode();
		int hwvr = test.fcdGetVersion();
		System.out.println("mode=" + mode + " version="+hwvr);
		if (mode==FCD.FME_APP) {
			System.out.println("Tuning to 100MHz");
			mode = test.fcdAppSetFreqkHz(100000);
			System.out.println("mode=" + mode);
			StringBuffer sb = new StringBuffer("Version: ");
			test.fcdGetFwVerStr(sb);
			System.out.println(sb);
			System.out.println("Sampling audio for 5 seconds..");
			AudioFormat af = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, (float)192000, 16, 2, 4, (float)192000, false);
			TargetDataLine dl = test.getLine(af);
			if (dl!=null) {
				byte[] buf = new byte[76800];
				try {
					FileOutputStream dmp = null;
					if (args.length>0)
						dmp = new FileOutputStream(args[0]);
					dl.open(af, 76800);
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
		} else if (mode==FCD.FME_BL) {
			System.out.println("Bootloader mode");
		}
	}
}
 
