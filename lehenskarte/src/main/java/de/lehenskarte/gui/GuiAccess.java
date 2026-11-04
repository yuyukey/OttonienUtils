package de.lehenskarte.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;

import java.lang.reflect.Field;

/** Zugriff auf den Zeichenzustand von GuiGraphics, um eigene Vierecke (Linienzüge mit Gehrung) einzureihen. */
final class GuiAccess {
	private static Field field;

	private GuiAccess() {
	}

	static GuiRenderState state(GuiGraphics g) {
		try {
			if (field == null) {
				for (Field f : GuiGraphics.class.getDeclaredFields()) {
					if (f.getType() == GuiRenderState.class) {
						f.setAccessible(true);
						field = f;
						break;
					}
				}
			}
			return (GuiRenderState) field.get(g);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
