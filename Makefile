FREQ=100000
PWD=$(shell pwd)
JTRANS=$(PWD)/../JTransforms/jtransforms-2.3.jar
FCDAPI=$(PWD)/../qthid/bin/fcdapi.jar
JNALIB=/usr/share/java/jna.jar

CLASSES=bin/jsdr.class bin/phase.class bin/fft.class bin/demod.class
CLASSPATH=$(FCDAPI):$(JTRANS):$(JNALIB)

all: bin/jsdr.jar

clean:
	rm -rf bin *~

bin/jsdr.jar: $(CLASSES) JSDR.MF
	sed -e 's^CLASSPATH^$(FCDAPI) $(JTRANS) $(JNALIB)^' <JSDR.MF >bin/temp.mf
	jar cfm $@ bin/temp.mf -C bin .

# Special order-only dependancy, just ensures bin target is built before classes
$(CLASSES): | bin
bin:
	mkdir -p bin

# Compile that java
bin/%.class: %.java
	javac -classpath .:$(CLASSPATH) -d bin $<

# Try it!
test: all
	java -jar bin/jsdr.jar
