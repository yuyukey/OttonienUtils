package de.schreibstube.test;

import de.schreibstube.SchreibstubeClient;
import de.schreibstube.gui.LetterScreen;
import de.schreibstube.gui.RecipientScreen;
import de.schreibstube.gui.SettingsScreen;
import de.schreibstube.send.HeldLetter;
import de.schreibstube.send.LetterSender;
import de.schreibstube.text.Courier;
import de.schreibstube.text.RichText;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Prüft die Nachbildung des Brief-Plugins und macht Screenshots der Schreibstube. */
public class SchreibstubeClientTest implements FabricClientGameTest {
	static final String LETTER = "§6§lAn den edlen Herrn von Geldern§r\n\n"
			+ "Werter Freund, mit großer Freude habe ich Euren letzten Brief empfangen. Die Ernte in §2Merdorf§r war in diesem Jahr "
			+ "reichlich, und die Händler aus dem Süden bringen §oneue Stoffe§r und Gewürze auf den Markt. Ich lade Euch herzlich ein, "
			+ "zum §nErntefest§r am nächsten Sonntag zu kommen. Es wird Musik, Wein und ein großes Turnier geben.\n\n"
			+ "Bitte gebt mir bis Freitag Bescheid, ob Ihr kommen könnt, damit ich ein Zimmer im Gasthaus richten lasse.\n"
			+ "\f§lNachtrag:§r Bringt bitte das Banner Eures Hauses mit.\n\n§oHochachtungsvoll,§r\n§lyuyukey";

	@Override
	public void runTest(ClientGameTestContext ctx) {
		checkCourier();

		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:phantom_membrane[minecraft:custom_name='Pergament und Feder']");
			world.getClientWorld().waitForChunksRender();

			// Schreibzeug weiter links: Server würde das nehmen, also warnen
			String supply = "minecraft:phantom_membrane[minecraft:custom_name='Pergament und Feder']";
			ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(4));
			world.getServer().runCommand("item replace entity @a hotbar.4 with " + supply);
			ctx.waitTicks(3);
			ctx.runOnClient(mc -> {
				String err = HeldLetter.checkInventory(mc.player);
				if (err == null || !err.contains("Hotbar-Platz 1")) throw new AssertionError("Pergament links nicht erkannt: " + err);
			});
			world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:air");
			ctx.waitTicks(3);
			ctx.runOnClient(mc -> {
				String err = HeldLetter.checkInventory(mc.player);
				if (err != null) throw new AssertionError("Pergament in der Hand abgelehnt: " + err);
				if (LetterSender.start(List.of("Hallo")) != null) throw new AssertionError("Start abgelehnt");
			});
			// So wie der Server (oder Mouse Wheelie): der Brief landet im Inventar, nicht in der Hand
			ctx.waitTicks(10);
			world.getServer().runCommand("item replace entity @a inventory.3 with minecraft:written_book[minecraft:written_book_content="
					+ "{title:\"§7Brief von §6Player0\",author:\"Player0\",pages:[\"Hallo\"]}]");
			ctx.waitFor(mc -> HeldLetter.isStarted(mc.player.getMainHandItem()), 200);
			ctx.waitFor(mc -> !LetterSender.busy(), 200);
			ctx.runOnClient(mc -> {
				if (!HeldLetter.isStarted(mc.player.getMainHandItem())) throw new AssertionError("Brief nicht zurück in der Hand");
				if (!HeldLetter.isFresh(mc.player.getInventory().getItem(12))) throw new AssertionError("Pergament nicht getauscht");
				if (!HeldLetter.existingPages(mc.player.getMainHandItem()).equals(List.of("Hallo"))) throw new AssertionError("Seiten des Briefs");
				mc.setScreen(null);
			});
			ctx.waitTicks(3);

			// Mouse Wheelie: Server tauscht Pergament gegen Brief in zwei Schritten, 55 Pergamente im Inventar
			mouseWheelie(ctx, world, supply, false);
			mouseWheelie(ctx, world, supply, true);

			// Erhaltenen Brief lesen und mit einem Klick in den Editor holen; der alte Entwurf wird gesichert
			ctx.runOnClient(mc -> de.schreibstube.Drafts.saveCurrent(RichText.parseLegacy("Mein Entwurf", false)));
			world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:written_book[minecraft:written_book_content="
					+ "{title:\"§7Brief von §6Kamerad\",author:\"Kamerad\",pages:[{text:\"Liebe Grüße\",color:\"dark_red\"},\"Seite zwei\"]}]");
			ctx.waitTicks(5);
			ctx.runOnClient(mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.BookViewScreen(
					net.minecraft.client.gui.screens.inventory.BookViewScreen.BookAccess.fromItem(mc.player.getMainHandItem()))));
			ctx.waitTicks(5);
			ctx.takeScreenshot("schreibstube-brief-erhalten");
			ctx.runOnClient(mc -> {
				var button = mc.screen.children().stream()
						.filter(w -> w instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().contains("Im Editor öffnen"))
						.map(w -> (net.minecraft.client.gui.components.Button) w).findFirst()
						.orElseThrow(() -> new AssertionError("Knopf „Im Editor öffnen“ fehlt"));
				if (mc.screen.children().stream().anyMatch(w -> w instanceof net.minecraft.client.gui.components.Button b
						&& b.getMessage().getString().contains("Verschicken"))) throw new AssertionError("Verschicken-Knopf bei erhaltenem Brief");
				button.onPress(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
			});
			ctx.waitTicks(5);
			ctx.takeScreenshot("schreibstube-brief-im-editor");
			ctx.runOnClient(mc -> {
				if (!(mc.screen instanceof LetterScreen s)) throw new AssertionError("Editor nicht offen: " + mc.screen);
				RichText t = s.editor().text();
				if (!t.plain().equals("Liebe Grüße" + RichText.PAGE_BREAK + "Seite zwei")) throw new AssertionError("Brieftext: " + t.plain());
				if (RichText.color(t.styleAt(0)) != RichText.colorOf(4)) throw new AssertionError("Farbe verloren: " + t.toLegacy('&'));
				boolean backup = de.schreibstube.Drafts.list().stream().anyMatch(e -> e.name().startsWith("Entwurf vor Brief") && e.preview().equals("Mein Entwurf"));
				if (!backup) throw new AssertionError("Alter Entwurf nicht gesichert: " + de.schreibstube.Drafts.list());
				s.editor().setText(new RichText(), false);
				mc.setScreen(null);
			});
			world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
			ctx.waitTicks(3);

			// Vorschau: Formatierung darf beim Zeilenumbruch nicht verrutschen
			ctx.runOnClient(mc -> {
				String page = "§9§l§oSchreiben klicken und den Brief danach mit Rechtsklick öffnen. Achte §rdabei auf drei Dinge:";
				var lines = mc.font.split(RichText.parseLegacy(page, false).toComponent(true), 114);
				StringBuilder bold = new StringBuilder();
				for (var l : lines) l.accept((i, st, cp) -> {
					if (st.isBold()) bold.appendCodePoint(cp);
					return true;
				});
				if (bold.toString().contains("dabei")) throw new AssertionError("Vorschau verrutscht: " + bold);
			});
			ctx.runOnClient(mc -> mc.setScreen(new LetterScreen(null)));
			ctx.waitTicks(5);
			ctx.runOnClient(mc -> {
				LetterScreen s = (LetterScreen) mc.screen;
				s.editor().setText(RichText.parseLegacy(LETTER, false), false);
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("schreibstube-editor");

			// Tippen, markieren und formatieren wie ein Spieler
			ctx.runOnClient(mc -> ((LetterScreen) mc.screen).editor().setText(new RichText(), false));
			ctx.getInput().typeChars("Hallo Welt");
			ctx.runOnClient(mc -> {
				var ed = ((LetterScreen) mc.screen).editor();
				if (!ed.text().plain().equals("Hallo Welt")) throw new AssertionError("Getippt: " + ed.text().plain());
			});
			// Das Test-Framework schickt keine Strg/Umschalt-Modifikatoren, deshalb direkt
			ctx.runOnClient(mc -> {
				var ed = ((LetterScreen) mc.screen).editor();
				ed.select(10, 6);
				ed.toggleFlag(RichText.BOLD);
				ed.copy(false);
				ed.select(10, 10);
				ed.paste();
			});
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> {
				var ed = ((LetterScreen) mc.screen).editor();
				RichText t = ed.text();
				if (!t.plain().equals("Hallo WeltWelt")) throw new AssertionError("Einfügen: " + t.plain());
				if ((t.styleAt(6) & RichText.BOLD) == 0 || (t.styleAt(5) & RichText.BOLD) != 0 || (t.styleAt(12) & RichText.BOLD) == 0)
					throw new AssertionError("Fett falsch: " + t.toLegacy('&'));
				if (!mc.keyboardHandler.getClipboard().equals("&lWelt")) throw new AssertionError("Zwischenablage: " + mc.keyboardHandler.getClipboard());
			});
			ctx.runOnClient(mc -> {
				var ed = ((LetterScreen) mc.screen).editor();
				ed.undo();
				if (!ed.text().plain().equals("Hallo Welt")) throw new AssertionError("Rückgängig: " + ed.text().plain());
				ed.select(0, 5);
				ed.setColor(RichText.colorOf(4));
				ed.select(0, 10);
			});
			ctx.waitTicks(2);
			ctx.takeScreenshot("schreibstube-markiert");

			// Langer Brief über mehrere Seiten, letzte Seite in der Vorschau
			ctx.runOnClient(mc -> {
				LetterScreen s = (LetterScreen) mc.screen;
				StringBuilder sb = new StringBuilder();
				for (int i = 1; i <= 6; i++) sb.append("§").append("0123456789abcdef".charAt(i)).append("Absatz ").append(i)
						.append(":§r Dies ist ein etwas längerer Absatz, damit der Brief über mehrere Seiten geht und man die Vorschau sieht.\n");
				s.editor().setText(RichText.parseLegacy(sb.toString(), false), false);
				Courier.Plan p = s.plan();
				SchreibstubeClient.LOGGER.info("[Test] Langer Brief: {} Seiten, {} Befehle", p.pages().size(), p.commands().size());
				for (String c : p.commands()) if (c.length() > Courier.MAX_PAYLOAD) throw new AssertionError("Befehl zu lang: " + c.length());
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("schreibstube-lang");

			ctx.runOnClient(mc -> mc.setScreen(new RecipientScreen(null, null)));
			ctx.waitTicks(5);
			ctx.takeScreenshot("schreibstube-empfaenger");
			ctx.runOnClient(mc -> mc.setScreen(new SettingsScreen(null)));
			ctx.waitTicks(5);
			ctx.takeScreenshot("schreibstube-einstellungen");
			ctx.runOnClient(mc -> mc.setScreen(new LetterScreen(null).openingDrafts()));
			ctx.waitTicks(5);
			ctx.takeScreenshot("schreibstube-entwuerfe");
			ctx.runOnClient(mc -> mc.setScreen(null));
			ctx.waitTicks(2);
		}
	}

	/** Spielt nach, was der Server beim ersten /letter tut. Mit {@code writing} läuft dabei die Schreibstube. */
	private static void mouseWheelie(ClientGameTestContext ctx, TestSingleplayerContext world, String supply, boolean writing) {
		boolean present = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mousewheelie");
		SchreibstubeClient.LOGGER.info("[Test] Mouse Wheelie geladen: {}, Schreibstube aktiv: {}", present, writing);
		if (!present) return;
		world.getServer().runCommand("clear @a");
		world.getServer().runCommand("item replace entity @a hotbar.4 with " + supply);
		world.getServer().runCommand("item replace entity @a inventory.5 with " + supply.replace("]", "]") + " 55");
		ctx.waitTicks(5);
		if (writing) ctx.runOnClient(mc -> {
			if (LetterSender.start(List.of("Hallo", "Welt")) != null) throw new AssertionError("Start abgelehnt");
			if (refillEnabled()) throw new AssertionError("Mouse Wheelie füllt beim Schreiben weiter nach");
		});
		ctx.waitTicks(3);
		String letter = "minecraft:written_book[minecraft:written_book_content={title:\"§7Brief von §6Player0\",author:\"Player0\",pages:[\"Hallo\"]}]";
		world.getServer().runCommand("item replace entity @a hotbar.4 with minecraft:air");
		ctx.waitTicks(1);
		world.getServer().runCommand("item replace entity @a hotbar.4 with " + letter);
		ctx.waitTicks(40);
		if (writing) ctx.waitFor(mc -> !LetterSender.busy(), 200);
		ctx.runOnClient(mc -> {
			var carried = mc.player.inventoryMenu.getCarried();
			var hand = mc.player.getMainHandItem();
			SchreibstubeClient.LOGGER.info("[Test] Mouse Wheelie ({}): Hand={} x{}, Mauszeiger={} x{}", writing ? "mit Schreibstube" : "ohne",
					hand.getItem(), hand.getCount(), carried.getItem(), carried.getCount());
			if (writing && !HeldLetter.isStarted(hand)) throw new AssertionError("Brief nicht in der Hand: " + hand + ", Zeiger " + carried);
			if (writing && !carried.isEmpty()) throw new AssertionError("Etwas hängt am Mauszeiger: " + carried);
			if (!refillEnabled()) throw new AssertionError("Nachfüllen von Mouse Wheelie nicht wieder an");
		});
		world.getServer().runCommand("clear @a");
		ctx.waitTicks(3);
	}

	private static boolean refillEnabled() {
		try {
			Object cfg = Class.forName("de.siphalor.mousewheelie.MouseWheelie").getField("config").get(null);
			Object refill = cfg.getClass().getField("refill").get(cfg);
			return refill.getClass().getField("enable").getBoolean(refill);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static void checkCourier() {
		// Server: Leerzeichen zusammenfassen, \n, &-Codes
		List<String> pages = new ArrayList<>();
		Courier.receive(pages, "Hallo   &lWelt\\nZweite Zeile");
		if (!pages.equals(List.of("Hallo §lWelt\nZweite Zeile "))) throw new AssertionError("Server-Nachbildung: " + pages);

		// Jeder Befehl beginnt eine neue Zeile, die Seite läuft bei 13 Zeilen über
		pages.clear();
		for (int i = 0; i < 14; i++) Courier.receive(pages, "Zeile " + i);
		if (pages.size() != 2) throw new AssertionError("Seitenwechsel: " + pages);

		// Planung: Ergebnis muss dem Text entsprechen
		RichText doc = RichText.parseLegacy(LETTER, false);
		Courier.Plan plan = Courier.plan(doc, List.of());
		String visible = String.join(" ", plan.pages()).replaceAll("§.", "").replaceAll("\\s+", " ").strip();
		String expected = doc.plain().replace(RichText.PAGE_BREAK, ' ').replaceAll("\\s+", " ").strip();
		if (!visible.equals(expected)) throw new AssertionError("Text verändert:\n" + visible + "\n" + expected);
		for (String c : plan.commands()) if (c.length() > Courier.MAX_PAYLOAD) throw new AssertionError("Befehl zu lang: " + c);
		// Seitenumbruch: Nachtrag steht oben auf einer eigenen Seite
		boolean found = false;
		for (String p : plan.pages()) if (p.replaceAll("§.", "").startsWith("Nachtrag:")) found = true;
		if (!found) throw new AssertionError("Seitenumbruch fehlt: " + plan.pages());
		// Formatierung läuft über einen geteilten Absatz weiter
		StringBuilder sb = new StringBuilder("§l");
		for (int i = 0; i < 60; i++) sb.append("Wort").append(i).append(' ');
		Courier.Plan bold = Courier.plan(RichText.parseLegacy(sb.toString(), false), List.of());
		for (String p : bold.pages()) if (!p.startsWith("§l") && !p.startsWith("§r§l")) throw new AssertionError("Fett geht verloren: " + bold.pages());
		// Seitenanfänge im Text: Seite 2 beginnt mit einem Wort, genau dort, wo der Server umbricht
		if (bold.pageStarts().size() != bold.pages().size() - 1) throw new AssertionError("Seitenanfänge: " + bold.pageStarts());
		String boldPlain = RichText.parseLegacy(sb.toString(), false).plain();
		for (var e : bold.pageStarts().entrySet()) {
			String page = bold.pages().get(e.getValue()).replaceAll("§.", "");
			if (!boldPlain.startsWith(page.substring(0, 6), e.getKey())) throw new AssertionError("Seite " + e.getValue() + " beginnt nicht bei " + e.getKey());
		}
		Integer nachtrag = null;
		for (var e : plan.pageStarts().entrySet()) if (doc.plain().startsWith("Nachtrag", e.getKey())) nachtrag = e.getValue();
		if (nachtrag == null) throw new AssertionError("Seitenumbruch vor Nachtrag nicht markiert: " + plan.pageStarts());
		SchreibstubeClient.LOGGER.info("[Test] Brief: {} Seiten, {} Befehle: {}", plan.pages().size(), plan.commands().size(), plan.commands());
	}
}
