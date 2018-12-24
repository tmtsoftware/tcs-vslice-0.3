/***

 JNI implementation to provide ability to call PLCIO C functions
 from Java.

 Alastair Borrowman, Observatory Sciences Ltd.

 Implemented following guidance at
 http://www.ibm.com/developerworks/java/tutorials/j-jni

***/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <jni.h>
#include "plc.h"
#include "atst_giss_abplc_ABPlcioMaster.h"

#define PLCIO_LOGFILE "/var/tmp/plcio.log"

#define HOSTNAME_MAX_LENGTH 56
#define CONN_NAME_MAX_LENGTH 56
#define MAX_OPEN_CONNECTIONS 30
#define PLCIO_PC_FORMAT_MAX_LENGTH 128
#define TAG_NAME_MAX_LENGTH 56
#define FUNC_NAME_MAX_LEN 56
#define STR_MAX_LEN 256
#define STR_BYTES_DEBUG_LEN 512

#define CLASS_CONNECTION_EXCEPTION "atst/base/hardware/connections/ConnectionException"
#define CLASS_ABPLCIO_EXCEPTION_JNI "atst/giss/abplc/ABPlcioExceptionJNI"
#define CLASS_ABPLCIO_EXCEPTION_PLCIO "atst/giss/abplc/ABPlcioExceptionPLCIO"

/* The log category used in all calls to CSF atst/cs/services/Log methods */
#define LOG_CAT "ABPLCIO_MASTER_JNI"

/* Debug */
int DEBUG_ON = 0;
/* int DEBUG_ON = 1; */
#define DEBUG_PRINT \
  if (DEBUG_ON) printf
int DEBUG_READ_WRITE_ON = 0;
/* int DEBUG_READ_WRITE_ON = 1; */

/*
 * Global static variables
 */

/*
 * Initialisation flag
 */
static int isInitialised = 0;

/*
 * Java method IDs used to access Java methods from
 * JNI code. IDs are created and cached in plcioInit()
 * and deleted in plcioUninit().
 */
static jmethodID jmID_cs_getDebugLevel;
static jmethodID jmID_cs_logDebug;
static jmethodID jmID_cs_logWarn;
static jmethodID jmID_plc_readCallback;
static jmethodID jmID_plc_validaddrCallback;

/*
 * Array of pointers to PLCIO PLC objects.
 * A PLC object pointer is returned from plc_open() and used as
 * parameter to all subsequent PLCIO functions using the opened
 * connection. When a connection is opened a connNumber
 * is returned to the Java method requesting the connection
 * and is then used as connNumber parameter in all
 * subsequent calls to JNI C functions using the connection.
 * The connNumber is the index into the array referencing
 * the connection's PLC object.
 * To maintain access to open PLC objects these are
 * stored in a static array.
 */
static PLC *plcConnArray[MAX_OPEN_CONNECTIONS];
/*
 * Array used to store names of open connections.
 * Index of a connection's name is the connNumber of
 * the connection.
 */
static char plcConnNames[MAX_OPEN_CONNECTIONS][CONN_NAME_MAX_LENGTH];
/*
 * Total number of currently open connections.
 * Total shall never exceed MAX_OPEN_CONNECTIONS.
 */
static int plcConnTotal;

/*
 * Private functions
 */

/*
 * Prototypes
 */
int cacheJavaMethodID(JNIEnv *, jclass, int, char *, char *, jmethodID *);
void clearConnArray();
void clearJavaMethodIDs(JNIEnv *);
int closeAllOpenConnections(JNIEnv *, jclass);
int getDebugLevel(JNIEnv *, jclass);
int isValidConnNumber(JNIEnv *, int, char *, int);
void logDebug(JNIEnv *, jclass, int, char *);
void logWarn(JNIEnv *, jclass, char *);
void printByteBuffer(JNIEnv *, signed char *, int, char *);
void toStringByteBuffer(JNIEnv *, jclass, signed char *, int, char *, int);
int throwJavaException(JNIEnv *, char *, char *);
int initPlcioNative(JNIEnv *, jclass);
int uninitPlcioNative(JNIEnv *, jclass);

/* cacheJavaMethodID()
 * Obtain the methodID of the given method and store
 * in global static variable for later use.
 * Caching methodIDs is one of the tips detailed in JNI best practices info
 * from http://www.ibm.com/developerworks/java/library/j-jni/index.html#notc */
int cacheJavaMethodID(JNIEnv *env, jclass jcls, int isStaticMethod,
		      char *methodName, char *methodSignature, jmethodID *globalMethodID)
{
  jmethodID jmID = NULL;

  /* if the static globalMethodID is not NULL then clean-up before assigning to it */
  if (*globalMethodID != NULL)
    {
      (*env)->DeleteGlobalRef(env, (jobject) *globalMethodID);
      *globalMethodID = NULL;
    }

  /* get the methodID */
  if (isStaticMethod)
    {
      jmID = (*env)->GetStaticMethodID(env, jcls, methodName, methodSignature);
    }
  else
    {
      jmID = (*env)->GetMethodID(env, jcls, methodName, methodSignature);
    }
  if (jmID == NULL)
    {
      char errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - Could not obtain methodID for Java method '%s' with signature '%s'",
	       __FILE__, __FUNCTION__, __LINE__, methodName, methodSignature);

      /* throw JNI exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
      return -1;
    }

  /* create global variable to store method ID - deleted in plcioUninit() */
  *globalMethodID = (jmethodID) (*env)->NewGlobalRef(env, (jobject) jmID);
  if (*globalMethodID == NULL)
    {
      char errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - Error occurred calling JNI NewGlobalRef, could not create global ref for methodID of Java method '%s'",
	       __FILE__, __FUNCTION__, __LINE__, methodName);

      /* throw JNI exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
      return -1;
    }

  /* methodID successfully assigned to static global variable */
  return 0;
} /* end cacheJavaMethodID() */

/* clearConnArray()
 * Set all PLC object pointers in plcConnArray to NULL, any
 * pointers referencing open connections should be closed
 * prior to calling this function. */
void clearConnArray()
{
  int i = 0;

  for (i = 0; i < MAX_OPEN_CONNECTIONS; i++)
    {
      plcConnArray[i] = NULL;
      memset(plcConnNames[i], '\0', sizeof(plcConnNames[i]));
    }
  plcConnTotal = 0;
} /* end clearConnArray() */

/* clearJavaMethodIDs()
 * Set all Java Method IDs to NULL. Method IDs are initialised
   in plcioInit() */
void clearJavaMethodIDs(JNIEnv *env)
{
  if (jmID_cs_getDebugLevel != NULL)
    {
      (*env)->DeleteGlobalRef(env, (jobject) jmID_cs_getDebugLevel);
      jmID_cs_getDebugLevel = NULL;
    }
  if (jmID_cs_logDebug != NULL)
    {
      (*env)->DeleteGlobalRef(env, (jobject) jmID_cs_logDebug);
      jmID_cs_logDebug = NULL;
    }
  if (jmID_cs_logWarn != NULL)
    {
      (*env)->DeleteGlobalRef(env, (jobject) jmID_cs_logWarn);
      jmID_cs_logWarn = NULL;
    }
  if (jmID_plc_readCallback != NULL)
    {
      (*env)->DeleteGlobalRef(env, (jobject) jmID_plc_readCallback);
      jmID_plc_readCallback = NULL;
    }
  if (jmID_plc_validaddrCallback != NULL)
    {
      (*env)->DeleteGlobalRef(env, (jobject) jmID_plc_validaddrCallback);
      jmID_plc_validaddrCallback = NULL;
    }
} /* end clearJavaMethodIDs() */

/* closeAllOpenConnections()
 * For all non-NULL items in plcConnArray call plc_close().
 * Returns 0 on success or -1 if one or more connections are
 * not closed successfully */
int closeAllOpenConnections(JNIEnv *env, jclass jcls)
{
  int i = 0;
  int returnVal = 0;
  char errStrTemp[MAX_OPEN_CONNECTIONS][STR_MAX_LEN];

  for (i = 0; i < MAX_OPEN_CONNECTIONS; i++)
    {
      if (plcConnArray[i] != NULL)
	{
	  if (plc_close(plcConnArray[i]) < 0)
	    {
	      /* &&&ajb for now to ensure correct error reporting also use plc_print_error() */
	      plc_print_error(plcConnArray[i], "plc_close");
	      snprintf(errStrTemp[i], (STR_MAX_LEN - 1),
		       "PLCIO plc_close error closing connNumber %d, PLCIO Err %d: %s\n",
		       i, plcConnArray[i]->j_error, plcConnArray[i]->ac_errmsg);
	      returnVal = -1;
	    }
	  else
	    {
	      char debugStr[STR_MAX_LEN];
	      snprintf(debugStr, (STR_MAX_LEN - 1),
		       "PLCIO JNI C %s():%d - PLCIO plc_close() returned success for connName '%s', connNumber = %d, (plcConnTotal now = %d)",
		       __FUNCTION__, __LINE__, plcConnNames[i], i, (plcConnTotal - 1));
	      logDebug(env, jcls, 2, debugStr);
	    }
	  /* irrespective of return value from plc_close
	     set this connNumber details to NULL */
	  plcConnArray[i] = NULL;
	  memset(plcConnNames[i], '\0', sizeof(plcConnNames[i]));
	  plcConnTotal -= 1;
	}
    } /* end for */

  if (plcConnTotal > 0)
    {
      char warnStr[STR_MAX_LEN];
      snprintf(warnStr, (STR_MAX_LEN - 1),
	       "PLCIO JNI C %s():%d - closed all open PLC connections but plcConnTotal = %d",
	       __FUNCTION__, __LINE__, plcConnTotal);
      logWarn(env, jcls, warnStr);
    }

  if (returnVal < 0)
    {
      /* an error has occurred closing one or more PLC connections */
      char errStr[STR_MAX_LEN], allErrStr[STR_MAX_LEN];

      /* create errStr from all reported close errors upto maximum
	 length of errStr */
      for (i = 0; i < MAX_OPEN_CONNECTIONS; i++)
	{
	  if ((strlen(errStrTemp[i]) > 0) && (strlen(allErrStr) < STR_MAX_LEN))
	    {
	      strncat(allErrStr, errStrTemp[i], ((STR_MAX_LEN -1) - strlen(allErrStr)));
	    }
	}
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - one or more PLCIO plc_close calls returned -1:\n%s",
	       __FILE__, __FUNCTION__, __LINE__, allErrStr);

      /* throw PLCIO exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_PLCIO, errStr);
    }

  return returnVal;
} /* end closeAllOpenConnections() */

/* getDebugLevel()
 * Return the current debug level of PLCIO JNI log category */
int getDebugLevel(JNIEnv *env, jclass jcls)
{
  jstring jLogCat = (*env)->NewStringUTF(env, LOG_CAT);
  int debugLevel;

  debugLevel = (*env)->CallStaticIntMethod(env, jcls, jmID_cs_getDebugLevel, jLogCat);

  (*env)->DeleteLocalRef(env, jLogCat);


  if ((*env)->ExceptionOccurred(env))
    {
      char errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - error occurred calling Java method 'Log.getDebugLevel(%s)'",
	       __FILE__, __FUNCTION__, __LINE__, LOG_CAT);
      /* throw JNI exception */
      (*env)->ExceptionDescribe(env);
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
    }

  return debugLevel;
} /* end getDebugLevel() */


/* isValidConnNumber()
 * Test whether given connNumber is within range of allowed connNumbers
 * and that it refers to an open PLC connection */
int isValidConnNumber(JNIEnv *env,
		      int connNumber, char *funcName, int funcLine)
{
  /* check connNumber is within range of allowed connNumbers */
  if ((connNumber < 0) || (connNumber >= MAX_OPEN_CONNECTIONS))
    {
      char  errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - connNumber '%d' is not valid, must be in range 0 to %d",
	       __FILE__, funcName, funcLine, connNumber, (MAX_OPEN_CONNECTIONS - 1));

      /* throw JNI exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
      return -1;
    }

  /*  check connNumber references an open connection */
  if (plcConnArray[connNumber] == NULL)
    {
      char errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - connNumber '%d' not currently open",
	       __FILE__, funcName, funcLine, connNumber);

      /* throw JNI exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
      return -1;
    }

  return 0;
} /* end isValidConnNumber() */

/* logDebug()
 * Wrapper function for CSF Log.debug() */
void logDebug(JNIEnv *env, jclass jcls, int level, char *message)
{
  jstring jLogCat = (*env)->NewStringUTF(env, LOG_CAT);
  jstring jMessage = (*env)->NewStringUTF(env, message);

  (*env)->CallStaticVoidMethod(env, jcls, jmID_cs_logDebug, jLogCat, level, jMessage);

  (*env)->DeleteLocalRef(env, jLogCat);
  (*env)->DeleteLocalRef(env, jMessage);

  if ((*env)->ExceptionOccurred(env))
    {
      char errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - error occurred calling Java method 'Log.debug(%s, %d, %s)'",
	       __FILE__, __FUNCTION__, __LINE__, LOG_CAT, level, message);
      /* throw JNI exception */
      (*env)->ExceptionDescribe(env);
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
    }
} /* end logDebug() */

/* logWarn()
 * Wrapper function for CSF Log.warn() */
void logWarn(JNIEnv *env, jclass jcls, char *message)
{
  jstring jLogCat = (*env)->NewStringUTF(env, LOG_CAT);
  jstring jMessage = (*env)->NewStringUTF(env, message);

  (*env)->CallStaticVoidMethod(env, jcls, jmID_cs_logWarn, jLogCat, jMessage);

  (*env)->DeleteLocalRef(env, jLogCat);
  (*env)->DeleteLocalRef(env, jMessage);

  if ((*env)->ExceptionOccurred(env))
    {
      char errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - error occurred calling Java method 'Log.warn(%s, %s)'",
	       __FILE__, __FUNCTION__, __LINE__, LOG_CAT, message);
      /* throw JNI exception */
      (*env)->ExceptionDescribe(env);
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
    }
} /* end logWarn() */

/* printByteBuffer()
 * Display on screen the contents of a byteBuffer using types as described
 * in the PLCIO pc_format string */
void printByteBuffer(JNIEnv *env,
		     signed char *pjbyteArray, int pjbyteArrayLength, char *plcioPcFormat)
{
  int index;

  /* display raw contents of buffer in 2 or 4 byte chunks depending
     upon length of data */
  if (pjbyteArrayLength & 1)
    {
      for(index = 0; index < pjbyteArrayLength; index++)
	{
	  printf(" %02x", ((unsigned char *) pjbyteArray)[index]);
	}
    }
  else
    {
      for(index = 0; index < (pjbyteArrayLength / 2); index++)
	{
	  printf(" %04x", ((unsigned short *) pjbyteArray)[index]);
	}
    }
  printf("\n");

  return;
} /* end printByteBuffer() */

/* toStringByteBuffer()
 * Return contents of a byteBuffer as printable string */
void toStringByteBuffer(JNIEnv *env, jclass jcls,
			signed char *pjbyteArray, int pjbyteArrayLength,
			char *rtnString, int rtnStringLen)
{
  char byteString[10];
  int index, rtnStringLenInUse = 0, catLen = 0, rtnStringOverflow = 0;

  /* ensure the return strings are empty */
  memset(rtnString, '\0', rtnStringLen);

  /* cat contents of buffer in 2 or 4 byte chunks depending
     upon length of data */
  if (pjbyteArrayLength & 1)
    {
      catLen = 4; /* 4 = 2 byte value chars, 1 space and 1 end of string char */
      for(index = 0; index < pjbyteArrayLength; index++)
	{
	  rtnStringLenInUse = strlen(rtnString) + catLen;
	  if (rtnStringLenInUse >= rtnStringLen)
	    {
	      rtnStringOverflow = 1;
	      break;
	    }
	  else
	    {
	      /* print value as cast 2 bytes - this will swap the endianess and display value */
	      memset(byteString, '\0', sizeof(byteString));
	      snprintf(byteString, (sizeof(byteString) - 1), " %02x", ((unsigned char *) pjbyteArray)[index]);
	      strncat(rtnString, byteString, catLen);
	    }
	}
    }
  else
    {
      catLen = 6; /* 6 = 4 byte value chars, 1 space and 1 end of line */
      for(index = 0; index < (pjbyteArrayLength / 2); index++)
	{
	  rtnStringLenInUse = strlen(rtnString) + catLen;
	  if (rtnStringLenInUse >= rtnStringLen)
	    {
	      rtnStringOverflow = 1;
	      break;
	    }
	  else
	    {
	      /* print value as cast 4 bytes - this will swap the endianess of value */
	      memset(byteString, '\0', sizeof(byteString));
	      snprintf(byteString, (sizeof(byteString) - 1), " %04x", ((unsigned short *) pjbyteArray)[index]);
	      strncat(rtnString, byteString, catLen);
	    }
	}
    }

  if (rtnStringOverflow)
    {
      char warnStr[STR_MAX_LEN];
      snprintf(warnStr, (STR_MAX_LEN - 1),
	       "PLCIO JNI C %s():%d - length of debug string (%zu) not sufficent for byte data, final byte copied to debug string = %d, total bytes = %d",
	       __FUNCTION__, __LINE__, strlen(rtnString), index, pjbyteArrayLength);
      logWarn(env, jcls, warnStr);
    }

  return;
} /* end toStringByteBuffer() */

int throwJavaException(JNIEnv *env, char *exceptionClassName, char *message)
{
  int returnVal = 0;

  returnVal = (*env)->ThrowNew(env, (*env)->FindClass(env, exceptionClassName), message);

  if (returnVal < 0)
    {
      if ((*env)->ExceptionOccurred(env))
	{
	  printf("C - ERROR %s:JNI %s():%d - Java exception occurred when trying to find class '%s'\n",
		 __FILE__, __FUNCTION__, __LINE__, exceptionClassName);
	  (*env)->ExceptionDescribe(env);
	  (*env)->ExceptionClear(env);
	}
      printf("C - ERROR %s:JNI %s():%d - error returned from ThrowNew() when throwing exception of class '%s' with message '%s'\n",
	     __FILE__, __FUNCTION__, __LINE__, exceptionClassName, message);
    }

  return returnVal;
} /* end throwJavaException() */


/* initPlcioNative()
 * This function is called from Java_PlcioNative_plc_1open() if isInitialised
 * equals false.
 * Actions:
 *  - clear global static variables
 *  - cache method IDs and class of object using these functions
 *    (see http://www.ibm.com/developerworks/java/library/j-jni/index.html#notc
 *    for details as to why this is important)
 *  - initialise PLCIO logging by calling plc_log_init()
 */
int initPlcioNative(JNIEnv *env, jclass jcls)
{
  /* if we are already initialised return immediately */
  if (isInitialised)
    {
      /* NB can't call logDebug() here to call CSF Log.debug() as access to method has not yet been setup */
      DEBUG_PRINT("C - %s:%s():%d - PLCIO JNI already initialised returning success with no action\n",
		  __FILE__, __FUNCTION__, __LINE__);
      return 0;
    }
  /* NB can't call logDebug() here to call CSF Log.debug() as access to method has not yet been setup */
  DEBUG_PRINT("C - %s:JNI %s():%d - starting initialisation of PLCIO JNI\n",
	      __FILE__, __FUNCTION__, __LINE__);

  /* begin by clearing/initialising global static variables */
  clearConnArray();
  plcConnTotal = 0;
  clearJavaMethodIDs(env);


  /* get methodID of Java Common Services static method Log.getDebugLevel(logCategory) */
  jclass jclsLog = (*env)->FindClass(env, "atst/giss/abplc/Log");
  if ((jclsLog == NULL) || (*env)->ExceptionOccurred(env))
    {
      char errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - exception or NULL returned from JNI FindClass(%s)",
	       __FILE__, __FUNCTION__, __LINE__, "atst/giss/abplc/Log");
      /* throw JNI exception */
      (*env)->ExceptionDescribe(env);
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
      return -1;
    }
  if (cacheJavaMethodID(env, jclsLog, 1, "getDebugLevel",
			"(Ljava/lang/String;)I",
			&jmID_cs_getDebugLevel) < 0)
    {
      /* method cacheJavaMethodID() throws appropriate execption on error */
      return -1;
    }
  /* and from same class methodID of static method Log.debug(logCategory, logLevel, logMessage) */
  if (cacheJavaMethodID(env, jclsLog, 1, "debug",
			"(Ljava/lang/String;ILjava/lang/String;)V",
			&jmID_cs_logDebug) < 0)
    {
      /* method cacheJavaMethodID() throws appropriate execption on error */
      return -1;
    }
  /* and from same class methodID of static method Log.warn(logCategory, logMessage) */
  if (cacheJavaMethodID(env, jclsLog, 1, "warn",
			"(Ljava/lang/String;Ljava/lang/String;)V",
			&jmID_cs_logWarn) < 0)
    {
      /* method cacheJavaMethodID() throws appropriate execption on error */
      return -1;
    }

  /* get methodID of Java static method plc_readCallback() */
  if (cacheJavaMethodID(env, jcls, 1, "plc_readCallback",
			"(ILjava/lang/String;Ljava/lang/String;I[BI)I",
			&jmID_plc_readCallback) < 0)
    {
      /* method cacheJavaMethodID() throws appropriate execption on error */
      return -1;
    }

  /* get methodID of Java static method plc_validaddrCallback() */
  if (cacheJavaMethodID(env, jcls, 1, "plc_validaddrCallback",
			"(ILjava/lang/String;Ljava/lang/String;III)V",
			&jmID_plc_validaddrCallback) < 0)
    {
      /* method cacheJavaMethodID() throws appropriate execption on error */
      return -1;
    }

  /* initialise PLCIO logging to file */
  plc_log_init(PLCIO_LOGFILE);

  /* signal initialisation has been completed */
  isInitialised = 1;

  char debugStr[STR_MAX_LEN];
  snprintf(debugStr, (STR_MAX_LEN - 1),
	   "PLCIO JNI C %s():%d - PLCIO JNI initialised successfully", __FUNCTION__, __LINE__);
  logDebug(env, jcls, 2, debugStr);

  return 0;
} /* end initPlcioNative() */

/* uninitPlcioNative()
 * Uninitialization required for native methods contained in this file.
 * This function is called from Java_PlcioNative_plc_1close() when the final
 * connection is closed (i.e. connTotal == 0).
 * Actions:
 *  - check for any open connections and close them
 *  - delete global references to Java method IDs
 *  - clear all global static variables
 */
int uninitPlcioNative(JNIEnv *env, jclass jcls)
{
  int returnVal = 0;

  /* if we are already uninitialised return immediately */
  if (!isInitialised)
    {
      /* NB can't call logWarn() here to call CSF Log.warn() as access to method is not available when uninitialised */
      printf("C - %s:%s():%d - already uninitialised returning success with no action\n",
	      __FILE__, __FUNCTION__, __LINE__);

      /* nothing to do so return */
      return 0;
    }

  /* close any open connections */
  returnVal = closeAllOpenConnections(env, jcls);

  if (returnVal >= 0) {
    char debugStr[STR_MAX_LEN];
    snprintf(debugStr, (STR_MAX_LEN - 1),
	     "PLCIO JNI C %s():%d - PLCIO JNI uninitialised successfully",
	     __FUNCTION__, __LINE__);
    logDebug(env, jcls, 2, debugStr);
  }
  else {
    char warnStr[STR_MAX_LEN];
    snprintf(warnStr, (STR_MAX_LEN - 1),
	     "PLCIO JNI C %s():%d - PLCIO JNI in uninitPlcioNative() not all open PLCIO connections closed successfully",
	     __FUNCTION__, __LINE__);
    logWarn(env, jcls, warnStr);
  }

  /* end by clearing all global static variables */
  clearConnArray();
  plcConnTotal = 0;
  clearJavaMethodIDs(env);

  /* signal we are no longer initialised */
  isInitialised = 0;

  return returnVal;
} /* end uninitPlcioNative()  */

/*
 * JNI function implementations
 * PLCIO function wrappers
 */

/*
 * plc_open()
 */
JNIEXPORT jint JNICALL Java_atst_giss_abplc_ABPlcioMaster_plc_1open
(JNIEnv *env, jclass jcls, jstring jplcModuleHostname, jstring jconnName)
{
  char FUNCTION_NAME[] = "plc_open";
  const char *pjplcModuleHostname = (*env)->GetStringUTFChars(env, jplcModuleHostname, 0);
  char plcModuleHostname[HOSTNAME_MAX_LENGTH];
  const char *pjconnName = (*env)->GetStringUTFChars(env, jconnName, 0);
  char connName[CONN_NAME_MAX_LENGTH];
  int myConnNumber = -1, i;

  /* */
  /* code below can be used to get name of object calling this function */
  /* */
  /* jmethodID jmID_toString = (*env)->GetMethodID(env, jcls, "toString", "()Ljava/lang/String;"); */
  /* jstring jtoString = (jstring) (*env)->CallObjectMethod(env, jcls, jmID_toString); */
  /* const char *pjtoString = (*env)->GetStringUTFChars(env, jtoString, NULL); */
  /* DEBUG_PRINT("\nC - %s:JNI %s():%d - plc_open() is being called by '%s'\n", */
  /* 	      __FILE__, FUNCTION_NAME, __LINE__, pjtoString); */
  /* (*env)->ReleaseStringUTFChars(env, jtoString, pjtoString); */

  if (!isInitialised)
    {
      /* NB can't call logWarn() here to call CSF Log.warn() as access to method is not available when uninitialised */
      DEBUG_PRINT("C - %s:JNI %s():%d - plcioNative.c is not intialised; calling initPlcioNative()\n",
		  __FILE__, FUNCTION_NAME, __LINE__);
      if (initPlcioNative(env, jcls) < 0)
	{
	  printf("C - ERROR %s:JNI %s():%d - initPlcioNative() returned failure\n",
		 __FILE__, FUNCTION_NAME, __LINE__);
	  /* error information will have been returned to Java by initPlcioNative() throwing exception */
	  return -1;
      }
    }

  /* check moduleHostname string is not too long */
  if ((strlen(pjplcModuleHostname) + 1) > HOSTNAME_MAX_LENGTH)
    {
      char errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - plcModuleHostname of '%s' is too long (%d chars), maximum permitted is %d",
	       __FILE__, FUNCTION_NAME, __LINE__, pjplcModuleHostname, (int)(strlen(pjplcModuleHostname) + 1),
	       HOSTNAME_MAX_LENGTH);

      (*env)->ReleaseStringUTFChars(env, jplcModuleHostname, pjplcModuleHostname);
      (*env)->ReleaseStringUTFChars(env, jconnName, pjconnName);

      /* throw JNI exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
      return -1;
    }
  /* now we know it's safe to copy Java PLC hostname string to local copy and release */
  strncpy(plcModuleHostname, pjplcModuleHostname, HOSTNAME_MAX_LENGTH - 1);
  (*env)->ReleaseStringUTFChars(env, jplcModuleHostname, pjplcModuleHostname);

  /* check connName string is not too long */
  if ((strlen(pjconnName) + 1) > CONN_NAME_MAX_LENGTH)
    {
      char errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - connName of '%s' is too long (%d chars), maximum permitted is %d",
	       __FILE__, FUNCTION_NAME, __LINE__, pjconnName, (int)(strlen(pjconnName) + 1),
	       CONN_NAME_MAX_LENGTH);

      (*env)->ReleaseStringUTFChars(env, jconnName, pjconnName);

      /* throw JNI exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
      return -1;
    }
  /* now we know it's safe to copy Java connection name string to local copy and release */
  strcpy(connName, pjconnName);
  (*env)->ReleaseStringUTFChars(env, jconnName, pjconnName);

  /* check we've got a free connection */
  myConnNumber = -1;
  for (i = 0; i < MAX_OPEN_CONNECTIONS; i++)
    {
      if (plcConnArray[i] == NULL)
	{
	  myConnNumber = i;
	  break;
	}
    }
  if (myConnNumber < 0)
    {
      char errStr[STR_MAX_LEN];
      /* no more connections can be opened */
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - NO more free connections, connection total = %d, maximum connections = %d",
	       __FILE__, FUNCTION_NAME, __LINE__, plcConnTotal, MAX_OPEN_CONNECTIONS);

      /* throw JNI exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
      return -1;
    }

  char debugStr[STR_MAX_LEN];
  snprintf(debugStr, (STR_MAX_LEN - 1),
	   "PLCIO JNI C %s():%d - calling PLCIO plc_open(\"%s\") for connName '%s' using connNumber = %d",
	   FUNCTION_NAME, __LINE__, plcModuleHostname, connName, myConnNumber);
  logDebug(env, jcls, 4, debugStr);

  /* call PLCIO plc_open() */
  plcConnArray[myConnNumber] = plc_open(plcModuleHostname);


  if (plcConnArray[myConnNumber] == NULL)
    {

      char errStr[STR_MAX_LEN];
      /* if plc_open returns NULL then PLCIO provides error info in plc_open_ptr */
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - PLCIO plc_open() returned NULL, PLCIO Err %d: %s",
	       __FILE__, FUNCTION_NAME, __LINE__, plc_open_ptr->j_error, plc_open_ptr->ac_errmsg);

  snprintf(debugStr, (STR_MAX_LEN - 1),
	   "PLCIO JNI C %s():%d - completed call, return value = null %d: %s",
	   FUNCTION_NAME, __LINE__, plc_open_ptr->j_error, plc_open_ptr->ac_errmsg);
  logDebug(env, jcls, 4, debugStr);
      /* throw PLCIO exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_PLCIO, errStr);
      return -1;
    }


  strncpy(plcConnNames[myConnNumber], connName, strlen(connName) + 1);
  plcConnTotal += 1;

  snprintf(debugStr, (STR_MAX_LEN - 1),
	   "PLCIO JNI C %s():%d - PLCIO plc_open(\"%s\") returned success for connName '%s', connNumber = %d, (plcConnTotal = %d)",
	   FUNCTION_NAME, __LINE__, plcModuleHostname, plcConnNames[myConnNumber], myConnNumber, plcConnTotal);
  logDebug(env, jcls, 2, debugStr);

  return myConnNumber;
} /* end plc_open() */

/*
 * plc_close()
 */
JNIEXPORT jint JNICALL Java_atst_giss_abplc_ABPlcioMaster_plc_1close
(JNIEnv *env, jclass jcls, jint connNumber)
{
  char FUNCTION_NAME[] = "plc_close";
  char errStr[STR_MAX_LEN];
  int returnVal = 0;

  if (!isInitialised)
    {
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - JNI is NOT initialisied so no connections can be open so cannot close connection to connName '%s' using connNumber = %d",
	        __FILE__, FUNCTION_NAME, __LINE__, plcConnNames[connNumber], connNumber);

      /* throw PLCIO exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_PLCIO, errStr);
      return -1;
    }

  if (isValidConnNumber(env, connNumber, FUNCTION_NAME, __LINE__) < 0)
    {
      /* &&&ajb this will occur if the connNumber's connection has already
	 been sucessfully closed, e.g. if plcioUninit() has been called prior
	 to this then all open connections will have been closed and all globals
	 set to null. Should this simply return 0 here instead of -1??? */
      return -1;
    }

  /* call PLCIO plc_close() */
  if (plc_close(plcConnArray[connNumber]) < 0)
    {
      /* access PLCIO err number and description from PLC object */
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - PLCIO plc_close() returned -1, PLCIO Err %d: %s",
	       __FILE__, FUNCTION_NAME, __LINE__,
	       plcConnArray[connNumber]->j_error, plcConnArray[connNumber]->ac_errmsg);

      /* don't throw the PLCIO exception here as doing so will cause calls to
	 (*env)->ExceptionOccurred(env) (e.g. made in logDebug()) to signal that
	 an exception has occurred - instead throw exception at end of function
         once other processing is complete */
      returnVal = -1;
    }

  /* reduce connection total by 1 irrespective of return value from plc_close() - if
     failure occurred the connection will be unusable and should be marked as such */
  plcConnTotal -= 1;

  if (returnVal >= 0)
    {
      char debugStr[STR_MAX_LEN];
      snprintf(debugStr, (STR_MAX_LEN - 1),
	       "PLCIO JNI C %s():%d - PLCIO plc_close() returned success for connName '%s', connNumber = %d, (plcConnTotal now = %d)",
	       FUNCTION_NAME, __LINE__, plcConnNames[connNumber], connNumber, plcConnTotal);
      logDebug(env, jcls, 2, debugStr);
    }

  /* irrespective of return value from plc_close set this connNumber details to NULL */
  plcConnArray[connNumber] = NULL;
  memset(plcConnNames[connNumber], '\0', sizeof(plcConnNames[connNumber]));

  if (plcConnTotal <= 0)
    {
      char debugStr[STR_MAX_LEN];
      snprintf(debugStr, (STR_MAX_LEN - 1),
	       "PLCIO JNI C %s():%d - final PLC connection has been closed - calling uninitPlcioNative()",
	       FUNCTION_NAME, __LINE__);
      logDebug(env, jcls, 2, debugStr);

      if (uninitPlcioNative(env, jcls) < 0)
	{
	  /* error information will be returned to Java by uninitPlcioNative() throwing exception */
	  return -1;
	}
    }

  if (returnVal < 0) {
    /* throw the exception to signal that call to plc_close() (see above) failed */
    throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_PLCIO, errStr);
  }

  return returnVal;
} /* end plc_1close() */

/*
 * plc_read()
 */
JNIEXPORT jint JNICALL Java_atst_giss_abplc_ABPlcioMaster_plc_1read
(JNIEnv *env, jclass jcls,
 jint connNumber, jstring jtagName, jint jreadLength, jint jreadTimeout, jstring jplcioPcFormat,
 jint jreadTagKey)
{
  char FUNCTION_NAME[] = "plc_read";
  const char *pjtagName = (*env)->GetStringUTFChars(env, jtagName, 0);
  char plcTagName[TAG_NAME_MAX_LENGTH];
  const char *pjplcioPcFormat = (*env)->GetStringUTFChars(env, jplcioPcFormat, 0);
  char plcioPcFormat[PLCIO_PC_FORMAT_MAX_LENGTH];
  jstring jplcTagName, jconnName;
  /* NB. bytes read from PLC are stored in a pointer to a C char (pbytesRead),
     and transferred to Java using a jbyteArray (jbytesRead) not a jcharArray,
     as sizeof(jbyte) = sizeof(char) = 1, whereas sizeof(jchar) = 2 */
  signed char *pbytesRead;
  jbyteArray jbytesRead;
  int bytesReadLength;

  if (!isInitialised)
    {
      char errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - JNI is NOT initialisied so NO connections can be open so CANNOT read from connection connName '%s' using connNumber = %d the tag named '%s'",
	       __FILE__, FUNCTION_NAME, __LINE__, plcConnNames[connNumber], connNumber, pjtagName);

      /* throw PLCIO exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_PLCIO, errStr);
      return -1;
    }

  /* check connNumber is OK */
  if (isValidConnNumber(env, connNumber, FUNCTION_NAME, __LINE__) < 0)
    {
      /* function isValidConnNumber() throws appropriate exception */
      return -1;
    }

  /* check tagName string length is not too long */
  if ((strlen(pjtagName) <= 0 ) || ((strlen(pjtagName)+1) > TAG_NAME_MAX_LENGTH))
    {
      char errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - connName '%s' attempt to read tag with invalid length, tagName '%s' with length %d, is outside range 1 to %d",
	       __FILE__, FUNCTION_NAME, __LINE__, plcConnNames[connNumber], pjtagName,
	       (int)(strlen(pjtagName)), (TAG_NAME_MAX_LENGTH - 1));

      (*env)->ReleaseStringUTFChars(env, jtagName, pjtagName);

     /* throw JNI exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
      return -1;
    }
  /* now we know it's safe to copy Java tagName string to local copy and release */
  strncpy(plcTagName, pjtagName, TAG_NAME_MAX_LENGTH - 1);
  (*env)->ReleaseStringUTFChars(env, jtagName, pjtagName);

  /* check PLCIO pc_format string length is not too long */
  if ((strlen(pjplcioPcFormat) <= 0) || ((strlen(pjplcioPcFormat)+1) > PLCIO_PC_FORMAT_MAX_LENGTH))
    {
      char errStr[STR_MAX_LEN];
      /* copy half of invalid pc_format string for use in error message */     
      strncpy(plcioPcFormat, pjplcioPcFormat, (int) (PLCIO_PC_FORMAT_MAX_LENGTH / 2));
      (*env)->ReleaseStringUTFChars(env, jplcioPcFormat, pjplcioPcFormat);

      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - connName '%s' attempt to read tag '%s' with invalid pc_format string length, pc_format of '%s...' with length %d, is outside range 1 to %d",
	       __FILE__, FUNCTION_NAME, __LINE__, plcConnNames[connNumber], plcTagName, plcioPcFormat,
	       (int)(strlen(pjplcioPcFormat)), (PLCIO_PC_FORMAT_MAX_LENGTH - 1));

     /* throw JNI exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
      return -1;
    }
  /* now we know it's safe to copy Java pc_format string to local copy and release */
  strncpy(plcioPcFormat, pjplcioPcFormat, PLCIO_PC_FORMAT_MAX_LENGTH - 1);
  (*env)->ReleaseStringUTFChars(env, jplcioPcFormat, pjplcioPcFormat);

  /* calloc enough space to pbytesRead for length of data being read */
  pbytesRead = (signed char *) calloc(jreadLength, sizeof(char));
  if (pbytesRead == NULL)
    {
      char errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - calloc(%d, %d) returned NULL when calloc'ng buffer to read tag '%s' of data length = %d",
	       __FILE__, FUNCTION_NAME, __LINE__, jreadLength, (int) sizeof(char), plcTagName, jreadLength);

     /* throw JNI exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
      return -1;
    }

  /* call PLCIO plc_read() */
  bytesReadLength = plc_read(plcConnArray[connNumber], PLC_RREG, plcTagName,
			     pbytesRead, jreadLength, jreadTimeout, plcioPcFormat);
  if (bytesReadLength < 0)
    {
      char errStr[STR_MAX_LEN];
      /* access PLCIO err number and description from PLC object */
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - PLCIO plc_read returned -1, PLCIO Err %d: %s",
	       __FILE__, FUNCTION_NAME, __LINE__,
	       plcConnArray[connNumber]->j_error, plcConnArray[connNumber]->ac_errmsg);

      /* throw PLCIO exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_PLCIO, errStr);
      return -1;
    }

  if (DEBUG_READ_WRITE_ON)
    {
      printf("C - data returned from plc_read(%s, PLC_RREG, \"%s\", pbytesRead, %d, %d, \"%s\") for readTag KeyID %d:\n",
	     plcConnNames[connNumber], plcTagName, jreadLength, jreadTimeout, plcioPcFormat, jreadTagKey);
      printByteBuffer(env, pbytesRead, bytesReadLength, plcioPcFormat);
    }

  if (getDebugLevel(env, jcls) >= 4)
    {
      char debugStr[STR_BYTES_DEBUG_LEN];
      snprintf(debugStr, (STR_BYTES_DEBUG_LEN - 1),
	       "PLCIO JNI C %s():%d - call to PLCIO plc_read(%s, PLC_RREG, \"%s\", pbytesRead, %d, %d, \"%s\") for readTag KeyID %d returned data:",
	       FUNCTION_NAME, __LINE__, plcConnNames[connNumber], plcTagName, jreadLength, jreadTimeout, plcioPcFormat, jreadTagKey);
      int debugStrRemainingChars = (STR_BYTES_DEBUG_LEN - strlen(debugStr)) - 1;
      char debugByteStr[debugStrRemainingChars];
      toStringByteBuffer(env, jcls, pbytesRead, bytesReadLength, debugByteStr, debugStrRemainingChars);
      strncat(debugStr, debugByteStr, (STR_BYTES_DEBUG_LEN - strlen(debugStr)));
      logDebug(env, jcls, 4, debugStr);
    }

  if (bytesReadLength != jreadLength)
    {
      char errStr[STR_MAX_LEN];
      /* read was successful but number of bytes read not equal number requested */
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - PLCIO plc_read() requested %d bytes but received %d bytes when reading connNumber %d tag '%s'. Bytes read = '%s'",
	       __FILE__, FUNCTION_NAME, __LINE__, jreadLength, bytesReadLength, connNumber, plcTagName, pbytesRead);

      free(pbytesRead);

     /* throw JNI exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
      return -1;
    }

  /* store bytes read into jbyteArray to be transferred to Java
     using plc_readCallback() method */
  jbytesRead = (*env)->NewByteArray(env, (sizeof(jbyte) * bytesReadLength));
  (*env)->SetByteArrayRegion(env, jbytesRead, 0,
			    (sizeof(jbyte) * bytesReadLength), (jbyte *) pbytesRead);
  /* finshed with pbytesRead - free memory allocated using calloc */
  free(pbytesRead);

  /* call Java method used to accept results from plc_read() */
  jconnName = (*env)->NewStringUTF(env, plcConnNames[connNumber]);
  jplcTagName = (*env)->NewStringUTF(env, plcTagName);
  if ((*env)->CallStaticIntMethod(env, jcls, jmID_plc_readCallback, connNumber, jconnName,
				  jplcTagName, bytesReadLength, jbytesRead, jreadTagKey) < 0)
    {
      char errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - Error returned from Java method plc_readCallback() when passing data from read of tag '%s'",
	       __FILE__, FUNCTION_NAME, __LINE__,  plcTagName);

     /* throw JNI exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
      return -1;
    }
  /* catch and throw any JNI exceptions occuring during call to Java method plc_readCallback() */
  else if ((*env)->ExceptionOccurred(env))
    {
      char errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - Java exception occurred calling Java method plc_readCallback()",
	       __FILE__, FUNCTION_NAME, __LINE__);

      /* throw JNI exception */
      (*env)->ExceptionDescribe(env);
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
      return -1;
    }

  (*env)->DeleteLocalRef(env, jbytesRead);
  (*env)->DeleteLocalRef(env, jconnName);
  (*env)->DeleteLocalRef(env, jplcTagName);

  return 0;
} /* end plc_1read() */

/*
 * plc_write()
 */
JNIEXPORT jint JNICALL Java_atst_giss_abplc_ABPlcioMaster_plc_1write
(JNIEnv *env, jclass jcls,
 jint connNumber, jstring jtagName, jbyteArray jtagBytes,
 jint jwriteLength, jint jwriteTimeout, jstring jplcioPcFormat)
{
  char FUNCTION_NAME[] = "plc_write";
  const char *pjtagName = (*env)->GetStringUTFChars(env, jtagName, 0);
  char plcTagName[TAG_NAME_MAX_LENGTH];
  const char *pjplcioPcFormat = (*env)->GetStringUTFChars(env, jplcioPcFormat, 0);
  char plcioPcFormat[PLCIO_PC_FORMAT_MAX_LENGTH];
  /* pjtagBytes is used to store the data passed from Java to be written to the
     PLC, it is transferred from Java in an array of type jbyte, for linux jbyte
     is declared as signed char (see jni_md.h), so to prevent signedness warnings
     from compiler we use a signed char here */
  signed char *pjtagBytes;

   if (!isInitialised)
    {
      char errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - JNI is NOT initialisied so NO connections can be open so CANNOT write to connection connName '%s' using connNumber = %d the tag named '%s'",
	       __FILE__, FUNCTION_NAME, __LINE__, plcConnNames[connNumber], connNumber, pjtagName);

      /* throw PLCIO exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_PLCIO, errStr);
      return -1;
    }

  /* check connNumber is OK */
  if (isValidConnNumber(env, connNumber, FUNCTION_NAME, __LINE__) < 0)
    {
      /* function isValidConnNumber() throws appropriate exception */
      return -1;
    }

  /* check tagName string length is not too long */
  if ((strlen(pjtagName) <= 0 ) || ((strlen(pjtagName)+1) > TAG_NAME_MAX_LENGTH))
    {
      char errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - connName '%s' attempt to write tag with invalid length, tagName '%s' with length %d, is outside range 1 to %d",
	       __FILE__, FUNCTION_NAME, __LINE__, plcConnNames[connNumber], pjtagName,
	       (int)(strlen(pjtagName)), (TAG_NAME_MAX_LENGTH - 1));

      (*env)->ReleaseStringUTFChars(env, jtagName, pjtagName);

     /* throw JNI exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
      return -1;
    }
  /* now we know it's safe to copy Java tagName string to local copy and release */
  strncpy(plcTagName, pjtagName, TAG_NAME_MAX_LENGTH - 1);
  (*env)->ReleaseStringUTFChars(env, jtagName, pjtagName);

  /* check PLCIO pc_format string length is not too long */
  if ((strlen(pjplcioPcFormat) <= 0) || ((strlen(pjplcioPcFormat)+1) > PLCIO_PC_FORMAT_MAX_LENGTH))
    {
      char errStr[STR_MAX_LEN];
      /* copy half of invalid pc_format string for use in error message */     
      strncpy(plcioPcFormat, pjplcioPcFormat, (int) (PLCIO_PC_FORMAT_MAX_LENGTH / 2));
      (*env)->ReleaseStringUTFChars(env, jplcioPcFormat, pjplcioPcFormat);

      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - connName '%s' attempt to write tag '%s' with invalid pc_format string length, pc_format of '%s...' with length %d, is outside range 1 to %d",
	       __FILE__, FUNCTION_NAME, __LINE__, plcConnNames[connNumber], plcTagName, plcioPcFormat,
	       (int)(strlen(pjplcioPcFormat)), (PLCIO_PC_FORMAT_MAX_LENGTH - 1));

     /* throw JNI exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
      return -1;
    }
  strncpy(plcioPcFormat, pjplcioPcFormat, PLCIO_PC_FORMAT_MAX_LENGTH - 1);
  (*env)->ReleaseStringUTFChars(env, jplcioPcFormat, pjplcioPcFormat);

  /* retrieve data to write from Java byte array */
  pjtagBytes = (*env)->GetByteArrayElements(env, jtagBytes, 0);

  if (DEBUG_READ_WRITE_ON)
    {
      printf("C - calling plc_write(%s, PLC_WREG, \"%s\", pjtagBytes, %d, %d, \"%s\") writing pjtagBytes:\n",
	     plcConnNames[connNumber], plcTagName, jwriteLength, jwriteTimeout, plcioPcFormat);
      printByteBuffer(env, pjtagBytes, jwriteLength, plcioPcFormat);
    }

  if (getDebugLevel(env, jcls) >= 4)
    {
      char debugStr[STR_MAX_LEN];
      snprintf(debugStr, (STR_MAX_LEN - 1),
	       "PLCIO JNI C %s():%d - calling PLCIO plc_write(%s, PLC_WREG, \"%s\", pjtagBytes, %d, %d, \"%s\") writing pjtagBytes data:",
	       FUNCTION_NAME, __LINE__, plcConnNames[connNumber], plcTagName, jwriteLength, jwriteTimeout, plcioPcFormat);
      int debugStrRemainingChars = (STR_MAX_LEN - strlen(debugStr)) - 1;
       char debugByteStr[debugStrRemainingChars];
      toStringByteBuffer(env, jcls, pjtagBytes, jwriteLength, debugByteStr, debugStrRemainingChars);
      strncat(debugStr, debugByteStr, (STR_MAX_LEN - 1));
      logDebug(env, jcls, 4, debugStr);
    }

  /* call PLCIO plc_write() */
  if (plc_write(plcConnArray[connNumber], PLC_WREG, plcTagName,
		pjtagBytes, jwriteLength, jwriteTimeout, plcioPcFormat) < 0)
    {
      char errStr[STR_MAX_LEN];
      (*env)->ReleaseByteArrayElements(env, jtagBytes, pjtagBytes, 0);
      /* access PLCIO err number and description from PLC object */
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - PLCIO plc_write returned -1, PLCIO Err %d: %s",
	       __FILE__, FUNCTION_NAME, __LINE__,
	       plcConnArray[connNumber]->j_error, plcConnArray[connNumber]->ac_errmsg);

      /* throw PLCIO exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_PLCIO, errStr);
      return -1;
    }

  (*env)->ReleaseByteArrayElements(env, jtagBytes, pjtagBytes, 0);

  return 0;
} /* end plc_1write() */

/*
 * plc_validaddr()
 */
JNIEXPORT jint JNICALL Java_atst_giss_abplc_ABPlcioMaster_plc_1validaddr
(JNIEnv *env, jobject jobj, jint connNumber, jstring jtagName)
{
  char FUNCTION_NAME[] = "plc_validaddr";
  const char *pjtagName = (*env)->GetStringUTFChars(env, jtagName, 0);
  char plcTagName[TAG_NAME_MAX_LENGTH];
  int tagSize, tagDomain, tagOffset;
  jstring jplcTagName, jconnName;

   if (!isInitialised)
    {
      char errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - JNI is NOT initialisied so NO connections can be open so CANNOT validate address over connection connName '%s' using connNumber = %d the tag named '%s'",
	       __FILE__, FUNCTION_NAME, __LINE__, plcConnNames[connNumber], connNumber, pjtagName);

      /* throw PLCIO exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_PLCIO, errStr);
      return -1;
    }

  /* check connNumber is OK */
  if (isValidConnNumber(env, connNumber, FUNCTION_NAME, __LINE__) < 0)
    {
      /* function isValidConnNumber() throws appropriate exception */
      return -1;
    }

  /* check tagName string length is not too long */
  if ((strlen(pjtagName) <= 0 ) || ((strlen(pjtagName)+1) > TAG_NAME_MAX_LENGTH))
    {
      char  errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - connName '%s' attempt to validate tag with invalid length, tagName '%s' with length %d, is outside range 1 to %d",
	       __FILE__, FUNCTION_NAME, __LINE__, plcConnNames[connNumber], pjtagName,
	       (int)(strlen(pjtagName)), (TAG_NAME_MAX_LENGTH - 1));

      (*env)->ReleaseStringUTFChars(env, jtagName, pjtagName);

      /* throw JNI exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
      return -1;
    }
  /* now we know it's safe to copy Java tagName string to local copy and release */
  strncpy(plcTagName, pjtagName, TAG_NAME_MAX_LENGTH - 1);
  (*env)->ReleaseStringUTFChars(env, jtagName, pjtagName);

  /* call PLCIO plc_validaddr() */
  if (plc_validaddr(plcConnArray[connNumber], plcTagName,
		    &tagSize, &tagDomain, &tagOffset) < 0)
    {
      char errStr[STR_MAX_LEN];
      /* access PLCIO err number and description from PLC object */
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - PLCIO plc_validaddr returned -1, PLCIO Err %d: %s",
	       __FILE__, FUNCTION_NAME, __LINE__,
	       plcConnArray[connNumber]->j_error, plcConnArray[connNumber]->ac_errmsg);

      /* throw PLCIO exception */
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_PLCIO, errStr);
      return -1;
    }

  /* call Java method used to accept results from plc_validaddr */
  jconnName = (*env)->NewStringUTF(env, plcConnNames[connNumber]);
  jplcTagName = (*env)->NewStringUTF(env, plcTagName);
  (*env)->CallStaticVoidMethod(env, jobj, jmID_plc_validaddrCallback,
			       connNumber, jconnName,
			       jplcTagName, tagSize, tagDomain, tagOffset);

  (*env)->DeleteLocalRef(env, jconnName);
  (*env)->DeleteLocalRef(env, jplcTagName);

  /* catch and throw any JNI exceptions occuring during call to Java method plc_validaddrCallback() */
  if ((*env)->ExceptionOccurred(env))
    {
      char errStr[STR_MAX_LEN];
      snprintf(errStr, (STR_MAX_LEN - 1),
	       "C - ERROR %s:JNI %s():%d - Java exception occurred calling Java method plc_validaddrCallback()",
	       __FILE__, FUNCTION_NAME, __LINE__);

      /* throw JNI exception */
      (*env)->ExceptionDescribe(env);
      throwJavaException(env, CLASS_ABPLCIO_EXCEPTION_JNI, errStr);
      return -1;
    }

  return 0;
} /* end plc_1validaddr() */

/*
 * JNI function implementations
 * Helper functions
 */

/*
 * plcioGetNumberOfOpenConnections() */
JNIEXPORT jint JNICALL Java_PlcioNative_plcioGetNumberOfOpenConnections
(JNIEnv * env, jobject jobj)
{
  return plcConnTotal;
}  /* end Java_PlcioNative_plcioGetNumberOfOpenConnections() */

/*
 * End of file
 */
