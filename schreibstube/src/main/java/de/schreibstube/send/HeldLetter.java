package de.schreibstube.send;

import de.schreibstube.Config;
import de.schreibstube.text.RichText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Was man in der Hand hält: frisches Schreibzeug, ein angefangener Brief oder ein Buch zum Übernehmen. */
public final class HeldLetter {
	private HeldLetter() {
	}

	/** Pergament und Feder: Item mit passendem Namen (auf Ottonien eine Phantomhaut). */
	public static boolean isFresh(ItemStack s) {
		if (s.isEmpty() || isLetter(s)) return false;
		String name = ChatFormatting.stripFormatting(s.getHoverName().getString());
		return name != null && name.toLowerCase(Locale.ROOT).contains(Config.get().itemName.toLowerCase(Locale.ROOT).strip());
	}

	/**
	 * Ein Brief des Servers: ein beschriebenes Buch mit dem Titel „§7Brief von §6Name“.
	 * Daraus wird er beim ersten /letter.
	 */
	public static boolean isLetter(ItemStack s) {
		if (s.isEmpty() || !s.is(Items.WRITTEN_BOOK)) return false;
		WrittenBookContent book = s.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (book == null) return false;
		String title = ChatFormatting.stripFormatting(book.title().raw());
		return title != null && title.contains("Brief von");
	}

	/**
	 * Ein angefangener Brief, an den /letter weiter anhängt: von einem selbst und noch ohne Empfänger „Zu …“.
	 * Erhaltene Briefe haben nicht immer ein „Zu …“, darum zählt auch der Absender im Titel.
	 */
	public static boolean isStarted(ItemStack s) {
		if (!isLetter(s) || !isFromMe(s)) return false;
		ItemLore lore = s.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				String l = ChatFormatting.stripFormatting(line.getString());
				if (l != null && l.startsWith("Zu")) return false;
			}
		}
		return true;
	}

	/** Absender aus „Brief von Name“ (sonst der Autor des Buchs) ist der eigene Spieler. */
	private static boolean isFromMe(ItemStack s) {
		var player = net.minecraft.client.Minecraft.getInstance().player;
		WrittenBookContent book = s.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (player == null || book == null) return true;
		String title = ChatFormatting.stripFormatting(book.title().raw());
		int at = title == null ? -1 : title.indexOf("Brief von");
		String sender = at >= 0 ? title.substring(at + "Brief von".length()).strip() : book.author();
		if (sender.isEmpty()) sender = book.author();
		return sender.equalsIgnoreCase(player.getGameProfile().name());
	}

	public static boolean isWritable(ItemStack s) {
		return isFresh(s) || isStarted(s);
	}

	/** Gleiches Schreibzeug (Item, Name, Beschreibung) wie in der Hand. */
	private static boolean sameSupply(ItemStack a, ItemStack b) {
		return !a.isEmpty() && a.getItem() == b.getItem() && a.getHoverName().getString().equals(b.getHoverName().getString())
				&& java.util.Objects.equals(a.get(DataComponents.LORE), b.get(DataComponents.LORE));
	}

	/** Fehlermeldung, wenn das Item in der Hand nicht beschrieben werden kann, sonst null. */
	public static String check(ItemStack s) {
		if (!isWritable(s)) return "Halte „" + Config.get().itemName + "“ oder deinen angefangenen Brief in der Haupthand.";
		if (s.getCount() > 1) return "Halte genau ein „" + Config.get().itemName + "“ in der Hand, nicht " + s.getCount() + ".";
		return null;
	}

	/**
	 * Wie {@link #check}, zusätzlich: Der Server beschreibt immer das erste Schreibzeug von links.
	 * Liegt links vom gewählten Platz noch eins, würde er das nehmen statt das in der Hand.
	 */
	public static String checkInventory(net.minecraft.world.entity.player.Player player) {
		ItemStack hand = player.getMainHandItem();
		String err = check(hand);
		if (err != null || !isFresh(hand)) return err;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getSelectedSlot(); i++) {
			if (sameSupply(inv.getItem(i), hand)) {
				return "In Hotbar-Platz " + (i + 1) + " liegt noch „" + Config.get().itemName
						+ "“. Der Server beschreibt immer das erste von links – nimm dieses in die Hand oder leg es weiter nach rechts.";
			}
		}
		return null;
	}

	/** Seiten, die schon im Brief stehen (§-Codes), damit die Vorschau dort weitermacht. */
	public static List<String> existingPages(ItemStack s) {
		List<String> out = new ArrayList<>();
		if (!isStarted(s)) return out;
		WrittenBookContent book = s.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (book == null) return out;
		for (Component page : book.getPages(false)) out.add(toRich(page).toLegacy('§'));
		return out;
	}

	/** Text aus einem Buch, Buch und Feder oder Brief in der Hand, Seiten mit Seitenumbruch getrennt. */
	public static Optional<RichText> importable(ItemStack s) {
		if (s.isEmpty()) return Optional.empty();
		List<RichText> pages = new ArrayList<>();
		WrittenBookContent written = s.get(DataComponents.WRITTEN_BOOK_CONTENT);
		WritableBookContent writable = s.get(DataComponents.WRITABLE_BOOK_CONTENT);
		if (written != null) {
			for (Component page : written.getPages(false)) pages.add(toRich(page));
		} else if (writable != null) {
			for (Filterable<String> page : writable.pages()) pages.add(RichText.parseLegacy(page.raw(), false));
		}
		if (pages.isEmpty()) return Optional.empty();
		RichText out = new RichText();
		for (int i = 0; i < pages.size(); i++) {
			if (i > 0) out.append(RichText.PAGE_BREAK, 0);
			RichText p = pages.get(i);
			// Leerzeilen am Seitenende weglassen
			int end = p.length();
			while (end > 0 && (p.charAt(end - 1) == '\n' || p.charAt(end - 1) == ' ')) end--;
			out.append(p.sub(0, end));
		}
		return Optional.of(out);
	}

	public static RichText toRich(Component c) {
		RichText t = new RichText();
		c.visit((style, text) -> {
			// §-Codes im Text selbst (manche Plugins schreiben so) mit übernehmen
			RichText part = RichText.parseLegacy(text, false);
			int base = styleOf(style);
			for (int i = 0; i < part.length(); i++) {
				int ps = part.styleAt(i);
				t.append(part.charAt(i), ps != 0 ? ps : base);
			}
			return Optional.empty();
		}, Style.EMPTY);
		return t;
	}

	private static int styleOf(Style st) {
		int s = 0;
		TextColor color = st.getColor();
		if (color != null) {
			ChatFormatting f = nearest(color.getValue());
			if (f != null) s = RichText.colorOf(f.getId());
		}
		if (st.isBold()) s |= RichText.BOLD;
		if (st.isItalic()) s |= RichText.ITALIC;
		if (st.isUnderlined()) s |= RichText.UNDERLINE;
		if (st.isStrikethrough()) s |= RichText.STRIKE;
		if (st.isObfuscated()) s |= RichText.OBFUSCATED;
		return s;
	}

	private static ChatFormatting nearest(int rgb) {
		ChatFormatting best = null;
		long bestD = Long.MAX_VALUE;
		for (ChatFormatting f : ChatFormatting.values()) {
			if (!f.isColor() || f.getColor() == null) continue;
			int c = f.getColor();
			long dr = ((c >> 16) & 255) - ((rgb >> 16) & 255), dg = ((c >> 8) & 255) - ((rgb >> 8) & 255), db = (c & 255) - (rgb & 255);
			long d = dr * dr + dg * dg + db * db;
			if (d < bestD) {
				bestD = d;
				best = f;
			}
		}
		return best;
	}
}
