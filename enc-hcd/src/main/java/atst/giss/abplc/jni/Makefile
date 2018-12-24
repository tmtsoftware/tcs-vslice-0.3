OUTSIDE_INC_DIR  += /usr/local/include/idom
OUTSIDE_INC_DIR  += /usr/local/include/giss
OUTSIDE_LIB      += plc plccip plcvgis
OUTSIDE_LIBS_DIR += /usr/local/lib/idom
OUTSIDE_LIBS_DIR += /usr/local/lib/giss

# you should not need to edit below this point

#include ${ATST}/Make.common

PACKAGE = giss
JNILIB  = atst_giss_abplc_ABPlcioMaster

CFILES := $(wildcard *.c)
CPPFILES := $(wildcard *.cpp)
OBJECTS := $(wildcard *.o)

all: 
 
	gcc $(foreach cppfile,$(CPPFILES),$(CC) $(CFLAGS) $(CPPFLAGS) -fPIC $(foreach inc,$(OUTSIDE_INC_DIR),-I$(inc)) -c $(cppfile))
	$(LD) $(foreach lib,$(OUTSIDE_LIB),-l$(lib)) $(foreach libdir,$(OUTSIDE_LIBS_DIR),-L$(libdir)) -shared -o lib$(JNILIB).so *.o
##	@mkdir -p $(ATSTROOT)/lib/$(ARCHNAME)/$(PACKAGE)/
##	$(CP) lib$(JNILIB).so $(ATSTROOT)/lib/$(ARCHNAME)/$(PACKAGE)/
## ajb - copy library to lib/c++/ so can be accessed by src/c++/atst/ecs/plciovemcs
#	@mkdir -p $(ATSTROOT)/lib/c++/$(ARCHNAME)/$(PACKAGE)/
#	$(CP) lib$(JNILIB).so $(ATSTROOT)/lib/c++/$(ARCHNAME)/$(PACKAGE)/

clean: 	
	$(foreach obj,$(OBJECTS),@$(RM) $(obj))
	$(RM) lib$(JNILIB).so
#	$(RM) $(ATSTROOT)/lib/c++/$(ARCHNAME)/$(PACKAGE)/lib$(JNILIB).so

