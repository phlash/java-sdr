FREQ=100000
PWD=$(shell pwd)
JTRANS=$(PWD)/../../Downloads/jtransforms-2.3.jar
FCDAPI=$(PWD)/../qthid/bin/fcdapi.jar
#JTRANS=$(PWD)/../jtransforms-2.3.jar
JNALIB=/usr/share/java/jna.jar

CLASSES=bin/jsdr.class bin/phase.class bin/fft.class bin/demod.class bin/FUNcubeBPSKDemod.class bin/FECDecoder.class
CLASSPATH=$(FCDAPI):$(JTRANS):$(JNALIB)

all: bin jsdr.jar

clean:
	rm -rf bin *~ jsdr.jar

jsdr.jar: $(CLASSES) JSDR.MF
	sed -e 's^CLASSPATH^$(FCDAPI) $(JTRANS) $(JNALIB)^' <JSDR.MF >bin/temp.mf
	jar cfm $@ bin/temp.mf -C bin .

# Special order-only dependancy, just ensures bin target is built before classes
bin:
	mkdir -p bin

# Compile that java
bin/%.class: %.java
	javac -classpath .:$(CLASSPATH) -d bin $<

# Try it!
test: all
	java -jar jsdr.jar
