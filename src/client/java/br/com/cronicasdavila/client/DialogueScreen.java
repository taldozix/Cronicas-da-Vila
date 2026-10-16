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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import br.com.cronicasdavila.net.ModPayloads;
import br.com.cronicasdavila.util.Text;

/**
 * A caixa de diálogo, no estilo Zelda: Breath of the Wild.
 *
 * <ul>
 *     <li>Texto aparece letra por letra; clique/Enter/Espaço completa ou avança.</li>
 *     <li>As respostas aparecem à direita; escolha com o mouse ou com as setas ↑ ↓ (ou W/S) e confirme com Enter/Espaço/clique.</li>
 *     <li>Enquanto a conversa acontece, a câmera fica travada olhando para o NPC e o jogador não anda.</li>
 * </ul>
 */
public class DialogueScreen extends Screen {
	private static final int CHARS_PER_SECOND = 55;
	private static final int LINE_HEIGHT = 11;

	private int entityId;
	private String speaker = "";
	private String subtitle = "";
	private String tone = "neutral";
	private boolean nether;
	private String kind = "villager";
	private final List<String> pages = new ArrayList<>();
	private final List<ChoiceView> choices = new ArrayList<>();

	private int pageIndex;
	private List<Text.Segment> segments = List.of();
	private int pageLength;
	private long pageStart;
	private boolean waitingServer;

	private int selected;
	private int hovered = -1;
	private int lastMouseX = -1;
	private int lastMouseY = -1;
	private long openedAt;
	private long choicesShownAt = -1;
	boolean closedByServer;

	private record ChoiceView(String id, List<Text.Segment> segments, String style) {
	}

	public DialogueScreen(JsonObject node) {
		super(Component.literal("Diálogo"));
		this.openedAt = System.currentTimeMillis();
		setNode(node);
	}

	@Override
	protected void init() {
		// Solta o mouse para dar para clicar nas respostas (a câmera continua travada no NPC).
		if (this.minecraft != null) {
			this.minecraft.mouseHandler.releaseMouse();
		}
	}

	public int entityId() {
		return entityId;
	}

	/** Recebe um novo "nó" de conversa do servidor. */
	public void setNode(JsonObject node) {
		this.entityId = node.get("entity").getAsInt();
		this.speaker = node.get("speaker").getAsString();
		this.subtitle = node.has("subtitle") ? node.get("subtitle").getAsString() : "";
		this.tone = node.has("tone") ? node.get("tone").getAsString() : "neutral";
		this.nether = node.has("nether") && node.get("nether").getAsBoolean();
		this.kind = node.has("kind") ? node.get("kind").getAsString() : (nether ? "piglin" : "villager");

		this.pages.clear();

		for (JsonElement element : node.getAsJsonArray("pages")) {
			this.pages.add(element.getAsString());
		}

		if (this.pages.isEmpty()) {
			this.pages.add("...");
		}

		this.choices.clear();

		for (JsonElement element : node.getAsJsonArray("choices")) {
			JsonObject choice = element.getAsJsonObject();
			this.choices.add(new ChoiceView(choice.get("id").getAsString(), Text.segments(choice.get("text").getAsString()),
					choice.has("style") ? choice.get("style").getAsString() : "neutral"));
		}

		this.selected = 0;
		this.hovered = -1;
		this.waitingServer = false;
		this.choicesShownAt = -1;
		showPage(0);
	}

	/** Quantas letras já "fizeram barulho" nesta página (som de escrita). */
	private int typedChars;

	private void showPage(int index) {
		this.pageIndex = index;
		this.typedChars = 0;
		this.segments = Text.segments(pages.get(index));
		this.pageLength = Text.visibleLength(segments);
		this.pageStart = System.currentTimeMillis();
	}

	private int visibleChars() {
		long elapsed = System.currentTimeMillis() - pageStart;
		return (int) Math.min(pageLength, elapsed * CHARS_PER_SECOND / 1000L);
	}

	private boolean pageFullyShown() {
		return visibleChars() >= pageLength;
	}

	private boolean onLastPage() {
		return pageIndex >= pages.size() - 1;
	}

	private boolean choicesVisible() {
		return onLastPage() && pageFullyShown() && !choices.isEmpty() && !waitingServer;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------------------------------------
	// Lógica de avanço
	// ---------------------------------------------------------------------------------------------

	private void advance() {
		if (waitingServer) {
			return;
		}

		if (!pageFullyShown()) {
			pageStart = 0; // mostra o resto da página na hora
			return;
		}

		if (!onLastPage()) {
			showPage(pageIndex + 1);
			return;
		}

		if (choices.isEmpty()) {
			onClose();
		}
	}

	private void confirm(int index) {
		if (!choicesVisible() || index < 0 || index >= choices.size()) {
			return;
		}

		waitingServer = true;
		Sfx.select();
		ClientPlayNetworking.send(new ModPayloads.ActionPayload("choice:" + choices.get(index).id()));
	}

	private void moveSelection(int delta) {
		if (choices.isEmpty()) {
			return;
		}

		int before = selected;
		selected = Math.floorMod(selected + delta, choices.size());

		if (selected != before) {
			Sfx.play(br.com.cronicasdavila.sound.ModSounds.SELECT, 1.25F, 0.3F);
		}
	}

	@Override
	public void onClose() {
		if (!closedByServer) {
			ClientPlayNetworking.send(new ModPayloads.ActionPayload("close"));
		}

		super.onClose();
	}

	// ---------------------------------------------------------------------------------------------
	// Entrada: teclado e mouse
	// ---------------------------------------------------------------------------------------------

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();

		if (key == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}

		boolean confirmKey = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER
				|| key == InputConstants.KEY_SPACE || key == InputConstants.KEY_E;

		if (choicesVisible()) {
			if (key == InputConstants.KEY_UP || key == InputConstants.KEY_W) {
				moveSelection(-1);
				return true;
			}

			if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_S) {
				moveSelection(1);
				return true;
			}

			if (confirmKey) {
				confirm(selected);
				return true;
			}

			if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) {
				int index = key - InputConstants.KEY_1;

				if (index < choices.size()) {
					selected = index;
					confirm(index);
				}

				return true;
			}
		} else if (confirmKey) {
			advance();
			return true;
		}

		return super.keyPressed(event);
	}

	private final MousePoll mouse = new MousePoll();

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (Ui.isLeftClick(event) && mouse.claim()) {
			click((int) event.x(), (int) event.y());
		}

		return true;
	}

	/** Clique: escolhe a resposta embaixo do mouse ou avança o texto. */
	private void click(int x, int y) {
		if (choicesVisible()) {
			int index = choiceAt(x, y);

			if (index >= 0) {
				selected = index;
				confirm(index);
			}

			return;
		}

		advance();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (choicesVisible() && scrollY != 0) {
			moveSelection(scrollY > 0 ? -1 : 1);
			return true;
		}

		return false;
	}

	// ---------------------------------------------------------------------------------------------
	// Layout
	// ---------------------------------------------------------------------------------------------

	private int boxWidth() {
		return Math.min(this.width - 40, 460);
	}

	private int boxX() {
		return (this.width - boxWidth()) / 2;
	}

	private List<FormattedCharSequence> wrapped(int maxChars) {
		MutableComponent text = Text.build(segments, maxChars);
		return this.font.split(text, boxWidth() - 36);
	}

	private int boxHeight() {
		int lines = Math.max(3, this.font.split(Text.build(segments, Integer.MAX_VALUE), boxWidth() - 36).size());
		return 34 + lines * LINE_HEIGHT;
	}

	private int boxY() {
		return this.height - boxHeight() - 30;
	}

	private int choiceWidth() {
		int max = 120;

		for (ChoiceView choice : choices) {
			max = Math.max(max, this.font.width(Text.build(choice.segments(), Integer.MAX_VALUE)) + 34);
		}

		return Math.min(max, this.width - 40);
	}

	private int choiceHeight() {
		return 18;
	}

	/** Posição Y do topo de cada escolha (empilhadas para cima a partir da caixa). */
	private int choiceY(int index) {
		int count = choices.size();
		int top = boxY() - 10 - count * (choiceHeight() + 3);
		return top + index * (choiceHeight() + 3);
	}

	private int choiceX() {
		return boxX() + boxWidth() - choiceWidth();
	}

	private int choiceAt(int mouseX, int mouseY) {
		int x1 = choiceX();
		int x2 = x1 + choiceWidth();

		for (int i = 0; i < choices.size(); i++) {
			int y1 = choiceY(i);

			if (mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y1 + choiceHeight()) {
				return i;
			}
		}

		return -1;
	}

	// ---------------------------------------------------------------------------------------------
	// Desenho
	// ---------------------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// Sem desfoque/escurecimento: o mundo continua visível, como numa cena de cinema.
		float t = Ui.ease((System.currentTimeMillis() - openedAt) / 350F);
		int bar = (int) (26 * t);
		graphics.fill(0, 0, this.width, bar, Ui.argb(235, 0x000000));
		graphics.fill(0, this.height - bar, this.width, this.height, Ui.argb(235, 0x000000));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		lockCamera();
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		// Clique lido direto do mouse (funciona mesmo se o evento do jogo não chegar).
		if (mouse.pressed() && mouse.claim()) {
			click(mouseX, mouseY);
		}

		float fade = Ui.ease((System.currentTimeMillis() - openedAt) / 250F);
		int boxX = boxX();
		int boxY = boxY();
		int boxW = boxWidth();
		int boxH = boxHeight();
		int slide = (int) ((1F - fade) * 12);

		// Caixa principal
		Ui.panel(graphics, boxX, boxY + slide, boxX + boxW, boxY + boxH + slide, fade);
		graphics.fill(boxX + 14, boxY + slide + 1, boxX + boxW - 14, boxY + slide + 2, Ui.argb((int) (170 * fade), accentColor()));

		// Nome de quem fala
		int nameColor = switch (kind) {
			case "piglin" -> 0xFF8A3D;
			case "pillager" -> 0xE05A5A;
			case "shulker" -> 0xD08CFF;
			case "witch" -> 0xB57EDC;
			default -> 0xFFD36B;
		};
		graphics.text(this.font, Component.literal(speaker).withStyle(style -> style.withBold(true)), boxX + 16, boxY + slide + 8,
				Ui.argb((int) (255 * fade), nameColor), true);
		int nameWidth = this.font.width(Component.literal(speaker).withStyle(style -> style.withBold(true)));

		if (!subtitle.isEmpty()) {
			graphics.text(this.font, subtitle, boxX + 22 + nameWidth, boxY + slide + 8, Ui.argb((int) (150 * fade), 0xBBBBBB), false);
		}

		// Texto com efeito de máquina de escrever (e o "blip" da voz de cada NPC)
		int visible = visibleChars();

		if (visible > typedChars && visible < pageLength) {
			Sfx.typing(kind, subtitle.startsWith("Criança"));
		}

		typedChars = visible;
		List<FormattedCharSequence> lines = wrapped(visible);
		int textY = boxY + slide + 22;

		for (FormattedCharSequence line : lines) {
			graphics.text(this.font, line, boxX + 18, textY, Ui.argb((int) (255 * fade), 0xF2F2F2), true);
			textY += LINE_HEIGHT;
		}

		// Indicador "continuar"
		if (pageFullyShown() && (!onLastPage() || choices.isEmpty()) && !waitingServer) {
			int bob = (int) (Math.sin(System.currentTimeMillis() / 180.0) * 2);
			Ui.triangleDown(graphics, boxX + boxW - 18, boxY + boxH - 12 + bob, 4, Ui.argb(230, 0xFFFFFF));
		}

		// Escolhas
		if (choicesVisible()) {
			if (choicesShownAt < 0) {
				choicesShownAt = System.currentTimeMillis();
			}

			if (mouseX != lastMouseX || mouseY != lastMouseY) {
				lastMouseX = mouseX;
				lastMouseY = mouseY;
				int over = choiceAt(mouseX, mouseY);

				if (over >= 0) {
					selected = over;
				}
			}

			drawChoices(graphics);
		}

		drawHints(graphics);
	}

	private int accentColor() {
		return switch (tone) {
			case "danger" -> 0xFF4444;
			case "calm" -> 0x66FF88;
			case "nether" -> 0xFF7733;
			case "villain" -> 0xB01E2E;
			case "end" -> 0xC77DFF;
			case "swamp" -> 0x7FBF5F;
			default -> 0xFFFFFF;
		};
	}

	private void drawChoices(GuiGraphicsExtractor graphics) {
		float appear = Ui.ease((System.currentTimeMillis() - choicesShownAt) / 200F);
		int x1 = choiceX();
		int x2 = x1 + choiceWidth();
		long now = System.currentTimeMillis();

		for (int i = 0; i < choices.size(); i++) {
			ChoiceView choice = choices.get(i);
			int y1 = choiceY(i);
			int offset = (int) ((1F - appear) * (16 + i * 6));
			boolean isSelected = i == selected;

			if (isSelected) {
				float pulse = (float) (0.6 + 0.4 * Math.sin(now / 160.0));
				Ui.roundedRect(graphics, x1 + offset - 1, y1 - 1, x2 + offset + 1, y1 + choiceHeight() + 1, Ui.argb((int) (90 * pulse * appear), 0xFFFFFF));
				Ui.roundedRect(graphics, x1 + offset, y1, x2 + offset, y1 + choiceHeight(), Ui.argb((int) (235 * appear), 0x1F1F2A));
				Ui.roundedOutline(graphics, x1 + offset, y1, x2 + offset, y1 + choiceHeight(), Ui.argb((int) (255 * pulse * appear), 0xFFFFFF));
				Ui.triangleRight(graphics, x1 + offset - 9, y1 + choiceHeight() / 2, 4, Ui.argb((int) (255 * appear), 0xFFFFFF));
			} else {
				Ui.roundedRect(graphics, x1 + offset, y1, x2 + offset, y1 + choiceHeight(), Ui.argb((int) (190 * appear), 0x0B0B0F));
				Ui.roundedOutline(graphics, x1 + offset, y1, x2 + offset, y1 + choiceHeight(), Ui.argb((int) (45 * appear), 0xFFFFFF));
			}

			int color = styleColor(choice.style());
			graphics.text(this.font, Text.build(choice.segments(), Integer.MAX_VALUE), x1 + offset + 12, y1 + 5,
					Ui.argb((int) ((isSelected ? 255 : 200) * appear), color), true);
		}
	}

	private static int styleColor(String style) {
		return switch (style) {
			case "accept" -> 0x8CFF8C;
			case "decline" -> 0xBBBBBB;
			case "danger" -> 0xFF7070;
			case "trade" -> 0xFFD24A;
			case "magic" -> 0xD9A0FF;
			case "quest" -> 0xFFE97A;
			case "info" -> 0x9BE7FF;
			default -> 0xFFFFFF;
		};
	}

	private void drawHints(GuiGraphicsExtractor graphics) {
		String hint = choicesVisible()
				? "↑↓ / mouse: escolher    Enter / Espaço / clique: confirmar    Esc: sair"
				: "Enter / Espaço / clique: continuar    Esc: sair";
		int width = this.font.width(hint);
		graphics.text(this.font, hint, (this.width - width) / 2, this.height - 17, Ui.argb(140, 0xCCCCCC), false);
	}

	/** Mantém a câmera do jogador virada suavemente para o rosto do NPC. */
	private void lockCamera() {
		LocalPlayer player = this.minecraft.player;

		if (player == null || this.minecraft.level == null) {
			return;
		}

		Entity npc = this.minecraft.level.getEntity(entityId);

		if (npc == null) {
			return;
		}

		Vec3 eye = player.getEyePosition();
		Vec3 target = npc.getEyePosition();
		double dx = target.x - eye.x;
		double dy = target.y - eye.y;
		double dz = target.z - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float wantedYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float wantedPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
		float yaw = approach(player.getYRot(), wantedYaw, 0.18F);
		float pitch = approach(player.getXRot(), wantedPitch, 0.18F);
		player.setYRot(yaw);
		player.setXRot(pitch);
		player.yRotO = yaw;
		player.xRotO = pitch;
	}

	private static float approach(float current, float target, float factor) {
		float diff = target - current;

		while (diff < -180F) {
			diff += 360F;
		}

		while (diff >= 180F) {
			diff -= 360F;
		}

		return current + diff * factor;
	}
}
