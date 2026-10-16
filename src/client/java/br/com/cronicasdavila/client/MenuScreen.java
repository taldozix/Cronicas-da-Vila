package br.com.cronicasdavila.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import br.com.cronicasdavila.net.ModPayloads;
import br.com.cronicasdavila.util.Text;

/**
 * O menu do Crônicas da Vila (tecla J): Início, Missões, Reputação, Guia e Opções.
 * Visual: título grande no topo, botões retos à esquerda (o selecionado em ciano) e um painel escuro à direita.
 */
public class MenuScreen extends Screen {
	public static final String[] TAB_KEYS = {"inicio", "missoes", "reputacao", "guia", "opcoes"};
	private static final String[] TAB_NAMES = {"INÍCIO", "MISSÕES", "REPUTAÇÃO", "GUIA", "OPÇÕES"};
	private static final int SIDE_W = 112;
	private static final int BUTTON_H = 22;

	private static final Map<String, String> PROFESSION_ICONS = Map.ofEntries(
			Map.entry("farmer", "minecraft:composter"), Map.entry("fisherman", "minecraft:barrel"),
			Map.entry("shepherd", "minecraft:loom"), Map.entry("butcher", "minecraft:smoker"),
			Map.entry("leatherworker", "minecraft:cauldron"), Map.entry("fletcher", "minecraft:fletching_table"),
			Map.entry("mason", "minecraft:stonecutter"), Map.entry("toolsmith", "minecraft:smithing_table"),
			Map.entry("weaponsmith", "minecraft:grindstone"), Map.entry("armorer", "minecraft:blast_furnace"),
			Map.entry("cartographer", "minecraft:cartography_table"), Map.entry("librarian", "minecraft:lectern"),
			Map.entry("cleric", "minecraft:brewing_stand"), Map.entry("piglin", "minecraft:gold_ingot"),
			Map.entry("pillager", "minecraft:crossbow"), Map.entry("witch", "minecraft:fermented_spider_eye"),
			Map.entry("shulker", "minecraft:shulker_shell")
	);

	/** Uma área clicável desenhada neste quadro. */
	private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
		boolean contains(double x, double y) {
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
	}

	private final Screen parent;
	/** Áreas clicáveis sendo montadas neste quadro. */
	private List<Hit> hits = new ArrayList<>();
	/** As áreas do último quadro completo (o clique usa esta cópia, que nunca está pela metade). */
	private volatile List<Hit> lastHits = List.of();
	private boolean clickHandled;
	private final int[] scroll = new int[TAB_KEYS.length];
	private final int[] contentHeight = new int[TAB_KEYS.length];
	private int tab;
	private int selectedQuest;
	private boolean showStory;
	private boolean confirmAbandon;
	private int detailScroll;
	private int detailHeight;
	private int guideTopic;
	private int guideScroll;
	private int guideHeight;
	private int mouseX;
	private int mouseY;
	private ItemStack hoveredItem = ItemStack.EMPTY;
	private Component hoveredText;

	// Animações
	private final long openTime = System.currentTimeMillis();
	private long tabChangedAt;
	private int tabDirection = 1;
	private long lastFrame = System.currentTimeMillis();
	/** Progresso (0 a 1) do destaque de cada botão lateral: mouse em cima e selecionado. */
	private final float[] hoverAnim = new float[TAB_KEYS.length + 1];
	private final float[] selectAnim = new float[TAB_KEYS.length + 1];
	private float hudAnim;

	public MenuScreen(Screen parent, String tabKey) {
		super(Component.literal("Crônicas da Vila"));
		this.parent = parent;
		setTab(tabKey);
	}

	public void setTab(String tabKey) {
		for (int i = 0; i < TAB_KEYS.length; i++) {
			if (TAB_KEYS[i].equals(tabKey)) {
				tab = i;
			}
		}
	}

	private boolean openSoundPlayed;

	@Override
	protected void init() {
		// Aberto pela tecla J no meio do jogo, o mouse ainda está "preso" na câmera: solta para dar para clicar.
		if (this.minecraft != null) {
			this.minecraft.mouseHandler.releaseMouse();
		}

		if (!openSoundPlayed) {
			openSoundPlayed = true;
			Sfx.open();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	private static void send(String action) {
		ClientPlayNetworking.send(new ModPayloads.ActionPayload(action));
	}

	// ---------------------------------------------------------------------------------------------
	// Layout
	// ---------------------------------------------------------------------------------------------

	private int sideX() {
		return Math.max(10, (int) (this.width * 0.07));
	}

	private int contentTop() {
		return this.height >= 300 ? 64 : 52;
	}

	private int contentBottom() {
		return this.height - 30;
	}

	private int panelX1() {
		return sideX() + SIDE_W + 14;
	}

	private int panelX2() {
		return this.width - Math.max(10, (int) (this.width * 0.06));
	}

	private boolean hover(int x1, int y1, int x2, int y2) {
		return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
	}

	private void hit(int x1, int y1, int x2, int y2, Runnable action) {
		hits.add(new Hit(x1, y1, x2, y2, action));
	}

	/** Área clicável dentro de uma região com rolagem: só vale a parte visível. */
	private void hitClipped(int x1, int y1, int x2, int y2, int clipTop, int clipBottom, Runnable action) {
		int top = Math.max(y1, clipTop);
		int bottom = Math.min(y2, clipBottom);

		if (bottom > top) {
			hits.add(new Hit(x1, top, x2, bottom, action));
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Entrada
	// ---------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (Ui.isLeftClick(event)) {
			clickHandled = true;

			if (click(event.x(), event.y())) {
				return true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	/** Reserva: se o clique não chegou, o soltar do botão faz a ação. */
	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (Ui.isLeftClick(event) && !clickHandled) {
			click(event.x(), event.y());
		}

		clickHandled = false;
		return super.mouseReleased(event);
	}

	private boolean click(double x, double y) {
		// Barra lateral, Fechar e o botão do HUD: calculados direto pela posição.
		int by = contentTop() + 10;

		for (int i = 0; i <= TAB_KEYS.length; i++) {
			if (i == TAB_KEYS.length) {
				by += 6;
			}

			int sx = sideX() + sideOffset(i);

			if (x >= sx && x < sx + SIDE_W && y >= by && y < by + BUTTON_H) {
				if (i == TAB_KEYS.length) {
					onClose();
				} else {
					changeTab(i);
				}

				return true;
			}

			by += BUTTON_H + 6;
		}

		String label = "HUD: " + ClientConfig.modeName(ClientConfig.trackerMode()).toUpperCase();
		int hudW = Math.max(110, this.font.width(label) + 20);
		int hudRight = panelX2() + panelOffset();

		if (x >= hudRight - hudW && x < hudRight && y >= contentTop() - 18 && y < contentTop()) {
			ClientConfig.cycleTrackerMode();
			Sfx.hud();
			return true;
		}

		// O resto (cartões, botões das abas, opções) usa as áreas do último quadro desenhado.
		List<Hit> snapshot = lastHits;

		for (int i = snapshot.size() - 1; i >= 0; i--) {
			if (snapshot.get(i).contains(x, y)) {
				Sfx.select();
				snapshot.get(i).action().run();
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int step = (int) Math.signum(scrollY) * 18;
		int visible = contentBottom() - contentTop() - 24;

		if (tab == 1) {
			detailScroll = clamp(detailScroll - step, detailHeight - visible + 90);
		} else if (tab == 3) {
			guideScroll = clamp(guideScroll - step, guideHeight - visible);
		} else {
			scroll[tab] = clamp(scroll[tab] - step, contentHeight[tab] - visible);
		}

		return true;
	}

	private static int clamp(int value, int max) {
		return Math.max(0, Math.min(Math.max(0, max), value));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();

		// Teclas 1 a 5 abrem cada aba direto.
		int[] numbers = {InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3, InputConstants.KEY_4, InputConstants.KEY_5};

		for (int i = 0; i < numbers.length; i++) {
			if (key == numbers[i]) {
				changeTab(i);
				return true;
			}
		}

		if (key == InputConstants.KEY_TAB || key == InputConstants.KEY_RIGHT) {
			changeTab((tab + 1) % TAB_KEYS.length);
			return true;
		}

		if (key == InputConstants.KEY_LEFT) {
			changeTab((tab + TAB_KEYS.length - 1) % TAB_KEYS.length);
			return true;
		}

		List<JsonObject> quests = ClientState.quests();

		if (tab == 1 && !quests.isEmpty()) {
			if (key == InputConstants.KEY_UP || key == InputConstants.KEY_W) {
				Sfx.select();
				selectQuest(Math.floorMod(selectedQuest - 1, quests.size()));
				return true;
			}

			if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_S) {
				Sfx.select();
				selectQuest(Math.floorMod(selectedQuest + 1, quests.size()));
				return true;
			}

			if (key == InputConstants.KEY_T || key == InputConstants.KEY_RETURN) {
				track(quests.get(Math.min(selectedQuest, quests.size() - 1)));
				return true;
			}
		}

		if (CronicasDaVilaClient.isJournalKey(key)) {
			onClose();
			return true;
		}

		return super.keyPressed(event);
	}

	private void changeTab(int index) {
		if (index != tab) {
			Sfx.select();
			tabDirection = index > tab ? 1 : -1;
			tabChangedAt = System.currentTimeMillis();
		}

		tab = index;
		confirmAbandon = false;
	}

	// ---------------------------------------------------------------------------------------------
	// Animações (deslizar ao abrir, ao trocar de aba e ao passar o mouse)
	// ---------------------------------------------------------------------------------------------

	private float since(long start, long delay, float duration) {
		return Ui.ease((System.currentTimeMillis() - start - delay) / duration);
	}

	/** Os botões entram da esquerda, um depois do outro. */
	private int sideOffset(int index) {
		float e = since(openTime, index * 45L, 280F);
		return (int) (-(1F - e) * (SIDE_W + sideX() + 10));
	}

	/** O painel entra da direita. */
	private int panelOffset() {
		return (int) ((1F - since(openTime, 60L, 320F)) * 60);
	}

	/** O conteúdo desliza para o lado da aba escolhida. */
	private int contentOffset() {
		return (int) ((1F - since(tabChangedAt, 0L, 220F)) * 28 * tabDirection);
	}

	/** O título desce um pouco ao abrir. */
	private int titleOffset() {
		return (int) (-(1F - since(openTime, 0L, 300F)) * 18);
	}

	private static float approach(float current, float target, float dt) {
		float speed = Math.min(1F, dt * 14F);
		return current + (target - current) * speed;
	}

	private void selectQuest(int index) {
		selectedQuest = index;
		detailScroll = 0;
		confirmAbandon = false;
	}

	private static void track(JsonObject quest) {
		send("track:" + quest.get("prof").getAsString());
	}

	// ---------------------------------------------------------------------------------------------
	// Desenho
	// ---------------------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// Escurece o mundo atrás, deixando ver um pouco (como na tela de título dos servidores).
		graphics.fill(0, 0, this.width, this.height, Ui.argb(150, 0x05070A));
		graphics.fill(0, 0, this.width, contentTop() - 6, Ui.argb(60, 0x000000));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		this.hits = new ArrayList<>();
		this.hoveredItem = ItemStack.EMPTY;
		this.hoveredText = null;

		drawHeader(graphics);
		drawSidebar(graphics);
		drawFooter(graphics);

		int x1 = panelX1() + panelOffset();
		int x2 = panelX2() + panelOffset();
		int y1 = contentTop();
		int y2 = contentBottom();
		Ui.flatPanel(graphics, x1, y1, x2, y2, 215);
		drawQuickToggle(graphics, x2, y1);

		// O conteúdo da aba desliza dentro do painel.
		int slide = contentOffset();
		int cx1 = x1 + slide;
		int cx2 = x2 + slide;
		graphics.enableScissor(x1, y1, x2, y2);

		if (!ClientState.hasData()) {
			String text = "Carregando...";
			graphics.text(this.font, text, (cx1 + cx2 - this.font.width(text)) / 2, (y1 + y2) / 2, Ui.argb(255, Ui.MUTED), false);
		} else {
			switch (tab) {
				case 0 -> drawHome(graphics, cx1, y1, cx2, y2);
				case 1 -> drawQuests(graphics, cx1, y1, cx2, y2);
				case 2 -> drawReputation(graphics, cx1, y1, cx2, y2);
				case 3 -> drawGuide(graphics, cx1, y1, cx2, y2);
				default -> drawOptions(graphics, cx1, y1, cx2, y2);
			}
		}

		graphics.disableScissor();

		lastHits = hits;

		if (!hoveredItem.isEmpty()) {
			graphics.setTooltipForNextFrame(this.font, hoveredItem, mouseX, mouseY);
		} else if (hoveredText != null) {
			graphics.setTooltipForNextFrame(hoveredText, mouseX, mouseY);
		}
	}

	private void drawHeader(GuiGraphicsExtractor graphics) {
		String title = "CRÔNICAS DA VILA";
		float scale = this.width >= 560 && this.height >= 300 ? 3.0F : 2.0F;
		int titleWidth = (int) (this.font.width(title) * scale);
		int top = 8 + titleOffset();
		Ui.bigText(graphics, this.font, title, (this.width - titleWidth) / 2, top, scale, Ui.CREAM, Ui.CREAM_SHADOW);

		String subtitle = "DIÁRIO DE AVENTURAS";
		int spacing = scale >= 3.0F ? 4 : 2;
		int subtitleWidth = Ui.spacedWidth(this.font, subtitle, spacing);
		Ui.spacedText(graphics, this.font, subtitle, (this.width - subtitleWidth) / 2, top + (int) (9 * scale) + 4, spacing, 0xFFFFFF);
	}

	private void drawSidebar(GuiGraphicsExtractor graphics) {
		long now = System.currentTimeMillis();
		float dt = Math.min(0.1F, (now - lastFrame) / 1000F);
		lastFrame = now;
		int y = contentTop() + 10;

		for (int i = 0; i <= TAB_KEYS.length; i++) {
			boolean close = i == TAB_KEYS.length;

			if (close) {
				y += 6;
			}

			int x = sideX() + sideOffset(i);
			boolean hovered = hover(x, y, x + SIDE_W, y + BUTTON_H);
			hoverAnim[i] = approach(hoverAnim[i], hovered ? 1F : 0F, dt);
			selectAnim[i] = approach(selectAnim[i], !close && i == tab ? 1F : 0F, dt);
			Ui.animatedButton(graphics, this.font, x, y, x + SIDE_W, y + BUTTON_H, close ? "FECHAR  (Esc)" : (i + 1) + "  " + TAB_NAMES[i], selectAnim[i], hoverAnim[i]);
			y += BUTTON_H + 6;
		}
	}

	/** Botão lilás acima do painel (como o "Discord" da referência): troca o rastreador do HUD. */
	private void drawQuickToggle(GuiGraphicsExtractor graphics, int panelRight, int panelTop) {
		String label = "HUD: " + ClientConfig.modeName(ClientConfig.trackerMode()).toUpperCase();
		int w = Math.max(110, this.font.width(label) + 20);
		int x1 = panelRight - w;
		int y1 = panelTop - 18;
		boolean hovered = hover(x1, y1, panelRight, panelTop);
		hudAnim = approach(hudAnim, hovered ? 1F : 0F, 0.016F);
		graphics.fill(x1, y1, panelRight, panelTop, Ui.argb(230, Ui.LAVENDER));
		graphics.fill(x1, y1, x1 + (int) (w * hudAnim), panelTop, Ui.argb(255, 0xA9ADEC));
		graphics.text(this.font, label, x1 + (w - this.font.width(label)) / 2, y1 + 5, Ui.argb(255, 0xFFFFFF), true);
		hit(x1, y1, panelRight, panelTop, ClientConfig::cycleTrackerMode);

		if (hovered) {
			hoveredText = Component.literal("Rastreador de missões na tela: Completo, Compacto ou Oculto (tecla H)");
		}
	}

	private void drawFooter(GuiGraphicsExtractor graphics) {
		int bottom = this.height - 4;
		String[] left = {
				"Crônicas da Vila " + ClientState.getString("version", ""),
				"Minecraft 26.3 · Fabric",
				ClientState.getInt("totalQuests", 0) + " missões · " + ClientState.getInt("totalCompleted", 0) + " concluídas"
		};

		for (int i = 0; i < left.length; i++) {
			graphics.text(this.font, left[left.length - 1 - i], 6, bottom - 9 - i * 9, Ui.argb(200, 0xDDDDDD), true);
		}

		String name = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getName().getString() : "";
		String right = name + " — " + ClientState.getString("rankName", "Amador");
		graphics.text(this.font, right, this.width - 6 - this.font.width(right), bottom - 9, Ui.argb(200, 0xDDDDDD), true);
	}

	private int wrap(GuiGraphicsExtractor graphics, String markup, int x, int y, int width, int color, int maxY) {
		for (FormattedCharSequence line : this.font.split(Text.parse(markup), width)) {
			if (y + 9 > maxY) {
				break;
			}

			graphics.text(this.font, line, x, y, Ui.argb(255, color), false);
			y += 10;
		}

		return y;
	}

	private int lineCount(String markup, int width) {
		return this.font.split(Text.parse(markup), width).size();
	}

	private void sectionTitle(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
		graphics.text(this.font, Component.literal(text).withStyle(style -> style.withBold(true)), x, y, Ui.argb(255, color), true);
	}

	private void bar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float fraction, int color) {
		graphics.fill(x, y, x + width, y + height, Ui.argb(200, 0x2A2E34));
		int filled = (int) (width * Math.max(0F, Math.min(1F, fraction)));

		if (filled > 0) {
			graphics.fill(x, y, x + filled, y + height, Ui.argb(255, color));
		}
	}

	private void item(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, boolean decorations) {
		if (stack.isEmpty()) {
			return;
		}

		graphics.item(stack, x, y);

		if (decorations) {
			graphics.itemDecorations(this.font, stack, x, y);
		}

		if (hover(x, y, x + 16, y + 16)) {
			hoveredItem = stack;
		}
	}

	private static int factionColor(JsonObject object) {
		return switch (ClientState.str(object, "faction", "GOOD")) {
			case "VILLAIN" -> Ui.VILLAIN;
			case "NEUTRAL" -> Ui.END;
			default -> Ui.GOLD;
		};
	}

	// ---------------------------------------------------------------------------------------------
	// Início
	// ---------------------------------------------------------------------------------------------

	private void drawHome(GuiGraphicsExtractor graphics, int px1, int py1, int px2, int py2) {
		int pad = 14;
		int boxW = Math.min(150, (int) ((px2 - px1) * 0.32));
		int bx1 = px1 + pad;
		int by1 = py1 + pad;
		int bx2 = bx1 + boxW;
		int by2 = py2 - pad;

		// Retrato do jogador (o boneco segue o mouse).
		graphics.fill(bx1, by1, bx2, by2, Ui.argb(255, 0x3A4A55));
		graphics.fill(bx1, by1 + (by2 - by1) / 2, bx2, by2, Ui.argb(255, 0x2E3B33));

		if (this.minecraft != null && this.minecraft.player != null) {
			int size = Math.max(20, (int) ((by2 - by1) * 0.40));
			InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, bx1, by1, bx2, by2, size, 0.0625F, mouseX, mouseY, this.minecraft.player);
		}

		int x = bx2 + 18;
		int width = px2 - pad - x;
		int y = py1 + pad + 2;
		String name = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getName().getString() : "aventureiro";
		Ui.bigText(graphics, this.font, Component.literal("Olá, " + name + "!"), x, y, 1.5F, 0xFFFFFF);
		y += 22;

		int villainQuests = ClientState.getInt("villainQuests", 0);
		boolean villain = ClientState.getBool("villain");
		String[][] rows = {
				{"Nível", "<o>" + ClientState.getString("rankName", "Amador") + "</o>"},
				{"Pelas missões", ClientState.getString("rankQuestName", "Amador") + " <d>(fica para sempre)</d>"},
				{"Pela XP", ClientState.getString("rankXpName", "Amador")},
				{"Karma", ClientState.getString("karmaTier", "Neutro") + " <d>(" + ClientState.getInt("karma", 0) + ")</d>"},
				{"Vilania", villainQuests == 0 ? "<d>Inocente</d>"
						: "<r>" + ClientState.getString("villainName", "") + "</r> <d>· " + ClientState.getInt("villainPercent", 0) + "%</d>"},
				{"Missões", ClientState.quests().size() + " ativas <d>· " + ClientState.getInt("totalCompleted", 0) + " concluídas</d>"},
				{"Herói da Vila", ClientState.getBool("hero") ? "<o>★ Conquistado</o>" : ClientState.getInt("mastered", 0) + "/13 profissões"},
				{"Vilão da História", villain ? "<p>☠ " + (ClientState.getBool("villainPermanent") ? "Para sempre" : "Ativo") + "</p>"
						: ClientState.getInt("villainMastered", 0) + "/" + ClientState.getInt("villainFactions", 3) + " inimigos dominados"}
		};

		int labelW = 0;

		for (String[] row : rows) {
			labelW = Math.max(labelW, this.font.width(row[0]));
		}

		for (String[] row : rows) {
			graphics.text(this.font, row[0], x, y, Ui.argb(255, Ui.MUTED), false);
			int valueColor = row[0].equals("Karma") ? ClientState.getInt("karmaColor", 0xFFFFFF) : Ui.TEXT;
			graphics.text(this.font, Ui.trimComponent(this.font, Text.parse(row[1]), width - labelW - 10), x + labelW + 10, y, Ui.argb(255, valueColor), false);
			y += 14;
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Missões
	// ---------------------------------------------------------------------------------------------

	private void drawQuests(GuiGraphicsExtractor graphics, int px1, int py1, int px2, int py2) {
		List<JsonObject> quests = ClientState.quests();
		int pad = 12;

		if (quests.isEmpty()) {
			String line = "Nenhuma missão ativa no momento.";
			Ui.bigText(graphics, this.font, Component.literal(line), (px1 + px2 - (int) (this.font.width(line) * 1.5F)) / 2, (py1 + py2) / 2 - 16, 1.5F, 0xFFFFFF);
			String hint = "Converse com aldeões, piglins, saqueadores, bruxas e shulkers.";
			graphics.text(this.font, hint, (px1 + px2 - this.font.width(hint)) / 2, (py1 + py2) / 2 + 4, Ui.argb(255, Ui.MUTED), false);
			return;
		}

		selectedQuest = Math.min(selectedQuest, quests.size() - 1);
		int listW = Math.max(130, Math.min(190, (int) ((px2 - px1) * 0.38)));
		int lx1 = px1 + pad;
		int lx2 = lx1 + listW;
		int top = py1 + pad;
		int bottom = py2 - pad;
		String tracked = ClientState.getString("tracked", "");
		int cardH = 34;
		contentHeight[1] = quests.size() * (cardH + 4);
		scroll[1] = clamp(scroll[1], contentHeight[1] - (bottom - top));

		graphics.enableScissor(lx1, top, lx2, bottom);

		for (int i = 0; i < quests.size(); i++) {
			JsonObject quest = quests.get(i);
			int index = i;
			int y = top + i * (cardH + 4) - scroll[1];

			if (y + cardH < top || y > bottom) {
				continue;
			}

			boolean selected = i == selectedQuest;
			boolean hovered = hover(lx1, Math.max(y, top), lx2, Math.min(y + cardH, bottom));
			int progress = ClientState.num(quest, "progress", 0);
			int target = Math.max(1, ClientState.num(quest, "target", 1));
			boolean done = progress >= target;
			int color = done ? Ui.GOOD : factionColor(quest);

			graphics.fill(lx1, y, lx2, y + cardH, Ui.argb(230, selected ? 0x1E3B40 : (hovered ? Ui.BUTTON_HOVER : Ui.CARD)));
			graphics.fill(lx1, y, lx1 + 2, y + cardH, Ui.argb(255, selected ? Ui.CYAN : color));
			item(graphics, Ui.stack(ClientState.str(quest, "icon", "minecraft:paper"), 1), lx1 + 7, y + 5, false);

			String prefix = quest.get("prof").getAsString().equals(tracked) ? "◆ " : "";
			graphics.text(this.font, Ui.trim(this.font, prefix + ClientState.str(quest, "title", "?"), lx2 - lx1 - 32), lx1 + 28, y + 5,
					Ui.argb(255, selected ? 0xFFFFFF : (done ? Ui.GOOD : 0xE0E0E0)), false);
			int percent = Math.min(100, progress * 100 / target);
			String pct = done ? "✔" : percent + "%";
			bar(graphics, lx1 + 28, y + 21, lx2 - lx1 - 36 - this.font.width(pct), 3, percent / 100F, done ? Ui.GOOD : Ui.CYAN);
			graphics.text(this.font, pct, lx2 - 5 - this.font.width(pct), y + 18, Ui.argb(255, done ? Ui.GOOD : Ui.MUTED), false);
			hitClipped(lx1, y, lx2, y + cardH, top, bottom, () -> selectQuest(index));
		}

		graphics.disableScissor();
		drawQuestDetails(graphics, quests.get(selectedQuest), lx2 + 14, top, px2 - pad, bottom);
	}

	private void drawQuestDetails(GuiGraphicsExtractor graphics, JsonObject quest, int x1, int top, int x2, int bottom) {
		int width = x2 - x1;
		int progress = ClientState.num(quest, "progress", 0);
		int target = Math.max(1, ClientState.num(quest, "target", 1));
		boolean done = progress >= target;
		boolean villainQuest = "VILLAIN".equals(ClientState.str(quest, "faction", "GOOD"));
		int buttonsY = bottom - 20;
		int y = top - detailScroll;
		int startY = y;

		graphics.enableScissor(x1, top, x2, buttonsY - 6);

		// Título e quem pediu
		graphics.text(this.font, Component.literal(ClientState.str(quest, "title", "?")).withStyle(style -> style.withBold(true)), x1, y,
				Ui.argb(255, factionColor(quest)), true);
		y += 12;
		String giver = ClientState.str(quest, "tierName", "") + " · " + ClientState.str(quest, "profName", "") + " · pedida por "
				+ ClientState.str(quest, "giver", "?");
		graphics.text(this.font, Ui.trim(this.font, giver, width), x1, y, Ui.argb(255, Ui.MUTED), false);
		y += 10;
		String where = "Local: " + ClientState.str(quest, "giverPos", "?") + " (" + dimensionName(ClientState.str(quest, "giverDim", "")) + ")";
		graphics.text(this.font, Ui.trim(this.font, where, width), x1, y, Ui.argb(255, 0x7C838A), false);
		y += 16;

		// Objetivo
		String objective = ClientState.str(quest, "objective", "");
		int objectiveLines = Math.min(2, lineCount(objective, width - 34));
		int boxH = 22 + objectiveLines * 10;
		graphics.fill(x1, y, x2, y + boxH, Ui.argb(230, Ui.CARD));
		item(graphics, Ui.stack(ClientState.str(quest, "icon", "minecraft:paper"), 1), x1 + 6, y + 5, false);
		wrap(graphics, objective, x1 + 28, y + 5, width - 34, Ui.TEXT, y + 5 + objectiveLines * 10);
		int percent = Math.min(100, progress * 100 / target);
		String count = (done ? "✔ Pronta! " : "") + progress + "/" + target + " · " + percent + "%";
		int barY = y + boxH - 9;
		bar(graphics, x1 + 28, barY + 2, width - 40 - this.font.width(count), 3, percent / 100F, done ? Ui.GOOD : Ui.CYAN);
		graphics.text(this.font, count, x2 - 6 - this.font.width(count), barY - 1, Ui.argb(255, done ? Ui.GOOD : Ui.TEXT), false);
		y += boxH + 10;

		// Recompensas: ícones dos itens
		sectionTitle(graphics, "RECOMPENSAS", x1, y, Ui.CYAN_LIGHT);
		y += 12;
		int ix = x1;

		for (JsonElement element : quest.getAsJsonArray("rewards")) {
			JsonObject reward = element.getAsJsonObject();

			if (ix + 18 > x2) {
				ix = x1;
				y += 20;
			}

			boolean bonus = reward.has("bonus") && reward.get("bonus").getAsBoolean();
			graphics.fill(ix, y, ix + 18, y + 18, Ui.argb(200, bonus ? 0x1E3B40 : 0x22262B));
			item(graphics, Ui.stack(reward.get("item").getAsString(), reward.get("count").getAsInt()), ix + 1, y + 1, true);
			ix += 21;
		}

		y += 22;
		int karma = ClientState.num(quest, "karmaReward", 0);
		String extras = "<a>+" + ClientState.num(quest, "xp", 0) + " XP</a>"
				+ (karma == 0 ? "" : karma > 0 ? "  <g>+" + karma + " karma</g>" : "  <r>" + karma + " karma</r>")
				+ (quest.has("repeat") && quest.get("repeat").getAsBoolean() ? "  <d>(repetição: metade)</d>" : "");
		graphics.text(this.font, Text.parse(extras), x1, y, Ui.argb(255, 0xFFFFFF), false);
		y += 16;

		// Dicas ou história
		int tabW = 70;
		boolean hintsHover = hover(x1, y, x1 + tabW, y + 14);
		boolean storyHover = hover(x1 + tabW + 4, y, x1 + tabW * 2 + 4, y + 14);
		Ui.smallButton(graphics, this.font, x1, y, x1 + tabW, y + 14, "DICAS", Ui.CYAN, !showStory, hintsHover);
		Ui.smallButton(graphics, this.font, x1 + tabW + 4, y, x1 + tabW * 2 + 4, y + 14, "HISTÓRIA", Ui.CYAN, showStory, storyHover);
		hitClipped(x1, y, x1 + tabW, y + 14, top, buttonsY - 6, () -> showStory = false);
		hitClipped(x1 + tabW + 4, y, x1 + tabW * 2 + 4, y + 14, top, buttonsY - 6, () -> showStory = true);
		y += 20;

		for (String line : ClientState.strings(quest, showStory ? "story" : "hints")) {
			for (FormattedCharSequence sequence : this.font.split(Text.parse(line), width - 8)) {
				graphics.text(this.font, sequence, x1 + 4, y, Ui.argb(255, 0xCFCFCF), false);
				y += 10;
			}

			y += 3;
		}

		graphics.disableScissor();
		detailHeight = y - startY;

		// Botões
		boolean trackedNow = quest.get("prof").getAsString().equals(ClientState.getString("tracked", ""));
		int bw = Math.min(140, (width - 6) / 2);
		boolean trackHover = hover(x1, buttonsY, x1 + bw, bottom);
		Ui.smallButton(graphics, this.font, x1, buttonsY, x1 + bw, bottom, trackedNow ? "◆ RASTREANDO" : "RASTREAR NO HUD", Ui.CYAN, trackedNow, trackHover);
		hit(x1, buttonsY, x1 + bw, bottom, () -> track(quest));

		int ax = x1 + bw + 6;
		boolean abandonHover = hover(ax, buttonsY, ax + bw, bottom);
		String abandon = confirmAbandon ? "CONFIRMAR?" : (villainQuest ? "DESISTIR (+5 KARMA)" : "DESISTIR (-5 KARMA)");
		Ui.smallButton(graphics, this.font, ax, buttonsY, ax + bw, bottom, abandon, 0xA03030, confirmAbandon, abandonHover);
		hit(ax, buttonsY, ax + bw, bottom, () -> {
			if (confirmAbandon) {
				send("abandon:" + quest.get("prof").getAsString());
				confirmAbandon = false;
				selectedQuest = 0;
			} else {
				confirmAbandon = true;
			}
		});
	}

	private static String dimensionName(String dimension) {
		return switch (dimension) {
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "Fim";
			default -> "Mundo Superior";
		};
	}

	// ---------------------------------------------------------------------------------------------
	// Reputação
	// ---------------------------------------------------------------------------------------------

	private void drawReputation(GuiGraphicsExtractor graphics, int px1, int py1, int px2, int py2) {
		int pad = 12;
		int top = py1 + pad;
		int bottom = py2 - pad;
		int x1 = px1 + pad;
		int x2 = px2 - pad;
		int y = top - scroll[2];
		int startY = y;
		graphics.enableScissor(px1, top, px2, bottom);

		// Os dois lados, lado a lado
		int cardW = (x2 - x1 - 10) / 2;
		int cardH = 74;
		drawHeroCard(graphics, x1, y, x1 + cardW, y + cardH);
		drawVillainCard(graphics, x1 + cardW + 10, y, x2, y + cardH);
		y += cardH + 14;

		List<JsonObject> villagers = new ArrayList<>();
		List<JsonObject> enemies = new ArrayList<>();

		for (JsonObject profession : ClientState.professions()) {
			("VILLAIN".equals(ClientState.str(profession, "faction", "GOOD")) ? enemies : villagers).add(profession);
		}

		sectionTitle(graphics, "AFINIDADE COM AS VILAS", x1, y, Ui.GOLD);
		y += 13;
		y = drawAffinityGrid(graphics, villagers, x1, x2, y) + 10;
		sectionTitle(graphics, "AFINIDADE COM OS INIMIGOS DAS VILAS", x1, y, Ui.VILLAIN);
		y += 13;
		y = drawAffinityGrid(graphics, enemies, x1, x2, y);

		graphics.disableScissor();
		contentHeight[2] = y - startY;
	}

	private void drawHeroCard(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2) {
		graphics.fill(x1, y1, x2, y2, Ui.argb(230, Ui.CARD));
		graphics.fill(x1, y1, x2, y1 + 2, Ui.argb(255, Ui.GOLD));
		int x = x1 + 8;
		int width = x2 - x1 - 16;
		graphics.text(this.font, "LADO HERÓI", x, y1 + 7, Ui.argb(255, Ui.GOLD), false);
		int karma = ClientState.getInt("karma", 0);
		String tier = ClientState.getString("karmaTier", "Neutro") + " (" + karma + ")";
		graphics.text(this.font, Component.literal(tier).withStyle(style -> style.withBold(true)), x, y1 + 19, Ui.argb(255, ClientState.getInt("karmaColor", 0xFFFFFF)), true);

		// Barra de karma de -1000 a +1000, com o zero no meio
		int by = y1 + 34;
		graphics.fill(x, by, x + width, by + 5, Ui.argb(200, 0x2A2E34));
		int mid = x + width / 2;
		int fill = (int) (width / 2F * Math.max(-1F, Math.min(1F, karma / 1000F)));

		if (fill > 0) {
			graphics.fill(mid, by, mid + fill, by + 5, Ui.argb(255, Ui.GOOD));
		} else if (fill < 0) {
			graphics.fill(mid + fill, by, mid, by + 5, Ui.argb(255, Ui.VILLAIN));
		}

		graphics.fill(mid, by - 2, mid + 1, by + 7, Ui.argb(255, 0xFFFFFF));
		int mastered = ClientState.getInt("mastered", 0);
		String hero = ClientState.getBool("hero") ? "★ Herói da Vila" : "Herói da Vila: " + mastered + "/13";
		graphics.text(this.font, Ui.trim(this.font, hero, width), x, y1 + 46, Ui.argb(255, ClientState.getBool("hero") ? Ui.GOLD : Ui.TEXT), false);
		bar(graphics, x, y1 + 58, width, 3, mastered / 13F, Ui.GOLD);

		if (hover(x1, y1, x2, y2)) {
			hoveredText = Component.literal(ClientState.getString("karmaDesc", ""));
		}
	}

	private void drawVillainCard(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2) {
		graphics.fill(x1, y1, x2, y2, Ui.argb(230, 0x1F1517));
		graphics.fill(x1, y1, x2, y1 + 2, Ui.argb(255, Ui.VILLAIN));
		int x = x1 + 8;
		int width = x2 - x1 - 16;
		graphics.text(this.font, "LADO VILÃO", x, y1 + 7, Ui.argb(255, Ui.VILLAIN), false);
		boolean innocent = ClientState.getInt("villainQuests", 0) == 0;
		int rank = ClientState.getInt("villainRank", 1);
		String name = innocent ? "Inocente" : ClientState.getString("villainName", "Arruaceiro") + " (" + rank + "/5)";
		graphics.text(this.font, Component.literal(name).withStyle(style -> style.withBold(true)), x, y1 + 19,
				Ui.argb(255, innocent ? 0xBBBBBB : ClientState.getInt("villainColor", Ui.VILLAIN)), true);

		int percent = ClientState.getInt("villainPercent", 0);
		bar(graphics, x, y1 + 34, width, 5, percent / 100F, 0xB02A2A);
		String line;

		if (ClientState.getBool("villain")) {
			line = "☠ Vilão da História" + (ClientState.getBool("villainPermanent") ? " para sempre" : "");
		} else if (rank < 5) {
			line = "Serviços: " + ClientState.getInt("villainTierDone", 0) + "/" + ClientState.getInt("villainTierNeeded", 4)
					+ " → " + ClientState.getString("villainNextName", "Bandido");
		} else {
			line = "Domine " + ClientState.getInt("villainMastered", 0) + "/" + ClientState.getInt("villainFactions", 3) + " inimigos";
		}

		graphics.text(this.font, Ui.trim(this.font, line, width), x, y1 + 46, Ui.argb(255, ClientState.getBool("villain") ? Ui.END : Ui.TEXT), false);
		graphics.text(this.font, "Vilania: " + percent + "%", x, y1 + 58, Ui.argb(255, Ui.MUTED), false);

		if (ClientState.getBool("villain")) {
			long ticks = ClientState.getInt("overworldTicks", 0);
			String time = (ticks / 1200) + "/20 min longe do Nether";
			graphics.text(this.font, time, x2 - 8 - this.font.width(time), y1 + 58, Ui.argb(255, ticks >= 24000 ? 0xFF5555 : Ui.MUTED), false);
		}
	}

	private int drawAffinityGrid(GuiGraphicsExtractor graphics, List<JsonObject> professions, int x1, int x2, int y) {
		int columns = x2 - x1 >= 420 ? 3 : 2;
		int gap = 6;
		int cellW = (x2 - x1 - gap * (columns - 1)) / columns;
		int cellH = 26;

		for (int i = 0; i < professions.size(); i++) {
			JsonObject profession = professions.get(i);
			int cx = x1 + (i % columns) * (cellW + gap);
			int cy = y + (i / columns) * (cellH + gap);
			String key = profession.get("key").getAsString();
			boolean villainSide = "VILLAIN".equals(ClientState.str(profession, "faction", "GOOD"));
			boolean end = key.equals("shulker");
			boolean mastered = profession.get("mastered").getAsBoolean();
			int level = profession.get("level").getAsInt();
			int accent = villainSide ? Ui.VILLAIN : (end ? Ui.END : Ui.GOLD);

			graphics.fill(cx, cy, cx + cellW, cy + cellH, Ui.argb(220, villainSide ? 0x24161A : (end ? 0x1E1628 : Ui.CARD)));

			if (mastered) {
				graphics.fill(cx, cy, cx + 2, cy + cellH, Ui.argb(255, accent));
			}

			item(graphics, Ui.stack(PROFESSION_ICONS.getOrDefault(key, "minecraft:emerald"), 1), cx + 5, cy + 5, false);
			String levelText = mastered ? (villainSide ? "Arquivilão ☠" : "Mestre ★") : profession.get("levelName").getAsString();
			int levelW = this.font.width(levelText);
			graphics.text(this.font, Ui.trim(this.font, profession.get("name").getAsString(), cellW - 30 - levelW - 6), cx + 25, cy + 4,
					Ui.argb(255, villainSide ? 0xFF9A8A : (end ? 0xD8B0FF : 0xFFFFFF)), false);
			graphics.text(this.font, levelText, cx + cellW - 5 - levelW, cy + 4, Ui.argb(255, mastered ? accent : Ui.MUTED), false);

			for (int pip = 0; pip < 5; pip++) {
				int px = cx + 25 + pip * 9;
				graphics.fill(px, cy + 17, px + 7, cy + 20, Ui.argb(255, pip < level ? (mastered ? accent : (villainSide ? 0xC03030 : Ui.CYAN)) : 0x33363B));
			}

			if (!mastered) {
				String progress = profession.get("done").getAsInt() + "/" + profession.get("need").getAsInt();
				graphics.text(this.font, progress, cx + cellW - 5 - this.font.width(progress), cy + 15, Ui.argb(200, 0x7C838A), false);
			}
		}

		return y + ((professions.size() + columns - 1) / columns) * (cellH + gap);
	}

	// ---------------------------------------------------------------------------------------------
	// Guia (como funciona o mod e os comandos)
	// ---------------------------------------------------------------------------------------------

	private void drawGuide(GuiGraphicsExtractor graphics, int px1, int py1, int px2, int py2) {
		int pad = 12;
		int top = py1 + pad;
		int bottom = py2 - pad;
		int listW = Math.min(124, (int) ((px2 - px1) * 0.3));
		int x = px1 + pad;
		int y = top;

		for (int i = 0; i < Guide.TOPICS.length; i++) {
			int index = i;
			boolean hovered = hover(x, y, x + listW, y + 18);
			Ui.flatButton(graphics, this.font, x, y, x + listW, y + 18, Guide.TOPICS[i][0], i == guideTopic, hovered, true);
			hit(x, y, x + listW, y + 18, () -> {
				guideTopic = index;
				guideScroll = 0;
			});
			y += 21;
		}

		int tx1 = x + listW + 16;
		int tx2 = px2 - pad;
		int ty = top - guideScroll;
		int startY = ty;
		graphics.enableScissor(tx1, top, tx2, bottom);
		Ui.bigText(graphics, this.font, Component.literal(Guide.TOPICS[guideTopic][0]), tx1, ty, 1.5F, 0xFFFFFF);
		ty += 20;

		for (int i = 1; i < Guide.TOPICS[guideTopic].length; i++) {
			for (FormattedCharSequence line : this.font.split(Text.parse(Guide.TOPICS[guideTopic][i]), tx2 - tx1)) {
				graphics.text(this.font, line, tx1, ty, Ui.argb(255, 0xD8D8D8), false);
				ty += 10;
			}

			ty += 6;
		}

		graphics.disableScissor();
		guideHeight = ty - startY;
	}

	// ---------------------------------------------------------------------------------------------
	// Opções (o "mod menu")
	// ---------------------------------------------------------------------------------------------

	private void drawOptions(GuiGraphicsExtractor graphics, int px1, int py1, int px2, int py2) {
		int pad = 12;
		int top = py1 + pad;
		int bottom = py2 - pad;
		int x1 = px1 + pad;
		int x2 = px2 - pad;
		int y = top - scroll[4];
		int startY = y;
		graphics.enableScissor(px1, top, px2, bottom);

		sectionTitle(graphics, "SUA TELA", x1, y, Ui.CYAN_LIGHT);
		graphics.text(this.font, "só no seu computador", x1 + this.font.width("SUA TELA") + 12, y, Ui.argb(255, 0x6F767D), false);
		y += 14;
		String mode = ClientConfig.trackerMode();
		y = choiceRow(graphics, x1, x2, y, top, bottom, "Rastreador de missões", "Painel completo, ícones compactos na lateral ou escondido (tecla H).",
				new String[] {"Completo", "Compacto", "Oculto"},
				mode.equals(ClientConfig.COMPACT) ? 1 : mode.equals(ClientConfig.HIDDEN) ? 2 : 0,
				index -> ClientConfig.setTrackerMode(index == 1 ? ClientConfig.COMPACT : index == 2 ? ClientConfig.HIDDEN : ClientConfig.FULL));
		y = choiceRow(graphics, x1, x2, y, top, bottom, "Lado do rastreador compacto", "Onde os ícones das missões ficam na tela.",
				new String[] {"Esquerda", "Direita"}, ClientConfig.trackerOnRight() ? 1 : 0, index -> {
					if ((index == 1) != ClientConfig.trackerOnRight()) {
						ClientConfig.toggleTrackerSide();
					}
				});
		y = choiceRow(graphics, x1, x2, y, top, bottom, "Sons do mod", "Escrita dos NPCs, cliques do menu, missões e a fanfarra de subir de nível.",
				new String[] {"Ligado", "Desligado"}, ClientConfig.sounds() ? 0 : 1, index -> {
					if ((index == 0) != ClientConfig.sounds()) {
						ClientConfig.toggleSounds();
					}
				});
		y = choiceRow(graphics, x1, x2, y, top, bottom, "Avisos na tela", "Os avisos do mod aparecem por alguns segundos no canto da tela.",
				new String[] {"Ligado", "Desligado"}, ClientConfig.notices() ? 0 : 1, index -> {
					if ((index == 0) != ClientConfig.notices()) {
						ClientConfig.toggleNotices();
					}
				});

		y += 8;
		boolean canEdit = ClientState.getBool("canEditSettings");
		sectionTitle(graphics, "MUNDO", x1, y, Ui.CYAN_LIGHT);
		graphics.text(this.font, canEdit ? "vale para todos os jogadores" : "bloqueado: só o dono do mundo ou um operador muda",
				x1 + this.font.width("MUNDO") + 12, y, Ui.argb(255, canEdit ? 0x6F767D : 0xC08A4A), false);
		y += 14;

		for (JsonObject setting : ClientState.settings()) {
			y = settingRow(graphics, setting, x1, x2, y, top, bottom, canEdit);
		}

		y += 6;

		if (canEdit) {
			int bw = 150;
			boolean hovered = hover(x1, Math.max(y, top), x1 + bw, Math.min(y + 18, bottom));
			Ui.smallButton(graphics, this.font, x1, y, x1 + bw, y + 18, "RESTAURAR PADRÃO", 0xA03030, false, hovered);
			hitClipped(x1, y, x1 + bw, y + 18, top, bottom, () -> send("setting_reset"));
			y += 24;
		}

		graphics.disableScissor();
		contentHeight[4] = y - startY;
	}

	private interface IntAction {
		void run(int value);
	}

	private int choiceRow(GuiGraphicsExtractor graphics, int x1, int x2, int y, int top, int bottom, String name, String description,
			String[] choices, int selected, IntAction action) {
		int rowH = 26;
		graphics.fill(x1, y, x2, y + rowH, Ui.argb(220, Ui.CARD));
		graphics.text(this.font, name, x1 + 8, y + 4, Ui.argb(255, 0xFFFFFF), false);
		int choiceW = 62;
		int cx = x2 - 6 - choices.length * (choiceW + 4) + 4;
		graphics.text(this.font, Ui.trim(this.font, description, cx - x1 - 16), x1 + 8, y + 15, Ui.argb(255, 0x7C838A), false);

		if (hover(x1, y, cx, y + rowH) && y >= top && y + rowH <= bottom) {
			hoveredText = Component.literal(description);
		}

		for (int i = 0; i < choices.length; i++) {
			int index = i;
			int bx = cx + i * (choiceW + 4);
			boolean hovered = hover(bx, y + 5, bx + choiceW, y + 21);
			Ui.smallButton(graphics, this.font, bx, y + 5, bx + choiceW, y + 21, choices[i], Ui.CYAN, i == selected, hovered);
			hitClipped(bx, y + 5, bx + choiceW, y + 21, top, bottom, () -> action.run(index));
		}

		return y + rowH + 4;
	}

	private int settingRow(GuiGraphicsExtractor graphics, JsonObject setting, int x1, int x2, int y, int top, int bottom, boolean canEdit) {
		int rowH = 26;
		String key = setting.get("key").getAsString();
		boolean toggle = setting.get("toggle").getAsBoolean();
		int value = setting.get("value").getAsInt();
		int min = setting.get("min").getAsInt();
		int max = setting.get("max").getAsInt();
		int step = Math.max(1, setting.get("step").getAsInt());
		String description = setting.get("desc").getAsString();

		graphics.fill(x1, y, x2, y + rowH, Ui.argb(220, Ui.CARD));
		graphics.text(this.font, setting.get("name").getAsString(), x1 + 8, y + 4, Ui.argb(255, canEdit ? 0xFFFFFF : 0xA0A0A0), false);
		int controlW = toggle ? 128 : 112;
		int cx = x2 - 6 - controlW;
		graphics.text(this.font, Ui.trim(this.font, description, cx - x1 - 16), x1 + 8, y + 15, Ui.argb(255, 0x7C838A), false);

		if (hover(x1, y, cx, y + rowH) && y >= top && y + rowH <= bottom) {
			hoveredText = Component.literal(description);
		}

		if (toggle) {
			for (int i = 0; i < 2; i++) {
				int bx = cx + i * 66;
				boolean on = i == 0;
				boolean hovered = canEdit && hover(bx, y + 5, bx + 62, y + 21);
				Ui.smallButton(graphics, this.font, bx, y + 5, bx + 62, y + 21, on ? "Ligado" : "Desligado", on ? Ui.CYAN : 0x6A6F75,
						(value == 1) == on, hovered);

				if (canEdit) {
					hitClipped(bx, y + 5, bx + 62, y + 21, top, bottom, () -> change(setting, key, on ? 1 : 0));
				}
			}
		} else {
			String unit = setting.get("unit").getAsString();
			String text = (key.equals("bargainBonus") && value > 0 ? "+" : "") + value + unit;
			boolean minusHover = canEdit && hover(cx, y + 5, cx + 20, y + 21);
			boolean plusHover = canEdit && hover(cx + controlW - 20, y + 5, cx + controlW, y + 21);
			Ui.smallButton(graphics, this.font, cx, y + 5, cx + 20, y + 21, "-", Ui.CYAN, false, minusHover);
			Ui.smallButton(graphics, this.font, cx + controlW - 20, y + 5, cx + controlW, y + 21, "+", Ui.CYAN, false, plusHover);
			graphics.fill(cx + 22, y + 5, cx + controlW - 22, y + 21, Ui.argb(200, 0x0B0D10));
			graphics.text(this.font, text, cx + (controlW - this.font.width(text)) / 2, y + 9, Ui.argb(255, canEdit ? Ui.CYAN_LIGHT : 0xA0A0A0), false);

			if (canEdit) {
				hitClipped(cx, y + 5, cx + 20, y + 21, top, bottom, () -> change(setting, key, Math.max(min, value - step)));
				hitClipped(cx + controlW - 20, y + 5, cx + controlW, y + 21, top, bottom, () -> change(setting, key, Math.min(max, value + step)));
			}
		}

		return y + rowH + 4;
	}

	/** Manda a mudança ao servidor e já mostra o novo valor (o servidor confirma na próxima sincronização). */
	private static void change(JsonObject setting, String key, int value) {
		setting.addProperty("value", value);
		send("setting:" + key + ":" + value);
	}
}
