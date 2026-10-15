package br.com.cronicasdavila.client;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import br.com.cronicasdavila.net.ModPayloads;
import br.com.cronicasdavila.util.Text;

/**
 * Diário de Aventuras: todas as missões aceitas, nível do jogador, karma e afinidade com cada profissão.
 * Abre com a tecla J ou pelo botão "Missões" no inventário.
 */
public class QuestJournalScreen extends Screen {
	private static final int GOLD = 0xFFD36B;
	private static final String[] TABS = {"Missões", "Reputação"};

	private final Screen parent;
	private int tab;
	private int selected;
	private int detailScroll;
	private boolean confirmAbandon;
	private final long openedAt = System.currentTimeMillis();

	public QuestJournalScreen(Screen parent) {
		super(Component.literal("Diário de Aventuras"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	// ---------------------------------------------------------------------------------------------
	// Layout
	// ---------------------------------------------------------------------------------------------

	private int panelX1() {
		return Math.max(10, (this.width - 520) / 2);
	}

	private int panelX2() {
		return this.width - panelX1();
	}

	private int panelY1() {
		return 34;
	}

	private int panelY2() {
		return this.height - 22;
	}

	private int leftWidth() {
		return (int) ((panelX2() - panelX1()) * 0.40);
	}

	private int listTop() {
		return panelY1() + 96;
	}

	private int tabX(int index) {
		return this.width / 2 - 110 + index * 112;
	}

	// ---------------------------------------------------------------------------------------------
	// Entrada
	// ---------------------------------------------------------------------------------------------

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		List<JsonObject> quests = ClientState.quests();

		if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_TAB) {
			tab = 1 - tab;
			return true;
		}

		if (tab == 0 && !quests.isEmpty()) {
			if (key == InputConstants.KEY_UP || key == InputConstants.KEY_W) {
				selected = Math.floorMod(selected - 1, quests.size());
				detailScroll = 0;
				confirmAbandon = false;
				return true;
			}

			if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_S) {
				selected = Math.floorMod(selected + 1, quests.size());
				detailScroll = 0;
				confirmAbandon = false;
				return true;
			}

			if (key == InputConstants.KEY_T || key == InputConstants.KEY_RETURN) {
				track(quests.get(Math.min(selected, quests.size() - 1)));
				return true;
			}
		}

		if (CronicasDaVilaClient.isJournalKey(key)) {
			onClose();
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX > panelX1() + leftWidth()) {
			detailScroll = Math.max(0, detailScroll - (int) Math.signum(scrollY));
		} else {
			List<JsonObject> quests = ClientState.quests();

			if (!quests.isEmpty()) {
				selected = Math.floorMod(selected - (int) Math.signum(scrollY), quests.size());
				detailScroll = 0;
			}
		}

		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mx = (int) event.x();
		int my = (int) event.y();

		for (int i = 0; i < TABS.length; i++) {
			if (mx >= tabX(i) && mx < tabX(i) + 104 && my >= 12 && my < 30) {
				tab = i;
				return true;
			}
		}

		if (tab != 0) {
			return super.mouseClicked(event, doubleClick);
		}

		List<JsonObject> quests = ClientState.quests();

		// Lista de missões
		int x1 = panelX1() + 8;
		int x2 = panelX1() + leftWidth() - 8;

		for (int i = 0; i < quests.size(); i++) {
			int y = listTop() + i * 24;

			if (mx >= x1 && mx < x2 && my >= y && my < y + 22) {
				selected = i;
				detailScroll = 0;
				confirmAbandon = false;
				return true;
			}
		}

		// Botões
		if (!quests.isEmpty()) {
			JsonObject quest = quests.get(Math.min(selected, quests.size() - 1));
			int by = panelY2() - 26;
			int dx = panelX1() + leftWidth() + 12;

			if (my >= by && my < by + 18) {
				if (mx >= dx && mx < dx + 120) {
					track(quest);
					return true;
				}

				if (mx >= dx + 128 && mx < dx + 268) {
					if (confirmAbandon) {
						ClientPlayNetworking.send(new ModPayloads.ActionPayload("abandon:" + quest.get("prof").getAsString()));
						confirmAbandon = false;
						selected = 0;
					} else {
						confirmAbandon = true;
					}

					return true;
				}
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	private void track(JsonObject quest) {
		ClientPlayNetworking.send(new ModPayloads.ActionPayload("track:" + quest.get("prof").getAsString()));
	}

	// ---------------------------------------------------------------------------------------------
	// Desenho
	// ---------------------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, this.width, this.height, Ui.argb(205, 0x050508));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		float fade = Ui.ease((System.currentTimeMillis() - openedAt) / 250F);

		// Abas
		for (int i = 0; i < TABS.length; i++) {
			int x = tabX(i);
			boolean active = i == tab;
			Ui.roundedRect(graphics, x, 12, x + 104, 30, Ui.argb(active ? 230 : 150, active ? 0x2A2416 : 0x111114));
			Ui.roundedOutline(graphics, x, 12, x + 104, 30, Ui.argb(active ? 255 : 60, active ? GOLD : 0xFFFFFF));
			int w = this.font.width(TABS[i]);
			graphics.text(this.font, TABS[i], x + (104 - w) / 2, 17, Ui.argb((int) (255 * fade), active ? GOLD : 0xBBBBBB), true);
		}

		Ui.panel(graphics, panelX1(), panelY1(), panelX2(), panelY2(), fade);

		if (!ClientState.hasData()) {
			centered(graphics, "Carregando seu diário...", (panelY1() + panelY2()) / 2, 0xBBBBBB);
			return;
		}

		if (tab == 0) {
			drawProfile(graphics, panelX1() + 10, panelY1() + 8, leftWidth() - 20);
			drawQuestList(graphics, mouseX, mouseY);
			drawQuestDetails(graphics);
		} else {
			drawReputation(graphics);
		}

		String hint = tab == 0 ? "↑↓ escolher missão   T/Enter rastrear no HUD   ←→ trocar aba   Esc fechar" : "←→ trocar aba   Esc fechar";
		centered(graphics, hint, this.height - 15, 0x888888);
	}

	private void centered(GuiGraphicsExtractor graphics, String text, int y, int color) {
		int w = this.font.width(text);
		graphics.text(this.font, text, (this.width - w) / 2, y, Ui.argb(255, color), false);
	}

	private void drawProfile(GuiGraphicsExtractor graphics, int x, int y, int width) {
		String rankName = ClientState.getString("rankName", "Amador");
		int rank = ClientState.getInt("rank", 1);
		int rankColor = ClientState.getInt("rankColor", 0xFFFFFF);
		int xpLevel = ClientState.getInt("xpLevel", 0);
		int nextXp = ClientState.getInt("nextRankXp", 20);
		String nextName = ClientState.getString("nextRankName", "Aprendiz");

		graphics.text(this.font, Component.literal("★ " + rankName.toUpperCase()).withStyle(style -> style.withBold(true)), x, y, Ui.argb(255, rankColor), true);
		graphics.text(this.font, "Nível " + rank + "/5", x + width - this.font.width("Nível " + rank + "/5"), y, Ui.argb(255, 0xAAAAAA), false);

		if (rank < 5) {
			graphics.text(this.font, "XP: " + xpLevel + " / " + nextXp + " → " + nextName, x, y + 13, Ui.argb(255, 0xDDDDDD), false);
			Ui.bar(graphics, x, y + 24, width, 4, xpLevel / (float) Math.max(1, nextXp), Ui.argb(255, 0x7CFC00));
			int done = ClientState.getInt("tierDone", 0);
			int need = ClientState.getInt("tierNeeded", 5);
			graphics.text(this.font, "Missões: " + done + " / " + need + " (nível permanente)", x, y + 32, Ui.argb(255, 0xDDDDDD), false);
			Ui.bar(graphics, x, y + 43, width, 4, done / (float) Math.max(1, need), Ui.argb(255, GOLD));
		} else {
			graphics.text(this.font, "Você chegou ao topo. Lendas falam de você.", x, y + 13, Ui.argb(255, GOLD), false);
		}

		int karma = ClientState.getInt("karma", 0);
		int karmaColor = ClientState.getInt("karmaColor", 0xFFFFFF);
		String karmaTier = ClientState.getString("karmaTier", "Neutro");
		graphics.text(this.font, "Karma: " + karmaTier + " (" + karma + ")", x, y + 53, Ui.argb(255, karmaColor), true);

		// Barra de karma de -1000 a +1000, com o centro marcado
		int barY = y + 65;
		graphics.fill(x, barY, x + width / 2, barY + 5, Ui.argb(200, 0x5A1010));
		graphics.fill(x + width / 2, barY, x + width, barY + 5, Ui.argb(200, 0x104A1A));
		int marker = x + (int) ((karma + 1000) / 2000F * width);
		graphics.fill(marker - 1, barY - 2, marker + 2, barY + 7, Ui.argb(255, 0xFFFFFF));
		graphics.fill(x + width / 2, barY - 1, x + width / 2 + 1, barY + 6, Ui.argb(160, 0xFFFFFF));
	}

	private void drawQuestList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		List<JsonObject> quests = ClientState.quests();
		int x1 = panelX1() + 8;
		int x2 = panelX1() + leftWidth() - 8;
		graphics.text(this.font, "MISSÕES ATIVAS (" + quests.size() + ")", x1, listTop() - 12, Ui.argb(255, GOLD), true);

		if (quests.isEmpty()) {
			graphics.text(this.font, "Nenhuma missão.", x1, listTop() + 4, Ui.argb(255, 0x999999), false);
			graphics.text(this.font, "Converse com aldeões e piglins!", x1, listTop() + 16, Ui.argb(255, 0x999999), false);
			return;
		}

		selected = Math.min(selected, quests.size() - 1);
		String tracked = ClientState.getString("tracked", "");

		for (int i = 0; i < quests.size(); i++) {
			JsonObject quest = quests.get(i);
			int y = listTop() + i * 24;

			if (y + 22 > panelY2() - 4) {
				break;
			}

			boolean isSelected = i == selected;
			boolean hover = mouseX >= x1 && mouseX < x2 && mouseY >= y && mouseY < y + 22;
			int bg = isSelected ? Ui.argb(230, 0x2A2416) : Ui.argb(hover ? 170 : 120, 0x16161C);
			Ui.roundedRect(graphics, x1, y, x2, y + 22, bg);

			if (isSelected) {
				Ui.roundedOutline(graphics, x1, y, x2, y + 22, Ui.argb(255, GOLD));
			}

			boolean done = ClientState.num(quest, "progress", 0) >= ClientState.num(quest, "target", 1);
			String title = ClientState.str(quest, "title", "?");
			String prefix = quest.get("prof").getAsString().equals(tracked) ? "◆ " : "";
			int titleColor = done ? 0x8CFF8C : ("danger".equals(ClientState.str(quest, "tone", "")) ? 0xFF8080 : 0xFFFFFF);
			graphics.text(this.font, trim(prefix + title, x2 - x1 - 10), x1 + 6, y + 3, Ui.argb(255, titleColor), true);
			String sub = ClientState.str(quest, "tierName", "") + " · " + ClientState.str(quest, "profName", "") + (done ? " · PRONTA!" : "");
			graphics.text(this.font, trim(sub, x2 - x1 - 10), x1 + 6, y + 12, Ui.argb(255, done ? 0x8CFF8C : 0x999999), false);
		}
	}

	private String trim(String text, int width) {
		if (this.font.width(text) <= width) {
			return text;
		}

		String result = text;

		while (result.length() > 1 && this.font.width(result + "…") > width) {
			result = result.substring(0, result.length() - 1);
		}

		return result + "…";
	}

	private void drawQuestDetails(GuiGraphicsExtractor graphics) {
		List<JsonObject> quests = ClientState.quests();
		int x = panelX1() + leftWidth() + 12;
		int right = panelX2() - 12;
		int width = right - x;
		int top = panelY1() + 10;
		int bottom = panelY2() - 32;

		graphics.fill(x - 6, panelY1() + 8, x - 5, panelY2() - 8, Ui.argb(60, 0xFFFFFF));

		if (quests.isEmpty()) {
			List<FormattedCharSequence> intro = this.font.split(Text.parse(
					"Clique com o <y>botão direito</y> em um aldeão (ou em um piglin, se estiver usando <o>ouro</o>) para conversar. "
							+ "Cada profissão tem suas próprias histórias e missões, de <d>Amador</d> até <o>Mestre</o>.\n\n"
							+ "<d>Dica: Shift + botão direito abre as trocas direto.</d>"), width);
			int y = top + 4;

			for (FormattedCharSequence line : intro) {
				graphics.text(this.font, line, x, y, Ui.argb(255, 0xDDDDDD), false);
				y += 11;
			}

			return;
		}

		JsonObject quest = quests.get(Math.min(selected, quests.size() - 1));
		boolean danger = "danger".equals(ClientState.str(quest, "tone", ""));
		List<Line> lines = new ArrayList<>();

		lines.add(new Line(Component.literal(ClientState.str(quest, "title", "?")).withStyle(style -> style.withBold(true)), danger ? 0xFF7070 : GOLD));
		lines.add(new Line(Component.literal(ClientState.str(quest, "tierName", "") + " · " + ClientState.str(quest, "profName", "")
				+ (quest.has("repeat") && quest.get("repeat").getAsBoolean() ? " · repetição" : "")), 0xAAAAAA));
		lines.add(new Line(Component.literal("Quem pediu: " + ClientState.str(quest, "giver", "?")), 0x9BE7FF));
		lines.add(new Line(Component.literal("Onde: " + ClientState.str(quest, "giverPos", "?") + " (" + dimensionName(ClientState.str(quest, "giverDim", "")) + ")"), 0x9BE7FF));
		lines.add(Line.SPACER);
		addWrapped(lines, "<b>Objetivo:</b> " + ClientState.str(quest, "objective", ""), width, 0xFFFFFF);
		lines.add(Line.PROGRESS);
		lines.add(Line.SPACER);
		lines.add(new Line(Component.literal("Recompensas:").withStyle(style -> style.withBold(true)), GOLD));

		for (JsonElement element : quest.getAsJsonArray("rewards")) {
			JsonObject reward = element.getAsJsonObject();
			String text = "  • <o>" + reward.get("count").getAsInt() + "x {item:" + reward.get("item").getAsString() + "}</o>";

			if (reward.has("enchant")) {
				text += " <p>({ench:" + reward.get("enchant").getAsString() + "} " + reward.get("level").getAsInt() + ")</p>";
			}

			addWrapped(lines, text, width, 0xFFFFFF);
		}

		addWrapped(lines, "  • <a>+" + ClientState.num(quest, "karmaReward", 0) + " de karma</a>", width, 0xFFFFFF);
		lines.add(Line.SPACER);
		lines.add(new Line(Component.literal("A história:").withStyle(style -> style.withBold(true)), GOLD));

		for (String story : ClientState.strings(quest, "story")) {
			addWrapped(lines, "<i>" + story + "</i>", width, 0xCCCCCC);
		}

		List<String> hints = ClientState.strings(quest, "hints");

		if (!hints.isEmpty()) {
			lines.add(Line.SPACER);
			lines.add(new Line(Component.literal("Dicas:").withStyle(style -> style.withBold(true)), GOLD));

			for (String hint : hints) {
				addWrapped(lines, hint, width, 0xDDDDDD);
			}
		}

		int maxScroll = Math.max(0, lines.size() - (bottom - top) / 11);
		detailScroll = Math.min(detailScroll, maxScroll);
		int y = top;

		for (int i = detailScroll; i < lines.size(); i++) {
			if (y + 10 > bottom) {
				break;
			}

			Line line = lines.get(i);

			if (line == Line.PROGRESS) {
				int progress = ClientState.num(quest, "progress", 0);
				int target = Math.max(1, ClientState.num(quest, "target", 1));
				boolean done = progress >= target;
				Ui.bar(graphics, x, y + 2, width - 50, 5, progress / (float) target, Ui.argb(255, done ? 0x55FF55 : (danger ? 0xFF5555 : 0xFFD24A)));
				graphics.text(this.font, progress + "/" + target, x + width - 44, y, Ui.argb(255, done ? 0x8CFF8C : 0xFFFFFF), false);
			} else if (line.sequence != null) {
				graphics.text(this.font, line.sequence, x, y, Ui.argb(255, line.color), true);
			} else if (line.component != null) {
				graphics.text(this.font, line.component, x, y, Ui.argb(255, line.color), true);
			}

			y += 11;
		}

		if (maxScroll > 0) {
			graphics.text(this.font, detailScroll < maxScroll ? "▼ role para ler mais" : "▲", right - 80, bottom, Ui.argb(150, 0xAAAAAA), false);
		}

		// Botões
		int by = panelY2() - 26;
		boolean tracked = quest.get("prof").getAsString().equals(ClientState.getString("tracked", ""));
		Ui.roundedRect(graphics, x, by, x + 120, by + 18, Ui.argb(220, tracked ? 0x1E3A22 : 0x1F1F2A));
		Ui.roundedOutline(graphics, x, by, x + 120, by + 18, Ui.argb(200, tracked ? 0x55FF55 : 0xFFFFFF));
		String trackText = tracked ? "◆ Rastreando no HUD" : "Rastrear no HUD (T)";
		graphics.text(this.font, trackText, x + (120 - this.font.width(trackText)) / 2, by + 5, Ui.argb(255, tracked ? 0x8CFF8C : 0xFFFFFF), true);

		int ax = x + 128;
		Ui.roundedRect(graphics, ax, by, ax + 140, by + 18, Ui.argb(220, confirmAbandon ? 0x4A1010 : 0x1F1F2A));
		Ui.roundedOutline(graphics, ax, by, ax + 140, by + 18, Ui.argb(200, 0xFF5555));
		String abandonText = confirmAbandon ? "Clique de novo p/ confirmar" : "Desistir (-5 karma)";
		graphics.text(this.font, abandonText, ax + (140 - this.font.width(abandonText)) / 2, by + 5, Ui.argb(255, 0xFF8080), true);
	}

	private static String dimensionName(String id) {
		return switch (id) {
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> "Mundo Superior";
		};
	}

	private void addWrapped(List<Line> lines, String markup, int width, int color) {
		for (FormattedCharSequence sequence : this.font.split(Text.parse(markup), width)) {
			lines.add(new Line(sequence, color));
		}
	}

	private static final class Line {
		static final Line SPACER = new Line((Component) null, 0);
		static final Line PROGRESS = new Line((Component) null, 0);

		final Component component;
		final FormattedCharSequence sequence;
		final int color;

		Line(Component component, int color) {
			this.component = component;
			this.sequence = null;
			this.color = color;
		}

		Line(FormattedCharSequence sequence, int color) {
			this.component = null;
			this.sequence = sequence;
			this.color = color;
		}
	}

	private void drawReputation(GuiGraphicsExtractor graphics) {
		int x1 = panelX1() + 12;
		int x2 = panelX2() - 12;
		int y = panelY1() + 10;

		int karma = ClientState.getInt("karma", 0);
		int karmaColor = ClientState.getInt("karmaColor", 0xFFFFFF);
		graphics.text(this.font, Component.literal("KARMA: " + ClientState.getString("karmaTier", "Neutro").toUpperCase() + " (" + karma + ")")
				.withStyle(style -> style.withBold(true)), x1, y, Ui.argb(255, karmaColor), true);
		y += 12;

		for (FormattedCharSequence line : this.font.split(Component.literal(ClientState.getString("karmaDesc", "")), x2 - x1)) {
			graphics.text(this.font, line, x1, y, Ui.argb(255, 0xCCCCCC), false);
			y += 10;
		}

		y += 6;
		boolean hero = ClientState.getBool("hero");
		int mastered = ClientState.getInt("mastered", 0);
		String heroText = hero ? "★ HERÓI DA VILA ★ — todas as profissões dominadas!"
				: "Herói da Vila: " + mastered + "/13 profissões dominadas (complete uma missão de Mestre em cada uma)";
		graphics.text(this.font, heroText, x1, y, Ui.argb(255, hero ? GOLD : 0xDDDDDD), true);
		Ui.bar(graphics, x1, y + 11, x2 - x1, 4, mastered / 13F, Ui.argb(255, GOLD));
		y += 24;

		graphics.text(this.font, "AFINIDADE COM CADA PROFISSÃO", x1, y, Ui.argb(255, GOLD), true);
		y += 14;

		List<JsonObject> professions = ClientState.professions();
		int columns = 2;
		int columnWidth = (x2 - x1 - 10) / columns;

		for (int i = 0; i < professions.size(); i++) {
			JsonObject profession = professions.get(i);
			int cx = x1 + (i % columns) * (columnWidth + 10);
			int cy = y + (i / columns) * 22;

			if (cy + 20 > panelY2() - 6) {
				break;
			}

			boolean isMastered = profession.get("mastered").getAsBoolean();
			int level = profession.get("level").getAsInt();
			boolean nether = profession.get("key").getAsString().equals("piglin");
			Ui.roundedRect(graphics, cx, cy, cx + columnWidth, cy + 20, Ui.argb(140, nether ? 0x2A1208 : 0x16161C));

			if (isMastered) {
				Ui.roundedOutline(graphics, cx, cy, cx + columnWidth, cy + 20, Ui.argb(255, GOLD));
			}

			String name = profession.get("name").getAsString();
			graphics.text(this.font, name, cx + 5, cy + 3, Ui.argb(255, nether ? 0xFF8A3D : 0xFFFFFF), true);
			String levelText = isMastered ? "Mestre ★" : profession.get("levelName").getAsString();
			graphics.text(this.font, levelText, cx + columnWidth - 5 - this.font.width(levelText), cy + 3,
					Ui.argb(255, isMastered ? GOLD : 0xBBBBBB), false);

			// 5 pips de nível
			for (int pip = 0; pip < 5; pip++) {
				int px = cx + 5 + pip * 9;
				int color = pip < level ? (isMastered ? GOLD : 0x55FF55) : 0x333333;
				graphics.fill(px, cy + 13, px + 7, cy + 16, Ui.argb(255, color));
			}

			if (!isMastered) {
				String progress = profession.get("done").getAsInt() + "/" + profession.get("need").getAsInt() + " p/ subir";
				graphics.text(this.font, progress, cx + columnWidth - 5 - this.font.width(progress), cy + 11, Ui.argb(200, 0x888888), false);
			}
		}
	}
}
