/* Copyright (c)2010, KSL Embedded (www.kslemb.com)
 * All rights reserved. 
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions 
 * are met: 
 * 1. Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer. 
 * 2. Redistributions in binary form must reproduce the above copyright 
 *    notice, this list of conditions and the following disclaimer in the 
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the KSL EMBEDDED nor the names of its contributors 
 *    may be used to endorse or promote products derived from this software 
 *    without specific prior written permission.  
 *
 * THIS SOFTWARE IS PROVIDED BY THE KSL EMBEDDED AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR 
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE KSL ENBEDDED OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR 
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/**
 * USB HID Communication class for Java, based on JNA (provide easy access to native shared libraries).
 *
 * Copyright (c)2010 KSL Embedded. 
 * Java class for communication with USB HID devices.
 * This version of the software (Windows release) includes next methods:
 * 1. SetVendorID() - set Vendor device Identifier used to open USB HID device.
 * 2. SetProductID() - set Vendor product Identifier used to open USB HID device.
 * 3. getHIDHandle() - retrieving handle to the HID device with specified VID & PID.
 * 4. isOpened() - check if USB HID device is opened.
 * 5. SetFeatureReport() - send SetFeature request to the device via Control pipe.
 * 6. GetFeatuteReport() - receive Feature request from the device via Control pipe.
 * 7. CloseHIDDevice() - close previously opened USB HID device.
 * 
 * @version 1.0
 * @author Konstantin Vovk <ksl@kslemb.com>
 * @author Vladimir Gapoyants <gp@kslemb.com>
 */

//package hid;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.*;  
import com.sun.jna.ptr.*;  
import com.sun.jna.win32.*;  
import com.sun.jna.platform.win32.*;

public class HIDwin32 {

    /** 
     * HID Communication class Return Codes 
     */
    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * Last operation executed successfully 
     */
    byte HID_DEVICE_SUCCESS             = 0x00;
    /** 
     * USB HID device specified by the VID and PID not detected in the system 
     */
    byte HID_DEVICE_NOT_FOUND           = 0x01;
    /** 
     * HID Device not opened
     */
    byte HID_DEVICE_NOT_OPENED          = 0x02;
    /** 
     * HID Device alredy opened 
     */
    byte HID_DEVICE_ALREADY_OPENED      = 0x03;
    /** 
     * Transfer timeout for Read/Write operation 
     */
    byte HID_DEVICE_TRANSFER_TIMEOUT    = 0x04;
    /** 
     * Error detected in last data transaction 
     */
    byte HID_DEVICE_TRANSFER_FAILED     = 0x05;
    /** 
     * Can't receive HID device description 
     */
    byte HID_DEVICE_CANNOT_GET_HID_INFO = 0x06;
    /** 
     * HID device invalid handle value 
     */
    byte HID_DEVICE_HANDLE_ERROR        = 0x07;
    /** 
     * HID device invalid supplied buffer size 
     */
    byte HID_DEVICE_INVALID_BUFFER_SIZE = 0x08;
    
    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * USB HID Device Vendor ID 
     */
    private short vendorID;
    /** 
     * USB HID Device Product ID 
     */
    private short productID;

    //-------------------------------------------------------------------------------------------------------------------    
    /** 
     * Set VID used to open USB HID device 
     * 
     * @param   VendorID    Vendor ID used to open USB HID device.
     */
    public void SetVendorID(short VendorID) {
        vendorID = VendorID;
    }

    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * Set PID used to open USB HID device 
     * 
     * @param   ProductID    Product ID used to open USB HID device.
     */
    public void SetProductID(short ProductID) {
        productID = ProductID;
    }
    
    /** 
     * HID device Input/Output/Feature Report buffers length 
     */
    //-------------------------------------------------------------------------------------------------------------------    
    /** 
     * Input Report buffer length. 
     */
    private short InputReportLength;
    /** 
     * Output Report buffer length. 
     */
    private short OutputReportLength;
    /** 
     * Feature Report buffer length. 
     */
    private short FeatureReportLength;

    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * Retrieve length of the HID Input Report.
     */ 
    public short GetInputReportLength() {
        return InputReportLength;
    }
    
    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * Retrieve length of the HID Output Report 
     */ 
    public short GetOutputReportLength() {
        return OutputReportLength;
    }

    //-------------------------------------------------------------------------------------------------------------------    
    /** 
     * Retrieve length of the HID Feature Report 
     */ 
    public short GetFeatureReportLength() {
        return FeatureReportLength;
    }

    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * Handle to the opened USB HID Device 
     */
    private WinNT.HANDLE HIDHandle;
    
    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * Windows GUID structure.
     */
    public static class GUID extends Structure {
        public int      Data1;
        public short    Data2;
        public short    Data3;
        public byte     Data4[] = new byte[8];
        protected List getFieldOrder() {
            return Arrays.asList(new String[] { "Data1", "Data2", "Data3", "Data4" });
        }
}

    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * Handle definition.
     */
    public static class HDEVINFO extends WinNT.HANDLE {
    }


    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * SP_DEVINFO_DATA structure defines a device instance that is a member of a device information set. 
     */
    public static class SP_DEVINFO_DATA extends Structure {
        public SP_DEVINFO_DATA() {
            ClassGuid = new GUID();
        }
        /** 
         * The size, in bytes, of the SP_DEVINFO_DATA structure. 
         */
        public int  cbSize;
        /** 
         * Device setup class GUID 
         */ 
        public GUID ClassGuid;
        /** 
         * Handle to the device instance (handle to the devnode) 
         */
        public int  DevInst;
        /** 
         * Reserved. For internal use only. 
         */
        public int  Reserved;

         protected List getFieldOrder() {
            return Arrays.asList(new String[] { "cbSize", "ClassGuid", "DevInst", "Reserved" });
        }
   }

    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * An SP_DEVICE_INTERFACE_DATA structure defines a device interface in a device information set. 
     */
    public static class SP_DEVICE_INTERFACE_DATA extends Structure {
        public SP_DEVICE_INTERFACE_DATA() {
            InterfaceClassGuid = new GUID();
        }
        /** 
         * The size, in bytes, of the SP_DEVICE_INTERFACE_DATA structure. 
         */
        public int  cbSize;
        /** 
         * The GUID for the class to which the device interface belongs. 
         */
        public GUID InterfaceClassGuid;
        /** 
         * Can be one or more of the following:
         *  SPINT_ACTIVE    - The interface is active (enabled).
         *  SPINT_DEFAULT   - The interface is the default interface for the device class.
         *  SPINT_REMOVED   - The interface is removed.  
         */
        public int  Flags;
        /** 
         * Reserved. Do not use. 
         */
        public int  Reserved;

         protected List getFieldOrder() {
            return Arrays.asList(new String[] { "cbSize", "InterfaceClassGuid", "Flags", "Reserved" });
        }
   }

    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * An SP_DEVICE_INTERFACE_DETAIL_DATA structure contains the path for a device interface. 
     */
    public static class SP_DEVICE_INTERFACE_DETAIL_DATA extends Structure {
        /** 
         * The size, in bytes, of the fixed portion of the SP_DEVICE_INTERFACE_DETAIL_DATA structure. 
         */
        public int cbSize;
        /** 
         * A NULL-terminated string that contains the device interface path. This path can be passed to Win32 functions CreateFile. 
         */
        public char[] devicePath = new char[1];
        public SP_DEVICE_INTERFACE_DETAIL_DATA() {
            setAlignType(Structure.ALIGN_NONE);
        }        

         protected List getFieldOrder() {
            return Arrays.asList(new String[] { "cbSize", "devicePath" });
        }
    }

    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * The HIDD_ATTRIBUTES structure contains vendor information about a HIDClass device. 
     */
    public static class HIDD_ATTRIBUTES extends Structure {
        /** 
         * Specifies the size, in bytes, of a HIDD_ATTRIBUTES structure. 
         */
        public int   Size;
        /** 
         * Specifies a HID device's vendor ID. 
         */
        public short VendorID;
        /** 
         * Specifies a HID device's product ID. 
         */
        public short ProductID;
        /** 
         * Specifies the manufacturer's revision number for a HIDClass device. 
         */
        public short VersionNumber;

         protected List getFieldOrder() {
            return Arrays.asList(new String[] { "Size", "VendorID", "ProductID", "VersionNumber" });
        }
    }

    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * The HIDP_CAPS structure contains information about a top-level collection's capability (defined by the usage, reports, link, and controls). 
     */
    public static class HIDP_CAPS extends Structure {
        /** 
         * Specifies a top-level collection's usage ID. 
         */
        public short Usage;
        /** 
         * Specifies the top-level collection's usage page. 
         */
        public short UsagePage;
        /** 
         * Specifies the maximum size, in bytes, of the input reports including the report ID. 
         */
        public short InputReportByteLength;
        /** 
         * Specifies the maximum size, in bytes, of all the output reports including the report ID. 
         */
        public short OutputReportByteLength;
        /** 
         * Specifies the maximum length, in bytes, of all the feature reports including the report ID. 
         */
        public short FeatureReportByteLength;
        /** 
         * Reserved for internal system use. 
         */
        public short Reserved[] = new short[17];
        /** 
         * Specifies the number of HIDP_LINK_COLLECTION_NODE structures, returned for this top-level collection by HidP_GetLinkCollectionNodes. 
         */
        public short NumberLinkConnectionNodes;
        /** 
         * Specifies the number of input HIDP_BUTTON_CAPS structures that HidP_GetButtonCaps returns. 
         */
        public short NumberInputButtonCaps;
        /** 
         * Specifies the number of input HIDP_VALUE_CAPS structures that HidP_GetValueCaps returns. 
         */
        public short NumberInputValueCaps;
        /** 
         * Specifies the number of data indices assigned to buttons and values in all input reports. 
         */
        public short NumberInputDataIndices;
        /** 
         * Specifies the number of output HIDP_BUTTON_CAPS structures that HidP_GetButtonCaps returns. 
         */
        public short NumberOutputButtonCaps;
        /** 
         * Specifies the number of output HIDP_VALUE_CAPS structures that HidP_GetValueCaps returns. 
         */
        public short NumberOutputValueCaps;
        /** 
         * Specifies the number of data indices assigned to buttons and values in all output reports. 
         */
        public short NumberOutputDateIndices;
        /** 
         * Specifies the total number of feature HIDP_BUTTONS_CAPS structures that HidP_GetButtonCaps returns. 
         */
        public short NumberFeatureButtonCaps;
        /** 
         * Specifies the total number of feature HIDP_BUTTONS_CAPS structures that HidP_GetButtonCaps returns. 
         */
        public short NumberFeatureValueCaps;
        /** 
         * Specifies the number of data indices assigned to buttons and values in all feature reports. 
         */
        public short NumberFeatureDataIndices;

         protected List getFieldOrder() {
            return Arrays.asList(new String[] {
					"Usage", "UsagePage", "InputReportByteLength", "OutputReportByteLength",
            	"FeatureReportByteLength", "Reserved", "NumberLinkConnectionNodes",
				   "NumberInputButtonCaps", "NumberInputValueCaps", "NumberInputDataIndices",
					"NumberOutputButtonCaps", "NumberOutputValueCaps", "NumberOutputDateIndices",
					"NumberFeatureButtonCaps", "NumberFeatureValueCaps", "NumberFeatureDateIndices" });
        }
    }

    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * Declare a Java interface that holds the native "hid.dll" library methods by extending the W32API interface. 
     */
    public interface Hid extends WinNT {
        /** 
         * Load a library interface from the "hid.dll" shared library 
         */
        Hid INSTANCE = (Hid) Native.loadLibrary("hid", Hid.class, W32APIOptions.UNICODE_OPTIONS);
        
        /** 
         * Method returns the device interface GUID for HIDClass devices. 
         */
        void HidD_GetHidGuid(GUID Guid);
        /** 
         * Method returns the attributes of a specified top-level collection. 
         */
        int HidD_GetAttributes(WinNT.HANDLE Handle, HIDD_ATTRIBUTES HIDAttributes);
        /** 
         * Method returns a top-level collection's preparsed data (report descriptor associated with a top-level collection) 
         */
        int HidD_GetPreparsedData(WinNT.HANDLE Handle, IntByReference HIDPreparsedData);
        /** 
         * Method returns a top-level collection's HIDP_CAPS structure. 
         */
        int HidP_GetCaps(int HIDPreparsedData, HIDP_CAPS HIDCapabilities);
        /** 
         * Send a feature report to the specified top-level collection. 
         */
        boolean HidD_SetFeature(WinNT.HANDLE HidDeviceObject, byte[] ReportBuffer, int ReportBufferLength);
        /** 
         * Get a feature report from a specified top-level collection. 
         */
        boolean HidD_GetFeature (WinNT.HANDLE HidDeviceObject, byte[] ReportBuffer, int ReportBufferLength);
    }

    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * Declare a Java interface that holds the native "setupapi.dll" library methods by extending the W32API interface. 
     */
    public interface Setupapi extends WinNT {
        /** 
         * Load a library interface from the "setupapi.dll" shared library 
         */
        Setupapi INSTANCE = (Setupapi) Native.loadLibrary("setupapi", Setupapi.class, W32APIOptions.UNICODE_OPTIONS);
       
       /** 
        * SetupDiGetClassDevs Flag constants.
        * 
        * Specifies control options that filter the device information elements that are added to the device information set. 
        */
        /** 
         * Return only the device that is associated with the system default device interface, if one is set, for the specified device interface classes. 
         */
        public static int DIGCF_DEFAULT         = 0x00000001; 
        /** 
         * Return only devices that are currently present in a system. 
         */
        static int DIGCF_PRESENT                = 0x00000002;
        /** 
         * Return a list of installed devices for all device setup classes or all device interface classes. 
         */
        public static int DIGCF_ALLCLASSES      = 0x00000004; 
        /** 
         * Return only devices that are a part of the current hardware profile. 
         */
        public static int DIGCF_PROFILE         = 0x00000008;
        /** 
         * Return devices that support device interfaces for the specified device interface classes. 
         * 
         * This flag must be set in the Flags parameter if the Enumerator parameter specifies a device instance ID. 
         */
        public static int DIGCF_DEVICEINTERFACE = 0x00000010; 
        
        /** 
         * Method returns a device information set that contains all devices of a specified class. 
         */
        HDEVINFO SetupDiGetClassDevs(GUID Guid, String Enumerator, WinDef.HWND Parent, int Flags);
        /** 
         * Method returns a context structure for a device interface element of a device information set. 
         * 
         * Each call returns information about one device interface.
         * The function can be called repeatedly to get information about several 
         * interfaces exposed by one or more devices. 
         */
        int SetupDiEnumDeviceInterfaces(HDEVINFO DeviceInfoSet, SP_DEVINFO_DATA DeviceInfoData, GUID Guid, int MemberIndex, SP_DEVICE_INTERFACE_DATA DeviceInterfaceData);
        /** 
         * Method returns details about a particular device interface. 
         */
        int SetupDiGetDeviceInterfaceDetail(HDEVINFO DeviceInfoSet, SP_DEVICE_INTERFACE_DATA DeviceInterfaceData, SP_DEVICE_INTERFACE_DETAIL_DATA DeviceInterfaceDetailData, int DeviceInterfaceDetailDataSize, IntByReference RequiredSize, SP_DEVINFO_DATA DeviceInfoData);
        /** 
         * Method destroys a device information set and frees all associated memory. 
         */
        int SetupDiDestroyDeviceInfoList(HDEVINFO  DeviceInfoSet);
    }

    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * Declare a Java interface that holds the native "kernel32.dll" library methods by extending the W32API interface. 
     */
    public interface Kernel32RW extends Kernel32 {

        Kernel32 INSTANCE = (Kernel32RW) Native.loadLibrary("kernel32", Kernel32RW.class, W32APIOptions.UNICODE_OPTIONS);

        /** 
         * CreateFile constants 
         */
        /** 
         * Enable read access 
         */
        int GENERIC_READ        = 0x80000000;
        /** 
         * Enable write access 
         */
        int GENERIC_WRITE       = 0x40000000;
        
        /** 
         * Read data from USB HID device.
         */
        int ReadFile(WinNT.HANDLE Handle, byte[] buffer, int nNumberOfBytesToRead,  IntByReference NumberOfBytesRead,  IntByReference Overlapped);
        /** 
         * Write data to the USB HID device.
         */
        int WriteFile(WinNT.HANDLE Handle, byte[] buffer, int NumberOfBytesToWrite, IntByReference NumberOfBytesWritten, IntByReference Overlapped);
    }

    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * Debug console output.
     */
    private void debug(String err) {
        System.out.println(err);
    }

    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * Get last error description.
     * 
     * @param   code    Sysyem error code.
     * @return          Error message for the specified error code.
     */
    private String getSystemError(int code) {
        Kernel32 lib = Kernel32.INSTANCE;
        PointerByReference pref = new PointerByReference();
        lib.FormatMessage(
            WinBase.FORMAT_MESSAGE_ALLOCATE_BUFFER | WinBase.FORMAT_MESSAGE_FROM_SYSTEM | WinBase.FORMAT_MESSAGE_IGNORE_INSERTS, 
            null, 
            code, 
            0, 
            pref, 
            0, 
            null);
        String s = pref.getValue().getString(0, !Boolean.getBoolean("w32.ascii"));
        lib.LocalFree(pref.getValue());
        return s;
    } 
    
    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * Open USB HID device with the previously specified VID and PID.
     * 
     * @note For the system input device (keyboard, mouse) the dwDesiredAccess field in CreateFile must be '0'.
     * @return          'true' if the HID device with specified VID and PID exists in system and successfully opened.
     */ 
     public boolean getHIDHandle() {
        
        WinNT.HANDLE HandleToDevice = null;
        GUID Guid = new GUID();
        
        /** Get the GUID for all system USB HID devices. */
        Hid.INSTANCE.HidD_GetHidGuid(Guid);

        /** Receive a handle to the device information set for all installed devices. */ 
        HDEVINFO hDevInfo = Setupapi.INSTANCE.SetupDiGetClassDevs(Guid, null, null, Setupapi.DIGCF_DEVICEINTERFACE | Setupapi.DIGCF_PRESENT);

        int index = 0;
        do {
            HandleToDevice = WinBase.INVALID_HANDLE_VALUE;
            
            SP_DEVICE_INTERFACE_DATA DeviceInterfaceData = new SP_DEVICE_INTERFACE_DATA();
            DeviceInterfaceData.cbSize = DeviceInterfaceData.size();
            /* Query the device using the index to get the interface data */
            int result = Setupapi.INSTANCE.SetupDiEnumDeviceInterfaces(hDevInfo, null, Guid, index, DeviceInterfaceData);
            /* If no more HID devices at the root hub - LastError = ERROR_NO_MORE_ITEMS */
            if (result == 0) {
                /* Go out from the device search loop */
                break;
            }
            /* A successful query was made, use it to get the detailed data of the device */
            IntByReference reqlength = new IntByReference();
            /* Obtain the length of the detailed data structure, and then allocate space and retrieve it */
            result = Setupapi.INSTANCE.SetupDiGetDeviceInterfaceDetail(hDevInfo, DeviceInterfaceData, null, 0, reqlength, null);

            // Create SP_DEVICE_INTERFACE_DETAIL_DATA structure and set appropriate length for device Path */
            SP_DEVICE_INTERFACE_DETAIL_DATA DeviceInterfaceDetailData      = new SP_DEVICE_INTERFACE_DETAIL_DATA();
            SP_DEVICE_INTERFACE_DETAIL_DATA DeviceInterfaceDetailDataDummy = new SP_DEVICE_INTERFACE_DETAIL_DATA();
            DeviceInterfaceDetailData.cbSize = DeviceInterfaceDetailDataDummy.size();
            DeviceInterfaceDetailData.devicePath = new char[reqlength.getValue()];
            /* Obtain DEVICE_INTERFACE_DETAIL_DATA structure for grab path for HID device */
            result = Setupapi.INSTANCE.SetupDiGetDeviceInterfaceDetail(hDevInfo, DeviceInterfaceData, DeviceInterfaceDetailData, reqlength.getValue(), reqlength, null);
            if (result == 0) {
                debug("SetupDiGetDeviceInterfaceDetail: " + getSystemError(Kernel32.INSTANCE.GetLastError()));
                break;
            }    

            /* Kernel32RW.GENERIC_READ | Kernel32RW.GENERIC_WRITE not used in dwDesiredAccess field for system devices such a keyboard or mouse */
            int shareMode = WinNT.FILE_SHARE_READ | WinNT.FILE_SHARE_WRITE;
            int Access = 0; // WinNT.GENERIC_WRITE | Kernel32RW.GENERIC_READ;
            HandleToDevice = Kernel32.INSTANCE.CreateFile(
                Native.toString(DeviceInterfaceDetailData.devicePath), 
                Access, 
                shareMode, 
                null, 
                WinNT.OPEN_EXISTING, 
                WinNT.FILE_FLAG_OVERLAPPED, 
                (WinNT.HANDLE)null);
            if (HandleToDevice == WinBase.INVALID_HANDLE_VALUE) {
                debug("For device with index " + Integer.toString(index) + " CreateFile: " + getSystemError(Kernel32.INSTANCE.GetLastError()));
            }    
            /* Create HIDD_ATTRIBUTES structure */
            HIDD_ATTRIBUTES HIDAttributes = new HIDD_ATTRIBUTES();
            HIDAttributes.Size = HIDAttributes.size();
            /* Fill HIDD_ATTRIBUTES structure */
            result = Hid.INSTANCE.HidD_GetAttributes(HandleToDevice, HIDAttributes);
            if (result == 0) {
                debug("For device with index " + Integer.toString(index) + " HidD_GetAttributes: " + getSystemError(Kernel32.INSTANCE.GetLastError()));
            }
            /* Check VID & PID of the opened device */
            if (HIDAttributes.VendorID == vendorID && HIDAttributes.ProductID == productID) {
                debug("Device is found");
                break;
            }
            else {
                /* Close HID Device */
                Kernel32.INSTANCE.CloseHandle(HandleToDevice);
            }
            /* Check the next HID device for the valid VID and PID */
            index++;
        } while (true);

        Setupapi.INSTANCE.SetupDiDestroyDeviceInfoList(hDevInfo);
    
        /* Save Handle to the opened device */
        HIDHandle = HandleToDevice;
        
        if (HandleToDevice.equals(WinBase.INVALID_HANDLE_VALUE)) {
            debug("Device with VID=0x" + Integer.toHexString(vendorID & 0xffff) + " and PID=0x" + Integer.toHexString(productID & 0xffff) + " not found");
            return false;
        }
        else {
            IntByReference HIDPreparsedData = new IntByReference();
            Hid.INSTANCE.HidD_GetPreparsedData(HandleToDevice, HIDPreparsedData); 
            HIDP_CAPS HIDCapabilities = new HIDP_CAPS();
            int result = Hid.INSTANCE.HidP_GetCaps(HIDPreparsedData.getValue(), HIDCapabilities);
            InputReportLength = HIDCapabilities.InputReportByteLength;
            OutputReportLength = HIDCapabilities.OutputReportByteLength;
            FeatureReportLength = HIDCapabilities.FeatureReportByteLength;
            return true;
        }
    }

    //-------------------------------------------------------------------------------------------------------------------
    /**
     * Check if USB HID device already opened.
     * @return          'true' if the the HID device is alredy opend. 'false' otherwise.
     */
    public boolean isOpened() {
        if (HIDHandle != WinBase.INVALID_HANDLE_VALUE) {
            return true;
        }
        else {
            return false;
        }
    }
    
    //-------------------------------------------------------------------------------------------------------------------
    /** 
     * Close already opened USB HID device.
     */
    public void CloseHIDDevice () {
        if (HIDHandle != WinBase.INVALID_HANDLE_VALUE) {
            /* Close HID Device */
            Kernel32.INSTANCE.CloseHandle(HIDHandle);
        }
    }
    
    //-------------------------------------------------------------------------------------------------------------------
    /**
     * Method send Feature request to the USB HID device via Control transfer through Endpoint0.
     * 
     * @param   buffer      Transmitted Data buffer.
     * @param   buffersize  Size of the transmitted buffer (must be equal to Feature Report length).
     * @return  Returned status: HID_DEVICE_SUCCESS - Transaction successfuly completed,
     *                           HID_DEVICE_NOT_OPENED - Device is not opened,
     *                           HID_DEVICE_TRANSFER_FAILED - Transaction completed with errors.
     */
    public byte SetFeatureReport(byte[] buffer, short buffersize) {
        /* Check to see that the device is opened */
        if (HIDHandle.equals(WinBase.INVALID_HANDLE_VALUE)) {
            return HID_DEVICE_NOT_OPENED;
        }
        /* Write Feature report */
        boolean Status = Hid.INSTANCE.HidD_SetFeature(HIDHandle, buffer, buffersize);
        if (Status == false) {
            return HID_DEVICE_TRANSFER_FAILED;
        }
        else {
            return HID_DEVICE_SUCCESS;
        }
    }     

    //-------------------------------------------------------------------------------------------------------------------
    /**
     * Method retrieves Feature report from the USB HID device via Control transfer through Endpoint0.
     * @param   buffer      Received Data buffer.
     * @return  Returned status: HID_DEVICE_SUCCESS - Transaction successfuly completed,
     *                           HID_DEVICE_NOT_OPENED - Device is not opened,
     *                           HID_DEVICE_TRANSFER_FAILED - Transaction completed with errors.
     */
    public byte GetFeatureReport(byte[] buffer) {
        /* Check to see that the device is opened */
        if (HIDHandle.equals(WinBase.INVALID_HANDLE_VALUE)) {
            return HID_DEVICE_NOT_OPENED;
        }
        /* Get Feature report */
        boolean Status = Hid.INSTANCE.HidD_GetFeature(HIDHandle, buffer, buffer.length);
        if (Status == false) {
            return HID_DEVICE_TRANSFER_FAILED;
        }
        else {
            return HID_DEVICE_SUCCESS;
        }
    }

//-------------------------------------------------------------------------------------------------------------------    
    public HIDwin32() {
        super();
        HIDHandle = WinBase.INVALID_HANDLE_VALUE;
    }
}
