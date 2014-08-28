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

// Base class of all FCD driver classes, factory for O/S specific drivers and provides Audio I/O

import java.io.FileOutputStream;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;


public abstract class FCD {
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

	// Methods implemented by O/S specific driver classes 
	public abstract int fcdGetVersion();
	public abstract int fcdGetMode();
	public abstract int fcdGetFwVerStr(StringBuffer fwver);
	public abstract int fcdAppReset();
	public abstract int fcdAppSetFreqkHz(int freq);
	public abstract int fcdAppSetFreq(int freq);

	// Factory method
	public static FCD getFCD() {
		// Linux or Windows Sir?
		String os = System.getProperty("os.name").toLowerCase();
		if (os.indexOf("win") >= 0) {
			return new FCDwindows();
		} else if (os.indexOf("lin") >= 0) {
			return new FCDlinux();
		} else {
			throw new RuntimeException("unsupported operating system - sorry");
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
		FCD test = FCD.getFCD();
		int mode = test.fcdGetMode();
		int hwvr = test.fcdGetVersion();
		System.out.println("mode=" + mode + " version="+hwvr);
		if (mode==FCD.FME_APP) {
			System.out.println("mode=" + mode);
			System.out.println("Tuning to 100MHz");
			mode = test.fcdAppSetFreqkHz(100000);
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
