JFLAGS = -g
JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
        Properties.java \
        ChatMessage.java \
        VoiceNote.java \
        Channel.java \
        Client.java \
		ClientUI.java \
		ClientThread.java \
		ServerUI.java	
		

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
