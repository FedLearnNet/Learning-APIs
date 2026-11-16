package de.unihamburg.daibetes.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import org.apache.commons.codec.digest.DigestUtils;

import java.security.SecureRandom;
import java.util.HexFormat;

public class HashUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String sha256(Object obj) {
        try {
            String json = MAPPER.writeValueAsString(obj);
            return DigestUtils.sha256Hex(json);
        } catch (Exception e) {
            Log.errorf("Failed to serialize object for hashing: %s", e.getMessage());
            return DigestUtils.sha256Hex(obj.toString());

        }
    }

    public static String randomHex(int bytes) {
        byte[] random = new byte[bytes];
        RANDOM.nextBytes(random);
        return HexFormat.of().formatHex(random);
    }
}
