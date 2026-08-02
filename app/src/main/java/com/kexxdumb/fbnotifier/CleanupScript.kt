package com.kexxdumb.fbnotifier

// Se re-ejecuta con cada cambio en la página (MutationObserver), porque el
// feed de Facebook carga contenido nuevo mientras haces scroll.
object CleanupScript {
    val JS = """
        (function() {
          if (window.__fhCleanupInstalled) return;
          window.__fhCleanupInstalled = true;

          // --- 1) Banners "Descarga la app" / "Abrir app" -----------------
          // Estos se detectan por el DESTINO del link (Play Store, esquemas
          // de apertura de app), NO por el texto — así funciona sin importar
          // el idioma en que tengas Facebook configurado.
          function hideAppBanners() {
            document.querySelectorAll('a[href]').forEach(function(a) {
              var href = a.getAttribute('href') || '';
              var isAppLink =
                href.indexOf('play.google.com/store/apps') !== -1 ||
                href.indexOf('itunes.apple.com') !== -1 ||
                href.indexOf('market://') === 0 ||
                href.indexOf('intent://') === 0 ||
                href.indexOf('fb://') === 0;
              if (!isAppLink) return;
              var el = a.closest('[role="banner"], header, div') || a;
              // Sube un par de niveles para ocultar el banner completo, no
              // solo el link, sin borrar de más (limita cuántos niveles sube).
              var container = el;
              for (var i = 0; i < 2 && container.parentElement; i++) {
                if (container.getBoundingClientRect().height > 200) break;
                container = container.parentElement;
              }
              container.style.display = 'none';
            });
          }

          // --- 2) Posts patrocinados: best-effort por texto, multi-idioma -
          // Menos confiable: si Facebook cambia el texto exacto en algún
          // idioma, o agrega uno nuevo, esto deja de detectarlo ahí.
          var sponsoredLabels = [
            'Sponsored', 'Publicidad', 'Patrocinado', 'Gesponsert',
            'Sponsorisé', 'Sponsorizzato', 'Gesponsord', 'Sponsrad'
          ];
          function hideSponsoredPosts() {
            document.querySelectorAll('span, a').forEach(function(el) {
              var text = (el.textContent || '').trim();
              if (sponsoredLabels.indexOf(text) === -1) return;
              var card = el.closest('article, [role="article"]');
              if (card) card.style.display = 'none';
            });
          }

          function cleanup() {
            try { hideAppBanners(); } catch (e) {}
            try { hideSponsoredPosts(); } catch (e) {}
          }

          cleanup();

          var debounceTimer = null;
          var observer = new MutationObserver(function() {
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(cleanup, 300);
          });
          observer.observe(document.body, { childList: true, subtree: true });
        })();
    """.trimIndent()
}
