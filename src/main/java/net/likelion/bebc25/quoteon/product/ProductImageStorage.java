package net.likelion.bebc25.quoteon.product;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class ProductImageStorage {

    static final String PUBLIC_PREFIX = "/images/products/";
    private static final Pattern SAFE_FILENAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,200}\\.(jpg|jpeg|png)$");

    private final Path directory;

    public ProductImageStorage() {
        this.directory = Path.of(System.getProperty("user.dir"), "images", "products").toAbsolutePath().normalize();
    }

    public StoredImage store(ProductImageValidator.ValidatedImage image) {
        String filename = UUID.randomUUID() + "." + image.extension();
        Path target = resolveStored(filename);
        try {
            Files.createDirectories(directory);
            Files.write(target, image.bytes());
        } catch (IOException exception) {
            throw new IllegalStateException("이미지를 저장하지 못했습니다.", exception);
        }
        return new StoredImage(PUBLIC_PREFIX + filename);
    }

    public LoadedImage load(String filename) {
        if (!isSafeFilename(filename)) {
            throw new InvalidProductImagePathException();
        }
        Path target = resolveStored(filename);
        if (!Files.isRegularFile(target)) {
            throw new ProductImageNotFoundException();
        }
        try {
            byte[] bytes = Files.readAllBytes(target);
            MediaType mediaType = filename.toLowerCase(Locale.ROOT).endsWith(".png")
                    ? MediaType.IMAGE_PNG
                    : MediaType.IMAGE_JPEG;
            return new LoadedImage(bytes, mediaType);
        } catch (IOException exception) {
            throw new ProductImageNotFoundException();
        }
    }

    public void deletePublicPath(String publicPath) {
        if (publicPath == null || !publicPath.startsWith(PUBLIC_PREFIX)) {
            return;
        }
        String filename = publicPath.substring(PUBLIC_PREFIX.length());
        if (!isSafeFilename(filename)) {
            return;
        }
        try {
            Files.deleteIfExists(resolveStored(filename));
        } catch (IOException exception) {
            throw new IllegalStateException("기존 이미지를 정리하지 못했습니다.", exception);
        }
    }

    private Path resolveStored(String filename) {
        Path target = directory.resolve(filename).normalize();
        if (!target.startsWith(directory) || !directory.equals(target.getParent())) {
            throw new InvalidProductImagePathException();
        }
        return target;
    }

    static boolean isSafeFilename(String filename) {
        return filename != null
                && !filename.contains("..")
                && !filename.contains("/")
                && !filename.contains("\\")
                && SAFE_FILENAME.matcher(filename).matches();
    }

    public record StoredImage(String publicPath) {
    }

    public record LoadedImage(byte[] bytes, MediaType mediaType) {
    }
}
