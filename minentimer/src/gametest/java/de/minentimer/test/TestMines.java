package de.minentimer.test;

import de.minentimer.Mine;
import de.minentimer.MineSize;
import de.minentimer.ResourceType;

import java.util.List;

/** Minen, die der Test zu Beginn anlegt (früher die Beispiel-Minen der Mod). */
final class TestMines {
	private TestMines() {
	}

	static List<Mine> create() {
		return List.of(
				new Mine("eisenmine", "Eisenmine", 264, 90, 1114, MineSize.MINE, ResourceType.EISEN),
				new Mine("eisen_vorkommen_1", "Eisen Vorkommen 1", 11, 58, 1358, MineSize.VORKOMMEN, ResourceType.EISEN),
				new Mine("eisen_vorkommen_2", "Eisen Vorkommen 2", 622, 84, 1423, MineSize.VORKOMMEN, ResourceType.EISEN),
				new Mine("kohle_vorkommen_1", "Kohle Vorkommen 1", 272, 108, 1091, MineSize.VORKOMMEN, ResourceType.KOHLE),
				new Mine("kohle_vorkommen_2", "Kohle Vorkommen 2", 135, 65, 1616, MineSize.VORKOMMEN, ResourceType.KOHLE),
				new Mine("salz_vorkommen_1", "Salz Vorkommen 1", 351, 58, 1860, MineSize.VORKOMMEN, ResourceType.SALZ),
				new Mine("salz_vorkommen_2", "Salz Vorkommen 2", 641, 94, 1266, MineSize.VORKOMMEN, ResourceType.SALZ),
				new Mine("ton_vorkommen", "Ton Vorkommen", 595, 64, 1663, MineSize.VORKOMMEN, ResourceType.TON));
	}
}
