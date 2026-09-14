package bio.cosy.feddb.core.api.file;

import org.apache.poi.poifs.filesystem.FileMagic;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

public class FileHelper {

    public static boolean isZipFile(File file) {
        try {
            return file != null && file.isFile() && FileMagic.valueOf(file) == FileMagic.OOXML;
        } catch (IOException | UncheckedIOException e) {
            return false;
        }
    }
}
