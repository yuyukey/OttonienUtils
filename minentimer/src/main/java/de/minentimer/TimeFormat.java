package de.minentimer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeFormat {
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

	private TimeFormat() {
	}

	/** 1:23:45 bzw. 04:12 */
	public static String duration(long ms) {
		long s = Math.max(0, (ms + 999) / 1000);
		long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
		return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%02d:%02d", m, sec);
	}

	public static String clock(long epochMs) {
		return CLOCK.format(Instant.ofEpochMilli(epochMs));
	}

	/** 2 h, 1,5 h, 45 min */
	public static String minutes(int minutes) {
		if (minutes % 60 == 0) return (minutes / 60) + " h";
		if (minutes > 60 && minutes % 30 == 0) return (minutes / 60) + ",5 h";
		return minutes + " min";
	}
}
