package br.com.cronicasdavila.effect;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

import br.com.cronicasdavila.CronicasDaVila;

/**
 * Efeitos novos do mod.
 *
 * <p><b>Vilão da História</b> — o antagonista do "Herói da Vila". Concedido a quem chega a Arquivilão e domina
 * todos os inimigos que dão missões. Enquanto ativo, todas as criaturas (menos os chefes) fogem de você.
 * Em troca, o mundo normal te rejeita: fadiga I por lá e, depois de um dia inteiro fora do Nether, você pega fogo.
 */
public final class ModEffects {
	public static final Holder<MobEffect> VILAO_DA_HISTORIA = Registry.registerForHolder(
			BuiltInRegistries.MOB_EFFECT, CronicasDaVila.id("vilao_da_historia"), new VilaoDaHistoriaEffect());

	private ModEffects() {
	}

	public static void init() {
		// Força o carregamento da classe (registra os efeitos).
	}

	public static class VilaoDaHistoriaEffect extends MobEffect {
		protected VilaoDaHistoriaEffect() {
			super(MobEffectCategory.NEUTRAL, 0x5A1E6E);
		}
	}
}
