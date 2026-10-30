package net.likelion.bebc25.quoteon.product;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

@Component
public class ProductImageValidator {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final String EMPTY_MESSAGE = "빈 파일은 업로드할 수 없습니다.";
    public static final String SIZE_MESSAGE = "파일 크기는 5MB 이하여야 합니다.";
    static final String TYPE_MESSAGE = "JPEG 또는 PNG 이미지만 업로드할 수 있습니다.";

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidProductImageException(EMPTY_MESSAGE);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidProductImageException(SIZE_MESSAGE);
        }
        byte[] bytes = readBytes(file);
        if (bytes.length == 0) {
            throw new InvalidProductImageException(EMPTY_MESSAGE);
        }
        if (bytes.length > MAX_BYTES) {
            throw new InvalidProductImageException(SIZE_MESSAGE);
        }
        ImageKind kind = detect(bytes);
        if (kind == null || !decodes(bytes, kind)) {
            throw new InvalidProductImageException(TYPE_MESSAGE);
        }
        requireMatchingExtension(file.getOriginalFilename(), kind);
        return new ValidatedImage(bytes, kind.storedExtension());
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new InvalidProductImageException(TYPE_MESSAGE);
        }
    }

    private static ImageKind detect(byte[] bytes) {
        if (isJpeg(bytes)) {
            return ImageKind.JPEG;
        }
        if (isPng(bytes)) {
            return ImageKind.PNG;
        }
        return null;
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A;
    }

    private static boolean decodes(byte[] bytes, ImageKind kind) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                return false;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return false;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName();
                if (format == null || !kind.formatNames().contains(format.toLowerCase(Locale.ROOT))) {
                    return false;
                }
                BufferedImage image = reader.read(0);
                return image != null;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            return false;
        }
    }

    private static void requireMatchingExtension(String originalFilename, ImageKind kind) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return;
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
        if (!kind.acceptedExtensions().contains(extension)) {
            throw new InvalidProductImageException(TYPE_MESSAGE);
        }
    }

    public record ValidatedImage(byte[] bytes, String extension) {
    }

    private enum ImageKind {
        JPEG(Set.of("jpeg", "jpg"), Set.of("jpg", "jpeg"), "jpg"),
        PNG(Set.of("png"), Set.of("png"), "png");

        private final Set<String> formatNames;
        private final Set<String> acceptedExtensions;
        private final String storedExtension;

        ImageKind(Set<String> formatNames, Set<String> acceptedExtensions, String storedExtension) {
            this.formatNames = formatNames;
            this.acceptedExtensions = acceptedExtensions;
            this.storedExtension = storedExtension;
        }

        private Set<String> formatNames() {
            return formatNames;
        }

        private Set<String> acceptedExtensions() {
            return acceptedExtensions;
        }

        private String storedExtension() {
            return storedExtension;
        }
    }
}
