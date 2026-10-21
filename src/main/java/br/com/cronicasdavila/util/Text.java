package br.com.cronicasdavila.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Marcação simples de cores usada em todos os diálogos:
 *
 * <pre>
 * &lt;r&gt;vermelho&lt;/r&gt;  — perigo / ação importante (ex.: "Elimine")
 * &lt;g&gt;verde&lt;/g&gt;     — ação tranquila (ex.: "Colha")
 * &lt;y&gt;amarelo&lt;/y&gt;   — quantidades e itens pedidos
 * &lt;o&gt;dourado&lt;/o&gt;   — recompensas, ouro, coisas valiosas
 * &lt;a&gt;ciano&lt;/a&gt;     — nomes de lugares e pessoas
 * &lt;p&gt;roxo&lt;/p&gt;      — magia, mistério
 * &lt;n&gt;laranja&lt;/n&gt;   — Nether, fogo, lava
 * &lt;d&gt;cinza&lt;/d&gt;     — sussurros, comentários
 * &lt;b&gt;negrito&lt;/b&gt;  &lt;i&gt;itálico&lt;/i&gt;  &lt;e&gt;língua dos Endermen&lt;/e&gt; (símbolos da mesa de encantamento)
 * {item:minecraft:diamond}  {entity:minecraft:zombie}  {ench:minecraft:mending}
 * </pre>
 */
public final class Text {
	/** A fonte dos símbolos da mesa de encantamento, usada como a língua dos Endermen. */
	private static final FontDescription ENDER_FONT = new FontDescription.Resource(Identifier.withDefaultNamespace("alt"));

	private Text() {
	}

	public record Segment(String text, Component component, Style style) {
		int length() {
			return component != null ? component.getString().length() : text.length();
		}
	}

	public static int colorOf(char tag) {
		return switch (tag) {
			case 'r' -> 0xFF5555;
			case 'g' -> 0x55FF55;
			case 'y' -> 0xFFFF55;
			case 'o' -> 0xFFAA00;
			case 'a' -> 0x55FFFF;
			case 'p' -> 0xFF77FF;
			case 'n' -> 0xFF7733;
			case 'd' -> 0xAAAAAA;
			case 'w' -> 0xFFFFFF;
			default -> -1;
		};
	}

	public static List<Segment> segments(String markup) {
		List<Segment> result = new ArrayList<>();
		Deque<Style> styles = new ArrayDeque<>();
		styles.push(Style.EMPTY);
		StringBuilder buffer = new StringBuilder();
		int i = 0;

		while (i < markup.length()) {
			char c = markup.charAt(i);

			if (c == '<') {
				int end = markup.indexOf('>', i);

				if (end > i && end - i <= 4) {
					String tag = markup.substring(i + 1, end);
					boolean closing = tag.startsWith("/");
					String name = closing ? tag.substring(1) : tag;

					if (name.length() == 1 && (colorOf(name.charAt(0)) >= 0 || name.equals("b") || name.equals("i") || name.equals("e"))) {
						flush(result, buffer, styles.peek());

						if (closing) {
							if (styles.size() > 1) {
								styles.pop();
							}
						} else {
							Style current = styles.peek();
							Style next;

							if (name.equals("b")) {
								next = current.withBold(true);
							} else if (name.equals("e")) {
								next = current.withFont(ENDER_FONT).withColor(TextColor.fromRgb(0xC77DFF));
							} else if (name.equals("i")) {
								next = current.withItalic(true).withColor(TextColor.fromRgb(0xAAAAAA));
							} else {
								next = current.withColor(TextColor.fromRgb(colorOf(name.charAt(0))));
							}

							styles.push(next);
						}

						i = end + 1;
						continue;
					}
				}
			} else if (c == '{') {
				int end = markup.indexOf('}', i);

				if (end > i) {
					Component placeholder = placeholder(markup.substring(i + 1, end));

					if (placeholder != null) {
						flush(result, buffer, styles.peek());
						result.add(new Segment(null, placeholder, styles.peek()));
						i = end + 1;
						continue;
					}
				}
			}

			buffer.append(c);
			i++;
		}

		flush(result, buffer, styles.peek());
		return result;
	}

	private static void flush(List<Segment> result, StringBuilder buffer, Style style) {
		if (!buffer.isEmpty()) {
			result.add(new Segment(buffer.toString(), null, style));
			buffer.setLength(0);
		}
	}

	private static Component placeholder(String content) {
		int colon = content.indexOf(':');

		if (colon < 0) {
			return null;
		}

		String kind = content.substring(0, colon);
		String id = content.substring(colon + 1);

		switch (kind) {
			case "item" -> {
				Identifier identifier = Identifier.tryParse(id);

				if (identifier == null) {
					return null;
				}

				Item item = BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
				return item != null ? new ItemStack(item).getHoverName() : Component.literal(id);
			}
			case "entity" -> {
				Identifier identifier = Identifier.tryParse(id);
				return identifier == null ? null : Component.translatable("entity." + identifier.getNamespace() + "." + identifier.getPath());
			}
			case "ench" -> {
				Identifier identifier = Identifier.tryParse(id);
				return identifier == null ? null : Component.translatable("enchantment." + identifier.getNamespace() + "." + identifier.getPath());
			}
			default -> {
				return null;
			}
		}
	}

	public static int visibleLength(List<Segment> segments) {
		int total = 0;

		for (Segment segment : segments) {
			total += segment.length();
		}

		return total;
	}

	/** Monta o texto mostrando no máximo {@code maxChars} caracteres (efeito máquina de escrever). */
	public static MutableComponent build(List<Segment> segments, int maxChars) {
		MutableComponent root = Component.empty();
		int remaining = maxChars;

		for (Segment segment : segments) {
			if (remaining <= 0) {
				break;
			}

			String text = segment.component != null ? segment.component.getString() : segment.text;

			if (segment.component != null && text.length() <= remaining) {
				root.append(segment.component.copy().withStyle(segment.style));
			} else {
				String cut = text.length() > remaining ? text.substring(0, remaining) : text;
				root.append(Component.literal(cut).withStyle(segment.style));
			}

			remaining -= text.length();
		}

		return root;
	}

	public static MutableComponent parse(String markup) {
		return build(segments(markup), Integer.MAX_VALUE);
	}

	/** Remove a marcação, deixando só o texto. */
	public static String plain(String markup) {
		return parse(markup).getString();
	}
}
