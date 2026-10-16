package br.com.cronicasdavila.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;

/**
 * Lê o botão esquerdo do mouse direto da janela, a cada quadro.
 *
 * <p>Em algumas instalações o evento de clique do jogo não chega às telas do mod. Assim, a tela
 * confere sozinha se o botão acabou de ser apertado e usa a posição do mouse que recebe ao ser
 * desenhada (que já vem na escala da interface, em qualquer tamanho de HUD, em tela cheia ou não).
 * Se o evento normal também chegar, {@link #claim()} evita contar o mesmo clique duas vezes.</p>
 */
final class MousePoll {
	private static final long SAME_CLICK_MS = 180L;

	private boolean wasDown;
	private long lastHandled;

	MousePoll() {
		// O clique que abriu a tela (num botão do inventário, por exemplo) não conta.
		wasDown = isDown();
	}

	private static boolean isDown() {
		try {
			long window = Minecraft.getInstance().getWindow().handle();
			return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** O botão acabou de ser apertado neste quadro? */
	boolean pressed() {
		boolean down = isDown();
		boolean edge = down && !wasDown;
		wasDown = down;
		return edge;
	}

	/** Marca o clique como tratado. Retorna false se ele já foi tratado pelo outro caminho. */
	boolean claim() {
		long now = System.currentTimeMillis();

		if (now - lastHandled < SAME_CLICK_MS) {
			return false;
		}

		lastHandled = now;
		return true;
	}
}
