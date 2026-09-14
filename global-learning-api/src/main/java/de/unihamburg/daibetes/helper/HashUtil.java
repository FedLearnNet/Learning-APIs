package de.unihamburg.daibetes.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import org.apache.commons.codec.digest.DigestUtils;

public class HashUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String sha256(Object obj) {
        try {
            String json = MAPPER.writeValueAsString(obj);
            return DigestUtils.sha256Hex(json);
        } catch (Exception e) {
            Log.errorf("Failed to serialize object for hashing: %s", e.getMessage());
            return DigestUtils.sha256Hex(obj.toString());

        }
    }
}
