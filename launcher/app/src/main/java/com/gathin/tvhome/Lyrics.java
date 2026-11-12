package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Synced lyrics for the Now Playing panel's lyrics card (config lyrics): lines with the time each one starts, read
 * from LRC text ("[01:23.45] a line"), the format LRCLIB returns as syncedLyrics.
 */
final class Lyrics {
    final long[] times;
    final String[] lines;

    private Lyrics(long[] times, String[] lines) {
        this.times = times;
        this.lines = lines;
    }

    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern OFFSET = Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE);

    /**
     * Parses LRC: one or more time stamps in front of each line, an optional [offset:ms] tag; other tags and lines
     * without text (instrumental breaks) are left out. Null when no timed line has text.
     */
    static Lyrics parseLrc(String lrc) {
        if (lrc == null) return null;
        long offset = 0;
        List<long[]> stamps = new ArrayList<>();   // {time, index into texts}
        List<String> texts = new ArrayList<>();
        for (String raw : lrc.split("\\r?\\n")) {
            String line = raw.trim();
            Matcher off = OFFSET.matcher(line);
            if (off.find()) {
                offset = Long.parseLong(off.group(1));
                continue;
            }
            Matcher m = STAMP.matcher(line);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                long min = Long.parseLong(m.group(1)), sec = Long.parseLong(m.group(2));
                String frac = m.group(3);
                long ms = frac == null ? 0 : Long.parseLong((frac + "00").substring(0, 3));
                times.add(min * 60_000 + sec * 1000 + ms);
                end = m.end();
            }
            if (times.isEmpty()) continue;
            String text = line.substring(end).trim();
            if (text.isEmpty()) continue;
            texts.add(text);
            for (long t : times) stamps.add(new long[]{t, texts.size() - 1});
        }
        if (stamps.isEmpty()) return null;
        stamps.sort((a, b) -> Long.compare(a[0], b[0]));
        long[] t = new long[stamps.size()];
        String[] l = new String[stamps.size()];
        for (int i = 0; i < t.length; i++) {
            // A positive offset shows the lines earlier, as LRC players do.
            t[i] = Math.max(0, stamps.get(i)[0] - offset);
            l[i] = texts.get((int) stamps.get(i)[1]);
        }
        return new Lyrics(t, l);
    }

    /** The line playing at this position: the last one that has started, or -1 before the first. */
    int indexAt(long positionMs) {
        int i = Arrays.binarySearch(times, positionMs);
        if (i >= 0) {
            while (i + 1 < times.length && times[i + 1] == positionMs) i++;
            return i;
        }
        return -i - 2;
    }

    int size() {
        return lines.length;
    }

    /** When line {@code i} starts, in ms into the song. */
    long time(int i) {
        return times[i];
    }

    String line(int i) {
        return i >= 0 && i < lines.length ? lines[i] : "";
    }
}
