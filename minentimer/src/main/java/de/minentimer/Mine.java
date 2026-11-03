package de.minentimer;

import java.util.List;

/** Eine Mine bzw. ein Vorkommen an einer festen Position mit genau einem Timer. */
public class Mine {
	public String id;
	public String name;
	public int x;
	public int y;
	public int z;
	/** Rohstoff-Art, bestimmt zusammen mit {@link #size} die Respawn-Zeit. Ohne Startwert, damit {@link #normalize()} alte Daten erkennt. */
	public ResourceType type;
	/** Kleines Vorkommen oder große Mine. */
	public MineSize size;
	/** Lehen, zu dem die Mine gehört (frei wählbarer Name), leer = keins. */
	public String area = "";
	/** Eigene Respawn-Zeit in Minuten, 0 = Standard. */
	public int customMinutes;
	/** Zeitpunkt der letzten Änderung (für die Synchronisierung). */
	public long updatedAt;
	/** Gelöscht, bleibt als Markierung für die Synchronisierung erhalten. */
	public boolean deleted;
	/** Ältere Formate, werden beim Laden in {@link #type} bzw. {@link #size} übernommen. */
	public List<ResourceType> resources;
	public Boolean lehen;

	public Mine() {
	}

	public Mine(String id, String name, int x, int y, int z, MineSize size, ResourceType type) {
		this.id = id;
		this.name = name;
		this.x = x;
		this.y = y;
		this.z = z;
		this.size = size;
		this.type = type;
	}

	/** Füllt fehlende Werte auf und übernimmt ältere Formate. */
	public void normalize() {
		if (type == null && resources != null) {
			type = resources.stream().filter(r -> r != null).findFirst().orElse(null);
		}
		if (type == null) type = ResourceType.EISEN;
		if (size == null) size = Boolean.TRUE.equals(lehen) ? MineSize.MINE : MineSize.VORKOMMEN;
		resources = null;
		lehen = null;
		if (name == null) name = id;
		area = area == null ? "" : area.trim();
	}

	public int respawnMinutes() {
		return customMinutes > 0 ? customMinutes : type.minutes(size);
	}

	public double distanceTo(double px, double py, double pz) {
		double dx = x + 0.5 - px, dy = y + 0.5 - py, dz = z + 0.5 - pz;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	public Mine copy() {
		Mine m = new Mine(id, name, x, y, z, size, type);
		m.customMinutes = customMinutes;
		m.area = area;
		m.updatedAt = updatedAt;
		m.deleted = deleted;
		return m;
	}
}
