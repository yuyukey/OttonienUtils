package de.minentimer;

/** Größe eines Abbaugebiets: kleines Vorkommen oder große Mine (längere Respawn-Zeit). */
public enum MineSize {
	VORKOMMEN("Vorkommen"), MINE("Mine");

	public final String label;

	MineSize(String label) {
		this.label = label;
	}
}
