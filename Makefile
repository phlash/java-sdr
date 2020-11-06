FREQ=100000
PWD=$(shell pwd)
# Where to put all the built objects and dependencies
OUT=bin
# The output java package path
PKG=com/ashbysoft/java_sdr
# JTransforms now lives in a sane repository, we could switch to Maven for builds...
JTRANS=https://repo1.maven.org/maven2/edu/emory/mathcs/JTransforms/2.4/JTransforms-2.4.jar
# FLAC audio file support 'cause 10min IQ WAVs are nearly 500M, but a FLAC is nearer 250M..
JFLAC=https://repo.spring.io/plugins-release/jflac/jflac/1.3/jflac-1.3.jar
# Finally: a cross-platform HID API for Java, only older forks in Maven as far as I can find..
# NB: linking to a specific tree version, hence the fugly links :)
HIDAPI=https://github.com/nyholku/purejavahidapi/raw/f769fcddf62503cff554e646587c92350ca664e5/bin/purejavahidapi.jar
JNALIB=https://github.com/nyholku/purejavahidapi/raw/f769fcddf62503cff554e646587c92350ca664e5/lib/jna-5.5.0.jar
JNAPLT=https://github.com/nyholku/purejavahidapi/raw/f769fcddf62503cff554e646587c92350ca664e5/lib/jna-platform-5.5.0.jar

# Our built objects
CLASSES= \
	IConfig.class \
	IPublish.class \
	IPublishListener.class \
	ILogger.class \
	AudioDescriptor.class \
	AudioSource.class \
	IAudio.class \
	IAudioHandler.class \
	IUIHost.class \
	IUIComponent.class \
	JavaAudio.class \
	jsdr.class \
	waterfall.class \
	FCD.class \
	phase.class \
	fft.class \
	demod.class 
#	FUNcubeBPSKDemod.class \
#	FECDecoder.class \

OUTCLS=$(addprefix $(OUT)/$(PKG)/,$(CLASSES))
TARGET=jsdr.jar
OUTTGT=$(OUT)/$(TARGET)

# Sources to build
SOURCES=$(subst .class,.java,$(CLASSES))

# Generate class paths for compile and metafile
DEPS=$(JTRANS) $(JFLAC) $(HIDAPI) $(JNALIB) $(JNAPLT)
OUTDEPS=$(addprefix $(OUT)/,$(notdir $(DEPS)))
SPACE := 
SPACE += 
COMPILE_CP=$(subst $(SPACE),:,$(OUTDEPS))
MF_CP=$(notdir $(DEPS))
MF_PKG=$(subst /,.,$(PKG))

all: $(OUT) $(OUTDEPS) $(OUTTGT)

clean:
	rm -rf bin *~

.PHONY: clean

# Target executable jar
$(OUTTGT): $(OUTCLS) JSDR.MF
	sed -e 's^CLASSPATH^$(MF_CP)^' -e 's^PKG^$(MF_PKG)^' <JSDR.MF >$(OUT)/temp.mf
	cd $(OUT); jar cfm $(TARGET) temp.mf $(PKG)/*.class

# NB: we compile all sources together here, since Javac works better like that!
$(OUT)/$(PKG)/%.class: %.java
	javac -classpath .:$(COMPILE_CP) -d $(OUT) $(SOURCES)

# Special dependancy, just ensures bin target is built before classes and deps
$(OUT):
	mkdir -p bin

# Dependencies - there must be a better way..
$(OUTDEPS):
	@for u in $(DEPS); do wget -nv -P $(OUT) $$u; done

# Try it!
test: all
	java -jar $(OUT)/jsdr.jar
