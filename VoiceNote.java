import java.io.Serializable;

public class VoiceNote implements Serializable {

    private final byte audioData[];
    private final String dest;
    private final String src;
    private int id;

    public VoiceNote(byte[] audioData, String dest, String src) {
        this.audioData = audioData;
        this.dest = dest;
        this.src = src;
    }

    public byte[] getAudioData() {
        return audioData;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDest() {
        return dest;
    }

    public String getSrc() {
        return src;
    }
}
