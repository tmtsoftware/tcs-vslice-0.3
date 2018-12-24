package atst.giss.abplc;

/**
 * IABPlcioMaster is the public interface to the Allen-Bradley
 * PLCIO Master.
 * 
 * @author Alastair Borrowman (OSL)
 *
 */
public interface IABPlcioMaster {
	 
	/**
	 * The method called to access PLCIO library functions to open/close
	 * connections to the GIS PLC and read/write tags to/from the GIS
	 * PLC.
	 * <p>
	 * This method is only called by methods of {@linkplain ABPlcioChannel}.
	 * 
	 * @param plcioCall {@linkplain PlcioCall} object describing the PLCIO
	 * function to be called including all required parameters.
	 * 
	 * @throws ABPlcioExceptionPLCIO -- ABPlcioExceptionPLCIO
	 * @throws ABPlcioExceptionJNI -- ABPlcioExceptionJNI
	 * @throws ConnectionException -- ConnectionException
	 */
	public void plcAccess(IPlcioCall plcioCall)
			throws ABPlcioExceptionPLCIO, ABPlcioExceptionJNI, ConnectionException;
	
} // end interface IABPlcioMaster
