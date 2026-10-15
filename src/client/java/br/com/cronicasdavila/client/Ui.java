package br.com.cronicasdavila.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Pequenos utilitários de desenho para a interface "estilo Breath of the Wild":
 * painéis escuros translúcidos com cantos arredondados e contornos suaves.
 */
public final class Ui {
	private Ui() {
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
