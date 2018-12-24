/**
 * 
 */
package atst.giss.abplc;

/**
 * Interface describing {@linkplain PlcioCall} objects used to pass PLCIO function
 * call information to the {@linkplain ABPlcioMaster}.
 * 
 * @author Alastair Borrowman (OSL)
 *
 */
public interface IPlcioCall {
	
	/**
	 * Enumeration of all PLCIO function calls recognized and used by the GISS.
	 * 
	 * @author Alastair Borromwan (OSL)
	 */
	public enum PlcioMethodName {
		PLC_OPEN, PLC_CLOSE,
		PLC_READ, PLC_WRITE,
		PLC_VALIDADDR
	}

	/**
	 * Return the PLCIO method name this call object represents.
	 * 
	 * @return This call object's PLCIO method name.
	 */
	public PlcioMethodName getMethodName();
	
	/**
	 * If this call object represents a call to PLCIO function plc_open()
	 * this method will return the address argument passed to the PLCIO
	 * function.
	 * 
	 * @return The address argument passed to PLCIO function plc_open() or
	 * null if no address argument supplied in call object's constructor.
	 */
	public String getArgAddress();
	
	/**
	 * Return the connection name of connection used in call to PLCIO function.
	 * <p>
	 * The GISS names connections to the GIS PLC opened by PLCIO function plc_open()
	 * using the GIS PLC tag name the connection will be used to communicate.
	 * 
	 * @return The name of the connection the PLCIO call will use.
	 */
	public String getArgConnectionName();
	
	/**
	 * Return the connection channel number of connection used in call to PLCIO
	 * function.
	 * <p>
	 * A successful call to plc_open() returns a connection number that must be
	 * used in all subsequent PLCIO function calls using the open channel.
	 *  
	 * @return The number of the connection channel the PLCIO call will use.
	 */
	public int getParamConnectionNumber();
	
	/**
	 * Used by {@linkplain ABPlcioMaster} following successful call to PLCIO function
	 * plc_open() to set the connection channel number returned.
	 * 
	 * @param connectionNumber The connection channel number of the opened PLCIO
	 * connection.
	 */
	public void setParamConnectionNumber(int connectionNumber);
	
	/**
	 * Get the {@linkplain IPlcTag} to be communicated in this call to PLCIO function.
	 * 
	 * @return The GIS PLC tag object to be communicated using PLCIO function.
	 */
	public IPlcTag getParamTag();

	/**
	 * Used by {@linkplain ABPlcioMaster} following successful call to PLCIO function
	 * plc_read() to set GIS PLC tag read.
	 * 
	 * @param tag The GIS PLC tag as read from the GIS PLC.
	 */
	public void setParamTag(IPlcTag tag);
	
	/**
	 * Get the time the PLCIO function was called.
	 * 
	 * @return Time of PLCIO function call.
	 */
	public long getCallTime();
	
	/**
	 * Set this PLCIO call object's time its PLCIO function was called by
	 * {@linkplain ABPlcioChannel}.
	 * 
	 * @param cTime Time the PLCIO function was called.
	 */
	public void setCallTime(long cTime);
	
	/**
	 * Get the time waited between {@linkplain #getCallTime()} and actual time
	 * PLCIO function was called by {@linkplain ABPlcioMaster}.
	 * <p>
	 * This method returns the time taken to access the synchronized method used
	 * to access the GIS PLC through PLCIO functions.
	 * 
	 * @return Time waited between call to access PLCIO function and access being
	 * granted in ms.
	 */
	public long getWaitTime();
	
	/**
	 * Used by {@linkplain ABPlcioMaster} to set the time waited by this PLCIO call
	 * object to access the synchronized method protecting calls to PLCIO and returned
	 * by {@linkplain #getWaitTime()}.
	 * 
	 * @param wTime The time waited in ms.
	 */
	public void setWaitTime(long wTime);
	
	/**
	 * The time taken to carry out the PLCIO function call, i.e. time between PLCIO
	 * function being called and it returning.
	 * 
	 * @return Time PLCIO function took to operate in ms.
	 */
	public long getOpTime();
	
	/**
	 * Used by {@linkplain ABPlcioMaster} to set the time PLCIO call operation time
	 * returned by {@linkplain #getOpTime()}.
	 * 
	 * @param oTime The time taken for the PLCIO function call to carry out its
	 * operation in ms.
	 */
	public void setOpTime(long oTime);
}
