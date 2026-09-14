package bio.cosy.feddb.core.helper;

public class DecodedFile {
    private String fileName;
    private byte[] content;

    public DecodedFile(String fileName, byte[] content) {
        this.fileName = fileName;
        this.content = content;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getContent() {
        return content;
    }
}
