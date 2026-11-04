package de.lehenskarte.gui;

/** Für Tests: Kartenausschnitt in Xaero's World Map setzen und den tatsächlich gezeigten Ausschnitt ablesen. */
public final class MapDebug {
	/** Gewünschter Ausschnitt {x, z, scale} oder null. */
	public static volatile double[] request;
	/** Zuletzt gezeichneter Ausschnitt: Kartenmitte und echte Pixel pro Block. */
	public static volatile double cameraX, cameraZ, scale;

	private MapDebug() {
	}
}
