package com.odan.livingworld.debug;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/**
 * Marque un PNJ dont le nameplate affiche l'étiquette de debug LivingWorld.
 * Composant non persistant (enregistré sans codec) : il garde le nameplate d'origine
 * en mémoire pour le restaurer quand l'affichage est désactivé.
 */
public class NpcLabel implements Component<EntityStore> {

    /** Texte du nameplate avant l'étiquette ; null si le PNJ n'avait pas de nameplate. */
    @Nullable
    private final String originalText;
    private final boolean hadNameplate;
    /** Temps écoulé depuis la dernière mise à jour du texte, en secondes. */
    private float sinceUpdate;

    public NpcLabel() {
        this(null, false);
    }

    public NpcLabel(@Nullable String originalText, boolean hadNameplate) {
        this.originalText = originalText;
        this.hadNameplate = hadNameplate;
    }

    @Nullable
    public String getOriginalText() {
        return originalText;
    }

    public boolean hadNameplate() {
        return hadNameplate;
    }

    /** Ajoute dt et renvoie vrai si l'intervalle est écoulé (le compteur repart alors à zéro). */
    public boolean advance(float dt, float interval) {
        sinceUpdate += dt;
        if (sinceUpdate < interval) {
            return false;
        }
        sinceUpdate = 0;
        return true;
    }

    @Override
    public Component<EntityStore> clone() {
        NpcLabel copy = new NpcLabel(originalText, hadNameplate);
        copy.sinceUpdate = sinceUpdate;
        return copy;
    }
}
