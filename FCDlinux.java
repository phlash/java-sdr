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

// Java binding to libfcd.so/fcd.dll using JNA (http://jna.java.net)

//package uk.org.funcube.fcdapi;

import com.sun.jna.Platform;
import com.sun.jna.Library;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.Structure;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import java.net.URI;
import java.util.HashMap;

import java.io.FileOutputStream;

public class FCDlinux extends FCD {

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
		int fcdAppSetFreq(int nFreq);
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
			if (Platform.isWindows()) {
				HashMap opts = new HashMap(){{
					put( Library.OPTION_FUNCTION_MAPPER, StdCallLibrary.FUNCTION_MAPPER );
				}};
				inst = (libfcd) Native.loadLibrary(dll, libfcd.class, opts);
			} else {
				inst = (libfcd) Native.loadLibrary(dll, libfcd.class);
			}
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
		byte[] buf = new byte[70];		// XXX:Beware undocumented buffer size in fcd.h/c!
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

	public synchronized int fcdAppSetFreq(int nFreq) {
		return inst.fcdAppSetFreq(nFreq);
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
}
 
