package com.linghy.patches;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public class BinaryPatcher
{
    private static final int DEFAULT_BUFFER_SIZE = 10 * 1024 * 1024;

    public static class X86
    {
        public static final byte[] RET = {(byte) 0xC3};
        public static final byte[] RETN = {(byte) 0xC2, 0x00, 0x00}; // ret N
        public static final byte[] RETF = {(byte) 0xCB};

        public static final byte[] NOP = {(byte) 0x90};
        public static final byte[] NOP2 = {0x66, (byte) 0x90}; // 2-byte NOP
        public static final byte[] NOP3 = {0x0F, 0x1F, 0x00}; // 3-byte NOP
        public static final byte[] NOP4 = {0x0F, 0x1F, 0x40, 0x00}; // 4-byte NOP
        public static final byte[] NOP5 = {0x0F, 0x1F, 0x44, 0x00, 0x00}; // 5-byte NOP
        public static final byte[] NOP6 = {0x66, 0x0F, 0x1F, 0x44, 0x00, 0x00}; // 6-byte NOP
        public static final byte[] NOP7 = {0x0F, 0x1F, (byte) 0x80, 0x00, 0x00, 0x00, 0x00}; // 7-byte NOP
        public static final byte[] NOP8 = {0x0F, 0x1F, (byte) 0x84, 0x00, 0x00, 0x00, 0x00, 0x00}; // 8-byte NOP
        public static final byte[] NOP9 = {0x66, 0x0F, 0x1F, (byte) 0x84, 0x00, 0x00, 0x00, 0x00, 0x00}; // 9-byte NOP

        public static final byte[] INT3 = {(byte) 0xCC}; // Breakpoint
        public static final byte[] JMP_SHORT = {(byte) 0xEB, 0x00}; // JMP rel8

        // MOV EAX, immediate
        public static byte[] movEax(int value) {
            return new byte[] {
                    (byte) 0xB8,
                    (byte) (value & 0xFF),
                    (byte) ((value >> 8) & 0xFF),
                    (byte) ((value >> 16) & 0xFF),
                    (byte) ((value >> 24) & 0xFF)
            };
        }

        // XOR EAX, EAX
        public static final byte[] XOR_EAX_EAX = {0x33, (byte) 0xC0};
        public static final byte[] XOR_ECX_ECX = {0x33, (byte) 0xC9};
        public static final byte[] XOR_EDX_EDX = {0x33, (byte) 0xD2};
    }

    public static boolean makeRET(Path file, long offset) throws IOException {
        return makeRET(file, offset, 1);
    }

    public static boolean makeRET(Path file, long offset, int size) throws IOException
    {
        if (size < 1) {
            throw new IllegalArgumentException("Size must be at least 1");
        }

        byte[] patch = new byte[size];
        patch[0] = X86.RET[0]; // 0xC3

        for (int i = 1; i < size; i++) {
            patch[i] = X86.NOP[0]; // 0x90
        }

        writeBytes(file, offset, patch);
        return true;
    }

    public static boolean makeRETWithValue(Path file, long offset, int returnValue) throws IOException
    {
        byte[] movEax = X86.movEax(returnValue); // 5 bytes
        byte[] ret = X86.RET; // 1 byte

        byte[] patch = new byte[movEax.length + ret.length];
        System.arraycopy(movEax, 0, patch, 0, movEax.length);
        System.arraycopy(ret, 0, patch, movEax.length, ret.length);

        writeBytes(file, offset, patch);
        return true;
    }

    public static boolean makeNOP(Path file, long offset, int count) throws IOException
    {
        if (count < 1) {
            throw new IllegalArgumentException("Count must be at least 1");
        }

        byte[] nops = new byte[count];
        Arrays.fill(nops, X86.NOP[0]); // 0x90

        writeBytes(file, offset, nops);
        return true;
    }

    public static boolean makeOptimalNOP(Path file, long offset, int count) throws IOException
    {
        if (count < 1) {
            throw new IllegalArgumentException("Count must be at least 1");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int remaining = count;

        while (remaining > 0)
        {
            byte[] nop;

            if (remaining >= 9) {
                nop = X86.NOP9;
            } else if (remaining >= 8) {
                nop = X86.NOP8;
            } else if (remaining >= 7) {
                nop = X86.NOP7;
            } else if (remaining >= 6) {
                nop = X86.NOP6;
            } else if (remaining >= 5) {
                nop = X86.NOP5;
            } else if (remaining >= 4) {
                nop = X86.NOP4;
            } else if (remaining >= 3) {
                nop = X86.NOP3;
            } else if (remaining >= 2) {
                nop = X86.NOP2;
            } else {
                nop = X86.NOP;
            }

            baos.write(nop);
            remaining -= nop.length;
        }

        writeBytes(file, offset, baos.toByteArray());
        return true;
    }

    public static void writeMemory(Path file, long offset, byte[] data) throws IOException {
        writeBytes(file, offset, data);
    }

    public static void writeMemory(Path file, long offset, String hexData) throws IOException {
        writeBytes(file, offset, hexToBytes(hexData));
    }

    public static void writeByte(Path file, long offset, byte value) throws IOException {
        writeBytes(file, offset, new byte[]{value});
    }

    public static void writeByte(Path file, long offset, int value) throws IOException {
        writeBytes(file, offset, new byte[]{(byte) value});
    }

    public static void writeWord(Path file, long offset, int value) throws IOException
    {
        byte[] data = {
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF)
        };
        writeBytes(file, offset, data);
    }

    public static void writeDword(Path file, long offset, int value) throws IOException
    {
        byte[] data = {
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF)
        };
        writeBytes(file, offset, data);
    }

    public static void writeQword(Path file, long offset, long value) throws IOException
    {
        byte[] data = {
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF),
                (byte) ((value >> 32) & 0xFF),
                (byte) ((value >> 40) & 0xFF),
                (byte) ((value >> 48) & 0xFF),
                (byte) ((value >> 56) & 0xFF)
        };
        writeBytes(file, offset, data);
    }

    public static byte readByte(Path file, long offset) throws IOException {
        return readBytes(file, offset, 1)[0];
    }

    public static int readWord(Path file, long offset) throws IOException
    {
        byte[] data = readBytes(file, offset, 2);
        return (data[0] & 0xFF) | ((data[1] & 0xFF) << 8);
    }

    public static int readDword(Path file, long offset) throws IOException
    {
        byte[] data = readBytes(file, offset, 4);
        return (data[0] & 0xFF) |
                ((data[1] & 0xFF) << 8) |
                ((data[2] & 0xFF) << 16) |
                ((data[3] & 0xFF) << 24);
    }

    public static long readQword(Path file, long offset) throws IOException
    {
        byte[] data = readBytes(file, offset, 8);
        return (data[0] & 0xFFL) |
                ((data[1] & 0xFFL) << 8) |
                ((data[2] & 0xFFL) << 16) |
                ((data[3] & 0xFFL) << 24) |
                ((data[4] & 0xFFL) << 32) |
                ((data[5] & 0xFFL) << 40) |
                ((data[6] & 0xFFL) << 48) |
                ((data[7] & 0xFFL) << 56);
    }

    public static int replaceAllBytes(Path file, byte oldValue, byte newValue) throws IOException
    {
        int replacements = 0;
        long fileSize = Files.size(file);

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw"))
        {
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            long pos = 0;

            while (pos < fileSize)
            {
                int bytesRead = raf.read(buffer);
                if (bytesRead <= 0) break;

                boolean modified = false;
                for (int i = 0; i < bytesRead; i++)
                {
                    if (buffer[i] == oldValue)
                    {
                        buffer[i] = newValue;
                        modified = true;
                        replacements++;
                    }
                }

                if (modified)
                {
                    raf.seek(pos);
                    raf.write(buffer, 0, bytesRead);
                    raf.seek(pos + bytesRead);
                }

                pos += bytesRead;
            }
        }

        return replacements;
    }

    public static class PatternMatch
    {
        public final long offset;
        public final byte[] context;
        public final int contextStart;
        public final int contextEnd;

        public PatternMatch(long offset, byte[] context, int contextStart, int contextEnd)
        {
            this.offset = offset;
            this.context = context;
            this.contextStart = contextStart;
            this.contextEnd = contextEnd;
        }

        @Override
        public String toString() {
            return String.format("Match at 0x%X", offset);
        }
    }

    public static class SearchConfig
    {
        public long startOffset = 0;
        public long maxSearchSize = Long.MAX_VALUE;
        public int bufferSize = DEFAULT_BUFFER_SIZE;
        public int contextBefore = 64;
        public int contextAfter = 64;
        public boolean findAll = false;

        public SearchConfig startAt(long offset) {
            this.startOffset = offset;
            return this;
        }

        public SearchConfig maxSize(long size) {
            this.maxSearchSize = size;
            return this;
        }

        public SearchConfig withContext(int before, int after) {
            this.contextBefore = before;
            this.contextAfter = after;
            return this;
        }

        public SearchConfig findAll() {
            this.findAll = true;
            return this;
        }
    }

    public interface ProgressListener {
        void onProgress(long current, long total, String message);
    }

    public static PatternMatch findPattern(Path file, byte[] pattern) throws IOException {
        return findPattern(file, pattern, new SearchConfig(), null);
    }

    public static PatternMatch findPattern(Path file, byte[] pattern, SearchConfig config,
                                           ProgressListener listener) throws IOException
    {
        List<PatternMatch> results = findPatterns(file, pattern, config, listener);
        return results.isEmpty() ? null : results.get(0);
    }

    public static List<PatternMatch> findAllPatterns(Path file, byte[] pattern) throws IOException {
        return findPatterns(file, pattern, new SearchConfig().findAll(), null);
    }

    public static List<PatternMatch> findPatterns(Path file, byte[] pattern, SearchConfig config,
                                                  ProgressListener listener) throws IOException
    {
        List<PatternMatch> matches = new ArrayList<>();
        long fileSize = Files.size(file);
        long searchEnd = Math.min(fileSize, config.startOffset + config.maxSearchSize);
        long searchSize = searchEnd - config.startOffset;

        if (searchSize <= 0 || pattern.length == 0) {
            return matches;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r"))
        {
            int bufferSize = (int) Math.min(searchSize, config.bufferSize);
            byte[] buffer = new byte[bufferSize];

            long currentPos = config.startOffset;
            raf.seek(config.startOffset);

            while (currentPos < searchEnd)
            {
                int toRead = (int) Math.min(bufferSize, searchEnd - currentPos);
                int bytesRead = raf.read(buffer, 0, toRead);

                if (bytesRead <= 0) break;

                for (int i = 0; i <= bytesRead - pattern.length; i++)
                {
                    if (matchesPattern(buffer, i, pattern))
                    {
                        long matchOffset = currentPos + i;
                        byte[] context = readContext(raf, matchOffset, pattern.length,
                                config.contextBefore, config.contextAfter);

                        matches.add(new PatternMatch(matchOffset, context,
                                config.contextBefore,
                                config.contextBefore + pattern.length));

                        if (!config.findAll) {
                            return matches;
                        }
                    }
                }

                if (listener != null) {
                    listener.onProgress(currentPos - config.startOffset, searchSize,
                            "Searching...");
                }

                currentPos += bytesRead - pattern.length + 1;
                raf.seek(currentPos);
            }
        }

        return matches;
    }

    public static PatternMatch findNestedPattern(Path file, byte[] outerPattern,
                                                 byte[] innerPattern, SearchConfig config,
                                                 ProgressListener listener) throws IOException
    {
        List<PatternMatch> outerMatches = findPatterns(file, outerPattern, config, listener);

        for (PatternMatch outer : outerMatches)
        {
            if (outer.context == null) continue;

            int innerPos = findPatternInArray(outer.context, 0,
                    outer.context.length, innerPattern);

            if (innerPos != -1)
            {
                long absoluteOffset = outer.offset - config.contextBefore + innerPos;
                return new PatternMatch(absoluteOffset, outer.context, innerPos,
                        innerPos + innerPattern.length);
            }
        }

        return null;
    }

    public static byte[] readBytes(Path file, long offset, int length) throws IOException
    {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r"))
        {
            raf.seek(offset);
            byte[] data = new byte[length];
            int bytesRead = raf.read(data);

            if (bytesRead != length) {
                throw new IOException("Could not read " + length + " bytes at offset 0x" +
                        Long.toHexString(offset));
            }

            return data;
        }
    }

    public static void writeBytes(Path file, long offset, byte[] data) throws IOException
    {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw"))
        {
            raf.seek(offset);
            raf.write(data);
        }
    }

    public static boolean replaceBytes(Path file, long offset, byte[] expected,
                                       byte[] replacement) throws IOException
    {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw"))
        {
            raf.seek(offset);

            byte[] actual = new byte[expected.length];
            int bytesRead = raf.read(actual);

            if (bytesRead != expected.length || !Arrays.equals(actual, expected)) {
                return false;
            }

            raf.seek(offset);
            raf.write(replacement);

            if (replacement.length < expected.length)
            {
                byte[] padding = new byte[expected.length - replacement.length];
                raf.write(padding);
            }

            return true;
        }
    }

    public static boolean patchPattern(Path file, byte[] pattern, byte[] replacement,
                                       SearchConfig config) throws IOException
    {
        PatternMatch match = findPattern(file, pattern, config, null);
        if (match == null) return false;

        return replaceBytes(file, match.offset, pattern, replacement);
    }

    public static Path createBackup(Path file) throws IOException {
        return createBackup(file, ".backup");
    }

    public static Path createBackup(Path file, String suffix) throws IOException
    {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        Path backupPath = file.resolveSibling(file.getFileName() + suffix + "." + timestamp);

        try {
            Files.copy(file, backupPath, StandardCopyOption.REPLACE_EXISTING);
            return backupPath;
        } catch (IOException e) {
            System.err.println("Failed to create backup: " + e.getMessage());
            return null;
        }
    }

    public static void restoreBackup(Path backup, Path target) throws IOException
    {
        if (backup != null && Files.exists(backup)) {
            Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static boolean verify(Path file, long offset, byte[] expected) throws IOException
    {
        byte[] actual = readBytes(file, offset, expected.length);
        return Arrays.equals(actual, expected);
    }

    public static byte[] hexToBytes(String hex)
    {
        hex = hex.replaceAll("\\s+", "");
        int len = hex.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }

        return data;
    }

    public static String bytesToHex(byte[] bytes) {
        return bytesToHex(bytes, " ");
    }

    public static String bytesToHex(byte[] bytes, String separator)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++)
        {
            if (i > 0 && separator != null) {
                sb.append(separator);
            }
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    public static String hexDump(byte[] data, long baseOffset)
    {
        return hexDump(data, baseOffset, 16);
    }

    public static String hexDump(byte[] data, long baseOffset, int bytesPerLine)
    {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < data.length; i += bytesPerLine)
        {
            sb.append(String.format("%08X: ", baseOffset + i));

            for (int j = 0; j < bytesPerLine; j++)
            {
                if (i + j < data.length) {
                    sb.append(String.format("%02X ", data[i + j] & 0xFF));
                } else {
                    sb.append("   ");
                }
            }

            sb.append(" | ");

            for (int j = 0; j < bytesPerLine && i + j < data.length; j++)
            {
                byte b = data[i + j];
                sb.append((b >= 32 && b < 127) ? (char) b : '.');
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    public static class MaskedPattern
    {
        public final byte[] pattern;
        public final boolean[] mask;

        public MaskedPattern(byte[] pattern, boolean[] mask)
        {
            this.pattern = pattern;
            this.mask = mask;
        }

        public static MaskedPattern fromString(String hex)
        {
            String[] tokens = hex.trim().split("\\s+");
            List<Byte> patternList = new ArrayList<>();
            List<Boolean> maskList = new ArrayList<>();

            for (String token : tokens)
            {
                if (token.equals("?") || token.equals("??"))
                {
                    patternList.add((byte) 0);
                    maskList.add(false);
                }
                else
                {
                    patternList.add((byte) Integer.parseInt(token, 16));
                    maskList.add(true);
                }
            }

            byte[] pattern = new byte[patternList.size()];
            boolean[] mask = new boolean[maskList.size()];

            for (int i = 0; i < pattern.length; i++) {
                pattern[i] = patternList.get(i);
                mask[i] = maskList.get(i);
            }

            return new MaskedPattern(pattern, mask);
        }
    }

    public static PatternMatch findMaskedPattern(Path file, MaskedPattern maskedPattern,
                                                 SearchConfig config) throws IOException
    {
        long fileSize = Files.size(file);
        long searchEnd = Math.min(fileSize, config.startOffset + config.maxSearchSize);

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r"))
        {
            byte[] buffer = new byte[config.bufferSize];
            long currentPos = config.startOffset;
            raf.seek(config.startOffset);

            while (currentPos < searchEnd)
            {
                int bytesRead = raf.read(buffer);
                if (bytesRead <= 0) break;

                for (int i = 0; i <= bytesRead - maskedPattern.pattern.length; i++)
                {
                    if (matchesMaskedPattern(buffer, i, maskedPattern))
                    {
                        long matchOffset = currentPos + i;
                        byte[] context = readContext(raf, matchOffset,
                                maskedPattern.pattern.length,
                                config.contextBefore, config.contextAfter);

                        return new PatternMatch(matchOffset, context,
                                config.contextBefore,
                                config.contextBefore + maskedPattern.pattern.length);
                    }
                }

                currentPos += bytesRead - maskedPattern.pattern.length + 1;
                raf.seek(currentPos);
            }
        }

        return null;
    }

    private static boolean matchesPattern(byte[] data, int offset, byte[] pattern)
    {
        if (offset + pattern.length > data.length) {
            return false;
        }

        for (int i = 0; i < pattern.length; i++)
        {
            if (data[offset + i] != pattern[i]) {
                return false;
            }
        }

        return true;
    }

    private static boolean matchesMaskedPattern(byte[] data, int offset, MaskedPattern masked)
    {
        if (offset + masked.pattern.length > data.length) {
            return false;
        }

        for (int i = 0; i < masked.pattern.length; i++)
        {
            if (masked.mask[i] && data[offset + i] != masked.pattern[i]) {
                return false;
            }
        }

        return true;
    }

    private static int findPatternInArray(byte[] data, int start, int length, byte[] pattern)
    {
        int end = Math.min(start + length, data.length);

        for (int i = start; i <= end - pattern.length; i++)
        {
            if (matchesPattern(data, i, pattern)) {
                return i;
            }
        }

        return -1;
    }

    private static byte[] readContext(RandomAccessFile raf, long offset, int patternLength,
                                      int before, int after) throws IOException
    {
        long startPos = Math.max(0, offset - before);
        long endPos = offset + patternLength + after;

        int contextSize = (int) (endPos - startPos);
        byte[] context = new byte[contextSize];

        long savedPos = raf.getFilePointer();
        raf.seek(startPos);
        raf.read(context);
        raf.seek(savedPos);

        return context;
    }
}
