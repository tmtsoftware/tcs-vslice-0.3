package atst.giss.abplc;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


/**
 * The class PlcTag describes an Allen-Bradley PLC tag and stores its values in the Cache.
 * <p>
 * The information required by the GISS to operate with the PLC tag is stored in the propertyDB
 * in attributes using a well-defined naming structure that uses as its base the format:
 * <ul>
 * <li><code>&lt;controllerName&gt;.tag:&lt;propertyTagName&gt;</code>
 * </ul>
 * For example, all propertyDB attributes storing the Cover controller's coverCStatus tag
 * information (meta-data) begin with <code>atst.giss.ghc.tag:GIC_to_OCS</code>.
 * <p>
 * The meta-data of each tag is read from the propertyDB on PlcTag object creation
 * from the following attributes that are prefixed with the above base format to get
 * their full name:
 * <ul>
 * <li><code>:pcFormat</code> &mdash; the PLCIO pcFormat string that fully describes the
 * contents of this tag. Each alphabetic character of the pcFormat describes the type
 * of data stored in the tag, for example <code>j</code> describes data of type
 * integer. Optionally the type character may be followed by the length in bytes, for
 * example, <code>j8</code> would describe data that consists of 2 integers (1 integer
 * has length 4 bytes), this would be equivalent to <code>jj</code>. The order in which the
 * <i>type descriptors</i> (and optional byte length) appear in the pcFormat string describe
 * the byte stream that is transferred between GISS and GIS when the tag is transferred.
 * The class {@linkplain PlcioPcFormat} is used to work with pcFormat
 * strings.
 * <li><code>:direction</code> &mdash; the direction of a tag can be either <code>read</code>
 * or <code>write</code>. No tags are bi-directional.
 * <li><code>:itemList</code> &mdash; contains a list of all the data <b><i>items</i></b>
 * stored in this tag in the order in which they appear when the tag is transferred as a byte
 * stream. Tag pcFormat type descriptors may
 * may relate to one or more tag <i>items</i>. For example, a tag described
 * by the pcFormat <code>jj</code>, may contain in its first 4 bytes the single data item relating
 * to the current tracking mode of the GIS as an integer, the second 4 bytes may contain
 * up to 32 Boolean data items containing the various status of limit switches, drive
 * status, etc.
 *</ul>
 * 
 * @author Alastair Borrowman (OSL)
 */
public class PlcTag implements IPlcTag {

    /*
     *  Private class constants
     */
    /** Log category of PlcTag. */
    private static final String LOG_CAT = "PLC_TAG";    
    /** Log category of PlcTag relating to tag item operations. */
    private static final String LOG_CAT_ITEM = LOG_CAT + "_ITEM";    
    /** Array containing all log categories used in this class. */
    private static final String[] LOG_CATS_INUSE = {LOG_CAT,  LOG_CAT_ITEM};
    
    /** propertyDB entry storing PLCIO pcFormat string of tag. */
    private static final String PROPERTY_PCFORMAT = ":pcformat";

    /** propertyDB entry storing direction (read or write) of tag. */
    private static final String PROPERTY_DIRECTION = ":direction";
    
    /** Value of propertyDB direction attribute if direction is read. */
    private static final String TAG_DIRECTION_CONST_READ = "read";

    /** Value of propertyDB direction attribute if direction is write. */
    private static final String TAG_DIRECTION_CONST_WRITE = "write";
    
    /** propertyDB entry storing the PLCIO timeout in seconds. */
    private static final String PROPERTY_PLCIO_TIMEOUT = ":plcioTimeout";

    /** propertyDB entry storing item information. */
    private static final String PROPERTY_ITEM = ":item";

    /** propertyDB entry storing the bit position within a tag member of the tag item. */
    private static final String PROPERTY_BIT_POS = ":bitPos";

    /** The index in array returned from Java String.split() method containing
     * the tag name when used on a String representation of a property attribute. */
    private static final int PROPERTY_TAG_SPLIT_INDEX_TAG_NAME = 1;

    /** The index in array returned from Java String.split() method containing
     * the tag data item when used on a String representation of a property attribute. */
    private static final int PROPERTY_TAG_SPLIT_INDEX_TAG_ITEM_NAME = 3;

    /** Constant String value used to represent a boolean value of true.
     * Boolean attributes may contain strings not limited to values 'true' or
     * 'false' but may also use 'open' or 'close', etc. */
    private static final String BOOLEAN_STRING_TRUE = "true";

    /** Constant String value used to represent a boolean value of true. */
    private static final String BOOLEAN_STRING_FALSE = "false";
    
    /*
     * Class (static) methods
     */
    
    /**
     * Get the list of all CSF log categories used by this class.
     * 
     * @return String array containing all log categories used.
     */
    public static String[] getLogCatsUsed() {
        return LOG_CATS_INUSE;
    }
    
    /*
     *  Private instance variables holding meta-data of Tag read from propertyDB.
     */
    /** The name of this tag as used as the tag identifier in the Allen-Bradley PLC. */
    private String tagName;

    /** The name of tag in propertyDB */
    private String propTagName;

    /** The direction (read or write) of this tag. */
    private final int direction;

    /** The direction of this tag in String format. */
    private String directionStr;
    
    /** The timeout in ms used in PLCIO function calls. */
    private int plcioTimeoutMs;

    /** The PLCIO pcFormat string completely describing the data types and length
     *  of the data contained in this tag. */
    private String pcFormat;

    /** A Java ArrayList containing {@linkplain PlcioPcFormatType} objects for
     * each individual data type described in this object's pcFormat string
     * stored in pcFormat. */
    private ArrayList<PlcioPcFormatType> pcFormatTypeAL;
    
    /** The total number of data members contained in this tag as described by
     * the pcFormat string stored in pcFormat. */
    private int totalMembers;
    
    /** The total number of bytes used to store all data members contained in
     * this tag as described by the pcFormat string stored in pcFormat. */
    private int totalByteLength;

    /** Java HashMap storing by tag data 'item name' a reference to a TagItem
     * object containing item's information. */
    private Map<String,TagItem> tagItemMap;

    /** Java HashMap storing by 'tag member number' a Java ArrayList containing
     * list of tag item names stored in this tag member. The tag item names are
     * used to get the item's TagItem object from the tagItemMap. */
    private Map<Integer,ArrayList<String>> tagMemberItemNamesMap;

    /** The actual member data values of the tag stored as Strings. If this is
     * a READ tag then the values will be those read from the PLC. If this is a
     * WRITE tag then the values will be those that are to be written to the PLC.
     * The pcFormat is used to convert the actual values read or written to/from
     * the PLC to the correct data types contained in the tag. */
    private String[] memberValues;

    /** The time of the last update to memberValues as returned by call to
     *  {@linkplain AtstDate#getCurrent()}.toString() */
    private String valuesLastUpdated;

    /*
     *  Class methods
     */
    /**
     * Given an attribute name that follows the format
     * <code>&lt;controllerName&gt;.tag:&lt;tagName&gt;</code>
     * retrieve the <code>&lt;tagName&gt;</code>.
     * 
     * @param attName    The attribute name containing a tag name.
     * 
     * @return    String containing the tag name or null if param
     * attName does not contain String of correct format to contain
     * a tag name.
     */
    public static String getTagNameFromAttributeName(String attName) {
        String[] attNameSplit = attName.split(":");
        
        if (attNameSplit.length > PROPERTY_TAG_SPLIT_INDEX_TAG_NAME) {
            return attNameSplit[PROPERTY_TAG_SPLIT_INDEX_TAG_NAME];
        }
        
        return null;
    }

    /**
     * Given an attribute name that follows the format
     * <code>&lt;controllerName&gt;.tag&lt;tagName&gt;:item&lt;itemName&gt;</code>
     * retrieve the <code>&lt;itemName&gt;</code>.
     * 
     * @param attName    The attribute name containing a tag item name.
     * 
     * @return    String containing the tag item name or null if param
     * attName does not contain String of correct format to contain
     * an item name.
     */
    public static String getTagItemNameFromAttributeName(String attName) {
        String[] attNameSplit = attName.split(":");
        
        if (attNameSplit.length > PROPERTY_TAG_SPLIT_INDEX_TAG_ITEM_NAME) {
            return attNameSplit[PROPERTY_TAG_SPLIT_INDEX_TAG_ITEM_NAME];
        }        
        
        return null;
    }


    public PlcTag(String tagName, int direction, String pcFormat, int plcioTimeoutMs,
                  int totalMembers, int totalByteLength, String[] tagItemNames, String[] tagItemTypes) throws Exception {
        this.tagName = tagName;
        this.propTagName = PROPERTY_TAG + ":" + tagName;
        this.direction = direction;
        this.pcFormat = pcFormat;
        this.plcioTimeoutMs = plcioTimeoutMs;
        this.totalMembers = totalMembers;
        this.totalByteLength = totalByteLength;

        pcFormatTypeAL = PlcioPcFormat.plcioPcFormatStr2ArrayList(pcFormat);

        // create HashMap to store by item name a TagItem object for each item in tag
        // create HashMap storing by tag member number an ArrayList containing all
        // tag data item names contained in the member

        // tagItemNames are ordered by sequence in the tag, as is tagItemTypes
        constructTagMemberItemMaps(tagItemNames, tagItemTypes);

    }



   /**
     * Create a new PlcTag object.
     * <p>
     * The new tag is created from information stored in the propertyDB
     * located under the tag name passed as input parameter.
     * The information from the propertyDB contains the tag's meta-data
     * that contains all information required to use the tag:
     * <ul>
     * <li>tag name (as recognized by GIS and defined in GISS/GIS ICD)
     * <li>pcFormat
     * <li>direction (read or write)
     * <li>tag data items
     * </ul>
     * 
     * @param tName        The string used in the propertyDB to identify all
     * attributes relating to the PLC tag that the tag object is to represent.
     * 
     * @throws ABPlcioExceptionBadPlcTagProperties If the tag cannot be parsed
     */
    public PlcTag(String tName) throws ABPlcioExceptionBadPlcTagProperties {
        Log.debug(LOG_CAT, 2, "Creating PlcTag object for tag named '" + tName + "'");

        tagName = tName;
        propTagName = PROPERTY_TAG + ":" + tName;
        AttributeTable tagMetadataTable = new AttributeTable();
        
        // what direction is the tag - read or write?
        directionStr = Cache.lookup(propTagName + PROPERTY_DIRECTION).getString();
        if (directionStr.equals(TAG_DIRECTION_CONST_READ)) {
            direction = IPlcTag.DIRECTION_READ;
        }
        else if (directionStr.equals(TAG_DIRECTION_CONST_WRITE)) {
            direction = IPlcTag.DIRECTION_WRITE;
        }
        else {
            throw new ABPlcioExceptionBadPlcTagProperties("PLC tag '" + tagName + "' property '" +
                    propTagName + PROPERTY_DIRECTION + "' does not contain a valid direction, must have " +
                    "value '" + TAG_DIRECTION_CONST_READ + "' or '" + TAG_DIRECTION_CONST_WRITE + "'");
        }
        
        // what is the timeout used in calls to PLCIO function when communicating
        // (reading or writing) this tag with the GIS PLC?
        plcioTimeoutMs = (int) Math.round(Cache.lookup(propTagName + PROPERTY_PLCIO_TIMEOUT).getDouble() * 1000.0);

        // get the PLCIO pcFormat string that fully describes the types of data
        // contained in this tag and create the ArrayList containing each type
        // it describes
        pcFormat = Cache.lookup(propTagName + PROPERTY_PCFORMAT).getString();


        pcFormatTypeAL = PlcioPcFormat.plcioPcFormatStr2ArrayList(pcFormat);

        // read the list of tag data items stored in this tag from the propertyDB
        
        String[] tagItemNames = Cache.lookup(propTagName + PROPERTY_ITEM_LIST).getStringArray();


        //constructTagMemberItemMaps(tagItemNames);

        // collect and store tag metadata in the Cache so that it can be retrieved using get
        assembleAndStoreMetadata(tagItemNames, tagMetadataTable, totalByteLength);


        Log.debug(LOG_CAT, 4, "PlcTag object created = " + this.toString());

    } // end Constructor


    // tag item types is array of strings.  Only valid values are from IPlcTag.PropTypes

    private void constructTagMemberItemMaps(String[] tagItemNames, String[] tagItemTypes) throws ABPlcioExceptionBadPlcTagProperties {

        // create HashMap to store by item name a TagItem object for each item in tag
        tagItemMap = new HashMap<String,TagItem>(tagItemNames.length);
        // create HashMap storing by tag member number an ArrayList containing all
        // tag data item names contained in the member
        tagMemberItemNamesMap = new HashMap<Integer,ArrayList<String>>();

        // parse each type described by the pcFormat of this tag and create a TagItem
        // object for each tag item named in tagItemNames
        TagItem thisTagItem;
        int memberNum = 0;
        int tagBytePos = 0;
        int itemNum = 0;
        for (int pcFormatTypeDescNum = 0; pcFormatTypeDescNum < pcFormatTypeAL.size(); pcFormatTypeDescNum++) {
            // what type is pcFormat describing?
            char plcioTypeId = pcFormatTypeAL.get(pcFormatTypeDescNum).getTypeId();
            // how many bytes are used to store all type members?
            int typeTotalBytes = pcFormatTypeAL.get(pcFormatTypeDescNum).getByteLen();
            Log.debug(LOG_CAT_ITEM, 4, "Creating tag '" + tagName + "' data items described by pcFormat " +
                    "type '" + plcioTypeId + "' byte length = " + typeTotalBytes +
                    ". About to process tag member # " + memberNum + ", data item # " + itemNum +
                    " (item total = " + tagItemNames.length +
                    "), starting at tag byte position = " + tagBytePos);

            // based on type and number of bytes progress along tagItemNames creating
            // a TagItem until all space (bytes) described by this pcFormat type have been filled
            int bytesUsed = 0;
            while (bytesUsed < typeTotalBytes) {
                // create the ArrayList storing all item names stored in current tag member
                // that will be added to the tagMemberItemNamesMap
                ArrayList<String> thisMemberItemNamesAL = new ArrayList<String>();
                Log.note(Integer.toString(itemNum));
                //Log.note(Arrays.toString(tagItemNames));
                String itemName = tagItemNames[itemNum];
                String propItemName = propTagName + PROPERTY_ITEM + ":" + itemName;
                // get the propertyDB type used to store this tag item as this informs
                // how many bytes are used in the pcFormat to describe it

                // sm - removed access to propertyDB.  Assume instead that these are passed in
                String propItemType = tagItemTypes[itemNum];
                // is this a propertyDB type this class knows how to deal with?
                isValidPropType(propItemName, propItemType);

                switch (plcioTypeId) {
                    case PlcioPcFormat.TYPE_C: // char = 1 byte
                        if (propItemType.equals(PropTypes.STRING.getTypeString())) {
                            // type describes a single item of type char
                            thisTagItem = new TagItem(itemName, propItemName, propItemType,
                                    memberNum, plcioTypeId, tagBytePos);
                            tagItemMap.put(itemName, thisTagItem);
                            thisMemberItemNamesAL.add(itemName);
                            Log.debug(LOG_CAT_ITEM, 4, "Created: " + thisTagItem.toString());

                            itemNum++;
                        } else {
                            throw new ABPlcioExceptionBadPlcTagProperties("PLCIO pcFormat of type '" +
                                    plcioTypeId + "' (" + PlcioPcFormat.getTypeString(plcioTypeId) + ") of property '" +
                                    propItemName + "' can not be used to describe types stored in propertyDB as '" +
                                    propItemType + "'");
                        }

                        bytesUsed += PlcioPcFormat.getTypeByteLength(PlcioPcFormat.TYPE_C);
                        tagBytePos += PlcioPcFormat.getTypeByteLength(PlcioPcFormat.TYPE_C);
                        break;

                    case PlcioPcFormat.TYPE_I: // short = 2 bytes
                        if (propItemType.equals(PropTypes.BOOLEAN.getTypeString())) {
                            // up to 16 boolean tag items from bit 0 to bit 15 can be stored in each 16 bit short
                            int bitsUsed = 0;
                            while (propItemType.equals(PropTypes.BOOLEAN.getTypeString()) && (bitsUsed <= 15)) {
                                // create a TagItem object
                                thisTagItem = new TagItem(itemName, propItemName, propItemType,
                                        memberNum, plcioTypeId, tagBytePos);
                                tagItemMap.put(itemName, thisTagItem);
                                thisMemberItemNamesAL.add(itemName);
                                Log.debug(LOG_CAT_ITEM, 4, "Created: " + thisTagItem.toString());
                                Log.debug(LOG_CAT_ITEM, 4, "Inside PlcTag.java, PlcioPcFormat.TYPE_J");
                                bitsUsed = thisTagItem.getBitPos();
                                if (bitsUsed > 15) {
                                    throw new ABPlcioExceptionBadPlcTagProperties("Too many boolean tag items " +
                                            " defined in propertyDB for PLCIO typeId of '" +
                                            plcioTypeId + "' (" + PlcioPcFormat.getTypeString(plcioTypeId) +
                                            ") bit position defined  must range from 0 to 15, last tag item " +
                                            " propertyDB attributes read for tag '" + tagName +
                                            "' created item: '" + propItemType + "'");
                                }

                                itemNum++;
                                if (itemNum >= tagItemNames.length) {
                                    // no more items to process so break out of while loop
                                    break;
                                }
                                Log.note("ITEMNUM: " + itemNum);

                                itemName = tagItemNames[itemNum];
                                propItemName = propTagName + PROPERTY_ITEM + ":" + itemName;
                                Log.note("PROPITEMNAME: " + propItemName);
                                propItemType = Property.getType(propItemName);
                                Log.note("PROPITEMTYPE: " + propItemType);
                                isValidPropType(propItemName, propItemType);

                                // have we just created the last boolean item of a tag member
                                // and about to move on to a boolean item of a new tag member?
                                if (propItemType.equals(PropTypes.BOOLEAN.getTypeString())) {
                                    int nextItemBitPos = Cache.lookup(propItemName + PROPERTY_BIT_POS).getInteger();
                                    if (nextItemBitPos <= bitsUsed) {
                                        // next item can't be in the same tag member as previous item
                                        // so break out of while loop to move to next member
                                        break;
                                    }
                                }
                            } // end while
                        } else if (propItemType.equals(PropTypes.INTEGER.getTypeString())) {
                            // type describes a single item of type short
                            thisTagItem = new TagItem(itemName, propItemName, propItemType,
                                    memberNum, plcioTypeId, tagBytePos);
                            tagItemMap.put(itemName, thisTagItem);
                            thisMemberItemNamesAL.add(itemName);
                            Log.debug(LOG_CAT_ITEM, 4, "Created: " + thisTagItem.toString());

                            itemNum++;
                        } else {
                            throw new ABPlcioExceptionBadPlcTagProperties("PLCIO pcFormat of type '" +
                                    plcioTypeId + "' (" + PlcioPcFormat.getTypeString(plcioTypeId) + ") of property '" +
                                    propItemName + "' can not be used to describe types stored in propertyDB as '" +
                                    propItemType + "'");
                        }

                        bytesUsed += PlcioPcFormat.getTypeByteLength(PlcioPcFormat.TYPE_I);
                        tagBytePos += PlcioPcFormat.getTypeByteLength(PlcioPcFormat.TYPE_I);
                        break;

                    case PlcioPcFormat.TYPE_J: // integer = 4 bytes
                        if (propItemType.equals(PropTypes.BOOLEAN.getTypeString())) {
                            // up to 32 boolean tag items from bit 0 to bit 31 can be stored in each 32 bit integer
                            int bitsUsed = 0;
                            while (propItemType.equals(PropTypes.BOOLEAN.getTypeString()) && (bitsUsed <= 31)) {
                                // create a TagItem object
                                thisTagItem = new TagItem(itemName, propItemName, propItemType,
                                        memberNum, plcioTypeId, tagBytePos);
                                tagItemMap.put(itemName, thisTagItem);
                                thisMemberItemNamesAL.add(itemName);
                                Log.debug(LOG_CAT_ITEM, 4, "Created: " + thisTagItem.toString());

                                bitsUsed = thisTagItem.getBitPos();
                                if (bitsUsed > 31) {
                                    throw new ABPlcioExceptionBadPlcTagProperties("Too many boolean tag items " +
                                            " defined in propertyDB for PLCIO typeId of '" +
                                            plcioTypeId + "' (" + PlcioPcFormat.getTypeString(plcioTypeId) +
                                            ") bit position defined  must range from 0 to 31, last tag item " +
                                            " propertyDB attributes read for tag '" + tagName +
                                            "' created item: '" + propItemType + "'");
                                }

                                itemNum++;
                                if (itemNum >= tagItemNames.length) {
                                    // no more items to process so break out of while loop
                                    break;
                                }
                                Log.note("ITEMNUM: " + itemNum);
                                itemName = tagItemNames[itemNum];
                                propItemName = propTagName + PROPERTY_ITEM + ":" + itemName;
                                Log.note("PROPITEMNAME: " + propItemName);
                                propItemType = Property.getType(propItemName);
                                Log.note("PROPITEMTYPE: " + propItemType);
                                isValidPropType(propItemName, propItemType);

                                // have we just created the last boolean item of a tag member
                                // and about to move on to a boolean item of a new tag member?
                                if (propItemType.equals(PropTypes.BOOLEAN.getTypeString())) {
                                    //Log.note("I'm inside BITPOS");
                                    int nextItemBitPos = Cache.lookup(propItemName + PROPERTY_BIT_POS).getInteger();
                                    if (nextItemBitPos <= bitsUsed) {
                                        // next item can't be in the same tag member as previous item
                                        // so break out of while loop to move to next member
                                        break;
                                    }
                                }
                            } // end while
                        } else if (propItemType.equals(PropTypes.INTEGER.getTypeString())) {
                            // type describes a single item of type integer
                            thisTagItem = new TagItem(itemName, propItemName, propItemType,
                                    memberNum, plcioTypeId, tagBytePos);
                            tagItemMap.put(itemName, thisTagItem);
                            thisMemberItemNamesAL.add(itemName);
                            Log.debug(LOG_CAT_ITEM, 4, "Created: " + thisTagItem.toString());

                            itemNum++;
                        } else {
                            throw new ABPlcioExceptionBadPlcTagProperties("PLCIO pcFormat of type '" +
                                    plcioTypeId + "' (" + PlcioPcFormat.getTypeString(plcioTypeId) + ") of property '" +
                                    propItemName + "' can not be used to describe types stored in propertyDB as '" +
                                    propItemType + "'");
                        }

                        bytesUsed += PlcioPcFormat.getTypeByteLength(PlcioPcFormat.TYPE_J);
                        tagBytePos += PlcioPcFormat.getTypeByteLength(PlcioPcFormat.TYPE_J);
                        break;

                    case PlcioPcFormat.TYPE_Q: // long = 8 bytes
                        throw new ABPlcioExceptionBadPlcTagProperties("PLCIO pcFormat of type '" +
                                plcioTypeId + "' (" + PlcioPcFormat.getTypeString(plcioTypeId) +
                                ") not currently supported.");


                        // bytesUsed += PlcioPcFormat.getTypeByteLength(PlcioPcFormat.TYPE_Q);
                        // tagBytePos += PlcioPcFormat.getTypeByteLength(PlcioPcFormat.TYPE_Q);

                        // NB. currently don't need break following this case due to it
                        // throwing exception - if this changes in future the break WILL BE NEEDED.
                        // break;

                    case PlcioPcFormat.TYPE_R: // real = 4 bytes
                        if (propItemType.equals(PropTypes.REAL.getTypeString())) {
                            // type describes a single item of type real
                            thisTagItem = new TagItem(itemName, propItemName, propItemType,
                                    memberNum, plcioTypeId, tagBytePos);
                            tagItemMap.put(itemName, thisTagItem);
                            thisMemberItemNamesAL.add(itemName);
                            Log.debug(LOG_CAT_ITEM, 4, "Created: " + thisTagItem.toString());

                            itemNum++;
                        } else {
                            throw new ABPlcioExceptionBadPlcTagProperties("PLCIO pcFormat of type '" +
                                    plcioTypeId + "' (" + PlcioPcFormat.getTypeString(plcioTypeId) + ") of property '" +
                                    propItemName + "' can not be used to describe types stored in propertyDB as '" +
                                    propItemType + "'");
                        }

                        bytesUsed += PlcioPcFormat.getTypeByteLength(PlcioPcFormat.TYPE_R);
                        tagBytePos += PlcioPcFormat.getTypeByteLength(PlcioPcFormat.TYPE_R);
                        break;

                    case PlcioPcFormat.TYPE_D: // double = 8 bytes
                        throw new ABPlcioExceptionBadPlcTagProperties("PLCIO pcFormat of type '" +
                                plcioTypeId + "' (" + PlcioPcFormat.getTypeString(plcioTypeId) +
                                ") not currently supported.");


                        // bytesUsed += PlcioPcFormat.getTypeByteLength(PlcioPcFormat.TYPE_D);
                        // tagBytePos += PlcioPcFormat.getTypeByteLength(PlcioPcFormat.TYPE_D);

                        // NB. currently don't need break following this case due to it
                        // throwing exception - if this changes in future the break WILL BE NEEDED.
                        // break;

                    default:
                        throw new ABPlcioExceptionBadPlcTagProperties("Tag '" + tagName + "' has pcFormat '" +
                                pcFormat + "' that contains illegal PLCIO typeId '" + plcioTypeId + "'");
                } // end switch

                // add the list of all items stored in this tag member to the tagMemberItemNamesMap
                tagMemberItemNamesMap.put(memberNum, thisMemberItemNamesAL);

                // progress to the next member as described by this type descriptor in the pcFormat
                memberNum++;
            } // end while

        } // end for


        totalMembers = memberNum;
        totalByteLength = PlcioPcFormat.getPlcioPcFormatStrTotalBytes(pcFormat);

        // initialize tag values to null
        memberValues = new String[totalMembers];
        valuesLastUpdated = null;

    }




    private void assembleAndStoreMetadata(String[] tagItemNames, AttributeTable tagMetadataTable, int totalByteLength) {

        // collect and store tag metadata in the Cache so that it can be retrieved using get
        String[] tagMetadata = new String[TagMetadataIndex.values().length + (tagItemNames.length - 1)];
        tagMetadata[TagMetadataIndex.PCFORMAT.getIndex()] = pcFormat;
        tagMetadata[TagMetadataIndex.BYTE_LENGTH.getIndex()] = String.valueOf(totalByteLength);
        for (int i = 0; i < tagItemNames.length; i++) {
            TagItem item = tagItemMap.get(tagItemNames[i]);
            tagMetadata[TagMetadataIndex.ITEM_LIST_START.getIndex() + i] = item.getItemName();
            String[] itemMetadata = new String[TagItemMetadataIndex.values().length];
            itemMetadata[TagItemMetadataIndex.PLCIO_TYPE.getIndex()] = String.valueOf(item.getMemberPlcioType());
            itemMetadata[TagItemMetadataIndex.MEMBER_NUM.getIndex()] = String.valueOf(item.getMemberNum());
            if (item.isBoolean()) {
                itemMetadata[TagItemMetadataIndex.BYTE_POS.getIndex()] = String.format("%s (%d)",
                        item.getBytePos(), item.getBitPos());
            }
            else {
                itemMetadata[TagItemMetadataIndex.BYTE_POS.getIndex()] = String.valueOf(item.getBytePos());
            }
            itemMetadata[TagItemMetadataIndex.DESCRIPTION.getIndex()] = Property.getDescription(item.getPropItemName());
            itemMetadata[TagItemMetadataIndex.DEFAULT_VAL.getIndex()] = Property.getDefault(item.getPropItemName()).getString();

            tagMetadataTable.insert(item.getPropItemName()+TAG_METADATA, itemMetadata);
        }
        tagMetadataTable.insert(propTagName+TAG_METADATA, tagMetadata);
        Cache.storeAll(tagMetadataTable);

    }


    // Documented in IPlcTag
    @Override
    public String getName() {
        return tagName;
    } // end getName()

    // Documented in IPlcTag
    @Override
    public String getPcFormatString() {
        return pcFormat;
    } // end getFormatString()
    
    // Documented in IPlcTag
    @Override
    public ArrayList<PlcioPcFormatType> getPcFormatTypeAL() {
        // return a defensive copy so that callers cannot modify
        // this tag's PlcioPcFormatType ArrayList
        return new ArrayList<PlcioPcFormatType>(pcFormatTypeAL);
    } // end getPcFormatTypeAL()
    
    // Documented in IPlcTag
    @Override
    public int getDirection() {
        return direction;
    } // end getDirection()

    // Documented in IPlcTag
    @Override
    public int getPlcioTimeoutMs() {
        return plcioTimeoutMs;
    } // end getPlcioTimeoutMs()
    
    // Documented in IPlcTag
    @Override
    public int getMemberTotal() {
        return totalMembers;
    } // end getTotalMembers()
    
    // Documented in IPlcTag
    @Override
    public IPlcioPcFormatType getMemberPcFormatType(int memberNum) {
        IPlcioPcFormatType returnType = null;
        
        int memberNumberRange = 0;
        for (PlcioPcFormatType pcFormatType : pcFormatTypeAL) {
            if (memberNum < (memberNumberRange + pcFormatType.getNumberOfMembers())) {
                returnType = pcFormatType;
                break;
            }
            else {
                // set memberNumberRange to equal member number of next pcFormatType,
                // i.e starting at the end of this pcFormatType
                memberNumberRange += pcFormatType.getNumberOfMembers();
            }
        }
        
        return returnType;
    } // end getMemberPcFormatType()
    
    // Documented in IPlcTag
    @Override
    public String[] getMemberItemList(int memberNum) {
        return tagMemberItemNamesMap.get(memberNum).toArray(new String[0]);
    } // end getTotalMembers()
    
    // Documented in IPlcTag
    @Override
    public int getTotalByteLength() {
        return totalByteLength;
    } // end getTotalByteLength
    
    // Documented in IPlcTag
    @Override
    public String[] getItemNames() {
        return tagItemMap.keySet().toArray(new String[0]);
    } // end getItemNames()
    
    // Documented in IPlcTag
    @Override
    public boolean isValidTagItem(String itemName) {
        return tagItemMap.containsKey(itemName);
    }

    // Documented in IPlcTag
    @Override
    public String getTagItemPropName(String itemName) {
        return tagItemMap.get(itemName).getPropItemName();
    }
    
    // Documented in IPlcTag
    @Override
    public String getTagItemPropTypeString(String itemName) {
        return tagItemMap.get(itemName).getPropItemTypeString();
    }
    
    // Documented in IPlcTag
    @Override
    public PropTypes getTagItemPropType(String itemName) {
        return tagItemMap.get(itemName).getPropItemType();
    }
    
    // Documented in IPlcTag
    @Override
    public char getTagItemMemberPlcioType(String itemName) {
        return tagItemMap.get(itemName).getMemberPlcioType();
    }
    
    // Documented in IPlcTag
    @Override
    public int getTagItemMemberNum(String itemName) {
        return tagItemMap.get(itemName).getMemberNum();
    }
    
    // Documented in IPlcTag
    @Override
    public int getTagItemBytePos(String itemName) {
        return tagItemMap.get(itemName).getBytePos();
    }    

    // Documented in IPlcTag
    @Override
    public int getTagItemBitPos(String itemName) {
        return tagItemMap.get(itemName).getBitPos();
    }    

    // Documented in IPlcTag
    @Override
    public int getTagItemBitMask(String itemName) {
        return tagItemMap.get(itemName).getBitMask();
    }    

    // Documented in IPlcTag
    @Override
    public String [] getMemberValues() {
        return memberValues;
    } // end getMemberValues()
    
    // Documented in IPlcTag
    @Override
    public String getMemberValue(String itemName) {
        return memberValues[tagItemMap.get(itemName).getMemberNum()];
    } // end getMemberValue()
    
    // Documented in IPlcTag
    @Override
    public IAttributeTable getCacheTagItemValues() {
        AttributeTable table = new AttributeTable();
        
        for (TagItem item : tagItemMap.values()) {
            table.insert(Cache.lookup(item.getPropItemName()));            
        }

        return table;
    } // end getTagItemsFromCache()
    
    @Override

    public void setCacheTagItemValues(IAttributeTable newValues) {
        Cache.storeAll(newValues);
    } // end setTagItemsInCache()


    // Documented in IPlcTag
    @Override
    public int setMemberValues(String[] newValues) {

        if (newValues.length != memberValues.length) {
            // TODO throw exception rather than return -1
            return -1;
        }
        memberValues = newValues;
        valuesLastUpdated = new Date().toString();
        
        // update the tag data items in Cache
        AttributeTable tagItemsTable = new AttributeTable();
        for (int memberIndex = 0; memberIndex < memberValues.length; memberIndex++) {
            for (String itemName : tagMemberItemNamesMap.get(memberIndex)) {
                TagItem item = tagItemMap.get(itemName);
                if (item.isBoolean()) {
                    int statusWord = Integer.parseInt(memberValues[memberIndex]);
                    boolean val = ((statusWord & item.getBitMask()) == item.getBitMask());
                    tagItemsTable.insert(item.propItemName, Boolean.toString(val));
                }
                else {
                    tagItemsTable.insert(item.propItemName, memberValues[memberIndex]);
                }
            }
        }
        
        // add the tag last update time attribute
        tagItemsTable.insert(propTagName + PROPERTY_LAST_UPDATE_TIME, valuesLastUpdated);
        
        // store values in Cache
        Cache.storeAll(tagItemsTable);
        
        //Log.debug(LOG_CAT, 4, "tag '" + tagName + "' values in Cache now = " + tagItemsTable.toString());
                
        return memberValues.length;        
    } // end setMemberValues()

    // Documented in IPlcTag
    @Override
    public int setMemberValues() {
        int memberValuesSet = 0;
        
        Log.debug(LOG_CAT, 4, "tag '" + tagName +
                "' prior to setting memberValues from Cache tag data items memberValues = " // +
                //Misc.array2string(memberValues)
                );
        
        for (int memberIndex = 0; memberIndex < memberValues.length; memberIndex++) {
            // get the ArrayList containing all TagItem objects referencing all tag
            // data items stored in this tag member
            ArrayList<String> memberTagItemNamesAL = tagMemberItemNamesMap.get(memberIndex);
            
            if (memberTagItemNamesAL.size() == 1) {
                // if this tag member contains only 1 tag data item then simply copy
                // this data item's value from Cache into the memberValues array
                TagItem item = tagItemMap.get(memberTagItemNamesAL.get(0));
                memberValues[memberIndex] = item.getItemValueFromCache();
                memberValuesSet++;
            }
            else if (memberTagItemNamesAL.size() > 1) {
                // create a data word containing the value of each data item stored
                // in this tag member, and set member value to value of data word
                int statusWord = 0x0000;
                
                for (String itemName : memberTagItemNamesAL) {
                    TagItem item = tagItemMap.get(itemName);
                    if (!item.isBoolean()) {
                        // this should never happen...
                        Log.severe(LOG_CAT, "tag '" + tagName + "' memberNumber " + memberIndex +
                                " contains multiple data items but item '" + item.getItemName() +
                                "' is NOT of type boolean, item details:" + item.toString() +
                                "\n. Tag details: " + toString());

                        // TODO throw exception rather than return -1
                        return -1;
                    }
                    
                    if (item.getItemValueFromCache().equals(BOOLEAN_STRING_TRUE)) {
                        statusWord |= item.getBitMask();
                    }
                    else {
                        statusWord &= ~item.getBitMask();
                    }
                }
                memberValues[memberIndex] = Integer.toString(statusWord);
                memberValuesSet++;
            }
            else {
                // this should never happen...
                Log.warn(LOG_CAT, "tag '" + tagName + "' memberNumber " + memberIndex +
                        " contains no data items. Tag details: " + toString());

                // TODO throw exception rather than return -1
                return -1;
            }
        } // end for loop
    
        if (memberValuesSet != memberValues.length) {
            Log.severe(LOG_CAT, "when retriving data items of tag '" + tagName + " from Cache " +
                    "to update tag's memberValues prior to writing Tag the number of values set = " +
                    memberValuesSet + " but memberValues.length = " + memberValues.length +
                    ". Tag details: " + toString());
            
            // TODO throw exception rather than return -1
            return -1;
        }
        
        // update in Cache tag's last update time attribute setting value to time now
        valuesLastUpdated = new Date().toString();
        Cache.store(new Attribute(propTagName + PROPERTY_LAST_UPDATE_TIME, valuesLastUpdated));
        
        Log.debug(LOG_CAT, 4, "tag '" + tagName +
                "' after setting memberValues from Cache tag data items memberValues = " // +
                //Misc.array2string(memberValues)
        );

        return memberValuesSet;    
    } // end setMemberValues()



    // Documented in IPlcTag
    @Override
    public String getValuesLastUpdateString() {
        return valuesLastUpdated;
    } // end getValuesLastUpdateString()
    
    // Documented in IPlcTag
    @Override
    public String tagValuesToString() {
        StringBuilder result = new StringBuilder();

        result.append("{");
        for (int i = 0; i < this.memberValues.length; i++) {
            result.append("[" + i + "] = '" + this.memberValues[i] + "'");
            if (i != (this.memberValues.length - 1)) {
                result.append(", ");
            }
        }
        result.append("}");

        return result.toString();
    } // end tagValuesToString()
    

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((tagName == null) ? 0 : tagName.hashCode());
        result = prime * result + ((pcFormat == null) ? 0 : pcFormat.hashCode());
        result = prime * result + direction;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        PlcTag other = (PlcTag) obj;
        if (tagName == null) {
            if (other.tagName != null) return false;
        } else if (!tagName.equals(other.tagName)) return false;
        if (pcFormat == null) {
            if (other.pcFormat != null) return false;
        } else if (!pcFormat.equals(other.pcFormat)) return false;
        if (direction != other.direction) return false;
        return true;
    } 
   
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        String leftMargin = "  ";
        String separator = ": ";
        String newLine = System.getProperty("line.separator");

        result.append(this.getClass().getName());
        result.append(" Object {" + newLine);

        result.append(leftMargin + "tagName" + separator +
                this.tagName + newLine);
        
        result.append(leftMargin + "tagDirection" + separator +
                ((this.direction == DIRECTION_READ) ? "READ" : "WRITE") + newLine);
        
        result.append(leftMargin + "plcioTimeout" + separator +
                (this.getPlcioTimeoutMs()/1000.0) + "s (" + this.getPlcioTimeoutMs() + "ms)" + newLine);
        
        result.append(leftMargin + "plcioPcFormatStr" + separator +
                this.pcFormat + newLine);

        result.append(leftMargin + "plcioPcFormatTypeDescriptionAL" + separator + "{");
        for (int i = 0; i < this.pcFormatTypeAL.size(); i++) {
            result.append("[" + i + "] " + this.pcFormatTypeAL.get(i).toString());
            if (i != (this.pcFormatTypeAL.size() - 1)) {
                result.append(" ");
                }
            }
        result.append("}" + newLine);
        
        result.append(leftMargin + "totalByteLength" + separator +
                this.totalByteLength + newLine);
        
        result.append(leftMargin + "totalTagMembers" + separator +
                this.totalMembers + newLine);
        for (int memberIndex = 0; memberIndex < tagMemberItemNamesMap.size(); memberIndex++) {
            result.append(leftMargin + "tag member[" + memberIndex +
                    "], value = '" + memberValues[memberIndex] + "', contains item" +
                    ((tagMemberItemNamesMap.get(memberIndex).size() > 1) ? "s":"") + separator + newLine);
            for (String itemName : tagMemberItemNamesMap.get(memberIndex)) {
                result.append(leftMargin + leftMargin  + tagItemMap.get(itemName).toString() + newLine);
            }
        }
        
        result.append(leftMargin + "tagValues" + separator +
                this.tagValuesToString() + newLine);
        
        result.append(leftMargin + "tagValuesLastUpdate" + separator +
                valuesLastUpdated);
        result.append(newLine);

        result.append("}");
        
        return result.toString();
    } // end toString()
    
    /*
     *  Private methods
     */
    
    private boolean isValidPropType(String propItemName, String propType) throws ABPlcioExceptionBadPlcTagProperties {
        boolean valid = false;
        for (PropTypes type : PropTypes.values()) {
            if (type.getTypeString().equals(propType)) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            PropTypes[] types = PropTypes.values();
            String[] validPropTypes = new String[types.length];
            for (int i = 0; i < types.length; i++) {
                validPropTypes[i] = types[i].getTypeString(); 
            }
            throw new ABPlcioExceptionBadPlcTagProperties("The propertyDB type '" + propType + "' of property '" +
                    propItemName + "' is not a valid type for a PLC tag item, valid types are: " // +
                    //Misc.array2string(validPropTypes)
                    );
        }
        
        return valid;
    } // end isValidPropType()
    
    /*
     * Private inner-class used to represent individual data items belonging
     * to this tag.
     * 
     * @author Alastair Borrowman (OSL)
     *
     */
    private class TagItem {
        private final String itemName;
        private final String propItemName;
        private final String propItemTypeString;
        private IPlcTag.PropTypes propItemType;
        private final int memberNum;
        private final char memberPlcioType;
        private final int bytePos;
        private final int bitPos;
        private final int bitMask;
        private final boolean isBoolean;

        // Constructor
        TagItem(String name, String propName, String propTypeString, int memberNum, char plcioType, int bytePos) {
            this.itemName = name;
            this.propItemName = propName;
            this.propItemTypeString = propTypeString;
            // which IPlcTag.PropTypes enumeration does this propItemTypeString represent?
            for (IPlcTag.PropTypes type : IPlcTag.PropTypes.values()) {
                if (type.getTypeString().equals(propItemTypeString)) {
                    propItemType = type;
                    break;
                }
            }
            this.memberNum = memberNum;
            this.memberPlcioType = plcioType;
            this.bytePos = bytePos;
            // does this item store a boolean value?
            if (propItemTypeString.equals(PropTypes.BOOLEAN.getTypeString())) {
                bitPos = Cache.lookup(propItemName + PROPERTY_BIT_POS).getInteger();
                bitMask = (int) Math.pow(2, bitPos);
                isBoolean = true;
            }
            else {
                bitPos = bitMask = -1;
                isBoolean = false;
            }
        } // end Constructor

        String getItemName() {
            return itemName;
        } // end getItemName()

        String getPropItemName() {
            return propItemName;
        } // end getPropItemName()

        String getPropItemTypeString() {
            return propItemTypeString;
        } // end getPropItemTypeString()

        IPlcTag.PropTypes getPropItemType() {
            return propItemType;
        } // end getPropItemType()
        
        int getMemberNum() {
            return memberNum;
        } // end getMemberNum()
        
        char getMemberPlcioType() {
            return memberPlcioType;
        } // end getMemberPlcioType()
        
        int getBytePos(){
            return bytePos;
        } // end getBytePos()
        
        int getBitPos() {
            return bitPos;
        } // end getBitPos()
        
        int getBitMask() {
            return bitMask;
        } // end getBitMask()
        
        String getItemValueFromCache() {
            Attribute itemAtt = Cache.lookup(propItemName);
            String value = null;
            
            if ((itemAtt != null) && (!itemAtt.isEmpty())) {
                if (this.isBoolean) {
                    // the actual string representation of a boolean's value in the
                    // Cache may not be "true" or "false", it may use "open" or "close",
                    // etc. here we want to fix boolean strings returned to either
                    // "true" or "false"

                    /*
                    if (itemAtt.getBoolean()) {
                        value = BOOLEAN_STRING_TRUE;
                    }
                    else {
                        value = BOOLEAN_STRING_FALSE;
                    }
                    */
                }
                else {
                    value = itemAtt.getString();
                }
            }
            // else {
            // no value has been Cached and no property exists - null will be returned
            // }
            
            return value;
        } // end getItemValueFromCache()
        
        boolean isBoolean() {
            return isBoolean;
        } // end isBoolean()
        
        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            
            result.append("tag item name = '" + itemName +
                    "' value in Cache = '" + getItemValueFromCache() +
                    "' propertyDB attribute = '" + propItemName +
                    "', property type = " + propItemTypeString +
                    ", stored in PLCIO type '" + memberPlcioType + "' ");
            
            try {
                result.append("(" + PlcioPcFormat.getTypeString(memberPlcioType) +"), ");
            } catch(ABPlcioExceptionBadPlcTagProperties ex) {
                result.append("(not valid PLCIO type), ");
            }
            
            result.append("memberNum = " + memberNum + ", ");
            
            if (this.isBoolean) {
                result.append("bytePos:bitPos = " + bytePos + ":" + bitPos);
                result.append(" (bitMask = " + String.format("%#06x", bitMask) + ") raw = " + bitMask);
            }
            else {
                result.append("bytePos = " + bytePos);
            }
            
            return result.toString();
        } // end toString()
    } // end class TagItem
    
} // end class PlcTag
