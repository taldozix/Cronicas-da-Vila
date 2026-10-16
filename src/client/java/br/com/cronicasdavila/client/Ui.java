package br.com.cronicasdavila.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Pequenos utilitários de desenho para a interface "estilo Breath of the Wild":
 * painéis escuros translúcidos com cantos arredondados e contornos suaves.
 */
public final class Ui {
	/** Paleta do menu (inspirada nas telas de servidores modded: painéis escuros chapados e um destaque ciano). */
	public static final int CYAN = 0x5EC4D1;
	public static final int CYAN_LIGHT = 0x9BE7F0;
	public static final int LAVENDER = 0x8B8FD8;
	public static final int CREAM = 0xF2E2B3;
	public static final int CREAM_SHADOW = 0x5E4E30;
	public static final int PANEL = 0x0E1013;
	public static final int BUTTON = 0x17191D;
	public static final int BUTTON_HOVER = 0x24282E;
	public static final int CARD = 0x1A1D22;
	public static final int TEXT = 0xE8E8E8;
	public static final int MUTED = 0x9AA0A6;
	public static final int VILLAIN = 0xE05A5A;
	public static final int END = 0xC48CFF;
	public static final int GOLD = 0xFFD36B;
	public static final int GOOD = 0x6EE07A;

	private Ui() {
	}

	/**
	 * Clique com o botão esquerdo. O número do botão mudou nesta versão do jogo (o esquerdo não é mais 0),
	 * então usamos a constante do próprio Minecraft.
	 */
	public static boolean isLeftClick(net.minecraft.client.input.MouseButtonEvent event) {
		// No 26.3 o esquerdo chega como 1 (o direito e o do meio têm outros números).
		return event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT || event.button() == 1;
	}

	/** Painel chapado e escuro, sem bordas (como na tela de referência). */
	public static void flatPanel(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int alpha) {
		g.fill(x1, y1, x2, y2, argb(alpha, PANEL));
	}

	/** Botão reto do menu lateral. Selecionado = ciano. */
	public static void flatButton(GuiGraphicsExtractor g, Font font, int x1, int y1, int x2, int y2, String label,
			boolean selected, boolean hovered, boolean enabled) {
		int bg = selected ? CYAN : (hovered && enabled ? BUTTON_HOVER : BUTTON);
		g.fill(x1, y1, x2, y2, argb(selected ? 235 : 215, bg));
		int color = !enabled ? 0x666A70 : (selected ? 0xFFFFFF : (hovered ? 0xFFFFFF : 0xC9CDD2));
		g.text(font, label, x1 + 10, y1 + (y2 - y1 - 8) / 2, argb(255, color), selected);
	}

	/**
	 * Botão da barra lateral com animação: o destaque do mouse e o ciano da seleção
	 * deslizam da esquerda para a direita, e o texto anda um pouco para o lado.
	 */
	public static void animatedButton(GuiGraphicsExtractor g, Font font, int x1, int y1, int x2, int y2, String label,
			float selected, float hovered) {
		int width = x2 - x1;
		g.fill(x1, y1, x2, y2, argb(215, BUTTON));

		if (hovered > 0.01F) {
			g.fill(x1, y1, x1 + (int) (width * hovered), y2, argb(230, BUTTON_HOVER));
		}

		if (selected > 0.01F) {
			g.fill(x1, y1, x1 + (int) (width * selected), y2, argb(240, CYAN));
		}

		if (hovered > 0.01F && selected < 0.5F) {
			int barH = (int) ((y2 - y1) * hovered);
			int top = y1 + (y2 - y1 - barH) / 2;
			g.fill(x1, top, x1 + 2, top + barH, argb(255, CYAN));
		}

		float lift = Math.max(selected, hovered);
		int color = selected > 0.5F || hovered > 0.5F ? 0xFFFFFF : 0xC9CDD2;
		g.text(font, label, x1 + 10 + (int) (4 * lift), y1 + (y2 - y1 - 8) / 2, argb(255, color), selected > 0.5F);
	}

	/** Botão pequeno com o texto centralizado (para ações dentro dos painéis). */
	public static void smallButton(GuiGraphicsExtractor g, Font font, int x1, int y1, int x2, int y2, String label,
			int accent, boolean active, boolean hovered) {
		int bg = active ? accent : (hovered ? BUTTON_HOVER : BUTTON);
		g.fill(x1, y1, x2, y2, argb(active ? 230 : 220, bg));

		if (!active) {
			g.fill(x1, y2 - 1, x2, y2, argb(160, accent));
		}

		int w = font.width(label);
		g.text(font, label, x1 + (x2 - x1 - w) / 2, y1 + (y2 - y1 - 8) / 2, argb(255, active || hovered ? 0xFFFFFF : 0xC9CDD2), active);
	}

	/** Texto ampliado com transparência (cores já em ARGB). */
	public static void bigTextArgb(GuiGraphicsExtractor g, Font font, String text, int x, int y, float scale, int argbColor, int argbShadow) {
		g.pose().pushMatrix();
		g.pose().scale(scale, scale);
		int sx = Math.round(x / scale);
		int sy = Math.round(y / scale);
		g.text(font, text, sx + 1, sy + 1, argbShadow, false);
		g.text(font, text, sx, sy, argbColor, false);
		g.pose().popMatrix();
	}

	/** Passa um pouco do ponto e volta (efeito de "pulo"). */
	public static float easeOutBack(float t) {
		t = Math.max(0F, Math.min(1F, t));
		float c1 = 1.70158F;
		float c3 = c1 + 1F;
		return 1F + c3 * (float) Math.pow(t - 1F, 3) + c1 * (float) Math.pow(t - 1F, 2);
	}

	/** Escurece uma cor RGB (para sombras). */
	public static int darker(int rgb, float factor) {
		int r = (int) (((rgb >> 16) & 0xFF) * factor);
		int gr = (int) (((rgb >> 8) & 0xFF) * factor);
		int b = (int) ((rgb & 0xFF) * factor);
		return (r << 16) | (gr << 8) | b;
	}

	/** Texto ampliado (o título grande do menu). */
	public static void bigText(GuiGraphicsExtractor g, Font font, String text, int x, int y, float scale, int color, int shadowColor) {
		g.pose().pushMatrix();
		g.pose().scale(scale, scale);
		int sx = Math.round(x / scale);
		int sy = Math.round(y / scale);

		if (shadowColor != 0) {
			g.text(font, text, sx + 1, sy + 1, argb(255, shadowColor), false);
		}

		g.text(font, text, sx, sy, argb(255, color), false);
		g.pose().popMatrix();
	}

	public static void bigText(GuiGraphicsExtractor g, Font font, Component text, int x, int y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().scale(scale, scale);
		g.text(font, text, Math.round(x / scale), Math.round(y / scale), argb(255, color), true);
		g.pose().popMatrix();
	}

	/** Largura do texto com espaço entre as letras ("M O D D E D   S M P"). */
	public static int spacedWidth(Font font, String text, int spacing) {
		int width = 0;

		for (int i = 0; i < text.length(); i++) {
			width += font.width(String.valueOf(text.charAt(i))) + spacing;
		}

		return Math.max(0, width - spacing);
	}

	public static void spacedText(GuiGraphicsExtractor g, Font font, String text, int x, int y, int spacing, int color) {
		int cx = x;

		for (int i = 0; i < text.length(); i++) {
			String letter = String.valueOf(text.charAt(i));
			g.text(font, letter, cx, y, argb(255, color), true);
			cx += font.width(letter) + spacing;
		}
	}

	/** Pilha de item a partir do id ("minecraft:diamond"). */
	public static ItemStack stack(String id, int count) {
		Identifier identifier = id == null ? null : Identifier.tryParse(id);

		if (identifier == null) {
			return ItemStack.EMPTY;
		}

		return new ItemStack(BuiltInRegistries.ITEM.getValue(identifier), Math.max(1, count));
	}

	/** Primeira linha de um texto colorido que cabe na largura. */
	public static net.minecraft.util.FormattedCharSequence trimComponent(Font font, Component text, int width) {
		java.util.List<net.minecraft.util.FormattedCharSequence> lines = font.split(text, Math.max(10, width));
		return lines.isEmpty() ? net.minecraft.util.FormattedCharSequence.EMPTY : lines.get(0);
	}

	/** Corta o texto com "…" para caber na largura. */
	public static String trim(Font font, String text, int width) {
		if (font.width(text) <= width) {
			return text;
		}

		return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
	}

	public static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	/** Retângulo preenchido com cantos arredondados (sem sobreposição, para não somar transparência). */
	public static void roundedRect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
		if (x2 - x1 < 4 || y2 - y1 < 4) {
			g.fill(x1, y1, x2, y2, color);
			return;
		}

		g.fill(x1 + 2, y1, x2 - 2, y2, color);
		g.fill(x1, y1 + 2, x1 + 1, y2 - 2, color);
		g.fill(x1 + 1, y1 + 1, x1 + 2, y2 - 1, color);
		g.fill(x2 - 2, y1 + 1, x2 - 1, y2 - 1, color);
		g.fill(x2 - 1, y1 + 2, x2, y2 - 2, color);
	}

	/** Contorno arredondado de 1 pixel. */
	public static void roundedOutline(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
		g.fill(x1 + 2, y1, x2 - 2, y1 + 1, color);
		g.fill(x1 + 2, y2 - 1, x2 - 2, y2, color);
		g.fill(x1, y1 + 2, x1 + 1, y2 - 2, color);
		g.fill(x2 - 1, y1 + 2, x2, y2 - 2, color);
		g.fill(x1 + 1, y1 + 1, x1 + 2, y1 + 2, color);
		g.fill(x2 - 2, y1 + 1, x2 - 1, y1 + 2, color);
		g.fill(x1 + 1, y2 - 2, x1 + 2, y2 - 1, color);
		g.fill(x2 - 2, y2 - 2, x2 - 1, y2 - 1, color);
	}

	/** Painel padrão: fundo escuro translúcido + contorno claro discreto. */
	public static void panel(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, float alpha) {
		roundedRect(g, x1, y1, x2, y2, argb((int) (200 * alpha), 0x0B0B0F));
		roundedOutline(g, x1, y1, x2, y2, argb((int) (60 * alpha), 0xFFFFFF));
	}

	/** Triângulo apontando para baixo (indicador "continuar"). */
	public static void triangleDown(GuiGraphicsExtractor g, int cx, int y, int size, int color) {
		for (int row = 0; row < size; row++) {
			int half = size - row;
			g.fill(cx - half, y + row, cx + half, y + row + 1, color);
		}
	}

	/** Triângulo apontando para a direita (cursor de seleção). */
	public static void triangleRight(GuiGraphicsExtractor g, int x, int cy, int size, int color) {
		for (int col = 0; col < size; col++) {
			int half = size - col;
			g.fill(x + col, cy - half, x + col + 1, cy + half, color);
		}
	}

	/** Barra de progresso. */
	public static void bar(GuiGraphicsExtractor g, int x, int y, int width, int height, float fraction, int color) {
		g.fill(x, y, x + width, y + height, argb(160, 0x000000));
		int filled = (int) (width * Math.max(0F, Math.min(1F, fraction)));

		if (filled > 0) {
			g.fill(x, y, x + filled, y + height, color);
		}

		roundedOutline(g, x - 1, y - 1, x + width + 1, y + height + 1, argb(80, 0xFFFFFF));
	}

	public static float ease(float t) {
		t = Math.max(0F, Math.min(1F, t));
		return 1F - (1F - t) * (1F - t) * (1F - t);
	}
}
