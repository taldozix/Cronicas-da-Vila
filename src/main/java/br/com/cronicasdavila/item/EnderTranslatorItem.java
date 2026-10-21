package br.com.cronicasdavila.item;

import java.util.function.Consumer;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import br.com.cronicasdavila.util.Text;

/** Tradutor Ender: com ele no inventário, a língua dos Endermen deixa de ser só símbolos. */
public class EnderTranslatorItem extends Item {
	public EnderTranslatorItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Text.parse("<p>Traduz a língua antiga dos Endermen.</p>"));
		tooltip.accept(Text.parse("<d>Basta tê-lo no inventário ao conversar.</d>"));
		tooltip.accept(Text.parse("<d>Achado só nos baús das Cidades do Fim.</d>"));
	}
}
