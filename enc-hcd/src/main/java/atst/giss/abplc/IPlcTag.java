package atst.giss.abplc;

import java.util.ArrayList;

//import atst.cs.interfaces.IAttributeTable;

/**
 * The public interface containing constants and methods used to
 * operate with {@linkplain PlcTag} objects.
 * <p>
 * The constants defined include the <i>types</i> of PLC tags used
 * by the GIS, these are:
 * <ul>
 * <li><code>CStatus</code> &mdash; all GISS components and controllers use a
 * CStatus PLC read tag to read the current hardware status from the GIS.
 * <li><code>Cmds</code> &mdash; all GISS controllers use a Cmds PLC write tag to
 * write commands to the GIS to operate enclosure hardware.
 * <li><code>Pos</code> &mdash; all GISS tracking controllers use a Pos PLC write
 * tag to write demand position to the GIS to move their tracking mechanism.
 * <li><code>CPos</code> &mdash; all GISS tracking controllers use a CPos PLC read
 * tag to read the current tracking mechanism position from the GIS.
 * </ul>
 * <p>
 * The tag types which a component/controller uses are discovered by
 * querying the propertyDB. All information required to operate with the tag (name,
 * pcFormat, direction, etc.) are obtained from the component's
 * entries in the propertyDB when a PlcTag object is created using
 * {@linkplain PlcTag#PlcTag(String)}.
 * 
 * 
 * @author Alastair Borrowman (OSL)
 * @author David Morris (NSO)
 *
 */
public interface IPlcTag {
	
	/*
	 *  Public constants
	 */
	/**
	 * Constant used to indicate an GIS PLC <b>read</b> tag.
	 * <p>
	 * A read tag is <i>read</i> by the GISS from the GIS PLC.
	 */
	public static final int DIRECTION_READ = 0;
	/**
	 * Constant used to indicate an GIS PLC <b>write</b> tag.
	 * <p>
	 * A write tag is <i>written</i> by the GISS to the GIS PLC.
	 */
	public static final int DIRECTION_WRITE = 1;
	
	/*
	 *  Constants defining the common GIS PLC tag types
	 */
	/**
	 * The GIS PLC tag type <b>CStatus</b>.
	 */
	public static final String GIS_TAG_TYPE_CSTATUS = "OCS";
	/**
	 * The GIS PLC tag type <b>Cmds</b>.
	 */
	public static final String GIS_TAG_TYPE_CMDS = "Cmds";
	
	/*
	 *  Constants defining property DB entries
	 */
	/**
	 * Property DB entry containing list of controller's tags.
	 */
	public static final String PROPERTY_TAG_LIST = "tagList";
	/**
	 * Property DB entry containing tag information.
	 */
	public static final String PROPERTY_TAG = "tag";
	/**
	 * Property DB entry storing list of items contained in a tag.
	 */
	public static final String PROPERTY_ITEM_LIST = ":itemList";
	/**
	 * Property DB entry containing tag data item information.
	 */
	public static final String PROPERTY_ITEM = ":item";
	/**
	 * Property DB PLC tag item name prefix used to identify a
	 * tag data item used as <i>padding</i> to ensure PLC tag
	 * byte boundary rules are maintained (see PLCIO user manual
	 * for details).
	 */
	public static final String PROPERTY_ITEM_PADDING_PREFIX = "charPad";
	/**
	 * Property DB entry storing interval value at which a read tag
	 * will be read from the GIS PLC.
	 */
	public static final String PROPERTY_INTERVAL = ":interval";
	/**
     * Property DB entry storing current connection status.
     */
    public static final String PROPERTY_CONNECTION_STATUS = ":connectionStatus";
    /**
     * Property DB entry storing the reconnect interval value at which
     * rate the component's Connection class shall attempt to establish
     * a connection to read/write the tag.
     */
    public static final String PROPERTY_RECONNECT_INTERVAL = ":reconnectInterval";
	/**
	 * Property DB entry storing read now value.
	 */
	public static final String PROPERTY_READ_NOW = ":readNow";
	/** 
	 * PropertyDB entry storing the last update (read or write) time of the tag.
	 * */
	public static final String PROPERTY_LAST_UPDATE_TIME = ":lastUpdateTime";

	
	/**
	 * Enumeration representing the propertyDB types used by GIS PLC
	 * tag items.
	 */
	public enum PropTypes {
		BOOLEAN ("boolean"),
		INTEGER ("integer"),
		STRING ("string"),
		REAL ("real");
		
		private final String private_typeString;
		
		PropTypes(String typeString) {
			private_typeString = typeString;
		}
		
		/**
		 * Get the string representation of this propertyDB type.
		 * 
		 * @return The string of this type.
		 */
		public String getTypeString() {return this.private_typeString;}
		
	} // end enum PropTypes

	/*
	 *  Constants defining Cache attributes storing tag metadata
	 */
	/**
	 * PropertyDB attribute storing tag and tag item metadata information.
	 */
	public static final String TAG_METADATA = ":metadata";

	
	/**
	 * Enumeration describing the contents of the tag's propertyDB attribute
	 * {@linkplain IPlcTag#TAG_METADATA} value.
	 */
	public enum TagMetadataIndex {
		PCFORMAT (0),
		BYTE_LENGTH (1),
		ITEM_LIST_START (2);
		
		private final int private_index;
		
		private TagMetadataIndex(int index){
			private_index = index;
		}
		
		public int getIndex() {return private_index;}
	} // end enum TagMetadataIndex
	
	/**
	 * Enumeration describing the contents of a tag item's propertyDB attribute
	 * {@linkplain IPlcTag#TAG_METADATA} value.
	 */
	public enum TagItemMetadataIndex {
		PLCIO_TYPE (0),
		MEMBER_NUM (1),
		BYTE_POS (2),
		DESCRIPTION (3),
		DEFAULT_VAL (4);
		
		private final int private_index;
		
		private TagItemMetadataIndex(int index){
			private_index = index;
		}
		
		public int getIndex() {return private_index;}
	} // end enum TagItemMetadataIndex
	
	/*
	 * Interface methods
	 */
	/**
	 * Get the name of the PLC tag this object represents.
	 * <p>
	 * The name of the tag is the identifier of the tag in
	 * the GIS PLC. The object's tag name is set on creation
	 * and is immutable.
	 * 
	 * @return	The tag's name.
	 */
	public String getName();

	/**
	 * Get this tag's PLCIO pcFormat string.
	 * <p>
	 * The tag's pcFormat string fully describes the data types
	 * and length of the data contained in the tag. The tag object's pcFormat
	 * string is set on creation and is immutable.
	 * 
	 * @return	The tag's pcFormat string.
	 */
	public String getPcFormatString();

	/**
	 * Get the ArrayList containing all type descriptors contained
	 * in this tag's PLCIO pcFormat string.
	 * <p>
	 * Each element in the list is a {@linkplain PlcioPcFormatType} object and the
	 * list's order matches the order they are defined in the pcFormat string.
	 * 
	 * @return	An ArrayList containing this tag's type descriptors.
	 */
	public ArrayList<PlcioPcFormatType> getPcFormatTypeAL();

	/**
	 * Get the communication direction of this tag - one of READ or WRITE.
	 * <p>
	 * The direction of a tag object is set on creation and is immutable.
	 * 
	 * @return	The direction of this tag, either {@linkplain PlcTag#DIRECTION_READ}
	 * or {@linkplain PlcTag#DIRECTION_WRITE}.
	 */
	public int getDirection();
	
	/**
	 * Get the timeout in ms used in PLCIO function call when communicating (reading
	 * or writing) this tag to the GIS PLC.
	 *  
	 * @return PLCIO function timeout in ms.
	 */
	public int getPlcioTimeoutMs();

	/**
	 * Get the total number of members of this tag as described by its PLCIO
	 * pcFormat string.
	 * <p>
	 * The array returned by {@linkplain PlcTag#getMemberValues()} will have length
	 * equal to value returned by this method.
	 * 
	 * @return	Total number of member of this tag.
	 */
	public int getMemberTotal();

	/**
	 * Get the PLCIO pcFormat type used to store the given tag member in the PLC.
	 * 
	 * @param memberNum The tag member number for which the type is to be returned.
	 * 
	 * @return A {@linkplain IPlcioPcFormatType} describing the PLCIO pcFormat type
	 * 		of the tag member.
	 */
    public IPlcioPcFormatType getMemberPcFormatType(int memberNum);
    
	/**
	 * Get the list of tag data items stored in given member.
	 * <p>
	 * 
	 * @param memberNum	The tag member number for which the list of tag data items are
	 * 		to be returned.
	 * 
	 * @return	A String array containing all tag data item names of the tag's member.
	 */
	public String[] getMemberItemList(int memberNum);

	/**
	 * Get the total length in bytes of the data stored in this tag.
	 * 
	 * @return	The total byte length of this tag's data.
	 */
	public int getTotalByteLength();
	
	/**
	 * Get all item names stored in this tag.
	 * 
	 * @return An array of strings containing the names of all data items
	 * stored in this tag.
	 */
    public String[] getItemNames();
	
	/**
	 * Return whether the named item is a tag data item contained
	 * in this tag.
	 * 
	 * @param itemName	The name of tag data item to check.
	 * 
	 * @return <b>true</b> if param itemName is a tag data item contained
	 * in this tag, otherwise <b>false</b>.
	 */
	public boolean isValidTagItem(String itemName);
	
	/**
	 * Get the property DB name used to store named item in
	 * property DB and Cache.
	 * 
	 * @param itemName	A valid item name of this tag.
	 * 
	 * @return	The property DB name storing tag data item value.
	 */
	public String getTagItemPropName(String itemName);
	
	/**
	 * Get the propertyDB type used to store this item in
	 * propertyDB and Cache as a String.
	 * 
	 * @param itemName	A valid item name of this tag.
	 * 
	 * @return	The String of property DB type storing tag data item value.
	 */
	public String getTagItemPropTypeString(String itemName);
	
	/**
	 * Get the propertyDB type used to store this item in
	 * propertyDB and Cache as a {@linkplain PropTypes}.
	 * 
	 * @param itemName	A valid item name of this tag.
	 * 
	 * @return	The {@linkplain PropTypes} enumeration of
	 * 		property DB type storing tag data item value.
	 */
	public PropTypes getTagItemPropType(String itemName);
	
	/**
	 * Get the PLCIO type of the tag's member that this item
	 * of the tag is stored in.
	 * 
	 * @param itemName	A valid item name of this tag.
	 * 
	 * @return A PLCIO type as defined in {@linkplain PlcioPcFormat}.
	 */
	public char getTagItemMemberPlcioType(String itemName);

	/**
	 * Get the member number of the tag that this data item
	 * is contained in.
	 * 
	 * @param itemName	A valid item name of this tag.
	 * 
	 * @return The tag member this data item is contained in.
	 */
	public int getTagItemMemberNum(String itemName);

	/**
	 * Get the byte position in the tag that this item
	 * is stored in.
	 * 
	 * @param itemName	A valid item name of this tag.
	 * 
	 * @return The item's byte position.
	 */
	public int getTagItemBytePos(String itemName);

	/**
	 * Get the bit position in the tag's byte that this item
	 * is stored in. Only valid for tag items of type boolean.
	 * 
	 * @param itemName	A valid item name of this tag of type boolean.
	 * 
	 * @return The item's bit position or -1 if this tag item is not
	 *		stored in propertyDB as type boolean.
	 */
	public int getTagItemBitPos(String itemName);

	/**
	 * Get the bitmask used to mask this tag's boolean item.
	 * Only valid for tag items of type boolean.
	 * 
	 * @param itemName	A valid item name of this tag of type boolean.
	 * 
	 * @return The item's bit mask or -1 if this tag item is not
	 *		stored in propertyDB as type boolean.
	 */
	public int getTagItemBitMask(String itemName);

	/**
	 * Get the current values of this tag stored in the PlcTag object.
	 * <p>
	 * The values are <b>not</b> read from the PLC, the tag's
	 * values currently stored in the object are merely returned.
	 * 
	 * @return	A String array containing the tag's values.
	 */
	public String[] getMemberValues();

	/**
	 * Get the current value of the tag member containing the given
	 * tag's item.
	 * <p>
	 * The value is <b>not</b> read from the PLC, the tag's member value
	 * containing the named tag item, currently stored in the object is
	 * the value returned.
	 * 
	 * @param itemName the tag's item for which the member value is to
	 * be returned.
	 * @return the tag's member value in which the tag item is contained.
	 */
	public String getMemberValue(String itemName);

	/**
     * Get all of this tag's data items from the Cache and return in
     * AttributeTable.
     * <p>
     * The values are <b>not</b> read from the PLC; the data items belonging
     * to this tag currently stored in the Cache are used to populate the
     * returned table.
     * 
     * @return	Attribute table containing the tag data item values currently
     * stored in Cache.
     */
    public IAttributeTable getCacheTagItemValues();
    
    /**
     * Set the tag's data items in the Cache to values contained in AttributeTable.
     * <p>
     * The values are <b>not</b> written to the PLC; the data items belonging
     * to this stored in the Cache are updated to those in the table.
     * 
     * @param	newValues Attribute table containing the new tag data item values
     * to be stored in the Cache.
     */
    public void setCacheTagItemValues(IAttributeTable newValues);
    
	/**
	 * Set the member values stored in this tag to new given values, also update
	 * the Cache to new values and update the last update time.
	 * <p>
	 * No values are read from or written to the PLC, this tag's values
	 * are merely updated to store the new given values and the tag's
	 * data items in the Cache are updated to reflect the new values.
	 * 
	 * @param newValues	A String array containing the new values to be stored
	 * in the tagValues of this tag object and used to update the Cache.
	 * 
	 * @return	The length of this object's tagValues array or -1 if error occurred.
	 */
	public int setMemberValues(String[] newValues);

	/**
	 * Set the member values stored in this tag to tag's data item values
	 * currently stored in the Cache and update the last update time.
	 * <p>
	 * No values are read from or written to the PLC, this tag's values
	 * are merely updated from the tag's data items in the Cache.
	 * This method is used prior to passing the PlcTag object to the
	 * {@linkplain ABPlcioChannel#write(IPlcTag)} to write this
	 * tag's values contained in Cache to the PLC.
	 *  
	 * @return	The length of this object's tagValues array or -1 if error occurred.
	 */
	public int setMemberValues();

	/**
	 * Return a String representation of the time that this tag was last updated.
	 * <p>
	 * If this tag is a READ tag then the time will be the time this tag
	 * was last read from the GIS. If this tag is a WRITE tag the time
	 * will be the time the tag was last written to the GIS.
	 * 
	 * @return	The time of the last update to this tag's values in format returned
	 * from {@linkplain atst.cs.util.AtstDate#getCurrent()}.toString().
	 */
	public String getValuesLastUpdateString();

	/**
	 * Return a String representation of this tag object's tag values.<br>
	 * <p>
	 * The format of the returned String is:<br>
	 * {@code {[0] = '&lt;member_0_value&gt;', [1] = '&lt;member_1_value&gt;', ..., [n] = '&lt;member_n_value&gt;'}}
	 * 
	 * @return	A String representation of this tag's values.
	 */
	public String tagValuesToString();

	/**
	 * Check whether given tag object equals this tag object.
	 * <p>
	 * Tags are considered equal if all but their values and last update
	 * time are equal.
	 * 
	 * @return	True if tags are equal, otherwise false.
	 */
	public boolean equals(Object obj);
	
	/**
	 * Return a String representation of the PlcTag object.
	 * 
	 * @return	A String representation of this tag.
	 */
	public String toString();

}
