// Écran multijoueur — phase 1 : état honnête de l'avancement (le lobby réseau arrive en phase 2).
export function onlineScreen(menu) {
  return menu.frame('Multijoueur', 'Parties en ligne', `<div class="grp"><div class="detail cut"><span class="eyebrow">En préparation</span><h2>Serveur de parties</h2>
    <p>Le mode en ligne n'est pas encore disponible dans cette version. L'entraînement contre les bots est entièrement jouable.</p></div></div>`);
}
export function onlineAction() {}
