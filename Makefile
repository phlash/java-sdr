FREQ=100000
PWD=$(shell pwd)
JTRANS=../jtransforms-2.3.jar
JNALIB=/usr/share/java/jna.jar
JNAPLT=/usr/share/java/jna-platform.jar
#JNALIB=../jna-4.1.0.jar
#JNAPLT=../jna-platform-4.1.0.jar

CLASSES=bin/jsdr.class bin/phase.class bin/fft.class bin/demod.class bin/FUNcubeBPSKDemod.class bin/FECDecoder.class \
	bin/FCD.class bin/FCDlinux.class bin/FCDwindows.class bin/HIDwin32.class
DEPS=$(JTRANS) $(JNALIB) $(JNAPLT)
BINS=$(addprefix bin/,$(notdir $(DEPS)))
SPACE := 
SPACE += 
COMPILE_CP=$(subst $(SPACE),:,$(DEPS))
MF_CP=$(notdir $(DEPS))

all: bin bin/jsdr.jar $(BINS)

clean:
	rm -rf bin *~

bin/jsdr.jar: $(CLASSES) JSDR.MF
	sed -e 's^CLASSPATH^$(MF_CP)^' <JSDR.MF >bin/temp.mf
	cd bin; jar cfm jsdr.jar temp.mf *.class

# Dependencies - there must be a better way..
$(BINS):
	cp -p $(DEPS) bin

# Special order-only dependancy, just ensures bin target is built before classes and deps
bin:
	mkdir -p bin

# Compile that java
bin/%.class: %.java
	javac -classpath .:$(COMPILE_CP) -d bin $<

# Try it!
test: all
	java -jar bin/jsdr.jar
