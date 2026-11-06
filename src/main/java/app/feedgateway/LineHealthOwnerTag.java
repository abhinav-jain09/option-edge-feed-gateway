package app.feedgateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The owner tag of a line-health user line: thirty-two lowercase hexadecimal characters, the first 128 bits of the SHA-256 of the authenticated subject. It is the same
 * function as the web tier's {@code LevelAbsorptionManualLinePublisher.ownerTag} (a shared set of vectors is tested on both sides), so a record the web tier tagged for a
 * subject is delivered to that subject's sockets and to no others.
 */
final class LineHealthOwnerTag {

    private LineHealthOwnerTag() { }

    static String of(String subject) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(subject.getBytes(StandardCharsets.UTF_8));
            StringBuilder tag = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                tag.append(Character.forDigit((digest[i] >> 4) & 0xF, 16)).append(Character.forDigit(digest[i] & 0xF, 16));
            }
            return tag.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
