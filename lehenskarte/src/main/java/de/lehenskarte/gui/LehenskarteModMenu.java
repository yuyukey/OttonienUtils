package de.lehenskarte.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Einstellungs-Knopf in Mod Menu. Wird nur geladen, wenn Mod Menu installiert ist. */
public class LehenskarteModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SettingsScreen::new;
	}
}
