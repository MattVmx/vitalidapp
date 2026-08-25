(function () {
  'use strict';

  var storageKey = 'vitalidapp-directory-scroll';
  var root = document.documentElement;
  var savedPosition = readSavedPosition();

  if (savedPosition !== null) {
    root.classList.add('directory-scroll-pending');
  }

  function readSavedPosition() {
    var storedValue;

    try {
      storedValue = window.sessionStorage.getItem(storageKey);
      window.sessionStorage.removeItem(storageKey);
    } catch (error) {
      return null;
    }

    if (storedValue === null) {
      return null;
    }

    var position = Number(storedValue);
    return Number.isFinite(position) ? position : null;
  }

  function saveScrollPosition() {
    try {
      window.sessionStorage.setItem(storageKey, String(window.scrollY));
    } catch (error) {
      // La navegación sigue funcionando si el navegador bloquea el almacenamiento.
    }
  }

  function restoreScrollPosition() {
    if (savedPosition === null) {
      return;
    }

    root.style.scrollBehavior = 'auto';
    window.scrollTo(0, savedPosition);

    window.requestAnimationFrame(function () {
      root.style.removeProperty('scroll-behavior');
      root.classList.remove('directory-scroll-pending');
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

  window.setTimeout(function () {
    root.classList.remove('directory-scroll-pending');
  }, 1500);
})();
