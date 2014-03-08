package com.tarnation.Eliasar.heroes.skills;

import com.herocraftonline.heroes.Heroes;
import com.herocraftonline.heroes.api.SkillResult;
import com.herocraftonline.heroes.characters.Hero;
import com.herocraftonline.heroes.characters.Monster;
import com.herocraftonline.heroes.characters.effects.EffectType;
import com.herocraftonline.heroes.characters.effects.PeriodicDamageEffect;
import com.herocraftonline.heroes.characters.skill.*;
import com.tarnation.Eliasar.util.ParticleEffect;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

public class SkillCensure extends TargettedSkill {

    private String applyText;
    private String expireText;

    public SkillCensure(Heroes plugin) {
        super(plugin, "Censure");
        setDescription("Deals $1 damage over $2 seconds. Deals $3 damage to undead.");
        setUsage("/skill censure");
        setArgumentRange(0, 0);
        setIdentifiers("skill censure");

        setTypes(SkillType.LIGHT, SkillType.SILENCABLE, SkillType.DAMAGING);
    }

    @Override
    public ConfigurationSection getDefaultConfig() {
        ConfigurationSection node = super.getDefaultConfig();

        node.set(SkillSetting.DURATION.node(), 6000);
        node.set(SkillSetting.PERIOD.node(), 2000);
        node.set(SkillSetting.DAMAGE_TICK.node(), 0.75);
        node.set(SkillSetting.DAMAGE_INCREASE.node(), 0.05);
        node.set(SkillSetting.MANA.node(), 20);
        node.set(SkillSetting.COOLDOWN.node(), 10000);
        node.set(SkillSetting.APPLY_TEXT.node(), "$1 has been silenced!");
        node.set(SkillSetting.EXPIRE_TEXT.node(), "$1 is no longer silenced.");
        node.set("particle-name", "smoke");
        node.set("particle-power", 1);
        node.set("particle-amount", 100);
        return node;
    }

    @Override
    public void init() {
        super.init();
        applyText = SkillConfigManager.getRaw(this, SkillSetting.APPLY_TEXT.node(), "$1 has been silenced!");
        expireText = SkillConfigManager.getRaw(this, SkillSetting.EXPIRE_TEXT.node(), "$1 is no longer silenced.");
    }

    @Override
    public String getDescription(Hero hero) {
        String description = "";
        String ending = "§6; ";

        // Mana
        int mana = SkillConfigManager.getUseSetting(hero, this, SkillSetting.MANA.node(), 0, false)
                - (SkillConfigManager.getUseSetting(hero, this, SkillSetting.MANA_REDUCE.node(), 0, false) * hero.getLevel());
        if (mana > 0) {
            description += "§6Cost: §9" + mana + "MP" + ending;
        }

        // Health cost
        int healthCost = SkillConfigManager.getUseSetting(hero, this, SkillSetting.HEALTH_COST, 0, false) -
                (SkillConfigManager.getUseSetting(hero, this, SkillSetting.HEALTH_COST_REDUCE, mana, true) * hero.getLevel());
        if (healthCost > 0 && mana > 0) {
            description += "§6" + healthCost + ending;
        } else if (healthCost > 0) {
            description += "§6Cost: §c" + healthCost + "HP" + ending;
        }

        // Cooldown
        int cooldown = (SkillConfigManager.getUseSetting(hero, this, SkillSetting.COOLDOWN.node(), 0, false)
                - SkillConfigManager.getUseSetting(hero, this, SkillSetting.COOLDOWN_REDUCE.node(), 0, false) * hero.getLevel()) / 1000;
        if (cooldown > 0) {
            description += "§6CD: §9" + cooldown + "s" + ending;
        }

        // Damage tick
        float dmgTick = (float) SkillConfigManager.getUseSetting(hero, this, SkillSetting.DAMAGE_TICK.node(), 0.75, false)
                + (float) SkillConfigManager.getUseSetting(hero, this, SkillSetting.DAMAGE_INCREASE.node(), 0.05, false) * hero.getLevel();

        // Period
        int period = SkillConfigManager.getUseSetting(hero, this, SkillSetting.PERIOD.node(), 2000, false);

        // Duration
        int duration = SkillConfigManager.getUseSetting(hero, this, SkillSetting.DURATION.node(), 6000, false);

        description += getDescription()
                .replace("$1", "§9" + dmgTick * (duration/period) + "§6")
                .replace("$2", "§9" + duration / 1000 + "§6")
                .replace("$3", "§9" + dmgTick * (duration/period) * 2 + "§6");

        return description;
    }

    @Override
    public SkillResult use(Hero hero, LivingEntity target, String[] strings) {

        Player player = hero.getPlayer();
        long period = SkillConfigManager.getUseSetting(hero, this, SkillSetting.PERIOD.node(), 2000, false);
        long duration = SkillConfigManager.getUseSetting(hero, this, SkillSetting.DURATION.node(), 6000, false);
        double dmgTick = SkillConfigManager.getUseSetting(hero, this, SkillSetting.DAMAGE_TICK.node(), 0.75, false)
                + SkillConfigManager.getUseSetting(hero, this, SkillSetting.DAMAGE_INCREASE.node(), 0.05, false) * hero.getLevel();

        // Do not damage players in creative
        if (target instanceof Player) {
            if (((Player) target).getGameMode() == GameMode.CREATIVE)
                return SkillResult.INVALID_TARGET;
        } else if (target == null) {
            return SkillResult.INVALID_TARGET;
        }

        if (Skill.damageCheck(hero.getPlayer(), target)) {
            broadcastExecuteText(hero, target);
            addSpellTarget(target, hero);
            CensureEffect ce = new CensureEffect(this, period, duration, dmgTick, player);
            plugin.getCharacterManager().getCharacter(target).addEffect(ce);
        } else {
            return SkillResult.INVALID_TARGET;
        }
        return SkillResult.NORMAL;
    }

    public class CensureEffect extends PeriodicDamageEffect {

        private String particleName;
        private float particlePower;
        private int particleAmount;

        public CensureEffect(Skill skill, long period, long duration, double tickDamage, Player caster) {
            super(skill, "Censure", period, duration, tickDamage, caster);
            this.particleName = SkillConfigManager.getUseSetting(getApplierHero(), SkillCensure.this, "particle-name", "smoke");
            this.particlePower = (float) SkillConfigManager.getUseSetting(getApplierHero(), SkillCensure.this, "particle-power", 1, false);
            this.particleAmount = SkillConfigManager.getUseSetting(getApplierHero(), SkillCensure.this, "particle-amount", 100, false);
            this.types.add(EffectType.DISPELLABLE);
            this.types.add(EffectType.SILENCE);
        }

        @Override
        public void applyToHero(Hero hero) {
            super.applyToHero(hero);
            Player p = hero.getPlayer();
            broadcast(p.getLocation(), SkillCensure.this.applyText.replace("$1", p.getDisplayName()));
        }

        @Override
        public void removeFromHero(Hero hero) {
            super.removeFromHero(hero);
            Player p = hero.getPlayer();
            broadcast(p.getLocation(), SkillCensure.this.expireText.replace("$1", p.getDisplayName()));
        }

        @Override
        public void tickMonster(Monster monster) {
            if ((monster instanceof Zombie) || (monster instanceof Skeleton))
                setTickDamage(getTickDamage() * 2.0);

            //addSpellTarget(monster.getEntity(), plugin.getCharacterManager().getHero(getApplier()));
            damageEntity(monster.getEntity(), getApplier(), getTickDamage(), DamageCause.ENTITY_ATTACK, false);

            playEffect(monster.getEntity());
        }

        @Override
        public void tickHero(Hero hero) {
            //Player p = hero.getPlayer();
            //addSpellTarget(p, plugin.getCharacterManager().getHero(getApplier()));
            damageEntity(hero.getPlayer(), getApplier(), getTickDamage(), DamageCause.ENTITY_ATTACK, false);

            playEffect(hero.getEntity());
        }

        public void playEffect(LivingEntity le) {
            // Particle effect - Eli's
            ParticleEffect pe = new ParticleEffect(particleName, le.getEyeLocation(), particlePower, particleAmount);
            pe.playEffect();

            // TODO: When Spigot supports it, uncomment for particles
            //CraftWorld.Spigot playerParticles = new CraftWorld.Spigot();
            //playerParticles.playEffect(player.getEyeLocation(), Effect.HEART, 0, 0, 0, 0, 0, particlePower, particleAmount, 64);
            //pePlayer.playEffect();
        }
    }
}