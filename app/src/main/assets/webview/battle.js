/*
 * Injected into the Showdown web client by BattleWebView after every page load.
 * Adds battle.css and scales the battle to fill the screen. Safe to run more than once.
 */
function thorShowdownInstall(css) {
    var BATTLE_WIDTH = 640;
    var BATTLE_HEIGHT = 360;
    var STYLE_ID = 'thor-showdown-style';

    var style = document.getElementById(STYLE_ID) || document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = css;
    document.head.appendChild(style);

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
    }
}
