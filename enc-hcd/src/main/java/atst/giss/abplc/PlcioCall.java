/**
 * 
 */
package atst.giss.abplc;

/**
 * Class providing behavior required to pass PLCIO function call information to
 * the {@linkplain ABPlcioMaster#plcAccess(IPlcioCall)} method.
 * 
 * @author Alastair Borrowman (OSL)
 *
 */
public class PlcioCall implements IPlcioCall {

	private final PlcioMethodName methodName;
	private final String argAddress;
	private final String argConnectionName;
	private int paramConnectionNumber;
	private IPlcTag paramTag;
	private long callTime;
	private long waitTime;
	private long opTime;

	public PlcioCall(PlcioMethodName mName, int connectionNumber) {

		this(mName, null, null, connectionNumber, null);
	}
	
	public PlcioCall(PlcioMethodName mName, int connectionNumber, IPlcTag tag) {

		this(mName, null, null, connectionNumber, tag);
	}
	
	public PlcioCall(PlcioMethodName mName, String address, String connectionName, int connectionNumber) {

		this(mName, address, connectionName, connectionNumber, null);
	}
	
	public PlcioCall(PlcioMethodName mName, String address, String connectionName, int connectionNumber, IPlcTag tag) {

		methodName = mName;
		argAddress = address;
		argConnectionName = connectionName;
		paramConnectionNumber = connectionNumber;
		paramTag = tag;
		callTime = 0;
		waitTime = 0;
		opTime = 0;

	}
	
	// documented in IPlcioCall
	@Override
	public PlcioMethodName getMethodName() {
		return methodName;
	}

	// documented in IPlcioCall
	@Override
	public String getArgAddress() {
		return argAddress;
	}

	// documented in IPlcioCall
	@Override
	public String getArgConnectionName() {
		return argConnectionName;
	}

	// documented in IPlcioCall
	@Override
	public int getParamConnectionNumber() {
		return paramConnectionNumber;
	}

	// documented in IPlcioCall
	@Override
	public void setParamConnectionNumber(int connectionNumber) {
		paramConnectionNumber = connectionNumber;
	}

	// documented in IPlcioCall
	@Override
	public IPlcTag getParamTag() {
		return paramTag;
	}

	// documented in IPlcioCall
	@Override
	public void setParamTag(IPlcTag tag) {
		paramTag = tag;
	}

	// documented in IPlcioCall
	@Override
	public long getCallTime() {
		return callTime;
	}
	
	// documented in IPlcioCall
	@Override
	public void setCallTime(long cTime) {
		callTime = cTime;
	}
	
	// documented in IPlcioCall
	@Override
	public long getWaitTime() {
		return waitTime;
	}
	
	// documented in IPlcioCall
	@Override
	public void setWaitTime(long wTime) {
		waitTime = wTime;
	}
	
	// documented in IPlcioCall
	@Override
	public long getOpTime() {
		return opTime;
	}
	
	// documented in IPlcioCall
	@Override
	public void setOpTime(long oTime) {
		opTime = oTime;
	}
	
	/**
	 * Return a String describing this PlcioCall object.
	 * <p>
	 * Information returned depends upon {@link IPlcioCall.PlcioMethodName}
	 * this object refers to.
	 */
	@Override
	public String toString() {
		StringBuilder rtnString = new StringBuilder();
		
		rtnString.append("methodName="+methodName+", ");
		switch(methodName) {
		case PLC_OPEN:
			rtnString.append("address="+argAddress+", connectionName="+argConnectionName+", ");
			break;
		case PLC_CLOSE:
			// nothing else to add for PLC_CLOSE
			break;
		case PLC_READ: // intentional full-through
		case PLC_WRITE: // intentional full-through
		case PLC_VALIDADDR:
			rtnString.append("tagName="+paramTag.getName()+", ");
			break;
		} // end switch
		
		rtnString.append("connectionNumber="+paramConnectionNumber);
		
		return rtnString.toString();
	}
	
} // end class PlcCall
