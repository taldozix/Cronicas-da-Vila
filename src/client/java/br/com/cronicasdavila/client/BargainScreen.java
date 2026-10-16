package br.com.cronicasdavila.client;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import br.com.cronicasdavila.net.ModPayloads;
import br.com.cronicasdavila.trade.BargainMath;
import br.com.cronicasdavila.util.Text;

/**
 * Tela de barganha: escolha uma troca do aldeão, escolha um item do seu inventário para oferecer
 * no lugar do preço e veja a chance de dar certo antes de tentar.
 */
public class BargainScreen extends Screen {
	private static final int W = 380;
	private static final int H = 236;
	private static final int ROW = 22;
	private static final int GOLD = 0x5EC4D1;

	private JsonObject node;
	private final List<JsonObject> offers = new ArrayList<>();
	private int selectedOffer = -1;
	private int selectedSlot = -1;
	private int scroll;
	private boolean waiting;
	private boolean canTry;

	public BargainScreen(JsonObject node) {
		super(Component.literal("Barganha"));
		update(node);
	}

	public void update(JsonObject node) {
		this.node = node;
		this.offers.clear();

		if (node.has("offers")) {
			for (JsonElement element : node.getAsJsonArray("offers")) {
				offers.add(element.getAsJsonObject());
			}
		}

		if (selectedOffer >= offers.size()) {
			selectedOffer = -1;
		}

		// Depois de uma barganha que deu certo, o item oferecido sumiu do inventário.
		if (node.has("success") && node.get("success").getAsBoolean()) {
			selectedSlot = -1;
		}

		waiting = false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int x0() {
		return (this.width - W) / 2;
	}

	private int y0() {
		return Math.max(4, (this.height - H) / 2);
	}

	private int listX() {
		return x0() + 8;
	}

	private int listY() {
		return y0() + 36;
	}

	private int listWidth() {
		return 176;
	}

	private int visibleRows() {
		return 6;
	}

	private int gridX() {
		return x0() + W - 8 - 162;
	}

	private int gridY() {
		return y0() + 92;
	}

	@Override
	protected void init() {
		if (this.minecraft != null) {
			this.minecraft.mouseHandler.releaseMouse();
		}
	}

	private int tryX1() {
		return x0() + W - 8 - 130;
	}

	private int backX2() {
		return x0() + 8 + 110;
	}

	private int buttonsY() {
		return y0() + H - 26;
	}

	private void goBack() {
		ClientPlayNetworking.send(new ModPayloads.ActionPayload("bargain_back"));
		onClose();
	}

	private void attempt() {
		if (selectedOffer < 0 || selectedSlot < 0 || waiting) {
			return;
		}

		waiting = true;
		ClientPlayNetworking.send(new ModPayloads.ActionPayload("bargain_try:" + offers.get(selectedOffer).get("index").getAsInt() + ":" + selectedSlot));
	}

	private static ItemStack stack(JsonObject object) {
		if (object == null) {
			return ItemStack.EMPTY;
		}

		Identifier id = Identifier.tryParse(object.get("item").getAsString());

		if (id == null) {
			return ItemStack.EMPTY;
		}

		return new ItemStack(BuiltInRegistries.ITEM.getValue(id), Math.max(1, object.get("count").getAsInt()));
	}

	private ItemStack inventoryItem(int slot) {
		if (this.minecraft == null || this.minecraft.player == null || slot < 0) {
			return ItemStack.EMPTY;
		}

		return this.minecraft.player.getInventory().getItem(slot);
	}

	/** Posição na grade: 3 linhas do inventário (slots 9 a 35) e, separada, a barra rápida (0 a 8). */
	private int slotAt(int mouseX, int mouseY) {
		for (int slot = 0; slot < 36; slot++) {
			int sx = slotX(slot);
			int sy = slotY(slot);

			if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
				return slot;
			}
		}

		return -1;
	}

	private int slotX(int slot) {
		return gridX() + (slot % 9) * 18;
	}

	private int slotY(int slot) {
		return slot < 9 ? gridY() + 3 * 18 + 4 : gridY() + (slot / 9 - 1) * 18;
	}

	private int offerAt(int mouseX, int mouseY) {
		if (mouseX < listX() || mouseX >= listX() + listWidth()) {
			return -1;
		}

		int row = (mouseY - listY()) / ROW;

		if (mouseY < listY() || row >= visibleRows()) {
			return -1;
		}

		int index = row + scroll;
		return index < offers.size() ? index : -1;
	}

	private int chance() {
		if (selectedOffer < 0 || selectedSlot < 0) {
			return -1;
		}

		ItemStack offered = inventoryItem(selectedSlot);

		if (offered.isEmpty()) {
			return -1;
		}

		JsonObject offer = offers.get(selectedOffer);
		return BargainMath.chance(BargainMath.value(offered), offer.get("cost").getAsDouble(), ClientState.num(node, "level", 1),
				node.has("mastered") && node.get("mastered").getAsBoolean(), ClientState.num(node, "karma", 0), ClientState.num(node, "bonus", 0));
	}

	// ---------------------------------------------------------------------------------------------
	// Entrada
	// ---------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (Ui.isLeftClick(event)) {
			click((int) event.x(), (int) event.y());
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	private void click(int mouseX, int mouseY) {
		int by = buttonsY();

		if (mouseY >= by && mouseY < by + 20) {
			if (mouseX >= tryX1() && mouseX < tryX1() + 130) {
				if (canTry) {
					Sfx.select();
					attempt();
				}

				return;
			}

			if (mouseX >= x0() + 8 && mouseX < backX2()) {
				Sfx.select();
				goBack();
				return;
			}
		}

		int offer = offerAt(mouseX, mouseY);

		if (offer >= 0) {
			Sfx.select();
			selectedOffer = offer;
			return;
		}

		int slot = slotAt(mouseX, mouseY);

		if (slot >= 0) {
			Sfx.select();
			selectedSlot = inventoryItem(slot).isEmpty() ? -1 : slot;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int max = Math.max(0, offers.size() - visibleRows());
		scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
		return true;
	}

	// ---------------------------------------------------------------------------------------------
	// Desenho
	// ---------------------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, this.width, this.height, Ui.argb(140, 0x000000));
		Ui.panel(graphics, x0(), y0(), x0() + W, y0() + H, 1F);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		boolean refused = node.has("refused") && node.get("refused").getAsBoolean();
		int chance = chance();
		canTry = !waiting && !refused && chance >= 0 && selectedOffer >= 0 && !offers.get(selectedOffer).get("out").getAsBoolean();
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		// Botões
		int by = buttonsY();
		boolean tryHover = mouseX >= tryX1() && mouseX < tryX1() + 130 && mouseY >= by && mouseY < by + 20;
		boolean backHover = mouseX >= x0() + 8 && mouseX < backX2() && mouseY >= by && mouseY < by + 20;
		Ui.smallButton(graphics, this.font, tryX1(), by, tryX1() + 130, by + 20, canTry ? "⚖ TENTAR BARGANHA" : "ESCOLHA TROCA E ITEM",
				Ui.CYAN, canTry && tryHover, canTry && tryHover);
		Ui.smallButton(graphics, this.font, x0() + 8, by, backX2(), by + 20, "VOLTAR ÀS TROCAS", Ui.CYAN, false, backHover);

		int x0 = x0();
		int y0 = y0();
		graphics.text(this.font, Component.literal("⚖ Barganha com " + ClientState.str(node, "name", "o aldeão")).withStyle(style -> style.withBold(true)),
				x0 + 10, y0 + 8, Ui.argb(255, GOLD), true);
		graphics.text(this.font, ClientState.str(node, "professionName", "") + "  ·  Sua afinidade: " + ClientState.str(node, "levelName", ""),
				x0 + 10, y0 + 20, Ui.argb(255, 0xBBBBBB), false);

		ItemStack hovered = ItemStack.EMPTY;

		// Trocas do aldeão
		graphics.text(this.font, "Escolha a troca:", listX(), listY() - 10 + 0, Ui.argb(255, 0xDDDDDD), false);

		for (int row = 0; row < visibleRows() && row + scroll < offers.size(); row++) {
			int index = row + scroll;
			JsonObject offer = offers.get(index);
			int ry = listY() + row * ROW;
			boolean out = offer.get("out").getAsBoolean();
			boolean selected = index == selectedOffer;
			Ui.roundedRect(graphics, listX(), ry, listX() + listWidth(), ry + ROW - 2, Ui.argb(selected ? 170 : 110, selected ? 0x3A2E10 : 0x16161C));

			if (selected) {
				Ui.roundedOutline(graphics, listX(), ry, listX() + listWidth(), ry + ROW - 2, Ui.argb(255, GOLD));
			}

			ItemStack result = stack(offer.getAsJsonObject("result"));
			graphics.item(result, listX() + 3, ry + 2);
			graphics.itemDecorations(this.font, result, listX() + 3, ry + 2);
			String label = ClientState.str(offer.getAsJsonObject("result"), "label", "");

			if (this.font.width(label) > 92) {
				label = this.font.plainSubstrByWidth(label, 88) + "…";
			}

			graphics.text(this.font, out ? "Esgotado" : label, listX() + 23, ry + 6, Ui.argb(255, out ? 0x777777 : 0xFFFFFF), false);

			ItemStack costA = stack(offer.getAsJsonObject("costA"));
			int cx = listX() + listWidth() - 40;
			graphics.item(costA, cx, ry + 2);
			graphics.itemDecorations(this.font, costA, cx, ry + 2);

			if (offer.has("costB")) {
				ItemStack costB = stack(offer.getAsJsonObject("costB"));
				graphics.item(costB, cx + 19, ry + 2);
				graphics.itemDecorations(this.font, costB, cx + 19, ry + 2);
			}

			if (mouseY >= ry && mouseY < ry + ROW - 2) {
				if (mouseX >= listX() + 3 && mouseX < listX() + 19) {
					hovered = result;
				} else if (mouseX >= cx && mouseX < cx + 16) {
					hovered = costA;
				}
			}
		}

		if (offers.size() > visibleRows()) {
			graphics.text(this.font, "▲▼ role para ver mais", listX(), listY() + visibleRows() * ROW + 1, Ui.argb(160, 0x999999), false);
		}

		// O item oferecido
		int rx = gridX();
		graphics.text(this.font, "Sua oferta (no lugar do preço):", rx, listY() - 10, Ui.argb(255, 0xDDDDDD), false);
		Ui.roundedRect(graphics, rx, listY(), rx + 22, listY() + 22, Ui.argb(180, 0x000000));
		Ui.roundedOutline(graphics, rx, listY(), rx + 22, listY() + 22, Ui.argb(255, selectedSlot >= 0 ? GOLD : 0x555555));
		ItemStack offered = inventoryItem(selectedSlot);

		if (!offered.isEmpty()) {
			graphics.item(offered, rx + 3, listY() + 3);
			graphics.itemDecorations(this.font, offered, rx + 3, listY() + 3);
		}

		String hint = selectedSlot < 0 ? "Clique num item abaixo" : offered.getHoverName().getString();
		graphics.text(this.font, this.font.plainSubstrByWidth(hint, 132), rx + 28, listY() + 2, Ui.argb(255, 0xFFFFFF), false);

		if (chance >= 0) {
			int color = chance >= 60 ? 0x55FF55 : (chance >= 30 ? 0xFFFF55 : 0xFF5555);
			graphics.text(this.font, "Chance: " + chance + "%", rx + 28, listY() + 13, Ui.argb(255, color), true);
			Ui.bar(graphics, rx, listY() + 28, 162, 4, chance / 100F, Ui.argb(255, color));
		} else {
			graphics.text(this.font, "Chance: —", rx + 28, listY() + 13, Ui.argb(255, 0x888888), false);
		}

		// Inventário
		graphics.text(this.font, "Seu inventário:", rx, gridY() - 10, Ui.argb(255, 0xDDDDDD), false);

		for (int slot = 0; slot < 36; slot++) {
			int sx = slotX(slot);
			int sy = slotY(slot);
			graphics.fill(sx, sy, sx + 17, sy + 17, Ui.argb(150, slot == selectedSlot ? 0x5A4A18 : 0x222228));
			ItemStack stack = inventoryItem(slot);

			if (!stack.isEmpty()) {
				graphics.item(stack, sx + 1, sy + 1);
				graphics.itemDecorations(this.font, stack, sx + 1, sy + 1);

				if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
					hovered = stack;
				}
			}
		}

		// Mensagens
		String message = node.has("message") ? node.get("message").getAsString()
				: refused ? "<r>Ele ficou ofendido com a última barganha. Volte amanhã.</r>"
				: "<d>Se ele recusar, você fica com o seu item, mas ele não barganha mais com você hoje.</d>";
		List<FormattedCharSequence> lines = this.font.split(Text.parse(message), W - 20);
		int my = y0 + H - 50;

		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			graphics.text(this.font, lines.get(i), x0 + 10, my + i * 10, Ui.argb(255, 0xEEEEEE), true);
		}

		if (!hovered.isEmpty()) {
			graphics.setTooltipForNextFrame(this.font, hovered, mouseX, mouseY);
		}
	}
}
