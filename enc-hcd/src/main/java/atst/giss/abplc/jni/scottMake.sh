
gcc -fPIC -shared -I/opt/jdk1.8.0_101/include -I/opt/jdk1.8.0_101/include/linux -lplc -lplccip atst_giss_abplc_ABPlcioMaster.c -o libatst_giss_abplc_ABPlcioMaster.so

sudo cp lib*.so /usr/local/lib
