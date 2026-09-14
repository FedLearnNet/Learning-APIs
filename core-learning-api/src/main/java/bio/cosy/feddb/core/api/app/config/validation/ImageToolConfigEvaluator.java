package bio.cosy.feddb.core.api.app.config.validation;

import bio.cosy.feddb.core.api.app.config.ToolConfigDTO;
import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.file.analytics.FileProfile;
import bio.cosy.feddb.core.base.BaseValidationResultDTO;

import java.util.Base64;
import java.util.List;

public class ImageToolConfigEvaluator implements ToolConfigEvaluator {

    @Override
    public BaseValidationResultDTO evaluateContent(ToolConfigDTO config, FileDTO file, String content) {
        try {
            byte[] blob = readImageBytes(content);

            if (blob.length == 0) {
                return BaseValidationResultDTO.fail(List.of("Image is empty."));
            }

            if (isPng(blob) || isJpeg(blob) || isGif(blob) || isWebp(blob) || isBmp(blob)) {
                return BaseValidationResultDTO.ok();
            }

            BaseValidationResultDTO ok = BaseValidationResultDTO.ok();
            ok.getMeta().put("warnings", List.of("Unknown image format (magic header not recognized)."));
            return ok;

        } catch (Exception e) {
            return BaseValidationResultDTO.fail(List.of(message(e)));
        }
    }

    private byte[] readImageBytes(String v) {
        if (v == null) {
            throw new IllegalArgumentException("Expected base64 image content, got: null");
        }
        String s = v.trim();
        if (s.isEmpty()) {
            return new byte[0];
        }

        int commaIdx = s.indexOf(',');
        if (s.startsWith("data:") && commaIdx >= 0) {
            String meta = s.substring(5, commaIdx).toLowerCase();
            String payload = s.substring(commaIdx + 1).trim();

            if (meta.contains(";base64")) {
                return decodeBase64(payload);
            }
            throw new IllegalArgumentException("Unsupported data URL encoding (expected base64).");
        }

        return decodeBase64(s);
    }

    private byte[] decodeBase64(String s) {
        try {
            return Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Expected base64 encoded image content.");
        }
    }

    private boolean isPng(byte[] b) {
        byte[] sig = new byte[] {(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        return startsWith(b, sig);
    }

    private boolean isJpeg(byte[] b) {
        return b.length >= 4
                && (b[0] == (byte) 0xFF && b[1] == (byte) 0xD8)
                && (b[b.length - 2] == (byte) 0xFF && b[b.length - 1] == (byte) 0xD9);
    }

    private boolean isGif(byte[] b) {
        return startsWith(b, ascii("GIF87a")) || startsWith(b, ascii("GIF89a"));
    }

    private boolean isWebp(byte[] b) {
        // len >= 12 and b[0:4] == "RIFF" and b[8:12] == "WEBP"
        return b.length >= 12
                && equalsRange(b, 0, ascii("RIFF"))
                && equalsRange(b, 8, ascii("WEBP"));
    }

    private boolean isBmp(byte[] b) {
        return startsWith(b, ascii("BM"));
    }

    private boolean startsWith(byte[] b, byte[] prefix) {
        if (b.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (b[i] != prefix[i]) return false;
        }
        return true;
    }

    private boolean equalsRange(byte[] b, int offset, byte[] expected) {
        if (offset < 0 || b.length < offset + expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if (b[offset + i] != expected[i]) return false;
        }
        return true;
    }

    private byte[] ascii(String s) {
        // ASCII-safe: headers are ASCII bytes
        return s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    @Override
    public BaseValidationResultDTO evaluateProfile(ToolConfigDTO config, FileDTO file, FileProfile profile) {
        return null;
    }
}
