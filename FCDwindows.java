// Direct HID access using HIDwin32 (aka communication.java) from KSL Embedded (www.kslemb.com)
// Author: Phil Ashby
// Date: August 2014

public class FCDwindows extends FCD {
	private HIDwin32 device;

	// FCD HID commands
	private static int FCD_CMD_LENGTH            = 65;
	private static byte FCD_CMD_BL_QUERY         = (byte)1;
	private static byte FCD_CMD_APP_RESET        = (byte)255;
	private static byte FCD_CMD_APP_SET_FREQ_KHZ = (byte)100;
	private static byte FCD_CMD_APP_SET_FREQ_HZ  = (byte)101;

	private boolean openFCD() {
		// Try opening v2 FCD 48kHz special, then standard, then v1
		device = new HIDwin32();
		device.SetVendorID((short)0x04D8);	// FCD vendor ID (Hanlincrest Ltd, via Microchip)
		device.SetProductID((short)0xFB30);	// Special 48kHz v2 build
		if (!device.getHIDHandle()) {
			device.SetProductID((short)0xFB31);	// v2.x standard (192kHz)
			if (!device.getHIDHandle()) {
				device.SetProductID((short)0xFB56);	// v1.x standard (96kHz)
				if (!device.getHIDHandle()) {
					return false;
				}
			}
		}
		return true;
	}

	private void clear(byte[] report) {
		for (int i=0; i<report.length; i++)
			report[i] = (byte)0xcc;
	}

	public FCDwindows() {
		StringBuffer fwstr = new StringBuffer();
		if (fcdGetFwVerStr(fwstr) != FME_NONE) {
			System.err.println("Detected FCD: " + fwstr);
		} else {
			System.err.println("No FCD found");
		}
	}

	// Methods implemented by O/S specific driver classes 
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
		if (openFCD()) {
			byte[] report = new byte[FCD_CMD_LENGTH];
			report[0] = 0;
			report[1] = FCD_CMD_BL_QUERY;
			if (device.HID_DEVICE_SUCCESS == device.SetFeatureReport(report, (short)report.length)) {
				clear(report);
				if (device.HID_DEVICE_SUCCESS == device.GetFeatureReport(report)) {
					// check we have command returned to us, then copy out buffer as string
					if (FCD_CMD_BL_QUERY == report[0] && 1 == report[1]) {
						for (int i=2; i<report.length; i++)
							fwver.append(report[i]>=32 ? new String(report,i,1) : ' ');
						if (fwver.toString().startsWith("FCDAPP"))
							rv = FME_APP;
						else
							rv = FME_BL;
					}
				}
			}
			device.CloseHIDDevice();
		}
		return rv;
	}

	public int fcdAppReset() {
		if (openFCD()) {
			byte[] report = new byte[FCD_CMD_LENGTH];
			report[0] = 0;
			report[1] = FCD_CMD_APP_RESET;
			device.SetFeatureReport(report, (short)report.length);
			device.CloseHIDDevice();
		}
		return FME_NONE;	// since we just reset the FCD, it's gone away
	}

	public int fcdAppSetFreqkHz(int freq) {
		int rv = FME_NONE;
		if (openFCD()) {
			rv = FME_BL;
			byte[] report = new byte[FCD_CMD_LENGTH];
			report[0] = 0;
			report[1] = FCD_CMD_APP_SET_FREQ_KHZ;
			report[2] = (byte)freq;
			report[3] = (byte)(freq>>8);
			report[4] = (byte)(freq>>16);
			if (device.HID_DEVICE_SUCCESS == device.SetFeatureReport(report, (short)report.length)) {
				clear(report);
				if (device.HID_DEVICE_SUCCESS == device.GetFeatureReport(report)) {
					if (FCD_CMD_APP_SET_FREQ_KHZ == report[0] && 1 == report[1])
						rv = FME_APP;
				}
			}
			device.CloseHIDDevice();
		}
		return rv;
	}

	public int fcdAppSetFreq(int freq) {
		int rv = FME_NONE;
		if (openFCD()) {
			rv = FME_BL;
			byte[] report = new byte[FCD_CMD_LENGTH];
			report[0] = 0;
			report[1] = FCD_CMD_APP_SET_FREQ_HZ;
			report[2] = (byte)freq;
			report[3] = (byte)(freq>>8);
			report[4] = (byte)(freq>>16);
			report[5] = (byte)(freq>>24);
			if (device.HID_DEVICE_SUCCESS == device.SetFeatureReport(report, (short)report.length)) {
				clear(report);
				if (device.HID_DEVICE_SUCCESS == device.GetFeatureReport(report)) {
					if (FCD_CMD_APP_SET_FREQ_KHZ == report[0] && 1 == report[1])
						rv = FME_APP;
				}
			}
			device.CloseHIDDevice();
		}
		return rv;
	}
}

