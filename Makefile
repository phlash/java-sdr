FREQ=100000
PWD=$(shell pwd)
JTRANS=../jtransforms-2.3.jar
#JNALIB=/usr/share/java/jna.jar
#JNAPLT=/usr/share/java/jna-platform.jar
JNALIB=../jna-4.1.0.jar
JNAPLT=../jna-platform-4.1.0.jar
LIBFCD=../../fcdctl/libfcd.so
L64FCD=libfcd64.so
WINFCD=../../fcdctl/fcd.dll
W64FCD=../../fcdctl/fcd64.dll

CLASSES=bin/jsdr.class bin/phase.class bin/fft.class bin/demod.class bin/FUNcubeBPSKDemod.class bin/FECDecoder.class \
	bin/FCD.class bin/FCDlinux.class bin/FCDwindows.class bin/HIDwin32.class
CLASSPATH=$(JTRANS):$(JNALIB):$(JNAPLT)

all: bin bin/jsdr.jar

clean:
	rm -rf bin *~

bin/jsdr.jar: $(CLASSES) JSDR.MF
	sed -e 's^CLASSPATH^../$(JTRANS) ../$(JNALIB) ../$(JNAPLT)^' <JSDR.MF >bin/temp.mf
	cd bin; jar cfm jsdr.jar temp.mf *.class

# Special order-only dependancy, just ensures bin target is built before classes
bin:
	mkdir -p bin
	ln -s $(LIBFCD) bin
	ln -s libfcd.so bin/$(L64FCD)

# Compile that java
bin/%.class: %.java
	javac -classpath .:$(CLASSPATH) -d bin $<

# Try it!
test: all
	java -jar bin/jsdr.jar
