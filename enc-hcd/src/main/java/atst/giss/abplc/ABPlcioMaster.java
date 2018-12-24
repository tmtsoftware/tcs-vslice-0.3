package atst.giss.abplc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

//import atst.base.hardware.connections.ConnectionException;

//import atst.cs.util.Misc;

/**
 * ABPlcioMaster is the class that handles all Allen-Bradley PLC communication
 * using the PLCIO C library. Access to PLCIO is made using JNI method calls.<br>
 * <br>
 * <i> The master NEEDS to be constructed in the container's namespace because
 * it uses JNI code. Connections MUST load it via a call to:
 * {@linkplain Misc#getSharedObject(String)} and then cast the returned object
 * into an IABPlcioMaster. The interface MUST be used because
 * the object is used across different namespaces.</i><br>
 * <br>
 * Developed from {@linkplain atst.base.hardware.connections.example.FooMuxMaster}
 * by John Hubbard.
 * 
 * @author Alastair Borrowman (OSL)
 */
public class ABPlcioMaster implements IABPlcioMaster {

    /*
     *  Private class constants
     */
    // Log categories of the ABPlcioMaster class
    private static final String LOG_CAT = "ABPLCIO_MASTER";
    private static final String LOG_CAT_PLC_READ = "ABPLCIO_MASTER_PLC_READ";
    private static final String LOG_CAT_PLC_WRITE = "ABPLCIO_MASTER_PLC_WRITE";

    private static final RealABPlcioMaster master;
    static {
        /*
         * Load the PLCIO JNI C library.
         */
        System.loadLibrary("atst_giss_abplc_ABPlcioMaster");

        // Initialize the real singleton master
        master = new RealABPlcioMaster();
    }

    /*
     * JNI PLCIO wrapper methods - have a direct relationship with PLCIO
     * functions of the same name
     */
    private static native int plc_open(String plcModuleHostname, String connName)
            throws ABPlcioExceptionPLCIO, ABPlcioExceptionJNI;
    private static native int plc_close(int connNumber)
            throws ABPlcioExceptionPLCIO, ABPlcioExceptionJNI;
    private static native int plc_read(int connNumber, String tagName,
            int readLength, int readTimeout, String plcioPcFormat, int readTagIndexSAL)
                    throws ABPlcioExceptionPLCIO, ABPlcioExceptionJNI;
    private static native int plc_write(int connNumber, String tagName, byte [] tagBytes,
            int writeLength, int writeTimeout, String plcioPcFormat)
                    throws ABPlcioExceptionPLCIO, ABPlcioExceptionJNI;
    private native int plc_validaddr(int connNumber, String tagName)
            throws ABPlcioExceptionPLCIO, ABPlcioExceptionJNI;

    /*
     * JNI Java callback methods - these methods are called from JNI code
     * to pass information back from C to Java.
     */

    /**
     * Callback method called from JNI PLCIO C to return to Java tag values read
     * from the PLC following call to JNI plc_read().
     * @param connNumber -- the connection number
     * @param connName -- the connection name
     * @param tagName -- the tag to read
     * @param bytesReadTotal -- the number of bytes to read
     * @param tagBytes -- the array to put the read bytes into
     * @param readTagKeyID -- the tag key id
     * @return 0 if the read is successful, 1 if the read fails
     */
    // This method has to be declared static as it is called in JNI code that itself
    // is called from a static class (RealABPlcioMaster), therefore the calling JNI
    // code only has a class reference (not an object reference) passed to it as
    // a parameter when called, and so can only call static Java methods,
    // i.e. by calling getStaticMethodID() and callStaticXXXMethod()
    // that accept jclass parameter, rather than getMethodID() and callXXXMethod()
    // that accept jobject parameter.
    public static int plc_readCallback(int connNumber, String connName, String tagName,
            int bytesReadTotal, byte [] tagBytes, int readTagKeyID) {
        IPlcTag tag = null;

        Log.debug(LOG_CAT_PLC_READ, 4, "Java - plc_readCallback() connNumber = " + connNumber +
                ", connName '" + connName + "'" +
                ", tagName '" + tagName + "', bytesReadTotal = " + bytesReadTotal + ", bytes = " + Arrays.toString(tagBytes) +
                ", plcReadTagsSHM KeyID = " + readTagKeyID);

        tag = master.plcReadTagsSHM.get(readTagKeyID);
        if (tag == null) {
            Log.severe(LOG_CAT_PLC_READ, "Java ERROR: plc_readCallback(), can't access PLC tag with key " + readTagKeyID +
            " in plcReadTagsSHM; plcReadTagsSHM.get(" + readTagKeyID +
            ") returned null");
            return -1;
        }

        // test given tag name equals name of PlcTag obtained from this
        // object's plcReadTagsSHM
        if (!tagName.equals(tag.getName())) {
            Log.severe(LOG_CAT_PLC_READ, "Java ERROR: plc_readCallback(), tagName '" + tagName +
                    "' not equal tagName of '" + tag.getName() +
                    "' retrieved with key " + readTagKeyID +
                       " of plcReadTagsSHM");
            return -1;
           }

        // test number of bytes read equals total number of bytes in PlcTag
        if ((bytesReadTotal != tag.getTotalByteLength()) ||
                (tagBytes.length != tag.getTotalByteLength())) {
            Log.severe(LOG_CAT_PLC_READ, "Java ERROR: plc_readCallback(), bytesReadTotal = " + bytesReadTotal +
                    " read tagBytes array length = " + tagBytes.length +
                    ", one or both not equal tag's totalByteLength of " + tag.getTotalByteLength() +
                    " retrieved with key " + readTagKeyID +
                       " of plcReadTagsSHM");
            return -1;
           }

        // Read tagData into Java nio ByteBuffer and set its byte order to little endian
        // as raw byte data received from C is little endian but Java is always BIG endian
        ByteBuffer tagByteBuffer = ByteBuffer.wrap(tagBytes);
        tagByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        
        if ((bytesReadTotal != tagBytes.length) &&
                (bytesReadTotal != tag.getTotalByteLength()) &&
                (bytesReadTotal != tagByteBuffer.limit())) {
            Log.severe(LOG_CAT_PLC_READ, "Java - plc_readCallback() miss-match between byte lengths:" +
                "byte length total read = "+ bytesReadTotal + ", length of tagBytes array = " + tagBytes.length +
                ", tag's totalByteLength from plcReadTagsSHM = " + tag.getTotalByteLength() +
                ", tagByteBuffer limit = " + tagByteBuffer.limit());
        }
        
        if (Log.getDebugLevel(LOG_CAT_PLC_READ) >= 4) {
            StringBuffer rawBytesStr = new StringBuffer();
            for (int i = 0; i < tagBytes.length; i++) {
                rawBytesStr.append(String.format(" %02x", tagBytes[i]));
            }
            Log.debug(LOG_CAT_PLC_READ, 4, "Java - plc_readCallback() raw bytes received (LITTLE_ENDIAN (C)) tagBytes[" +
                    tagBytes.length + "] = " + rawBytesStr.toString());
        }
        
        String[] tagValues = new String [tag.getMemberTotal()];
        StringBuilder readValueBytesHexStr = new StringBuilder();
        int tagValueIndex = 0, tagByteBufferPos = 0;

        ArrayList<PlcioPcFormatType> pcFormatTypeAL = tag.getPcFormatTypeAL();
        for (int i = 0; i < pcFormatTypeAL.size(); i++) {

            IPlcioPcFormatType pcFormatType = pcFormatTypeAL.get(i);

            // store data read based upon its type and length in a String array
            // (for subsequent storage in this PlcTag object's tagValues array)
            Log.debug(LOG_CAT_PLC_READ, 4, " tag pcFormatTypeDescriptor[" + i + "] contains " + pcFormatType.getNumberOfMembers() +
                    " members of type " + pcFormatType.getTypeIdStr() + ":");
            
            switch (pcFormatType.getTypeId()) {
            case PlcioPcFormat.TYPE_C: // char
                for (int j = 0; j < pcFormatType.getNumberOfMembers(); j++) {
                    // PLCIO stores char in 1 byte, the Java primitive of
                    // length 1 byte is the type byte so retrieve 1 byte
                    // from the byte buffer when reading type char
                    tagByteBufferPos = tagByteBuffer.position();
                    tagValues[tagValueIndex] = Byte.toString(tagByteBuffer.get());
                    
                    if (Log.getDebugLevel(LOG_CAT_PLC_READ) >= 3) {
                        String byteHexStr = String.format("%02x", Byte.parseByte(tagValues[tagValueIndex]));
                        readValueBytesHexStr.append(byteHexStr);
                        if (tagValueIndex != (tagValues.length - 1)) readValueBytesHexStr.append(" ");
                        Log.debug(LOG_CAT_PLC_READ, 4, "  [" + j + "] (tagByteBufferPos " + tagByteBufferPos + ") " +
                                byteHexStr + " (bytes hex), " +    Byte.parseByte(tagValues[tagValueIndex]) + " (char)");
                    }
                    tagValueIndex += 1;
                }
                break;
            case PlcioPcFormat.TYPE_I: // short
                for (int j = 0; j < pcFormatType.getNumberOfMembers(); j++) {
                    tagByteBufferPos = tagByteBuffer.position();
                    tagValues[tagValueIndex] = Short.toString(tagByteBuffer.getShort());
                    
                    if (Log.getDebugLevel(LOG_CAT_PLC_READ) >= 3) {
                        String byteHexStr = String.format("%04x", Short.parseShort(tagValues[tagValueIndex]));
                        readValueBytesHexStr.append(byteHexStr);
                        if (tagValueIndex != (tagValues.length - 1)) readValueBytesHexStr.append(" ");
                        Log.debug(LOG_CAT_PLC_READ, 4, "  [" + j + "] (tagByteBufferPos " + tagByteBufferPos + ") " +
                                byteHexStr + " (bytes hex), " +    Short.parseShort(tagValues[tagValueIndex]) + " (short)");
                    }
                    tagValueIndex += 1;
                }
                break;
            case PlcioPcFormat.TYPE_J: // int
                for (int j = 0; j < pcFormatType.getNumberOfMembers(); j++) {
                    tagByteBufferPos = tagByteBuffer.position();
                    tagValues[tagValueIndex] = Integer.toString(tagByteBuffer.getInt());
                    
                    if (Log.getDebugLevel(LOG_CAT_PLC_READ) >= 3) {
                        String byteHexStr = String.format("%08x", Integer.parseInt(tagValues[tagValueIndex]));
                        readValueBytesHexStr.append(byteHexStr);
                        if (tagValueIndex != (tagValues.length - 1)) readValueBytesHexStr.append(" ");
                        Log.debug(LOG_CAT_PLC_READ, 4, "  [" + j + "] (tagByteBufferPos " + tagByteBufferPos + ") " +
                                byteHexStr + " (bytes hex), " +    Integer.parseInt(tagValues[tagValueIndex]) + " (int)");
                    }
                    tagValueIndex += 1;
                }
                break;
            case PlcioPcFormat.TYPE_Q: // long
                for (int j = 0; j < pcFormatType.getNumberOfMembers(); j++) {
                    tagByteBufferPos = tagByteBuffer.position();
                    tagValues[tagValueIndex] = Long.toString(tagByteBuffer.getLong());
                    
                    if (Log.getDebugLevel(LOG_CAT_PLC_READ) >= 3) {
                        String byteHexStr = String.format("%16x", Long.parseLong(tagValues[tagValueIndex]));
                        readValueBytesHexStr.append(byteHexStr);
                        if (tagValueIndex != (tagValues.length - 1)) readValueBytesHexStr.append(" ");
                        Log.debug(LOG_CAT_PLC_READ, 4, "  [" + j + "] (tagByteBufferPos " + tagByteBufferPos + ") " +
                                byteHexStr + " (bytes hex), " +    Long.parseLong(tagValues[tagValueIndex]) + " (long)");
                    }
                    tagValueIndex += 1;
                }
                break;
            case PlcioPcFormat.TYPE_R: // float
                for (int j = 0; j < pcFormatType.getNumberOfMembers(); j++) {
                    tagByteBufferPos = tagByteBuffer.position();
                    tagValues[tagValueIndex] = Float.toString(tagByteBuffer.getFloat());
                    
                    if (Log.getDebugLevel(LOG_CAT_PLC_READ) >= 3) {
                        String byteHexStr = String.format("%08x", Float.floatToIntBits(Float.parseFloat(tagValues[tagValueIndex])));
                        readValueBytesHexStr.append(byteHexStr);
                        if (tagValueIndex != (tagValues.length - 1)) readValueBytesHexStr.append(" ");
                        Log.debug(LOG_CAT_PLC_READ, 4, "  [" + j + "] (tagByteBufferPos " + tagByteBufferPos + ") " +
                                byteHexStr + " (bytes hex), " +    Float.parseFloat(tagValues[tagValueIndex]) + " (float)");
                    }
                    tagValueIndex += 1;
                }
                break;
            case PlcioPcFormat.TYPE_D: // double
                for (int j = 0; j < pcFormatType.getNumberOfMembers(); j++) {
                    tagByteBufferPos = tagByteBuffer.position();
                    tagValues[tagValueIndex] = Double.toString(tagByteBuffer.getDouble());
                    
                    if (Log.getDebugLevel(LOG_CAT_PLC_READ) >= 3) {
                        String byteHexStr = String.format("%16x", Double.doubleToLongBits(Double.parseDouble(tagValues[tagValueIndex])));
                        readValueBytesHexStr.append(byteHexStr);
                        if (tagValueIndex != (tagValues.length - 1)) readValueBytesHexStr.append(" ");
                        Log.debug(LOG_CAT_PLC_READ, 4, "  [" + j + "] (tagByteBufferPos " + tagByteBufferPos + ") " +
                                byteHexStr + " (bytes hex), " +    Double.parseDouble(tagValues[tagValueIndex]) + " (double)");
                    }
                    tagValueIndex += 1;
                }
                break;
            } // end switch
        } // end for

        // store read values in the PlcTag object's tagValues array and store
        // in the plcReadTagsSHM HashMap
        tag.setMemberValues(tagValues);
        master.plcReadTagsSHM.put(readTagKeyID, tag);

        Log.debug(LOG_CAT_PLC_READ, 3, "Java - callback received plc_readCallback(" + connNumber + ", " + connName +
                ", " + bytesReadTotal + ", " + readValueBytesHexStr.toString() + ", " + readTagKeyID + ")");

        return 0;
    } // end plc_readCallback()

    /**
     * Callback method called from JNI PLCIO C to return to Java tag validation read
     * from the PLC following call to JNI plc_validaddr().
     * @param connectionNumber -- the connection number
     * @param connName -- the name of the connection
     * @param tagName -- the tag to validate
     * @param tagSize -- the size of the tag
     * @param tagDomain -- the domain of the tag
     * @param tagOffset -- the offset of the tag
     */
    // This method has to be declared static as it is called in JNI code that itself
    // is called from a static class (RealABPlcioMaster), therefore the calling JNI
    // code only has a class reference (not an object reference) passed to it as
    // a parameter when called, and so can only call static Java methods,
    // i.e. by calling getStaticMethodID() and callStaticXXXMethod()
    // that accept jclass parameter, rather than getMethodID() and callXXXMethod()
    // that accept jobject parameter.
    public static void plc_validaddrCallback(int connectionNumber, String connName,
            String tagName, int tagSize, int tagDomain, int tagOffset) {
        Log.debug(LOG_CAT , 2, "Java - plc_validaddr() callback for connNum " + connectionNumber +
                ", connName '" + connName +
                "', tagName '" + tagName + "', size = " + tagSize +
                ", domain = " + tagDomain + ", offset = " + tagOffset);
    } // end plc_validaddrCallback()
    
    /**
     * The PLC access method through which all access is made to the PLC.
     * <p>
     * It forwards the call to the real Master's {@linkplain ABPlcioMaster.RealABPlcioMaster#realAccessPlc(IPlcioCall)}.
     * 
     * @param plcioCall    An {@linkplain IPlcioCall} object describing the PLC access
     *                     required and including any necessary parameters for the call.
     * 
     * @throws ABPlcioExceptionPLCIO -- ABPlcioExceptionPLCIO 
     * @throws ABPlcioExceptionJNI -- ABPlcioExceptionJNI
     * @throws ConnectionException -- ConnectionException
     */
    public void plcAccess(IPlcioCall plcioCall)
            throws ABPlcioExceptionPLCIO, ABPlcioExceptionJNI, ConnectionException {
        
        if (Log.getDebugLevel(LOG_CAT) >= 4) {
            switch(plcioCall.getMethodName()) {
            case PLC_OPEN:
                Log.debug(LOG_CAT, 4, "plcAccess(" + this.toString() + " " + Thread.currentThread().getName() + ") PLC_OPEN");
                break;
            case PLC_CLOSE:
                Log.debug(LOG_CAT, 4, "plcAccess(" + this.toString() + " " + Thread.currentThread().getName() + ") PLC_CLOSE");
                break;
            case PLC_READ:
                Log.debug(LOG_CAT, 4, "plcAccess(" + this.toString() + " " + Thread.currentThread().getName() + ") PLC_READ " + plcioCall.getParamTag().getName());
                break;
            case PLC_WRITE:
                Log.debug(LOG_CAT, 4, "plcAccess(" + this.toString() + " " + Thread.currentThread().getName() + ") PLC_WRITE " + plcioCall.getParamTag().getName());
                break;
            case PLC_VALIDADDR:
                Log.debug(LOG_CAT, 4, "plcAccess(" + this.toString() + " " + Thread.currentThread().getName() + ") PLC_VALIDADDR " + plcioCall.getParamTag().getName());
                break;
            }
        }
        
        master.realAccessPlc(plcioCall);
    } // end plcAccess()
    
    /*
     * Private inner class RealABPlcioMaster
     */
    private static class RealABPlcioMaster {
        /* HashMap used to ensure that connection number returned from PLCIO
         * plc_open() JNI call is unique. */
        private Map<Integer, Integer> connectionNumberMap;
        
        /* When method readTag() is called to read tag values from PLC using native
         * plc_read(), the C function returns the tag values to Java by calling
         * the method plc_readCallback(), this stores the tag in the
         * SynchronizedHashMap plcReadTagsSHM from which readTag() can
         * access it and pass the tag values back to the calling object. */
        private Map<Integer, IPlcTag> plcReadTagsSHM;
        private int plcReadTagsSHM_keyID;

        /**
         * Construct the real ABPlcioMaster.
         */
        public RealABPlcioMaster() {
            // Initialize the SynchronizedHashMap used to ensure unique connection number
            // is returned from PLCIO plc_open()
            connectionNumberMap = Collections.synchronizedMap(new HashMap<Integer, Integer>());
            // Initialize the SynchronizedHashMap used to pass tag values read from
            // PLC between JNI and Java
            plcReadTagsSHM = Collections.synchronizedMap(new HashMap<Integer, IPlcTag>());
            plcReadTagsSHM_keyID = 0;
        } // end constructor

        /**
         * The method called by {@linkplain ABPlcioMaster#plcAccess(IPlcioCall)} to access
         * the PLCIO JNI methods.
         * 
         * @param plcioCall    An {@linkplain IPlcioCall} object describing the PLC access
         *                     required and including any necessary parameters for the call.
         * 
         * @throws ABPlcioExceptionPLCIO -- ABPlcioExceptionPLCIO 
         * @throws ABPlcioExceptionJNI -- ABPlcioExceptionJNI
         * @throws ConnectionException -- ConnectionException
         */
        public synchronized void realAccessPlc(IPlcioCall plcioCall)
                throws ABPlcioExceptionPLCIO, ABPlcioExceptionJNI, ConnectionException {
            
            long startTime = System.currentTimeMillis();
            plcioCall.setWaitTime(startTime - plcioCall.getCallTime());
            
            int connectionNumber = -1;
            switch(plcioCall.getMethodName()) {
            case PLC_OPEN:
                if (Log.getDebugLevel(LOG_CAT) >= 4) {
                    Log.debug(LOG_CAT, 4, "realAccessPlc(" + this.toString() + " " + Thread.currentThread().getName() + ") PLC_OPEN");
                }
                connectionNumber = ABPlcioMaster.plc_open(plcioCall.getArgAddress(), plcioCall.getArgConnectionName());
                // ensure the connectionNumber returned from plc_open() is valid
                if ((connectionNumber < 0) || connectionNumberMap.containsKey(connectionNumberMap)) {
                    String exMsg;
                    if (connectionNumber < 0) exMsg = "plc_open() returned invalid connection number="+connectionNumber;
                    else exMsg = "plc_open() returned already inuse connection number="+connectionNumber;
                    throw new ConnectionException(exMsg);
                }
                connectionNumberMap.put(connectionNumber, connectionNumber);
                plcioCall.setParamConnectionNumber(connectionNumber);
                break;
            case PLC_CLOSE:
                if (Log.getDebugLevel(LOG_CAT) >= 4) {
                    Log.debug(LOG_CAT, 4, "realAccessPlc(" + this.toString() + " " + Thread.currentThread().getName() + ") PLC_CLOSE");
                }
                connectionNumber = plcioCall.getParamConnectionNumber();
                connectionNumberMap.remove(connectionNumber);
                ABPlcioMaster.plc_close(connectionNumber);
                break;
            case PLC_READ:
                if (Log.getDebugLevel(LOG_CAT) >= 4) {
                    Log.debug(LOG_CAT, 4, "realAccessPlc(" + this.toString() + " " + Thread.currentThread().getName() + ") PLC_READ " + plcioCall.getParamTag().getName());
                }
                int readTagKeyID = getPlcReadTagsSHM_keyID();
                IPlcTag tag = plcioCall.getParamTag();
                plcReadTagsSHM.put(readTagKeyID, tag);
                ABPlcioMaster.plc_read(plcioCall.getParamConnectionNumber(), tag.getName(),
                        tag.getTotalByteLength(), tag.getPlcioTimeoutMs(),
                        tag.getPcFormatString(), readTagKeyID);
                plcioCall.setParamTag(plcReadTagsSHM.remove(readTagKeyID));
                break;
            case PLC_WRITE:
                if (Log.getDebugLevel(LOG_CAT) >= 4) {
                    Log.debug(LOG_CAT, 4, "realAccessPlc(" + this.toString() + " " + Thread.currentThread().getName() + ") PLC_WRITE " + plcioCall.getParamTag().getName());
                }
                realPlcWrite(plcioCall.getParamConnectionNumber(), plcioCall.getParamTag());
                break;
            case PLC_VALIDADDR:
                // TODO call ABPlcioMasterFullSync.plc_validaddrCallback()
                break;
            } // end switch
            
            plcioCall.setOpTime(System.currentTimeMillis() - startTime);
            return;
        } // end realAccessPlc()

        /**
         * The method called by {@linkplain #realAccessPlc(IPlcioCall)} when a tag write to the
         * PLC is requested.
         * <p>
         * Formats the current tag values into bytes to be written to the PLC and calls PLCIO JNI
         * {@linkplain ABPlcioMaster#plc_write(int, String, byte[], int, int, String)}.
         * 
         * @param connectionNumber    The connection number to be used to write the tag
         * @param tag    The tag which is to be written to the PLC
         * 
         * @throws ConnectionException
         */
        public void realPlcWrite(int connectionNumber, IPlcTag tag) throws ConnectionException {
            
            Log.debug(LOG_CAT_PLC_WRITE, 4, "Java - realPlcWrite(" + connectionNumber + ", " + tag.getName() + ")");

            // firstly check that tagValues are not null
            String [] tagValues = tag.getMemberValues();
            for (int i = 0; i < tagValues.length; i++) {
                if (tagValues[i] == null) {
                    throw new ConnectionException("Java - realPlcWrite(" + connectionNumber + ", " + tag.getName() +
                            "), tagValues array contains null items starting at index " + i);
                }
            }

            // tagData values are written into a Java nio ByteBuffer with its byte order set
            // to little endian as C uses little endian while Java is always BIG endian
            ByteBuffer tagByteBuffer = ByteBuffer.allocate(tag.getTotalByteLength());
            tagByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            
            StringBuilder writeValueBytesHexStr = new StringBuilder();
            int tagValueIndex = 0, tagByteBufferPos = 0;

            ArrayList<PlcioPcFormatType> pcFormatTypeAL = tag.getPcFormatTypeAL();
            for (int i = 0; i < pcFormatTypeAL.size(); i++) {
                
                IPlcioPcFormatType pcFormatType = pcFormatTypeAL.get(i);

                Log.debug(LOG_CAT_PLC_WRITE, 4, " tag pcFormatTypeDescriptor[" + i + "] contains " + pcFormatType.getNumberOfMembers() +
                        " members of type " + pcFormatType.getTypeIdStr() + ":");

                switch (pcFormatType.getTypeId()) {
                case PlcioPcFormat.TYPE_C: // char
                    for (int j = 0; j < pcFormatType.getNumberOfMembers(); j++) {
                        // PLCIO stores char in 1 byte (Java char is 2 bytes),
                        // the Java primitive type of length 1 byte is the byte so
                        // need to convert char to byte array and only write the
                        // first byte of the array to the ByteBuffer
                        byte [] byteData = tagValues[tagValueIndex].getBytes();
                        if (byteData.length == 1) {
                            tagByteBufferPos = tagByteBuffer.position();
                            tagByteBuffer.put(byteData[0]);
                        }
                        else {
                            Log.severe(LOG_CAT_PLC_WRITE, "Java ERROR: realPlcWrite() loosing char data: " +
                                    "number of bytes = " + byteData.length);
                        }
                        
                        if (Log.getDebugLevel(LOG_CAT_PLC_WRITE) >= 3) {
                            String byteHexStr = String.format("%02x", byteData[0]);
                            writeValueBytesHexStr.append(byteHexStr);
                            if (tagValueIndex != (tagValues.length - 1)) writeValueBytesHexStr.append(" ");
                            Log.debug(LOG_CAT_PLC_WRITE, 4, "  [" + j + "] (tagByteBufferPos " + tagByteBufferPos + ") " +
                                    byteHexStr + " (bytes hex), " +    byteData[0] + " (char)");
                        }
                        tagValueIndex += 1;
                    }
                    break;
                case PlcioPcFormat.TYPE_I: // short
                    for (int j = 0; j < pcFormatType.getNumberOfMembers(); j++) {
                        tagByteBufferPos = tagByteBuffer.position();
                        tagByteBuffer.putShort(Short.parseShort(tagValues[tagValueIndex]));

                        if (Log.getDebugLevel(LOG_CAT_PLC_WRITE) >= 3) {
                            String byteHexStr = String.format("%04x", Short.parseShort(tagValues[tagValueIndex]));
                            writeValueBytesHexStr.append(byteHexStr);
                            if (tagValueIndex != (tagValues.length - 1)) writeValueBytesHexStr.append(" ");
                            Log.debug(LOG_CAT_PLC_WRITE, 4, "  [" + j + "] (tagByteBufferPos " + tagByteBufferPos + ") " +
                                    byteHexStr + " (bytes hex), " +    Short.parseShort(tagValues[tagValueIndex]) + " (short)");
                        }
                        tagValueIndex += 1;
                    }
                    break;
                case PlcioPcFormat.TYPE_J: // int
                    for (int j = 0; j < pcFormatType.getNumberOfMembers(); j++) {
                        tagByteBufferPos = tagByteBuffer.position();
                        tagByteBuffer.putInt(Integer.parseInt(tagValues[tagValueIndex]));                            
                        
                        if (Log.getDebugLevel(LOG_CAT_PLC_WRITE) >= 3) {
                            String byteHexStr = String.format("%08x", Integer.parseInt(tagValues[tagValueIndex]));
                            writeValueBytesHexStr.append(byteHexStr);
                            if (tagValueIndex != (tagValues.length - 1)) writeValueBytesHexStr.append(" ");
                            Log.debug(LOG_CAT_PLC_WRITE, 4, "  [" + j + "] (tagByteBufferPos " + tagByteBufferPos + ") " +
                                    byteHexStr + " (bytes hex), " +    Integer.parseInt(tagValues[tagValueIndex]) + " (int)");
                        }
                        tagValueIndex += 1;
                    }
                    break;
                case PlcioPcFormat.TYPE_Q: // long
                    for (int j = 0; j < pcFormatType.getNumberOfMembers(); j++) {
                        tagByteBufferPos = tagByteBuffer.position();
                        tagByteBuffer.putLong(Long.parseLong(tagValues[tagValueIndex]));
                        
                        if (Log.getDebugLevel(LOG_CAT_PLC_WRITE) >= 3) {
                            String byteHexStr = String.format("%16x", Long.parseLong(tagValues[tagValueIndex]));
                            writeValueBytesHexStr.append(byteHexStr);
                            if (tagValueIndex != (tagValues.length - 1)) writeValueBytesHexStr.append(" ");
                            Log.debug(LOG_CAT_PLC_WRITE, 4, "  [" + j + "] (tagByteBufferPos " + tagByteBufferPos + ") " +
                                    byteHexStr + " (bytes hex), " +    Long.parseLong(tagValues[tagValueIndex]) + " (long)");
                        }
                        tagValueIndex += 1;
                    }
                    break;
                case PlcioPcFormat.TYPE_R: // float
                    for (int j = 0; j < pcFormatType.getNumberOfMembers(); j++) {
                        tagByteBufferPos = tagByteBuffer.position();
                        tagByteBuffer.putFloat(Float.parseFloat(tagValues[tagValueIndex]));
                        
                        if (Log.getDebugLevel(LOG_CAT_PLC_WRITE) >= 3) {
                            String byteHexStr = String.format("%08x", Float.floatToIntBits(Float.parseFloat(tagValues[tagValueIndex])));
                            writeValueBytesHexStr.append(byteHexStr);
                            if (tagValueIndex != (tagValues.length - 1)) writeValueBytesHexStr.append(" ");
                            Log.debug(LOG_CAT_PLC_WRITE, 4, "  [" + j + "] (tagByteBufferPos " + tagByteBufferPos + ") " +
                                    byteHexStr + " (bytes hex), " +    Float.parseFloat(tagValues[tagValueIndex]) + " (float)");
                        }
                        tagValueIndex += 1;
                    }
                    break;
                case PlcioPcFormat.TYPE_D: // double
                    for (int j = 0; j < pcFormatType.getNumberOfMembers(); j++) {
                        tagByteBufferPos = tagByteBuffer.position();
                        tagByteBuffer.putDouble(Double.parseDouble(tagValues[tagValueIndex]));
                        
                        if (Log.getDebugLevel(LOG_CAT_PLC_WRITE) >= 3) {
                        String byteHexStr = String.format("%16x", Double.doubleToLongBits(Double.parseDouble(tagValues[tagValueIndex])));
                        writeValueBytesHexStr.append(byteHexStr);
                        if (tagValueIndex != (tagValues.length - 1)) writeValueBytesHexStr.append(" ");
                        Log.debug(LOG_CAT_PLC_WRITE, 4, "  [" + j + "] (tagByteBufferPos " + tagByteBufferPos + ") " +
                                byteHexStr + " (bytes hex), " +    Double.parseDouble(tagValues[tagValueIndex]) + " (double)");
                        }
                        tagValueIndex += 1;
                    }
                    break;
                } // end switch
            } // end for


            if (tagByteBuffer.limit() != tag.getTotalByteLength())
            {
                throw new ConnectionException("Java ERROR: realPlcWrite() tag '" + tag.getName() +
                        "' problem creating ByteBuffer from tagValues," +
                        " length of created ByteBuffer = " + tagByteBuffer.limit() +
                        " not equal tag.totalByteLength of " + tag.getTotalByteLength() +
                        ". NOT calling plc_write()");
            }

            Log.debug(LOG_CAT_PLC_WRITE, 3, "Java - calling plc_write(" + connectionNumber + ", " + tag.getName() +
                    ", " + writeValueBytesHexStr.toString() + ", " + tagByteBuffer.limit() + ", " +
                    tag.getPlcioTimeoutMs() + ", " + tag.getPcFormatString() + ")");

            ABPlcioMaster.plc_write(connectionNumber, tag.getName(),
                    tagByteBuffer.array(), tagByteBuffer.limit(),
                    tag.getPlcioTimeoutMs(), tag.getPcFormatString());
            
            return;
        } // end realPlcWrite()

        /**
         * Called from readTag() to get a unique keyID used as key into synchronized
         * HashMap plcReadTagsSHM, which is used to hold PLC Tag objects while the tag
         * is being read.
         */
        private synchronized int getPlcReadTagsSHM_keyID() {
            if (plcReadTagsSHM_keyID > 10000) {
                plcReadTagsSHM_keyID = 1;
            }
            else {
                plcReadTagsSHM_keyID += 1;
            }
            return plcReadTagsSHM_keyID;
        } // end getPlcReadTagsSHM_keyID()

    } // end class RealABPlcioMaster

} // end class ABPlcioMaster
