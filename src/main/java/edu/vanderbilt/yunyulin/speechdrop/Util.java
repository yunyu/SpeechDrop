package edu.vanderbilt.yunyulin.speechdrop;

import ro.pippo.core.Response;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Util {
    public static void deleteFolderRecursive(final Path folder) throws IOException {
        if (Files.exists(folder)) {
            Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) {
                        throw exc;
                    }
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    public static void zip(Collection<File> files, OutputStream out) throws IOException {
        try (ZipOutputStream zipOut = new ZipOutputStream(out)) {
            List<String> filenames = new ArrayList<>(files.size());
            for (File file : files) {
                // Just in case something gets deleted while we're archiving
                if (!Files.exists(file.toPath())) continue;

                String entryName;
                int suffix = 1;

                while (true) {
                    entryName = file.getName();
                    if (suffix > 1) {
                        String suffixString = " (" + suffix + ")";
                        int dotIndex = entryName.lastIndexOf('.');
                        if (dotIndex == -1) {
                            entryName += suffixString;
                        } else {
                            entryName = entryName.substring(0, dotIndex)
                                    + suffixString
                                    + entryName.substring(dotIndex, entryName.length());
                        }
                    }
                    if (!filenames.contains(entryName)) {
                        filenames.add(entryName);
                        break;
                    } else {
                        suffix++;
                    }
                }

                zipOut.putNextEntry(new ZipEntry(entryName));
                Files.copy(file.toPath(), zipOut);
                zipOut.closeEntry();
            }
        }
    }

    private final static byte[] emptyZip = new byte[]
            {80, 75, 5, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    public static InputStream getEmptyZipInputStream() {
        return new ByteArrayInputStream(emptyZip);
    }

    public static OutputStream getResponseOutputStream(Response res, String fileName) {
        res.chunked(true);
        res.header("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        return res.getOutputStream();
    }
}
