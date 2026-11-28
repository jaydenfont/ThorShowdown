/*
 * Injected into the Showdown web client by BattleWebView, before the page's own scripts run
 * (document start), so Showdown's controls never appear without battle.css.
 * Adds battle.css and scales the battle to fill the screen. Safe to run more than once.
 */
function thorShowdownInstall(css) {
    var BATTLE_WIDTH = 640;
    var BATTLE_HEIGHT = 360;
    var STYLE_ID = 'thor-showdown-style';

    // At document start <head> may not exist yet; a <style> works anywhere in the document.
    var style = document.getElementById(STYLE_ID) || document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = css;
    (document.head || document.documentElement).appendChild(style);

    // The viewport can change size during a re-layout, so the fit is recomputed on resize.
    function fit() {
        var scale = Math.min(innerWidth / BATTLE_WIDTH, innerHeight / BATTLE_HEIGHT);
        var root = document.documentElement.style;
        root.setProperty('--thor-scale', scale);
        root.setProperty('--thor-left', (innerWidth - BATTLE_WIDTH * scale) / 2 + 'px');
    }

    fit();
    if (!window.thorShowdownFitting) {
        window.thorShowdownFitting = true;
        window.addEventListener('resize', fit);
        document.addEventListener('DOMContentLoaded', fit);
    }
}
