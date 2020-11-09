package com.ashbysoft.sdr;
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

import java.io.FileOutputStream;
import java.io.IOException;

import purejavahidapi.*;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.Line;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;

/**
 * FUNcube Dongle interface, uses PureJavaHidApi for control & Javax.sound for
 * audio (aka I/Q) samples
 */
public class FCD implements InputReportListener {
	// Return consts..
	public static final int FME_NONE = 0;
	public static final int FME_BL = 1;
	public static final int FME_APP = 2;
	// fcdGetVersion return values..
	public static final int FCD_VERSION_NONE = 0;/*!< No FCD detected */
	public static final int FCD_VERSION_1    = 1;/*!< Original FCD. */
	public static final int FCD_VERSION_1_1  = 2;/*!< Bias T added in 1.1 */
	public static final int FCD_VERSION_2    = 3;/*!< Pro+ */
	public static final int FCD_VERSION_UNK  = 4;/* Sorry no idea! */

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

	// FCD HID commands
	private static final int FCD_CMD_TIMEOUT           = 5;
	private static final int FCD_CMD_LENGTH            = 64;
	private static final byte FCD_CMD_BL_QUERY         = (byte)1;
	private static final byte FCD_CMD_APP_RESET        = (byte)255;
	private static final byte FCD_CMD_APP_SET_FREQ_KHZ = (byte)100;
	private static final byte FCD_CMD_APP_SET_FREQ_HZ  = (byte)101;

	// Report listener
	private byte[] response = null;
	private Semaphore reshere = new Semaphore(0, true);
	public void onInputReport(HidDevice source,byte reportID,byte[] reportData,int reportLength) {
		// ensure we will not overwrite an existing response (we are the only writer!)
		if (response!=null) {
			String err = String.format("FCD: HID report receive overrun: Path=%s, reportID=%d",
				source.getHidDeviceInfo().getPath(), reportID);
			msgout.statusMsg(err);
			System.err.println(err);
			return;
		}
		// save response, wake up any threads..
		response = reportData;
		reshere.release();
	}

	// Private constructor (must use factory)
	private MessageOut msgout;
	private HidDevice device = null;
	private FCD(MessageOut m, HidDeviceInfo path) {
		msgout = m;
		msgout.logMsg(String.format("FCD: opening %s (%s)", path.getPath(), path.getProductString()));
		try {
			device = PureJavaHidApi.openDevice(path);
			device.setInputReportListener(this);
		} catch (IOException ex) {
			String err = String.format("FCD: Exception opening device: %s", ex.toString());
			msgout.statusMsg(err);
			System.err.println(err);
		}
	}
	// Methods called by client
	public int fcdGetVersion() {
		StringBuffer fwstr = new StringBuffer();
		switch (fcdGetFwVerStr(fwstr)) {
		case FME_NONE:
			return FCD_VERSION_NONE;
		case FME_BL:
			// In bootloader mode, we only know v1.x or v2.x
			if (fwstr.toString().startsWith("FCDBL"))
				return FCD_VERSION_1;
			else if (fwstr.toString().startsWith("FCD2BL"))
				return FCD_VERSION_2;
			break;
		case FME_APP:
			// In application mode, we can parse for 1.0/1.1 or 2.x
			if (fwstr.toString().startsWith("FCDAPP")) {
				if (fwstr.substring(17).startsWith("1.0"))
					return FCD_VERSION_1;
				else if (fwstr.substring(17).startsWith("1.1"))
					return FCD_VERSION_1_1;
				else if (fwstr.substring(17).startsWith("2.0"))
					return FCD_VERSION_2;
			}
			break;
		default:
			// Eh? assume unknown device..
		}
		return FCD_VERSION_UNK;
	}
	public int fcdGetMode() {
		StringBuffer fwstr = new StringBuffer();
		return fcdGetFwVerStr(fwstr);
	}
	public int fcdGetFwVerStr(StringBuffer fwver) {
		int rv = FME_NONE;
		String err = null;
		if (device!=null) {
			byte[] report = new byte[FCD_CMD_LENGTH];
			report[0] = FCD_CMD_BL_QUERY;
			// send the report (always ID 0)..
			device.setOutputReport((byte)0, report, report.length);
			// wait for response..
			try {
				if (reshere.tryAcquire(FCD_CMD_TIMEOUT, TimeUnit.SECONDS)) {
					byte[] res = response;
					response = null;
					// check we got the answer to our request
					if (res[0] == FCD_CMD_BL_QUERY && res[1] == (byte)1) {
						for (int i=2; i<report.length; i++)
							fwver.append(res[i]>=32 ? new String(res,i,1) : ' ');
						if (fwver.toString().startsWith("FCDAPP"))
							rv = FME_APP;
						else
							rv = FME_BL;
					} else {
						err = String.format("FCD: Got invalid HID response: %d (expecting %d)\n",
							report[0], FCD_CMD_BL_QUERY);
					}
				} else {
					// device went away?
					err = String.format("FCD: Timeout waiting for HID response\n");
				}
			} catch (InterruptedException ex) {
				err = String.format("FCD: Interrupted waiting for response: %s", ex.toString());
			}
		}
		if (err!=null) {
			msgout.statusMsg(err);
			System.err.println(err);
		}
		return rv;
	}
	public int fcdAppReset() {
		if (device!=null) {
			byte[] report = new byte[FCD_CMD_LENGTH];
			report[0] = FCD_CMD_APP_RESET;
			device.setOutputReport((byte)0, report, report.length);
			// Device now resets without acknowledgement, we drop everything
			device.close();
			device = null;
		}
		return FME_NONE;
	}
	public int fcdAppSetFreqkHz(int freq) {
		int rv = FME_NONE;
		String err = null;
		if (device!=null) {
			byte[] report = new byte[FCD_CMD_LENGTH];
			report[0] = FCD_CMD_APP_SET_FREQ_KHZ;
			report[1] = (byte)freq;
			report[2] = (byte)(freq>>8);
			report[3] = (byte)(freq>>16);
			// send the report (always ID 0)..
			device.setOutputReport((byte)0, report, report.length);
			// wait for response..
			try {
				if (reshere.tryAcquire(FCD_CMD_TIMEOUT, TimeUnit.SECONDS)) {
					byte[] res = response;
					response = null;
					// check we got the answer to our request
					if (res[0] == FCD_CMD_APP_SET_FREQ_KHZ && res[1] == (byte)1) {
						rv = FME_APP;
					} else {
						err = String.format("FCD: Got invalid HID response: %d (expecting %d)\n",
							report[0], FCD_CMD_BL_QUERY);
					}
				} else {
					// device went away?
					err = String.format("FCD: Timeout waiting for HID response\n");
				}
			} catch (InterruptedException ex) {
				err = String.format("FCD: Interrupted waiting for response: %s", ex.toString());
			}
		}
		if (err!=null) {
			msgout.statusMsg(err);
			System.err.println(err);
		}
		return rv;
	}
	public int fcdAppSetFreq(int freq) {
		int rv = FME_NONE;
		String err = null;
		if (device!=null) {
			byte[] report = new byte[FCD_CMD_LENGTH];
			report[0] = FCD_CMD_APP_SET_FREQ_HZ;
			report[1] = (byte)freq;
			report[2] = (byte)(freq>>8);
			report[3] = (byte)(freq>>16);
			report[4] = (byte)(freq>>24);
			// send the report (always ID 0)..
			device.setOutputReport((byte)0, report, report.length);
			// wait for response..
			try {
				if (reshere.tryAcquire(FCD_CMD_TIMEOUT, TimeUnit.SECONDS)) {
					byte[] res = response;
					response = null;
					// check we got the answer to our request
					if (res[0] == FCD_CMD_APP_SET_FREQ_HZ && res[1] == (byte)1) {
						rv = FME_APP;
					} else {
						err = String.format("FCD: Got invalid HID response: %d (expecting %d)\n",
							report[0], FCD_CMD_BL_QUERY);
					}
				} else {
					// device went away?
					err = String.format("FCD: Timeout waiting for HID response\n");
				}
			} catch (InterruptedException ex) {
				err = String.format("FCD: Interrupted waiting for response: %s", ex.toString());
			}
		}
		if (err!=null) {
			msgout.statusMsg(err);
			System.err.println(err);
		}
		return rv;
	}

	// Factory method
	private static FCD cached = null;
	public static FCD getFCD(MessageOut m) { return getFCD(m, null); }
	public static synchronized FCD getFCD(MessageOut m, String partialPath) {
		if (cached!=null)
			return cached;
		// enumerate all HID devices, match partialPath if supplied, else first FUNcube Dongle
		// https://github.com/nyholku/purejavahidapi
		List<HidDeviceInfo> devs = PureJavaHidApi.enumerateDevices();
		HidDeviceInfo found = null;
		for (HidDeviceInfo info: devs) {
			m.logMsg(String.format("FCD: VID = 0x%04X PID = 0x%04X Manufacturer = %s Product = %s Path = %s, Serial = %s",
			info.getVendorId(),
			info.getProductId(),
			info.getManufacturerString(),
			info.getProductString(),
			info.getPath(),
			info.getSerialNumberString()));
			if (partialPath!=null) {
				if (info.getPath().indexOf(partialPath)>=0)
					found = info;
			} else {
				if (info.getProductString().indexOf("FUNcube Dongle")>=0)
					found = info;
			}
		}
		if (found!=null)
			cached = new FCD(m, found);
		return cached;
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
			msgout.logMsg("FCD: mixer: (" + mix.getClass().getName() + "): " + mixers[m].getDescription() + '/' + mixers[m].getName());
			boolean hasLines = false;
			for (Line.Info inf: mix.getTargetLineInfo()) {
				msgout.logMsg(" - target line: " + inf.toString());
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
		FCD test = FCD.getFCD(new MessageOut() {
			public void logMsg(String s) { System.err.println(s); }
			public void statusMsg(String s) { logMsg(s); }
		});
		int mode = test.fcdGetMode();
		int hwvr = test.fcdGetVersion();
		System.out.println("mode=" + mode + " version="+hwvr);
		if (mode==FCD.FME_APP) {
			System.out.println("mode=" + mode);
			System.out.println("Tuning to 100MHz with kHz API");
			mode = test.fcdAppSetFreqkHz(100000);
			System.out.println("Tuning to 107.5MHz with Hz API");
			mode = test.fcdAppSetFreqkHz(107500000);
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
		} else if (mode==FCD.FME_BL) {
			System.out.println("Bootloader mode");
		}
	}
}
