package de.minentimer;

import com.google.gson.annotations.SerializedName;

/** Rohstoffarten mit ihren Respawn-Zeiten (Vorkommen / Mine). */
public enum ResourceType {
	EISEN("Eisenerz", 0xFFD8AF93, 120, 240),
	KOHLE("Steinkohle", 0xFF9A9A9A, 120, 240),
	/** Früher zwei Sorten (Lapis-/Kupfererz), alte Daten werden übernommen. */
	@SerializedName(value = "SALZ", alternate = {"SALZ_LAPIS", "SALZ_KUPFER"})
	SALZ("Salz", 0xFFEDEDED, 120, 240),
	TORF("Torf", 0xFF9C7350, 240, 240),
	TON("Ton", 0xFFAEB4C8, 60, 60);

	public final String displayName;
	public final int color;
	public final int vorkommenMinutes;
	public final int mineMinutes;

	ResourceType(String displayName, int color, int vorkommenMinutes, int mineMinutes) {
		this.displayName = displayName;
		this.color = color;
		this.vorkommenMinutes = vorkommenMinutes;
		this.mineMinutes = mineMinutes;
	}

	public int minutes(MineSize size) {
		return size == MineSize.MINE ? mineMinutes : vorkommenMinutes;
	}
}
