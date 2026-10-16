package br.com.cronicasdavila.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.google.gson.JsonObject;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

import br.com.cronicasdavila.util.Text;

/**
 * O que o mod desenha por cima do jogo: o rastreador de missões (completo ou compacto)
 * e os avisos que antes iam para o chat.
 */
final class Hud {
	private static final long NOTICE_MS = 6000L;

	private Hud() {
	}

	// ---------------------------------------------------------------------------------------------
	// Rastreador de missões
	// ---------------------------------------------------------------------------------------------

	static void drawTracker(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null || client.gui.screen() != null) {
			return;
		}

		// Ao trocar de modo, o antigo sai deslizando para a lateral e o novo entra.
		float t = Ui.ease((System.currentTimeMillis() - ClientConfig.trackerChangedAt()) / 280F);

		if (t < 1F) {
			draw(graphics, client, ClientConfig.previousTrackerMode(), t);
		}

		draw(graphics, client, ClientConfig.trackerMode(), 1F - t);
	}

	/** Desenha um modo deslocado para fora da tela ("hidden" = 0 é no lugar, 1 é totalmente fora). */
	private static void draw(GuiGraphicsExtractor graphics, Minecraft client, String mode, float hidden) {
		switch (mode) {
			case ClientConfig.COMPACT -> drawCompact(graphics, client, hidden);
			case ClientConfig.HIDDEN -> {
			}
			default -> drawFull(graphics, client, hidden);
		}
	}

	private static int percent(JsonObject quest) {
		int target = Math.max(1, ClientState.num(quest, "target", 1));
		return Math.min(100, ClientState.num(quest, "progress", 0) * 100 / target);
	}

	/** Painel com a missão rastreada: ícone, título, objetivo e barra. */
	private static void drawFull(GuiGraphicsExtractor graphics, Minecraft client, float hidden) {
		JsonObject quest = ClientState.trackedQuest();

		if (quest == null) {
			return;
		}

		int progress = ClientState.num(quest, "progress", 0);
		int target = Math.max(1, ClientState.num(quest, "target", 1));
		boolean done = progress >= target;
		int width = 196;
		int x = 6 - (int) ((width + 12) * hidden);
		int y = 6;

		List<FormattedCharSequence> objective = client.font.split(Text.parse(done
				? "<g>✔ Volte e fale com " + ClientState.str(quest, "giver", "?") + "</g>"
				: ClientState.str(quest, "objective", "")), width - 34);
		int lines = Math.min(2, objective.size());
		int height = 28 + lines * 10;
		int accent = done ? Ui.GOOD : switch (ClientState.str(quest, "faction", "GOOD")) {
			case "VILLAIN" -> Ui.VILLAIN;
			case "NEUTRAL" -> Ui.END;
			default -> Ui.CYAN;
		};

		graphics.fill(x, y, x + width, y + height, Ui.argb(170, Ui.PANEL));
		graphics.fill(x, y, x + 2, y + height, Ui.argb(255, accent));
		graphics.item(Ui.stack(ClientState.str(quest, "icon", "minecraft:paper"), 1), x + 7, y + 5);
		graphics.text(client.font, Ui.trim(client.font, ClientState.str(quest, "title", ""), width - 34), x + 28, y + 5, Ui.argb(255, 0xFFFFFF), true);

		for (int i = 0; i < lines; i++) {
			graphics.text(client.font, objective.get(i), x + 28, y + 16 + i * 10, Ui.argb(255, 0xDDDDDD), true);
		}

		int barY = y + height - 7;
		String count = progress + "/" + target;
		int barW = width - 36 - client.font.width(count) - 4;
		graphics.fill(x + 28, barY, x + 28 + barW, barY + 3, Ui.argb(200, 0x2A2E34));
		graphics.fill(x + 28, barY, x + 28 + barW * percent(quest) / 100, barY + 3, Ui.argb(255, done ? Ui.GOOD : accent));
		graphics.text(client.font, count, x + width - 6 - client.font.width(count), barY - 3, Ui.argb(255, done ? Ui.GOOD : 0xFFFFFF), false);
	}

	/**
	 * Versão reduzida: uma coluna de ícones na lateral da tela (como o time de pokémons do Pixelmon),
	 * cada um com a porcentagem da missão. A rastreada fica com borda ciano.
	 */
	private static void drawCompact(GuiGraphicsExtractor graphics, Minecraft client, float hidden) {
		List<JsonObject> quests = new ArrayList<>(ClientState.quests());

		if (quests.isEmpty()) {
			return;
		}

		JsonObject tracked = ClientState.trackedQuest();

		if (tracked != null) {
			quests.remove(tracked);
			quests.add(0, tracked);
		}

		int count = Math.min(6, quests.size());
		int box = 22;
		int gap = 4;
		int screenW = client.getWindow().getGuiScaledWidth();
		int screenH = client.getWindow().getGuiScaledHeight();
		boolean right = ClientConfig.trackerOnRight();
		int out = (int) ((box + 40) * hidden);
		int x = right ? screenW - box - 4 + out : 4 - out;
		int y = (screenH - count * (box + gap)) / 2;

		for (int i = 0; i < count; i++) {
			JsonObject quest = quests.get(i);
			int percent = percent(quest);
			boolean done = percent >= 100;
			boolean isTracked = quest == tracked;
			int by = y + i * (box + gap);

			graphics.fill(x, by, x + box, by + box, Ui.argb(170, Ui.PANEL));

			if (isTracked || done) {
				int color = Ui.argb(255, done ? Ui.GOOD : Ui.CYAN);
				graphics.fill(x, by, x + box, by + 1, color);
				graphics.fill(x, by + box - 1, x + box, by + box, color);
				graphics.fill(x, by, x + 1, by + box, color);
				graphics.fill(x + box - 1, by, x + box, by + box, color);
			}

			graphics.item(Ui.stack(ClientState.str(quest, "icon", "minecraft:paper"), 1), x + 3, by + 2);
			graphics.fill(x + 2, by + box - 3, x + box - 2, by + box - 2, Ui.argb(200, 0x2A2E34));
			graphics.fill(x + 2, by + box - 3, x + 2 + (box - 4) * percent / 100, by + box - 2, Ui.argb(255, done ? Ui.GOOD : Ui.CYAN));

			String text = done ? "✔" : percent + "%";
			int tx = right ? x - 3 - client.font.width(text) : x + box + 3;
			graphics.text(client.font, text, tx, by + 7, Ui.argb(255, done ? Ui.GOOD : 0xFFFFFF), true);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Comemoração ao subir de nível
	// ---------------------------------------------------------------------------------------------

	private record Celebration(String title, String subtitle, int color) {
	}

	private static final Deque<Celebration> CELEBRATIONS = new ArrayDeque<>();
	private static final float CELEBRATION_SECONDS = 3.8F;
	private static Celebration current;
	private static long currentStart;

	static void celebrate(String title, String subtitle, int color) {
		if (CELEBRATIONS.size() < 6) {
			CELEBRATIONS.add(new Celebration(title, subtitle, color));
		}
	}

	/**
	 * Faixa no meio da tela: abre do centro para os lados, o título "salta" para dentro,
	 * um brilho passa por cima e faíscas giram em volta. Uma comemoração por vez.
	 */
	static void drawCelebration(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		long now = System.currentTimeMillis();

		if (current == null) {
			current = CELEBRATIONS.poll();

			if (current == null || client.player == null) {
				current = null;
				return;
			}

			currentStart = now;
			Sfx.byId("quest.levelup");
		}

		float t = (now - currentStart) / 1000F;

		if (t > CELEBRATION_SECONDS) {
			current = null;
			return;
		}

		float fade = t > CELEBRATION_SECONDS - 0.6F ? (CELEBRATION_SECONDS - t) / 0.6F : Math.min(1F, t / 0.15F);
		fade = Math.max(0.04F, fade);
		int screenW = client.getWindow().getGuiScaledWidth();
		int screenH = client.getWindow().getGuiScaledHeight();
		int cx = screenW / 2;
		int cy = (int) (screenH * 0.30);
		int color = current.color();

		// Faixa que abre do centro
		int half = (int) (Math.min(220, screenW * 0.36F) * Ui.ease(t / 0.35F));
		graphics.fill(cx - half, cy - 22, cx + half, cy + 26, Ui.argb((int) (175 * fade), Ui.PANEL));
		graphics.fill(cx - half, cy - 22, cx + half, cy - 21, Ui.argb((int) (255 * fade), color));
		graphics.fill(cx - half, cy + 25, cx + half, cy + 26, Ui.argb((int) (255 * fade), color));

		// Brilho passando por cima
		if (t > 0.3F && t < 1.2F && half > 20) {
			int sweep = cx - half + (int) (2 * half * ((t - 0.3F) / 0.9F));
			graphics.fill(Math.max(cx - half, sweep - 10), cy - 21, Math.min(cx + half, sweep + 10), cy + 25, Ui.argb((int) (55 * fade), 0xFFFFFF));
		}

		// Título com "pulo"
		float scale = 2.4F * Math.max(0.2F, Ui.easeOutBack(t / 0.45F));
		int titleW = (int) (client.font.width(current.title()) * scale);
		Ui.bigTextArgb(graphics, client.font, current.title(), cx - titleW / 2, cy - 4 - (int) (9 * scale / 2) - 4, scale,
				Ui.argb((int) (255 * fade), color), Ui.argb((int) (255 * fade), Ui.darker(color, 0.35F)));

		// Estrelas dos lados
		int bob = (int) (Math.sin(t * 6) * 2);
		int starX = Math.max(titleW / 2 + 12, 40);
		graphics.text(client.font, "★", cx - starX - 6, cy - 9 + bob, Ui.argb((int) (255 * fade), color), true);
		graphics.text(client.font, "★", cx + starX, cy - 9 - bob, Ui.argb((int) (255 * fade), color), true);

		// Subtítulo
		if (t > 0.25F) {
			float subFade = fade * Math.min(1F, (t - 0.25F) / 0.3F);
			int subW = client.font.width(current.subtitle());
			graphics.text(client.font, current.subtitle(), cx - subW / 2, cy + 12, Ui.argb((int) (255 * Math.max(0.04F, subFade)), 0xFFFFFF), true);
		}

		// Faíscas girando em volta
		for (int i = 0; i < 14; i++) {
			double angle = i * (Math.PI * 2 / 14) + t * 1.3;
			double radius = 30 + 90 * Ui.ease(t / 0.8F);
			int px = cx + (int) (Math.cos(angle) * radius * 1.9);
			int py = cy + 2 + (int) (Math.sin(angle) * radius * 0.45) - (int) (t * 6);
			int size = i % 3 == 0 ? 2 : 1;
			graphics.fill(px, py, px + size, py + size, Ui.argb((int) (220 * fade), i % 2 == 0 ? color : 0xFFFFFF));
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Avisos (no lugar do chat)
	// ---------------------------------------------------------------------------------------------

	static void drawNotices(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null || !ClientConfig.notices() || client.gui.screen() instanceof MenuScreen) {
			return;
		}

		long now = System.currentTimeMillis();
		int width = 210;
		int x = client.getWindow().getGuiScaledWidth() - width - 6;
		int y = 6;
		int shown = 0;

		for (ClientState.Notice notice : ClientState.notices()) {
			long age = now - notice.time();

			if (age > NOTICE_MS || shown >= 3) {
				break;
			}

			float alpha = Math.max(0.05F, age > NOTICE_MS - 600 ? (NOTICE_MS - age) / 600F : 1F);
			// Entra deslizando pela direita e sai deslizando de volta.
			float in = Ui.ease(age / 250F);
			float outT = age > NOTICE_MS - 400 ? Ui.ease((age - (NOTICE_MS - 400)) / 400F) : 0F;
			int nx = x + (int) ((width + 10) * Math.max(1F - in, outT));
			List<FormattedCharSequence> lines = client.font.split(Text.parse(notice.text()), width - 14);
			int count = Math.min(4, lines.size());
			int height = 8 + count * 10;
			graphics.fill(nx, y, nx + width, y + height, Ui.argb((int) (190 * alpha), Ui.PANEL));
			graphics.fill(nx, y, nx + 2, y + height, Ui.argb((int) (255 * alpha), Ui.CYAN));

			for (int i = 0; i < count; i++) {
				graphics.text(client.font, lines.get(i), nx + 8, y + 5 + i * 10, Ui.argb((int) (255 * alpha), 0xFFFFFF), true);
			}

			y += height + 4;
			shown++;
		}
	}
}
