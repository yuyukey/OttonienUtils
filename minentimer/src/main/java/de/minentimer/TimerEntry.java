package de.minentimer;

/** Zustand eines Respawn-Timers. readyAt = 0 bedeutet bereit/zurückgesetzt. */
public class TimerEntry {
	public long readyAt;
	public long startedAt;
	public long updatedAt;
	public String by = "";

	public boolean isRunning(long now) {
		return readyAt > now;
	}
}
