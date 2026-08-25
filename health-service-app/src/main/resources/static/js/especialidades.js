(function () {
  'use strict';

  var storageKey = 'vitalidapp-directory-scroll';

  function saveScrollPosition() {
    try {
      window.sessionStorage.setItem(storageKey, String(window.scrollY));
    } catch (error) {
      // La navegación sigue funcionando si el navegador bloquea el almacenamiento.
    }
  }

  function restoreScrollPosition() {
    var savedPosition;

    try {
      savedPosition = window.sessionStorage.getItem(storageKey);
      window.sessionStorage.removeItem(storageKey);
    } catch (error) {
      return;
    }

    if (savedPosition === null) {
      return;
    }

    var position = Number(savedPosition);
    if (!Number.isFinite(position)) {
      return;
    }

    window.requestAnimationFrame(function () {
      window.requestAnimationFrame(function () {
        window.scrollTo(0, position);
      });
    });
  }

  document.addEventListener('click', function (event) {
    if (event.target.closest('a[data-directory-navigation]')) {
      saveScrollPosition();
    }
  });

  document.addEventListener('submit', function (event) {
    if (event.target.matches('form[data-directory-navigation]')) {
      saveScrollPosition();
    }
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', restoreScrollPosition);
  } else {
    restoreScrollPosition();
  }
})();
